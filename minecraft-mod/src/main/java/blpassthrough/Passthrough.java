package blpassthrough;

import blpassthrough.shm.Protocol;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Passthrough implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("blpassthrough");
	private static final int DEATH_REPORT_DELAY = 20;
	private static final int MAX_RENDER_DISTANCE = 8;
	private static final int MAX_SIMULATION_DISTANCE = 6;
	private static final boolean DEBUG = Boolean.getBoolean("blpassthrough.debug");

	private static Passthrough instance;

	private HostLink hostLink;
	private PlayerPublisher playerPublisher;
	private final Coords coords = new Coords();
	private final InputDriver inputDriver = new InputDriver();
	private final FrameExporter frameExporter = new FrameExporter();
	private final WorldBridge worldBridge = new WorldBridge(this.coords);
	private volatile HostState frameHost;
	private CameraPose framePose;
	private long frameCounter;
	private int deadTicks;
	private boolean hidHud;
	private int menuMission = Integer.MIN_VALUE;
	private int menuWait;
	private static final int MENU_OPEN_DELAY = 10;

	@Override
	public void onInitializeClient() {
		instance = this;
		this.hostLink = new HostLink();
		this.playerPublisher = new PlayerPublisher();
		LOGGER.info("Bannerlord passthrough ready; waiting for Bannerlord to publish a mission");
	}

	public static Passthrough get() {
		return instance;
	}

	public void beginFrame(Minecraft minecraft) {
		this.frameHost = this.hostLink.poll();
		this.framePose = null;
		if (this.frameHost == null) {
			return;
		}

		this.coords.update(this.frameHost);
		this.inputDriver.frame(minecraft, this.frameHost);
		this.worldBridge.snapClientProxies(minecraft, this.frameHost);
	}

	public void cameraUpdated(Minecraft minecraft, Camera camera) {
		HostState host = this.frameHost;
		LocalPlayer player = minecraft.player;
		if (host == null || player == null || !camera.isInitialized()) {
			return;
		}

		this.framePose = CameraPose.of(camera, this.coords, ++this.frameCounter);
		int flags = 0;
		if (this.worldBridge.isSpawned(host.missionId) && !player.isDeadOrDying()) {
			flags |= Protocol.PLAYER_VALID;
		}
		if (minecraft.options.getCameraType().isFirstPerson()) {
			flags |= Protocol.PLAYER_FIRST_PERSON;
		}
		if (player.isDeadOrDying()) {
			flags |= Protocol.PLAYER_DEAD;
		}
		if (minecraft.gui.screen() != null) {
			flags |= Protocol.PLAYER_CURSOR;
		}
		if (player.isPassenger()) {
			flags |= Protocol.PLAYER_MOUNTED;
		}
		if (minecraft.gui.screen() instanceof DevPanelScreen) {
			flags |= Protocol.PLAYER_PAUSED;
		}

		Entity ground = player.getRootVehicle();
		float partialTicks = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vector3d feet = this.coords.toBannerlord(
			Mth.lerp(partialTicks, ground.xo, ground.getX()),
			Mth.lerp(partialTicks, ground.yo, ground.getY()),
			Mth.lerp(partialTicks, ground.zo, ground.getZ()));
		float yaw = (float) Math.toRadians(player.getVisualRotationYInDegrees());
		Vector3f body = Coords.directionToBannerlord((float) -Math.sin(yaw), 0.0F, (float) Math.cos(yaw));
		Vec3 pearl = this.worldBridge.pearl();
		Vector3d focus = pearl != null ? this.coords.toBannerlord(pearl.x, pearl.y, pearl.z) : null;
		this.playerPublisher.publish(flags, this.framePose, host.missionId, feet, body, focus);
	}

	public void tickInput(Minecraft minecraft) {
		if (minecraft.level == null) {
			this.frameHost = this.hostLink.poll();
		}
		HostState host = this.frameHost;
		if (host != null) {
			this.inputDriver.tick(minecraft, host);
			this.worldBridge.snapClientProxies(minecraft, host);
		}
	}

	public void tick(Minecraft minecraft) {
		HostState host = this.frameHost;
		WorldBootstrap.tick(minecraft, host != null);
		if (minecraft.options.renderDistance().get() > MAX_RENDER_DISTANCE) {
			minecraft.options.renderDistance().set(MAX_RENDER_DISTANCE);
		}
		if (minecraft.options.simulationDistance().get() > MAX_SIMULATION_DISTANCE) {
			minecraft.options.simulationDistance().set(MAX_SIMULATION_DISTANCE);
		}
		if (WorldBootstrap.PRELOAD) {
			minecraft.options.pauseOnLostFocus = false;
		}
		if (host == null || !host.has(Protocol.FLAG_MENU)) {
			if (this.menuMission != Integer.MIN_VALUE && minecraft.gui.screen() != null) {
				minecraft.gui.setScreen(null);
			}
			this.menuMission = Integer.MIN_VALUE;
		}
		if (host == null) {
			return;
		}

		minecraft.options.pauseOnLostFocus = false;
		if (minecraft.options.cloudStatus().get() != CloudStatus.OFF) {
			minecraft.options.cloudStatus().set(CloudStatus.OFF);
		}
		if (minecraft.options.bobView().get()) {
			minecraft.options.bobView().set(false);
		}
		if (minecraft.options.damageTiltStrength().get() != 0.0) {
			minecraft.options.damageTiltStrength().set(0.0);
		}
		WindowShaper.match(minecraft, host);
		boolean bannerlordUi = host.has(Protocol.FLAG_BANNERLORD_UI) || host.has(Protocol.FLAG_MENU);
		if (bannerlordUi != this.hidHud) {
			this.hidHud = bannerlordUi;
			if (minecraft.gui.hud.isHidden() != bannerlordUi) {
				minecraft.gui.hud.toggle();
			}
		}

		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}

		this.worldBridge.tick(minecraft, host);
		this.openMenuInventory(minecraft, host);
		if (DEBUG && player.tickCount % 60 == 0) {
			int cellX = (int) Math.floor(player.getX()) - host.groundOriginX;
			int cellY = (int) Math.floor(-player.getZ()) - host.groundOriginY;
			float ground = cellX >= 0 && cellY >= 0 && cellX < Protocol.GROUND_SIZE && cellY < Protocol.GROUND_SIZE
				? host.ground[cellY * Protocol.GROUND_SIZE + cellX] : Float.NaN;
			LOGGER.info("debug: steve y {} onGround {} flying {} | bannerlord ground {} -> minecraft {} | agent z {} -> {}",
				player.getY(), player.onGround(), player.isFallFlying(), ground, ground + this.coords.yOffset(),
				host.playerPos.z, host.playerPos.z + this.coords.yOffset());
		}
		if (player.isDeadOrDying()) {
			if (++this.deadTicks == DEATH_REPORT_DELAY) {
				this.worldBridge.reportDeath();
			}
		} else {
			this.deadTicks = 0;
		}
	}

	public boolean isActive() {
		return this.frameHost != null;
	}

	private void openMenuInventory(Minecraft minecraft, HostState host) {
		if (!host.has(Protocol.FLAG_MENU) || !this.worldBridge.isSpawned(host.missionId) || this.menuMission == host.missionId
			|| minecraft.player == null) {
			this.menuWait = 0;
			return;
		}
		if (++this.menuWait >= MENU_OPEN_DELAY) {
			this.menuMission = host.missionId;
			minecraft.gui.setScreen(new InventoryScreen(minecraft.player));
		}
	}

	public boolean isBannerlordUi() {
		HostState host = this.frameHost;
		return host != null && host.has(Protocol.FLAG_BANNERLORD_UI);
	}

	public boolean isTurkish() {
		HostState host = this.frameHost;
		return host != null && host.has(Protocol.FLAG_TURKISH);
	}

	public boolean isHostPaused() {
		HostState host = this.frameHost;
		return host != null ? host.has(Protocol.FLAG_PAUSED) : this.hostLink.stalled();
	}

	public boolean isMenu() {
		HostState host = this.frameHost;
		return host != null && host.has(Protocol.FLAG_MENU);
	}

	public WorldBridge worldBridge() {
		return this.worldBridge;
	}

	public FrameExporter frameExporter() {
		return this.frameExporter;
	}

	public CameraPose framePose() {
		return this.framePose;
	}
}
