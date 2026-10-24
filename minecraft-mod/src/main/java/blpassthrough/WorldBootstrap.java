package blpassthrough;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPresets;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

public final class WorldBootstrap {
	public static final String WORLD_NAME = "BannerlordPassthrough";
	private static final boolean AUTO_JOIN = !"false".equals(System.getProperty("blpassthrough.autojoin"));
	static final boolean PRELOAD = Boolean.getBoolean("blpassthrough.preload");
	private static final List<String> SETUP_COMMANDS = List.of(
		"gamerule advance_time false",
		"gamerule advance_weather false",
		"gamerule spawn_mobs false",
		"gamerule keep_inventory true",
		"time set 6000",
		"weather clear");

	private static boolean joinAttempted;
	private static IntegratedServer configuredServer;

	private WorldBootstrap() {
	}

	public static void tick(Minecraft minecraft, boolean bannerlordActive) {
		Screen screen = minecraft.gui.screen();
		if (AUTO_JOIN && (bannerlordActive || PRELOAD) && !joinAttempted && minecraft.level == null
			&& (screen instanceof TitleScreen || screen instanceof AccessibilityOnboardingScreen)) {
			joinAttempted = true;
			minecraft.options.onboardAccessibility = false;
			join(minecraft, new TitleScreen());
		}

		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server != null && server != configuredServer && minecraft.player != null) {
			configuredServer = server;
			server.execute(() -> {
				for (String command : SETUP_COMMANDS) {
					server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
				}
			});
		}
	}

	private static void join(Minecraft minecraft, TitleScreen title) {
		if (minecraft.getLevelSource().levelExists(WORLD_NAME)) {
			try (var access = minecraft.getLevelSource().createAccess(WORLD_NAME)) {
				access.deleteLevel();
				Passthrough.LOGGER.info("Deleted the last session's passthrough world");
			} catch (Exception e) {
				Passthrough.LOGGER.warn("Could not delete the old passthrough world; opening it instead", e);
				minecraft.createWorldOpenFlows().openWorld(WORLD_NAME, () -> minecraft.gui.setScreen(title));
				return;
			}
		}

		Passthrough.LOGGER.info("Creating passthrough world '{}'", WORLD_NAME);
		LevelSettings settings = new LevelSettings(
			WORLD_NAME, GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT);
		WorldOptions options = new WorldOptions(WorldOptions.randomSeed(), false, false);
		minecraft.createWorldOpenFlows().createFreshLevel(WORLD_NAME, settings, options, WorldBootstrap::voidDimensions, title);
	}

	private static WorldDimensions voidDimensions(HolderLookup.Provider registries) {
		var voidPreset = registries.lookupOrThrow(Registries.FLAT_LEVEL_GENERATOR_PRESET)
			.getOrThrow(FlatLevelGeneratorPresets.THE_VOID)
			.value();
		return WorldPresets.createNormalWorldDimensions(registries)
			.replaceOverworldGenerator(registries, new FlatLevelSource(voidPreset.settings()));
	}
}
