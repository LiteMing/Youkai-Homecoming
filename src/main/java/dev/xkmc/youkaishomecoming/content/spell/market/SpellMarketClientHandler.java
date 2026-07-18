package dev.xkmc.youkaishomecoming.content.spell.market;

import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class SpellMarketClientHandler {

	public static void open() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) return;
		mc.execute(() -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) return;
			try {
				client.setScreen(new SpellMarketScreen(client.screen));
			} catch (Exception e) {
				YoukaisHomecoming.LOGGER.error("Failed to open spell market screen", e);
				if (client.player != null) {
					String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
					client.player.displayClientMessage(Component.literal("[YH] Failed to open spell market: " + message), false);
				}
			}
		});
	}
}
