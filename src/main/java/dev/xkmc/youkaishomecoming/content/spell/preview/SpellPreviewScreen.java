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
import dev.xkmc.youkaishomecoming.content.spell.preview.dock.*;
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
	private static final int TOP_BAR_HEIGHT = 20;
	private static final int BUTTON_HEIGHT = 16;
	private static final int BUTTON_SPACING = 2;

	// Dock layout system
	private DockLayout dockLayout;
	private ViewportDockPanel viewportPanel;
	private ActionListDockPanel actionListDockPanel;
	private EditorDockPanel editorDockPanel;
	private ControlsDockPanel controlsDockPanel;
	private HelpDockPanel helpDockPanel;

	// Editor panels (direct references for hotkey access)
	private ActionListPanel actionListPanel;
	private ActionEditorPanel actionEditorPanel;
	private boolean editorVisible = true;
	private boolean showHelp = false;
	private int helpScroll = 0;
	private ActionListPanel.AddTarget pendingAddTarget;

	// Phase dropdown state
	private final List<ResourceLocation> phaseList = new ArrayList<>();
	private int selectedPhaseIndex = 0;

	private boolean autoReplay = true;

	/** Snapshot of the definition at the time the editor was opened (for custom spell reset). */
	private final com.google.gson.JsonElement openSnapshot;

	public SpellPreviewScreen(SpellDefinition definition) {
		super(Component.literal("Spell Preview: " + definition.id));
		this.definition = definition;
		this.scene = new VirtualSpellScene(definition);
		this.viewport = new OrthographicViewport();
		this.phaseList.addAll(definition.phases.keySet());
		// Save snapshot of current state when editor opens
		this.openSnapshot = SpellDefinition.CODEC.encodeStart(
				com.mojang.serialization.JsonOps.INSTANCE, definition).result().orElse(null);
		// Create persistent dock panels
		this.viewportPanel = new ViewportDockPanel(viewport, scene);
		this.helpDockPanel = new HelpDockPanel();
	}

	@Override
	protected void init() {
		super.init();

		// --- Top bar: view angle buttons + toggle editor + spell name ---
		int bx = 4;
		int by = 2;
		int bw = 50;
		for (ViewAngle angle : ViewAngle.values()) {
			addRenderableWidget(Button.builder(Component.literal(angle.getLabel()), btn -> {
				viewport.setPerspectiveMode(false);
				viewport.setViewAngle(angle);
			}).bounds(bx, by, bw, BUTTON_HEIGHT).build());
			bx += bw + BUTTON_SPACING;
		}
		// Perspective / Orthographic toggle
		String perspLabel = viewport.isPerspectiveMode() ? "Ortho" : "Persp";
		addRenderableWidget(Button.builder(Component.literal(perspLabel), btn -> {
			boolean newPersp = !viewport.isPerspectiveMode();
			viewport.setPerspectiveMode(newPersp);
			if (newPersp) {
				// Set camera to dummy target position
				viewport.setCameraToTarget(scene.getTargetPos());
			}
			rebuildScreen();
		}).bounds(bx, by, 40, BUTTON_HEIGHT).build());
		bx += 42;
		// Bind target toggle (only in perspective mode)
		if (viewport.isPerspectiveMode()) {
			String bindLabel = viewport.isTargetBoundToCamera() ? "Unbind" : "BindTgt";
			addRenderableWidget(Button.builder(Component.literal(bindLabel), btn -> {
				viewport.setTargetBoundToCamera(!viewport.isTargetBoundToCamera());
				rebuildScreen();
			}).bounds(bx, by, 48, BUTTON_HEIGHT).build());
			bx += 50;
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
		// Reset button: restore to original (built-in) or open-snapshot (custom)
		addRenderableWidget(Button.builder(Component.literal("Reset"), btn -> resetToDefault())
				.bounds(bx, by, 40, BUTTON_HEIGHT).build());
		bx += 42;
		// Auto Replay toggle
		addRenderableWidget(Button.builder(Component.literal(autoReplay ? "Auto:ON" : "Auto:OFF"), btn -> {
			autoReplay = !autoReplay;
			rebuildScreen();
		}).bounds(bx, by, 52, BUTTON_HEIGHT).build());
		bx += 54;
		// Help button
		addRenderableWidget(Button.builder(Component.literal("Help"), btn -> {
			showHelp = !showHelp;
		}).bounds(bx, by, 32, BUTTON_HEIGHT).build());
		bx += 34;
		// Collapse All / Expand All
		addRenderableWidget(Button.builder(Component.literal("\u25B6All"), btn -> {
			if (actionListPanel != null) actionListPanel.collapseAll();
		}).bounds(bx, by, 34, BUTTON_HEIGHT).build());
		bx += 36;
		addRenderableWidget(Button.builder(Component.literal("\u25BCAll"), btn -> {
			if (actionListPanel != null) actionListPanel.expandAll();
		}).bounds(bx, by, 34, BUTTON_HEIGHT).build());
		bx += 36;
		// Toggle show all add-buttons
		if (actionListPanel != null) {
			String addLabel = actionListPanel.isShowAllAddButtons() ? "[+]:All" : "[+]:Sel";
			addRenderableWidget(Button.builder(Component.literal(addLabel), btn -> {
				actionListPanel.toggleShowAllAddButtons();
				rebuildScreen();
			}).bounds(bx, by, 42, BUTTON_HEIGHT).build());
			bx += 44;
		}
		// Reset Layout button
		addRenderableWidget(Button.builder(Component.literal("RstLayout"), btn -> {
			DockSerializer.deleteLayout();
			rebuildScreen();
		}).bounds(bx, by, 56, BUTTON_HEIGHT).build());

		// --- Create editor panels ---
		actionListPanel = new ActionListPanel(
				(action, path) -> {
					if (actionEditorPanel != null) {
						actionEditorPanel.setAction(action, path.leafIndex());
					}
				},
				this::onRequestAddAction,
				() -> { scene.reset(); scene.play(); }
		);
		actionListPanel.loadCustomNames(definition.customNames);

		actionEditorPanel = new ActionEditorPanel(
				this::addRenderableWidget,
				this::removeWidget,
				this::onActionEdited,
				this::onDeleteAction
		);
		actionEditorPanel.setToggleDisableCallback(() -> {
			if (actionListPanel != null && actionListPanel.toggleSelectedDisabled()) {
				actionEditorPanel.clearAction();
				if (autoReplay) { scene.reset(); scene.play(); }
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
		controlsDockPanel = new ControlsDockPanel(scene, viewport, this::rebuildScreen, this::cyclePhase);
		controlsDockPanel.setWidgetCallbacks(this::addRenderableWidget, this::removeWidget);

		// --- Build dock layout tree (load from config or use default) ---
		java.util.Map<String, DockPanel> panelMap = new java.util.LinkedHashMap<>();
		panelMap.put(viewportPanel.dockId(), viewportPanel);
		panelMap.put(actionListDockPanel.dockId(), actionListDockPanel);
		panelMap.put(editorDockPanel.dockId(), editorDockPanel);
		panelMap.put(controlsDockPanel.dockId(), controlsDockPanel);
		panelMap.put(helpDockPanel.dockId(), helpDockPanel);

		DockNode root = DockSerializer.loadLayout(panelMap, SpellPreviewScreen::buildDefaultLayout);
		dockLayout = new DockLayout(root);
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

		DockGroup viewportGroup = new DockGroup(viewport);
		DockGroup actionListGroup = new DockGroup(actions);
		DockGroup editorGroup = new DockGroup(properties);
		DockGroup controlsGroup = new DockGroup(controls);
		// Help 面板默认不显示

		DockSplit rightSplit = new DockSplit(false, 0.4f, actionListGroup, editorGroup);
		DockSplit mainSplit = new DockSplit(true, 0.6f, viewportGroup, rightSplit);
		return new DockSplit(false, 0.8f, mainSplit, controlsGroup);
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

	/**
	 * Reset the spell to its original state.
	 * Built-in spells: restored from SpellRegistry defaults (code-defined).
	 * Custom spells: restored from the snapshot taken when the editor was opened.
	 */
	private void resetToDefault() {
		// Try built-in default first
		SpellDefinition restored = SpellRegistry.getDefault(definition.id);
		if (restored == null && openSnapshot != null) {
			// Custom spell: restore from open-time snapshot
			restored = SpellDefinition.CODEC.parse(
					com.mojang.serialization.JsonOps.INSTANCE, openSnapshot).result().orElse(null);
		}
		if (restored == null) return;

		// Replace all phases in the current definition with restored data
		for (var entry : restored.phases.entrySet()) {
			var current = definition.phases.get(entry.getKey());
			if (current != null) {
				var src = entry.getValue();
				current.onEnter.clear(); current.onEnter.addAll(src.onEnter);
				current.onTick.clear(); current.onTick.addAll(src.onTick);
				current.onExit.clear(); current.onExit.addAll(src.onExit);
				current.onDamage.clear(); current.onDamage.addAll(src.onDamage);
				current.transitions.clear(); current.transitions.addAll(src.transitions);
			}
		}

		// Refresh UI
		if (actionEditorPanel != null) actionEditorPanel.clearAction();
		updateActionListPhase();
		scene.reset();
		scene.play();

		var mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(Component.literal("[YH] Spell reset to default"), true);
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

		// Perspective camera movement (delegated to ViewportDockPanel)
		if (viewportPanel != null) {
			viewportPanel.tick(isAnyEditBoxFocused());
		}

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
		String spellName = definition.id.toString();
		int nameX = width - font.width(spellName) - 4;
		guiGraphics.drawString(font, spellName, nameX, 5, 0xFFAAAAAA, false);

		// Phase name (in controls area)
		if (!phaseList.isEmpty() && controlsDockPanel != null) {
			int row4Y = controlsDockPanel.getY() + 4 + (BUTTON_HEIGHT + BUTTON_SPACING) * 3;
			String phaseName = phaseList.get(selectedPhaseIndex).getPath();
			guiGraphics.drawString(font, phaseName, controlsDockPanel.getX() + 64, row4Y + 4, 0xFFFFFF88, false);
		}

		// Help overlay
		if (showHelp) {
			renderHelpOverlay(guiGraphics, mouseX, mouseY);
		}
	}

	// --- Help overlay ---

	private static final String[] HELP_LINES = {
			"\u00A7e\u00A7l--- 快捷键 ---",
			"",
			"\u00A7fSpace       \u00A77播放/暂停",
			"\u00A7fR           \u00A77重置到 tick 0",
			"\u00A7fRight       \u00A77单步推进 1 tick",
			"\u00A7fDel/Bksp    \u00A77删除选中节点",
			"",
			"\u00A7fCtrl+Z      \u00A77撤销",
			"\u00A7fCtrl+Y      \u00A77重做",
			"\u00A7fCtrl+C      \u00A77复制节点",
			"\u00A7fCtrl+X      \u00A77剪切节点",
			"\u00A7fCtrl+V      \u00A77粘贴节点",
			"\u00A7fCtrl+Up     \u00A77上移节点",
			"\u00A7fCtrl+Down   \u00A77下移节点",
			"\u00A7fCtrl+D      \u00A77启用/禁用节点",
			"\u00A7fCtrl+E      \u00A77折叠/展开选中子树",
			"\u00A7fCtrl+Sh+E   \u00A77全部折叠/全部展开",
			"\u00A7fCtrl+N      \u00A77切换自定义节点名显示",
			"\u00A7fCtrl+B      \u00A77切换 +按钮 全部显示/仅选中显示",
			"",
			"\u00A7e\u00A7l--- 鼠标操作 ---",
			"",
			"\u00A76节点树:",
			"\u00A7f  单击节点    \u00A77选中并在右侧编辑（显示该节点的 + 按钮）",
			"\u00A7f  双击节点    \u00A77重命名 (Enter 确认, Esc 取消)",
			"\u00A7f  点击 \u25BC/\u25B6   \u00A77折叠/展开子树",
			"\u00A7f  拖拽节点    \u00A77拖放重排序或移入分支",
			"\u00A7f  点击 [+]   \u00A77添加新节点到段落/分支",
			"",
			"\u00A76属性面板:",
			"\u00A7f  [Disable]  \u00A77禁用节点 (运行时跳过)",
			"\u00A7f  [Delete]   \u00A77删除节点",
			"\u00A7f  Ctrl+点击 \u00A7b$var\u00A7f  \u00A77跳转到变量定义节点",
			"\u00A7f  Tab        \u00A77表达式自动补全",
			"\u00A7f  滚轮       \u00A77滚动属性列表（右侧有滚动条）",
			"",
			"\u00A763D 视口 (正交模式):",
			"\u00A7f  左键拖拽    \u00A77移动目标位置",
			"\u00A7f  中键拖拽    \u00A77平移摄像机",
			"\u00A7f  右键拖拽    \u00A77旋转摄像机",
			"\u00A7f  滚轮        \u00A77缩放",
			"",
			"\u00A763D 视口 (透视模式):",
			"\u00A7f  左键单击    \u00A77进入自由视角（隐藏鼠标）",
			"\u00A7f  WASD/空格/Shift  \u00A77移动摄像机（自由视角中）",
			"\u00A7f  鼠标移动    \u00A77旋转视角（自由视角中）",
			"\u00A7f  滚轮        \u00A77调节飞行速度",
			"\u00A7f  右键拖拽    \u00A77轴心旋转（环绕前方中心）",
			"\u00A7f  中键拖拽    \u00A77视角平面平移",
			"\u00A7f  Esc         \u00A77退出自由视角 / 退出透视模式",
			"",
			"\u00A7e\u00A7l--- 工具栏按钮 ---",
			"",
			"\u00A7fTop/Front/Side  \u00A77切换正交预设角度",
			"\u00A7fPersp/Ortho     \u00A77切换透视/正交模式",
			"\u00A7fBindTgt/Unbind  \u00A77绑定/解绑目标跟随摄像机（透视）",
			"\u00A7f\u25B6All / \u25BCAll     \u00A77全部折叠 / 全部展开节点树",
			"\u00A7f[+]:Sel/All     \u00A77+ 按钮 仅选中显示 / 全部显示",
			"\u00A7fApply           \u00A77应用并保存符卡到所有使用它的实体",
			"\u00A7fExport          \u00A77导出 JSON 到 youkaishomecoming_exports/",
			"\u00A7fReset           \u00A77恢复到内建默认值",
			"\u00A7fAuto:ON/OFF     \u00A77编辑后自动回放预览",
			"",
			"\u00A7e\u00A7l--- Mover 类型 ---",
			"",
			"\u00A7fnone          \u00A77默认直线飞行",
			"\u00A7facceleration  \u00A77恒定加速度",
			"\u00A7frotate        \u00A77旋转",
			"\u00A7fpolar         \u00A77极坐标运动",
			"\u00A7fzero          \u00A77静止不动",
			"\u00A7fbezier        \u00A77三次贝塞尔曲线路径",
			"",
			"\u00A7e\u00A7l--- 表达式语法 ---",
			"",
			"\u00A77运算符: \u00A7f+ - * / %  \u00A77括号: \u00A7f( )",
			"\u00A77变量: \u00A7b$wave  $i  $ver",
			"\u00A77函数: \u00A7erand\u00A7f(min,max)  \u00A7esqrt\u00A7f(x)",
			"\u00A77       \u00A7esin\u00A7f(x,amp?,phase?)  \u00A7ecos\u00A7f(...)",
			"\u00A77       \u00A7elerp\u00A7f(start,end,dur)",
			"\u00A77       \u00A7ehp\u00A7f(full,empty)  \u00A7etick_mod\u00A7f(n)",
			"\u00A77关键字: \u00A7etick  total_tick  distance",
			"",
			"\u00A7e\u00A7l--- 语法高亮 ---",
			"",
			"\u00A7b$variable      \u00A77浅蓝色",
			"\u00A7erand() sqrt()  \u00A77函数 = 黄色",
			"\u00A7etick distance  \u00A77关键字 = 黄色",
			"\u00A7e(  \u00A7c(  \u00A7a(  \u00A79(  \u00A77括号 = 彩虹(仅合法时)",
			"",
			"\u00A78按 Esc 或 Help 关闭此面板",
			"\u00A78关闭编辑器时自动保存符卡到存档",
	};

	private void renderHelpOverlay(GuiGraphics g, int mouseX, int mouseY) {
		int margin = 30;
		int hx = margin;
		int hy = margin;
		int hw = width - margin * 2;
		int hh = height - margin * 2;

		// Background
		g.pose().pushPose();
		g.pose().translate(0, 0, 400);
		g.fill(hx, hy, hx + hw, hy + hh, 0xEE111122);
		g.fill(hx, hy, hx + hw, hy + 1, 0xFF444488);
		g.fill(hx, hy + hh - 1, hx + hw, hy + hh, 0xFF444488);
		g.fill(hx, hy, hx + 1, hy + hh, 0xFF444488);
		g.fill(hx + hw - 1, hy, hx + hw, hy + hh, 0xFF444488);

		// Title
		String title = "Spell Editor Help";
		g.drawString(font, title, hx + (hw - font.width(title)) / 2, hy + 4, 0xFFFFFF88, false);

		// Scrollable content
		int contentY = hy + 18;
		int contentH = hh - 22;
		g.enableScissor(hx + 4, contentY, hx + hw - 4, contentY + contentH);

		int lineH = 10;
		int maxScroll = Math.max(0, HELP_LINES.length * lineH - contentH);
		helpScroll = Math.max(0, Math.min(maxScroll, helpScroll));

		for (int i = 0; i < HELP_LINES.length; i++) {
			int ly = contentY + i * lineH - helpScroll;
			if (ly + lineH < contentY || ly > contentY + contentH) continue;
			g.drawString(font, HELP_LINES[i], hx + 8, ly, 0xFFCCCCCC, false);
		}
		g.disableScissor();

		// Scrollbar
		if (maxScroll > 0) {
			int sbX = hx + hw - 6;
			int trackH = contentH - 2;
			int thumbH = Math.max(10, trackH * contentH / (HELP_LINES.length * lineH));
			int thumbY = contentY + 1 + (trackH - thumbH) * helpScroll / maxScroll;
			g.fill(sbX, contentY, sbX + 4, contentY + contentH, 0x33FFFFFF);
			g.fill(sbX + 1, thumbY, sbX + 3, thumbY + thumbH, 0x88AAAACC);
		}

		g.pose().popPose();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (showHelp) {
			showHelp = false;
			return true;
		}
		// Editor dropdown/completion may extend beyond panel bounds — check first
		if (actionEditorPanel != null && actionEditorPanel.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		// Dock layout dispatches to panels
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
		if (showHelp) {
			helpScroll -= (int) (delta * 30);
			return true;
		}
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
		// Help overlay: any key closes
		if (showHelp) {
			showHelp = false;
			return true;
		}

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
			// All other keys → let EditBox handle (typing, cursor, Ctrl+A/C/V within text)
			return super.keyPressed(keyCode, scanCode, modifiers);
		}

		// === Below: no EditBox is focused, custom hotkeys active ===

		// Ctrl+Z/Y for undo/redo
		if (net.minecraft.client.gui.screens.Screen.hasControlDown()) {
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Z && actionListPanel != null) {
				if (actionListPanel.undo()) {
					if (actionEditorPanel != null) actionEditorPanel.clearAction();
					if (autoReplay) { scene.reset(); scene.play(); }
					return true;
				}
			}
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Y && actionListPanel != null) {
				if (actionListPanel.redo()) {
					if (actionEditorPanel != null) actionEditorPanel.clearAction();
					if (autoReplay) { scene.reset(); scene.play(); }
					return true;
				}
			}
		}

		// Ctrl+D = toggle disable, Ctrl+N = toggle custom names, Ctrl+E = collapse/expand
		if (net.minecraft.client.gui.screens.Screen.hasControlDown()) {
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_D && actionListPanel != null) {
				if (actionListPanel.toggleSelectedDisabled()) {
					if (actionEditorPanel != null) actionEditorPanel.clearAction();
					if (autoReplay) { scene.reset(); scene.play(); }
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

		// Let action list handle key presses (e.g. rename mode)
		if (actionListPanel != null && actionListPanel.keyPressed(keyCode, scanCode, modifiers)) {
			return true;
		}

		// Delete / Backspace = delete selected action
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE
				|| keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
			if (actionListPanel != null && actionListPanel.deleteSelected()) {
				if (actionEditorPanel != null) actionEditorPanel.clearAction();
				if (autoReplay) { scene.reset(); scene.play(); }
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
	public boolean charTyped(char codePoint, int modifiers) {
		if (actionListPanel != null && actionListPanel.charTyped(codePoint, modifiers)) {
			return true;
		}
		return super.charTyped(codePoint, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
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
		// Sync custom node names back to definition before saving
		if (actionListPanel != null) {
			definition.customNames.clear();
			definition.customNames.putAll(actionListPanel.getCustomNames());
		}
		// Persist the current definition to SpellRegistry and disk
		SpellRegistry.register(definition);
		var server = Minecraft.getInstance().getSingleplayerServer();
		if (server != null) {
			server.execute(() -> CustomSpellStorage.saveSpell(server, definition));
		}
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
