package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.latvian.mods.kubejs.event.EventJS;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class RegisterSpellsEventJS extends EventJS {

	private final List<SpellDefinitionBuilderJS> builders = new ArrayList<>();

	public SpellDefinitionBuilderJS create(String id) {
		var builder = new SpellDefinitionBuilderJS(new ResourceLocation(id));
		builders.add(builder);
		return builder;
	}

	static void fireAndRegister() {
		if (!YHSpellKubeJSEvents.REGISTER.hasListeners()) return;
		var event = new RegisterSpellsEventJS();
		YHSpellKubeJSEvents.REGISTER.post(event);
		for (var builder : event.builders) {
			SpellDefinition def = builder.build();
			SpellRegistry.registerDefault(def.id, def);
		}
	}
}
