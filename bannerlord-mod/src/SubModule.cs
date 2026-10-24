using System;
using HarmonyLib;
using TaleWorlds.CampaignSystem;
using TaleWorlds.Core;
using TaleWorlds.Engine;
using TaleWorlds.Library;
using TaleWorlds.MountAndBlade;
using TaleWorlds.MountAndBlade.View.Screens;

namespace MinecraftPassthrough
{
	public sealed class SubModule : MBSubModuleBase
	{
		internal static HostPublisher Publisher { get; private set; }

		internal static EventReader Events { get; private set; }

		internal static PlayerLink Player { get; private set; }

		internal static InventoryLink Inventory { get; private set; }

		protected override void OnSubModuleLoad()
		{
			try
			{
				Publisher = new HostPublisher();
				Events = new EventReader();
				Player = new PlayerLink();
				Inventory = new InventoryLink();
				_linked = true;
			}
			catch (Exception e)
			{
				Debug.Print("[MinecraftPassthrough] could not open shared memory: " + e);
			}
		}

		private static bool _creditShown;
		private static bool _linked;
		private static bool _patched;

		protected override void OnBeforeInitialModuleScreenSetAsRoot()
		{
			if (!_patched && _linked)
			{
				_patched = true;
				new Harmony("minecraftpassthrough").PatchAll(typeof(SubModule).Assembly);
			}
			BannerlordConfig.StopGameOnFocusLost = false;
			if (!_creditShown)
			{
				_creditShown = true;
				InformationManager.DisplayMessage(new InformationMessage("Bannerlord × Minecraft by Emre Turksoy; Psicodart Games — discord.gg/R4qStgw9bk", Color.FromUint(0xFF8FD16F)));
			}
		}

		protected override void OnSubModuleUnloaded()
		{
			Publisher?.Dispose();
			Publisher = null;
			Events?.Dispose();
			Events = null;
			Player?.Dispose();
			Player = null;
			Inventory?.Dispose();
			Inventory = null;
		}

		protected override void OnGameStart(Game game, IGameStarter gameStarter)
		{
			if (game.GameType is Campaign && gameStarter is CampaignGameStarter campaign)
			{
				campaign.AddBehavior(new SteveInventoryBehavior());
			}
		}

		protected override void OnApplicationTick(float dt)
		{
			DevAutostart.Tick(dt);
			Guard.Run("MapInventory", () => MapInventory.Tick(dt));
			Guard.Run("TickPause", () => PassthroughMissionLogic.Current?.TickPause());
		}

		public override void OnMissionBehaviorInitialize(Mission mission)
		{
			if (Publisher != null)
			{
				mission.AddMissionBehavior(new PassthroughMissionLogic());
				mission.AddMissionBehavior(new PassthroughEscapeView());
			}
		}
	}

	[HarmonyPatch(typeof(MissionScreen), "UpdateCamera")]
	internal static class MissionScreenUpdateCameraPatch
	{
		private static void Postfix(MissionScreen __instance)
		{
			PassthroughMissionLogic.Current?.Publish(__instance);
		}
	}

	[HarmonyPatch(typeof(MissionScreen), "OnFrameTick")]
	internal static class MissionScreenOnFrameTickPatch
	{
		private static void Postfix(MissionScreen __instance)
		{
			if (PassthroughMissionLogic.Current?.CursorMode == true)
			{
				__instance.SceneLayer?.InputRestrictions.SetMouseVisibility(true);
			}
		}
	}

	[HarmonyPatch(typeof(Agent), "HandleBlow")]
	internal static class AgentHandleBlowPatch
	{
		private static void Prefix(Agent __instance, ref Blow b)
		{
			if (PassthroughMissionLogic.Current?.IsStandIn(__instance) == true)
			{
				b.BlowFlag |= BlowFlags.NoSound;
			}
		}
	}

	[HarmonyPatch(typeof(Agent), nameof(Agent.TeleportToPosition))]
	internal static class AgentTeleportToPositionPatch
	{
		private static void Postfix(Agent __instance, Vec3 position)
		{
			Guard.Run("TeleportToPosition", () => PassthroughMissionLogic.Current?.OnTeleported(__instance, position));
		}
	}

	[HarmonyPatch(typeof(Agent), nameof(Agent.MakeVoice))]
	internal static class AgentMakeVoicePatch
	{
		private static bool Prefix(Agent __instance)
		{
			return PassthroughMissionLogic.Current?.IsStandIn(__instance) != true;
		}
	}

	[HarmonyPatch(typeof(SceneView), nameof(SceneView.SetCamera))]
	internal static class SceneViewSetCameraPatch
	{
		private static void Prefix(Camera camera)
		{
			PassthroughMissionLogic.Current?.OverrideCamera(camera);
		}
	}
}
