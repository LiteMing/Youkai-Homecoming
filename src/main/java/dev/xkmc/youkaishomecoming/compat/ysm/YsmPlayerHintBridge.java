package dev.xkmc.youkaishomecoming.compat.ysm;

import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import rip.ysm.api.client.PlayerPresentationAPI;

/** YH spell hint policy for OYSM's native player renderer. */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class YsmPlayerHintBridge {
	private YsmPlayerHintBridge() { }

	@SubscribeEvent
	public static void clientSetup(FMLClientSetupEvent event) {
		if (!YSMClientCompat.isLoaded()) return;
		event.enqueueWork(() -> PlayerPresentationAPI.registerHintProvider(YoukaisHomecoming.MODID, (player, model, hasAnimation) -> {
			if (YSMClientCompat.isBeatenProjection(player)) return null;
			String hint = YsmSpellHintClient.animationOverride(player);
			if (hint.isBlank()) return null;
			String normalized = YsmAnimationHints.normalize(hint, key -> YSMCompatConfig.expressionToken(model, key));
			return YsmAnimationHints.resolve(normalized, hasAnimation);
		}));
	}
}
