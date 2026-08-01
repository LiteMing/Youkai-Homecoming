package dev.xkmc.youkaishomecoming.compat.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.xkmc.youkaishomecoming.compat.ysm.YSMCompatConfig.RenderBinding;
import dev.xkmc.youkaishomecoming.content.entity.boss.BossYoukaiEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.GeneralYoukaiEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.registrate.YHEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class YSMClientCompat {

	private static final String MOD_ID = "yes_steve_model";
	private static final String MODEL_REMILIA = "YH内置/remilia";
	private static final String TEXTURE_DEFAULT = "default";
	private static final ArgumentType<String> YSM_ID_ARGUMENT = new TokenArgument("Expected YSM id", List.of(MODEL_REMILIA, "namespace:path/model"));
	private static final ArgumentType<String> ENTITY_TARGET_ARGUMENT = new TokenArgument("Expected entity target", List.of("@e[limit=1,sort=nearest]", "@a", "00000000-0000-0000-0000-000000000000"));
	private static final boolean LOADED = ModList.get().isLoaded(MOD_ID);
	private static final Map<ResourceLocation, RenderBinding> TYPE_DEBUG_OVERRIDES = new LinkedHashMap<>();
	private static final Map<UUID, RenderBinding> ENTITY_DEBUG_OVERRIDES = new LinkedHashMap<>();
	private static final int DEBUG_TEXT_COLOR = 0xffffffff;
	private static final int DEBUG_LABEL_COLOR = 0xffb8e6ff;
	private static final int DEBUG_BG_A = 0xa0000000;
	private static final int DEBUG_BG_B = 0x90000000;
	private static final SuggestionProvider<CommandSourceStack> ENTITY_SUGGESTIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(ForgeRegistries.ENTITY_TYPES.getKeys().stream()
					.filter(id -> Objects.equals(id.getNamespace(), YoukaisHomecoming.MODID))
					.map(ResourceLocation::toString), builder);
	private static final SuggestionProvider<CommandSourceStack> MODEL_SUGGESTIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(loadedModelIds(), builder);
	private static final SuggestionProvider<CommandSourceStack> TARGET_ENTITY_SUGGESTIONS = (ctx, builder) -> {
		Entity pointed = getPointedEntity();
		if (pointed != null) {
			builder.suggest(pointed.getUUID().toString(), Component.literal("pointed entity"));
		}
		return builder.buildFuture();
	};

	private static Method renderMethod;
	private static Method clearDebugMethod;
	private static Method debugSnapshotMethod;
	private static Method loadedModelIdsMethod;
	private static Method modelTextureNamesMethod;
	private static Method modelAnimationNamesMethod;
	private static Method modelDefaultTextureNameMethod;
	private static boolean unavailable;
	private static int delegatedRenderDepth;
	private static boolean textureListUnavailable;
	private static boolean animationListUnavailable;
	private static boolean defaultTextureUnavailable;
	private static boolean debugOverlay;
	private static UUID debugTarget;
	private static final long CATALOG_CACHE_NANOS = 500_000_000L;
	private static long loadedModelIdsCacheAt;
	private static List<String> loadedModelIdsCache = List.of();
	private static final Map<String, CachedList> textureNameCache = new LinkedHashMap<>();
	private static final Map<String, CachedList> animationNameCache = new LinkedHashMap<>();
	private static final Map<String, CachedString> defaultTextureCache = new LinkedHashMap<>();

	public static boolean isLoaded() {
		return LOADED;
	}

	public static boolean delegateRender(LivingEntity e, float yaw, float pTick, PoseStack pose, MultiBufferSource buffer, int light) {
		if (delegatedRenderDepth > 0) {
			return false;
		}
		RenderRequest request = resolveRenderRequest(e);
		if (!LOADED || unavailable || request == null) {
			return false;
		}
		Method method = getRenderMethod();
		if (method == null) {
			return false;
		}
		delegatedRenderDepth++;
		try {
			Object result = method.invoke(null, e, request.modelId(), request.textureName(), request.animationHint(), yaw, pTick, pose, buffer, light);
			return result instanceof Boolean value && value;
		} catch (IllegalAccessException | InvocationTargetException ex) {
			unavailable = true;
			YoukaisHomecoming.LOGGER.warn("Failed to delegate youkai rendering to Yes Steve Model", ex);
			return false;
		} finally {
			delegatedRenderDepth--;
		}
	}

	public static boolean isDelegatingRender() {
		return delegatedRenderDepth > 0;
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void renderMappedLivingEntity(RenderLivingEvent.Pre<?, ?> event) {
		LivingEntity entity = event.getEntity();
		float yaw = Mth.rotLerp(event.getPartialTick(), entity.yBodyRotO, entity.yBodyRot);
		if (delegateRender(entity, yaw, event.getPartialTick(), event.getPoseStack(),
				event.getMultiBufferSource(), event.getPackedLight())) {
			event.setCanceled(true);
		}
	}

	private static Method getRenderMethod() {
		if (renderMethod != null) {
			return renderMethod;
		}
		try {
			renderMethod = Class.forName("rip.ysm.api.client.ExternalLivingRenderAPI").getMethod(
					"render",
					LivingEntity.class,
					String.class,
					String.class,
					String.class,
					float.class,
					float.class,
					PoseStack.class,
					MultiBufferSource.class,
					int.class
			);
			return renderMethod;
		} catch (ClassNotFoundException | NoSuchMethodException ex) {
			unavailable = true;
			YoukaisHomecoming.LOGGER.warn("Yes Steve Model external living renderer API is unavailable", ex);
			return null;
		}
	}

	private static Method getClearDebugMethod() {
		if (clearDebugMethod != null) {
			return clearDebugMethod;
		}
		try {
			clearDebugMethod = Class.forName("rip.ysm.api.client.ExternalLivingRenderAPI").getMethod("clearDebug");
			return clearDebugMethod;
		} catch (ClassNotFoundException | NoSuchMethodException ex) {
			YoukaisHomecoming.LOGGER.warn("Yes Steve Model external living debug clear API is unavailable", ex);
			return null;
		}
	}

	private static Method getDebugSnapshotMethod() {
		if (debugSnapshotMethod != null) {
			return debugSnapshotMethod;
		}
		try {
			debugSnapshotMethod = Class.forName("rip.ysm.api.client.ExternalLivingRenderAPI").getMethod(
					"getDebugSnapshot",
					LivingEntity.class
			);
			return debugSnapshotMethod;
		} catch (ClassNotFoundException | NoSuchMethodException ex) {
			YoukaisHomecoming.LOGGER.warn("Yes Steve Model external living debug snapshot API is unavailable", ex);
			return null;
		}
	}

	private static Method getLoadedModelIdsMethod() {
		if (loadedModelIdsMethod != null) {
			return loadedModelIdsMethod;
		}
		try {
			loadedModelIdsMethod = Class.forName("rip.ysm.api.client.ExternalLivingRenderAPI").getMethod("getLoadedModelIds");
			return loadedModelIdsMethod;
		} catch (ClassNotFoundException | NoSuchMethodException ex) {
			YoukaisHomecoming.LOGGER.warn("Yes Steve Model loaded model id API is unavailable", ex);
			return null;
		}
	}

	private static Method getModelTextureNamesMethod() {
		if (textureListUnavailable) {
			return null;
		}
		if (modelTextureNamesMethod != null) {
			return modelTextureNamesMethod;
		}
		try {
			modelTextureNamesMethod = Class.forName("rip.ysm.api.client.ExternalLivingRenderAPI").getMethod(
					"getModelTextureNames",
					String.class
			);
			return modelTextureNamesMethod;
		} catch (ClassNotFoundException | NoSuchMethodException ex) {
			textureListUnavailable = true;
			YoukaisHomecoming.LOGGER.warn("Yes Steve Model texture list API is unavailable", ex);
			return null;
		}
	}

	private static Method getModelAnimationNamesMethod() {
		if (animationListUnavailable) {
			return null;
		}
		if (modelAnimationNamesMethod != null) {
			return modelAnimationNamesMethod;
		}
		try {
			modelAnimationNamesMethod = Class.forName("rip.ysm.api.client.ExternalLivingRenderAPI").getMethod(
					"getModelAnimationNames",
					String.class
			);
			return modelAnimationNamesMethod;
		} catch (ClassNotFoundException | NoSuchMethodException ex) {
			animationListUnavailable = true;
			YoukaisHomecoming.LOGGER.warn("Yes Steve Model animation list API is unavailable", ex);
			return null;
		}
	}

	private static Method getModelDefaultTextureNameMethod() {
		if (defaultTextureUnavailable) {
			return null;
		}
		if (modelDefaultTextureNameMethod != null) {
			return modelDefaultTextureNameMethod;
		}
		try {
			modelDefaultTextureNameMethod = Class.forName("rip.ysm.api.client.ExternalLivingRenderAPI").getMethod(
					"getModelDefaultTextureName",
					String.class
			);
			return modelDefaultTextureNameMethod;
		} catch (ClassNotFoundException | NoSuchMethodException ex) {
			defaultTextureUnavailable = true;
			YoukaisHomecoming.LOGGER.warn("Yes Steve Model default texture API is unavailable", ex);
			return null;
		}
	}

	private static void clearYsmDebug() {
		if (!LOADED) {
			return;
		}
		Method method = getClearDebugMethod();
		if (method == null) {
			return;
		}
		try {
			method.invoke(null);
		} catch (IllegalAccessException | InvocationTargetException ex) {
			YoukaisHomecoming.LOGGER.warn("Failed to clear Yes Steve Model external living debug overlay", ex);
		}
	}

	private static Map<String, String> getYsmDebugSnapshot(LivingEntity entity) {
		if (!LOADED) {
			return Map.of("loaded", "false");
		}
		Method method = getDebugSnapshotMethod();
		if (method == null) {
			return Map.of("debugApi", "missing");
		}
		try {
			Object result = method.invoke(null, entity);
			if (result instanceof Map<?, ?> map) {
				Map<String, String> copy = new LinkedHashMap<>();
				map.forEach((key, value) -> copy.put(String.valueOf(key), String.valueOf(value)));
				return copy;
			}
			return Map.of("debugApi", "invalid result");
		} catch (IllegalAccessException | InvocationTargetException ex) {
			YoukaisHomecoming.LOGGER.warn("Failed to read Yes Steve Model external living debug snapshot", ex);
			return Map.of("debugApi", "error");
		}
	}

	public static List<String> loadedModelIds() {
		long now = System.nanoTime();
		if (isCacheFresh(loadedModelIdsCacheAt, now)) {
			return new ArrayList<>(loadedModelIdsCache);
		}
		List<String> result = new ArrayList<>(List.of(MODEL_REMILIA));
		if (!LOADED) {
			loadedModelIdsCache = List.copyOf(result);
			loadedModelIdsCacheAt = now;
			return result;
		}
		Method method = getLoadedModelIdsMethod();
		if (method == null) {
			loadedModelIdsCache = List.copyOf(result);
			loadedModelIdsCacheAt = now;
			return result;
		}
		try {
			Object value = method.invoke(null);
			if (value instanceof Iterable<?> iterable) {
				for (Object entry : iterable) {
					String id = String.valueOf(entry);
					if (!id.isBlank() && !result.contains(id)) {
						result.add(id);
					}
				}
			}
		} catch (IllegalAccessException | InvocationTargetException ex) {
			YoukaisHomecoming.LOGGER.warn("Failed to read Yes Steve Model loaded model ids", ex);
		}
		loadedModelIdsCache = List.copyOf(result);
		loadedModelIdsCacheAt = now;
		return result;
	}

	private static RenderBinding resolveBinding(LivingEntity e) {
		BindingResolution resolution = resolveBindingWithSource(e);
		RenderBinding binding = resolution.binding();
		return binding != null && binding.enabled() ? binding : null;
	}

	private static RenderRequest resolveRenderRequest(LivingEntity e) {
		BindingResolution resolution = resolveBindingWithSource(e);
		RenderBinding binding = resolution.binding();
		if (binding != null && !binding.enabled()) {
			return null;
		}
		String modelOverride = e instanceof YsmRenderOverrideTarget target ? target.getYsmModelOverride() : "";
		String textureOverride = e instanceof YsmRenderOverrideTarget target ? target.getYsmTextureOverride() : "";
		String modelId = !modelOverride.isBlank() ? modelOverride : binding == null ? "" : binding.modelId();
		if (modelId.isBlank()) {
			return null;
		}
		String textureName = !textureOverride.isBlank() ? textureOverride :
				!modelOverride.isBlank() ? TEXTURE_DEFAULT :
						binding == null ? TEXTURE_DEFAULT : binding.textureName();
		return new RenderRequest(modelId, textureName, selectAnimation(e, modelId));
	}

	private static BindingResolution resolveBindingWithSource(LivingEntity e) {
		RenderBinding entityOverride = ENTITY_DEBUG_OVERRIDES.get(e.getUUID());
		if (entityOverride != null) {
			return new BindingResolution(entityOverride, "entity override");
		}
		ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
		if (entityId == null) {
			return new BindingResolution(null, "unknown type");
		}
		RenderBinding override = TYPE_DEBUG_OVERRIDES.get(entityId);
		if (override != null) {
			return new BindingResolution(override, "type override");
		}
		RenderBinding binding = YSMCompatConfig.defaultBinding(entityId);
		return new BindingResolution(binding, binding == null ? "none" : "default");
	}

	private static String selectAnimation(LivingEntity e, String modelId) {
		if (e instanceof YoukaiEntity youkai && youkai.isBeaten()) {
			// Route beaten poses through the special= group: the base-predicate keys
			// (defeat/falling/climbing) are only consumed when OpenYSM's base controller runs,
			// which model state machines can replace. The cap controller resolves special=
			// via resolveSpecialAnimationHint with a + fallback chain, so any model works:
			// own beaten_* animations first, then ubiquitous generic ones.
			return switch (youkai.getBeatenPhase()) {
				case YoukaiEntity.BEATEN_DEFEAT -> "special=beaten_defeat+defeat+death+die+attacked";
				case YoukaiEntity.BEATEN_FALLING -> "special=beaten_falling+falling+fall+jump+fly";
				case YoukaiEntity.BEATEN_PRONE -> "special=beaten_prone+prone+climbing+climb+sleep";
				default -> "special=beaten_defeat+defeat+death+die+attacked";
			};
		}
		if (e.hasEffect(YHEffects.BEATEN.get())) {
			return "special=beaten_prone+prone+climbing+climb+sleep";
		}
		Vec3 motion = e.getDeltaMovement();
		double horizontalSpeedSqr = motion.x * motion.x + motion.z * motion.z;
		boolean flying = e.isNoGravity();
		if (e instanceof GeneralYoukaiEntity youkai) {
			flying |= youkai.isFlying();
			if (youkai instanceof BossYoukaiEntity && youkai.isAggressive()) {
				flying = true;
			}
		}
		boolean angry = isAngryExpression(e);
		String overrideHint = e instanceof YsmRenderOverrideTarget target ? target.getYsmAnimationOverride() : "";
		String actionHint = actionAnimationHint(modelId, overrideHint);
		List<String> hints = new ArrayList<>(3);
		if (flying) {
			hints.add("fly");
		} else if (e.onGround()) {
			if (horizontalSpeedSqr > 0.0025) {
				hints.add("walk");
			} else {
				hints.add("calm");
			}
		}
		if (angry && !overridesPassiveExpression(overrideHint)) {
			hints.add(YSMCompatConfig.expressionToken(modelId, "angry"));
		}
		if (!actionHint.isBlank()) {
			hints.add(actionHint);
		}
		return hints.isEmpty() ? null : String.join(" ", hints);
	}

	private static String actionAnimationHint(String modelId, String animation) {
		if (animation.isBlank()) {
			return "";
		}
		List<String> tokens = new ArrayList<>();
		for (String token : splitAnimationHint(animation)) {
			tokens.add(actionAnimationToken(modelId, token));
		}
		return String.join(" ", tokens);
	}

	private static String actionAnimationToken(String modelId, String token) {
		String key = hintKey(token);
		if (key.isBlank() || token.contains("=") || "fly".equals(key) || "walk".equals(key) ||
				"calm".equals(key) || "climb".equals(key) || "climbing".equals(key)) {
			return token;
		}
		String semantic = YSMCompatConfig.expressionToken(modelId, key);
		int equals = semantic.indexOf('=');
		if ("angry".equals(key) || "cast".equals(key) || "charge".equals(key) || "special".equals(key)) {
			return semantic;
		}
		String candidates = equals >= 0 ? semantic.substring(equals + 1) : key;
		return "special=" + candidates;
	}

	private static boolean overridesPassiveExpression(String animation) {
		for (String token : splitAnimationHint(animation)) {
			String key = hintKey(token);
			if (!key.isBlank() && !"fly".equals(key) && !"walk".equals(key) && !"calm".equals(key)) {
				return true;
			}
		}
		return false;
	}

	private static List<String> splitAnimationHint(String animation) {
		if (animation.isBlank()) {
			return List.of();
		}
		List<String> result = new ArrayList<>();
		for (String token : animation.split("[,;|\\s]+")) {
			if (!token.isBlank()) {
				result.add(token.trim());
			}
		}
		return result;
	}

	public static List<String> loadedTextureNames(String modelId) {
		String cacheKey = modelId == null ? "" : modelId;
		long now = System.nanoTime();
		CachedList cached = textureNameCache.get(cacheKey);
		if (cached != null && isCacheFresh(cached.loadedAt(), now)) {
			return new ArrayList<>(cached.values());
		}
		List<String> result = new ArrayList<>();
		if (modelId == null || modelId.isBlank()) {
			for (String id : loadedModelIds()) {
				addAllUnique(result, loadedTextureNames(id));
			}
			if (result.isEmpty()) {
				result.add(TEXTURE_DEFAULT);
			}
			textureNameCache.put(cacheKey, new CachedList(List.copyOf(result), now));
			return result;
		}
		String defaultTexture = defaultTextureName(modelId);
		if (!defaultTexture.isBlank()) {
			result.add(defaultTexture);
		}
		if (LOADED) {
			Method method = getModelTextureNamesMethod();
			if (method != null) {
				try {
					addIterableUnique(result, method.invoke(null, modelId));
				} catch (IllegalAccessException | InvocationTargetException ex) {
					YoukaisHomecoming.LOGGER.warn("Failed to read Yes Steve Model texture ids for {}", modelId, ex);
				}
			}
		}
		if (!result.contains(TEXTURE_DEFAULT)) {
			result.add(TEXTURE_DEFAULT);
		}
		textureNameCache.put(cacheKey, new CachedList(List.copyOf(result), now));
		return result;
	}

	public static List<String> loadedAnimationNames(String modelId) {
		String cacheKey = modelId == null ? "" : modelId;
		long now = System.nanoTime();
		CachedList cached = animationNameCache.get(cacheKey);
		if (cached != null && isCacheFresh(cached.loadedAt(), now)) {
			return new ArrayList<>(cached.values());
		}
		List<String> result = new ArrayList<>(List.of(
				"special", "cast", "charge", "angry", "fly", "walk", "calm",
				"defeat", "falling", "climb", "climbing", "beaten",
				"beaten_defeat", "beaten_falling", "beaten_prone"
		));
		if (modelId == null || modelId.isBlank()) {
			for (String id : loadedModelIds()) {
				addAllUnique(result, loadedAnimationNames(id));
			}
			animationNameCache.put(cacheKey, new CachedList(List.copyOf(result), now));
			return result;
		}
		if (LOADED) {
			Method method = getModelAnimationNamesMethod();
			if (method != null) {
				try {
					addIterableUnique(result, method.invoke(null, modelId));
				} catch (IllegalAccessException | InvocationTargetException ex) {
					YoukaisHomecoming.LOGGER.warn("Failed to read Yes Steve Model animation ids for {}", modelId, ex);
				}
			}
		}
		animationNameCache.put(cacheKey, new CachedList(List.copyOf(result), now));
		return result;
	}

	public static String defaultTextureName(String modelId) {
		if (modelId == null || modelId.isBlank()) {
			return TEXTURE_DEFAULT;
		}
		if (!LOADED) {
			return TEXTURE_DEFAULT;
		}
		long now = System.nanoTime();
		CachedString cached = defaultTextureCache.get(modelId);
		if (cached != null && isCacheFresh(cached.loadedAt(), now)) {
			return cached.value();
		}
		Method method = getModelDefaultTextureNameMethod();
		if (method == null) {
			return TEXTURE_DEFAULT;
		}
		try {
			Object value = method.invoke(null, modelId);
			String name = String.valueOf(value);
			name = name.isBlank() || "null".equals(name) ? TEXTURE_DEFAULT : name;
			defaultTextureCache.put(modelId, new CachedString(name, now));
			return name;
		} catch (IllegalAccessException | InvocationTargetException ex) {
			YoukaisHomecoming.LOGGER.warn("Failed to read Yes Steve Model default texture for {}", modelId, ex);
			return TEXTURE_DEFAULT;
		}
	}

	private static boolean isCacheFresh(long loadedAt, long now) {
		return loadedAt > 0 && now - loadedAt < CATALOG_CACHE_NANOS;
	}

	private static void addIterableUnique(List<String> result, Object value) {
		if (value instanceof Iterable<?> iterable) {
			for (Object entry : iterable) {
				String id = String.valueOf(entry);
				if (!id.isBlank() && !result.contains(id)) {
					result.add(id);
				}
			}
		}
	}

	private static void addAllUnique(List<String> result, Iterable<String> values) {
		for (String value : values) {
			if (value != null && !value.isBlank() && !result.contains(value)) {
				result.add(value);
			}
		}
	}

	private static String hintKey(String token) {
		int equals = token.indexOf('=');
		return (equals >= 0 ? token.substring(0, equals) : token).trim();
	}

	private static boolean hasHintToken(String animationHint, String expected) {
		for (String token : splitAnimationHint(animationHint)) {
			if (expected.equals(hintKey(token))) {
				return true;
			}
		}
		return false;
	}

	@SubscribeEvent
	public static void registerClientCommands(RegisterClientCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("yhysm")
				.then(Commands.literal("status")
						.executes(YSMClientCompat::showStatus))
				.then(Commands.literal("models")
						.executes(YSMClientCompat::showLoadedModels))
				.then(Commands.literal("reset")
						.executes(ctx -> {
							TYPE_DEBUG_OVERRIDES.clear();
							ENTITY_DEBUG_OVERRIDES.clear();
							ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] Debug render mappings reset."));
							return 1;
						}))
				.then(Commands.literal("debug")
						.then(Commands.literal("on")
								.executes(ctx -> setDebugTarget(ctx, getPointedEntity()))
								.then(Commands.argument("entities", ENTITY_TARGET_ARGUMENT)
										.suggests(TARGET_ENTITY_SUGGESTIONS)
										.executes(ctx -> setDebugTarget(ctx, getFirstResolvedEntity(ctx)))))
						.then(Commands.literal("off")
								.executes(YSMClientCompat::disableDebug))
						.then(Commands.literal("status")
								.executes(YSMClientCompat::showDebugStatus))
						.then(Commands.literal("inspect")
								.executes(ctx -> inspectDebugTarget(ctx, getPointedEntityOrSelected()))
								.then(Commands.argument("entities", ENTITY_TARGET_ARGUMENT)
										.suggests(TARGET_ENTITY_SUGGESTIONS)
										.executes(ctx -> inspectDebugTarget(ctx, getFirstResolvedEntity(ctx))))))
				.then(Commands.literal("type")
						.then(Commands.literal("set")
								.then(Commands.argument("entity_type", ResourceLocationArgument.id())
										.suggests(ENTITY_SUGGESTIONS)
										.then(Commands.argument("model", YSM_ID_ARGUMENT)
												.suggests(MODEL_SUGGESTIONS)
												.executes(ctx -> setTypeMapping(ctx, TEXTURE_DEFAULT))
												.then(Commands.argument("texture", YSM_ID_ARGUMENT)
														.executes(ctx -> setTypeMapping(ctx, getYsmId(ctx, "texture")))))))
						.then(Commands.literal("off")
								.then(Commands.argument("entity_type", ResourceLocationArgument.id())
										.suggests(ENTITY_SUGGESTIONS)
										.executes(ctx -> setTypeDisabled(ctx))))
						.then(Commands.literal("unset")
								.then(Commands.argument("entity_type", ResourceLocationArgument.id())
										.suggests(ENTITY_SUGGESTIONS)
										.executes(ctx -> unsetTypeMapping(ctx)))))
				.then(Commands.literal("entity")
						.then(Commands.literal("set")
								.then(Commands.argument("entities", ENTITY_TARGET_ARGUMENT)
										.suggests(TARGET_ENTITY_SUGGESTIONS)
										.then(Commands.argument("model", YSM_ID_ARGUMENT)
												.suggests(MODEL_SUGGESTIONS)
												.executes(ctx -> setEntityMapping(ctx, TEXTURE_DEFAULT))
												.then(Commands.argument("texture", YSM_ID_ARGUMENT)
														.executes(ctx -> setEntityMapping(ctx, getYsmId(ctx, "texture")))))))
						.then(Commands.literal("off")
								.then(Commands.argument("entities", ENTITY_TARGET_ARGUMENT)
										.suggests(TARGET_ENTITY_SUGGESTIONS)
										.executes(ctx -> setEntityDisabled(ctx))))
						.then(Commands.literal("unset")
								.then(Commands.argument("entities", ENTITY_TARGET_ARGUMENT)
										.suggests(TARGET_ENTITY_SUGGESTIONS)
										.executes(ctx -> unsetEntityMapping(ctx))))));
	}

	private static int showStatus(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		source.sendSystemMessage(Component.literal("[YH/YSM] Yes Steve Model loaded: " + LOADED));
		source.sendSystemMessage(Component.literal("[YH/YSM] Default mappings:"));
		YSMCompatConfig.defaultBindings().forEach((entityId, binding) -> source.sendSystemMessage(Component.literal(formatStatusLine(entityId, binding))));
		if (TYPE_DEBUG_OVERRIDES.isEmpty()) {
			source.sendSystemMessage(Component.literal("[YH/YSM] Type overrides: none"));
		} else {
			source.sendSystemMessage(Component.literal("[YH/YSM] Type overrides:"));
			TYPE_DEBUG_OVERRIDES.forEach((entityId, binding) -> source.sendSystemMessage(Component.literal(formatStatusLine(entityId, binding))));
		}
		if (ENTITY_DEBUG_OVERRIDES.isEmpty()) {
			source.sendSystemMessage(Component.literal("[YH/YSM] Entity overrides: none"));
		} else {
			source.sendSystemMessage(Component.literal("[YH/YSM] Entity overrides:"));
			ENTITY_DEBUG_OVERRIDES.forEach((uuid, binding) -> source.sendSystemMessage(Component.literal(formatStatusLine(uuid, binding))));
		}
		return 1;
	}

	private static int showLoadedModels(CommandContext<CommandSourceStack> ctx) {
		List<String> ids = loadedModelIds();
		ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] Loaded YSM model ids (" + ids.size() + "):"));
		for (String id : ids) {
			ctx.getSource().sendSystemMessage(Component.literal("  " + id));
		}
		return ids.size();
	}

	private static int setDebugTarget(CommandContext<CommandSourceStack> ctx, Entity entity) {
		if (!(entity instanceof LivingEntity living)) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] Point at a living entity or pass a visible client entity selector/UUID."));
			return 0;
		}
		debugTarget = living.getUUID();
		debugOverlay = true;
		clearYsmDebug();
		ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] Debug target: " + entityDebugName(living)));
		return 1;
	}

	private static int disableDebug(CommandContext<CommandSourceStack> ctx) {
		debugOverlay = false;
		debugTarget = null;
		clearYsmDebug();
		ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] Debug overlay disabled."));
		return 1;
	}

	private static int showDebugStatus(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] Debug overlay: " + debugOverlay));
		ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] Target: " + (debugTarget == null ? "none" : debugTarget)));
		Entity entity = getDebugTargetEntity();
		if (entity != null) {
			return inspectDebugTarget(ctx, entity);
		}
		return 1;
	}

	private static int inspectDebugTarget(CommandContext<CommandSourceStack> ctx, Entity entity) {
		if (entity == null) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] No visible debug target. Point at an entity or pass a selector/UUID."));
			return 0;
		}
		for (DebugLine line : collectDebugLines(entity)) {
			ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] " + line.label() + ": " + line.value()));
		}
		return 1;
	}

	public static void renderDebugOverlay(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
		if (!debugOverlay || Minecraft.getInstance().screen != null) {
			return;
		}
		Entity entity = getDebugTargetEntity();
		if (entity == null) {
			return;
		}
		List<DebugLine> lines = collectDebugLines(entity);
		if (lines.isEmpty()) {
			return;
		}
		Font font = gui.getFont();
		int labelWidth = 0;
		int valueWidth = 0;
		for (DebugLine line : lines) {
			labelWidth = Math.max(labelWidth, font.width(line.label()));
			valueWidth = Math.max(valueWidth, font.width(line.value()));
		}
		int rowHeight = font.lineHeight + 2;
		int x = 5;
		int y = 5;
		int panelWidth = Math.min(width - 10, labelWidth + valueWidth + 22);
		for (int i = 0; i < lines.size(); i++) {
			DebugLine line = lines.get(i);
			graphics.fill(x - 2, y - 1, x + panelWidth, y + rowHeight - 1, i % 2 == 0 ? DEBUG_BG_A : DEBUG_BG_B);
			graphics.drawString(font, line.label(), x, y, DEBUG_LABEL_COLOR, false);
			graphics.drawString(font, line.value(), x + labelWidth + 12, y, DEBUG_TEXT_COLOR, false);
			y += rowHeight;
			if (y + rowHeight > height - 5) {
				break;
			}
		}
	}

	private static List<DebugLine> collectDebugLines(Entity entity) {
		List<DebugLine> lines = new ArrayList<>();
		ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
		lines.add(new DebugLine("entity", entityDebugName(entity)));
		lines.add(new DebugLine("type", String.valueOf(typeId)));
		lines.add(new DebugLine("uuid", entity.getUUID().toString()));
		if (entity instanceof GeneralYoukaiEntity youkai) {
			BindingResolution resolution = resolveBindingWithSource(youkai);
			RenderBinding binding = resolution.binding();
			RenderRequest request = resolveRenderRequest(youkai);
			lines.add(new DebugLine("binding", formatBinding(resolution.source(), binding)));
			String modelId = request != null ? request.modelId() : binding != null && binding.enabled() ? binding.modelId() : "";
			lines.add(new DebugLine("yh.render", request == null ? "none" : request.modelId() + " / " + request.textureName()));
			lines.add(new DebugLine("yh.spellYsm", youkai.describeYsmRenderOverride()));
			lines.add(new DebugLine("yh.hint", request == null ? "null" : String.valueOf(request.animationHint())));
			lines.add(new DebugLine("yh.expression", isAngryExpression(youkai) ? "angry" : "none"));
			if (!modelId.isBlank()) {
				lines.add(new DebugLine("yh.expressionMap", YSMCompatConfig.debugExpressionMapping(modelId, "angry")));
			}
			lines.add(new DebugLine("yh.flags", formatYoukaiFlags(youkai)));
			lines.add(new DebugLine("yh.motion", formatMotion(youkai.getDeltaMovement())));
			lines.add(new DebugLine("yh.target", youkai.getTarget() == null ? "none" : entityDebugName(youkai.getTarget())));
		} else if (entity instanceof LivingEntity living) {
			BindingResolution resolution = resolveBindingWithSource(living);
			RenderBinding binding = resolution.binding();
			RenderRequest request = resolveRenderRequest(living);
			lines.add(new DebugLine("binding", formatBinding(resolution.source(), binding)));
			lines.add(new DebugLine("yh.render", request == null ? "none" : request.modelId() + " / " + request.textureName()));
			lines.add(new DebugLine("yh.hint", request == null ? "null" : String.valueOf(request.animationHint())));
			if (living instanceof YsmRenderOverrideTarget target) {
				lines.add(new DebugLine("yh.spellYsm", target.describeYsmRenderOverride()));
			}
		}
		if (entity instanceof LivingEntity living) {
			lines.addAll(collectYsmDebugLines(living));
		}
		return lines;
	}

	private static List<DebugLine> collectYsmDebugLines(LivingEntity entity) {
		List<DebugLine> lines = new ArrayList<>();
		if (!LOADED) {
			lines.add(new DebugLine("ysm.loaded", "false"));
			return lines;
		}
		Map<String, String> snapshot = getYsmDebugSnapshot(entity);
		snapshot.forEach((key, value) -> lines.add(new DebugLine("ysm." + key, value)));
		return lines;
	}

	private static String formatBinding(String source, RenderBinding binding) {
		if (binding == null) {
			return source + " -> none";
		}
		if (!binding.enabled()) {
			return source + " -> off";
		}
		return source + " -> " + binding.modelId() + " / " + binding.textureName();
	}

	private static boolean isAngryExpression(LivingEntity entity) {
		if (entity instanceof GeneralYoukaiEntity youkai) {
			return youkai.isAggressive() ||
					youkai.getTarget() != null ||
					youkai instanceof BossYoukaiEntity boss && boss.isChaotic();
		}
		return entity.hurtTime > 0;
	}

	private static String formatYoukaiFlags(GeneralYoukaiEntity entity) {
		boolean boss = entity instanceof BossYoukaiEntity;
		boolean chaotic = entity instanceof BossYoukaiEntity bossEntity && bossEntity.isChaotic();
		return "ground=" + entity.onGround() +
				", flying=" + entity.isFlying() +
				", noGravity=" + entity.isNoGravity() +
				", aggressive=" + entity.isAggressive() +
				", boss=" + boss +
				", chaotic=" + chaotic +
				", hurt=" + entity.hurtTime;
	}

	private static String formatMotion(Vec3 motion) {
		double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
		return String.format(Locale.ROOT, "x=%.3f y=%.3f z=%.3f h=%.3f", motion.x, motion.y, motion.z, horizontal);
	}

	private static String entityDebugName(Entity entity) {
		return entity.getDisplayName().getString() + " (" + entity.getId() + ")";
	}

	private static String formatStatusLine(ResourceLocation entityId, RenderBinding binding) {
		if (!binding.enabled()) {
			return "  " + entityId + " -> off";
		}
		return "  " + entityId + " -> " + binding.modelId() + " / " + binding.textureName();
	}

	private static String formatStatusLine(UUID uuid, RenderBinding binding) {
		if (!binding.enabled()) {
			return "  " + uuid + " -> off";
		}
		return "  " + uuid + " -> " + binding.modelId() + " / " + binding.textureName();
	}

	private static int setTypeMapping(CommandContext<CommandSourceStack> ctx, String textureName) {
		ResourceLocation entityId = parseEntityType(ctx);
		if (entityId == null) {
			return 0;
		}
		String modelId = getYsmId(ctx, "model");
		if (modelId.isBlank() || textureName.isBlank()) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] Model and texture must not be blank."));
			return 0;
		}
		TYPE_DEBUG_OVERRIDES.put(entityId, RenderBinding.enabled(modelId, textureName));
		ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] type " + entityId + " -> " + modelId + " / " + textureName));
		return 1;
	}

	private static int setTypeDisabled(CommandContext<CommandSourceStack> ctx) {
		ResourceLocation entityId = parseEntityType(ctx);
		if (entityId == null) {
			return 0;
		}
		TYPE_DEBUG_OVERRIDES.put(entityId, RenderBinding.disabled());
		ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] type " + entityId + " YSM rendering disabled."));
		return 1;
	}

	private static int unsetTypeMapping(CommandContext<CommandSourceStack> ctx) {
		ResourceLocation entityId = parseEntityType(ctx);
		if (entityId == null) {
			return 0;
		}
		TYPE_DEBUG_OVERRIDES.remove(entityId);
		ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] type " + entityId + " uses its default mapping."));
		return 1;
	}

	private static int setEntityMapping(CommandContext<CommandSourceStack> ctx, String textureName) {
		List<Entity> entities = resolveClientEntities(ctx);
		if (entities.isEmpty()) {
			return 0;
		}
		String modelId = getYsmId(ctx, "model");
		if (modelId.isBlank() || textureName.isBlank()) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] Model and texture must not be blank."));
			return 0;
		}
		for (Entity entity : entities) {
			ENTITY_DEBUG_OVERRIDES.put(entity.getUUID(), RenderBinding.enabled(modelId, textureName));
		}
		ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] " + entities.size() + " entity override(s) -> " + modelId + " / " + textureName));
		return entities.size();
	}

	private static int setEntityDisabled(CommandContext<CommandSourceStack> ctx) {
		List<Entity> entities = resolveClientEntities(ctx);
		if (entities.isEmpty()) {
			return 0;
		}
		for (Entity entity : entities) {
			ENTITY_DEBUG_OVERRIDES.put(entity.getUUID(), RenderBinding.disabled());
		}
		ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] " + entities.size() + " entity override(s) disabled."));
		return entities.size();
	}

	private static int unsetEntityMapping(CommandContext<CommandSourceStack> ctx) {
		List<Entity> entities = resolveClientEntities(ctx);
		if (entities.isEmpty()) {
			return 0;
		}
		for (Entity entity : entities) {
			ENTITY_DEBUG_OVERRIDES.remove(entity.getUUID());
		}
		ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] " + entities.size() + " entity override(s) removed."));
		return entities.size();
	}

	private static ResourceLocation parseEntityType(CommandContext<CommandSourceStack> ctx) {
		ResourceLocation entityId = ResourceLocationArgument.getId(ctx, "entity_type");
		if (!ForgeRegistries.ENTITY_TYPES.containsKey(entityId)) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] Unknown entity type: " + entityId));
			return null;
		}
		if (!Objects.equals(entityId.getNamespace(), YoukaisHomecoming.MODID)) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] Entity type is not from Youkai Homecoming: " + entityId));
			return null;
		}
		return entityId;
	}

	private static Entity getPointedEntity() {
		HitResult hitResult = Minecraft.getInstance().hitResult;
		if (hitResult instanceof EntityHitResult entityHitResult) {
			return entityHitResult.getEntity();
		}
		return null;
	}

	private static Entity getPointedEntityOrSelected() {
		Entity pointed = getPointedEntity();
		return pointed != null ? pointed : getDebugTargetEntity();
	}

	private static Entity getDebugTargetEntity() {
		if (debugTarget == null) {
			return null;
		}
		Minecraft minecraft = Minecraft.getInstance();
		for (Entity entity : collectClientEntities(minecraft)) {
			if (entity.getUUID().equals(debugTarget)) {
				return entity;
			}
		}
		return null;
	}

	private static Entity getFirstResolvedEntity(CommandContext<CommandSourceStack> ctx) {
		List<Entity> entities = resolveClientEntities(ctx);
		return entities.isEmpty() ? null : entities.get(0);
	}

	private static String getYsmId(CommandContext<CommandSourceStack> ctx, String name) {
		return ctx.getArgument(name, String.class);
	}

	private static List<Entity> resolveClientEntities(CommandContext<CommandSourceStack> ctx) {
		String target = ctx.getArgument("entities", String.class);
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] No client level is loaded."));
			return List.of();
		}
		if (target.startsWith("@")) {
			return resolveClientSelector(ctx, target, minecraft);
		}
		try {
			UUID uuid = UUID.fromString(target);
			for (Entity entity : collectClientEntities(minecraft)) {
				if (entity.getUUID().equals(uuid)) {
					return List.of(entity);
				}
			}
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] Entity UUID is not visible on the client: " + uuid));
			return List.of();
		} catch (IllegalArgumentException ignored) {
			for (Entity entity : collectClientEntities(minecraft)) {
				if (entity.getName().getString().equals(target)) {
					return List.of(entity);
				}
			}
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] Unsupported entity target or invisible entity: " + target));
			return List.of();
		}
	}

	private static List<Entity> resolveClientSelector(CommandContext<CommandSourceStack> ctx, String target, Minecraft minecraft) {
		char selector = target.length() > 1 ? target.charAt(1) : '\0';
		Map<String, String> options = parseSelectorOptions(target);
		List<Entity> result = new ArrayList<>();
		switch (selector) {
			case 's' -> {
				if (minecraft.player != null) {
					result.add(minecraft.player);
				}
			}
			case 'p' -> {
				for (Entity entity : collectClientEntities(minecraft)) {
					if (entity instanceof net.minecraft.world.entity.player.Player) {
						result.add(entity);
					}
				}
				sortEntities(result, "nearest", minecraft);
				limitEntities(result, 1);
			}
			case 'a' -> {
				for (Entity entity : collectClientEntities(minecraft)) {
					if (entity instanceof net.minecraft.world.entity.player.Player) {
						result.add(entity);
					}
				}
			}
			case 'e' -> result.addAll(collectClientEntities(minecraft));
			default -> {
				ctx.getSource().sendFailure(Component.literal("[YH/YSM] Unsupported client selector: @" + selector));
				return List.of();
			}
		}
		filterByType(result, options.get("type"));
		sortEntities(result, options.get("sort"), minecraft);
		if (options.containsKey("limit")) {
			try {
				limitEntities(result, Math.max(0, Integer.parseInt(options.get("limit"))));
			} catch (NumberFormatException ex) {
				ctx.getSource().sendFailure(Component.literal("[YH/YSM] Invalid selector limit: " + options.get("limit")));
				return List.of();
			}
		}
		if (result.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] No visible client entities matched: " + target));
		}
		return result;
	}

	private static List<Entity> collectClientEntities(Minecraft minecraft) {
		Map<UUID, Entity> entities = new LinkedHashMap<>();
		if (minecraft.level != null) {
			for (Entity entity : minecraft.level.entitiesForRendering()) {
				entities.put(entity.getUUID(), entity);
			}
			for (Entity entity : minecraft.level.players()) {
				entities.put(entity.getUUID(), entity);
			}
		}
		if (minecraft.player != null) {
			entities.put(minecraft.player.getUUID(), minecraft.player);
		}
		return new ArrayList<>(entities.values());
	}

	private static Map<String, String> parseSelectorOptions(String target) {
		int start = target.indexOf('[');
		int end = target.lastIndexOf(']');
		if (start < 0 || end <= start) {
			return Map.of();
		}
		Map<String, String> options = new LinkedHashMap<>();
		for (String part : target.substring(start + 1, end).split(",")) {
			int equals = part.indexOf('=');
			if (equals > 0) {
				options.put(part.substring(0, equals).trim(), part.substring(equals + 1).trim());
			}
		}
		return options;
	}

	private static void filterByType(List<Entity> entities, String typeOption) {
		if (typeOption == null || typeOption.isBlank()) {
			return;
		}
		boolean negate = typeOption.startsWith("!");
		String expected = negate ? typeOption.substring(1) : typeOption;
		entities.removeIf(entity -> {
			ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
			boolean matches = id != null && id.toString().equals(expected);
			return negate ? matches : !matches;
		});
	}

	private static void sortEntities(List<Entity> entities, String sort, Minecraft minecraft) {
		if (sort == null || sort.isBlank() || "arbitrary".equals(sort)) {
			return;
		}
		Entity origin = minecraft.getCameraEntity() != null ? minecraft.getCameraEntity() : minecraft.player;
		if (origin == null) {
			return;
		}
		Comparator<Entity> comparator = Comparator.comparingDouble(origin::distanceToSqr);
		if ("furthest".equals(sort)) {
			comparator = comparator.reversed();
		} else if (!"nearest".equals(sort)) {
			return;
		}
		entities.sort(comparator);
	}

	private static void limitEntities(List<Entity> entities, int limit) {
		while (entities.size() > limit) {
			entities.remove(entities.size() - 1);
		}
	}

	private static class TokenArgument implements ArgumentType<String> {

		private final SimpleCommandExceptionType emptyInput;
		private final Collection<String> examples;

		private TokenArgument(String errorMessage, Collection<String> examples) {
			this.emptyInput = new SimpleCommandExceptionType(new LiteralMessage(errorMessage));
			this.examples = examples;
		}

		@Override
		public String parse(StringReader reader) throws CommandSyntaxException {
			int start = reader.getCursor();
			while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
				reader.skip();
			}
			if (reader.getCursor() == start) {
				throw emptyInput.createWithContext(reader);
			}
			return reader.getString().substring(start, reader.getCursor());
		}

		@Override
		public Collection<String> getExamples() {
			return examples;
		}
	}

	private record BindingResolution(RenderBinding binding, String source) {
	}

	private record RenderRequest(String modelId, String textureName, String animationHint) {
	}

	private record DebugLine(String label, String value) {
	}

	private record CachedList(List<String> values, long loadedAt) {
	}

	private record CachedString(String value, long loadedAt) {
	}
}
