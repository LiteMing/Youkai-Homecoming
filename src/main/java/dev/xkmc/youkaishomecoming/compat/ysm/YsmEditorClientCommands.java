package dev.xkmc.youkaishomecoming.compat.ysm;

import dev.xkmc.youkaishomecoming.content.spell.preview.SpellPreviewScreen;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID)
public final class YsmEditorClientCommands {
	private static boolean openPending;
	@SubscribeEvent public static void register(RegisterClientCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("yhysm").then(Commands.literal("editor").executes(context -> { openPending = true; return 1; })));
	}
	@SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END || !openPending) return;
		openPending = false;
		if (Minecraft.getInstance().level != null) Minecraft.getInstance().setScreen(SpellPreviewScreen.createYsmEditor());
	}
}
