package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.preview.ActionListPanel;
import dev.xkmc.youkaishomecoming.content.spell.preview.EditorTextBoxes;
import dev.xkmc.youkaishomecoming.content.spell.preview.SpellEditorLocalization;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellJsonSalvage;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellJsonChecker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@OnlyIn(Dist.CLIENT)
public class RawJsonDockPanel implements DockPanel {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int PADDING = 4;
	private static final int STATUS_HEIGHT = 18;
	private static final int LINE_NUMBER_WIDTH = 32;
	private static final String DRAFT_DIR = "youkaishomecoming_spells/raw_json_drafts";
	private static final Pattern ERROR_LINE = Pattern.compile("(?i)\\bline\\s+(\\d+)");

	private final Supplier<SpellDefinition> definitionSupplier;
	private final Supplier<ResourceLocation> phaseSupplier;
	private final Supplier<ActionListPanel.ActionPath> selectedPathSupplier;
	private final Consumer<SpellDefinition> applyDefinition;
	private BiConsumer<SpellDefinition, Consumer<Boolean>> confirmOverwrite = (definition, answer) -> answer.accept(true);
	private BooleanSupplier magicCircleModeSupplier = () -> false;
	private Supplier<String> magicCircleJsonSupplier = () -> "";
	private Consumer<String> applyMagicCircleJson = ignored -> {};

	private Consumer<AbstractWidget> addWidgetCallback;
	private Consumer<GuiEventListener> removeWidgetCallback;
	private RawJsonEditBox editor;
	private int x, y, w, h;
	private boolean suppressChange;
	private boolean dirtyInvalidDraft;
	private String dirtyDraftMessage = "";
	private Path dirtyDraftPath;
	private ActionListPanel.ActionPath highlightedPath;
	private ContentMode displayedMode;
	private String status = "";
	private int statusColor = 0xFF888888;
	private int errorLine = -1;

	public enum ContentMode {
		SPELL,
		MAGIC_CIRCLE
	}

	public RawJsonDockPanel(Supplier<SpellDefinition> definitionSupplier,
							Supplier<ResourceLocation> phaseSupplier,
							Supplier<ActionListPanel.ActionPath> selectedPathSupplier,
							Consumer<SpellDefinition> applyDefinition) {
		this.definitionSupplier = definitionSupplier;
		this.phaseSupplier = phaseSupplier;
		this.selectedPathSupplier = selectedPathSupplier;
		this.applyDefinition = applyDefinition;
	}

	public void setOverwriteConfirmation(BiConsumer<SpellDefinition, Consumer<Boolean>> confirmation) {
		this.confirmOverwrite = confirmation;
	}

	public void setMagicCircleContext(BooleanSupplier magicCircleModeSupplier,
									  Supplier<String> magicCircleJsonSupplier,
									  Consumer<String> applyMagicCircleJson) {
		this.magicCircleModeSupplier = magicCircleModeSupplier == null ? () -> false : magicCircleModeSupplier;
		this.magicCircleJsonSupplier = magicCircleJsonSupplier == null ? () -> "" : magicCircleJsonSupplier;
		this.applyMagicCircleJson = applyMagicCircleJson == null ? ignored -> {} : applyMagicCircleJson;
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
		syncEditorFromContext();
		Font font = Minecraft.getInstance().font;
		String msg = SpellEditorLocalization.t(status);
		if (editor != null) {
			editor.setDiagnostics(errorLine, statusColor == 0xFFFF8888 ? msg : "", statusColor);
			renderLineNumbers(graphics, font);
		}
		// Keep diagnostics in a dedicated strip inside the Raw JSON dock. The
		// multiline widget's gray character counter remains above this strip.
		if (statusColor != 0xFFFF8888 || msg.isBlank()) {
			int maxWidth = Math.max(0, w - PADDING * 2);
			msg = font.plainSubstrByWidth(msg, maxWidth);
			graphics.drawString(font, msg, x + PADDING, y + h - STATUS_HEIGHT + 4, statusColor, false);
		}
	}

	private void renderLineNumbers(GuiGraphics graphics, Font font) {
		if (editor == null || !editor.visible) {
			return;
		}
		int lineCount = editor.lineCount();
		int firstLine = editor.firstVisibleLine();
		int lineOffset = editor.visibleLineOffset();
		int visibleLines = Math.max(1, editor.getHeight() / RawJsonEditBox.LINE_HEIGHT + 2);
		int numberRight = editor.getX() - 6;
		for (int i = 0; i < visibleLines; i++) {
			int line = firstLine + i + 1;
			if (line > lineCount) {
				break;
			}
			int lineY = editor.getY() + editor.textTop() + i * RawJsonEditBox.LINE_HEIGHT - lineOffset;
			if (lineY + RawJsonEditBox.LINE_HEIGHT < editor.getY()
					|| lineY > editor.getY() + editor.getHeight()) {
				continue;
			}
			int color = line == errorLine ? 0xFFFF7777 : 0xFF777777;
			String label = Integer.toString(line);
			graphics.drawString(font, label, numberRight - font.width(label), lineY, color, false);
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (editor != null && editor.isFocused() && editor.handleUndoRedoKey(keyCode)) {
			return true;
		}
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
			if (editor.visible == active) {
				return;
			}
			if (active) {
				highlightedPath = null;
				editor.visible = true;
				syncEditorFromContext();
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
		editor.setCharacterLimit(SpellJsonChecker.MAX_JSON_LENGTH);
		editor.setValueListener(text -> {
			if (!suppressChange) {
				editor.recordUserChange(text);
			}
			onJsonChanged(text);
		});
		editor.visible = currentVisible;
		if (!currentText.isEmpty()) {
			setEditorText(currentText);
		} else {
			restoreDraftIfPresent();
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
		return x + PADDING + LINE_NUMBER_WIDTH;
	}

	private int editorY() {
		return y + PADDING;
	}

	private int editorWidth() {
		return Math.max(10, w - PADDING * 2 - LINE_NUMBER_WIDTH);
	}

	private int editorHeight() {
		return Math.max(10, h - PADDING * 2 - STATUS_HEIGHT);
	}

	private void syncEditorFromContext() {
		if (magicCircleModeSupplier.getAsBoolean()) {
			syncEditorFromMagicCircle();
		} else {
			syncEditorFromDefinition();
		}
	}

	private void syncEditorFromMagicCircle() {
		if (editor == null || !editor.visible) {
			return;
		}
		String text = magicCircleJsonSupplier.get();
		if (text == null) {
			text = "";
		}
		boolean modeChanged = displayedMode != ContentMode.MAGIC_CIRCLE;
		boolean shouldReplaceText = modeChanged || (!editor.isFocused() && !editor.getValue().equals(text));
		if (shouldReplaceText) {
			setEditorText(text);
			highlightedPath = null;
			selectRange(0, 0, false);
		}
		if (!editor.isFocused()) {
			highlightedPath = null;
		}
		displayedMode = ContentMode.MAGIC_CIRCLE;
		if (status.isBlank() || "Raw JSON ready".equals(status) || "Raw JSON applied".equals(status)
				|| "Magic Circle JSON ready".equals(status) || "Magic Circle JSON applied".equals(status)) {
			setStatus("Magic Circle JSON ready", 0xFF88AACC);
		}
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
		if (dirtyInvalidDraft && displayedMode == ContentMode.SPELL) {
			return;
		}
		FormattedJson formatted = encodeDefinition(definition, phaseSupplier.get(), selectedPathSupplier.get());
		if (formatted == null) {
			return;
		}
		ActionListPanel.ActionPath selected = selectedPathSupplier.get();
		boolean modeChanged = displayedMode != ContentMode.SPELL;
		boolean shouldReplaceText = modeChanged || (!editor.isFocused() && !editor.getValue().equals(formatted.text()));
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
		if (status.isBlank() || "Raw JSON applied".equals(status) || "Magic Circle JSON ready".equals(status)
				|| "Magic Circle JSON applied".equals(status)) {
			setStatus("Raw JSON ready", 0xFF88AACC);
		}
		displayedMode = ContentMode.SPELL;
	}

	private void setEditorText(String text) {
		suppressChange = true;
		editor.setValue(text);
		editor.resetUndoHistory(text);
		suppressChange = false;
	}

	private void onJsonChanged(String text) {
		if (suppressChange) {
			return;
		}
		if (displayedMode == ContentMode.MAGIC_CIRCLE || magicCircleModeSupplier.getAsBoolean()) {
			onMagicCircleJsonChanged(text);
			return;
		}
		var checked = SpellJsonChecker.check(text);
		if (!checked.clean()) {
			applySalvageOrDraft(text, checked.salvage(), errorStatus(checked.errorKey(), checked.detail()));
			return;
		}
		SpellDefinition parsed = checked.definition();
		applyWithConfirmation(text, parsed, () -> {
			dirtyInvalidDraft = false;
			dirtyDraftMessage = "";
			dirtyDraftPath = null;
			highlightedPath = null;
			SpellDefinition currentDefinition = definitionSupplier.get();
			if (currentDefinition != null) clearDraftFile(currentDefinition.id);
			clearDraftFile(parsed.id);
			applyDefinition.accept(parsed);
			setStatus("Raw JSON applied", 0xFF88FF88);
		});
	}

	/** Keep the original draft while showing any nodes recovered by the shared checker. */
	private void applySalvageOrDraft(String text, SpellJsonSalvage.Result recovered, String strictError) {
		if (recovered == null) {
			markDraft(text, strictError);
			return;
		}
		applyWithConfirmation(text, recovered.definition(), () -> {
			dirtyInvalidDraft = true;
			dirtyDraftMessage = strictError;
			dirtyDraftPath = saveDraftFile(text);
			highlightedPath = null;
			applyDefinition.accept(recovered.definition());
			String detail = recovered.messages().isEmpty() ? "" : "  " + recovered.messages().get(0);
			setStatus(SpellEditorLocalization.t("Salvaged broken nodes") + ": "
					+ recovered.brokenCount() + detail, 0xFFFFCC66);
		});
	}

	private void applyWithConfirmation(String text, SpellDefinition incoming, Runnable apply) {
		confirmOverwrite.accept(incoming, accepted -> {
			try {
				if (accepted) apply.run();
				else markDraft(text, Component.translatable("youkaishomecoming.spell_editor.overwrite.cancelled").getString());
			} catch (RuntimeException e) {
				markDraft(text, errorStatus("Invalid spell JSON", e.getMessage()));
			}
		});
	}

	private void onMagicCircleJsonChanged(String text) {
		try {
			applyMagicCircleJson.accept(text);
			dirtyInvalidDraft = false;
			dirtyDraftMessage = "";
			dirtyDraftPath = null;
			highlightedPath = null;
			displayedMode = ContentMode.MAGIC_CIRCLE;
			setStatus("Magic Circle JSON applied", 0xFF88FF88);
		} catch (JsonSyntaxException e) {
			setStatus(errorStatus("Invalid JSON", e.getMessage()), 0xFFFF8888);
		} catch (RuntimeException e) {
			setStatus(errorStatus("Invalid magic circle JSON", e.getMessage()), 0xFFFF8888);
		}
	}

	public boolean hasDirtyDraft() {
		return dirtyInvalidDraft;
	}

	public String dirtyDraftMessage() {
		return dirtyDraftMessage;
	}

	public Path dirtyDraftPath() {
		return dirtyDraftPath;
	}

	/** Drop an invalid local draft when the user explicitly abandons editor edits. */
	public void discardDraft() {
		if (dirtyDraftPath != null) {
			try {
				Files.deleteIfExists(dirtyDraftPath);
			} catch (IOException ignored) {
			}
		}
		dirtyInvalidDraft = false;
		dirtyDraftMessage = "";
		dirtyDraftPath = null;
	}

	private void markDraft(String text, String message) {
		dirtyInvalidDraft = true;
		dirtyDraftMessage = message == null ? "" : message;
		dirtyDraftPath = saveDraftFile(text);
		setStatus(dirtyDraftMessage, 0xFFFF8888);
	}

	private void restoreDraftIfPresent() {
		SpellDefinition definition = definitionSupplier.get();
		if (definition == null) {
			return;
		}
		Path path = draftPath(definition.id);
		if (!Files.isRegularFile(path)) {
			return;
		}
		try {
			String draft = Files.readString(path, StandardCharsets.UTF_8);
			if (!draft.isBlank()) {
				setEditorText(draft);
				onJsonChanged(draft);
			}
		} catch (IOException e) {
			setStatus(errorStatus("Unable to load Raw JSON draft", e.getMessage()), 0xFFFF8888);
		}
	}

	private Path saveDraftFile(String text) {
		SpellDefinition definition = definitionSupplier.get();
		ResourceLocation id = definition == null ? null : definition.id;
		Path path = draftPath(id);
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, text == null ? "" : text, StandardCharsets.UTF_8);
			return path;
		} catch (IOException e) {
			dirtyDraftMessage = errorStatus("Unable to save Raw JSON draft", e.getMessage());
			return null;
		}
	}

	private void clearDraftFile(ResourceLocation id) {
		Path path = draftPath(id);
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
		}
	}

	private static Path draftPath(ResourceLocation id) {
		String namespace = id == null ? "draft" : sanitizePathPart(id.getNamespace());
		String path = id == null ? "untitled" : sanitizePathPart(id.getPath());
		return Minecraft.getInstance().gameDirectory.toPath()
				.resolve(DRAFT_DIR)
				.resolve(namespace)
				.resolve(path + ".json");
	}

	private static String sanitizePathPart(String raw) {
		if (raw == null || raw.isBlank()) {
			return "untitled";
		}
		return raw.replaceAll("[^a-zA-Z0-9._-]+", "_");
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
		this.errorLine = color == 0xFFFF8888 ? extractErrorLine(this.status) : -1;
	}

	private static int extractErrorLine(String message) {
		if (message == null || message.isBlank()) {
			return -1;
		}
		Matcher matcher = ERROR_LINE.matcher(message);
		if (!matcher.find()) {
			return -1;
		}
		try {
			int line = Integer.parseInt(matcher.group(1));
			return line > 0 ? line : -1;
		} catch (NumberFormatException ignored) {
			return -1;
		}
	}

	private record FormattedJson(String text, int highlightStart, int highlightEnd) {
	}

	static final class RawJsonEditBox extends MultiLineEditBox {

		private static final int HISTORY_LIMIT = 100;
		private static final int LINE_HEIGHT = 9;
		private static final String[] TEXT_FIELD_NAMES = {"textField", "f_238540_"};
		private final List<String> undoHistory = new ArrayList<>();
		private final List<String> redoHistory = new ArrayList<>();
		private String lastHistoryValue = "";
		private boolean applyingHistory;
		private int diagnosticLine = -1;
		private String diagnosticMessage = "";
		private int diagnosticColor = 0xFFFF7777;
		private int lineCount = 1;

		RawJsonEditBox(Font font, int x, int y, int width, int height,
							   Component placeholder, Component message) {
			super(font, x, y, width, height, placeholder, message);
		}

		private void setDiagnostics(int line, String message, int color) {
			diagnosticLine = line;
			diagnosticMessage = message == null ? "" : message;
			diagnosticColor = color;
		}

		@Override
		public void setFocused(boolean focused) {
			if (focused) {
				EditorTextBoxes.clearActiveSelection();
			} else {
				MultilineTextField textField = textField();
				if (textField != null) {
					collapseSelection(textField);
				}
			}
			super.setFocused(focused);
		}

		@Override
		public void setValue(String value) {
			super.setValue(value);
			lineCount = countLines(value);
		}

		private int lineCount() {
			return lineCount;
		}

		private static int countLines(String value) {
			if (value == null || value.isEmpty()) {
				return 1;
			}
			int count = 1;
			for (int i = 0; i < value.length(); i++) {
				if (value.charAt(i) == '\n') {
					count++;
				}
			}
			return count;
		}

		@Override
		public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			super.renderWidget(graphics, mouseX, mouseY, partialTick);
			if (diagnosticLine > 0) {
				int lineY = getY() + innerPadding() + (diagnosticLine - 1) * LINE_HEIGHT
						- (int) scrollAmount();
				if (lineY + LINE_HEIGHT >= getY() && lineY <= getY() + getHeight()) {
					// Draw after vanilla text so the diagnostic remains visible while
					// preserving the source text under a translucent red tint.
					graphics.fill(getX(), lineY, getX() + getWidth(), lineY + LINE_HEIGHT, 0x44FF3333);
					graphics.fill(getX(), lineY, getX() + 2, lineY + LINE_HEIGHT, 0xFFFF5555);
				}
			}
			if (!diagnosticMessage.isBlank()) {
				int stripY = getY() + getHeight() - 12;
				int textRight = getX() + getWidth() - 52;
				graphics.fill(getX() + 2, stripY - 1, Math.max(getX() + 2, textRight), getY() + getHeight() - 1,
						0xDD260E0E);
				int maxWidth = Math.max(0, getWidth() - 52);
				String text = Minecraft.getInstance().font.plainSubstrByWidth(diagnosticMessage, maxWidth);
				graphics.drawString(Minecraft.getInstance().font, text, getX() + 5, stripY + 1,
						diagnosticColor, false);
			}
		}

		private int firstVisibleLine() {
			return Math.max(0, (int) Math.floor(scrollAmount() / LINE_HEIGHT));
		}

		private int visibleLineOffset() {
			return (int) scrollAmount() % LINE_HEIGHT;
		}

		private int textTop() {
			return innerPadding();
		}

		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			if (handleUndoRedoKey(keyCode)) {
				return true;
			}
			// MultiLineEditBox handles the actual clipboard operation. Mirror the
			// right-click path with a floating notification after it succeeds.
			if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_C) {
				MultilineTextField textField = textField();
				boolean hasSelection = textField != null && !textField.getSelectedText().isEmpty();
				boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
				if (handled && hasSelection) {
					EditorTextBoxes.notifyCopied();
				}
				return handled;
			}
			return super.keyPressed(keyCode, scanCode, modifiers);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (button == 1 && withinContentAreaPoint(mouseX, mouseY)) {
				EditorTextBoxes.clearActiveSelection();
				MultilineTextField textField = textField();
				if (textField != null) {
					String selected = textField.getSelectedText();
					if (!selected.isEmpty()) {
						Minecraft.getInstance().keyboardHandler.setClipboard(selected);
						EditorTextBoxes.notifyCopied();
					}
				}
				return true;
			}
			if (button == 0 && withinContentAreaPoint(mouseX, mouseY)) {
				setFocused(true);
				MultilineTextField textField = textField();
				if (textField != null) {
					textField.setSelecting(Screen.hasShiftDown());
					seekCursorToMouse(textField, mouseX, mouseY);
					if (!Screen.hasShiftDown()) {
						collapseSelection(textField);
					}
					return true;
				}
			}
			return super.mouseClicked(mouseX, mouseY, button);
		}

		private boolean handleUndoRedoKey(int keyCode) {
			if (!Screen.hasControlDown()) {
				return false;
			}
			if (keyCode == GLFW.GLFW_KEY_Z && Screen.hasShiftDown()) {
				return redoEdit();
			}
			if (keyCode == GLFW.GLFW_KEY_Z) {
				return undoEdit();
			}
			if (keyCode == GLFW.GLFW_KEY_Y) {
				return redoEdit();
			}
			return false;
		}

		void resetUndoHistory(String text) {
			undoHistory.clear();
			redoHistory.clear();
			lastHistoryValue = text == null ? "" : text;
		}

		/** Retain the caret and undo stack when a dock resize rebuilds the multiline field. */
		void restoreEditingState(RawJsonEditBox previous) {
			if (previous == null || !getValue().equals(previous.getValue())) return;
			undoHistory.clear(); undoHistory.addAll(previous.undoHistory);
			redoHistory.clear(); redoHistory.addAll(previous.redoHistory);
			lastHistoryValue = previous.lastHistoryValue;
			var before = previous.textField();
			var after = textField();
			if (before != null && after != null) {
				after.setSelecting(false);
				after.seekCursor(Whence.ABSOLUTE, before.cursor());
			}
			setScrollAmount(previous.scrollAmount());
		}

		void recordUserChange(String text) {
			if (applyingHistory) {
				return;
			}
			String next = text == null ? "" : text;
			if (next.equals(lastHistoryValue)) {
				return;
			}
			undoHistory.add(lastHistoryValue);
			if (undoHistory.size() > HISTORY_LIMIT) {
				undoHistory.remove(0);
			}
			redoHistory.clear();
			lastHistoryValue = next;
		}

		private boolean undoEdit() {
			if (undoHistory.isEmpty()) {
				return false;
			}
			String current = getValue();
			String previous = undoHistory.remove(undoHistory.size() - 1);
			redoHistory.add(current);
			applyHistoryValue(previous);
			return true;
		}

		private boolean redoEdit() {
			if (redoHistory.isEmpty()) {
				return false;
			}
			String current = getValue();
			String next = redoHistory.remove(redoHistory.size() - 1);
			undoHistory.add(current);
			if (undoHistory.size() > HISTORY_LIMIT) {
				undoHistory.remove(0);
			}
			applyHistoryValue(next);
			return true;
		}

		private void applyHistoryValue(String text) {
			applyingHistory = true;
			try {
				setValue(text);
			} finally {
				applyingHistory = false;
			}
			lastHistoryValue = text == null ? "" : text;
			highlightRange(lastHistoryValue.length(), lastHistoryValue.length(), true);
		}

		private void seekCursorToMouse(MultilineTextField textField, double mouseX, double mouseY) {
			double localX = mouseX - getX() - innerPadding();
			double localY = mouseY - getY() - innerPadding() + scrollAmount();
			textField.seekCursorToPoint(localX, localY);
		}

		private void collapseSelection(MultilineTextField textField) {
			textField.setSelecting(false);
			textField.seekCursor(Whence.ABSOLUTE, textField.cursor());
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
