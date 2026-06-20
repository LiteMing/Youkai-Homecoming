package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
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

@SerialClass
public class SpellEditorSyncToServer extends SerialPacketBase {

	private static final Gson GSON = new Gson();

	public enum Action {
		SAVE,
		SAVE_AND_REAPPLY,
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

	public static SpellEditorSyncToServer delete(ResourceLocation spellId) {
		return new SpellEditorSyncToServer(Action.DELETE, spellId.toString(), "");
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		ServerPlayer sender = context.getSender();
		if (sender == null) return;
		if (!sender.hasPermissions(2)) {
			sender.sendSystemMessage(Component.literal("[YH] No permission to edit spells on this server."));
			return;
		}
		try {
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

	private void saveSpell(ServerPlayer sender, boolean reapply) {
		SpellDefinition definition = parseDefinition();
		SpellRegistry.register(definition);
		CustomSpellStorage.saveSpell(sender.server, definition);
		int count = reapply ? SpellRuntimeAccess.reapply(sender.server, definition.id, true) : 0;
		if (reapply) {
			sender.sendSystemMessage(Component.literal("[YH] Applied & saved spell to " + count + " entities"));
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

}
