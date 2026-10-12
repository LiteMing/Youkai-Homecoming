package dev.xkmc.youkaishomecoming.compat.ysm;

import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/** Client projection of YH business signals through OYSM's preset registry. */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID)
public final class YsmClientPresentation {

	private record Preview(Map<String, Float> parameters) { }
	private static final Map<LivingEntity, Preview> PREVIEWS = new WeakHashMap<>();

	private YsmClientPresentation() { }

	public static void preview(LivingEntity entity, Map<String, Float> parameters) {
		PREVIEWS.put(entity, new Preview(Map.copyOf(parameters)));
	}

	public static boolean isPreview(LivingEntity entity) { return PREVIEWS.containsKey(entity); }
	public static void forgetPreview(LivingEntity entity) { PREVIEWS.remove(entity); }
	public static void clear() { PREVIEWS.clear(); }

	public static YsmPresentationResolver.Resolved resolve(LivingEntity entity, String model) {
		Preview preview = PREVIEWS.get(entity);
		Map<String, Float> bindingParameters = Map.of();
		if (preview != null) bindingParameters = preview.parameters();
		else {
			var binding = YSMClientCompat.resolveBinding(entity);
			if (binding != null && YsmModelPaths.same(binding.modelId(), model)) bindingParameters = binding.parameters();
		}
		if (!(entity instanceof YsmRenderOverrideTarget target))
			return new YsmPresentationResolver.Resolved(null, Map.of(), false, false);
		var signals = target.getYsmSignals();
		if (entity instanceof YoukaiEntity youkai) {
			YsmPresentationSignals.Trigger state = !youkai.isBeaten() ? null : switch (youkai.getBeatenPhase()) {
				case YoukaiEntity.BEATEN_DEFEAT -> YsmPresentationSignals.Trigger.DEFEAT;
				case YoukaiEntity.BEATEN_FALLING -> YsmPresentationSignals.Trigger.FALLING;
				default -> YsmPresentationSignals.Trigger.PRONE;
			};
			if (state != null && state != signals.state()) signals = signals.advance(state, false,
					Math.max(0, target.getYsmPresentationTime() - youkai.getBeatenPhaseTicks()));
			else if (state == null && signals.state().beaten()) signals = signals.advance(YsmPresentationSignals.Trigger.IDLE, false, target.getYsmPresentationTime());
		}
		var resolved = YsmPresentationResolver.resolve(model, YsmPresetBridge.presets(model), signals,
				target.getYsmPresentation(), target.getYsmPresentationTime(), bindingParameters);
		if (!resolved.beaten() && resolved.body() != null && !resolved.body().explicit() && !YsmSpellHintClient.animationOverride(entity).isEmpty())
			return new YsmPresentationResolver.Resolved(null, resolved.parameters(), false, resolved.combatExpressionRouted());
		return resolved;
	}

	@SubscribeEvent
	public static void unload(LevelEvent.Unload event) {
		if (event.getLevel().isClientSide()) clear();
	}
}
