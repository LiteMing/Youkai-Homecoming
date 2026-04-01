package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

public interface YHSpellKubeJSEvents {

	EventGroup GROUP = EventGroup.of("YHEvents");
	EventHandler REGISTER_SPELLS = GROUP.startup("registerSpells", () -> RegisterSpellEventJS.class);

	static void register() {
		GROUP.register();
	}
}
