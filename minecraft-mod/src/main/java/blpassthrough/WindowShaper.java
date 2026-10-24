package blpassthrough;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

final class WindowShaper {
	private static final int HEIGHT = 720;
	private static final int GUI_SCALE = 2;

	private static int shapedFor;

	private WindowShaper() {
	}

	static void match(Minecraft minecraft, HostState host) {
		if (host.aspect <= 0.0F) {
			return;
		}

		int width = Math.round(HEIGHT * host.aspect);
		int key = width * 31 + HEIGHT;
		Window window = minecraft.getWindow();
		if (key == shapedFor || minecraft.options.fullscreen().get()) {
			return;
		}

		shapedFor = key;
		window.setWindowed(width, HEIGHT);
		minecraft.options.guiScale().set(GUI_SCALE);
		Passthrough.LOGGER.info("Resized Minecraft to {}x{} to match Bannerlord's aspect ratio", width, HEIGHT);
	}
}
