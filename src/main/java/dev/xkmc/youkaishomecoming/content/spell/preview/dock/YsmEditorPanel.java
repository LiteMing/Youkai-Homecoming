package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import dev.xkmc.youkaishomecoming.content.spell.preview.YsmEditorController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Scrollable YSM forms with the editor's anchored dropdown/completion interaction. */
abstract class YsmEditorPanel implements DockPanel {
	protected final YsmEditorController editor;
	protected int x, y, w, h;
	private int cursor, scroll, contentHeight, builtVersion = -1;
	private boolean dirty = true;
	private final List<Row> rows = new ArrayList<>();
	private final Map<String, EditBox> fields = new LinkedHashMap<>();
	private final Map<String, AbstractWidget> anchors = new LinkedHashMap<>();
	private final Map<String, Picker> pickers = new LinkedHashMap<>();
	private EditBox focused;
	private Popup popup;
	private long completionRequest;
	private record Row(int top, int left, Component label, AbstractWidget widget) { }
	protected record Option(String value, Component label, Component detail) {
		public Option(String value, Component label) { this(value, label, Component.empty()); }
	}
	@FunctionalInterface
	protected interface Completion {
		CompletableFuture<List<Option>> suggest(String input, int caret);
	}
	private record Picker(Completion completion, Consumer<Option> select, String selected) { }
	private static final class Popup {
		final String anchor;
		final boolean all;
		List<Option> options = List.of();
		boolean loading = true;
		int selected, scroll;
		Popup(String anchor, boolean all) { this.anchor = anchor; this.all = all; }
	}

	protected YsmEditorPanel(YsmEditorController editor) { this.editor = editor; }
	protected abstract void build();
	protected void changed() { dirty = true; }
	protected void toTop() { scroll = 0; dirty = true; closeOverlay(); }

	@Override public void setBounds(int x, int y, int w, int h) {
		if (this.w != w) dirty = true;
		this.x = x; this.y = y; this.w = w; this.h = h;
		position();
	}
	@Override public int getX() { return x; }
	@Override public int getY() { return y; }
	@Override public int getWidth() { return w; }
	@Override public int getHeight() { return h; }

	protected void label(Component text) {
		var font = Minecraft.getInstance().font;
		for (var line : font.split(text, Math.max(20, w - 20))) {
			StringBuilder value = new StringBuilder();
			line.accept((index, style, cp) -> { value.appendCodePoint(cp); return true; });
			rows.add(new Row(cursor, 0, Component.literal(value.toString()), null));
			cursor += 12;
		}
		cursor += 3;
	}
	protected Button button(Component label, Runnable action, boolean enabled) {
		var button = Button.builder(label, ignored -> editor.attempt(action))
				.bounds(0, 0, Math.max(20, w - 20), 20).build();
		button.active = enabled && !editor.waiting();
		button.setTooltip(Tooltip.create(label));
		rows.add(new Row(cursor, 0, null, button));
		cursor += 23;
		return button;
	}
	protected void select(String id, Component label, String selected, List<Option> options, Consumer<String> change) {
		label(label);
		Component current = options.stream().filter(option -> option.value().equals(selected)).map(Option::label)
				.findFirst().orElse(YsmEditorController.text("choose"));
		Button button = button(current.copy().append("  \u25be"), () -> open(id, true), !options.isEmpty());
		anchors.put(id, button);
		pickers.put(id, new Picker(localOptions(() -> options), option -> change.accept(option.value()), selected));
	}
	protected static Completion localOptions(Supplier<List<Option>> options) {
		return (input, caret) -> {
			String needle = input.toLowerCase(Locale.ROOT);
			return CompletableFuture.completedFuture(options.get().stream().filter(option ->
					(option.value() + " " + option.label().getString() + " " + option.detail().getString())
							.toLowerCase(Locale.ROOT).contains(needle)).toList());
		};
	}
	protected EditBox edit(String id, Component label, String value, int max, Consumer<String> change) {
		return editOptions(id, label, value, max, change, null, null);
	}
	protected EditBox edit(String id, Component label, String value, int max, Consumer<String> change, Supplier<List<String>> options) {
		return editOptions(id, label, value, max, change, localOptions(() -> options.get().stream()
				.map(option -> new Option(option, Component.literal(option))).toList()), null);
	}
	protected EditBox editOptions(String id, Component label, String value, int max, Consumer<String> change,
			Completion completion, Consumer<Option> selection) {
		label(label);
		int inner = Math.max(42, w - 20);
		var box = new EditBox(Minecraft.getInstance().font, 0, 0, completion == null ? inner : inner - 22, 20, label);
		box.setMaxLength(max);
		box.setValue(value);
		box.setEditable(!editor.waiting());
		box.setResponder(input -> {
			change.accept(input);
			if (popup != null && popup.anchor.equals(id)) {
				// Typing always filters, even if the list was originally opened with its arrow.
				popup = new Popup(id, false);
				requestOptions(popup);
			}
		});
		fields.put(id, box);
		anchors.put(id, box);
		rows.add(new Row(cursor, 0, null, box));
		if (completion != null) {
			box.setTooltip(Tooltip.create(YsmEditorController.text("tab_complete")));
			pickers.put(id, new Picker(completion, selection == null ? option -> box.setValue(option.value()) : selection, value));
			var arrow = Button.builder(Component.literal("\u25be"), ignored -> {
				focus(box);
				open(id, true);
			}).bounds(0, 0, 20, 20).build();
			arrow.active = !editor.waiting();
			rows.add(new Row(cursor, inner - 20, null, arrow));
		}
		cursor += 24;
		return box;
	}

	private void rebuild() {
		String focusId = fieldId(focused);
		int caret = focused == null ? 0 : focused.getCursorPosition();
		Popup previous = popup;
		closeOverlay();
		rows.clear(); fields.clear(); anchors.clear(); pickers.clear(); focused = null;
		cursor = 6;
		build();
		contentHeight = cursor + 6;
		builtVersion = editor.viewVersion();
		dirty = false;
		if (fields.containsKey(focusId)) {
			focus(fields.get(focusId));
			focused.moveCursorTo(Math.min(caret, focused.getValue().length()));
		}
		position();
		if (previous != null && anchors.containsKey(previous.anchor)) open(previous.anchor, previous.all);
	}
	private String fieldId(EditBox box) {
		return fields.entrySet().stream().filter(entry -> entry.getValue() == box).map(Map.Entry::getKey).findFirst().orElse("");
	}
	private void focus(EditBox box) {
		if (focused != null) focused.setFocused(false);
		focused = box;
		if (focused != null) focused.setFocused(true);
	}
	private void position() {
		scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - h)));
		for (Row row : rows) if (row.widget != null) {
			row.widget.setX(x + 8 + row.left); row.widget.setY(y + row.top - scroll);
			row.widget.visible = row.widget.getY() >= y && row.widget.getY() + row.widget.getHeight() <= y + h;
		}
	}
	public void tick() { fields.values().forEach(EditBox::tick); }
	@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (dirty || builtVersion != editor.viewVersion()) rebuild();
		graphics.fill(x, y, x + w, y + h, 0xff1c2027);
		graphics.enableScissor(x, y, x + w, y + h);
		for (Row row : rows) {
			int top = y + row.top - scroll;
			if (row.label != null && top >= y - 12 && top < y + h)
				graphics.drawString(Minecraft.getInstance().font, row.label, x + 8, top, 0xffc6d4df, false);
			if (row.widget != null) row.widget.render(graphics, mouseX, mouseY, partialTick);
		}
		if (contentHeight > h && h > 0) {
			int thumb = Math.max(12, h * h / contentHeight);
			int top = y + (h - thumb) * scroll / Math.max(1, contentHeight - h);
			graphics.fill(x + w - 4, top, x + w - 1, top + thumb, 0xff7293a8);
		}
		graphics.disableScissor();
	}

	private void open(String id, boolean all) {
		if (editor.waiting() || !pickers.containsKey(id)) return;
		popup = new Popup(id, all);
		requestOptions(popup);
	}
	private void requestOptions(Popup opened) {
		Picker picker = pickers.get(opened.anchor);
		if (picker == null) { closeOverlay(); return; }
		EditBox box = fields.get(opened.anchor);
		String query = opened.all || box == null ? "" : box.getValue();
		int caret = opened.all || box == null ? 0 : box.getCursorPosition();
		long request = ++completionRequest;
		picker.completion().suggest(query, caret).whenComplete((options, failure) -> Minecraft.getInstance().execute(() -> {
			if (popup != opened || request != completionRequest) return;
			opened.options = failure == null && options != null ? List.copyOf(options) : List.of();
			opened.loading = false;
			opened.selected = 0;
			for (int i = 0; i < opened.options.size(); i++) if (opened.options.get(i).value().equals(picker.selected())) opened.selected = i;
			keepSelectionVisible();
		}));
	}
	public void closeOverlay() { popup = null; completionRequest++; }
	private int[] popupBounds() {
		var anchor = popup == null ? null : anchors.get(popup.anchor);
		if (anchor == null) return new int[]{x, y, 0, 0, 16, 0};
		var mc = Minecraft.getInstance();
		int screenW = mc.screen == null ? mc.getWindow().getGuiScaledWidth() : mc.screen.width;
		int screenH = mc.screen == null ? mc.getWindow().getGuiScaledHeight() : mc.screen.height;
		int rowH = popup.options.stream().anyMatch(option -> !option.detail().getString().isEmpty()) ? 26 : 18;
		int width = Math.max(100, anchor.getWidth());
		for (Option option : popup.options)
			width = Math.max(width, Math.max(mc.font.width(option.label()), mc.font.width(option.detail())) + 18);
		width = Math.min(width, Math.max(40, screenW - 8));
		int left = Math.max(4, Math.min(anchor.getX(), screenW - width - 4));
		int below = screenH - anchor.getY() - anchor.getHeight() - 4;
		int above = anchor.getY() - 4;
		int wanted = Math.min(8, Math.max(1, popup.options.size()));
		boolean upwards = below < wanted * rowH && above > below;
		int visible = Math.max(1, Math.min(wanted, Math.max(0, upwards ? above : below) / rowH));
		int height = visible * rowH + 2;
		int top = upwards ? anchor.getY() - height : anchor.getY() + anchor.getHeight();
		return new int[]{left, Math.max(2, top), width, height, rowH, visible};
	}
	private void keepSelectionVisible() {
		if (popup == null) return;
		int visible = popupBounds()[5];
		popup.scroll = Math.max(0, Math.min(popup.scroll, Math.max(0, popup.options.size() - visible)));
		if (popup.selected < popup.scroll) popup.scroll = popup.selected;
		if (popup.selected >= popup.scroll + visible) popup.scroll = popup.selected - visible + 1;
	}
	@Override public void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
		if (popup == null) return;
		var anchor = anchors.get(popup.anchor);
		if (anchor == null || !anchor.visible) { closeOverlay(); return; }
		int[] b = popupBounds();
		var font = Minecraft.getInstance().font;
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 800);
		graphics.fill(b[0] - 1, b[1] - 1, b[0] + b[2] + 1, b[1] + b[3] + 1, 0xff7892ab);
		graphics.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], 0xff151a22);
		if (popup.options.isEmpty()) {
			graphics.drawString(font, YsmEditorController.text(popup.loading ? "suggest_loading" : "no_options"),
					b[0] + 5, b[1] + 5, 0xffbbc8d6, false);
		}
		int previewIndex = popup.selected;
		for (int i = 0; i < b[5] && popup.scroll + i < popup.options.size(); i++) {
			int index = popup.scroll + i, top = b[1] + 1 + i * b[4];
			var option = popup.options.get(index);
			boolean hovered = mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= top && mouseY < top + b[4];
			if (hovered) previewIndex = index;
			if (hovered || index == popup.selected) graphics.fill(b[0] + 1, top, b[0] + b[2] - 1, top + b[4], 0xff354d63);
			graphics.drawString(font, font.plainSubstrByWidth(option.label().getString(), b[2] - 12), b[0] + 5, top + 4, 0xffe3edf7, false);
			if (b[4] > 18) graphics.drawString(font, font.plainSubstrByWidth(option.detail().getString(), b[2] - 12),
					b[0] + 5, top + 15, 0xff9eb3c7, false);
		}
		if (popup.options.size() > b[5]) {
			int thumb = Math.max(8, b[3] * b[5] / popup.options.size());
			int top = b[1] + (b[3] - thumb) * popup.scroll / Math.max(1, popup.options.size() - b[5]);
			graphics.fill(b[0] + b[2] - 3, top, b[0] + b[2] - 1, top + thumb, 0xff9ab5cc);
		}
		graphics.pose().popPose();
		if (previewIndex >= 0 && previewIndex < popup.options.size())
			renderOptionPreview(graphics, popup.anchor, popup.options.get(previewIndex), b);
	}
	protected void renderOptionPreview(GuiGraphics graphics, String anchor, Option option, int[] bounds) { }
	private void choose(int index) {
		if (popup == null || index < 0 || index >= popup.options.size()) return;
		Option option = popup.options.get(index);
		Picker picker = pickers.get(popup.anchor);
		closeOverlay();
		if (picker != null) editor.attempt(() -> picker.select().accept(option));
	}
	/** Screen dispatches these before docks: a dropdown can extend into the next dock. */
	public boolean overlayMouseClicked(double mx, double my, int button) {
		if (popup == null) return false;
		int[] b = popupBounds();
		if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) {
			if (button == 0) choose(popup.scroll + (int) (my - b[1] - 1) / b[4]);
			return true;
		}
		closeOverlay();
		return false;
	}
	public boolean overlayMouseScrolled(double mx, double my, double amount) {
		if (popup == null) return false;
		int[] b = popupBounds();
		if (mx < b[0] || mx >= b[0] + b[2] || my < b[1] || my >= b[1] + b[3]) return false;
		popup.scroll = Math.max(0, Math.min(popup.scroll - (int) amount, Math.max(0, popup.options.size() - b[5])));
		return true;
	}

	@Override public boolean mouseClicked(double mx, double my, int button) {
		if (!isMouseOver(mx, my)) return false;
		if (dirty || builtVersion != editor.viewVersion()) rebuild();
		focus(null);
		for (Row row : rows) if (row.widget != null && row.widget.visible && row.widget.mouseClicked(mx, my, button)) {
			if (row.widget instanceof EditBox box) focus(box);
			return true;
		}
		return true;
	}
	@Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		return focused != null && focused.mouseDragged(mx, my, button, dx, dy);
	}
	@Override public boolean mouseScrolled(double mx, double my, double amount) {
		if (!isMouseOver(mx, my)) return false;
		closeOverlay();
		scroll -= (int) (amount * 30); position(); return true;
	}
	@Override public boolean keyPressed(int key, int scan, int modifiers) {
		if (popup != null) {
			if (key == GLFW.GLFW_KEY_ESCAPE) { closeOverlay(); return true; }
			if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { choose(popup.selected); return true; }
			if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_TAB) {
				int direction = key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_TAB && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1;
				if (!popup.options.isEmpty()) popup.selected = Math.floorMod(popup.selected + direction, popup.options.size());
				keepSelectionVisible();
				return true;
			}
		}
		if (focused == null || !focused.visible) return false;
		if (key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_DOWN) {
			String id = fieldId(focused);
			if (pickers.containsKey(id)) { open(id, false); return true; }
		}
		return focused.keyPressed(key, scan, modifiers);
	}
	@Override public boolean charTyped(char value, int modifiers) {
		return focused != null && focused.visible && focused.charTyped(value, modifiers);
	}
	@Override public void onDeactivated() { focus(null); closeOverlay(); }
}
