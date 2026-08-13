package dev.xkmc.fastprojectileapi.spellcircle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.xkmc.youkaishomecoming.content.capability.PvpDanmakuStatusOverlay;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.entity.youkai.SpellCertificationEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.SpellProgressColor;
import dev.xkmc.youkaishomecoming.content.spell.certification.network.CertificationClientHandler;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** World-space spell HP/time progress rings shared by bosses, certification and PVP. */
public final class SpellProgressCircleRenderer {

	private static final ResourceLocation TEX = new ResourceLocation("youkaishomecoming",
			"textures/entities/spell_circle.png");
	private static final int SEGMENTS = 96;
	private static final float HP_RADIUS = 52;
	private static final float HP_OUTLINE_Z = 0.040f;
	private static final float HP_BACKGROUND_Z = 0.045f;
	private static final float HP_PROGRESS_Z = 0.050f;
	private static final float HP_MARKER_Z = 0.055f;
	private static final float HP_MARKER_SIZE = 2.0f;

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
		SpellComponent.Stroke hpOutline = stroke(HP_RADIUS, 2.8f, HP_OUTLINE_Z, outlineColor(entity));
		renderHealthProgress(handle, hpOutline, progress, true);
		SpellComponent.Stroke hpBackground = stroke(HP_RADIUS, 2.0f, HP_BACKGROUND_Z, "0x55445555");
		renderHealthProgress(handle, hpBackground, progress, true);
		SpellComponent.Stroke hp = stroke(HP_RADIUS, 1.6f, HP_PROGRESS_Z, "0xFFFFFFFF");
		renderHealthProgress(handle, hp, progress, false);
		renderHealthMarkers(handle, progress);
		if (progress.durationTicks() > 0) {
			SpellComponent.Stroke timeBackground = stroke(46, 1.6f, 0.05f, "0x55445555");
			timeBackground.render(handle);
			SpellComponent.Stroke time = stroke(46, 1.6f, 0.05f, "0xFF55D9FF");
			renderRemainingProgress(handle, time, progress.timeRatio(pTick));
		}
		pose.popPose();
	}

	private static String outlineColor(Entity entity) {
		int rgb = SpellProgressColor.outlineRgb(entity, 0xFF3344);
		return String.format(Locale.ROOT, "0xFF%06X", rgb);
	}

	private static SpellComponent.Stroke stroke(float radius, float width, float z, String color) {
		SpellComponent.Stroke stroke = new SpellComponent.Stroke();
		stroke.vertex = SEGMENTS;
		stroke.cycle = -1;
		stroke.radius = radius;
		stroke.width = width;
		stroke.angle = (float) Math.PI / 2;
		stroke.z = z;
		stroke.color = color;
		return stroke;
	}

	private static void renderHealthProgress(SpellComponent.RenderHandle handle,
			SpellComponent.Stroke stroke, Progress progress, boolean background) {
		int[] healthSegments = progress.healthSegments();
		int segmentCount = healthSegments.length > 0 ? healthSegments.length : progress.segmentCount();
		if (segmentCount <= 1) {
			renderRemainingProgress(handle, stroke, background ? 1 : progress.healthRatio());
			return;
		}
		int count = Math.max(1, segmentCount);
		long totalHealth = 0;
		if (healthSegments.length >= count) {
			for (int i = 0; i < count; i++) totalHealth += Math.max(0, healthSegments[i]);
		}
		if (totalHealth <= 0) totalHealth = count;
		long cursor = 0;
		double depleted = Math.max(0, Math.min(totalHealth,
				(long) progress.completedHealth()
						+ Math.max(0, progress.segmentMaxHealth() - progress.health())));
		for (int i = 0; i < count; i++) {
			long size = healthSegments.length >= count ? Math.max(0, healthSegments[i]) : 1;
			float rawStart = cursor / (float) totalHealth;
			cursor += size;
			float rawEnd = cursor / (float) totalHealth;
			if (background) {
				stroke.renderProgressRange(handle, rawStart, rawEnd);
				continue;
			}
			double depletedInSegment = Math.max(0, Math.min(size,
					depleted - (cursor - size)));
			float remainingStart = rawStart + (float) (depletedInSegment / totalHealth);
			stroke.renderProgressRange(handle, remainingStart, rawEnd);
		}
	}

	/** Small square phase boundaries overlay the continuous HP ring without erasing progress. */
	private static void renderHealthMarkers(SpellComponent.RenderHandle handle, Progress progress) {
		int[] segments = progress.healthSegments();
		if (segments.length <= 1) return;
		long total = 0;
		for (int segment : segments) total += Math.max(0, segment);
		if (total <= 0) return;
		SpellComponent.Stroke marker = stroke(HP_RADIUS, HP_MARKER_SIZE, HP_MARKER_Z, "0xFFFFD83D");
		float halfMarker = HP_MARKER_SIZE / ((float) Math.PI * 2 * HP_RADIUS) / 2;
		long cursor = Math.max(0, segments[0]);
		for (int i = 1; i < segments.length; i++) {
			float boundary = cursor / (float) total;
			renderWrappedRange(handle, marker, boundary - halfMarker, boundary + halfMarker);
			cursor += Math.max(0, segments[i]);
		}
	}

	private static void renderWrappedRange(SpellComponent.RenderHandle handle,
			SpellComponent.Stroke stroke, float start, float end) {
		if (start < 0) {
			stroke.renderProgressRange(handle, start + 1, 1);
			stroke.renderProgressRange(handle, 0, end);
		} else if (end > 1) {
			stroke.renderProgressRange(handle, start, 1);
			stroke.renderProgressRange(handle, 0, end - 1);
		} else {
			stroke.renderProgressRange(handle, start, end);
		}
	}

	private static void renderRemainingProgress(SpellComponent.RenderHandle handle,
			SpellComponent.Stroke stroke, float ratio) {
		float clamped = Math.max(0, Math.min(1, ratio));
		stroke.renderProgressRange(handle, 1 - clamped, 1);
	}

	@Nullable
	private static Progress resolve(Entity entity) {
		if (entity instanceof SpellCertificationEntity) {
			var state = CertificationClientHandler.getState(entity.getId());
			if (state == null || !state.active()) return null;
			return new Progress(state.healthLeft(), state.healthTotal(), state.segmentMaxHealth(),
					state.elapsedTicks(), state.targetTicks(), state.receivedTick(),
					state.completedHealth(), state.healthSegments().length, state.healthSegments());
		}
		if (entity instanceof Player) {
			if (entity == Minecraft.getInstance().player) {
				var trial = CertificationClientHandler.getMyState();
				if (trial != null && trial.active()) {
					return new Progress(trial.healthLeft(), trial.healthTotal(), trial.segmentMaxHealth(),
							trial.elapsedTicks(), trial.targetTicks(), trial.receivedTick(),
							trial.completedHealth(), trial.healthSegments().length, trial.healthSegments());
				}
				var own = GrazeCapability.HOLDER.get((Player) entity).getPlayerSpellStatus();
				if (own.active()) {
					return new Progress(own.health(), totalHealth(own.healthSegments()), own.maxHealth(),
							own.elapsedTicks(), own.durationTicks(), 0, own.completedHealth(),
							own.healthSegments().length, own.healthSegments());
				}
			}
			var state = PvpDanmakuStatusOverlay.spellProgress(entity.getId());
			if (state == null) return null;
			return new Progress(state.health(), totalHealth(state.healthSegments()), state.maxHealth(),
					state.elapsedTicks(), state.durationTicks(), state.receivedTick(),
					state.completedHealth(), state.healthSegments().length, state.healthSegments());
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

	private static int totalHealth(int[] segments) {
		long total = 0;
		for (int segment : segments) total += Math.max(0, segment);
		return (int) Math.min(Integer.MAX_VALUE, total);
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

		float timeRatio(float pTick) {
			if (durationTicks <= 0) return 0;
			float elapsed = elapsedTicks;
			if (receivedTick > 0) {
				var player = Minecraft.getInstance().player;
				if (player != null) elapsed += Math.max(0, player.tickCount - receivedTick) + pTick;
			}
			return Math.max(0, Math.min(1, 1 - elapsed / durationTicks));
		}
	}
}
