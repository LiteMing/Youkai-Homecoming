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

	private static final int ARENA_STEP_BLOCKS = 1;

	private final SpellDefinition definition;
	private int durationTicks;
	private double halfSize;
	private String status = "";
	private net.minecraft.client.gui.components.EditBox durationBox;
	private net.minecraft.client.gui.components.EditBox hpBox;

	public CertificationScreen(SpellDefinition definition) {
		super(Component.literal("Spell Certification"));
		this.definition = definition;
		// the spell's declared duration is the certification timeout; the player
		// can adjust duration and HP right here (overriding the definition for
		// this certification — the definition is sent as full JSON to the server)
		this.durationTicks = Math.max(20, definition.itemForm.duration());
		this.halfSize = YHModConfig.COMMON.certificationFixedArenaHalfSize.get();
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = 34;

		durationBox = new net.minecraft.client.gui.components.EditBox(this.font,
				cx - 160, y, 150, 20, Component.literal("Duration"));
		durationBox.setMaxLength(8);
		durationBox.setValue(String.valueOf(Math.max(20, definition.itemForm.duration())));
		durationBox.setResponder(s -> {
			try {
				durationTicks = Math.max(20, Integer.parseInt(s.trim()));
			} catch (NumberFormatException ignored) {
				// keep last valid value
			}
		});
		addRenderableWidget(durationBox);
		addRenderableWidget(Button.builder(Component.literal("Duration (ticks)"),
						b -> {
						}).bounds(cx - 10, y, 120, 20).build());

		hpBox = new net.minecraft.client.gui.components.EditBox(this.font,
				cx - 160, y + 24, 150, 20, Component.literal("Spell HP"));
		hpBox.setMaxLength(8);
		hpBox.setValue(String.valueOf(Math.max(1, definition.itemForm.hp())));
		hpBox.setResponder(s -> {
			try {
				definition.itemForm = definition.itemForm.withHp(Math.max(1, Integer.parseInt(s.trim())));
			} catch (NumberFormatException ignored) {
				// keep last valid value
			}
		});
		addRenderableWidget(hpBox);
		addRenderableWidget(Button.builder(Component.literal("Spell HP"),
						b -> {
						}).bounds(cx - 10, y + 24, 120, 20).build());
		y += 60;

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
		// apply the typed duration, then send the full definition JSON so the
		// server quotes against the adjusted duration/HP
		definition.itemForm = definition.itemForm.withDuration(Math.max(20, durationTicks));
		String json;
		try {
			var encoded = dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition.CODEC
					.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, definition)
					.getOrThrow(false, s -> {});
			json = new com.google.gson.Gson().toJson(encoded);
		} catch (Exception e) {
			status = "encode failed: " + e.getMessage();
			return;
		}
		YoukaisHomecoming.HANDLER.toServer(new CertificationQuoteRequestToServer(json, durationTicks, halfSize));
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
					"Timeout: %ds  |  Spell HP: %d  |  Final cast: %ds  |  Arena: %.0f",
					quote.durationTicks / 20, quote.spellHp,
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
