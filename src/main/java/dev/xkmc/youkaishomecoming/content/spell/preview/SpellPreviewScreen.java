package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.spell.action.FireDanmakuAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.definition.GroupRotation;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.OriginConfig;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
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
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
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

	// Controllers (extracted logic)
	private final SpellEditorController spellController;
	private final PhaseEditorController phaseController;

	// Dock layout system
	private DockLayout dockLayout;
	private ViewportDockPanel viewportPanel;
	private ActionListDockPanel actionListDockPanel;
	private EditorDockPanel editorDockPanel;
	private ControlsDockPanel controlsDockPanel;
	private StatusDockPanel statusDockPanel;
	private VariablesDockPanel variablesDockPanel;
	private PerfDockPanel perfDockPanel;
	private HelpDockPanel helpDockPanel;
	private RawJsonDockPanel rawJsonDockPanel;
	private MagicCircleDockPanel magicCircleDockPanel;
	private RawJsonDockPanel.ContentMode rawJsonContext = RawJsonDockPanel.ContentMode.SPELL;

	// Editor panels (direct references for hotkey access)
	private ActionListPanel actionListPanel;
	private ActionEditorPanel actionEditorPanel;
	private boolean editorVisible = true;
	private ActionListPanel.AddTarget pendingAddTarget;

	/** Persists across editor open/close within the same game session. */
	private static boolean autoReplay = true;
	private com.google.gson.JsonObject pendingDockLayout;

	public SpellPreviewScreen(SpellDefinition definition) {
		this(definition, SpellEditorController.isDraftDefinition(definition));
	}

	private SpellPreviewScreen(SpellDefinition definition, boolean draftMode) {
		super(Component.literal(draftMode ? SpellEditorLocalization.t("Spell Editor") : SpellEditorLocalization.t("Spell Preview") + ": " + definition.id));
		this.definition = definition;
		this.scene = new VirtualSpellScene(definition);
		this.scene.setOnStateChanged(this::syncSceneState);
		this.viewport = new OrthographicViewport();
		this.spellController = new SpellEditorController(definition, draftMode, scene, this::rebuildScreen);
		this.phaseController = new PhaseEditorController(definition);
		if (!phaseController.getPhaseList().isEmpty()) {
			this.scene.resetToPhase(phaseController.getPhaseList().get(0));
		}
		// Create persistent dock panels
		this.viewportPanel = new ViewportDockPanel(viewport, scene);
		this.viewportPanel.setGroupTransformCallbacks(
				this::onGroupOffsetDragged,
				this::onGroupAngleDragged,
				this::onGroupDragBegin,
				this::onGroupDeselect,
				this::onClickSelectDanmaku
		);
		this.viewportPanel.setOnRotationSpeedChanged(this::onRotationSpeedDragged);
		this.viewportPanel.setEditBoxFocusedSupplier(this::isAnyEditBoxFocused);
		this.statusDockPanel = new StatusDockPanel(scene, viewport);
		this.variablesDockPanel = new VariablesDockPanel(scene);
		this.helpDockPanel = new HelpDockPanel();
	}

	public static SpellPreviewScreen createDraftEditor() {
		return new SpellPreviewScreen(SpellEditorController.createDraftDefinition(), true);
	}

	@Override
	protected void init() {
		super.init();
		boolean fullEdit = !isDraftMode();
		int bx = 4;
		int by = 2;
		for (ViewAngle angle : ViewAngle.values()) {
			bx = addTopBarButton(bx, by, SpellEditorLocalization.t(angle.getLabel()), 50, btn -> {
				viewport.setPerspectiveMode(false);
				viewport.setViewAngle(angle);
			}, fullEdit);
		}
		// Perspective / Orthographic toggle
		String perspLabel = SpellEditorLocalization.t(viewport.isPerspectiveMode() ? "Ortho" : "Persp");
		bx = addTopBarButton(bx, by, perspLabel, 40, btn -> {
			boolean newPersp = !viewport.isPerspectiveMode();
			viewport.setPerspectiveMode(newPersp);
			if (newPersp) {
				// Set camera to dummy target position
				viewport.setCameraToTarget(scene.getTargetPos());
			}
			rebuildScreen();
		}, fullEdit);
		// Bind target toggle (only in perspective mode)
		if (viewport.isPerspectiveMode()) {
			String bindLabel = SpellEditorLocalization.t(viewport.isTargetBoundToCamera() ? "Unbind" : "BindTgt");
			bx = addTopBarButton(bx, by, bindLabel, 48, btn -> {
				viewport.setTargetBoundToCamera(!viewport.isTargetBoundToCamera());
				rebuildScreen();
			}, fullEdit);
		}
		// Toggle editor button
		bx += 10;
		bx = addTopBarButton(bx, by, SpellEditorLocalization.t(editorVisible ? "Editor <<" : "Editor >>"), 60, btn -> {
			editorVisible = !editorVisible;
			rebuildScreen();
		}, fullEdit);
		// Apply button: re-apply edited spell to all entities using it
		bx = addTopBarButton(bx, by, SpellEditorLocalization.t("Apply"), 40, btn -> applyToEntities(), fullEdit);
		// Export button: save spell definition as JSON datapack file
		bx = addTopBarButton(bx, by, SpellEditorLocalization.t("Export"), 46, btn -> exportToDatapack(), fullEdit);
		// Reset button: restore to original (built-in) or open-snapshot (custom)
		bx = addTopBarButton(bx, by, SpellEditorLocalization.t("Reset"), 40, btn -> resetToDefault(), fullEdit);
		// Auto Replay toggle
		bx = addTopBarButton(bx, by, SpellEditorLocalization.t(autoReplay ? "Auto:ON" : "Auto:OFF"), 52, btn -> {
			autoReplay = !autoReplay;
			rebuildScreen();
		}, fullEdit);
		// Help button — toggles HelpDockPanel as a docked tab
		bx = addTopBarButton(bx, by, SpellEditorLocalization.t("Help"), 32, btn -> {
			toggleHelpPanel();
		}, fullEdit);
		bx = addTopBarButton(bx, by, SpellEditorLocalization.modeButtonLabel(), 34, btn -> {
			SpellEditorLocalization.toggle();
			rebuildScreen();
		}, fullEdit);
		// Collapse All / Expand All
		bx = addTopBarButton(bx, by, SpellEditorLocalization.t("\u25B6All"), 34, btn -> {
			if (actionListPanel != null) actionListPanel.collapseAll();
		}, fullEdit);
		bx = addTopBarButton(bx, by, SpellEditorLocalization.t("\u25BCAll"), 34, btn -> {
			if (actionListPanel != null) actionListPanel.expandAll();
		}, fullEdit);
		// Toggle show all add-buttons
		if (actionListPanel != null) {
			String addLabel = SpellEditorLocalization.t(actionListPanel.isShowAllAddButtons() ? "[+]:All" : "[+]:Sel");
			bx = addTopBarButton(bx, by, addLabel, 42, btn -> {
				actionListPanel.toggleShowAllAddButtons();
				rebuildScreen();
			}, fullEdit);
		}
		// Reset Layout button
		addTopBarButton(bx, by, SpellEditorLocalization.t("RstLayout"), 56, btn -> {
			DockSerializer.deleteLayout();
			rebuildScreen(false);
		}, fullEdit);

		// --- Create editor panels ---
		actionListPanel = new ActionListPanel(
				(action, path) -> {
					setActionEditorAction(action, path.leafIndex());
					// Highlight the selected action's danmaku in the viewport
					scene.getHolder().setHighlightedActionIndex(path.leafIndex());
					// Update rotation gizmo state based on selected action
					updateRotationGizmoForAction(action);
				},
				this::onRequestAddAction,
				this::onActionListReordered
		);
		actionListPanel.loadCustomNames(definition.customNames);

		actionEditorPanel = new ActionEditorPanel(
				this::addRenderableWidget,
				this::removeWidget,
				this::onActionEdited,
				this::onDeleteAction
		);
		actionEditorPanel.setPhaseOptions(() -> List.copyOf(phaseController.getPhaseList()), phaseController::getPhaseOptionLabel);
		actionEditorPanel.setSpellOptions(spellController::getSpellOptions, spellController::getSpellOptionLabel);
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
		rawJsonDockPanel = new RawJsonDockPanel(
				this::currentDefinitionForRawJson,
				phaseController::getSelectedPhaseId,
				() -> actionListPanel == null ? null : actionListPanel.getSelectedPath(),
				this::onRawJsonDefinitionEdited
		);
		rawJsonDockPanel.setWidgetCallbacks(this::addRenderableWidget, this::removeWidget);
		magicCircleDockPanel = new MagicCircleDockPanel(viewport);
		magicCircleDockPanel.setWidgetCallbacks(this::addRenderableWidget, this::removeWidget);
		viewportPanel.setMagicCircleEditor(magicCircleDockPanel);
		rawJsonDockPanel.setMagicCircleContext(
				() -> rawJsonContext == RawJsonDockPanel.ContentMode.MAGIC_CIRCLE,
				() -> magicCircleDockPanel == null ? "" : magicCircleDockPanel.encodeRawJson(),
				text -> {
					if (magicCircleDockPanel != null) {
						magicCircleDockPanel.applyRawJson(text);
					}
				}
		);
		controlsDockPanel = new ControlsDockPanel(
				scene, viewport, this::rebuildScreen, () -> resetSelectedPhasePreview(false),
				spellController::getSpellOptions, spellController::getCurrentSpellSelectionId,
				spellController::getCurrentSpellButtonLabel, spellController::getSpellOptionLabel,
				spellController::switchSelectedSpell, spellController::enterDraftSpellEditor,
				spellController::deleteSelectedSpell, spellController::canDeleteSelectedSpell,
				spellController::isDraftMode, spellController::nameCurrentDraftSpell, this::cyclePhase,
				phaseController::getSelectedPhaseDisplayName, this::renameSelectedPhase, this::addPhase,
				this::deleteSelectedPhase, phaseController::canDeleteSelectedPhase);
		controlsDockPanel.setWidgetCallbacks(w -> this.addRenderableWidget(w), this::removeWidget);
		perfDockPanel = new PerfDockPanel(scene);

		// --- Build dock layout tree (load from config or use default) ---
		java.util.Map<String, DockPanel> panelMap = new java.util.LinkedHashMap<>();
		panelMap.put(viewportPanel.dockId(), viewportPanel);
		panelMap.put(actionListDockPanel.dockId(), actionListDockPanel);
		panelMap.put(editorDockPanel.dockId(), editorDockPanel);
		panelMap.put(rawJsonDockPanel.dockId(), rawJsonDockPanel);
		panelMap.put(magicCircleDockPanel.dockId(), magicCircleDockPanel);
		panelMap.put(controlsDockPanel.dockId(), controlsDockPanel);
		panelMap.put(statusDockPanel.dockId(), statusDockPanel);
		panelMap.put(variablesDockPanel.dockId(), variablesDockPanel);
		panelMap.put(perfDockPanel.dockId(), perfDockPanel);
		panelMap.put(helpDockPanel.dockId(), helpDockPanel);

		com.google.gson.JsonObject layoutSnapshot = pendingDockLayout;
		pendingDockLayout = null;
		if (layoutSnapshot != null) {
			dockLayout = new DockLayout(DockSerializer.loadLayout(layoutSnapshot, panelMap, SpellPreviewScreen::buildDefaultLayout));
		} else {
			boolean hadSavedLayout = DockSerializer.hasSavedLayout();
			boolean savedLayoutHasStatusPanel = DockSerializer.savedLayoutContainsPanel(statusDockPanel.dockId());
			boolean savedLayoutHasVariablesPanel = DockSerializer.savedLayoutContainsPanel(variablesDockPanel.dockId());
			boolean savedLayoutHasRawJsonPanel = DockSerializer.savedLayoutContainsPanel(rawJsonDockPanel.dockId());
			boolean savedLayoutHasMagicCirclePanel = DockSerializer.savedLayoutContainsPanel(magicCircleDockPanel.dockId());
			DockNode root = DockSerializer.loadLayout(panelMap, SpellPreviewScreen::buildDefaultLayout);
			dockLayout = new DockLayout(root);
			if (hadSavedLayout && !savedLayoutHasStatusPanel) {
				relocateMissingStatusPanel();
			}
			if (hadSavedLayout && !savedLayoutHasVariablesPanel) {
				relocateMissingVariablesPanel();
			}
			if (hadSavedLayout && (!savedLayoutHasRawJsonPanel || rawJsonSharesEditorGroup())) {
				relocateMissingRawJsonPanel();
			}
			if (hadSavedLayout && !savedLayoutHasMagicCirclePanel) {
				relocateMissingMagicCirclePanel();
			}
		}
		dockLayout.layout(0, TOP_BAR_HEIGHT, width, height - TOP_BAR_HEIGHT);
		// Set active group to the one containing the viewport
		DockGroup vpGroup = dockLayout.findGroupContaining(viewportPanel);
		if (vpGroup != null) dockLayout.setActiveGroup(vpGroup);
		syncEditorDockWidgetVisibility();

		controlsDockPanel.buildButtons();
		updateActionListPhase();
	}

	private int addTopBarButton(int bx, int by, String label, int minWidth, Button.OnPress onPress, boolean active) {
		int bw = Math.max(minWidth, font.width(label) + 12);
		Button button = Button.builder(Component.literal(label), onPress)
				.bounds(bx, by, bw, BUTTON_HEIGHT).build();
		button.active = active;
		addRenderableWidget(button);
		return bx + bw + BUTTON_SPACING;
	}

	/**
	 * 构建默认布局树。用于首次打开或布局文件损坏时的回退。
	 */
	static DockNode buildDefaultLayout(java.util.Map<String, DockPanel> panelMap) {
		DockPanel viewport = panelMap.get("viewport");
		DockPanel actions = panelMap.get("actions");
		DockPanel properties = panelMap.get("properties");
		DockPanel rawJson = panelMap.get("raw_json");
		DockPanel magicCircle = panelMap.get("magic_circle");
		DockPanel controls = panelMap.get("controls");
		DockPanel status = panelMap.get("status");
		DockPanel variables = panelMap.get("variables");
		DockPanel perf = panelMap.get("perf");

		DockGroup viewportGroup = new DockGroup(viewport);
		DockGroup actionListGroup = new DockGroup(actions);
		DockGroup editorGroup = new DockGroup(properties);
		DockGroup controlsGroup = new DockGroup(controls, perf);
		DockGroup statusGroup = new DockGroup(status, variables, rawJson, magicCircle);
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

	private void relocateMissingVariablesPanel() {
		if (dockLayout == null || variablesDockPanel == null || statusDockPanel == null) {
			return;
		}
		DockGroup statusGroup = dockLayout.findGroupContaining(statusDockPanel);
		if (statusGroup == null) {
			return;
		}
		DockGroup currentGroup = dockLayout.findGroupContaining(variablesDockPanel);
		if (currentGroup == statusGroup) {
			return;
		}
		if (currentGroup != null) {
			currentGroup.removePanel(variablesDockPanel);
		}
		statusGroup.addPanel(variablesDockPanel);
	}

	private void relocateMissingRawJsonPanel() {
		if (dockLayout == null || rawJsonDockPanel == null || statusDockPanel == null) {
			return;
		}
		DockGroup statusGroup = dockLayout.findGroupContaining(statusDockPanel);
		if (statusGroup == null) {
			return;
		}
		DockGroup currentGroup = dockLayout.findGroupContaining(rawJsonDockPanel);
		if (currentGroup == statusGroup) {
			return;
		}
		if (currentGroup != null) {
			currentGroup.removePanel(rawJsonDockPanel);
		}
		statusGroup.addPanel(rawJsonDockPanel);
	}

	private void relocateMissingMagicCirclePanel() {
		if (dockLayout == null || magicCircleDockPanel == null || statusDockPanel == null) {
			return;
		}
		DockGroup statusGroup = dockLayout.findGroupContaining(statusDockPanel);
		if (statusGroup == null) {
			return;
		}
		DockGroup currentGroup = dockLayout.findGroupContaining(magicCircleDockPanel);
		if (currentGroup == statusGroup) {
			return;
		}
		if (currentGroup != null) {
			currentGroup.removePanel(magicCircleDockPanel);
		}
		statusGroup.addPanel(magicCircleDockPanel);
	}

	private boolean rawJsonSharesEditorGroup() {
		if (dockLayout == null || rawJsonDockPanel == null || editorDockPanel == null) {
			return false;
		}
		DockGroup rawGroup = dockLayout.findGroupContaining(rawJsonDockPanel);
		return rawGroup != null && rawGroup == dockLayout.findGroupContaining(editorDockPanel);
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

	private SpellDefinition currentDefinitionForRawJson() {
		syncCustomNamesToDefinition();
		return definition;
	}

	private void onRawJsonDefinitionEdited(SpellDefinition newDefinition) {
		ResourceLocation selectedPhase = phaseController.getSelectedPhaseId();
		ActionListPanel.ActionPath selectedPath = actionListPanel == null ? null : actionListPanel.getSelectedPath();
		boolean wasPlaying = scene.isPlaying();

		this.definition = newDefinition;
		spellController.setDefinition(newDefinition);
		spellController.setDraftMode(SpellEditorController.isDraftDefinition(newDefinition));
		phaseController.setDefinition(newDefinition);
		phaseController.reloadPhaseList();
		selectPhaseAfterRawJsonApply(selectedPhase);
		if (actionListPanel != null) {
			actionListPanel.loadCustomNames(newDefinition.customNames);
		}
		updateActionListPhase();
		refreshPhaseControls();
		scene.switchSpellDefinition(newDefinition, true);
		resetSelectedPhasePreview(wasPlaying || autoReplay);
		if (selectedPath == null || actionListPanel == null || !actionListPanel.selectPath(selectedPath)) {
			clearActionSelection();
		}
	}

	private void selectPhaseAfterRawJsonApply(ResourceLocation preferredPhase) {
		List<ResourceLocation> phases = phaseController.getPhaseList();
		if (phases.isEmpty()) {
			phaseController.setSelectedPhaseIndex(0);
			return;
		}
		int idx = preferredPhase == null ? -1 : phases.indexOf(preferredPhase);
		if (idx < 0) {
			idx = phases.indexOf(definition.entryPhase);
		}
		phaseController.setSelectedPhaseIndex(idx >= 0 ? idx : 0);
	}

	private void setActionEditorAction(SpellAction action, int index) {
		if (actionEditorPanel == null) {
			return;
		}
		actionEditorPanel.setAction(action, index);
		syncEditorDockWidgetVisibility();
	}

	private void syncEditorDockWidgetVisibility() {
		if (dockLayout == null) {
			return;
		}
		if (editorDockPanel != null && actionEditorPanel != null) {
			DockGroup editorGroup = dockLayout.findGroupContaining(editorDockPanel);
			actionEditorPanel.setAllWidgetsVisible(editorGroup != null && editorGroup.getActivePanel() == editorDockPanel);
		}
		if (rawJsonDockPanel != null) {
			DockGroup rawJsonGroup = dockLayout.findGroupContaining(rawJsonDockPanel);
			boolean rawJsonActive = rawJsonGroup != null && rawJsonGroup.getActivePanel() == rawJsonDockPanel;
			DockGroup magicCircleGroup = magicCircleDockPanel == null ? null : dockLayout.findGroupContaining(magicCircleDockPanel);
			boolean magicCircleActive = magicCircleGroup != null && magicCircleGroup.getActivePanel() == magicCircleDockPanel;
			if (magicCircleActive) {
				rawJsonContext = RawJsonDockPanel.ContentMode.MAGIC_CIRCLE;
			} else if (!rawJsonActive) {
				rawJsonContext = RawJsonDockPanel.ContentMode.SPELL;
			}
			rawJsonDockPanel.setEditorActive(rawJsonActive);
		}
		if (magicCircleDockPanel != null) {
			DockGroup magicCircleGroup = dockLayout.findGroupContaining(magicCircleDockPanel);
			boolean magicCircleActive = magicCircleGroup != null && magicCircleGroup.getActivePanel() == magicCircleDockPanel;
			DockGroup rawJsonGroup = rawJsonDockPanel == null ? null : dockLayout.findGroupContaining(rawJsonDockPanel);
			boolean rawJsonActive = rawJsonGroup != null && rawJsonGroup.getActivePanel() == rawJsonDockPanel;
			magicCircleDockPanel.setEditorActive(magicCircleActive);
			magicCircleDockPanel.setPreviewActive(magicCircleActive ||
					(rawJsonActive && rawJsonContext == RawJsonDockPanel.ContentMode.MAGIC_CIRCLE));
		}
	}

	private boolean isEditorDockActive() {
		if (editorDockPanel == null || dockLayout == null) {
			return false;
		}
		DockGroup group = dockLayout.findGroupContaining(editorDockPanel);
		return group != null && group.getActivePanel() == editorDockPanel;
	}

	/** Transient edit during a drag — replaces selected action without pushing undo. */
	private void onActionEditedTransient(SpellAction newAction) {
		if (actionListPanel != null) {
			actionListPanel.replaceSelectedActionWithoutUndo(newAction);
			if (autoReplay) replaySelectedPhase();
		}
	}

	// --- Group transform callbacks (viewport drag interaction) ---

	/** Called by viewport when a drag gesture begins — push a single undo snapshot for the whole drag. */
	private void onGroupDragBegin() {
		if (actionListPanel != null) actionListPanel.pushUndoSnapshot();
	}

	private void onGroupOffsetDragged(Vec3 delta) {
		if (delta.lengthSqr() < 1e-8) return;
		if (actionEditorPanel == null || actionEditorPanel.getCurrentAction() == null) return;
		SpellAction action = actionEditorPanel.getCurrentAction();
		if (action instanceof FireDanmakuAction fda) {
			var origin = fda.origin();
			// Only edit axes whose offset is a plain constant — non-constant expressions
			// (random/expression/etc.) would be silently flattened to constants, losing the user's intent.
			NumberProvider newX = bumpConstant(origin.offsetX(), delta.x);
			NumberProvider newY = bumpConstant(origin.offsetY(), delta.y);
			NumberProvider newZ = bumpConstant(origin.offsetZ(), delta.z);
			if (newX == origin.offsetX() && newY == origin.offsetY() && newZ == origin.offsetZ()) {
				return; // nothing constant to bump
			}
			var newOrigin = new OriginConfig(origin.mode(), newX, newY, newZ, origin.rotation());
			var newAction = fda.withOrigin(newOrigin);
			onActionEditedTransient(newAction);
			if (actionEditorPanel != null) {
				setActionEditorAction(newAction, actionEditorPanel.getActionIndex());
			}
		}
	}

	private void onGroupAngleDragged(double angleDelta) {
		if (actionEditorPanel == null || actionEditorPanel.getCurrentAction() == null) return;
		SpellAction action = actionEditorPanel.getCurrentAction();
		if (action instanceof FireDanmakuAction fda) {
			// Determine which axis to rotate based on viewport's rotate mode
			int axis = viewportPanel != null ? viewportPanel.getRotateAxis() : 1;
			if (axis < 0) axis = 1; // default Y if not in rotate mode

			GroupRotation current = fda.groupRotation().orElse(
					new GroupRotation(NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0)));
			NumberProvider newX = current.rotX(), newY = current.rotY(), newZ = current.rotZ();
			NumberProvider bumped;
			switch (axis) {
				case 0 -> {
					bumped = bumpConstant(current.rotX(), angleDelta);
					if (bumped == current.rotX()) return; // non-constant, refuse to clobber
					newX = bumped;
				}
				case 1 -> {
					bumped = bumpConstant(current.rotY(), angleDelta);
					if (bumped == current.rotY()) return;
					newY = bumped;
				}
				case 2 -> {
					bumped = bumpConstant(current.rotZ(), angleDelta);
					if (bumped == current.rotZ()) return;
					newZ = bumped;
				}
			}
			var newGr = new GroupRotation(newX, newY, newZ);
			var newAction = fda.withGroupRotation(Optional.of(newGr));
			onActionEditedTransient(newAction);
			if (actionEditorPanel != null) {
				setActionEditorAction(newAction, actionEditorPanel.getActionIndex());
			}
		}
	}

	/**
	 * Returns a Constant NumberProvider with value bumped by delta, or the original if
	 * the input isn't a Constant (signaling "non-constant — don't clobber").
	 */
	private static NumberProvider bumpConstant(NumberProvider p, double delta) {
		if (p instanceof dev.xkmc.youkaishomecoming.content.spell.definition.NumberProviders.Constant c) {
			return NumberProvider.constant(c.value() + delta);
		}
		return p;
	}

	private void onGroupDeselect() {
		clearActionSelection();
	}

	/**
	 * Update the rotation gizmo state on the viewport based on the selected action.
	 */
	private void updateRotationGizmoForAction(@Nullable SpellAction action) {
		if (viewportPanel != null) {
			viewportPanel.setRotationGizmo(false, 0, 0, 0);
		}
	}

	/**
	 * Called when the user drags the rotation gizmo to modify degrees_per_tick.
	 * Currently a no-op since space rotation system is not yet implemented.
	 */
	private void onRotationSpeedDragged(double speedDelta) {
		// No-op: space rotation mover not yet available
	}

	private void onClickSelectDanmaku(int actionIndex) {
		if (actionIndex < 0) return;
		scene.getHolder().setHighlightedActionIndex(actionIndex);
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
			if (actionEditorPanel != null) actionEditorPanel.clearScrollState();
			if (autoReplay) replaySelectedPhase();
		}
	}

	private void onDeleteAction() {
		if (actionListPanel != null && actionListPanel.deleteSelected()) {
			if (actionEditorPanel != null) actionEditorPanel.clearScrollState();
			clearActionSelection();
			if (autoReplay) replaySelectedPhase();
		}
	}

	/**
	 * Re-apply the edited spell definition to all entities currently using it.
	 * Sends the full edited definition to the server before reapplying it.
	 */
	private void applyToEntities() {
		if (isDraftMode()) {
			return;
		}
		syncCustomNamesToDefinition();
		SpellRegistry.register(definition);
		SpellEditorNetworkClient.saveAndReapply(definition);
	}

	/**
	 * Export the current spell definition to the server global spell directory.
	 * Exported spells are loaded for every save on the same game/server instance.
	 */
	private void exportToDatapack() {
		syncCustomNamesToDefinition();
		spellController.exportToDatapack();
	}

	/**
	 * Reset the spell to its original state.
	 * Built-in spells: restored from SpellRegistry defaults (code-defined).
	 * Custom spells: restored from the snapshot taken when the editor was opened.
	 */
	private void resetToDefault() {
		spellController.resetToDefault();
		// Reload phase list after reset
		phaseController.reloadPhaseList();

		// Refresh UI
		clearActionSelection();
		if (actionListPanel != null) {
			actionListPanel.loadCustomNames(definition.customNames);
		}
		updateActionListPhase();
		refreshPhaseControls();
		replaySelectedPhase();
	}

	private void updateActionListPhase() {
		if (actionListPanel == null) return;
		ResourceLocation phaseId = phaseController.getSelectedPhaseId();
		if (phaseId == null) {
			actionListPanel.setPhase(null);
			return;
		}
		PhaseDefinition phase = definition.phases.get(phaseId);
		actionListPanel.setPhase(phase);
	}

	private ResourceLocation getSelectedPhaseId() {
		return phaseController.getSelectedPhaseId();
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

	/**
	 * Called when the action list is reordered (drag-drop or move up/down from ActionListPanel).
	 * Clears scroll state since action indices have shifted, then replays.
	 */
	private void onActionListReordered() {
		if (actionEditorPanel != null) actionEditorPanel.clearScrollState();
		replaySelectedPhase();
	}

	private void cyclePhase(int delta) {
		if (phaseController.getPhaseList().isEmpty()) return;
		boolean wasPlaying = scene.isPlaying();
		phaseController.cyclePhase(delta);
		resetSelectedPhasePreview(wasPlaying);
		clearActionSelection();
		updateActionListPhase();
		refreshPhaseControls();
	}

	private void addPhase() {
		phaseController.addPhase();
		resetSelectedPhasePreview(autoReplay);
		clearActionSelection();
		updateActionListPhase();
		refreshPhaseControls();
		refreshActionEditor();
	}

	private boolean canDeleteSelectedPhase() {
		return phaseController.canDeleteSelectedPhase();
	}

	private void deleteSelectedPhase() {
		ResourceLocation removedPhaseId = phaseController.deleteSelectedPhase();
		if (removedPhaseId == null) {
			return;
		}
		boolean wasPlaying = scene.isPlaying();
		clearActionSelection();
		updateActionListPhase();
		refreshPhaseControls();
		refreshActionEditor();
		resetSelectedPhasePreview(wasPlaying || autoReplay);
	}

	/** Clear both the action editor focus and the viewport highlight — used whenever the active
	 *  phase changes, since action indices are phase-scoped and would otherwise dangle. */
	private void clearActionSelection() {
		scene.getHolder().setHighlightedActionIndex(-1);
		if (actionEditorPanel != null) {
			actionEditorPanel.clearAction();
		}
		// Clear rotation gizmo
		updateRotationGizmoForAction(null);
	}

	private void renameSelectedPhase(String name) {
		phaseController.renameSelectedPhase(name);
		// Sync custom name changes to actionListPanel
		ResourceLocation phaseId = phaseController.getSelectedPhaseId();
		if (phaseId != null && actionListPanel != null) {
			String key = PhaseEditorController.getPhaseNameKey(phaseId);
			String legacyKey = PhaseEditorController.getLegacyPhaseNameKey(phaseId);
			String custom = phaseController.getStoredPhaseCustomName(phaseId);
			actionListPanel.setCustomName(legacyKey, null);
			actionListPanel.setCustomName(key, custom);
		}
		refreshPhaseControls();
		refreshActionEditor();
	}

	private String getSelectedPhaseDisplayName() {
		return phaseController.getSelectedPhaseDisplayName();
	}

	private List<ResourceLocation> getSpellOptions() {
		return spellController.getSpellOptions();
	}

	private String getSpellOptionLabel(ResourceLocation spellId) {
		return spellController.getSpellOptionLabel(spellId);
	}

	private String getCurrentSpellButtonLabel() {
		return spellController.getCurrentSpellButtonLabel();
	}

	private ResourceLocation getCurrentSpellSelectionId() {
		return spellController.getCurrentSpellSelectionId();
	}

	private void switchSelectedSpell(ResourceLocation spellId) {
		spellController.switchSelectedSpell(spellId);
	}

	private boolean canDeleteSelectedSpell() {
		return spellController.canDeleteSelectedSpell();
	}

	private void enterDraftSpellEditor() {
		spellController.enterDraftSpellEditor();
	}

	private void nameCurrentDraftSpell(String name) {
		spellController.nameCurrentDraftSpell(name);
	}

	private void deleteSelectedSpell() {
		spellController.deleteSelectedSpell();
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

	private static String formatResourceId(ResourceLocation id) {
		return SpellEditorController.formatResourceId(id);
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
		spellController.saveCurrentDefinition();
	}

	private boolean isDraftMode() {
		return spellController.isDraftMode();
	}

	private void displayEditorMessage(String message) {
		var mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(Component.literal(message), true);
		}
	}

	private void switchToDefinition(SpellDefinition definition) {
		if (!spellController.isSkipSaveOnNextDefinitionSwitch()) {
			saveCurrentDefinition();
		}
		spellController.clearSkipFlag();
		boolean oldDraftMode = spellController.isDraftMode();
		this.definition = definition;
		spellController.setDefinition(definition);
		spellController.setDraftMode(SpellEditorController.isDraftDefinition(definition));
		spellController.rememberOpenSnapshot(definition);
		phaseController.setDefinition(definition);
		phaseController.reloadPhaseList();
		if (oldDraftMode != spellController.isDraftMode()) {
			rebuildScreen();
			return;
		}
		ResourceLocation currentPhase = scene.getCurrentPhaseId();
		int idx = phaseController.getPhaseList().indexOf(currentPhase);
		phaseController.setSelectedPhaseIndex(idx >= 0 ? idx : 0);
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
		int idx = phaseController.getPhaseList().indexOf(currentPhase);
		if (idx >= 0 && idx != phaseController.getSelectedPhaseIndex()) {
			phaseController.setSelectedPhaseIndex(idx);
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
			syncEditorDockWidgetVisibility();
			dockLayout.render(guiGraphics, mouseX, mouseY, partialTick);
		}

		// Screen widgets (top bar buttons, control panel buttons)
		super.render(guiGraphics, mouseX, mouseY, partialTick);

		// Dropdown/completion overlay on top of everything
		if (dockLayout != null) {
			dockLayout.renderOverlay(guiGraphics, mouseX, mouseY);
		}

		// Spell name on top bar
		String spellName = isDraftMode() ? SpellEditorLocalization.t("New Spell") : definition.id.toString();
		int nameX = width - font.width(spellName) - 4;
		guiGraphics.drawString(font, spellName, nameX, 5, 0xFFAAAAAA, false);

	}


	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// Editor dropdown/completion may extend beyond panel bounds — check first
		if (isEditorDockActive() && actionEditorPanel != null && actionEditorPanel.mouseClicked(mouseX, mouseY, button)) {
			// 同步 activeGroup 到编辑器所在的 Group
			if (dockLayout != null) {
				DockGroup eg = dockLayout.findGroupContaining(editorDockPanel);
				if (eg != null) dockLayout.setActiveGroup(eg);
			}
			syncEditorDockWidgetVisibility();
			return true;
		}
		// Dock layout dispatches to panels (also updates activeGroup)
		if (dockLayout != null && dockLayout.mouseClicked(mouseX, mouseY, button)) {
			syncEditorDockWidgetVisibility();
			// When clicking outside the properties panel (e.g. viewport), clear editbox focus
			// to remove the highlight, but keep the properties panel itself open.
			DockGroup editorGroup = dockLayout.findGroupContaining(editorDockPanel);
			if (dockLayout.getActiveGroup() != editorGroup) {
				if (actionEditorPanel != null) {
					actionEditorPanel.unfocusAllEditBoxes();
				}
				setFocused(null);
			}
			return true;
		}
		syncEditorDockWidgetVisibility();
		// Screen widgets (top bar buttons, control panel buttons)
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (dockLayout != null && dockLayout.mouseReleased(mouseX, mouseY, button)) {
			syncEditorDockWidgetVisibility();
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
		return getFocused() instanceof net.minecraft.client.gui.components.EditBox ||
				getFocused() instanceof net.minecraft.client.gui.components.MultiLineEditBox;
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
		if (isEditorDockActive() && actionEditorPanel != null && actionEditorPanel.keyPressed(keyCode, scanCode, modifiers)) {
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
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB && isEditorDockActive() && actionEditorPanel != null) {
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
					if (actionEditorPanel != null) actionEditorPanel.clearScrollState();
					resetSelectedPhasePreview(false);
					return true;
				}
			}
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
				if (actionListPanel.moveSelectedUp()) {
					if (actionEditorPanel != null) actionEditorPanel.clearScrollState();
					resetSelectedPhasePreview(false);
					return true;
				}
			}
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
				if (actionListPanel.moveSelectedDown()) {
					if (actionEditorPanel != null) actionEditorPanel.clearScrollState();
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
				if (actionEditorPanel != null) {
					actionEditorPanel.clearScrollState();
					actionEditorPanel.clearAction();
				}
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
		// R = reset (or enter rotate mode if group is highlighted)
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
			if (viewportPanel != null && viewportPanel.keyPressed(keyCode, 0, 0)) {
				return true;
			}
			resetSelectedPhasePreview(false);
			return true;
		}
		// X/Y/Z axis selection in rotate mode
		if (viewportPanel != null && viewportPanel.isRotateMode()) {
			if (viewportPanel.keyPressed(keyCode, scanCode, modifiers)) {
				return true;
			}
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
		if (isAnyEditBoxFocused()) {
			return super.charTyped(codePoint, modifiers);
		}
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
		reportRawJsonDraftOnClose();
		saveCurrentDefinition();
		// Save dock layout
		if (dockLayout != null) {
			DockSerializer.saveLayout(dockLayout.getRoot());
		}
	}

	private void reportRawJsonDraftOnClose() {
		if (rawJsonDockPanel == null || !rawJsonDockPanel.hasDirtyDraft()) {
			return;
		}
		String message = "[YH] Raw JSON was not applied";
		String reason = rawJsonDockPanel.dirtyDraftMessage();
		if (reason != null && !reason.isBlank()) {
			message += ": " + reason;
		}
		Path draftPath = rawJsonDockPanel.dirtyDraftPath();
		if (draftPath != null) {
			message += " (draft: " + draftPath + ")";
		}
		var mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(Component.literal(message), false);
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
