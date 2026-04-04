package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.CustomSpellStorage;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Standalone screen for previewing and editing spell card effects.
 * Left: orthographic viewport, Right: action list + property editor.
 * Opened via /yhspell preview <spell_id>.
 */
@OnlyIn(Dist.CLIENT)
public class SpellPreviewScreen extends Screen {

	private final SpellDefinition definition;
	private final VirtualSpellScene scene;
	private final OrthographicViewport viewport;

	// Layout constants
	private static final int CONTROL_HEIGHT = 98;
	private static final int TOP_BAR_HEIGHT = 20;
	private static final int BUTTON_HEIGHT = 16;
	private static final int BUTTON_SPACING = 2;
	private static final int EDITOR_PANEL_WIDTH = 200;
	private static final int ACTION_LIST_HEIGHT_RATIO = 40; // percent of right panel for action list

	// Editor panels
	private ActionListPanel actionListPanel;
	private ActionEditorPanel actionEditorPanel;
	private boolean editorVisible = true;
	private ActionListPanel.AddTarget pendingAddTarget;

	// Phase dropdown state
	private final List<ResourceLocation> phaseList = new ArrayList<>();
	private int selectedPhaseIndex = 0;

	private boolean dragging = false;
	private boolean rotating = false;
	private boolean movingTarget = false;
	private boolean autoReplay = true;

	public SpellPreviewScreen(SpellDefinition definition) {
		super(Component.literal("Spell Preview: " + definition.id));
		this.definition = definition;
		this.scene = new VirtualSpellScene(definition);
		this.viewport = new OrthographicViewport();
		this.phaseList.addAll(definition.phases.keySet());
	}

	@Override
	protected void init() {
		super.init();

		int editorW = editorVisible ? EDITOR_PANEL_WIDTH : 0;
		int viewportW = width - editorW;

		// Set viewport bounds (left side)
		int viewportY = TOP_BAR_HEIGHT;
		int viewportHeight = height - TOP_BAR_HEIGHT - CONTROL_HEIGHT;
		viewport.setBounds(0, viewportY, viewportW, viewportHeight);

		// --- Top bar: view angle buttons + toggle editor + spell name ---
		int bx = 4;
		int by = 2;
		int bw = 50;
		for (ViewAngle angle : ViewAngle.values()) {
			addRenderableWidget(Button.builder(Component.literal(angle.getLabel()), btn -> {
				viewport.setViewAngle(angle);
			}).bounds(bx, by, bw, BUTTON_HEIGHT).build());
			bx += bw + BUTTON_SPACING;
		}
		// Toggle editor button
		bx += 10;
		addRenderableWidget(Button.builder(Component.literal(editorVisible ? "Editor <<" : "Editor >>"), btn -> {
			editorVisible = !editorVisible;
			rebuildScreen();
		}).bounds(bx, by, 60, BUTTON_HEIGHT).build());
		bx += 62;
		// Apply button: re-apply edited spell to all entities using it
		addRenderableWidget(Button.builder(Component.literal("Apply"), btn -> applyToEntities())
				.bounds(bx, by, 40, BUTTON_HEIGHT).build());
		bx += 42;
		// Export button: save spell definition as JSON datapack file
		addRenderableWidget(Button.builder(Component.literal("Export"), btn -> exportToDatapack())
				.bounds(bx, by, 46, BUTTON_HEIGHT).build());
		bx += 48;
		// Auto Replay toggle
		addRenderableWidget(Button.builder(Component.literal(autoReplay ? "Auto:ON" : "Auto:OFF"), btn -> {
			autoReplay = !autoReplay;
			rebuildScreen();
		}).bounds(bx, by, 52, BUTTON_HEIGHT).build());

		// --- Control panel at bottom ---
		int panelY = height - CONTROL_HEIGHT;
		int row1Y = panelY + 4;
		int row2Y = row1Y + BUTTON_HEIGHT + BUTTON_SPACING;
		int row3Y = row2Y + BUTTON_HEIGHT + BUTTON_SPACING;
		int row4Y = row3Y + BUTTON_HEIGHT + BUTTON_SPACING;
		int row5Y = row4Y + BUTTON_HEIGHT + BUTTON_SPACING;

		// Row 1: Playback controls
		bx = 4;
		addRenderableWidget(Button.builder(Component.literal("\u25B6/\u275A\u275A"), btn -> scene.togglePlayPause())
				.bounds(bx, row1Y, 40, BUTTON_HEIGHT).build());
		bx += 42;
		addRenderableWidget(Button.builder(Component.literal("\u25A0"), btn -> scene.reset())
				.bounds(bx, row1Y, 20, BUTTON_HEIGHT).build());
		bx += 22;
		addRenderableWidget(Button.builder(Component.literal("\u25B8"), btn -> scene.step())
				.bounds(bx, row1Y, 20, BUTTON_HEIGHT).build());

		// Row 2: Speed buttons
		bx = 4;
		for (int i = 0; i < VirtualSpellScene.SPEED_OPTIONS.length; i++) {
			float speed = VirtualSpellScene.SPEED_OPTIONS[i];
			String label = speed < 1 ? speed + "x" : ((int) speed) + "x";
			final int idx = i;
			addRenderableWidget(Button.builder(Component.literal(label), btn -> scene.setSpeedIndex(idx))
					.bounds(bx, row2Y, 36, BUTTON_HEIGHT).build());
			bx += 38;
		}

		// Row 3: Distance + HP
		bx = 4;
		addRenderableWidget(Button.builder(Component.literal("Dist:"), btn -> {})
				.bounds(bx, row3Y, 30, BUTTON_HEIGHT).build());
		bx += 32;
		for (float dist : VirtualSpellScene.DISTANCE_OPTIONS) {
			addRenderableWidget(Button.builder(Component.literal(String.valueOf((int) dist)), btn -> scene.setTargetDistance(dist))
					.bounds(bx, row3Y, 24, BUTTON_HEIGHT).build());
			bx += 26;
		}
		bx += 10;
		addRenderableWidget(Button.builder(Component.literal("HP:"), btn -> {})
				.bounds(bx, row3Y, 24, BUTTON_HEIGHT).build());
		bx += 26;
		for (float hp : VirtualSpellScene.HP_OPTIONS) {
			String hpLabel = ((int) (hp * 100)) + "%";
			addRenderableWidget(Button.builder(Component.literal(hpLabel), btn -> scene.setHealthRatio(hp))
					.bounds(bx, row3Y, 30, BUTTON_HEIGHT).build());
			bx += 32;
		}

		// Row 4: Phase selection
		bx = 4;
		addRenderableWidget(Button.builder(Component.literal("Phase:"), btn -> {})
				.bounds(bx, row4Y, 40, BUTTON_HEIGHT).build());
		bx += 42;
		addRenderableWidget(Button.builder(Component.literal("<"), btn -> cyclePhase(-1))
				.bounds(bx, row4Y, 16, BUTTON_HEIGHT).build());
		bx += 18;
		addRenderableWidget(Button.builder(Component.literal(">"), btn -> cyclePhase(1))
				.bounds(bx + 100, row4Y, 16, BUTTON_HEIGHT).build());

		// Row 5: Range
		bx = 4;
		int[] rangeOptions = {50, 100, 200, 500};
		addRenderableWidget(Button.builder(Component.literal("Range:"), btn -> {})
				.bounds(bx, row5Y, 40, BUTTON_HEIGHT).build());
		bx += 42;
		for (int range : rangeOptions) {
			final float r = range;
			addRenderableWidget(Button.builder(Component.literal(String.valueOf(range)), btn -> {
				viewport.setGridExtent(r);
				viewport.setClipDepth(r * 4);
			}).bounds(bx, row5Y, 30, BUTTON_HEIGHT).build());
			bx += 32;
		}

		// --- Editor panels (right side) ---
		if (editorVisible) {
			int editorX = viewportW;
			int rightPanelY = TOP_BAR_HEIGHT;
			int rightPanelH = height - TOP_BAR_HEIGHT - CONTROL_HEIGHT;
			int actionListH = rightPanelH * ACTION_LIST_HEIGHT_RATIO / 100;
			int editorH = rightPanelH - actionListH;

			actionListPanel = new ActionListPanel(
					(action, path) -> {
						if (actionEditorPanel != null) {
							actionEditorPanel.setAction(action, path.leafIndex());
						}
					},
					this::onRequestAddAction,
					() -> { scene.reset(); scene.play(); }
			);
			actionListPanel.setBounds(editorX, rightPanelY, editorW, actionListH);

			actionEditorPanel = new ActionEditorPanel(
					this::addRenderableWidget,
					this::removeWidget,
					this::onActionEdited,
					this::onDeleteAction
			);
			actionEditorPanel.setBounds(editorX, rightPanelY + actionListH, editorW, editorH);

			// Set current phase on the action list
			updateActionListPhase();
		} else {
			actionListPanel = null;
			actionEditorPanel = null;
		}
	}

	private void rebuildScreen() {
		this.init(minecraft, width, height);
	}

	private void onActionEdited(SpellAction newAction) {
		if (actionListPanel != null) {
			actionListPanel.replaceSelectedAction(newAction);
			if (autoReplay) { scene.reset(); scene.play(); }
		}
	}

	private void onRequestAddAction(ActionListPanel.AddTarget target) {
		pendingAddTarget = target;
		if (actionEditorPanel != null) {
			actionEditorPanel.showTypeSelector(this::onTypeSelected);
		}
	}

	private void onTypeSelected(SpellAction action) {
		if (actionListPanel != null && pendingAddTarget != null) {
			actionListPanel.insertAction(pendingAddTarget, action);
			pendingAddTarget = null;
			if (autoReplay) { scene.reset(); scene.play(); }
		}
	}

	private void onDeleteAction() {
		if (actionListPanel != null && actionListPanel.deleteSelected()) {
			if (actionEditorPanel != null) actionEditorPanel.clearAction();
			if (autoReplay) { scene.reset(); scene.play(); }
		}
	}

	/**
	 * Re-apply the edited spell definition to all entities currently using it.
	 * Works in singleplayer by directly accessing the integrated server.
	 * Matches both new SpellRuntime entities and legacy SpellCardWrapper entities.
	 */
	private void applyToEntities() {
		var mc = Minecraft.getInstance();
		// Update the SpellRegistry so subsequent /yhspell set uses the edited definition
		SpellRegistry.register(definition);
		// Persist to world save data
		var server = mc.getSingleplayerServer();
		if (server != null) {
			server.execute(() -> CustomSpellStorage.saveSpell(server, definition));
		}
		if (server != null) {
			ResourceLocation spellId = definition.id;
			String spellIdStr = spellId.toString();
			server.execute(() -> {
				int count = 0;
				for (var level : server.getAllLevels()) {
					for (var entity : level.getAllEntities()) {
						if (!(entity instanceof YoukaiEntity youkai)) continue;
						boolean match = false;
						if (youkai.spellRuntime != null
								&& youkai.spellRuntime.getDefinition().id.equals(spellId)) {
							match = true;
						} else if (youkai.spellCard != null
								&& spellIdStr.equals(youkai.spellCard.modelId)) {
							match = true;
						}
						if (match) {
							youkai.setSpellRuntime(new SpellRuntime(definition));
							count++;
						}
					}
				}
				int finalCount = count;
				mc.execute(() -> {
					if (mc.player != null) {
						mc.player.displayClientMessage(
								Component.literal("[YH] Applied & saved spell to " + finalCount + " entities"), true);
					}
				});
			});
		} else if (mc.player != null) {
			mc.player.connection.sendCommand("yhspell reapply " + definition.id);
		}
	}

	/**
	 * Export the current spell definition to a JSON file in the game directory.
	 * File is written to: ./youkaishomecoming_exports/<namespace>/<path>.json
	 */
	private void exportToDatapack() {
		var mc = Minecraft.getInstance();
		try {
			com.google.gson.JsonElement json = SpellDefinition.CODEC.encodeStart(
					com.mojang.serialization.JsonOps.INSTANCE, definition).getOrThrow(false, s -> {});
			com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
			String jsonStr = gson.toJson(json);

			java.io.File dir = new java.io.File(mc.gameDirectory, "youkaishomecoming_exports/" + definition.id.getNamespace());
			dir.mkdirs();
			java.io.File file = new java.io.File(dir, definition.id.getPath().replace('/', '_') + ".json");
			try (var writer = new java.io.FileWriter(file)) {
				writer.write(jsonStr);
			}

			if (mc.player != null) {
				mc.player.displayClientMessage(
						Component.literal("[YH] Exported to " + file.getPath()), false);
			}
		} catch (Exception e) {
			if (mc.player != null) {
				mc.player.displayClientMessage(
						Component.literal("[YH] Export failed: " + e.getMessage()), false);
			}
		}
	}

	private void updateActionListPhase() {
		if (actionListPanel == null || phaseList.isEmpty()) return;
		ResourceLocation phaseId = phaseList.get(selectedPhaseIndex);
		PhaseDefinition phase = definition.phases.get(phaseId);
		if (phase != null) {
			actionListPanel.setPhase(phase);
		}
	}

	private void cyclePhase(int delta) {
		if (phaseList.isEmpty()) return;
		selectedPhaseIndex = (selectedPhaseIndex + delta + phaseList.size()) % phaseList.size();
		scene.forcePhase(phaseList.get(selectedPhaseIndex));
		if (actionEditorPanel != null) actionEditorPanel.clearAction();
		updateActionListPhase();
	}

	@Override
	public void tick() {
		super.tick();
		scene.tick();

		// Sync selected phase index with runtime
		ResourceLocation current = scene.getCurrentPhaseId();
		int idx = phaseList.indexOf(current);
		if (idx >= 0 && idx != selectedPhaseIndex) {
			selectedPhaseIndex = idx;
			updateActionListPhase();
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		// Dark background
		renderBackground(guiGraphics);

		// Render the orthographic viewport
		viewport.render(guiGraphics, scene, partialTick);

		// Render control panel background
		int panelY = height - CONTROL_HEIGHT;
		guiGraphics.fill(0, panelY, width, height, 0xCC000000);

		// Render editor panels (before widgets so text shows under widgets)
		if (actionListPanel != null) {
			actionListPanel.render(guiGraphics, mouseX, mouseY, partialTick);
		}
		if (actionEditorPanel != null) {
			actionEditorPanel.render(guiGraphics, mouseX, mouseY, partialTick, false);
		}

		// Render widgets (buttons)
		super.render(guiGraphics, mouseX, mouseY, partialTick);

		// Render dropdown overlay ON TOP of all widgets
		if (actionEditorPanel != null) {
			actionEditorPanel.renderDropdown(guiGraphics, mouseX, mouseY);
		}

		// Status text on top bar
		String spellName = definition.id.toString();
		int editorW = editorVisible ? EDITOR_PANEL_WIDTH : 0;
		int nameX = width - editorW - font.width(spellName) - 4;
		guiGraphics.drawString(font, spellName, nameX, 5, 0xFFAAAAAA, false);

		// Playback info
		int row1Y = panelY + 4;
		String status = (scene.isPlaying() ? "\u25B6 " : "\u275A\u275A ") +
				"tick:" + scene.getTotalTick() +
				"  phase:" + scene.getCurrentPhaseId().getPath() +
				"  entities:" + scene.getEntityCount() +
				"  speed:" + scene.getCurrentSpeed() + "x";
		guiGraphics.drawString(font, status, 90, row1Y + 4, 0xFFCCCCCC, false);

		// Phase name between < > buttons
		if (!phaseList.isEmpty()) {
			int row4Y = panelY + 4 + (BUTTON_HEIGHT + BUTTON_SPACING) * 3;
			String phaseName = phaseList.get(selectedPhaseIndex).getPath();
			guiGraphics.drawString(font, phaseName, 64, row4Y + 4, 0xFFFFFF88, false);
		}

		// View angle + target info
		var tp = scene.getTargetPos();
		String targetInfo = String.format("Target: (%.1f, %.1f, %.1f)", tp.x, tp.y, tp.z);
		int viewportW = width - editorW;
		guiGraphics.drawString(font, "View: " + viewport.getViewLabel(),
				4, height - CONTROL_HEIGHT - 22, 0xFF888888, false);
		guiGraphics.drawString(font, targetInfo,
				4, height - CONTROL_HEIGHT - 12, 0xFFBBBB44, false);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// Custom-drawn overlays (completion, dropdown) take priority over widgets
		if (actionEditorPanel != null && actionEditorPanel.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (actionListPanel != null && actionListPanel.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		// Let widgets (EditBox, Button) handle clicks
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}

		if (viewport.isMouseOver(mouseX, mouseY)) {
			if (button == 0) {
				movingTarget = true;
				return true;
			}
			if (button == 2) {
				dragging = true;
				return true;
			}
			if (button == 1) {
				rotating = true;
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (actionListPanel != null && actionListPanel.mouseReleased(mouseX, mouseY, button)) {
			return true;
		}
		if (movingTarget && button == 0) {
			movingTarget = false;
			return true;
		}
		if (dragging && button == 2) {
			dragging = false;
			return true;
		}
		if (rotating && button == 1) {
			rotating = false;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (actionListPanel != null && actionListPanel.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
			return true;
		}
		if (movingTarget) {
			var delta = viewport.screenDeltaToWorldDelta((float) dragX, (float) dragY);
			scene.moveTarget(delta);
			return true;
		}
		if (dragging) {
			viewport.pan((float) dragX, (float) dragY);
			return true;
		}
		if (rotating) {
			viewport.rotate((float) dragX, (float) dragY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (actionListPanel != null && actionListPanel.mouseScrolled(mouseX, mouseY, delta)) {
			return true;
		}
		if (actionEditorPanel != null && actionEditorPanel.mouseScrolled(mouseX, mouseY, delta)) {
			return true;
		}
		if (viewport.isMouseOver(mouseX, mouseY)) {
			viewport.zoom((float) delta);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// Handle editor dropdown (Escape to close, block other keys)
		if (actionEditorPanel != null && actionEditorPanel.keyPressed(keyCode, scanCode, modifiers)) {
			return true;
		}
		// Tab in an expression editbox → open completion
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB && actionEditorPanel != null) {
			if (Minecraft.getInstance().screen != null
					&& Minecraft.getInstance().screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox eb) {
				if (actionEditorPanel.handleTabCompletion(eb)) {
					return true;
				}
			}
		}
		// Don't capture keys when an edit box is focused (block space from triggering play/pause)
		if (actionEditorPanel != null && actionEditorPanel.isEditingExprBox()) {
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) return true;
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) return true;
			return super.keyPressed(keyCode, scanCode, modifiers);
		}

		// Ctrl+C/X/V for action clipboard
		if (net.minecraft.client.gui.screens.Screen.hasControlDown()) {
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_C) {
				if (actionListPanel.copySelected()) return true;
			}
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_X) {
				if (actionListPanel.cutSelected()) {
					actionEditorPanel.clearAction();
					scene.reset();
					return true;
				}
			}
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_V) {
				if (actionListPanel.pasteAfterSelected()) {
					scene.reset();
					return true;
				}
			}
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
				if (actionListPanel.moveSelectedUp()) {
					scene.reset();
					return true;
				}
			}
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
				if (actionListPanel.moveSelectedDown()) {
					scene.reset();
					return true;
				}
			}
		}

		// Space = play/pause
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) {
			scene.togglePlayPause();
			return true;
		}
		// R = reset
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
			scene.reset();
			return true;
		}
		// Right arrow = step
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) {
			scene.step();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// Expose for ActionEditorPanel
	public <T extends net.minecraft.client.gui.components.events.GuiEventListener &
			net.minecraft.client.gui.components.Renderable &
			net.minecraft.client.gui.narration.NarratableEntry> T addRenderableWidget(T widget) {
		return super.addRenderableWidget(widget);
	}

	public void removeWidget(net.minecraft.client.gui.components.events.GuiEventListener widget) {
		super.removeWidget(widget);
	}
}
