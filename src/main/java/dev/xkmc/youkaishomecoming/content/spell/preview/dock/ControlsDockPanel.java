package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import dev.xkmc.youkaishomecoming.content.spell.preview.EditorTextBoxes;
import dev.xkmc.youkaishomecoming.content.spell.preview.OrthographicViewport;
import dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder;
import dev.xkmc.youkaishomecoming.content.spell.preview.SpellEditorLocalization;
import dev.xkmc.youkaishomecoming.content.spell.preview.VirtualSpellScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 可停靠的播放控制面板。从 SpellPreviewScreen.init() 中提取
 * 所有底部控制按钮（播放/速度/距离/HP/Phase/Range/Target 属性等）。
 */
@OnlyIn(Dist.CLIENT)
public class ControlsDockPanel implements DockPanel {

	private static final int BUTTON_HEIGHT = 16;
	private static final int BUTTON_SPACING = 2;
	private static final int DROPDOWN_ITEM_H = 18;
	private static final int DROPDOWN_MAX_VISIBLE = 10;

	private final VirtualSpellScene scene;
	private final OrthographicViewport viewport;
	private final Runnable rebuildCallback;
	private final Runnable resetPhaseCallback;
	private final Supplier<List<ResourceLocation>> spellOptionsSupplier;
	private final Supplier<ResourceLocation> currentSpellIdSupplier;
	private final Supplier<String> currentSpellLabelSupplier;
	private final Function<ResourceLocation, String> spellDisplayFormatter;
	private final Consumer<ResourceLocation> switchSpellCallback;
	private final Runnable newSpellCallback;
	private final Runnable deleteSpellCallback;
	private final Supplier<Boolean> canDeleteSpellSupplier;
	private final Supplier<Boolean> spellDraftModeSupplier;
	private final Supplier<String> defaultSpellNamespaceSupplier;
	private final Function<String, @Nullable Component> createSpellCallback;
	private final Consumer<Integer> cyclePhaseCallback;
	private final Supplier<String> currentPhaseNameSupplier;
	private final Consumer<String> renamePhaseCallback;
	private final Runnable addPhaseCallback;
	private final Runnable deletePhaseCallback;
	private final Supplier<Boolean> canDeletePhaseSupplier;

	private int x, y, w, h;
	private final List<Button> buttons = new ArrayList<>();
	private final List<EditBox> editBoxes = new ArrayList<>();
	private final List<ControlLabel> labels = new ArrayList<>();
	private Button spellDropdownButton;
	private Button spellNewButton;
	private Button spellDeleteButton;
	private EditBox newSpellIdBox;
	@Nullable private Component newSpellCreationError;
	private int newSpellCreationMessageY;
	private DropdownOverlay spellDropdown;
	private int spellDropdownHoverIndex = -1;
	private int spellDropdownScrollOffset = 0;
	private ConfirmOverlay spellDeleteConfirm;
	private int spellDeleteConfirmHoverIndex = -1;
	private Button actionMenuButton;
	private ActionMenuOverlay actionMenu;
	private int actionMenuHoverIndex = -1;
	private Consumer<AbstractWidget> addWidgetCallback;
	private Consumer<GuiEventListener> removeWidgetCallback;
	private boolean active;

	private static final Set<String> COLLAPSED_SPELL_FOLDERS = new HashSet<>();

	private record DropdownItem(
			@Nullable ResourceLocation value,
			String label,
			boolean isFolder,
			String folderKey,
			int depth,
			boolean isSelected
	) {}

	private record DropdownOverlay(
			List<DropdownItem> items,
			int selectedIndex
	) {}

	private record ConfirmOverlay(String[] options) {}

	private record MenuEntry(String label, Runnable action, boolean active) {}

	private record ActionMenuOverlay(String title, List<MenuEntry> entries) {}

	private record ControlLabel(int x, int y, String text) {}

	public ControlsDockPanel(VirtualSpellScene scene,
							 OrthographicViewport viewport,
							 Runnable rebuildCallback,
							 Runnable resetPhaseCallback,
							 Supplier<List<ResourceLocation>> spellOptionsSupplier,
							 Supplier<ResourceLocation> currentSpellIdSupplier,
							 Supplier<String> currentSpellLabelSupplier,
							 Function<ResourceLocation, String> spellDisplayFormatter,
							 Consumer<ResourceLocation> switchSpellCallback,
							 Runnable newSpellCallback,
							 Runnable deleteSpellCallback,
							 Supplier<Boolean> canDeleteSpellSupplier,
							 Supplier<Boolean> spellDraftModeSupplier,
							 Supplier<String> defaultSpellNamespaceSupplier,
							 Function<String, @Nullable Component> createSpellCallback,
							 Consumer<Integer> cyclePhaseCallback,
							 Supplier<String> currentPhaseNameSupplier,
							 Consumer<String> renamePhaseCallback,
							 Runnable addPhaseCallback,
							 Runnable deletePhaseCallback,
							 Supplier<Boolean> canDeletePhaseSupplier) {
		this.scene = scene;
		this.viewport = viewport;
		this.rebuildCallback = rebuildCallback;
		this.resetPhaseCallback = resetPhaseCallback;
		this.spellOptionsSupplier = spellOptionsSupplier;
		this.currentSpellIdSupplier = currentSpellIdSupplier;
		this.currentSpellLabelSupplier = currentSpellLabelSupplier;
		this.spellDisplayFormatter = spellDisplayFormatter;
		this.switchSpellCallback = switchSpellCallback;
		this.newSpellCallback = newSpellCallback;
		this.deleteSpellCallback = deleteSpellCallback;
		this.canDeleteSpellSupplier = canDeleteSpellSupplier;
		this.spellDraftModeSupplier = spellDraftModeSupplier;
		this.defaultSpellNamespaceSupplier = defaultSpellNamespaceSupplier;
		this.createSpellCallback = createSpellCallback;
		this.cyclePhaseCallback = cyclePhaseCallback;
		this.currentPhaseNameSupplier = currentPhaseNameSupplier;
		this.renamePhaseCallback = renamePhaseCallback;
		this.addPhaseCallback = addPhaseCallback;
		this.deletePhaseCallback = deletePhaseCallback;
		this.canDeletePhaseSupplier = canDeletePhaseSupplier;
	}

	/**
	 * 设置 widget 注册/注销回调。必须在 init() 前调用。
	 */
	public void setWidgetCallbacks(Consumer<AbstractWidget> addWidget, Consumer<GuiEventListener> removeWidget) {
		this.addWidgetCallback = addWidget;
		this.removeWidgetCallback = removeWidget;
	}

	/**
	 * 创建所有控制按钮并注册到 Screen。
	 * 在 Screen.init() 或 setBounds 后调用。
	 */
	public void buildButtons() {
		clearButtons();
		boolean draftMode = isDraftMode();

		int row1Y = y + 4;
		int row2Y = row1Y + BUTTON_HEIGHT + BUTTON_SPACING;
		int row3Y = row2Y + BUTTON_HEIGHT + BUTTON_SPACING;
		int row4Y = row3Y + BUTTON_HEIGHT + BUTTON_SPACING;
		int row5Y = row4Y + BUTTON_HEIGHT + BUTTON_SPACING;

		int bx;

		bx = x + 4;
		if (draftMode) {
			addSpellControls(bx, row1Y, true);
			addNewSpellControls(x + 4, row2Y);
			applyWidgetVisibility();
			return;
		}

		// Row 1: Playback controls
		bx = addButton(bx, row1Y, 40, "\u25B6/\u275A\u275A", btn -> scene.togglePlayPause());
		bx = addButton(bx, row1Y, 20, "\u25A0", btn -> resetPhaseCallback.run());
		bx = addButton(bx, row1Y, 20, "\u25B8", btn -> scene.step());
		bx = addMenuButton(bx, row1Y, 40, scene.getPilotTierLabel(), this::openPilotMenu);
		bx = addButton(bx, row1Y, 32, scene.isPilotDebugOverlay() ? "Dbg:ON" : "Dbg", btn -> {
			scene.togglePilotDebugOverlay();
			rebuildCallback.run();
		});
		bx += 8;
		addSpellControls(bx, row1Y, false);

		// Row 3: compact preview simulation settings.
		bx = x + 4;
		bx = addLabel(bx, row3Y, 44, "Preview");
		bx = addMenuButton(bx, row3Y, 62, "Speed", this::openSpeedMenu);
		int curLimit = PreviewCardHolder.getMaxEntityCount();
		String limitHint = curLimit >= 1000 ? "Limit:" + (curLimit / 1000) + "k" : "Limit:" + curLimit;
		bx = addEditBox(bx, row3Y, 52, limitHint, val -> {
			try {
				String s = val.toLowerCase().replace("k", "000").trim();
				PreviewCardHolder.setMaxEntityCount(Integer.parseInt(s));
			} catch (NumberFormatException ignored) {}
		});
		bx = addEditBox(bx, row3Y, 46, "HP:" + (int) (scene.getHealthRatio() * 100) + "%", val -> {
			try {
				String s = val.replace("%", "").trim();
				float v = Float.parseFloat(s);
				if (v > 1) v = v / 100f;
				scene.setHealthRatio(v);
			} catch (NumberFormatException ignored) {}
		});
		bx = addEditBox(bx, row3Y, 56, "Power:" + formatDimension(scene.getCasterPower()), val -> {
			try {
				scene.setCasterPower(Double.parseDouble(val.trim()));
			} catch (NumberFormatException ignored) {}
		});
		bx = addMenuButton(bx, row3Y, 48, "Range", this::openRangeMenu);
		bx = addMenuButton(bx, row3Y, 58, "Markers", this::openMarkerMenu);
		bx = addMenuButton(bx, row3Y, 44, "Focus", this::openFocusMenu);
		addMenuButton(bx, row3Y, 48, "Reset", this::openResetPositionMenu);

		// Row 2: phase selection.
		bx = x + 4;
		bx = addButton(bx, row2Y, 40, "Label:", btn -> {});
		bx = addButton(bx, row2Y, 16, "<", btn -> cyclePhaseCallback.accept(-1));
		bx = addTextEditBox(bx, row2Y, 84,
				currentPhaseNameSupplier.get(),
				"Display Name", 48,
				s -> !s.contains("\n") && !s.contains("\r"),
				renamePhaseCallback);
		bx = addButton(bx, row2Y, 16, ">", btn -> cyclePhaseCallback.accept(1));
		bx = addButton(bx, row2Y, 20, "+", btn -> addPhaseCallback.run());
		Button deleteButton = Button.builder(Component.literal("-"), btn -> deletePhaseCallback.run())
				.bounds(bx, row2Y, 20, BUTTON_HEIGHT).build();
		deleteButton.active = canDeletePhaseSupplier.get();
		buttons.add(deleteButton);
		if (addWidgetCallback != null) {
			addWidgetCallback.accept(deleteButton);
		}

		// Row 4: entity-target simulation state.
		bx = x + 4;
		bx = addLabel(bx, row4Y, 70, "Entity Target");
		bx = addEditBox(bx, row4Y, 46, "Dist:" + (int) scene.getTargetDistance(), val -> {
			try { scene.setTargetDistance(Float.parseFloat(val)); } catch (NumberFormatException ignored) {}
		});
		bx = addMenuButton(bx, row4Y, 46, "State", this::openTargetStateMenu);
		bx = addEditBox(bx, row4Y, 48, "THP:" + (int) (scene.getTargetHealthRatio() * 100) + "%", val -> {
			try {
				String s = val.replace("%", "").trim();
				float v = Float.parseFloat(s);
				if (v > 1) v = v / 100f;
				scene.setTargetHealthRatio(v);
				rebuildCallback.run();
			} catch (NumberFormatException ignored) {}
		});
		bx = addEditBox(bx, row4Y, 50, "Height:" + (int) scene.getTargetHeight(), val -> {
			try {
				scene.setTargetHeight(Double.parseDouble(val));
				rebuildCallback.run();
			} catch (NumberFormatException ignored) {}
		});

		// Row 5: compact preview-only block target transform. This state is never serialized into spell JSON.
		bx = x + 4;
		var blockPos = scene.getBlockTargetPos();
		var boxSize = scene.getTargetBoxSize();
		bx = addLabel(bx, row5Y, 70, "Block Target");
		bx = addVectorEditBox(bx, row5Y, 170, "XYZ", blockPos, scene::setBlockTargetPos);
		addVectorEditBox(bx, row5Y, 170, "WHD", boxSize, scene::setTargetBoxSize);
		applyWidgetVisibility();
	}

	private int addButton(int bx, int by, int bw, String label, Button.OnPress action) {
		String text = SpellEditorLocalization.t(label);
		bw = buttonWidth(text, bw);
		Button btn = Button.builder(Component.literal(text), action)
				.bounds(bx, by, bw, BUTTON_HEIGHT).build();
		buttons.add(btn);
		if (addWidgetCallback != null) {
			addWidgetCallback.accept(btn);
		}
		return bx + bw + BUTTON_SPACING;
	}

	private int addMenuButton(int bx, int by, int bw, String label, Consumer<Button> opener) {
		String text = SpellEditorLocalization.t(label) + " \u25BE";
		bw = buttonWidth(text, bw);
		Button btn = Button.builder(Component.literal(text), opener::accept)
				.bounds(bx, by, bw, BUTTON_HEIGHT).build();
		buttons.add(btn);
		if (addWidgetCallback != null) {
			addWidgetCallback.accept(btn);
		}
		return bx + bw + BUTTON_SPACING;
	}

	private int addLabel(int bx, int by, int bw, String label) {
		String text = SpellEditorLocalization.t(label);
		bw = Math.max(bw, Minecraft.getInstance().font.width(text) + 8);
		labels.add(new ControlLabel(bx + 2, by + 4, text));
		return bx + bw + BUTTON_SPACING;
	}

	private int buttonWidth(String text, int minWidth) {
		return Math.max(minWidth, Minecraft.getInstance().font.width(text) + 12);
	}

	private void openSpeedMenu(Button anchor) {
		List<MenuEntry> entries = new ArrayList<>();
		for (int i = 0; i < VirtualSpellScene.SPEED_OPTIONS.length; i++) {
			float speed = VirtualSpellScene.SPEED_OPTIONS[i];
			String label = speed < 1 ? speed + "x" : ((int) speed) + "x";
			if (i == scene.getSpeedIndex()) {
				label = "* " + label;
			}
			final int idx = i;
			entries.add(new MenuEntry(label, () -> scene.setSpeedIndex(idx), true));
		}
		openActionMenu(anchor, "Speed", entries);
	}

	/** AI tier menu — same amp semantics as player AUTO_DODGE buff (0/1/2). */
	private void openPilotMenu(Button anchor) {
		List<MenuEntry> entries = new ArrayList<>();
		boolean off = !scene.isPilotEnabled();
		entries.add(new MenuEntry((off ? "* " : "") + "OFF", () -> {
			scene.setPilotEnabled(false);
			rebuildCallback.run();
		}, true));
		String[] labels = {
				"I  Rescue (amp 0)",
				"II Assist (amp 1)",
				"III Takeover (amp 2)"
		};
		for (int t = 0; t <= 2; t++) {
			final int tier = t;
			boolean sel = scene.isPilotEnabled() && scene.getPilotTier() == tier;
			String label = (sel ? "* " : "") + labels[t];
			entries.add(new MenuEntry(label, () -> {
				scene.setPilotTier(tier);
				rebuildCallback.run();
			}, true));
		}
		openActionMenu(anchor, "AI Pilot Tier", entries);
	}

	private void openRangeMenu(Button anchor) {
		List<MenuEntry> entries = new ArrayList<>();
		for (int range : new int[]{50, 100, 200, 500}) {
			final float r = range;
			entries.add(new MenuEntry(String.valueOf(range), () -> {
				viewport.setGridExtent(r);
				viewport.setClipDepth(r * 4);
			}, true));
		}
		openActionMenu(anchor, "Viewport Range", entries);
	}

	private void openMarkerMenu(Button anchor) {
		List<MenuEntry> entries = new ArrayList<>();
		entries.add(new MenuEntry(viewport.isShowCasterMarker() ? "Caster Marker: ON" : "Caster Marker: OFF", () -> {
			viewport.setShowCasterMarker(!viewport.isShowCasterMarker());
			rebuildCallback.run();
		}, true));
		entries.add(new MenuEntry(viewport.isShowTargetMarker() ? "Target Marker: ON" : "Target Marker: OFF", () -> {
			viewport.setShowTargetMarker(!viewport.isShowTargetMarker());
			rebuildCallback.run();
		}, true));
		openActionMenu(anchor, "Markers", entries);
	}

	private void openTargetStateMenu(Button anchor) {
		openActionMenu(anchor, "Target State", List.of(
				new MenuEntry(scene.isTargetOnGround() ? "Ground: Y" : "Ground: N", () -> {
					scene.setTargetOnGround(!scene.isTargetOnGround());
					rebuildCallback.run();
				}, true),
				new MenuEntry(scene.isTargetFlying() ? "Flying: Y" : "Flying: N", () -> {
					scene.setTargetFlying(!scene.isTargetFlying());
					rebuildCallback.run();
				}, true),
				new MenuEntry(scene.isTargetFallFlying() ? "Elytra: Y" : "Elytra: N", () -> {
					scene.setTargetFallFlying(!scene.isTargetFallFlying());
					rebuildCallback.run();
				}, true)
		));
	}

	private void openFocusMenu(Button anchor) {
		openActionMenu(anchor, "Focus", List.of(
				new MenuEntry("Target", () -> viewport.focusOnWorldPos(scene.getTargetPos()), true),
				new MenuEntry("Block Target", () -> viewport.focusOnWorldPos(scene.getBlockTargetPos()), true),
				new MenuEntry("Caster", () -> viewport.focusOnWorldPos(scene.getCasterPos()), true)
		));
	}

	private void openResetPositionMenu(Button anchor) {
		openActionMenu(anchor, "Reset Position", List.of(
				new MenuEntry("Target Position", () -> {
					scene.resetTargetPos();
					rebuildCallback.run();
				}, true),
				new MenuEntry("Block Target Position", () -> {
					scene.resetBlockTargetPos();
					rebuildCallback.run();
				}, true),
				new MenuEntry("Caster Position", () -> {
					scene.resetCasterPos();
					rebuildCallback.run();
				}, true)
		));
	}

	private void openActionMenu(Button anchor, String title, List<MenuEntry> entries) {
		closeSpellDropdown();
		closeSpellDeleteConfirm();
		actionMenuButton = anchor;
		actionMenu = new ActionMenuOverlay(title, List.copyOf(entries));
		actionMenuHoverIndex = -1;
	}

	private void addSpellControls(int bx, int by, boolean draftMode) {
		int labelW = 36;
		int dropdownW = Math.max(96, Math.min(220, w / 4));
		int nextX = addButton(bx, by, labelW, "Spell:", btn -> {});
		String currentLabel = currentSpellLabelSupplier.get();
		String displayText = fitToWidth(currentLabel, dropdownW - 14);
		Button btn = Button.builder(Component.literal(displayText + " \u25BC"), b -> openSpellDropdown())
				.bounds(nextX, by, dropdownW, BUTTON_HEIGHT).build();
		spellDropdownButton = btn;
		buttons.add(btn);
		if (addWidgetCallback != null) {
			addWidgetCallback.accept(btn);
		}
		if (draftMode) {
			return;
		}
		int newX = nextX + dropdownW + BUTTON_SPACING;
		Button newBtn = Button.builder(Component.literal("+"), b -> newSpellCallback.run())
				.bounds(newX, by, 20, BUTTON_HEIGHT).build();
		newBtn.active = !draftMode;
		spellNewButton = newBtn;
		buttons.add(newBtn);
		if (addWidgetCallback != null) {
			addWidgetCallback.accept(newBtn);
		}
		int deleteX = newX + 20 + BUTTON_SPACING;
		Button deleteBtn = Button.builder(Component.literal("-"), b -> openSpellDeleteConfirm())
				.bounds(deleteX, by, 20, BUTTON_HEIGHT).build();
		deleteBtn.active = !draftMode && canDeleteSpellSupplier.get();
		spellDeleteButton = deleteBtn;
		buttons.add(deleteBtn);
		if (addWidgetCallback != null) {
			addWidgetCallback.accept(deleteBtn);
		}
	}

	private void addNewSpellControls(int bx, int by) {
		String label = SpellEditorLocalization.t("New Spell ID");
		Component buttonText = Component.translatable("youkaishomecoming.spell_editor.create.button");
		int buttonW = Math.max(68, Minecraft.getInstance().font.width(buttonText) + 12);
		int right = x + w - 4;
		int innerW = Math.max(20, right - bx);
		int labelW = Math.max(72, Minecraft.getInstance().font.width(label) + 8);
		int inputX = bx;
		int minimumInputW = 80;
		boolean stacked = innerW < minimumInputW + BUTTON_SPACING + buttonW;
		if (!stacked && innerW >= labelW + BUTTON_SPACING + minimumInputW + BUTTON_SPACING + buttonW) {
			labels.add(new ControlLabel(bx + 2, by + 4, label));
			inputX += labelW + BUTTON_SPACING;
		}
		int inputW = stacked ? innerW : Math.min(260,
				right - inputX - buttonW - BUTTON_SPACING);
		int buttonX = stacked ? bx : inputX + inputW + BUTTON_SPACING;
		int buttonY = stacked ? by + BUTTON_HEIGHT + BUTTON_SPACING : by;
		int fittedButtonW = stacked ? Math.min(buttonW, innerW) : buttonW;
		addTextEditBox(inputX, by, inputW, "",
				defaultSpellNamespaceSupplier.get() + ":spell_name", 96,
				s -> !s.contains("\n") && !s.contains("\r") && s.indexOf(' ') < 0,
				this::submitNewSpell);
		newSpellIdBox = editBoxes.get(editBoxes.size() - 1);
		newSpellIdBox.setResponder(value -> newSpellCreationError = null);
		Button create = Button.builder(buttonText, button -> submitNewSpell(newSpellIdBox.getValue()))
				.bounds(buttonX, buttonY, fittedButtonW, BUTTON_HEIGHT).build();
		buttons.add(create);
		if (addWidgetCallback != null) addWidgetCallback.accept(create);
		newSpellCreationMessageY = buttonY + BUTTON_HEIGHT + BUTTON_SPACING + 4;
	}

	private void submitNewSpell(String rawId) {
		newSpellCreationError = createSpellCallback.apply(rawId);
	}

	private int addEditBox(int bx, int by, int bw, String hint, java.util.function.Consumer<String> onSubmit) {
		// Preview controls are transient state rather than serialized editor data.
		// Apply valid numeric input as it is typed so a focus change cannot silently
		// discard the value (the Enter handler remains as an explicit confirmation).
		return addTextEditBox(bx, by, bw, "", hint, 16, s -> s.matches("[0-9.%\\-]*"), onSubmit, true);
	}

	private static String formatDimension(double value) {
		String text = String.format(Locale.ROOT, "%.2f", value);
		return text.replaceAll("0+$", "").replaceAll("\\.$", "");
	}

	private int addVectorEditBox(int bx, int by, int bw, String label, net.minecraft.world.phys.Vec3 value,
								 java.util.function.Consumer<net.minecraft.world.phys.Vec3> onSubmit) {
		String hint = label + ": " + formatDimension(value.x) + ", " + formatDimension(value.y) + ", " + formatDimension(value.z);
		return addTextEditBox(bx, by, bw, "", hint, 64,
				s -> s.matches("[0-9eE+.,;\\s\\-]*"),
				s -> parseVector(s).ifPresent(value3 -> {
					onSubmit.accept(value3);
					rebuildCallback.run();
				}));
	}

	private static java.util.Optional<net.minecraft.world.phys.Vec3> parseVector(String input) {
		String[] values = input.trim().split("[,;\\s]+", -1);
		if (values.length != 3) return java.util.Optional.empty();
		try {
			return java.util.Optional.of(new net.minecraft.world.phys.Vec3(
					Double.parseDouble(values[0]), Double.parseDouble(values[1]), Double.parseDouble(values[2])));
		} catch (NumberFormatException ignored) {
			return java.util.Optional.empty();
		}
	}

	private int addTextEditBox(int bx, int by, int bw, String value, String hint, int maxLength,
							   java.util.function.Predicate<String> filter,
							   java.util.function.Consumer<String> onSubmit) {
		return addTextEditBox(bx, by, bw, value, hint, maxLength, filter, onSubmit, false);
	}

	private int addTextEditBox(int bx, int by, int bw, String value, String hint, int maxLength,
							   java.util.function.Predicate<String> filter,
							   java.util.function.Consumer<String> onSubmit,
							   boolean submitOnChange) {
		EditBox box = new EditBox(Minecraft.getInstance().font, bx, by, bw, BUTTON_HEIGHT, Component.empty());
		EditorTextBoxes.configure(box);
		box.setMaxLength(maxLength);
		box.setValue(value);
		box.setHint(Component.literal(SpellEditorLocalization.t(hint)).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
		box.setResponder(submitOnChange ? val -> {
			if (!val.isBlank()) onSubmit.accept(val);
		} : val -> {}); // non-control fields commit explicitly on Enter
		box.setFilter(filter::test);
		editBoxes.add(box);
		if (addWidgetCallback != null) {
			addWidgetCallback.accept(box);
		}
		editBoxSubmits.put(box, onSubmit);
		return bx + bw + BUTTON_SPACING;
	}

	// Map from EditBox to its submit callback
	private final java.util.Map<EditBox, java.util.function.Consumer<String>> editBoxSubmits = new java.util.HashMap<>();

	public void clearButtons() {
		closeSpellDropdown();
		closeSpellDeleteConfirm();
		closeActionMenu();
		if (removeWidgetCallback != null) {
			for (Button btn : buttons) {
				removeWidgetCallback.accept(btn);
			}
			for (EditBox box : editBoxes) {
				removeWidgetCallback.accept(box);
			}
		}
		buttons.clear();
		editBoxes.clear();
		labels.clear();
		editBoxSubmits.clear();
		spellDropdownButton = null;
		spellNewButton = null;
		spellDeleteButton = null;
		newSpellIdBox = null;
		newSpellCreationError = null;
		newSpellCreationMessageY = 0;
	}

	// ---- DockPanel 基础实现 ----

	@Override
	public String dockTitle() {
		return "Controls";
	}

	@Override
	public String dockId() {
		return "controls";
	}

	@Override
	public void setBounds(int x, int y, int w, int h) {
		boolean moved = (this.x != x || this.y != y || this.w != w || this.h != h);
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		// 布局变化后重建按钮（按钮使用绝对坐标）
		if (moved && addWidgetCallback != null && !buttons.isEmpty()) {
			buildButtons();
		}
	}

	@Override
	public int getX() { return x; }

	@Override
	public int getY() { return y; }

	@Override
	public int getWidth() { return w; }

	@Override
	public int getHeight() { return h; }

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(x, y, x + w, y + h, 0xCC000000);
		Font font = Minecraft.getInstance().font;
		for (ControlLabel label : labels) {
			graphics.drawString(font, label.text(), label.x(), label.y(), 0xFF8CC6FF, false);
		}
		if (isDraftMode()) {
			Component message = newSpellCreationError != null ? newSpellCreationError
					: Component.translatable("youkaishomecoming.spell_editor.create.help",
					defaultSpellNamespaceSupplier.get());
			int color = newSpellCreationError == null ? 0xFFAAAAAA : 0xFFFF7777;
			graphics.drawString(font, fitToWidth(message.getString(), w - 8), x + 4,
					newSpellCreationMessageY, color, false);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (spellDeleteConfirm != null) {
			if (handleSpellDeleteConfirmClick(mouseX, mouseY)) {
				return true;
			}
			closeSpellDeleteConfirm();
			return true;
		}
		if (spellDropdown != null) {
			if (handleSpellDropdownClick(mouseX, mouseY)) {
				return true;
			}
			closeSpellDropdown();
			return true;
		}
		if (actionMenu != null) {
			if (handleActionMenuClick(mouseX, mouseY)) {
				return true;
			}
			closeActionMenu();
			return true;
		}
		// 按钮和 EditBox 的点击由 Screen widget 系统处理
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (spellDeleteConfirm != null) {
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
				closeSpellDeleteConfirm();
			}
			return true;
		}
		if (spellDropdown != null) {
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
				closeSpellDropdown();
			}
			return true;
		}
		if (actionMenu != null) {
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
				closeActionMenu();
			}
			return true;
		}
		// Enter key submits focused EditBox value
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
			for (EditBox box : editBoxes) {
				if (box.isFocused()) {
					var submit = editBoxSubmits.get(box);
					if (submit != null) {
						submit.accept(box.getValue());
					}
					box.setFocused(false);
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (spellDeleteConfirm != null) {
			return true;
		}
		if (actionMenu != null) {
			return true;
		}
		if (spellDropdown == null) {
			return false;
		}
		List<DropdownItem> items = spellDropdown.items();
		if (items == null || items.isEmpty()) {
			return true;
		}
		int[] bounds = computeSpellDropdownBounds();
		int visibleItems = Math.max(1, bounds[4]);
		int maxScroll = Math.max(0, items.size() - visibleItems);
		spellDropdownScrollOffset = Math.max(0, Math.min(maxScroll,
				spellDropdownScrollOffset - (int) (delta * 3)));
		return true;
	}

	@Override
	public void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
		doRenderSpellDropdown(graphics, mouseX, mouseY);
		doRenderSpellDeleteConfirm(graphics, mouseX, mouseY);
		doRenderActionMenu(graphics, mouseX, mouseY);
	}

	@Override
	public void onActivated() {
		active = true;
		applyWidgetVisibility();
	}

	@Override
	public void onDeactivated() {
		active = false;
		closeSpellDropdown();
		closeSpellDeleteConfirm();
		closeActionMenu();
		applyWidgetVisibility();
	}

	private void applyWidgetVisibility() {
		for (Button btn : buttons) {
			btn.visible = active;
		}
		for (EditBox box : editBoxes) {
			box.visible = active;
		}
	}

	public boolean isDropdownOpen() {
		return spellDropdown != null || spellDeleteConfirm != null || actionMenu != null;
	}

	private boolean isDraftMode() {
		return spellDraftModeSupplier.get();
	}

	private void openSpellDropdown() {
		List<ResourceLocation> values = spellOptionsSupplier.get();
		if (values == null || values.isEmpty() || spellDropdownButton == null) {
			return;
		}
		closeSpellDeleteConfirm();
		closeActionMenu();

		ResourceLocation current = currentSpellIdSupplier.get();
		List<DropdownItem> items = buildFolderTree(values, current);

		int selectedIndex = -1;
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).isSelected()) {
				selectedIndex = i;
				break;
			}
		}

		spellDropdown = new DropdownOverlay(items, selectedIndex);
		spellDropdownHoverIndex = -1;
		int visibleItems = Math.min(items.size(), DROPDOWN_MAX_VISIBLE);
		int maxScroll = Math.max(0, items.size() - visibleItems);
		if (selectedIndex >= visibleItems) {
			spellDropdownScrollOffset = selectedIndex - visibleItems + 1;
		} else {
			spellDropdownScrollOffset = 0;
		}
		spellDropdownScrollOffset = Math.max(0, Math.min(maxScroll, spellDropdownScrollOffset));
	}

	private List<DropdownItem> buildFolderTree(List<ResourceLocation> values, @Nullable ResourceLocation current) {
		// 按 namespace/prefix 分组
		Map<String, List<ResourceLocation>> grouped = new java.util.TreeMap<>();
		for (ResourceLocation rl : values) {
			String prefix;
			String path = rl.getPath();
			if (path.contains("/")) {
				prefix = rl.getNamespace() + ":" + path.substring(0, path.lastIndexOf('/'));
			} else {
				prefix = rl.getNamespace();
			}
			grouped.computeIfAbsent(prefix, k -> new ArrayList<>()).add(rl);
		}

		List<DropdownItem> items = new ArrayList<>();
		for (Map.Entry<String, List<ResourceLocation>> entry : grouped.entrySet()) {
			String folder = entry.getKey();
			List<ResourceLocation> list = entry.getValue();
			boolean isCollapsed = COLLAPSED_SPELL_FOLDERS.contains(folder);
			String icon = isCollapsed ? "\u25B6 " : "\u25BC ";
			items.add(new DropdownItem(null, icon + folder + " (" + list.size() + ")", true, folder, 0, false));

			if (!isCollapsed) {
				for (ResourceLocation rl : list) {
					boolean selected = java.util.Objects.equals(rl, current);
					String leafName = rl.getPath();
					if (leafName.contains("/")) {
						leafName = leafName.substring(leafName.lastIndexOf('/') + 1);
					}
					items.add(new DropdownItem(rl, "  " + formatSpellOption(rl), false, folder, 1, selected));
				}
			}
		}
		return items;
	}

	private void closeSpellDropdown() {
		spellDropdown = null;
		spellDropdownHoverIndex = -1;
		spellDropdownScrollOffset = 0;
	}

	private void openSpellDeleteConfirm() {
		if (spellDeleteButton == null || !canDeleteSpellSupplier.get()) {
			return;
		}
		closeSpellDropdown();
		closeActionMenu();
		ResourceLocation currentSpellId = currentSpellIdSupplier.get();
		String label = currentSpellId == null ? "current spell" : fitToWidth(formatSpellOption(currentSpellId), 150);
		spellDeleteConfirm = new ConfirmOverlay(new String[]{
				"Cancel",
				"Delete " + label
		});
		spellDeleteConfirmHoverIndex = -1;
	}

	private void closeSpellDeleteConfirm() {
		spellDeleteConfirm = null;
		spellDeleteConfirmHoverIndex = -1;
	}

	private void closeActionMenu() {
		actionMenu = null;
		actionMenuButton = null;
		actionMenuHoverIndex = -1;
	}

	private String formatSpellOption(ResourceLocation spellId) {
		String formatted = spellDisplayFormatter.apply(spellId);
		if (formatted == null || formatted.isBlank()) {
			return spellId.toString();
		}
		return formatted;
	}

	private String fitToWidth(String text, int width) {
		Font font = Minecraft.getInstance().font;
		if (text == null || text.isEmpty() || font.width(text) <= width) {
			return text;
		}
		String ellipsis = "...";
		String clipped = font.plainSubstrByWidth(text, Math.max(0, width - font.width(ellipsis)));
		return clipped + ellipsis;
	}

	private int[] computeSpellDropdownBounds() {
		if (spellDropdown == null || spellDropdownButton == null) {
			return new int[]{0, 0, 0, 0, 0};
		}
		List<DropdownItem> items = spellDropdown.items();
		if (items == null || items.isEmpty()) {
			return new int[]{0, 0, 0, 0, 0};
		}
		int visibleItems = Math.min(items.size(), DROPDOWN_MAX_VISIBLE);
		int totalH = visibleItems * DROPDOWN_ITEM_H;
		int dropdownX = spellDropdownButton.getX();
		int dropdownY = spellDropdownButton.getY() + spellDropdownButton.getHeight();
		int dropdownW = spellDropdownButton.getWidth();
		if (dropdownY + totalH > y + h) {
			dropdownY = spellDropdownButton.getY() - totalH;
		}
		if (dropdownY < y) {
			dropdownY = y;
		}
		if (dropdownY + totalH > y + h) {
			totalH = y + h - dropdownY;
		}
		if (totalH < DROPDOWN_ITEM_H) {
			totalH = DROPDOWN_ITEM_H;
		}
		return new int[]{dropdownX, dropdownY, dropdownW, totalH, Math.max(1, totalH / DROPDOWN_ITEM_H)};
	}

	private int[] computeSpellDeleteConfirmBounds() {
		if (spellDeleteConfirm == null || spellDeleteButton == null) {
			return new int[]{0, 0, 0, 0, 0};
		}
		String[] options = spellDeleteConfirm.options();
		if (options == null || options.length == 0) {
			return new int[]{0, 0, 0, 0, 0};
		}
		Font font = Minecraft.getInstance().font;
		int dropdownW = 120;
		for (String option : options) {
			dropdownW = Math.max(dropdownW, font.width(SpellEditorLocalization.t(option)) + 18);
		}
		dropdownW = Math.min(dropdownW, Math.max(120, w / 3));
		int totalH = options.length * DROPDOWN_ITEM_H;
		int dropdownX = spellDeleteButton.getX() + spellDeleteButton.getWidth() - dropdownW;
		if (dropdownX < x) {
			dropdownX = x;
		}
		int dropdownY = spellDeleteButton.getY() + spellDeleteButton.getHeight();
		if (dropdownY + totalH > y + h) {
			dropdownY = spellDeleteButton.getY() - totalH;
		}
		if (dropdownY < y) {
			dropdownY = y;
		}
		if (dropdownY + totalH > y + h) {
			totalH = y + h - dropdownY;
		}
		if (totalH < DROPDOWN_ITEM_H) {
			totalH = DROPDOWN_ITEM_H;
		}
		return new int[]{dropdownX, dropdownY, dropdownW, totalH, options.length};
	}

	private int[] computeActionMenuBounds() {
		if (actionMenu == null || actionMenuButton == null) {
			return new int[]{0, 0, 0, 0, 0};
		}
		List<MenuEntry> entries = actionMenu.entries();
		if (entries == null || entries.isEmpty()) {
			return new int[]{0, 0, 0, 0, 0};
		}
		Font font = Minecraft.getInstance().font;
		int dropdownW = Math.max(96, font.width(SpellEditorLocalization.t(actionMenu.title())) + 18);
		for (MenuEntry entry : entries) {
			dropdownW = Math.max(dropdownW, font.width(SpellEditorLocalization.t(entry.label())) + 18);
		}
		dropdownW = Math.min(dropdownW, Math.max(120, w / 2));
		int titleH = 14;
		int totalH = titleH + entries.size() * DROPDOWN_ITEM_H;
		int dropdownX = actionMenuButton.getX();
		if (dropdownX + dropdownW > x + w) {
			dropdownX = x + w - dropdownW;
		}
		if (dropdownX < x) {
			dropdownX = x;
		}
		int dropdownY = actionMenuButton.getY() + actionMenuButton.getHeight();
		if (dropdownY + totalH > y + h) {
			dropdownY = actionMenuButton.getY() - totalH;
		}
		if (dropdownY < y) {
			dropdownY = y;
		}
		if (dropdownY + totalH > y + h) {
			totalH = y + h - dropdownY;
		}
		if (totalH < titleH + DROPDOWN_ITEM_H) {
			totalH = titleH + DROPDOWN_ITEM_H;
		}
		return new int[]{dropdownX, dropdownY, dropdownW, totalH, Math.max(1, (totalH - titleH) / DROPDOWN_ITEM_H)};
	}

	private void doRenderSpellDropdown(GuiGraphics graphics, int mouseX, int mouseY) {
		if (spellDropdown == null) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int[] bounds = computeSpellDropdownBounds();
		int dx = bounds[0], dy = bounds[1], dw = bounds[2], dh = bounds[3];
		int visibleItems = bounds[4];
		List<DropdownItem> items = spellDropdown.items();
		if (items == null || items.isEmpty()) {
			return;
		}

		boolean needsScroll = items.size() > visibleItems;
		int scrollbarW = needsScroll ? 6 : 0;

		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 200);
		graphics.fill(dx + 3, dy + 3, dx + dw + 3, dy + dh + 3, 0x88000000);
		graphics.fill(dx, dy, dx + dw, dy + dh, 0xFF1a1a30);
		graphics.fill(dx, dy, dx + dw, dy + 1, 0xFF666688);
		graphics.fill(dx, dy + dh - 1, dx + dw, dy + dh, 0xFF666688);
		graphics.fill(dx, dy, dx + 1, dy + dh, 0xFF666688);
		graphics.fill(dx + dw - 1, dy, dx + dw, dy + dh, 0xFF666688);

		spellDropdownHoverIndex = -1;
		int contentW = dw - scrollbarW;
		if (mouseX >= dx && mouseX < dx + contentW && mouseY >= dy && mouseY < dy + dh) {
			int rawIdx = (mouseY - dy) / DROPDOWN_ITEM_H + spellDropdownScrollOffset;
			if (rawIdx >= 0 && rawIdx < items.size()) {
				spellDropdownHoverIndex = rawIdx;
			}
		}

		int visCount = Math.min(items.size(), dh / DROPDOWN_ITEM_H);
		for (int i = 0; i < visCount; i++) {
			int optIdx = i + spellDropdownScrollOffset;
			if (optIdx >= items.size()) {
				break;
			}
			DropdownItem item = items.get(optIdx);
			int itemY = dy + i * DROPDOWN_ITEM_H;
			boolean isHovered = optIdx == spellDropdownHoverIndex;
			boolean isSelected = item.isSelected();
			if (isHovered) {
				graphics.fill(dx + 1, itemY, dx + contentW - 1, itemY + DROPDOWN_ITEM_H, 0x44FFFFFF);
			}
			int textX = dx + 4 + item.depth() * 8;
			if (isSelected) {
				graphics.drawString(font, "\u25B6", dx + 3, itemY + 4, 0xFFFFCC44, false);
				textX = dx + 14 + item.depth() * 8;
			}
			int textColor = item.isFolder()
					? (isHovered ? 0xFFFFFFFF : 0xFFB0C4DE)
					: (isHovered ? 0xFFFFDD66 : (isSelected ? 0xFFFFCC88 : 0xFFDDDDDD));
			graphics.drawString(font, item.label(), textX, itemY + 4, textColor, false);
		}

		if (needsScroll) {
			int sbX = dx + dw - scrollbarW;
			graphics.fill(sbX, dy, sbX + scrollbarW, dy + dh, 0x33FFFFFF);
			int trackH = dh - 2;
			int thumbH = Math.max(10, trackH * visibleItems / items.size());
			int maxScroll = Math.max(1, items.size() - visibleItems);
			int thumbTravel = trackH - thumbH;
			if (thumbTravel > 0) {
				int thumbY = dy + 1 + thumbTravel * spellDropdownScrollOffset / maxScroll;
				graphics.fill(sbX + 1, thumbY, sbX + scrollbarW - 1, thumbY + thumbH, 0xAAAAAACC);
			}
		}
		graphics.pose().popPose();
	}

	private void doRenderSpellDeleteConfirm(GuiGraphics graphics, int mouseX, int mouseY) {
		if (spellDeleteConfirm == null) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		String[] options = spellDeleteConfirm.options();
		int[] bounds = computeSpellDeleteConfirmBounds();
		int dx = bounds[0], dy = bounds[1], dw = bounds[2], dh = bounds[3];

		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 200);
		graphics.fill(dx + 3, dy + 3, dx + dw + 3, dy + dh + 3, 0x88000000);
		graphics.fill(dx, dy, dx + dw, dy + dh, 0xFF301818);
		graphics.fill(dx, dy, dx + dw, dy + 1, 0xFFAA6666);
		graphics.fill(dx, dy + dh - 1, dx + dw, dy + dh, 0xFFAA6666);
		graphics.fill(dx, dy, dx + 1, dy + dh, 0xFFAA6666);
		graphics.fill(dx + dw - 1, dy, dx + dw, dy + dh, 0xFFAA6666);

		spellDeleteConfirmHoverIndex = -1;
		if (mouseX >= dx && mouseX < dx + dw && mouseY >= dy && mouseY < dy + dh) {
			int rawIdx = (mouseY - dy) / DROPDOWN_ITEM_H;
			if (rawIdx >= 0 && rawIdx < options.length) {
				spellDeleteConfirmHoverIndex = rawIdx;
			}
		}

		for (int i = 0; i < options.length; i++) {
			int itemY = dy + i * DROPDOWN_ITEM_H;
			boolean isHovered = i == spellDeleteConfirmHoverIndex;
			if (isHovered) {
				graphics.fill(dx + 1, itemY, dx + dw - 1, itemY + DROPDOWN_ITEM_H, 0x44FFFFFF);
			}
			int color = i == 0 ? 0xFFDDDDDD : (isHovered ? 0xFFFF8888 : 0xFFFF6666);
			graphics.drawString(font, SpellEditorLocalization.t(options[i]), dx + 6, itemY + 4, color, false);
		}
		graphics.pose().popPose();
	}

	private void doRenderActionMenu(GuiGraphics graphics, int mouseX, int mouseY) {
		if (actionMenu == null) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		List<MenuEntry> entries = actionMenu.entries();
		int[] bounds = computeActionMenuBounds();
		int dx = bounds[0], dy = bounds[1], dw = bounds[2], dh = bounds[3];
		int visibleItems = bounds[4];
		int titleH = 14;

		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 200);
		graphics.fill(dx + 3, dy + 3, dx + dw + 3, dy + dh + 3, 0x88000000);
		graphics.fill(dx, dy, dx + dw, dy + dh, 0xFF181828);
		graphics.fill(dx, dy, dx + dw, dy + 1, 0xFF666688);
		graphics.fill(dx, dy + dh - 1, dx + dw, dy + dh, 0xFF666688);
		graphics.fill(dx, dy, dx + 1, dy + dh, 0xFF666688);
		graphics.fill(dx + dw - 1, dy, dx + dw, dy + dh, 0xFF666688);
		graphics.drawString(font, fitToWidth(SpellEditorLocalization.t(actionMenu.title()), dw - 8), dx + 4, dy + 3, 0xFFFFDD88, false);

		actionMenuHoverIndex = -1;
		int itemTop = dy + titleH;
		int itemH = Math.max(0, dh - titleH);
		if (mouseX >= dx && mouseX < dx + dw && mouseY >= itemTop && mouseY < itemTop + itemH) {
			int rawIdx = (mouseY - itemTop) / DROPDOWN_ITEM_H;
			if (rawIdx >= 0 && rawIdx < entries.size() && rawIdx < visibleItems) {
				actionMenuHoverIndex = rawIdx;
			}
		}

		int count = Math.min(entries.size(), visibleItems);
		for (int i = 0; i < count; i++) {
			MenuEntry entry = entries.get(i);
			int itemY = itemTop + i * DROPDOWN_ITEM_H;
			boolean hovered = i == actionMenuHoverIndex && entry.active();
			if (hovered) {
				graphics.fill(dx + 1, itemY, dx + dw - 1, itemY + DROPDOWN_ITEM_H, 0x44FFFFFF);
			}
			int color = !entry.active() ? 0xFF777777 : hovered ? 0xFFFFDD66 : 0xFFDDDDDD;
			graphics.drawString(font, fitToWidth(SpellEditorLocalization.t(entry.label()), dw - 10), dx + 6, itemY + 4, color, false);
		}
		graphics.pose().popPose();
	}

	private boolean handleSpellDropdownClick(double mouseX, double mouseY) {
		if (spellDropdown == null) {
			return false;
		}
		int[] bounds = computeSpellDropdownBounds();
		int dx = bounds[0], dy = bounds[1], dw = bounds[2], dh = bounds[3];
		int visibleItems = bounds[4];
		List<DropdownItem> items = spellDropdown.items();
		boolean needsScroll = items.size() > visibleItems;
		int scrollbarW = needsScroll ? 6 : 0;
		int contentW = dw - scrollbarW;
		if (mouseX >= dx && mouseX < dx + contentW && mouseY >= dy && mouseY < dy + dh) {
			int visIdx = (int) ((mouseY - dy) / DROPDOWN_ITEM_H);
			int optIdx = visIdx + spellDropdownScrollOffset;
			if (optIdx >= 0 && optIdx < items.size()) {
				DropdownItem clicked = items.get(optIdx);
				if (clicked.isFolder()) {
					String key = clicked.folderKey();
					if (COLLAPSED_SPELL_FOLDERS.contains(key)) {
						COLLAPSED_SPELL_FOLDERS.remove(key);
					} else {
						COLLAPSED_SPELL_FOLDERS.add(key);
					}
					openSpellDropdown(); // 重新按折叠状态刷新菜单树
				} else if (clicked.value() != null) {
					ResourceLocation selected = clicked.value();
					closeSpellDropdown();
					switchSpellCallback.accept(selected);
				}
				return true;
			}
		}
		return false;
	}

	private boolean handleSpellDeleteConfirmClick(double mouseX, double mouseY) {
		if (spellDeleteConfirm == null) {
			return false;
		}
		int[] bounds = computeSpellDeleteConfirmBounds();
		int dx = bounds[0], dy = bounds[1], dw = bounds[2], dh = bounds[3];
		if (mouseX >= dx && mouseX < dx + dw && mouseY >= dy && mouseY < dy + dh) {
			int idx = (int) ((mouseY - dy) / DROPDOWN_ITEM_H);
			closeSpellDeleteConfirm();
			if (idx == 1) {
				deleteSpellCallback.run();
			}
			return true;
		}
		return false;
	}

	private boolean handleActionMenuClick(double mouseX, double mouseY) {
		if (actionMenu == null) {
			return false;
		}
		int[] bounds = computeActionMenuBounds();
		int dx = bounds[0], dy = bounds[1], dw = bounds[2], dh = bounds[3];
		int visibleItems = bounds[4];
		int titleH = 14;
		int itemTop = dy + titleH;
		if (mouseX >= dx && mouseX < dx + dw && mouseY >= itemTop && mouseY < dy + dh) {
			int idx = (int) ((mouseY - itemTop) / DROPDOWN_ITEM_H);
			if (idx >= 0 && idx < actionMenu.entries().size() && idx < visibleItems) {
				MenuEntry entry = actionMenu.entries().get(idx);
				Runnable action = entry.action();
				boolean active = entry.active();
				closeActionMenu();
				if (active && action != null) {
					action.run();
				}
				return true;
			}
		}
		return false;
	}
}
