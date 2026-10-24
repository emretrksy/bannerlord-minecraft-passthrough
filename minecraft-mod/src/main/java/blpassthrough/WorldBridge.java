package blpassthrough;

import blpassthrough.shm.Protocol;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public final class WorldBridge {
	public static final String PROXY_NAME = "blpassthrough:proxy";
	private static final float HIT_SCALE = 6.0F;
	private static final float THROWN_DAMAGE = 1.0F;
	private static final int KNOCK_BACK = 32;
	private static final int PROXY_IFRAMES = 10;
	private static final int MAX_COLUMN = 12;
	private static final float BLAST_REACH = 2.0F;
	private static final double STEP_HEIGHT = 1.0;
	private static final int KEEP_RADIUS = 128;
	private static final double PEARL_RANGE = 160.0;
	private static final double AGENT_RANGE = 64.0;
	private static final double HORSE_SPEED = 0.3375;
	private static final double HORSE_JUMP = 1.0;
	private static final double CREEPER_IGNITE_RANGE = 3.5;
	private static final int SWEEP_INTERVAL = 20;
	private static final int VOID_DEPTH = 16;
	private static final int REPORT_INTERVAL = 40;
	private static final int MENU_REPORT_INTERVAL = 4;
	private static final float MIN_START_HEALTH = 4.0F;
	private static final double DEV_KILL_RANGE = 16.0;
	private static final int BARRIER_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private final Coords coords;
	private EventWriter events;

	private IntegratedServer server;
	private int spawnedMission = Integer.MIN_VALUE;
	private int sentGroundVersion;
	private int seenHurtCount;
	private boolean hurtSynced;
	private int seenLootCount;
	private int seenTeleportCount;

	private Map<Long, Set<BlockPos>> barriers = new HashMap<>();
	private int barrierTotal;
	private final Map<Integer, Husk> proxies = new HashMap<>();
	private final Map<Integer, Integer> proxyIndexByEntityId = new HashMap<>();
	private final Map<Integer, Integer> proxyEntityIds = new ConcurrentHashMap<>();
	private final Set<Integer> enemyProxyIds = new HashSet<>();
	private final Map<Integer, Long> lastProxyHit = new HashMap<>();
	private boolean awaitingGround;
	private boolean horsePending;
	private int sweepTicks;
	private int lowestGround = Integer.MAX_VALUE;
	private Vec3 lastSafe;
	private Horse horse;
	private volatile Vec3 pearl;
	private int ticksSinceSpawn;
	private boolean devFought;
	private int serverMission = Integer.MIN_VALUE;
	private int loadedMission = Integer.MIN_VALUE;
	private int reportTicks;
	private String lastReport;
	private final Set<BlockPos> changedBlocks = new HashSet<>();
	private boolean cleaning;
	private InventoryLink inventoryLink;

	private volatile boolean campaign;
	private volatile boolean autoRevive;
	private volatile boolean invulnerable;
	private volatile int proxyCount;
	private volatile int barrierCount;
	private static final boolean DEV_TEST = Boolean.getBoolean("blpassthrough.devtest");

	WorldBridge(Coords coords) {
		this.coords = coords;
	}

	public boolean isSpawned(int missionId) {
		return this.spawnedMission == missionId;
	}

	public void tick(Minecraft minecraft, HostState host) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server == null || minecraft.player == null || !host.has(Protocol.FLAG_PLAYER)) {
			return;
		}

		if (server != this.server) {
			this.server = server;
			this.spawnedMission = Integer.MIN_VALUE;
			server.execute(this::resetServerState);
		}
		if (this.events == null) {
			this.events = new EventWriter();
		}

		UUID id = minecraft.player.getUUID();
		if (host.missionId != this.spawnedMission) {
			if (minecraft.player.isDeadOrDying()) {
				minecraft.player.respawn();
				return;
			}

			this.spawnedMission = host.missionId;
			this.sentGroundVersion = host.groundVersion - 1;
			this.hurtSynced = false;
			this.seenLootCount = host.lootCount;
			this.seenTeleportCount = host.teleportCount;
			Vec3 feet = this.coords.position(host.playerPos);
			Vec3 facing = Coords.direction(host.lookDir).multiply(1.0, 0.0, 1.0);
			Float yaw = facing.lengthSqr() > 1.0E-4 ? Coords.yaw(facing) : null;
			boolean mounted = host.has(Protocol.FLAG_MOUNTED);
			if (this.inventoryLink == null) {
				this.inventoryLink = new InventoryLink();
			}
			InventoryLink.Offer offer = this.inventoryLink.offer();
			if (offer != null && offer.missionId() != host.missionId) {
				offer = null;
			}
			InventoryLink.Offer kit = offer;
			int mission = host.missionId;
			server.execute(() -> this.spawn(server, id, feet, yaw, mounted, mission, kit));
		}
		if (host.teleportCount != this.seenTeleportCount) {
			this.seenTeleportCount = host.teleportCount;
			Vec3 target = this.coords.position(host.teleportTarget);
			server.execute(() -> this.teleport(server, id, target));
		}

		HostState ground = host.groundVersion != this.sentGroundVersion ? host : null;
		this.sentGroundVersion = host.groundVersion;

		List<HostState.Hurt> hurts = new ArrayList<>();
		if (!this.hurtSynced) {
			this.seenHurtCount = host.hurtCount;
			this.hurtSynced = true;
		}
		int fresh = Math.min(host.hurtCount - this.seenHurtCount, Protocol.HURT_CAPACITY);
		for (int n = host.hurtCount - fresh; n < host.hurtCount; n++) {
			hurts.add(host.hurt(n));
		}
		this.seenHurtCount = host.hurtCount;

		List<Vec3> lootAt = new ArrayList<>();
		List<HostState.Loot> loots = new ArrayList<>();
		int freshLoot = Math.min(host.lootCount - this.seenLootCount, Protocol.LOOT_CAPACITY);
		for (int n = host.lootCount - freshLoot; n < host.lootCount; n++) {
			HostState.Loot loot = host.loot(n);
			loots.add(loot);
			lootAt.add(this.coords.position(loot.position()));
		}
		this.seenLootCount = host.lootCount;

		double yOffset = this.coords.yOffset();
		List<HostState.Agent> agents = host.agents;
		server.execute(() -> {
			this.serverTick(server, id, ground, yOffset, agents, hurts);
			this.dropLoot(server, loots, lootAt);
		});
	}

	public void reportDeath() {
		this.spawnedMission = Integer.MIN_VALUE;
		if (this.events != null) {
			this.events.write(Protocol.EVENT_PLAYER_DIED, 0, new Vector3d(), 0.0F, 0.0F);
		}
	}

	public void onExplosion(Vec3 centre, float radius, boolean creeper) {
		if (this.events != null && Passthrough.get().isActive()) {
			this.events.write(Protocol.EVENT_EXPLOSION, creeper ? 1 : 0, this.coords.toBannerlord(centre.x, centre.y, centre.z), radius * BLAST_REACH, radius);
		}
	}

	public void onBlockChanged(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
		if (this.events == null || !Passthrough.get().isActive() || before.is(Blocks.BARRIER) || after.is(Blocks.BARRIER)) {
			return;
		}
		if (!this.cleaning) {
			this.changedBlocks.add(pos.immutable());
		}
	}

	public static boolean isProxy(Entity entity) {
		Component name = entity.getCustomName();
		return name != null && PROXY_NAME.equals(name.getString());
	}

	public Integer proxyIndex(Entity entity) {
		return this.proxyIndexByEntityId.get(entity.getId());
	}

	public boolean isEnemyProxy(LivingEntity entity) {
		return this.enemyProxyIds.contains(entity.getId());
	}

	public boolean isAllyProxy(Entity entity) {
		return this.proxyIndexByEntityId.containsKey(entity.getId()) && !this.enemyProxyIds.contains(entity.getId());
	}

	public Vec3 pearl() {
		return this.pearl;
	}

	public void snapClientProxies(Minecraft minecraft, HostState host) {
		ClientLevel level = minecraft.level;
		if (level == null || !this.isSpawned(host.missionId)) {
			return;
		}
		for (HostState.Agent agent : host.agents) {
			Integer id = this.proxyEntityIds.get(agent.index());
			Entity proxy = id != null ? level.getEntity(id) : null;
			if (proxy == null || !isProxy(proxy)) {
				continue;
			}
			Vec3 at = this.coords.position(agent.position());
			proxy.getInterpolation().cancel();
			proxy.snapTo(at.x, at.y, at.z, Coords.yaw(Coords.direction(agent.facing())), 0.0F);
			proxy.setOldPosAndRot();
		}
	}

	public boolean onProxyHurt(int agentIndex, LivingEntity proxy, DamageSource source, float damage) {
		if (!(source.getEntity() instanceof ServerPlayer player) || source.is(DamageTypeTags.IS_EXPLOSION) || this.events == null) {
			return false;
		}
		if (!this.enemyProxyIds.contains(proxy.getId())) {
			return false;
		}

		long now = proxy.level().getGameTime();
		Long last = this.lastProxyHit.get(agentIndex);
		if (last != null && now - last < PROXY_IFRAMES) {
			return false;
		}
		this.lastProxyHit.put(agentIndex, now);

		float kind = weaponKind(player, source);
		if (source.getDirectEntity() instanceof Snowball || source.getDirectEntity() instanceof ThrownEgg) {
			damage = Math.max(damage, THROWN_DAMAGE);
			kind += KNOCK_BACK;
		}
		Vec3 push = proxy.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		push = push.lengthSqr() > 1.0E-6 ? push.normalize() : Vec3.ZERO;
		this.events.write(Protocol.EVENT_AGENT_HIT, agentIndex, new Vector3d(push.x, -push.z, 0.3), kind, damage * HIT_SCALE);
		Passthrough.LOGGER.info("Steve hit Bannerlord agent {} for {} ({})", agentIndex, damage * HIT_SCALE, source.getMsgId());
		return true;
	}

	private static float weaponKind(ServerPlayer player, DamageSource source) {
		int kind;
		if (source.getDirectEntity() instanceof AbstractArrow arrow) {
			kind = arrow.getWeaponItem() != null && arrow.getWeaponItem().is(Items.CROSSBOW) ? 6 : 5;
		} else if (source.getDirectEntity() instanceof ThrownTrident || source.getDirectEntity() instanceof Snowball
			|| source.getDirectEntity() instanceof ThrownEgg || source.getDirectEntity() instanceof SteveFireball) {
			kind = 7;
		} else {
			ItemStack held = player.getMainHandItem();
			kind = held.is(ItemTags.SWORDS) ? 1
				: held.is(ItemTags.AXES) ? 2
				: held.is(Items.MACE) ? 3
				: held.is(ItemTags.SPEARS) || held.is(Items.TRIDENT) ? 4
				: 0;
		}
		return kind + (player.isPassenger() ? 16 : 0);
	}

	private void resetServerState() {
		this.barriers = new HashMap<>();
		this.barrierTotal = 0;
		this.proxies.clear();
		this.proxyIndexByEntityId.clear();
		this.proxyEntityIds.clear();
		this.enemyProxyIds.clear();
		this.lastProxyHit.clear();
		this.pearl = null;
	}

	private void spawn(MinecraftServer server, UUID id, Vec3 feet, Float yaw, boolean mounted, int mission, InventoryLink.Offer offer) {
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		if (player == null) {
			return;
		}

		ServerLevel level = player.level();
		player.stopRiding();
		this.clearWorld(level, player);
		this.resetServerState();

		player.setGameMode(GameType.SURVIVAL);
		player.getAbilities().invulnerable = this.invulnerable;
		setBase(player, Attributes.STEP_HEIGHT, STEP_HEIGHT);
		player.getAbilities().mayfly = true;
		player.getAbilities().flying = true;
		player.onUpdateAbilities();
		player.connection.teleport(feet.x, feet.y + 0.6, feet.z, yaw != null ? yaw : player.getYRot(), 0.0F);
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(5.0F);
		this.serverMission = mission;
		this.campaign = offer != null && offer.mode() == InventoryLink.MODE_CAMPAIGN;
		if (offer != null && offer.mode() == InventoryLink.MODE_TOURNAMENT) {
			giveTournamentKit(player);
		} else if (!this.campaign) {
			giveTierKit(player, START_TIER);
		} else if (this.loadedMission != mission) {
			this.loadedMission = mission;
			if (offer.inventory().isBlank() || !InventoryLink.load(player, offer.inventory())) {
				giveStarterKit(player);
			}
			InventoryLink.deliver(player, offer.goods());
			if (offer.health() > 0.0F && offer.health() < 1.0F) {
				player.setHealth(Math.max(MIN_START_HEALTH, player.getMaxHealth() * offer.health()));
			}
		}
		this.lastReport = null;
		this.awaitingGround = true;
		this.lowestGround = Integer.MAX_VALUE;
		this.lastSafe = null;
		this.horsePending = mounted;
		this.ticksSinceSpawn = 0;
		this.devFought = false;
		Passthrough.LOGGER.info("Steve spawned into the battle at {}", feet);
	}

	private void serverTick(MinecraftServer server, UUID id, HostState ground, double yOffset, List<HostState.Agent> agents, List<HostState.Hurt> hurts) {
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		if (player == null) {
			return;
		}

		ServerLevel level = player.level();
		if (ground != null) {
			this.applyGround(level, ground, yOffset, player);
		}
		if (this.awaitingGround && !level.getBlockState(player.blockPosition().below()).isAir()) {
			this.awaitingGround = false;
			player.getAbilities().flying = false;
			player.getAbilities().mayfly = false;
			player.onUpdateAbilities();
			if (this.horsePending) {
				this.horsePending = false;
				this.mountHorse(level, player);
			}
		}
		this.catchFall(player);
		if (player.getAbilities().invulnerable != this.invulnerable) {
			player.getAbilities().invulnerable = this.invulnerable;
			player.onUpdateAbilities();
		}
		this.reportInventory(player);
		this.updateProxies(level, agents);
		this.proxyCount = this.proxies.size();
		this.barrierCount = this.barrierTotal;
		if (++this.sweepTicks >= SWEEP_INTERVAL) {
			this.sweepTicks = 0;
			this.sweepOrphans(level);
		}
		this.igniteCreepers(level, player);
		this.pearl = this.findPearl(level, player);

		if (DEV_TEST && ++this.ticksSinceSpawn >= 200 && this.ticksSinceSpawn % 100 == 0 && !this.devFought) {
			this.devFought = this.devFight(level, player);
		}

		for (HostState.Hurt hurt : hurts) {
			LivingEntity victim = hurt.damage() < 0.0F && player.getVehicle() instanceof LivingEntity horse ? horse : player;
			Husk attacker = this.proxies.get(hurt.attacker());
			victim.hurtServer(level, attacker != null ? level.damageSources().mobAttack(attacker) : this.blowFrom(level, victim, hurt), Math.abs(hurt.damage()));
		}
	}

	private DamageSource blowFrom(ServerLevel level, LivingEntity victim, HostState.Hurt hurt) {
		Vec3 from = victim.getEyePosition().add(hurt.fromX() * 4.0, 0.0, -hurt.fromY() * 4.0);
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.MOB_ATTACK), from);
	}

	private boolean devFight(ServerLevel level, ServerPlayer player) {
		Husk target = null;
		for (Husk proxy : this.proxies.values()) {
			if (this.enemyProxyIds.contains(proxy.getId()) && (target == null || proxy.distanceToSqr(player) < target.distanceToSqr(player))) {
				target = proxy;
			}
		}
		if (target == null) {
			return false;
		}

		Vec3 towards = player.position().subtract(target.position()).multiply(1.0, 0.0, 1.0).normalize();
		Vec3 at = target.position().add(towards.scale(2.5));
		float yaw = Coords.yaw(towards.reverse());
		player.connection.teleport(at.x, target.getY() + 0.5, at.z, yaw, 0.0F);
		player.attack(target);
		Creeper creeper = EntityTypes.CREEPER.create(level, EntitySpawnReason.COMMAND);
		if (creeper != null) {
			creeper.snapTo(at.x, target.getY() + 0.5, at.z, yaw, 0.0F);
			level.addFreshEntity(creeper);
		}
		Passthrough.LOGGER.info("devtest: attacked agent {} at {} and summoned a creeper", this.proxyIndexByEntityId.get(target.getId()), target.position());
		return true;
	}

	private void clearWorld(ServerLevel level, ServerPlayer player) {
		this.horse = null;
		List<Entity> leftovers = new ArrayList<>();
		for (Entity entity : level.getAllEntities()) {
			if (!(entity instanceof ServerPlayer)) {
				leftovers.add(entity);
			}
		}
		leftovers.forEach(Entity::discard);

		this.cleaning = true;
		for (Set<BlockPos> chunk : this.barriers.values()) {
			for (BlockPos pos : chunk) {
				clearBarrier(level, pos);
			}
		}
		for (BlockPos pos : this.changedBlocks) {
			if (!level.getBlockState(pos).isAir()) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		this.cleaning = false;
		this.changedBlocks.clear();
		this.barriers = new HashMap<>();
		this.barrierTotal = 0;
	}

	private static void clearBarrier(ServerLevel level, BlockPos pos) {
		if (level.getBlockState(pos).is(Blocks.BARRIER)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), BARRIER_FLAGS);
		}
	}

	private void dropLoot(MinecraftServer server, List<HostState.Loot> loots, List<Vec3> at) {
		ServerLevel level = server.overworld();
		for (int i = 0; i < loots.size(); i++) {
			Vec3 where = at.get(i).add(0.0, 0.5, 0.0);
			for (ItemStack stack : LootTable.roll(loots.get(i), level.getRandom())) {
				ItemEntity item = new ItemEntity(level, where.x, where.y, where.z, stack);
				item.setDefaultPickUpDelay();
				level.addFreshEntity(item);
			}
		}
	}

	private void reportInventory(ServerPlayer player) {
		int interval = Passthrough.get().isMenu() ? MENU_REPORT_INTERVAL : REPORT_INTERVAL;
		if (!this.campaign || this.inventoryLink == null || player.isDeadOrDying() || ++this.reportTicks < interval) {
			return;
		}
		this.reportTicks = 0;
		String inventory = InventoryLink.save(player);
		float health = player.getHealth() / player.getMaxHealth();
		String report = inventory + health;
		if (!report.equals(this.lastReport)) {
			this.lastReport = report;
			this.inventoryLink.report(this.serverMission, inventory, health);
		}
	}

	public boolean isCampaign() {
		return this.campaign;
	}

	public boolean autoRevive() {
		return this.autoRevive;
	}

	public void setAutoRevive(boolean value) {
		this.autoRevive = value;
	}

	public boolean invulnerable() {
		return this.invulnerable;
	}

	public void setInvulnerable(boolean value) {
		this.invulnerable = value;
	}

	public int proxyCount() {
		return this.proxyCount;
	}

	public int barrierCount() {
		return this.barrierCount;
	}

	public void devTierKit(int tier) {
		this.onServer(player -> giveTierKit(player, tier));
	}

	public void devHeal() {
		this.onServer(player -> {
			player.setHealth(player.getMaxHealth());
			player.getFoodData().setFoodLevel(20);
			player.getFoodData().setSaturation(5.0F);
			player.clearFire();
		});
	}

	public void devClearInventory() {
		this.onServer(player -> player.getInventory().clearContent());
	}

	public void devGive(ItemStack stack) {
		ItemStack copy = stack.copy();
		this.onServer(player -> {
			player.getInventory().add(copy);
		});
	}

	public void devKillNearby() {
		this.onServer(player -> {
			if (this.events == null) {
				return;
			}
			double range = DEV_KILL_RANGE * DEV_KILL_RANGE;
			for (Map.Entry<Integer, Husk> entry : this.proxies.entrySet()) {
				Husk proxy = entry.getValue();
				if (this.enemyProxyIds.contains(proxy.getId()) && proxy.distanceToSqr(player) < range) {
					this.events.write(Protocol.EVENT_AGENT_HIT, entry.getKey(), new Vector3d(0.0, 0.0, 1.0), 0.0F, 1000.0F);
				}
			}
		});
	}

	public void devClearBlocks() {
		this.onServer(player -> {
			ServerLevel level = player.level();
			this.cleaning = true;
			for (BlockPos pos : this.changedBlocks) {
				if (!level.getBlockState(pos).isAir()) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			this.cleaning = false;
			this.changedBlocks.clear();
			List<Entity> loose = new ArrayList<>();
			for (Entity entity : level.getAllEntities()) {
				if (!(entity instanceof ServerPlayer) && !isProxy(entity) && entity != this.horse) {
					loose.add(entity);
				}
			}
			loose.forEach(Entity::discard);
		});
	}

	private void onServer(Consumer<ServerPlayer> action) {
		Minecraft minecraft = Minecraft.getInstance();
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server == null || minecraft.player == null) {
			return;
		}
		UUID id = minecraft.player.getUUID();
		server.execute(() -> {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				action.accept(player);
			}
		});
	}

	private void teleport(MinecraftServer server, UUID id, Vec3 target) {
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		if (player == null) {
			return;
		}

		Entity body = player.getRootVehicle();
		if (body != player) {
			body.teleportTo(target.x, target.y + 0.6, target.z);
			body.setDeltaMovement(Vec3.ZERO);
			body.resetFallDistance();
		} else {
			player.getAbilities().mayfly = true;
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
			player.connection.teleport(target.x, target.y + 0.6, target.z, player.getYRot(), player.getXRot());
			this.awaitingGround = true;
		}
		player.setDeltaMovement(Vec3.ZERO);
		player.resetFallDistance();
		this.lowestGround = Integer.MAX_VALUE;
		this.lastSafe = null;
		Passthrough.LOGGER.info("Bannerlord moved Steve to {}", target);
	}

	private void catchFall(ServerPlayer player) {
		Entity body = player.getRootVehicle();
		if (this.awaitingGround) {
			return;
		}
		if (body.onGround()) {
			this.lastSafe = body.position();
			return;
		}
		if (this.lastSafe == null || this.lowestGround == Integer.MAX_VALUE || body.getY() > this.lowestGround - VOID_DEPTH) {
			return;
		}

		Vec3 at = this.lastSafe;
		Passthrough.LOGGER.info("Steve fell through the ground at {}; back to {}", body.position(), at);
		if (body != player) {
			body.teleportTo(at.x, at.y, at.z);
			body.setDeltaMovement(Vec3.ZERO);
			body.resetFallDistance();
		} else {
			player.connection.teleport(at.x, at.y, at.z, player.getYRot(), player.getXRot());
		}
		player.setDeltaMovement(Vec3.ZERO);
		player.resetFallDistance();
	}

	private void mountHorse(ServerLevel level, ServerPlayer player) {
		Horse horse = EntityTypes.HORSE.create(level, EntitySpawnReason.COMMAND);
		if (horse == null) {
			return;
		}
		horse.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
		horse.setTamed(true);
		horse.setOwner(player);
		horse.setPersistenceRequired();
		horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
		horse.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.DIAMOND_HORSE_ARMOR));
		setBase(horse, Attributes.MOVEMENT_SPEED, HORSE_SPEED);
		setBase(horse, Attributes.JUMP_STRENGTH, HORSE_JUMP);
		level.addFreshEntity(horse);
		player.startRiding(horse);
		this.horse = horse;
		Passthrough.LOGGER.info("Steve mounted his horse");
	}

	private static void setBase(LivingEntity entity, Holder<Attribute> attribute, double value) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	private void sweepOrphans(ServerLevel level) {
		for (Husk husk : level.getEntities(EntityTypes.HUSK, husk -> isProxy(husk) && !this.proxyIndexByEntityId.containsKey(husk.getId()))) {
			husk.discard();
		}
	}

	private void igniteCreepers(ServerLevel level, ServerPlayer player) {
		List<Creeper> creepers = level.getEntitiesOfClass(Creeper.class, player.getBoundingBox().inflate(AGENT_RANGE), creeper -> !creeper.isIgnited());
		double range = CREEPER_IGNITE_RANGE * CREEPER_IGNITE_RANGE;
		for (Creeper creeper : creepers) {
			for (Husk proxy : this.proxies.values()) {
				if (this.enemyProxyIds.contains(proxy.getId()) && proxy.distanceToSqr(creeper) < range) {
					creeper.ignite();
					break;
				}
			}
		}
	}

	private Vec3 findPearl(ServerLevel level, ServerPlayer player) {
		List<ThrownEnderpearl> pearls = level.getEntitiesOfClass(
			ThrownEnderpearl.class, player.getBoundingBox().inflate(PEARL_RANGE), thrown -> thrown.getOwner() == player);
		return pearls.isEmpty() ? null : pearls.getFirst().position();
	}

	private void applyGround(ServerLevel level, HostState host, double yOffset, ServerPlayer player) {
		int size = Protocol.GROUND_SIZE;
		int[] tops = new int[size * size];
		for (int k = 0; k < tops.length; k++) {
			float height = host.ground[k];
			tops[k] = Float.isNaN(height) ? Integer.MIN_VALUE : (int) Math.round(height + yOffset);
		}

		int minX = host.groundOriginX >> 4;
		int maxX = (host.groundOriginX + size - 1) >> 4;
		int minZ = (-(host.groundOriginY + size)) >> 4;
		int maxZ = (-(host.groundOriginY) - 1) >> 4;
		for (int cx = minX; cx <= maxX; cx++) {
			for (int cz = minZ; cz <= maxZ; cz++) {
				level.getChunk(cx, cz);
			}
		}

		AABB occupied = player.getBoundingBox().minmax(player.getRootVehicle().getBoundingBox());
		Set<BlockPos> next = new HashSet<>();
		for (int j = 0; j < size; j++) {
			for (int i = 0; i < size; i++) {
				int top = tops[j * size + i];
				if (top == Integer.MIN_VALUE) {
					continue;
				}

				int bottom = top;
				bottom = Math.min(bottom, neighbour(tops, size, i - 1, j, top));
				bottom = Math.min(bottom, neighbour(tops, size, i + 1, j, top));
				bottom = Math.min(bottom, neighbour(tops, size, i, j - 1, top));
				bottom = Math.min(bottom, neighbour(tops, size, i, j + 1, top));
				bottom = Math.max(bottom, top - MAX_COLUMN);

				int x = host.groundOriginX + i;
				int z = -(host.groundOriginY + j) - 1;
				for (int y = bottom - 1; y < top; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (occupied.intersects(new AABB(pos))) {
						continue;
					}
					next.add(pos);
					if (level.getBlockState(pos).isAir()) {
						level.setBlock(pos, Blocks.BARRIER.defaultBlockState(), BARRIER_FLAGS);
					}
				}
			}
		}

		int minBlockX = host.groundOriginX;
		int maxBlockX = host.groundOriginX + size - 1;
		int minBlockZ = -(host.groundOriginY + size);
		int maxBlockZ = -host.groundOriginY - 1;
		long keep = (long) KEEP_RADIUS * KEEP_RADIUS;
		for (Iterator<Map.Entry<Long, Set<BlockPos>>> chunks = this.barriers.entrySet().iterator(); chunks.hasNext(); ) {
			Map.Entry<Long, Set<BlockPos>> entry = chunks.next();
			int chunkX = ChunkPos.getX(entry.getKey());
			int chunkZ = ChunkPos.getZ(entry.getKey());
			Set<BlockPos> chunk = entry.getValue();
			double dx = (chunkX << 4) + 8 - player.getX();
			double dz = (chunkZ << 4) + 8 - player.getZ();
			if (dx * dx + dz * dz > keep && level.hasChunk(chunkX, chunkZ)) {
				for (BlockPos old : chunk) {
					clearBarrier(level, old);
				}
				this.barrierTotal -= chunk.size();
				chunks.remove();
				continue;
			}
			if (chunkX < minX || chunkX > maxX || chunkZ < minZ || chunkZ > maxZ) {
				continue;
			}
			for (Iterator<BlockPos> it = chunk.iterator(); it.hasNext(); ) {
				BlockPos old = it.next();
				boolean resampled = old.getX() >= minBlockX && old.getX() <= maxBlockX && old.getZ() >= minBlockZ && old.getZ() <= maxBlockZ;
				if (resampled && !next.contains(old)) {
					clearBarrier(level, old);
					it.remove();
					this.barrierTotal--;
				}
			}
			if (chunk.isEmpty()) {
				chunks.remove();
			}
		}
		for (BlockPos pos : next) {
			if (this.barriers.computeIfAbsent(ChunkPos.pack(pos), key -> new HashSet<>()).add(pos)) {
				this.barrierTotal++;
			}
			this.lowestGround = Math.min(this.lowestGround, pos.getY() + 1);
		}
	}

	private static int neighbour(int[] tops, int size, int i, int j, int fallback) {
		if (i < 0 || j < 0 || i >= size || j >= size || tops[j * size + i] == Integer.MIN_VALUE) {
			return fallback;
		}
		return tops[j * size + i];
	}

	private void updateProxies(ServerLevel level, List<HostState.Agent> agents) {
		Set<Integer> seen = new HashSet<>();
		for (HostState.Agent agent : agents) {
			seen.add(agent.index());
			Vec3 at = this.coords.position(agent.position());
			float yaw = Coords.yaw(Coords.direction(agent.facing()));
			Husk proxy = this.proxies.get(agent.index());
			if (proxy == null || !proxy.isAlive()) {
				proxy = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
				if (proxy == null) {
					continue;
				}
				proxy.setNoAi(true);
				proxy.setSilent(true);
				proxy.setNoGravity(true);
				proxy.setPersistenceRequired();
				proxy.setCustomName(Component.literal(PROXY_NAME));
				proxy.setCustomNameVisible(false);
				proxy.snapTo(at.x, at.y, at.z, yaw, 0.0F);
				level.addFreshEntity(proxy);
				this.proxies.put(agent.index(), proxy);
				this.proxyIndexByEntityId.put(proxy.getId(), agent.index());
				this.proxyEntityIds.put(agent.index(), proxy.getId());
			} else {
				proxy.snapTo(at.x, at.y, at.z, yaw, 0.0F);
				proxy.setDeltaMovement(Vec3.ZERO);
			}

			if (agent.has(Protocol.AGENT_ENEMY)) {
				this.enemyProxyIds.add(proxy.getId());
			} else {
				this.enemyProxyIds.remove(proxy.getId());
			}
		}

		this.proxies.entrySet().removeIf(entry -> {
			if (seen.contains(entry.getKey())) {
				return false;
			}
			Husk proxy = entry.getValue();
			this.proxyIndexByEntityId.remove(proxy.getId());
			this.proxyEntityIds.remove(entry.getKey());
			this.enemyProxyIds.remove(proxy.getId());
			proxy.discard();
			return true;
		});
	}

	private static void giveTournamentKit(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		inventory.clearContent();
		inventory.setItem(0, new ItemStack(Items.WOODEN_SWORD));
		inventory.setItem(1, new ItemStack(Items.WOODEN_SPEAR));
		inventory.setItem(2, new ItemStack(Items.BOW));
		inventory.setItem(9, new ItemStack(Items.ARROW, 32));
		inventory.setSelectedSlot(0);
		player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
		player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
	}

	private static void giveStarterKit(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		inventory.clearContent();
		inventory.setItem(0, new ItemStack(Items.STONE_SWORD));
		inventory.setItem(1, new ItemStack(Items.BOW));
		inventory.setItem(2, new ItemStack(Items.BREAD, 16));
		inventory.setItem(9, new ItemStack(Items.ARROW, 32));
		inventory.setSelectedSlot(0);
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
		player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
		player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
		player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
	}

	public static final int KIT_TIERS = 5;
	private static final int START_TIER = 5;
	private static final Item[] TIER_SWORD = {Items.STONE_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
	private static final Item[] TIER_AXE = {Items.STONE_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE};
	private static final Item[] TIER_SPEAR = {Items.STONE_SPEAR, Items.IRON_SPEAR, Items.GOLDEN_SPEAR, Items.DIAMOND_SPEAR, Items.NETHERITE_SPEAR};
	private static final Item[] TIER_HELMET = {Items.LEATHER_HELMET, Items.IRON_HELMET, Items.GOLDEN_HELMET, Items.DIAMOND_HELMET, Items.NETHERITE_HELMET};
	private static final Item[] TIER_CHEST = {Items.LEATHER_CHESTPLATE, Items.IRON_CHESTPLATE, Items.GOLDEN_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE};
	private static final Item[] TIER_LEGS = {Items.LEATHER_LEGGINGS, Items.IRON_LEGGINGS, Items.GOLDEN_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS};
	private static final Item[] TIER_BOOTS = {Items.LEATHER_BOOTS, Items.IRON_BOOTS, Items.GOLDEN_BOOTS, Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS};
	private static final int[] TIER_ARROWS = {16, 32, 48, 64, 128};
	private static final int[] TIER_FOOD = {8, 16, 24, 32, 48};
	private static final int[] TIER_GOLDEN_APPLES = {0, 1, 3, 6, 12};
	private static final int[] TIER_PEARLS = {0, 2, 4, 8, 16};
	private static final int[] TIER_FIRE_CHARGES = {0, 4, 8, 16, 32};
	private static final int[] TIER_WIND_CHARGES = {0, 0, 4, 8, 16};
	private static final int[] TIER_TNT = {0, 0, 4, 8, 16};
	private static final int[] TIER_CREEPERS = {0, 1, 2, 4, 8};
	private static final int[] TIER_TOTEMS = {0, 0, 0, 1, 2};
	private static final int[] TIER_ROCKETS = {0, 0, 0, 0, 64};
	private static final int[] TIER_BLOCKS = {16, 32, 64, 64, 128};
	private static final int[] TIER_THROWABLES = {16, 16, 16, 8, 8};
	private static final int[] TIER_SHARPNESS = {0, 0, 1, 2, 4};
	private static final int[] TIER_PROTECTION = {0, 0, 1, 2, 4};
	private static final int[] TIER_POWER = {0, 0, 1, 2, 4};

	private static void giveTierKit(ServerPlayer player, int tier) {
		int t = Math.clamp(tier, 1, KIT_TIERS) - 1;
		Inventory inventory = player.getInventory();
		inventory.clearContent();

		inventory.setItem(0, enchanted(player, new ItemStack(TIER_SWORD[t]), Enchantments.SHARPNESS, TIER_SHARPNESS[t]));
		inventory.setItem(1, enchanted(player, new ItemStack(TIER_AXE[t]), Enchantments.SHARPNESS, TIER_SHARPNESS[t]));
		inventory.setItem(2, new ItemStack(TIER_SPEAR[t]));
		inventory.setItem(3, enchanted(player, new ItemStack(Items.BOW), Enchantments.POWER, TIER_POWER[t]));
		if (t >= 4) {
			inventory.setItem(4, enchanted(player, new ItemStack(Items.MACE), Enchantments.WIND_BURST, 1));
		} else if (t >= 3) {
			inventory.setItem(4, new ItemStack(Items.TRIDENT));
		} else if (t >= 2) {
			inventory.setItem(4, new ItemStack(Items.CROSSBOW));
		}
		inventory.setItem(8, new ItemStack(t >= 1 ? Items.COOKED_BEEF : Items.BREAD, TIER_FOOD[t]));
		inventory.setSelectedSlot(0);

		List<ItemStack> bag = new ArrayList<>();
		bag.add(new ItemStack(Items.ARROW, TIER_ARROWS[t]));
		bag.add(new ItemStack(Items.COBBLESTONE, TIER_BLOCKS[t]));
		bag.add(new ItemStack(t >= 2 ? Items.EGG : Items.SNOWBALL, TIER_THROWABLES[t]));
		bag.add(new ItemStack(Items.GOLDEN_APPLE, TIER_GOLDEN_APPLES[t]));
		bag.add(new ItemStack(Items.ENDER_PEARL, TIER_PEARLS[t]));
		bag.add(new ItemStack(Items.FIRE_CHARGE, TIER_FIRE_CHARGES[t]));
		bag.add(new ItemStack(Items.WIND_CHARGE, TIER_WIND_CHARGES[t]));
		bag.add(new ItemStack(Items.TNT, TIER_TNT[t]));
		bag.add(new ItemStack(Items.CREEPER_SPAWN_EGG, TIER_CREEPERS[t]));
		bag.add(new ItemStack(Items.TOTEM_OF_UNDYING, TIER_TOTEMS[t]));
		bag.add(new ItemStack(Items.FIREWORK_ROCKET, TIER_ROCKETS[t]));
		if (TIER_TNT[t] > 0) {
			bag.add(new ItemStack(Items.FLINT_AND_STEEL));
		}
		if (t >= 4) {
			bag.add(new ItemStack(Items.ELYTRA));
			bag.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 2));
		}
		int slot = Inventory.getSelectionSize();
		for (ItemStack stack : bag) {
			while (!stack.isEmpty() && slot < Inventory.INVENTORY_SIZE) {
				inventory.setItem(slot++, stack.split(stack.getMaxStackSize()));
			}
		}

		player.setItemSlot(EquipmentSlot.HEAD, enchanted(player, new ItemStack(TIER_HELMET[t]), Enchantments.PROTECTION, TIER_PROTECTION[t]));
		player.setItemSlot(EquipmentSlot.CHEST, enchanted(player, new ItemStack(TIER_CHEST[t]), Enchantments.PROTECTION, TIER_PROTECTION[t]));
		player.setItemSlot(EquipmentSlot.LEGS, enchanted(player, new ItemStack(TIER_LEGS[t]), Enchantments.PROTECTION, TIER_PROTECTION[t]));
		player.setItemSlot(EquipmentSlot.FEET, enchanted(player, new ItemStack(TIER_BOOTS[t]), Enchantments.PROTECTION, TIER_PROTECTION[t]));
		player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
	}

	private static ItemStack enchanted(ServerPlayer player, ItemStack stack, ResourceKey<Enchantment> enchantment, int level) {
		if (level > 0) {
			stack.enchant(player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment), level);
		}
		return stack;
	}
}
