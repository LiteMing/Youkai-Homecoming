package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.latvian.mods.kubejs.event.EventHandler;

public class YHSpellEventBindings {

	public final EventHandler registerSpells = YHSpellKubeJSEvents.REGISTER_SPELLS;
	public final KubeJSSpellActions actions = new KubeJSSpellActions();
	public final KubeJSSpellConditions conditions = new KubeJSSpellConditions();
}
