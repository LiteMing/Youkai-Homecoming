package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDisplay;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellItemForm;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyProfile;
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
import dev.xkmc.youkaishomecoming.content.spell.preview.dock.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Standalone screen for previewing and editing spell card effects.
 * Left: orthographic viewport, Right: action list + property editor.
 * Opened via /yhspell preview <spell_id>.
 */
@OnlyIn(Dist.CLIENT)
public class SpellPreviewScreen extends Screen {

	private SpellDefinition definition;
	private final VirtualSpellScene scene;
	private final OrthographicViewport viewport;

	// Layout constants
	private static final int TOP_BAR_HEIGHT = 20;
	private static final int BUTTON_HEIGHT = 16;
	private static final int BUTTON_SPACING = 2;
	private static final ResourceLocation DRAFT_SPELL_ID = new ResourceLocation("minecraft", "__yh_editor__");
	private static final ResourceLocation DRAFT_ENTRY_PHASE = new ResourceLocation("minecraft", "__yh_editor__/main");

	// Dock layout system
	private DockLayout dockLayout;
	private ViewportDockPanel viewportPanel;
	private ActionListDockPanel actionListDockPanel;
	private EditorDockPanel editorDockPanel;
	private ControlsDockPanel controlsDockPanel;
	private StatusDockPanel statusDockPanel;
	private PerfDockPanel perfDockPanel;
	private HelpDockPanel helpDockPanel;

	// Editor panels (direct references for hotkey access)
	private ActionListPanel actionListPanel;
	private ActionEditorPanel actionEditorPanel;
	private boolean editorVisible = true;
	private ActionListPanel.AddTarget pendingAddTarget;

	// Phase dropdown state
	private final List<ResourceLocation> phaseList = new ArrayList<>();
	private int selectedPhaseIndex = 0;

	private boolean autoReplay = true;
	private boolean draftMode;
	private boolean skipSaveOnNextDefinitionSwitch;
	private com.google.gson.JsonObject pendingDockLayout;

	/** Snapshots of visited spell definitions when this editor first saw them. */
	private final java.util.Map<ResourceLocation, com.google.gson.JsonElement> openSnapshots = new java.util.HashMap<>();

	public SpellPreviewScreen(SpellDefinition definition) {
		this(definition, isDraftDefinition(definition));
	}

	private SpellPreviewScreen(SpellDefinition definition, boolean draftMode) {
		super(Component.literal(draftMode ? "Spell Editor" : "Spell Preview: " + definition.id));
		this.definition = definition;
		this.draftMode = draftMode;
		this.scene = new VirtualSpellScene(definition);
		this.scene.setOnStateChanged(this::syncSceneState);
		this.viewport = new OrthographicViewport();
		this.phaseList.addAll(definition.phases.keySet());
		if (!phaseList.isEmpty()) {
			this.scene.resetToPhase(phaseList.get(selectedPhaseIndex));
		}
		if (!draftMode) {
			rememberOpenSnapshot(definition);
		}
		// Create persistent dock panels
		this.viewportPanel = new ViewportDockPanel(viewport, scene);
		this.statusDockPanel = new StatusDockPanel(scene, viewport);
		this.helpDockPanel = new HelpDockPanel();
	}

	public static SpellPreviewScreen createDraftEditor() {
		return new SpellPreviewScreen(createDraftDefinition(), true);
	}

	@Override
	protected void init() {
		super.init();
		boolean fullEdit = !isDraftMode();

		// --- Top bar: view angle buttons + toggle editor + spell name ---
		int bx = 4;
		int by = 2;
		int bw = 50;
		for (ViewAngle angle : ViewAngle.values()) {
			Button angleButton = Button.builder(Component.literal(angle.getLabel()), btn -> {
				viewport.setPerspectiveMode(false);
				viewport.setViewAngle(angle);
			}).bounds(bx, by, bw, BUTTON_HEIGHT).build();
			angleButton.active = fullEdit;
			addRenderableWidget(angleButton);
			bx += bw + BUTTON_SPACING;
		}
		// Perspective / Orthographic toggle
		String perspLabel = viewport.isPerspectiveMode() ? "Ortho" : "Persp";
		Button perspectiveButton = Button.builder(Component.literal(perspLabel), btn -> {
			boolean newPersp = !viewport.isPerspectiveMode();
			viewport.setPerspectiveMode(newPersp);
			if (newPersp) {
				// Set camera to dummy target position
				viewport.setCameraToTarget(scene.getTargetPos());
			}
			rebuildScreen();
		}).bounds(bx, by, 40, BUTTON_HEIGHT).build();
		perspectiveButton.active = fullEdit;
		addRenderableWidget(perspectiveButton);
		bx += 42;
		// Bind target toggle (only in perspective mode)
		if (viewport.isPerspectiveMode()) {
			String bindLabel = viewport.isTargetBoundToCamera() ? "Unbind" : "BindTgt";
			Button bindButton = Button.builder(Component.literal(bindLabel), btn -> {
				viewport.setTargetBoundToCamera(!viewport.isTargetBoundToCamera());
				rebuildScreen();
			}).bounds(bx, by, 48, BUTTON_HEIGHT).build();
			bindButton.active = fullEdit;
			addRenderableWidget(bindButton);
			bx += 50;
		}
		// Toggle editor button
		bx += 10;
		Button editorToggleButton = Button.builder(Component.literal(editorVisible ? "Editor <<" : "Editor >>"), btn -> {
			editorVisible = !editorVisible;
			rebuildScreen();
		}).bounds(bx, by, 60, BUTTON_HEIGHT).build();
		editorToggleButton.active = fullEdit;
		addRenderableWidget(editorToggleButton);
		bx += 62;
		// Apply button: re-apply edited spell to all entities using it
		Button applyButton = Button.builder(Component.literal("Apply"), btn -> applyToEntities())
				.bounds(bx, by, 40, BUTTON_HEIGHT).build();
		applyButton.active = fullEdit;
		addRenderableWidget(applyButton);
		bx += 42;
		// Export button: save spell definition as JSON datapack file
		Button exportButton = Button.builder(Component.literal("Export"), btn -> exportToDatapack())
				.bounds(bx, by, 46, BUTTON_HEIGHT).build();
		exportButton.active = fullEdit;
		addRenderableWidget(exportButton);
		bx += 48;
		// Reset button: restore to original (built-in) or open-snapshot (custom)
		Button resetButton = Button.builder(Component.literal("Reset"), btn -> resetToDefault())
				.bounds(bx, by, 40, BUTTON_HEIGHT).build();
		resetButton.active = fullEdit;
		addRenderableWidget(resetButton);
		bx += 42;
		// Auto Replay toggle
		Button autoReplayButton = Button.builder(Component.literal(autoReplay ? "Auto:ON" : "Auto:OFF"), btn -> {
			autoReplay = !autoReplay;
			rebuildScreen();
		}).bounds(bx, by, 52, BUTTON_HEIGHT).build();
		autoReplayButton.active = fullEdit;
		addRenderableWidget(autoReplayButton);
		bx += 54;
		// Help button — toggles HelpDockPanel as a docked tab
		Button helpButton = Button.builder(Component.literal("Help"), btn -> {
			toggleHelpPanel();
		}).bounds(bx, by, 32, BUTTON_HEIGHT).build();
		helpButton.active = fullEdit;
		addRenderableWidget(helpButton);
		bx += 34;
		// Collapse All / Expand All
		Button collapseAllButton = Button.builder(Component.literal("\u25B6All"), btn -> {
			if (actionListPanel != null) actionListPanel.collapseAll();
		}).bounds(bx, by, 34, BUTTON_HEIGHT).build();
		collapseAllButton.active = fullEdit;
		addRenderableWidget(collapseAllButton);
		bx += 36;
		Button expandAllButton = Button.builder(Component.literal("\u25BCAll"), btn -> {
			if (actionListPanel != null) actionListPanel.expandAll();
		}).bounds(bx, by, 34, BUTTON_HEIGHT).build();
		expandAllButton.active = fullEdit;
		addRenderableWidget(expandAllButton);
		bx += 36;
		// Toggle show all add-buttons
		if (actionListPanel != null) {
			String addLabel = actionListPanel.isShowAllAddButtons() ? "[+]:All" : "[+]:Sel";
			Button addButtonModeButton = Button.builder(Component.literal(addLabel), btn -> {
				actionListPanel.toggleShowAllAddButtons();
				rebuildScreen();
			}).bounds(bx, by, 42, BUTTON_HEIGHT).build();
			addButtonModeButton.active = fullEdit;
			addRenderableWidget(addButtonModeButton);
			bx += 44;
		}
		// Reset Layout button
		Button resetLayoutButton = Button.builder(Component.literal("RstLayout"), btn -> {
			DockSerializer.deleteLayout();
			rebuildScreen(false);
		}).bounds(bx, by, 56, BUTTON_HEIGHT).build();
		resetLayoutButton.active = fullEdit;
		addRenderableWidget(resetLayoutButton);

		// --- Create editor panels ---
		actionListPanel = new ActionListPanel(
				(action, path) -> {
					if (actionEditorPanel != null) {
						actionEditorPanel.setAction(action, path.leafIndex());
					}
				},
				this::onRequestAddAction,
				this::replaySelectedPhase
		);
		actionListPanel.loadCustomNames(definition.customNames);

		actionEditorPanel = new ActionEditorPanel(
				this::addRenderableWidget,
				this::removeWidget,
				this::onActionEdited,
				this::onDeleteAction
		);
		actionEditorPanel.setPhaseOptions(() -> List.copyOf(phaseList), this::getPhaseOptionLabel);
		actionEditorPanel.setSpellOptions(this::getSpellOptions, this::getSpellOptionLabel);
		actionEditorPanel.setToggleDisableCallback(() -> {
			if (actionListPanel != null && actionListPanel.toggleSelectedDisabled()) {
				actionEditorPanel.clearAction();
				if (autoReplay) replaySelectedPhase();
			}
		});
		actionEditorPanel.setVariableJumpCallback(varName -> {
			if (actionListPanel != null) {
				actionListPanel.jumpToVariableDefinition(varName);
			}
		});

		// --- Wrap in dock panel adapters ---
		actionListDockPanel = new ActionListDockPanel(actionListPanel);
		editorDockPanel = new EditorDockPanel(actionEditorPanel);
		controlsDockPanel = new ControlsDockPanel(
				scene, viewport, this::rebuildScreen, () -> resetSelectedPhasePreview(false),
				this::getSpellOptions, this::getCurrentSpellSelectionId, this::getCurrentSpellButtonLabel, this::getSpellOptionLabel,
				this::switchSelectedSpell, this::enterDraftSpellEditor, this::deleteSelectedSpell,
				this::canDeleteSelectedSpell, this::isDraftMode, this::nameCurrentDraftSpell, this::cyclePhase,
				this::getSelectedPhaseDisplayName, this::renameSelectedPhase, this::addPhase,
				this::deleteSelectedPhase, this::canDeleteSelectedPhase);
		controlsDockPanel.setWidgetCallbacks(w -> this.addRenderableWidget(w), this::removeWidget);
		perfDockPanel = new PerfDockPanel(scene);

		// --- Build dock layout tree (load from config or use default) ---
		java.util.Map<String, DockPanel> panelMap = new java.util.LinkedHashMap<>();
		panelMap.put(viewportPanel.dockId(), viewportPanel);
		panelMap.put(actionListDockPanel.dockId(), actionListDockPanel);
		panelMap.put(editorDockPanel.dockId(), editorDockPanel);
		panelMap.put(controlsDockPanel.dockId(), controlsDockPanel);
		panelMap.put(statusDockPanel.dockId(), statusDockPanel);
		panelMap.put(perfDockPanel.dockId(), perfDockPanel);
		panelMap.put(helpDockPanel.dockId(), helpDockPanel);

		com.google.gson.JsonObject layoutSnapshot = pendingDockLayout;
		pendingDockLayout = null;
		if (layoutSnapshot != null) {
			dockLayout = new DockLayout(DockSerializer.loadLayout(layoutSnapshot, panelMap, SpellPreviewScreen::buildDefaultLayout));
		} else {
			boolean hadSavedLayout = DockSerializer.hasSavedLayout();
			boolean savedLayoutHasStatusPanel = DockSerializer.savedLayoutContainsPanel(statusDockPanel.dockId());
			DockNode root = DockSerializer.loadLayout(panelMap, SpellPreviewScreen::buildDefaultLayout);
			dockLayout = new DockLayout(root);
			if (hadSavedLayout && !savedLayoutHasStatusPanel) {
				relocateMissingStatusPanel();
			}
		}
		dockLayout.layout(0, TOP_BAR_HEIGHT, width, height - TOP_BAR_HEIGHT);
		// Set active group to the one containing the viewport
		DockGroup vpGroup = dockLayout.findGroupContaining(viewportPanel);
		if (vpGroup != null) dockLayout.setActiveGroup(vpGroup);

		controlsDockPanel.buildButtons();
		updateActionListPhase();
	}

	/**
	 * 构建默认布局树。用于首次打开或布局文件损坏时的回退。
	 */
	static DockNode buildDefaultLayout(java.util.Map<String, DockPanel> panelMap) {
		DockPanel viewport = panelMap.get("viewport");
		DockPanel actions = panelMap.get("actions");
		DockPanel properties = panelMap.get("properties");
		DockPanel controls = panelMap.get("controls");
		DockPanel status = panelMap.get("status");
		DockPanel perf = panelMap.get("perf");

		DockGroup viewportGroup = new DockGroup(viewport);
		DockGroup actionListGroup = new DockGroup(actions);
		DockGroup editorGroup = new DockGroup(properties);
		DockGroup controlsGroup = new DockGroup(controls, perf);
		DockGroup statusGroup = new DockGroup(status);
		// Help 面板默认不显示

		DockSplit rightSplit = new DockSplit(false, 0.4f, actionListGroup, editorGroup);
		DockSplit mainSplit = new DockSplit(true, 0.6f, viewportGroup, rightSplit);
		DockSplit bottomSplit = new DockSplit(true, 0.72f, controlsGroup, statusGroup);
		return new DockSplit(false, 0.8f, mainSplit, bottomSplit);
	}

	private void relocateMissingStatusPanel() {
		if (dockLayout == null || statusDockPanel == null) {
			return;
		}
		DockGroup currentGroup = dockLayout.findGroupContaining(statusDockPanel);
		if (currentGroup != null && currentGroup.getPanelCount() == 1) {
			return;
		}
		boolean removed = false;
		if (currentGroup != null) {
			removed = currentGroup.removePanel(statusDockPanel);
		}
		DockGroup anchor = dockLayout.findGroupContaining(controlsDockPanel);
		if (anchor == null) {
			anchor = dockLayout.findGroupContaining(perfDockPanel);
		}
		if (anchor == null) {
			anchor = dockLayout.findGroupContaining(viewportPanel);
		}
		if (anchor == null) {
			if (removed && currentGroup != null) {
				currentGroup.addPanel(statusDockPanel);
			}
			return;
		}
		DockGroup statusGroup = new DockGroup(statusDockPanel);
		DockSplit split = new DockSplit(true, 0.72f, anchor, statusGroup);
		if (dockLayout.getRoot() == anchor) {
			dockLayout.setRoot(split);
			return;
		}
		if (!replaceDockNode(dockLayout.getRoot(), anchor, split) && removed && currentGroup != null) {
			currentGroup.addPanel(statusDockPanel);
		}
	}

	private boolean replaceDockNode(DockNode current, DockNode oldNode, DockNode newNode) {
		if (current instanceof DockSplit split) {
			if (split.getFirst() == oldNode) {
				split.setFirst(newNode);
				return true;
			}
			if (split.getSecond() == oldNode) {
				split.setSecond(newNode);
				return true;
			}
			return replaceDockNode(split.getFirst(), oldNode, newNode)
					|| replaceDockNode(split.getSecond(), oldNode, newNode);
		}
		return false;
	}

	private void rebuildScreen() {
		rebuildScreen(true);
	}

	private void rebuildScreen(boolean preserveLayout) {
		pendingDockLayout = preserveLayout && dockLayout != null ? DockSerializer.serialize(dockLayout.getRoot()) : null;
		this.init(minecraft, width, height);
	}

	private void toggleHelpPanel() {
		if (dockLayout == null) return;
		DockGroup helpGroup = dockLayout.findGroupContaining(helpDockPanel);
		if (helpGroup != null) {
			// Help 已显示 → 移除
			helpGroup.removePanel(helpDockPanel);
			dockLayout.relayout();
		} else {
			// Help 未显示 → 添加到编辑器面板所在的 Group 作为新 Tab
			DockGroup target = dockLayout.findGroupContaining(editorDockPanel);
			if (target == null) target = dockLayout.getActiveGroup();
			if (target != null) {
				target.addPanel(helpDockPanel);
				target.setActiveIndex(target.getPanelCount() - 1);
			}
		}
	}

	private void onActionEdited(SpellAction newAction) {
		if (actionListPanel != null) {
			actionListPanel.replaceSelectedAction(newAction);
			if (autoReplay) replaySelectedPhase();
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
			if (autoReplay) replaySelectedPhase();
		}
	}

	private void onDeleteAction() {
		if (actionListPanel != null && actionListPanel.deleteSelected()) {
			if (actionEditorPanel != null) actionEditorPanel.clearAction();
			if (autoReplay) replaySelectedPhase();
		}
	}

	/**
	 * Re-apply the edited spell definition to all entities currently using it.
	 * Works in singleplayer by directly accessing the integrated server.
	 * Matches both new SpellRuntime entities and legacy SpellCardWrapper entities.
	 */
	private void applyToEntities() {
		if (isDraftMode()) {
			return;
		}
		var mc = Minecraft.getInstance();
		saveCurrentDefinition();
		var server = mc.getSingleplayerServer();
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
		if (isDraftMode()) {
			return;
		}
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

	/**
	 * Reset the spell to its original state.
	 * Built-in spells: restored from SpellRegistry defaults (code-defined).
	 * Custom spells: restored from the snapshot taken when the editor was opened.
	 */
	private void resetToDefault() {
		if (isDraftMode()) {
			return;
		}
		// Try built-in default first
		SpellDefinition restored = SpellRegistry.getDefault(definition.id);
		var openSnapshot = openSnapshots.get(definition.id);
		if (restored == null && openSnapshot != null) {
			// Custom spell: restore from open-time snapshot
			restored = SpellDefinition.CODEC.parse(
					com.mojang.serialization.JsonOps.INSTANCE, openSnapshot).result().orElse(null);
		}
		if (restored == null) return;

		// Replace the mutable spell content with the restored snapshot.
		definition.phases.clear();
		definition.phases.putAll(restored.phases);
		definition.customNames.clear();
		definition.customNames.putAll(restored.customNames);
		phaseList.clear();
		phaseList.addAll(definition.phases.keySet());
		selectedPhaseIndex = phaseList.isEmpty() ? 0 : Math.min(selectedPhaseIndex, phaseList.size() - 1);

		// Refresh UI
		if (actionEditorPanel != null) actionEditorPanel.clearAction();
		if (actionListPanel != null) {
			actionListPanel.loadCustomNames(definition.customNames);
		}
		updateActionListPhase();
		refreshPhaseControls();
		replaySelectedPhase();

		var mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(Component.literal("[YH] Spell reset to default"), true);
		}
	}

	private void rememberOpenSnapshot(SpellDefinition definition) {
		if (isDraftDefinition(definition)) {
			return;
		}
		openSnapshots.computeIfAbsent(definition.id, id -> SpellDefinition.CODEC.encodeStart(
				com.mojang.serialization.JsonOps.INSTANCE, definition).result().orElse(null));
	}

	private void updateActionListPhase() {
		if (actionListPanel == null) return;
		if (phaseList.isEmpty()) {
			actionListPanel.setPhase(null);
			return;
		}
		ResourceLocation phaseId = phaseList.get(selectedPhaseIndex);
		PhaseDefinition phase = definition.phases.get(phaseId);
		actionListPanel.setPhase(phase);
	}

	private ResourceLocation getSelectedPhaseId() {
		if (phaseList.isEmpty()) return null;
		return phaseList.get(selectedPhaseIndex);
	}

	private void resetSelectedPhasePreview(boolean autoplay) {
		ResourceLocation phaseId = getSelectedPhaseId();
		if (phaseId == null) {
			scene.reset();
		} else {
			scene.resetToPhase(phaseId);
		}
		if (autoplay) {
			scene.play();
		}
	}

	private void replaySelectedPhase() {
		resetSelectedPhasePreview(true);
	}

	private void cyclePhase(int delta) {
		if (phaseList.isEmpty()) return;
		boolean wasPlaying = scene.isPlaying();
		selectedPhaseIndex = (selectedPhaseIndex + delta + phaseList.size()) % phaseList.size();
		resetSelectedPhasePreview(wasPlaying);
		if (actionEditorPanel != null) actionEditorPanel.clearAction();
		updateActionListPhase();
		refreshPhaseControls();
	}

	private void addPhase() {
		ResourceLocation newPhaseId = createUniquePhaseId();
		definition.phases.put(newPhaseId, new PhaseDefinition(newPhaseId, List.of(), List.of(), List.of(), List.of(), List.of()));
		phaseList.add(newPhaseId);
		selectedPhaseIndex = phaseList.size() - 1;
		resetSelectedPhasePreview(autoReplay);
		if (actionEditorPanel != null) {
			actionEditorPanel.clearAction();
		}
		updateActionListPhase();
		refreshPhaseControls();
		refreshActionEditor();
	}

	private boolean canDeleteSelectedPhase() {
		ResourceLocation phaseId = getSelectedPhaseId();
		return phaseId != null && phaseList.size() > 1 && !phaseId.equals(definition.entryPhase);
	}

	private void deleteSelectedPhase() {
		ResourceLocation removedPhaseId = getSelectedPhaseId();
		if (removedPhaseId == null || !canDeleteSelectedPhase()) {
			return;
		}
		boolean wasPlaying = scene.isPlaying();
		int removedTransitions = removeTransitionsTargeting(removedPhaseId);
		definition.phases.remove(removedPhaseId);
		phaseList.remove(selectedPhaseIndex);
		clearPhaseCustomName(removedPhaseId);
		selectedPhaseIndex = Math.max(0, Math.min(selectedPhaseIndex, phaseList.size() - 1));
		if (actionEditorPanel != null) {
			actionEditorPanel.clearAction();
		}
		updateActionListPhase();
		refreshPhaseControls();
		refreshActionEditor();
		resetSelectedPhasePreview(wasPlaying || autoReplay);

		var mc = Minecraft.getInstance();
		if (mc.player != null) {
			String msg = "[YH] Deleted phase " + formatPhaseId(removedPhaseId) +
					(removedTransitions > 0 ? " and removed " + removedTransitions + " transitions" : "");
			mc.player.displayClientMessage(Component.literal(msg), true);
		}
	}

	private int removeTransitionsTargeting(ResourceLocation removedPhaseId) {
		int removed = 0;
		for (PhaseDefinition phase : definition.phases.values()) {
			var iter = phase.transitions.iterator();
			while (iter.hasNext()) {
				var transition = iter.next();
				if (removedPhaseId.equals(transition.targetPhase())) {
					iter.remove();
					removed++;
				}
			}
		}
		return removed;
	}

	private void renameSelectedPhase(String name) {
		if (phaseList.isEmpty()) return;
		ResourceLocation phaseId = phaseList.get(selectedPhaseIndex);
		String trimmed = name.trim();
		if (trimmed.isEmpty() || trimmed.equals(formatPhaseId(phaseId)) || trimmed.equals(phaseId.getPath())) {
			clearPhaseCustomName(phaseId);
		} else {
			setPhaseCustomName(phaseId, trimmed);
		}
		refreshPhaseControls();
		refreshActionEditor();
	}

	private String getSelectedPhaseDisplayName() {
		if (phaseList.isEmpty()) return "";
		ResourceLocation phaseId = phaseList.get(selectedPhaseIndex);
		String custom = getStoredPhaseCustomName(phaseId);
		return custom != null ? custom : formatPhaseId(phaseId);
	}

	private String getPhaseOptionLabel(ResourceLocation phaseId) {
		String custom = getStoredPhaseCustomName(phaseId);
		if (custom == null || custom.isBlank() || custom.equals(phaseId.getPath())) {
			return formatPhaseId(phaseId);
		}
		return custom + " (" + formatPhaseId(phaseId) + ")";
	}

	private List<ResourceLocation> getSpellOptions() {
		var spells = new ArrayList<>(SpellRegistry.getAll().keySet());
		spells.sort(java.util.Comparator.comparing(SpellPreviewScreen::formatResourceId));
		return spells;
	}

	private String getSpellOptionLabel(ResourceLocation spellId) {
		return formatResourceId(spellId);
	}

	private String getCurrentSpellButtonLabel() {
		return isDraftMode() ? "New Spell" : getSpellOptionLabel(definition.id);
	}

	private ResourceLocation getCurrentSpellSelectionId() {
		return isDraftMode() ? null : definition.id;
	}

	private void switchSelectedSpell(ResourceLocation spellId) {
		if (spellId == null || (!isDraftMode() && spellId.equals(definition.id))) {
			return;
		}
		SpellDefinition target = SpellRegistry.get(spellId);
		if (target == null) {
			return;
		}
		saveCurrentDefinition();
		skipSaveOnNextDefinitionSwitch = true;
		boolean wasPlaying = scene.isPlaying();
		scene.pause();
		scene.switchSpellDefinition(target, true);
		if (wasPlaying) {
			scene.play();
		} else {
			scene.pause();
		}
	}

	private boolean canDeleteSelectedSpell() {
		return !isDraftMode() && !SpellRegistry.hasDefault(definition.id);
	}

	private void enterDraftSpellEditor() {
		saveCurrentDefinition();
		skipSaveOnNextDefinitionSwitch = true;
		draftMode = true;
		scene.pause();
		scene.switchSpellDefinition(createDraftDefinition(), true);
	}

	private void nameCurrentDraftSpell(String name) {
		if (!isDraftMode()) {
			return;
		}
		ResourceLocation spellId = parseDraftSpellId(name);
		if (spellId == null) {
			displayEditorMessage("[YH] Invalid spell id");
			return;
		}
		if (SpellRegistry.contains(spellId)) {
			displayEditorMessage("[YH] Spell already exists: " + formatResourceId(spellId));
			return;
		}
		SpellDefinition created = createEmptySpellDefinition(spellId);
		SpellRegistry.register(created);
		var server = Minecraft.getInstance().getSingleplayerServer();
		if (server != null) {
			server.execute(() -> CustomSpellStorage.saveSpell(server, created));
		}
		skipSaveOnNextDefinitionSwitch = true;
		scene.pause();
		scene.switchSpellDefinition(created, true);
		displayEditorMessage("[YH] Created spell " + formatResourceId(spellId));
	}

	private void deleteSelectedSpell() {
		ResourceLocation spellId = definition.id;
		if (!canDeleteSelectedSpell()) {
			return;
		}
		openSnapshots.remove(spellId);
		SpellRegistry.remove(spellId);
		var server = Minecraft.getInstance().getSingleplayerServer();
		if (server != null) {
			server.execute(() -> CustomSpellStorage.deleteSpell(server, spellId));
		}
		skipSaveOnNextDefinitionSwitch = true;
		draftMode = true;
		scene.pause();
		scene.switchSpellDefinition(createDraftDefinition(), true);
		displayEditorMessage("[YH] Deleted spell " + formatResourceId(spellId));
	}

	private void refreshPhaseControls() {
		if (controlsDockPanel != null) {
			controlsDockPanel.buildButtons();
		}
	}

	private void refreshActionEditor() {
		if (actionEditorPanel != null) {
			actionEditorPanel.refreshCurrentView();
		}
	}

	private ResourceLocation createUniquePhaseId() {
		int index = Math.max(phaseList.size() + 1, 1);
		ResourceLocation id;
		do {
			id = new ResourceLocation(definition.id.getNamespace(), "phase_" + index++);
		} while (definition.phases.containsKey(id));
		return id;
	}

	private static String getPhaseNameKey(ResourceLocation phaseId) {
		return "phase:" + formatPhaseId(phaseId);
	}

	private static String getLegacyPhaseNameKey(ResourceLocation phaseId) {
		return "phase:" + formatResourceId(phaseId);
	}

	private static String formatPhaseId(ResourceLocation phaseId) {
		return phaseId.toString();
	}

	private String getStoredPhaseCustomName(ResourceLocation phaseId) {
		String value = definition.customNames.get(getPhaseNameKey(phaseId));
		if (value != null && !value.isBlank()) {
			return value;
		}
		value = definition.customNames.get(getLegacyPhaseNameKey(phaseId));
		return value != null && !value.isBlank() ? value : null;
	}

	private void clearPhaseCustomName(ResourceLocation phaseId) {
		String key = getPhaseNameKey(phaseId);
		String legacyKey = getLegacyPhaseNameKey(phaseId);
		definition.customNames.remove(key);
		definition.customNames.remove(legacyKey);
		if (actionListPanel != null) {
			actionListPanel.setCustomName(key, null);
			actionListPanel.setCustomName(legacyKey, null);
		}
	}

	private void setPhaseCustomName(ResourceLocation phaseId, String value) {
		String key = getPhaseNameKey(phaseId);
		String legacyKey = getLegacyPhaseNameKey(phaseId);
		definition.customNames.remove(legacyKey);
		definition.customNames.put(key, value);
		if (actionListPanel != null) {
			actionListPanel.setCustomName(legacyKey, null);
			actionListPanel.setCustomName(key, value);
		}
	}

	private static String formatResourceId(ResourceLocation id) {
		return "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
	}

	private void syncCustomNamesToDefinition() {
		if (actionListPanel != null) {
			definition.customNames.clear();
			definition.customNames.putAll(actionListPanel.getCustomNames());
		}
	}

	private void saveCurrentDefinition() {
		if (isDraftMode()) {
			return;
		}
		syncCustomNamesToDefinition();
		SpellRegistry.register(definition);
		var server = Minecraft.getInstance().getSingleplayerServer();
		if (server != null) {
			server.execute(() -> CustomSpellStorage.saveSpell(server, definition));
		}
	}

	private boolean isDraftMode() {
		return draftMode || isDraftDefinition(definition);
	}

	private static boolean isDraftDefinition(SpellDefinition definition) {
		return definition != null && DRAFT_SPELL_ID.equals(definition.id);
	}

	private static SpellDefinition createDraftDefinition() {
		return new SpellDefinition(
				DRAFT_SPELL_ID,
				new SpellDisplay("new_spell", "", Optional.empty(), Optional.empty()),
				SpellItemForm.NONE,
				DRAFT_ENTRY_PHASE,
				Map.of(),
				DifficultyProfile.DEFAULT
		);
	}

	private static SpellDefinition createEmptySpellDefinition(ResourceLocation spellId) {
		ResourceLocation phaseId = new ResourceLocation(spellId.getNamespace(), spellId.getPath() + "/main");
		PhaseDefinition phase = new PhaseDefinition(
				phaseId,
				List.of(),
				List.of(),
				List.of(),
				List.of(),
				List.of()
		);
		return new SpellDefinition(
				spellId,
				new SpellDisplay(spellId.getPath(), "", Optional.empty(), Optional.empty()),
				SpellItemForm.NONE,
				phaseId,
				Map.of(phaseId, phase),
				DifficultyProfile.DEFAULT
		);
	}

	private ResourceLocation parseDraftSpellId(String raw) {
		String trimmed = raw == null ? "" : raw.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		ResourceLocation id = ResourceLocation.tryParse(trimmed.contains(":") ? trimmed : "minecraft:" + trimmed);
		if (id == null || DRAFT_SPELL_ID.equals(id)) {
			return null;
		}
		return id;
	}

	private void displayEditorMessage(String message) {
		var mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(Component.literal(message), true);
		}
	}

	private void switchToDefinition(SpellDefinition definition) {
		if (!skipSaveOnNextDefinitionSwitch) {
			saveCurrentDefinition();
		}
		skipSaveOnNextDefinitionSwitch = false;
		boolean oldDraftMode = this.draftMode;
		this.definition = definition;
		this.draftMode = isDraftDefinition(definition);
		rememberOpenSnapshot(definition);
		phaseList.clear();
		phaseList.addAll(definition.phases.keySet());
		if (oldDraftMode != this.draftMode) {
			rebuildScreen();
			return;
		}
		ResourceLocation currentPhase = scene.getCurrentPhaseId();
		int idx = phaseList.indexOf(currentPhase);
		selectedPhaseIndex = idx >= 0 ? idx : 0;
		if (actionEditorPanel != null) {
			actionEditorPanel.clearAction();
		}
		if (actionListPanel != null) {
			actionListPanel.loadCustomNames(definition.customNames);
		}
		updateActionListPhase();
		refreshPhaseControls();
		refreshActionEditor();
	}

	private void syncSceneState() {
		SpellDefinition currentDefinition = scene.getDefinition();
		if (definition != currentDefinition) {
			switchToDefinition(currentDefinition);
		}
		ResourceLocation currentPhase = scene.getCurrentPhaseId();
		int idx = phaseList.indexOf(currentPhase);
		if (idx >= 0 && idx != selectedPhaseIndex) {
			selectedPhaseIndex = idx;
			if (actionEditorPanel != null) {
				actionEditorPanel.clearAction();
			}
			updateActionListPhase();
			refreshPhaseControls();
			refreshActionEditor();
		}
	}

	@Override
	public void tick() {
		super.tick();
		scene.tick();

		// Perspective camera movement (delegated to ViewportDockPanel)
		if (viewportPanel != null) {
			viewportPanel.tick(isAnyEditBoxFocused());
		}

		syncSceneState();
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(guiGraphics);

		// Dock layout renders all panels
		if (dockLayout != null) {
			dockLayout.render(guiGraphics, mouseX, mouseY, partialTick);
		}

		// Screen widgets (top bar buttons, control panel buttons)
		super.render(guiGraphics, mouseX, mouseY, partialTick);

		// Dropdown/completion overlay on top of everything
		if (dockLayout != null) {
			dockLayout.renderOverlay(guiGraphics, mouseX, mouseY);
		}

		// Spell name on top bar
		String spellName = isDraftMode() ? "New Spell" : definition.id.toString();
		int nameX = width - font.width(spellName) - 4;
		guiGraphics.drawString(font, spellName, nameX, 5, 0xFFAAAAAA, false);

	}


	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// Editor dropdown/completion may extend beyond panel bounds — check first
		if (actionEditorPanel != null && actionEditorPanel.mouseClicked(mouseX, mouseY, button)) {
			// 同步 activeGroup 到编辑器所在的 Group
			if (dockLayout != null) {
				DockGroup eg = dockLayout.findGroupContaining(editorDockPanel);
				if (eg != null) dockLayout.setActiveGroup(eg);
			}
			return true;
		}
		// Dock layout dispatches to panels (also updates activeGroup)
		if (dockLayout != null && dockLayout.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		// Screen widgets (top bar buttons, control panel buttons)
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (dockLayout != null && dockLayout.mouseReleased(mouseX, mouseY, button)) {
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (dockLayout != null && dockLayout.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		if (viewportPanel != null && viewportPanel.mouseMoved(mouseX, mouseY)) {
			return;
		}
		super.mouseMoved(mouseX, mouseY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (viewport.isPerspectiveCaptured()) {
			viewport.perspectiveAdjustSpeed((float) delta);
			return true;
		}
		if (dockLayout != null && dockLayout.mouseScrolled(mouseX, mouseY, delta)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	/**
	 * Check if any EditBox in the screen is currently focused (user is typing text).
	 * When true, all custom hotkeys should be suppressed to avoid conflicts.
	 */
	private boolean isAnyEditBoxFocused() {
		return getFocused() instanceof net.minecraft.client.gui.components.EditBox;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// ESC in perspective mode
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && viewport.isPerspectiveMode()) {
			if (viewport.isPerspectiveCaptured()) {
				// First ESC: exit captured free-look, restore cursor
				viewport.setPerspectiveCaptured(false);
				org.lwjgl.glfw.GLFW.glfwSetInputMode(
						Minecraft.getInstance().getWindow().getWindow(),
						org.lwjgl.glfw.GLFW.GLFW_CURSOR,
						org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL);
				return true;
			}
			// Second ESC (not captured): exit perspective mode entirely
			viewport.setPerspectiveMode(false);
			rebuildScreen();
			return true;
		}

		// Handle editor dropdown/completion overlays (Escape to close, arrow keys, etc.)
		if (actionEditorPanel != null && actionEditorPanel.keyPressed(keyCode, scanCode, modifiers)) {
			return true;
		}
		if (controlsDockPanel != null && controlsDockPanel.isDropdownOpen()
				&& dockLayout != null && dockLayout.keyPressed(keyCode, scanCode, modifiers)) {
			return true;
		}

		// === EditBox focus gate ===
		// When an EditBox is focused, ALL custom hotkeys are blocked.
		// Only Tab (for completion) is handled specially, everything else goes to super
		// which routes to the focused EditBox for normal text editing.
		if (isAnyEditBoxFocused()) {
			// Tab → expression autocomplete
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB && actionEditorPanel != null) {
				if (getFocused() instanceof net.minecraft.client.gui.components.EditBox eb) {
					if (actionEditorPanel.handleTabCompletion(eb)) {
						return true;
					}
				}
			}
			// Escape → unfocus the EditBox (return to normal mode)
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
				setFocused(null);
				return true;
			}
			// Let the active dock consume submit-style shortcuts such as Enter in Controls.
			if (dockLayout != null && dockLayout.keyPressed(keyCode, scanCode, modifiers)) {
				return true;
			}
			// All other keys → let EditBox handle (typing, cursor, Ctrl+A/C/V within text)
			return super.keyPressed(keyCode, scanCode, modifiers);
		}

		if (isDraftMode()) {
			return super.keyPressed(keyCode, scanCode, modifiers);
		}

		// === Below: no EditBox is focused, custom hotkeys active ===

		// Ctrl+Z/Y for undo/redo
		if (net.minecraft.client.gui.screens.Screen.hasControlDown()) {
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Z && actionListPanel != null) {
				if (actionListPanel.undo()) {
					if (actionEditorPanel != null) actionEditorPanel.clearAction();
					if (autoReplay) replaySelectedPhase();
					return true;
				}
			}
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Y && actionListPanel != null) {
				if (actionListPanel.redo()) {
					if (actionEditorPanel != null) actionEditorPanel.clearAction();
					if (autoReplay) replaySelectedPhase();
					return true;
				}
			}
		}

		// Ctrl+D = toggle disable, Ctrl+N = toggle custom names, Ctrl+E = collapse/expand
		if (net.minecraft.client.gui.screens.Screen.hasControlDown()) {
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_D && actionListPanel != null) {
				if (actionListPanel.toggleSelectedDisabled()) {
					if (actionEditorPanel != null) actionEditorPanel.clearAction();
					if (autoReplay) replaySelectedPhase();
					return true;
				}
			}
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_N && actionListPanel != null) {
				actionListPanel.toggleCustomNames();
				return true;
			}
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_E && actionListPanel != null) {
				if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
					// Ctrl+Shift+E = collapse/expand all
					if (actionListPanel.isShowAllAddButtons()) {
						actionListPanel.collapseAll();
					} else {
						actionListPanel.expandAll();
					}
				} else {
					actionListPanel.toggleSelectedCollapse();
				}
				return true;
			}
			// Ctrl+B = toggle show all add-buttons
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_B && actionListPanel != null) {
				actionListPanel.toggleShowAllAddButtons();
				return true;
			}
		}

		// Ctrl+A for select all
		if (net.minecraft.client.gui.screens.Screen.hasControlDown() && actionListPanel != null) {
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_A) {
				actionListPanel.selectAll();
				return true;
			}
		}

		// Ctrl+C/X/V for action clipboard
		if (net.minecraft.client.gui.screens.Screen.hasControlDown() && actionListPanel != null) {
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_C) {
				if (actionListPanel.copySelected()) return true;
			}
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_X) {
				if (actionListPanel.cutSelected()) {
					if (actionEditorPanel != null) actionEditorPanel.clearAction();
					resetSelectedPhasePreview(false);
					return true;
				}
			}
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_V) {
				if (actionListPanel.pasteAfterSelected()) {
					resetSelectedPhasePreview(false);
					return true;
				}
			}
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
				if (actionListPanel.moveSelectedUp()) {
					resetSelectedPhasePreview(false);
					return true;
				}
			}
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
				if (actionListPanel.moveSelectedDown()) {
					resetSelectedPhasePreview(false);
					return true;
				}
			}
		}

		// Let action list handle key presses (e.g. rename mode)
		if (actionListPanel != null && actionListPanel.keyPressed(keyCode, scanCode, modifiers)) {
			return true;
		}

		// Delete / Backspace = delete selected action
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE
				|| keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
			if (actionListPanel != null && actionListPanel.deleteSelected()) {
				if (actionEditorPanel != null) actionEditorPanel.clearAction();
				if (autoReplay) replaySelectedPhase();
				return true;
			}
		}



		// In captured perspective mode, suppress keys used for camera movement
		// WASD, Space, Shift are consumed by perspective camera in tick()
		if (viewport.isPerspectiveCaptured()) {
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_W
					|| keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_A
					|| keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_S
					|| keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_D
					|| keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE
					|| keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) {
				return true; // consumed by perspective camera
			}
		}

		// Space = play/pause (orthographic mode only now)
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) {
			scene.togglePlayPause();
			return true;
		}
		// R = reset
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
			resetSelectedPhasePreview(false);
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
	public boolean charTyped(char codePoint, int modifiers) {
		if (actionListPanel != null && actionListPanel.charTyped(codePoint, modifiers)) {
			return true;
		}
		return super.charTyped(codePoint, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		// Pause in singleplayer like vanilla book screen.
		// Minecraft engine automatically skips pausing in multiplayer/LAN.
		return true;
	}

	/**
	 * Auto-save when the editor screen is closed.
	 * This ensures edits are persisted even if the user forgets to click Apply.
	 */
	@Override
	public void removed() {
		super.removed();
		// Restore cursor if hidden during perspective capture
		if (viewport.isPerspectiveCaptured()) {
			viewport.setPerspectiveCaptured(false);
			org.lwjgl.glfw.GLFW.glfwSetInputMode(
					Minecraft.getInstance().getWindow().getWindow(),
					org.lwjgl.glfw.GLFW.GLFW_CURSOR,
					org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL);
		}
		saveCurrentDefinition();
		// Save dock layout
		if (dockLayout != null) {
			DockSerializer.saveLayout(dockLayout.getRoot());
		}
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
