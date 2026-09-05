package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpecialNodeCounter;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysis;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysisLimits;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalyzer;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapability;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellDraftBudget;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHealthPlan;
import dev.xkmc.youkaishomecoming.content.spell.preview.SpellEditorLocalization;
import dev.xkmc.youkaishomecoming.content.spell.preview.VirtualSpellScene;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class VariablesDockPanel implements DockPanel {

	private static final int PADDING = 6;
	private static final int REFRESH_INTERVAL = 3;

	private final VirtualSpellScene scene;

	private int x, y, w, h;
	private int scrollOffset;
	private boolean scrollbarDragging;
	private int frameCounter;
	private List<Line> cachedLines;

	public VariablesDockPanel(VirtualSpellScene scene) {
		this.scene = scene;
	}

	@Override
	public String dockTitle() {
		return SpellEditorLocalization.isChinese() ? "变量" : "Variables";
	}

	@Override
	public String dockId() {
		return "variables";
	}

	@Override
	public void setBounds(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		this.cachedLines = null;
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
		graphics.fill(x, y, x + w, y + h, 0xCC10121C);
		graphics.fill(x, y, x + w, y + 1, 0xFF536B8F);
		graphics.fill(x, y + h - 1, x + w, y + h, 0xFF243447);
		graphics.fill(x, y, x + 1, y + h, 0xFF243447);
		graphics.fill(x + w - 1, y, x + w, y + h, 0xFF243447);

		Font font = Minecraft.getInstance().font;
		int contentX = x + PADDING;
		int contentY = y + PADDING;
		int contentWidth = Math.max(0, w - PADDING * 2);
		int contentHeight = Math.max(0, h - PADDING * 2);
		if (contentWidth <= 0 || contentHeight <= 0) {
			return;
		}

		frameCounter++;
		if (cachedLines == null || frameCounter >= REFRESH_INTERVAL) {
			frameCounter = 0;
			cachedLines = buildWrappedLines(font, Math.max(1, contentWidth - 6));
		}

		int lineStep = font.lineHeight + 2;
		int totalHeight = cachedLines.size() * lineStep;
		int maxScroll = Math.max(0, totalHeight - contentHeight);
		scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

		int clipRight = Math.max(contentX, x + w - PADDING - (maxScroll > 0 ? 6 : 0));
		graphics.enableScissor(contentX, contentY, clipRight, contentY + contentHeight);
		for (int i = 0; i < cachedLines.size(); i++) {
			int lineY = contentY + i * lineStep - scrollOffset;
			if (lineY + font.lineHeight < contentY || lineY > contentY + contentHeight) {
				continue;
			}
			Line line = cachedLines.get(i);
			graphics.drawString(font, line.text(), contentX, lineY, line.color(), false);
		}
		graphics.disableScissor();

		if (maxScroll > 0) {
			renderScrollbar(graphics, contentY, contentHeight, totalHeight, maxScroll);
		}
	}

	private List<Line> buildWrappedLines(Font font, int width) {
		List<Line> lines = new ArrayList<>();
		Map<String, Double> variables = scene.getVariables();
		boolean zh = SpellEditorLocalization.isChinese();
		appendWrapped(lines, font, width,
				"tick:" + scene.getTotalTick() +
						(zh ? "  阶段tick:" : "  phaseTick:") + scene.getPhaseTick() +
						(zh ? "  变量数:" : "  variables:") + variables.size(),
				0xFFE2E8F0);
		appendBudgetProjection(lines, font, width, zh);

		if (variables.isEmpty()) {
			appendWrapped(lines, font, width, zh ? "当前没有运行时变量" : "No runtime variables", 0xFF8FA3B8);
			return lines;
		}

		List<Map.Entry<String, Double>> entries = new ArrayList<>(variables.entrySet());
		entries.sort(Comparator.comparing(Map.Entry::getKey));
		for (Map.Entry<String, Double> entry : entries) {
			appendWrapped(lines, font, width, "$" + entry.getKey() + " = " + formatNumber(entry.getValue()), 0xFF9DECF9);
		}
		return lines;
	}

	private void appendBudgetProjection(List<Line> lines, Font font, int width, boolean zh) {
		SpellDraftBudget budget = findActiveBudget();
		SpecialNodeCounter.Summary nodes;
		SpellAnalysis analysis;
		try {
			var definitions = new ArrayList<dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition>();
			var plan = SpellHealthPlan.analyzeIfPresent(scene.getDefinition(), SpellRegistry::get);
			if (plan.isPresent()) definitions.addAll(plan.get().definitions().values());
			else definitions.add(scene.getDefinition());
			nodes = SpecialNodeCounter.summarize(definitions);
			var analyses = new ArrayList<SpellAnalysis>();
			SpellAnalysisLimits limits = SpellAnalysisLimits.certification();
			long projectionWindow = plan.isPresent() && plan.get().totalDurationTicks() > 0
					? Math.min(limits.certificationWindowTicks(), plan.get().totalDurationTicks())
					: limits.certificationWindowTicks();
			limits = limits.withCertificationWindow(projectionWindow);
			for (var definition : definitions) analyses.add(SpellAnalyzer.analyzePreview(definition, limits));
			analysis = SpellAnalysis.combine(analyses);
		} catch (IllegalArgumentException e) {
			String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			appendWrapped(lines, font, width,
					(zh ? "预算分析不可用: " : "Budget analysis unavailable: ") + message,
					0xFFFF7777);
			return;
		}

		int excess = budget.excessNodes(nodes);
		appendWrapped(lines, font, width,
				zh ? "节点: 普通 " + nodes.ordinaryNodes() + "/免费 " + budget.freeNodeCount()
						+ "  超额 " + excess + "  高级Hook " + nodes.advancedHookNodes()
						: "Nodes: ordinary " + nodes.ordinaryNodes() + "/free " + budget.freeNodeCount()
						+ "  excess " + excess + "  advanced hooks " + nodes.advancedHookNodes(),
				excess > 0 ? 0xFFFFD36B : 0xFFAED8AE);
		boolean nonSpell = scene.getDefinition() != null
				&& scene.getDefinition().itemForm.cardType() == dev.xkmc.youkaishomecoming.content.spell.definition.SpellCardType.NON_SPELL;
		int powerScaledSpawnLimit = budget.maxSpawnPerTickForPower(
				dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCardRank.fromBudget(budget),
				scene.getCasterPower(), nonSpell);
		appendMetric(lines, font, width, zh ? "每tick生成" : "Spawn/tick",
				analysis.maxSpawnPerTick(), powerScaledSpawnLimit);
		appendMetric(lines, font, width, zh ? "峰值存活" : "Peak alive",
				analysis.peakAliveUpperBound(), budget.maxPeakAlive());
		appendMetric(lines, font, width, zh ? "弹幕tick（总量）" : "Projectile ticks (total)",
				analysis.projectileTicks(), budget.maxProjectileTicks());
		appendMetric(lines, font, width, zh ? "Hook执行" : "Hook executions",
				analysis.hookExecutionUpperBound(), budget.maxHookExecutions());
		appendWrapped(lines, font, width,
				(zh ? "实验能力: " : "Experimental: ")
						+ capabilityPair(nodes, budget, SpellCapability.TELEPORT, zh ? "传送" : "teleport") + "  "
						+ capabilityPair(nodes, budget, SpellCapability.ERASE_ENEMY_DANMAKU, zh ? "消弹" : "erase") + "  "
						+ capabilityPair(nodes, budget, SpellCapability.CLEAR_SCREEN, zh ? "清屏" : "clear") + "  "
						+ capabilityPair(nodes, budget, SpellCapability.BOSS_ON_DAMAGE, zh ? "受伤Hook" : "onDamage") + "  "
						+ capabilityPair(nodes, budget, SpellCapability.CONFINED_TARGET, zh ? "限制目标" : "confine") + "  "
						+ capabilityPair(nodes, budget, SpellCapability.EXPERIMENTAL_FIRE, zh ? "高级发射" : "advanced fire"),
				budget.permitsExperimental(nodes) ? 0xFFCC9DFF : 0xFFFF7777);
		appendWrapped(lines, font, width,
				(zh ? "数据能力策略: " : "Data capability policies: ")
						+ policyLabel(SpellCapability.ORIGIN_TARGET, zh ? "原点目标" : "origin target") + "  "
						+ policyLabel(SpellCapability.SIZED_PROJECTILE, zh ? "尺寸" : "size") + "  "
						+ policyLabel(SpellCapability.LONG_LIFETIME, zh ? "长寿命" : "long lifetime") + "  "
						+ policyLabel(SpellCapability.TARGET_COORDINATE, zh ? "目标坐标" : "target xyz") + "  "
						+ policyLabel(SpellCapability.TRACKING_MOVER, zh ? "追踪Mover" : "tracking mover"),
				0xFFB7C9D6);
		if (nodes.operatorOnlyNodes() > 0 || nodes.deniedNodes() > 0) {
			appendWrapped(lines, font, width,
					(zh ? "不可认证: OP " : "Not certifiable: OP ") + nodes.operatorOnlyNodes()
							+ (zh ? "  禁用 " : "  denied ") + nodes.deniedNodes(), 0xFFFF7777);
		}
		// 抢救出来的坏节点也是 denied 的一种，单列一行以便和「故意禁用的节点」区分。
		if (nodes.brokenNodes() > 0) {
			appendWrapped(lines, font, width,
					(zh ? "损坏节点: " : "Broken nodes: ") + nodes.brokenNodes()
							+ (zh ? "  (需修复后才能认证/导出)" : "  (fix before certify/export)"),
					0xFFFF7777);
		}
	}

	private void appendMetric(List<Line> lines, Font font, int width, String label, long current, long limit) {
		appendWrapped(lines, font, width, label + ": " + formatBudget(current) + " / " + formatBudget(limit),
				current > limit ? 0xFFFF7777 : 0xFF9DECF9);
	}

	private static String formatBudget(long value) {
		if (value >= 1_000_000_000L) return String.format(Locale.ROOT, "%.2fB", value / 1_000_000_000.0);
		if (value >= 1_000_000L) return String.format(Locale.ROOT, "%.2fM", value / 1_000_000.0);
		if (value >= 1_000L) return String.format(Locale.ROOT, "%.2fK", value / 1_000.0);
		return Long.toString(value);
	}

	private static String capabilityPair(SpecialNodeCounter.Summary nodes, SpellDraftBudget budget,
			SpellCapability capability, String label) {
		int grant = budget.legacyExperimentalQuota() > 0
				? budget.legacyExperimentalQuota() : budget.experimentalGrant(capability);
		return label + " " + nodes.experimentalCount(capability) + "/" + grant;
	}

	private static String policyLabel(SpellCapability capability, String label) {
		String policy = dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapabilityPolicies
				.currentPolicy(capability).name().toLowerCase(Locale.ROOT);
		return label + "=" + policy;
	}

	private SpellDraftBudget findActiveBudget() {
		var player = Minecraft.getInstance().player;
		if (player == null) return SpellDraftBudget.defaults();
		SpellDraftBudget blank = null;
		var definitionId = scene.getDefinition().id;
		for (var stack : player.getInventory().items) {
			if (!(stack.getItem() instanceof DynamicSpellItem) || DynamicSpellItem.isComplete(stack)
					|| dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator.isCertified(stack)) {
				continue;
			}
			var bound = DynamicSpellItem.getSpellId(stack);
			if (bound != null && bound.equals(definitionId)) return DynamicSpellItem.getDraftBudget(stack);
			if (bound == null && blank == null) blank = DynamicSpellItem.getDraftBudget(stack);
		}
		return blank != null ? blank : SpellDraftBudget.defaults();
	}

	private void appendWrapped(List<Line> output, Font font, int width, String text, int color) {
		for (var seq : font.split(Component.literal(text), width)) {
			output.add(new Line(seq, color));
		}
	}

	private void renderScrollbar(GuiGraphics graphics, int contentY, int contentHeight, int totalHeight, int maxScroll) {
		int sbX = x + w - 6;
		int trackH = contentHeight - 2;
		int thumbH = Math.max(10, trackH * contentHeight / Math.max(contentHeight, totalHeight));
		int thumbY = contentY + 1 + (trackH - thumbH) * scrollOffset / maxScroll;
		graphics.fill(sbX, contentY, sbX + 4, contentY + contentHeight, 0x33FFFFFF);
		graphics.fill(sbX + 1, thumbY, sbX + 3, thumbY + thumbH, 0x889DECF9);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !isMouseOver(mouseX, mouseY)) return false;
		int maxScroll = maxScroll();
		if (maxScroll > 0) {
			int sbX = x + w - 6;
			if (mouseX >= sbX && mouseX < sbX + 4) {
				scrollbarDragging = true;
				updateScrollbarDrag(mouseY, maxScroll);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (scrollbarDragging && button == 0) {
			updateScrollbarDrag(mouseY, maxScroll());
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (scrollbarDragging && button == 0) {
			scrollbarDragging = false;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (!isMouseOver(mouseX, mouseY)) return false;
		scrollOffset -= (int) (delta * 30);
		scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset));
		return true;
	}

	private int maxScroll() {
		if (cachedLines == null) return 0;
		Font font = Minecraft.getInstance().font;
		int lineStep = font.lineHeight + 2;
		int contentHeight = Math.max(0, h - PADDING * 2);
		return Math.max(0, cachedLines.size() * lineStep - contentHeight);
	}

	private void updateScrollbarDrag(double mouseY, int maxScroll) {
		if (maxScroll <= 0 || cachedLines == null) return;
		Font font = Minecraft.getInstance().font;
		int lineStep = font.lineHeight + 2;
		int contentY = y + PADDING;
		int contentHeight = Math.max(0, h - PADDING * 2);
		int totalHeight = cachedLines.size() * lineStep;
		int trackH = contentHeight - 2;
		int thumbH = Math.max(10, trackH * contentHeight / Math.max(contentHeight, totalHeight));
		int thumbTravel = trackH - thumbH;
		if (thumbTravel <= 0) return;
		double relY = mouseY - (contentY + 1) - thumbH / 2.0;
		double ratio = Math.max(0, Math.min(1, relY / thumbTravel));
		scrollOffset = (int) (ratio * maxScroll);
	}

	private static String formatNumber(double value) {
		if (!Double.isFinite(value)) {
			return Double.toString(value);
		}
		if (Math.abs(value) < 0.0000005) {
			return "0";
		}
		double abs = Math.abs(value);
		if (abs >= 100000 || abs < 0.001) {
			return String.format(Locale.ROOT, "%.4e", value);
		}
		String text = String.format(Locale.ROOT, "%.4f", value);
		while (text.endsWith("0")) {
			text = text.substring(0, text.length() - 1);
		}
		if (text.endsWith(".")) {
			text = text.substring(0, text.length() - 1);
		}
		return text;
	}

	private record Line(net.minecraft.util.FormattedCharSequence text, int color) {}
}
