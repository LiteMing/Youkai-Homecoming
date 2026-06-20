package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.action.*;
import dev.xkmc.youkaishomecoming.content.spell.condition.*;
import dev.xkmc.youkaishomecoming.content.spell.definition.ColorProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProviders;
import dev.xkmc.youkaishomecoming.content.spell.definition.PatternType;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Renders a clickable tree of actions within a phase.
 * Shows actions from onEnter, onTick, and onExit lists.
 * ConditionalAction sub-actions are shown recursively at increasing indent.
 * Section headers have [+] buttons for adding new actions.
 */
@OnlyIn(Dist.CLIENT)
public class ActionListPanel {

	private static final int ROW_HEIGHT = 14;
	private static final int PADDING = 4;
	private static final int INDENT_PX = 8;

	/**
	 * Path segment: index within a list, plus the branch taken to reach the next level.
	 * branch is null for the leaf segment (the selected action itself).
	 */
	public record PathEntry(int index, @Nullable String branch) {}

	/**
	 * Identifies an action in the tree. path entries encode the traversal:
	 * [{index=2}] = top-level action at index 2
	 * [{index=2, branch="true"}, {index=0}] = child 0 in the if_true branch of top-level action 2
	 * [{index=2, branch="true"}, {index=0, branch="false"}, {index=1}] = deeper nesting
	 */
	public record ActionPath(String section, List<PathEntry> path) {
		public static ActionPath topLevel(String section, int index) {
			return new ActionPath(section, List.of(new PathEntry(index, null)));
		}

		public ActionPath child(String branch, int childIndex) {
			var entries = new ArrayList<>(path);
			var last = entries.remove(entries.size() - 1);
			entries.add(new PathEntry(last.index, branch));
			entries.add(new PathEntry(childIndex, null));
			return new ActionPath(section, List.copyOf(entries));
		}

		public boolean isNested() {
			return path.size() > 1;
		}

		public int leafIndex() {
			return path.get(path.size() - 1).index;
		}
	}

	/**
	 * Identifies where to insert a new action.
	 * For section-level: parentPath=null, branch=null.
	 * For conditional branch: parentPath = path to the ConditionalAction, branch = "true"/"false".
	 */
	public record AddTarget(String section, @Nullable ActionPath parentPath, @Nullable String branch) {
		public static AddTarget section(String section) {
			return new AddTarget(section, null, null);
		}

		public static AddTarget branch(String section, ActionPath parentPath, String branch) {
			return new AddTarget(section, parentPath, branch);
		}

		public boolean isBranch() {
			return branch != null && parentPath != null;
		}
	}

	private enum RowKind {SECTION, ACTION, ADD_BUTTON}

	private record Row(RowKind kind, int y, int indent, boolean ancestorDisabled,
					   String section, String sectionTitle,
					   ActionPath path, SpellAction action,
					   AddTarget addTarget, String addLabel) {
		static Row section(String section, String title, int y) {
			return new Row(RowKind.SECTION, y, 0, false, section, title, null, null, null, null);
		}

		static Row action(ActionPath path, SpellAction action, int indent, int y, boolean ancestorDisabled) {
			return new Row(RowKind.ACTION, y, indent, ancestorDisabled, null, null, path, action, null, null);
		}

		static Row addButton(AddTarget target, String label, int indent, int y) {
			return new Row(RowKind.ADD_BUTTON, y, indent, false, null, null, null, null, target, label);
		}
	}

	private int x, y, w, h;
	private PhaseDefinition phase;
	private ActionPath selectedPath;
	private final List<Row> rows = new ArrayList<>();
	private int scrollOffset = 0;
	private boolean dirty = true;

	// Multi-selection state: additional selected paths (always includes selectedPath if non-null)
	private final java.util.LinkedHashSet<ActionPath> selectedPaths = new java.util.LinkedHashSet<>();

	// Currently selected add-button (for paste target)
	private AddTarget selectedAddTarget = null;

	// Collapse state: set of action paths that are collapsed
	private final java.util.Set<String> collapsedPaths = new java.util.HashSet<>();
	// When true, all add-buttons are shown (toggle with Ctrl+B); when false only selected node's add-buttons show
	private boolean showAllAddButtons = false;

	// During drag, set of action paths that are force-expanded to show their add-buttons
	private final java.util.Set<String> dragExpandedPaths = new java.util.HashSet<>();

	// Custom node names (collapseKey → display name)
	private final java.util.Map<String, String> customNames = new java.util.HashMap<>();
	private boolean showCustomNames = true;

	// Rename state
	private ActionPath renamingPath = null;
	private String renamingText = "";
	private long lastClickTime = 0;
	private ActionPath lastClickPath = null;
	private static final long DOUBLE_CLICK_MS = 400;

	// Drag state
	private boolean isDragging = false;
	private ActionPath dragSourcePath = null;
	private String dragSourceSection = null;
	private double dragStartX, dragStartY;
	private static final int DRAG_THRESHOLD = 4;
	private boolean dragThresholdMet = false;

	// Scrollbar drag state
	private boolean scrollbarDragging = false;

	// Drop target: either a gap between top-level rows or an AddTarget (branch insert)
	private int dragIndicatorY = -1;       // Y for the indicator line (reorder mode)
	private int dragInsertIndex = -1;      // index for reorder within section
	private String dragInsertSection = null;
	private AddTarget dragBranchTarget = null;  // non-null when dropping into a branch
	private int dragBranchHighlightY = -1; // Y of the highlighted add-button row

	private final BiConsumer<SpellAction, ActionPath> onSelect;
	private final Consumer<AddTarget> onRequestAdd;
	private final Runnable onMoved;
	private final UndoManager undoManager = new UndoManager();

	public ActionListPanel(BiConsumer<SpellAction, ActionPath> onSelect, Consumer<AddTarget> onRequestAdd, Runnable onMoved) {
		this.onSelect = onSelect;
		this.onRequestAdd = onRequestAdd;
		this.onMoved = onMoved;
	}

	/** Save current state before a mutation. */
	private void pushUndo() {
		if (phase != null) undoManager.pushUndo(phase);
	}

	/** Undo last change, returning true if state was restored. */
	public boolean undo() {
		if (phase == null) return false;
		var restored = undoManager.undo(phase);
		if (restored == null) return false;
		applyRestoredPhase(restored);
		return true;
	}

	/** Redo last undone change, returning true if state was restored. */
	public boolean redo() {
		if (phase == null) return false;
		var restored = undoManager.redo(phase);
		if (restored == null) return false;
		applyRestoredPhase(restored);
		return true;
	}

	private void applyRestoredPhase(PhaseDefinition restored) {
		// Replace the contents of the current phase with restored data
		phase.onEnter.clear(); phase.onEnter.addAll(restored.onEnter);
		phase.onTick.clear(); phase.onTick.addAll(restored.onTick);
		phase.onExit.clear(); phase.onExit.addAll(restored.onExit);
		phase.onDamage.clear(); phase.onDamage.addAll(restored.onDamage);
		phase.transitions.clear(); phase.transitions.addAll(restored.transitions);
		selectedPath = null;
		selectedPaths.clear();
		selectedAddTarget = null;
		dirty = true;
	}

	public void setBounds(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		this.dirty = true;
	}

	public void setPhase(PhaseDefinition phase) {
		this.phase = phase;
		this.selectedPath = null;
		this.selectedPaths.clear();
		this.selectedAddTarget = null;
		this.scrollOffset = 0;
		this.dirty = true;
	}

	/** Load custom names from spell definition (called on editor open). */
	public void loadCustomNames(java.util.Map<String, String> names) {
		customNames.clear();
		customNames.putAll(names);
	}

	/** Save custom names back to spell definition (called on editor close/save). */
	public java.util.Map<String, String> getCustomNames() {
		return new java.util.HashMap<>(customNames);
	}

	public void setCustomName(String key, @org.jetbrains.annotations.Nullable String value) {
		if (value == null || value.isBlank()) {
			customNames.remove(key);
		} else {
			customNames.put(key, value);
		}
	}

	public ActionPath getSelectedPath() {
		return selectedPath;
	}

	public void clearSelection() {
		selectedPath = null;
		selectedPaths.clear();
		selectedAddTarget = null;
	}

	/** Whether the given path is in the multi-selection set. */
	private boolean isSelected(ActionPath path) {
		if (path == null) return false;
		if (path.equals(selectedPath)) return true;
		return selectedPaths.contains(path);
	}

	/** Select all action nodes in the current phase. */
	public void selectAll() {
		if (phase == null) return;
		buildRowsIfDirty();
		selectedPaths.clear();
		ActionPath first = null;
		for (Row row : rows) {
			if (row.kind == RowKind.ACTION && row.path != null) {
				selectedPaths.add(row.path);
				if (first == null) first = row.path;
			}
		}
		if (first != null) selectedPath = first;
		selectedAddTarget = null;
		dirty = true;
	}

	/** Get all currently selected paths (ordered). */
	public java.util.List<ActionPath> getSelectedPaths() {
		if (selectedPaths.isEmpty() && selectedPath != null) {
			return List.of(selectedPath);
		}
		return new ArrayList<>(selectedPaths);
	}

	public void markDirty() {
		dirty = true;
	}

	// --- Row building ---

	private void buildRowsIfDirty() {
		if (dirty) {
			buildRows();
			dirty = false;
		}
	}

	private void buildRows() {
		rows.clear();
		if (phase == null) return;
		int cy = y + PADDING - scrollOffset;
		cy = buildSection("onEnter", phase.onEnter, cy, "enter");
		cy = buildSection("onTick", phase.onTick, cy, "tick");
		cy = buildSection("onExit", phase.onExit, cy, "exit");
		buildSection("onDamage", phase.onDamage, cy, "damage");
	}

	private int buildSection(String title, List<SpellAction> actions, int startY, String section) {
		int cy = startY;
		rows.add(Row.section(section, title + " (" + actions.size() + ")", cy));
		cy += ROW_HEIGHT;

		for (int i = 0; i < actions.size(); i++) {
			ActionPath actionPath = ActionPath.topLevel(section, i);
			cy = buildActionTree(actions.get(i), actionPath, 1, cy, section);
		}
		return cy;
	}

	private boolean hasChildren(SpellAction action) {
		SpellAction inner = action instanceof SpellActions.DisabledAction da ? da.inner() : action;
		if (inner instanceof SpellActions.ConditionalAction) return true;
		if (inner instanceof SpellActions.RepeatAction) return true;
		if (inner instanceof SpellActions.SequenceAction) return true;
		if (inner instanceof DelayAction) return true;
		if (inner instanceof BurstAction) return true;
		if (inner instanceof FireDanmakuAction fda)
			return fda.onExpiry().isPresent() || fda.onTrail().isPresent()
					|| fda.onHitEntity().isPresent() || fda.onHitBlock().isPresent();
		if (inner instanceof SpawnShooterAction) return true;
		return false;
	}

	private String collapseKey(ActionPath path) {
		var sb = new StringBuilder(path.section());
		for (var e : path.path()) {
			sb.append('/').append(e.index());
			if (e.branch() != null) sb.append(':').append(e.branch());
		}
		return sb.toString();
	}

	/**
	 * Recursively build tree rows for an action. If the action is a ConditionalAction,
	 * its if_true/if_false branches are rendered as indented sub-trees.
	 * Collapsed nodes only show themselves, not their children.
	 */
	/** Whether add-buttons should be shown for the given parent action path */
	private boolean shouldShowAddButtons(ActionPath parentPath) {
		if (showAllAddButtons) return true;
		if (isDragging && dragExpandedPaths.contains(collapseKey(parentPath))) return true;
		// Only show add-buttons for the currently selected node
		return selectedPath != null && collapseKey(selectedPath).equals(collapseKey(parentPath));
	}

	private int buildActionTree(SpellAction action, ActionPath actionPath, int indent, int startY, String section) {
		return buildActionTree(action, actionPath, indent, startY, section, false);
	}

	private int buildActionTree(SpellAction action, ActionPath actionPath, int indent, int startY, String section, boolean parentDisabled) {
		int cy = startY;
		boolean selfDisabled = action instanceof SpellActions.DisabledAction;
		boolean effectiveDisabled = parentDisabled || selfDisabled;
		rows.add(Row.action(actionPath, action, indent, cy, parentDisabled));
		cy += ROW_HEIGHT;

		// Unwrap DisabledAction for child rendering
		SpellAction inner = selfDisabled ? ((SpellActions.DisabledAction) action).inner() : action;

		// If this node is collapsed, skip all children
		if (hasChildren(inner) && collapsedPaths.contains(collapseKey(actionPath))) {
			return cy;
		}

		boolean showAdd = shouldShowAddButtons(actionPath);

		if (inner instanceof SpellActions.ConditionalAction cond) {
			for (int j = 0; j < cond.ifTrue().size(); j++) {
				ActionPath childPath = actionPath.child("true", j);
				cy = buildActionTree(cond.ifTrue().get(j), childPath, indent + 1, cy, section, effectiveDisabled);
			}
			if (showAdd) {
				rows.add(Row.addButton(AddTarget.branch(section, actionPath, "true"), "+ if_true", indent + 1, cy));
				cy += ROW_HEIGHT;
			}

			for (int j = 0; j < cond.ifFalse().size(); j++) {
				ActionPath childPath = actionPath.child("false", j);
				cy = buildActionTree(cond.ifFalse().get(j), childPath, indent + 1, cy, section, effectiveDisabled);
			}
			if (showAdd) {
				rows.add(Row.addButton(AddTarget.branch(section, actionPath, "false"), "+ if_false", indent + 1, cy));
				cy += ROW_HEIGHT;
			}
		}

		if (inner instanceof SpellActions.RepeatAction repeat) {
			for (int j = 0; j < repeat.body().size(); j++) {
				ActionPath childPath = actionPath.child("body", j);
				cy = buildActionTree(repeat.body().get(j), childPath, indent + 1, cy, section, effectiveDisabled);
			}
			if (showAdd) {
				rows.add(Row.addButton(AddTarget.branch(section, actionPath, "body"), "+ body", indent + 1, cy));
				cy += ROW_HEIGHT;
			}
		}

		if (inner instanceof DelayAction delay) {
			for (int j = 0; j < delay.body().size(); j++) {
				ActionPath childPath = actionPath.child("body", j);
				cy = buildActionTree(delay.body().get(j), childPath, indent + 1, cy, section, effectiveDisabled);
			}
			if (showAdd) {
				rows.add(Row.addButton(AddTarget.branch(section, actionPath, "body"), "+ body", indent + 1, cy));
				cy += ROW_HEIGHT;
			}
		}

		if (inner instanceof BurstAction burst) {
			for (int j = 0; j < burst.body().size(); j++) {
				ActionPath childPath = actionPath.child("body", j);
				cy = buildActionTree(burst.body().get(j), childPath, indent + 1, cy, section, effectiveDisabled);
			}
			if (showAdd) {
				rows.add(Row.addButton(AddTarget.branch(section, actionPath, "body"), "+ body", indent + 1, cy));
				cy += ROW_HEIGHT;
			}
		}

		if (inner instanceof FireDanmakuAction fda) {
			List<SpellAction> expiryActions = fda.onExpiry().orElse(List.of());
			for (int j = 0; j < expiryActions.size(); j++) {
				ActionPath childPath = actionPath.child("onExpiry", j);
				cy = buildActionTree(expiryActions.get(j), childPath, indent + 1, cy, section, effectiveDisabled);
			}
			if (showAdd) {
				rows.add(Row.addButton(AddTarget.branch(section, actionPath, "onExpiry"), "+ onExpiry", indent + 1, cy));
				cy += ROW_HEIGHT;
			}

			List<SpellAction> trailActions = fda.onTrail().orElse(List.of());
			for (int j = 0; j < trailActions.size(); j++) {
				ActionPath childPath = actionPath.child("onTrail", j);
				cy = buildActionTree(trailActions.get(j), childPath, indent + 1, cy, section, effectiveDisabled);
			}
			if (showAdd) {
				rows.add(Row.addButton(AddTarget.branch(section, actionPath, "onTrail"), "+ onTrail", indent + 1, cy));
				cy += ROW_HEIGHT;
			}

			List<SpellAction> hitEntityActions = fda.onHitEntity().orElse(List.of());
			for (int j = 0; j < hitEntityActions.size(); j++) {
				ActionPath childPath = actionPath.child("onHitEntity", j);
				cy = buildActionTree(hitEntityActions.get(j), childPath, indent + 1, cy, section, effectiveDisabled);
			}
			if (showAdd) {
				rows.add(Row.addButton(AddTarget.branch(section, actionPath, "onHitEntity"), "+ onHitEntity", indent + 1, cy));
				cy += ROW_HEIGHT;
			}

			List<SpellAction> hitBlockActions = fda.onHitBlock().orElse(List.of());
			for (int j = 0; j < hitBlockActions.size(); j++) {
				ActionPath childPath = actionPath.child("onHitBlock", j);
				cy = buildActionTree(hitBlockActions.get(j), childPath, indent + 1, cy, section, effectiveDisabled);
			}
			if (showAdd) {
				rows.add(Row.addButton(AddTarget.branch(section, actionPath, "onHitBlock"), "+ onHitBlock", indent + 1, cy));
				cy += ROW_HEIGHT;
			}
		}

		if (inner instanceof SpawnShooterAction ssa) {
			for (int j = 0; j < ssa.body().size(); j++) {
				ActionPath childPath = actionPath.child("body", j);
				cy = buildActionTree(ssa.body().get(j), childPath, indent + 1, cy, section, effectiveDisabled);
			}
			if (showAdd) {
				rows.add(Row.addButton(AddTarget.branch(section, actionPath, "body"), "+ body", indent + 1, cy));
				cy += ROW_HEIGHT;
			}
		}

		if (inner instanceof SpellActions.SequenceAction seq) {
			for (int j = 0; j < seq.actions().size(); j++) {
				ActionPath childPath = actionPath.child("actions", j);
				cy = buildActionTree(seq.actions().get(j), childPath, indent + 1, cy, section, effectiveDisabled);
			}
			if (showAdd) {
				rows.add(Row.addButton(AddTarget.branch(section, actionPath, "actions"), "+ actions", indent + 1, cy));
				cy += ROW_HEIGHT;
			}
		}
		return cy;
	}

	// --- Rendering ---

	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		Font font = Minecraft.getInstance().font;
		g.fill(x, y, x + w, y + h, 0xCC1a1a2e);
		g.fill(x, y, x + 1, y + h, 0xFF444466);

		if (phase == null) {
			g.drawString(font, SpellEditorLocalization.t("No phase"), x + PADDING, y + PADDING, 0xFF888888, false);
			return;
		}

		buildRowsIfDirty();
		g.enableScissor(x, y, x + w, y + h);

		for (Row row : rows) {
			if (row.y + ROW_HEIGHT < y || row.y > y + h) continue;

			if (row.kind == RowKind.SECTION) {
				g.drawString(font, SpellEditorLocalization.t(row.sectionTitle), x + PADDING, row.y + 2, 0xFF88AACC, false);
				String plus = "[+]";
				int plusX = x + w - font.width(plus) - PADDING;
				boolean plusHovered = mouseX >= plusX && mouseX < x + w
						&& mouseY >= row.y && mouseY < row.y + ROW_HEIGHT;
				g.drawString(font, plus, plusX, row.y + 2, plusHovered ? 0xFFFFFF44 : 0xFF66AA66, false);
			} else if (row.kind == RowKind.ACTION) {
				int ix = x + PADDING + row.indent * INDENT_PX;
				boolean isPrimary = row.path != null && row.path.equals(selectedPath);
				boolean isMultiSelected = row.path != null && isSelected(row.path);
				boolean hovered = mouseX >= x && mouseX < x + w
						&& mouseY >= row.y && mouseY < row.y + ROW_HEIGHT;
				boolean selfDisabled = row.action instanceof SpellActions.DisabledAction;
				boolean isDisabled = selfDisabled || row.ancestorDisabled;
				SpellAction displayAction = selfDisabled ? ((SpellActions.DisabledAction) row.action).inner() : row.action;

				int bgColor = isPrimary ? 0xFF334466
						: (isMultiSelected ? 0xFF2a3a56 : (hovered ? 0xFF2a2a4e : 0));
				if (bgColor != 0) g.fill(x + 1, row.y, x + w, row.y + ROW_HEIGHT, bgColor);
				// Multi-selection left border indicator
				if (isMultiSelected && !isPrimary) {
					g.fill(x + 1, row.y, x + 3, row.y + ROW_HEIGHT, 0xFF5588BB);
				}

				// Collapse/expand indicator for nodes with children
				SpellAction checkAction = displayAction;
				if (checkAction != null && row.path != null && hasChildren(checkAction)) {
					boolean collapsed = collapsedPaths.contains(collapseKey(row.path));
					String indicator = collapsed ? "\u25B6" : "\u25BC"; // ▶ or ▼
					boolean indicatorHovered = mouseX >= ix && mouseX < ix + font.width(indicator) + 2
							&& mouseY >= row.y && mouseY < row.y + ROW_HEIGHT;
					g.drawString(font, indicator, ix, row.y + 2,
							indicatorHovered ? 0xFFFFFF44 : 0xFF8888AA, false);
					ix += font.width(indicator) + 2;
				}

				// Rename mode: show editable text
				if (renamingPath != null && row.path != null && row.path.equals(renamingPath)) {
					g.drawString(font, "> " + renamingText + "_", ix, row.y + 2, 0xFFFFFF44, false);
				} else {
					String label = SpellEditorLocalization.t(getDisplayLabel(displayAction, row.path));
					int textColor;
					if (isDisabled) {
						textColor = 0xFF666666; // Gray for disabled
					} else {
						textColor = (isPrimary || isMultiSelected) ? 0xFFFFFF88 : getActionColor(displayAction);
					}
					// Italic effect for disabled: draw slightly shifted
					if (isDisabled) {
						label = "\u00A7o" + label; // §o = italic formatting code
					}
					g.drawString(font, label, ix, row.y + 2, textColor, false);
				}
			} else if (row.kind == RowKind.ADD_BUTTON) {
				int ix = x + PADDING + row.indent * INDENT_PX;
				boolean hovered = mouseX >= x && mouseX < x + w
						&& mouseY >= row.y && mouseY < row.y + ROW_HEIGHT;
				boolean selected = row.addTarget != null && row.addTarget.equals(selectedAddTarget);
				if (selected) {
					g.fill(x + 1, row.y, x + w, row.y + ROW_HEIGHT, 0xFF2a4a2e);
					g.fill(x + 1, row.y, x + 3, row.y + ROW_HEIGHT, 0xFF66FF66);
				}
				g.drawString(font, SpellEditorLocalization.t(row.addLabel), ix, row.y + 2, selected ? 0xFFFFFF88 : (hovered ? 0xFFFFFF44 : 0xFF448844), false);
			}
		}

		// Drag indicator: yellow line for reorder, green highlight for branch insert
		if (isDragging) {
			if (dragBranchTarget != null && dragBranchHighlightY >= y && dragBranchHighlightY <= y + h) {
				// Green highlight on the branch add-button row
				g.fill(x + 1, dragBranchHighlightY, x + w, dragBranchHighlightY + ROW_HEIGHT, 0x6644AA44);
				g.fill(x + 1, dragBranchHighlightY, x + 3, dragBranchHighlightY + ROW_HEIGHT, 0xFF44FF44);
			} else if (dragIndicatorY >= y && dragIndicatorY <= y + h) {
				g.fill(x + 2, dragIndicatorY - 1, x + w - 2, dragIndicatorY + 1, 0xFFFFFF44);
				// Small markers at edges
				g.fill(x + 2, dragIndicatorY - 3, x + 6, dragIndicatorY + 3, 0xFFFFFF44);
				g.fill(x + w - 6, dragIndicatorY - 3, x + w - 2, dragIndicatorY + 3, 0xFFFFFF44);
			}
		}

		// Scrollbar
		int maxScroll = getMaxScroll();
		if (maxScroll > 0) {
			int sbW = 4;
			int sbX = x + w - sbW;
			// Track
			g.fill(sbX, y, sbX + sbW, y + h, 0x33FFFFFF);
			// Thumb
			int trackH = h - 2;
			int contentH = maxScroll + h;
			int thumbH = Math.max(10, trackH * h / contentH);
			int thumbY = y + 1 + (int) ((long) (trackH - thumbH) * scrollOffset / maxScroll);
			g.fill(sbX + 1, thumbY, sbX + sbW - 1, thumbY + thumbH, 0x88AAAACC);
		}

		g.disableScissor();
	}

	// --- Mouse handling ---

	/** Toggle showing custom node names. */
	public void toggleCustomNames() {
		showCustomNames = !showCustomNames;
		dirty = true;
	}

	public boolean isShowingCustomNames() {
		return showCustomNames;
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || phase == null) return false;
		if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) return false;

		// Scrollbar click detection
		int maxScroll = getMaxScroll();
		if (maxScroll > 0) {
			int sbW = 4;
			int sbX = x + w - sbW;
			if (mouseX >= sbX && mouseX < sbX + sbW) {
				scrollbarDragging = true;
				updateScrollbarDrag(mouseY);
				return true;
			}
		}

		// If renaming, finish it on any click
		if (renamingPath != null) {
			finishRename();
		}

		buildRowsIfDirty();
		Font font = Minecraft.getInstance().font;

		for (Row row : rows) {
			if (mouseY < row.y || mouseY >= row.y + ROW_HEIGHT) continue;

			if (row.kind == RowKind.SECTION) {
				String plus = "[+]";
				int plusX = x + w - font.width(plus) - PADDING;
				if (mouseX >= plusX) {
					onRequestAdd.accept(AddTarget.section(row.section));
					return true;
				}
			} else if (row.kind == RowKind.ACTION) {
				// Check if click is on collapse/expand indicator
				if (row.action != null && row.path != null && hasChildren(row.action)) {
					int ix = x + PADDING + row.indent * INDENT_PX;
					String indicator = "\u25BC"; // width is same for ▶ and ▼
					int indicatorW = font.width(indicator) + 2;
					if (mouseX >= ix && mouseX < ix + indicatorW) {
						String key = collapseKey(row.path);
						if (collapsedPaths.contains(key)) {
							collapsedPaths.remove(key);
						} else {
							collapsedPaths.add(key);
						}
						dirty = true;
						return true;
					}
				}
				// Double-click detection for rename
				long now = System.currentTimeMillis();
				if (row.path.equals(lastClickPath) && (now - lastClickTime) < DOUBLE_CLICK_MS) {
					startRename(row.path, row.action);
					lastClickPath = null;
					return true;
				}
				lastClickTime = now;
				lastClickPath = row.path;

				boolean ctrlDown = net.minecraft.client.gui.screens.Screen.hasControlDown();
				boolean shiftDown = net.minecraft.client.gui.screens.Screen.hasShiftDown();

				if (ctrlDown) {
					// Ctrl+click: toggle individual selection
					if (selectedPaths.contains(row.path)) {
						selectedPaths.remove(row.path);
						if (row.path.equals(selectedPath)) {
							selectedPath = selectedPaths.isEmpty() ? null : selectedPaths.iterator().next();
						}
					} else {
						selectedPaths.add(row.path);
						if (selectedPath == null) selectedPath = row.path;
					}
				} else if (shiftDown && selectedPath != null) {
					// Shift+click: range select between selectedPath and clicked row
					selectedPaths.clear();
					boolean inRange = false;
					for (Row r : rows) {
						if (r.kind != RowKind.ACTION || r.path == null) continue;
						if (r.path.equals(selectedPath) || r.path.equals(row.path)) {
							selectedPaths.add(r.path);
							if (inRange) break; // reached the end of range
							inRange = true;
							continue;
						}
						if (inRange) {
							selectedPaths.add(r.path);
						}
					}
					// Ensure both endpoints are included
					selectedPaths.add(selectedPath);
					selectedPaths.add(row.path);
				} else {
					// Normal click: single select, clear multi-selection
					selectedPaths.clear();
					selectedPaths.add(row.path);
					selectedPath = row.path;
				}

				selectedAddTarget = null;
				dirty = true; // rebuild to show/hide add-buttons for newly selected node
				onSelect.accept(row.action, row.path);
				// Start potential drag for any action (only for single selection)
				if (!ctrlDown && !shiftDown) {
					dragSourcePath = row.path;
					dragSourceSection = row.path.section;
					dragStartX = mouseX;
					dragStartY = mouseY;
					dragThresholdMet = false;
				}
				return true;
			} else if (row.kind == RowKind.ADD_BUTTON) {
				selectedAddTarget = row.addTarget;
				selectedPath = row.addTarget.parentPath();
				dirty = true;
				onRequestAdd.accept(row.addTarget);
				return true;
			}
		}
		return false;
	}

	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (button != 0) return false;
		if (scrollbarDragging) {
			updateScrollbarDrag(mouseY);
			return true;
		}
		if (dragSourcePath == null) return false;

		// Check drag threshold before starting visual drag
		if (!dragThresholdMet) {
			double dx = mouseX - dragStartX;
			double dy = mouseY - dragStartY;
			if (dx * dx + dy * dy < DRAG_THRESHOLD * DRAG_THRESHOLD) return false;
			dragThresholdMet = true;
			isDragging = true;
		}

		// Find the insertion point closest to mouse Y
		buildRowsIfDirty();
		updateDragInsertPoint(mouseY);
		return true;
	}

	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button != 0) return false;
		if (scrollbarDragging) {
			scrollbarDragging = false;
			return true;
		}
		boolean wasDragging = isDragging;
		if (isDragging && dragSourcePath != null) {
			performDrop();
		}
		cancelDrag();
		return wasDragging;
	}

	private void cancelDrag() {
		isDragging = false;
		dragSourcePath = null;
		dragSourceSection = null;
		dragIndicatorY = -1;
		dragInsertIndex = -1;
		dragInsertSection = null;
		dragBranchTarget = null;
		dragBranchHighlightY = -1;
		dragThresholdMet = false;
		if (!dragExpandedPaths.isEmpty()) {
			dragExpandedPaths.clear();
			dirty = true;
		}
	}

	/**
	 * Update the drag insertion indicator based on mouse Y.
	 * Checks two kinds of drop targets:
	 * 1. Gaps between top-level action rows in the same section (reorder)
	 * 2. ADD_BUTTON rows (insert into branch of Conditional/Repeat)
	 */
	private void updateDragInsertPoint(double mouseY) {
		dragIndicatorY = -1;
		dragInsertIndex = -1;
		dragInsertSection = null;
		dragBranchTarget = null;
		dragBranchHighlightY = -1;

		String sourceSection = dragSourcePath.section;
		double bestDist = Double.MAX_VALUE;

		// === Check ACTION rows that have children (auto-expand for drag) ===
		boolean expandedAny = false;
		for (Row row : rows) {
			if (row.kind == RowKind.ACTION && row.path != null && row.action != null) {
				SpellAction inner = row.action instanceof SpellActions.DisabledAction da ? da.inner() : row.action;
				if (hasChildren(inner) && mouseY >= row.y && mouseY < row.y + ROW_HEIGHT) {
					// Auto-expand this node during drag so its add-buttons become visible
					String key = collapseKey(row.path);
					collapsedPaths.remove(key);
					dragExpandedPaths.add(key);
					expandedAny = true;
				}
			}
		}
		if (expandedAny) {
			dirty = true;
			buildRowsIfDirty();
		}

		// === Check ADD_BUTTON rows (branch insert targets) ===
		for (Row row : rows) {
			if (row.kind != RowKind.ADD_BUTTON || row.addTarget == null) continue;
			// Only allow dropping into branches within the same section
			if (!sourceSection.equals(row.addTarget.section)) continue;
			// Skip add-buttons that belong to the dragged action's own subtree.
			// Dropping into self would delete the action then fail to insert (swallowing bug).
			if (dragSourcePath != null && row.addTarget.parentPath() != null) {
				var srcEntries = dragSourcePath.path();
				var targetEntries = row.addTarget.parentPath().path();
				if (targetEntries.size() >= srcEntries.size()) {
					boolean isDescendant = true;
					for (int k = 0; k < srcEntries.size(); k++) {
						if (srcEntries.get(k).index() != targetEntries.get(k).index()) {
							isDescendant = false;
							break;
						}
					}
					if (isDescendant) continue;
				}
			}
			// Check if mouse is directly over this add-button row
			if (mouseY >= row.y && mouseY < row.y + ROW_HEIGHT) {
				double dist = Math.abs(mouseY - (row.y + ROW_HEIGHT / 2.0));
				if (dist < bestDist) {
					bestDist = dist;
					// Clear reorder target
					dragIndicatorY = -1;
					dragInsertIndex = -1;
					dragInsertSection = null;
					// Set branch target
					dragBranchTarget = row.addTarget;
					dragBranchHighlightY = row.y;
				}
				return; // If mouse is directly on an add-button, prefer it
			}
		}

		// === Check gaps between top-level action rows (reorder) ===
		List<Row> sectionRows = new ArrayList<>();
		String currentSection = null;
		for (Row row : rows) {
			if (row.kind == RowKind.SECTION) {
				currentSection = row.section;
			}
			if (row.kind == RowKind.ACTION && currentSection != null
					&& currentSection.equals(sourceSection)
					&& row.path != null && !row.path.isNested()) {
				sectionRows.add(row);
			}
		}
		if (sectionRows.isEmpty()) return;

		for (int i = 0; i <= sectionRows.size(); i++) {
			int gapY;
			if (i == 0) {
				gapY = sectionRows.get(0).y;
			} else if (i == sectionRows.size()) {
				Row lastRow = sectionRows.get(sectionRows.size() - 1);
				gapY = findBottomOfActionTree(lastRow);
			} else {
				gapY = sectionRows.get(i).y;
			}
			double dist = Math.abs(mouseY - gapY);
			if (dist < bestDist) {
				bestDist = dist;
				dragIndicatorY = gapY;
				dragInsertIndex = i;
				dragInsertSection = sourceSection;
				// Clear branch target
				dragBranchTarget = null;
				dragBranchHighlightY = -1;
			}
		}
	}

	/**
	 * Find the bottom Y of an action tree (including nested children).
	 */
	private int findBottomOfActionTree(Row actionRow) {
		int bottomY = actionRow.y + ROW_HEIGHT;
		boolean found = false;
		for (Row row : rows) {
			if (row == actionRow) {
				found = true;
				continue;
			}
			if (found) {
				if (row.kind == RowKind.ACTION && row.path != null && !row.path.isNested()) {
					break; // hit next top-level action
				}
				if (row.kind == RowKind.SECTION) {
					break; // hit next section
				}
				bottomY = row.y + ROW_HEIGHT;
			}
		}
		return bottomY;
	}

	/**
	 * Perform the drop: move the action from source to target.
	 * Supports two modes: reorder (same-section gap) and branch insert.
	 */
	private void performDrop() {
		if (phase == null || dragSourcePath == null) return;

		// Get the source action before removing it
		SpellAction action = getActionAt(dragSourcePath);
		if (action == null) return;

		if (dragBranchTarget != null) {
			// === Branch insert mode ===
			AddTarget adjustedTarget = adjustAddTargetAfterDelete(dragBranchTarget, dragSourcePath);
			if (adjustedTarget == null) return;

			PhaseDefinition beforeDrop = copyPhase(phase);
			pushUndo();

			// Remove from source
			boolean removed = doDeleteAt(dragSourcePath);
			if (!removed) {
				restoreDropSnapshot(beforeDrop);
				return;
			}
			dirty = true;

			// Insert into the branch target
			if (!insertActionInternal(adjustedTarget, action)) {
				restoreDropSnapshot(beforeDrop);
				return;
			}
			selectedAddTarget = null;
			dirty = true;
			onSelect.accept(action, selectedPath);
			onMoved.run();

		} else if (dragInsertIndex >= 0 && dragInsertSection != null) {
			// === Reorder mode (top-level within same section) ===
			if (!dragSourcePath.section.equals(dragInsertSection)) return;

			if (dragSourcePath.isNested()) {
				// Source is nested: remove from branch, insert at top level
				PhaseDefinition beforeDrop = copyPhase(phase);
				pushUndo();
				boolean removed = doDeleteAt(dragSourcePath);
				if (!removed) {
					restoreDropSnapshot(beforeDrop);
					return;
				}
				dirty = true;

				List<SpellAction> list = getSectionList(dragInsertSection);
				if (list == null) {
					restoreDropSnapshot(beforeDrop);
					return;
				}
				int actualDst = Math.max(0, Math.min(dragInsertIndex, list.size()));
				list.add(actualDst, action);
				selectedPath = ActionPath.topLevel(dragInsertSection, actualDst);
				onSelect.accept(action, selectedPath);
				onMoved.run();
			} else {
				// Source is top-level: simple swap
				List<SpellAction> list = getSectionList(dragSourcePath.section);
				if (list == null) return;

				int srcIdx = dragSourcePath.leafIndex();
				int dstIdx = dragInsertIndex;
				if (srcIdx < 0 || srcIdx >= list.size()) return;
				if (dstIdx == srcIdx || dstIdx == srcIdx + 1) return; // no-op

				pushUndo();
				list.remove(srcIdx);
				int actualDst = dstIdx > srcIdx ? dstIdx - 1 : dstIdx;
				actualDst = Math.max(0, Math.min(actualDst, list.size()));
				list.add(actualDst, action);

				selectedPath = ActionPath.topLevel(dragSourcePath.section, actualDst);
				dirty = true;
				onSelect.accept(action, selectedPath);
				onMoved.run();
			}
		}
	}

	/**
	 * Delete the action at the given path. Returns true if successful.
	 */
	private @Nullable AddTarget adjustAddTargetAfterDelete(AddTarget target, ActionPath deletedPath) {
		if (target.parentPath() == null || !target.section().equals(deletedPath.section())) {
			return target;
		}
		ActionPath adjustedParent = adjustPathAfterDelete(target.parentPath(), deletedPath);
		if (adjustedParent == null) {
			return null;
		}
		return AddTarget.branch(target.section(), adjustedParent, target.branch());
	}

	private @Nullable ActionPath adjustPathAfterDelete(ActionPath path, ActionPath deletedPath) {
		if (!path.section().equals(deletedPath.section())) {
			return path;
		}
		if (isSameActionOrDescendant(path, deletedPath)) {
			return null;
		}

		List<PathEntry> deletedEntries = deletedPath.path();
		List<PathEntry> targetEntries = path.path();
		int deletedDepth = deletedEntries.size() - 1;
		if (targetEntries.size() <= deletedDepth) {
			return path;
		}
		if (!sameContainerPrefix(targetEntries, deletedEntries, deletedDepth)) {
			return path;
		}

		int deletedIndex = deletedEntries.get(deletedDepth).index();
		PathEntry affected = targetEntries.get(deletedDepth);
		if (affected.index() <= deletedIndex) {
			return path;
		}

		var adjusted = new ArrayList<>(targetEntries);
		adjusted.set(deletedDepth, new PathEntry(affected.index() - 1, affected.branch()));
		return new ActionPath(path.section(), List.copyOf(adjusted));
	}

	private boolean sameContainerPrefix(List<PathEntry> targetEntries, List<PathEntry> deletedEntries, int containerDepth) {
		for (int i = 0; i < containerDepth; i++) {
			if (!samePathEntry(targetEntries.get(i), deletedEntries.get(i), true)) {
				return false;
			}
		}
		return true;
	}

	private boolean isSameActionOrDescendant(ActionPath path, ActionPath possibleAncestor) {
		if (!path.section().equals(possibleAncestor.section())) {
			return false;
		}
		List<PathEntry> entries = path.path();
		List<PathEntry> ancestorEntries = possibleAncestor.path();
		if (entries.size() < ancestorEntries.size()) {
			return false;
		}
		for (int i = 0; i < ancestorEntries.size(); i++) {
			boolean compareBranch = i < ancestorEntries.size() - 1;
			if (!samePathEntry(entries.get(i), ancestorEntries.get(i), compareBranch)) {
				return false;
			}
		}
		return true;
	}

	private boolean samePathEntry(PathEntry a, PathEntry b, boolean compareBranch) {
		if (a.index() != b.index()) {
			return false;
		}
		return !compareBranch || java.util.Objects.equals(a.branch(), b.branch());
	}

	private @Nullable PhaseDefinition copyPhase(PhaseDefinition source) {
		return PhaseDefinition.CODEC.encodeStart(JsonOps.INSTANCE, source)
				.result()
				.flatMap(json -> PhaseDefinition.CODEC.parse(JsonOps.INSTANCE, json).result())
				.orElse(null);
	}

	private void restoreDropSnapshot(@Nullable PhaseDefinition snapshot) {
		if (snapshot != null) {
			applyRestoredPhase(snapshot);
		}
	}

	private boolean doDeleteAt(ActionPath path) {
		List<SpellAction> list = getSectionList(path.section);
		if (list == null) return false;
		return doDelete(list, path.path, 0);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (isMouseOver(mouseX, mouseY)) {
			int maxScroll = getMaxScroll();
			scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (delta * ROW_HEIGHT)));
			dirty = true;
			return true;
		}
		return false;
	}

	private int getMaxScroll() {
		if (rows.isEmpty()) return 0;
		Row last = rows.get(rows.size() - 1);
		// Total content height = bottom of last row - top of panel
		int contentBottom = last.y + scrollOffset + ROW_HEIGHT + PADDING - y;
		return Math.max(0, contentBottom - h);
	}

	private void updateScrollbarDrag(double mouseY) {
		int maxScroll = getMaxScroll();
		if (maxScroll <= 0) return;
		int trackH = h - 2;
		int contentH = maxScroll + h;
		int thumbH = Math.max(10, trackH * h / contentH);
		int thumbTravel = trackH - thumbH;
		if (thumbTravel <= 0) return;
		// Map mouse Y to scroll offset
		double relY = mouseY - (y + 1) - thumbH / 2.0;
		double ratio = relY / thumbTravel;
		ratio = Math.max(0, Math.min(1, ratio));
		scrollOffset = (int) (ratio * maxScroll);
		dirty = true;
	}

	/** Handle key presses for rename mode. */
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (renamingPath != null) {
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
				finishRename();
				return true;
			}
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
				cancelRename();
				return true;
			}
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE && !renamingText.isEmpty()) {
				renamingText = renamingText.substring(0, renamingText.length() - 1);
				return true;
			}
			return true; // consume all keys while renaming
		}
		return false;
	}

	/** Handle character input for rename mode. */
	public boolean charTyped(char codePoint, int modifiers) {
		if (renamingPath != null) {
			if (codePoint >= 32) {
				renamingText += codePoint;
			}
			return true;
		}
		return false;
	}

	public boolean isMouseOver(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	// --- Action manipulation (recursive tree traversal) ---

	public void replaceSelectedAction(SpellAction newAction) {
		if (selectedPath == null) return;
		pushUndo();
		replaceAction(selectedPath, newAction);
	}

	/** Replace the selected action without pushing an undo snapshot (for transient drag edits). */
	public void replaceSelectedActionWithoutUndo(SpellAction newAction) {
		if (selectedPath == null) return;
		replaceAction(selectedPath, newAction);
	}

	/** Explicitly push an undo snapshot (e.g. at the start of a drag gesture). */
	public void pushUndoSnapshot() {
		pushUndo();
	}

	public void replaceAction(ActionPath path, SpellAction newAction) {
		if (phase == null) return;
		List<SpellAction> list = getSectionList(path.section);
		if (list == null) return;
		doReplace(list, path.path, 0, newAction);
		dirty = true;
	}

	private boolean doReplace(List<SpellAction> list, List<PathEntry> path, int depth, SpellAction newAction) {
		PathEntry entry = path.get(depth);
		if (entry.index >= list.size()) return false;

		if (depth == path.size() - 1) {
			list.set(entry.index, newAction);
			return true;
		}

		SpellAction parent = list.get(entry.index);
		if (parent instanceof SpellActions.ConditionalAction cond && entry.branch != null) {
			boolean isTrue = "true".equals(entry.branch);
			List<SpellAction> branch = new ArrayList<>(isTrue ? cond.ifTrue() : cond.ifFalse());
			if (!doReplace(branch, path, depth + 1, newAction)) return false;

			SpellActions.ConditionalAction rebuilt = isTrue
					? new SpellActions.ConditionalAction(cond.condition(), branch, cond.ifFalse())
					: new SpellActions.ConditionalAction(cond.condition(), cond.ifTrue(), branch);
			list.set(entry.index, rebuilt);
			return true;
		}
		if (parent instanceof SpellActions.RepeatAction repeat && "body".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(repeat.body());
			if (!doReplace(body, path, depth + 1, newAction)) return false;
			list.set(entry.index, new SpellActions.RepeatAction(repeat.count(), repeat.indexVariable(), body));
			return true;
		}
		if (parent instanceof DelayAction delay && "body".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(delay.body());
			if (!doReplace(body, path, depth + 1, newAction)) return false;
			list.set(entry.index, new DelayAction(delay.delayTicks(), body));
			return true;
		}
		if (parent instanceof BurstAction burst && "body".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(burst.body());
			if (!doReplace(body, path, depth + 1, newAction)) return false;
			list.set(entry.index, new BurstAction(burst.waves(), burst.interval(), burst.waveVariable(), body));
			return true;
		}
		if (parent instanceof SpawnShooterAction ssa && "body".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(ssa.body());
			if (!doReplace(body, path, depth + 1, newAction)) return false;
			list.set(entry.index, ssa.withBody(body));
			return true;
		}
		if (parent instanceof FireDanmakuAction fda && "onExpiry".equals(entry.branch)) {
			List<SpellAction> expiryActions = new ArrayList<>(fda.onExpiry().orElse(new ArrayList<>()));
			if (!doReplace(expiryActions, path, depth + 1, newAction)) return false;
			list.set(entry.index, fda.withOnExpiry(Optional.of(expiryActions)));
			return true;
		}
		if (parent instanceof FireDanmakuAction fda && "onTrail".equals(entry.branch)) {
			List<SpellAction> trailActions = new ArrayList<>(fda.onTrail().orElse(new ArrayList<>()));
			if (!doReplace(trailActions, path, depth + 1, newAction)) return false;
			list.set(entry.index, fda.withOnTrail(Optional.of(trailActions)));
			return true;
		}
		if (parent instanceof FireDanmakuAction fda && "onHitEntity".equals(entry.branch)) {
			List<SpellAction> hitActions = new ArrayList<>(fda.onHitEntity().orElse(new ArrayList<>()));
			if (!doReplace(hitActions, path, depth + 1, newAction)) return false;
			list.set(entry.index, fda.withOnHitEntity(Optional.of(hitActions)));
			return true;
		}
		if (parent instanceof FireDanmakuAction fda && "onHitBlock".equals(entry.branch)) {
			List<SpellAction> hitActions = new ArrayList<>(fda.onHitBlock().orElse(new ArrayList<>()));
			if (!doReplace(hitActions, path, depth + 1, newAction)) return false;
			list.set(entry.index, fda.withOnHitBlock(Optional.of(hitActions)));
			return true;
		}
		if (parent instanceof SpellActions.SequenceAction seq && "actions".equals(entry.branch)) {
			List<SpellAction> actions = new ArrayList<>(seq.actions());
			if (!doReplace(actions, path, depth + 1, newAction)) return false;
			list.set(entry.index, new SpellActions.SequenceAction(actions));
			return true;
		}
		return false;
	}

	public void insertAction(AddTarget target, SpellAction action) {
		if (phase == null) return;
		pushUndo();
		if (!insertActionInternal(target, action)) {
			return;
		}
		selectedAddTarget = null;
		dirty = true;
		onSelect.accept(action, selectedPath);
	}

	private boolean insertActionInternal(AddTarget target, SpellAction action) {
		if (phase == null) return false;
		List<SpellAction> list = getSectionList(target.section);
		if (list == null) return false;

		if (!target.isBranch()) {
			list.add(action);
			selectedPath = ActionPath.topLevel(target.section, list.size() - 1);
			return true;
		} else {
			return doInsert(list, target.parentPath.path, 0, target.branch, action, target.parentPath);
		}
	}

	private boolean doInsert(List<SpellAction> list, List<PathEntry> path, int depth,
							 String targetBranch, SpellAction newAction, ActionPath parentPath) {
		PathEntry entry = path.get(depth);
		if (entry.index >= list.size()) return false;

		SpellAction current = list.get(entry.index);

		if (depth == path.size() - 1) {
			// This is the target ConditionalAction, RepeatAction, or FireDanmakuAction
			if (current instanceof SpellActions.ConditionalAction cond) {
				boolean isTrue = "true".equals(targetBranch);
				List<SpellAction> branch = new ArrayList<>(isTrue ? cond.ifTrue() : cond.ifFalse());
				branch.add(newAction);

				SpellActions.ConditionalAction rebuilt = isTrue
						? new SpellActions.ConditionalAction(cond.condition(), branch, cond.ifFalse())
						: new SpellActions.ConditionalAction(cond.condition(), cond.ifTrue(), branch);
				list.set(entry.index, rebuilt);

				selectedPath = parentPath.child(targetBranch, branch.size() - 1);
				return true;
			}
			if (current instanceof SpellActions.RepeatAction repeat && "body".equals(targetBranch)) {
				List<SpellAction> body = new ArrayList<>(repeat.body());
				body.add(newAction);
				list.set(entry.index, new SpellActions.RepeatAction(repeat.count(), repeat.indexVariable(), body));
				selectedPath = parentPath.child(targetBranch, body.size() - 1);
				return true;
			}
			if (current instanceof DelayAction delay && "body".equals(targetBranch)) {
				List<SpellAction> body = new ArrayList<>(delay.body());
				body.add(newAction);
				list.set(entry.index, new DelayAction(delay.delayTicks(), body));
				selectedPath = parentPath.child(targetBranch, body.size() - 1);
				return true;
			}
			if (current instanceof BurstAction burst && "body".equals(targetBranch)) {
				List<SpellAction> body = new ArrayList<>(burst.body());
				body.add(newAction);
				list.set(entry.index, new BurstAction(burst.waves(), burst.interval(), burst.waveVariable(), body));
				selectedPath = parentPath.child(targetBranch, body.size() - 1);
				return true;
			}
			if (current instanceof SpawnShooterAction ssa && "body".equals(targetBranch)) {
				List<SpellAction> body = new ArrayList<>(ssa.body());
				body.add(newAction);
				list.set(entry.index, ssa.withBody(body));
				selectedPath = parentPath.child(targetBranch, body.size() - 1);
				return true;
			}
			if (current instanceof FireDanmakuAction fda && "onExpiry".equals(targetBranch)) {
				List<SpellAction> expiryActions = new ArrayList<>(fda.onExpiry().orElse(new ArrayList<>()));
				expiryActions.add(newAction);
				list.set(entry.index, fda.withOnExpiry(Optional.of(expiryActions)));
				selectedPath = parentPath.child(targetBranch, expiryActions.size() - 1);
				return true;
			}
			if (current instanceof FireDanmakuAction fda && "onTrail".equals(targetBranch)) {
				List<SpellAction> trailActions = new ArrayList<>(fda.onTrail().orElse(new ArrayList<>()));
				trailActions.add(newAction);
				list.set(entry.index, fda.withOnTrail(Optional.of(trailActions)));
				selectedPath = parentPath.child(targetBranch, trailActions.size() - 1);
				return true;
			}
			if (current instanceof FireDanmakuAction fda && "onHitEntity".equals(targetBranch)) {
				List<SpellAction> hitActions = new ArrayList<>(fda.onHitEntity().orElse(new ArrayList<>()));
				hitActions.add(newAction);
				list.set(entry.index, fda.withOnHitEntity(Optional.of(hitActions)));
				selectedPath = parentPath.child(targetBranch, hitActions.size() - 1);
				return true;
			}
			if (current instanceof FireDanmakuAction fda && "onHitBlock".equals(targetBranch)) {
				List<SpellAction> hitActions = new ArrayList<>(fda.onHitBlock().orElse(new ArrayList<>()));
				hitActions.add(newAction);
				list.set(entry.index, fda.withOnHitBlock(Optional.of(hitActions)));
				selectedPath = parentPath.child(targetBranch, hitActions.size() - 1);
				return true;
			}
			if (current instanceof SpellActions.SequenceAction seq && "actions".equals(targetBranch)) {
				List<SpellAction> actions = new ArrayList<>(seq.actions());
				actions.add(newAction);
				list.set(entry.index, new SpellActions.SequenceAction(actions));
				selectedPath = parentPath.child(targetBranch, actions.size() - 1);
				return true;
			}
			return false;
		}

		// Navigate deeper
		if (current instanceof SpellActions.ConditionalAction cond && entry.branch != null) {
			boolean isTrue = "true".equals(entry.branch);
			List<SpellAction> childList = new ArrayList<>(isTrue ? cond.ifTrue() : cond.ifFalse());
			if (!doInsert(childList, path, depth + 1, targetBranch, newAction, parentPath)) return false;

			SpellActions.ConditionalAction rebuilt = isTrue
					? new SpellActions.ConditionalAction(cond.condition(), childList, cond.ifFalse())
					: new SpellActions.ConditionalAction(cond.condition(), cond.ifTrue(), childList);
			list.set(entry.index, rebuilt);
			return true;
		}
		if (current instanceof SpellActions.RepeatAction repeat && "body".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(repeat.body());
			if (!doInsert(body, path, depth + 1, targetBranch, newAction, parentPath)) return false;
			list.set(entry.index, new SpellActions.RepeatAction(repeat.count(), repeat.indexVariable(), body));
			return true;
		}
		if (current instanceof DelayAction delay && "body".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(delay.body());
			if (!doInsert(body, path, depth + 1, targetBranch, newAction, parentPath)) return false;
			list.set(entry.index, new DelayAction(delay.delayTicks(), body));
			return true;
		}
		if (current instanceof BurstAction burst && "body".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(burst.body());
			if (!doInsert(body, path, depth + 1, targetBranch, newAction, parentPath)) return false;
			list.set(entry.index, new BurstAction(burst.waves(), burst.interval(), burst.waveVariable(), body));
			return true;
		}
		if (current instanceof SpawnShooterAction ssa && "body".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(ssa.body());
			if (!doInsert(body, path, depth + 1, targetBranch, newAction, parentPath)) return false;
			list.set(entry.index, ssa.withBody(body));
			return true;
		}
		if (current instanceof FireDanmakuAction fda && "onExpiry".equals(entry.branch)) {
			List<SpellAction> expiryActions = new ArrayList<>(fda.onExpiry().orElse(new ArrayList<>()));
			if (!doInsert(expiryActions, path, depth + 1, targetBranch, newAction, parentPath)) return false;
			list.set(entry.index, fda.withOnExpiry(Optional.of(expiryActions)));
			return true;
		}
		if (current instanceof FireDanmakuAction fda && "onTrail".equals(entry.branch)) {
			List<SpellAction> trailActions = new ArrayList<>(fda.onTrail().orElse(new ArrayList<>()));
			if (!doInsert(trailActions, path, depth + 1, targetBranch, newAction, parentPath)) return false;
			list.set(entry.index, fda.withOnTrail(Optional.of(trailActions)));
			return true;
		}
		if (current instanceof FireDanmakuAction fda && "onHitEntity".equals(entry.branch)) {
			List<SpellAction> hitActions = new ArrayList<>(fda.onHitEntity().orElse(new ArrayList<>()));
			if (!doInsert(hitActions, path, depth + 1, targetBranch, newAction, parentPath)) return false;
			list.set(entry.index, fda.withOnHitEntity(Optional.of(hitActions)));
			return true;
		}
		if (current instanceof FireDanmakuAction fda && "onHitBlock".equals(entry.branch)) {
			List<SpellAction> hitActions = new ArrayList<>(fda.onHitBlock().orElse(new ArrayList<>()));
			if (!doInsert(hitActions, path, depth + 1, targetBranch, newAction, parentPath)) return false;
			list.set(entry.index, fda.withOnHitBlock(Optional.of(hitActions)));
			return true;
		}
		if (current instanceof SpellActions.SequenceAction seq && "actions".equals(entry.branch)) {
			List<SpellAction> actions = new ArrayList<>(seq.actions());
			if (!doInsert(actions, path, depth + 1, targetBranch, newAction, parentPath)) return false;
			list.set(entry.index, new SpellActions.SequenceAction(actions));
			return true;
		}
		return false;
	}

	public boolean deleteSelected() {
		if (selectedPaths.size() > 1) return deleteMultipleSelected();
		if (phase == null || selectedPath == null) return false;
		pushUndo();
		List<SpellAction> list = getSectionList(selectedPath.section);
		if (list == null) return false;

		boolean result = doDelete(list, selectedPath.path, 0);
		if (result) {
			selectedPath = null;
			selectedPaths.clear();
			selectedAddTarget = null;
			dirty = true;
		}
		return result;
	}

	private boolean doDelete(List<SpellAction> list, List<PathEntry> path, int depth) {
		PathEntry entry = path.get(depth);
		if (entry.index >= list.size()) return false;

		if (depth == path.size() - 1) {
			list.remove(entry.index);
			return true;
		}

		SpellAction parent = list.get(entry.index);
		if (parent instanceof SpellActions.ConditionalAction cond && entry.branch != null) {
			boolean isTrue = "true".equals(entry.branch);
			List<SpellAction> branch = new ArrayList<>(isTrue ? cond.ifTrue() : cond.ifFalse());
			if (!doDelete(branch, path, depth + 1)) return false;

			SpellActions.ConditionalAction rebuilt = isTrue
					? new SpellActions.ConditionalAction(cond.condition(), branch, cond.ifFalse())
					: new SpellActions.ConditionalAction(cond.condition(), cond.ifTrue(), branch);
			list.set(entry.index, rebuilt);
			return true;
		}
		if (parent instanceof SpellActions.RepeatAction repeat && "body".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(repeat.body());
			if (!doDelete(body, path, depth + 1)) return false;
			list.set(entry.index, new SpellActions.RepeatAction(repeat.count(), repeat.indexVariable(), body));
			return true;
		}
		if (parent instanceof DelayAction delay && "body".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(delay.body());
			if (!doDelete(body, path, depth + 1)) return false;
			list.set(entry.index, new DelayAction(delay.delayTicks(), body));
			return true;
		}
		if (parent instanceof BurstAction burst && "body".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(burst.body());
			if (!doDelete(body, path, depth + 1)) return false;
			list.set(entry.index, new BurstAction(burst.waves(), burst.interval(), burst.waveVariable(), body));
			return true;
		}
		if (parent instanceof SpawnShooterAction ssa && "body".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(ssa.body());
			if (!doDelete(body, path, depth + 1)) return false;
			list.set(entry.index, ssa.withBody(body));
			return true;
		}
		if (parent instanceof FireDanmakuAction fda && "onExpiry".equals(entry.branch)) {
			List<SpellAction> expiryActions = new ArrayList<>(fda.onExpiry().orElse(new ArrayList<>()));
			if (!doDelete(expiryActions, path, depth + 1)) return false;
			list.set(entry.index, fda.withOnExpiry(expiryActions.isEmpty() ? Optional.empty() : Optional.of(expiryActions)));
			return true;
		}
		if (parent instanceof FireDanmakuAction fda && "onTrail".equals(entry.branch)) {
			List<SpellAction> trailActions = new ArrayList<>(fda.onTrail().orElse(new ArrayList<>()));
			if (!doDelete(trailActions, path, depth + 1)) return false;
			list.set(entry.index, fda.withOnTrail(trailActions.isEmpty() ? Optional.empty() : Optional.of(trailActions)));
			return true;
		}
		if (parent instanceof FireDanmakuAction fda && "onHitEntity".equals(entry.branch)) {
			List<SpellAction> hitActions = new ArrayList<>(fda.onHitEntity().orElse(new ArrayList<>()));
			if (!doDelete(hitActions, path, depth + 1)) return false;
			list.set(entry.index, fda.withOnHitEntity(hitActions.isEmpty() ? Optional.empty() : Optional.of(hitActions)));
			return true;
		}
		if (parent instanceof FireDanmakuAction fda && "onHitBlock".equals(entry.branch)) {
			List<SpellAction> hitActions = new ArrayList<>(fda.onHitBlock().orElse(new ArrayList<>()));
			if (!doDelete(hitActions, path, depth + 1)) return false;
			list.set(entry.index, fda.withOnHitBlock(hitActions.isEmpty() ? Optional.empty() : Optional.of(hitActions)));
			return true;
		}
		if (parent instanceof SpellActions.SequenceAction seq && "actions".equals(entry.branch)) {
			List<SpellAction> actions = new ArrayList<>(seq.actions());
			if (!doDelete(actions, path, depth + 1)) return false;
			list.set(entry.index, new SpellActions.SequenceAction(actions));
			return true;
		}
		return false;
	}

	// --- Clipboard ---

	private static SpellAction clipboard = null;

	/**
	 * Deep-copy selected action to clipboard via Codec round-trip.
	 */
	public boolean copySelected() {
		if (selectedPath == null || phase == null) return false;
		SpellAction action = getActionAt(selectedPath);
		if (action == null) return false;
		try {
			var json = SpellAction.CODEC.encodeStart(
					com.mojang.serialization.JsonOps.INSTANCE, action).result().orElse(null);
			if (json != null) {
				clipboard = SpellAction.CODEC.parse(
						com.mojang.serialization.JsonOps.INSTANCE, json).result().orElse(null);
			}
		} catch (Exception e) {
			clipboard = action; // fallback: shallow copy
		}
		return clipboard != null;
	}

	public boolean cutSelected() {
		if (selectedPaths.size() > 1) {
			return cutMultipleSelected();
		}
		if (copySelected()) {
			deleteSelected();
			return true;
		}
		return false;
	}

	/**
	 * Delete all multi-selected actions. Deletes from highest index to lowest
	 * to avoid index shift issues within the same section.
	 */
	public boolean deleteMultipleSelected() {
		if (phase == null || selectedPaths.size() <= 1) return deleteSelected();
		pushUndo();
		// Collect and sort paths: delete deepest/largest-index first to avoid shifting
		var paths = new ArrayList<>(selectedPaths);
		paths.sort((a, b) -> {
			int sc = a.section().compareTo(b.section());
			if (sc != 0) return sc;
			// Compare by leaf index descending
			return Integer.compare(b.leafIndex(), a.leafIndex());
		});
		boolean anyDeleted = false;
		for (ActionPath path : paths) {
			if (doDeleteAt(path)) {
				anyDeleted = true;
				// Rebuild is needed after each delete for nested paths
				dirty = true;
			}
		}
		if (anyDeleted) {
			selectedPath = null;
			selectedPaths.clear();
			selectedAddTarget = null;
			dirty = true;
		}
		return anyDeleted;
	}

	/**
	 * Cut all multi-selected actions to clipboard (as a list).
	 */
	private boolean cutMultipleSelected() {
		if (selectedPaths.size() <= 1) return false;
		// Copy the first selected action to clipboard (for simple paste compatibility)
		var paths = new ArrayList<>(selectedPaths);
		SpellAction first = getActionAt(paths.get(0));
		if (first != null) {
			try {
				var json = SpellAction.CODEC.encodeStart(
						com.mojang.serialization.JsonOps.INSTANCE, first).result().orElse(null);
				if (json != null) clipboard = SpellAction.CODEC.parse(
						com.mojang.serialization.JsonOps.INSTANCE, json).result().orElse(first);
				else clipboard = first;
			} catch (Exception e) {
				clipboard = first;
			}
		}
		return deleteMultipleSelected();
	}

	public boolean pasteAfterSelected() {
		if (clipboard == null || phase == null) return false;
		// Deep copy clipboard for paste
		SpellAction toPaste;
		try {
			var json = SpellAction.CODEC.encodeStart(
					com.mojang.serialization.JsonOps.INSTANCE, clipboard).result().orElse(null);
			toPaste = json != null ? SpellAction.CODEC.parse(
					com.mojang.serialization.JsonOps.INSTANCE, json).result().orElse(clipboard) : clipboard;
		} catch (Exception e) {
			toPaste = clipboard;
		}

		// If an add-button is selected, paste into that branch
		if (selectedAddTarget != null) {
			insertAction(selectedAddTarget, toPaste);
			dirty = true;
			return true;
		}

		if (selectedPath != null) {
			// Insert after selected action in the same list
			List<SpellAction> list = getSectionList(selectedPath.section);
			if (list != null && !selectedPath.isNested()) {
				int idx = selectedPath.leafIndex();
				pushUndo();
				list.add(idx + 1, toPaste);
				selectedPath = ActionPath.topLevel(selectedPath.section, idx + 1);
				dirty = true;
				onSelect.accept(toPaste, selectedPath);
				return true;
			}
		}
		// Default: add to onTick
		var list = getSectionList("tick");
		if (list != null) {
			pushUndo();
			list.add(toPaste);
			selectedPath = ActionPath.topLevel("tick", list.size() - 1);
			dirty = true;
			onSelect.accept(toPaste, selectedPath);
			return true;
		}
		return false;
	}

	/**
	 * Move selected action up in its section list.
	 */
	public boolean moveSelectedUp() {
		if (selectedPath == null || selectedPath.isNested()) return false;
		List<SpellAction> list = getSectionList(selectedPath.section);
		if (list == null) return false;
		int idx = selectedPath.leafIndex();
		if (idx <= 0) return false;
		pushUndo();
		java.util.Collections.swap(list, idx, idx - 1);
		selectedPath = ActionPath.topLevel(selectedPath.section, idx - 1);
		dirty = true;
		return true;
	}

	/**
	 * Move selected action down in its section list.
	 */
	public boolean moveSelectedDown() {
		if (selectedPath == null || selectedPath.isNested()) return false;
		List<SpellAction> list = getSectionList(selectedPath.section);
		if (list == null) return false;
		int idx = selectedPath.leafIndex();
		if (idx >= list.size() - 1) return false;
		pushUndo();
		java.util.Collections.swap(list, idx, idx + 1);
		selectedPath = ActionPath.topLevel(selectedPath.section, idx + 1);
		dirty = true;
		return true;
	}

	@Nullable
	private SpellAction getActionAt(ActionPath path) {
		List<SpellAction> list = getSectionList(path.section);
		if (list == null) return null;
		return getActionRecursive(list, path.path, 0);
	}

	@Nullable
	private SpellAction getActionRecursive(List<SpellAction> list, List<PathEntry> path, int depth) {
		PathEntry entry = path.get(depth);
		if (entry.index >= list.size()) return null;
		SpellAction action = list.get(entry.index);
		if (depth == path.size() - 1) return action;
		if (action instanceof SpellActions.ConditionalAction cond && entry.branch != null) {
			boolean isTrue = "true".equals(entry.branch);
			return getActionRecursive(isTrue ? cond.ifTrue() : cond.ifFalse(), path, depth + 1);
		}
		if (action instanceof SpellActions.RepeatAction repeat && "body".equals(entry.branch)) {
			return getActionRecursive(repeat.body(), path, depth + 1);
		}
		if (action instanceof DelayAction delay && "body".equals(entry.branch)) {
			return getActionRecursive(delay.body(), path, depth + 1);
		}
		if (action instanceof BurstAction burst && "body".equals(entry.branch)) {
			return getActionRecursive(burst.body(), path, depth + 1);
		}
		if (action instanceof SpawnShooterAction ssa && "body".equals(entry.branch)) {
			return getActionRecursive(ssa.body(), path, depth + 1);
		}
		if (action instanceof FireDanmakuAction fda && "onExpiry".equals(entry.branch)) {
			return getActionRecursive(fda.onExpiry().orElse(List.of()), path, depth + 1);
		}
		if (action instanceof FireDanmakuAction fda && "onTrail".equals(entry.branch)) {
			return getActionRecursive(fda.onTrail().orElse(List.of()), path, depth + 1);
		}
		if (action instanceof FireDanmakuAction fda && "onHitEntity".equals(entry.branch)) {
			return getActionRecursive(fda.onHitEntity().orElse(List.of()), path, depth + 1);
		}
		if (action instanceof FireDanmakuAction fda && "onHitBlock".equals(entry.branch)) {
			return getActionRecursive(fda.onHitBlock().orElse(List.of()), path, depth + 1);
		}
		if (action instanceof SpellActions.SequenceAction seq && "actions".equals(entry.branch)) {
			return getActionRecursive(seq.actions(), path, depth + 1);
		}
		return null;
	}

	private List<SpellAction> getSectionList(String section) {
		if (phase == null) return null;
		return switch (section) {
			case "enter" -> phase.onEnter;
			case "tick" -> phase.onTick;
			case "exit" -> phase.onExit;
			case "damage" -> phase.onDamage;
			default -> null;
		};
	}

	// --- Collapse ---

	/** Toggle collapse on the currently selected node. */
	public void toggleSelectedCollapse() {
		if (selectedPath == null) return;
		// Find the action at this path to check if it has children
		SpellAction action = getActionAt(selectedPath);
		if (action != null && hasChildren(action)) {
			String key = collapseKey(selectedPath);
			if (collapsedPaths.contains(key)) {
				collapsedPaths.remove(key);
			} else {
				collapsedPaths.add(key);
			}
			dirty = true;
		}
	}

	/** Collapse all nodes that have children. */
	public void collapseAll() {
		collapsedPaths.clear();
		// Walk all actions and add collapse keys for those with children
		if (phase == null) return;
		collapseAllIn(phase.onEnter, "enter");
		collapseAllIn(phase.onTick, "tick");
		collapseAllIn(phase.onExit, "exit");
		collapseAllIn(phase.onDamage, "damage");
		dirty = true;
	}

	private void collapseAllIn(List<SpellAction> actions, String section) {
		for (int i = 0; i < actions.size(); i++) {
			collapseAllRecursive(actions.get(i), ActionPath.topLevel(section, i));
		}
	}

	private void collapseAllRecursive(SpellAction action, ActionPath path) {
		SpellAction inner = action instanceof SpellActions.DisabledAction da ? da.inner() : action;
		if (hasChildren(inner)) {
			collapsedPaths.add(collapseKey(path));
		}
		// Recurse into children so they'll be collapsed when expanded later
		if (inner instanceof SpellActions.ConditionalAction cond) {
			for (int j = 0; j < cond.ifTrue().size(); j++)
				collapseAllRecursive(cond.ifTrue().get(j), path.child("true", j));
			for (int j = 0; j < cond.ifFalse().size(); j++)
				collapseAllRecursive(cond.ifFalse().get(j), path.child("false", j));
		}
		if (inner instanceof SpellActions.RepeatAction r) {
			for (int j = 0; j < r.body().size(); j++)
				collapseAllRecursive(r.body().get(j), path.child("body", j));
		}
		if (inner instanceof DelayAction d) {
			for (int j = 0; j < d.body().size(); j++)
				collapseAllRecursive(d.body().get(j), path.child("body", j));
		}
		if (inner instanceof BurstAction b) {
			for (int j = 0; j < b.body().size(); j++)
				collapseAllRecursive(b.body().get(j), path.child("body", j));
		}
		if (inner instanceof FireDanmakuAction fda) {
			for (int j = 0; j < fda.onExpiry().orElse(List.of()).size(); j++)
				collapseAllRecursive(fda.onExpiry().get().get(j), path.child("onExpiry", j));
			for (int j = 0; j < fda.onTrail().orElse(List.of()).size(); j++)
				collapseAllRecursive(fda.onTrail().get().get(j), path.child("onTrail", j));
			for (int j = 0; j < fda.onHitEntity().orElse(List.of()).size(); j++)
				collapseAllRecursive(fda.onHitEntity().get().get(j), path.child("onHitEntity", j));
			for (int j = 0; j < fda.onHitBlock().orElse(List.of()).size(); j++)
				collapseAllRecursive(fda.onHitBlock().get().get(j), path.child("onHitBlock", j));
		}
		if (inner instanceof SpawnShooterAction ssa) {
			for (int j = 0; j < ssa.body().size(); j++)
				collapseAllRecursive(ssa.body().get(j), path.child("body", j));
		}
		if (inner instanceof SpellActions.SequenceAction seq) {
			for (int j = 0; j < seq.actions().size(); j++)
				collapseAllRecursive(seq.actions().get(j), path.child("actions", j));
		}
	}

	/** Expand all nodes. */
	public void expandAll() {
		collapsedPaths.clear();
		dirty = true;
	}

	/** Toggle show/hide all add-buttons. */
	public void toggleShowAllAddButtons() {
		showAllAddButtons = !showAllAddButtons;
		dirty = true;
	}

	public boolean isShowAllAddButtons() {
		return showAllAddButtons;
	}

	// --- Rename ---

	private void startRename(ActionPath path, SpellAction action) {
		renamingPath = path;
		String key = collapseKey(path);
		String existing = customNames.get(key);
		renamingText = existing != null ? existing : "";
	}

	private void finishRename() {
		if (renamingPath != null) {
			String key = collapseKey(renamingPath);
			if (renamingText.isEmpty()) {
				customNames.remove(key);
			} else {
				customNames.put(key, renamingText);
			}
			renamingPath = null;
			renamingText = "";
			dirty = true;
		}
	}

	private void cancelRename() {
		renamingPath = null;
		renamingText = "";
	}

	// --- Variable jump ---

	/**
	 * Find and select the node that defines the given variable name.
	 * Searches for SetVariable(key), RepeatAction(indexVariable), BurstAction(waveVariable).
	 */
	public boolean jumpToVariableDefinition(String varName) {
		buildRowsIfDirty();
		for (Row row : rows) {
			if (row.kind != RowKind.ACTION || row.action == null) continue;
			SpellAction action = row.action instanceof SpellActions.DisabledAction da ? da.inner() : row.action;
			boolean defines = false;
			if (action instanceof SpellActions.SetVariable sv && sv.key().equals(varName)) defines = true;
			if (action instanceof SpellActions.RepeatAction ra && ra.indexVariable().equals(varName)) defines = true;
			if (action instanceof BurstAction ba && ba.waveVariable().equals(varName)) defines = true;
			if (defines) {
				selectedPath = row.path;
				onSelect.accept(row.action, row.path);
				// Scroll to make visible
				if (row.y < y || row.y + ROW_HEIGHT > y + h) {
					scrollOffset = Math.max(0, row.y + scrollOffset - y - h / 3);
					dirty = true;
				}
				return true;
			}
		}
		return false;
	}

	// --- Enable/Disable ---

	/**
	 * Toggle the selected action between enabled and disabled.
	 * Wraps in DisabledAction to disable, unwraps to re-enable.
	 */
	public boolean toggleSelectedDisabled() {
		if (selectedPath == null || phase == null) return false;
		List<SpellAction> list = getSectionList(selectedPath.section);
		if (list == null) return false;
		pushUndo();
		boolean result = doToggleDisabled(list, selectedPath.path, 0);
		if (result) {
			dirty = true;
			onMoved.run();
		}
		return result;
	}

	private boolean doToggleDisabled(List<SpellAction> list, List<PathEntry> path, int depth) {
		if (depth >= path.size()) return false;
		PathEntry entry = path.get(depth);
		if (entry.index < 0 || entry.index >= list.size()) return false;
		SpellAction current = list.get(entry.index);

		if (depth == path.size() - 1) {
			// Toggle at this level
			if (current instanceof SpellActions.DisabledAction da) {
				list.set(entry.index, da.inner());
			} else {
				list.set(entry.index, new SpellActions.DisabledAction(current));
			}
			return true;
		}

		// Recurse into children — unwrap disabled for navigation
		SpellAction parent = current instanceof SpellActions.DisabledAction da ? da.inner() : current;
		if (parent instanceof SpellActions.ConditionalAction cond && entry.branch != null) {
			boolean isTrue = "true".equals(entry.branch);
			List<SpellAction> branch = new ArrayList<>(isTrue ? cond.ifTrue() : cond.ifFalse());
			if (!doToggleDisabled(branch, path, depth + 1)) return false;
			SpellActions.ConditionalAction rebuilt = isTrue
					? new SpellActions.ConditionalAction(cond.condition(), branch, cond.ifFalse())
					: new SpellActions.ConditionalAction(cond.condition(), cond.ifTrue(), branch);
			list.set(entry.index, current instanceof SpellActions.DisabledAction ? new SpellActions.DisabledAction(rebuilt) : rebuilt);
			return true;
		}
		if (parent instanceof SpellActions.RepeatAction repeat && "body".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(repeat.body());
			if (!doToggleDisabled(body, path, depth + 1)) return false;
			var rebuilt = new SpellActions.RepeatAction(repeat.count(), repeat.indexVariable(), body);
			list.set(entry.index, current instanceof SpellActions.DisabledAction ? new SpellActions.DisabledAction(rebuilt) : rebuilt);
			return true;
		}
		if (parent instanceof DelayAction delay && "body".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(delay.body());
			if (!doToggleDisabled(body, path, depth + 1)) return false;
			var rebuilt = new DelayAction(delay.delayTicks(), body);
			list.set(entry.index, current instanceof SpellActions.DisabledAction ? new SpellActions.DisabledAction(rebuilt) : rebuilt);
			return true;
		}
		if (parent instanceof BurstAction burst && "body".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(burst.body());
			if (!doToggleDisabled(body, path, depth + 1)) return false;
			var rebuilt = new BurstAction(burst.waves(), burst.interval(), burst.waveVariable(), body);
			list.set(entry.index, current instanceof SpellActions.DisabledAction ? new SpellActions.DisabledAction(rebuilt) : rebuilt);
			return true;
		}
		if (parent instanceof SpawnShooterAction ssa && "body".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(ssa.body());
			if (!doToggleDisabled(body, path, depth + 1)) return false;
			var rebuilt = ssa.withBody(body);
			list.set(entry.index, current instanceof SpellActions.DisabledAction ? new SpellActions.DisabledAction(rebuilt) : rebuilt);
			return true;
		}
		if (parent instanceof FireDanmakuAction fda && "onExpiry".equals(entry.branch)) {
			List<SpellAction> expiryActions = new ArrayList<>(fda.onExpiry().orElse(new ArrayList<>()));
			if (!doToggleDisabled(expiryActions, path, depth + 1)) return false;
			var rebuilt = fda.withOnExpiry(expiryActions.isEmpty() ? Optional.empty() : Optional.of(expiryActions));
			list.set(entry.index, current instanceof SpellActions.DisabledAction ? new SpellActions.DisabledAction(rebuilt) : rebuilt);
			return true;
		}
		if (parent instanceof FireDanmakuAction fda && "onTrail".equals(entry.branch)) {
			List<SpellAction> trailActions = new ArrayList<>(fda.onTrail().orElse(new ArrayList<>()));
			if (!doToggleDisabled(trailActions, path, depth + 1)) return false;
			var rebuilt = fda.withOnTrail(trailActions.isEmpty() ? Optional.empty() : Optional.of(trailActions));
			list.set(entry.index, current instanceof SpellActions.DisabledAction ? new SpellActions.DisabledAction(rebuilt) : rebuilt);
			return true;
		}
		if (parent instanceof FireDanmakuAction fda && "onHitEntity".equals(entry.branch)) {
			List<SpellAction> hitActions = new ArrayList<>(fda.onHitEntity().orElse(new ArrayList<>()));
			if (!doToggleDisabled(hitActions, path, depth + 1)) return false;
			var rebuilt = fda.withOnHitEntity(hitActions.isEmpty() ? Optional.empty() : Optional.of(hitActions));
			list.set(entry.index, current instanceof SpellActions.DisabledAction ? new SpellActions.DisabledAction(rebuilt) : rebuilt);
			return true;
		}
		if (parent instanceof FireDanmakuAction fda && "onHitBlock".equals(entry.branch)) {
			List<SpellAction> hitActions = new ArrayList<>(fda.onHitBlock().orElse(new ArrayList<>()));
			if (!doToggleDisabled(hitActions, path, depth + 1)) return false;
			var rebuilt = fda.withOnHitBlock(hitActions.isEmpty() ? Optional.empty() : Optional.of(hitActions));
			list.set(entry.index, current instanceof SpellActions.DisabledAction ? new SpellActions.DisabledAction(rebuilt) : rebuilt);
			return true;
		}
		if (parent instanceof SpellActions.SequenceAction seq && "actions".equals(entry.branch)) {
			List<SpellAction> actions = new ArrayList<>(seq.actions());
			if (!doToggleDisabled(actions, path, depth + 1)) return false;
			var rebuilt = new SpellActions.SequenceAction(actions);
			list.set(entry.index, current instanceof SpellActions.DisabledAction ? new SpellActions.DisabledAction(rebuilt) : rebuilt);
			return true;
		}
		return false;
	}

	// --- Labels ---

	private String getDisplayLabel(SpellAction action, ActionPath path) {
		if (path == null || path.path.isEmpty()) return "?";
		// Check custom name first
		if (showCustomNames) {
			String key = collapseKey(path);
			String custom = customNames.get(key);
			if (custom != null && !custom.isEmpty()) {
				return custom;
			}
		}
		String prefix = "";
		if (path.path.size() > 1) {
			String branch = path.path.get(path.path.size() - 2).branch;
			if ("true".equals(branch)) prefix = "T";
			else if ("false".equals(branch)) prefix = "F";
			else if ("body".equals(branch)) prefix = "B";
			else if ("onExpiry".equals(branch)) prefix = "E";
			else if ("onHitEntity".equals(branch)) prefix = "H";
			else if ("onHitBlock".equals(branch)) prefix = "H";
		}
		int index = path.path.get(path.path.size() - 1).index;
		return prefix + getActionLabel(action, index);
	}

	private String getActionLabel(SpellAction action, int index) {
		if (action instanceof FireDanmakuAction fda) {
			String colorLabel = fda.color() instanceof ColorProvider.Constant cc ? cc.color().name().toLowerCase() : "dynamic";
			return index + ": fire " + fda.bulletType().name().toLowerCase() + " " + colorLabel;
		}
		if (action instanceof FireLaserAction fla) {
			return index + ": laser " + fla.laserType().name().toLowerCase() + " " + fla.color().name().toLowerCase();
		}
		if (action instanceof SpellActions.ConditionalAction ca) {
			return index + ": if " + getConditionBrief(ca.condition());
		}
		if (action instanceof SpellActions.SequenceAction sa) {
			return index + ": sequence(" + sa.actions().size() + ")";
		}
		if (action instanceof SpellActions.ClearScreen) return index + ": clear_screen";
		if (action instanceof EraseEnemyDanmakuAction ee) {
			return index + ": erase enemy r=" + formatNumberProvider(ee.radius());
		}
		if (action instanceof SpellActions.PlaySoundAction) return index + ": play_sound";
		if (action instanceof SpellActions.SetVariable sv) return index + ": set " + sv.key();
		if (action instanceof SpellActions.AddVariable av) return index + ": add " + av.key();
		if (action instanceof SpellActions.ForcePhase fp) {
			return index + ": force " + describePhaseTarget(fp.phaseId()) + (fp.clearScreen() ? " [clear]" : " [keep]");
		}
		if (action instanceof SpellActions.ForceSpell fs) {
			return index + ": spell " + formatResourceId(fs.spellId()) + (fs.clearScreen() ? " [clear]" : " [keep]");
		}
		if (action instanceof SpellActions.FireSpell fs) {
			String phase = fs.phaseId().map(id -> "@" + formatPhaseId(id)).orElse("");
			return index + ": fire spell " + formatResourceId(fs.spellId()) + phase;
		}
		if (action instanceof SpellActions.RepeatAction ra) return index + ": repeat(" + (int) (ra.count() instanceof NumberProviders.Constant c ? c.value() : 0) + ")";
		if (action instanceof DelayAction da) return index + ": delay(" + da.delayTicks() + "t)";
		if (action instanceof BurstAction ba) return index + ": burst(" + ba.waves() + "x" + ba.interval() + "t)";
		if (action instanceof SpawnShooterAction ssa) {
			String pattern = ssa.pattern() == PatternType.AIMED ? "" : " " + formatNumberProvider(ssa.count()) + "x" + ssa.pattern().name().toLowerCase();
			String ysm = "";
			if (!ssa.ysmModel().isBlank() || !ssa.ysmTexture().isBlank() || !ssa.ysmAnimation().isBlank()) {
				StringBuilder builder = new StringBuilder(" ysm");
				if (!ssa.ysmModel().isBlank()) {
					builder.append("=").append(ssa.ysmModel());
				}
				if (!ssa.ysmAnimation().isBlank()) {
					builder.append("@").append(ssa.ysmAnimation());
				}
				ysm = builder.toString();
			}
			return index + ": shooter" + pattern + "(hp=" + ssa.health() + ")" + ysm;
		}
		if (action instanceof YsmRenderAction yra) {
			if (yra.clear()) {
				return index + ": ysm clear " + ysmClearTargetBrief(yra.clearTarget(), "all");
			}
			StringBuilder builder = new StringBuilder(index + ": ysm set");
			if (!yra.model().isBlank()) {
				builder.append(" model=").append(yra.model());
			}
			if (!yra.texture().isBlank()) {
				builder.append(" tex=").append(yra.texture());
			}
			if (!yra.animation().isBlank()) {
				builder.append(" anim=").append(yra.animation());
			}
			if (yra.model().isBlank() && yra.texture().isBlank() && yra.animation().isBlank()) {
				builder.append(" render");
			}
			if (yra.duration() > 0) {
				builder.append(" ").append(yra.duration()).append("t expire=")
						.append(ysmClearTargetBrief(yra.clearTarget(), "changed"));
			}
			return builder.toString();
		}
		if (action instanceof TeleportAction) return index + ": teleport";
		if (action instanceof SpellActions.NoopAction) return index + ": noop";
		return index + ": " + action.getClass().getSimpleName();
	}

	private String describePhaseTarget(net.minecraft.resources.ResourceLocation phaseId) {
		String key = "phase:" + formatPhaseId(phaseId);
		String legacyKey = "phase:" + formatResourceId(phaseId);
		String custom = customNames.get(key);
		if ((custom == null || custom.isBlank()) && !legacyKey.equals(key)) {
			custom = customNames.get(legacyKey);
		}
		if (custom == null || custom.isBlank() || custom.equals(phaseId.getPath())) {
			return formatPhaseId(phaseId);
		}
		return custom + " (" + formatPhaseId(phaseId) + ")";
	}

	private static String formatPhaseId(net.minecraft.resources.ResourceLocation id) {
		return id.toString();
	}

	private static String ysmClearTargetBrief(String value, String fallback) {
		String target = value == null || value.isBlank() || "changed".equals(value) && "all".equals(fallback) ? fallback : value;
		return switch (target) {
			case "changed" -> "changed";
			case "animation", "anim" -> "animation";
			case "model" -> "model";
			case "texture" -> "texture";
			case "model_texture", "model+texture", "render" -> "model+texture";
			case "all" -> "all";
			default -> target;
		};
	}

	private static String formatResourceId(net.minecraft.resources.ResourceLocation id) {
		return "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
	}

	private static String formatNumberProvider(NumberProvider provider) {
		if (provider instanceof NumberProviders.Constant c) {
			double value = c.value();
			return Math.rint(value) == value ? Integer.toString((int) value) : Double.toString(value);
		}
		return "*";
	}

	static String getConditionBrief(SpellCondition cond) {
		if (cond instanceof SpellConditions.TickInterval ti) return "tick%" + ti.interval();
		if (cond instanceof SpellConditions.HealthBelow hb) return "hp<" + Math.round(hb.threshold() * 100) + "%";
		if (cond instanceof SpellConditions.HealthAbove ha) return "hp>" + Math.round(ha.threshold() * 100) + "%";
		if (cond instanceof SpellConditions.TickElapsed te) return "t>=" + te.ticks();
		if (cond instanceof SpellConditions.DistanceAbove da) return "dist>" + (int) da.distance();
		if (cond instanceof SpellConditions.DistanceBelow db) return "dist<" + (int) db.distance();
		if (cond instanceof SpellConditions.HitCountCondition hc) return "hits>=" + hc.count();
		if (cond instanceof SpellConditions.AlwaysCondition ac) return ac.value() ? "always" : "never";
		if (cond instanceof SpellConditions.VariableCheck vc) return vc.key() + vc.op() + (int) vc.value();
		if (cond instanceof SpellConditions.NotCondition nc) return "!" + getConditionBrief(nc.condition());
		if (cond instanceof SpellConditions.AndCondition ac) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < ac.conditions().size(); i++) {
				if (i > 0) sb.append("&");
				sb.append(getConditionBrief(ac.conditions().get(i)));
			}
			return sb.toString();
		}
		return "?";
	}

	private static int getActionColor(SpellAction action) {
		if (action instanceof FireDanmakuAction || action instanceof FireLaserAction) return 0xFFCCCCCC;
		if (action instanceof SpellActions.ConditionalAction) return 0xFFAAAADD;
		if (action instanceof SpellActions.RepeatAction) return 0xFFAAAADD;
		if (action instanceof DelayAction) return 0xFFDDAAAA;
		if (action instanceof BurstAction) return 0xFFDDAAAA;
		if (action instanceof SpawnShooterAction) return 0xFFDDCCAA;
		if (action instanceof TeleportAction || action instanceof TeleportRandomAction) return 0xFFAADDAA;
		if (action instanceof YsmRenderAction) return 0xFFAADDEE;
		if (action instanceof SpellActions.SequenceAction) return 0xFFAAAADD;
		return 0xFF999999;
	}

}
