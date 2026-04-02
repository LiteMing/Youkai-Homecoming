package dev.xkmc.youkaishomecoming.content.spell.editor;

import com.mojang.blaze3d.platform.InputConstants;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellCondition;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellConditions;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.Transition;
import dev.xkmc.youkaishomecoming.content.spell.definition.TransitionMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

public class SpellEditorScreen extends Screen {

	private static final int PANEL_MARGIN = 12;
	private static final int PANEL_GAP = 8;
	private static final int TOP_BUTTON_WIDTH = 72;
	private static final int TOP_BUTTON_HEIGHT = 20;
	private static final int MAX_ID_LENGTH = 256;
	private static final int FIELD_HEIGHT = 20;
	private static final Pattern DECIMAL_INPUT = Pattern.compile("-?(?:\\d+(?:\\.\\d*)?|\\d*\\.\\d*)?");
	private static final Pattern INTEGER_INPUT = Pattern.compile("-?\\d*");
	private static final Pattern NON_NEGATIVE_INTEGER_INPUT = Pattern.compile("\\d*");
	private static final List<String> VARIABLE_OPERATORS = List.of("==", "!=", "<", "<=", ">", ">=");

	private final EditorState state;
	private final List<LabelSpec> staticLabels = new ArrayList<>();
	private final List<LabelSpec> dynamicLabels = new ArrayList<>();
	private final List<AbstractWidget> dynamicWidgets = new ArrayList<>();
	private final List<String> propertyNotes = new ArrayList<>();

	private PhaseEditorSection selectedSection = PhaseEditorSection.ON_TICK;
	private int selectedEntryIndex = -1;

	private PhaseListWidget phaseList;
	private EntryListWidget entryList;

	private EditBox spellIdBox;
	private EditBox displayNameBox;
	private EditBox descriptionBox;
	private EditBox iconIdBox;
	private EditBox modelIdBox;
	private EditBox cooldownBox;
	private EditBox itemIconIdBox;
	private EditBox phaseIdBox;

	private Button closeButton;
	private Button resetButton;
	private Button undoButton;
	private Button redoButton;
	private Button loadButton;
	private Button saveButton;
	private Button copyJsonButton;
	private Button copyPhaseJsonButton;

	private Button addPhaseButton;
	private Button deletePhaseButton;
	private Button duplicatePhaseButton;
	private Button generateItemButton;
	private Button requiresTargetButton;

	private final List<Button> sectionButtons = new ArrayList<>();
	private Button addEntryButton;
	private Button deleteEntryButton;
	private Button duplicateEntryButton;
	private Button copyEntryButton;
	private Button pasteEntryButton;
	private Button pasteAsNewEntryButton;
	private Button moveUpEntryButton;
	private Button moveDownEntryButton;

	private Button renamePhaseButton;
	private Button setEntryPhaseButton;
	@Nullable
	private Button actionTypeButton;
	@Nullable
	private Button conditionTypeButton;
	@Nullable
	private Button transitionModeButton;
	@Nullable
	private Button variableOpButton;
	@Nullable
	private Button alwaysToggleButton;

	private boolean syncingWidgets;
	private Component status = Component.empty();
	private int statusColor = 0xFFFFFFFF;
	private int propertyNoteY;

	private int metaX;
	private int metaY;
	private int metaWidth;
	private int metaHeight;
	private int phaseX;
	private int phaseY;
	private int phaseWidth;
	private int entryX;
	private int entryY;
	private int entryWidth;
	private int propertyX;
	private int propertyY;
	private int propertyWidth;
	private int bodyBottom;

	public SpellEditorScreen(EditorState state) {
		super(Component.literal("Spell Editor"));
		this.state = state;
	}

	@Override
	protected void init() {
		super.init();
		clearWidgets();
		staticLabels.clear();
		dynamicLabels.clear();
		dynamicWidgets.clear();
		propertyNotes.clear();
		sectionButtons.clear();
		computeLayout();
		initToolbar();
		initMetadataFields();
		initPhasePanel();
		initEntryPanel();
		initPropertyBase();
		refreshAllLists();
		syncWidgetsFromState();
		rebuildPropertyWidgets();
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
		for (AbstractWidget widget : dynamicWidgets) {
			if (widget instanceof EditBox box) {
				box.tick();
			}
		}
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
				renameSelectedPhase();
				return true;
			}
		}
		return false;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);
		drawPanel(graphics, metaX, metaY, metaWidth, metaHeight, "Definition");
		drawPanel(graphics, phaseX, phaseY, phaseWidth, bodyBottom - phaseY, "Phases");
		drawPanel(graphics, entryX, entryY, entryWidth, bodyBottom - entryY, selectedSection.title());
		drawPanel(graphics, propertyX, propertyY, propertyWidth, bodyBottom - propertyY, "Properties");

		super.render(graphics, mouseX, mouseY, partialTick);

		Component header = state.isDirty() ? Component.literal("Spell Editor*") : title;
		graphics.drawString(font, header, PANEL_MARGIN, 18, 0xFFFFFF, false);
		drawLabels(graphics, staticLabels);
		drawLabels(graphics, dynamicLabels);
		drawPhaseInfo(graphics);
		drawPropertyInfo(graphics);
		drawPropertyNotes(graphics);

		if (!status.getString().isEmpty()) {
			graphics.drawString(font, status, PANEL_MARGIN, height - 16, statusColor, false);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void computeLayout() {
		metaX = PANEL_MARGIN;
		metaY = 38;
		metaWidth = width - PANEL_MARGIN * 2;
		metaHeight = 90;
		phaseY = metaY + metaHeight + PANEL_GAP;
		entryY = phaseY;
		propertyY = phaseY;
		bodyBottom = height - 36;
		int bodyWidth = width - PANEL_MARGIN * 2 - PANEL_GAP * 2;
		phaseWidth = Math.min(248, Math.max(200, bodyWidth / 4));
		int desiredProperty = Math.min(340, Math.max(240, bodyWidth / 3));
		entryWidth = bodyWidth - phaseWidth - desiredProperty;
		if (entryWidth < 248) {
			desiredProperty = Math.max(220, desiredProperty - (248 - entryWidth));
			entryWidth = bodyWidth - phaseWidth - desiredProperty;
		}
		if (entryWidth < 220) {
			phaseWidth = Math.max(184, phaseWidth - (220 - entryWidth));
			entryWidth = bodyWidth - phaseWidth - desiredProperty;
		}
		propertyWidth = bodyWidth - phaseWidth - entryWidth;
		phaseX = PANEL_MARGIN;
		entryX = phaseX + phaseWidth + PANEL_GAP;
		propertyX = entryX + entryWidth + PANEL_GAP;
	}

	private void initToolbar() {
		int x = PANEL_MARGIN;
		int y = 10;
		closeButton = addRenderableWidget(new Button.Builder(Component.literal("Close"), button -> onClose())
				.pos(x, y).size(TOP_BUTTON_WIDTH, TOP_BUTTON_HEIGHT).build());
		x += TOP_BUTTON_WIDTH + 4;
		resetButton = addRenderableWidget(new Button.Builder(Component.literal("Reset"), button -> resetToOriginal())
				.pos(x, y).size(TOP_BUTTON_WIDTH, TOP_BUTTON_HEIGHT).build());
		x += TOP_BUTTON_WIDTH + 4;
		undoButton = addRenderableWidget(new Button.Builder(Component.literal("Undo"), button -> undo())
				.pos(x, y).size(TOP_BUTTON_WIDTH, TOP_BUTTON_HEIGHT).build());
		x += TOP_BUTTON_WIDTH + 4;
		redoButton = addRenderableWidget(new Button.Builder(Component.literal("Redo"), button -> redo())
				.pos(x, y).size(TOP_BUTTON_WIDTH, TOP_BUTTON_HEIGHT).build());
		x += TOP_BUTTON_WIDTH + 4;
		loadButton = addRenderableWidget(new Button.Builder(Component.literal("Load"), button -> loadProject())
				.pos(x, y).size(TOP_BUTTON_WIDTH, TOP_BUTTON_HEIGHT).build());
		x += TOP_BUTTON_WIDTH + 4;
		saveButton = addRenderableWidget(new Button.Builder(Component.literal("Save"), button -> saveProject())
				.pos(x, y).size(TOP_BUTTON_WIDTH, TOP_BUTTON_HEIGHT).build());
		x += TOP_BUTTON_WIDTH + 4;
		copyJsonButton = addRenderableWidget(new Button.Builder(Component.literal("Copy JSON"), button -> copyJson())
				.pos(x, y).size(88, TOP_BUTTON_HEIGHT).build());
		x += 92;
		copyPhaseJsonButton = addRenderableWidget(new Button.Builder(Component.literal("Copy Phase"), button -> copySelectedPhaseJson())
				.pos(x, y).size(92, TOP_BUTTON_HEIGHT).build());
	}

	private void initMetadataFields() {
		int innerX = metaX + 8;
		int usableWidth = metaWidth - 16;
		int gap = 8;
		int row1Y = metaY + 18;
		int row2Y = row1Y + 28;
		int row3Y = row2Y + 30;
		int spellWidth = 220;
		int nameWidth = 180;
		int descWidth = usableWidth - spellWidth - nameWidth - gap * 2;
		int resourceWidth = (usableWidth - 72 - gap * 3) / 3;

		spellIdBox = addRenderableWidget(createBoundField(innerX, row1Y, spellWidth, MAX_ID_LENGTH, value -> {
			state.spellId = value;
			onDefinitionEdited();
		}));
		addStaticLabel("Spell ID", innerX, row1Y - 10);

		int nameX = innerX + spellWidth + gap;
		displayNameBox = addRenderableWidget(createBoundField(nameX, row1Y, nameWidth, 128, value -> {
			state.displayName = value;
			onDefinitionEdited();
		}));
		addStaticLabel("Display Name", nameX, row1Y - 10);

		int descX = nameX + nameWidth + gap;
		descriptionBox = addRenderableWidget(createBoundField(descX, row1Y, descWidth, 512, value -> {
			state.description = value;
			onDefinitionEdited();
		}));
		addStaticLabel("Description", descX, row1Y - 10);

		iconIdBox = addRenderableWidget(createBoundField(innerX, row2Y, resourceWidth, MAX_ID_LENGTH, value -> {
			state.iconId = value;
			onDefinitionEdited();
		}));
		addStaticLabel("Icon ID", innerX, row2Y - 10);

		int modelX = innerX + resourceWidth + gap;
		modelIdBox = addRenderableWidget(createBoundField(modelX, row2Y, resourceWidth, MAX_ID_LENGTH, value -> {
			state.modelId = value;
			onDefinitionEdited();
		}));
		addStaticLabel("Model ID", modelX, row2Y - 10);

		int itemX = modelX + resourceWidth + gap;
		itemIconIdBox = addRenderableWidget(createBoundField(itemX, row2Y, resourceWidth, MAX_ID_LENGTH, value -> {
			state.itemIconId = value;
			onDefinitionEdited();
		}));
		addStaticLabel("Item Icon", itemX, row2Y - 10);

		int cooldownX = itemX + resourceWidth + gap;
		cooldownBox = addRenderableWidget(createBoundField(cooldownX, row2Y, usableWidth - (cooldownX - innerX), 10, value -> {
			state.cooldown = value;
			onDefinitionEdited();
		}));
		cooldownBox.setFilter(value -> NON_NEGATIVE_INTEGER_INPUT.matcher(value).matches());
		addStaticLabel("Cooldown", cooldownX, row2Y - 10);

		int toggleWidth = 128;
		generateToggleButton(innerX, row3Y, toggleWidth);
		generateTargetButton(innerX + toggleWidth + gap, row3Y, toggleWidth + 12);
	}

	private void generateToggleButton(int x, int y, int width) {
		generateItemButton = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
			state.generateItem = !state.generateItem;
			onDefinitionEdited();
		}).pos(x, y).size(width, FIELD_HEIGHT).build());
	}

	private void generateTargetButton(int x, int y, int width) {
		requiresTargetButton = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
			state.requiresTarget = !state.requiresTarget;
			onDefinitionEdited();
		}).pos(x, y).size(width, FIELD_HEIGHT).build());
	}

	private void initPhasePanel() {
		Minecraft mc = Objects.requireNonNull(minecraft);
		int listTop = phaseY + 18;
		int listBottom = bodyBottom - 54;
		phaseList = addRenderableWidget(new PhaseListWidget(mc, phaseWidth - 12, listBottom - listTop, listTop, listBottom, 24));
		phaseList.setLeftPos(phaseX + 6);

		int buttonY = bodyBottom - 28;
		int buttonWidth = (phaseWidth - 24 - PANEL_GAP * 2) / 3;
		addPhaseButton = addRenderableWidget(new Button.Builder(Component.literal("Add"), button -> addPhase())
				.pos(phaseX + 8, buttonY).size(buttonWidth, 20).build());
		deletePhaseButton = addRenderableWidget(new Button.Builder(Component.literal("Delete"), button -> deletePhase())
				.pos(phaseX + 8 + buttonWidth + PANEL_GAP, buttonY).size(buttonWidth, 20).build());
		duplicatePhaseButton = addRenderableWidget(new Button.Builder(Component.literal("Duplicate"), button -> duplicatePhase())
				.pos(phaseX + 8 + (buttonWidth + PANEL_GAP) * 2, buttonY).size(buttonWidth, 20).build());
	}

	private void initEntryPanel() {
		Minecraft mc = Objects.requireNonNull(minecraft);
		int innerX = entryX + 8;
		int tabWidth = (entryWidth - 16 - PANEL_GAP * 3) / 4;
		int tabY = entryY + 8;
		for (int i = 0; i < PhaseEditorSection.values().length; i++) {
			PhaseEditorSection section = PhaseEditorSection.values()[i];
			Button button = addRenderableWidget(new Button.Builder(Component.literal(section.title()), value -> setSelectedSection(section))
					.pos(innerX + i * (tabWidth + PANEL_GAP), tabY).size(tabWidth, 20).build());
			sectionButtons.add(button);
		}

		int listTop = tabY + 28;
		int listBottom = bodyBottom - 78;
		entryList = addRenderableWidget(new EntryListWidget(mc, entryWidth - 12, listBottom - listTop, listTop, listBottom, 24));
		entryList.setLeftPos(entryX + 6);

		int buttonWidth = (entryWidth - 16 - PANEL_GAP * 3) / 4;
		int row1Y = bodyBottom - 52;
		int row2Y = bodyBottom - 28;
		addEntryButton = addRenderableWidget(new Button.Builder(Component.literal("Add"), button -> addEntry())
				.pos(innerX, row1Y).size(buttonWidth, 20).build());
		deleteEntryButton = addRenderableWidget(new Button.Builder(Component.literal("Del"), button -> deleteEntry())
				.pos(innerX + buttonWidth + PANEL_GAP, row1Y).size(buttonWidth, 20).build());
		duplicateEntryButton = addRenderableWidget(new Button.Builder(Component.literal("Dup"), button -> duplicateEntry())
				.pos(innerX + (buttonWidth + PANEL_GAP) * 2, row1Y).size(buttonWidth, 20).build());
		copyEntryButton = addRenderableWidget(new Button.Builder(Component.literal("Copy"), button -> copySelectedItemJson())
				.pos(innerX + (buttonWidth + PANEL_GAP) * 3, row1Y).size(buttonWidth, 20).build());
		pasteEntryButton = addRenderableWidget(new Button.Builder(Component.literal("Paste"), button -> pasteItemJson(false))
				.pos(innerX, row2Y).size(buttonWidth, 20).build());
		pasteAsNewEntryButton = addRenderableWidget(new Button.Builder(Component.literal("Paste+"), button -> pasteItemJson(true))
				.pos(innerX + buttonWidth + PANEL_GAP, row2Y).size(buttonWidth, 20).build());
		moveUpEntryButton = addRenderableWidget(new Button.Builder(Component.literal("Up"), button -> moveEntry(-1))
				.pos(innerX + (buttonWidth + PANEL_GAP) * 2, row2Y).size(buttonWidth, 20).build());
		moveDownEntryButton = addRenderableWidget(new Button.Builder(Component.literal("Down"), button -> moveEntry(1))
				.pos(innerX + (buttonWidth + PANEL_GAP) * 3, row2Y).size(buttonWidth, 20).build());
	}

	private void initPropertyBase() {
		int innerX = propertyX + 8;
		int labelX = innerX;
		int fieldX = innerX + 72;
		int renameWidth = 76;
		int fieldWidth = propertyWidth - 16 - 72 - renameWidth - 4;
		int phaseRowY = propertyY + 18;

		phaseIdBox = addRenderableWidget(new EditBox(font, fieldX, phaseRowY, fieldWidth, FIELD_HEIGHT, Component.empty()));
		phaseIdBox.setMaxLength(MAX_ID_LENGTH);
		renamePhaseButton = addRenderableWidget(new Button.Builder(Component.literal("Rename"), button -> renameSelectedPhase())
				.pos(fieldX + fieldWidth + 4, phaseRowY).size(renameWidth, FIELD_HEIGHT).build());
		addStaticLabel("Phase ID", labelX, phaseRowY + 6);

		int entryButtonY = phaseRowY + 28;
		setEntryPhaseButton = addRenderableWidget(new Button.Builder(Component.literal("Use Selected As Entry"), button -> {
			state.setEntryPhase(state.selectedPhase);
			onDefinitionEdited();
			rebuildPropertyWidgets();
		}).pos(fieldX, entryButtonY).size(Math.min(180, propertyWidth - 16 - 72), FIELD_HEIGHT).build());
	}

	private void rebuildPropertyWidgets() {
		clearDynamicWidgets();
		dynamicLabels.clear();
		propertyNotes.clear();
		actionTypeButton = null;
		conditionTypeButton = null;
		transitionModeButton = null;
		variableOpButton = null;
		alwaysToggleButton = null;

		int innerX = propertyX + 8;
		int labelX = innerX;
		int fieldX = innerX + 88;
		int fieldWidth = propertyWidth - 16 - 88;
		int y = propertyY + 92;

		if (state.selectedPhase == null) {
			propertyNotes.add("No phase selected.");
			propertyNoteY = y;
			refreshButtonState();
			return;
		}
		if (selectedEntryIndex < 0) {
			propertyNotes.add("No entry selected in this section.");
			propertyNotes.add("Pick an action or transition in the center list to edit it.");
			propertyNoteY = y;
			refreshButtonState();
			return;
		}
		if (selectedSection.isTransitionSection()) {
			Transition transition = getSelectedTransition();
			if (transition == null) {
				propertyNotes.add("Selected transition is missing.");
				propertyNoteY = y;
				refreshButtonState();
				return;
			}
			EditorConditionType type = EditorConditionType.fromCondition(transition.condition());
			conditionTypeButton = addDynamicWidget(new Button.Builder(
					Component.literal("Condition Type: " + type.label()),
					button -> cycleConditionType()
			).pos(innerX, y).size(propertyWidth - 16, FIELD_HEIGHT).build());
			y += 26;

			EditBox targetBox = addPropertyField(labelX, fieldX, fieldWidth, y, "Target Phase",
					transition.targetPhase().toString(), MAX_ID_LENGTH, value -> {
						if (!ResourceLocation.isValidResourceLocation(value)) {
							return;
						}
						applyTransitionEdit(current -> new Transition(current.condition(), new ResourceLocation(value), current.mode()), false);
					}, null);
			targetBox.setSuggestion(state.entryPhaseId);
			y += 24;

			transitionModeButton = addDynamicWidget(new Button.Builder(
					Component.literal("Mode: " + formatModeLabel(transition.mode())),
					button -> cycleTransitionMode()
			).pos(innerX, y).size(propertyWidth - 16, FIELD_HEIGHT).build());
			y += 28;

			y = buildConditionEditor(transition.condition(), innerX, labelX, fieldX, fieldWidth, y);
			propertyNoteY = y + 4;
			if (!type.fieldEditable()) {
				propertyNotes.add("This composite condition is summary-only in the form editor.");
				propertyNotes.add("Use Copy/Paste JSON to edit nested condition trees.");
			}
		} else {
			SpellAction action = getSelectedAction();
			if (action == null) {
				propertyNotes.add("Selected action is missing.");
				propertyNoteY = y;
				refreshButtonState();
				return;
			}
			EditorActionType type = EditorActionType.fromAction(action);
			actionTypeButton = addDynamicWidget(new Button.Builder(
					Component.literal("Action Type: " + type.label()),
					button -> cycleActionType()
			).pos(innerX, y).size(propertyWidth - 16, FIELD_HEIGHT).build());
			y += 28;

			y = buildActionEditor(action, innerX, labelX, fieldX, fieldWidth, y);
			propertyNoteY = y + 4;
			if (!type.fieldEditable()) {
				propertyNotes.add("This action uses JSON fallback in the current Phase 5 editor.");
				propertyNotes.add("Copy it, edit JSON externally, then paste it back.");
			}
		}
		refreshButtonState();
	}

	private int buildActionEditor(SpellAction action, int innerX, int labelX, int fieldX, int fieldWidth, int y) {
		if (action instanceof SpellActions.NoopAction) {
			propertyNotes.add("Noop has no editable fields.");
			return y;
		}
		if (action instanceof SpellActions.ClearScreen) {
			propertyNotes.add("Clear Screen has no editable fields.");
			return y;
		}
		if (action instanceof SpellActions.SetVariable setVariable) {
			addPropertyField(labelX, fieldX, fieldWidth, y, "Key", setVariable.key(), 128, value -> {
				applyActionEdit(current -> current instanceof SpellActions.SetVariable set
						? new SpellActions.SetVariable(value, set.value())
						: current, false);
			}, null);
			y += 24;
			addPropertyField(labelX, fieldX, fieldWidth, y, "Value", Double.toString(setVariable.value()), 32, value -> {
				Double parsed = tryParseDouble(value);
				if (parsed != null) {
					applyActionEdit(current -> current instanceof SpellActions.SetVariable set
							? new SpellActions.SetVariable(set.key(), parsed)
							: current, false);
				}
			}, DECIMAL_INPUT);
			return y + 24;
		}
		if (action instanceof SpellActions.AddVariable addVariable) {
			addPropertyField(labelX, fieldX, fieldWidth, y, "Key", addVariable.key(), 128, value -> {
				applyActionEdit(current -> current instanceof SpellActions.AddVariable add
						? new SpellActions.AddVariable(value, add.delta())
						: current, false);
			}, null);
			y += 24;
			addPropertyField(labelX, fieldX, fieldWidth, y, "Delta", Double.toString(addVariable.delta()), 32, value -> {
				Double parsed = tryParseDouble(value);
				if (parsed != null) {
					applyActionEdit(current -> current instanceof SpellActions.AddVariable add
							? new SpellActions.AddVariable(add.key(), parsed)
							: current, false);
				}
			}, DECIMAL_INPUT);
			return y + 24;
		}
		if (action instanceof SpellActions.ForcePhase forcePhase) {
			EditBox box = addPropertyField(labelX, fieldX, fieldWidth, y, "Phase", forcePhase.phaseId().toString(), MAX_ID_LENGTH, value -> {
				if (!ResourceLocation.isValidResourceLocation(value)) {
					return;
				}
				applyActionEdit(current -> current instanceof SpellActions.ForcePhase
						? new SpellActions.ForcePhase(new ResourceLocation(value))
						: current, false);
			}, null);
			box.setSuggestion(state.entryPhaseId);
			return y + 24;
		}
		if (action instanceof SpellActions.PlaySoundAction soundAction) {
			addPropertyField(labelX, fieldX, fieldWidth, y, "Sound", soundAction.soundId().toString(), MAX_ID_LENGTH, value -> {
				if (!ResourceLocation.isValidResourceLocation(value)) {
					return;
				}
				applyActionEdit(current -> current instanceof SpellActions.PlaySoundAction sound
						? new SpellActions.PlaySoundAction(new ResourceLocation(value), sound.volume(), sound.pitch())
						: current, false);
			}, null);
			y += 24;
			addPropertyField(labelX, fieldX, fieldWidth, y, "Volume", Float.toString(soundAction.volume()), 16, value -> {
				Float parsed = tryParseFloat(value);
				if (parsed != null) {
					applyActionEdit(current -> current instanceof SpellActions.PlaySoundAction sound
							? new SpellActions.PlaySoundAction(sound.soundId(), parsed, sound.pitch())
							: current, false);
				}
			}, DECIMAL_INPUT);
			y += 24;
			addPropertyField(labelX, fieldX, fieldWidth, y, "Pitch", Float.toString(soundAction.pitch()), 16, value -> {
				Float parsed = tryParseFloat(value);
				if (parsed != null) {
					applyActionEdit(current -> current instanceof SpellActions.PlaySoundAction sound
							? new SpellActions.PlaySoundAction(sound.soundId(), sound.volume(), parsed)
							: current, false);
				}
			}, DECIMAL_INPUT);
			return y + 24;
		}
		if (action instanceof SpellActions.ConditionalAction conditional) {
			propertyNotes.add("Condition: " + SpellEditorSummary.summarizeCondition(conditional.condition()));
			propertyNotes.add("if_true: " + conditional.ifTrue().size() + " action(s)");
			propertyNotes.add("if_false: " + conditional.ifFalse().size() + " action(s)");
			return y;
		}
		if (action instanceof SpellActions.SequenceAction sequence) {
			propertyNotes.add("Sequence contains " + sequence.actions().size() + " action(s).");
			return y;
		}
		if (action instanceof dev.xkmc.youkaishomecoming.content.spell.action.LegacyTickerAction) {
			propertyNotes.add("Legacy ticker actions cannot be structured-edited here.");
			return y;
		}
		propertyNotes.add("No form editor for " + action.getClass().getSimpleName() + ".");
		return y;
	}

	private int buildConditionEditor(SpellCondition condition, int innerX, int labelX, int fieldX, int fieldWidth, int y) {
		if (condition instanceof SpellConditions.AlwaysCondition always) {
			alwaysToggleButton = addDynamicWidget(new Button.Builder(
					Component.literal("Always: " + (always.value() ? "True" : "False")),
					button -> applyConditionEdit(current -> current instanceof SpellConditions.AlwaysCondition value
							? new SpellConditions.AlwaysCondition(!value.value())
							: current, true)
			).pos(innerX, y).size(propertyWidth - 16, FIELD_HEIGHT).build());
			return y + 24;
		}
		if (condition instanceof SpellConditions.HealthBelow below) {
			addPropertyField(labelX, fieldX, fieldWidth, y, "Threshold", Float.toString(below.threshold()), 16, value -> {
				Float parsed = tryParseFloat(value);
				if (parsed != null) {
					applyConditionEdit(current -> current instanceof SpellConditions.HealthBelow
							? new SpellConditions.HealthBelow(parsed)
							: current, false);
				}
			}, DECIMAL_INPUT);
			return y + 24;
		}
		if (condition instanceof SpellConditions.HealthAbove above) {
			addPropertyField(labelX, fieldX, fieldWidth, y, "Threshold", Float.toString(above.threshold()), 16, value -> {
				Float parsed = tryParseFloat(value);
				if (parsed != null) {
					applyConditionEdit(current -> current instanceof SpellConditions.HealthAbove
							? new SpellConditions.HealthAbove(parsed)
							: current, false);
				}
			}, DECIMAL_INPUT);
			return y + 24;
		}
		if (condition instanceof SpellConditions.TickElapsed tickElapsed) {
			addPropertyField(labelX, fieldX, fieldWidth, y, "Ticks", Integer.toString(tickElapsed.ticks()), 16, value -> {
				Integer parsed = tryParseInt(value);
				if (parsed != null) {
					applyConditionEdit(current -> current instanceof SpellConditions.TickElapsed
							? new SpellConditions.TickElapsed(parsed)
							: current, false);
				}
			}, INTEGER_INPUT);
			return y + 24;
		}
		if (condition instanceof SpellConditions.DistanceAbove distanceAbove) {
			addPropertyField(labelX, fieldX, fieldWidth, y, "Distance", Double.toString(distanceAbove.distance()), 16, value -> {
				Double parsed = tryParseDouble(value);
				if (parsed != null) {
					applyConditionEdit(current -> current instanceof SpellConditions.DistanceAbove
							? new SpellConditions.DistanceAbove(parsed)
							: current, false);
				}
			}, DECIMAL_INPUT);
			return y + 24;
		}
		if (condition instanceof SpellConditions.DistanceBelow distanceBelow) {
			addPropertyField(labelX, fieldX, fieldWidth, y, "Distance", Double.toString(distanceBelow.distance()), 16, value -> {
				Double parsed = tryParseDouble(value);
				if (parsed != null) {
					applyConditionEdit(current -> current instanceof SpellConditions.DistanceBelow
							? new SpellConditions.DistanceBelow(parsed)
							: current, false);
				}
			}, DECIMAL_INPUT);
			return y + 24;
		}
		if (condition instanceof SpellConditions.HitCountCondition hitCount) {
			addPropertyField(labelX, fieldX, fieldWidth, y, "Count", Integer.toString(hitCount.count()), 16, value -> {
				Integer parsed = tryParseInt(value);
				if (parsed != null) {
					applyConditionEdit(current -> current instanceof SpellConditions.HitCountCondition
							? new SpellConditions.HitCountCondition(parsed)
							: current, false);
				}
			}, INTEGER_INPUT);
			return y + 24;
		}
		if (condition instanceof SpellConditions.VariableCheck variableCheck) {
			addPropertyField(labelX, fieldX, fieldWidth, y, "Key", variableCheck.key(), 128, value -> {
				applyConditionEdit(current -> current instanceof SpellConditions.VariableCheck check
						? new SpellConditions.VariableCheck(value, check.op(), check.value())
						: current, false);
			}, null);
			y += 24;
			variableOpButton = addDynamicWidget(new Button.Builder(
					Component.literal("Operator: " + variableCheck.op()),
					button -> cycleVariableOperator()
			).pos(innerX, y).size(propertyWidth - 16, FIELD_HEIGHT).build());
			y += 24;
			addPropertyField(labelX, fieldX, fieldWidth, y, "Value", Double.toString(variableCheck.value()), 16, value -> {
				Double parsed = tryParseDouble(value);
				if (parsed != null) {
					applyConditionEdit(current -> current instanceof SpellConditions.VariableCheck check
							? new SpellConditions.VariableCheck(check.key(), check.op(), parsed)
							: current, false);
				}
			}, DECIMAL_INPUT);
			return y + 24;
		}
		if (condition instanceof SpellConditions.NotCondition notCondition) {
			propertyNotes.add("Not: " + SpellEditorSummary.summarizeCondition(notCondition.condition()));
			return y;
		}
		if (condition instanceof SpellConditions.AndCondition andCondition) {
			propertyNotes.add("And with " + andCondition.conditions().size() + " sub-conditions.");
			return y;
		}
		if (condition instanceof SpellConditions.OrCondition orCondition) {
			propertyNotes.add("Or with " + orCondition.conditions().size() + " sub-conditions.");
			return y;
		}
		propertyNotes.add("No form editor for " + condition.getClass().getSimpleName() + ".");
		return y;
	}

	private void clearDynamicWidgets() {
		for (AbstractWidget widget : dynamicWidgets) {
			removeWidget(widget);
		}
		dynamicWidgets.clear();
	}

	private <T extends AbstractWidget> T addDynamicWidget(T widget) {
		dynamicWidgets.add(widget);
		return addRenderableWidget(widget);
	}

	private EditBox addPropertyField(int labelX, int fieldX, int fieldWidth, int y, String label, String initialValue,
									 int maxLength, Consumer<String> responder, @Nullable Pattern filter) {
		dynamicLabels.add(new LabelSpec(label, labelX, y + 6, 0xE0E0E0));
		EditBox box = addDynamicWidget(new EditBox(font, fieldX, y, fieldWidth, FIELD_HEIGHT, Component.empty()));
		box.setMaxLength(maxLength);
		box.setValue(initialValue);
		if (filter != null) {
			box.setFilter(value -> filter.matcher(value).matches());
		}
		box.setResponder(value -> {
			if (!syncingWidgets) {
				responder.accept(value);
			}
		});
		return box;
	}

	private EditBox createBoundField(int x, int y, int width, int maxLength, Consumer<String> consumer) {
		EditBox box = new EditBox(font, x, y, width, FIELD_HEIGHT, Component.empty());
		box.setMaxLength(maxLength);
		box.setResponder(value -> {
			if (!syncingWidgets) {
				consumer.accept(value);
			}
		});
		return box;
	}

	private void addStaticLabel(String text, int x, int y) {
		staticLabels.add(new LabelSpec(text, x, y, 0xE0E0E0));
	}

	private void drawLabels(GuiGraphics graphics, List<LabelSpec> labels) {
		for (LabelSpec label : labels) {
			graphics.drawString(font, label.text(), label.x(), label.y(), label.color(), false);
		}
	}

	private void drawPhaseInfo(GuiGraphics graphics) {
		int infoX = requiresTargetButton.getX() + requiresTargetButton.getWidth() + 12;
		int infoY = metaY + 76;
		String entryText = "Entry Phase: " + state.entryPhaseId;
		graphics.drawString(font, entryText, infoX, infoY + 6, 0xB0D8FF, false);
	}

	private void drawPropertyInfo(GuiGraphics graphics) {
		int x = propertyX + 8;
		int y = propertyY + 58;
		PhaseDefinition phase = state.getSelectedPhaseDefinition();
		if (phase == null) {
			graphics.drawString(font, "No phase selected", x, y, 0xD0D0D0, false);
			return;
		}
		String summary = "Entry: " + (phase.id.toString().equals(state.entryPhaseId) ? "Yes" : "No")
				+ " | Section: " + selectedSection.title()
				+ " | Items: " + state.getEntryCount(selectedSection);
		graphics.drawString(font, summary, x, y, 0xD0D0D0, false);
		if (selectedSection.isTransitionSection()) {
			Transition transition = getSelectedTransition();
			if (transition != null) {
				graphics.drawString(font, "Selected: " + SpellEditorSummary.summarizeTransition(transition), x, y + 12, 0xB8C7D1, false);
			}
		} else {
			SpellAction action = getSelectedAction();
			if (action != null) {
				graphics.drawString(font, "Selected: " + SpellEditorSummary.summarizeAction(action), x, y + 12, 0xB8C7D1, false);
			}
		}
	}

	private void drawPropertyNotes(GuiGraphics graphics) {
		int x = propertyX + 8;
		int y = propertyNoteY;
		for (String note : propertyNotes) {
			for (var line : font.split(Component.literal(note), propertyWidth - 16)) {
				if (y > bodyBottom - 12) {
					return;
				}
				graphics.drawString(font, line, x, y, 0xD0D0D0, false);
				y += 10;
			}
			y += 2;
		}
	}

	private void drawPanel(GuiGraphics graphics, int x, int y, int width, int height, String title) {
		graphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0x55222222);
		graphics.drawString(font, title, x + 4, y - 14, 0xE0E0E0, false);
	}

	private void onDefinitionEdited() {
		state.recordSnapshot();
		refreshButtonState();
	}

	private void setSelectedSection(PhaseEditorSection section) {
		if (selectedSection == section) {
			return;
		}
		selectedSection = section;
		selectedEntryIndex = -1;
		refreshEntryList();
		rebuildPropertyWidgets();
		refreshButtonState();
	}

	private void onSelectedPhaseChanged(ResourceLocation phaseId) {
		state.setSelectedPhase(phaseId);
		selectedEntryIndex = -1;
		refreshPhaseListSelection();
		refreshEntryList();
		syncPhaseField();
		rebuildPropertyWidgets();
		refreshButtonState();
	}

	private void addPhase() {
		ResourceLocation phaseId = state.addPhase();
		state.recordSnapshot();
		selectedEntryIndex = -1;
		refreshAllLists();
		syncWidgetsFromState();
		rebuildPropertyWidgets();
		setStatus("Added phase " + phaseId, 0xFF90EE90);
	}

	private void deletePhase() {
		String error = state.getRemoveSelectedPhaseError();
		if (error != null) {
			setStatus(error, 0xFFFF8080);
			return;
		}
		if (state.removeSelectedPhase()) {
			state.recordSnapshot();
			selectedEntryIndex = -1;
			refreshAllLists();
			syncWidgetsFromState();
			rebuildPropertyWidgets();
			setStatus("Deleted selected phase", 0xFF90EE90);
		}
	}

	private void renameSelectedPhase() {
		try {
			ResourceLocation renamed = state.renameSelectedPhase(phaseIdBox.getValue());
			state.recordSnapshot();
			refreshAllLists();
			syncWidgetsFromState();
			rebuildPropertyWidgets();
			setStatus("Renamed phase to " + renamed, 0xFF90EE90);
		} catch (Exception e) {
			setStatus("Rename failed: " + e.getMessage(), 0xFFFF8080);
		}
	}

	private void duplicatePhase() {
		try {
			ResourceLocation duplicated = state.duplicateSelectedPhase();
			state.recordSnapshot();
			selectedEntryIndex = -1;
			refreshAllLists();
			syncWidgetsFromState();
			rebuildPropertyWidgets();
			setStatus("Duplicated phase to " + duplicated, 0xFF90EE90);
		} catch (Exception e) {
			setStatus("Duplicate failed: " + e.getMessage(), 0xFFFF8080);
		}
	}

	private void addEntry() {
		try {
			selectedEntryIndex = state.addDefaultEntry(selectedSection);
			state.recordSnapshot();
			refreshAllLists();
			rebuildPropertyWidgets();
			setStatus("Added " + selectedSection.title() + " entry", 0xFF90EE90);
		} catch (Exception e) {
			setStatus("Add failed: " + e.getMessage(), 0xFFFF8080);
		}
	}

	private void deleteEntry() {
		if (selectedEntryIndex < 0) {
			setStatus("No entry selected", 0xFFFF8080);
			return;
		}
		selectedEntryIndex = state.removeEntry(selectedSection, selectedEntryIndex);
		state.recordSnapshot();
		refreshAllLists();
		rebuildPropertyWidgets();
		setStatus("Deleted selected entry", 0xFF90EE90);
	}

	private void duplicateEntry() {
		if (selectedEntryIndex < 0) {
			setStatus("No entry selected", 0xFFFF8080);
			return;
		}
		selectedEntryIndex = state.duplicateEntry(selectedSection, selectedEntryIndex);
		if (selectedEntryIndex >= 0) {
			state.recordSnapshot();
			refreshAllLists();
			rebuildPropertyWidgets();
			setStatus("Duplicated selected entry", 0xFF90EE90);
		}
	}

	private void moveEntry(int delta) {
		if (selectedEntryIndex < 0) {
			setStatus("No entry selected", 0xFFFF8080);
			return;
		}
		int moved = state.moveEntry(selectedSection, selectedEntryIndex, delta);
		if (moved != selectedEntryIndex) {
			selectedEntryIndex = moved;
			state.recordSnapshot();
			refreshAllLists();
			rebuildPropertyWidgets();
			setStatus("Moved entry " + (delta < 0 ? "up" : "down"), 0xFF90EE90);
		}
	}

	private void copySelectedItemJson() {
		try {
			String json;
			if (selectedSection.isTransitionSection()) {
				Transition transition = getSelectedTransition();
				if (transition == null) {
					setStatus("No transition selected", 0xFFFF8080);
					return;
				}
				json = SpellEditorCodec.encodeTransitionJson(transition);
			} else {
				SpellAction action = getSelectedAction();
				if (action == null) {
					setStatus("No action selected", 0xFFFF8080);
					return;
				}
				json = SpellEditorCodec.encodeActionJson(action);
			}
			Objects.requireNonNull(minecraft).keyboardHandler.setClipboard(json);
			setStatus("Entry JSON copied to clipboard", 0xFF90EE90);
		} catch (Exception e) {
			setStatus("Copy failed: " + e.getMessage(), 0xFFFF8080);
		}
	}

	private void pasteItemJson(boolean asNew) {
		String json = Objects.requireNonNull(minecraft).keyboardHandler.getClipboard();
		if (json == null || json.isBlank()) {
			setStatus("Clipboard is empty", 0xFFFF8080);
			return;
		}
		try {
			if (selectedSection.isTransitionSection()) {
				Transition transition = SpellEditorCodec.decodeTransitionJson(json);
				if (asNew || selectedEntryIndex < 0) {
					selectedEntryIndex = state.addTransition(transition);
				} else {
					state.replaceTransition(selectedEntryIndex, transition);
				}
			} else {
				SpellAction action = SpellEditorCodec.decodeActionJson(json);
				if (asNew || selectedEntryIndex < 0) {
					selectedEntryIndex = state.addAction(selectedSection, action);
				} else {
					state.replaceAction(selectedSection, selectedEntryIndex, action);
				}
			}
			state.recordSnapshot();
			refreshAllLists();
			rebuildPropertyWidgets();
			setStatus(asNew ? "Pasted JSON as new entry" : "Pasted JSON into selected entry", 0xFF90EE90);
		} catch (Exception e) {
			setStatus("Paste failed: " + e.getMessage(), 0xFFFF8080);
		}
	}

	private void cycleActionType() {
		SpellAction action = getSelectedAction();
		if (action == null) {
			return;
		}
		EditorActionType current = EditorActionType.fromAction(action);
		EditorActionType next = nextActionType(current);
		try {
			SpellAction replacement = next.create(selectedPhaseOrFallback());
			applyActionEdit(value -> replacement, true);
			setStatus("Action type set to " + next.label(), 0xFF90EE90);
		} catch (Exception e) {
			setStatus("Action type switch failed: " + e.getMessage(), 0xFFFF8080);
		}
	}

	private void cycleConditionType() {
		Transition transition = getSelectedTransition();
		if (transition == null) {
			return;
		}
		EditorConditionType current = EditorConditionType.fromCondition(transition.condition());
		EditorConditionType next = current.next();
		applyTransitionEdit(value -> new Transition(next.create(), value.targetPhase(), value.mode()), true);
		setStatus("Condition type set to " + next.label(), 0xFF90EE90);
	}

	private void cycleTransitionMode() {
		Transition transition = getSelectedTransition();
		if (transition == null) {
			return;
		}
		TransitionMode[] values = TransitionMode.values();
		TransitionMode next = values[(transition.mode().ordinal() + 1) % values.length];
		applyTransitionEdit(value -> new Transition(value.condition(), value.targetPhase(), next), true);
	}

	private void cycleVariableOperator() {
		Transition transition = getSelectedTransition();
		if (transition == null || !(transition.condition() instanceof SpellConditions.VariableCheck check)) {
			return;
		}
		int index = VARIABLE_OPERATORS.indexOf(check.op());
		String next = VARIABLE_OPERATORS.get((index + 1 + VARIABLE_OPERATORS.size()) % VARIABLE_OPERATORS.size());
		applyConditionEdit(value -> value instanceof SpellConditions.VariableCheck variable
				? new SpellConditions.VariableCheck(variable.key(), next, variable.value())
				: value, true);
	}

	private void applyActionEdit(Function<SpellAction, SpellAction> editor, boolean rebuildProperty) {
		SpellAction action = getSelectedAction();
		if (action == null) {
			return;
		}
		if (state.replaceAction(selectedSection, selectedEntryIndex, editor.apply(action))) {
			state.recordSnapshot();
			refreshEntryList();
			if (rebuildProperty) {
				rebuildPropertyWidgets();
			} else {
				refreshButtonState();
			}
		}
	}

	private void applyTransitionEdit(Function<Transition, Transition> editor, boolean rebuildProperty) {
		Transition transition = getSelectedTransition();
		if (transition == null) {
			return;
		}
		if (state.replaceTransition(selectedEntryIndex, editor.apply(transition))) {
			state.recordSnapshot();
			refreshEntryList();
			if (rebuildProperty) {
				rebuildPropertyWidgets();
			} else {
				refreshButtonState();
			}
		}
	}

	private void applyConditionEdit(Function<SpellCondition, SpellCondition> editor, boolean rebuildProperty) {
		Transition transition = getSelectedTransition();
		if (transition == null) {
			return;
		}
		applyTransitionEdit(value -> new Transition(editor.apply(value.condition()), value.targetPhase(), value.mode()), rebuildProperty);
	}

	private void refreshAllLists() {
		refreshPhaseList();
		refreshEntryList();
	}

	private void refreshPhaseList() {
		phaseList.refresh();
	}

	private void refreshPhaseListSelection() {
		phaseList.refreshSelection();
	}

	private void refreshEntryList() {
		ensureEntrySelection();
		entryList.refresh();
	}

	private void ensureEntrySelection() {
		int count = state.getEntryCount(selectedSection);
		if (count <= 0) {
			selectedEntryIndex = -1;
			return;
		}
		if (selectedEntryIndex < 0) {
			selectedEntryIndex = 0;
			return;
		}
		if (selectedEntryIndex >= count) {
			selectedEntryIndex = count - 1;
		}
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
			syncPhaseField();
			refreshPhaseListSelection();
		} finally {
			syncingWidgets = false;
		}
		refreshButtonState();
	}

	private void syncPhaseField() {
		phaseIdBox.setValue(state.selectedPhase == null ? "" : state.selectedPhase.toString());
	}

	private void refreshButtonState() {
		List<String> errors = state.validate();
		saveButton.active = errors.isEmpty();
		saveButton.setMessage(Component.literal(state.isDirty() ? "Save*" : "Save"));
		copyJsonButton.active = errors.isEmpty();
		loadButton.active = canLoadProject();
		undoButton.active = state.canUndo();
		redoButton.active = state.canRedo();
		copyPhaseJsonButton.active = state.selectedPhase != null;
		deletePhaseButton.active = state.getRemoveSelectedPhaseError() == null;
		duplicatePhaseButton.active = state.selectedPhase != null;
		renamePhaseButton.active = state.selectedPhase != null;
		setEntryPhaseButton.active = state.selectedPhase != null;
		updateSectionButtons();
		boolean hasSelection = selectedEntryIndex >= 0;
		addEntryButton.active = state.selectedPhase != null;
		deleteEntryButton.active = hasSelection;
		duplicateEntryButton.active = hasSelection;
		copyEntryButton.active = hasSelection;
		pasteEntryButton.active = state.selectedPhase != null;
		pasteAsNewEntryButton.active = state.selectedPhase != null;
		moveUpEntryButton.active = hasSelection && selectedEntryIndex > 0;
		moveDownEntryButton.active = hasSelection && selectedEntryIndex >= 0 && selectedEntryIndex < state.getEntryCount(selectedSection) - 1;
		generateItemButton.setMessage(toggleLabel("Generate Item", state.generateItem));
		requiresTargetButton.setMessage(toggleLabel("Requires Target", state.requiresTarget));

		if (errors.isEmpty()) {
			setStatus(state.isDirty() ? "Modified" : "Ready", state.isDirty() ? 0xFFFFD37A : 0xFF90EE90);
		} else {
			setStatus(errors.get(0), 0xFFFF8080);
		}
	}

	private void updateSectionButtons() {
		for (int i = 0; i < sectionButtons.size(); i++) {
			PhaseEditorSection section = PhaseEditorSection.values()[i];
			Button button = sectionButtons.get(i);
			button.active = section != selectedSection;
			button.setMessage(Component.literal(section == selectedSection ? "[" + section.title() + "]" : section.title()));
		}
	}

	private boolean canLoadProject() {
		String spellId = state.spellId.trim();
		return ResourceLocation.isValidResourceLocation(spellId)
				&& SpellEditorIO.hasProject(new ResourceLocation(spellId));
	}

	private void undo() {
		state.undo();
		selectedEntryIndex = -1;
		refreshAllLists();
		syncWidgetsFromState();
		rebuildPropertyWidgets();
	}

	private void redo() {
		state.redo();
		selectedEntryIndex = -1;
		refreshAllLists();
		syncWidgetsFromState();
		rebuildPropertyWidgets();
	}

	private void resetToOriginal() {
		state.resetToOriginal();
		selectedEntryIndex = -1;
		refreshAllLists();
		syncWidgetsFromState();
		rebuildPropertyWidgets();
		setStatus("Reset to original definition", 0xFFE0E0E0);
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
			selectedEntryIndex = -1;
			refreshAllLists();
			syncWidgetsFromState();
			rebuildPropertyWidgets();
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
			Objects.requireNonNull(minecraft).keyboardHandler.setClipboard(json);
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
			Objects.requireNonNull(minecraft).keyboardHandler.setClipboard(json);
			setStatus("Phase JSON copied to clipboard", 0xFF90EE90);
		} catch (Exception e) {
			setStatus("Copy phase failed: " + e.getMessage(), 0xFFFF8080);
		}
	}

	private void setStatus(String text, int color) {
		status = Component.literal(text);
		statusColor = color;
	}

	private SpellAction getSelectedAction() {
		return state.getAction(selectedSection, selectedEntryIndex);
	}

	@Nullable
	private Transition getSelectedTransition() {
		return state.getTransition(selectedEntryIndex);
	}

	private ResourceLocation selectedPhaseOrFallback() {
		return state.selectedPhase != null ? state.selectedPhase : state.getPhaseIds().get(0);
	}

	private EditorActionType nextActionType(EditorActionType current) {
		EditorActionType candidate = current;
		for (int i = 0; i < EditorActionType.values().length; i++) {
			candidate = candidate.next();
			try {
				candidate.create(selectedPhaseOrFallback());
				return candidate;
			} catch (Exception ignored) {
			}
		}
		return current;
	}

	private static String formatModeLabel(TransitionMode mode) {
		return mode.name().toLowerCase(Locale.ROOT);
	}

	private static Component toggleLabel(String label, boolean value) {
		return Component.literal(label + ": " + (value ? "On" : "Off"));
	}

	@Nullable
	private static Double tryParseDouble(String value) {
		if (value.isBlank() || "-".equals(value) || ".".equals(value) || "-.".equals(value)) {
			return null;
		}
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	@Nullable
	private static Float tryParseFloat(String value) {
		if (value.isBlank() || "-".equals(value) || ".".equals(value) || "-.".equals(value)) {
			return null;
		}
		try {
			return Float.parseFloat(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	@Nullable
	private static Integer tryParseInt(String value) {
		if (value.isBlank() || "-".equals(value)) {
			return null;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private record LabelSpec(String text, int x, int y, int color) {
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
			return phaseWidth - 20;
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
			String title = phaseId.toString().equals(state.entryPhaseId) ? "* " + phaseId : phaseId.toString();
			graphics.drawString(font, title, left + 4, top + 4, 0xFFFFFF, false);
			PhaseDefinition phase = state.phases.get(phaseId);
			if (phase != null) {
				String summary = "E" + phase.onEnter.size() + " T" + phase.onTick.size()
						+ " X" + phase.onExit.size() + " R" + phase.transitions.size();
				graphics.drawString(font, summary, left + 4, top + 13, 0xB8C7D1, false);
			}
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (button == 0) {
				phaseList.setSelected(this);
				onSelectedPhaseChanged(phaseId);
				return true;
			}
			return false;
		}

		@Override
		public Component getNarration() {
			return Component.literal(phaseId.toString());
		}
	}

	private class EntryListWidget extends ObjectSelectionList<EntryRow> {

		private EntryListWidget(Minecraft minecraft, int width, int height, int y0, int y1, int itemHeight) {
			super(minecraft, width, height, y0, y1, itemHeight);
			setRenderBackground(false);
			setRenderTopAndBottom(false);
		}

		private void refresh() {
			clearEntries();
			int count = state.getEntryCount(selectedSection);
			for (int i = 0; i < count; i++) {
				addEntry(new EntryRow(i));
			}
			refreshSelection();
		}

		private void refreshSelection() {
			for (EntryRow row : children()) {
				if (row.index == selectedEntryIndex) {
					setSelected(row);
					return;
				}
			}
			setSelected(null);
		}

		@Override
		public int getRowWidth() {
			return entryWidth - 20;
		}
	}

	private class EntryRow extends ObjectSelectionList.Entry<EntryRow> {

		private final int index;

		private EntryRow(int index) {
			this.index = index;
		}

		@Override
		public void render(GuiGraphics graphics, int rowIndex, int top, int left, int width, int height,
						   int mouseX, int mouseY, boolean hovered, float partialTick) {
			boolean selected = index == selectedEntryIndex;
			int bg = selected ? 0xAA6C5B2A : hovered ? 0x66333333 : 0x33161616;
			graphics.fill(left, top, left + width, top + height - 1, bg);
			String summary;
			String typeLine;
			if (selectedSection.isTransitionSection()) {
				Transition transition = state.getTransition(index);
				summary = transition == null ? "(missing)" : SpellEditorSummary.summarizeTransition(transition);
				typeLine = transition == null ? "" : EditorConditionType.fromCondition(transition.condition()).label();
			} else {
				SpellAction action = state.getAction(selectedSection, index);
				summary = action == null ? "(missing)" : SpellEditorSummary.summarizeAction(action);
				typeLine = action == null ? "" : EditorActionType.fromAction(action).label();
			}
			graphics.drawString(font, (index + 1) + ". " + summary, left + 4, top + 4, 0xFFFFFF, false);
			graphics.drawString(font, typeLine, left + 4, top + 13, 0xB8C7D1, false);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (button == 0) {
				selectedEntryIndex = index;
				entryList.setSelected(this);
				rebuildPropertyWidgets();
				return true;
			}
			return false;
		}

		@Override
		public Component getNarration() {
			return Component.literal(Integer.toString(index + 1));
		}
	}
}
