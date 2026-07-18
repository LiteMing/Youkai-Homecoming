package dev.xkmc.youkaishomecoming.content.spell.market;

import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

@OnlyIn(Dist.CLIENT)
public class SpellMarketManager {

	private static final Logger LOGGER = LoggerFactory.getLogger("SpellMarket");

	private static SpellMarketManager instance;

	@Nullable
	private SpellMarketAPI api;

	private SpellMarketManager() {
		reload();
	}

	public static SpellMarketManager getInstance() {
		if (instance == null) {
			instance = new SpellMarketManager();
		}
		return instance;
	}

	public void reload() {
		if (YHModConfig.COMMON.spellMarketEnabled.get()) {
			String url = YHModConfig.COMMON.spellMarketUrl.get();
			this.api = new SpellMarketAPI(url);
			LOGGER.info("Spell market enabled, server URL: {}", url);
		} else {
			this.api = null;
			LOGGER.info("Spell market disabled by config");
		}
	}

	@Nullable
	public SpellMarketAPI getAPI() {
		return api;
	}

	public boolean isEnabled() {
		return api != null;
	}

}
