package dev.xkmc.fastprojectileapi.spellcircle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.xkmc.youkaishomecoming.content.capability.PvpDanmakuStatusOverlay;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.entity.youkai.SpellCertificationEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.certification.network.CertificationClientHandler;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** World-space spell HP/time progress rings shared by bosses, certification and PVP. */
public final class SpellProgressCircleRenderer {

	private static final ResourceLocation TEX = new ResourceLocation("youkaishomecoming",
			"textures/entities/spell_circle.png");
	private static final int SEGMENTS = 96;

	private SpellProgressCircleRenderer() {
	}

	public static void render(PoseStack pose, MultiBufferSource buffer, int light,
							 Entity entity, float pTick, float alpha) {
		Progress progress = resolve(entity);
		if (progress == null || alpha <= 0.01f) return;
		VertexConsumer builder = buffer.getBuffer(SpellRenderState.getSpell(TEX));
		SpellComponent.RenderHandle handle = new SpellComponent.RenderHandle(
				pose, buffer, builder, entity.tickCount + pTick, light);
		handle.alpha = alpha;
		pose.pushPose();
		SpellComponent.Stroke hpBackground = stroke(52, 3.0f, "0x55330000");
		renderHealthProgress(handle, hpBackground, progress, true);
		SpellComponent.Stroke hp = stroke(52, 3.0f, "0xFFFF3344");
		renderHealthProgress(handle, hp, progress, false);
		if (progress.durationTicks() > 0) {
			SpellComponent.Stroke timeBackground = stroke(46, 1.6f, "0x55445555");
			timeBackground.render(handle);
			SpellComponent.Stroke time = stroke(46, 1.6f, "0xFF55D9FF");
			time.renderProgress(handle, progress.timeRatio(entity, pTick));
		}
		pose.popPose();
	}

	private static SpellComponent.Stroke stroke(float radius, float width, String color) {
		SpellComponent.Stroke stroke = new SpellComponent.Stroke();
		stroke.vertex = SEGMENTS;
		stroke.cycle = 1;
		stroke.radius = radius;
		stroke.width = width;
		stroke.angle = (float) -Math.PI / 2;
		stroke.z = 0.05f;
		stroke.color = color;
		return stroke;
	}

	private static void renderHealthProgress(SpellComponent.RenderHandle handle,
			SpellComponent.Stroke stroke, Progress progress, boolean background) {
		int[] healthSegments = progress.healthSegments();
		int segmentCount = healthSegments.length > 0 ? healthSegments.length : progress.segmentCount();
		if (segmentCount <= 1) {
			stroke.renderProgress(handle, background ? 1 : progress.healthRatio());
			return;
		}
		int count = Math.min(32, Math.max(1, segmentCount));
		long totalHealth = 0;
		if (healthSegments.length >= count) {
			for (int i = 0; i < count; i++) totalHealth += Math.max(0, healthSegments[i]);
		}
		if (totalHealth <= 0) totalHealth = count;
		long cursor = 0;
		for (int i = 0; i < count; i++) {
			long size = healthSegments.length >= count ? Math.max(0, healthSegments[i]) : 1;
			float rawStart = cursor / (float) totalHealth;
			cursor += size;
			float rawEnd = cursor / (float) totalHealth;
			float gap = Math.min(Math.min(0.018f, 0.24f / count), (rawEnd - rawStart) * 0.2f);
			float segmentStart = rawStart + gap;
			float segmentEnd = rawEnd - gap;
			float segmentProgress = background ? 1 : i + 1 < count ? 0 : progress.currentHealthRatio();
			float end = segmentStart + (segmentEnd - segmentStart) * segmentProgress;
			stroke.renderProgressRange(handle, segmentStart, end);
		}
	}

	@Nullable
	private static Progress resolve(Entity entity) {
		if (entity instanceof SpellCertificationEntity) {
			var state = CertificationClientHandler.getState(entity.getId());
			if (state == null || !state.active()) return null;
			return new Progress(state.healthLeft(), state.healthTotal(),
					state.elapsedTicks(), state.targetTicks(), 0, 0, 1);
		}
		if (entity instanceof Player) {
			if (entity == Minecraft.getInstance().player) {
				var trial = CertificationClientHandler.getMyState();
				if (trial != null && trial.active()) {
					return new Progress(trial.healthLeft(), trial.healthTotal(), trial.elapsedTicks(),
							trial.targetTicks(), 0, 0, 1);
				}
				var own = GrazeCapability.HOLDER.get((Player) entity).getPlayerSpellStatus();
				if (own.active()) {
					return new Progress(own.health(), own.maxHealth(), own.elapsedTicks(), own.durationTicks(), 0, 0, 1);
				}
			}
			var state = PvpDanmakuStatusOverlay.spellProgress(entity.getId());
			if (state == null) return null;
			return new Progress(state.health(), state.maxHealth(), state.elapsedTicks(),
					state.durationTicks(), 0, 0, 1);
		}
		if (entity instanceof YoukaiEntity youkai && youkai.clientSpellMaxHealth > 0
				&& youkai.clientInDanmakuCombat) {
			int total = youkai.clientSpellHealthTotal > 0 ? youkai.clientSpellHealthTotal : youkai.clientSpellMaxHealth;
			return new Progress(youkai.getHealth(), total,
				youkai.clientSpellMaxHealth, youkai.clientSpellElapsedTicks, youkai.clientSpellDurationTicks,
				youkai.clientSpellStateReceivedTick, youkai.clientSpellHealthCompleted,
				youkai.clientSpellHealthSegmentCount, youkai.clientSpellHealthSegments);
		}
		return null;
	}

	private record Progress(float health, float maxHealth, float segmentMaxHealth,
							int elapsedTicks, int durationTicks, int receivedTick,
							int completedHealth, int segmentCount, int[] healthSegments) {
		private Progress(float health, float maxHealth, int elapsedTicks, int durationTicks,
						  int receivedTick, int completedHealth, int segmentCount) {
			this(health, maxHealth, maxHealth, elapsedTicks, durationTicks, receivedTick,
					completedHealth, segmentCount, new int[0]);
		}

		float healthRatio() {
			if (maxHealth <= 0) return 0;
			return Math.max(0, Math.min(1, health / maxHealth));
		}

		float currentHealthRatio() {
			if (segmentMaxHealth <= 0) return healthRatio();
			return Math.max(0, Math.min(1, health / segmentMaxHealth));
		}

		float timeRatio(Entity entity, float pTick) {
			if (durationTicks <= 0) return 0;
			int elapsed = elapsedTicks;
			if (receivedTick > 0) {
				elapsed += Math.max(0, entity.tickCount - receivedTick);
			}
			return Math.max(0, Math.min(1, 1 - elapsed / (float) durationTicks));
		}
	}
}
