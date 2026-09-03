package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.action.BurstAction;
import dev.xkmc.youkaishomecoming.content.spell.action.DelayAction;
import dev.xkmc.youkaishomecoming.content.spell.action.FireDanmakuAction;
import dev.xkmc.youkaishomecoming.content.spell.action.FireLaserAction;
import dev.xkmc.youkaishomecoming.content.spell.action.RunCommandAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpawnShooterAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpecialNodeCounter;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellDraftBudget;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHealthPlan;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.market.SpellMarketValidator;
import dev.xkmc.youkaishomecoming.content.spell.runtime.CustomSpellStorage;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntimeAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SerialClass
public class SpellEditorSyncToServer extends SerialPacketBase {

	private static final Gson GSON = new Gson();

	public enum Action {
		SAVE,
		SAVE_AND_REAPPLY,
		IMPORT_MARKET,
		DELETE
	}

	@SerialClass.SerialField
	public Action action = Action.SAVE;
	@SerialClass.SerialField
	public String spellId = "";
	@SerialClass.SerialField
	public String definitionJson = "";
	// Chunked transfer fields: totalChunks == 0 means the definition is inline.
	// Large definitions (e.g. a custom card assembled from many copied actions)
	// would otherwise exceed FriendlyByteBuf.writeUtf's 32767 limit and kill the
	// connection — chunks stay well under it, mirroring SpellPreviewChunkToClient.
	@SerialClass.SerialField
	public int transferId;
	@SerialClass.SerialField
	public int totalChunks;
	@SerialClass.SerialField
	public int chunkIndex;
	@SerialClass.SerialField
	public String chunk = "";

	@Deprecated
	public SpellEditorSyncToServer() {
	}

	private SpellEditorSyncToServer(Action action, String spellId, String definitionJson) {
		this.action = action;
		this.spellId = spellId;
		this.definitionJson = definitionJson;
	}

	/** Build a single transfer chunk of an editor packet. */
	public static SpellEditorSyncToServer chunk(SpellEditorSyncToServer base, int transferId,
												int chunkIndex, int totalChunks, String chunk) {
		SpellEditorSyncToServer packet = new SpellEditorSyncToServer(base.action, base.spellId, "");
		packet.transferId = transferId;
		packet.chunkIndex = chunkIndex;
		packet.totalChunks = totalChunks;
		packet.chunk = chunk;
		return packet;
	}

	public static SpellEditorSyncToServer save(SpellDefinition definition, boolean reapply) {
		if (definition.hasLegacyTicker()) {
			throw new IllegalArgumentException("Cannot save legacy_ticker spell via JSON: " + definition.id);
		}
		var json = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
				.getOrThrow(false, s -> {});
		return new SpellEditorSyncToServer(reapply ? Action.SAVE_AND_REAPPLY : Action.SAVE,
				definition.id.toString(), GSON.toJson(json));
	}

	public static SpellEditorSyncToServer importMarket(SpellDefinition definition) {
		if (definition.hasLegacyTicker()) {
			throw new IllegalArgumentException("Cannot import legacy_ticker spell via JSON: " + definition.id);
		}
		var json = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
				.getOrThrow(false, s -> {});
		return new SpellEditorSyncToServer(Action.IMPORT_MARKET, definition.id.toString(), GSON.toJson(json));
	}

	/**
	 * Import the exact JSON downloaded from the market.  Keeping the raw payload
	 * avoids a client-side codec round trip before the server archives it and lets
	 * the Raw JSON editor remain the recovery path when a client cannot decode it.
	 */
	public static SpellEditorSyncToServer importMarketRaw(ResourceLocation spellId, String rawJson) {
		if (spellId == null || rawJson == null || rawJson.isBlank()) {
			throw new IllegalArgumentException("Market spell JSON is missing");
		}
		return new SpellEditorSyncToServer(Action.IMPORT_MARKET, spellId.toString(), rawJson);
	}

	public static SpellEditorSyncToServer delete(ResourceLocation spellId) {
		return new SpellEditorSyncToServer(Action.DELETE, spellId.toString(), "");
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		ServerPlayer sender = context.getSender();
		if (sender == null) return;
		try {
			if (totalChunks > 0) {
				handleChunk(sender);
				return;
			}
			execute(sender);
		} catch (Exception e) {
			String msg = e.getMessage();
			sender.sendSystemMessage(Component.literal("[YH] Spell editor sync failed: " +
					(msg == null ? e.getClass().getSimpleName() : msg)));
		}
	}

	// ------------------------------------------------------------ chunking

	private static final Map<String, Assembly> ASSEMBLIES = new ConcurrentHashMap<>();

	private static final class Assembly {
		final Action action;
		final String spellId;
		final int totalChunks;
		final String[] parts;
		int received;

		Assembly(Action action, String spellId, int total) {
			this.action = action;
			this.spellId = spellId;
			this.totalChunks = total;
			this.parts = new String[total];
		}
	}

	private void handleChunk(ServerPlayer sender) {
		if (chunkIndex < 0 || chunkIndex >= totalChunks) {
			return;
		}
		String key = sender.getStringUUID() + "#" + transferId;
		Assembly assembly = ASSEMBLIES.computeIfAbsent(key,
				k -> new Assembly(action, spellId, totalChunks));
		assembly.parts[chunkIndex] = chunk;
		assembly.received++;
		if (assembly.received < assembly.totalChunks) {
			return;
		}
		ASSEMBLIES.remove(key);
		StringBuilder sb = new StringBuilder(assembly.totalChunks * SpellPreviewChunkToClient.MAX_CHUNK_CHARS);
		for (String part : assembly.parts) {
			if (part == null) {
				return; // incomplete (should not happen: reliable channel, all chunks arrive)
			}
			sb.append(part);
		}
		definitionJson = sb.toString();
		action = assembly.action;
		spellId = assembly.spellId;
		execute(sender);
	}

	private void execute(ServerPlayer sender) {
		try {
			if (action != Action.IMPORT_MARKET && !sender.hasPermissions(2)) {
				// Non-OP players may collaboratively save custom spells or delete a
				// spell they originally created. Anything else stays operator-only.
				if (action == Action.SAVE) {
					saveSelfMadeSpell(sender);
					return;
				}
				if (action == Action.DELETE) {
					deleteOwnSpell(sender);
					return;
				}
				sender.sendSystemMessage(Component.literal("[YH] No permission to edit spells on this server."));
				return;
			}
			if (action == Action.IMPORT_MARKET) {
				importMarketSpell(sender);
				return;
			}
			if (action == Action.DELETE) {
				deleteSpell(sender);
			} else {
				saveSpell(sender, action == Action.SAVE_AND_REAPPLY);
			}
		} catch (Exception e) {
			String msg = e.getMessage();
			sender.sendSystemMessage(Component.literal("[YH] Spell editor sync failed: " +
					(msg == null ? e.getClass().getSimpleName() : msg)));
		}
	}

	/**
	 * Non-operator save path: the spell must be a custom definition —
	 * never a built-in or market-managed id — and must not contain run_command.
	 * Existing custom definitions are collaborative and may be handed to another
	 * player for continued editing. A brand-new id still records its creator only
	 * to protect deletion; the metadata does not restrict later saves.
	 */
	private void saveSelfMadeSpell(ServerPlayer sender) {
		SpellDefinition definition = parseDefinition();
		definition = applyHeldDraftTraits(sender, definition);
		ResourceLocation id = definition.id;
		if (id == null) {
			throw new IllegalArgumentException("Spell id is missing");
		}
		SpellRegistry.Origin origin = SpellRegistry.getOrigin(id);
		if (origin != null && origin != SpellRegistry.Origin.CUSTOM) {
			throw new IllegalArgumentException("Cannot save this spell without operator permission: " + id);
		}
		if (containsPrivilegedAction(definition)) {
			throw new IllegalArgumentException("Spell contains an operator-only action");
		}
		enforceDraftCapabilities(sender, definition, true);
		SpellRegistry.register(definition);
		CustomSpellStorage.saveSpell(sender.server, definition);
		// Bind the held blank card on any save; bound cards are never rebound.
		bindBlankCardInHand(sender, id);
		if (origin == null) {
			// Brand-new spell: record a deletion owner without restricting edits.
			CustomSpellStorage.saveOwner(sender.server, id, sender.getUUID());
			sender.sendSystemMessage(Component.literal("[YH] Saved spell " + id + " and bound your spell card"));
		} else {
			sender.sendSystemMessage(Component.literal("[YH] Saved spell " + id));
		}
	}

	/**
	 * Bind a spell id onto the blank DynamicSpellItem the player is holding
	 * (main hand, then offhand, then first blank card in the inventory). Valid
	 * bindings are never replaced; missing legacy {@code minecraft:path}
	 * bindings may be upgraded to the submitted player-namespaced id.
	 */
	private static void bindBlankCardInHand(ServerPlayer sender, ResourceLocation id) {
		for (ItemStack stack : new ItemStack[]{sender.getMainHandItem(), sender.getOffhandItem()}) {
			if (tryBindBlankCard(stack, id)) {
				return;
			}
		}
		for (ItemStack stack : sender.getInventory().items) {
			if (tryBindBlankCard(stack, id)) {
				return;
			}
		}
	}

	private static boolean tryBindBlankCard(ItemStack stack, ResourceLocation id) {
		return stack.getItem() instanceof DynamicSpellItem
				&& DynamicSpellItem.bindCreatedSpellId(stack, id);
	}

	/**
	 * Non-operator delete: only the creator may delete their own spell card.
	 * Operators keep full control through the regular delete path.
	 */
	private void deleteOwnSpell(ServerPlayer sender) {
		ResourceLocation id = ResourceLocation.tryParse(spellId);
		if (id == null) {
			throw new IllegalArgumentException("Invalid spell id: " + spellId);
		}
		UUID owner = CustomSpellStorage.loadOwner(sender.server, id);
		if (owner == null || !owner.equals(sender.getUUID())) {
			throw new IllegalArgumentException("Only the creator can delete this spell card: " + id);
		}
		if (SpellRegistry.hasDefault(id)) {
			throw new IllegalArgumentException("Cannot delete built-in spell: " + id);
		}
		SpellRegistry.remove(id);
		CustomSpellStorage.deleteSpell(sender.server, id);
		sender.sendSystemMessage(Component.literal("[YH] Deleted spell " + id));
	}

	private void saveSpell(ServerPlayer sender, boolean reapply) {
		SpellDefinition definition = parseDefinition();
		definition = applyHeldDraftTraits(sender, definition);
		// Operators may author privileged boss logic. A held draft budget is still
		// shown by the editor, but does not restrict this server-authoring path.
		SpellRegistry.register(definition);
		CustomSpellStorage.saveSpell(sender.server, definition);
		// Bind the held blank card on ANY save: cards already bound are skipped,
		// so editing an existing spell never rebinds, while naming an id that the
		// server already knows (e.g. after a restart) still binds the card.
		bindBlankCardInHand(sender, definition.id);
		int count = reapply ? SpellRuntimeAccess.reapply(sender.server, definition.id, true) : 0;
		if (reapply) {
			sender.sendSystemMessage(Component.literal("[YH] Applied & saved spell to " + count + " entities"));
		}
	}

	/** Survival drafts may save freely over ordinary/performance budgets so the
	 * editor remains usable for iterative tuning. Capability grants are hard
	 * boundaries and are checked here as well as at certification. */
	private static void enforceDraftCapabilities(ServerPlayer sender, SpellDefinition definition,
			boolean requireDraft) {
		SpellDraftBudget budget = null;
		SpellDraftBudget blankBudget = null;
		for (ItemStack stack : sender.getInventory().items) {
			if (stack.getItem() instanceof DynamicSpellItem && !DynamicSpellItem.isComplete(stack)
					&& !dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator.isCertified(stack)) {
				ResourceLocation bound = DynamicSpellItem.getSpellId(stack);
				if (bound != null && definition.id.equals(bound)) {
					budget = DynamicSpellItem.getDraftBudget(stack);
					break;
				} else if (bound == null && blankBudget == null) {
					blankBudget = DynamicSpellItem.getDraftBudget(stack);
				}
			}
		}
		if (budget == null) budget = blankBudget;
		if (budget == null) {
			if (requireDraft) throw new IllegalArgumentException("No unfinished spell-card base is available");
			return;
		}
		SpecialNodeCounter.Summary nodes = SpecialNodeCounter.summarize(definition);
		if (nodes.operatorOnlyNodes() > 0 || nodes.deniedNodes() > 0) {
			throw new IllegalArgumentException("Spell contains operator-only or denied nodes");
		}
		if (!budget.permitsExperimental(nodes)) {
			throw new IllegalArgumentException("Experimental capability grants exceed the draft budget");
		}
	}

	private static SpellDefinition applyHeldDraftTraits(ServerPlayer sender, SpellDefinition definition) {
		ItemStack blank = ItemStack.EMPTY;
		for (int slot = 0; slot < sender.getInventory().getContainerSize(); slot++) {
			ItemStack stack = sender.getInventory().getItem(slot);
			if (!(stack.getItem() instanceof DynamicSpellItem) || DynamicSpellItem.isComplete(stack)
					|| dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator.isCertified(stack)) continue;
			ResourceLocation bound = DynamicSpellItem.getSpellId(stack);
			if (bound != null && bound.equals(definition.id)) {
				return DynamicSpellItem.applyDraftTraits(stack, definition);
			}
			if (bound == null && blank.isEmpty()) blank = stack;
		}
		return blank.isEmpty() ? definition : DynamicSpellItem.applyDraftTraits(blank, definition);
	}

	private void importMarketSpell(ServerPlayer sender) {
		SpellDefinition definition = parseDefinition();
		validateMarketImport(definition);
		SpellRegistry.register(definition);
		if (!CustomSpellStorage.saveSpell(sender.server, definition, definitionJson)) {
			throw new IllegalStateException("Failed to save downloaded spell to world storage: " + definition.id);
		}
		sender.sendSystemMessage(Component.literal("[YH] Imported market spell " + definition.id));
	}

	private void validateMarketImport(SpellDefinition definition) {
		if (SpellRegistry.hasDefault(definition.id)) {
			throw new IllegalArgumentException("Cannot import over built-in spell: " + definition.id);
		}
		if (SpellRegistry.contains(definition.id)) {
			throw new IllegalArgumentException("Cannot overwrite existing spell without operator permission: " + definition.id);
		}
		if (containsPrivilegedAction(definition)) {
			throw new IllegalArgumentException("Market spell contains run_command and requires operator permission");
		}
		var json = JsonParser.parseString(definitionJson);
		SpellMarketValidator.validateManualImport(definitionJson, json, definition);
	}

	private void deleteSpell(ServerPlayer sender) {
		ResourceLocation id = ResourceLocation.tryParse(spellId);
		if (id == null) {
			throw new IllegalArgumentException("Invalid spell id: " + spellId);
		}
		if (SpellRegistry.hasDefault(id)) {
			throw new IllegalArgumentException("Cannot delete built-in spell: " + id);
		}
		if (SpellRegistry.getOrigin(id) == SpellRegistry.Origin.MARKET) {
			throw new IllegalArgumentException("Cannot delete a market-managed spell from the custom editor: " + id);
		}
		SpellRegistry.remove(id);
		CustomSpellStorage.deleteSpell(sender.server, id);
		sender.sendSystemMessage(Component.literal("[YH] Deleted spell " + id));
	}

	private SpellDefinition parseDefinition() {
		var json = JsonParser.parseString(definitionJson);
		var result = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, json);
		if (result.error().isPresent()) {
			throw new IllegalArgumentException(result.error().get().message());
		}
		SpellDefinition definition = result.result().orElseThrow(() ->
				new IllegalArgumentException("Spell parse returned no result"));
		if (!spellId.isBlank()) {
			ResourceLocation expected = ResourceLocation.tryParse(spellId);
			if (expected == null) {
				throw new IllegalArgumentException("Invalid spell id: " + spellId);
			}
			if (!expected.equals(definition.id)) {
				throw new IllegalArgumentException("Spell id mismatch: " + expected + " != " + definition.id);
			}
		}
		return definition;
	}

	private boolean containsPrivilegedAction(SpellDefinition definition) {
		for (var phase : definition.phases.values()) {
			if (containsPrivilegedAction(phase.onEnter) ||
					containsPrivilegedAction(phase.onTick) ||
					containsPrivilegedAction(phase.onExit) ||
					containsPrivilegedAction(phase.onDamage)) {
				return true;
			}
		}
		return false;
	}

	private boolean containsPrivilegedAction(List<SpellAction> actions) {
		for (SpellAction action : actions) {
			if (action instanceof RunCommandAction) {
				return true;
			}
			if (action instanceof SpellActions.ConditionalAction cond &&
					(containsPrivilegedAction(cond.ifTrue()) || containsPrivilegedAction(cond.ifFalse()))) {
				return true;
			}
			if (action instanceof SpellActions.SequenceAction seq && containsPrivilegedAction(seq.actions())) {
				return true;
			}
			if (action instanceof SpellActions.RepeatAction repeat && containsPrivilegedAction(repeat.body())) {
				return true;
			}
			if (action instanceof SpellActions.DisabledAction disabled && containsPrivilegedAction(List.of(disabled.inner()))) {
				return true;
			}
			if (action instanceof DelayAction delay && containsPrivilegedAction(delay.body())) {
				return true;
			}
			if (action instanceof BurstAction burst && containsPrivilegedAction(burst.body())) {
				return true;
			}
			if (action instanceof SpawnShooterAction shooter && containsPrivilegedAction(shooter.body())) {
				return true;
			}
			if (action instanceof FireDanmakuAction danmaku &&
					(containsPrivilegedAction(danmaku.onExpiry().orElse(List.of())) ||
							containsPrivilegedAction(danmaku.onTrail().orElse(List.of())) ||
							containsPrivilegedAction(danmaku.onHitEntity().orElse(List.of())) ||
							containsPrivilegedAction(danmaku.onHitBlock().orElse(List.of())))) {
				return true;
			}
			if (action instanceof FireLaserAction laser &&
					(containsPrivilegedAction(laser.onExpiry().orElse(List.of())) ||
							containsPrivilegedAction(laser.onTrail().orElse(List.of())) ||
							containsPrivilegedAction(laser.onHitEntity().orElse(List.of())) ||
							containsPrivilegedAction(laser.onHitBlock().orElse(List.of())))) {
				return true;
			}
		}
		return false;
	}

}
