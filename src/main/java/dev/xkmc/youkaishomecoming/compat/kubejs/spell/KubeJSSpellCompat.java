package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.latvian.mods.kubejs.script.ScriptType;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;

public class KubeJSSpellCompat {

	public static void registerStartupSpells() {
		RegisterSpellEventJS event = new RegisterSpellEventJS();
		YHSpellKubeJSEvents.REGISTER_SPELLS.post(ScriptType.STARTUP, event);
		int count = event.registerAll();
		if (count > 0) {
			YoukaisHomecoming.LOGGER.info("Registered {} spell definitions from KubeJS startup scripts", count);
		}
	}
}
