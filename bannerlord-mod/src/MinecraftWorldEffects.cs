using System.Collections.Generic;
using TaleWorlds.Core;
using TaleWorlds.Engine;
using TaleWorlds.Library;
using TaleWorlds.MountAndBlade;

namespace MinecraftPassthrough
{
	internal sealed class MinecraftWorldEffects
	{
		private const float DamagePerPower = 30f;
		private const float KnockDownDamage = 60f;
		private const float HalvingArmour = 50f;

		private readonly Mission _mission;

		public MinecraftWorldEffects(Mission mission)
		{
			_mission = mission;
		}

		public void Handle(MinecraftEvent e)
		{
			Guard.Run("event " + e.Type, () => HandleCore(e));
		}

		private void HandleCore(MinecraftEvent e)
		{
			switch (e.Type)
			{
				case EventReader.Explosion:
					Explode(e.Position, e.Radius, e.Power, e.Arg == 1);
					break;
				case EventReader.AgentHit:
					Agent target = _mission.FindAgentWithIndex(e.Arg);
					if (target != null && target.IsActive())
					{
						float armour = target.GetBaseArmorEffectivenessForBodyPart(BoneBodyPartType.Chest);
						int damage = MathF.Round(e.Power * HalvingArmour / (HalvingArmour + MathF.Max(0f, armour)));
						Strike(target, damage, e.Position, ((int)e.Radius & SkillXp.KnockBack) != 0);
						SkillXp.Award((int)e.Radius, damage);
						InformationManager.DisplayMessage(new InformationMessage($"Steve → {target.Name}: {damage}", Colors.Green));
					}
					break;
				case EventReader.PlayerDied:
					KillMainAgent();
					break;
			}
		}

		private void Explode(Vec3 centre, float radius, float power, bool enemiesOnly)
		{
			Agent main = _mission.MainAgent;
			var victims = new List<Agent>();
			foreach (Agent agent in _mission.Agents)
			{
				if (enemiesOnly && (main == null || !PassthroughMissionLogic.IsHostile(agent, main)))
				{
					continue;
				}
				if (agent.IsActive() && agent != main && agent.Position.Distance(centre) <= radius)
				{
					victims.Add(agent);
				}
			}

			foreach (Agent agent in victims)
			{
				float falloff = 1f - agent.Position.Distance(centre) / radius;
				Vec3 push = agent.Position - centre;
				push.z = 0.5f;
				Strike(agent, MathF.Round(DamagePerPower * power * falloff), push);
			}
		}

		private void Strike(Agent agent, int damage, Vec3 direction, bool knockBack = false)
		{
			if (damage <= 0)
			{
				return;
			}

			var blow = new Blow(_mission.MainAgent?.Index ?? -1)
			{
				DamageCalculated = true,
				BaseMagnitude = damage,
				InflictedDamage = damage,
				DamageType = DamageTypes.Blunt,
				BoneIndex = 0,
				GlobalPosition = agent.Position,
			};
			blow.Direction = direction.LengthSquared > 1E-06f ? direction.NormalizedCopy() : Vec3.Up;
			blow.SwingDirection = blow.Direction;
			blow.WeaponRecord.FillAsMeleeBlow(null, null, -1, 0);
			blow.WeaponRecord.WeaponFlags |= WeaponFlags.AffectsArea;
			if (damage >= KnockDownDamage)
			{
				blow.BlowFlag |= BlowFlags.KnockDown;
			}
			else if (knockBack)
			{
				blow.BlowFlag |= BlowFlags.KnockBack;
			}

			AttackCollisionData collision = AttackCollisionData.GetAttackCollisionDataForDebugPurpose(
				false, false, false, true, false, false, false, false, false, false, false, false,
				CombatCollisionResult.StrikeAgent, -1, 0, 2, blow.BoneIndex, BoneBodyPartType.Abdomen, -1,
				Agent.UsageDirection.AttackLeft, -1, CombatHitResultFlags.NormalHit, 0.5f, 1f, 0f, 0f, 0f, 0f, 0f, 0f,
				Vec3.Up, blow.Direction, blow.GlobalPosition, Vec3.Zero, Vec3.Zero, agent.Velocity, Vec3.Up);
			agent.RegisterBlow(blow, in collision);
		}

		private void KillMainAgent()
		{
			Agent main = _mission.MainAgent;
			if (main != null && main.IsActive())
			{
				PassthroughMissionLogic.Current?.SteveDied();
				main.SetMortalityState(Agent.MortalityState.Mortal);
				Strike(main, MathF.Round(main.Health) + 100, Vec3.Up);
			}
		}
	}
}
