package dev.xkmc.youkaishomecoming.content.spell.feedback;

import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Server-authoritative sink: resolves coordinates, clamps values, and targets players. */
public final class ServerFeedbackSink implements SpellFeedbackSink {
	private final CardHolder holder;
	@Nullable private final SpellHitContext hitContext;
	private int emitted;
	public ServerFeedbackSink(CardHolder holder, @Nullable SpellHitContext hitContext) {
		this.holder = holder;
		this.hitContext = hitContext;
	}
	@Override public void playSound(SoundCue cue) {
		if (cue == null || !allow() || !(holder.self().level() instanceof ServerLevel level)) return;
		Vec3 pos = resolvePosition(cue.origin(), cue.position());
		if (pos == null) return;
		double radius = cue.radius() > 0 ? Math.min(cue.radius(), YHModConfig.COMMON.feedbackMaxRadius.get())
				: YHModConfig.COMMON.feedbackMaxRadius.get();
		SoundCue resolved = new SoundCue(cue.soundId(), cue.source(), cue.origin(), pos,
				cue.volume(), cue.pitch(), radius, cue.attenuation());
		for (var player : level.players()) if (player.distanceToSqr(pos) <= radius * radius)
			ServerFeedbackDispatcher.enqueue(level, player, resolved);
	}
	@Override public void cameraShake(CameraShakeCue cue) {
		if (cue == null || !allow() || !(holder.self().level() instanceof ServerLevel level)) return;
		Vec3 pos = resolvePosition(cue.origin(), cue.position());
		if (pos == null) return;
		var common = YHModConfig.COMMON;
		double intensity = Math.min(Math.max(0, cue.intensity()), common.feedbackMaxCameraIntensity.get());
		int duration = Math.min(Math.max(1, cue.duration()), common.feedbackMaxCameraDurationTicks.get());
		double radius = cue.radius() <= 0 ? common.feedbackMaxRadius.get()
				: Math.min(cue.radius(), common.feedbackMaxRadius.get());
		CameraShakeCue resolved = new CameraShakeCue(cue.origin(), pos, intensity, duration,
				cue.frequency(), radius, cue.falloff(), cue.channel());
		for (var player : level.players()) if (player.distanceToSqr(pos) <= radius * radius)
			ServerFeedbackDispatcher.enqueue(level, player, resolved);
	}
	private boolean allow() { return emitted++ < YHModConfig.COMMON.feedbackMaxCuesPerContext.get(); }
	@Nullable private Vec3 resolvePosition(CueOrigin origin, @Nullable Vec3 explicit) {
		if (explicit != null) return explicit;
		if (origin == CueOrigin.HIT && hitContext != null) return hitContext.hitPosition();
		if (origin == CueOrigin.TARGET) {
			LivingEntity target = holder.targetEntity();
			if (target != null) return target.position().add(0, target.getBbHeight() * 0.5, 0);
		}
		return holder.center();
	}
}
