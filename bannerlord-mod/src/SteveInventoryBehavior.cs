using TaleWorlds.CampaignSystem;

namespace MinecraftPassthrough
{
	internal sealed class SteveInventoryBehavior : CampaignBehaviorBase
	{
		private string _inventory = "";
		private string _pending = "";

		public static SteveInventoryBehavior Current => Campaign.Current?.GetCampaignBehavior<SteveInventoryBehavior>();

		public string Inventory
		{
			get => _inventory;
			set => _inventory = value ?? "";
		}

		public string Pending => _pending;

		public void AddPending(string item, int count)
		{
			_pending += item + " " + count + "\n";
		}

		public void Delivered(string offered)
		{
			if (!string.IsNullOrEmpty(offered) && _pending.StartsWith(offered))
			{
				_pending = _pending.Substring(offered.Length);
			}
		}

		public const string CapturedOrder = "#captured";

		public override void RegisterEvents()
		{
			CampaignEvents.OnSessionLaunchedEvent.AddNonSerializedListener(this, MinecraftShop.AddTo);
			CampaignEvents.HeroPrisonerTaken.AddNonSerializedListener(this, (captor, prisoner) =>
			{
				if (prisoner == Hero.MainHero && !string.IsNullOrEmpty(_inventory))
				{
					AddPending(CapturedOrder, 0);
				}
			});
		}

		public override void SyncData(IDataStore dataStore)
		{
			dataStore.SyncData("blmc_steve_inventory", ref _inventory);
			dataStore.SyncData("blmc_steve_pending", ref _pending);
			_inventory = _inventory ?? "";
			_pending = _pending ?? "";
		}
	}
}
