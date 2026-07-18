package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.latvian.mods.kubejs.event.EventJS;
import dev.xkmc.youkaishomecoming.content.spell.market.SpellMarketServerManager;

public class MarketSyncEventJS extends EventJS {

	public final String jobId;
	public final SpellMarketServerManager.SyncResult result;

	public MarketSyncEventJS(String jobId, SpellMarketServerManager.SyncResult result) {
		this.jobId = jobId;
		this.result = result;
	}
}
