package dev.xkmc.youkaishomecoming.content.spell.editor;

import com.mojang.blaze3d.platform.InputConstants;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class SpellEditorScreen extends Screen {

	private static final int PANEL_MARGIN = 12;
	private static final int LEFT_PANEL_WIDTH = 250;
	private static final int TOP_BUTTON_WIDTH = 74;
	private static final int TOP_BUTTON_HEIGHT = 20;
	private static final int MAX_ID_LENGTH = 256;

	private final EditorState state;

	private PhaseListWidget phaseList;

	private EditBox spellIdBox;
	private EditBox displayNameBox;
	private EditBox descriptionBox;
	private EditBox iconIdBox;
	private EditBox modelIdBox;
	private EditBox cooldownBox;
	private EditBox itemIconIdBox;
	private EditBox phaseIdBox;

	private Button cancelButton;
	private Button resetButton;
	private Button undoButton;
	private Button redoButton;
	private Button loadButton;
	private Button saveButton;
	private Button copyJsonButton;
	private Button addPhaseButton;
	private Button removePhaseButton;
	private Button renamePhaseButton;
	private Button duplicatePhaseButton;
	private Button copyPhaseJsonButton;
	private Button setEntryPhaseButton;
	private Button generateItemButton;
	private Button requiresTargetButton;

	private boolean syncingWidgets;
	private Component status = Component.empty();
	private int statusColor = 0xFFFFFFFF;

	public SpellEditorScreen(EditorState state) {
		super(Component.literal("Spell Editor"));
		this.state = state;
	}

	@Override
	protected void init() {
		super.init();
		clearWidgets();
		initButtons();
		initPhaseList();
		initFields();
		syncWidgetsFromState();
	}

	@Override
	public void tick() {
		super.tick();
		spellIdBox.tick();
		displayNameBox.tick();
		descriptionBox.tick();
		iconIdBox.tick();
		modelIdBox.tick();
		cooldownBox.tick();
		itemIconIdBox.tick();
		phaseIdBox.tick();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (hasControlDown() && keyCode == GLFW.GLFW_KEY_S) {
			saveProject();
			return true;
		}
		if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Z) {
			undo();
			return true;
		}
		if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Y) {
			redo();
			return true;
		}
		if (hasControlDown() && hasShiftDown() && keyCode == GLFW.GLFW_KEY_C) {
			copySelectedPhaseJson();
			return true;
		}
		if (super.keyPressed(keyCode, scanCode, modifiers)) {
			return true;
		}
		if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
			if (phaseIdBox.isFocused()) {
				renamePhase();
			}
			return true;
		}
		return false;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);

		int leftX = PANEL_MARGIN;
		int topY = 40;
		int leftBottom = height - 38;
		int rightX = leftX + LEFT_PANEL_WIDTH + 12;
		int rightWidth = width - rightX - PANEL_MARGIN;
		Component header = state.isDirty() ? Component.literal("Spell Editor*") : title;

		graphics.drawString(font, header, PANEL_MARGIN, 18, 0xFFFFFF, false);

		graphics.fill(leftX - 2, topY - 2, leftX + LEFT_PANEL_WIDTH + 2, leftBottom + 2, 0x55222222);
		graphics.fill(rightX - 2, topY - 2, rightX + rightWidth + 2, leftBottom + 2, 0x55222222);

		graphics.drawString(font, "Phases", leftX + 4, topY - 14, 0xE0E0E0, false);
		graphics.drawString(font, "Definition", rightX + 4, topY - 14, 0xE0E0E0, false);

		int labelX = rightX + 8;
		int textX = rightX + 108;
		drawFieldLabel(graphics, "Spell ID", labelX, spellIdBox.getY() + 6);
		drawFieldLabel(graphics, "Name", labelX, displayNameBox.getY() + 6);
		drawFieldLabel(graphics, "Description", labelX, descriptionBox.getY() + 6);
		drawFieldLabel(graphics, "Icon ID", labelX, iconIdBox.getY() + 6);
		drawFieldLabel(graphics, "Model ID", labelX, modelIdBox.getY() + 6);
		drawFieldLabel(graphics, "Cooldown", labelX, cooldownBox.getY() + 6);
		drawFieldLabel(graphics, "Item Icon", labelX, itemIconIdBox.getY() + 6);
		drawFieldLabel(graphics, "Entry Phase", labelX, itemIconIdBox.getY() + 34);
		graphics.drawString(font, state.entryPhaseId, textX, itemIconIdBox.getY() + 34, 0xB0D8FF, false);
		drawFieldLabel(graphics, "Phase ID", labelX, phaseIdBox.getY() + 6);

		int detailsY = copyPhaseJsonButton.getY() + 30;
		graphics.drawString(font, "Selected Phase", labelX, detailsY, 0xE0E0E0, false);
		detailsY += 14;
		for (String line : state.describeSelectedPhase()) {
			for (var wrapped : font.split(Component.literal(line), rightWidth - 16)) {
				if (detailsY > leftBottom - 12) {
					break;
				}
				graphics.drawString(font, wrapped, labelX, detailsY, 0xD0D0D0, false);
				detailsY += 10;
			}
			if (detailsY > leftBottom - 12) {
				break;
			}
		}

		if (!status.getString().isEmpty()) {
			graphics.drawString(font, status, PANEL_MARGIN, height - 16, statusColor, false);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void initButtons() {
		int x = PANEL_MARGIN;
		int y = 10;
		cancelButton = addRenderableWidget(new Button.Builder(Component.literal("Close"), e -> onClose())
				.pos(x, y).size(TOP_BUTTON_WIDTH, TOP_BUTTON_HEIGHT).build());
		x += TOP_BUTTON_WIDTH + 4;
		resetButton = addRenderableWidget(new Button.Builder(Component.literal("Reset"), e -> resetToOriginal())
				.pos(x, y).size(TOP_BUTTON_WIDTH, TOP_BUTTON_HEIGHT).build());
		x += TOP_BUTTON_WIDTH + 4;
		undoButton = addRenderableWidget(new Button.Builder(Component.literal("Undo"), e -> undo())
				.pos(x, y).size(TOP_BUTTON_WIDTH, TOP_BUTTON_HEIGHT).build());
		x += TOP_BUTTON_WIDTH + 4;
		redoButton = addRenderableWidget(new Button.Builder(Component.literal("Redo"), e -> redo())
				.pos(x, y).size(TOP_BUTTON_WIDTH, TOP_BUTTON_HEIGHT).build());
		x += TOP_BUTTON_WIDTH + 4;
		loadButton = addRenderableWidget(new Button.Builder(Component.literal("Load"), e -> loadProject())
				.pos(x, y).size(TOP_BUTTON_WIDTH, TOP_BUTTON_HEIGHT).build());
		x += TOP_BUTTON_WIDTH + 4;
		saveButton = addRenderableWidget(new Button.Builder(Component.literal("Save"), e -> saveProject())
				.pos(x, y).size(TOP_BUTTON_WIDTH, TOP_BUTTON_HEIGHT).build());
		x += TOP_BUTTON_WIDTH + 4;
		copyJsonButton = addRenderableWidget(new Button.Builder(Component.literal("Copy JSON"), e -> copyJson())
				.pos(x, y).size(96, TOP_BUTTON_HEIGHT).build());
	}

	private void initPhaseList() {
		int x = PANEL_MARGIN;
		int y0 = 56;
		int y1 = height - 70;
		phaseList = addRenderableWidget(new PhaseListWidget(minecraft, LEFT_PANEL_WIDTH, y1 - y0, y0, y1, 22));
		phaseList.setLeftPos(x);
		int buttonY = height - 62;
		addPhaseButton = addRenderableWidget(new Button.Builder(Component.literal("Add Phase"), e -> addPhase())
				.pos(x, buttonY).size(120, 20).build());
		removePhaseButton = addRenderableWidget(new Button.Builder(Component.literal("Delete Phase"), e -> deletePhase())
				.pos(x + 126, buttonY).size(120, 20).build());
		phaseList.refresh();
	}

	private void initFields() {
		int rightX = PANEL_MARGIN + LEFT_PANEL_WIDTH + 12;
		int textX = rightX + 108;
		int width = this.width - textX - PANEL_MARGIN - 8;
		int y = 56;

		spellIdBox = addRenderableWidget(createField(textX, y, width, MAX_ID_LENGTH, value -> {
			state.spellId = value;
			onStateEdited();
		}));
		y += 24;
		displayNameBox = addRenderableWidget(createField(textX, y, width, 128, value -> {
			state.displayName = value;
			onStateEdited();
		}));
		y += 24;
		descriptionBox = addRenderableWidget(createField(textX, y, width, 512, value -> {
			state.description = value;
			onStateEdited();
		}));
		y += 24;
		iconIdBox = addRenderableWidget(createField(textX, y, width, MAX_ID_LENGTH, value -> {
			state.iconId = value;
			onStateEdited();
		}));
		y += 24;
		modelIdBox = addRenderableWidget(createField(textX, y, width, MAX_ID_LENGTH, value -> {
			state.modelId = value;
			onStateEdited();
		}));
		y += 24;
		cooldownBox = addRenderableWidget(createField(textX, y, width, 10, value -> {
			state.cooldown = value;
			onStateEdited();
		}));
		cooldownBox.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
		y += 24;
		itemIconIdBox = addRenderableWidget(createField(textX, y, width, MAX_ID_LENGTH, value -> {
			state.itemIconId = value;
			onStateEdited();
		}));

		y += 28;
		generateItemButton = addRenderableWidget(new Button.Builder(Component.empty(), e -> {
			state.generateItem = !state.generateItem;
			onStateEdited();
		}).pos(textX, y).size(140, 20).build());
		requiresTargetButton = addRenderableWidget(new Button.Builder(Component.empty(), e -> {
			state.requiresTarget = !state.requiresTarget;
			onStateEdited();
		}).pos(textX + 148, y).size(160, 20).build());
		y += 28;
		setEntryPhaseButton = addRenderableWidget(new Button.Builder(Component.literal("Use Selected As Entry"), e -> {
			state.setEntryPhase(state.selectedPhase);
			onStateEdited();
		}).pos(textX, y).size(180, 20).build());
		y += 28;

		int renameWidth = Math.max(84, Math.min(108, width / 4));
		int phaseFieldWidth = Math.max(120, width - renameWidth - 8);
		phaseIdBox = addRenderableWidget(createPassiveField(textX, y, phaseFieldWidth, MAX_ID_LENGTH));
		renamePhaseButton = addRenderableWidget(new Button.Builder(Component.literal("Rename"), e -> renamePhase())
				.pos(textX + phaseFieldWidth + 8, y).size(renameWidth, 20).build());
		y += 28;

		int dualButtonWidth = (width - 8) / 2;
		duplicatePhaseButton = addRenderableWidget(new Button.Builder(Component.literal("Duplicate"), e -> duplicatePhase())
				.pos(textX, y).size(dualButtonWidth, 20).build());
		copyPhaseJsonButton = addRenderableWidget(new Button.Builder(Component.literal("Copy Phase"), e -> copySelectedPhaseJson())
				.pos(textX + dualButtonWidth + 8, y).size(width - dualButtonWidth - 8, 20).build());
	}

	private EditBox createField(int x, int y, int width, int maxLength, java.util.function.Consumer<String> consumer) {
		EditBox box = new EditBox(font, x, y, width, 20, Component.empty());
		box.setMaxLength(maxLength);
		box.setResponder(value -> {
			if (!syncingWidgets) {
				consumer.accept(value);
			}
		});
		return box;
	}

	private EditBox createPassiveField(int x, int y, int width, int maxLength) {
		EditBox box = new EditBox(font, x, y, width, 20, Component.empty());
		box.setMaxLength(maxLength);
		return box;
	}

	private void onStateEdited() {
		state.recordSnapshot();
		refreshButtonState();
	}

	private void syncWidgetsFromState() {
		syncingWidgets = true;
		try {
			spellIdBox.setValue(state.spellId);
			displayNameBox.setValue(state.displayName);
			descriptionBox.setValue(state.description);
			iconIdBox.setValue(state.iconId);
			modelIdBox.setValue(state.modelId);
			cooldownBox.setValue(state.cooldown);
			itemIconIdBox.setValue(state.itemIconId);
			phaseIdBox.setValue(state.selectedPhase == null ? "" : state.selectedPhase.toString());
			phaseList.refreshSelection();
		} finally {
			syncingWidgets = false;
		}
		refreshButtonState();
	}

	private void refreshButtonState() {
		List<String> errors = state.validate();
		saveButton.active = errors.isEmpty();
		saveButton.setMessage(Component.literal(state.isDirty() ? "Save*" : "Save"));
		copyJsonButton.active = errors.isEmpty();
		loadButton.active = canLoadProject();
		undoButton.active = state.canUndo();
		redoButton.active = state.canRedo();
		removePhaseButton.active = state.getRemoveSelectedPhaseError() == null;
		renamePhaseButton.active = state.selectedPhase != null;
		duplicatePhaseButton.active = state.selectedPhase != null;
		copyPhaseJsonButton.active = state.selectedPhase != null;
		setEntryPhaseButton.active = state.selectedPhase != null;
		generateItemButton.setMessage(toggleLabel("Generate Item", state.generateItem));
		requiresTargetButton.setMessage(toggleLabel("Requires Target", state.requiresTarget));
		if (errors.isEmpty()) {
			setStatus(state.isDirty() ? "Modified" : "Ready", state.isDirty() ? 0xFFFFD37A : 0xFF90EE90);
		} else {
			setStatus(errors.get(0), 0xFFFF8080);
		}
	}

	private boolean canLoadProject() {
		String spellId = state.spellId.trim();
		return ResourceLocation.isValidResourceLocation(spellId)
				&& SpellEditorIO.hasProject(new ResourceLocation(spellId));
	}

	private void undo() {
		state.undo();
		phaseList.refresh();
		syncWidgetsFromState();
	}

	private void redo() {
		state.redo();
		phaseList.refresh();
		syncWidgetsFromState();
	}

	private void resetToOriginal() {
		state.resetToOriginal();
		phaseList.refresh();
		syncWidgetsFromState();
		setStatus("Reset to original definition", 0xFFE0E0E0);
	}

	private void addPhase() {
		ResourceLocation phaseId = state.addPhase();
		state.recordSnapshot();
		phaseList.refresh();
		syncWidgetsFromState();
		setStatus("Added phase " + phaseId, 0xFFE0E0E0);
	}

	private void deletePhase() {
		if (state.removeSelectedPhase()) {
			state.recordSnapshot();
			phaseList.refresh();
			syncWidgetsFromState();
			setStatus("Deleted selected phase", 0xFFE0E0E0);
		} else {
			setStatus(state.getRemoveSelectedPhaseError(), 0xFFFF8080);
		}
	}

	private void renamePhase() {
		try {
			ResourceLocation phaseId = state.renameSelectedPhase(phaseIdBox.getValue());
			state.recordSnapshot();
			phaseList.refresh();
			syncWidgetsFromState();
			setStatus("Renamed phase to " + phaseId, 0xFF90EE90);
		} catch (Exception e) {
			setStatus("Rename failed: " + e.getMessage(), 0xFFFF8080);
		}
	}

	private void duplicatePhase() {
		try {
			ResourceLocation phaseId = state.duplicateSelectedPhase();
			state.recordSnapshot();
			phaseList.refresh();
			syncWidgetsFromState();
			setStatus("Duplicated phase to " + phaseId, 0xFF90EE90);
		} catch (Exception e) {
			setStatus("Duplicate failed: " + e.getMessage(), 0xFFFF8080);
		}
	}

	private void saveProject() {
		try {
			var result = SpellEditorIO.saveProject(state.buildProjectData());
			state.markSaved();
			refreshButtonState();
			setStatus("Saved: " + result.projectPath().getFileName() + " + datapack export", 0xFF90EE90);
		} catch (Exception e) {
			setStatus("Save failed: " + e.getMessage(), 0xFFFF8080);
		}
	}

	private void loadProject() {
		try {
			ResourceLocation id = new ResourceLocation(state.spellId.trim());
			var loaded = SpellEditorIO.loadProject(id);
			if (loaded.isEmpty()) {
				setStatus("No saved project for " + id, 0xFFFFC080);
				return;
			}
			state.loadProject(loaded.get());
			phaseList.refresh();
			syncWidgetsFromState();
			setStatus("Loaded saved project for " + id, 0xFF90EE90);
		} catch (IllegalArgumentException e) {
			setStatus("Invalid spell ID: " + state.spellId, 0xFFFF8080);
		} catch (IOException e) {
			setStatus("Load failed: " + e.getMessage(), 0xFFFF8080);
		} catch (Exception e) {
			setStatus("Project is invalid: " + e.getMessage(), 0xFFFF8080);
		}
	}

	private void copyJson() {
		try {
			String json = SpellEditorIO.toClipboardJson(state.buildDefinition());
			Minecraft.getInstance().keyboardHandler.setClipboard(json);
			setStatus("Definition JSON copied to clipboard", 0xFF90EE90);
		} catch (Exception e) {
			setStatus("Copy failed: " + e.getMessage(), 0xFFFF8080);
		}
	}

	private void copySelectedPhaseJson() {
		PhaseDefinition phase = state.getSelectedPhaseDefinition();
		if (phase == null) {
			setStatus("No phase selected", 0xFFFF8080);
			return;
		}
		try {
			String json = SpellEditorIO.toClipboardJson(phase);
			Minecraft.getInstance().keyboardHandler.setClipboard(json);
			setStatus("Phase JSON copied to clipboard", 0xFF90EE90);
		} catch (Exception e) {
			setStatus("Copy phase failed: " + e.getMessage(), 0xFFFF8080);
		}
	}

	private void setStatus(String text, int color) {
		status = Component.literal(text);
		statusColor = color;
	}

	private void drawFieldLabel(GuiGraphics graphics, String label, int x, int y) {
		graphics.drawString(font, label, x, y, 0xE0E0E0, false);
	}

	private static Component toggleLabel(String label, boolean value) {
		return Component.literal(label + ": " + (value ? "On" : "Off"));
	}

	private class PhaseListWidget extends ObjectSelectionList<PhaseEntry> {

		private PhaseListWidget(Minecraft minecraft, int width, int height, int y0, int y1, int itemHeight) {
			super(minecraft, width, height, y0, y1, itemHeight);
			setRenderBackground(false);
			setRenderTopAndBottom(false);
		}

		private void refresh() {
			clearEntries();
			for (ResourceLocation phaseId : state.getPhaseIds()) {
				addEntry(new PhaseEntry(phaseId));
			}
			refreshSelection();
		}

		private void refreshSelection() {
			for (PhaseEntry entry : children()) {
				if (Objects.equals(entry.phaseId, state.selectedPhase)) {
					setSelected(entry);
					return;
				}
			}
			setSelected(null);
		}

		@Override
		public int getRowWidth() {
			return LEFT_PANEL_WIDTH - 12;
		}
	}

	private class PhaseEntry extends ObjectSelectionList.Entry<PhaseEntry> {

		private final ResourceLocation phaseId;

		private PhaseEntry(ResourceLocation phaseId) {
			this.phaseId = phaseId;
		}

		@Override
		public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
						   int mouseX, int mouseY, boolean hovered, float partialTick) {
			boolean selected = Objects.equals(state.selectedPhase, phaseId);
			int bg = selected ? 0xAA355C7D : hovered ? 0x66333333 : 0x33161616;
			graphics.fill(left, top, left + width, top + height - 1, bg);
			graphics.drawString(font, phaseId.toString(), left + 4, top + 4, 0xFFFFFF, false);
			PhaseDefinition phase = state.phases.get(phaseId);
			if (phase != null) {
				String summary = "tick " + phase.onTick.size() + " | trans " + phase.transitions.size();
				graphics.drawString(font, summary, left + 4, top + 13, 0xB8C7D1, false);
			}
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (button == 0) {
				state.setSelectedPhase(phaseId);
				phaseList.setSelected(this);
				syncWidgetsFromState();
				return true;
			}
			return false;
		}

		@Override
		public Component getNarration() {
			return Component.literal(phaseId.toString());
		}
	}
}
