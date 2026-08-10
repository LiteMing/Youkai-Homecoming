package dev.xkmc.youkaishomecoming.client.hud;

import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationState;
import dev.xkmc.youkaishomecoming.content.spell.certification.network.CertificationClientHandler;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Certification HUD projection (design doc D12): remaining time, No-Hit status,
 * failure reason and success state for the author's own trial. Pure client
 * projection of CertificationStateToClient — never infers battle state.
 */
@Mod.EventBusSubscriber(modid = YoukaisHomecoming.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CertificationHud {

	private CertificationHud() {
	}

	@SubscribeEvent
	public static void onRenderGui(RenderGuiEvent.Post event) {
		var state = CertificationClientHandler.getMyState();
		if (state == null) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		GuiGraphics gui = event.getGuiGraphics();
		int width = mc.getWindow().getGuiScaledWidth();
		var font = mc.font;
		if (state.active()) {
			// Active HP/time is projected on the player's spell circle.
			return;
		}
		if (state.state() == CertificationState.SUCCESS) {
			gui.drawString(font, Component.literal("[YH] No-Hit Success!"),
					width / 2 - 46, 6, 0xFF55FF55);
			return;
		}
		if (state.state() == CertificationState.FAILED
				|| state.state() == CertificationState.SYSTEM_ERROR) {
			String reason = state.failReason() == null ? "unknown" : state.failReason();
			gui.drawString(font, Component.literal("[YH] Certification failed: " + reason),
					width / 2 - 80, 6, 0xFFFF5555);
		}
	}
}
