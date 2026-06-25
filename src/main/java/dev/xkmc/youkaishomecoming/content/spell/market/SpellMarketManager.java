package dev.xkmc.youkaishomecoming.content.spell.market;

import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

@OnlyIn(Dist.CLIENT)
public class SpellMarketManager {

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
		if (YHModConfig.CLIENT.spellMarketEnabled.get()) {
			String url = YHModConfig.CLIENT.spellMarketUrl.get();
			this.api = new SpellMarketAPI(url);
		} else {
			this.api = null;
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
