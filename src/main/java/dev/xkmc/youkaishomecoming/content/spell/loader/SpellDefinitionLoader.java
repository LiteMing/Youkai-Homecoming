package dev.xkmc.youkaishomecoming.content.spell.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.LinkedHashMap;
import java.util.Map;

public class SpellDefinitionLoader extends SimpleJsonResourceReloadListener {

	public static final String DIRECTORY = "spell_definitions";

	private static final Gson GSON = new GsonBuilder().create();

	public SpellDefinitionLoader() {
		super(GSON, DIRECTORY);
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager, ProfilerFiller profiler) {
		Map<ResourceLocation, SpellDefinition> loaded = new LinkedHashMap<>();
		for (var entry : entries.entrySet()) {
			ResourceLocation resourceId = entry.getKey();
			var result = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
					.resultOrPartial(error -> YoukaisHomecoming.LOGGER.error(
							"Failed to parse spell definition {} from datapack: {}", resourceId, error));
			if (result.isEmpty()) {
				continue;
			}
			SpellDefinition definition = result.get();
			if (!resourceId.equals(definition.id)) {
				YoukaisHomecoming.LOGGER.warn(
						"Datapack spell file {} declares id {}; using declared id for registration",
						resourceId, definition.id);
			}
			SpellDefinition previous = loaded.put(definition.id, definition);
			if (previous != null) {
				YoukaisHomecoming.LOGGER.warn(
						"Duplicate datapack spell definition id {} replaced an earlier file during reload",
						definition.id);
			}
		}
		SpellRegistry.replaceDatapackDefinitions(loaded);
		rebindActiveSpellRuntimes();
		YoukaisHomecoming.LOGGER.info(
				"Loaded {} datapack spell definitions (builtin={}, total={})",
				loaded.size(), SpellRegistry.getBuiltin().size(), SpellRegistry.size());
	}

	private static void rebindActiveSpellRuntimes() {
		var server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) {
			return;
		}
		for (var level : server.getAllLevels()) {
			for (var entity : level.getAllEntities()) {
				if (entity instanceof YoukaiEntity youkai && youkai.spellRuntime != null) {
					SpellDefinition current = youkai.spellRuntime.getDefinition();
					SpellDefinition updated = SpellRegistry.get(current.id);
					if (updated != null && updated != current) {
						youkai.setSpellRuntime(youkai.spellRuntime.copyStateTo(updated));
					}
				}
			}
		}
	}
}
