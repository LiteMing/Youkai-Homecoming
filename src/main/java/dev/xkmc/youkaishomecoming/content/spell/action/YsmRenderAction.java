package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.compat.ysm.YsmRenderOverrideTarget;
import dev.xkmc.youkaishomecoming.compat.ysm.YHModel;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import net.minecraft.util.StringRepresentable;

/** Scenario actions share YHModel requests with scripts and the isolated spell preview. */
public record YsmRenderAction(Operation operation, String model, String texture, String preset,
		String clip, String parameter, float value, int duration) implements SpellAction {

	public enum Operation implements StringRepresentable {
		MODEL, PRESET, ANIMATION, PARAMETER, CLEAR, RESET_MODEL;
		public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
		public static final Codec<Operation> CODEC = StringRepresentable.fromEnum(Operation::values);
	}

	public static final Codec<YsmRenderAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Operation.CODEC.fieldOf("operation").forGetter(YsmRenderAction::operation),
			Codec.STRING.optionalFieldOf("model", "").forGetter(YsmRenderAction::model),
			Codec.STRING.optionalFieldOf("texture", "").forGetter(YsmRenderAction::texture),
			Codec.STRING.optionalFieldOf("preset", "").forGetter(YsmRenderAction::preset),
			Codec.STRING.optionalFieldOf("clip", "").forGetter(YsmRenderAction::clip),
			Codec.STRING.optionalFieldOf("parameter", "").forGetter(YsmRenderAction::parameter),
			Codec.FLOAT.optionalFieldOf("value", 0f).forGetter(YsmRenderAction::value),
			Codec.intRange(-1, Integer.MAX_VALUE).optionalFieldOf("duration", -1).forGetter(YsmRenderAction::duration)
	).apply(i, YsmRenderAction::new));

	public static YsmRenderAction empty() {
		return new YsmRenderAction(Operation.PRESET, "", "", "", "", "", 0, -1);
	}

	public YsmRenderAction {
		model = normalize(model);
		texture = normalize(texture);
		preset = normalize(preset);
		clip = normalize(clip);
		parameter = normalize(parameter);
		if (!Float.isFinite(value) || duration < -1) throw new IllegalArgumentException("Invalid YSM action value or duration");
	}

	@Override
	public void execute(SpellContext ctx) {
		YsmRenderOverrideTarget target = null;
		if (ctx.self() instanceof YsmRenderOverrideTarget selfTarget) {
			target = selfTarget;
		} else if (ctx.holder() instanceof YsmRenderOverrideTarget holderTarget) {
			target = holderTarget;
		}
		if (target == null || !target.canMutateYsmPresentation()) return;
		if (duration > dev.xkmc.youkaishomecoming.init.data.YHModConfig.COMMON.modelPresentationMaxTicks.get())
			throw new IllegalArgumentException("YSM action duration exceeds configured maximum");
		int ticks = duration < 0 ? YHModel.defaultDuration() : duration;
		switch (operation) {
			case MODEL -> {
				if (!model.isEmpty()) target.setYsmRenderOverride(model, texture.isEmpty() ? "default" : texture, "", Math.max(0, duration), "model_texture");
			}
			case PRESET -> {
				String scope = model.isEmpty() ? target.currentYsmModel() : model;
				// Missing/shared definitions may arrive later on a preview client. They must not abort the spell.
				if (!scope.isEmpty() && !preset.isEmpty() && target.ysmProfile(scope).presets().containsKey(preset))
					YHModel.applyPreset(target, scope, preset, duration);
			}
			case ANIMATION -> { if (!clip.isEmpty()) YHModel.play(target, clip, ticks); }
			case PARAMETER -> { if (!parameter.isEmpty()) YHModel.setParameter(target, parameter, value, ticks); }
			case CLEAR -> { YHModel.clear(target); target.clearYsmRenderOverride("animation"); }
			case RESET_MODEL -> target.clearYsmRenderOverride("model_texture");
		}
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	public YsmRenderAction withOperation(Operation op) { return new YsmRenderAction(op, model, texture, preset, clip, parameter, value, duration); }
	public YsmRenderAction withModel(String v) { return new YsmRenderAction(operation, v, texture, preset, clip, parameter, value, duration); }
	public YsmRenderAction withTexture(String v) { return new YsmRenderAction(operation, model, v, preset, clip, parameter, value, duration); }
	public YsmRenderAction withPreset(String v) { return new YsmRenderAction(operation, model, texture, v, clip, parameter, value, duration); }
	public YsmRenderAction withClip(String v) { return new YsmRenderAction(operation, model, texture, preset, v, parameter, value, duration); }
	public YsmRenderAction withParameter(String v) { return new YsmRenderAction(operation, model, texture, preset, clip, v, value, duration); }
	public YsmRenderAction withValue(float v) { return new YsmRenderAction(operation, model, texture, preset, clip, parameter, v, duration); }
	public YsmRenderAction withDuration(int v) { return new YsmRenderAction(operation, model, texture, preset, clip, parameter, value, v); }
}
