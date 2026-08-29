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
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellCondition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.preview.ActionListPanel;
import dev.xkmc.youkaishomecoming.content.spell.preview.SpellEditorLocalization;
import dev.xkmc.youkaishomecoming.content.spell.preview.SpellJsonSalvage;
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
import java.util.function.Consumer;
import java.util.function.Supplier;

@OnlyIn(Dist.CLIENT)
public class RawJsonDockPanel implements DockPanel {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int PADDING = 4;
	private static final int STATUS_HEIGHT = 13;
	private static final int MAX_JSON_LENGTH = 1_048_576;
	private static final String DRAFT_DIR = "youkaishomecoming_spells/raw_json_drafts";

	private final Supplier<SpellDefinition> definitionSupplier;
	private final Supplier<ResourceLocation> phaseSupplier;
	private final Supplier<ActionListPanel.ActionPath> selectedPathSupplier;
	private final Consumer<SpellDefinition> applyDefinition;
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
		int maxWidth = Math.max(0, w - PADDING * 2);
		msg = font.plainSubstrByWidth(msg, maxWidth);
		graphics.drawString(font, msg, x + PADDING, y + h - STATUS_HEIGHT, statusColor, false);
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
		editor.setCharacterLimit(MAX_JSON_LENGTH);
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
		try {
			JsonElement json = JsonParser.parseString(text);
			String[] parseError = new String[1];
			Optional<SpellDefinition> parsed = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, json)
					.resultOrPartial(msg -> parseError[0] = msg);
			if (parsed.isEmpty()) {
				applySalvageOrDraft(text, json, errorStatus("Invalid spell JSON", parseError[0]));
				return;
			}
			String[] encodeError = new String[1];
			Optional<JsonElement> encoded = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, parsed.get())
					.resultOrPartial(msg -> encodeError[0] = msg);
			if (encoded.isEmpty()) {
				markDraft(text, errorStatus("Invalid spell JSON", encodeError[0]));
				return;
			}
			DroppedField droppedField = findDroppedField(json, encoded.get(), "$");
			if (droppedField != null) {
				String key = droppedField.parseError ? "Invalid spell JSON" : "Raw JSON has unsupported field";
				applySalvageOrDraft(text, json, errorStatus(key, droppedField.message()));
				return;
			}
			dirtyInvalidDraft = false;
			dirtyDraftMessage = "";
			dirtyDraftPath = null;
			highlightedPath = null;
			SpellDefinition currentDefinition = definitionSupplier.get();
			if (currentDefinition != null) {
				clearDraftFile(currentDefinition.id);
			}
			clearDraftFile(parsed.get().id);
			applyDefinition.accept(parsed.get());
			setStatus("Raw JSON applied", 0xFF88FF88);
		} catch (JsonSyntaxException e) {
			markDraft(text, errorStatus("Invalid JSON", e.getMessage()));
		} catch (RuntimeException e) {
			markDraft(text, errorStatus("Invalid spell JSON", e.getMessage()));
		}
	}

	/**
	 * 严格解析失败后的抢救回退。
	 *
	 * <p>逐个动作重解析，把解析不了的片段降级成惰性占位节点，让节点树照常建立，
	 * 用户可以直接定位、替换或删除坏节点，而不是只看到一行错误信息。
	 * 抢救不了（骨架本身坏了）时仍走原本的硬错误路径。
	 *
	 * <p>草稿文件照常写入，原文永远不会因为抢救而丢失。
	 */
	private void applySalvageOrDraft(String text, JsonElement json, String strictError) {
		SpellJsonSalvage.Result salvaged;
		try {
			salvaged = SpellJsonSalvage.salvage(json, text);
		} catch (RuntimeException e) {
			salvaged = null;
		}
		if (salvaged == null || salvaged.brokenCount() == 0) {
			markDraft(text, strictError);
			return;
		}
		// 抢救过的定义必须留下草稿：它含有占位节点，不能被当成一份干净的存档。
		dirtyInvalidDraft = true;
		dirtyDraftMessage = strictError;
		dirtyDraftPath = saveDraftFile(text);
		highlightedPath = null;
		applyDefinition.accept(salvaged.definition());
		String detail = salvaged.messages().isEmpty() ? "" : "  " + salvaged.messages().get(0);
		setStatus(SpellEditorLocalization.t("Salvaged broken nodes") + ": "
				+ salvaged.brokenCount() + detail, 0xFFFFCC66);
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

	private static DroppedField findDroppedField(JsonElement input, JsonElement encoded, String path) {
		if (input == null || encoded == null) {
			return null;
		}
		if (input.isJsonObject() && encoded.isJsonObject()) {
			JsonObject inObj = input.getAsJsonObject();
			JsonObject outObj = encoded.getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : inObj.entrySet()) {
				String key = entry.getKey();
				String childPath = path + "." + key;
				if (!outObj.has(key)) {
					if (isCodecDefaultOmitted(childPath, entry.getValue())) {
						continue;
					}
					DroppedField parseError = diagnoseDroppedActionList(entry.getValue(), childPath);
					return parseError != null ? parseError : DroppedField.unsupported(childPath);
				}
				DroppedField child = findDroppedField(entry.getValue(), outObj.get(key), childPath);
				if (child != null) {
					return child;
				}
			}
		} else if (input.isJsonArray() && encoded.isJsonArray()) {
			JsonArray inArray = input.getAsJsonArray();
			JsonArray outArray = encoded.getAsJsonArray();
			for (int i = 0; i < inArray.size(); i++) {
				String childPath = path + "[" + i + "]";
				if (i >= outArray.size()) {
					if (isCodecDefaultOmitted(childPath, inArray.get(i))) {
						continue;
					}
					return DroppedField.unsupported(childPath);
				}
				DroppedField child = findDroppedField(inArray.get(i), outArray.get(i), childPath);
				if (child != null) {
					return child;
				}
			}
		}
		return null;
	}

	private static DroppedField diagnoseDroppedActionList(JsonElement input, String path) {
		if (!isActionListPath(path) || !input.isJsonArray()) {
			return null;
		}
		JsonArray actions = input.getAsJsonArray();
		for (int i = 0; i < actions.size(); i++) {
			String actionPath = path + "[" + i + "]";
			DroppedField child = diagnoseAction(actions.get(i), actionPath);
			if (child != null) {
				return child;
			}
		}
		return null;
	}

	private static DroppedField diagnoseAction(JsonElement action, String path) {
		String[] error = new String[1];
		Optional<SpellAction> parsed = SpellAction.CODEC.parse(JsonOps.INSTANCE, action)
				.resultOrPartial(msg -> error[0] = msg);
		if (parsed.isEmpty()) {
			DroppedField child = diagnoseActionChildren(action, path);
			if (child != null) {
				return child;
			}
			String detail = path;
			if (error[0] != null && !error[0].isBlank()) {
				detail += ": " + error[0];
			}
			return DroppedField.parseError(detail);
		}
		return diagnoseActionChildren(action, path);
	}

	private static DroppedField diagnoseActionChildren(JsonElement action, String path) {
		if (!action.isJsonObject()) {
			return null;
		}
		JsonObject object = action.getAsJsonObject();
		String type = getStringField(object, "type");
		if ("conditional".equals(type)) {
			if (object.has("condition")) {
				DroppedField condition = diagnoseCondition(object.get("condition"), path + ".condition");
				if (condition != null) {
					return condition;
				}
			}
			DroppedField ifTrue = diagnoseActionListField(object, "if_true", path + ".if_true");
			if (ifTrue != null) {
				return ifTrue;
			}
			return diagnoseActionListField(object, "if_false", path + ".if_false");
		}
		if ("sequence".equals(type)) {
			return diagnoseActionListField(object, "actions", path + ".actions");
		}
		if ("repeat".equals(type) || "delay".equals(type) || "burst".equals(type) || "spawn_shooter".equals(type)) {
			return diagnoseActionListField(object, "body", path + ".body");
		}
		if ("fire_danmaku".equals(type)) {
			for (String key : new String[]{"on_expiry", "on_trail", "on_hit_entity", "on_hit_block"}) {
				DroppedField child = diagnoseActionListField(object, key, path + "." + key);
				if (child != null) {
					return child;
				}
			}
		}
		if ("disabled".equals(type) && object.has("inner")) {
			return diagnoseAction(object.get("inner"), path + ".inner");
		}
		return null;
	}

	private static DroppedField diagnoseActionListField(JsonObject object, String key, String path) {
		if (!object.has(key)) {
			return null;
		}
		JsonElement value = object.get(key);
		if (!value.isJsonArray()) {
			return DroppedField.parseError(path + ": expected JSON array");
		}
		return diagnoseDroppedActionList(value, path);
	}

	private static DroppedField diagnoseCondition(JsonElement condition, String path) {
		String[] error = new String[1];
		Optional<SpellCondition> parsed = SpellCondition.CODEC.parse(JsonOps.INSTANCE, condition)
				.resultOrPartial(msg -> error[0] = msg);
		if (condition.isJsonObject()) {
			JsonObject object = condition.getAsJsonObject();
			String type = getStringField(object, "type");
			if (("and".equals(type) || "or".equals(type)) && object.has("conditions")) {
				JsonElement conditions = object.get("conditions");
				if (!conditions.isJsonArray()) {
					return DroppedField.parseError(path + ".conditions: expected JSON array");
				}
				JsonArray array = conditions.getAsJsonArray();
				for (int i = 0; i < array.size(); i++) {
					DroppedField child = diagnoseCondition(array.get(i), path + ".conditions[" + i + "]");
					if (child != null) {
						return child;
					}
				}
			}
			if ("not".equals(type) && object.has("condition")) {
				DroppedField child = diagnoseCondition(object.get("condition"), path + ".condition");
				if (child != null) {
					return child;
				}
			}
		}
		if (parsed.isEmpty()) {
			String detail = path;
			if (error[0] != null && !error[0].isBlank()) {
				detail += ": " + error[0];
			}
			return DroppedField.parseError(detail);
		}
		return null;
	}

	private static String getStringField(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
				? value.getAsString() : "";
	}

	private static boolean isActionListPath(String path) {
		return path.endsWith(".on_enter") || path.endsWith(".on_tick") ||
				path.endsWith(".on_exit") || path.endsWith(".on_damage") ||
				path.endsWith(".if_true") || path.endsWith(".if_false") ||
				path.endsWith(".actions") || path.endsWith(".body") ||
				path.endsWith(".on_expiry") || path.endsWith(".on_trail") ||
				path.endsWith(".on_hit_entity") || path.endsWith(".on_hit_block");
	}

	private static boolean isCodecDefaultOmitted(String path, JsonElement value) {
		if (value == null || value.isJsonNull()) {
			return false;
		}
		if (value.isJsonArray() && value.getAsJsonArray().isEmpty() && isActionListPath(path)) {
			return true;
		}
		if (path.endsWith(".transitions") && value.isJsonArray() && value.getAsJsonArray().isEmpty()) {
			return true;
		}
		if (path.endsWith(".difficulty") && isDefaultDifficulty(value)) {
			return true;
		}
		if (path.endsWith(".item_form") && isDefaultItemForm(value)) {
			return true;
		}
		if (endsWithAny(path, ".difficulty.speed_base", ".difficulty.frequency_base", ".difficulty.count_base")) {
			return isNumber(value, 1);
		}
		if (endsWithAny(path, ".difficulty.speed_per_health_lost", ".difficulty.frequency_per_health_lost",
				".difficulty.count_per_health_lost")) {
			return isNumber(value, 0);
		}
		if (endsWithAny(path, ".condition.offset", ".origin.offset_x", ".origin.offset_y", ".origin.offset_z",
				".origin.rotation", ".destination.offset_x", ".destination.offset_y", ".destination.offset_z",
				".destination.rotation", ".angle_offset", ".elevation", ".group_rotation.rot_x",
				".group_rotation.rot_y", ".group_rotation.rot_z")) {
			return isNumberOrNumericString(value, 0);
		}
		if (endsWithAny(path, ".mover.x", ".mover.y", ".mover.z", ".mover.speed")) {
			return isNumberOrNumericString(value, 0) || isString(value, "0");
		}
		if (path.endsWith(".spread")) {
			return isNumberOrNumericString(value, 360);
		}
		if (path.endsWith(".length")) {
			return isNumberOrNumericString(value, 80);
		}
		if (path.endsWith(".pattern")) {
			return isString(value, "ring");
		}
		if (path.endsWith(".aim_mode")) {
			return isString(value, "target");
		}
		if (endsWithAny(path, ".origin.mode", ".destination.mode")) {
			return isString(value, "caster");
		}
		if (endsWithAny(path, ".trail_interval", ".color.interval", ".volume", ".pitch", ".size")) {
			return isNumberOrNumericString(value, 1);
		}
		if (path.endsWith(".hit_behavior_entity")) {
			return isString(value, "discard");
		}
		if (path.endsWith(".hit_behavior_block")) {
			return isString(value, "continue");
		}
		if (path.endsWith(".laser")) {
			return isString(value, "laser");
		}
		if (path.endsWith(".index_variable")) {
			return isString(value, "i");
		}
		if (path.endsWith(".if_false")) {
			return isNumberOrNumericString(value, 0);
		}
		if (path.endsWith(".condition.value")) {
			return isBoolean(value, true);
		}
		if (path.endsWith(".condition.op")) {
			return isString(value, ">");
		}
		if (endsWithAny(path, ".item_form.generate", ".item_form.requires_target")) {
			return isBoolean(value, false);
		}
		if (path.endsWith(".item_form.cooldown")) {
			return isNumber(value, 100);
		}
		if (path.endsWith(".mover.aim")) {
			return isString(value, "none");
		}
		return false;
	}

	private static boolean endsWithAny(String path, String... suffixes) {
		for (String suffix : suffixes) {
			if (path.endsWith(suffix)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isDefaultDifficulty(JsonElement value) {
		if (!value.isJsonObject()) {
			return false;
		}
		JsonObject obj = value.getAsJsonObject();
		for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
			String key = entry.getKey();
			boolean ok = switch (key) {
				case "speed_base", "frequency_base", "count_base" -> isNumber(entry.getValue(), 1);
				case "speed_per_health_lost", "frequency_per_health_lost", "count_per_health_lost" ->
						isNumber(entry.getValue(), 0);
				default -> false;
			};
			if (!ok) {
				return false;
			}
		}
		return true;
	}

	private static boolean isDefaultItemForm(JsonElement value) {
		if (!value.isJsonObject()) {
			return false;
		}
		JsonObject obj = value.getAsJsonObject();
		for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
			String key = entry.getKey();
			boolean ok = switch (key) {
				case "generate", "requires_target" -> isBoolean(entry.getValue(), false);
				case "cooldown" -> isNumber(entry.getValue(), 0) || isNumber(entry.getValue(), 100);
				default -> false;
			};
			if (!ok) {
				return false;
			}
		}
		return true;
	}

	private static boolean isString(JsonElement value, String expected) {
		return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() &&
				expected.equals(value.getAsString());
	}

	private static boolean isBoolean(JsonElement value, boolean expected) {
		return value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() &&
				value.getAsBoolean() == expected;
	}

	private static boolean isNumber(JsonElement value, double expected) {
		return value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() &&
				Double.compare(value.getAsDouble(), expected) == 0;
	}

	private static boolean isNumberOrNumericString(JsonElement value, double expected) {
		if (isNumber(value, expected)) {
			return true;
		}
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
			return false;
		}
		try {
			return Double.compare(Double.parseDouble(value.getAsString().trim()), expected) == 0;
		} catch (NumberFormatException ignored) {
			return false;
		}
	}

	private record DroppedField(String message, boolean parseError) {
		private static DroppedField unsupported(String path) {
			return new DroppedField(path, false);
		}

		private static DroppedField parseError(String message) {
			return new DroppedField(message, true);
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

		private static final int HISTORY_LIMIT = 100;
		private static final int LINE_HEIGHT = 9;
		private static final String[] TEXT_FIELD_NAMES = {"textField", "f_238540_"};
		private final List<String> undoHistory = new ArrayList<>();
		private final List<String> redoHistory = new ArrayList<>();
		private String lastHistoryValue = "";
		private boolean applyingHistory;

		private RawJsonEditBox(Font font, int x, int y, int width, int height,
							   Component placeholder, Component message) {
			super(font, x, y, width, height, placeholder, message);
		}

		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			if (handleUndoRedoKey(keyCode)) {
				return true;
			}
			return super.keyPressed(keyCode, scanCode, modifiers);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
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

		private void resetUndoHistory(String text) {
			undoHistory.clear();
			redoHistory.clear();
			lastHistoryValue = text == null ? "" : text;
		}

		private void recordUserChange(String text) {
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
