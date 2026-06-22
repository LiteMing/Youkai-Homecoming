package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.preview.ActionListPanel;
import dev.xkmc.youkaishomecoming.content.spell.preview.SpellEditorLocalization;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

@OnlyIn(Dist.CLIENT)
public class RawJsonDockPanel implements DockPanel {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int PADDING = 4;
	private static final int STATUS_HEIGHT = 13;
	private static final int MAX_JSON_LENGTH = 1_048_576;

	private final Supplier<SpellDefinition> definitionSupplier;
	private final Supplier<ResourceLocation> phaseSupplier;
	private final Supplier<ActionListPanel.ActionPath> selectedPathSupplier;
	private final Consumer<SpellDefinition> applyDefinition;

	private Consumer<AbstractWidget> addWidgetCallback;
	private Consumer<GuiEventListener> removeWidgetCallback;
	private RawJsonEditBox editor;
	private int x, y, w, h;
	private boolean suppressChange;
	private boolean dirtyInvalidDraft;
	private ActionListPanel.ActionPath highlightedPath;
	private String status = "";
	private int statusColor = 0xFF888888;

	public RawJsonDockPanel(Supplier<SpellDefinition> definitionSupplier,
							Supplier<ResourceLocation> phaseSupplier,
							Supplier<ActionListPanel.ActionPath> selectedPathSupplier,
							Consumer<SpellDefinition> applyDefinition) {
		this.definitionSupplier = definitionSupplier;
		this.phaseSupplier = phaseSupplier;
		this.selectedPathSupplier = selectedPathSupplier;
		this.applyDefinition = applyDefinition;
	}

	public void setWidgetCallbacks(Consumer<AbstractWidget> addWidgetCallback,
								   Consumer<GuiEventListener> removeWidgetCallback) {
		this.addWidgetCallback = addWidgetCallback;
		this.removeWidgetCallback = removeWidgetCallback;
		createEditor();
	}

	@Override
	public String dockTitle() {
		return "Raw JSON";
	}

	@Override
	public String dockId() {
		return "raw_json";
	}

	@Override
	public void setBounds(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		layoutEditor();
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
		syncEditorFromDefinition();
		Font font = Minecraft.getInstance().font;
		String msg = SpellEditorLocalization.t(status);
		int maxWidth = Math.max(0, w - PADDING * 2);
		msg = font.plainSubstrByWidth(msg, maxWidth);
		graphics.drawString(font, msg, x + PADDING, y + h - STATUS_HEIGHT, statusColor, false);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && editor != null && editor.isFocused()) {
			editor.setFocused(false);
			return true;
		}
		return false;
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
		if (editor != null) {
			if (active) {
				highlightedPath = null;
				editor.visible = true;
				syncEditorFromDefinition();
			} else {
				editor.setFocused(false);
				editor.visible = false;
			}
		}
	}

	private void createEditor() {
		String currentText = editor == null ? "" : editor.getValue();
		boolean currentVisible = editor != null && editor.visible;
		boolean currentFocused = editor != null && editor.isFocused();
		if (editor != null && removeWidgetCallback != null) {
			removeWidgetCallback.accept(editor);
		}
		if (addWidgetCallback == null) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		editor = new RawJsonEditBox(font, editorX(), editorY(), editorWidth(), editorHeight(),
				Component.literal("raw_json"), Component.empty());
		editor.setCharacterLimit(MAX_JSON_LENGTH);
		editor.setValueListener(this::onJsonChanged);
		editor.visible = currentVisible;
		if (!currentText.isEmpty()) {
			setEditorText(currentText);
		}
		editor.setFocused(currentFocused);
		highlightedPath = null;
		addWidgetCallback.accept(editor);
	}

	private void layoutEditor() {
		if (editor == null) {
			return;
		}
		int nextX = editorX();
		int nextY = editorY();
		int nextW = editorWidth();
		int nextH = editorHeight();
		if (editor.getWidth() != nextW || editor.getHeight() != nextH) {
			createEditor();
			return;
		}
		editor.setX(nextX);
		editor.setY(nextY);
	}

	private int editorX() {
		return x + PADDING;
	}

	private int editorY() {
		return y + PADDING;
	}

	private int editorWidth() {
		return Math.max(10, w - PADDING * 2);
	}

	private int editorHeight() {
		return Math.max(10, h - PADDING * 2 - STATUS_HEIGHT);
	}

	private void syncEditorFromDefinition() {
		if (editor == null || !editor.visible) {
			return;
		}
		SpellDefinition definition = definitionSupplier.get();
		if (definition == null) {
			setEditorText("");
			dirtyInvalidDraft = false;
			highlightedPath = null;
			setStatus("No action selected", 0xFF888888);
			return;
		}
		if (dirtyInvalidDraft) {
			return;
		}
		FormattedJson formatted = encodeDefinition(definition, phaseSupplier.get(), selectedPathSupplier.get());
		if (formatted == null) {
			return;
		}
		ActionListPanel.ActionPath selected = selectedPathSupplier.get();
		boolean shouldReplaceText = !editor.isFocused() && !editor.getValue().equals(formatted.text());
		boolean shouldHighlight = !editor.isFocused() && selected != null && !selected.equals(highlightedPath);
		if (shouldReplaceText) {
			setEditorText(formatted.text());
			highlightedPath = null;
		}
		if (!editor.isFocused()) {
			if (selected != null && formatted.highlightStart() >= 0) {
				if (shouldReplaceText || shouldHighlight) {
					selectRange(formatted.highlightStart(), formatted.highlightEnd(), true);
					highlightedPath = selected;
				}
			} else if (highlightedPath != null) {
				selectRange(0, 0, false);
				highlightedPath = null;
			}
		}
		if (status.isBlank() || "Raw JSON applied".equals(status)) {
			setStatus("Raw JSON ready", 0xFF88AACC);
		}
	}

	private void setEditorText(String text) {
		suppressChange = true;
		editor.setValue(text);
		suppressChange = false;
	}

	private void onJsonChanged(String text) {
		if (suppressChange) {
			return;
		}
		try {
			JsonElement json = JsonParser.parseString(text);
			String[] parseError = new String[1];
			Optional<SpellDefinition> parsed = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, json)
					.resultOrPartial(msg -> parseError[0] = msg);
			if (parsed.isEmpty()) {
				dirtyInvalidDraft = true;
				setStatus(errorStatus("Invalid spell JSON", parseError[0]), 0xFFFF8888);
				return;
			}
			String[] encodeError = new String[1];
			Optional<JsonElement> encoded = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, parsed.get())
					.resultOrPartial(msg -> encodeError[0] = msg);
			if (encoded.isEmpty()) {
				dirtyInvalidDraft = true;
				setStatus(errorStatus("Invalid spell JSON", encodeError[0]), 0xFFFF8888);
				return;
			}
			dirtyInvalidDraft = false;
			highlightedPath = null;
			applyDefinition.accept(parsed.get());
			setStatus("Raw JSON applied", 0xFF88FF88);
		} catch (JsonSyntaxException e) {
			dirtyInvalidDraft = true;
			setStatus(errorStatus("Invalid JSON", e.getMessage()), 0xFFFF8888);
		} catch (RuntimeException e) {
			dirtyInvalidDraft = true;
			setStatus(errorStatus("Invalid spell JSON", e.getMessage()), 0xFFFF8888);
		}
	}

	private FormattedJson encodeDefinition(SpellDefinition definition, ResourceLocation phaseId,
										   ActionListPanel.ActionPath selectedPath) {
		String[] error = new String[1];
		Optional<JsonElement> json = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
				.resultOrPartial(msg -> error[0] = msg);
		if (json.isEmpty()) {
			setStatus(errorStatus("Unable to encode spell JSON", error[0]), 0xFFFF8888);
			return null;
		}
		JsonElement selected = findSelectedActionJson(json.get(), phaseId, selectedPath);
		return formatJson(json.get(), selected);
	}

	private JsonElement findSelectedActionJson(JsonElement root, ResourceLocation phaseId,
											   ActionListPanel.ActionPath selectedPath) {
		if (phaseId == null || selectedPath == null || !root.isJsonObject()) {
			return null;
		}
		JsonObject rootObj = root.getAsJsonObject();
		JsonObject phases = getObject(rootObj, "phases");
		if (phases == null) {
			return null;
		}
		JsonObject phase = getObject(phases, phaseId.toString());
		if (phase == null) {
			return null;
		}
		JsonArray currentList = getArray(phase, sectionKey(selectedPath.section()));
		if (currentList == null) {
			return null;
		}
		JsonElement current = null;
		var entries = selectedPath.path();
		for (int i = 0; i < entries.size(); i++) {
			int index = entries.get(i).index();
			if (index < 0 || index >= currentList.size()) {
				return null;
			}
			current = currentList.get(index);
			if (i < entries.size() - 1) {
				if (!current.isJsonObject()) {
					return null;
				}
				String branch = branchKey(entries.get(i).branch());
				if (branch == null) {
					return null;
				}
				currentList = getArray(current.getAsJsonObject(), branch);
				if (currentList == null) {
					return null;
				}
			}
		}
		return current;
	}

	private static JsonObject getObject(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	private static JsonArray getArray(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
	}

	private static String sectionKey(String section) {
		return switch (section) {
			case "enter" -> "on_enter";
			case "tick" -> "on_tick";
			case "exit" -> "on_exit";
			case "damage" -> "on_damage";
			default -> section;
		};
	}

	private static String branchKey(String branch) {
		if (branch == null) {
			return null;
		}
		return switch (branch) {
			case "true" -> "if_true";
			case "false" -> "if_false";
			case "onExpiry" -> "on_expiry";
			case "onTrail" -> "on_trail";
			case "onHitEntity" -> "on_hit_entity";
			case "onHitBlock" -> "on_hit_block";
			default -> branch;
		};
	}

	private FormattedJson formatJson(JsonElement root, JsonElement selected) {
		StringBuilder builder = new StringBuilder();
		int[] range = new int[]{-1, -1};
		writeJson(builder, root, selected, range, 0);
		return new FormattedJson(builder.toString(), range[0], range[1]);
	}

	private void writeJson(StringBuilder builder, JsonElement element, JsonElement selected, int[] range, int depth) {
		boolean mark = element == selected && range[0] < 0;
		if (mark) {
			range[0] = builder.length();
		}
		if (element == null || element.isJsonNull()) {
			builder.append("null");
		} else if (element.isJsonObject()) {
			writeObject(builder, element.getAsJsonObject(), selected, range, depth);
		} else if (element.isJsonArray()) {
			writeArray(builder, element.getAsJsonArray(), selected, range, depth);
		} else if (element.isJsonPrimitive()) {
			writePrimitive(builder, element.getAsJsonPrimitive());
		}
		if (mark) {
			range[1] = builder.length();
		}
	}

	private void writeObject(StringBuilder builder, JsonObject object, JsonElement selected, int[] range, int depth) {
		if (object.entrySet().isEmpty()) {
			builder.append("{}");
			return;
		}
		builder.append("{\n");
		int i = 0;
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			indent(builder, depth + 1);
			builder.append(GSON.toJson(entry.getKey())).append(": ");
			writeJson(builder, entry.getValue(), selected, range, depth + 1);
			if (++i < object.size()) {
				builder.append(',');
			}
			builder.append('\n');
		}
		indent(builder, depth);
		builder.append('}');
	}

	private void writeArray(StringBuilder builder, JsonArray array, JsonElement selected, int[] range, int depth) {
		if (array.isEmpty()) {
			builder.append("[]");
			return;
		}
		builder.append("[\n");
		for (int i = 0; i < array.size(); i++) {
			indent(builder, depth + 1);
			writeJson(builder, array.get(i), selected, range, depth + 1);
			if (i + 1 < array.size()) {
				builder.append(',');
			}
			builder.append('\n');
		}
		indent(builder, depth);
		builder.append(']');
	}

	private static void writePrimitive(StringBuilder builder, JsonPrimitive primitive) {
		builder.append(GSON.toJson(primitive));
	}

	private static void indent(StringBuilder builder, int depth) {
		builder.append("  ".repeat(Math.max(0, depth)));
	}

	private void selectRange(int start, int end, boolean scrollToRange) {
		if (editor == null || start < 0 || end < start) {
			return;
		}
		editor.highlightRange(start, end, scrollToRange);
	}

	private static String errorStatus(String key, String detail) {
		String prefix = SpellEditorLocalization.t(key);
		return detail == null || detail.isBlank() ? prefix : prefix + ": " + detail;
	}

	private void setStatus(String status, int color) {
		this.status = status == null ? "" : status;
		this.statusColor = color;
	}

	private record FormattedJson(String text, int highlightStart, int highlightEnd) {
	}

	private static final class RawJsonEditBox extends MultiLineEditBox {

		private static final int LINE_HEIGHT = 9;
		private static final String[] TEXT_FIELD_NAMES = {"textField", "f_238540_"};

		private RawJsonEditBox(Font font, int x, int y, int width, int height,
							   Component placeholder, Component message) {
			super(font, x, y, width, height, placeholder, message);
		}

		private void highlightRange(int start, int end, boolean scrollToRange) {
			MultilineTextField textField = textField();
			if (textField == null) {
				return;
			}
			int length = getValue().length();
			int clampedStart = Math.max(0, Math.min(start, length));
			int clampedEnd = Math.max(clampedStart, Math.min(end, length));
			textField.setSelecting(false);
			textField.seekCursor(Whence.ABSOLUTE, clampedStart);
			int line = textField.getLineAtCursor();
			textField.setSelecting(true);
			textField.seekCursor(Whence.ABSOLUTE, clampedEnd);
			textField.setSelecting(false);
			if (scrollToRange && line >= 0) {
				scrollToLine(line);
			}
		}

		private void scrollToLine(int line) {
			int visibleHeight = Math.max(0, getHeight() - totalInnerPadding());
			double target = line * LINE_HEIGHT - visibleHeight / 2.0 + LINE_HEIGHT / 2.0;
			setScrollAmount(Math.max(0, Math.min(getMaxScrollAmount(), target)));
		}

		private MultilineTextField textField() {
			for (String name : TEXT_FIELD_NAMES) {
				try {
					Field field = MultiLineEditBox.class.getDeclaredField(name);
					field.setAccessible(true);
					return (MultilineTextField) field.get(this);
				} catch (ReflectiveOperationException ignored) {
				}
			}
			return null;
		}
	}

}
