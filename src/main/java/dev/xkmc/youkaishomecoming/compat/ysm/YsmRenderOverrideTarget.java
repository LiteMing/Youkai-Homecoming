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

	/** Only server-saved bindings are knowable here; client resource defaults need an explicit model ID. */
	default String currentYsmModel() {
		if (!getYsmModelOverride().isEmpty()) return getYsmModelOverride();
		if (this instanceof net.minecraft.world.entity.Entity entity && entity.getServer() != null) {
			var data = YsmOverrideData.get(entity.getServer());
			var binding = data.getEntityOverrides().get(entity.getUUID());
			if (binding == null) binding = data.getTypeOverrides().get(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
			if (binding != null && binding.enabled()) return binding.modelId();
		}
		return "";
	}

	default YsmModelProfile ysmProfile(String model) {
		if (this instanceof net.minecraft.world.entity.Entity entity && entity.getServer() != null)
			return YsmProfileData.get(entity.getServer()).entry(model).profile();
		throw new IllegalArgumentException("Shared preset queries require a server entity or isolated preview");
	}

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
