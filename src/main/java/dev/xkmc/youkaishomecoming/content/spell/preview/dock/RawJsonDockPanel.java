package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.preview.SpellEditorLocalization;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

@OnlyIn(Dist.CLIENT)
public class RawJsonDockPanel implements DockPanel {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int PADDING = 4;
	private static final int STATUS_HEIGHT = 13;
	private static final int MAX_JSON_LENGTH = 65535;

	private final Supplier<SpellAction> actionSupplier;
	private final Consumer<SpellAction> applyAction;

	private Consumer<AbstractWidget> addWidgetCallback;
	private Consumer<GuiEventListener> removeWidgetCallback;
	private MultiLineEditBox editor;
	private int x, y, w, h;
	private SpellAction displayedAction;
	private boolean suppressChange;
	private boolean dirtyInvalidDraft;
	private String status = "";
	private int statusColor = 0xFF888888;

	public RawJsonDockPanel(Supplier<SpellAction> actionSupplier, Consumer<SpellAction> applyAction) {
		this.actionSupplier = actionSupplier;
		this.applyAction = applyAction;
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
		syncEditorFromAction();
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
		if (editor != null) {
			editor.visible = true;
			syncEditorFromAction();
		}
	}

	@Override
	public void onDeactivated() {
		if (editor != null) {
			editor.setFocused(false);
			editor.visible = false;
		}
	}

	private void createEditor() {
		if (editor != null && removeWidgetCallback != null) {
			removeWidgetCallback.accept(editor);
		}
		if (addWidgetCallback == null) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		editor = new MultiLineEditBox(font, x + PADDING, y + PADDING, 10, 10,
				Component.literal("raw_json"), Component.empty());
		editor.setCharacterLimit(MAX_JSON_LENGTH);
		editor.setValueListener(this::onJsonChanged);
		editor.visible = false;
		layoutEditor();
		addWidgetCallback.accept(editor);
	}

	private void layoutEditor() {
		if (editor == null) {
			return;
		}
		editor.setX(x + PADDING);
		editor.setY(y + PADDING);
		editor.setWidth(Math.max(10, w - PADDING * 2));
		editor.setHeight(Math.max(10, h - PADDING * 2 - STATUS_HEIGHT));
	}

	private void syncEditorFromAction() {
		if (editor == null || !editor.visible) {
			return;
		}
		SpellAction action = actionSupplier.get();
		if (action == null) {
			if (displayedAction != null || !editor.getValue().isEmpty()) {
				setEditorText("");
			}
			displayedAction = null;
			dirtyInvalidDraft = false;
			setStatus("No action selected", 0xFF888888);
			return;
		}
		if (action == displayedAction || dirtyInvalidDraft) {
			return;
		}
		Optional<String> encoded = encodeAction(action);
		if (encoded.isPresent()) {
			displayedAction = action;
			setEditorText(encoded.get());
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
		SpellAction current = actionSupplier.get();
		if (current == null) {
			dirtyInvalidDraft = !text.isBlank();
			setStatus("No action selected", 0xFFCC8888);
			return;
		}
		try {
			JsonElement json = JsonParser.parseString(text);
			String[] parseError = new String[1];
			Optional<SpellAction> parsed = SpellAction.CODEC.parse(JsonOps.INSTANCE, json)
					.resultOrPartial(msg -> parseError[0] = msg);
			if (parsed.isEmpty()) {
				dirtyInvalidDraft = true;
				setStatus(errorStatus("Invalid action JSON", parseError[0]), 0xFFFF8888);
				return;
			}
			String[] encodeError = new String[1];
			Optional<JsonElement> encoded = SpellAction.CODEC.encodeStart(JsonOps.INSTANCE, parsed.get())
					.resultOrPartial(msg -> encodeError[0] = msg);
			if (encoded.isEmpty()) {
				dirtyInvalidDraft = true;
				setStatus(errorStatus("Invalid action JSON", encodeError[0]), 0xFFFF8888);
				return;
			}
			dirtyInvalidDraft = false;
			displayedAction = parsed.get();
			applyAction.accept(parsed.get());
			setStatus("Raw JSON applied", 0xFF88FF88);
		} catch (JsonSyntaxException e) {
			dirtyInvalidDraft = true;
			setStatus(errorStatus("Invalid JSON", e.getMessage()), 0xFFFF8888);
		} catch (RuntimeException e) {
			dirtyInvalidDraft = true;
			setStatus(errorStatus("Invalid action JSON", e.getMessage()), 0xFFFF8888);
		}
	}

	private Optional<String> encodeAction(SpellAction action) {
		String[] error = new String[1];
		Optional<JsonElement> json = SpellAction.CODEC.encodeStart(JsonOps.INSTANCE, action)
				.resultOrPartial(msg -> error[0] = msg);
		if (json.isEmpty()) {
			setStatus(errorStatus("Unable to encode action JSON", error[0]), 0xFFFF8888);
			return Optional.empty();
		}
		return Optional.of(GSON.toJson(json.get()));
	}

	private static String errorStatus(String key, String detail) {
		String prefix = SpellEditorLocalization.t(key);
		return detail == null || detail.isBlank() ? prefix : prefix + ": " + detail;
	}

	private void setStatus(String status, int color) {
		this.status = status == null ? "" : status;
		this.statusColor = color;
	}

}
