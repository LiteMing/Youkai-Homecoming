package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.action.BurstAction;
import dev.xkmc.youkaishomecoming.content.spell.action.DelayAction;
import dev.xkmc.youkaishomecoming.content.spell.action.FireDanmakuAction;
import dev.xkmc.youkaishomecoming.content.spell.action.RunCommandAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpawnShooterAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.CustomSpellStorage;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntimeAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;

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

	@Deprecated
	public SpellEditorSyncToServer() {
	}

	private SpellEditorSyncToServer(Action action, String spellId, String definitionJson) {
		this.action = action;
		this.spellId = spellId;
		this.definitionJson = definitionJson;
	}

	public static SpellEditorSyncToServer save(SpellDefinition definition, boolean reapply) {
		var json = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
				.getOrThrow(false, s -> {});
		return new SpellEditorSyncToServer(reapply ? Action.SAVE_AND_REAPPLY : Action.SAVE,
				definition.id.toString(), GSON.toJson(json));
	}

	public static SpellEditorSyncToServer importMarket(SpellDefinition definition) {
		var json = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
				.getOrThrow(false, s -> {});
		return new SpellEditorSyncToServer(Action.IMPORT_MARKET, definition.id.toString(), GSON.toJson(json));
	}

	public static SpellEditorSyncToServer exportGlobal(SpellDefinition definition) {
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
			if (!sender.hasPermissions(2)) {
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
	}

	private void deleteSpell(ServerPlayer sender) {
		ResourceLocation id = ResourceLocation.tryParse(spellId);
		if (id == null) {
			throw new IllegalArgumentException("Invalid spell id: " + spellId);
		}
		if (SpellRegistry.hasDefault(id)) {
			throw new IllegalArgumentException("Cannot delete built-in spell: " + id);
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
		}
		return false;
	}

}
