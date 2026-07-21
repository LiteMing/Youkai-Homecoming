package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

public class YHSpellKubeJSEvents {

	public static final EventGroup GROUP = EventGroup.of("YHSpellEvents");

	public static final EventHandler REGISTER = GROUP.startup("register", () -> RegisterSpellsEventJS.class);
	public static final EventHandler MARKET_SYNC_COMPLETED = GROUP.server("marketSyncCompleted", () -> MarketSyncEventJS.class);
	public static final EventHandler DYNAMIC_SPELL_CAST = GROUP.server("dynamicSpellCast", () -> DynamicSpellCastEventJS.class).hasResult();
	public static final EventHandler DYNAMIC_SPELL_SINGLE_USE = GROUP.server("dynamicSpellSingleUse", () -> DynamicSpellSingleUseEventJS.class);

}
