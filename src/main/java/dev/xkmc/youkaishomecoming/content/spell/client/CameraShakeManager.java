package dev.xkmc.youkaishomecoming.content.spell.client;

import dev.xkmc.youkaishomecoming.content.spell.feedback.CameraShakeCue;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-only visual shake state; it never writes player rotation.
 *
 * <p>Camera shake is applied from Forge's camera-angle event rather than
 * {@code GameRenderer#bobView}.  The latter is skipped when the player turns
 * vanilla View Bob off, which made this feedback silently disappear.</p>
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID,
		bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CameraShakeManager {
	private static final Map<String, ActiveShake> ACTIVE = new HashMap<>();
	private static long tick;

	/* Keep the requested intensity in cue units while producing a visible,
	 * bounded angle offset.  The server still owns the intensity cap. */
	private static final float YAW_DEGREES_PER_INTENSITY = 1.8f;
	private static final float PITCH_DEGREES_PER_INTENSITY = 1.35f;
	private static final float ROLL_DEGREES_PER_INTENSITY = 2.1f;

	private CameraShakeManager() {
	}

	public static void add(CameraShakeCue cue) {
		if (cue == null || !YHModConfig.CLIENT.feedbackCameraShakeEnabled.get()) return;
		var player = Minecraft.getInstance().player;
		if (player == null) return;
		double distance = player.position().distanceTo(cue.position() == null ? player.position() : cue.position());
		double attenuation = 1;
		if (cue.radius() > 0) {
			double ratio = Mth.clamp(distance / cue.radius(), 0, 1);
			attenuation = switch (cue.falloff()) {
				case NONE -> 1;
				case LINEAR -> 1 - ratio;
				case QUADRATIC -> (1 - ratio) * (1 - ratio);
			};
		}
		float strength = (float) (cue.intensity() * attenuation * YHModConfig.CLIENT.feedbackCameraShakeScale.get());
		if (strength <= 0) return;
		ActiveShake old = ACTIVE.get(cue.channel());
		if (old == null) {
			ACTIVE.put(cue.channel(), new ActiveShake(strength, cue.duration(), cue.frequency(), tick));
		} else {
			old.intensity = Math.max(old.intensity, strength);
			old.remaining = Math.max(old.remaining, cue.duration());
			old.frequency = Math.max(old.frequency, cue.frequency());
		}
	}

	public static void tick() {
		tick++;
		// Keep a cue alive for its final render frame.  Client packets can arrive
		// immediately before the tick boundary; removing a one-tick cue here
		// would otherwise make it invisible altogether.
		ACTIVE.values().removeIf(s -> --s.remaining < 0);
	}

	/** Clear effects when the client leaves a world/server. */
	public static void clear() {
		ACTIVE.clear();
		tick = 0;
	}

	@SubscribeEvent
	public static void cameraShake(ViewportEvent.ComputeCameraAngles event) {
		if (ACTIVE.isEmpty() || !YHModConfig.CLIENT.feedbackCameraShakeEnabled.get()) return;
		float yaw = 0, pitch = 0, roll = 0;
		float partialTick = (float) event.getPartialTick();
		for (ActiveShake shake : ACTIVE.values()) {
			double elapsed = tick - shake.startTick + partialTick;
			// A short fade-in avoids a discontinuity when a packet arrives mid-frame;
			// remaining provides a smooth tail without per-frame random noise.
			float fadeIn = Mth.clamp((float) (elapsed / 2.0), 0, 1);
			float fadeOut = Mth.clamp((shake.remaining + 1) / 8.0f, 0, 1);
			float envelope = fadeIn * fadeOut;
			if (envelope <= 0) continue;
			double phase = elapsed * shake.frequency * 0.35;
			yaw += (float) (Math.sin(phase * 1.11 + 0.7) * shake.intensity * envelope
					* YAW_DEGREES_PER_INTENSITY);
			pitch += (float) (Math.sin(phase * 0.83 + 2.1) * shake.intensity * envelope
					* PITCH_DEGREES_PER_INTENSITY);
			roll += (float) (Math.sin(phase * 0.97 + 4.2) * shake.intensity * envelope
					* ROLL_DEGREES_PER_INTENSITY);
		}
		float option = Minecraft.getInstance().options.fovEffectScale().get().floatValue();
		event.setYaw(event.getYaw() + Mth.clamp(yaw, -3.0f, 3.0f) * option);
		event.setPitch(event.getPitch() + Mth.clamp(pitch, -2.25f, 2.25f) * option);
		event.setRoll(event.getRoll() + Mth.clamp(roll, -3.5f, 3.5f) * option);
	}

	private static final class ActiveShake {
		float intensity;
		int remaining;
		double frequency;
		final long startTick;

		private ActiveShake(float intensity, int remaining, double frequency, long startTick) {
			this.intensity = intensity;
			this.remaining = remaining;
			this.frequency = frequency;
			this.startTick = startTick;
		}
	}
}
