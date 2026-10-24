package blpassthrough;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class DevPanelScreen extends Screen {
	private static final int MARGIN = 8;
	private static final int BUTTON_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 18;
	private static final int GAP = 2;
	private static final int TAB_WIDTH = 64;
	private static final int CELL = 18;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREY = 0xFFA0A0A0;
	private static final int PANEL = 0xC0101010;
	private static final int HOVER = 0x80FFFFFF;
	private static final int SEARCH_WIDTH = 160;
	private static final int MAX_QUERY = 50;

	private static int selectedTab;
	private static int page;
	private static String query = "";
	private static final String[][] KIT_NAMES = {
		{"Deri", "Leather"}, {"Demir", "Iron"}, {"Altın", "Gold"}, {"Elmas", "Diamond"}, {"Netherite", "Netherite"},
	};

	private List<CreativeModeTab> tabs = List.of();
	private List<ItemStack> items = List.of();
	private int gridX;
	private int gridY;
	private int columns;
	private int rows;
	private int infoY;
	private EditBox search;

	public DevPanelScreen() {
		super(Component.literal(t("Geliştirici paneli", "Developer panel")));
	}

	private static String t(String turkish, String english) {
		return Passthrough.get().isTurkish() ? turkish : english;
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	@Override
	protected void init() {
		WorldBridge bridge = Passthrough.get().worldBridge();
		int y = MARGIN + 12;
		y = this.button(y, t("Ölünce dirilme: ", "Revive on death: ") + onOff(bridge.autoRevive()), () -> bridge.setAutoRevive(!bridge.autoRevive()));
		y = this.button(y, t("Ölümsüzlük: ", "Invulnerable: ") + onOff(bridge.invulnerable()), () -> bridge.setInvulnerable(!bridge.invulnerable()));
		for (int tier = 1; tier <= WorldBridge.KIT_TIERS; tier++) {
			int kit = tier;
			y = this.button(y, "Kit " + tier + ": " + t(KIT_NAMES[tier - 1][0], KIT_NAMES[tier - 1][1]), () -> bridge.devTierKit(kit));
		}
		y = this.button(y, t("Can ve açlığı doldur", "Fill health and hunger"), bridge::devHeal);
		y = this.button(y, t("Yakındaki düşmanları öldür", "Kill nearby enemies"), bridge::devKillNearby);
		y = this.button(y, t("Steve'in bloklarını temizle", "Clear Steve's blocks"), bridge::devClearBlocks);
		y = this.button(y, t("Envanteri boşalt", "Empty inventory"), bridge::devClearInventory);
		y = this.button(y, t("Kapat (F10 / Esc)", "Close (F10 / Esc)"), this::onClose);
		this.infoY = y + 4;

		LocalPlayer player = this.minecraft.player;
		if (player != null) {
			CreativeModeTabs.tryRebuildTabContents(player.connection.enabledFeatures(), false, player.level().registryAccess());
			List<CreativeModeTab> categories = new ArrayList<>();
			for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
				if (tab.getType() == CreativeModeTab.Type.CATEGORY && tab.shouldDisplay()) {
					categories.add(tab);
				}
			}
			this.tabs = categories;
		}
		selectedTab = Math.clamp(selectedTab, 0, Math.max(0, this.tabs.size() - 1));
		this.items = this.findItems();

		int left = MARGIN * 2 + BUTTON_WIDTH;
		int right = this.width - MARGIN;
		int perRow = Math.max(1, (right - left) / (TAB_WIDTH + GAP));
		int tabY = MARGIN + 12;
		for (int i = 0; i < this.tabs.size(); i++) {
			int index = i;
			int x = left + (i % perRow) * (TAB_WIDTH + GAP);
			int ty = tabY + (i / perRow) * (BUTTON_HEIGHT + GAP);
			String name = this.tabs.get(i).getDisplayName().getString();
			this.addRenderableWidget(Button.builder(Component.literal(i == selectedTab ? "> " + name : name), b -> {
				selectedTab = index;
				page = 0;
				this.rebuildWidgets();
			}).bounds(x, ty, TAB_WIDTH, BUTTON_HEIGHT).build());
		}

		int tabRows = (this.tabs.size() + perRow - 1) / perRow;
		int searchY = tabY + tabRows * (BUTTON_HEIGHT + GAP) + 2;
		this.search = new EditBox(this.font, left, searchY, SEARCH_WIDTH, BUTTON_HEIGHT, Component.literal(t("Ara", "Search")));
		this.search.setMaxLength(MAX_QUERY);
		this.search.setHint(Component.literal(t("Eşya ara…", "Search items…")));
		this.search.setValue(query);
		this.search.setResponder(text -> {
			query = text;
			page = 0;
			this.items = this.findItems();
		});
		this.addRenderableWidget(this.search);
		if (!query.isEmpty()) {
			this.setInitialFocus(this.search);
		}
		this.gridX = left;
		this.gridY = searchY + BUTTON_HEIGHT + 4;
		this.columns = Math.max(1, (right - left) / CELL);
		this.rows = Math.max(1, (this.height - MARGIN - BUTTON_HEIGHT - GAP - this.gridY) / CELL);
		int pages = this.pages();
		page = Math.clamp(page, 0, pages - 1);
		int pagerY = this.gridY + this.rows * CELL + GAP;
		this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
			page = Math.max(0, page - 1);
			this.rebuildWidgets();
		}).bounds(left, pagerY, 20, BUTTON_HEIGHT).build());
		this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
			page = Math.min(this.pages() - 1, page + 1);
			this.rebuildWidgets();
		}).bounds(left + 24, pagerY, 20, BUTTON_HEIGHT).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(MARGIN / 2, MARGIN / 2, this.width - MARGIN / 2, this.height - MARGIN / 2, PANEL);
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.text(this.font, this.title, MARGIN, MARGIN, WHITE);

		WorldBridge bridge = Passthrough.get().worldBridge();
		LocalPlayer player = this.minecraft.player;
		List<String> info = new ArrayList<>();
		info.add(t("Mod: ", "Mode: ") + (bridge.isCampaign()
			? t("Kampanya (envanter kayda yazılır)", "Campaign (inventory is saved)")
			: t("Özel savaş", "Custom battle")));
		if (player != null) {
			info.add(String.format("Steve: %.0f %.0f %.0f", player.getX(), player.getY(), player.getZ()));
			info.add(String.format(t("Can: %.0f / %.0f", "Health: %.0f / %.0f"), player.getHealth(), player.getMaxHealth()));
		}
		info.add(t("Asker kopyaları: ", "Soldier proxies: ") + bridge.proxyCount());
		info.add(t("Bariyer blokları: ", "Barrier blocks: ") + bridge.barrierCount());
		int y = this.infoY;
		for (String line : info) {
			graphics.text(this.font, line, MARGIN, y, GREY);
			y += 10;
		}

		graphics.text(this.font, t("Sol tık: 1 adet, sağ tık: deste", "Left click: one, right click: a stack"), this.gridX + 48, this.gridY + this.rows * CELL + GAP + 5, GREY);
		graphics.text(this.font, t("Sayfa ", "Page ") + (page + 1) + " / " + this.pages(), this.gridX + 48 + 190, this.gridY + this.rows * CELL + GAP + 5, GREY);
		if (this.items.isEmpty() && !query.isBlank()) {
			graphics.text(this.font, t("Eşleşen eşya yok", "No matching items"), this.gridX, this.gridY + 4, GREY);
		}

		int first = page * this.columns * this.rows;
		ItemStack hovered = null;
		for (int i = 0; i < this.columns * this.rows && first + i < this.items.size(); i++) {
			int x = this.gridX + (i % this.columns) * CELL;
			int cy = this.gridY + (i / this.columns) * CELL;
			ItemStack stack = this.items.get(first + i);
			if (mouseX >= x && mouseX < x + CELL && mouseY >= cy && mouseY < cy + CELL) {
				graphics.fill(x, cy, x + CELL, cy + CELL, HOVER);
				hovered = stack;
			}
			graphics.item(stack, x + 1, cy + 1);
		}
		if (hovered != null) {
			graphics.setTooltipForNextFrame(this.font, hovered, mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		ItemStack stack = this.itemAt(event.x(), event.y());
		if (stack != null) {
			ItemStack give = stack.copy();
			give.setCount(event.button() == InputConstants.MOUSE_BUTTON_RIGHT ? give.getMaxStackSize() : 1);
			Passthrough.get().worldBridge().devGive(give);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private List<ItemStack> findItems() {
		String needle = normalize(query.strip());
		if (needle.isEmpty()) {
			return this.tabs.isEmpty() ? List.of() : new ArrayList<>(this.tabs.get(selectedTab).getDisplayItems());
		}
		Map<Item, ItemStack> found = new LinkedHashMap<>();
		for (CreativeModeTab tab : this.tabs) {
			for (ItemStack stack : tab.getDisplayItems()) {
				if (found.containsKey(stack.getItem())) {
					continue;
				}
				String name = normalize(stack.getHoverName().getString());
				String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().replace('_', ' ');
				if (name.contains(needle) || id.contains(needle)) {
					found.put(stack.getItem(), stack);
				}
			}
		}
		return new ArrayList<>(found.values());
	}

	private static String normalize(String text) {
		return text.toLowerCase(Locale.ROOT).replace('ı', 'i').replace(String.valueOf((char) 0x307), "");
	}

	private ItemStack itemAt(double mouseX, double mouseY) {
		int column = (int) Math.floor((mouseX - this.gridX) / CELL);
		int row = (int) Math.floor((mouseY - this.gridY) / CELL);
		if (column < 0 || row < 0 || column >= this.columns || row >= this.rows) {
			return null;
		}
		int index = page * this.columns * this.rows + row * this.columns + column;
		return index < this.items.size() ? this.items.get(index) : null;
	}

	private int pages() {
		return Math.max(1, (this.items.size() + this.columns * this.rows - 1) / (this.columns * this.rows));
	}

	private int button(int y, String label, Runnable action) {
		this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
			action.run();
			this.rebuildWidgets();
		}).bounds(MARGIN, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		return y + BUTTON_HEIGHT + GAP;
	}

	private static String onOff(boolean value) {
		return value ? t("AÇIK", "ON") : t("KAPALI", "OFF");
	}
}
