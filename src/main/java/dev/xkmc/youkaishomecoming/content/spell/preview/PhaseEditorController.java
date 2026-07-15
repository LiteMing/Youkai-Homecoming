package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages phase-level operations: add, delete, rename, cycle, custom names.
 * Extracted from SpellPreviewScreen to reduce its size.
 */
@OnlyIn(Dist.CLIENT)
public class PhaseEditorController {

	private final List<ResourceLocation> phaseList = new ArrayList<>();
	private int selectedPhaseIndex = 0;

	private SpellDefinition definition;

	public PhaseEditorController(SpellDefinition definition) {
		this.definition = definition;
		this.phaseList.addAll(definition.phases.keySet());
	}

	// --- Accessors ---

	public List<ResourceLocation> getPhaseList() {
		return phaseList;
	}

	public int getSelectedPhaseIndex() {
		return selectedPhaseIndex;
	}

	public void setSelectedPhaseIndex(int index) {
		this.selectedPhaseIndex = index;
	}

	public ResourceLocation getSelectedPhaseId() {
		if (phaseList.isEmpty()) return null;
		return phaseList.get(selectedPhaseIndex);
	}

	public void setDefinition(SpellDefinition definition) {
		this.definition = definition;
	}

	// --- Phase list management ---

	public void reloadPhaseList() {
		phaseList.clear();
		phaseList.addAll(definition.phases.keySet());
		selectedPhaseIndex = Math.max(0, Math.min(selectedPhaseIndex, phaseList.size() - 1));
	}

	public void cyclePhase(int delta) {
		if (phaseList.isEmpty()) return;
		selectedPhaseIndex = (selectedPhaseIndex + delta + phaseList.size()) % phaseList.size();
	}

	public ResourceLocation addPhase() {
		ResourceLocation newPhaseId = createUniquePhaseId();
		definition.phases.put(newPhaseId, new PhaseDefinition(newPhaseId, List.of(), List.of(), List.of(), List.of(), List.of()));
		phaseList.add(newPhaseId);
		selectedPhaseIndex = phaseList.size() - 1;
		return newPhaseId;
	}

	public boolean canDeleteSelectedPhase() {
		ResourceLocation phaseId = getSelectedPhaseId();
		return phaseId != null && phaseList.size() > 1 && !phaseId.equals(definition.entryPhase);
	}

	/**
	 * Delete the currently selected phase.
	 * @return the removed phase ID, or null if deletion was not possible.
	 */
	public ResourceLocation deleteSelectedPhase() {
		ResourceLocation removedPhaseId = getSelectedPhaseId();
		if (removedPhaseId == null || !canDeleteSelectedPhase()) {
			return null;
		}
		int removedTransitions = removeTransitionsTargeting(removedPhaseId);
		definition.phases.remove(removedPhaseId);
		phaseList.remove(selectedPhaseIndex);
		clearPhaseCustomName(removedPhaseId);
		selectedPhaseIndex = Math.max(0, Math.min(selectedPhaseIndex, phaseList.size() - 1));

		var mc = Minecraft.getInstance();
		if (mc.player != null) {
			String msg = "[YH] Deleted phase " + formatPhaseId(removedPhaseId) +
					(removedTransitions > 0 ? " and removed " + removedTransitions + " transitions" : "");
			mc.player.displayClientMessage(Component.literal(msg), true);
		}
		return removedPhaseId;
	}

	// --- Phase naming ---

	public void renameSelectedPhase(String name) {
		if (phaseList.isEmpty()) return;
		ResourceLocation phaseId = phaseList.get(selectedPhaseIndex);
		String trimmed = name.trim();
		if (trimmed.isEmpty() || trimmed.equals(formatPhaseId(phaseId)) || trimmed.equals(phaseId.getPath())) {
			clearPhaseCustomName(phaseId);
		} else {
			setPhaseCustomName(phaseId, trimmed);
		}
	}

	public String getSelectedPhaseDisplayName() {
		if (phaseList.isEmpty()) return "";
		ResourceLocation phaseId = phaseList.get(selectedPhaseIndex);
		String custom = getStoredPhaseCustomName(phaseId);
		return custom != null ? custom : formatPhaseId(phaseId);
	}

	public String getPhaseOptionLabel(ResourceLocation phaseId) {
		String custom = getStoredPhaseCustomName(phaseId);
		if (custom == null || custom.isBlank() || custom.equals(phaseId.getPath())) {
			return formatPhaseId(phaseId);
		}
		return custom + " (" + formatPhaseId(phaseId) + ")";
	}

	// --- Custom name storage (delegates to definition.customNames) ---

	public String getStoredPhaseCustomName(ResourceLocation phaseId) {
		String value = definition.customNames.get(getPhaseNameKey(phaseId));
		if (value != null && !value.isBlank()) {
			return value;
		}
		value = definition.customNames.get(getLegacyPhaseNameKey(phaseId));
		return value != null && !value.isBlank() ? value : null;
	}

	public void clearPhaseCustomName(ResourceLocation phaseId) {
		definition.customNames.remove(getPhaseNameKey(phaseId));
		definition.customNames.remove(getLegacyPhaseNameKey(phaseId));
	}

	public void setPhaseCustomName(ResourceLocation phaseId, String value) {
		String legacyKey = getLegacyPhaseNameKey(phaseId);
		definition.customNames.remove(legacyKey);
		definition.customNames.put(getPhaseNameKey(phaseId), value);
	}

	// --- Internal helpers ---

	private ResourceLocation createUniquePhaseId() {
		int index = Math.max(phaseList.size() + 1, 1);
		ResourceLocation id;
		do {
			id = new ResourceLocation(definition.id.getNamespace(), "phase_" + index++);
		} while (definition.phases.containsKey(id));
		return id;
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

	static String getPhaseNameKey(ResourceLocation phaseId) {
		return "phase:" + formatPhaseId(phaseId);
	}

	static String getLegacyPhaseNameKey(ResourceLocation phaseId) {
		return "phase:" + SpellEditorController.formatResourceId(phaseId);
	}

	static String formatPhaseId(ResourceLocation phaseId) {
		return phaseId.toString();
	}
}
