package dev.xkmc.youkaishomecoming.content.capability;

import dev.xkmc.youkaishomecoming.compat.stg.control.ClassicControlClient;
import dev.xkmc.youkaishomecoming.events.AutoDodgeClientHandlers;
import dev.xkmc.youkaishomecoming.events.AutoDodgeClientHandlers.GuidanceDirection;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
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
		if (!YHModConfig.CLIENT.combatStatusHudEnabled.get()) return;
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
					: YHLangData.CLASSIC_CONTROL_STATUS_MODERN.get();
		}
		if (dodgeText == null && controlText == null) return;

		var font = gui.getFont();
		float scale = YHModConfig.CLIENT.combatStatusHudScale.get().floatValue();
		int lineStep = font.lineHeight + 5;
		java.util.List<Component> lines = new java.util.ArrayList<>(2);
		if (controlText != null) lines.add(controlText);
		if (dodgeText != null) lines.add(dodgeText);
		int panelWidth = lines.stream().mapToInt(font::width).max().orElse(0) + 6;
		int panelHeight = lines.size() * lineStep - 1;
		int renderedWidth = Math.round(panelWidth * scale);
		int renderedHeight = Math.round(panelHeight * scale);
		int x = anchored(YHModConfig.CLIENT.combatStatusHudXAnchor.get(),
				YHModConfig.CLIENT.combatStatusHudXOffset.get(), width, renderedWidth);
		int y = anchored(YHModConfig.CLIENT.combatStatusHudYAnchor.get(),
				YHModConfig.CLIENT.combatStatusHudYOffset.get(), height, renderedHeight);
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().scale(scale, scale, scale);
		for (Component line : lines) {
			drawStatus(graphics, font, line, 3, 2);
			graphics.pose().translate(0, lineStep, 0);
		}
		graphics.pose().popPose();
	}

	private static int anchored(int anchor, int offset, int viewport, int content) {
		return offset + (anchor + 1) * (viewport - content) / 2;
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
