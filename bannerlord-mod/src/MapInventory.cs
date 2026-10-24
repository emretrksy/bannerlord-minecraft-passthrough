extern alias vectors;

using System;
using TaleWorlds.CampaignSystem;
using TaleWorlds.CampaignSystem.GameState;
using TaleWorlds.Core;
using TaleWorlds.InputSystem;
using TaleWorlds.Library;
using TaleWorlds.ScreenSystem;

namespace MinecraftPassthrough
{
	internal static class MapInventory
	{
		private const float CloseGrace = 1f;
		private const float OpenTimeout = 20f;

		private static bool _open;
		private static uint _missionId;
		private static string _offeredGoods;
		private static float _elapsed;
		private static float _closing = -1f;
		private static bool _sawScreen;
		private static BlockingLayer _layer;
		private static readonly InputCapture Input = new InputCapture();

		public static void Tick(float dt)
		{
			if (!_open)
			{
				if (CanOpen() && TaleWorlds.InputSystem.Input.IsKeyPressed(InputKey.E))
				{
					Open();
				}
				return;
			}

			_elapsed += dt;
			SteveInventoryBehavior steve = SteveInventoryBehavior.Current;
			InventoryReport report = SubModule.Inventory?.Poll(_missionId);
			if (report != null && steve != null)
			{
				steve.Inventory = report.Inventory;
				steve.Delivered(_offeredGoods);
				_offeredGoods = "";
			}

			PlayerState player = SubModule.Player?.Poll(dt, _missionId);
			bool screen = player != null && player.Has(PlayerLink.FlagCursor);
			_sawScreen |= screen;
			if (TaleWorlds.InputSystem.Input.IsKeyPressed(InputKey.Escape) && screen)
			{
				Input.PressCloseScreen();
			}

			if (_closing < 0f && ((_sawScreen && !screen) || (!_sawScreen && _elapsed > OpenTimeout) || !CanStayOpen()))
			{
				if (!_sawScreen)
				{
					InformationManager.DisplayMessage(new InformationMessage(Text.Get("blmc_no_answer", "Minecraft did not answer: make sure it is open."), Colors.Red));
				}
				_closing = 0f;
			}
			if (_closing >= 0f && (_closing += dt) > CloseGrace)
			{
				Close();
				return;
			}

			SubModule.Publisher?.PublishMenu(_missionId, Input.Capture(true, true));
		}

		private static bool CanOpen()
		{
			return SubModule.Publisher != null && SubModule.Inventory != null && SteveInventoryBehavior.Current != null && CanStayOpen();
		}

		private static bool CanStayOpen()
		{
			return GameStateManager.Current?.ActiveState is MapState && Campaign.Current != null;
		}

		private static void Open()
		{
			SteveInventoryBehavior steve = SteveInventoryBehavior.Current;
			_open = true;
			_elapsed = 0f;
			_closing = -1f;
			_sawScreen = false;
			_missionId = MissionIds.Next();
			_offeredGoods = steve.Pending;
			SubModule.Inventory.Offer(_missionId, InventoryLink.ModeCampaign, steve.Inventory, _offeredGoods, HeroHealth());

			Campaign.Current.TimeControlMode = CampaignTimeControlMode.Stop;
			_layer = new BlockingLayer();
			ScreenManager.TopScreen?.AddLayer(_layer);
			ScreenManager.TrySetFocus(_layer);
		}

		private static void Close()
		{
			_open = false;
			SubModule.Publisher?.PublishIdle();
			if (_layer != null)
			{
				ScreenManager.TryLoseFocus(_layer);
				ScreenManager.TopScreen?.RemoveLayer(_layer);
				_layer = null;
			}
		}

		public static float HeroHealth()
		{
			Hero hero = Hero.MainHero;
			return hero == null || hero.MaxHitPoints <= 0 ? 1f : Math.Max(0f, Math.Min(1f, (float)hero.HitPoints / hero.MaxHitPoints));
		}

		private sealed class BlockingLayer : ScreenLayer
		{
			public BlockingLayer() : base("MinecraftInventory", 10000)
			{
				InputRestrictions.SetInputRestrictions(true, InputUsageMask.All);
			}

			public override bool HitTest(vectors::System.Numerics.Vector2 position)
			{
				return true;
			}

			public override bool HitTest()
			{
				return true;
			}
		}
	}

	internal static class MissionIds
	{
		private static uint _last = (uint)Environment.TickCount;

		public static uint Next()
		{
			return ++_last;
		}
	}
}
