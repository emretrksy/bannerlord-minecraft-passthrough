package blpassthrough;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class LootTable {
	private static final Item[] SWORDS = { Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD };
	private static final Item[] AXES = { Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE };
	private static final Item[] SPEARS = { Items.WOODEN_SPEAR, Items.STONE_SPEAR, Items.IRON_SPEAR, Items.DIAMOND_SPEAR, Items.NETHERITE_SPEAR };
	private static final Item[][] ARMOUR = {
		{ Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS },
		{ Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS },
		{ Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS },
		{ Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS },
		{ Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS },
	};

	private static final int SWORD = 1, AXE = 2, MACE = 3, SPEAR = 4, BOW = 5, CROSSBOW = 6, THROWN = 7;
	private static final int FLAG_SHIELD = 1, FLAG_AMMO = 2;

	private LootTable() {
	}

	static List<ItemStack> roll(HostState.Loot loot, RandomSource random) {
		List<ItemStack> drops = new ArrayList<>();
		int tier = Math.clamp(loot.tier(), 0, 4);
		float roll = random.nextFloat();
		if (roll < 0.15F) {
			ItemStack weapon = weapon(loot.weapon(), tier, random);
			if (weapon != null) {
				drops.add(worn(weapon, random));
			}
		} else if (roll < 0.27F) {
			Item[] set = ARMOUR[tier];
			drops.add(worn(new ItemStack(set[random.nextInt(set.length)]), random));
		} else if (roll < 0.32F && (loot.flags() & FLAG_SHIELD) != 0) {
			drops.add(worn(new ItemStack(Items.SHIELD), random));
		} else if (roll < 0.55F) {
			drops.add((loot.flags() & FLAG_AMMO) != 0
				? new ItemStack(Items.ARROW, 4 + random.nextInt(9))
				: new ItemStack(random.nextBoolean() ? Items.BREAD : Items.COOKED_BEEF, 1 + random.nextInt(3)));
		}

		if (tier >= 3 && random.nextFloat() < 0.02F) {
			drops.add(new ItemStack(random.nextBoolean() ? Items.ENDER_PEARL : Items.GOLDEN_APPLE));
		}
		return drops;
	}

	private static ItemStack weapon(int kind, int tier, RandomSource random) {
		return switch (kind) {
			case SWORD -> new ItemStack(SWORDS[tier]);
			case AXE -> new ItemStack(AXES[tier]);
			case MACE -> new ItemStack(tier >= 4 && random.nextFloat() < 0.1F ? Items.MACE : AXES[tier]);
			case SPEAR, THROWN -> new ItemStack(SPEARS[tier]);
			case BOW -> new ItemStack(Items.BOW);
			case CROSSBOW -> new ItemStack(Items.CROSSBOW);
			default -> null;
		};
	}

	private static ItemStack worn(ItemStack stack, RandomSource random) {
		if (stack.isDamageableItem()) {
			int max = stack.getMaxDamage();
			stack.setDamageValue((int) (max * (0.2F + random.nextFloat() * 0.6F)));
		}
		return stack;
	}
}
