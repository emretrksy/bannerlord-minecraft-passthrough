using TaleWorlds.Core;
using TaleWorlds.Library;
using TaleWorlds.MountAndBlade;

namespace MinecraftPassthrough
{
	internal struct LootDrop
	{
		public const byte WeaponNone = 0, WeaponSword = 1, WeaponAxe = 2, WeaponMace = 3, WeaponSpear = 4, WeaponBow = 5, WeaponCrossbow = 6, WeaponThrown = 7;
		public const byte FlagShield = 1, FlagAmmo = 2, FlagMounted = 4;

		public Vec3 Position;
		public byte Tier;
		public byte Weapon;
		public byte Flags;

		public static LootDrop Describe(Agent agent)
		{
			var drop = new LootDrop { Position = agent.Position, Tier = TierOf(agent.GetBaseArmorEffectivenessForBodyPart(BoneBodyPartType.Chest)) };
			if (agent.HasMount)
			{
				drop.Flags |= FlagMounted;
			}

			for (EquipmentIndex slot = EquipmentIndex.WeaponItemBeginSlot; slot < EquipmentIndex.NumAllWeaponSlots; slot++)
			{
				MissionWeapon weapon = agent.Equipment[slot];
				if (weapon.IsEmpty)
				{
					continue;
				}
				switch (weapon.CurrentUsageItem.WeaponClass)
				{
					case WeaponClass.SmallShield:
					case WeaponClass.LargeShield:
						drop.Flags |= FlagShield;
						break;
					case WeaponClass.Arrow:
					case WeaponClass.Bolt:
						drop.Flags |= FlagAmmo;
						break;
					default:
						if (drop.Weapon == WeaponNone)
						{
							drop.Weapon = Classify(weapon.CurrentUsageItem.WeaponClass);
						}
						break;
				}
			}
			return drop;
		}

		private static byte TierOf(float chestArmour)
		{
			if (chestArmour < 12f) return 0;
			if (chestArmour < 25f) return 1;
			if (chestArmour < 38f) return 2;
			if (chestArmour < 50f) return 3;
			return 4;
		}

		private static byte Classify(WeaponClass weapon)
		{
			switch (weapon)
			{
				case WeaponClass.Dagger:
				case WeaponClass.OneHandedSword:
				case WeaponClass.TwoHandedSword:
					return WeaponSword;
				case WeaponClass.OneHandedAxe:
				case WeaponClass.TwoHandedAxe:
				case WeaponClass.Pick:
					return WeaponAxe;
				case WeaponClass.Mace:
				case WeaponClass.TwoHandedMace:
					return WeaponMace;
				case WeaponClass.OneHandedPolearm:
				case WeaponClass.TwoHandedPolearm:
				case WeaponClass.LowGripPolearm:
					return WeaponSpear;
				case WeaponClass.Bow:
					return WeaponBow;
				case WeaponClass.Crossbow:
					return WeaponCrossbow;
				case WeaponClass.Javelin:
				case WeaponClass.ThrowingAxe:
				case WeaponClass.ThrowingKnife:
					return WeaponThrown;
				default:
					return WeaponNone;
			}
		}
	}
}
