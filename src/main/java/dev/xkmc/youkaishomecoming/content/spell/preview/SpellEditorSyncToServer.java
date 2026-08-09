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
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.market.SpellMarketValidator;
import dev.xkmc.youkaishomecoming.content.spell.runtime.CustomSpellStorage;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntimeAccess;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SerialClass
public class SpellEditorSyncToServer extends SerialPacketBase {

	private static final Gson GSON = new Gson();

	public enum Action {
		SAVE,
		SAVE_AND_REAPPLY,
		IMPORT_MARKET,
		EXPORT_GLOBAL,
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

	public static SpellEditorSyncToServer exportGlobal(SpellDefinition definition) {
		if (definition.hasLegacyTicker()) {
			throw new IllegalArgumentException("Cannot export legacy_ticker spell via JSON: " + definition.id);
		}
		var json = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
				.getOrThrow(false, s -> {});
		return new SpellEditorSyncToServer(Action.EXPORT_GLOBAL, definition.id.toString(), GSON.toJson(json));
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
				// Non-OP players may save a NEW self-made spell (crafted blank card,
				// editor save path). Anything else stays operator-only.
				if (action == Action.SAVE) {
					saveSelfMadeSpell(sender);
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
			} else if (action == Action.EXPORT_GLOBAL) {
				exportGlobalSpell(sender);
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
	 * Non-operator save path: the spell must be a custom (self-made) definition —
	 * never a built-in or market-managed id — and must not contain run_command.
	 * On success the bound spell card (reusable DynamicSpellItem) is handed to
	 * the player, closing the certification loop for non-OP players.
	 */
	private void saveSelfMadeSpell(ServerPlayer sender) {
		SpellDefinition definition = parseDefinition();
		ResourceLocation id = definition.id;
		if (id == null) {
			throw new IllegalArgumentException("Spell id is missing");
		}
		SpellRegistry.Origin origin = SpellRegistry.getOrigin(id);
		if (origin != null && origin != SpellRegistry.Origin.CUSTOM) {
			throw new IllegalArgumentException("Cannot save this spell without operator permission: " + id);
		}
		if (containsPrivilegedAction(definition)) {
			throw new IllegalArgumentException("run_command requires operator permission");
		}
		SpellRegistry.register(definition);
		CustomSpellStorage.saveSpell(sender.server, definition);
		if (origin == null) {
			// brand-new self-made spell: hand the player the bound (unfinished) card.
			// Editing an existing card never issues a second card.
			ItemStack card = DynamicSpellItem.createStack(YHDanmaku.DYNAMIC_SPELL.get(), id, false);
			if (!sender.getInventory().add(card)) {
				sender.drop(card, false);
			}
			sender.sendSystemMessage(Component.literal("[YH] Saved spell " + id + " and handed you the spell card"));
		} else {
			sender.sendSystemMessage(Component.literal("[YH] Saved spell " + id));
		}
	}

	private void exportGlobalSpell(ServerPlayer sender) {
		SpellDefinition definition = parseDefinition();
		SpellRegistry.register(definition);
		var file = CustomSpellStorage.saveGlobalSpell(definition);
		sender.sendSystemMessage(Component.literal("[YH] Exported global spell " + definition.id + " to " + file.getPath()));
	}

	private void saveSpell(ServerPlayer sender, boolean reapply) {
		SpellDefinition definition = parseDefinition();
		SpellRegistry.register(definition);
		CustomSpellStorage.saveSpell(sender.server, definition);
		int count = reapply ? SpellRuntimeAccess.reapply(sender.server, definition.id, true) : 0;
		if (reapply) {
			sender.sendSystemMessage(Component.literal("[YH] Applied & saved spell to " + count + " entities"));
		}
	}

	private void importMarketSpell(ServerPlayer sender) {
		SpellDefinition definition = parseDefinition();
		validateMarketImport(definition);
		SpellRegistry.register(definition);
		CustomSpellStorage.saveSpell(sender.server, definition);
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
		SpellMarketValidator.validate(definitionJson, json, definition);
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
