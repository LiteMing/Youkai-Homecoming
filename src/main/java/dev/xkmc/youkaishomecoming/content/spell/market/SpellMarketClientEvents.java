package dev.xkmc.youkaishomecoming.content.spell.market;

import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SpellMarketClientEvents {

	@SubscribeEvent
	public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
		SpellMarketCommand.register(event.getDispatcher());
	}

}
