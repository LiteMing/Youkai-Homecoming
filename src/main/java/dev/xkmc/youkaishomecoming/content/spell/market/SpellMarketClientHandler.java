package dev.xkmc.youkaishomecoming.content.spell.market;

import net.minecraft.client.Minecraft;

public class SpellMarketClientHandler {

	public static void open() {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.setScreen(new SpellMarketScreen(mc.screen)));
	}
}
