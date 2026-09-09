package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

/**
 * Optional, action-scoped presentation settings for any danmaku. This is
 * presentation data only: the server still owns the YH projectile, its hitbox
 * and its lifetime. The offsets are local to the projectile renderer and are
 * expressed in model units (forward, right, up).
 */
public record YsmProjectileConfig(
		ModelSource modelSource,
		String model,
		String slot,
		float modelScale,
		int maxInstances,
		Fallback fallback,
		boolean acknowledgeCost,
		float offsetForward,
		float offsetRight,
		float offsetUp,
		float pitchOffset,
		float yawOffset,
		float tiltOffset
) {

	/** Compatibility constructor for definitions written before local offsets. */
	public YsmProjectileConfig(ModelSource modelSource, String model, String slot,
			float modelScale, int maxInstances, Fallback fallback, boolean acknowledgeCost) {
		this(modelSource, model, slot, modelScale, maxInstances, fallback,
				acknowledgeCost, 0, 0, 0, 0, 0, 0);
	}

	/** Compatibility constructor for definitions written before orientation offsets. */
	public YsmProjectileConfig(ModelSource modelSource, String model, String slot,
			float modelScale, int maxInstances, Fallback fallback, boolean acknowledgeCost,
			float offsetForward, float offsetRight, float offsetUp) {
		this(modelSource, model, slot, modelScale, maxInstances, fallback, acknowledgeCost,
				offsetForward, offsetRight, offsetUp, 0, 0, 0);
	}

	public enum ModelSource implements StringRepresentable {
		FIXED("fixed");

		private final String name;
		ModelSource(String name) { this.name = name; }
		@Override public String getSerializedName() { return name; }
	}

	public enum Fallback implements StringRepresentable {
		YH("yh");

		private final String name;
		Fallback(String name) { this.name = name; }
		@Override public String getSerializedName() { return name; }
	}

	public static final Codec<YsmProjectileConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("model_source", ModelSource.FIXED.getSerializedName())
					.xmap(YsmProjectileConfig::sourceOf, ModelSource::getSerializedName).forGetter(YsmProjectileConfig::modelSource),
			Codec.STRING.optionalFieldOf("model", "").forGetter(YsmProjectileConfig::model),
			Codec.STRING.optionalFieldOf("slot", "arrow").forGetter(YsmProjectileConfig::slot),
			Codec.FLOAT.optionalFieldOf("model_scale", 1.0f).forGetter(YsmProjectileConfig::modelScale),
			Codec.INT.optionalFieldOf("max_instances", 8).forGetter(YsmProjectileConfig::maxInstances),
			Codec.STRING.optionalFieldOf("fallback", Fallback.YH.getSerializedName())
					.xmap(YsmProjectileConfig::fallbackOf, Fallback::getSerializedName).forGetter(YsmProjectileConfig::fallback),
			Codec.BOOL.optionalFieldOf("acknowledge_cost", false).forGetter(YsmProjectileConfig::acknowledgeCost),
			Codec.FLOAT.optionalFieldOf("offset_forward", 0.0f).forGetter(YsmProjectileConfig::offsetForward),
			Codec.FLOAT.optionalFieldOf("offset_right", 0.0f).forGetter(YsmProjectileConfig::offsetRight),
			Codec.FLOAT.optionalFieldOf("offset_up", 0.0f).forGetter(YsmProjectileConfig::offsetUp),
			Codec.FLOAT.optionalFieldOf("pitch_offset", 0.0f).forGetter(YsmProjectileConfig::pitchOffset),
			Codec.FLOAT.optionalFieldOf("yaw_offset", 0.0f).forGetter(YsmProjectileConfig::yawOffset),
			Codec.FLOAT.optionalFieldOf("tilt_offset", 0.0f).forGetter(YsmProjectileConfig::tiltOffset)
	).apply(i, YsmProjectileConfig::new));

	public YsmProjectileConfig {
		modelSource = modelSource == null ? ModelSource.FIXED : modelSource;
		model = model == null ? "" : model.trim();
		slot = slot == null || slot.isBlank() ? "arrow" : slot.trim().toLowerCase(java.util.Locale.ROOT);
		modelScale = Float.isFinite(modelScale) ? Math.max(0.01f, Math.min(32f, modelScale)) : 1.0f;
		maxInstances = Math.max(0, Math.min(128, maxInstances));
		fallback = fallback == null ? Fallback.YH : fallback;
		offsetForward = finiteOffset(offsetForward);
		offsetRight = finiteOffset(offsetRight);
		offsetUp = finiteOffset(offsetUp);
		pitchOffset = finiteAngle(pitchOffset);
		yawOffset = finiteAngle(yawOffset);
		tiltOffset = finiteAngle(tiltOffset);
	}

	public boolean enabled() {
		return modelSource == ModelSource.FIXED && !model.isBlank() && maxInstances > 0;
	}

	public YsmProjectileConfig withModel(String value) {
		return copy(value, slot, modelScale, maxInstances, acknowledgeCost, offsetForward, offsetRight, offsetUp,
				pitchOffset, yawOffset, tiltOffset);
	}

	public YsmProjectileConfig withSlot(String value) {
		return copy(model, value, modelScale, maxInstances, acknowledgeCost, offsetForward, offsetRight, offsetUp,
				pitchOffset, yawOffset, tiltOffset);
	}

	public YsmProjectileConfig withModelScale(float value) {
		return copy(model, slot, value, maxInstances, acknowledgeCost, offsetForward, offsetRight, offsetUp,
				pitchOffset, yawOffset, tiltOffset);
	}

	public YsmProjectileConfig withMaxInstances(int value) {
		return copy(model, slot, modelScale, value, acknowledgeCost, offsetForward, offsetRight, offsetUp,
				pitchOffset, yawOffset, tiltOffset);
	}

	public YsmProjectileConfig withAcknowledgeCost(boolean value) {
		return copy(model, slot, modelScale, maxInstances, value, offsetForward, offsetRight, offsetUp,
				pitchOffset, yawOffset, tiltOffset);
	}

	public YsmProjectileConfig withOffsets(float forward, float right, float up) {
		return copy(model, slot, modelScale, maxInstances, acknowledgeCost, forward, right, up,
				pitchOffset, yawOffset, tiltOffset);
	}

	public YsmProjectileConfig withPitchOffset(float value) {
		return copy(model, slot, modelScale, maxInstances, acknowledgeCost, offsetForward, offsetRight,
				offsetUp, value, yawOffset, tiltOffset);
	}

	public YsmProjectileConfig withYawOffset(float value) {
		return copy(model, slot, modelScale, maxInstances, acknowledgeCost, offsetForward, offsetRight,
				offsetUp, pitchOffset, value, tiltOffset);
	}

	public YsmProjectileConfig withTiltOffset(float value) {
		return copy(model, slot, modelScale, maxInstances, acknowledgeCost, offsetForward, offsetRight,
				offsetUp, pitchOffset, yawOffset, value);
	}

	public YsmProjectileConfig withOrientationOffsets(float pitch, float yaw, float tilt) {
		return copy(model, slot, modelScale, maxInstances, acknowledgeCost, offsetForward, offsetRight,
				offsetUp, pitch, yaw, tilt);
	}

	private YsmProjectileConfig copy(String model, String slot, float modelScale, int maxInstances,
			boolean acknowledgeCost, float offsetForward, float offsetRight, float offsetUp,
			float pitchOffset, float yawOffset, float tiltOffset) {
		return new YsmProjectileConfig(modelSource, model, slot, modelScale, maxInstances, fallback,
				acknowledgeCost, offsetForward, offsetRight, offsetUp, pitchOffset, yawOffset, tiltOffset);
	}

	private static float finiteOffset(float value) {
		return Float.isFinite(value) ? Math.max(-64.0f, Math.min(64.0f, value)) : 0.0f;
	}

	private static float finiteAngle(float value) {
		return Float.isFinite(value) ? Math.max(-180.0f, Math.min(180.0f, value)) : 0.0f;
	}

	private static ModelSource sourceOf(String value) {
		return ModelSource.FIXED.getSerializedName().equalsIgnoreCase(value) ? ModelSource.FIXED : ModelSource.FIXED;
	}

	private static Fallback fallbackOf(String value) {
		return Fallback.YH.getSerializedName().equalsIgnoreCase(value) ? Fallback.YH : Fallback.YH;
	}
}
