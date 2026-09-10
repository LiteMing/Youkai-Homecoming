package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import dev.xkmc.youkaishomecoming.content.spell.action.ShowSpellTitleAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.client.ActiveSpellHudOverlay;
import dev.xkmc.youkaishomecoming.content.spell.client.SpellTitleRenderer;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDisplay;
import dev.xkmc.youkaishomecoming.content.spell.preview.ActionListPanel;
import dev.xkmc.youkaishomecoming.content.spell.preview.SpellTitlePreviewTimeline;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.util.SpellTextResolver;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.Supplier;

/** A local title composition/playback panel. It never sends or consumes title packets. */
public final class SpellTitlePreviewDockPanel implements DockPanel {

	private final Supplier<SpellAction> selectedAction;
	private final Supplier<SpellDefinition> definition;
	private final Supplier<SpellContext> context;
	private final SpellTitleRenderer renderer = new SpellTitleRenderer();
	private final SpellTitlePreviewTimeline playback = new SpellTitlePreviewTimeline();
	private Selection selection;
	private int x, y, w, h;
	private boolean scrubbing;

	private record Selection(ResourceLocation spell, ResourceLocation phase, ActionListPanel.ActionPath path) {}

	public SpellTitlePreviewDockPanel(Supplier<SpellAction> selectedAction, Supplier<SpellDefinition> definition,
			Supplier<SpellContext> context) {
		this.selectedAction = selectedAction;
		this.definition = definition;
		this.context = context;
	}

	@Nullable
	public static ShowSpellTitleAction titleAction(@Nullable SpellAction action) {
		while (action instanceof SpellActions.DisabledAction disabled) action = disabled.inner();
		return action instanceof ShowSpellTitleAction title ? title : null;
	}

	public void select(ResourceLocation spell, ResourceLocation phase, ActionListPanel.ActionPath path) {
		var next = new Selection(spell, phase, path);
		if (!Objects.equals(selection, next)) {
			selection = next;
			playback.seek(SpellTitleRenderer.layoutProgress());
		}
	}

	public void tick(boolean visible) {
		var action = titleAction(selectedAction.get());
		if (action == null) {
			clear();
		} else {
			playback.setDuration(action.duration());
			if (visible) playback.tick();
		}
	}

	public void clear() {
		selection = null;
		playback.seek(0);
		scrubbing = false;
	}

	@Override public String dockTitle() { return text("preview").getString(); }
	@Override public String dockId() { return "spell_title_preview"; }
	@Override public int getX() { return x; }
	@Override public int getY() { return y; }
	@Override public int getWidth() { return w; }
	@Override public int getHeight() { return h; }
	@Override public void setBounds(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }
	@Override public void onDeactivated() { scrubbing = false; }

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if (w <= 0 || h <= 0) return;
		g.enableScissor(x, y, x + w, y + h);
		try {
			renderContents(g, mouseX, mouseY, partialTick);
		} finally {
			g.disableScissor();
		}
	}

	private void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		var mc = Minecraft.getInstance();
		var font = mc.font;
		g.fill(x, y, x + w, y + h, 0xFF171B23);
		var action = titleAction(selectedAction.get());
		if (action == null) {
			int lineY = y + 12;
			for (var line : font.split(text("preview_empty"), Math.max(1, w - 16))) {
				g.drawString(font, line, x + 8, lineY, 0xFFBBC7D9, false);
				lineY += font.lineHeight + 3;
			}
			return;
		}
		if (!hasRoom()) {
			g.drawString(font, font.plainSubstrByWidth(text("preview_small").getString(), Math.max(1, w - 16)),
					x + 8, y + 12, 0xFFBBC7D9, false);
			return;
		}
		playback.setDuration(action.duration());
		String[] labels = {playback.isPlaying() ? "preview_pause" : "preview_play",
				"preview_replay", "preview_layout", "preview_docked"};
		for (int i = 0; i < labels.length; i++) {
			int[] button = buttonBounds(i);
			boolean hover = contains(button, mouseX, mouseY);
			g.fill(button[0], button[1], button[0] + button[2], button[1] + button[3], hover ? 0xFF395477 : 0xFF293547);
			String label = font.plainSubstrByWidth(text(labels[i]).getString(), Math.max(1, button[2] - 6));
			g.drawString(font, label, button[0] + (button[2] - font.width(label)) / 2, button[1] + 4, 0xFFFFFFFF, false);
		}

		// Fit the configured in-game GUI into the dock, independently of the editor's
		// own 960x540 workspace scale. Image offsets remain actual GUI pixels.
		var window = mc.getWindow();
		int guiScale = Math.max(1, window.calculateScale(mc.options.guiScale().get(), mc.isEnforceUnicode()));
		int guiWidth = (int) Math.ceil(window.getWidth() / (double) guiScale);
		int guiHeight = (int) Math.ceil(window.getHeight() / (double) guiScale);
		int top = y + 10 + ((4 + columns() - 1) / columns()) * 20;
		int availableHeight = sliderY() - top - 8;
		if (w > 16 && availableHeight > 0) {
			float fit = Math.min((w - 16f) / guiWidth, availableHeight / (float) guiHeight);
			int canvasW = Math.max(1, Math.round(guiWidth * fit));
			int canvasH = Math.max(1, Math.round(guiHeight * fit));
			int canvasX = x + (w - canvasW) / 2;
			int canvasY = top + (availableHeight - canvasH) / 2;
			g.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, 0xFF0E141F);
			g.renderOutline(canvasX - 1, canvasY - 1, canvasW + 2, canvasH + 2, 0xFF53647B);
			g.enableScissor(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH);
			g.pose().pushPose();
			g.pose().translate(canvasX, canvasY, 0);
			g.pose().scale(fit, fit, 1);
			try {
				var display = definition.get().display;
				String name = SpellDisplay.displayText(display.name()).getString();
				String description = action.description() == null || action.description().isBlank() ? display.description()
						: SpellTextResolver.resolve(action.description(), context.get());
				int rightHeight = mc.gui instanceof ForgeGui gui ? gui.rightHeight : 0;
				var row = YHModConfig.CLIENT.activeSpellHudEnabled.get()
						? ActiveSpellHudOverlay.previewRow(font, guiWidth, guiHeight, rightHeight,
								definition.get().id.toString(), display.name(), action.presentation()) : null;
				renderer.render(g, font, name, SpellDisplay.displayText(description).getString(), action.presentation(),
						playback.progress(partialTick), guiWidth, guiHeight, 0, row);
			} finally {
				g.pose().popPose();
				g.disableScissor();
			}
		}

		int trackX = x + 8, trackW = Math.max(1, w - 16), trackY = sliderY();
		float progress = playback.progress(partialTick);
		g.fill(trackX, trackY + 5, trackX + trackW, trackY + 8, 0xFF374356);
		int cursor = trackX + Math.round((trackW - 1) * progress);
		g.fill(trackX, trackY + 5, cursor, trackY + 8, 0xFF719EDB);
		g.fill(cursor - 2, trackY + 2, cursor + 3, trackY + 11, 0xFFDCEAFF);
		String time = text("preview_progress", Math.round(progress * playback.duration()), playback.duration()).getString();
		g.drawString(font, font.plainSubstrByWidth(time, trackW), trackX, trackY + 14, 0xFFC8D5E8, false);
		boolean missingImage = renderer.isBackgroundMissing(action.presentation());
		Component status = missingImage
				? text("preview_missing", action.presentation().background().orElseThrow().toString())
				: !YHModConfig.CLIENT.activeSpellHudEnabled.get() ? text("preview_hud_disabled")
				: text("preview_canvas", guiWidth, guiHeight);
		g.drawString(font, font.plainSubstrByWidth(status.getString(), trackW), trackX, y + h - 13,
				missingImage ? 0xFFFFB487 : 0xFF96A6BD, false);
	}

	private int columns() { return w >= 240 ? 4 : w >= 128 ? 2 : 1; }
	private boolean hasRoom() { return w >= 96 && h >= 62 + ((4 + columns() - 1) / columns()) * 20; }
	private int sliderY() { return y + h - 43; }
	private int[] buttonBounds(int index) {
		int columns = columns(), cell = Math.max(1, (w - 12) / columns);
		return new int[]{x + 6 + index % columns * cell, y + 6 + index / columns * 20, Math.max(1, cell - 3), 16};
	}
	private static boolean contains(int[] bounds, double px, double py) {
		return px >= bounds[0] && px < bounds[0] + bounds[2] && py >= bounds[1] && py < bounds[1] + bounds[3];
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!isMouseOver(mouseX, mouseY) || button != 0 || !hasRoom() || titleAction(selectedAction.get()) == null) return false;
		for (int i = 0; i < 4; i++) {
			if (!contains(buttonBounds(i), mouseX, mouseY)) continue;
			switch (i) {
				case 0 -> playback.togglePlaying();
				case 1 -> playback.restart();
				case 2 -> playback.seek(SpellTitleRenderer.layoutProgress());
				case 3 -> playback.seek(1);
			}
			return true;
		}
		if (mouseY >= sliderY() && mouseY < sliderY() + 13) {
			scrubbing = true;
			seek(mouseX);
		}
		return true;
	}

	private void seek(double mouseX) { playback.seek((float) ((mouseX - x - 8) / Math.max(1, w - 17))); }
	@Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
		if (!scrubbing || button != 0) return false;
		seek(mouseX);
		return true;
	}
	@Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
		boolean handled = scrubbing;
		scrubbing = false;
		return handled;
	}
	@Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (modifiers != 0 || titleAction(selectedAction.get()) == null) return false;
		switch (keyCode) {
			case GLFW.GLFW_KEY_SPACE -> playback.togglePlaying();
			case GLFW.GLFW_KEY_HOME -> playback.restart();
			case GLFW.GLFW_KEY_END -> playback.seek(1);
			case GLFW.GLFW_KEY_LEFT -> playback.seek(playback.progress(0) - 1f / playback.duration());
			case GLFW.GLFW_KEY_RIGHT -> playback.seek(playback.progress(0) + 1f / playback.duration());
			default -> { return false; }
		}
		return true;
	}

	private static Component text(String key, Object... args) {
		return Component.translatable("youkaishomecoming.spell_editor.title." + key, args);
	}
}
