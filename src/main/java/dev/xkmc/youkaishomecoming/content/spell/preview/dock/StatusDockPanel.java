package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import dev.xkmc.youkaishomecoming.content.spell.preview.OrthographicViewport;
import dev.xkmc.youkaishomecoming.content.spell.preview.SpellEditorLocalization;
import dev.xkmc.youkaishomecoming.content.spell.preview.VirtualSpellScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Compact preview HUD panel that consolidates numeric/status text that used to
 * be duplicated in Controls and Viewport overlays.
 */
@OnlyIn(Dist.CLIENT)
public class StatusDockPanel implements DockPanel {

	private static final int PADDING = 6;
	/** Refresh text every N frames to reduce GC pressure from string rebuilding at 60fps. */
	private static final int REFRESH_INTERVAL = 3;

	private final VirtualSpellScene scene;
	private final OrthographicViewport viewport;

	private int x, y, w, h;
	private int frameCounter;
	private List<Line> cachedLines;

	public StatusDockPanel(VirtualSpellScene scene, OrthographicViewport viewport) {
		this.scene = scene;
		this.viewport = viewport;
	}

	@Override
	public String dockTitle() {
		return "Status";
	}

	@Override
	public String dockId() {
		return "status";
	}

	@Override
	public void setBounds(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		this.cachedLines = null; // Invalidate on resize
	}

	@Override
	public int getX() {
		return x;
	}

	@Override
	public int getY() {
		return y;
	}

	@Override
	public int getWidth() {
		return w;
	}

	@Override
	public int getHeight() {
		return h;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(x, y, x + w, y + h, 0xCC0C111B);
		graphics.fill(x, y, x + w, y + 1, 0xFF4F6E8C);
		graphics.fill(x, y + h - 1, x + w, y + h, 0xFF243447);
		graphics.fill(x, y, x + 1, y + h, 0xFF243447);
		graphics.fill(x + w - 1, y, x + w, y + h, 0xFF243447);

		Font font = Minecraft.getInstance().font;
		int contentWidth = Math.max(0, w - PADDING * 2);
		int contentHeight = Math.max(0, h - PADDING * 2);
		if (contentWidth <= 0 || contentHeight <= 0) {
			return;
		}

		// Only rebuild text lines every REFRESH_INTERVAL frames
		frameCounter++;
		if (cachedLines == null || frameCounter >= REFRESH_INTERVAL) {
			frameCounter = 0;
			cachedLines = buildWrappedLines(font, contentWidth);
		}

		int lineY = y + PADDING;
		int lineStep = font.lineHeight + 2;
		int maxLines = Math.max(1, (contentHeight + 2) / lineStep);
		int renderedLines = 0;
		for (Line line : cachedLines) {
			if (renderedLines >= maxLines || lineY + font.lineHeight > y + h - PADDING) {
				break;
			}
			graphics.drawString(font, line.text(), x + PADDING, lineY, line.color(), false);
			lineY += lineStep;
			renderedLines++;
		}
	}

	private List<Line> buildWrappedLines(Font font, int width) {
		List<Line> lines = new ArrayList<>();
		boolean zh = SpellEditorLocalization.isChinese();
		String playState = zh ? (scene.isPlaying() ? "运行中" : "已暂停") : (scene.isPlaying() ? "Running" : "Paused");
		ResourceLocation phaseId = scene.getCurrentPhaseId();
		String phaseText = phaseId == null ? "-" : phaseId.toString();
		if (zh) {
			appendWrapped(lines, font, width,
					playState + "  tick:" + scene.getTotalTick() +
							"  阶段tick:" + scene.getPhaseTick() +
							"  速度:" + formatDecimal(scene.getCurrentSpeed()) + "x",
					0xFFE2E8F0);
			appendWrapped(lines, font, width, "阶段: " + phaseText, 0xFFB9C8DA);
			appendWrapped(lines, font, width,
					"实体: " + scene.getEntityCount() +
							"  命中: " + scene.getHitCount() +
							"  安全: " + (scene.isSafetyTripped() ? "触发" : "正常"),
					scene.isSafetyTripped() ? 0xFFFFAA55 : 0xFFAED8AE);
			appendWrapped(lines, font, width, "视图: " + SpellEditorLocalization.t(viewport.getViewLabel()), 0xFF8CC6FF);
			appendWrapped(lines, font, width, "实体目标: " + formatVec(scene.getTargetPos()), 0xFFFFD36B);
			appendWrapped(lines, font, width,
					"方块目标: " + formatVec(scene.getBlockTargetPos()) +
							"  尺寸:" + formatVec(scene.getTargetBoxSize()), 0xFF66CCFF);
			appendWrapped(lines, font, width, "施法者: " + formatVec(scene.getCasterPos()), 0xFFFF9A9A);
			appendWrapped(lines, font, width,
					"距离: " + formatDecimal(scene.getTargetDistance()) +
							"  施法者HP: " + Math.round(scene.getHealthRatio() * 100) + "%" +
							"  P点: " + formatDecimal(scene.getCasterPower()) +
							"  目标HP: " + Math.round(scene.getTargetHealthRatio() * 100) + "%",
					0xFFD6D3F0);
			appendWrapped(lines, font, width,
					"地面=" + yesNo(scene.isTargetOnGround()) +
							"  飞行=" + yesNo(scene.isTargetFlying()) +
							"  鞘翅=" + yesNo(scene.isTargetFallFlying()) +
							"  目标Y=" + formatDecimal(scene.getTargetHeight()),
					0xFFB8C5D6);
		} else {
			appendWrapped(lines, font, width,
					playState + "  tick:" + scene.getTotalTick() +
							"  phaseTick:" + scene.getPhaseTick() +
							"  speed:" + formatDecimal(scene.getCurrentSpeed()) + "x",
					0xFFE2E8F0);
			appendWrapped(lines, font, width, "phase: " + phaseText, 0xFFB9C8DA);
			appendWrapped(lines, font, width,
					"entities: " + scene.getEntityCount() +
							"  hits: " + scene.getHitCount() +
							"  safety: " + (scene.isSafetyTripped() ? "TRIPPED" : "OK"),
					scene.isSafetyTripped() ? 0xFFFFAA55 : 0xFFAED8AE);
			appendWrapped(lines, font, width, "view: " + viewport.getViewLabel(), 0xFF8CC6FF);
			appendWrapped(lines, font, width, "entity target: " + formatVec(scene.getTargetPos()), 0xFFFFD36B);
			appendWrapped(lines, font, width,
					"block target: " + formatVec(scene.getBlockTargetPos()) +
							"  size:" + formatVec(scene.getTargetBoxSize()), 0xFF66CCFF);
			appendWrapped(lines, font, width, "caster: " + formatVec(scene.getCasterPos()), 0xFFFF9A9A);
			appendWrapped(lines, font, width,
					"dist: " + formatDecimal(scene.getTargetDistance()) +
							"  casterHP: " + Math.round(scene.getHealthRatio() * 100) + "%" +
							"  power: " + formatDecimal(scene.getCasterPower()) +
							"  targetHP: " + Math.round(scene.getTargetHealthRatio() * 100) + "%",
					0xFFD6D3F0);
			appendWrapped(lines, font, width,
					"ground=" + yesNo(scene.isTargetOnGround()) +
							"  fly=" + yesNo(scene.isTargetFlying()) +
							"  elytra=" + yesNo(scene.isTargetFallFlying()) +
							"  targetY=" + formatDecimal(scene.getTargetHeight()),
					0xFFB8C5D6);
		}
		return lines;
	}

	private void appendWrapped(List<Line> output, Font font, int width, String text, int color) {
		for (var seq : font.split(Component.literal(text), width)) {
			output.add(new Line(seq, color));
		}
	}

	private static String formatVec(Vec3 vec) {
		return "(" + formatDecimal(vec.x) + ", " + formatDecimal(vec.y) + ", " + formatDecimal(vec.z) + ")";
	}

	private static String formatDecimal(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	private static String yesNo(boolean value) {
		return SpellEditorLocalization.t(value ? "Y" : "N");
	}

	private record Line(net.minecraft.util.FormattedCharSequence text, int color) {}
}
