package dev.xkmc.youkaishomecoming.content.spell.editor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDisplay;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellItemForm;
import dev.xkmc.youkaishomecoming.content.spell.definition.Transition;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyProfile;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class EditorState {

	private static final int MAX_HISTORY = 64;

	private final SpellDefinition originalDefinition;

	public String spellId;
	public String displayName;
	public String description;
	public String iconId;
	public String modelId;
	public String itemIconId;
	public String cooldown;
	public boolean generateItem;
	public boolean requiresTarget;
	public String entryPhaseId;
	@Nullable
	public ResourceLocation selectedPhase;

	public final LinkedHashMap<ResourceLocation, PhaseDefinition> phases = new LinkedHashMap<>();
	public final LinkedHashMap<ResourceLocation, EditorNodeLayout> phaseLayouts = new LinkedHashMap<>();

	public double viewX;
	public double viewY;
	public double zoom;

	private final Deque<String> undo = new ArrayDeque<>();
	private final Deque<String> redo = new ArrayDeque<>();
	private boolean restoringSnapshot;
	private String cleanSnapshot = "";

	private EditorState(SpellDefinition originalDefinition) {
		this.originalDefinition = originalDefinition;
	}

	public static EditorState fromDefinition(SpellDefinition definition) {
		return fromProject(new SpellEditorData(definition, EditorLayout.DEFAULT), definition);
	}

	public static EditorState fromProject(SpellEditorData data, SpellDefinition originalDefinition) {
		EditorState state = new EditorState(originalDefinition);
		state.applyProject(data);
		state.recordSnapshot();
		state.markSaved();
		return state;
	}

	public SpellDefinition getOriginalDefinition() {
		return originalDefinition;
	}

	public void resetToOriginal() {
		undo.clear();
		redo.clear();
		applyProject(new SpellEditorData(originalDefinition, EditorLayout.DEFAULT));
		recordSnapshot();
	}

	public void loadProject(SpellEditorData data) {
		undo.clear();
		redo.clear();
		applyProject(data);
		recordSnapshot();
		markSaved();
	}

	public List<String> validate() {
		List<String> errors = new ArrayList<>();
		ResourceLocation id = parseRequiredId(spellId, "Spell ID", errors);
		parseOptionalId(iconId, "Icon ID", errors);
		parseOptionalId(modelId, "Model ID", errors);
		parseOptionalId(itemIconId, "Item Icon", errors);
		int parsedCooldown = parseCooldown(errors);
		if (displayName.trim().isEmpty()) {
			errors.add("Display name cannot be empty");
		}
		if (phases.isEmpty()) {
			errors.add("At least one phase is required");
		}
		ResourceLocation entry = parseRequiredId(entryPhaseId, "Entry phase", errors);
		if (entry != null && !phases.containsKey(entry)) {
			errors.add("Entry phase does not exist in current phase list");
		}
		if (parsedCooldown < 0) {
			errors.add("Cooldown must be a non-negative integer");
		}
		if (id != null && !phases.isEmpty()) {
			for (var phaseEntry : phases.entrySet()) {
				ResourceLocation phaseId = phaseEntry.getKey();
				if (phaseId.toString().isBlank()) {
					errors.add("Phase ID cannot be blank");
				}
				PhaseDefinition phase = phaseEntry.getValue();
				if (!phaseId.equals(phase.id)) {
					errors.add("Phase definition id does not match key: " + phaseId);
				}
				for (Transition transition : phase.transitions) {
					if (!phases.containsKey(transition.targetPhase())) {
						errors.add("Transition from " + phaseId + " targets missing phase " + transition.targetPhase());
					}
				}
			}
		}
		return errors;
	}

	public SpellDefinition buildDefinition() {
		List<String> errors = validate();
		if (!errors.isEmpty()) {
			throw new IllegalStateException(errors.get(0));
		}
		ResourceLocation id = new ResourceLocation(spellId.trim());
		ResourceLocation entry = new ResourceLocation(entryPhaseId.trim());
		ResourceLocation icon = parseNullable(iconId);
		ResourceLocation model = parseNullable(modelId);
		ResourceLocation itemIcon = parseNullable(itemIconId);
		int parsedCooldown = Integer.parseInt(cooldown.trim());
		return new SpellDefinition(
				id,
				new SpellDisplay(displayName.trim(), description.trim(), icon, model),
				new SpellItemForm(generateItem, parsedCooldown, requiresTarget, itemIcon),
				entry,
				phases,
				DifficultyProfile.DEFAULT
		);
	}

	public SpellEditorData buildProjectData() {
		return new SpellEditorData(
				buildDefinition(),
				new EditorLayout(new LinkedHashMap<>(phaseLayouts), viewX, viewY, zoom)
		);
	}

	public boolean canUndo() {
		return undo.size() > 1;
	}

	public boolean canRedo() {
		return !redo.isEmpty();
	}

	public boolean isDirty() {
		return !currentSnapshot().equals(cleanSnapshot);
	}

	public void markSaved() {
		cleanSnapshot = currentSnapshot();
	}

	public void undo() {
		if (!canUndo()) {
			return;
		}
		String current = undo.removeLast();
		redo.addLast(current);
		applySnapshot(undo.getLast());
	}

	public void redo() {
		if (!canRedo()) {
			return;
		}
		String next = redo.removeLast();
		undo.addLast(next);
		applySnapshot(next);
	}

	public void recordSnapshot() {
		if (restoringSnapshot) {
			return;
		}
		String snapshot = createSnapshot();
		if (!undo.isEmpty() && undo.peekLast().equals(snapshot)) {
			return;
		}
		undo.addLast(snapshot);
		while (undo.size() > MAX_HISTORY) {
			undo.removeFirst();
		}
		redo.clear();
	}

	public List<String> describeSelectedPhase() {
		PhaseDefinition phase = getSelectedPhaseDefinition();
		if (phase == null) {
			return List.of("No phase selected");
		}
		List<String> lines = new ArrayList<>();
		lines.add("Phase ID: " + phase.id);
		lines.add("Entry Phase: " + (phase.id.toString().equals(entryPhaseId) ? "Yes" : "No"));
		lines.add("On Enter: " + phase.onEnter.size());
		lines.add("On Tick: " + phase.onTick.size());
		lines.add("On Exit: " + phase.onExit.size());
		lines.add("Transitions: " + phase.transitions.size());
		appendActionLines(lines, "enter", phase.onEnter);
		appendActionLines(lines, "tick", phase.onTick);
		appendActionLines(lines, "exit", phase.onExit);
		for (int i = 0; i < phase.transitions.size(); i++) {
			var transition = phase.transitions.get(i);
			lines.add("transition[" + i + "]: " + shortName(transition.condition()) +
					" -> " + transition.targetPhase() + " (" + transition.mode().name().toLowerCase(Locale.ROOT) + ")");
		}
		return lines;
	}

	public List<ResourceLocation> getPhaseIds() {
		return Collections.unmodifiableList(new ArrayList<>(phases.keySet()));
	}

	@Nullable
	public PhaseDefinition getSelectedPhaseDefinition() {
		return selectedPhase == null ? null : phases.get(selectedPhase);
	}

	public void setSelectedPhase(@Nullable ResourceLocation phaseId) {
		if (phaseId != null && phases.containsKey(phaseId)) {
			selectedPhase = phaseId;
		} else {
			selectedPhase = phases.isEmpty() ? null : phases.keySet().iterator().next();
		}
	}

	public void setEntryPhase(@Nullable ResourceLocation phaseId) {
		if (phaseId != null && phases.containsKey(phaseId)) {
			entryPhaseId = phaseId.toString();
		}
	}

	public ResourceLocation addPhase() {
		ResourceLocation spell = parseCurrentSpellIdOrFallback();
		ResourceLocation phaseId = nextPhaseId(spell);
		PhaseDefinition phase = new PhaseDefinition(phaseId, List.of(), List.of(), List.of(), List.of());
		phases.put(phaseId, phase);
		phaseLayouts.put(phaseId, createDefaultLayout(phases.size() - 1));
		selectedPhase = phaseId;
		return phaseId;
	}

	@Nullable
	public String getRemoveSelectedPhaseError() {
		if (selectedPhase == null) {
			return "No phase selected";
		}
		if (phases.size() <= 1) {
			return "Cannot delete the last phase";
		}
		for (var entry : phases.entrySet()) {
			for (Transition transition : entry.getValue().transitions) {
				if (transition.targetPhase().equals(selectedPhase)) {
					return "Phase is still targeted by transitions";
				}
			}
		}
		return null;
	}

	public ResourceLocation renameSelectedPhase(String newIdText) {
		if (selectedPhase == null) {
			throw new IllegalStateException("No phase selected");
		}
		String trimmed = newIdText.trim();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("Phase ID cannot be empty");
		}
		if (!ResourceLocation.isValidResourceLocation(trimmed)) {
			throw new IllegalArgumentException("Invalid phase ID: " + trimmed);
		}
		ResourceLocation oldId = selectedPhase;
		ResourceLocation newId = new ResourceLocation(trimmed);
		if (oldId.equals(newId)) {
			return oldId;
		}
		if (phases.containsKey(newId)) {
			throw new IllegalArgumentException("Phase already exists: " + newId);
		}
		replacePhaseId(oldId, newId);
		return newId;
	}

	public ResourceLocation duplicateSelectedPhase() {
		if (selectedPhase == null) {
			throw new IllegalStateException("No phase selected");
		}
		ResourceLocation sourceId = selectedPhase;
		PhaseDefinition source = phases.get(sourceId);
		if (source == null) {
			throw new IllegalStateException("Selected phase is missing");
		}
		ResourceLocation duplicateId = nextDuplicatePhaseId(sourceId);
		LinkedHashMap<ResourceLocation, PhaseDefinition> duplicatedPhases = new LinkedHashMap<>();
		LinkedHashMap<ResourceLocation, EditorNodeLayout> duplicatedLayouts = new LinkedHashMap<>();
		for (var entry : phases.entrySet()) {
			ResourceLocation phaseId = entry.getKey();
			duplicatedPhases.put(phaseId, entry.getValue());
			if (phaseLayouts.containsKey(phaseId)) {
				duplicatedLayouts.put(phaseId, phaseLayouts.get(phaseId));
			}
			if (phaseId.equals(sourceId)) {
				duplicatedPhases.put(duplicateId, copyPhase(source, duplicateId, sourceId, duplicateId));
				EditorNodeLayout layout = phaseLayouts.getOrDefault(phaseId, createDefaultLayout(duplicatedPhases.size() - 1));
				duplicatedLayouts.put(duplicateId, new EditorNodeLayout(layout.x() + 32, layout.y() + 32));
			}
		}
		phases.clear();
		phases.putAll(duplicatedPhases);
		phaseLayouts.clear();
		phaseLayouts.putAll(duplicatedLayouts);
		ensurePhaseLayouts();
		selectedPhase = duplicateId;
		return duplicateId;
	}

	public boolean removeSelectedPhase() {
		if (getRemoveSelectedPhaseError() != null) {
			return false;
		}
		phases.remove(selectedPhase);
		phaseLayouts.remove(selectedPhase);
		if (entryPhaseId.equals(selectedPhase.toString())) {
			entryPhaseId = phases.keySet().iterator().next().toString();
		}
		selectedPhase = phases.keySet().iterator().next();
		return true;
	}

	private void applyProject(SpellEditorData data) {
		SpellDefinition definition = data.definition();
		EditorLayout layout = data.editor();
		spellId = definition.id.toString();
		displayName = definition.display.name();
		description = definition.display.description();
		iconId = definition.display.icon() == null ? "" : definition.display.icon().toString();
		modelId = definition.display.modelId() == null ? "" : definition.display.modelId().toString();
		itemIconId = definition.itemForm.iconItem() == null ? "" : definition.itemForm.iconItem().toString();
		cooldown = Integer.toString(definition.itemForm.cooldown());
		generateItem = definition.itemForm.generate();
		requiresTarget = definition.itemForm.requiresTarget();
		entryPhaseId = definition.entryPhase.toString();

		phases.clear();
		phases.putAll(definition.phases);

		phaseLayouts.clear();
		phaseLayouts.putAll(layout.phaseLayout());
		ensurePhaseLayouts();

		viewX = layout.viewX();
		viewY = layout.viewY();
		zoom = layout.zoom();
		setSelectedPhase(definition.entryPhase);
	}

	private void applySnapshot(String snapshotJson) {
		restoringSnapshot = true;
		try {
			SnapshotData snapshot = SpellEditorCodec.decode(SnapshotData.CODEC, snapshotJson);
			spellId = snapshot.spellId();
			displayName = snapshot.displayName();
			description = snapshot.description();
			iconId = snapshot.iconId();
			modelId = snapshot.modelId();
			itemIconId = snapshot.itemIconId();
			cooldown = snapshot.cooldown();
			generateItem = snapshot.generateItem();
			requiresTarget = snapshot.requiresTarget();
			entryPhaseId = snapshot.entryPhaseId();
			phases.clear();
			for (var entry : snapshot.phases().entrySet()) {
				phases.put(new ResourceLocation(entry.getKey()), entry.getValue());
			}
			phaseLayouts.clear();
			for (var entry : snapshot.phaseLayouts().entrySet()) {
				phaseLayouts.put(new ResourceLocation(entry.getKey()), entry.getValue());
			}
			ensurePhaseLayouts();
			selectedPhase = snapshot.selectedPhase().map(ResourceLocation::new).orElse(null);
			setSelectedPhase(selectedPhase);
			viewX = snapshot.viewX();
			viewY = snapshot.viewY();
			zoom = snapshot.zoom();
		} finally {
			restoringSnapshot = false;
		}
	}

	private ResourceLocation nextPhaseId(ResourceLocation spell) {
		String namespace = spell.getNamespace();
		String basePath = spell.getPath();
		String candidate = basePath + "/phase_" + Math.max(1, phases.size() + 1);
		int index = Math.max(1, phases.size() + 1);
		ResourceLocation id = new ResourceLocation(namespace, candidate);
		while (phases.containsKey(id)) {
			index++;
			id = new ResourceLocation(namespace, basePath + "/phase_" + index);
		}
		return id;
	}

	private ResourceLocation nextDuplicatePhaseId(ResourceLocation base) {
		String namespace = base.getNamespace();
		String path = base.getPath() + "_copy";
		ResourceLocation id = new ResourceLocation(namespace, path);
		int index = 2;
		while (phases.containsKey(id)) {
			id = new ResourceLocation(namespace, path + "_" + index);
			index++;
		}
		return id;
	}

	private void replacePhaseId(ResourceLocation oldId, ResourceLocation newId) {
		LinkedHashMap<ResourceLocation, PhaseDefinition> renamedPhases = new LinkedHashMap<>();
		LinkedHashMap<ResourceLocation, EditorNodeLayout> renamedLayouts = new LinkedHashMap<>();
		for (var entry : phases.entrySet()) {
			ResourceLocation sourceId = entry.getKey();
			ResourceLocation targetId = sourceId.equals(oldId) ? newId : sourceId;
			renamedPhases.put(targetId, copyPhase(entry.getValue(), targetId, oldId, newId));
			if (phaseLayouts.containsKey(sourceId)) {
				renamedLayouts.put(targetId, phaseLayouts.get(sourceId));
			}
		}
		phases.clear();
		phases.putAll(renamedPhases);
		phaseLayouts.clear();
		phaseLayouts.putAll(renamedLayouts);
		ensurePhaseLayouts();
		if (entryPhaseId.equals(oldId.toString())) {
			entryPhaseId = newId.toString();
		}
		selectedPhase = newId;
	}

	private void ensurePhaseLayouts() {
		int index = 0;
		for (ResourceLocation phaseId : phases.keySet()) {
			int layoutIndex = index;
			phaseLayouts.computeIfAbsent(phaseId, k -> createDefaultLayout(layoutIndex));
			index++;
		}
		phaseLayouts.keySet().removeIf(key -> !phases.containsKey(key));
	}

	private String currentSnapshot() {
		return undo.peekLast() == null ? createSnapshot() : undo.peekLast();
	}

	private String createSnapshot() {
		return SpellEditorCodec.encode(SnapshotData.CODEC, SnapshotData.fromState(this));
	}

	private ResourceLocation parseCurrentSpellIdOrFallback() {
		if (ResourceLocation.isValidResourceLocation(spellId.trim())) {
			return new ResourceLocation(spellId.trim());
		}
		return originalDefinition.id;
	}

	private int parseCooldown(List<String> errors) {
		String text = cooldown.trim();
		if (text.isEmpty()) {
			errors.add("Cooldown cannot be empty");
			return -1;
		}
		try {
			return Integer.parseInt(text);
		} catch (NumberFormatException e) {
			errors.add("Cooldown must be an integer");
			return -1;
		}
	}

	@Nullable
	private static ResourceLocation parseRequiredId(String text, String label, List<String> errors) {
		String trimmed = text.trim();
		if (trimmed.isEmpty()) {
			errors.add(label + " cannot be empty");
			return null;
		}
		if (!ResourceLocation.isValidResourceLocation(trimmed)) {
			errors.add(label + " is not a valid resource location: " + trimmed);
			return null;
		}
		return new ResourceLocation(trimmed);
	}

	@Nullable
	private static ResourceLocation parseOptionalId(String text, String label, List<String> errors) {
		String trimmed = text.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		if (!ResourceLocation.isValidResourceLocation(trimmed)) {
			errors.add(label + " is not a valid resource location: " + trimmed);
			return null;
		}
		return new ResourceLocation(trimmed);
	}

	@Nullable
	private static ResourceLocation parseNullable(String text) {
		String trimmed = text.trim();
		return trimmed.isEmpty() ? null : new ResourceLocation(trimmed);
	}

	private static PhaseDefinition copyPhase(PhaseDefinition phase,
											 ResourceLocation newPhaseId,
											 @Nullable ResourceLocation oldTarget,
											 @Nullable ResourceLocation newTarget) {
		List<Transition> transitions = new ArrayList<>(phase.transitions.size());
		for (Transition transition : phase.transitions) {
			ResourceLocation target = transition.targetPhase();
			if (oldTarget != null && newTarget != null && target.equals(oldTarget)) {
				target = newTarget;
			}
			transitions.add(target.equals(transition.targetPhase()) ? transition
					: new Transition(transition.condition(), target, transition.mode()));
		}
		return new PhaseDefinition(newPhaseId, phase.onEnter, phase.onTick, phase.onExit, transitions);
	}

	private static void appendActionLines(List<String> lines, String group, List<SpellAction> actions) {
		for (int i = 0; i < actions.size(); i++) {
			lines.add(group + "[" + i + "]: " + shortName(actions.get(i)));
		}
	}

	private static String shortName(Object value) {
		String simple = value.getClass().getSimpleName();
		if (!simple.isEmpty()) {
			return simple;
		}
		String full = value.getClass().getName();
		int split = Math.max(full.lastIndexOf('$'), full.lastIndexOf('.'));
		return split >= 0 ? full.substring(split + 1) : full;
	}

	private static EditorNodeLayout createDefaultLayout(int index) {
		return new EditorNodeLayout(80 + (index % 3) * 180, 80 + (index / 3) * 120);
	}

	private record SnapshotData(
			String spellId,
			String displayName,
			String description,
			String iconId,
			String modelId,
			String itemIconId,
			String cooldown,
			boolean generateItem,
			boolean requiresTarget,
			String entryPhaseId,
			Map<String, PhaseDefinition> phases,
			Map<String, EditorNodeLayout> phaseLayouts,
			Optional<String> selectedPhase,
			double viewX,
			double viewY,
			double zoom
	) {
		private static final Codec<SnapshotData> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("spell_id").forGetter(SnapshotData::spellId),
				Codec.STRING.fieldOf("display_name").forGetter(SnapshotData::displayName),
				Codec.STRING.optionalFieldOf("description", "").forGetter(SnapshotData::description),
				Codec.STRING.optionalFieldOf("icon_id", "").forGetter(SnapshotData::iconId),
				Codec.STRING.optionalFieldOf("model_id", "").forGetter(SnapshotData::modelId),
				Codec.STRING.optionalFieldOf("item_icon_id", "").forGetter(SnapshotData::itemIconId),
				Codec.STRING.fieldOf("cooldown").forGetter(SnapshotData::cooldown),
				Codec.BOOL.optionalFieldOf("generate_item", false).forGetter(SnapshotData::generateItem),
				Codec.BOOL.optionalFieldOf("requires_target", false).forGetter(SnapshotData::requiresTarget),
				Codec.STRING.fieldOf("entry_phase_id").forGetter(SnapshotData::entryPhaseId),
				Codec.unboundedMap(Codec.STRING, PhaseDefinition.CODEC).fieldOf("phases").forGetter(SnapshotData::phases),
				Codec.unboundedMap(Codec.STRING, EditorNodeLayout.CODEC).optionalFieldOf("phase_layouts", Map.of()).forGetter(SnapshotData::phaseLayouts),
				Codec.STRING.optionalFieldOf("selected_phase").forGetter(SnapshotData::selectedPhase),
				Codec.DOUBLE.optionalFieldOf("view_x", 0.0).forGetter(SnapshotData::viewX),
				Codec.DOUBLE.optionalFieldOf("view_y", 0.0).forGetter(SnapshotData::viewY),
				Codec.DOUBLE.optionalFieldOf("zoom", 1.0).forGetter(SnapshotData::zoom)
		).apply(i, SnapshotData::new));

		private SnapshotData {
			phases = new LinkedHashMap<>(phases);
			phaseLayouts = new LinkedHashMap<>(phaseLayouts);
		}

		private static SnapshotData fromState(EditorState state) {
			Map<String, PhaseDefinition> phases = new LinkedHashMap<>();
			for (var entry : state.phases.entrySet()) {
				phases.put(entry.getKey().toString(), entry.getValue());
			}
			Map<String, EditorNodeLayout> layouts = new LinkedHashMap<>();
			for (var entry : state.phaseLayouts.entrySet()) {
				layouts.put(entry.getKey().toString(), entry.getValue());
			}
			return new SnapshotData(
					state.spellId,
					state.displayName,
					state.description,
					state.iconId,
					state.modelId,
					state.itemIconId,
					state.cooldown,
					state.generateItem,
					state.requiresTarget,
					state.entryPhaseId,
					phases,
					layouts,
					Optional.ofNullable(state.selectedPhase).map(ResourceLocation::toString),
					state.viewX,
					state.viewY,
					state.zoom
			);
		}
	}
}
