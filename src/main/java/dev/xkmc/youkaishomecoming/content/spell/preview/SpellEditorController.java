package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDisplay;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellItemForm;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyProfile;
import dev.xkmc.youkaishomecoming.content.spell.runtime.CustomSpellStorage;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manages spell-level operations: create, delete, switch, draft mode, export, reset.
 * Extracted from SpellPreviewScreen to reduce its size.
 */
@OnlyIn(Dist.CLIENT)
public class SpellEditorController {

	static final ResourceLocation DRAFT_SPELL_ID = new ResourceLocation("minecraft", "__yh_editor__");
	static final ResourceLocation DRAFT_ENTRY_PHASE = new ResourceLocation("minecraft", "__yh_editor__/main");

	private final VirtualSpellScene scene;
	private final Runnable rebuildCallback;

	/** Snapshots of visited spell definitions when this editor first saw them. */
	private final java.util.Map<ResourceLocation, com.google.gson.JsonElement> openSnapshots = new java.util.HashMap<>();

	private SpellDefinition definition;
	private boolean draftMode;
	private boolean skipSaveOnNextDefinitionSwitch;

	public SpellEditorController(SpellDefinition definition, boolean draftMode,
								 VirtualSpellScene scene, Runnable rebuildCallback) {
		this.definition = definition;
		this.draftMode = draftMode;
		this.scene = scene;
		this.rebuildCallback = rebuildCallback;
		if (!draftMode) {
			rememberOpenSnapshot(definition);
		}
	}

	// --- Accessors ---

	public SpellDefinition getDefinition() {
		return definition;
	}

	public void setDefinition(SpellDefinition definition) {
		this.definition = definition;
	}

	public boolean isDraftMode() {
		return draftMode || isDraftDefinition(definition);
	}

	public void setDraftMode(boolean draftMode) {
		this.draftMode = draftMode;
	}

	public boolean isSkipSaveOnNextDefinitionSwitch() {
		return skipSaveOnNextDefinitionSwitch;
	}

	public void setSkipSaveOnNextDefinitionSwitch(boolean skip) {
		this.skipSaveOnNextDefinitionSwitch = skip;
	}

	public void clearSkipFlag() {
		this.skipSaveOnNextDefinitionSwitch = false;
	}

	public java.util.Map<ResourceLocation, com.google.gson.JsonElement> getOpenSnapshots() {
		return openSnapshots;
	}

	// --- Spell switching ---

	public List<ResourceLocation> getSpellOptions() {
		var spells = new ArrayList<>(SpellRegistry.getAll().keySet());
		spells.sort(java.util.Comparator.comparing(SpellEditorController::formatResourceId));
		return spells;
	}

	public String getSpellOptionLabel(ResourceLocation spellId) {
		return formatResourceId(spellId);
	}

	public String getCurrentSpellButtonLabel() {
		return isDraftMode() ? "New Spell" : getSpellOptionLabel(definition.id);
	}

	public ResourceLocation getCurrentSpellSelectionId() {
		return isDraftMode() ? null : definition.id;
	}

	public void switchSelectedSpell(ResourceLocation spellId) {
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

	public boolean canDeleteSelectedSpell() {
		return !isDraftMode() && !SpellRegistry.hasDefault(definition.id);
	}

	public void enterDraftSpellEditor() {
		saveCurrentDefinition();
		skipSaveOnNextDefinitionSwitch = true;
		draftMode = true;
		scene.pause();
		scene.switchSpellDefinition(createDraftDefinition(), true);
	}

	public void nameCurrentDraftSpell(String name) {
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

	public void deleteSelectedSpell() {
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

	// --- Save / Reset / Export ---

	public void saveCurrentDefinition() {
		if (isDraftMode()) {
			return;
		}
		SpellRegistry.register(definition);
		var server = Minecraft.getInstance().getSingleplayerServer();
		if (server != null) {
			server.execute(() -> CustomSpellStorage.saveSpell(server, definition));
		}
	}

	public void resetToDefault() {
		if (isDraftMode()) {
			return;
		}
		SpellDefinition restored = SpellRegistry.getDefault(definition.id);
		var openSnapshot = openSnapshots.get(definition.id);
		if (restored == null && openSnapshot != null) {
			restored = SpellDefinition.CODEC.parse(
					com.mojang.serialization.JsonOps.INSTANCE, openSnapshot).result().orElse(null);
		}
		if (restored == null) return;

		definition.phases.clear();
		definition.phases.putAll(restored.phases);
		definition.customNames.clear();
		definition.customNames.putAll(restored.customNames);

		displayEditorMessage("[YH] Spell reset to default");
	}

	public void exportToDatapack() {
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

	// --- Snapshot management ---

	public void rememberOpenSnapshot(SpellDefinition definition) {
		if (isDraftDefinition(definition)) {
			return;
		}
		openSnapshots.computeIfAbsent(definition.id, id -> SpellDefinition.CODEC.encodeStart(
				com.mojang.serialization.JsonOps.INSTANCE, definition).result().orElse(null));
	}

	// --- Static helpers ---

	public static boolean isDraftDefinition(SpellDefinition definition) {
		return definition != null && DRAFT_SPELL_ID.equals(definition.id);
	}

	public static SpellDefinition createDraftDefinition() {
		return new SpellDefinition(
				DRAFT_SPELL_ID,
				new SpellDisplay("new_spell", "", Optional.empty(), Optional.empty()),
				SpellItemForm.NONE,
				DRAFT_ENTRY_PHASE,
				Map.of(),
				DifficultyProfile.DEFAULT
		);
	}

	public static SpellDefinition createEmptySpellDefinition(ResourceLocation spellId) {
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

	static String formatResourceId(ResourceLocation id) {
		return "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
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
}
