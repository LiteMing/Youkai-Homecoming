package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.ref.WeakReference;

/** Short feedback drawn above the active screen, including dock and text overlays. */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EditorNotifications {

	private static WeakReference<Screen> owner = new WeakReference<>(null);
	private static Component message;
	private static long expiresAt;
	private static int anchorX, anchorY;

	private EditorNotifications() {
	}

	public static void show(Component text) {
		Minecraft minecraft = Minecraft.getInstance();
		Screen screen = minecraft.screen;
		if (screen == null) {
			if (minecraft.player != null) minecraft.player.displayClientMessage(text, true);
			return;
		}
		owner = new WeakReference<>(screen);
		message = text.copy();
		expiresAt = Util.getMillis() + 1600;
		anchorX = (int) (minecraft.mouseHandler.xpos() * screen.width / minecraft.getWindow().getScreenWidth());
		anchorY = (int) (minecraft.mouseHandler.ypos() * screen.height / minecraft.getWindow().getScreenHeight());
	}

	@SubscribeEvent
	public static void render(ScreenEvent.Render.Post event) {
		if (message == null) return;
		Screen screen = owner.get();
		if (screen == null || Minecraft.getInstance().screen != screen || Util.getMillis() >= expiresAt) {
			message = null;
			owner.clear();
			return;
		}
		if (event.getScreen() != screen) return;
		var graphics = event.getGuiGraphics();
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 600);
		graphics.renderTooltip(Minecraft.getInstance().font, message, anchorX, anchorY);
		graphics.pose().popPose();
	}
}
