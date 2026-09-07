package dev.xkmc.youkaishomecoming.compat.kubejs.stg;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

public final class YHStgKubeJSEvents {

	public static final EventGroup GROUP = EventGroup.of("YHStgEvents");
	public static final EventHandler CLASSIC_CONTROL_CHANGED = GROUP.client(
			"classicControlChanged", () -> ClassicControlModeEventJS.class);

	private YHStgKubeJSEvents() {
	}
}
