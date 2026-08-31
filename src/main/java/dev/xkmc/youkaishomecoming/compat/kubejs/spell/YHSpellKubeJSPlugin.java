package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ClassFilter;
import dev.xkmc.youkaishomecoming.compat.stg.YHStg;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellConditions;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntimeAccess;

public class YHSpellKubeJSPlugin extends KubeJSPlugin {

	@Override
	public void registerEvents() {
		YHSpellKubeJSEvents.GROUP.register();
	}

	@Override
	public void registerBindings(BindingsEvent event) {
		event.add("SpellRegistry", SpellRegistry.class);
		event.add("YHSpellRuntime", SpellRuntimeAccess.class);
		event.add("YHSpellMarket", YHSpellMarket.class);
		event.add("YHSpellConfig", YHSpellConfig.class);
		event.add("YHStg", YHStg.class);
		event.add("SpellConditions", SpellConditions.class);
		event.add("SpellActions", SpellActions.class);
	}

	@Override
	public void registerClasses(ScriptType type, ClassFilter filter) {
		filter.allow("dev.xkmc.youkaishomecoming.compat.stg");
		filter.allow("dev.xkmc.youkaishomecoming.content.spell");
	}

	@Override
	public void initStartup() {
		KubeJSSpellActions.register();
		KubeJSSpellConditions.register();
		RegisterSpellsEventJS.fireAndRegister();
	}
}
