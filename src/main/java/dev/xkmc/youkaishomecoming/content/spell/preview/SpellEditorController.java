package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.spell.action.SetSpellHealthAction;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDisplay;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellItemForm;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyProfile;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

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
	private ResourceLocation baselineDefinitionId;

	public SpellEditorController(SpellDefinition definition, boolean draftMode,
								 VirtualSpellScene scene, Runnable rebuildCallback) {
		this.definition = definition;
		this.draftMode = draftMode;
		this.scene = scene;
		this.rebuildCallback = rebuildCallback;
		this.baselineDefinitionId = draftMode || definition == null ? null : definition.id;
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
		skipSaveOnNextDefinitionSwitch = true;
		baselineDefinitionId = target.id;
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
		skipSaveOnNextDefinitionSwitch = true;
		baselineDefinitionId = null;
		draftMode = true;
		scene.pause();
		scene.switchSpellDefinition(createDraftDefinition(), true);
	}

	@Nullable
	public Component nameCurrentDraftSpell(String name) {
		if (!isDraftMode()) {
			return Component.translatable("youkaishomecoming.spell_editor.create.error.not_draft");
		}
		if (name == null || name.trim().isEmpty()) {
			return Component.translatable("youkaishomecoming.spell_editor.create.error.missing_id");
		}
		ResourceLocation spellId = parseDraftSpellId(name);
		if (spellId == null) {
			return Component.translatable("youkaishomecoming.spell_editor.create.error.invalid_id");
		}
		if (SpellRegistry.contains(spellId)) {
			return Component.translatable("youkaishomecoming.spell_editor.create.error.exists", spellId);
		}
		SpellDefinition created = createEmptySpellDefinition(spellId);
		if (!SpellEditorNetworkClient.save(created)) {
			return Component.translatable("youkaishomecoming.spell_editor.error.encode_failed");
		}
		SpellRegistry.register(created);
		// Bind the blank card the player is holding the moment the id is created —
		// the card and the spell id become one from here on (server re-binds on
		// save as authority, so OP saves get the card too).
		bindHeldBlankCard(spellId);
		skipSaveOnNextDefinitionSwitch = true;
		scene.pause();
		scene.switchSpellDefinition(created, true);
		displayEditorMessage("[YH] Created spell " + formatResourceId(spellId));
		return null;
	}

	/**
	 * Apply the newly created spell id to the blank DynamicSpellItem the player is
	 * holding (main hand, then offhand, then first blank card in the inventory).
	 * Blank cards are bound normally; missing legacy {@code minecraft:path}
	 * bindings may be upgraded to the new player namespace.
	 */
	private void bindHeldBlankCard(ResourceLocation spellId) {
		var player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
			if (tryBind(stack, spellId)) {
				return;
			}
		}
		for (ItemStack stack : player.getInventory().items) {
			if (tryBind(stack, spellId)) {
				return;
			}
		}
	}

	private static boolean tryBind(ItemStack stack, ResourceLocation spellId) {
		return stack.getItem() instanceof dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem
				&& dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem
				.bindCreatedSpellId(stack, spellId);
	}

	public void deleteSelectedSpell() {
		ResourceLocation spellId = definition.id;
		if (!canDeleteSelectedSpell()) {
			return;
		}
		openSnapshots.remove(spellId);
		SpellRegistry.remove(spellId);
		SpellEditorNetworkClient.delete(spellId);
		skipSaveOnNextDefinitionSwitch = true;
		draftMode = true;
		scene.pause();
		scene.switchSpellDefinition(createDraftDefinition(), true);
		displayEditorMessage("[YH] Deleted spell " + formatResourceId(spellId));
	}

// --- Save / Reset ---

	/**
	 * Restore the definition that was visible when this editor session last saved
	 * it. This is deliberately client-side: abandoning edits must never send a
	 * compensating write to the server.
	 */
	public void discardCurrentDefinitionChanges() {
		if (isDraftMode()) {
			definition = createDraftDefinition();
			return;
		}
		SpellDefinition restored = null;
		ResourceLocation baselineId = baselineDefinitionId == null ? definition.id : baselineDefinitionId;
		var snapshot = openSnapshots.get(baselineId);
		if (snapshot != null) {
			restored = SpellDefinition.CODEC.parse(
					com.mojang.serialization.JsonOps.INSTANCE, snapshot).result().orElse(null);
		}
		if (restored == null) {
			restored = SpellRegistry.getDefault(baselineId);
		}
		if (restored != null) {
			definition = restored;
			SpellRegistry.register(restored);
			baselineDefinitionId = restored.id;
		}
	}

	/** Refresh the client-side saved baseline after an explicit server save. */
	public void markDefinitionSaved() {
		if (isDraftMode() || definition == null || definition.hasLegacyTicker()) {
			return;
		}
		SpellDefinition.CODEC.encodeStart(
				com.mojang.serialization.JsonOps.INSTANCE, definition)
				.result().ifPresent(json -> {
					openSnapshots.put(definition.id, json);
					baselineDefinitionId = definition.id;
				});
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

		// For legacy defaults, switch to the live instance (factory not copyable via phases map)
		if (restored.hasLegacyTicker()) {
			skipSaveOnNextDefinitionSwitch = true;
			scene.pause();
			scene.switchSpellDefinition(restored, true);
			displayEditorMessage("[YH] Spell reset to default");
			return;
		}

		definition.phases.clear();
		definition.phases.putAll(restored.phases);
		definition.customNames.clear();
		definition.customNames.putAll(restored.customNames);

		displayEditorMessage("[YH] Spell reset to default");
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
				List.of(new SetSpellHealthAction(SetSpellHealthAction.Mode.SET,
						NumberProvider.constant(50), NumberProvider.constant(100))),
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
		ResourceLocation id = ResourceLocation.tryParse(trimmed.contains(":")
				? trimmed : getDefaultSpellNamespace() + ":" + trimmed);
		if (id == null || DRAFT_SPELL_ID.equals(id)) {
			return null;
		}
		return id;
	}

	public String getDefaultSpellNamespace() {
		var player = Minecraft.getInstance().player;
		if (player == null) return "player";
		return dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem
				.playerSpellNamespace(player);
	}

	private void displayEditorMessage(String message) {
		displayEditorMessage(Component.literal(message));
	}

	private void displayEditorMessage(Component message) {
		var mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(message, true);
		}
	}
}
