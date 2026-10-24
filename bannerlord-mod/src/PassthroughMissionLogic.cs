using System;
using System.Collections.Generic;
using TaleWorlds.CampaignSystem;
using TaleWorlds.Core;
using TaleWorlds.Engine;
using TaleWorlds.InputSystem;
using TaleWorlds.Library;
using TaleWorlds.MountAndBlade;
using TaleWorlds.MountAndBlade.View.MissionViews;
using TaleWorlds.MountAndBlade.View.Screens;
using TaleWorlds.MountAndBlade.ViewModelCollection;
using TaleWorlds.ScreenSystem;

namespace MinecraftPassthrough
{
	internal sealed class PassthroughMissionLogic : MissionLogic
	{
		private const InputKey ToggleKey = InputKey.F9;
		private const float HealthScale = 5f;
		private const float GroundInterval = 0.4f;
		private const float ResampleDistance = 6f;
		private const float ResampleClimb = 1.5f;
		private const float HeadHeight = 2f;
		private const float Headroom = 1.9f;
		private const float HitReach = 0.6f;
		private const float HorseBack = 1.1f;
		private const float RiderTop = 2.9f;
		private const float MinSampleGap = 0.1f;
		private const float AgentRadius = 48f;
		private const float MaxJump = 100f;
		private const float TeleportReach = 2f;
		private const float TeleportWait = 5f;
		private const float BoundaryWall = 8f;
		private const int CellCacheLimit = 20000;
		private const int StillFramesToPause = 3;

		private readonly uint _missionId = MissionIds.Next();
		private bool _enabled = true;
		private Agent _controlled;
		private PlayerState _player;
		private bool _ownTeleport;
		private Vec3 _teleportTarget;
		private float _teleportUntil = -1f;
		private float _groundTimer;
		private float _focusTimer;
		private float _sinceSample = float.MaxValue;
		private Vec2 _groundCentre;
		private float _groundCentreZ;
		private readonly float[] _ground = new float[HostPublisher.GroundSize * HostPublisher.GroundSize];
		private readonly bool[] _inside = new bool[HostPublisher.GroundSize * HostPublisher.GroundSize];
		private readonly Dictionary<long, GroundCell> _cells = new Dictionary<long, GroundCell>();
		private float _lastPublishTime = -1f;
		private int _stillFrames;
		private readonly List<AgentEntry> _agents = new List<AgentEntry>();
		private MinecraftWorldEffects _effects;
		private readonly HurtFlash _flash = new HurtFlash();
		private Camera _combatCamera;
		private bool _mounted;
		private Agent _leftMount;
		private string _offeredGoods = "";
		private bool _campaign;
		private bool _paused;
		private float _steveHealth = -1f;
		private bool _steveDied;
		private MissionBehavior _statusView;
		private bool _statusHidden;
		private MBAgentVisuals _silenced;

		public static PassthroughMissionLogic Current { get; private set; }

		public InputCapture Input { get; } = new InputCapture();

		public bool CursorMode => Active && _player != null && _player.Has(PlayerLink.FlagCursor);

		private bool Active => _enabled && Mission.Mode != MissionMode.Deployment && Mission.Mode != MissionMode.Conversation
			&& Mission.Mode != MissionMode.Barter;

		private bool BannerlordUi => !CursorMode && ScreenManager.GetMouseVisibility();

		public bool IsStandIn(Agent agent)
		{
			return agent != null && Active && (agent == _controlled || agent == _leftMount);
		}

		public void SteveDied()
		{
			_steveDied = true;
		}

		public bool CloseMinecraftScreen()
		{
			if (!CursorMode)
			{
				return false;
			}
			Input.PressCloseScreen();
			return true;
		}

		public override void AfterStart()
		{
			Current = this;
			_effects = new MinecraftWorldEffects(Mission);

			SteveInventoryBehavior campaign = SteveInventoryBehavior.Current;
			if (campaign != null && IsPractice())
			{
				SubModule.Inventory?.Offer(_missionId, InventoryLink.ModeTournament, null, null);
				return;
			}
			_campaign = campaign != null;
			_offeredGoods = campaign?.Pending ?? "";
			SubModule.Inventory?.Offer(_missionId, campaign != null ? InventoryLink.ModeCampaign : InventoryLink.ModeFullKit, campaign?.Inventory, _offeredGoods,
				campaign != null ? MapInventory.HeroHealth() : 1f);
		}

		private bool IsPractice()
		{
			foreach (MissionBehavior behavior in Mission.MissionBehaviors)
			{
				string name = behavior.GetType().Name;
				if (name.Contains("Tournament") || name.Contains("ArenaPractice"))
				{
					return true;
				}
			}
			return false;
		}

		public override void OnEndMissionInternal()
		{
			if (_campaign && !_steveDied && _steveHealth >= 0f && Hero.MainHero != null)
			{
				Hero.MainHero.HitPoints = Math.Max(1, (int)Math.Round(Hero.MainHero.MaxHitPoints * _steveHealth));
			}
			Detach();
		}

		public override void OnRemoveBehavior()
		{
			Detach();
		}

		public override void OnMissionTick(float dt)
		{
			Guard.Run("OnMissionTick", () => MissionTick(dt));
		}

		private void MissionTick(float dt)
		{
			if (TaleWorlds.InputSystem.Input.IsKeyPressed(ToggleKey))
			{
				_enabled = !_enabled;
				InformationManager.DisplayMessage(new InformationMessage(_enabled ? Text.Get("blmc_on", "Minecraft passthrough on") : Text.Get("blmc_off", "Minecraft passthrough off")));
			}

			_flash.Tick(dt);
			UpdateStatusHud();
			InventoryReport report = SubModule.Inventory?.Poll(_missionId);
			SteveInventoryBehavior campaign = SteveInventoryBehavior.Current;
			if (report != null && campaign != null && _campaign)
			{
				campaign.Inventory = report.Inventory;
				_steveHealth = report.Health;
				campaign.Delivered(_offeredGoods);
				_offeredGoods = "";
			}
			if (Active)
			{
				SubModule.Events?.Poll(_effects.Handle);
			}

			_player = SubModule.Player?.Poll(dt, _missionId);
			Agent main = Mission.MainAgent;
			if (!Active || main == null || !main.IsActive())
			{
				Release();
				return;
			}

			TakeOver(main);
			bool teleporting = WaitingForTeleport();
			if (!teleporting && _player != null && _player.Feet.Distance(main.Position) < MaxJump)
			{
				_ownTeleport = true;
				main.TeleportToPosition(_player.Feet);
				_ownTeleport = false;
				Vec3 facing = _player.BodyForward;
				facing.z = 0f;
				if (facing.LengthSquared > 1E-04f)
				{
					main.LookDirection = facing.NormalizedCopy();
				}
			}

			RemoveLeftMount(main);
			Interact();
			UpdateGround(dt, teleporting ? _teleportTarget : _player?.Feet ?? main.Position);
			CollectAgents(main);
		}

		public void OnTeleported(Agent agent, Vec3 position)
		{
			if (_ownTeleport || agent != _controlled || !Active)
			{
				return;
			}
			_teleportTarget = position;
			_teleportUntil = Mission.CurrentTime + TeleportWait;
			_groundTimer = 0f;
			SubModule.Publisher?.Teleport(position);
		}

		private bool WaitingForTeleport()
		{
			if (_teleportUntil < 0f)
			{
				return false;
			}
			if (Mission.CurrentTime > _teleportUntil || (_player != null && _player.Feet.AsVec2.Distance(_teleportTarget.AsVec2) < TeleportReach))
			{
				_teleportUntil = -1f;
				return false;
			}
			return true;
		}

		public override void OnAgentHit(Agent affectedAgent, Agent affectorAgent, in MissionWeapon affectorWeapon, in Blow blow, in AttackCollisionData attackCollisionData)
		{
			Blow copy = blow;
			Guard.Run("OnAgentHit", () => AgentHit(affectedAgent, affectorAgent, copy));
		}

		private void AgentHit(Agent affectedAgent, Agent affectorAgent, Blow blow)
		{
			if (affectedAgent == null)
			{
				return;
			}

			if (Active && affectedAgent == _controlled)
			{
				if (_steveDied)
				{
					return;
				}
				HitTarget target = Target(blow.GlobalPosition);
				float damage = (blow.InflictedDamage + blow.AbsorbedByArmor) / HealthScale;
				if (target != HitTarget.None && damage > 0f)
				{
					Vec3 from = affectorAgent != null ? affectorAgent.Position - affectedAgent.Position : -blow.Direction;
					SubModule.Publisher?.Hurt(target == HitTarget.Horse ? -damage : damage, affectorAgent?.Index ?? -1, from.AsVec2.Normalized());
				}
				affectedAgent.Health = affectedAgent.HealthLimit;
			}
			else if (blow.InflictedDamage <= 0)
			{
			}
			else if (affectedAgent.Health < 1f)
			{
				_flash.Stop(affectedAgent);
				if (Active && affectorAgent != null && affectorAgent == _controlled && affectedAgent.IsHuman)
				{
					SubModule.Publisher?.Loot(LootDrop.Describe(affectedAgent));
				}
			}
			else if (Active && affectorAgent != null && affectorAgent == _controlled && affectedAgent != _leftMount)
			{
				_flash.Start(affectedAgent);
			}
		}

		public override void OnAgentRemoved(Agent affectedAgent, Agent affectorAgent, AgentState agentState, KillingBlow blow)
		{
			Guard.Run("OnAgentRemoved", () => _flash.Stop(affectedAgent));
		}

		private enum HitTarget
		{
			None,
			Steve,
			Horse,
		}

		private HitTarget Target(Vec3 hit)
		{
			if (_player == null)
			{
				return HitTarget.Steve;
			}
			float above = hit.z - _player.Feet.z;
			bool mounted = _player.Has(PlayerLink.FlagMounted);
			if (above <= -HitReach || above >= (mounted ? RiderTop : 1.8f) + HitReach)
			{
				return HitTarget.None;
			}
			return mounted && above < HorseBack ? HitTarget.Horse : HitTarget.Steve;
		}

		public override void OnAgentDeleted(Agent affectedAgent)
		{
			_flash.Forget(affectedAgent);
		}

		public void OverrideCamera(Camera camera)
		{
			Guard.Run("OverrideCamera", () => OverrideCameraCore(camera));
		}

		private void OverrideCameraCore(Camera camera)
		{
			if (Active && camera == _combatCamera && _player != null && _controlled != null)
			{
				camera.Frame = _player.CameraFrame;
				camera.SetFovVertical(_player.VerticalFov, Screen.AspectRatio, 0.1f, 12500f);
			}
		}

		public void Publish(MissionScreen screen)
		{
			Guard.Run("Publish", () => PublishCore(screen));
		}

		private void PublishCore(MissionScreen screen)
		{
			HostPublisher publisher = SubModule.Publisher;
			Camera camera = screen.CombatCamera;
			if (publisher == null || camera == null)
			{
				return;
			}

			if (!Active)
			{
				publisher.PublishIdle();
				return;
			}

			_combatCamera = camera;
			MatrixFrame view = camera.Frame;
			var frame = new HostFrame
			{
				Flags = HostPublisher.FlagMission,
				Time = Mission.CurrentTime,
				CameraPosition = view.origin,
				CameraRight = view.rotation.s,
				CameraUp = view.rotation.f,
				CameraForward = -view.rotation.u,
				VerticalFov = camera.GetFovVertical(),
				AspectRatio = camera.GetAspectRatio(),
				Near = camera.Near,
				Far = camera.Far,
				ScreenWidth = (uint)Screen.RealScreenResolutionWidth,
				ScreenHeight = (uint)Screen.RealScreenResolutionHeight,
				MissionId = _missionId,
			};

			float now = Mission.CurrentTime;
			_stillFrames = now == _lastPublishTime ? _stillFrames + 1 : 0;
			_lastPublishTime = now;
			if (!_paused && (MBCommon.IsPaused || _stillFrames >= StillFramesToPause))
			{
				frame.Flags |= HostPublisher.FlagPaused;
			}

			Agent main = _controlled;
			if (main != null && main.IsActive())
			{
				frame.Flags |= HostPublisher.FlagPlayer;
				if (BannerlordUi)
				{
					frame.Flags |= HostPublisher.FlagBannerlordUi;
				}
				if (_mounted)
				{
					frame.Flags |= HostPublisher.FlagMounted;
				}
				frame.PlayerPosition = main.Position;
				frame.BodyForward = main.LookDirection;
				frame.LookDirection = _player == null ? EnemyDirection(main) : main.LookDirection;
				frame.Velocity = main.Velocity;
				frame.Health = main.Health;
				frame.HealthMax = main.HealthLimit;
			}

			InputFrame input = Input.Capture(main != null && main.IsActive() && !BannerlordUi, CursorMode);
			publisher.Publish(frame, _agents, input);
		}

		private Vec3 EnemyDirection(Agent main)
		{
			float x = 0f, y = 0f;
			int count = 0;
			foreach (Agent agent in Mission.Agents)
			{
				if (agent.IsHuman && agent.IsActive() && agent.IsEnemyOf(main))
				{
					x += agent.Position.x;
					y += agent.Position.y;
					count++;
				}
			}
			if (count > 0)
			{
				var towards = new Vec2(x / count - main.Position.x, y / count - main.Position.y);
				if (towards.LengthSquared > 1f)
				{
					towards.Normalize();
					return new Vec3(towards.x, towards.y, 0f);
				}
			}
			return main.LookDirection;
		}

		private void TakeOver(Agent main)
		{
			if (_controlled != main)
			{
				Release();
				_controlled = main;
				_mounted = main.HasMount;
				main.SetMortalityState(Agent.MortalityState.Immortal);
			}

			Hide(main);
			if (main.AgentVisuals != null && main.AgentVisuals != _silenced)
			{
				_silenced = main.AgentVisuals;
				_silenced.SetVoiceDefinitionIndex(-1, 0f);
			}
			if (main.GetPrimaryWieldedItemIndex() != EquipmentIndex.None)
			{
				main.TryToSheathWeaponInHand(Agent.HandIndex.MainHand, Agent.WeaponWieldActionType.Instant);
			}
			if (main.GetOffhandWieldedItemIndex() != EquipmentIndex.None)
			{
				main.TryToSheathWeaponInHand(Agent.HandIndex.OffHand, Agent.WeaponWieldActionType.Instant);
			}
			if (main.HasMount)
			{
				_leftMount = main.MountAgent;
				Hide(_leftMount);
				main.EventControlFlags |= Agent.EventControlFlag.Dismount;
			}

			SetBannerlordControls(false);
		}

		private static void Hide(Agent agent)
		{
			if (agent.AgentVisuals != null && agent.AgentVisuals.GetVisible())
			{
				agent.AgentVisuals.SetVisible(false);
			}
		}

		private void SetBannerlordControls(bool enabled)
		{
			MissionMainAgentController controller = Mission.GetMissionBehavior<MissionMainAgentController>();
			if (controller != null)
			{
				controller.IsDisabled = !enabled;
			}
		}

		private void Release()
		{
			if (_controlled != null)
			{
				SetBannerlordControls(true);
			}
			if (_controlled != null && _controlled.IsActive())
			{
				_controlled.SetMortalityState(Agent.MortalityState.Mortal);
				_controlled.AgentVisuals?.SetVisible(true);
			}
			if (_leftMount != null && _leftMount.IsActive())
			{
				_leftMount.AgentVisuals?.SetVisible(true);
			}
			_controlled = null;
			_leftMount = null;
			_silenced = null;
		}

		public void TickPause()
		{
			PlayerState player = SubModule.Player?.Poll(0f, _missionId);
			SetPaused(Active && _controlled != null && player != null && player.Has(PlayerLink.FlagPaused));
		}

		private void SetPaused(bool paused)
		{
			if (paused == _paused)
			{
				return;
			}
			_paused = paused;
			if (paused)
			{
				MBCommon.PauseGameEngine();
			}
			else
			{
				MBCommon.UnPauseGameEngine();
			}
		}

		private void Detach()
		{
			SetPaused(false);
			_controlled = null;
			_leftMount = null;
			_silenced = null;
			_flash.ForgetAll();
			if (Current == this)
			{
				Current = null;
				SubModule.Publisher?.PublishIdle();
			}
		}

		private void Interact()
		{
			if (Mission.Mode == MissionMode.Battle)
			{
				return;
			}
			MissionMainAgentController controller = Mission.GetMissionBehavior<MissionMainAgentController>();
			if (controller?.InteractionComponent != null && controller.IsDisabled)
			{
				controller.InteractionComponent.FocusTick();
				controller.InteractionComponent.FocusStateCheckTick();
			}
		}

		private void UpdateStatusHud()
		{
			if (_statusView == null)
			{
				foreach (MissionBehavior behavior in Mission.MissionBehaviors)
				{
					if (behavior.GetType().Name == "MissionGauntletAgentStatus")
					{
						_statusView = behavior;
						break;
					}
				}
			}
			var status = _statusView?.GetType().GetProperty("DataSource")?.GetValue(_statusView) as MissionAgentStatusVM;
			if (status == null)
			{
				return;
			}

			bool hide = Active && _controlled != null;
			if (hide)
			{
				status.IsAgentStatusAvailable = false;
			}
			if (hide != _statusHidden)
			{
				_statusHidden = hide;
				status.TakenDamageController?.SetIsEnabled(!hide && BannerlordConfig.EnableDamageTakenVisuals);
				if (!hide)
				{
					status.IsAgentStatusAvailable = true;
				}
			}
		}

		private void RemoveLeftMount(Agent main)
		{
			if (_leftMount == null || main.HasMount)
			{
				return;
			}
			if (_leftMount.IsActive() && _leftMount.RiderAgent == null)
			{
				_leftMount.FadeOut(true, false);
			}
			_leftMount = null;
		}

		private void UpdateGround(float dt, Vec3 steve)
		{
			_groundTimer -= dt;
			_focusTimer -= dt;
			_sinceSample += dt;
			if (_sinceSample < MinSampleGap)
			{
				return;
			}

			if (_groundTimer <= 0f || steve.AsVec2.DistanceSquared(_groundCentre) > ResampleDistance * ResampleDistance
				|| MathF.Abs(steve.z - _groundCentreZ) > ResampleClimb)
			{
				_groundTimer = GroundInterval;
				_groundCentre = steve.AsVec2;
				_groundCentreZ = steve.z;
				SampleGround(steve);
			}
			else if (_focusTimer <= 0f && _player != null && _player.Has(PlayerLink.FlagFocus))
			{
				_focusTimer = GroundInterval;
				SampleGround(_player.Focus);
			}
			else
			{
				return;
			}
			_sinceSample = 0f;
		}

		private void SampleGround(Vec3 centre)
		{
			int size = HostPublisher.GroundSize;
			int originX = MathF.Floor(centre.x) - size / 2;
			int originY = MathF.Floor(centre.y) - size / 2;
			float probeZ = centre.z + 30f;
			if (_cells.Count > CellCacheLimit)
			{
				_cells.Clear();
			}
			for (int j = 0; j < size; j++)
			{
				for (int i = 0; i < size; i++)
				{
					long key = ((long)(originX + i) << 32) | (uint)(originY + j);
					if (!_cells.TryGetValue(key, out GroundCell cell) || !cell.Open || cell.Height > centre.z + HeadHeight)
					{
						var probe = new Vec3(originX + i + 0.5f, originY + j + 0.5f, probeZ);
						float height = FloorAt(probe, centre.z, out bool open);
						cell = new GroundCell
						{
							Height = height > -1000f ? height : float.NaN,
							Open = open,
							Inside = Mission.IsPositionInsideBoundaries(probe.AsVec2),
						};
						_cells[key] = cell;
					}
					_ground[j * size + i] = cell.Height;
					_inside[j * size + i] = cell.Inside;
				}
			}

			for (int j = 0; j < size; j++)
			{
				for (int i = 0; i < size; i++)
				{
					int k = j * size + i;
					if (!_inside[k] && !float.IsNaN(_ground[k]) && (Inside(i - 1, j) || Inside(i + 1, j) || Inside(i, j - 1) || Inside(i, j + 1)))
					{
						_ground[k] += BoundaryWall;
					}
				}
			}
			SubModule.Publisher?.SetGround(originX, originY, _ground);
		}

		private float FloorAt(Vec3 probe, float steveZ, out bool open)
		{
			Scene scene = Mission.Scene;
			float top = scene.GetGroundHeightAtPosition(probe);
			open = top <= -1000f || top <= steveZ + HeadHeight;
			if (open)
			{
				return top;
			}

			float floor = scene.GetGroundHeightAtPosition(new Vec3(probe.x, probe.y, steveZ + HeadHeight - 0.5f));
			if (floor <= -1000f || floor >= top - 0.1f)
			{
				return top;
			}

			var from = new Vec3(probe.x, probe.y, floor + 0.3f);
			var to = new Vec3(probe.x, probe.y, floor + Headroom);
			bool blocked = scene.RayCastForClosestEntityOrTerrain(from, to, out float _, 0.2f, BodyFlags.CommonCollisionExcludeFlags);
			return blocked ? top : floor;
		}

		private bool Inside(int i, int j)
		{
			int size = HostPublisher.GroundSize;
			return i >= 0 && j >= 0 && i < size && j < size && _inside[j * size + i];
		}

		internal static bool IsHostile(Agent agent, Agent main)
		{
			if (agent.IsMount)
			{
				return agent.RiderAgent != null && agent.RiderAgent.IsEnemyOf(main);
			}
			return agent.IsEnemyOf(main);
		}

		private void CollectAgents(Agent main)
		{
			_agents.Clear();
			foreach (Agent agent in Mission.Agents)
			{
				if (agent == main || agent == main.MountAgent || !agent.IsActive() || agent.Position.DistanceSquared(main.Position) > AgentRadius * AgentRadius)
				{
					continue;
				}

				uint flags = IsHostile(agent, main) ? AgentEntry.FlagEnemy : 0;
				Vec3 position = agent.Position;
				if (agent.IsMount)
				{
					flags |= AgentEntry.FlagMount;
				}
				else if (agent.HasMount)
				{
					flags |= AgentEntry.FlagRider;
					position = agent.GetEyeGlobalPosition() - new Vec3(0f, 0f, 1.62f);
				}

				Vec3 look = agent.LookDirection;
				_agents.Add(new AgentEntry
				{
					Index = agent.Index,
					Flags = flags,
					Position = position,
					Facing = new Vec2(look.x, look.y),
					Height = 1.8f,
				});
			}

			if (_agents.Count > HostPublisher.MaxAgents)
			{
				Vec3 centre = main.Position;
				_agents.Sort((a, b) => a.Position.DistanceSquared(centre).CompareTo(b.Position.DistanceSquared(centre)));
				_agents.RemoveRange(HostPublisher.MaxAgents, _agents.Count - HostPublisher.MaxAgents);
			}
		}

		private struct GroundCell
		{
			public float Height;
			public bool Open;
			public bool Inside;
		}
	}
}
