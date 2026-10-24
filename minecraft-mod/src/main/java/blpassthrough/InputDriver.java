package blpassthrough;

import blpassthrough.mixin.KeyMappingAccessor;
import blpassthrough.shm.Protocol;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;

public final class InputDriver {
	private static final int ATTACK = 7;
	private static final int USE = 8;
	private static final int INVENTORY = 11;
	private static final int HOTBAR_1 = 14;
	private static final int DEV_PANEL = 23;

	private int[] lastPresses;
	private double lastMouseX;
	private double lastMouseY;
	private double lastScroll;
	private int lastTyped;
	private boolean synced;
	private boolean cursorLeft;
	private boolean cursorRight;

	public void tick(Minecraft minecraft, HostState host) {
		HostState.Input input = host.input;
		if (!this.synced) {
			this.lastPresses = input.presses().clone();
			this.lastMouseX = input.mouseX();
			this.lastMouseY = input.mouseY();
			this.lastScroll = input.scroll();
			this.lastTyped = input.typedCount();
			this.synced = true;
			return;
		}

		this.type(minecraft, input);

		KeyMapping[] keys = mappings(minecraft.options);
		boolean controlling = input.has(Protocol.INPUT_CONTROLLING) && minecraft.player != null;
		Screen screen = minecraft.gui.screen();
		for (int i = 0; i < keys.length; i++) {
			int clicks = input.presses()[i] - this.lastPresses[i];
			this.lastPresses[i] = input.presses()[i];
			KeyMapping key = keys[i];
			if (i == DEV_PANEL && clicks > 0 && controlling) {
				if (screen instanceof DevPanelScreen) {
					screen.onClose();
				} else if (screen == null) {
					minecraft.gui.setScreen(new DevPanelScreen());
				}
			}
			if (key == null) {
				continue;
			}

			if (screen == null) {
				key.setDown(controlling && input.down(i));
				KeyMappingAccessor clickable = (KeyMappingAccessor) key;
				clickable.blpassthrough$setClickCount(clickable.blpassthrough$getClickCount() + Math.max(0, clicks));
			} else {
				key.setDown(false);
				if (i == INVENTORY && clicks > 0 && !screen.isInputCaptured()) {
					screen.onClose();
				}
			}
		}

		double scroll = input.scroll() - this.lastScroll;
		this.lastScroll = input.scroll();
		LocalPlayer player = minecraft.player;
		if (screen == null && player != null && scroll != 0.0) {
			int slot = player.getInventory().getSelectedSlot() - (int) Math.signum(scroll);
			player.getInventory().setSelectedSlot(Math.floorMod(slot, 9));
		} else if (screen != null && scroll != 0.0) {
			minecraft.mouseHandler.onScroll(minecraft.getWindow().handle(), 0.0, Math.signum(scroll));
		}
	}

	private void type(Minecraft minecraft, HostState.Input input) {
		int fresh = input.typedCount() - this.lastTyped;
		this.lastTyped = input.typedCount();
		if (fresh <= 0 || fresh > Protocol.TYPED_CAPACITY) {
			return;
		}
		long window = minecraft.getWindow().handle();
		for (int n = input.typedCount() - fresh; n < input.typedCount(); n++) {
			int codepoint = input.typed(n);
			if (minecraft.gui.screen() == null) {
				continue;
			}
			switch (codepoint) {
				case Protocol.TYPED_ESCAPE -> this.key(minecraft, window, InputConstants.KEY_ESCAPE, codepoint);
				case Protocol.TYPED_BACKSPACE -> this.key(minecraft, window, InputConstants.KEY_BACKSPACE, codepoint);
				case Protocol.TYPED_ENTER -> this.key(minecraft, window, InputConstants.KEY_RETURN, codepoint);
				default -> minecraft.keyboardHandler.charTyped(window, new CharacterEvent(codepoint));
			}
		}
	}

	private void key(Minecraft minecraft, long window, int scancode, int keycode) {
		minecraft.keyboardHandler.keyPress(window, InputConstants.PRESS, new KeyEvent(scancode, keycode, 0));
		minecraft.keyboardHandler.keyPress(window, InputConstants.RELEASE, new KeyEvent(scancode, keycode, 0));
	}

	public void frame(Minecraft minecraft, HostState host) {
		if (!this.synced) {
			return;
		}

		HostState.Input input = host.input;
		double dx = input.mouseX() - this.lastMouseX;
		double dy = input.mouseY() - this.lastMouseY;
		this.lastMouseX = input.mouseX();
		this.lastMouseY = input.mouseY();

		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}

		Screen screen = minecraft.gui.screen();
		if (screen == null) {
			this.cursorLeft = false;
			this.cursorRight = false;
			double s = minecraft.options.sensitivity().get() * 0.6 + 0.2;
			double scale = s * s * s * 8.0;
			int invert = minecraft.options.invertMouseY().get() ? -1 : 1;
			player.turn(dx * scale, dy * scale * invert);
			return;
		}

		Window window = minecraft.getWindow();
		double x = input.cursorX() * window.getWidth();
		double y = input.cursorY() * window.getHeight();
		minecraft.mouseHandler.onMove(window.handle(), x, y, 0.0, 0.0);
		this.cursorLeft = this.button(minecraft, InputConstants.MOUSE_BUTTON_LEFT, input.down(ATTACK), this.cursorLeft);
		this.cursorRight = this.button(minecraft, InputConstants.MOUSE_BUTTON_RIGHT, input.down(USE), this.cursorRight);
	}

	private boolean button(Minecraft minecraft, int button, boolean down, boolean wasDown) {
		if (down != wasDown) {
			minecraft.mouseHandler.onButton(minecraft.getWindow().handle(), new MouseButtonInfo(button, 0), down ? 1 : 0);
		}
		return down;
	}

	private static KeyMapping[] mappings(Options o) {
		KeyMapping[] keys = new KeyMapping[Protocol.KEY_COUNT];
		keys[0] = o.keyUp;
		keys[1] = o.keyDown;
		keys[2] = o.keyLeft;
		keys[3] = o.keyRight;
		keys[4] = o.keyJump;
		keys[5] = o.keyShift;
		keys[6] = o.keySprint;
		keys[ATTACK] = o.keyAttack;
		keys[USE] = o.keyUse;
		keys[9] = o.keyPickItem;
		keys[10] = o.keyDrop;
		keys[INVENTORY] = o.keyInventory;
		keys[12] = o.keySwapOffhand;
		keys[13] = o.keyTogglePerspective;
		for (int i = 0; i < 9; i++) {
			keys[HOTBAR_1 + i] = o.keyHotbarSlots[i];
		}
		return keys;
	}
}
