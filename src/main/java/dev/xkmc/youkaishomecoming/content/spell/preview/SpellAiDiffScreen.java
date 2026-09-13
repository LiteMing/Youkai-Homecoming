package dev.xkmc.youkaishomecoming.content.spell.preview;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Read-only comparison. Copy buttons always use the untouched request/result text. */
final class SpellAiDiffScreen extends Screen {
	private static final int TOP = 60;
	private final Screen parent;
	private final SpellAiComparison comparison;
	private final List<SpellAiComparison.Change> changes;
	private final List<FormattedCharSequence> lines = new ArrayList<>();
	private double scroll;

	SpellAiDiffScreen(Screen parent, SpellAiComparison comparison) {
		super(text("diff_title"));
		this.parent = parent;
		this.comparison = comparison;
		this.changes = comparison.changes();
	}

	@Override protected void init() {
		lines.clear();
		if (changes.isEmpty()) addLine(text("diff_unchanged"));
		for (var change : changes) {
			String prefix = change.before() == null ? "+ " : change.after() == null ? "- " : "~ ";
			addLine(Component.literal(prefix + change.path()).withStyle(ChatFormatting.YELLOW));
			if (change.before() != null) for (String line : change.before().split("\n", -1)) addLine(Component.literal("- " + line).withStyle(ChatFormatting.RED));
			if (change.after() != null) for (String line : change.after().split("\n", -1)) addLine(Component.literal("+ " + line).withStyle(ChatFormatting.GREEN));
			addLine(Component.empty());
		}
		int buttonWidth = Math.min(140, (width - 36) / 3);
		int left = (width - buttonWidth * 3 - 12) / 2;
		addRenderableWidget(Button.builder(text("copy_original"), button -> {
			Minecraft.getInstance().keyboardHandler.setClipboard(comparison.before());
			button.setMessage(text("copied"));
		}).bounds(left, height - 26, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(text("copy_result"), button -> {
			Minecraft.getInstance().keyboardHandler.setClipboard(comparison.after());
			button.setMessage(text("copied"));
		}).bounds(left + buttonWidth + 6, height - 26, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
				.bounds(left + (buttonWidth + 6) * 2, height - 26, buttonWidth, 20).build());
		scroll = Mth.clamp(scroll, 0, maxScroll());
	}

	private void addLine(Component line) {
		var wrapped = font.split(line, Math.max(40, width - 44));
		if (wrapped.isEmpty()) lines.add(FormattedCharSequence.EMPTY);
		else lines.addAll(wrapped);
	}

	@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);
		graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
		graphics.drawCenteredString(font, text("diff_baseline"), width / 2, 28, 0xAAAAAA);
		long added = changes.stream().filter(c -> c.before() == null).count();
		long removed = changes.stream().filter(c -> c.after() == null).count();
		graphics.drawCenteredString(font, Component.translatable("youkaishomecoming.spell_editor.ai.diff_counts",
				added, removed, changes.size() - added - removed), width / 2, 42, 0xCCCCCC);
		int bottom = Math.max(TOP, height - 36);
		graphics.fill(12, TOP - 4, width - 12, bottom, 0xBB111111);
		graphics.enableScissor(16, TOP, width - 20, bottom);
		int lineHeight = font.lineHeight + 2;
		int first = (int) scroll / lineHeight;
		for (int i = first; i < lines.size(); i++) {
			int y = TOP + i * lineHeight - (int) scroll;
			if (y >= bottom) break;
			graphics.drawString(font, lines.get(i), 18, y, 0xFFFFFF, false);
		}
		graphics.disableScissor();
		if (maxScroll() > 0) {
			int track = bottom - TOP;
			int thumb = Math.max(8, track * track / Math.max(1, lines.size() * lineHeight));
			int y = TOP + (int) ((track - thumb) * scroll / maxScroll());
			graphics.fill(width - 17, y, width - 14, y + thumb, 0xFF888888);
		}
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		scroll = Mth.clamp(scroll - amount * (font.lineHeight + 2) * 3, 0, maxScroll());
		return true;
	}

	@Override public boolean keyPressed(int key, int scanCode, int modifiers) {
		double next = switch (key) {
			case GLFW.GLFW_KEY_UP -> scroll - font.lineHeight - 2;
			case GLFW.GLFW_KEY_DOWN -> scroll + font.lineHeight + 2;
			case GLFW.GLFW_KEY_PAGE_UP -> scroll - Math.max(1, height - TOP - 36);
			case GLFW.GLFW_KEY_PAGE_DOWN -> scroll + Math.max(1, height - TOP - 36);
			case GLFW.GLFW_KEY_HOME -> 0;
			case GLFW.GLFW_KEY_END -> maxScroll();
			default -> Double.NaN;
		};
		if (Double.isNaN(next)) return super.keyPressed(key, scanCode, modifiers);
		scroll = Mth.clamp(next, 0, maxScroll());
		return true;
	}

	private double maxScroll() { return Math.max(0, lines.size() * (font.lineHeight + 2) - Math.max(1, height - TOP - 36)); }
	private static Component text(String key) { return Component.translatable("youkaishomecoming.spell_editor.ai." + key); }
	@Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
