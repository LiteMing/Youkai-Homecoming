package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
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
 * Standalone screen for previewing spell card effects in an orthographic viewport.
 * Opened via /yhspell preview <spell_id>.
 */
@OnlyIn(Dist.CLIENT)
public class SpellPreviewScreen extends Screen {

	private final SpellDefinition definition;
	private final VirtualSpellScene scene;
	private final OrthographicViewport viewport;

	// Control panel layout constants
	private static final int CONTROL_HEIGHT = 98;
	private static final int TOP_BAR_HEIGHT = 20;
	private static final int BUTTON_HEIGHT = 16;
	private static final int BUTTON_SPACING = 2;

	// Phase dropdown state
	private final List<ResourceLocation> phaseList = new ArrayList<>();
	private int selectedPhaseIndex = 0;

	private boolean dragging = false;
	private boolean rotating = false;
	private boolean movingTarget = false;

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

		// Set viewport bounds (full width, minus top bar and control panel)
		int viewportY = TOP_BAR_HEIGHT;
		int viewportHeight = height - TOP_BAR_HEIGHT - CONTROL_HEIGHT;
		viewport.setBounds(0, viewportY, width, viewportHeight);

		// --- Top bar: view angle buttons + spell name ---
		int bx = 4;
		int by = 2;
		int bw = 60;
		for (ViewAngle angle : ViewAngle.values()) {
			addRenderableWidget(Button.builder(Component.literal(angle.getLabel()), btn -> {
				viewport.setViewAngle(angle);
			}).bounds(bx, by, bw, BUTTON_HEIGHT).build());
			bx += bw + BUTTON_SPACING;
		}

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

		// Row 3: Distance + HP presets
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

		// Row 4: Phase selection (prev/next buttons + label)
		bx = 4;
		addRenderableWidget(Button.builder(Component.literal("Phase:"), btn -> {})
				.bounds(bx, row4Y, 40, BUTTON_HEIGHT).build());
		bx += 42;
		addRenderableWidget(Button.builder(Component.literal("<"), btn -> cyclePhase(-1))
				.bounds(bx, row4Y, 16, BUTTON_HEIGHT).build());
		bx += 18;
		addRenderableWidget(Button.builder(Component.literal(">"), btn -> cyclePhase(1))
				.bounds(bx + 100, row4Y, 16, BUTTON_HEIGHT).build());

		// Row 5: Range (grid extent + clip depth)
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
	}

	private void cyclePhase(int delta) {
		if (phaseList.isEmpty()) return;
		selectedPhaseIndex = (selectedPhaseIndex + delta + phaseList.size()) % phaseList.size();
		scene.forcePhase(phaseList.get(selectedPhaseIndex));
	}

	@Override
	public void tick() {
		super.tick();
		scene.tick();

		// Sync selected phase index with runtime
		ResourceLocation current = scene.getCurrentPhaseId();
		int idx = phaseList.indexOf(current);
		if (idx >= 0) selectedPhaseIndex = idx;
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

		// Render widgets (buttons)
		super.render(guiGraphics, mouseX, mouseY, partialTick);

		// Status text on top bar
		String spellName = definition.id.toString();
		guiGraphics.drawString(font, spellName, width - font.width(spellName) - 4, 5, 0xFFAAAAAA, false);

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

		// View angle indicator + target position
		var tp = scene.getTargetPos();
		String targetInfo = String.format("Target: (%.1f, %.1f, %.1f)  [Left-drag to move]",
				tp.x, tp.y, tp.z);
		guiGraphics.drawString(font, "View: " + viewport.getViewLabel(),
				4, height - CONTROL_HEIGHT - 22, 0xFF888888, false);
		guiGraphics.drawString(font, targetInfo,
				4, height - CONTROL_HEIGHT - 12, 0xFFBBBB44, false);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (viewport.isMouseOver(mouseX, mouseY)) {
			if (button == 0) {
				// Left click: move target
				movingTarget = true;
				return true;
			}
			if (button == 2) {
				// Middle click: pan
				dragging = true;
				return true;
			}
			if (button == 1) {
				// Right click: free rotate
				rotating = true;
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
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
		if (viewport.isMouseOver(mouseX, mouseY)) {
			viewport.zoom((float) delta);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// Space = play/pause
		if (keyCode == 32) {
			scene.togglePlayPause();
			return true;
		}
		// R = reset
		if (keyCode == 82) {
			scene.reset();
			return true;
		}
		// Right arrow = step
		if (keyCode == 262) {
			scene.step();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
