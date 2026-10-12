package dev.xkmc.youkaishomecoming.compat.ysm;

import rip.ysm.api.client.ExternalLivingPresentationAPI;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** YH presentation policy over OYSM's public external-model contract. */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID)
public final class YsmClientPresentationBridge {

	private static final Map<String, CatalogSnapshot> CATALOGS = new LinkedHashMap<>();
	private static final Map<LivingEntity, ClientState> ENTITIES = new WeakHashMap<>();
	private static final Map<LivingEntity, ClockRepairStats> CLOCK_REPAIRS = new WeakHashMap<>();

	private YsmClientPresentationBridge() { }

	public static Map<String, String> modelFolderNames() {
		return YSMClientCompat.isLoaded() ? ExternalLivingPresentationAPI.modelFolderNames() : Map.of();
	}

	public static YsmModelCatalog catalog(String model) {
		if (!YSMClientCompat.isLoaded()) return YsmModelCatalog.unavailable(YsmModelCatalog.Status.NOT_INSTALLED, "yes_steve_model");
		if (model == null || model.isBlank()) return YsmModelCatalog.unavailable(YsmModelCatalog.Status.MODEL_MISSING, "");
		ExternalLivingPresentationAPI.Catalog source = ExternalLivingPresentationAPI.catalog(model);
		if (!ExternalLivingPresentationAPI.isAvailable(source)) {
			CATALOGS.remove(model);
			return YsmModelCatalog.unavailable(YsmModelCatalog.Status.MODEL_NOT_READY, model);
		}
		CatalogSnapshot cached = CATALOGS.get(model);
		if (cached != null && cached.source == source) return cached.catalog;
		List<YsmModelCatalog.WheelEntry> wheel = source.wheel().stream()
				.map(entry -> new YsmModelCatalog.WheelEntry(entry.group(), entry.id(), entry.label(), entry.clipAvailable(), entry.configGroup(), entry.submenu())).toList();
		List<YsmModelCatalog.Control> controls = source.controls().stream()
				.map(control -> new YsmModelCatalog.Control(control.group(), control.groupLabel(), control.title(), control.description(),
						control.type(), control.expression(), control.parameter(), control.min(), control.max(), control.step(),
						control.choices().stream().map(choice -> new YsmModelCatalog.Choice(choice.label(), choice.expression(), choice.numericValue())).toList())).toList();
		List<String> parameterNames;
		try { parameterNames = ExternalLivingPresentationAPI.parameterNames(model); }
		catch (NoSuchMethodError ignored) { parameterNames = List.of(); } // OYSM 0.2.0–0.2.2 retain authored forms.
		YsmModelCatalog result = new YsmModelCatalog(YsmModelCatalog.Status.READY, "", source.animations(), wheel, controls, parameterNames);
		CATALOGS.put(model, new CatalogSnapshot(source, result));
		return result;
	}

	private record CatalogSnapshot(ExternalLivingPresentationAPI.Catalog source, YsmModelCatalog catalog) { }

	public static List<String> projectileSlots(String model) {
		if (!YSMClientCompat.isLoaded() || model == null || model.isBlank()) return List.of();
		return ExternalLivingPresentationAPI.catalog(model).projectileSlots();
	}

	/** OYSM owns parameter lifetime across draws and async evaluations. Close completes this draw's input snapshot. */
	public static Frame beforeRender(LivingEntity entity, String model, YsmPresentationResolver.Resolved state) {
		if (!YSMClientCompat.isLoaded()) return Frame.EMPTY;
		if (!(entity instanceof YsmRenderOverrideTarget)) return Frame.EMPTY;
		ClientState client = ENTITIES.computeIfAbsent(entity, ignored -> new ClientState());
		client.rendered = null;
		client.renderedFrame = null;
		client.renderSuccessful = false;
		client.parameterBases.clear();
		boolean preview = YsmClientPresentation.isPreview(entity);
		boolean hasPresentation = state.body() != null || !state.parameters().isEmpty() || preview;
		if (!hasPresentation) client.clearTransient();
		boolean suppressed = YSMClientCompat.isBeatenProjection(entity) && !state.beaten();
		client.suppressed = suppressed;
		String clip = state.body() == null ? "" : state.body().clip();
		String replayKey = state.body() == null ? "" : state.body().replayKey();
		ExternalLivingPresentationAPI.Frame nativeFrame = ExternalLivingPresentationAPI.prepare(entity, model, clip, replayKey,
				state.parameters(), suppressed);
		if (nativeFrame.clockReset()) {
			client.clipStatus = nativeFrame.status();
			CLOCK_REPAIRS.computeIfAbsent(entity, ignored -> new ClockRepairStats()).record(nativeFrame.status());
			return Frame.EMPTY;
		}
		if (!hasPresentation) return new Frame(nativeFrame);
		if (!nativeFrame.ready()) {
			client.clipStatus = nativeFrame.status();
			client.skipped = nativeFrame.status();
			return Frame.EMPTY;
		}
		client.clipStatus = nativeFrame.clipStatus();
		client.applied = String.join(", ", nativeFrame.applied());
		client.skipped = String.join(", ", nativeFrame.skipped());
		client.parameterBases = new LinkedHashMap<>(nativeFrame.parameterBases());
		client.renderedModel = model;
		client.rendered = state;
		client.renderedFrame = nativeFrame;
		return new Frame(nativeFrame);
	}

	@Nullable
	public static Float parameterBaseValue(LivingEntity entity, String name) {
		if (!YSMClientCompat.isLoaded()) return null;
		ClientState client = ENTITIES.get(entity);
		if (client != null && client.parameterBases.containsKey(name)) return client.parameterBases.get(name);
		try { return ExternalLivingPresentationAPI.parameterBaseValue(entity, name); }
		catch (IllegalArgumentException ignored) { return null; }
	}

	@Nullable
	public static Map<String, Float> previewInputs(LivingEntity entity, String model, List<String> names) {
		if (!YsmClientPresentation.isPreview(entity) || !YSMClientCompat.isLoaded()) return null;
		ClientState client = ENTITIES.get(entity);
		try { return ExternalLivingPresentationAPI.previewInputs(entity, model, names,
				client != null && client.renderSuccessful && model.equals(client.renderedModel) ? client.renderedFrame : null); }
		catch (IllegalArgumentException ignored) { return null; }
	}

	static void renderOutcome(LivingEntity entity, boolean rendered) {
		ClientState state = ENTITIES.get(entity);
		if (state != null) state.renderSuccessful = rendered;
	}

	public static Map<String, String> diagnostics(LivingEntity entity) {
		if (!YSMClientCompat.isLoaded()) return Map.of("presentation", "not_installed");
		Map<String, String> result = new LinkedHashMap<>();
		result.put("presentation.runtime", "public_api");
		result.put("presentation.parameters", "numeric_lease");
		result.put("presentation.replay", "public_api");
		result.put("presentation.clock", "public_api");
		ClientState state = ENTITIES.get(entity);
		if (state != null) {
			result.put("presentation.clip", state.clipStatus);
			result.put("presentation.applied", state.applied);
			result.put("presentation.skipped", state.skipped);
		}
		ClockRepairStats repairs = CLOCK_REPAIRS.get(entity);
		if (repairs != null) {
			result.put("presentation.clockResets", Integer.toString(repairs.count));
			result.put("presentation.clockLastReset", repairs.last);
		}
		return result;
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
		clearCaches();
		YSMClientCompat.clearSessionState();
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void unload(LevelEvent.Unload event) {
		if (event.getLevel().isClientSide()) clearCaches();
	}

	private static void clearCaches() {
		if (YSMClientCompat.isLoaded())
			for (LivingEntity entity : ENTITIES.keySet()) ExternalLivingPresentationAPI.forget(entity);
		ENTITIES.clear();
		CLOCK_REPAIRS.clear();
		CATALOGS.clear();
	}

	public static void forgetPreview(LivingEntity entity) {
		ClientState state = ENTITIES.remove(entity);
		if (YSMClientCompat.isLoaded()) ExternalLivingPresentationAPI.forget(entity);
		if (state != null) state.clearTransient();
	}

	public static final class Frame implements AutoCloseable {
		private static final Frame EMPTY = new Frame(null);
		private final ExternalLivingPresentationAPI.Frame delegate;

		private Frame(@Nullable ExternalLivingPresentationAPI.Frame delegate) { this.delegate = delegate; }

		@Override
		public void close() {
			if (delegate != null) delegate.close();
		}
	}

	private static final class ClientState {
		private Map<String, Float> parameterBases = new LinkedHashMap<>();
		private String clipStatus = "", applied = "", skipped = "";
		private String renderedModel = "";
		private YsmPresentationResolver.Resolved rendered;
		private ExternalLivingPresentationAPI.Frame renderedFrame;
		private boolean suppressed;
		private boolean renderSuccessful;

		private void clearTransient() {
			parameterBases.clear();
			clipStatus = "";
			applied = "";
			skipped = "";
			rendered = null;
			renderedFrame = null;
			suppressed = false;
			renderedModel = "";
			renderSuccessful = false;
		}
	}

	private static final class ClockRepairStats {
		private int count;
		private String last = "";

		private void record(String value) {
			count++;
			last = value;
		}
	}
}
