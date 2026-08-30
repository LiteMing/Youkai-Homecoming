package dev.xkmc.youkaishomecoming.client.screen;

import dev.xkmc.youkaishomecoming.content.spell.certification.network.CertificationClientHandler;
import dev.xkmc.youkaishomecoming.content.spell.certification.network.CertificationQuoteRequestToServer;
import dev.xkmc.youkaishomecoming.content.spell.certification.network.CertificationStartRequestToServer;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.client.Minecraft;
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
	private final Screen parentScreen;
	private Component status = Component.empty();

	public CertificationScreen(SpellDefinition definition) {
		this(definition, null);
	}

	public CertificationScreen(SpellDefinition definition, @org.jetbrains.annotations.Nullable Screen parentScreen) {
		super(Component.translatable("youkaishomecoming.cert.screen.title"));
		this.definition = definition;
		this.parentScreen = parentScreen;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = 34;

		addRenderableWidget(Button.builder(Component.translatable(
						"youkaishomecoming.cert.screen.health_source"), b -> {
						}).bounds(cx - 160, y, 320, 20).build());
		y += 34;

		// the arena half size is fixed by config (UI selection is ignored)
		double halfSize = YHModConfig.COMMON.certificationFixedArenaHalfSize.get();
		addRenderableWidget(Button.builder(Component.translatable(
						"youkaishomecoming.cert.screen.arena_fixed", String.format(Locale.ROOT, "%.0f", halfSize)),
						b -> {
						}).bounds(cx - 160, y, 320, 20).build());
		y += 34;

		addRenderableWidget(Button.builder(Component.translatable("youkaishomecoming.cert.screen.request_quote"),
						b -> requestQuote()).bounds(cx - 120, y, 110, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("youkaishomecoming.cert.screen.start"),
						b -> startCertification()).bounds(cx + 14, y, 110, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(Component.translatable("youkaishomecoming.cert.screen.cancel"),
						b -> onClose()).bounds(cx - 40, y, 80, 20).build());
	}

	private void requestQuote() {
		// Only the server-owned spell id crosses the wire. The server applies the
		// configured duration bounds and reads the canonical action tree itself.
		YoukaisHomecoming.HANDLER.toServer(new CertificationQuoteRequestToServer(definition));
		status = Component.translatable("youkaishomecoming.cert.screen.quote_requested");
	}

	private void startCertification() {
		var quote = CertificationClientHandler.getPendingQuote();
		if (quote == null) {
			status = Component.translatable("youkaishomecoming.cert.screen.no_quote");
			return;
		}

		// 检查本地是否已有既有快照，若有则直接提交开始认证
		String safeId = dev.xkmc.youkaishomecoming.client.render.SpellCardTextureCache.toStorageKey(definition.id.toString());
		java.nio.file.Path file = Minecraft.getInstance().gameDirectory.toPath()
				.resolve("spell_snapshots").resolve(safeId + ".png");
		if (java.nio.file.Files.isRegularFile(file)) {
			try {
				byte[] snap = java.nio.file.Files.readAllBytes(file);
				YoukaisHomecoming.HANDLER.toServer(new CertificationStartRequestToServer(quote.quoteId, snap));
				CertificationClientHandler.clearPendingQuote();
				onClose();
				return;
			} catch (Exception ignored) {
			}
		}

		// 本地尚无快照，进入视口取景拍照流程
		if (parentScreen instanceof dev.xkmc.youkaishomecoming.content.spell.preview.SpellPreviewScreen previewScreen) {
			previewScreen.getViewport().setCardFrameGuideActive(true);
			byte[] snap = dev.xkmc.youkaishomecoming.content.spell.preview.SpellSnapshotRenderer.captureSnapshot(
					previewScreen.getScene(), previewScreen.getViewport(), 0);
			if (snap != null && snap.length > 0) {
				Minecraft.getInstance().setScreen(
						new SpellCardSnapshotConfirmScreen(previewScreen, snap, () -> {
							previewScreen.getViewport().setCardFrameGuideActive(false);
							saveConfirmedSnapshot(snap);
							YoukaisHomecoming.HANDLER.toServer(new CertificationStartRequestToServer(quote.quoteId, snap));
							CertificationClientHandler.clearPendingQuote();
						}));
				return;
			}
		}

		// 兜底直接开始
		YoukaisHomecoming.HANDLER.toServer(new CertificationStartRequestToServer(quote.quoteId, new byte[0]));
		CertificationClientHandler.clearPendingQuote();
		onClose();
	}

	private void saveConfirmedSnapshot(byte[] snapBytes) {
		if (snapBytes == null || snapBytes.length == 0 || definition == null) return;
		try {
			java.nio.file.Path outDir = Minecraft.getInstance().gameDirectory.toPath().resolve("spell_snapshots");
			java.nio.file.Files.createDirectories(outDir);
			String safeId = dev.xkmc.youkaishomecoming.client.render.SpellCardTextureCache.toStorageKey(definition.id.toString());
			java.nio.file.Path fileById = outDir.resolve(safeId + ".png");
			java.nio.file.Files.write(fileById, snapBytes);
			String defHash = dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHash.canonicalHash(definition);
			java.nio.file.Path fileByHash = outDir.resolve(defHash + ".png");
			java.nio.file.Files.write(fileByHash, snapBytes);
			dev.xkmc.youkaishomecoming.client.render.SpellCardTextureCache.registerTexture(definition.id.toString(), snapBytes);
			dev.xkmc.youkaishomecoming.client.render.SpellCardTextureCache.registerTexture(defHash, snapBytes);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		renderBackground(gui);
		super.render(gui, mouseX, mouseY, partialTick);
		int cx = this.width / 2;
		gui.drawCenteredString(this.font, this.title, cx, 12, 0xFFFFFFFF);
		gui.drawCenteredString(this.font, status, cx, 120, 0xFFFFFFAA);
		var quote = CertificationClientHandler.getPendingQuote();
		if (quote != null) {
			// Info panel: fixed timeout/HP from the spell definition, final cast
			// duration of the certified item (reward curve) and the cost breakdown.
			Component durationLine = Component.translatable("youkaishomecoming.cert.screen.summary",
					quote.durationTicks, quote.spellHp, quote.rewardDurationTicks,
					String.format(Locale.ROOT, "%.2f", quote.durationTicks <= 0 ? 0.0
							: quote.rewardDurationTicks / (double) quote.durationTicks),
					String.format(Locale.ROOT, "%.0f", quote.arenaHalfSize));
			Component costLine = Component.translatable("youkaishomecoming.cert.screen.cost",
					quote.startCostUnits, String.format(Locale.ROOT, "%.1f", quote.castCostUnits / 100.0),
					xpLevels(quote.castCostUnits), quote.issueCostUnits, quote.maxSpawnPerTick);
			Component nodeLine = Component.translatable("youkaishomecoming.cert.screen.nodes",
					quote.ordinaryNodes, quote.freeNodeCount,
					Math.max(0, quote.ordinaryNodes - quote.freeNodeCount), quote.nodeCostUnits,
					quote.experimentalNodes, quote.operatorOnlyNodes);
			Component performanceLine = Component.translatable("youkaishomecoming.cert.screen.performance",
					quote.maxSpawnPerTick, quote.maxSpawnBudget,
					quote.peakAliveUpperBound, quote.maxPeakBudget);
			Component workLine = Component.translatable("youkaishomecoming.cert.screen.performance_work",
					quote.projectileTicks, quote.maxProjectileTicksBudget,
					quote.hookExecutionUpperBound, quote.maxHookExecutionsBudget);
			gui.drawCenteredString(this.font, durationLine, cx, 140, 0xFFA0FFA0);
			gui.drawCenteredString(this.font, costLine, cx, 152, 0xFFA0FFA0);
			gui.drawCenteredString(this.font, nodeLine, cx, 164, 0xFFFFD36B);
			gui.drawCenteredString(this.font, performanceLine, cx, 176, 0xFF9DECF9);
			gui.drawCenteredString(this.font, workLine, cx, 188, 0xFF9DECF9);
		}
	}

	/** Rough XP-level conversion at the default experience rate (20 units per level). */
	private static long xpLevels(long units) {
		return Math.max(0, (units + 19) / 20);
	}
}
