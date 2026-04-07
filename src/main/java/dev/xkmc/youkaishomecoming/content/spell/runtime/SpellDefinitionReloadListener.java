package dev.xkmc.youkaishomecoming.content.spell.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads spell definitions from datapacks under {@code data/<namespace>/spell_definitions/*.json}.
 */
@Mod.EventBusSubscriber(modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SpellDefinitionReloadListener extends SimpleJsonResourceReloadListener {

	private static final Logger LOGGER = LoggerFactory.getLogger("YoukaiHomecoming/SpellDatapackLoader");
	private static final Gson GSON = new GsonBuilder().create();
	public static final String DIRECTORY = "spell_definitions";

	public SpellDefinitionReloadListener() {
		super(GSON, DIRECTORY);
	}

	@SubscribeEvent
	public static void addReloadListener(AddReloadListenerEvent event) {
		event.addListener(new SpellDefinitionReloadListener());
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
		Map<ResourceLocation, SpellDefinition> loaded = new LinkedHashMap<>();
		for (var entry : resources.entrySet()) {
			ResourceLocation fileId = entry.getKey();
			JsonElement json = entry.getValue();
			SpellDefinition def = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, json)
					.resultOrPartial(err -> LOGGER.warn("Failed to parse spell definition {}: {}", fileId, err))
					.orElse(null);
			if (def == null) {
				continue;
			}
			if (!def.id.equals(fileId)) {
				LOGGER.warn("Skipped spell definition {} because JSON id {} does not match datapack path", fileId, def.id);
				continue;
			}
			loaded.put(fileId, def);
		}
		SpellRegistry.applyDatapackDefaults(loaded);
		LOGGER.info("Loaded {} spell definition(s) from datapacks", loaded.size());
	}
}
