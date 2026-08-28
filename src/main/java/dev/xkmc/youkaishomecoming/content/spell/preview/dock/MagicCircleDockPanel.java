package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import dev.xkmc.fastprojectileapi.spellcircle.SpellComponent;
import dev.xkmc.youkaishomecoming.content.spell.preview.EditorTextBoxes;
import dev.xkmc.youkaishomecoming.content.spell.preview.OrthographicViewport;
import dev.xkmc.youkaishomecoming.content.spell.preview.SpellCircleEditorNetworkClient;
import dev.xkmc.youkaishomecoming.content.spell.preview.SpellEditorLocalization;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class MagicCircleDockPanel implements DockPanel {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int PADDING = 4;
	private static final int ROW = 20;
	private static final int STATUS_HEIGHT = 13;
	private static final int BUTTON_HEIGHT = 16;
	private static final int SCROLLBAR_WIDTH = 6;
	private static final int DROPDOWN_ITEM_H = 18;
	private static final int DROPDOWN_MAX_VISIBLE = 10;
	private static final int LABEL_WIDTH = 76;
	private static final int SECTION_HEIGHT = 14;

	private static final String SECTION_STROKES = "strokes";
	private static final String SECTION_ITEMS = "items";
	private static final String SECTION_TEXTS = "texts";
	private static final String SECTION_LAYERS = "layers";

	/** 会话级：跨面板重建保留的选中魔法阵 ID。见 {@link #loadInitialSelection()}。 */
	@Nullable
	private static ResourceLocation lastSelectedId;

	/**
	 * 会话级：跨面板重建保留的分区折叠状态。
	 * 默认展开笔画与文字（文字是新功能，折叠会让人找不到入口），物品与层默认折叠。
	 */
	private static final Set<String> collapsedSections = new HashSet<>(Set.of(SECTION_ITEMS, SECTION_LAYERS));

	private final OrthographicViewport viewport;
	private final List<AbstractWidget> widgets = new ArrayList<>();
	private final Map<AbstractWidget, Integer> widgetBaseY = new IdentityHashMap<>();
	private Consumer<AbstractWidget> addWidgetCallback;
	private Consumer<GuiEventListener> removeWidgetCallback;

	private int x, y, w, h;
	private boolean active;
	private boolean previewActive;
	private boolean suppress;
	private int scrollOffset;
	private int contentHeight;
	private boolean scrollbarDragging;
	private final Map<ResourceLocation, SpellComponent> linkedComponents = new LinkedHashMap<>();
	/** 标签与控件的唯一真源，供 render 绘制标签、mouseClicked 命中分区表头。 */
	private final List<CircleRow> rows = new ArrayList<>();
	/** rebuildWidgets 期间的游标 Y。 */
	private int rowY;
	private ResourceLocation selectedId = new ResourceLocation("youkaishomecoming", "custom_circle");
	private SpellComponent component = createDefaultComponent();
	private int selectedStroke;
	private int selectedItem;
	private int selectedText;
	private int selectedLayer;
	private float previewSize = 1.0f;
	private String status = "Magic Circle ready";
	private int statusColor = 0xFF88AACC;

	private Button circleDropdownButton;
	private Button circleDeleteButton;
	private DropdownOverlay circleDropdown;
	private ConfirmOverlay deleteConfirm;
	private int deleteConfirmHoverIndex = -1;
	/** 本次编辑会话首次载入各魔法阵时的快照，供 Reset 还原。 */
	private final Map<ResourceLocation, JsonElement> openSnapshots = new LinkedHashMap<>();
	/** ID 输入框里尚未提交的文本；null 表示没有待提交改名。 */
	@Nullable
	private String pendingId;
	private boolean idBoxWasFocused;
	private int circleDropdownHoverIndex = -1;
	private int circleDropdownScrollOffset;
	private EditBox idBox;
	private EditBox previewSizeBox;
	private EditBox strokeColorBox;
	private EditBox strokeRadiusBox;
	private EditBox strokeWidthBox;
	private EditBox strokeVertexBox;
	private EditBox strokeCycleBox;
	private EditBox strokeRuneBox;
	private EditBox itemIdBox;
	private EditBox itemScaleBox;
	private EditBox itemRotationBox;
	private EditBox itemAlphaBox;
	private EditBox textContentBox;
	private EditBox textColorBox;
	private EditBox textScaleBox;
	private EditBox textRotationBox;
	private EditBox textAlphaBox;
	private EditBox textSpacingBox;
	private EditBox textRadiusBox;
	private EditBox textArcSpanBox;
	private EditBox textXBox;
	private EditBox textYBox;
	private EditBox textZBox;
	private EditBox layerChildrenBox;
	private EditBox layerRadiusBox;
	private EditBox layerRotationBox;
	private EditBox layerRotationSpeedBox;
	private EditBox layerScaleBox;
	private EditBox layerZBox;
	private EditBox layerAlphaBox;

	private record DropdownOverlay(List<ResourceLocation> values, String[] options, int selectedIndex) {
	}

	private record ConfirmOverlay(String[] options) {
	}

	// --- Top bar entry points (magic circle mode) ---
	// Save/Export/Reset live on the top bar, mirroring the spell card's Apply/Export/Reset.
	// New and delete sit next to the circle picker instead, mirroring "Spell: [▼] [+] [-]".

	public void saveCircleFromTopBar() {
		save(false);
	}

	public void exportCircleFromTopBar() {
		save(true);
	}

	public void resetCircleFromTopBar() {
		resetToDefault();
	}

	/**
	 * Restore the selected circle to the state it had when this editor session first
	 * loaded it — the same contract as the spell card's Reset, which falls back to its
	 * open-snapshot when there is no built-in default. Magic circles have no separate
	 * default registry, so the snapshot is the only source.
	 */
	private void resetToDefault() {
		JsonElement snapshot = openSnapshots.get(selectedId);
		if (snapshot == null) {
			setStatus("No snapshot to reset to", 0xFFFFCC88);
			return;
		}
		SpellComponent restored;
		try {
			restored = GSON.fromJson(snapshot, SpellComponent.class);
		} catch (RuntimeException e) {
			setStatus("No snapshot to reset to", 0xFFFFCC88);
			return;
		}
		if (restored == null) {
			setStatus("No snapshot to reset to", 0xFFFFCC88);
			return;
		}
		restored.invalidateCache();
		component = restored;
		selectedStroke = 0;
		selectedItem = 0;
		selectedText = 0;
		selectedLayer = 0;
		scrollOffset = 0;
		clampSelection();
		publishLocal(true);
		rebuildWidgets();
		setStatus("Magic Circle reset", 0xFF88FF88);
	}

	/** Remember a circle's contents the first time this session sees it, for Reset. */
	private void captureSnapshot(ResourceLocation id, SpellComponent value) {
		if (id == null || value == null || openSnapshots.containsKey(id)) {
			return;
		}
		value.invalidateCache();
		openSnapshots.put(id, GSON.toJsonTree(value));
	}

	/**
	 * 提交 ID 输入框里的改名。
	 *
	 * <p>把当前编辑内容移到新 ID 下，并把旧 ID 还原成本次会话载入时的快照 —— 改名不应该
	 * 顺手改掉原来那个魔法阵。旧 ID 若本来就是新建出来的（没有快照），则直接从本地注册表移除，
	 * 不留下空壳。
	 */
	private void commitIdRename() {
		String text = pendingId;
		pendingId = null;
		if (text == null) {
			return;
		}
		ResourceLocation id = ResourceLocation.tryParse(text.trim());
		if (id == null) {
			setStatus("Invalid circle id", 0xFFFF8888);
			if (idBox != null) {
				suppress = true;
				idBox.setValue(selectedId.toString());
				suppress = false;
			}
			return;
		}
		if (id.equals(selectedId)) {
			return;
		}
		ResourceLocation old = selectedId;
		JsonElement snapshot = openSnapshots.get(old);
		if (snapshot != null) {
			try {
				SpellComponent original = GSON.fromJson(snapshot, SpellComponent.class);
				if (original != null) {
					original.invalidateCache();
					YoukaisHomecoming.SPELL.getMerged().map.put(old.toString(), original);
				}
			} catch (RuntimeException ignored) {
				// 快照坏了就退回到直接移除，至少不会留下被改坏的原件。
				YoukaisHomecoming.SPELL.getMerged().map.remove(old.toString());
			}
		} else {
			YoukaisHomecoming.SPELL.getMerged().map.remove(old.toString());
		}
		linkedComponents.remove(old);
		selectedId = id;
		publishLocal(true);
		rebuildWidgets();
		setStatus("Magic Circle renamed", 0xFF88FF88);
	}

	private void openDeleteConfirm() {
		closeCircleDropdown();
		deleteConfirm = new ConfirmOverlay(new String[]{
				"Cancel",
				"Delete " + fitToWidth(selectedId.toString(), 150)
		});
		deleteConfirmHoverIndex = -1;
	}

	private void closeDeleteConfirm() {
		deleteConfirm = null;
		deleteConfirmHoverIndex = -1;
	}

	private int[] computeDeleteConfirmBounds() {
		if (deleteConfirm == null || circleDeleteButton == null) {
			return new int[]{0, 0, 0, 0};
		}
		String[] options = deleteConfirm.options();
		Font font = Minecraft.getInstance().font;
		int dw = 120;
		for (String option : options) {
			dw = Math.max(dw, font.width(SpellEditorLocalization.t(option)) + 18);
		}
		dw = Math.min(dw, Math.max(120, w - PADDING * 2));
		int dh = options.length * DROPDOWN_ITEM_H;
		int dx = Math.max(x, circleDeleteButton.getX() + circleDeleteButton.getWidth() - dw);
		int dy = circleDeleteButton.getY() + circleDeleteButton.getHeight();
		return new int[]{dx, dy, dw, dh};
	}

	private void renderDeleteConfirm(GuiGraphics graphics, int mouseX, int mouseY) {
		if (deleteConfirm == null) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		String[] options = deleteConfirm.options();
		int[] bounds = computeDeleteConfirmBounds();
		int dx = bounds[0], dy = bounds[1], dw = bounds[2], dh = bounds[3];

		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 200);
		graphics.fill(dx + 3, dy + 3, dx + dw + 3, dy + dh + 3, 0x88000000);
		graphics.fill(dx, dy, dx + dw, dy + dh, 0xFF301818);
		graphics.fill(dx, dy, dx + dw, dy + 1, 0xFFAA6666);
		graphics.fill(dx, dy + dh - 1, dx + dw, dy + dh, 0xFFAA6666);
		graphics.fill(dx, dy, dx + 1, dy + dh, 0xFFAA6666);
		graphics.fill(dx + dw - 1, dy, dx + dw, dy + dh, 0xFFAA6666);

		deleteConfirmHoverIndex = -1;
		if (mouseX >= dx && mouseX < dx + dw && mouseY >= dy && mouseY < dy + dh) {
			int rawIdx = (mouseY - dy) / DROPDOWN_ITEM_H;
			if (rawIdx >= 0 && rawIdx < options.length) {
				deleteConfirmHoverIndex = rawIdx;
			}
		}
		for (int i = 0; i < options.length; i++) {
			int itemY = dy + i * DROPDOWN_ITEM_H;
			boolean hovered = i == deleteConfirmHoverIndex;
			if (hovered) {
				graphics.fill(dx + 1, itemY, dx + dw - 1, itemY + DROPDOWN_ITEM_H, 0x44FFFFFF);
			}
			int color = i == 0 ? 0xFFDDDDDD : (hovered ? 0xFFFF8888 : 0xFFFF6666);
			graphics.drawString(font, SpellEditorLocalization.t(options[i]), dx + 6, itemY + 4, color, false);
		}
		graphics.pose().popPose();
	}

	private boolean handleDeleteConfirmClick(double mouseX, double mouseY) {
		if (deleteConfirm == null) {
			return false;
		}
		int[] bounds = computeDeleteConfirmBounds();
		int dx = bounds[0], dy = bounds[1], dw = bounds[2], dh = bounds[3];
		if (mouseX >= dx && mouseX < dx + dw && mouseY >= dy && mouseY < dy + dh) {
			int idx = (int) ((mouseY - dy) / DROPDOWN_ITEM_H);
			closeDeleteConfirm();
			if (idx == 1) {
				deleteCircle();
			}
			return true;
		}
		return false;
	}

	public MagicCircleDockPanel(OrthographicViewport viewport) {
		this.viewport = viewport;
		loadInitialSelection();
		publishLocal(false);
	}

	public void setWidgetCallbacks(Consumer<AbstractWidget> addWidgetCallback,
								   Consumer<GuiEventListener> removeWidgetCallback) {
		this.addWidgetCallback = addWidgetCallback;
		this.removeWidgetCallback = removeWidgetCallback;
		rebuildWidgets();
	}

	@Override
	public String dockTitle() {
		return "Magic Circle";
	}

	@Override
	public String dockId() {
		return "magic_circle";
	}

	@Override
	public void setBounds(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		rebuildWidgets();
	}

	@Override
	public int getX() {
		return x;
	}

	@Override
	public int getY() {
		return y;
	}

	@Override
	public int getWidth() {
		return w;
	}

	@Override
	public int getHeight() {
		return h;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(x, y, x + w, y + h, 0xCC000000);
		clampScrollOffset();
		updateWidgetScroll();
		// 点走焦点等同于确认改名，和符卡的 ID 输入框行为一致。
		boolean idFocused = idBox != null && idBox.isFocused();
		if (idBoxWasFocused && !idFocused) {
			commitIdRename();
		}
		idBoxWasFocused = idFocused;
		Font font = Minecraft.getInstance().font;
		renderFixedHeader(graphics, font);
		int labelX = x + PADDING;
		int top = contentTop();
		int bottom = contentBottom();
		if (bottom > top) {
			graphics.enableScissor(x, top, x + w, bottom);
			// 标签直接来自与控件同源的 rows，不可能再和控件顺序错位。
			for (CircleRow row : rows) {
				int rowY = row.baseY() - scrollOffset;
				if (rowY + row.height() < top || rowY > bottom) {
					continue;
				}
				if (row.section() != null) {
					graphics.fill(x + 1, rowY - 1, x + w - SCROLLBAR_WIDTH - 3, rowY + row.height() - 2, 0x33FFFFFF);
				}
				drawLabel(graphics, font, row.label(), labelX, rowY);
			}
			graphics.disableScissor();
		}
		renderScrollbar(graphics);

		String msg = font.plainSubstrByWidth(SpellEditorLocalization.t(status), Math.max(0, w - PADDING * 2));
		graphics.drawString(font, msg, x + PADDING, y + h - STATUS_HEIGHT, statusColor, false);
		if (previewActive) {
			viewport.setMagicCirclePreview(selectedId, component, previewSize);
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
			if (deleteConfirm != null) {
				closeDeleteConfirm();
				return true;
			}
			if (circleDropdown != null) {
				closeCircleDropdown();
				return true;
			}
		}
		boolean enter = keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
				|| keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
		if (enter && idBox != null && idBox.isFocused()) {
			idBox.setFocused(false);
			idBoxWasFocused = false;
			commitIdRename();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (deleteConfirm != null) {
			if (!handleDeleteConfirmClick(mouseX, mouseY)) {
				closeDeleteConfirm();
			}
			return true;
		}
		if (circleDropdown != null) {
			if (handleCircleDropdownClick(mouseX, mouseY)) {
				return true;
			}
			closeCircleDropdown();
			return true;
		}
		if (button == 0 && isOverScrollbar(mouseX, mouseY)) {
			scrollbarDragging = true;
			updateScrollFromMouse(mouseY);
			return true;
		}
		return button == 0 && toggleSectionAt(mouseX, mouseY);
	}

	/** 命中分区表头则折叠/展开该分区。 */
	private boolean toggleSectionAt(double mouseX, double mouseY) {
		if (mouseX < x || mouseX > x + w || mouseY < contentTop() || mouseY > contentBottom()) {
			return false;
		}
		for (CircleRow row : rows) {
			String section = row.section();
			if (section == null) {
				continue;
			}
			int top = row.baseY() - scrollOffset;
			if (mouseY >= top - 1 && mouseY < top + row.height() - 1) {
				if (!collapsedSections.remove(section)) {
					collapsedSections.add(section);
				}
				rebuildWidgets();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (scrollbarDragging && button == 0) {
			updateScrollFromMouse(mouseY);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (scrollbarDragging && button == 0) {
			scrollbarDragging = false;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (circleDropdown != null) {
			String[] options = circleDropdown.options();
			if (options == null || options.length == 0) {
				return true;
			}
			int[] bounds = computeCircleDropdownBounds();
			int visibleItems = Math.max(1, bounds[4]);
			int maxScroll = Math.max(0, options.length - visibleItems);
			circleDropdownScrollOffset = Math.max(0, Math.min(maxScroll,
					circleDropdownScrollOffset - (int) (delta * 3)));
			return true;
		}
		if (!isMouseOver(mouseX, mouseY) || maxScroll() <= 0) {
			return false;
		}
		scrollOffset -= (int) (delta * 30);
		clampScrollOffset();
		updateWidgetScroll();
		return true;
	}

	@Override
	public void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
		renderCircleDropdown(graphics, mouseX, mouseY);
		renderDeleteConfirm(graphics, mouseX, mouseY);
	}

	@Override
	public void onActivated() {
		setEditorActive(true);
	}

	@Override
	public void onDeactivated() {
		setEditorActive(false);
	}

	public void setEditorActive(boolean active) {
		boolean wasActive = this.active;
		this.active = active;
		if (wasActive != active) {
			for (AbstractWidget widget : widgets) {
				if (!active && widget.isFocused()) {
					widget.setFocused(false);
				}
			}
		}
		updateWidgetScroll();
		if (active) {
			if (!wasActive) {
				refreshWidgetValues();
			}
			setPreviewActive(true);
		} else {
			closeCircleDropdown();
			scrollbarDragging = false;
			if (wasActive) {
				setPreviewActive(false);
			}
		}
	}

	public void setPreviewActive(boolean active) {
		if (this.previewActive == active && !active) {
			return;
		}
		this.previewActive = active;
		if (active) {
			viewport.setMagicCirclePreview(selectedId, component, previewSize);
		} else {
			viewport.clearMagicCirclePreview();
		}
	}

	/**
	 * 重建面板内容。
	 *
	 * <p>标签与控件在同一次调用里成对产生（{@link #fieldRow} / {@link #buttonRow}），
	 * 因此 {@link #render} 画的标签一定对应此处创建的控件。旧实现把两者拆成两份
	 * 平行列表，插入任何一行都必须同时改两处，且错位不会被编译器发现。
	 */
	private void rebuildWidgets() {
		if (addWidgetCallback == null) {
			return;
		}
		for (AbstractWidget widget : widgets) {
			if (removeWidgetCallback != null) {
				removeWidgetCallback.accept(widget);
			}
		}
		widgets.clear();
		widgetBaseY.clear();
		rows.clear();
		closeCircleDropdown();
		circleDropdownButton = null;
		clampSelection();
		Font font = Minecraft.getInstance().font;

		// --- 固定表头：始终可见，不随正文滚动 ---
		// 与符卡的 "Spell: [▼] [+] [-]" 保持一致：新建/删除紧挨着选择器，而不是散在顶栏。
		int headerY = y + PADDING;
		int actionW = 20;
		int actionGap = 2;
		int pickerW = Math.max(40, fieldWidth() - (actionW + actionGap) * 2);
		circleDropdownButton = addFixedWidget(Button.builder(
						Component.literal(dropdownLabel(pickerW)), b -> openCircleDropdown())
				.bounds(fieldX(), headerY, pickerW, BUTTON_HEIGHT).build());
		int newX = fieldX() + pickerW + actionGap;
		addFixedWidget(Button.builder(Component.literal("+"), b -> newCircle())
				.bounds(newX, headerY, actionW, BUTTON_HEIGHT).build());
		int deleteX = newX + actionW + actionGap;
		circleDeleteButton = addFixedWidget(Button.builder(Component.literal("-"), b -> openDeleteConfirm())
				.bounds(deleteX, headerY, actionW, BUTTON_HEIGHT).build());
		headerY += ROW;
		// 逐键提交会把每个中间串都注册成一个魔法阵（输入 "1123" 会留下 1 / 11 / 112 / 1123）。
		// 与符卡的新建 ID 框一致：只记录待提交值，回车或失焦时才真正改名。
		idBox = addFixedWidget(makeEditBox(font, fieldX(), headerY, fieldWidth(), selectedId.toString(),
				text -> pendingId = text));
		headerY += ROW;
		previewSizeBox = addFixedWidget(makeEditBox(font, fieldX(), headerY, fieldWidth(),
				fmt(previewSize), this::setPreviewSize));

		// --- 可滚动正文：每个分区只展开选中元素的字段 ---
		rowY = contentTop();
		buildStrokeSection(font);
		buildItemSection(font);
		buildTextSection(font);
		buildLayerSection(font);

		contentHeight = rowY - contentTop();
		clampScrollOffset();
		updateWidgetScroll();
		refreshWidgetValues();
	}

	private void buildStrokeSection(Font font) {
		int count = component.strokes.size();
		if (!sectionRow(SECTION_STROKES, "Strokes", count, selectedStroke)) {
			return;
		}
		buttonRow(new ButtonSpec("+Stroke", 60, this::addStroke), new ButtonSpec("-Stroke", 60, this::removeStroke),
				new ButtonSpec("<", 24, this::prevStroke), new ButtonSpec(">", 24, this::nextStroke));
		if (count == 0) {
			emptyRow("No strokes");
			return;
		}
		SpellComponent.Stroke stroke = currentStroke();
		strokeColorBox = fieldRow(font, "Color", currentStrokeColor(), this::setStrokeColor);
		strokeRadiusBox = fieldRow(font, "Radius", fmt(stroke == null ? 48 : stroke.radius),
				text -> setStrokeFloat(text, "radius"));
		strokeWidthBox = fieldRow(font, "Width", fmt(stroke == null ? 2 : stroke.width),
				text -> setStrokeFloat(text, "width"));
		strokeVertexBox = fieldRow(font, "Vertex", String.valueOf(stroke == null ? 64 : stroke.vertex),
				text -> setStrokeInt(text, "vertex"));
		strokeCycleBox = fieldRow(font, "Cycle", String.valueOf(stroke == null ? 1 : stroke.cycle),
				text -> setStrokeInt(text, "cycle"));
		strokeRuneBox = fieldRow(font, "Rune", String.valueOf(stroke == null ? 0 : stroke.rune),
				text -> setStrokeInt(text, "rune"));
	}

	private void buildItemSection(Font font) {
		int count = getItemCount();
		if (!sectionRow(SECTION_ITEMS, "Items", count, selectedItem)) {
			return;
		}
		buttonRow(new ButtonSpec("+Item", 54, this::addItem), new ButtonSpec("-Item", 54, this::removeItem),
				new ButtonSpec("<", 24, this::prevItem), new ButtonSpec(">", 24, this::nextItem));
		if (count == 0) {
			emptyRow("No items");
			return;
		}
		SpellComponent.ItemLayer item = currentItem();
		itemIdBox = fieldRow(font, "Item ID", item == null ? "minecraft:air" : item.item, this::setItemId);
		itemScaleBox = fieldRow(font, "Scale", fmt(valueOf(item == null ? null : item.scale, 16)),
				text -> setItemValue(text, "scale", 16));
		itemRotationBox = fieldRow(font, "Rot", fmt(valueOf(item == null ? null : item.rotation, 0)),
				text -> setItemValue(text, "rotation", 0));
		itemAlphaBox = fieldRow(font, "Alpha", fmt(valueOf(item == null ? null : item.alpha, 1)),
				text -> setItemValue(text, "alpha", 1));
	}

	private void buildTextSection(Font font) {
		int count = getTextCount();
		if (!sectionRow(SECTION_TEXTS, "Texts", count, selectedText)) {
			return;
		}
		buttonRow(new ButtonSpec("+Text", 54, this::addText), new ButtonSpec("-Text", 54, this::removeText),
				new ButtonSpec("<", 24, this::prevText), new ButtonSpec(">", 24, this::nextText));
		if (count == 0) {
			emptyRow("No texts");
			return;
		}
		SpellComponent.TextLayer text = currentText();
		textContentBox = fieldRow(font, "Text", text == null ? "" : text.text, this::setTextContent);
		textColorBox = fieldRow(font, "Color", currentTextColor(), this::setTextColor);
		textScaleBox = fieldRow(font, "Scale", fmt(valueOf(text == null ? null : text.scale, 1)),
				value -> setTextValue(value, "scale", 1));
		textRotationBox = fieldRow(font, "Rot", fmt(valueOf(text == null ? null : text.rotation, 0)),
				value -> setTextValue(value, "rotation", 0));
		textAlphaBox = fieldRow(font, "Alpha", fmt(valueOf(text == null ? null : text.alpha, 1)),
				value -> setTextValue(value, "alpha", 1));
		textSpacingBox = fieldRow(font, "Spacing", fmt(text == null ? 0 : text.char_spacing), this::setTextSpacing);
		// Arc Radius 0 = 直排；仅环绕模式才需要跨度与朝向，所以这两行按需出现。
		textRadiusBox = fieldRow(font, "Arc Radius", fmt(text == null ? 0 : text.radius), this::setTextRadius);
		if (text != null && text.radius > 0) {
			textArcSpanBox = fieldRow(font, "Arc Span", fmt(text.arc_span), this::setTextArcSpan);
			buttonRow(new ButtonSpec(text.flip ? "Read: Inward" : "Read: Outward", 110, this::toggleTextFlip));
		} else {
			textArcSpanBox = null;
		}
		textXBox = fieldRow(font, "X", fmt(valueOf(text == null ? null : text.x_offset, 0)),
				value -> setTextValue(value, "x", 0));
		textYBox = fieldRow(font, "Y", fmt(valueOf(text == null ? null : text.y_offset, 0)),
				value -> setTextValue(value, "y", 0));
		textZBox = fieldRow(font, "Z", fmt(valueOf(text == null ? null : text.z_offset, 0)),
				value -> setTextValue(value, "z", 0));
	}

	private void buildLayerSection(Font font) {
		int count = component.layers.size();
		if (!sectionRow(SECTION_LAYERS, "Layers", count, selectedLayer)) {
			return;
		}
		buttonRow(new ButtonSpec("+Layer", 54, this::addLayer), new ButtonSpec("-Layer", 54, this::removeLayer),
				new ButtonSpec("<", 24, this::prevLayer), new ButtonSpec(">", 24, this::nextLayer));
		if (count == 0) {
			emptyRow("No layers");
			return;
		}
		buttonRow(new ButtonSpec("+Child", 66, this::addChildComponent),
				new ButtonSpec("Open Child", 74, this::openFirstChildComponent));
		SpellComponent.Layer layer = currentLayer();
		layerChildrenBox = fieldRow(font, "Children", currentLayerChildren(), this::setLayerChildren);
		layerRadiusBox = fieldRow(font, "Radius", fmt(valueOf(layer == null ? null : layer.radius, 0)),
				text -> setLayerValue(text, "radius", 0));
		layerRotationBox = fieldRow(font, "Rot", fmt(valueOf(layer == null ? null : layer.rotation, 0)),
				text -> setLayerValue(text, "rotation", 0));
		layerRotationSpeedBox = fieldRow(font, "Rot Speed", fmt(deltaOf(layer == null ? null : layer.rotation, 0)),
				this::setLayerRotationSpeed);
		layerScaleBox = fieldRow(font, "Scale", fmt(valueOf(layer == null ? null : layer.scale, 1)),
				text -> setLayerValue(text, "scale", 1));
		layerZBox = fieldRow(font, "Z", fmt(valueOf(layer == null ? null : layer.z_offset, 0)),
				text -> setLayerValue(text, "z", 0));
		layerAlphaBox = fieldRow(font, "Alpha", fmt(valueOf(layer == null ? null : layer.alpha, 1)),
				text -> setLayerValue(text, "alpha", 1));
	}

	// --- Row builders: label and widget are always produced together ---

	/** 分区表头。返回是否展开；折叠时调用方直接跳过该分区的字段。 */
	private boolean sectionRow(String key, String title, int count, int selected) {
		boolean collapsed = collapsedSections.contains(key);
		String indicator = count == 0 ? "(0)" : "(" + (Math.min(selected, count - 1) + 1) + "/" + count + ")";
		rows.add(new CircleRow((collapsed ? "▶ " : "▼ ") + SpellEditorLocalization.t(title) + " " + indicator,
				rowY, SECTION_HEIGHT, key));
		rowY += SECTION_HEIGHT;
		return !collapsed;
	}

	private EditBox fieldRow(Font font, String label, String value, Consumer<String> responder) {
		EditBox box = addEditBox(font, fieldX(), rowY, fieldWidth(), value, responder);
		rows.add(new CircleRow(SpellEditorLocalization.t(label), rowY, ROW, null));
		rowY += ROW;
		return box;
	}

	private void buttonRow(ButtonSpec... specs) {
		int bx = x + PADDING;
		for (ButtonSpec spec : specs) {
			bx = addButton(bx, rowY, spec.minWidth(), spec.label(), spec.onPress());
		}
		rows.add(new CircleRow("", rowY, ROW, null));
		rowY += ROW;
	}

	/** 分区展开但一个元素都没有时的占位行。 */
	private void emptyRow(String label) {
		rows.add(new CircleRow("§o" + SpellEditorLocalization.t(label), rowY, ROW, null));
		rowY += ROW;
	}

	private void renderFixedHeader(GuiGraphics graphics, Font font) {
		int labelX = x + PADDING;
		int headerY = y + PADDING;
		drawLabel(graphics, font, SpellEditorLocalization.t("Circle"), labelX, headerY);
		drawLabel(graphics, font, SpellEditorLocalization.t("ID"), labelX, headerY + ROW);
		drawLabel(graphics, font, SpellEditorLocalization.t("Preview"), labelX, headerY + ROW * 2);
		int sep = y + PADDING + ROW * 3 - 2;
		graphics.fill(x + 1, sep, x + w - 1, sep + 1, 0x44FFFFFF);
	}

	private String dropdownLabel(int width) {
		return fitToWidth(selectedId.toString(), Math.max(0, width - 14)) + " ▼";
	}

	private int fieldX() {
		return x + PADDING + LABEL_WIDTH;
	}

	private int fieldWidth() {
		return Math.max(50, controlsWidth() - LABEL_WIDTH - PADDING);
	}

	private record CircleRow(String label, int baseY, int height, @Nullable String section) {
	}

	private record ButtonSpec(String label, int minWidth, Runnable onPress) {
	}

	private EditBox makeEditBox(Font font, int x, int y, int w, String value, Consumer<String> responder) {
		EditBox box = new EditBox(font, x, y, w, 16, Component.empty());
		EditorTextBoxes.configure(box);
		box.setMaxLength(1024);
		box.setValue(value == null ? "" : value);
		box.setResponder(text -> {
			if (!suppress) {
				responder.accept(text);
			}
		});
		return box;
	}

	private EditBox addEditBox(Font font, int x, int y, int w, String value, Consumer<String> responder) {
		return addWidget(makeEditBox(font, x, y, w, value, responder));
	}

	private int addButton(int x, int y, int minWidth, String label, Runnable onPress) {
		String text = SpellEditorLocalization.t(label);
		int width = Math.max(minWidth, Minecraft.getInstance().font.width(text) + 12);
		addWidget(Button.builder(Component.literal(text), b -> onPress.run())
				.bounds(x, y, width, 16).build());
		return x + width + 4;
	}

	private <T extends AbstractWidget> T addWidget(T widget) {
		widgets.add(widget);
		widgetBaseY.put(widget, widget.getY());
		addWidgetCallback.accept(widget);
		updateWidget(widget);
		return widget;
	}

	/**
	 * \u56FA\u5B9A\u8868\u5934\u63A7\u4EF6\uFF1A\u4E0D\u767B\u8BB0 baseY\uFF0C{@link #updateWidget} \u56E0\u6B64\u4E0D\u4F1A\u968F\u6EDA\u52A8\u79FB\u52A8\u6216\u88C1\u526A\u5B83\u3002
	 */
	private <T extends AbstractWidget> T addFixedWidget(T widget) {
		widgets.add(widget);
		addWidgetCallback.accept(widget);
		updateWidget(widget);
		return widget;
	}

	private void refreshWidgetValues() {
		suppress = true;
		if (idBox != null) idBox.setValue(selectedId.toString());
		if (circleDropdownButton != null) {
			circleDropdownButton.setMessage(Component.literal(dropdownLabel(circleDropdownButton.getWidth())));
		}
		if (previewSizeBox != null) previewSizeBox.setValue(fmt(previewSize));
		SpellComponent.Stroke stroke = currentStroke();
		if (strokeColorBox != null) strokeColorBox.setValue(stroke == null || stroke.color == null ? "0xFFFFFFFF" : stroke.color);
		if (strokeRadiusBox != null) strokeRadiusBox.setValue(fmt(stroke == null ? 48 : stroke.radius));
		if (strokeWidthBox != null) strokeWidthBox.setValue(fmt(stroke == null ? 2 : stroke.width));
		if (strokeVertexBox != null) strokeVertexBox.setValue(String.valueOf(stroke == null ? 64 : stroke.vertex));
		if (strokeCycleBox != null) strokeCycleBox.setValue(String.valueOf(stroke == null ? 1 : stroke.cycle));
		if (strokeRuneBox != null) strokeRuneBox.setValue(String.valueOf(stroke == null ? 0 : stroke.rune));
		SpellComponent.ItemLayer item = currentItem();
		if (itemIdBox != null) itemIdBox.setValue(item == null ? "minecraft:air" : item.item);
		if (itemScaleBox != null) itemScaleBox.setValue(fmt(valueOf(item == null ? null : item.scale, 16)));
		if (itemRotationBox != null) itemRotationBox.setValue(fmt(valueOf(item == null ? null : item.rotation, 0)));
		if (itemAlphaBox != null) itemAlphaBox.setValue(fmt(valueOf(item == null ? null : item.alpha, 1)));
		SpellComponent.TextLayer text = currentText();
		if (textContentBox != null) textContentBox.setValue(text == null ? "" : text.text);
		if (textColorBox != null) textColorBox.setValue(currentTextColor());
		if (textScaleBox != null) textScaleBox.setValue(fmt(valueOf(text == null ? null : text.scale, 1)));
		if (textRotationBox != null) textRotationBox.setValue(fmt(valueOf(text == null ? null : text.rotation, 0)));
		if (textAlphaBox != null) textAlphaBox.setValue(fmt(valueOf(text == null ? null : text.alpha, 1)));
		if (textSpacingBox != null) textSpacingBox.setValue(fmt(text == null ? 0 : text.char_spacing));
		if (textRadiusBox != null) textRadiusBox.setValue(fmt(text == null ? 0 : text.radius));
		if (textArcSpanBox != null) textArcSpanBox.setValue(fmt(text == null ? 0 : text.arc_span));
		if (textXBox != null) textXBox.setValue(fmt(valueOf(text == null ? null : text.x_offset, 0)));
		if (textYBox != null) textYBox.setValue(fmt(valueOf(text == null ? null : text.y_offset, 0)));
		if (textZBox != null) textZBox.setValue(fmt(valueOf(text == null ? null : text.z_offset, 0)));
		SpellComponent.Layer layer = currentLayer();
		if (layerChildrenBox != null) layerChildrenBox.setValue(currentLayerChildren());
		if (layerRadiusBox != null) layerRadiusBox.setValue(fmt(valueOf(layer == null ? null : layer.radius, 0)));
		if (layerRotationBox != null) layerRotationBox.setValue(fmt(valueOf(layer == null ? null : layer.rotation, 0)));
		if (layerRotationSpeedBox != null) layerRotationSpeedBox.setValue(fmt(deltaOf(layer == null ? null : layer.rotation, 0)));
		if (layerScaleBox != null) layerScaleBox.setValue(fmt(valueOf(layer == null ? null : layer.scale, 1)));
		if (layerZBox != null) layerZBox.setValue(fmt(valueOf(layer == null ? null : layer.z_offset, 0)));
		if (layerAlphaBox != null) layerAlphaBox.setValue(fmt(valueOf(layer == null ? null : layer.alpha, 1)));
		suppress = false;
	}

	private void syncRawFromComponent() {
	}

	private void openCircleDropdown() {
		List<ResourceLocation> values = circleIds();
		if (!values.contains(selectedId)) {
			values = new ArrayList<>(values);
			values.add(selectedId);
			values.sort(java.util.Comparator.comparing(ResourceLocation::toString));
		}
		if (values.isEmpty() || circleDropdownButton == null) {
			return;
		}
		String[] options = new String[values.size()];
		int selectedIndex = -1;
		for (int i = 0; i < values.size(); i++) {
			ResourceLocation value = values.get(i);
			options[i] = value.toString();
			if (selectedIndex < 0 && value.equals(selectedId)) {
				selectedIndex = i;
			}
		}
		circleDropdown = new DropdownOverlay(List.copyOf(values), options, selectedIndex);
		circleDropdownHoverIndex = -1;
		int visibleItems = Math.min(options.length, DROPDOWN_MAX_VISIBLE);
		int maxScroll = Math.max(0, options.length - visibleItems);
		if (selectedIndex >= visibleItems) {
			circleDropdownScrollOffset = selectedIndex - visibleItems + 1;
		} else {
			circleDropdownScrollOffset = 0;
		}
		circleDropdownScrollOffset = Math.max(0, Math.min(maxScroll, circleDropdownScrollOffset));
	}

	private void closeCircleDropdown() {
		circleDropdown = null;
		circleDropdownHoverIndex = -1;
		circleDropdownScrollOffset = 0;
	}

	private int[] computeCircleDropdownBounds() {
		if (circleDropdown == null || circleDropdownButton == null) {
			return new int[]{0, 0, 0, 0, 0};
		}
		String[] options = circleDropdown.options();
		if (options == null || options.length == 0) {
			return new int[]{0, 0, 0, 0, 0};
		}
		int visibleItems = Math.min(options.length, DROPDOWN_MAX_VISIBLE);
		int totalH = visibleItems * DROPDOWN_ITEM_H;
		int dropdownX = circleDropdownButton.getX();
		int dropdownY = circleDropdownButton.getY() + circleDropdownButton.getHeight();
		int dropdownW = circleDropdownButton.getWidth();
		if (dropdownY + totalH > y + h) {
			dropdownY = circleDropdownButton.getY() - totalH;
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

	private void renderCircleDropdown(GuiGraphics graphics, int mouseX, int mouseY) {
		if (circleDropdown == null) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int[] bounds = computeCircleDropdownBounds();
		int dx = bounds[0], dy = bounds[1], dw = bounds[2], dh = bounds[3];
		int visibleItems = bounds[4];
		String[] options = circleDropdown.options();
		if (options == null || options.length == 0) {
			return;
		}

		boolean needsScroll = options.length > visibleItems;
		int scrollbarW = needsScroll ? SCROLLBAR_WIDTH : 0;

		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 200);
		graphics.fill(dx + 3, dy + 3, dx + dw + 3, dy + dh + 3, 0x88000000);
		graphics.fill(dx, dy, dx + dw, dy + dh, 0xFF1a1a30);
		graphics.fill(dx, dy, dx + dw, dy + 1, 0xFF666688);
		graphics.fill(dx, dy + dh - 1, dx + dw, dy + dh, 0xFF666688);
		graphics.fill(dx, dy, dx + 1, dy + dh, 0xFF666688);
		graphics.fill(dx + dw - 1, dy, dx + dw, dy + dh, 0xFF666688);

		circleDropdownHoverIndex = -1;
		int contentW = dw - scrollbarW;
		if (mouseX >= dx && mouseX < dx + contentW && mouseY >= dy && mouseY < dy + dh) {
			int rawIdx = (mouseY - dy) / DROPDOWN_ITEM_H + circleDropdownScrollOffset;
			if (rawIdx >= 0 && rawIdx < options.length) {
				circleDropdownHoverIndex = rawIdx;
			}
		}

		int visCount = Math.min(options.length, dh / DROPDOWN_ITEM_H);
		for (int i = 0; i < visCount; i++) {
			int optIdx = i + circleDropdownScrollOffset;
			if (optIdx >= options.length) {
				break;
			}
			int itemY = dy + i * DROPDOWN_ITEM_H;
			boolean isHovered = optIdx == circleDropdownHoverIndex;
			boolean isSelected = optIdx == circleDropdown.selectedIndex();
			if (isHovered) {
				graphics.fill(dx + 1, itemY, dx + contentW - 1, itemY + DROPDOWN_ITEM_H, 0x44FFFFFF);
			}
			int textX = dx + 4;
			if (isSelected) {
				graphics.drawString(font, "\u25B6", dx + 3, itemY + 4, 0xFFFFCC44, false);
				textX = dx + 14;
			}
			int textColor = isHovered ? 0xFFFFDD66 : (isSelected ? 0xFFFFCC88 : 0xFFDDDDDD);
			graphics.drawString(font, fitToWidth(options[optIdx], contentW - textX + dx - 4), textX, itemY + 4, textColor, false);
		}

		if (needsScroll) {
			int sbX = dx + dw - scrollbarW;
			graphics.fill(sbX, dy, sbX + scrollbarW, dy + dh, 0x33FFFFFF);
			int trackH = dh - 2;
			int thumbH = Math.max(10, trackH * visibleItems / options.length);
			int maxScroll = Math.max(1, options.length - visibleItems);
			int thumbTravel = trackH - thumbH;
			if (thumbTravel > 0) {
				int thumbY = dy + 1 + thumbTravel * circleDropdownScrollOffset / maxScroll;
				graphics.fill(sbX + 1, thumbY, sbX + scrollbarW - 1, thumbY + thumbH, 0xAAAAAACC);
			}
		}
		graphics.pose().popPose();
	}

	private boolean handleCircleDropdownClick(double mouseX, double mouseY) {
		if (circleDropdown == null) {
			return false;
		}
		int[] bounds = computeCircleDropdownBounds();
		int dx = bounds[0], dy = bounds[1], dw = bounds[2], dh = bounds[3];
		int visibleItems = bounds[4];
		boolean needsScroll = circleDropdown.options().length > visibleItems;
		int scrollbarW = needsScroll ? SCROLLBAR_WIDTH : 0;
		int contentW = dw - scrollbarW;
		if (mouseX >= dx && mouseX < dx + contentW && mouseY >= dy && mouseY < dy + dh) {
			int optIdx = (int) ((mouseY - dy) / DROPDOWN_ITEM_H) + circleDropdownScrollOffset;
			if (optIdx >= 0 && optIdx < circleDropdown.values().size()) {
				ResourceLocation selected = circleDropdown.values().get(optIdx);
				closeCircleDropdown();
				selectCircle(selected);
				return true;
			}
		}
		return false;
	}

	private void publishLocal(boolean syncRaw) {
		lastSelectedId = selectedId;
		component.invalidateCache();
		YoukaisHomecoming.SPELL.getMerged().map.put(selectedId.toString(), component);
		if (!linkedComponents.isEmpty()) {
			linkedComponents.put(selectedId, cloneComponent(component));
			for (var entry : linkedComponents.entrySet()) {
				entry.getValue().invalidateCache();
				YoukaisHomecoming.SPELL.getMerged().map.put(entry.getKey().toString(), entry.getValue());
			}
		}
		if (previewActive) {
			viewport.setMagicCirclePreview(selectedId, component, previewSize);
		}
	}

	private void onComponentEdited(String message) {
		clampSelection();
		publishLocal(true);
		setStatus(message, 0xFF88FF88);
	}

	public void applyRawJson(String text) {
		ParsedCircle parsed = parseRawJson(text);
		if (parsed == null || parsed.component() == null) {
			throw new IllegalArgumentException("Invalid magic circle JSON");
		}
		if (!parsed.components().isEmpty()) {
			linkedComponents.clear();
			for (var entry : parsed.components().entrySet()) {
				SpellComponent value = cloneComponent(entry.getValue());
				value.invalidateCache();
				linkedComponents.put(entry.getKey(), value);
				YoukaisHomecoming.SPELL.getMerged().map.put(entry.getKey().toString(), value);
			}
		}
		if (parsed.id() != null) {
			selectedId = parsed.id();
		}
		// 行结构（分区计数、直排/弧排字段集）依赖元素数量，数量变了必须重建行；
		// 本方法在 raw json 面板里是逐键调用的，所以数量不变时只刷新取值。
		int strokesBefore = component.strokes.size();
		int itemsBefore = getItemCount();
		int textsBefore = getTextCount();
		int layersBefore = component.layers.size();
		component = cloneComponent(parsed.component());
		component.invalidateCache();
		clampSelection();
		publishLocal(false);
		boolean structureChanged = strokesBefore != component.strokes.size()
				|| itemsBefore != getItemCount()
				|| textsBefore != getTextCount()
				|| layersBefore != component.layers.size();
		if (structureChanged) {
			rebuildWidgets();
		} else if (active) {
			refreshWidgetValues();
		}
		setStatus("Magic Circle JSON applied", 0xFF88FF88);
	}

	@Nullable
	private ParsedCircle parseRawJson(String text) {
		JsonElement json = JsonParser.parseString(text);
		if (!json.isJsonObject()) {
			return null;
		}
		JsonObject object = json.getAsJsonObject();
		if (object.has("map") && object.get("map").isJsonObject()) {
			JsonObject map = object.getAsJsonObject("map");
			if (map.entrySet().isEmpty()) {
				return null;
			}
			String key = selectedId.toString();
			Map.Entry<String, JsonElement> entry = map.has(key)
					? Map.entry(key, map.get(key))
					: map.entrySet().iterator().next();
			ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
			Map<ResourceLocation, SpellComponent> components = new LinkedHashMap<>();
			for (var componentEntry : map.entrySet()) {
				ResourceLocation componentId = ResourceLocation.tryParse(componentEntry.getKey());
				if (componentId == null) {
					throw new IllegalArgumentException("Invalid magic circle id: " + componentEntry.getKey());
				}
				SpellComponent component = GSON.fromJson(componentEntry.getValue(), SpellComponent.class);
				if (component == null) {
					throw new IllegalArgumentException("Invalid magic circle component: " + componentEntry.getKey());
				}
				component.invalidateCache();
				components.put(componentId, component);
			}
			return new ParsedCircle(id, components.get(id), components);
		}
		SpellComponent component = GSON.fromJson(object, SpellComponent.class);
		if (component == null) {
			return null;
		}
		component.invalidateCache();
		Map<ResourceLocation, SpellComponent> components = new LinkedHashMap<>();
		components.put(selectedId, component);
		return new ParsedCircle(selectedId, component, components);
	}

	public String encodeRawJson() {
		component.invalidateCache();
		JsonObject map = new JsonObject();
		Map<ResourceLocation, SpellComponent> components = componentsForSave();
		for (var entry : components.entrySet()) {
			entry.getValue().invalidateCache();
			map.add(entry.getKey().toString(), GSON.toJsonTree(entry.getValue()));
		}
		JsonObject root = new JsonObject();
		root.add("map", map);
		return GSON.toJson(root);
	}

	private void selectPreviousCircle() {
		List<ResourceLocation> ids = circleIds();
		if (ids.isEmpty()) return;
		int idx = ids.indexOf(selectedId);
		if (idx < 0) idx = 0;
		selectCircle(ids.get((idx - 1 + ids.size()) % ids.size()));
	}

	private void selectNextCircle() {
		List<ResourceLocation> ids = circleIds();
		if (ids.isEmpty()) return;
		int idx = ids.indexOf(selectedId);
		if (idx < 0) idx = -1;
		selectCircle(ids.get((idx + 1) % ids.size()));
	}

	private void selectCircle(ResourceLocation id) {
		selectedId = id;
		loadSelectedComponent();
		rebuildWidgets();
		setStatus("Magic Circle loaded", 0xFF88AACC);
	}

	private void newCircle() {
		selectedId = nextCustomId();
		component = createDefaultComponent();
		linkedComponents.clear();
		selectedStroke = 0;
		selectedItem = 0;
		selectedText = 0;
		selectedLayer = 0;
		scrollOffset = 0;
		publishLocal(true);
		rebuildWidgets();
		setStatus("Magic Circle created", 0xFF88FF88);
	}

	private void save(boolean global) {
		ResourceLocation id = selectedId;
		selectedId = id;
		publishLocal(true);
		Map<ResourceLocation, SpellComponent> components = componentsForSave();
		if (global) {
			SpellCircleEditorNetworkClient.exportGlobal(id, components);
			setStatus("Magic Circle export sent", 0xFF88FF88);
		} else {
			SpellCircleEditorNetworkClient.save(id, components);
			setStatus("Magic Circle save sent", 0xFF88FF88);
		}
	}

	private void deleteCircle() {
		ResourceLocation removed = selectedId;
		List<ResourceLocation> before = circleIds();
		int removedIndex = before.indexOf(removed);
		SpellCircleEditorNetworkClient.delete(removed);
		removeLocalCircle(removed);
		ResourceLocation next = nextSelectionAfterDelete(removed, removedIndex);
		if (next == null) {
			selectedId = nextCustomId();
			component = createDefaultComponent();
			linkedComponents.clear();
			selectedStroke = 0;
			selectedItem = 0;
			selectedLayer = 0;
			selectedText = 0;
			scrollOffset = 0;
			publishLocal(true);
			rebuildWidgets();
		} else {
			selectCircle(next);
		}
		setStatus("Magic Circle delete sent", 0xFF88FF88);
	}

	@Nullable
	private ResourceLocation nextSelectionAfterDelete(ResourceLocation removed, int removedIndex) {
		for (ResourceLocation id : linkedComponents.keySet()) {
			if (!id.equals(removed) && YoukaisHomecoming.SPELL.getMerged().map.containsKey(id.toString())) {
				return id;
			}
		}
		List<ResourceLocation> ids = circleIds();
		if (ids.isEmpty()) {
			return null;
		}
		int index = removedIndex < 0 ? 0 : Math.min(removedIndex, ids.size() - 1);
		return ids.get(index);
	}

	private void removeLocalCircle(ResourceLocation id) {
		String key = id.toString();
		YoukaisHomecoming.SPELL.getMerged().map.remove(key);
		linkedComponents.remove(id);
		removeChildReferences(component, key);
		for (var entry : linkedComponents.entrySet()) {
			removeChildReferences(entry.getValue(), key);
			entry.getValue().invalidateCache();
			YoukaisHomecoming.SPELL.getMerged().map.put(entry.getKey().toString(), entry.getValue());
		}
		for (SpellComponent value : YoukaisHomecoming.SPELL.getMerged().map.values()) {
			if (value != null) {
				value.invalidateCache();
			}
		}
	}

	private static void removeChildReferences(SpellComponent component, String childId) {
		if (component == null || component.layers == null) {
			return;
		}
		for (SpellComponent.Layer layer : component.layers) {
			if (layer == null || layer.children == null) {
				continue;
			}
			if (layer.children.removeIf(childId::equals)) {
				layer.invalidateCache();
			}
		}
		component.invalidateCache();
	}

	private void addStroke() {
		component.strokes.add(defaultStroke());
		selectedStroke = component.strokes.size() - 1;
		rebuildWidgets();
		onComponentEdited("Stroke added");
	}

	private void removeStroke() {
		if (component.strokes.isEmpty()) return;
		component.strokes.remove(Math.min(selectedStroke, component.strokes.size() - 1));
		clampSelection();
		rebuildWidgets();
		onComponentEdited("Stroke removed");
	}

	private void prevStroke() {
		if (component.strokes.isEmpty()) return;
		selectedStroke = (selectedStroke - 1 + component.strokes.size()) % component.strokes.size();
		rebuildWidgets();
	}

	private void nextStroke() {
		if (component.strokes.isEmpty()) return;
		selectedStroke = (selectedStroke + 1) % component.strokes.size();
		rebuildWidgets();
	}

	private void addItem() {
		component.items.add(defaultItem());
		selectedItem = component.items.size() - 1;
		rebuildWidgets();
		onComponentEdited("Item node added");
	}

	private void removeItem() {
		if (component.items.isEmpty()) return;
		component.items.remove(Math.min(selectedItem, component.items.size() - 1));
		clampSelection();
		rebuildWidgets();
		onComponentEdited("Item node removed");
	}

	private void prevItem() {
		if (component.items.isEmpty()) return;
		selectedItem = (selectedItem - 1 + component.items.size()) % component.items.size();
		rebuildWidgets();
	}

	private void nextItem() {
		if (component.items.isEmpty()) return;
		selectedItem = (selectedItem + 1) % component.items.size();
		rebuildWidgets();
	}

	// --- Text layers ---

	public int getTextCount() {
		return component.texts == null ? 0 : component.texts.size();
	}

	@Nullable
	private SpellComponent.TextLayer currentText() {
		int count = getTextCount();
		if (count == 0) return null;
		return component.texts.get(Math.max(0, Math.min(selectedText, count - 1)));
	}

	private void addText() {
		component.texts.add(defaultText());
		selectedText = component.texts.size() - 1;
		rebuildWidgets();
		onComponentEdited("Text node added");
	}

	private void removeText() {
		if (getTextCount() == 0) return;
		component.texts.remove(Math.min(selectedText, component.texts.size() - 1));
		clampSelection();
		rebuildWidgets();
		onComponentEdited("Text node removed");
	}

	private void prevText() {
		int count = getTextCount();
		if (count == 0) return;
		selectedText = (selectedText - 1 + count) % count;
		rebuildWidgets();
	}

	private void nextText() {
		int count = getTextCount();
		if (count == 0) return;
		selectedText = (selectedText + 1) % count;
		rebuildWidgets();
	}

	private String currentTextColor() {
		SpellComponent.TextLayer text = currentText();
		return text == null || text.color == null ? "0xFFFFFFFF" : text.color;
	}

	private void setTextContent(String value) {
		SpellComponent.TextLayer text = currentText();
		if (text == null) return;
		text.text = value == null ? "" : value;
		onComponentEdited("Text changed");
	}

	private void setTextColor(String value) {
		SpellComponent.TextLayer text = currentText();
		if (text == null) return;
		if (!isValidColor(value)) {
			setStatus("Invalid color", 0xFFFF8888);
			return;
		}
		text.color = value;
		onComponentEdited("Text color changed");
	}

	private void setTextValue(String value, String field, float fallback) {
		SpellComponent.TextLayer text = currentText();
		if (text == null) return;
		float parsed = parseFloat(value, Float.NaN);
		if (!Float.isFinite(parsed)) return;
		switch (field) {
			case "scale" -> text.scale = withValue(text.scale, fallback, parsed);
			case "rotation" -> text.rotation = withValue(text.rotation, fallback, parsed);
			case "alpha" -> text.alpha = withValue(text.alpha, fallback, parsed);
			case "x" -> text.x_offset = withValue(text.x_offset, fallback, parsed);
			case "y" -> text.y_offset = withValue(text.y_offset, fallback, parsed);
			case "z" -> text.z_offset = withValue(text.z_offset, fallback, parsed);
			default -> {
				return;
			}
		}
		onComponentEdited("Text changed");
	}

	private void setTextSpacing(String value) {
		SpellComponent.TextLayer text = currentText();
		if (text == null) return;
		float parsed = parseFloat(value, Float.NaN);
		if (!Float.isFinite(parsed)) return;
		text.char_spacing = parsed;
		onComponentEdited("Text changed");
	}

	private void setTextRadius(String value) {
		SpellComponent.TextLayer text = currentText();
		if (text == null) return;
		float parsed = parseFloat(value, Float.NaN);
		if (!Float.isFinite(parsed)) return;
		boolean wasArc = text.radius > 0;
		text.radius = Math.max(0, parsed);
		publishLocal(true);
		setStatus("Text changed", 0xFF88FF88);
		// 直排与弧排的字段集合不同，跨越 0 时必须重建行。
		if (wasArc != (text.radius > 0)) {
			rebuildWidgets();
		}
	}

	private void setTextArcSpan(String value) {
		SpellComponent.TextLayer text = currentText();
		if (text == null) return;
		float parsed = parseFloat(value, Float.NaN);
		if (!Float.isFinite(parsed)) return;
		text.arc_span = parsed;
		onComponentEdited("Text changed");
	}

	private void toggleTextFlip() {
		SpellComponent.TextLayer text = currentText();
		if (text == null) return;
		text.flip = !text.flip;
		rebuildWidgets();
		onComponentEdited("Text changed");
	}

	private static SpellComponent.Value withValue(@Nullable SpellComponent.Value existing, float fallback, float value) {
		SpellComponent.Value val = editableValue(existing, fallback);
		val.value = value;
		return val;
	}

	/**
	 * 新建文字层的默认值。直接给成环绕排布 —— 这是魔法阵最典型的用法，
	 * 点一下 +Text 就能立刻看到效果，而不是一行几乎看不见的小字。
	 * 半径对齐默认组件的外圈笔画（48）。
	 */
	private static SpellComponent.TextLayer defaultText() {
		SpellComponent.TextLayer text = new SpellComponent.TextLayer();
		text.text = "妖々夢";
		text.color = "0xFFFFFFFF";
		text.scale = value(2);
		text.alpha = value(1);
		text.radius = 40;
		return text;
	}

	private void addLayer() {
		component.layers.add(defaultLayer());
		selectedLayer = component.layers.size() - 1;
		rebuildWidgets();
		onComponentEdited("Layer added");
	}

	private void removeLayer() {
		if (component.layers.isEmpty()) return;
		component.layers.remove(Math.min(selectedLayer, component.layers.size() - 1));
		clampSelection();
		rebuildWidgets();
		onComponentEdited("Layer removed");
	}

	private void prevLayer() {
		if (component.layers.isEmpty()) return;
		selectedLayer = (selectedLayer - 1 + component.layers.size()) % component.layers.size();
		rebuildWidgets();
	}

	private void nextLayer() {
		if (component.layers.isEmpty()) return;
		selectedLayer = (selectedLayer + 1) % component.layers.size();
		rebuildWidgets();
	}

	private void addChildComponent() {
		if (component.layers == null) {
			component.layers = new ArrayList<>();
		}
		if (component.layers.isEmpty()) {
			component.layers.add(defaultLayer());
			selectedLayer = 0;
		}
		SpellComponent.Layer layer = currentLayer();
		if (layer == null) {
			return;
		}
		ResourceLocation childId = nextChildId(selectedId);
		SpellComponent child = createDefaultComponent();
		child.invalidateCache();
		if (layer.children == null) {
			layer.children = new ArrayList<>();
		}
		layer.children.add(childId.toString());
		layer.invalidateCache();
		linkedComponents.put(childId, child);
		YoukaisHomecoming.SPELL.getMerged().map.put(childId.toString(), child);
		rebuildWidgets();
		onComponentEdited("Child component added");
	}

	private void openFirstChildComponent() {
		SpellComponent.Layer layer = currentLayer();
		if (layer == null || layer.children == null || layer.children.isEmpty()) {
			setStatus("No child component", 0xFFFFCC88);
			return;
		}
		for (String child : layer.children) {
			ResourceLocation id = ResourceLocation.tryParse(child);
			if (id != null) {
				selectCircle(id);
				setStatus("Child component loaded", 0xFF88AACC);
				return;
			}
		}
		setStatus("No child component", 0xFFFFCC88);
	}

	private void setStrokeColor(String text) {
		SpellComponent.Stroke stroke = currentStroke();
		if (stroke == null) return;
		if (!isValidColor(text)) {
			setStatus("Invalid color", 0xFFFF8888);
			return;
		}
		stroke.color = text;
		onComponentEdited("Stroke color changed");
	}

	private void setStrokeFloat(String text, String field) {
		SpellComponent.Stroke stroke = currentStroke();
		if (stroke == null) return;
		float value = parseFloat(text, Float.NaN);
		if (!Float.isFinite(value)) return;
		if ("radius".equals(field)) {
			stroke.radius = value;
		} else if ("width".equals(field)) {
			stroke.width = value;
		}
		onComponentEdited("Stroke changed");
	}

	private void setStrokeInt(String text, String field) {
		SpellComponent.Stroke stroke = currentStroke();
		if (stroke == null) return;
		int value = parseInt(text, Integer.MIN_VALUE);
		if (value == Integer.MIN_VALUE) return;
		if ("vertex".equals(field)) {
			stroke.vertex = Math.max(3, value);
		} else if ("cycle".equals(field)) {
			stroke.cycle = Math.max(1, value);
		} else if ("rune".equals(field)) {
			stroke.rune = Math.max(0, value);
		}
		onComponentEdited("Stroke changed");
	}

	private void setItemId(String text) {
		SpellComponent.ItemLayer item = currentItem();
		if (item == null) return;
		item.item = text;
		item.invalidateCache();
		onComponentEdited("Item changed");
	}

	private void setItemValue(String text, String field, float fallback) {
		SpellComponent.ItemLayer item = currentItem();
		if (item == null) return;
		float value = parseFloat(text, Float.NaN);
		if (!Float.isFinite(value)) return;
		if ("scale".equals(field)) {
			SpellComponent.Value val = editableValue(item.scale, fallback);
			val.value = value;
			item.scale = val;
		} else if ("rotation".equals(field)) {
			SpellComponent.Value val = editableValue(item.rotation, fallback);
			val.value = value;
			item.rotation = val;
		} else if ("alpha".equals(field)) {
			SpellComponent.Value val = editableValue(item.alpha, fallback);
			val.value = value;
			item.alpha = val;
		}
		onComponentEdited("Item changed");
	}

	private void setLayerChildren(String text) {
		SpellComponent.Layer layer = currentLayer();
		if (layer == null) return;
		ArrayList<String> children = parseChildren(text);
		if (children.isEmpty() && text != null && !text.isBlank()) {
			setStatus("Invalid child id", 0xFFFF8888);
			return;
		}
		layer.children = children;
		layer.invalidateCache();
		onComponentEdited("Layer children changed");
	}

	private void setLayerValue(String text, String field, float fallback) {
		SpellComponent.Layer layer = currentLayer();
		if (layer == null) return;
		float value = parseFloat(text, Float.NaN);
		if (!Float.isFinite(value)) return;
		if ("radius".equals(field)) {
			SpellComponent.Value val = editableValue(layer.radius, fallback);
			val.value = value;
			layer.radius = val;
		} else if ("rotation".equals(field)) {
			SpellComponent.Value val = editableValue(layer.rotation, fallback);
			val.value = value;
			layer.rotation = val;
		} else if ("scale".equals(field)) {
			SpellComponent.Value val = editableValue(layer.scale, fallback);
			val.value = value;
			layer.scale = val;
		} else if ("z".equals(field)) {
			SpellComponent.Value val = editableValue(layer.z_offset, fallback);
			val.value = value;
			layer.z_offset = val;
		} else if ("alpha".equals(field)) {
			SpellComponent.Value val = editableValue(layer.alpha, fallback);
			val.value = value;
			layer.alpha = val;
		}
		layer.invalidateCache();
		onComponentEdited("Layer changed");
	}

	private void setLayerRotationSpeed(String text) {
		SpellComponent.Layer layer = currentLayer();
		if (layer == null) return;
		float value = parseFloat(text, Float.NaN);
		if (!Float.isFinite(value)) return;
		SpellComponent.Value rotation = editableValue(layer.rotation, 0);
		rotation.delta = value;
		layer.rotation = rotation;
		layer.invalidateCache();
		onComponentEdited("Layer changed");
	}

	private void setPreviewSize(String text) {
		float value = parseFloat(text, Float.NaN);
		if (!Float.isFinite(value)) return;
		previewSize = Math.max(0.0f, Math.min(64.0f, value));
		publishLocal(false);
		setStatus("Preview size changed", 0xFF88FF88);
	}

	@Nullable
	private SpellComponent.Stroke currentStroke() {
		return component.strokes.isEmpty() ? null : component.strokes.get(Math.min(selectedStroke, component.strokes.size() - 1));
	}

	@Nullable
	private SpellComponent.ItemLayer currentItem() {
		int index = getSelectedItemIndex();
		return index < 0 ? null : itemAt(index);
	}

	@Nullable
	private SpellComponent.Layer currentLayer() {
		int index = getSelectedLayerIndex();
		return index < 0 ? null : layerAt(index);
	}

	public int getItemCount() {
		return component.items == null ? 0 : component.items.size();
	}

	public int getSelectedItemIndex() {
		int count = getItemCount();
		return count == 0 ? -1 : Math.max(0, Math.min(selectedItem, count - 1));
	}

	public Vec3 getItemPosition(int index) {
		SpellComponent.ItemLayer item = itemAt(index);
		if (item == null) {
			return Vec3.ZERO;
		}
		return new Vec3(
				valueOf(item.x_offset, 0),
				valueOf(item.y_offset, 0),
				valueOf(item.z_offset, 0)
		);
	}

	public float getItemScale(int index) {
		SpellComponent.ItemLayer item = itemAt(index);
		return valueOf(item == null ? null : item.scale, 16);
	}

	public boolean selectItem(int index) {
		if (index < 0 || index >= getItemCount()) {
			return false;
		}
		selectedItem = index;
		refreshWidgetValues();
		setStatus("Item selected", 0xFF88AACC);
		return true;
	}

	public int getLayerCount() {
		return component.layers == null ? 0 : component.layers.size();
	}

	public int getSelectedLayerIndex() {
		int count = getLayerCount();
		return count == 0 ? -1 : Math.max(0, Math.min(selectedLayer, count - 1));
	}

	public void moveSelectedItem(double dx, double dy) {
		SpellComponent.ItemLayer item = currentItem();
		if (item == null || (!Double.isFinite(dx) && !Double.isFinite(dy))) {
			return;
		}
		if (Double.isFinite(dx) && Math.abs(dx) > 1.0e-5) {
			SpellComponent.Value x = editableValue(item.x_offset, 0);
			x.value += (float) dx;
			item.x_offset = x;
		}
		if (Double.isFinite(dy) && Math.abs(dy) > 1.0e-5) {
			SpellComponent.Value y = editableValue(item.y_offset, 0);
			y.value += (float) dy;
			item.y_offset = y;
		}
		refreshWidgetValues();
		onComponentEdited("Item moved");
	}

	public void rotateSelectedItem(double degrees) {
		SpellComponent.ItemLayer item = currentItem();
		if (item == null || !Double.isFinite(degrees) || Math.abs(degrees) <= 1.0e-5) {
			return;
		}
		SpellComponent.Value rotation = editableValue(item.rotation, 0);
		rotation.value = wrapDegrees(rotation.value + (float) degrees);
		item.rotation = rotation;
		refreshWidgetValues();
		onComponentEdited("Item rotated");
	}

	@Nullable
	private SpellComponent.ItemLayer itemAt(int index) {
		if (component.items == null || component.items.isEmpty()) {
			return null;
		}
		if (index < 0 || index >= component.items.size()) {
			return null;
		}
		return component.items.get(index);
	}

	@Nullable
	private SpellComponent.Layer layerAt(int index) {
		if (component.layers == null || component.layers.isEmpty()) {
			return null;
		}
		if (index < 0 || index >= component.layers.size()) {
			return null;
		}
		return component.layers.get(index);
	}

	private String currentLayerChildren() {
		SpellComponent.Layer layer = currentLayer();
		if (layer == null || layer.children == null || layer.children.isEmpty()) {
			return "";
		}
		return String.join(", ", layer.children);
	}

	private String currentStrokeColor() {
		SpellComponent.Stroke stroke = currentStroke();
		return stroke == null || stroke.color == null ? "0xFFFFFFFF" : stroke.color;
	}

	/**
	 * 面板会在每次 Screen 重建（切模式、切语言等）时重新实例化。
	 * 把上次选中的魔法阵 ID 记在会话级静态字段里，否则每次重建都会跳回第一个魔法阵。
	 */
	private void loadInitialSelection() {
		List<ResourceLocation> ids = circleIds();
		if (ids.isEmpty()) {
			return;
		}
		selectedId = lastSelectedId != null && ids.contains(lastSelectedId) ? lastSelectedId : ids.get(0);
		loadSelectedComponent();
	}

	private void loadSelectedComponent() {
		SpellComponent existing = linkedComponents.get(selectedId);
		if (existing == null) {
			existing = YoukaisHomecoming.SPELL.getMerged().map.get(selectedId.toString());
			linkedComponents.clear();
			if (existing != null) {
				linkedComponents.put(selectedId, cloneComponent(existing));
				collectReferencedComponents(linkedComponents);
			}
		}
		// Snapshot before the editor starts mutating it — this is what Reset restores.
		captureSnapshot(selectedId, existing);
		component = existing == null ? createDefaultComponent() : cloneComponent(existing);
		clampSelection();
		publishLocal(true);
	}

	private void clampSelection() {
		selectedStroke = component.strokes.isEmpty() ? 0 : Math.max(0, Math.min(selectedStroke, component.strokes.size() - 1));
		selectedItem = component.items.isEmpty() ? 0 : Math.max(0, Math.min(selectedItem, component.items.size() - 1));
		selectedText = getTextCount() == 0 ? 0 : Math.max(0, Math.min(selectedText, getTextCount() - 1));
		selectedLayer = component.layers.isEmpty() ? 0 : Math.max(0, Math.min(selectedLayer, component.layers.size() - 1));
	}

	private Map<ResourceLocation, SpellComponent> componentsForSave() {
		Map<ResourceLocation, SpellComponent> components = new LinkedHashMap<>();
		for (var entry : linkedComponents.entrySet()) {
			components.put(entry.getKey(), cloneComponent(entry.getValue()));
		}
		components.put(selectedId, cloneComponent(component));
		collectReferencedComponents(components);
		return components;
	}

	private static void collectReferencedComponents(Map<ResourceLocation, SpellComponent> components) {
		Set<ResourceLocation> seen = new HashSet<>(components.keySet());
		List<ResourceLocation> queue = new ArrayList<>(components.keySet());
		for (int i = 0; i < queue.size(); i++) {
			SpellComponent source = components.get(queue.get(i));
			if (source == null || source.layers == null) {
				continue;
			}
			for (SpellComponent.Layer layer : source.layers) {
				if (layer == null || layer.children == null) {
					continue;
				}
				for (String child : layer.children) {
					ResourceLocation childId = ResourceLocation.tryParse(child);
					if (childId == null || seen.contains(childId)) {
						continue;
					}
					SpellComponent childComponent = YoukaisHomecoming.SPELL.getMerged().map.get(childId.toString());
					if (childComponent == null) {
						continue;
					}
					seen.add(childId);
					components.put(childId, cloneComponent(childComponent));
					queue.add(childId);
				}
			}
		}
	}

	private static SpellComponent cloneComponent(SpellComponent component) {
		component.invalidateCache();
		SpellComponent clone = GSON.fromJson(GSON.toJsonTree(component), SpellComponent.class);
		if (clone == null) {
			return createDefaultComponent();
		}
		clone.invalidateCache();
		return clone;
	}

	private static SpellComponent createDefaultComponent() {
		SpellComponent component = new SpellComponent();
		component.strokes.add(defaultStroke());
		SpellComponent.Stroke inner = defaultStroke();
		inner.color = "0xFF66CCFF";
		inner.radius = 28;
		inner.width = 1;
		inner.vertex = 6;
		inner.cycle = 2;
		component.strokes.add(inner);
		return component;
	}

	private static SpellComponent.Stroke defaultStroke() {
		SpellComponent.Stroke stroke = new SpellComponent.Stroke();
		stroke.vertex = 64;
		stroke.cycle = 1;
		stroke.rune = 1;
		stroke.color = "0xFFFFFFFF";
		stroke.radius = 48;
		stroke.width = 3;
		return stroke;
	}

	private static SpellComponent.ItemLayer defaultItem() {
		SpellComponent.ItemLayer item = new SpellComponent.ItemLayer();
		item.item = "minecraft:ender_eye";
		item.scale = value(16);
		item.rotation = value(0);
		item.alpha = value(1);
		return item;
	}

	private static SpellComponent.Layer defaultLayer() {
		SpellComponent.Layer layer = new SpellComponent.Layer();
		layer.children = new ArrayList<>();
		layer.radius = value(0);
		layer.rotation = value(0);
		layer.scale = value(1);
		layer.z_offset = value(0);
		layer.alpha = value(1);
		return layer;
	}

	private static SpellComponent.Value value(float value) {
		SpellComponent.Value val = new SpellComponent.Value();
		val.value = value;
		return val;
	}

	private static SpellComponent.Value editableValue(@Nullable SpellComponent.Value value, float fallback) {
		return value == null ? value(fallback) : value;
	}

	private static float valueOf(@Nullable SpellComponent.Value value, float fallback) {
		return value == null ? fallback : value.value;
	}

	private static float deltaOf(@Nullable SpellComponent.Value value, float fallback) {
		return value == null ? fallback : value.delta;
	}

	private static float wrapDegrees(float value) {
		float ans = value % 360.0f;
		if (ans < -180.0f) {
			ans += 360.0f;
		}
		if (ans > 180.0f) {
			ans -= 360.0f;
		}
		return ans;
	}

	private ResourceLocation nextCustomId() {
		int index = 1;
		while (true) {
			ResourceLocation id = new ResourceLocation("youkaishomecoming", "custom_circle_" + index);
			if (!YoukaisHomecoming.SPELL.getMerged().map.containsKey(id.toString())) {
				return id;
			}
			index++;
		}
	}

	private ResourceLocation nextChildId(ResourceLocation parent) {
		int index = 1;
		String path = parent.getPath() + "_child_";
		while (true) {
			ResourceLocation id = new ResourceLocation(parent.getNamespace(), path + index);
			if (!linkedComponents.containsKey(id) && !YoukaisHomecoming.SPELL.getMerged().map.containsKey(id.toString())) {
				return id;
			}
			index++;
		}
	}

	private static List<ResourceLocation> circleIds() {
		List<ResourceLocation> ids = new ArrayList<>();
		for (String key : YoukaisHomecoming.SPELL.getMerged().map.keySet()) {
			ResourceLocation id = ResourceLocation.tryParse(key);
			if (id != null) ids.add(id);
		}
		ids.sort(java.util.Comparator.comparing(ResourceLocation::toString));
		return ids;
	}

	private int controlsWidth() {
		return Math.max(140, w - PADDING * 2 - SCROLLBAR_WIDTH - 2);
	}

	/** 正文顶部：让开固定表头（circle 下拉、ID、Preview 三行）与其分隔线。 */
	private int contentTop() {
		return y + PADDING + ROW * 3 + 2;
	}

	private int contentBottom() {
		return Math.max(contentTop(), y + h - STATUS_HEIGHT - 2);
	}

	private int visibleContentHeight() {
		return Math.max(0, contentBottom() - contentTop());
	}

	private int maxScroll() {
		return Math.max(0, contentHeight - visibleContentHeight());
	}

	private void clampScrollOffset() {
		scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset));
	}

	private void updateWidgetScroll() {
		for (AbstractWidget widget : widgets) {
			updateWidget(widget);
		}
	}

	private void updateWidget(AbstractWidget widget) {
		Integer baseY = widgetBaseY.get(widget);
		if (baseY == null) {
			widget.visible = active;
			return;
		}
		widget.setY(baseY - scrollOffset);
		int top = contentTop();
		int bottom = contentBottom();
		widget.visible = active && widget.getY() >= top && widget.getY() + widget.getHeight() <= bottom;
	}

	private void renderScrollbar(GuiGraphics graphics) {
		int maxScroll = maxScroll();
		if (maxScroll <= 0) {
			return;
		}
		int trackTop = contentTop();
		int trackBottom = contentBottom();
		int trackH = Math.max(1, trackBottom - trackTop);
		int sbX = x + w - SCROLLBAR_WIDTH - 2;
		graphics.fill(sbX, trackTop, sbX + SCROLLBAR_WIDTH, trackBottom, 0x33FFFFFF);
		int thumbH = Math.max(10, trackH * visibleContentHeight() / Math.max(1, contentHeight));
		int thumbTravel = Math.max(1, trackH - thumbH);
		int thumbY = trackTop + thumbTravel * scrollOffset / Math.max(1, maxScroll);
		graphics.fill(sbX + 1, thumbY, sbX + SCROLLBAR_WIDTH - 1, thumbY + thumbH, 0xAAAAAACC);
	}

	private boolean isOverScrollbar(double mouseX, double mouseY) {
		return maxScroll() > 0 && mouseX >= x + w - SCROLLBAR_WIDTH - 2 && mouseX < x + w - 2
				&& mouseY >= contentTop() && mouseY < contentBottom();
	}

	private void updateScrollFromMouse(double mouseY) {
		int trackTop = contentTop();
		int trackH = Math.max(1, contentBottom() - trackTop);
		int thumbH = Math.max(10, trackH * visibleContentHeight() / Math.max(1, contentHeight));
		int thumbTravel = Math.max(1, trackH - thumbH);
		double ratio = (mouseY - trackTop - thumbH / 2.0) / thumbTravel;
		scrollOffset = (int) Math.round(ratio * maxScroll());
		clampScrollOffset();
		updateWidgetScroll();
	}

	private static String fitToWidth(String text, int width) {
		Font font = Minecraft.getInstance().font;
		if (text == null || text.isEmpty() || font.width(text) <= width) {
			return text;
		}
		String ellipsis = "...";
		String clipped = font.plainSubstrByWidth(text, Math.max(0, width - font.width(ellipsis)));
		return clipped + ellipsis;
	}

	private static float parseFloat(String text, float fallback) {
		try {
			return Float.parseFloat(text.trim());
		} catch (Exception ignored) {
			return fallback;
		}
	}

	private static int parseInt(String text, int fallback) {
		try {
			return Integer.parseInt(text.trim());
		} catch (Exception ignored) {
			return fallback;
		}
	}

	private static String fmt(float value) {
		if (Math.abs(value - Math.round(value)) < 0.0001f) {
			return String.valueOf(Math.round(value));
		}
		return String.format(java.util.Locale.ROOT, "%.3f", value);
	}

	private static boolean isValidColor(String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		String raw = normalizeHexColor(text);
		if (raw == null || raw.length() > 8) {
			return false;
		}
		try {
			Integer.parseUnsignedInt(raw, 16);
			return true;
		} catch (NumberFormatException ignored) {
			return false;
		}
	}

	@Nullable
	private static String normalizeHexColor(String text) {
		if (text == null) return null;
		String raw = text.trim();
		if (raw.startsWith("#")) {
			raw = raw.substring(1);
		}
		if (raw.startsWith("0x") || raw.startsWith("0X")) {
			raw = raw.substring(2);
		}
		if (raw.isBlank()) return null;
		return raw;
	}

	private static ArrayList<String> parseChildren(String text) {
		ArrayList<String> children = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return children;
		}
		for (String token : text.split("[,\\s]+")) {
			if (token.isBlank()) {
				continue;
			}
			ResourceLocation id = ResourceLocation.tryParse(token.trim());
			if (id == null) {
				children.clear();
				return children;
			}
			children.add(id.toString());
		}
		return children;
	}

	private void setStatus(String status, int color) {
		this.status = status;
		this.statusColor = color;
	}

	private static void drawLabel(GuiGraphics graphics, Font font, String text, int x, int y) {
		graphics.drawString(font, SpellEditorLocalization.t(text), x, y + 4, 0xFFCCCCCC, false);
	}

	private record ParsedCircle(@Nullable ResourceLocation id, SpellComponent component,
								Map<ResourceLocation, SpellComponent> components) {
	}

}
