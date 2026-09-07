package dev.xkmc.youkaishomecoming.content.capability;

import dev.xkmc.youkaishomecoming.compat.stg.control.ClassicControlClient;
import dev.xkmc.youkaishomecoming.events.AutoDodgeClientHandlers;
import dev.xkmc.youkaishomecoming.events.AutoDodgeClientHandlers.GuidanceDirection;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public final class AutoDodgeStatusOverlay implements IGuiOverlay {

	@Override
	public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen != null || minecraft.player == null) return;
		var state = AutoDodgeClientHandlers.hudState();
		Component dodgeText = switch (state.mode()) {
			case HIDDEN -> null;
			case IDLE -> YHLangData.AUTO_DODGE_STATUS_IDLE.get();
			case AUTO -> YHLangData.AUTO_DODGE_STATUS_AUTO.get();
			case MANUAL -> YHLangData.AUTO_DODGE_STATUS_MANUAL.get();
			case GUIDED -> YHLangData.AUTO_DODGE_STATUS_GUIDED.get(directionText(state.directions()));
		};
		Component controlText = null;
		if (GrazeCapability.HOLDER.get(minecraft.player).isInDanmakuCombat()) {
			controlText = ClassicControlClient.isEnabled()
					? YHLangData.CLASSIC_CONTROL_STATUS_CLASSIC.get()
					: YHLangData.CLASSIC_CONTROL_STATUS_SPELL.get();
		}
		if (dodgeText == null && controlText == null) return;

		var font = gui.getFont();
		int x = 8;
		int lineStep = font.lineHeight + 5;
		int y = Math.max(8 + (dodgeText != null && controlText != null ? lineStep : 0), height - 48);
		if (dodgeText != null) {
			drawStatus(graphics, font, dodgeText, x, y);
			y -= lineStep;
		}
		if (controlText != null) drawStatus(graphics, font, controlText, x, y);
	}

	private static void drawStatus(GuiGraphics graphics, Font font,
			Component text, int x, int y) {
		int textWidth = font.width(text);
		graphics.fill(x - 3, y - 2, x + textWidth + 3, y + font.lineHeight + 2, 0x88000000);
		graphics.drawString(font, text, x, y, 0xFFFFFFFF, true);
	}

	private static Component directionText(java.util.List<GuidanceDirection> directions) {
		MutableComponent result = Component.empty();
		for (int i = 0; i < directions.size(); i++) {
			if (i > 0) result.append(Component.literal(" / "));
			result.append(directionText(directions.get(i)));
		}
		return result;
	}

	private static Component directionText(GuidanceDirection direction) {
		return switch (direction) {
			case UP -> YHLangData.AUTO_DODGE_DIRECTION_UP.get();
			case DOWN -> YHLangData.AUTO_DODGE_DIRECTION_DOWN.get();
			case LEFT -> YHLangData.AUTO_DODGE_DIRECTION_LEFT.get();
			case RIGHT -> YHLangData.AUTO_DODGE_DIRECTION_RIGHT.get();
			case FORWARD -> YHLangData.AUTO_DODGE_DIRECTION_FORWARD.get();
			case BACKWARD -> YHLangData.AUTO_DODGE_DIRECTION_BACKWARD.get();
		};
	}
}
