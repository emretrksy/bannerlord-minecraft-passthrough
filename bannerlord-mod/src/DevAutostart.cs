using System;
using System.Reflection;
using TaleWorlds.Core;
using TaleWorlds.Library;
using TaleWorlds.MountAndBlade;
using TaleWorlds.ScreenSystem;

namespace MinecraftPassthrough
{
	internal static class DevAutostart
	{
		private static readonly bool Enabled = Environment.GetEnvironmentVariable("BLMC_AUTOSTART") == "1";
		private static int _step;
		private static float _wait;
		private static float _logTimer;

		public static void Tick(float dt)
		{
			if (!Enabled || _step > 3)
			{
				return;
			}

			_wait += dt;
			_logTimer += dt;
			if (_logTimer > 5f)
			{
				_logTimer = 0f;
				Debug.Print($"[MinecraftPassthrough] autostart step {_step}, state {GameStateManager.Current?.ActiveState?.GetType().Name}, screen {ScreenManager.TopScreen?.GetType().Name}");
			}

			switch (_step)
			{
				case 0:
					if (GameStateManager.Current?.ActiveState is InitialState && _wait > 3f)
					{
						Type manager = FindType("TaleWorlds.MountAndBlade.CustomBattle.CustomGameManager");
						Debug.Print("[MinecraftPassthrough] autostart: custom game manager " + (manager != null ? "found" : "missing"));
						if (manager != null)
						{
							MBGameManager.StartNewGame((MBGameManager)Activator.CreateInstance(manager));
						}
						Advance();
					}
					break;
				case 1:
					ScreenBase screen = ScreenManager.TopScreen;
					if (screen != null && screen.GetType().Name == "CustomBattleScreen" && _wait > 3f)
					{
						object viewModel = screen.GetType().GetField("_dataSource", BindingFlags.Instance | BindingFlags.NonPublic)?.GetValue(screen);
						viewModel?.GetType().GetMethod("ExecuteStart")?.Invoke(viewModel, null);
						Advance();
					}
					break;
				case 2:
					if (Mission.Current != null && Mission.Current.Mode == MissionMode.Deployment && _wait > 3f)
					{
						Mission.Current.GetMissionBehavior<DeploymentHandler>()?.FinishDeployment();
						Advance();
					}
					else if (Mission.Current != null && Mission.Current.Mode == MissionMode.Battle && _wait > 3f)
					{
						Advance();
					}
					break;
				case 3:
					PlayScript();
					break;
				default:
					Advance();
					break;
			}
		}

		private const uint Forward = 1u << InputFrame.Forward;
		private const uint Jump = 1u << InputFrame.Jump;
		private const uint Sprint = 1u << InputFrame.Sprint;
		private const uint Attack = 1u << InputFrame.Attack;
		private const uint Use = 1u << InputFrame.Use;
		private const uint Perspective = 1u << InputFrame.Perspective;

		private static uint Hotbar(int slot) => 1u << (InputFrame.Hotbar1 + slot - 1);

		private static readonly (float duration, uint keys, float lookX, float lookY, string label)[] Script =
		{
			(8f, 0, 0, 0, "wait for Minecraft to spawn Steve"),
			(1.5f, Forward, 0, 0, "walk"),
			(1.5f, Forward | Sprint, 0, 0, "sprint"),
			(0.8f, Forward | Sprint | Jump, 0, 0, "sprint-jump"),
			(0.3f, Attack, 0, 0, "swing"), (0.6f, 0, 0, 0, ""),
			(0.3f, Attack, 0, 0, "swing"), (0.6f, 0, 0, 0, ""),
			(0.5f, 0, 0, 300, "look down"),
			(0.2f, Hotbar(7), 0, 0, "creeper egg"), (0.2f, 0, 0, 0, ""),
			(0.2f, Use, 0, 0, "spawn creeper"), (0.5f, 0, 0, -300, "look up"),
			(0.2f, Hotbar(2), 0, 0, "bow"), (0.2f, 0, 0, 0, ""),
			(1.3f, Use, 0, 0, "draw bow"), (0.5f, 0, 0, 0, "loose arrow"),
			(0.2f, Perspective, 0, 0, "third person"), (3f, 0, 400, 0, "turn around"),
			(0.2f, Hotbar(5), 0, 0, "ender pearl"), (0.2f, 0, 0, -200, ""),
			(0.2f, Use, 0, 0, "throw pearl"), (2f, 0, 0, 0, ""),
			(0.2f, Hotbar(6), 0, 0, "fireworks"), (0.2f, 0, 0, 0, ""),
			(0.4f, Forward | Jump, 0, 0, "jump"), (0.2f, Forward, 0, 0, ""),
			(0.2f, Forward | Jump, 0, 0, "open elytra"), (0.2f, Forward, 0, 0, ""),
			(0.2f, Forward | Use, 0, 0, "rocket"), (1.5f, Forward, 0, -40, "fly"),
			(0.2f, Forward | Use, 0, 0, "rocket"), (3f, Forward, 0, 0, "fly"), (3f, 0, 0, 0, "done"),
		};

		private static int _scriptIndex;
		private static int _announced = -1;

		private static void PlayScript()
		{
			PassthroughMissionLogic logic = PassthroughMissionLogic.Current;
			if (logic == null)
			{
				return;
			}

			var (duration, keys, lookX, lookY, label) = Script[_scriptIndex];
			if (_announced != _scriptIndex && label.Length > 0)
			{
				Debug.Print("[MinecraftPassthrough] script: " + label);
			}
			_announced = _scriptIndex;
			logic.Input.ScriptedDown = keys;
			logic.Input.ScriptedLook(lookX * 0.016f, lookY * 0.016f);
			if (_wait >= duration)
			{
				_wait = 0f;
				if (++_scriptIndex >= Script.Length)
				{
					logic.Input.ScriptedDown = 0;
					Advance();
				}
			}
		}

		private static Type FindType(string fullName)
		{
			foreach (Assembly assembly in AppDomain.CurrentDomain.GetAssemblies())
			{
				Type type = assembly.GetType(fullName, false);
				if (type != null)
				{
					return type;
				}
			}
			return null;
		}

		private static void Advance()
		{
			_step++;
			_wait = 0f;
		}
	}
}
