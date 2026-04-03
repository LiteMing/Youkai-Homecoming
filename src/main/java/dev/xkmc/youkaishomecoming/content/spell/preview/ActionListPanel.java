package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.spell.action.*;
import dev.xkmc.youkaishomecoming.content.spell.condition.*;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProviders;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
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

	private record Row(RowKind kind, int y, int indent,
					   String section, String sectionTitle,
					   ActionPath path, SpellAction action,
					   AddTarget addTarget, String addLabel) {
		static Row section(String section, String title, int y) {
			return new Row(RowKind.SECTION, y, 0, section, title, null, null, null, null);
		}

		static Row action(ActionPath path, SpellAction action, int indent, int y) {
			return new Row(RowKind.ACTION, y, indent, null, null, path, action, null, null);
		}

		static Row addButton(AddTarget target, String label, int indent, int y) {
			return new Row(RowKind.ADD_BUTTON, y, indent, null, null, null, null, target, label);
		}
	}

	private int x, y, w, h;
	private PhaseDefinition phase;
	private ActionPath selectedPath;
	private final List<Row> rows = new ArrayList<>();
	private int scrollOffset = 0;
	private boolean dirty = true;

	// Drag state
	private boolean isDragging = false;
	private ActionPath dragSourcePath = null;
	private String dragSourceSection = null;
	private double dragStartX, dragStartY;
	private static final int DRAG_THRESHOLD = 4;
	private boolean dragThresholdMet = false;

	// Drop target: either a gap between top-level rows or an AddTarget (branch insert)
	private int dragIndicatorY = -1;       // Y for the indicator line (reorder mode)
	private int dragInsertIndex = -1;      // index for reorder within section
	private String dragInsertSection = null;
	private AddTarget dragBranchTarget = null;  // non-null when dropping into a branch
	private int dragBranchHighlightY = -1; // Y of the highlighted add-button row

	private final BiConsumer<SpellAction, ActionPath> onSelect;
	private final Consumer<AddTarget> onRequestAdd;
	private final Runnable onMoved;

	public ActionListPanel(BiConsumer<SpellAction, ActionPath> onSelect, Consumer<AddTarget> onRequestAdd, Runnable onMoved) {
		this.onSelect = onSelect;
		this.onRequestAdd = onRequestAdd;
		this.onMoved = onMoved;
	}

	public void setBounds(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
	}

	public void setPhase(PhaseDefinition phase) {
		this.phase = phase;
		this.selectedPath = null;
		this.scrollOffset = 0;
		this.dirty = true;
	}

	public ActionPath getSelectedPath() {
		return selectedPath;
	}

	public void clearSelection() {
		selectedPath = null;
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
		buildSection("onExit", phase.onExit, cy, "exit");
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

	/**
	 * Recursively build tree rows for an action. If the action is a ConditionalAction,
	 * its if_true/if_false branches are rendered as indented sub-trees.
	 */
	private int buildActionTree(SpellAction action, ActionPath actionPath, int indent, int startY, String section) {
		int cy = startY;
		rows.add(Row.action(actionPath, action, indent, cy));
		cy += ROW_HEIGHT;

		if (action instanceof SpellActions.ConditionalAction cond) {
			// if_true sub-actions (recursive)
			for (int j = 0; j < cond.ifTrue().size(); j++) {
				ActionPath childPath = actionPath.child("true", j);
				cy = buildActionTree(cond.ifTrue().get(j), childPath, indent + 1, cy, section);
			}
			rows.add(Row.addButton(AddTarget.branch(section, actionPath, "true"), "+ if_true", indent + 1, cy));
			cy += ROW_HEIGHT;

			// if_false sub-actions (recursive)
			for (int j = 0; j < cond.ifFalse().size(); j++) {
				ActionPath childPath = actionPath.child("false", j);
				cy = buildActionTree(cond.ifFalse().get(j), childPath, indent + 1, cy, section);
			}
			rows.add(Row.addButton(AddTarget.branch(section, actionPath, "false"), "+ if_false", indent + 1, cy));
			cy += ROW_HEIGHT;
		}

		if (action instanceof SpellActions.RepeatAction repeat) {
			for (int j = 0; j < repeat.body().size(); j++) {
				ActionPath childPath = actionPath.child("body", j);
				cy = buildActionTree(repeat.body().get(j), childPath, indent + 1, cy, section);
			}
			rows.add(Row.addButton(AddTarget.branch(section, actionPath, "body"), "+ body", indent + 1, cy));
			cy += ROW_HEIGHT;
		}
		return cy;
	}

	// --- Rendering ---

	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		Font font = Minecraft.getInstance().font;
		g.fill(x, y, x + w, y + h, 0xCC1a1a2e);
		g.fill(x, y, x + 1, y + h, 0xFF444466);

		if (phase == null) {
			g.drawString(font, "No phase", x + PADDING, y + PADDING, 0xFF888888, false);
			return;
		}

		buildRowsIfDirty();
		g.enableScissor(x, y, x + w, y + h);

		for (Row row : rows) {
			if (row.y + ROW_HEIGHT < y || row.y > y + h) continue;

			if (row.kind == RowKind.SECTION) {
				g.drawString(font, row.sectionTitle, x + PADDING, row.y + 2, 0xFF88AACC, false);
				String plus = "[+]";
				int plusX = x + w - font.width(plus) - PADDING;
				boolean plusHovered = mouseX >= plusX && mouseX < x + w
						&& mouseY >= row.y && mouseY < row.y + ROW_HEIGHT;
				g.drawString(font, plus, plusX, row.y + 2, plusHovered ? 0xFFFFFF44 : 0xFF66AA66, false);
			} else if (row.kind == RowKind.ACTION) {
				int ix = x + PADDING + row.indent * INDENT_PX;
				boolean selected = row.path != null && row.path.equals(selectedPath);
				boolean hovered = mouseX >= x && mouseX < x + w
						&& mouseY >= row.y && mouseY < row.y + ROW_HEIGHT;

				int bgColor = selected ? 0xFF334466 : (hovered ? 0xFF2a2a4e : 0);
				if (bgColor != 0) g.fill(x + 1, row.y, x + w, row.y + ROW_HEIGHT, bgColor);

				String label = getDisplayLabel(row.action, row.path);
				int textColor = selected ? 0xFFFFFF88 : getActionColor(row.action);
				g.drawString(font, label, ix, row.y + 2, textColor, false);
			} else if (row.kind == RowKind.ADD_BUTTON) {
				int ix = x + PADDING + row.indent * INDENT_PX;
				boolean hovered = mouseX >= x && mouseX < x + w
						&& mouseY >= row.y && mouseY < row.y + ROW_HEIGHT;
				g.drawString(font, row.addLabel, ix, row.y + 2, hovered ? 0xFFFFFF44 : 0xFF448844, false);
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

		g.disableScissor();
	}

	// --- Mouse handling ---

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || phase == null) return false;
		if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) return false;

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
				selectedPath = row.path;
				onSelect.accept(row.action, row.path);
				// Start potential drag for any action
				dragSourcePath = row.path;
				dragSourceSection = row.path.section;
				dragStartX = mouseX;
				dragStartY = mouseY;
				dragThresholdMet = false;
				return true;
			} else if (row.kind == RowKind.ADD_BUTTON) {
				onRequestAdd.accept(row.addTarget);
				return true;
			}
		}
		return false;
	}

	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (button != 0 || dragSourcePath == null) return false;

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

		// === Check ADD_BUTTON rows (branch insert targets) ===
		for (Row row : rows) {
			if (row.kind != RowKind.ADD_BUTTON || row.addTarget == null) continue;
			// Only allow dropping into branches within the same section
			if (!sourceSection.equals(row.addTarget.section)) continue;
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
			// Remove from source
			boolean removed = doDeleteAt(dragSourcePath);
			if (!removed) return;
			dirty = true;

			// Insert into the branch target
			insertAction(dragBranchTarget, action);
			// selectedPath and onSelect are handled inside insertAction
			onMoved.run();

		} else if (dragInsertIndex >= 0 && dragInsertSection != null) {
			// === Reorder mode (top-level within same section) ===
			if (!dragSourcePath.section.equals(dragInsertSection)) return;

			if (dragSourcePath.isNested()) {
				// Source is nested: remove from branch, insert at top level
				boolean removed = doDeleteAt(dragSourcePath);
				if (!removed) return;
				dirty = true;

				List<SpellAction> list = getSectionList(dragInsertSection);
				if (list == null) return;
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
	private boolean doDeleteAt(ActionPath path) {
		List<SpellAction> list = getSectionList(path.section);
		if (list == null) return false;
		return doDelete(list, path.path, 0);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (isMouseOver(mouseX, mouseY)) {
			scrollOffset = Math.max(0, scrollOffset - (int) (delta * ROW_HEIGHT));
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
		replaceAction(selectedPath, newAction);
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
		return false;
	}

	public void insertAction(AddTarget target, SpellAction action) {
		if (phase == null) return;
		List<SpellAction> list = getSectionList(target.section);
		if (list == null) return;

		if (!target.isBranch()) {
			list.add(action);
			selectedPath = ActionPath.topLevel(target.section, list.size() - 1);
		} else {
			doInsert(list, target.parentPath.path, 0, target.branch, action, target.parentPath);
		}
		dirty = true;
		onSelect.accept(action, selectedPath);
	}

	private boolean doInsert(List<SpellAction> list, List<PathEntry> path, int depth,
							 String targetBranch, SpellAction newAction, ActionPath parentPath) {
		PathEntry entry = path.get(depth);
		if (entry.index >= list.size()) return false;

		SpellAction current = list.get(entry.index);

		if (depth == path.size() - 1) {
			// This is the target ConditionalAction or RepeatAction
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
		return false;
	}

	public boolean deleteSelected() {
		if (phase == null || selectedPath == null) return false;
		List<SpellAction> list = getSectionList(selectedPath.section);
		if (list == null) return false;

		boolean result = doDelete(list, selectedPath.path, 0);
		if (result) {
			selectedPath = null;
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
		if (copySelected()) {
			deleteSelected();
			return true;
		}
		return false;
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

		if (selectedPath != null) {
			// Insert after selected action in the same list
			List<SpellAction> list = getSectionList(selectedPath.section);
			if (list != null && !selectedPath.isNested()) {
				int idx = selectedPath.leafIndex();
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
		return null;
	}

	private List<SpellAction> getSectionList(String section) {
		if (phase == null) return null;
		return switch (section) {
			case "enter" -> phase.onEnter;
			case "tick" -> phase.onTick;
			case "exit" -> phase.onExit;
			default -> null;
		};
	}

	// --- Labels ---

	private static String getDisplayLabel(SpellAction action, ActionPath path) {
		if (path == null || path.path.isEmpty()) return "?";
		String prefix = "";
		if (path.path.size() > 1) {
			String branch = path.path.get(path.path.size() - 2).branch;
			if ("true".equals(branch)) prefix = "T";
			else if ("false".equals(branch)) prefix = "F";
		}
		int index = path.path.get(path.path.size() - 1).index;
		return prefix + getActionLabel(action, index);
	}

	private static String getActionLabel(SpellAction action, int index) {
		if (action instanceof FireDanmakuAction fda) {
			return index + ": fire " + fda.bulletType().name().toLowerCase() + " " + fda.color().name().toLowerCase();
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
		if (action instanceof SpellActions.PlaySoundAction) return index + ": play_sound";
		if (action instanceof SpellActions.SetVariable sv) return index + ": set " + sv.key();
		if (action instanceof SpellActions.AddVariable av) return index + ": add " + av.key();
		if (action instanceof SpellActions.ForcePhase fp) return index + ": force " + fp.phaseId().getPath();
		if (action instanceof SpellActions.RepeatAction ra) return index + ": repeat(" + (int) (ra.count() instanceof NumberProviders.Constant c ? c.value() : 0) + ")";
		if (action instanceof SpellActions.NoopAction) return index + ": noop";
		return index + ": " + action.getClass().getSimpleName();
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
		if (action instanceof SpellActions.SequenceAction) return 0xFFAAAADD;
		return 0xFF999999;
	}

}
