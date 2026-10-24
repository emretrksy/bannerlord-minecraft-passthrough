package blpassthrough;

import blpassthrough.shm.SharedMemory;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class InventoryLink {
	public static final int MODE_FULL_KIT = 1;
	public static final int MODE_CAMPAIGN = 2;
	public static final int MODE_TOURNAMENT = 3;
	private static final String REPAIR = "#repair";
	private static final String CAPTURED = "#captured";
	private static final int BAG_START = 9;
	private static final int BAG_END = 36;

	private static final String NAME = "Local\\BLMC_Inventory";
	private static final long SIZE = 131072;
	private static final int MAGIC = 0x494D4C42;
	private static final long HOST = 64;
	private static final long HOST_DATA = 128;
	private static final long STEVE = 65536;
	private static final long STEVE_DATA = STEVE + 64;
	private static final int MAX_LENGTH = 65536 - 128;

	private final SharedMemory memory = SharedMemory.createOrOpen(NAME, SIZE);
	private int steveSeq;

	public record Offer(int missionId, int mode, String inventory, String goods, float health) {
	}

	public InventoryLink() {
		this.steveSeq = this.memory.segment().get(ValueLayout.JAVA_INT, STEVE);
	}

	public Offer offer() {
		MemorySegment s = this.memory.segment();
		if (s.get(ValueLayout.JAVA_INT, 0) != MAGIC) {
			return null;
		}
		for (int attempt = 0; attempt < 4; attempt++) {
			int before = s.get(ValueLayout.JAVA_INT, HOST);
			VarHandle.acquireFence();
			if ((before & 1) != 0) {
				Thread.onSpinWait();
				continue;
			}
			int mission = s.get(ValueLayout.JAVA_INT, HOST + 4);
			int mode = s.get(ValueLayout.JAVA_INT, HOST + 8);
			int length = Math.clamp(s.get(ValueLayout.JAVA_INT, HOST + 12), 0, MAX_LENGTH);
			int goodsLength = Math.clamp(s.get(ValueLayout.JAVA_INT, HOST + 16), 0, MAX_LENGTH - length);
			byte[] text = s.asSlice(HOST_DATA, length).toArray(ValueLayout.JAVA_BYTE);
			byte[] goods = s.asSlice(HOST_DATA + length, goodsLength).toArray(ValueLayout.JAVA_BYTE);
			VarHandle.acquireFence();
			if (s.get(ValueLayout.JAVA_INT, HOST) == before) {
				float health = s.get(ValueLayout.JAVA_FLOAT, HOST + 20);
				return new Offer(mission, mode, new String(text, StandardCharsets.UTF_8), new String(goods, StandardCharsets.UTF_8), health);
			}
		}
		return null;
	}

	public synchronized void report(int missionId, String inventory, float health) {
		byte[] text = inventory.getBytes(StandardCharsets.UTF_8);
		if (text.length > MAX_LENGTH) {
			Passthrough.LOGGER.warn("Steve's inventory is too big to save ({} bytes)", text.length);
			return;
		}
		MemorySegment s = this.memory.segment();
		this.steveSeq |= 1;
		s.set(ValueLayout.JAVA_INT, STEVE, this.steveSeq);
		VarHandle.releaseFence();
		s.set(ValueLayout.JAVA_INT, STEVE + 4, missionId);
		s.set(ValueLayout.JAVA_INT, STEVE + 8, text.length);
		s.set(ValueLayout.JAVA_FLOAT, STEVE + 12, health);
		MemorySegment.copy(text, 0, s, ValueLayout.JAVA_BYTE, STEVE_DATA, text.length);
		VarHandle.releaseFence();
		this.steveSeq++;
		s.set(ValueLayout.JAVA_INT, STEVE, this.steveSeq);
	}

	public static String save(ServerPlayer player) {
		RegistryOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Inventory inventory = player.getInventory();
		ListTag items = new ListTag();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			Optional<Tag> item = ItemStack.CODEC.encodeStart(ops, stack).result();
			if (item.isPresent()) {
				CompoundTag entry = new CompoundTag();
				entry.putInt("Slot", slot);
				entry.put("Item", item.get());
				items.add(entry);
			}
		}
		CompoundTag root = new CompoundTag();
		root.put("Items", items);
		return root.toString();
	}

	public static void deliver(ServerPlayer player, String goods) {
		for (String line : goods.split("\n")) {
			String[] parts = line.trim().split(" ");
			if (parts.length != 2) {
				continue;
			}
			if (parts[0].equals(CAPTURED)) {
				for (int slot = BAG_START; slot < BAG_END; slot++) {
					player.getInventory().setItem(slot, ItemStack.EMPTY);
				}
				continue;
			}
			if (parts[0].equals(REPAIR)) {
				Inventory inventory = player.getInventory();
				for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
					ItemStack stack = inventory.getItem(slot);
					if (stack.isDamageableItem()) {
						stack.setDamageValue(0);
					}
				}
				continue;
			}
			Identifier id = Identifier.tryParse(parts[0]);
			Optional<Item> item = id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
			if (item.isEmpty()) {
				Passthrough.LOGGER.warn("Unknown item bought for Steve: {}", parts[0]);
				continue;
			}
			int count;
			try {
				count = Integer.parseInt(parts[1]);
			} catch (NumberFormatException e) {
				continue;
			}
			while (count > 0) {
				ItemStack stack = new ItemStack(item.get(), Math.min(count, item.get().getDefaultMaxStackSize()));
				count -= stack.getCount();
				if (!player.getInventory().add(stack) && !stack.isEmpty()) {
					player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack));
				}
			}
		}
	}

	public static boolean load(ServerPlayer player, String saved) {
		CompoundTag root;
		try {
			root = TagParser.parseCompoundFully(saved);
		} catch (Exception e) {
			Passthrough.LOGGER.warn("Could not read Steve's saved inventory", e);
			return false;
		}
		RegistryOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Inventory inventory = player.getInventory();
		inventory.clearContent();
		ListTag items = root.getListOrEmpty("Items");
		for (int i = 0; i < items.size(); i++) {
			CompoundTag entry = items.getCompoundOrEmpty(i);
			int slot = entry.getIntOr("Slot", -1);
			Tag item = entry.get("Item");
			if (slot < 0 || slot >= inventory.getContainerSize() || item == null) {
				continue;
			}
			ItemStack.CODEC.parse(ops, item).result().ifPresent(stack -> inventory.setItem(slot, stack));
		}
		return true;
	}
}
