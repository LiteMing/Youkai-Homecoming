package dev.xkmc.youkaishomecoming.client.screen;

import dev.xkmc.youkaishomecoming.content.spell.certification.network.CertificationClientHandler;
import dev.xkmc.youkaishomecoming.content.spell.certification.network.CertificationQuoteRequestToServer;
import dev.xkmc.youkaishomecoming.content.spell.certification.network.CertificationStartRequestToServer;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Server certification dialog (design doc §5.2, §18): duration and arena
 * selection, firm quote display (server-authoritative) and start confirmation.
 * The client never declares success; every parameter is re-clamped server-side
 * and the definition is resolved from the quote cache (quoteId only).
 */
public class CertificationScreen extends Screen {

	private static final int[] DURATION_PRESETS = {600, 1200, 2400};
	private static final int[] ARENA_PRESETS = {6, 8, 12, 16};

	private final SpellDefinition definition;
	private int durationTicks = 1200;
	private double halfSize = 8;
	private String status = "";

	public CertificationScreen(SpellDefinition definition) {
		super(Component.literal("Spell Certification"));
		this.definition = definition;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = 40;
		addRenderableWidget(Button.builder(Component.literal("Duration: 30s"),
						b -> selectDuration(600)).bounds(cx - 160, y, 74, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Duration: 60s"),
						b -> selectDuration(1200)).bounds(cx - 80, y, 74, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Duration: 120s"),
						b -> selectDuration(2400)).bounds(cx, y, 76, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Custom Ticks"),
						b -> selectDuration(Math.max(600, Math.min(6000, durationTicks))))
				.bounds(cx + 82, y, 78, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(Component.literal("Arena 6"),
						b -> selectArena(6)).bounds(cx - 160, y, 74, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Arena 8"),
						b -> selectArena(8)).bounds(cx - 80, y, 74, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Arena 12"),
						b -> selectArena(12)).bounds(cx, y, 76, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Arena 16"),
						b -> selectArena(16)).bounds(cx + 82, y, 78, 20).build());
		y += 34;
		addRenderableWidget(Button.builder(Component.literal("Request Quote"),
						b -> requestQuote()).bounds(cx - 120, y, 110, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Start Certification"),
						b -> startCertification()).bounds(cx + 14, y, 110, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(Component.literal("Cancel"),
						b -> onClose()).bounds(cx - 40, y, 80, 20).build());
	}

	private void selectDuration(int ticks) {
		durationTicks = ticks;
		status = "duration: " + ticks + " ticks";
	}

	private void selectArena(double half) {
		halfSize = half;
		status = "arena: " + half;
	}

	private void requestQuote() {
		YoukaisHomecoming.HANDLER.toServer(new CertificationQuoteRequestToServer(definition, durationTicks, halfSize));
		status = "quote requested...";
	}

	private void startCertification() {
		var quote = CertificationClientHandler.getPendingQuote();
		if (quote == null) {
			status = "no quote yet";
			return;
		}
		YoukaisHomecoming.HANDLER.toServer(new CertificationStartRequestToServer(quote.quoteId));
		CertificationClientHandler.clearPendingQuote();
		onClose();
	}

	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		renderBackground(gui);
		super.render(gui, mouseX, mouseY, partialTick);
		int cx = this.width / 2;
		gui.drawCenteredString(this.font, this.title, cx, 12, 0xFFFFFFFF);
		gui.drawCenteredString(this.font, Component.literal(status), cx, 120, 0xFFFFFFAA);
		var quote = CertificationClientHandler.getPendingQuote();
		if (quote != null) {
			String q = String.format(Locale.ROOT,
					"cost=%d  duration=%ds  arena=%.0f  maxSpawn/tick=%d  hash=%s...",
					quote.startCostUnits, quote.durationTicks / 20, quote.arenaHalfSize,
					quote.maxSpawnPerTick, quote.definitionHash.substring(0, Math.min(8, quote.definitionHash.length())));
			gui.drawCenteredString(this.font, Component.literal("Quote: " + q), cx, 140, 0xFFA0FFA0);
		}
	}
}
