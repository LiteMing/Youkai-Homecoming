package dev.xkmc.youkaishomecoming.compat.curios;

import dev.xkmc.youkaishomecoming.content.item.danmaku.ISpellItem;
import dev.xkmc.youkaishomecoming.init.data.YHTagGen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;

public class CuriosManager {

	public static boolean hasHead(LivingEntity le, Item item, boolean checkRender) {
		if (le.getItemBySlot(EquipmentSlot.HEAD).is(item))
			return true;
		if (ModList.get().isLoaded("curios")) {
			return CuriosApi.getCuriosInventory(le)
					.resolve().flatMap(e -> e.findFirstCurio(item))
					.map(e -> !checkRender || e.slotContext().visible())
					.orElse(false);
		}
		return false;
	}

	public static boolean hasWings(LivingEntity le, Item item, boolean checkRender) {
		if (le.getItemBySlot(EquipmentSlot.CHEST).is(item))
			return true;
		if (ModList.get().isLoaded("curios")) {
			return CuriosApi.getCuriosInventory(le)
					.resolve().flatMap(e -> e.findFirstCurio(item))
					.map(e -> !checkRender || e.slotContext().visible())
					.orElse(false);
		}
		return false;
	}

	public static boolean hasAnyWings(LivingEntity le) {
		if (le.getItemBySlot(EquipmentSlot.CHEST).is(YHTagGen.TOUHOU_WINGS))
			return true;
		if (ModList.get().isLoaded("curios")) {
			return CuriosApi.getCuriosInventory(le)
					.resolve().flatMap(e -> e.findFirstCurio(s -> s.is(YHTagGen.TOUHOU_WINGS)))
					.isPresent();
		}
		return false;
	}

	public static boolean hasAnySpellItem(LivingEntity le) {
		return !findFirstSpellItem(le).isEmpty();
	}

	/** Returns the first spell card in Curios order, or an empty stack. */
	public static ItemStack findFirstSpellItem(LivingEntity le) {
		if (!ModList.get().isLoaded("curios")) return ItemStack.EMPTY;
		return CuriosApi.getCuriosInventory(le).resolve()
				.flatMap(handler -> handler.findFirstCurio(stack -> stack.getItem() instanceof ISpellItem))
				.map(result -> result.stack())
				.orElse(ItemStack.EMPTY);
	}

}
