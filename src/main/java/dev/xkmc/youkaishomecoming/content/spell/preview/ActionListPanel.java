package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.action.*;
import dev.xkmc.youkaishomecoming.content.spell.condition.*;
import dev.xkmc.youkaishomecoming.content.spell.definition.BulletProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.ColorProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProviders;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellCardType;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
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

	private enum RowKind {SUMMARY, SECTION, ACTION, BRANCH}

	private record Row(RowKind kind, int y, int indent, boolean ancestorDisabled,
					   String section, String sectionTitle,
					   ActionPath path, SpellAction action,
					   AddTarget addTarget, String addLabel) {
		static Row section(String section, String title, int y) {
			return new Row(RowKind.SECTION, y, 0, false, section, title, null, null, null, null);
		}

		static Row summary(String title, int y) {
			return new Row(RowKind.SUMMARY, y, 0, false, null, title, null, null, null, null);
		}

		static Row action(ActionPath path, SpellAction action, int indent, int y, boolean ancestorDisabled) {
			return new Row(RowKind.ACTION, y, indent, ancestorDisabled, null, null, path, action, null, null);
		}

		static Row branch(AddTarget target, String label, int indent, int y) {
			return new Row(RowKind.BRANCH, y, indent, false, null, null,
					target.parentPath(), null, target, label);
		}
	}

	private int x, y, w, h;
	private PhaseDefinition phase;
	private final java.util.function.Supplier<SpellDefinition> definitionSupplier;
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
	// Optional action branches have their own folder state. This is deliberately
	// separate from the parent action collapse state so a callback can be folded
	// without hiding the action's ordinary fields and sibling callbacks.
	private final java.util.Set<String> collapsedBranchPaths = new java.util.HashSet<>();
	// When true, all add-buttons are shown (toggle with Ctrl+B); when false only selected node's add-buttons show
	private boolean showAllAddButtons = false;

	// During drag, set of action paths that are force-expanded to show their add-buttons
	private final java.util.Set<String> dragExpandedPaths = new java.util.HashSet<>();

	// Custom node names (collapseKey → display name)
	private final java.util.Map<String, String> customNames = new java.util.HashMap<>();
	// Names are attached to action identity while the editor is open. Paths are
	// only the serialized representation and therefore must not drive the UI
	// after a move or a parent record has been rebuilt.
	private final java.util.IdentityHashMap<SpellAction, String> nodeCustomNames = new java.util.IdentityHashMap<>();
	private final java.util.IdentityHashMap<SpellAction, Boolean> nodeCustomNameScoped = new java.util.IdentityHashMap<>();
	private final java.util.Map<String, SpellAction> customNameOwners = new java.util.HashMap<>();
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
	private int dragSourceIndent = 0;
	private static final int DRAG_THRESHOLD = 4;
	private static final int DRAG_INDENT_THRESHOLD = 12;
	private boolean dragThresholdMet = false;
	private List<ActionPath> dragSourceRoots = List.of();
	@Nullable
	private ActionPath pendingSingleSelection = null;

	// Scrollbar drag state
	private boolean scrollbarDragging = false;

	// Drop target: either a gap between top-level rows or an AddTarget (branch insert)
	private int dragIndicatorY = -1;       // Y for the indicator line (reorder mode)
	private int dragIndicatorIndent = -1;  // actual tree level of the reorder target
	private int dragInsertIndex = -1;      // index for reorder within container
	private String dragInsertSection = null;
	// null = top-level section list; otherwise parent path entries (last entry carries the branch)
	private List<PathEntry> dragInsertContainerPrefix = null;
	private AddTarget dragBranchTarget = null;  // non-null when dropping into a branch
	private int dragBranchHighlightY = -1; // Y of the highlighted add-button row

	// Last known mouse position (used for hover-based paste targets)
	private double lastMouseX = -1;
	private double lastMouseY = -1;

	private final BiConsumer<SpellAction, ActionPath> onSelect;
	private final Consumer<AddTarget> onRequestAdd;
	private final Runnable onMoved;
	private final UndoManager undoManager = new UndoManager();

	public ActionListPanel(BiConsumer<SpellAction, ActionPath> onSelect, Consumer<AddTarget> onRequestAdd,
			Runnable onMoved, java.util.function.Supplier<SpellDefinition> definitionSupplier) {
		this.onSelect = onSelect;
		this.onRequestAdd = onRequestAdd;
		this.onMoved = onMoved;
		this.definitionSupplier = definitionSupplier;
	}

	/** Save current state before a mutation. */
	private void pushUndo() {
		if (phase != null) {
			syncCustomNamesFromActions();
			undoManager.pushUndo(phase, customNames);
		}
	}

	/** Undo last change, returning true if state was restored. */
	public boolean undo() {
		if (phase == null) return false;
		syncCustomNamesFromActions();
		var restored = undoManager.undo(phase, customNames);
		if (restored == null) return false;
		applyRestoredPhase(restored.phase(), restored.customNames());
		return true;
	}

	/** Redo last undone change, returning true if state was restored. */
	public boolean redo() {
		if (phase == null) return false;
		syncCustomNamesFromActions();
		var restored = undoManager.redo(phase, customNames);
		if (restored == null) return false;
		applyRestoredPhase(restored.phase(), restored.customNames());
		return true;
	}

	private void applyRestoredPhase(PhaseDefinition restored) {
		applyRestoredPhase(restored, null);
	}

	private void applyRestoredPhase(PhaseDefinition restored,
			@Nullable java.util.Map<String, String> restoredCustomNames) {
		// Replace the contents of the current phase with restored data
		phase.onEnter.clear(); phase.onEnter.addAll(restored.onEnter);
		phase.onTick.clear(); phase.onTick.addAll(restored.onTick);
		phase.onExit.clear(); phase.onExit.addAll(restored.onExit);
		phase.onDamage.clear(); phase.onDamage.addAll(restored.onDamage);
		phase.transitions.clear(); phase.transitions.addAll(restored.transitions);
		selectedPath = null;
		selectedPaths.clear();
		selectedAddTarget = null;
		if (restoredCustomNames != null) {
			customNames.clear();
			customNames.putAll(restoredCustomNames);
		}
		dirty = true;
		bindCustomNamesToActions();
	}

	public void setBounds(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		this.dirty = true;
	}

	public void setPhase(PhaseDefinition phase) {
		boolean phaseChanged = this.phase != phase;
		if (this.phase != null && phaseChanged) syncCustomNamesFromActions();
		this.phase = phase;
		if (phaseChanged) undoManager.clear();
		this.selectedPath = null;
		this.selectedPaths.clear();
		this.selectedAddTarget = null;
		this.scrollOffset = 0;
		this.dirty = true;
		bindCustomNamesToActions();
	}

	/** Load custom names from spell definition (called on editor open). */
	public void loadCustomNames(java.util.Map<String, String> names) {
		customNames.clear();
		customNames.putAll(names);
		bindCustomNamesToActions();
	}

	/** Save custom names back to spell definition (called on editor close/save). */
	public java.util.Map<String, String> getCustomNames() {
		syncCustomNamesFromActions();
		return new java.util.HashMap<>(customNames);
	}

	public void setCustomName(String key, @org.jetbrains.annotations.Nullable String value) {
		if (value == null || value.isBlank()) {
			customNames.remove(key);
		} else {
			customNames.put(key, value);
		}
		bindCustomNamesToActions();
	}

	private void bindCustomNamesToActions() {
		nodeCustomNames.clear();
		nodeCustomNameScoped.clear();
		customNameOwners.clear();
		if (phase == null) return;
		buildRowsIfDirty();
		for (Row row : rows) {
			if (row.kind != RowKind.ACTION || row.action == null || row.path == null) continue;
			String scopedKey = scopedCollapseKey(row.path);
			String legacyKey = collapseKey(row.path);
			boolean scoped = customNames.containsKey(scopedKey);
			String key = scoped ? scopedKey : legacyKey;
			String name = customNames.get(key);
			if (name != null && !name.isBlank()) {
				nodeCustomNames.put(row.action, name);
				nodeCustomNameScoped.put(row.action, scoped);
				customNameOwners.put(key, row.action);
			}
		}
	}

	private void syncCustomNamesFromActions() {
		if (phase == null) return;
		buildRowsIfDirty();
		java.util.Map<SpellAction, String> currentKeys = new java.util.IdentityHashMap<>();
		for (Row row : rows) {
			if (row.kind != RowKind.ACTION || row.action == null || row.path == null) continue;
			String name = nodeCustomNames.get(row.action);
			if (name == null || name.isBlank()) continue;
			String key = keyFor(row.path, nodeCustomNameScoped.getOrDefault(
					row.action, preferScopedCustomNames()));
			currentKeys.put(row.action, key);
		}
		// Remove serialized keys that belonged to moved/deleted nodes. Keep a
		// key when another node now owns it, so an unrelated node is not renamed.
		for (var entry : new ArrayList<>(customNameOwners.entrySet())) {
			SpellAction owner = entry.getValue();
			String currentKey = currentKeys.get(owner);
			if (!java.util.Objects.equals(entry.getKey(), currentKey)
					&& java.util.Objects.equals(customNames.get(entry.getKey()), nodeCustomNames.get(owner))) {
				customNames.remove(entry.getKey());
			}
		}
		customNameOwners.clear();
		for (var entry : currentKeys.entrySet()) {
			String key = entry.getValue();
			String name = nodeCustomNames.get(entry.getKey());
			if (name != null && !name.isBlank()) {
				customNames.put(key, name);
				customNameOwners.put(key, entry.getKey());
			}
		}
	}

	@Nullable
	private String customNameFor(@Nullable SpellAction action, @Nullable ActionPath path) {
		if (action != null) {
			String name = nodeCustomNames.get(action);
			if (name != null && !name.isBlank()) return name;
		}
		if (path == null) return null;
		String scoped = customNames.get(scopedCollapseKey(path));
		return scoped != null && !scoped.isBlank() ? scoped : customNames.get(collapseKey(path));
	}

	private void setNodeCustomName(@Nullable SpellAction action, ActionPath path, @Nullable String value) {
		boolean scoped = action == null ? preferScopedCustomNames()
				: nodeCustomNameScoped.getOrDefault(action, preferScopedCustomNames());
		String key = keyFor(path, scoped);
		if (action != null) {
			for (var entry : new ArrayList<>(customNameOwners.entrySet())) {
				if (entry.getValue() == action && !entry.getKey().equals(key)) {
					customNames.remove(entry.getKey());
					customNameOwners.remove(entry.getKey());
				}
			}
		}
		if (value == null || value.isBlank()) {
			if (action != null) {
				nodeCustomNames.remove(action);
				nodeCustomNameScoped.remove(action);
			}
			customNames.remove(key);
			customNameOwners.remove(key);
		} else {
			if (action != null) {
				nodeCustomNames.put(action, value);
				nodeCustomNameScoped.put(action, scoped);
			}
			customNames.put(key, value);
			if (action != null) customNameOwners.put(key, action);
		}
	}

	private String scopedCollapseKey(ActionPath path) {
		return phaseScope() + "/" + collapseKey(path);
	}

	private String keyFor(ActionPath path, boolean scoped) {
		return scoped ? scopedCollapseKey(path) : collapseKey(path);
	}

	private String phaseScope() {
		if (phase == null || phase.id == null) return "main";
		String path = phase.id.getPath();
		int split = path.lastIndexOf('/');
		return split < 0 ? path : path.substring(split + 1);
	}

	private boolean preferScopedCustomNames() {
		return !"main".equals(phaseScope());
	}

	private void transferNodeCustomName(SpellAction oldAction, SpellAction newAction) {
		if (oldAction == newAction) return;
		String name = nodeCustomNames.remove(oldAction);
		Boolean scoped = nodeCustomNameScoped.remove(oldAction);
		if (name != null && !name.isBlank()) nodeCustomNames.put(newAction, name);
		if (scoped != null) nodeCustomNameScoped.put(newAction, scoped);
		for (var entry : customNameOwners.entrySet()) {
			if (entry.getValue() == oldAction) entry.setValue(newAction);
		}
	}

	public ActionPath getSelectedPath() {
		return selectedPath;
	}

	@Nullable
	public SpellAction getSelectedAction() {
		return selectedPath == null ? null : getActionAt(selectedPath);
	}

	@Nullable
	public SpellAction getActionAtPath(ActionPath path) {
		return path == null ? null : getActionAt(path);
	}

	public boolean selectPath(ActionPath path) {
		if (path == null) return false;
		SpellAction action = getActionAt(path);
		if (action == null) return false;
		selectedPath = path;
		selectedPaths.clear();
		selectedPaths.add(path);
		selectedAddTarget = null;
		dirty = true;
		onSelect.accept(action, path);
		return true;
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

	public boolean hasLinkedSpellTitle(@Nullable ActionPath path) {
		if (path == null || path.isNested()) return false;
		List<SpellAction> list = getSectionList(path.section());
		int index = path.leafIndex();
		return list != null && index >= 0 && index + 1 < list.size()
				&& list.get(index) instanceof SetSpellHealthAction
				&& list.get(index + 1) instanceof ShowSpellTitleAction;
	}

	public boolean setLinkedSpellTitle(@Nullable ActionPath path, boolean enabled) {
		if (path == null || path.isNested()) return false;
		List<SpellAction> list = getSectionList(path.section());
		int index = path.leafIndex();
		if (list == null || index < 0 || index >= list.size()
				|| !(list.get(index) instanceof SetSpellHealthAction)) return false;
		boolean linked = index + 1 < list.size() && list.get(index + 1) instanceof ShowSpellTitleAction;
		if (linked == enabled) return false;
		pushUndo();
		if (enabled) {
			list.add(index + 1, new ShowSpellTitleAction("", "", 100, 64.0));
		} else {
			list.remove(index + 1);
		}
		dirty = true;
		onMoved.run();
		return true;
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
		SpellDefinition definition = definitionSupplier == null ? null : definitionSupplier.get();
		rows.add(Row.summary(spellHeader(definition), cy));
		cy += ROW_HEIGHT;
		cy = buildSection("onEnter", phase.onEnter, cy, "enter");
		cy = buildSection("onTick", phase.onTick, cy, "tick");
		cy = buildSection("onExit", phase.onExit, cy, "exit");
		buildSection("onDamage", phase.onDamage, cy, "damage");
	}

	private static String spellHeader(@Nullable SpellDefinition definition) {
		if (definition == null) return SpellEditorLocalization.t("Spell Card");
		SpellCardType type = definition.itemForm.cardType() == null
				? SpellCardType.NORMAL : definition.itemForm.cardType();
		String name = definition.display.displayName().getString().trim();
		if (name.isEmpty()) name = definition.id.getPath();
		if (SpellEditorLocalization.isChinese()) {
			String prefix = switch (type) {
				case NORMAL -> "符卡";
				case TIMEOUT_SPELL -> "时符";
				case LAST_SPELL -> "终符";
				case NON_SPELL -> "非符";
			};
			return prefix + "「" + name + "」";
		}
		String prefix = switch (type) {
			case NORMAL -> "Spell Card";
			case TIMEOUT_SPELL -> "Timeout Spell";
			case LAST_SPELL -> "Last Spell";
			case NON_SPELL -> "Non-Spell";
		};
		return prefix + " \"" + name + "\"";
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
		if (inner instanceof dev.xkmc.youkaishomecoming.content.spell.action.HoldSourceAction) return true;
		if (inner instanceof BurstAction) return true;
		if (inner instanceof FireDanmakuAction fda)
			return fda.onExpiry().isPresent() || fda.onTrail().isPresent()
					|| fda.onHitEntity().isPresent() || fda.onHitBlock().isPresent();
		if (inner instanceof FireLaserAction fla)
			return fla.onExpiry().isPresent() || fla.onTrail().isPresent()
					|| fla.onHitEntity().isPresent() || fla.onHitBlock().isPresent();
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

	private String branchCollapseKey(AddTarget target) {
		return collapseKey(target.parentPath()) + "/@" + target.branch();
	}

	private boolean isBranchCollapsed(AddTarget target) {
		return collapsedBranchPaths.contains(branchCollapseKey(target));
	}

	private int buildBranch(List<SpellAction> actions, String branch, String label,
			ActionPath parentPath, int indent, int startY, String section,
			boolean parentDisabled, boolean alwaysShow, boolean showAdd) {
		if (!alwaysShow && actions.isEmpty() && !showAdd) return startY;
		int cy = startY;
		AddTarget target = AddTarget.branch(section, parentPath, branch);
		rows.add(Row.branch(target, label + " (" + actions.size() + ")", indent, cy));
		cy += ROW_HEIGHT;
		if (isBranchCollapsed(target)) return cy;
		for (int j = 0; j < actions.size(); j++) {
			ActionPath childPath = parentPath.child(branch, j);
			cy = buildActionTree(actions.get(j), childPath, indent + 1, cy, section, parentDisabled);
		}
		return cy;
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

	private boolean canAcceptBranchChildren(SpellAction action) {
		SpellAction inner = action instanceof SpellActions.DisabledAction da ? da.inner() : action;
		return hasChildren(inner) || inner instanceof FireDanmakuAction || inner instanceof FireLaserAction;
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

		// Dragged roots are visually collapsed without changing the saved collapse state.
		if (hasChildren(inner) && (collapsedPaths.contains(collapseKey(actionPath))
				|| isDragSourceRoot(actionPath))) {
			return cy;
		}

		boolean showAdd = shouldShowAddButtons(actionPath);

		if (inner instanceof SpellActions.ConditionalAction cond) {
			cy = buildBranch(cond.ifTrue(), "true", "if_true", actionPath,
					indent + 1, cy, section, effectiveDisabled, true, showAdd);
			cy = buildBranch(cond.ifFalse(), "false", "if_false", actionPath,
					indent + 1, cy, section, effectiveDisabled, true, showAdd);
		}

		if (inner instanceof SpellActions.RepeatAction repeat) {
			cy = buildBranch(repeat.body(), "body", "body", actionPath,
					indent + 1, cy, section, effectiveDisabled, true, showAdd);
		}

		if (inner instanceof DelayAction delay) {
			cy = buildBranch(delay.body(), "body", "body", actionPath,
					indent + 1, cy, section, effectiveDisabled, true, showAdd);
		}

		if (inner instanceof dev.xkmc.youkaishomecoming.content.spell.action.HoldSourceAction hold) {
			cy = buildBranch(hold.onRelease(), "onRelease", "onRelease", actionPath,
					indent + 1, cy, section, effectiveDisabled, true, showAdd);
		}

		if (inner instanceof BurstAction burst) {
			cy = buildBranch(burst.body(), "body", "body", actionPath,
					indent + 1, cy, section, effectiveDisabled, true, showAdd);
		}

		if (inner instanceof FireDanmakuAction fda) {
			cy = buildBranch(fda.onExpiry().orElse(List.of()), "onExpiry", "onExpiry", actionPath,
					indent + 1, cy, section, effectiveDisabled, false, showAdd);
			cy = buildBranch(fda.onTrail().orElse(List.of()), "onTrail", "onTrail", actionPath,
					indent + 1, cy, section, effectiveDisabled, false, showAdd);
			boolean entityDiscard = fda.hitBehaviorEntity() == dev.xkmc.youkaishomecoming.content.entity.danmaku.HitBehavior.DISCARD;
			boolean blockDiscard = fda.hitBehaviorBlock() == dev.xkmc.youkaishomecoming.content.entity.danmaku.HitBehavior.DISCARD;
			boolean entityHasNodes = fda.onHitEntity().isPresent() && !fda.onHitEntity().get().isEmpty();
			boolean blockHasNodes = fda.onHitBlock().isPresent() && !fda.onHitBlock().get().isEmpty();

			if (!entityDiscard || entityHasNodes) {
				cy = buildBranch(fda.onHitEntity().orElse(List.of()), "onHitEntity", "onHitEntity", actionPath,
						indent + 1, cy, section, effectiveDisabled, false, showAdd);
			}
			if (!blockDiscard || blockHasNodes) {
				cy = buildBranch(fda.onHitBlock().orElse(List.of()), "onHitBlock", "onHitBlock", actionPath,
						indent + 1, cy, section, effectiveDisabled, false, showAdd);
			}
		}

		if (inner instanceof FireLaserAction fla) {
			cy = buildBranch(fla.onExpiry().orElse(List.of()), "onExpiry", "onExpiry", actionPath,
					indent + 1, cy, section, effectiveDisabled, false, showAdd);
			cy = buildBranch(fla.onTrail().orElse(List.of()), "onTrail", "onTrail", actionPath,
					indent + 1, cy, section, effectiveDisabled, false, showAdd);
			boolean entityDiscard = fla.hitBehaviorEntity() == dev.xkmc.youkaishomecoming.content.entity.danmaku.HitBehavior.DISCARD;
			boolean blockDiscard = fla.hitBehaviorBlock() == dev.xkmc.youkaishomecoming.content.entity.danmaku.HitBehavior.DISCARD;
			boolean entityHasNodes = fla.onHitEntity().isPresent() && !fla.onHitEntity().get().isEmpty();
			boolean blockHasNodes = fla.onHitBlock().isPresent() && !fla.onHitBlock().get().isEmpty();

			if (!entityDiscard || entityHasNodes) {
				cy = buildBranch(fla.onHitEntity().orElse(List.of()), "onHitEntity", "onHitEntity", actionPath,
						indent + 1, cy, section, effectiveDisabled, false, showAdd);
			}
			if (!blockDiscard || blockHasNodes) {
				cy = buildBranch(fla.onHitBlock().orElse(List.of()), "onHitBlock", "onHitBlock", actionPath,
						indent + 1, cy, section, effectiveDisabled, false, showAdd);
			}
		}

		if (inner instanceof SpawnShooterAction ssa) {
			cy = buildBranch(ssa.body(), "body", "body", actionPath,
					indent + 1, cy, section, effectiveDisabled, true, showAdd);
		}

		if (inner instanceof SpellActions.SequenceAction seq) {
			cy = buildBranch(seq.actions(), "actions", "actions", actionPath,
					indent + 1, cy, section, effectiveDisabled, true, showAdd);
		}
		return cy;
	}

	// --- Rendering ---

	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		lastMouseX = mouseX;
		lastMouseY = mouseY;
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

			if (row.kind == RowKind.SUMMARY) {
				g.drawString(font, row.sectionTitle, x + PADDING, row.y + 2, 0xFFE2E8F0, false);
			} else if (row.kind == RowKind.SECTION) {
				String sectionLabel = SpellEditorNodeLabels.sectionMarker(row.section)
						+ SpellEditorLocalization.t(row.sectionTitle);
				g.drawString(font, sectionLabel, x + PADDING, row.y + 2, 0xFF88AACC, false);
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
					boolean collapsed = collapsedPaths.contains(collapseKey(row.path))
							|| isDragSourceRoot(row.path);
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
					String label = SpellEditorNodeLabels.actionMarker(displayAction)
							+ SpellEditorLocalization.t(getDisplayLabel(displayAction, row.path));
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
			} else if (row.kind == RowKind.BRANCH) {
				int ix = x + PADDING + row.indent * INDENT_PX;
				boolean hovered = mouseX >= x && mouseX < x + w
						&& mouseY >= row.y && mouseY < row.y + ROW_HEIGHT;
				boolean selected = row.addTarget != null && row.addTarget.equals(selectedAddTarget);
				if (hovered || selected) {
					g.fill(x + 1, row.y, x + w, row.y + ROW_HEIGHT,
							selected ? 0xFF34402e : 0xFF292d3d);
				}
				boolean collapsed = row.addTarget != null && isBranchCollapsed(row.addTarget);
				String indicator = collapsed ? "\u25B6" : "\u25BC";
				g.drawString(font, indicator, ix, row.y + 2,
						hovered ? 0xFFFFFF66 : 0xFFB8A85C, false);
				ix += font.width(indicator) + 2;
				String branchLabel = SpellEditorNodeLabels.branchMarker(row.addTarget.branch())
						+ SpellEditorLocalization.t(row.addLabel);
				g.drawString(font, branchLabel, ix, row.y + 2,
						selected ? 0xFFFFFF88 : 0xFFD8CA88, false);
				String plus = "[+]";
				int plusX = x + w - font.width(plus) - PADDING;
				boolean plusHovered = mouseX >= plusX && mouseX < x + w
						&& mouseY >= row.y && mouseY < row.y + ROW_HEIGHT;
				g.drawString(font, plus, plusX, row.y + 2,
						plusHovered ? 0xFFFFFF44 : 0xFF66AA66, false);
			}
		}

		// Drag indicator: yellow line for reorder, green highlight for branch insert
		if (isDragging) {
			if (dragBranchTarget != null && dragBranchHighlightY >= y && dragBranchHighlightY <= y + h) {
				// Green highlight on the branch folder row
				g.fill(x + 1, dragBranchHighlightY, x + w, dragBranchHighlightY + ROW_HEIGHT, 0x6644AA44);
				g.fill(x + 1, dragBranchHighlightY, x + 3, dragBranchHighlightY + ROW_HEIGHT, 0xFF44FF44);
			} else if (dragIndicatorY >= y && dragIndicatorY <= y + h) {
				int indicatorX = Math.min(x + w - 8,
						Math.max(x + 2, x + PADDING + Math.max(0, dragIndicatorIndent) * INDENT_PX));
				g.fill(indicatorX, dragIndicatorY - 1, x + w - 2, dragIndicatorY + 1, 0xFFFFFF44);
				// The left marker moves with the target indentation to expose the insertion level.
				g.fill(indicatorX, dragIndicatorY - 4, indicatorX + 4, dragIndicatorY + 4, 0xFFFFFF44);
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
					pendingSingleSelection = null;
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
					pendingSingleSelection = null;
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
				} else if (selectedPaths.size() > 1 && selectedPaths.contains(row.path)) {
					// Preserve the group until release so this gesture can still turn
					// into a multi-node drag. A plain click collapses it on release.
					selectedPath = row.path;
					pendingSingleSelection = row.path;
				} else {
					pendingSingleSelection = null;
					selectedPaths.clear();
					selectedPaths.add(row.path);
					selectedPath = row.path;
				}

				selectedAddTarget = null;
				dirty = true; // rebuild to show/hide add-buttons for newly selected node
				onSelect.accept(row.action, row.path);
				// Start a potential single- or multi-node drag.
				if (!ctrlDown && !shiftDown) {
					dragSourcePath = row.path;
					dragSourceSection = row.path.section;
					dragStartX = mouseX;
					dragStartY = mouseY;
					dragSourceIndent = row.indent;
					dragThresholdMet = false;
				}
				return true;
			} else if (row.kind == RowKind.BRANCH) {
				if (row.addTarget == null) return true;
				String plus = "[+]";
				int plusX = x + w - font.width(plus) - PADDING;
				if (mouseX >= plusX) {
					selectedAddTarget = row.addTarget;
					selectedPath = row.addTarget.parentPath();
					onRequestAdd.accept(row.addTarget);
					return true;
				}
				selectedAddTarget = null;
				String key = branchCollapseKey(row.addTarget);
				if (collapsedBranchPaths.contains(key)) {
					collapsedBranchPaths.remove(key);
				} else {
					collapsedBranchPaths.add(key);
				}
				dirty = true;
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
			dragSourceRoots = getDragSourcePaths();
			dragThresholdMet = true;
			isDragging = true;
			pendingSingleSelection = null;
			dirty = true;
		}

		// Find the insertion point closest to mouse Y
		buildRowsIfDirty();
		updateDragInsertPoint(mouseX, mouseY);
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
		} else if (pendingSingleSelection != null) {
			collapseSelectionTo(pendingSingleSelection);
		}
		cancelDrag();
		return wasDragging;
	}

	private void cancelDrag() {
		isDragging = false;
		dragSourcePath = null;
		dragSourceSection = null;
		dragIndicatorY = -1;
		dragIndicatorIndent = -1;
		dragInsertIndex = -1;
		dragInsertSection = null;
		dragInsertContainerPrefix = null;
		dragBranchTarget = null;
		dragBranchHighlightY = -1;
		dragThresholdMet = false;
		pendingSingleSelection = null;
		if (!dragSourceRoots.isEmpty() || !dragExpandedPaths.isEmpty()) {
			dragSourceRoots = List.of();
			dragExpandedPaths.clear();
			dirty = true;
		}
	}

	/**
	 * Update the drag insertion indicator based on mouse position.
	 * Checks two kinds of drop targets:
	 * 1. Gaps between top-level action rows in the same section (reorder)
	 * 2. Branch folder rows (insert into a nested action list)
	 */
	private void updateDragInsertPoint(double mouseX, double mouseY) {
		dragIndicatorY = -1;
		dragIndicatorIndent = -1;
		dragInsertIndex = -1;
		dragInsertSection = null;
		dragBranchTarget = null;
		dragBranchHighlightY = -1;

		List<ActionPath> sourcePaths = getDragSourcePaths();
		int intentIndent = getDragIntentIndent(mouseX);
		double bestDist = Double.MAX_VALUE;

		// Expand a potential parent only after the cursor explicitly enters its
		// child lane. Vertical dragging in the sibling lane must never open it.
		boolean expandedAny = false;
		for (Row row : rows) {
			if (row.kind == RowKind.ACTION && row.path != null && row.action != null) {
				SpellAction inner = row.action instanceof SpellActions.DisabledAction da ? da.inner() : row.action;
				if (canAcceptBranchChildren(inner) && intentIndent > row.indent
						&& mouseY >= row.y && mouseY < row.y + ROW_HEIGHT) {
					String key = collapseKey(row.path);
					if (collapsedPaths.remove(key) | dragExpandedPaths.add(key)) {
						expandedAny = true;
					}
				}
			}
		}
		if (expandedAny) {
			dirty = true;
			buildRowsIfDirty();
		}

		// A section header is an explicit top-level drop target. This also makes
		// empty onEnter/onTick/onExit/onDamage lists usable without a placeholder.
		for (Row row : rows) {
			if (row.kind != RowKind.SECTION) continue;
			if (intentIndent == 0 && mouseY >= row.y && mouseY < row.y + ROW_HEIGHT) {
				dragIndicatorY = row.y + ROW_HEIGHT;
				dragIndicatorIndent = 1;
				dragInsertIndex = 0;
				dragInsertSection = row.section;
				dragInsertContainerPrefix = null;
				return;
			}
		}

		// Directly hovering an action in the same indentation lane means sibling
		// reorder. Its lower half targets the gap after the entire subtree.
		for (Row row : rows) {
			if (row.kind != RowKind.ACTION || row.path == null || row.indent != intentIndent) continue;
			if (mouseY < row.y || mouseY >= row.y + ROW_HEIGHT) continue;
			List<PathEntry> prefix = row.path.path().subList(0, row.path.path().size() - 1);
			boolean after = mouseY >= row.y + ROW_HEIGHT / 2.0;
			dragIndicatorY = after ? findBottomOfRowSubtree(row) : row.y;
			dragIndicatorIndent = row.indent;
			dragInsertIndex = row.path.leafIndex() + (after ? 1 : 0);
			dragInsertSection = row.path.section();
			dragInsertContainerPrefix = prefix.isEmpty() ? null : new ArrayList<>(prefix);
			return;
		}

		// === Check branch folder rows (branch insert targets) ===
		for (Row row : rows) {
			if (row.kind != RowKind.BRANCH || row.addTarget == null) continue;
			// Skip folders that belong to the dragged action's own subtree.
			// Dropping into self would delete the action then fail to insert (swallowing bug).
			if (row.addTarget.parentPath() != null && isInsideAnySource(row.addTarget.parentPath(), sourcePaths)) {
				continue;
			}
			// Check if mouse is directly over this branch folder row.
			if (row.indent == intentIndent && mouseY >= row.y && mouseY < row.y + ROW_HEIGHT) {
				double dist = Math.abs(mouseY - (row.y + ROW_HEIGHT / 2.0));
				if (dist < bestDist) {
					bestDist = dist;
					// Clear reorder target
					dragIndicatorY = -1;
					dragIndicatorIndent = -1;
					dragInsertIndex = -1;
					dragInsertSection = null;
					// Set branch target
					dragBranchTarget = row.addTarget;
					dragBranchHighlightY = row.y;
				}
				return; // If mouse is directly on a folder, prefer it.
			}
		}

		// === Check gaps between action rows in the same container (reorder) ===
		// A container is either the top-level section list, or a branch list
		// (if_true/if_false/body/onExpiry/...) of a parent action. Rows are grouped
		// by their container prefix path (all entries except the leaf).
		record ContainerKey(String section, List<PathEntry> prefix) {}
		java.util.Map<ContainerKey, List<Row>> containers = new java.util.LinkedHashMap<>();
		String currentSection = null;
		for (Row row : rows) {
			if (row.kind == RowKind.SECTION) {
				currentSection = row.section;
				continue;
			}
			if (row.kind != RowKind.ACTION || row.path == null) continue;
			int n = row.path.path().size();
			List<PathEntry> prefix = row.path.path().subList(0, n - 1);
			containers.computeIfAbsent(new ContainerKey(currentSection, List.copyOf(prefix)), k -> new ArrayList<>()).add(row);
		}
		for (var entry : containers.entrySet()) {
			List<Row> group = entry.getValue();
			if (group.isEmpty() || group.get(0).indent != intentIndent) continue;
			String section = entry.getKey().section();
			List<PathEntry> prefix = entry.getKey().prefix();
			if (!prefix.isEmpty()) {
				// Skip containers inside the dragged action's own subtree (self-drop)
				ActionPath containerPath = new ActionPath(section, prefix);
				if (isInsideAnySource(containerPath, sourcePaths)) continue;
			}
			for (int i = 0; i <= group.size(); i++) {
				int gapY;
				if (i < group.size()) {
					gapY = group.get(i).y;
				} else {
					gapY = findBottomOfRowSubtree(group.get(group.size() - 1));
				}
				double dist = Math.abs(mouseY - gapY);
				if (dist < bestDist) {
					bestDist = dist;
					dragIndicatorY = gapY;
					dragIndicatorIndent = group.get(0).indent;
					dragInsertIndex = i;
					dragInsertSection = section;
					dragInsertContainerPrefix = prefix.isEmpty() ? null : new ArrayList<>(prefix);
					// Clear branch target
					dragBranchTarget = null;
					dragBranchHighlightY = -1;
				}
			}
		}

		// Empty sections have no action container in the map. Offer the gap just
		// below their header when it is the nearest target.
		for (Row row : rows) {
			if (intentIndent != 0 || row.kind != RowKind.SECTION || getSectionList(row.section) == null
					|| !getSectionList(row.section).isEmpty()) continue;
			int gapY = row.y + ROW_HEIGHT;
			double dist = Math.abs(mouseY - gapY);
			if (dist < bestDist) {
				bestDist = dist;
				dragIndicatorY = gapY;
				dragIndicatorIndent = 1;
				dragInsertIndex = 0;
				dragInsertSection = row.section;
				dragInsertContainerPrefix = null;
				dragBranchTarget = null;
				dragBranchHighlightY = -1;
			}
		}
	}

	private int getDragIntentIndent(double mouseX) {
		double delta = mouseX - dragStartX;
		double distance = Math.abs(delta);
		if (distance < DRAG_INDENT_THRESHOLD) return dragSourceIndent;
		int levels = 1 + (int) ((distance - DRAG_INDENT_THRESHOLD) / INDENT_PX);
		return Math.max(0, dragSourceIndent + (delta < 0 ? -levels : levels));
	}

	private void collapseSelectionTo(ActionPath path) {
		SpellAction action = getActionAt(path);
		selectedPaths.clear();
		selectedPaths.add(path);
		selectedPath = path;
		selectedAddTarget = null;
		dirty = true;
		if (action != null) onSelect.accept(action, path);
	}

	private boolean isInsideAnySource(ActionPath path, List<ActionPath> sourcePaths) {
		for (ActionPath source : sourcePaths) {
			if (isSameActionOrDescendant(path, source)) return true;
		}
		return false;
	}

	private boolean isDragSourceRoot(ActionPath path) {
		return isDragging && dragSourceRoots.contains(path);
	}

	/** Selected roots in visual order; descendants of another selected node are implicit. */
	private List<ActionPath> getDragSourcePaths() {
		if (isDragging && !dragSourceRoots.isEmpty()) return dragSourceRoots;
		List<ActionPath> selected = getSelectedPathsInTreeOrder();
		if (!selected.contains(dragSourcePath)) selected = List.of(dragSourcePath);
		return selectedRootPaths(selected);
	}

	private List<ActionPath> selectedRootPaths(List<ActionPath> selected) {
		List<ActionPath> roots = new ArrayList<>();
		for (ActionPath path : selected) {
			boolean covered = false;
			for (ActionPath root : roots) {
				if (isSameActionOrDescendant(path, root)) {
					covered = true;
					break;
				}
			}
			if (!covered) roots.add(path);
		}
		return roots;
	}

	/**
	 * Find the bottom Y of an action's subtree (including nested children and
	 * add-buttons that belong to the subtree).
	 */
	private int findBottomOfRowSubtree(Row actionRow) {
		int bottomY = actionRow.y + ROW_HEIGHT;
		boolean found = false;
		for (Row row : rows) {
			if (row == actionRow) {
				found = true;
				continue;
			}
			if (!found) continue;
			if (row.kind == RowKind.SECTION) {
				break;
			}
			if (row.kind == RowKind.ACTION) {
				if (!isSameActionOrDescendant(row.path, actionRow.path)) break;
				bottomY = row.y + ROW_HEIGHT;
			} else if (row.kind == RowKind.BRANCH) {
				if (row.addTarget != null && row.addTarget.parentPath() != null
						&& isSameActionOrDescendant(row.addTarget.parentPath(), actionRow.path)) {
					bottomY = row.y + ROW_HEIGHT;
				} else {
					break;
				}
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
		List<ActionPath> sourcePaths = getDragSourcePaths();
		if (sourcePaths.isEmpty()) return;
		List<SpellAction> actions = new ArrayList<>();
		for (ActionPath path : sourcePaths) {
			SpellAction action = getActionAt(path);
			if (action == null) return;
			actions.add(action);
		}

		PhaseDefinition beforeDrop = copyPhase(phase);
		if (beforeDrop == null) return;
		pushUndo();
		List<ActionPath> insertedPaths;
		if (dragBranchTarget != null) {
			insertedPaths = moveIntoBranch(sourcePaths, actions, dragBranchTarget, beforeDrop);
		} else if (dragInsertIndex >= 0 && dragInsertSection != null) {
			insertedPaths = moveIntoGap(sourcePaths, actions, beforeDrop);
		} else {
			return;
		}
		if (insertedPaths == null || insertedPaths.isEmpty()) return;

		selectedPaths.clear();
		selectedPaths.addAll(insertedPaths);
		selectedPath = insertedPaths.get(0);
		selectedAddTarget = null;
		dirty = true;
		syncCustomNamesFromActions();
		SpellAction selected = getActionAt(selectedPath);
		if (selected != null) onSelect.accept(selected, selectedPath);
		onMoved.run();
	}

	@Nullable
	private List<ActionPath> moveIntoBranch(List<ActionPath> sourcePaths, List<SpellAction> actions,
			AddTarget target, PhaseDefinition beforeDrop) {
		if (target.parentPath() != null && isInsideAnySource(target.parentPath(), sourcePaths)) return null;
		AddTarget adjustedTarget = target;
		for (int i = sourcePaths.size() - 1; i >= 0; i--) {
			adjustedTarget = adjustAddTargetAfterDelete(adjustedTarget, sourcePaths.get(i));
			if (adjustedTarget == null || !doDeleteAt(sourcePaths.get(i))) {
				restoreDropSnapshot(beforeDrop);
				return null;
			}
		}
		List<ActionPath> inserted = new ArrayList<>();
		for (SpellAction action : actions) {
			if (!insertActionInternal(adjustedTarget, action) || selectedPath == null) {
				restoreDropSnapshot(beforeDrop);
				return null;
			}
			inserted.add(selectedPath);
		}
		return inserted;
	}

	@Nullable
	private List<ActionPath> moveIntoGap(List<ActionPath> sourcePaths, List<SpellAction> actions,
			PhaseDefinition beforeDrop) {
		String targetSection = dragInsertSection;
		List<PathEntry> targetPrefix = dragInsertContainerPrefix == null
				? new ArrayList<>() : new ArrayList<>(dragInsertContainerPrefix);
		ActionPath targetContainer = new ActionPath(targetSection, List.copyOf(targetPrefix));
		if (!targetPrefix.isEmpty() && isInsideAnySource(targetContainer, sourcePaths)) return null;
		int targetIndex = dragInsertIndex;

		for (int i = sourcePaths.size() - 1; i >= 0; i--) {
			ActionPath source = sourcePaths.get(i);
			if (isDirectChildOf(source, targetSection, targetPrefix) && source.leafIndex() < targetIndex) {
				targetIndex--;
			}
			if (!targetPrefix.isEmpty()) {
				ActionPath adjusted = adjustPathAfterDelete(
						new ActionPath(targetSection, List.copyOf(targetPrefix)), source);
				if (adjusted == null) {
					restoreDropSnapshot(beforeDrop);
					return null;
				}
				targetPrefix = new ArrayList<>(adjusted.path());
			}
			if (!doDeleteAt(source)) {
				restoreDropSnapshot(beforeDrop);
				return null;
			}
		}

		List<SpellAction> targetList = getSectionList(targetSection);
		if (targetList == null) {
			restoreDropSnapshot(beforeDrop);
			return null;
		}
		List<ActionPath> inserted = new ArrayList<>();
		if (targetPrefix.isEmpty()) {
			int index = Math.max(0, Math.min(targetIndex, targetList.size()));
			for (int i = 0; i < actions.size(); i++) {
				targetList.add(index + i, actions.get(i));
				inserted.add(ActionPath.topLevel(targetSection, index + i));
			}
			return inserted;
		}

		String branch = targetPrefix.get(targetPrefix.size() - 1).branch();
		if (branch == null) {
			restoreDropSnapshot(beforeDrop);
			return null;
		}
		ActionPath parentPath = new ActionPath(targetSection, List.copyOf(targetPrefix));
		int index = Math.max(0, targetIndex);
		for (int i = 0; i < actions.size(); i++) {
			if (!doInsert(targetList, parentPath.path(), 0, branch, actions.get(i), parentPath, index + i)
					|| selectedPath == null) {
				restoreDropSnapshot(beforeDrop);
				return null;
			}
			inserted.add(selectedPath);
		}
		return inserted;
	}

	private boolean isDirectChildOf(ActionPath path, String section, List<PathEntry> prefix) {
		if (!path.section().equals(section) || path.path().size() != prefix.size() + 1) return false;
		for (int i = 0; i < prefix.size(); i++) {
			if (!samePathEntry(path.path().get(i), prefix.get(i), true)) return false;
		}
		return true;
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
		if (depth >= path.size()) return false;
		int index = path.get(depth).index();
		if (index < 0 || index >= list.size()) return false;
		SpellAction oldAction = list.get(index);
		boolean replaced = doReplaceImpl(list, path, depth, newAction);
		if (replaced && index < list.size()) transferNodeCustomName(oldAction, list.get(index));
		return replaced;
	}

	private boolean doReplaceImpl(List<SpellAction> list, List<PathEntry> path, int depth, SpellAction newAction) {
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
		if (parent instanceof dev.xkmc.youkaishomecoming.content.spell.action.HoldSourceAction hold && "onRelease".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(hold.onRelease());
			if (!doReplace(body, path, depth + 1, newAction)) return false;
			list.set(entry.index, new dev.xkmc.youkaishomecoming.content.spell.action.HoldSourceAction(hold.duration(), body));
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
		if (parent instanceof FireLaserAction fla && "onExpiry".equals(entry.branch)) {
			List<SpellAction> expiryActions = new ArrayList<>(fla.onExpiry().orElse(new ArrayList<>()));
			if (!doReplace(expiryActions, path, depth + 1, newAction)) return false;
			list.set(entry.index, fla.withOnExpiry(Optional.of(expiryActions)));
			return true;
		}
		if (parent instanceof FireLaserAction fla && "onTrail".equals(entry.branch)) {
			List<SpellAction> trailActions = new ArrayList<>(fla.onTrail().orElse(new ArrayList<>()));
			if (!doReplace(trailActions, path, depth + 1, newAction)) return false;
			list.set(entry.index, fla.withOnTrail(Optional.of(trailActions)));
			return true;
		}
		if (parent instanceof FireLaserAction fla && "onHitEntity".equals(entry.branch)) {
			List<SpellAction> hitActions = new ArrayList<>(fla.onHitEntity().orElse(new ArrayList<>()));
			if (!doReplace(hitActions, path, depth + 1, newAction)) return false;
			list.set(entry.index, fla.withOnHitEntity(Optional.of(hitActions)));
			return true;
		}
		if (parent instanceof FireLaserAction fla && "onHitBlock".equals(entry.branch)) {
			List<SpellAction> hitActions = new ArrayList<>(fla.onHitBlock().orElse(new ArrayList<>()));
			if (!doReplace(hitActions, path, depth + 1, newAction)) return false;
			list.set(entry.index, fla.withOnHitBlock(Optional.of(hitActions)));
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
			boolean inserted = doInsert(list, target.parentPath.path, 0, target.branch, action, target.parentPath);
			if (inserted) collapsedBranchPaths.remove(branchCollapseKey(target));
			return inserted;
		}
	}

	private boolean doInsert(List<SpellAction> list, List<PathEntry> path, int depth,
							 String targetBranch, SpellAction newAction, ActionPath parentPath) {
		return doInsert(list, path, depth, targetBranch, newAction, parentPath, -1);
	}

	private boolean doInsert(List<SpellAction> list, List<PathEntry> path, int depth,
							 String targetBranch, SpellAction newAction, ActionPath parentPath, int insertIndex) {
		if (depth >= path.size()) return false;
		int index = path.get(depth).index();
		if (index < 0 || index >= list.size()) return false;
		SpellAction oldAction = list.get(index);
		boolean inserted = doInsertImpl(list, path, depth, targetBranch, newAction, parentPath, insertIndex);
		if (inserted && index < list.size()) transferNodeCustomName(oldAction, list.get(index));
		return inserted;
	}

	private boolean doInsertImpl(List<SpellAction> list, List<PathEntry> path, int depth,
							 String targetBranch, SpellAction newAction, ActionPath parentPath, int insertIndex) {
		PathEntry entry = path.get(depth);
		if (entry.index >= list.size()) return false;

		SpellAction current = list.get(entry.index);

		if (depth == path.size() - 1) {
			// This is the target ConditionalAction, RepeatAction, or FireDanmakuAction
			if (current instanceof SpellActions.ConditionalAction cond) {
				boolean isTrue = "true".equals(targetBranch);
				List<SpellAction> branch = new ArrayList<>(isTrue ? cond.ifTrue() : cond.ifFalse());
				int pos = insertIndex < 0 ? branch.size() : Math.min(insertIndex, branch.size());
				branch.add(pos, newAction);

				SpellActions.ConditionalAction rebuilt = isTrue
						? new SpellActions.ConditionalAction(cond.condition(), branch, cond.ifFalse())
						: new SpellActions.ConditionalAction(cond.condition(), cond.ifTrue(), branch);
				list.set(entry.index, rebuilt);

				selectedPath = parentPath.child(targetBranch, pos);
				return true;
			}
			if (current instanceof SpellActions.RepeatAction repeat && "body".equals(targetBranch)) {
				List<SpellAction> body = new ArrayList<>(repeat.body());
				int pos = insertIndex < 0 ? body.size() : Math.min(insertIndex, body.size());
				body.add(pos, newAction);
				list.set(entry.index, new SpellActions.RepeatAction(repeat.count(), repeat.indexVariable(), body));
				selectedPath = parentPath.child(targetBranch, pos);
				return true;
			}
			if (current instanceof DelayAction delay && "body".equals(targetBranch)) {
				List<SpellAction> body = new ArrayList<>(delay.body());
				int pos = insertIndex < 0 ? body.size() : Math.min(insertIndex, body.size());
				body.add(pos, newAction);
				list.set(entry.index, new DelayAction(delay.delayTicks(), body));
				selectedPath = parentPath.child(targetBranch, pos);
				return true;
			}
			if (current instanceof dev.xkmc.youkaishomecoming.content.spell.action.HoldSourceAction hold && "onRelease".equals(targetBranch)) {
				List<SpellAction> body = new ArrayList<>(hold.onRelease());
				int pos = insertIndex < 0 ? body.size() : Math.min(insertIndex, body.size());
				body.add(pos, newAction);
				list.set(entry.index, new dev.xkmc.youkaishomecoming.content.spell.action.HoldSourceAction(hold.duration(), body));
				selectedPath = parentPath.child(targetBranch, pos);
				return true;
			}
			if (current instanceof BurstAction burst && "body".equals(targetBranch)) {
				List<SpellAction> body = new ArrayList<>(burst.body());
				int pos = insertIndex < 0 ? body.size() : Math.min(insertIndex, body.size());
				body.add(pos, newAction);
				list.set(entry.index, new BurstAction(burst.waves(), burst.interval(), burst.waveVariable(), body));
				selectedPath = parentPath.child(targetBranch, pos);
				return true;
			}
			if (current instanceof SpawnShooterAction ssa && "body".equals(targetBranch)) {
				List<SpellAction> body = new ArrayList<>(ssa.body());
				int pos = insertIndex < 0 ? body.size() : Math.min(insertIndex, body.size());
				body.add(pos, newAction);
				list.set(entry.index, ssa.withBody(body));
				selectedPath = parentPath.child(targetBranch, pos);
				return true;
			}
			if (current instanceof FireDanmakuAction fda && "onExpiry".equals(targetBranch)) {
				List<SpellAction> expiryActions = new ArrayList<>(fda.onExpiry().orElse(new ArrayList<>()));
				int pos = insertIndex < 0 ? expiryActions.size() : Math.min(insertIndex, expiryActions.size());
				expiryActions.add(pos, newAction);
				list.set(entry.index, fda.withOnExpiry(Optional.of(expiryActions)));
				selectedPath = parentPath.child(targetBranch, pos);
				return true;
			}
			if (current instanceof FireDanmakuAction fda && "onTrail".equals(targetBranch)) {
				List<SpellAction> trailActions = new ArrayList<>(fda.onTrail().orElse(new ArrayList<>()));
				int pos = insertIndex < 0 ? trailActions.size() : Math.min(insertIndex, trailActions.size());
				trailActions.add(pos, newAction);
				list.set(entry.index, fda.withOnTrail(Optional.of(trailActions)));
				selectedPath = parentPath.child(targetBranch, pos);
				return true;
			}
			if (current instanceof FireDanmakuAction fda && "onHitEntity".equals(targetBranch)) {
				List<SpellAction> hitActions = new ArrayList<>(fda.onHitEntity().orElse(new ArrayList<>()));
				int pos = insertIndex < 0 ? hitActions.size() : Math.min(insertIndex, hitActions.size());
				hitActions.add(pos, newAction);
				list.set(entry.index, fda.withOnHitEntity(Optional.of(hitActions)));
				selectedPath = parentPath.child(targetBranch, pos);
				return true;
			}
			if (current instanceof FireDanmakuAction fda && "onHitBlock".equals(targetBranch)) {
				List<SpellAction> hitActions = new ArrayList<>(fda.onHitBlock().orElse(new ArrayList<>()));
				int pos = insertIndex < 0 ? hitActions.size() : Math.min(insertIndex, hitActions.size());
				hitActions.add(pos, newAction);
				list.set(entry.index, fda.withOnHitBlock(Optional.of(hitActions)));
				selectedPath = parentPath.child(targetBranch, pos);
				return true;
			}
			if (current instanceof FireLaserAction fla && "onExpiry".equals(targetBranch)) {
				List<SpellAction> expiryActions = new ArrayList<>(fla.onExpiry().orElse(new ArrayList<>()));
				int pos = insertIndex < 0 ? expiryActions.size() : Math.min(insertIndex, expiryActions.size());
				expiryActions.add(pos, newAction);
				list.set(entry.index, fla.withOnExpiry(Optional.of(expiryActions)));
				selectedPath = parentPath.child(targetBranch, pos);
				return true;
			}
			if (current instanceof FireLaserAction fla && "onTrail".equals(targetBranch)) {
				List<SpellAction> trailActions = new ArrayList<>(fla.onTrail().orElse(new ArrayList<>()));
				int pos = insertIndex < 0 ? trailActions.size() : Math.min(insertIndex, trailActions.size());
				trailActions.add(pos, newAction);
				list.set(entry.index, fla.withOnTrail(Optional.of(trailActions)));
				selectedPath = parentPath.child(targetBranch, pos);
				return true;
			}
			if (current instanceof FireLaserAction fla && "onHitEntity".equals(targetBranch)) {
				List<SpellAction> hitActions = new ArrayList<>(fla.onHitEntity().orElse(new ArrayList<>()));
				int pos = insertIndex < 0 ? hitActions.size() : Math.min(insertIndex, hitActions.size());
				hitActions.add(pos, newAction);
				list.set(entry.index, fla.withOnHitEntity(Optional.of(hitActions)));
				selectedPath = parentPath.child(targetBranch, pos);
				return true;
			}
			if (current instanceof FireLaserAction fla && "onHitBlock".equals(targetBranch)) {
				List<SpellAction> hitActions = new ArrayList<>(fla.onHitBlock().orElse(new ArrayList<>()));
				int pos = insertIndex < 0 ? hitActions.size() : Math.min(insertIndex, hitActions.size());
				hitActions.add(pos, newAction);
				list.set(entry.index, fla.withOnHitBlock(Optional.of(hitActions)));
				selectedPath = parentPath.child(targetBranch, pos);
				return true;
			}
			if (current instanceof SpellActions.SequenceAction seq && "actions".equals(targetBranch)) {
				List<SpellAction> actions = new ArrayList<>(seq.actions());
				int pos = insertIndex < 0 ? actions.size() : Math.min(insertIndex, actions.size());
				actions.add(pos, newAction);
				list.set(entry.index, new SpellActions.SequenceAction(actions));
				selectedPath = parentPath.child(targetBranch, pos);
				return true;
			}
			return false;
		}

		// Navigate deeper
		if (current instanceof SpellActions.ConditionalAction cond && entry.branch != null) {
			boolean isTrue = "true".equals(entry.branch);
			List<SpellAction> childList = new ArrayList<>(isTrue ? cond.ifTrue() : cond.ifFalse());
			if (!doInsert(childList, path, depth + 1, targetBranch, newAction, parentPath, insertIndex)) return false;

			SpellActions.ConditionalAction rebuilt = isTrue
					? new SpellActions.ConditionalAction(cond.condition(), childList, cond.ifFalse())
					: new SpellActions.ConditionalAction(cond.condition(), cond.ifTrue(), childList);
			list.set(entry.index, rebuilt);
			return true;
		}
		if (current instanceof SpellActions.RepeatAction repeat && "body".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(repeat.body());
			if (!doInsert(body, path, depth + 1, targetBranch, newAction, parentPath, insertIndex)) return false;
			list.set(entry.index, new SpellActions.RepeatAction(repeat.count(), repeat.indexVariable(), body));
			return true;
		}
		if (current instanceof DelayAction delay && "body".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(delay.body());
			if (!doInsert(body, path, depth + 1, targetBranch, newAction, parentPath, insertIndex)) return false;
			list.set(entry.index, new DelayAction(delay.delayTicks(), body));
			return true;
		}
		if (current instanceof dev.xkmc.youkaishomecoming.content.spell.action.HoldSourceAction hold && "onRelease".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(hold.onRelease());
			if (!doInsert(body, path, depth + 1, targetBranch, newAction, parentPath, insertIndex)) return false;
			list.set(entry.index, new dev.xkmc.youkaishomecoming.content.spell.action.HoldSourceAction(hold.duration(), body));
			return true;
		}
		if (current instanceof BurstAction burst && "body".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(burst.body());
			if (!doInsert(body, path, depth + 1, targetBranch, newAction, parentPath, insertIndex)) return false;
			list.set(entry.index, new BurstAction(burst.waves(), burst.interval(), burst.waveVariable(), body));
			return true;
		}
		if (current instanceof SpawnShooterAction ssa && "body".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(ssa.body());
			if (!doInsert(body, path, depth + 1, targetBranch, newAction, parentPath, insertIndex)) return false;
			list.set(entry.index, ssa.withBody(body));
			return true;
		}
		if (current instanceof FireDanmakuAction fda && "onExpiry".equals(entry.branch)) {
			List<SpellAction> expiryActions = new ArrayList<>(fda.onExpiry().orElse(new ArrayList<>()));
			if (!doInsert(expiryActions, path, depth + 1, targetBranch, newAction, parentPath, insertIndex)) return false;
			list.set(entry.index, fda.withOnExpiry(Optional.of(expiryActions)));
			return true;
		}
		if (current instanceof FireDanmakuAction fda && "onTrail".equals(entry.branch)) {
			List<SpellAction> trailActions = new ArrayList<>(fda.onTrail().orElse(new ArrayList<>()));
			if (!doInsert(trailActions, path, depth + 1, targetBranch, newAction, parentPath, insertIndex)) return false;
			list.set(entry.index, fda.withOnTrail(Optional.of(trailActions)));
			return true;
		}
		if (current instanceof FireDanmakuAction fda && "onHitEntity".equals(entry.branch)) {
			List<SpellAction> hitActions = new ArrayList<>(fda.onHitEntity().orElse(new ArrayList<>()));
			if (!doInsert(hitActions, path, depth + 1, targetBranch, newAction, parentPath, insertIndex)) return false;
			list.set(entry.index, fda.withOnHitEntity(Optional.of(hitActions)));
			return true;
		}
		if (current instanceof FireDanmakuAction fda && "onHitBlock".equals(entry.branch)) {
			List<SpellAction> hitActions = new ArrayList<>(fda.onHitBlock().orElse(new ArrayList<>()));
			if (!doInsert(hitActions, path, depth + 1, targetBranch, newAction, parentPath, insertIndex)) return false;
			list.set(entry.index, fda.withOnHitBlock(Optional.of(hitActions)));
			return true;
		}
		if (current instanceof FireLaserAction fla && "onExpiry".equals(entry.branch)) {
			List<SpellAction> expiryActions = new ArrayList<>(fla.onExpiry().orElse(new ArrayList<>()));
			if (!doInsert(expiryActions, path, depth + 1, targetBranch, newAction, parentPath, insertIndex)) return false;
			list.set(entry.index, fla.withOnExpiry(Optional.of(expiryActions)));
			return true;
		}
		if (current instanceof FireLaserAction fla && "onTrail".equals(entry.branch)) {
			List<SpellAction> trailActions = new ArrayList<>(fla.onTrail().orElse(new ArrayList<>()));
			if (!doInsert(trailActions, path, depth + 1, targetBranch, newAction, parentPath, insertIndex)) return false;
			list.set(entry.index, fla.withOnTrail(Optional.of(trailActions)));
			return true;
		}
		if (current instanceof FireLaserAction fla && "onHitEntity".equals(entry.branch)) {
			List<SpellAction> hitActions = new ArrayList<>(fla.onHitEntity().orElse(new ArrayList<>()));
			if (!doInsert(hitActions, path, depth + 1, targetBranch, newAction, parentPath, insertIndex)) return false;
			list.set(entry.index, fla.withOnHitEntity(Optional.of(hitActions)));
			return true;
		}
		if (current instanceof FireLaserAction fla && "onHitBlock".equals(entry.branch)) {
			List<SpellAction> hitActions = new ArrayList<>(fla.onHitBlock().orElse(new ArrayList<>()));
			if (!doInsert(hitActions, path, depth + 1, targetBranch, newAction, parentPath, insertIndex)) return false;
			list.set(entry.index, fla.withOnHitBlock(Optional.of(hitActions)));
			return true;
		}
		if (current instanceof SpellActions.SequenceAction seq && "actions".equals(entry.branch)) {
			List<SpellAction> actions = new ArrayList<>(seq.actions());
			if (!doInsert(actions, path, depth + 1, targetBranch, newAction, parentPath, insertIndex)) return false;
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
		if (depth >= path.size()) return false;
		int index = path.get(depth).index();
		if (index < 0 || index >= list.size()) return false;
		int oldSize = list.size();
		SpellAction oldAction = list.get(index);
		boolean deleted = doDeleteImpl(list, path, depth);
		if (deleted && list.size() == oldSize && index < list.size()) {
			transferNodeCustomName(oldAction, list.get(index));
		}
		return deleted;
	}

	private boolean doDeleteImpl(List<SpellAction> list, List<PathEntry> path, int depth) {
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
		if (parent instanceof dev.xkmc.youkaishomecoming.content.spell.action.HoldSourceAction hold && "onRelease".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(hold.onRelease());
			if (!doDelete(body, path, depth + 1)) return false;
			list.set(entry.index, new dev.xkmc.youkaishomecoming.content.spell.action.HoldSourceAction(hold.duration(), body));
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
		if (parent instanceof FireLaserAction fla && "onExpiry".equals(entry.branch)) {
			List<SpellAction> expiryActions = new ArrayList<>(fla.onExpiry().orElse(new ArrayList<>()));
			if (!doDelete(expiryActions, path, depth + 1)) return false;
			list.set(entry.index, fla.withOnExpiry(expiryActions.isEmpty() ? Optional.empty() : Optional.of(expiryActions)));
			return true;
		}
		if (parent instanceof FireLaserAction fla && "onTrail".equals(entry.branch)) {
			List<SpellAction> trailActions = new ArrayList<>(fla.onTrail().orElse(new ArrayList<>()));
			if (!doDelete(trailActions, path, depth + 1)) return false;
			list.set(entry.index, fla.withOnTrail(trailActions.isEmpty() ? Optional.empty() : Optional.of(trailActions)));
			return true;
		}
		if (parent instanceof FireLaserAction fla && "onHitEntity".equals(entry.branch)) {
			List<SpellAction> hitActions = new ArrayList<>(fla.onHitEntity().orElse(new ArrayList<>()));
			if (!doDelete(hitActions, path, depth + 1)) return false;
			list.set(entry.index, fla.withOnHitEntity(hitActions.isEmpty() ? Optional.empty() : Optional.of(hitActions)));
			return true;
		}
		if (parent instanceof FireLaserAction fla && "onHitBlock".equals(entry.branch)) {
			List<SpellAction> hitActions = new ArrayList<>(fla.onHitBlock().orElse(new ArrayList<>()));
			if (!doDelete(hitActions, path, depth + 1)) return false;
			list.set(entry.index, fla.withOnHitBlock(hitActions.isEmpty() ? Optional.empty() : Optional.of(hitActions)));
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

	private record ClipboardItem(SpellAction action, @Nullable String customName) {}

	private static List<ClipboardItem> clipboard = null;

	/** Deep-copy an action via Codec round-trip, falling back to the original. */
	private static SpellAction deepCopy(SpellAction action) {
		try {
			var json = SpellAction.CODEC.encodeStart(
					com.mojang.serialization.JsonOps.INSTANCE, action).result().orElse(null);
			if (json != null) {
				SpellAction parsed = SpellAction.CODEC.parse(
						com.mojang.serialization.JsonOps.INSTANCE, json).result().orElse(null);
				if (parsed != null) return parsed;
			}
		} catch (Exception e) {
			// fallback: shallow copy
		}
		return action;
	}

	/** Selected paths in tree (visual) order. */
	private List<ActionPath> getSelectedPathsInTreeOrder() {
		buildRowsIfDirty();
		List<ActionPath> list = new ArrayList<>();
		for (Row row : rows) {
			if (row.kind == RowKind.ACTION && row.path != null && isSelected(row.path)) {
				list.add(row.path);
			}
		}
		return list;
	}

	/**
	 * Deep-copy all selected actions (multi-select aware) to clipboard.
	 */
	public boolean copySelected() {
		if (phase == null) return false;
		List<ActionPath> paths = selectedRootPaths(getSelectedPathsInTreeOrder());
		if (paths.isEmpty()) return false;
		List<ClipboardItem> copied = new ArrayList<>();
		for (ActionPath path : paths) {
			SpellAction action = getActionAt(path);
			if (action != null) {
				copied.add(new ClipboardItem(deepCopy(action), customNameFor(action, path)));
			}
		}
		if (copied.isEmpty()) return false;
		clipboard = copied;
		return true;
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
		// Descendants of a selected parent are implicit. Reverse visual order keeps
		// all remaining paths valid while siblings and sections shrink.
		var paths = selectedRootPaths(getSelectedPathsInTreeOrder());
		java.util.Collections.reverse(paths);
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
		if (!copySelected()) return false;
		return deleteMultipleSelected();
	}

	/** Find the branch folder under the mouse (hover-based paste target). */
	@Nullable
	private AddTarget findAddButtonAt(double mouseX, double mouseY) {
		if (phase == null) return null;
		buildRowsIfDirty();
		for (Row row : rows) {
			if (row.kind != RowKind.BRANCH || row.addTarget == null) continue;
			if (mouseX >= x && mouseX < x + w && mouseY >= row.y && mouseY < row.y + ROW_HEIGHT) {
				return row.addTarget;
			}
		}
		return null;
	}

	/** Insert all actions into the given branch at the given running index. */
	private boolean insertManyIntoBranch(String section, List<PathEntry> parentEntries, String branch,
										 int startIndex, List<SpellAction> actions) {
		ActionPath parentPath = new ActionPath(section, parentEntries);
		List<SpellAction> list = getSectionList(section);
		if (list == null) return false;
		for (int i = 0; i < actions.size(); i++) {
			if (!doInsert(list, parentPath.path(), 0, branch, actions.get(i), parentPath, startIndex + i)) {
				return false;
			}
		}
		return true;
	}

	public boolean pasteAfterSelected() {
		if (clipboard == null || clipboard.isEmpty() || phase == null) return false;
		// Deep copy clipboard for paste
		List<ClipboardItem> toPaste = new ArrayList<>();
		for (ClipboardItem item : clipboard) {
			toPaste.add(new ClipboardItem(deepCopy(item.action()), item.customName()));
		}
		if (toPaste.isEmpty()) return false;

		// If hovering an add-button (e.g. "+ if_true"), paste into that branch
		AddTarget hovered = findAddButtonAt(lastMouseX, lastMouseY);
		if (hovered != null && hovered.isBranch()) {
			pushUndo();
			for (ClipboardItem item : toPaste) {
				if (!insertActionInternal(hovered, item.action())) break;
				rememberClipboardName(selectedPath, item);
			}
			selectedAddTarget = null;
			dirty = true;
			SpellAction last = getSelectedAction();
			if (last != null) onSelect.accept(last, selectedPath);
			return true;
		}

		// If an add-button is selected, paste into that branch/section
		if (selectedAddTarget != null) {
			pushUndo();
			for (ClipboardItem item : toPaste) {
				if (!insertActionInternal(selectedAddTarget, item.action())) break;
				rememberClipboardName(selectedPath, item);
			}
			selectedAddTarget = null;
			dirty = true;
			SpellAction last = getSelectedAction();
			if (last != null) onSelect.accept(last, selectedPath);
			return true;
		}

		if (selectedPath != null) {
			List<SpellAction> list = getSectionList(selectedPath.section);
			if (list != null) {
				pushUndo();
				if (!selectedPath.isNested()) {
					// Insert after selected action in the same top-level list
					int idx = selectedPath.leafIndex();
					List<SpellAction> actions = toPaste.stream().map(ClipboardItem::action).toList();
					list.addAll(idx + 1, actions);
					for (int i = 0; i < toPaste.size(); i++) {
						rememberClipboardName(ActionPath.topLevel(selectedPath.section, idx + 1 + i), toPaste.get(i));
					}
					selectedPath = ActionPath.topLevel(selectedPath.section, idx + toPaste.size());
				} else {
					// Insert after selected action within its own branch
					int idx = selectedPath.leafIndex();
					List<PathEntry> parentEntries = selectedPath.path.subList(0, selectedPath.path.size() - 1);
					String branch = selectedPath.path.get(selectedPath.path.size() - 2).branch();
					List<SpellAction> actions = toPaste.stream().map(ClipboardItem::action).toList();
					insertManyIntoBranch(selectedPath.section, parentEntries, branch, idx + 1, actions);
					ActionPath parentPath = new ActionPath(selectedPath.section, parentEntries);
					for (int i = 0; i < toPaste.size(); i++) {
						rememberClipboardName(parentPath.child(branch, idx + 1 + i), toPaste.get(i));
					}
				}
				dirty = true;
				SpellAction last = getSelectedAction();
				if (last != null) onSelect.accept(last, selectedPath);
				return true;
			}
		}
		// Default: add to onTick
		var tickList = getSectionList("tick");
		if (tickList != null) {
			pushUndo();
			int start = tickList.size();
			tickList.addAll(toPaste.stream().map(ClipboardItem::action).toList());
			for (int i = 0; i < toPaste.size(); i++) {
				rememberClipboardName(ActionPath.topLevel("tick", start + i), toPaste.get(i));
			}
			selectedPath = ActionPath.topLevel("tick", tickList.size() - 1);
			dirty = true;
			SpellAction last = getSelectedAction();
			if (last != null) onSelect.accept(last, selectedPath);
			return true;
		}
		return false;
	}

	private void rememberClipboardName(@Nullable ActionPath path, ClipboardItem item) {
		if (path != null && item.customName() != null && !item.customName().isBlank()) {
			setNodeCustomName(item.action(), path, item.customName());
		}
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
		if (action instanceof dev.xkmc.youkaishomecoming.content.spell.action.HoldSourceAction hold && "onRelease".equals(entry.branch)) {
			return getActionRecursive(hold.onRelease(), path, depth + 1);
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
		if (action instanceof FireLaserAction fla && "onExpiry".equals(entry.branch)) {
			return getActionRecursive(fla.onExpiry().orElse(List.of()), path, depth + 1);
		}
		if (action instanceof FireLaserAction fla && "onTrail".equals(entry.branch)) {
			return getActionRecursive(fla.onTrail().orElse(List.of()), path, depth + 1);
		}
		if (action instanceof FireLaserAction fla && "onHitEntity".equals(entry.branch)) {
			return getActionRecursive(fla.onHitEntity().orElse(List.of()), path, depth + 1);
		}
		if (action instanceof FireLaserAction fla && "onHitBlock".equals(entry.branch)) {
			return getActionRecursive(fla.onHitBlock().orElse(List.of()), path, depth + 1);
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
		collapsedBranchPaths.clear();
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
			collapsedBranchPaths.add(branchCollapseKey(AddTarget.branch(path.section(), path, "true")));
			collapsedBranchPaths.add(branchCollapseKey(AddTarget.branch(path.section(), path, "false")));
			for (int j = 0; j < cond.ifTrue().size(); j++)
				collapseAllRecursive(cond.ifTrue().get(j), path.child("true", j));
			for (int j = 0; j < cond.ifFalse().size(); j++)
				collapseAllRecursive(cond.ifFalse().get(j), path.child("false", j));
		}
		if (inner instanceof SpellActions.RepeatAction r) {
			collapsedBranchPaths.add(branchCollapseKey(AddTarget.branch(path.section(), path, "body")));
			for (int j = 0; j < r.body().size(); j++)
				collapseAllRecursive(r.body().get(j), path.child("body", j));
		}
		if (inner instanceof DelayAction d) {
			collapsedBranchPaths.add(branchCollapseKey(AddTarget.branch(path.section(), path, "body")));
			for (int j = 0; j < d.body().size(); j++)
				collapseAllRecursive(d.body().get(j), path.child("body", j));
		}
		if (inner instanceof dev.xkmc.youkaishomecoming.content.spell.action.HoldSourceAction hold) {
			collapsedBranchPaths.add(branchCollapseKey(AddTarget.branch(path.section(), path, "onRelease")));
			for (int j = 0; j < hold.onRelease().size(); j++)
				collapseAllRecursive(hold.onRelease().get(j), path.child("onRelease", j));
		}
		if (inner instanceof BurstAction b) {
			collapsedBranchPaths.add(branchCollapseKey(AddTarget.branch(path.section(), path, "body")));
			for (int j = 0; j < b.body().size(); j++)
				collapseAllRecursive(b.body().get(j), path.child("body", j));
		}
		if (inner instanceof FireDanmakuAction fda) {
			if (fda.onExpiry().isPresent()) collapsedBranchPaths.add(branchCollapseKey(AddTarget.branch(path.section(), path, "onExpiry")));
			if (fda.onTrail().isPresent()) collapsedBranchPaths.add(branchCollapseKey(AddTarget.branch(path.section(), path, "onTrail")));
			if (fda.onHitEntity().isPresent()) collapsedBranchPaths.add(branchCollapseKey(AddTarget.branch(path.section(), path, "onHitEntity")));
			if (fda.onHitBlock().isPresent()) collapsedBranchPaths.add(branchCollapseKey(AddTarget.branch(path.section(), path, "onHitBlock")));
			for (int j = 0; j < fda.onExpiry().orElse(List.of()).size(); j++)
				collapseAllRecursive(fda.onExpiry().get().get(j), path.child("onExpiry", j));
			for (int j = 0; j < fda.onTrail().orElse(List.of()).size(); j++)
				collapseAllRecursive(fda.onTrail().get().get(j), path.child("onTrail", j));
			for (int j = 0; j < fda.onHitEntity().orElse(List.of()).size(); j++)
				collapseAllRecursive(fda.onHitEntity().get().get(j), path.child("onHitEntity", j));
			for (int j = 0; j < fda.onHitBlock().orElse(List.of()).size(); j++)
				collapseAllRecursive(fda.onHitBlock().get().get(j), path.child("onHitBlock", j));
		}
		if (inner instanceof FireLaserAction fla) {
			if (fla.onExpiry().isPresent()) collapsedBranchPaths.add(branchCollapseKey(AddTarget.branch(path.section(), path, "onExpiry")));
			if (fla.onTrail().isPresent()) collapsedBranchPaths.add(branchCollapseKey(AddTarget.branch(path.section(), path, "onTrail")));
			if (fla.onHitEntity().isPresent()) collapsedBranchPaths.add(branchCollapseKey(AddTarget.branch(path.section(), path, "onHitEntity")));
			if (fla.onHitBlock().isPresent()) collapsedBranchPaths.add(branchCollapseKey(AddTarget.branch(path.section(), path, "onHitBlock")));
			for (int j = 0; j < fla.onExpiry().orElse(List.of()).size(); j++)
				collapseAllRecursive(fla.onExpiry().get().get(j), path.child("onExpiry", j));
			for (int j = 0; j < fla.onTrail().orElse(List.of()).size(); j++)
				collapseAllRecursive(fla.onTrail().get().get(j), path.child("onTrail", j));
			for (int j = 0; j < fla.onHitEntity().orElse(List.of()).size(); j++)
				collapseAllRecursive(fla.onHitEntity().get().get(j), path.child("onHitEntity", j));
			for (int j = 0; j < fla.onHitBlock().orElse(List.of()).size(); j++)
				collapseAllRecursive(fla.onHitBlock().get().get(j), path.child("onHitBlock", j));
		}
		if (inner instanceof SpawnShooterAction ssa) {
			collapsedBranchPaths.add(branchCollapseKey(AddTarget.branch(path.section(), path, "body")));
			for (int j = 0; j < ssa.body().size(); j++)
				collapseAllRecursive(ssa.body().get(j), path.child("body", j));
		}
		if (inner instanceof SpellActions.SequenceAction seq) {
			collapsedBranchPaths.add(branchCollapseKey(AddTarget.branch(path.section(), path, "actions")));
			for (int j = 0; j < seq.actions().size(); j++)
				collapseAllRecursive(seq.actions().get(j), path.child("actions", j));
		}
	}

	/** Expand all nodes. */
	public void expandAll() {
		collapsedPaths.clear();
		collapsedBranchPaths.clear();
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
		String existing = customNameFor(action, path);
		renamingText = existing != null ? existing : "";
	}

	private void finishRename() {
		if (renamingPath != null) {
			setNodeCustomName(getActionAt(renamingPath), renamingPath,
					renamingText.isEmpty() ? null : renamingText);
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
		SpellAction oldAction = list.get(entry.index);
		boolean toggled = doToggleDisabledImpl(list, path, depth);
		if (toggled && entry.index < list.size()) transferNodeCustomName(oldAction, list.get(entry.index));
		return toggled;
	}

	private boolean doToggleDisabledImpl(List<SpellAction> list, List<PathEntry> path, int depth) {
		PathEntry entry = path.get(depth);
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
		if (parent instanceof dev.xkmc.youkaishomecoming.content.spell.action.HoldSourceAction hold && "onRelease".equals(entry.branch)) {
			List<SpellAction> body = new ArrayList<>(hold.onRelease());
			if (!doToggleDisabled(body, path, depth + 1)) return false;
			var rebuilt = new dev.xkmc.youkaishomecoming.content.spell.action.HoldSourceAction(hold.duration(), body);
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
		if (parent instanceof FireLaserAction fla && "onExpiry".equals(entry.branch)) {
			List<SpellAction> expiryActions = new ArrayList<>(fla.onExpiry().orElse(new ArrayList<>()));
			if (!doToggleDisabled(expiryActions, path, depth + 1)) return false;
			var rebuilt = fla.withOnExpiry(expiryActions.isEmpty() ? Optional.empty() : Optional.of(expiryActions));
			list.set(entry.index, current instanceof SpellActions.DisabledAction ? new SpellActions.DisabledAction(rebuilt) : rebuilt);
			return true;
		}
		if (parent instanceof FireLaserAction fla && "onTrail".equals(entry.branch)) {
			List<SpellAction> trailActions = new ArrayList<>(fla.onTrail().orElse(new ArrayList<>()));
			if (!doToggleDisabled(trailActions, path, depth + 1)) return false;
			var rebuilt = fla.withOnTrail(trailActions.isEmpty() ? Optional.empty() : Optional.of(trailActions));
			list.set(entry.index, current instanceof SpellActions.DisabledAction ? new SpellActions.DisabledAction(rebuilt) : rebuilt);
			return true;
		}
		if (parent instanceof FireLaserAction fla && "onHitEntity".equals(entry.branch)) {
			List<SpellAction> hitActions = new ArrayList<>(fla.onHitEntity().orElse(new ArrayList<>()));
			if (!doToggleDisabled(hitActions, path, depth + 1)) return false;
			var rebuilt = fla.withOnHitEntity(hitActions.isEmpty() ? Optional.empty() : Optional.of(hitActions));
			list.set(entry.index, current instanceof SpellActions.DisabledAction ? new SpellActions.DisabledAction(rebuilt) : rebuilt);
			return true;
		}
		if (parent instanceof FireLaserAction fla && "onHitBlock".equals(entry.branch)) {
			List<SpellAction> hitActions = new ArrayList<>(fla.onHitBlock().orElse(new ArrayList<>()));
			if (!doToggleDisabled(hitActions, path, depth + 1)) return false;
			var rebuilt = fla.withOnHitBlock(hitActions.isEmpty() ? Optional.empty() : Optional.of(hitActions));
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
			String custom = customNameFor(action, path);
			if (custom != null && !custom.isEmpty()) {
				return localizeCustomName(custom);
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
			String colorLabel = fda.color() instanceof ColorProvider.Constant cc ? cc.color().format() : "dynamic";
			if (fda.colorAnimation().isPresent()) colorLabel += "+anim";
			String bulletLabel = fda.bulletType() instanceof BulletProvider.Constant bc ? bc.bullet().name().toLowerCase() : "dynamic";
			return index + ": fire " + bulletLabel + " " + colorLabel;
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
		if (action instanceof ShowSpellTitleAction sta) {
			return index + ": show title " + sta.duration() + "t r=" + sta.radius();
		}
		if (action instanceof SetSpellCircleAction sca) {
			return switch (sca.mode()) {
				case SET -> index + ": spell circle " + formatResourceId(sca.circle()) + " size=" + sca.size();
				case OFF -> index + ": spell circle off";
				case CLEAR -> index + ": spell circle clear";
			};
		}
		if (action instanceof SetSpellHealthAction sha) {
			return sha.mode() == SetSpellHealthAction.Mode.CLEAR
					? index + ": spell initialization clear"
					: index + ": spell initialization hp=" + formatNumberProvider(sha.health())
					+ " duration=" + formatNumberProvider(sha.duration())
					+ describeSpellHealthTarget(" timeout", sha.onTimeout())
					+ describeSpellHealthTarget(" break", sha.onBreak());
		}
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
			String pattern = " " + formatNumberProvider(ssa.count()) + "x" + ssa.pattern().name().toLowerCase();
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

	private String describeSpellHealthTarget(String label, Optional<SpellAction> target) {
		if (target.orElse(null) instanceof SpellActions.ForcePhase phase) {
			return label + "->phase:" + describePhaseTarget(phase.phaseId());
		}
		if (target.orElse(null) instanceof SpellActions.ForceSpell spell) {
			return label + "->spell:" + formatResourceId(spell.spellId());
		}
		return "";
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
		return localizeCustomName(custom) + " (" + formatPhaseId(phaseId) + ")";
	}

	private static String localizeCustomName(String value) {
		if (value.startsWith("youkaishomecoming.spell_template.") && I18n.exists(value)) {
			return I18n.get(value);
		}
		return value;
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
