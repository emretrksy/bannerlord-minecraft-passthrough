using System.Collections.Generic;
using System.Linq;
using HarmonyLib;
using TaleWorlds.CampaignSystem;
using TaleWorlds.CampaignSystem.GameMenus;
using TaleWorlds.CampaignSystem.Settlements;
using TaleWorlds.Core;
using TaleWorlds.Core.ViewModelCollection.Information;
using TaleWorlds.Library;
using TaleWorlds.Localization;
using TaleWorlds.MountAndBlade.ViewModelCollection.Inquiries;

namespace MinecraftPassthrough
{
	internal static class MinecraftShop
	{
		private const float RichTown = 5000f;

		private sealed class Ware
		{
			public readonly string Id;
			public readonly int Count;
			public readonly int Price;
			private readonly string _english;
			private readonly string _key;

			public Ware(string id, int count, int price, string english, string key = null)
			{
				Id = id;
				Count = count;
				Price = price;
				_english = english;
				_key = key ?? id;
			}

			public string Name => Text.Get("blmc_ware_" + _key, _english);
		}

		private static readonly Ware[] Basics =
		{
			new Ware("bread", 16, 40, "Bread"),
			new Ware("cooked_beef", 16, 120, "Steak"),
			new Ware("arrow", 32, 80, "Arrows"),
			new Ware("shield", 1, 150, "Shield"),
			new Ware("bow", 1, 200, "Bow"),
			new Ware("crossbow", 1, 400, "Crossbow"),
			new Ware("cobblestone", 64, 30, "Cobblestone"),
			new Ware("snowball", 16, 40, "Snowballs"),
			new Ware("egg", 16, 40, "Eggs"),
			new Ware("fire_charge", 8, 500, "Fire charges"),
			new Ware("stone_sword", 1, 60, "Stone sword"),
			new Ware("stone_axe", 1, 60, "Stone axe"),
			new Ware("stone_spear", 1, 70, "Stone spear"),
			new Ware("iron_sword", 1, 450, "Iron sword"),
			new Ware("iron_axe", 1, 450, "Iron axe"),
			new Ware("iron_spear", 1, 500, "Iron spear"),
			new Ware("leather_helmet", 1, 60, "Leather cap"),
			new Ware("leather_chestplate", 1, 120, "Leather tunic"),
			new Ware("leather_leggings", 1, 100, "Leather pants"),
			new Ware("leather_boots", 1, 60, "Leather boots"),
			new Ware("chainmail_helmet", 1, 350, "Chainmail helmet"),
			new Ware("chainmail_chestplate", 1, 700, "Chainmail chestplate"),
			new Ware("chainmail_leggings", 1, 600, "Chainmail leggings"),
			new Ware("chainmail_boots", 1, 350, "Chainmail boots"),
			new Ware("iron_helmet", 1, 700, "Iron helmet"),
			new Ware("iron_chestplate", 1, 1400, "Iron chestplate"),
			new Ware("iron_leggings", 1, 1200, "Iron leggings"),
			new Ware("iron_boots", 1, 700, "Iron boots"),
		};

		private static readonly Dictionary<string, Ware[]> Specialities = new Dictionary<string, Ware[]>
		{
			["empire"] = new[]
			{
				new Ware("diamond_sword", 1, 4000, "Diamond sword"),
				new Ware("diamond_helmet", 1, 5000, "Diamond helmet"),
				new Ware("diamond_chestplate", 1, 9000, "Diamond chestplate"),
				new Ware("diamond_leggings", 1, 8000, "Diamond leggings"),
				new Ware("diamond_boots", 1, 5000, "Diamond boots"),
				new Ware("golden_apple", 2, 1500, "Golden apples"),
			},
			["vlandia"] = new[]
			{
				new Ware("crossbow", 1, 250, "Vlandian crossbow", "vlandian_crossbow"),
				new Ware("spectral_arrow", 16, 300, "Spectral arrows"),
				new Ware("iron_chestplate", 1, 1100, "Vlandian iron chestplate", "vlandian_chestplate"),
			},
			["sturgia"] = new[]
			{
				new Ware("tnt", 8, 1200, "TNT"),
				new Ware("flint_and_steel", 1, 200, "Flint and steel"),
				new Ware("diamond_axe", 1, 4000, "Diamond axe"),
			},
			["battania"] = new[]
			{
				new Ware("arrow", 64, 120, "Battanian arrows", "battanian_arrows"),
				new Ware("ender_pearl", 4, 1500, "Ender pearls"),
			},
			["khuzait"] = new[]
			{
				new Ware("firework_rocket", 16, 400, "Firework rockets"),
				new Ware("diamond_spear", 1, 4500, "Diamond spear"),
			},
			["aserai"] = new[]
			{
				new Ware("ender_pearl", 4, 1200, "Ender pearls"),
				new Ware("wind_charge", 8, 900, "Wind charges"),
				new Ware("creeper_spawn_egg", 2, 2500, "Creeper spawn eggs"),
			},
		};

		private static readonly Ware[] Rare =
		{
			new Ware("trident", 1, 8000, "Trident"),
			new Ware("mace", 1, 12000, "Mace"),
			new Ware("totem_of_undying", 1, 15000, "Totem of Undying"),
			new Ware("netherite_sword", 1, 15000, "Netherite sword"),
			new Ware("elytra", 1, 25000, "Elytra"),
		};

		public static void AddTo(CampaignGameStarter starter)
		{
			starter.AddGameMenuOption("town", "blmc_minecraft_trader", "{=blmc_trader_option}Minecraft trader (Steve's goods)",
				args =>
				{
					args.optionLeaveType = GameMenuOption.LeaveType.Trade;
					return Settlement.CurrentSettlement?.IsTown == true;
				},
				args => Open(), false, 4);
			starter.AddGameMenuOption("town", "blmc_minecraft_smith", "{=blmc_smith_option}Minecraft smith (mend Steve's goods)",
				args =>
				{
					args.optionLeaveType = GameMenuOption.LeaveType.Craft;
					return Settlement.CurrentSettlement?.IsTown == true;
				},
				args => Repair(), false, 5);
		}

		private static readonly System.Text.RegularExpressions.Regex Damage = new System.Text.RegularExpressions.Regex("\"minecraft:damage\":(\\d+)");

		private static void Repair()
		{
			SteveInventoryBehavior steve = SteveInventoryBehavior.Current;
			if (steve == null)
			{
				return;
			}
			if (steve.Pending.Contains(RepairOrder))
			{
				InformationManager.DisplayMessage(new InformationMessage(Text.Get("blmc_smith_busy", "Steve's goods are already being mended: they are ready in the next mission.")));
				return;
			}

			int wear = 0;
			foreach (System.Text.RegularExpressions.Match match in Damage.Matches(steve.Inventory))
			{
				wear += int.Parse(match.Groups[1].Value);
			}
			if (wear == 0)
			{
				InformationManager.DisplayMessage(new InformationMessage(Text.Get("blmc_smith_nothing", "Steve has nothing to mend.")));
				return;
			}

			int price = System.Math.Max(20, wear);
			int gold = Hero.MainHero.Gold;
			InformationManager.ShowInquiry(new InquiryData(Text.Get("blmc_smith_title", "Minecraft smith"),
				Text.Get("blmc_smith_text", "All of Steve's worn goods are mended for {PRICE} denars (you have {GOLD}). The mending is ready in the next mission.",
					"PRICE", price, "GOLD", gold),
				true, true, Text.Get("blmc_smith_mend", "Mend"), Text.Get("blmc_cancel", "Cancel"),
				() =>
				{
					if (Hero.MainHero.Gold < price)
					{
						InformationManager.DisplayMessage(new InformationMessage(Text.Get("blmc_smith_need", "Mending takes {PRICE} denars.", "PRICE", price), Colors.Red));
						return;
					}
					Hero.MainHero.ChangeHeroGold(-price);
					steve.AddPending(RepairOrder, 0);
				}, null));
		}

		private static MultiSelectionInquiryData _shopWindow;
		private static string _shopText;
		private static readonly AccessTools.FieldRef<MultiSelectionQueryPopUpVM, MultiSelectionInquiryData> WindowData =
			AccessTools.FieldRefAccess<MultiSelectionQueryPopUpVM, MultiSelectionInquiryData>("_data");

		internal static void ShowTotal(MultiSelectionQueryPopUpVM window)
		{
			if (_shopWindow == null || WindowData(window) != _shopWindow)
			{
				return;
			}

			int total = window.InquiryElements.Where(e => e.IsSelected).Sum(e => ((Ware)e.InquiryElement.Identifier).Price);
			int gold = Hero.MainHero?.Gold ?? 0;
			window.ButtonOkLabel = total > 0 ? Text.Get("blmc_buy_total", "Buy ({TOTAL} denars)", "TOTAL", total) : Text.Get("blmc_buy", "Buy");
			window.PopUpLabel = _shopText + "\n\n" + Text.Get("blmc_shop_selected", "Selected: {TOTAL} denars", "TOTAL", total)
				+ (total > gold ? " — " + Text.Get("blmc_shop_short", "{SHORT} denars short", "SHORT", total - gold) : "");
			if (total > gold)
			{
				window.IsButtonOkEnabled = false;
			}
		}

		private const string RepairOrder = "#repair";

		private static void Open()
		{
			Settlement town = Settlement.CurrentSettlement;
			if (town?.Town == null)
			{
				return;
			}

			var wares = new List<Ware>(Basics);
			if (town.Culture != null && Specialities.TryGetValue(town.Culture.StringId, out Ware[] own))
			{
				wares.AddRange(own);
			}
			if (town.Town.Prosperity >= RichTown)
			{
				wares.AddRange(Rare);
			}

			int gold = Hero.MainHero.Gold;
			string poor = Text.Get("blmc_not_enough", "Not enough denars");
			var elements = wares.Select(w => new InquiryElement(w,
				Text.Get("blmc_ware_line", "{NAME} ×{COUNT} — {PRICE} denars", "NAME", w.Name, "COUNT", w.Count, "PRICE", w.Price),
				null, w.Price <= gold, w.Price <= gold ? null : poor)).ToList();
			string description = Text.Get("blmc_shop_text", "You have {GOLD} denars. What you buy goes into Steve's inventory in the next battle.", "GOLD", gold) +
				(town.Town.Prosperity >= RichTown ? "" : " " + Text.Get("blmc_shop_rare", "Rare goods are sold only in rich towns."));

			_shopText = description;
			_shopWindow = new MultiSelectionInquiryData(
				Text.Get("blmc_shop_title", "Minecraft trader — {TOWN}", "TOWN", town.Name), description, elements, true, 1, elements.Count,
				Text.Get("blmc_buy", "Buy"), Text.Get("blmc_cancel", "Cancel"),
				Buy, null);
			MBInformationManager.ShowMultiSelectionInquiry(_shopWindow);
		}

		private static void Buy(List<InquiryElement> chosen)
		{
			_shopWindow = null;
			SteveInventoryBehavior steve = SteveInventoryBehavior.Current;
			if (steve == null)
			{
				return;
			}

			int total = chosen.Sum(e => ((Ware)e.Identifier).Price);
			if (total > Hero.MainHero.Gold)
			{
				InformationManager.DisplayMessage(new InformationMessage(Text.Get("blmc_shop_need", "These take {TOTAL} denars.", "TOTAL", total), Colors.Red));
				return;
			}

			Hero.MainHero.ChangeHeroGold(-total);
			foreach (InquiryElement element in chosen)
			{
				var ware = (Ware)element.Identifier;
				steve.AddPending("minecraft:" + ware.Id, ware.Count);
			}
			MBInformationManager.AddQuickInformation(new TextObject(Text.Get("blmc_shop_paid",
				"You paid {TOTAL} denars for {COUNT} goods. They are in Steve's inventory in the next battle.", "TOTAL", total, "COUNT", chosen.Count)));
		}
	}

	[HarmonyPatch(typeof(MultiSelectionQueryPopUpVM), "OnInquiryElementSelected")]
	internal static class ShopTotalPatch
	{
		private static void Postfix(MultiSelectionQueryPopUpVM __instance)
		{
			Guard.Run("shop total", () => MinecraftShop.ShowTotal(__instance));
		}
	}
}
