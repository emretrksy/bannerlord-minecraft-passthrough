using TaleWorlds.CampaignSystem;
using TaleWorlds.Core;

namespace MinecraftPassthrough
{
	internal static class SkillXp
	{
		private const int Sword = 1, Axe = 2, Mace = 3, Spear = 4, Bow = 5, Crossbow = 6, Thrown = 7;
		private const int Mounted = 16;
		public const int KnockBack = 32;
		private const float PerDamage = 1f;
		private const float RidingShare = 0.3f;

		public static void Award(int kind, int damage)
		{
			Hero hero = Hero.MainHero;
			if (hero == null || Campaign.Current == null || SteveInventoryBehavior.Current == null || damage <= 0)
			{
				return;
			}

			SkillObject skill = Skill(kind & ~Mounted & ~KnockBack);
			if (skill != null)
			{
				hero.AddSkillXp(skill, damage * PerDamage);
			}
			if ((kind & Mounted) != 0)
			{
				hero.AddSkillXp(DefaultSkills.Riding, damage * PerDamage * RidingShare);
			}
		}

		private static SkillObject Skill(int kind)
		{
			switch (kind)
			{
				case Sword:
					return DefaultSkills.OneHanded;
				case Axe:
				case Mace:
					return DefaultSkills.TwoHanded;
				case Spear:
					return DefaultSkills.Polearm;
				case Bow:
					return DefaultSkills.Bow;
				case Crossbow:
					return DefaultSkills.Crossbow;
				case Thrown:
					return DefaultSkills.Throwing;
				default:
					return DefaultSkills.Athletics;
			}
		}
	}
}
