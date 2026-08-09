package dev.xkmc.youkaishomecoming.client.screen;

import dev.xkmc.youkaishomecoming.content.spell.certification.network.CertificationClientHandler;
import dev.xkmc.youkaishomecoming.content.spell.certification.network.CertificationQuoteRequestToServer;
import dev.xkmc.youkaishomecoming.content.spell.certification.network.CertificationStartRequestToServer;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Server certification dialog (design doc §5.2, §18): duration and arena
 * selection via sliders bound to the configured min/max range, firm quote
 * display (server-authoritative) and start confirmation. The client never
 * declares success; every parameter is re-clamped server-side and the
 * definition is resolved from the quote cache (quoteId only).
 */
public class CertificationScreen extends Screen {

	private static final int DURATION_STEP_TICKS = 100;
	private static final int ARENA_STEP_BLOCKS = 1;

	private final SpellDefinition definition;
	private int durationTicks;
	private double halfSize;
	private String status = "";

	public CertificationScreen(SpellDefinition definition) {
		super(Component.literal("Spell Certification"));
		this.definition = definition;
		this.durationTicks = clampInt(YHModConfig.COMMON.certificationMinDurationTicks.get(),
				YHModConfig.COMMON.certificationMaxDurationTicks.get(), 1200);
		this.halfSize = clampInt(YHModConfig.COMMON.certificationMinArenaHalfSize.get(),
				YHModConfig.COMMON.certificationMaxArenaHalfSize.get(), 8);
	}

	private static int clampInt(int min, int max, int value) {
		return Math.max(min, Math.min(max, value));
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = 40;

		int minDur = YHModConfig.COMMON.certificationMinDurationTicks.get();
		int maxDur = Math.max(minDur, YHModConfig.COMMON.certificationMaxDurationTicks.get());
		addRenderableWidget(new AbstractSliderButton(cx - 160, y, 320, 20,
				Component.literal(""), durationProgress(minDur, maxDur, durationTicks)) {
			@Override
			protected void updateMessage() {
				durationTicks = sliderValue(minDur, maxDur, DURATION_STEP_TICKS, this.value);
				setMessage(Component.literal(String.format(Locale.ROOT, "Duration: %.0fs (%d ticks)",
						durationTicks / 20.0, durationTicks)));
			}

			@Override
			protected void applyValue() {
				durationTicks = sliderValue(minDur, maxDur, DURATION_STEP_TICKS, this.value);
				status = "duration: " + durationTicks + " ticks";
			}
		});
		y += 24;

		int minArena = YHModConfig.COMMON.certificationMinArenaHalfSize.get();
		int maxArena = Math.max(minArena, YHModConfig.COMMON.certificationMaxArenaHalfSize.get());
		addRenderableWidget(new AbstractSliderButton(cx - 160, y, 320, 20,
				Component.literal(""), arenaProgress(minArena, maxArena, halfSize)) {
			@Override
			protected void updateMessage() {
				halfSize = sliderValue(minArena, maxArena, ARENA_STEP_BLOCKS, this.value);
				setMessage(Component.literal(String.format(Locale.ROOT, "Arena half-size: %.0f blocks", halfSize)));
			}

			@Override
			protected void applyValue() {
				halfSize = sliderValue(minArena, maxArena, ARENA_STEP_BLOCKS, this.value);
				status = "arena: " + halfSize;
			}
		});
		y += 34;

		addRenderableWidget(Button.builder(Component.literal("Request Quote"),
						b -> requestQuote()).bounds(cx - 120, y, 110, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Start Certification"),
						b -> startCertification()).bounds(cx + 14, y, 110, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(Component.literal("Cancel"),
						b -> onClose()).bounds(cx - 40, y, 80, 20).build());
	}

	/** Slider position (0..1) for a tick value stepped by DURATION_STEP_TICKS. */
	private static double durationProgress(int min, int max, int ticks) {
		if (max <= min) return 0;
		int steppedMin = min - (min % DURATION_STEP_TICKS);
		return (ticks - steppedMin) / (double) (max - steppedMin);
	}

	private static double arenaProgress(int min, int max, double half) {
		if (max <= min) return 0;
		return (half - min) / (double) (max - min);
	}

	/** Convert a slider value (0..1) to a stepped int within [min, max]. */
	private static int sliderValue(int min, int max, int step, double value) {
		int raw = min + (int) Math.round(value * (max - min));
		if (step > 1) {
			raw = min + Math.round((float) (raw - min) / step) * step;
		}
		return clampInt(min, max, raw);
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
			// Info panel: timeout, break HP (each hit -1s), final cast duration of
			// the certified item (reward curve on break HP) and the cost breakdown.
			String durationLine = String.format(Locale.ROOT,
					"Timeout: %ds  |  Break HP: %ds (hit -1s)  |  Final cast: %ds  |  Arena: %.0f",
					quote.durationTicks / 20, quote.breakHpSeconds,
					quote.rewardDurationTicks / 20, quote.arenaHalfSize);
			String costLine = String.format(Locale.ROOT,
					"Cost: start %d (≈%d XP) / cast %d / issue %d  |  maxSpawn/tick: %d",
					quote.startCostUnits, xpLevels(quote.startCostUnits), quote.castCostUnits,
					quote.issueCostUnits, quote.maxSpawnPerTick);
			gui.drawCenteredString(this.font, Component.literal(durationLine), cx, 140, 0xFFA0FFA0);
			gui.drawCenteredString(this.font, Component.literal(costLine), cx, 152, 0xFFA0FFA0);
		}
	}

	/** Rough XP-level conversion at the default experience rate (20 units per level). */
	private static long xpLevels(long units) {
		return Math.max(0, (units + 19) / 20);
	}
}
