package dev.xkmc.youkaishomecoming.compat.ysm;

import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Optional adapter for OpenYSM 2.6.6.2-HCD-0.1.2 internals. The frozen external render API is unchanged.
 * No external types appear in signatures. Each capability is probed once and fails independently.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID)
public final class YsmClientPresentationBridge {

	private static final String OYSM = "com.elfmcys.yesstevemodel.";
	private static final Map<Object, YsmModelCatalog> CATALOGS = new WeakHashMap<>();
	private static final Map<LivingEntity, ClientState> ENTITIES = new WeakHashMap<>();
	private static CatalogAccess catalogs;
	private static FormAccess forms;
	private static RuntimeAccess runtime;
	private static ParameterAccess parameters;
	private static ReplayAccess replay;
	private static String catalogFailure, formFailure, runtimeFailure, parameterFailure, replayFailure;
	private static Field previewCache;
	private static boolean previewCacheChecked;
	private static FolderAccess folders;
	private static boolean foldersChecked;

	private YsmClientPresentationBridge() { }

	/** Optional native pack labels; directory IDs still come from actual model IDs. */
	public static Map<String, String> modelFolderNames() {
		if (!YSMClientCompat.isLoaded()) return Map.of();
		if (!foldersChecked) {
			foldersChecked = true;
			try { folders = new FolderAccess(); }
			catch (ReflectiveOperationException | LinkageError ignored) { }
		}
		if (folders == null) return Map.of();
		try {
			var names = new LinkedHashMap<String, String>();
			for (Object pack : stringMap(folders.packs.invoke(null)).values()) {
				String name = string(folders.name.invoke(pack));
				if (!name.isBlank()) names.put(string(folders.path.invoke(pack)).replace('\\', '/').replaceAll("/+$", ""), name);
			}
			return Map.copyOf(names);
		} catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
			folders = null;
			return Map.of();
		}
	}

	public static YsmModelCatalog catalog(String model) {
		if (!YSMClientCompat.isLoaded()) return YsmModelCatalog.unavailable(YsmModelCatalog.Status.NOT_INSTALLED, "yes_steve_model");
		try {
			CatalogAccess access = catalogAccess();
			if (access == null) return YsmModelCatalog.unavailable(YsmModelCatalog.Status.API_UNAVAILABLE, catalogFailure);
			if (!access.known(model)) return YsmModelCatalog.unavailable(YsmModelCatalog.Status.MODEL_MISSING, model);
			Object assembly = access.assembly(model);
			if (assembly == null) return YsmModelCatalog.unavailable(YsmModelCatalog.Status.MODEL_NOT_READY, model);
			YsmModelCatalog cached = CATALOGS.get(assembly);
			if (cached != null) return cached;
			Object bundle = access.bundle.invoke(assembly);
			List<String> clips = stringMap(access.animations.invoke(bundle)).keySet().stream().sorted().toList();
			List<YsmModelCatalog.WheelEntry> wheel = new ArrayList<>();
			List<YsmModelCatalog.Control> controls = new ArrayList<>();
			String detail = "";
			try {
				FormAccess metadata = formAccess();
				if (metadata == null) detail = formFailure;
				else metadata.read(assembly, clips, wheel, controls);
			} catch (ReflectiveOperationException | RuntimeException ex) {
				detail = failure(ex);
			}
			YsmModelCatalog result = new YsmModelCatalog(YsmModelCatalog.Status.READY, detail, clips, wheel, controls);
			CATALOGS.put(assembly, result);
			return result;
		} catch (ReflectiveOperationException | LinkageError ex) {
			catalogFailure = failure(ex);
			catalogs = null;
			return YsmModelCatalog.unavailable(YsmModelCatalog.Status.API_UNAVAILABLE, catalogFailure);
		}
	}

	/** Called immediately around the synchronous ExternalLivingRenderAPI.render invocation. */
	public static Frame beforeRender(LivingEntity entity, String model, YsmPresentationResolver.Resolved state) {
		if (!(entity instanceof YsmRenderOverrideTarget target)) return Frame.EMPTY;
		long now = target.getYsmPresentationTime();
		ClientState client = ENTITIES.get(entity);
		if (state.body() == null && state.parameters().isEmpty()) {
			if (client != null) {
				client.replayKey = "";
				client.clipStatus = "";
				client.suppressed = false;
				client.applied = "";
				client.skipped = "";
			}
			return Frame.EMPTY;
		}
		if (client == null) {
			client = new ClientState();
			ENTITIES.put(entity, client);
		}
		YsmParameterOverlay overlay = new YsmParameterOverlay();
		boolean applyingParameters = false;
		try {
			RuntimeAccess access = runtimeAccess();
			CatalogAccess modelAccess = catalogAccess();
			if (access == null || modelAccess == null) return Frame.EMPTY;
			Object animatable = access.animatable(entity);
			if (animatable == null || !Boolean.TRUE.equals(access.ready.invoke(animatable)) || !model.equals(access.modelId.invoke(animatable))) {
				client.skipped = "model_not_ready";
				return Frame.EMPTY;
			}
			// ExternalLivingRenderer owns a synchronous cache. Join optional work before touching its controller or variables.
			access.await.invoke(animatable);
			// A reload may replace the assembly before the renderer ticks the model. Never overlay the old storage.
			if (access.assembly.invoke(animatable) != modelAccess.assembly(model)) return Frame.EMPTY;
			YsmModelCatalog catalog = catalog(model);
			updatePlayback(client, state, entity, animatable, catalog);
			client.applied = "";
			client.skipped = "";
			if (state.parameters().isEmpty()) return Frame.EMPTY;
			applyingParameters = true;
			ParameterAccess values = parameterAccess();
			if (values == null) return Frame.EMPTY;
			Object storage = values.storage.invoke(access.processor.invoke(animatable));
			List<String> applied = new ArrayList<>();
			List<String> skipped = new ArrayList<>();
			for (var entry : state.parameters().entrySet()) {
				String name = entry.getKey();
				if (!catalog.accepts(name, entry.getValue())) {
					skipped.add(name + " (outside_declared_options)");
					continue;
				}
				YsmParameterOverlay.Slot slot = values.slot(storage, name);
				if (slot != null && overlay.apply(slot, entry.getValue())) applied.add(name);
				else skipped.add(name + " (not_numeric_or_not_ready)");
			}
			client.applied = String.join(", ", applied);
			client.skipped = String.join(", ", skipped);
			return new Frame(overlay);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
			if (applyingParameters) {
				parameterFailure = failure(ex);
				parameters = null;
			} else {
				runtimeFailure = failure(ex);
				runtime = null;
			}
			new Frame(overlay).close();
			return Frame.EMPTY;
		}
	}

	private static void updatePlayback(ClientState client, YsmPresentationResolver.Resolved state, LivingEntity entity,
			Object animatable, YsmModelCatalog catalog) {
		var animation = state.body();
		boolean suppressed = YSMClientCompat.isBeatenProjection(entity) && !state.beaten();
		if (animation == null || suppressed) {
			client.replayKey = "";
			client.clipStatus = suppressed ? "beaten_projection" : "";
			client.suppressed = suppressed;
			return;
		}
		client.clipStatus = catalog.status() != YsmModelCatalog.Status.READY ? "model_not_ready"
				: catalog.animations().contains(animation.clip()) ? "loop" : "missing_clip: " + animation.clip();
		try {
			ReplayAccess access = replayAccess();
			if (access == null) return;
			Object controller = access.controller.invoke(access.animationData.invoke(animatable), "player.cap");
			if (controller == null) return;
			String replayKey = animation.replayKey();
			if (client.controller.get() != controller || !client.replayKey.equals(replayKey) || client.suppressed) {
				access.clear.invoke(controller);
				client.controller = new WeakReference<>(controller);
				client.replayKey = replayKey;
			}
			client.suppressed = false;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
			replayFailure = failure(ex);
			replay = null;
		}
	}

	/** Returns the underlying numeric input between frames, not the transient YH override. */
	@Nullable
	public static Object parameterBaseValue(LivingEntity entity, String name) {
		try {
			RuntimeAccess access = runtimeAccess();
			ParameterAccess values = parameterAccess();
			if (access == null || values == null) return null;
			Object animatable = access.animatable(entity);
			if (animatable == null || !Boolean.TRUE.equals(access.ready.invoke(animatable))) return null;
			access.await.invoke(animatable);
			Object storage = values.storage.invoke(access.processor.invoke(animatable));
			YsmParameterOverlay.Slot slot = values.slot(storage, YsmPresentationState.normalizeParameter(name));
			return slot == null ? null : slot.get();
		} catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
			return null;
		}
	}

	public static Map<String, String> diagnostics(LivingEntity entity) {
		Map<String, String> result = new LinkedHashMap<>();
		if (!YSMClientCompat.isLoaded()) return Map.of("presentation", "not_installed");
		result.put("presentation.runtime", runtimeAccess() != null ? "available" : String.valueOf(runtimeFailure));
		result.put("presentation.parameters", parameterAccess() != null ? "numeric_render_overlay" : String.valueOf(parameterFailure));
		result.put("presentation.replay", replayAccess() != null ? "cap_reset" : String.valueOf(replayFailure));
		ClientState state = ENTITIES.get(entity);
		if (state != null) {
			result.put("presentation.clip", state.clipStatus);
			result.put("presentation.suppressed", Boolean.toString(state.suppressed));
			result.put("presentation.applied", state.applied);
			result.put("presentation.skipped", state.skipped);
		}
		return result;
	}

	@SubscribeEvent
	public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
		clearCaches();
		YSMClientCompat.clearSessionState();
	}

	@SubscribeEvent
	public static void unload(LevelEvent.Unload event) {
		if (event.getLevel().isClientSide()) clearCaches();
	}

	private static void clearCaches() {
		ENTITIES.clear();
		CATALOGS.clear();
	}

	/** OYSM's cache value retains its key entity. Evict ONLY our marked preview on close/reset. */
	public static void forgetPreview(LivingEntity entity) {
		ENTITIES.remove(entity);
		if (!YsmClientProfiles.isPreview(entity) || !YSMClientCompat.isLoaded()) return;
		try {
			RuntimeAccess access = runtimeAccess();
			if (access == null) return;
			if (!previewCacheChecked) {
				previewCacheChecked = true;
				Field field = Class.forName(OYSM + "client.renderer.ExternalLivingRenderer").getDeclaredField("cache");
				if (Map.class.isAssignableFrom(field.getType()) && field.trySetAccessible()) previewCache = field;
			}
			if (previewCache == null) return;
			Object renderer = access.renderer.invoke(null);
			if (renderer == null) return;
			Object animatable = access.cached.invoke(renderer, entity);
			if (animatable != null) access.await.invoke(animatable);
			if (previewCache.get(renderer) instanceof Map<?, ?> cache) cache.remove(entity);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
			previewCache = null; // Optional cleanup capability; never disable ordinary rendering.
			previewCacheChecked = true;
		}
	}

	public static final class Frame implements AutoCloseable {
		private static final Frame EMPTY = new Frame(null);
		private final YsmParameterOverlay overlay;

		private Frame(@Nullable YsmParameterOverlay overlay) { this.overlay = overlay; }

		@Override
		public void close() {
			if (overlay == null) return;
			try {
				overlay.close();
			} catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
				parameterFailure = failure(ex);
				parameters = null;
			}
		}
	}

	private static final class ClientState {
		// Do not retain the animatable/controller strongly: it references the WeakHashMap's entity key.
		private WeakReference<Object> controller = new WeakReference<>(null);
		private String replayKey = "";
		private boolean suppressed;
		private String clipStatus = "", applied = "", skipped = "";
	}

	private static CatalogAccess catalogAccess() {
		if (catalogs == null && catalogFailure == null) {
			try { catalogs = new CatalogAccess(); }
			catch (ReflectiveOperationException | LinkageError ex) { catalogFailure = failure(ex); }
		}
		return catalogs;
	}

	private static FormAccess formAccess() {
		if (forms == null && formFailure == null) {
			try { forms = new FormAccess(); }
			catch (ReflectiveOperationException | LinkageError ex) { formFailure = failure(ex); }
		}
		return forms;
	}

	private static RuntimeAccess runtimeAccess() {
		if (runtime == null && runtimeFailure == null) {
			try { runtime = new RuntimeAccess(); }
			catch (ReflectiveOperationException | LinkageError ex) { runtimeFailure = failure(ex); }
		}
		return runtime;
	}

	private static ParameterAccess parameterAccess() {
		if (parameters == null && parameterFailure == null) {
			try { parameters = new ParameterAccess(); }
			catch (ReflectiveOperationException | LinkageError ex) { parameterFailure = failure(ex); }
		}
		return parameters;
	}

	private static ReplayAccess replayAccess() {
		if (replay == null && replayFailure == null) {
			try { replay = new ReplayAccess(); }
			catch (ReflectiveOperationException | LinkageError ex) { replayFailure = failure(ex); }
		}
		return replay;
	}

	private static Method method(String type, String name, Class<?>... parameters) throws ReflectiveOperationException {
		return Class.forName(OYSM + type).getMethod(name, parameters);
	}

	private static String failure(Throwable ex) {
		Throwable cause = ex.getCause() == null ? ex : ex.getCause();
		return cause.getClass().getSimpleName() + ": " + cause.getMessage();
	}

	private static Map<String, Object> stringMap(Object object) {
		Map<String, Object> result = new LinkedHashMap<>();
		if (object instanceof Map<?, ?> map) map.forEach((key, value) -> result.put(String.valueOf(key), value));
		return result;
	}

	private static String string(Object value) { return value == null ? "" : value.toString(); }

	private static final class CatalogAccess {
		private final Method context = method("client.ClientModelManager", "getModelContext", String.class);
		private final Method knownModels = method("client.ClientModelManager", "getModelAssemblyMap");
		private final Method bundle = method("client.model.ModelAssembly", "getAnimationBundle");
		private final Method animations = method("client.model.PlayerModelBundle", "getMainAnimations");
		private CatalogAccess() throws ReflectiveOperationException { }
		private boolean known(String model) throws ReflectiveOperationException {
			return knownModels.invoke(null) instanceof Map<?, ?> map && map.containsKey(model);
		}
		private Object assembly(String model) throws ReflectiveOperationException {
			Object value = context.invoke(null, model);
			return value instanceof Optional<?> optional ? optional.orElse(null) : null;
		}
	}

	private static final class FolderAccess {
		private final Method packs = method("client.ClientModelManager", "getModelPackMap");
		private final Method path = method("resource.models.ModelPackData", "getPath");
		private final Method name = method("resource.models.ModelPackData", "getName");
		private FolderAccess() throws ReflectiveOperationException { }
	}

	private static final class FormAccess {
		private final Method data = method("client.model.ModelAssembly", "getModelData");
		private final Method properties = method("model.format.ServerModelInfo", "getModelProperties");
		private final Method extra = method("resource.models.ModelProperties", "getExtraAnimation");
		private final Method classify = method("resource.models.ModelProperties", "getExtraAnimationClassify");
		private final Method buttons = method("resource.models.ModelProperties", "getExtraAnimationButtons");
		private final Method configForms = method("client.gui.custom.ExtraAnimationButtons", "getConfigForms");
		private final Method groupLabel = method("client.gui.custom.ExtraAnimationButtons", "getName");
		private final Method title = method("client.gui.custom.AbstractConfig", "getTitle");
		private final Method description = method("client.gui.custom.AbstractConfig", "getDescription");
		private final Method type = method("client.gui.custom.AbstractConfig", "getType");
		private final Method value = method("client.gui.custom.AbstractConfig", "getValue");
		private final Method labels = method("client.gui.custom.configs.RadioConfig", "getLabels");
		private final Method min = method("client.gui.custom.configs.RangeConfig", "getMin");
		private final Method max = method("client.gui.custom.configs.RangeConfig", "getMax");
		private final Method step = method("client.gui.custom.configs.RangeConfig", "getStep");
		private FormAccess() throws ReflectiveOperationException { }

		private void read(Object assembly, List<String> clips, List<YsmModelCatalog.WheelEntry> wheel,
				List<YsmModelCatalog.Control> controls) throws ReflectiveOperationException {
			Object props = properties.invoke(data.invoke(assembly));
			Map<String, Object> groups = stringMap(buttons.invoke(props));
			addWheel("", stringMap(extra.invoke(props)), groups, clips, wheel);
			for (var group : stringMap(classify.invoke(props)).entrySet()) {
				addWheel(group.getKey(), stringMap(group.getValue()), groups, clips, wheel);
			}
			for (var group : groups.entrySet()) {
				Object entries = configForms.invoke(group.getValue());
				if (!(entries instanceof Object[] array)) continue;
				for (Object form : array) {
					String formType = string(type.invoke(form));
					String expression = string(value.invoke(form));
					String parameter;
					try { parameter = YsmPresentationState.normalizeParameter(expression); }
					catch (IllegalArgumentException ex) { parameter = ""; }
					List<YsmModelCatalog.Choice> choices = new ArrayList<>();
					if (formType.equals("radio")) {
						for (var label : stringMap(labels.invoke(form)).entrySet()) {
							choices.add(YsmModelCatalog.choice(parameter, label.getKey(), string(label.getValue())));
						}
					}
					boolean range = formType.equals("range");
					controls.add(new YsmModelCatalog.Control(group.getKey(), string(groupLabel.invoke(group.getValue())), string(title.invoke(form)), string(description.invoke(form)),
							formType, expression, parameter, range ? ((Number) min.invoke(form)).doubleValue() : 0,
							range ? ((Number) max.invoke(form)).doubleValue() : 1, range ? ((Number) step.invoke(form)).doubleValue() : 1, choices));
				}
			}
		}

		private void addWheel(String group, Map<String, Object> entries, Map<String, Object> configs, List<String> clips,
				List<YsmModelCatalog.WheelEntry> wheel) {
			entries.forEach((id, label) -> wheel.add(YsmModelCatalog.wheelEntry(group, id, string(label), clips, configs)));
		}
	}

	private static final class RuntimeAccess {
		private final Method renderer = method("client.renderer.RendererManager", "getExternalLivingRenderer");
		private final Method cached = method("client.renderer.ExternalLivingRenderer", "getCachedAnimatable", LivingEntity.class);
		private final Method ready = method("client.entity.GeoEntity", "isModelReady");
		private final Method modelId = method("client.entity.GeoEntity", "getModelId");
		private final Method assembly = method("client.entity.GeoEntity", "getModelAssembly");
		private final Method await = method("client.entity.GeoEntity", "awaitAsyncResult");
		private final Method processor = method("geckolib3.core.AnimatableEntity", "getEvaluationContext");
		private RuntimeAccess() throws ReflectiveOperationException { }
		private Object animatable(LivingEntity entity) throws ReflectiveOperationException {
			Object instance = renderer.invoke(null);
			return instance == null ? null : cached.invoke(instance, entity);
		}
	}

	/** Package visibility permits an optional contract test against the installed OYSM jar without linking it. */
	static final class ParameterAccess {
		private final Method storage = method("geckolib3.core.processor.AnimationProcessor", "getPublicVariableStorage");
		private final Method nameId = method("geckolib3.core.molang.util.StringPool", "computeIfAbsent", String.class);
		private final Method get = method("geckolib3.core.molang.storage.VariableStorage", "getScoped", int.class);
		private final Method set = method("geckolib3.core.molang.storage.VariableStorage", "setScoped", int.class, Object.class);
		private final Method property = method("molang.runtime.Struct", "getProperty", int.class);
		private final Method put = method("molang.runtime.Struct", "putProperty", int.class, Object.class);
		private final int roaming = (Integer) nameId.invoke(null, "roaming");
		private final Map<String, Integer> nameIds = new LinkedHashMap<>();
		ParameterAccess() throws ReflectiveOperationException { }

		@Nullable
		YsmParameterOverlay.Slot slot(Object storage, String name) throws ReflectiveOperationException {
			String normalized = YsmPresentationState.normalizeParameter(name);
			boolean roam = normalized.startsWith("v.roaming.");
			Integer cachedId = nameIds.get(normalized);
			if (cachedId == null) {
				cachedId = (Integer) nameId.invoke(null, normalized.substring(roam ? "v.roaming.".length() : "v.".length()));
				if (nameIds.size() >= YsmPresentationState.WIRE_MAX_PARAMETERS) nameIds.remove(nameIds.keySet().iterator().next());
				nameIds.put(normalized, cachedId);
			}
			int id = cachedId;
			Object owner = roam ? get.invoke(storage, roaming) : storage;
			if (owner == null) return null;
			Method read = roam ? property : get;
			Method write = roam ? put : set;
			return new YsmParameterOverlay.Slot() {
				@Override public Object get() throws ReflectiveOperationException { return read.invoke(owner, id); }
				@Override public void set(Object value) throws ReflectiveOperationException { write.invoke(owner, id, value); }
			};
		}
	}

	private static final class ReplayAccess {
		private final Method animationData = method("geckolib3.core.AnimatableEntity", "getAnimationData");
		private final Method controller = method("geckolib3.core.manager.AnimationData", "getAnimationControllerByName", String.class);
		private final Method clear = method("geckolib3.core.controller.PredicateBasedController", "clearAnimation");
		private ReplayAccess() throws ReflectiveOperationException { }
	}
}
