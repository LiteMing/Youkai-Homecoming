package dev.xkmc.youkaishomecoming.content.spell.loader;

import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SpellDatapackEvents {

	@SubscribeEvent
	public static void addReloadListener(AddReloadListenerEvent event) {
		event.addListener(new SpellDefinitionLoader());
	}
}
