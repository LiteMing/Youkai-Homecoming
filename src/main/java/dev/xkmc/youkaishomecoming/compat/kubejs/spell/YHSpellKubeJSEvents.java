package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

public class YHSpellKubeJSEvents {

	public static final EventGroup GROUP = EventGroup.of("YHSpellEvents");

	public static final EventHandler REGISTER = GROUP.startup("register", () -> RegisterSpellsEventJS.class);

}
