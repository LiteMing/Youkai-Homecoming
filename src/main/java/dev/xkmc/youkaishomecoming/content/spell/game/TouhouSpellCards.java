package dev.xkmc.youkaishomecoming.content.spell.game;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import dev.xkmc.youkaishomecoming.content.entity.boss.*;
import dev.xkmc.youkaishomecoming.content.entity.fairy.*;
import dev.xkmc.youkaishomecoming.content.entity.reimu.MaidenEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.GeneralYoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.bridge.LegacySpellBridge;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCard;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCardWrapper;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class TouhouSpellCards {

	/** Stable fallback for fairy entities whose requested fairy spell ID is absent. */
	public static final String FAIRY_GENERIC_ID = "fairy:generic";

	private static final Map<String, Supplier<SpellCard>> MAP = new ConcurrentHashMap<>();

	public static void registerSpell(String id, Supplier<SpellCard> card) {
		MAP.put(id, card);
		// Also register in the new SpellRegistry via legacy bridge
		ResourceLocation rl = new ResourceLocation(id);
		SpellRegistry.registerDefault(rl, LegacySpellBridge.fromLegacy(rl, card, id));
	}

	public static void registerSpells() {
		// Marisa is not yet migrated — always legacy.
		registerSpell("touhou_little_maid:kirisame_marisa", MarisaSpell::new);

		// All other Touhou spells have data-driven equivalents in MigratedSpellCards.
		// The useLegacySpellCards config toggles between the two implementations
		// (data-driven by default; flip to true to fall back to the original Java classes).
		boolean useLegacy = YHModConfig.COMMON.useLegacySpellCards.get();
		if (useLegacy) {
			registerSpell("touhou_little_maid:sunny_milk", SunnySpell::new);
			registerSpell("touhou_little_maid:luna_child", LunaSpell::new);
			registerSpell("touhou_little_maid:star_sapphire", StarSpell::new);
			registerSpell("touhou_little_maid:cirno", CirnoSpell::new);
			registerSpell("touhou_little_maid:mystia_lorelei", MystiaSpell::new);
			registerSpell("touhou_little_maid:konpaku_youmu", YoumuSpell::new);
			registerSpell("touhou_little_maid:eternity_larva", LarvaSpell::new);
			registerSpell("touhou_little_maid:kochiya_sanae", SanaeSpell::new);
			registerSpell("touhou_little_maid:clownpiece", ClownSpell::new);
			registerSpell("touhou_little_maid:izayoi_sakuya", SakuyaSpell::new);
			registerSpell("touhou_little_maid:kisin_sagume", KisinSpell::new);
			registerSpell("touhou_little_maid:remilia_scarlet", RemiliaSpell::new);
			registerSpell("touhou_little_maid:doremy_sweet", DoremiSpell::new);
			registerSpell("touhou_little_maid:komeiji_koishi", KoishiSpell::new);
			registerSpell("touhou_little_maid:hakurei_reimu", ReimuSpell::new);
			registerSpell("touhou_little_maid:yukari_yakumo", YukariSpell::new);
		} else {
			registerMigrated(MigratedSpellCards.sunnyMilk());
			registerMigrated(MigratedSpellCards.lunaChild());
			registerMigrated(MigratedSpellCards.starSapphire());
			registerMigrated(MigratedSpellCards.cirno());
			registerMigrated(MigratedSpellCards.mystia());
			registerMigrated(MigratedSpellCards.youmu());
			registerMigrated(MigratedSpellCards.larva());
			registerMigrated(MigratedSpellCards.sanae());
			registerMigrated(MigratedSpellCards.clown());
			registerMigrated(MigratedSpellCards.sakuya());
			registerMigrated(MigratedSpellCards.kisin());
			registerMigrated(MigratedSpellCards.remilia());
			registerMigrated(MigratedSpellCards.doremi());
			registerMigrated(MigratedSpellCards.koishi());
			registerMigrated(MigratedSpellCards.reimu());
			registerMigrated(MigratedSpellCards.yukari());
		}
	}

	/**
	 * Register a migrated data-driven SpellDefinition directly into SpellRegistry.
	 * No legacy SpellCard is created — the new runtime handles everything.
	 */
	public static void registerMigrated(dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition def) {
		SpellRegistry.registerDefault(def.id, def);
	}

	public static void setSpell(GeneralYoukaiEntity e, String id) {
		e.spellCard = new SpellCardWrapper();
		e.spellCard.modelId = id;

		String resolvedId = id;
		var sup = MAP.get(resolvedId);
		dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition definition = null;
		if (sup == null) {
			var exact = ResourceLocation.tryParse(resolvedId);
			if (exact != null) definition = SpellRegistry.get(exact);
		}
		if (sup == null && definition == null && isFairyFallbackCandidate(e, id)) {
			resolvedId = FAIRY_GENERIC_ID;
			sup = MAP.get(resolvedId);
			if (sup == null) {
				definition = SpellRegistry.get(new ResourceLocation(resolvedId));
			}
		}
		if (sup != null) {
			// Legacy path: use SpellCard subclass
			e.spellCard.card = sup.get();
			e.spellCard.spellId = new ResourceLocation(resolvedId);
		} else {
			// Migrated path: lookup from SpellRegistry and create SpellRuntime
			if (definition != null) {
				var runtime = new dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime(definition);
				e.setSpellRuntime(runtime);
				e.spellCard.spellId = new ResourceLocation(resolvedId);
			}
		}

		e.syncModel();
		if (ModList.get().isLoaded(TouhouLittleMaid.MOD_ID) && id.startsWith(TouhouLittleMaid.MOD_ID)) {
			var rl = new ResourceLocation(id);
			var name = Component.translatable(rl.toLanguageKey("model") + ".name");
			var desc = Component.translatable(rl.toLanguageKey("model") + ".desc");
			e.setCustomName(name.append(" - ").append(desc));
		}
	}

	private static boolean isFairyFallbackCandidate(GeneralYoukaiEntity entity, String id) {
		if (!(entity instanceof FairyEntity)) return false;
		var rl = ResourceLocation.tryParse(id);
		return rl != null && rl.getNamespace().equals("fairy") && !FAIRY_GENERIC_ID.equals(id);
	}

	public static void setReimu(MaidenEntity e) {
		setSpell(e, "touhou_little_maid:hakurei_reimu");
		if (ModList.get().isLoaded(TouhouLittleMaid.MOD_ID)) {
			e.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(InitItems.HAKUREI_GOHEI.get(), 1));
		}
	}

	public static void setCirno(CirnoEntity e) {
		setSpell(e, "touhou_little_maid:cirno");
	}

	public static void setYukari(YukariEntity e) {
		setSpell(e, "touhou_little_maid:yukari_yakumo");
	}

	public static void setSanae(SanaeEntity e) {
		setSpell(e, "touhou_little_maid:kochiya_sanae");
	}

	public static void setMarisa(MarisaEntity e) {
		setSpell(e, "touhou_little_maid:kirisame_marisa");
	}

	public static void setKoishi(KoishiEntity e) {
		setSpell(e, "touhou_little_maid:komeiji_koishi");
	}

	public static void setRemilia(RemiliaEntity e) {
		setSpell(e, "touhou_little_maid:remilia_scarlet");
	}

	public static void setMystia(MystiaEntity e) {
		setSpell(e, "touhou_little_maid:mystia_lorelei");
	}

	public static void setLuna(LunaEntity e) {
		setSpell(e, "touhou_little_maid:luna_child");
	}

	public static void setSunny(SunnyEntity e) {
		setSpell(e, "touhou_little_maid:sunny_milk");
	}

	public static void setStar(StarEntity e) {
		setSpell(e, "touhou_little_maid:star_sapphire");
	}

	public static void setLarva(LarvaEntity e) {
		setSpell(e, "touhou_little_maid:eternity_larva");
	}

	public static void setClown(ClownEntity e) {
		setSpell(e, "touhou_little_maid:clownpiece");
	}

}
