package dev.xkmc.youkaishomecoming.content.spell.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.xkmc.youkaishomecoming.content.spell.feedback.CameraShakeCue;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/** Client-only visual shake state; it never writes player rotation. */
public final class CameraShakeManager {
	private static final Map<String, ActiveShake> ACTIVE = new HashMap<>();
	private static long tick;
	private CameraShakeManager() {}
	public static void add(CameraShakeCue cue) {
		if (cue == null || !YHModConfig.CLIENT.feedbackCameraShakeEnabled.get()) return;
		var player = Minecraft.getInstance().player; if (player == null) return;
		double distance = player.position().distanceTo(cue.position() == null ? player.position() : cue.position());
		double attenuation = 1;
		if (cue.radius() > 0) {
			double ratio = Mth.clamp(distance / cue.radius(), 0, 1);
			attenuation = switch (cue.falloff()) {
				case NONE -> 1; case LINEAR -> 1 - ratio; case QUADRATIC -> (1 - ratio) * (1 - ratio);
			};
		}
		float strength = (float) (cue.intensity() * attenuation * YHModConfig.CLIENT.feedbackCameraShakeScale.get());
		if (strength <= 0) return;
		ActiveShake old = ACTIVE.get(cue.channel());
		if (old == null) ACTIVE.put(cue.channel(), new ActiveShake(strength, cue.duration(), cue.frequency(), tick));
		else { old.intensity = Math.max(old.intensity, strength); old.remaining = Math.max(old.remaining, cue.duration()); old.frequency = Math.max(old.frequency, cue.frequency()); }
	}
	public static void tick() { tick++; ACTIVE.values().removeIf(s -> --s.remaining <= 0); }
	public static void apply(PoseStack pose, float partialTick) {
		if (ACTIVE.isEmpty() || !YHModConfig.CLIENT.feedbackCameraShakeEnabled.get()) return;
		float x = 0, y = 0, z = 0;
		for (ActiveShake shake : ACTIVE.values()) {
			double time = tick - shake.startTick + partialTick; float envelope = Mth.clamp(shake.remaining / 8.0f, 0, 1);
			double phase = time * shake.frequency * 0.35;
			x += (float) (Math.sin(phase * 1.11 + 0.7) * shake.intensity * envelope * 0.035);
			y += (float) (Math.sin(phase * 0.83 + 2.1) * shake.intensity * envelope * 0.035);
			z += (float) (Math.sin(phase * 0.97 + 4.2) * shake.intensity * envelope * 0.02);
		}
		pose.translate(x, y, z); pose.mulPose(Axis.XP.rotationDegrees(y * 12)); pose.mulPose(Axis.YP.rotationDegrees(x * 12));
	}
	private static final class ActiveShake {
		float intensity; int remaining; double frequency; final long startTick;
		private ActiveShake(float intensity, int remaining, double frequency, long startTick) {
			this.intensity = intensity; this.remaining = remaining; this.frequency = frequency; this.startTick = startTick;
		}
	}
}
