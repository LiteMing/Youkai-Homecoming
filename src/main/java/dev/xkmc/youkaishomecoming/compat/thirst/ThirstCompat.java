package dev.xkmc.youkaishomecoming.compat.thirst;

import dev.ghen.thirst.foundation.common.event.RegisterThirstValueEvent;
import dev.xkmc.youkaishomecoming.init.food.YHBowl;
import dev.xkmc.youkaishomecoming.init.food.YHCoffee;
import dev.xkmc.youkaishomecoming.init.food.YHDrink;
import dev.xkmc.youkaishomecoming.init.food.YHFood;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ThirstCompat {

	public static void init() {
		MinecraftForge.EVENT_BUS.register(ThirstCompat.class);
	}

	@SubscribeEvent
	public static void compat(RegisterThirstValueEvent event) {
		for (var e : YHCoffee.values()) {
			event.addDrink(e.item.get(), 8, 13);
		}
		for (var e : YHDrink.values()) {
			event.addDrink(e.item.get(), 8, 13);
		}

		event.addDrink(YHFood.MILK_POPSICLE.item.get(), 6, 10);
		event.addDrink(YHFood.BIG_POPSICLE.item.get(), 6, 10);

		event.addDrink(YHFood.AVGOLEMONO.item.get(), 6, 10);
		event.addDrink(YHFood.SHIRAYUKI.item.get(), 6, 10);

		event.addDrink(YHBowl.HIGAN_SOUP.asItem(), 8, 13);
		event.addDrink(YHBowl.POOR_GOD_SOUP.asItem(), 8, 13);
		event.addDrink(YHBowl.POWER_SOUP.asItem(), 8, 13);
		event.addDrink(YHBowl.MUSHROOM_SOUP.asItem(), 8, 13);
		event.addDrink(YHBowl.MISO_SOUP.asItem(), 8, 13);
		event.addDrink(YHBowl.SEAFOOD_MISO_SOUP.asItem(), 8, 13);

	}

}
