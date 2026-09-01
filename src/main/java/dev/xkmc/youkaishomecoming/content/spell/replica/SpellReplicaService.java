package dev.xkmc.youkaishomecoming.content.spell.replica;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.SpellReplicaFilmItem;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHash;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellCardType;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.CustomSpellStorage;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small server-side state machine shared by Exposure and TLM camera integrations. */
public final class SpellReplicaService {
	private static final String PROGRESS = "yh_replica_progress";
	private static final String CAPTURED = "yh_replica_captured";
	private static final String SOURCE = "yh_replica_source";
	private static final String SOURCE_HASH = "yh_replica_source_hash";

	private SpellReplicaService() {}

	public static int progress(ItemStack stack) {
		return Math.max(0, Math.min(100, stack.hasTag() ? stack.getTag().getInt(PROGRESS) : 0));
	}

	public static boolean isComplete(ItemStack stack) {
		return progress(stack) >= 100 && source(stack) != null;
	}

	public static int progressPercent(int captured, int requiredCaptures) {
		int need = Math.max(1, requiredCaptures);
		return (int) Math.min(100, Math.max(0, captured) * 100L / need);
	}

	public static ResourceLocation source(ItemStack stack) {
		if (!stack.hasTag()) return null;
		return ResourceLocation.tryParse(stack.getTag().getString(SOURCE));
	}

	public static String sourceHash(ItemStack stack) {
		return stack.hasTag() ? stack.getTag().getString(SOURCE_HASH) : "";
	}

	public static void clearProgress(ItemStack stack) {
		if (!stack.hasTag()) return;
		stack.getTag().remove(PROGRESS);
		stack.getTag().remove(CAPTURED);
		stack.getTag().remove(SOURCE);
		stack.getTag().remove(SOURCE_HASH);
	}

	/**
	 * ItemStack NBT is mutable, so changing a tag does not notify the player's
	 * inventory or the active menu by itself. Call this after a replica state
	 * transition to make the change both saveable and visible to the client.
	 */
	public static void markInventoryChanged(ServerPlayer player) {
		if (player == null) return;
		player.getInventory().setChanged();
		if (player.containerMenu != null) player.containerMenu.broadcastChanges();
	}

	/** Records one photograph's contribution, capped at 100 and one source per film. */
	public static void record(ItemStack stack, ResourceLocation spellId, String definitionHash, int captured,
			int requiredCaptures) {
		if (!(stack.getItem() instanceof SpellReplicaFilmItem) || spellId == null || captured <= 0) return;
		ResourceLocation current = source(stack);
		if (current != null && !current.equals(spellId)) return;
		int need = Math.max(1, requiredCaptures);
		if (current == null) {
			stack.getOrCreateTag().putString(SOURCE, spellId.toString());
			if (definitionHash != null && !definitionHash.isBlank()) {
				stack.getOrCreateTag().putString(SOURCE_HASH, definitionHash);
			}
		} else if (!sourceHash(stack).isBlank() && definitionHash != null
				&& !sourceHash(stack).equals(definitionHash)) {
			return;
		}
		int previous = stack.getOrCreateTag().contains(CAPTURED)
				? stack.getTag().getInt(CAPTURED)
				: progress(stack) * need / 100;
		int total = (int) Math.min(Integer.MAX_VALUE, previous + (long) captured);
		stack.getOrCreateTag().putInt(CAPTURED, total);
		stack.getOrCreateTag().putInt(PROGRESS, progressPercent(total, need));
	}

	/** Replace a complete film with a tier-1 editable dynamic draft. */
	public static boolean completeIntoDraft(ServerPlayer player, ItemStack film) {
		ResourceLocation source = source(film);
		SpellDefinition definition = source == null ? null : SpellRegistry.get(source);
		if (!isComplete(film) || definition == null || definition.itemForm.cardType() == SpellCardType.NON_SPELL) return false;
		try {
			String currentHash = SpellHash.canonicalHash(definition);
			if (!sourceHash(film).isBlank() && !sourceHash(film).equals(currentHash)) {
				player.displayClientMessage(Component.translatable("youkaishomecoming.replica.source_changed"), false);
				return false;
			}
			ResourceLocation copyId = nextReplicaId(player, source, currentHash);
			SpellDefinition copy = copyDefinition(definition, copyId);
			if (!CustomSpellStorage.saveSpell(player.server, copy)) {
				player.displayClientMessage(Component.translatable("youkaishomecoming.replica.save_failed"), false);
				return false;
			}
			CustomSpellStorage.saveOwner(player.server, copy.id, player.getUUID());
			SpellRegistry.register(copy);
			ItemStack draft = DynamicSpellItem.createStack(YHDanmaku.DYNAMIC_SPELL.get(), copy.id);
			DynamicSpellItem.setRank(draft,
					dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCardRank.LESSER_WISDOM);
			DynamicSpellItem.setDraftBudget(draft,
					dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCardRank.LESSER_WISDOM.createBudget());
			DynamicSpellItem.setCardType(draft, SpellCardType.NORMAL);
			DynamicSpellItem.setExSpell(draft, false);
			DynamicSpellItem.setComplete(draft, false);
			replaceFilm(player, film, draft);
			markInventoryChanged(player);
			player.displayClientMessage(Component.translatable("youkaishomecoming.replica.completed"), false);
			return true;
		} catch (RuntimeException exception) {
			player.displayClientMessage(Component.translatable("youkaishomecoming.replica.copy_failed",
					exception.getMessage() == null ? "unknown" : exception.getMessage()), false);
			return false;
		}
	}

	public static SpellDefinition copyDefinition(SpellDefinition source, ResourceLocation copyId) {
		JsonObject root = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, source).result()
				.filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)
				.orElseThrow(() -> new IllegalArgumentException("source definition is not encodable"));
		root = root.deepCopy();
		JsonObject phases = root.getAsJsonObject("phases");
		if (phases == null || phases.size() == 0) throw new IllegalArgumentException("source has no phases");
		Map<String, String> phaseIds = new LinkedHashMap<>();
		for (String oldId : phases.keySet()) {
			ResourceLocation parsed = ResourceLocation.tryParse(oldId);
			if (parsed == null) throw new IllegalArgumentException("invalid phase id " + oldId);
			String suffix = parsed.getPath().startsWith(source.id.getPath() + "/")
					? parsed.getPath().substring(source.id.getPath().length())
					: "/phase/" + parsed.getPath();
			phaseIds.put(oldId, new ResourceLocation(copyId.getNamespace(), copyId.getPath() + suffix).toString());
		}
		root.addProperty("id", copyId.toString());
		String entry = root.get("entry_phase").getAsString();
		root.addProperty("entry_phase", phaseIds.getOrDefault(entry, entry));
		JsonObject rewrittenPhases = new JsonObject();
		for (var phase : phases.entrySet()) {
			JsonObject value = phase.getValue().getAsJsonObject().deepCopy();
			String newPhaseId = phaseIds.get(phase.getKey());
			value.addProperty("id", newPhaseId);
			rewriteReferences(value, phaseIds, source.id.toString(), copyId.toString());
			rewrittenPhases.add(newPhaseId, value);
		}
		root.add("phases", rewrittenPhases);
		SpellDefinition copy = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, root).result()
				.orElseThrow(() -> new IllegalArgumentException("rewritten definition is not decodable"));
		// Replication preserves the authored pattern, not aura-granted card traits.
		// The player receives a normal Tier-1 draft and must earn/apply special
		// traits independently before certification.
		copy.itemForm = copy.itemForm.withCardType(SpellCardType.NORMAL).withExSpell(false);
		return copy;
	}

	private static void rewriteReferences(JsonElement element, Map<String, String> phaseIds,
			String oldSpellId, String newSpellId) {
		if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) rewriteReferences(child, phaseIds, oldSpellId, newSpellId);
			return;
		}
		if (!element.isJsonObject()) return;
		JsonObject object = element.getAsJsonObject();
		for (var entry : object.entrySet()) {
			JsonElement value = entry.getValue();
			if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
				String current = value.getAsString();
				if (("target_phase".equals(entry.getKey()) || "phase_id".equals(entry.getKey()))
						&& phaseIds.containsKey(current)) {
					object.addProperty(entry.getKey(), phaseIds.get(current));
				} else if ("spell_id".equals(entry.getKey()) && oldSpellId.equals(current)) {
					object.addProperty(entry.getKey(), newSpellId);
				}
			} else {
				rewriteReferences(value, phaseIds, oldSpellId, newSpellId);
			}
		}
	}

	private static ResourceLocation nextReplicaId(ServerPlayer player, ResourceLocation source, String hash) {
		String base = "replica_" + source.getPath().replace('/', '_') + "_" + hash.substring(0, 8);
		String namespace = DynamicSpellItem.playerSpellNamespace(player);
		ResourceLocation candidate = new ResourceLocation(namespace, base);
		int suffix = 2;
		while (SpellRegistry.contains(candidate)) candidate = new ResourceLocation(namespace, base + "_" + suffix++);
		return candidate;
	}

	private static void replaceFilm(ServerPlayer player, ItemStack film, ItemStack draft) {
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i) == film) {
				inventory.setItem(i, draft);
				return;
			}
		}
		film.shrink(1);
		inventory.placeItemBackInInventory(draft);
	}
}
