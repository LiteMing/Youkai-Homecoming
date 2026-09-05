package dev.xkmc.youkaishomecoming.compat.ysm;

import java.util.Locale;

public interface YsmRenderOverrideTarget {

	int YSM_CLEAR_MODEL = 1;
	int YSM_CLEAR_TEXTURE = 2;
	int YSM_CLEAR_ANIMATION = 4;
	int YSM_CLEAR_ALL = YSM_CLEAR_MODEL | YSM_CLEAR_TEXTURE | YSM_CLEAR_ANIMATION;

	void setYsmRenderOverride(String modelId, String textureName, String animationHint, int duration, String clearTarget);

	default void clearYsmRenderOverride() {
		clearYsmRenderOverride("all");
	}

	void clearYsmRenderOverride(String target);

	boolean hasYsmRenderOverride();

	String getYsmModelOverride();

	String getYsmTextureOverride();

	String getYsmAnimationOverride();

	int getYsmOverrideTicksRemaining();

	String describeYsmRenderOverride();

	YsmPresentationState getYsmPresentation();

	void setYsmPresentation(YsmPresentationState state);

	/** World game time for live entities; the isolated simulation clock for previews. */
	long getYsmPresentationTime();

	YsmPresentationSignals getYsmSignals();

	void setYsmSignals(YsmPresentationSignals signals);

	default boolean canMutateYsmPresentation() {
		return this instanceof net.minecraft.world.entity.Entity entity && !entity.level().isClientSide()
				&& entity.getServer() != null && entity.getServer().isSameThread();
	}

	default void expireYsmPresentation() {
		YsmPresentationState current = getYsmPresentation();
		YsmPresentationState next = current.expire(getYsmPresentationTime());
		if (next != current) setYsmPresentation(next);
		YsmPresentationRuntime.tick(this);
	}

	static String normalizeYsmOverride(String value) {
		return value == null ? "" : value.trim();
	}

	static int changedMask(String model, String texture, String animation) {
		int mask = 0;
		if (!model.isBlank()) {
			mask |= YSM_CLEAR_MODEL;
		}
		if (!texture.isBlank()) {
			mask |= YSM_CLEAR_TEXTURE;
		}
		if (!animation.isBlank()) {
			mask |= YSM_CLEAR_ANIMATION;
		}
		return mask;
	}

	static int clearMask(String target, int changedMask) {
		return switch (normalizeYsmOverride(target).toLowerCase(Locale.ROOT)) {
			case "", "changed" -> changedMask;
			case "model" -> YSM_CLEAR_MODEL;
			case "texture" -> YSM_CLEAR_TEXTURE;
			case "animation", "anim" -> YSM_CLEAR_ANIMATION;
			case "model_texture", "model+texture", "render" -> YSM_CLEAR_MODEL | YSM_CLEAR_TEXTURE;
			case "all" -> YSM_CLEAR_ALL;
			default -> changedMask;
		};
	}
}
