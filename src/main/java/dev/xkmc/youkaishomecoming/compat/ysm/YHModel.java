package dev.xkmc.youkaishomecoming.compat.ysm;

import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Shared command/Java/KubeJS entry point. See docs-dev/yhmodel-script-api.md.
 * Live mutations must run on the server thread. This API does not require OYSM on the server.
 * Acceptance means that a request was synchronized, not that every client has the requested asset.
 */
public final class YHModel {

	private YHModel() { }

	public static boolean supports(Object target) {
		return target instanceof YsmRenderOverrideTarget;
	}

	public static int defaultDuration() {
		return Math.min(YHModConfig.COMMON.modelPresentationDefaultTicks.get(), YHModConfig.COMMON.modelPresentationMaxTicks.get());
	}

	/** Plays one exact clip in LOOP mode. Repeating this call explicitly restarts that clip. */
	public static void play(YsmRenderOverrideTarget target, String clip, int ticks) {
		target.setYsmPresentation(animationRequest(target, clip, ticks, YsmPresentationState.Source.SCRIPT));
	}

	public static void stop(YsmRenderOverrideTarget target) {
		target.setYsmPresentation(currentForMutation(target).stop());
	}

	public static void setParameter(YsmRenderOverrideTarget target, String name, double value, int ticks) {
		target.setYsmPresentation(parameterRequest(target, name, value, ticks, YsmPresentationState.Source.SCRIPT));
	}

	public static void clearParameter(YsmRenderOverrideTarget target, String name) {
		target.setYsmPresentation(currentForMutation(target).clearParameter(name));
	}

	public static void clearParameters(YsmRenderOverrideTarget target) {
		target.setYsmPresentation(currentForMutation(target).clearParameters());
	}

	/** Clears only new presentation requests, not model/texture bindings or legacy ysm_render fields. */
	public static void clear(YsmRenderOverrideTarget target) {
		target.setYsmPresentation(currentForMutation(target).stop().clearParameters());
	}

	public static String getAnimation(YsmRenderOverrideTarget target) {
		var animation = target.getYsmPresentation().animation();
		return animation != null && animation.active(target.getYsmPresentationTime()) ? animation.clip() : "";
	}

	/** 0 = no request, -1 = until explicitly cleared, otherwise remaining server ticks. */
	public static long getAnimationTicksRemaining(YsmRenderOverrideTarget target) {
		var animation = target.getYsmPresentation().animation();
		long now = target.getYsmPresentationTime();
		return animation == null || !animation.active(now) ? 0 : YsmPresentationState.remaining(animation.expiresAt(), now);
	}

	/** The requested override, not a client model's underlying/default value. */
	@Nullable
	public static Float getParameter(YsmRenderOverrideTarget target, String name) {
		var parameter = target.getYsmPresentation().parameters().get(YsmPresentationState.normalizeParameter(name));
		return parameter != null && parameter.active(target.getYsmPresentationTime()) ? parameter.value() : null;
	}

	/** The server's registered groups, not the client's native wheel/catalog. */
	public static List<String> listPresets(YsmRenderOverrideTarget target, String model) {
		return profile(target, model).presets().keySet().stream().sorted().toList();
	}

	public static List<String> listModels(YsmRenderOverrideTarget target) {
		currentForMutation(target);
		if (!(target instanceof Entity entity) || entity.getServer() == null) throw new IllegalArgumentException("Shared preset queries require a server entity");
		return YsmProfileData.get(entity.getServer()).entries().keySet().stream().sorted().toList();
	}

	public static String describePreset(YsmRenderOverrideTarget target, String model, String id) {
		return preset(target, model, id).description();
	}

	public static String profileJson(YsmRenderOverrideTarget target, String model) { return profile(target, model).toJson(); }

	/** Uses the registered default duration. State-trigger mappings ignore this duration for persistent states. */
	public static void applyPreset(YsmRenderOverrideTarget target, String model, String id) {
		applyPreset(target, model, id, preset(target, model, id).ticks());
	}

	public static void applyPreset(YsmRenderOverrideTarget target, String model, String id, int ticks) {
		target.setYsmPresentation(presetRequest(target, model, id, ticks, YsmPresentationState.Source.SCRIPT));
	}

	static YsmPresentationState presetRequest(YsmRenderOverrideTarget target, String model, String id, int ticks, YsmPresentationState.Source source) {
		var preset = preset(target, model, id);
		validatePreset(preset);
		if (ticks == -1) ticks = preset.ticks();
		checkDuration(ticks);
		return currentForMutation(target).applyPreset(model, preset, target.getYsmPresentationTime(), ticks,
				source, YHModConfig.COMMON.modelPresentationMaxParameters.get());
	}

	private static YsmModelProfile profile(YsmRenderOverrideTarget target, String model) {
		currentForMutation(target);
		if (!(target instanceof Entity entity) || entity.getServer() == null) throw new IllegalArgumentException("Shared preset queries require a server entity");
		return YsmProfileData.get(entity.getServer()).entry(model).profile();
	}

	private static YsmModelProfile.Preset preset(YsmRenderOverrideTarget target, String model, String id) {
		var value = profile(target, model).presets().get(YsmModelProfile.presetId(id));
		if (value == null) throw new IllegalArgumentException("Unknown preset: " + id);
		return value;
	}

	static void validatePreset(YsmModelProfile.Preset preset) {
		checkDuration(preset.ticks());
		if (preset.parameters().size() > YHModConfig.COMMON.modelPresentationMaxParameters.get()) throw new IllegalArgumentException("Too many preset parameters");
		for (float value : preset.parameters().values())
			if (Math.abs(value) > YHModConfig.COMMON.modelPresentationMaxParameterValue.get()) throw new IllegalArgumentException("Preset parameter exceeds maxParameterValue");
	}

	static YsmPresentationState currentForMutation(YsmRenderOverrideTarget target) {
		if (target == null || !target.canMutateYsmPresentation()) {
			throw new IllegalArgumentException("Model presentation mutations require a server-thread YH entity or an isolated preview");
		}
		if (target instanceof net.minecraft.world.entity.Entity entity && entity.isRemoved()) {
			throw new IllegalArgumentException("Model presentation target has been removed");
		}
		return target.getYsmPresentation().expire(target.getYsmPresentationTime());
	}

	static YsmPresentationState animationRequest(YsmRenderOverrideTarget target, String clip, int ticks, YsmPresentationState.Source source) {
		checkDuration(ticks);
		return currentForMutation(target).play(clip, target.getYsmPresentationTime(), ticks, source);
	}

	static YsmPresentationState parameterRequest(YsmRenderOverrideTarget target, String name, double value, int ticks, YsmPresentationState.Source source) {
		checkDuration(ticks);
		if (!Double.isFinite(value) || Math.abs(value) > YHModConfig.COMMON.modelPresentationMaxParameterValue.get()) {
			throw new IllegalArgumentException("Model parameter is not finite or exceeds maxParameterValue");
		}
		return currentForMutation(target).setParameter(name, (float) value, target.getYsmPresentationTime(), ticks, source,
				YHModConfig.COMMON.modelPresentationMaxParameters.get());
	}

	private static void checkDuration(int ticks) {
		if (ticks < 0 || ticks > YHModConfig.COMMON.modelPresentationMaxTicks.get()) {
			throw new IllegalArgumentException("Duration must be 0 (until cleared) or at most " + YHModConfig.COMMON.modelPresentationMaxTicks.get() + " ticks");
		}
	}
}
