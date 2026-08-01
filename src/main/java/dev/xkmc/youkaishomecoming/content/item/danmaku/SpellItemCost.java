package dev.xkmc.youkaishomecoming.content.item.danmaku;

import dev.xkmc.youkaishomecoming.compat.stg.YHStgApi;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Unified spell-card cast cost shared by every ISpellItem implementation.
 * Inside STG danmaku combat a bomb is spent; outside combat player XP levels are spent.
 * Returns false and notifies the player when the cost cannot be paid.
 */
public final class SpellItemCost {

	private SpellItemCost() {
	}

	public static boolean tryPay(ServerPlayer sp) {
		if (YHStgApi.isInDanmakuSession(sp)) {
			int cost = YHModConfig.COMMON.spellBombCost.get();
			if (YHStgApi.getBombRaw(sp) < cost) {
				sp.displayClientMessage(YHLangData.SPELL_COST_NO_BOMB.get(cost, YHStgApi.getBomb(sp)), false);
				return false;
			}
			YHStgApi.addBombRaw(sp, -cost);
			return true;
		}
		int cost = YHModConfig.COMMON.spellXpCost.get();
		if (sp.experienceLevel < cost) {
			sp.displayClientMessage(YHLangData.SPELL_COST_NO_XP.get(cost, sp.experienceLevel), false);
			return false;
		}
		sp.giveExperienceLevels(-cost);
		return true;
	}

	public static void appendCostTooltip(List<Component> list) {
		if (YHModConfig.COMMON.spellBombCost.get() > 0) {
			list.add(YHLangData.SPELL_COST_BOMB.get(YHModConfig.COMMON.spellBombCost.get()));
		}
		if (YHModConfig.COMMON.spellXpCost.get() > 0) {
			list.add(YHLangData.SPELL_COST_XP.get(YHModConfig.COMMON.spellXpCost.get()));
		}
	}
}
