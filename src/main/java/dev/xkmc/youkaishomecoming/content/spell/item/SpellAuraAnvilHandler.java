package dev.xkmc.youkaishomecoming.content.spell.item;

import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.SpellAuraItem;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellCardType;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Converts an unfinished dynamic card with a special-type aura in an anvil. */
@Mod.EventBusSubscriber(modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpellAuraAnvilHandler {
	private SpellAuraAnvilHandler() {
	}

	@SubscribeEvent
	public static void onAnvilUpdate(AnvilUpdateEvent event) {
		ItemStack left = event.getLeft();
		ItemStack right = event.getRight();
		if (!(left.getItem() instanceof DynamicSpellItem)
				|| !(right.getItem() instanceof SpellAuraItem aura)
				|| DynamicSpellItem.getSpellId(left) == null
				|| DynamicSpellItem.isComplete(left)
				|| CertifiedSpellValidator.isCertified(left)) {
			return;
		}
		SpellCardType type = aura.type();
		if (!aura.isEx() && type != SpellCardType.NON_SPELL
				&& type != SpellCardType.TIMEOUT_SPELL
				&& type != SpellCardType.LAST_SPELL) {
			return;
		}
		if (!aura.isEx() && type == SpellCardType.NON_SPELL && DynamicSpellItem.isExSpell(left)) return;
		ItemStack output = left.copy();
		if (aura.isEx()) {
			// EX is an independent health trait.  Do not reset an existing special
			// card type when the EX aura is applied.
			DynamicSpellItem.setExSpell(output, true);
		} else {
			DynamicSpellItem.setCardType(output, type);
			DynamicSpellItem.setExSpell(output, false);
		}
		event.setOutput(output);
		event.setMaterialCost(1);
		event.setCost(aura.isEx() ? 30 : switch (type) {
			case NON_SPELL -> 5;
			case TIMEOUT_SPELL -> 10;
			case LAST_SPELL -> 15;
			default -> 0;
		});
	}
}
