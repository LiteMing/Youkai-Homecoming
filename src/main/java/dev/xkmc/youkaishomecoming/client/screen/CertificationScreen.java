package dev.xkmc.youkaishomecoming.client.screen;

import dev.xkmc.youkaishomecoming.content.spell.certification.network.CertificationClientHandler;
import dev.xkmc.youkaishomecoming.content.spell.certification.network.CertificationQuoteRequestToServer;
import dev.xkmc.youkaishomecoming.content.spell.certification.network.CertificationStartRequestToServer;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Server certification dialog (design doc §5.2, §18): read-only health-plan
 * totals, firm quote display (server-authoritative) and start confirmation. The client never
 * declares success; every parameter is re-clamped server-side and the
 * definition is resolved from the quote cache (quoteId only).
 */
public class CertificationScreen extends Screen {

	private final SpellDefinition definition;
	private double halfSize;
	private String status = "";

	public CertificationScreen(SpellDefinition definition) {
		super(Component.literal("Spell Certification"));
		this.definition = definition;
		this.halfSize = YHModConfig.COMMON.certificationFixedArenaHalfSize.get();
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = 34;

		addRenderableWidget(Button.builder(Component.literal(
						"Health / timeout: read from set_spell_health"), b -> {
						}).bounds(cx - 160, y, 320, 20).build());
		y += 34;

		// the arena half size is fixed by config (UI selection is ignored)
		halfSize = YHModConfig.COMMON.certificationFixedArenaHalfSize.get();
		addRenderableWidget(Button.builder(Component.literal(
						String.format(Locale.ROOT, "Arena half-size: %.0f blocks (fixed)", halfSize)),
						b -> {
						}).bounds(cx - 160, y, 320, 20).build());
		y += 34;

		addRenderableWidget(Button.builder(Component.literal("Request Quote"),
						b -> requestQuote()).bounds(cx - 120, y, 110, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Start Certification"),
						b -> startCertification()).bounds(cx + 14, y, 110, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(Component.literal("Cancel"),
						b -> onClose()).bounds(cx - 40, y, 80, 20).build());
	}

	private void requestQuote() {
		// Only the server-owned spell id crosses the wire. The server applies the
		// configured duration bounds and reads the canonical action tree itself.
		YoukaisHomecoming.HANDLER.toServer(new CertificationQuoteRequestToServer(
				definition, 0, halfSize));
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
			// Info panel: fixed timeout/HP from the spell definition, final cast
			// duration of the certified item (reward curve) and the cost breakdown.
			String durationLine = String.format(Locale.ROOT,
					"Timeout: %dt  |  Spell HP: %d  |  Final cast: %dt (ratio %.2f)  |  Arena: %.0f",
				quote.durationTicks, quote.spellHp,
					quote.rewardDurationTicks, quote.durationTicks <= 0 ? 0.0
							: quote.rewardDurationTicks / (double) quote.durationTicks, quote.arenaHalfSize);
			String costLine = String.format(Locale.ROOT,
					"Cost: start %d / cast %.1f BOMB or %d XP lv / issue %d  |  maxSpawn/tick: %d",
					quote.startCostUnits, quote.castCostUnits / 100.0, xpLevels(quote.castCostUnits),
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
