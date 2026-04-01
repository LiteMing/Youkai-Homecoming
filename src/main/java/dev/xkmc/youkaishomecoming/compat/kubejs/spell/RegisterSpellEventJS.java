package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.latvian.mods.kubejs.event.StartupEventJS;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class RegisterSpellEventJS extends StartupEventJS {

	private final Map<ResourceLocation, SpellDefinitionBuilder> builders = new LinkedHashMap<>();

	public SpellDefinitionBuilder create(String id) {
		ResourceLocation spellId = KubeJSSpellSupport.parseId(id);
		return builders.computeIfAbsent(spellId, SpellDefinitionBuilder::new);
	}

	public Collection<SpellDefinition> buildDefinitions() {
		return builders.values().stream().map(SpellDefinitionBuilder::build).toList();
	}

	public int registerAll() {
		int count = 0;
		for (SpellDefinition definition : buildDefinitions()) {
			SpellRegistry.register(definition);
			count++;
		}
		return count;
	}

	public int size() {
		return builders.size();
	}
}
