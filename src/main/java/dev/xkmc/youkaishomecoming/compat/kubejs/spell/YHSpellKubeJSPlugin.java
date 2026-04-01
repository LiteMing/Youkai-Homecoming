package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ClassFilter;

public class YHSpellKubeJSPlugin extends KubeJSPlugin {

	@Override
	public void registerEvents() {
		YHSpellKubeJSEvents.register();
	}

	@Override
	public void registerBindings(BindingsEvent event) {
		if (event.getType().isStartup()) {
			event.add("YHEvents", new YHSpellEventBindings());
		}
	}

	@Override
	public void registerClasses(ScriptType type, ClassFilter filter) {
		if (type.isStartup()) {
			filter.allow("dev.xkmc.youkaishomecoming");
		}
	}
}
