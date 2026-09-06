package dev.xkmc.youkaishomecoming.compat.ysm;

import net.minecraft.world.entity.LivingEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Client projection of server data, plus isolated preview drafts. No OYSM classes. */
@OnlyIn(Dist.CLIENT)
public final class YsmClientProfiles {
	public record Response(boolean success, String message, long revision, String json) { }
	private static final Map<String, YsmProfileData.Entry> PROFILES = new LinkedHashMap<>();
	private static final Map<String, Response> RESPONSES = new LinkedHashMap<>();
	private record Preview(YsmModelProfile profile, Map<String, Float> parameters) { }
	private static final Map<LivingEntity, Preview> PREVIEWS = new WeakHashMap<>();

	private YsmClientProfiles() { }

	public static YsmProfileData.Entry entry(String model) {
		var entry = PROFILES.get(model);
		return entry == null ? new YsmProfileData.Entry(0, YsmModelProfile.empty(model)) : entry;
	}

	public static void receive(YsmProfileSyncToClient packet) {
		if (packet.reset) PROFILES.clear();
		if (!packet.json.isEmpty()) {
			try {
				var profile = YsmModelProfile.fromJson(packet.json);
				var previous = PROFILES.get(profile.model());
				if (previous == null || previous.revision() <= packet.revision)
					PROFILES.put(profile.model(), new YsmProfileData.Entry(packet.revision, profile));
			} catch (IllegalArgumentException ex) {
				response(packet.requestId, false, ex.getMessage());
				return;
			}
		}
		response(packet.requestId, packet.success, packet.message, packet.revision, packet.json);
	}

	public static void response(String id, boolean success, String message) {
		response(id, success, message, -1, "");
	}

	public static void response(String id, boolean success, String message, long revision, String json) {
		if (id == null || id.isEmpty()) return;
		if (RESPONSES.size() >= 64) RESPONSES.remove(RESPONSES.keySet().iterator().next());
		RESPONSES.put(id, new Response(success, message, revision, json));
	}

	public static Response takeResponse(String id) { return RESPONSES.remove(id); }
	public static void preview(LivingEntity entity, YsmModelProfile profile) { preview(entity, profile, Map.of()); }
	public static void preview(LivingEntity entity, YsmModelProfile profile, Map<String, Float> parameters) {
		PREVIEWS.put(entity, new Preview(profile, Map.copyOf(parameters)));
	}
	public static boolean isPreview(LivingEntity entity) { return PREVIEWS.containsKey(entity); }
	public static java.util.List<String> models() { return PROFILES.keySet().stream().sorted().toList(); }
	public static void forgetPreview(LivingEntity entity) { PREVIEWS.remove(entity); }
	public static void clear() { PROFILES.clear(); RESPONSES.clear(); PREVIEWS.clear(); }

	public static YsmPresentationResolver.Resolved resolve(LivingEntity entity, String model) {
		var preview = PREVIEWS.get(entity);
		var profile = preview == null ? null : preview.profile();
		Map<String, Float> bindingParameters = Map.of();
		if (preview != null && preview.profile().model().equals(model)) bindingParameters = preview.parameters();
		else if (preview == null) {
			var binding = YSMClientCompat.resolveBinding(entity);
			if (binding != null && binding.modelId().equals(model)) bindingParameters = binding.parameters();
		}
		if (profile == null) {
			var entry = PROFILES.get(model);
			profile = entry == null ? null : entry.profile();
		}
		if (!(entity instanceof YsmRenderOverrideTarget target))
			return new YsmPresentationResolver.Resolved(null, Map.of(), false, false);
		var signals = target.getYsmSignals();
		// Existing beaten EntityData can arrive one tick before the general signals. Project that
		// authoritative phase immediately, never a stale prone clip over a newly started defeat.
		if (entity instanceof YoukaiEntity youkai) {
			YsmModelProfile.Trigger state = !youkai.isBeaten() ? null : switch (youkai.getBeatenPhase()) {
				case YoukaiEntity.BEATEN_DEFEAT -> YsmModelProfile.Trigger.DEFEAT;
				case YoukaiEntity.BEATEN_FALLING -> YsmModelProfile.Trigger.FALLING;
				default -> YsmModelProfile.Trigger.PRONE;
			};
			if (state != null && state != signals.state()) signals = signals.advance(state, false,
					Math.max(0, target.getYsmPresentationTime() - youkai.getBeatenPhaseTicks()));
			else if (state == null && signals.state().beaten()) signals = signals.advance(YsmModelProfile.Trigger.IDLE, false, target.getYsmPresentationTime());
		}
		var resolved = YsmPresentationResolver.resolve(model, profile, signals, target.getYsmPresentation(), target.getYsmPresentationTime(), bindingParameters);
		// Legacy spell hints retain their existing semantics and precedence over automatic actions.
		if (!resolved.beaten() && resolved.body() != null && !resolved.body().explicit() && !target.getYsmAnimationOverride().isEmpty())
			return new YsmPresentationResolver.Resolved(null, resolved.parameters(), false,
					resolved.combatExpressionRouted());
		return resolved;
	}
}
