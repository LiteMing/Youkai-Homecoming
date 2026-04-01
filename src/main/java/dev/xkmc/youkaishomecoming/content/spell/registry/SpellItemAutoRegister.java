package dev.xkmc.youkaishomecoming.content.spell.registry;

import com.tterrag.registrate.util.entry.ItemEntry;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.SpellItem;
import dev.xkmc.youkaishomecoming.content.spell.item.ItemSpell;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHTagGen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class SpellItemAutoRegister {

	public record LegacySpellItemSpec(
			String itemId,
			Supplier<? extends ItemSpell> spellFactory,
			boolean requiresTarget,
			Supplier<Item> ammoItem,
			String modelPath,
			String displayName
	) {
	}

	public static ItemEntry<SpellItem> registerLegacy(LegacySpellItemSpec spec) {
		Supplier<ItemSpell> factory = () -> spec.spellFactory().get();
		return YoukaisHomecoming.REGISTRATE
				.item(spec.itemId(), p -> new SpellItem(
						p.stacksTo(1),
						factory,
						spec.requiresTarget(),
						spec.ammoItem()))
				.model((ctx, pvd) -> pvd.generated(ctx, pvd.modLoc(spec.modelPath())))
				.tag(YHTagGen.PRESET_SPELL)
				.lang(spec.displayName())
				.register();
	}

	public static ItemEntry<DynamicSpellItem> registerDynamic(
			String itemId,
			@Nullable ResourceLocation defaultSpellId,
			String modelPath,
			String displayName
	) {
		return YoukaisHomecoming.REGISTRATE
				.item(itemId, p -> new DynamicSpellItem(p.stacksTo(1), defaultSpellId))
				.model((ctx, pvd) -> pvd.generated(ctx, pvd.modLoc(modelPath)))
				.tag(YHTagGen.PRESET_SPELL)
				.lang(displayName)
				.register();
	}
}
