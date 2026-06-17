package dev.xkmc.youkaishomecoming.compat.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.xkmc.youkaishomecoming.content.entity.boss.BossYoukaiEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.GeneralYoukaiEntity;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class YSMClientCompat {

	private static final String MOD_ID = "yes_steve_model";
	private static final String MODEL_REMILIA = "yh/remilia";
	private static final String MODEL_FLANDRE = "yh/flandre";
	private static final String TEXTURE_DEFAULT = "default";
	private static final boolean LOADED = ModList.get().isLoaded(MOD_ID);
	private static final ResourceLocation REMILIA_ENTITY = YoukaisHomecoming.loc("remilia_scarlet");
	private static final Map<ResourceLocation, RenderBinding> DEFAULT_BINDINGS = Map.of(
			REMILIA_ENTITY, RenderBinding.enabled(MODEL_REMILIA, TEXTURE_DEFAULT)
	);
	private static final Map<ResourceLocation, RenderBinding> DEBUG_OVERRIDES = new LinkedHashMap<>();
	private static final SuggestionProvider<CommandSourceStack> ENTITY_SUGGESTIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(ForgeRegistries.ENTITY_TYPES.getKeys().stream()
					.filter(id -> Objects.equals(id.getNamespace(), YoukaisHomecoming.MODID))
					.map(ResourceLocation::toString), builder);
	private static final SuggestionProvider<CommandSourceStack> MODEL_SUGGESTIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Stream.of(MODEL_REMILIA, MODEL_FLANDRE), builder);

	private static Method renderMethod;
	private static boolean unavailable;

	public static boolean delegateRender(GeneralYoukaiEntity e, float yaw, float pTick, PoseStack pose, MultiBufferSource buffer, int light) {
		RenderBinding binding = resolveBinding(e);
		if (!LOADED || unavailable || binding == null) {
			return false;
		}
		Method method = getRenderMethod();
		if (method == null) {
			return false;
		}
		try {
			Object result = method.invoke(null, e, binding.modelId(), binding.textureName(), selectAnimation(e), yaw, pTick, pose, buffer, light);
			return result instanceof Boolean value && value;
		} catch (IllegalAccessException | InvocationTargetException ex) {
			unavailable = true;
			YoukaisHomecoming.LOGGER.warn("Failed to delegate youkai rendering to Yes Steve Model", ex);
			return false;
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

	private static RenderBinding resolveBinding(GeneralYoukaiEntity e) {
		ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
		if (entityId == null) {
			return null;
		}
		RenderBinding override = DEBUG_OVERRIDES.get(entityId);
		if (override != null) {
			return override.enabled() ? override : null;
		}
		return DEFAULT_BINDINGS.get(entityId);
	}

	private static String selectAnimation(GeneralYoukaiEntity e) {
		Vec3 motion = e.getDeltaMovement();
		double horizontalSpeedSqr = motion.x * motion.x + motion.z * motion.z;
		boolean flying = e.isFlying() || e.isNoGravity();
		if (flying) {
			return "fly";
		}
		if (!e.onGround()) {
			return null;
		}
		if (horizontalSpeedSqr > 0.0025) {
			return "walk";
		}
		if (e instanceof BossYoukaiEntity boss && boss.isChaotic()) {
			return "angry";
		}
		if (e.getTarget() != null) {
			return "angry";
		}
		return "calm";
	}

	@SubscribeEvent
	public static void registerClientCommands(RegisterClientCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("yhysm")
				.then(Commands.literal("status")
						.executes(YSMClientCompat::showStatus))
				.then(Commands.literal("reset")
						.executes(ctx -> {
							DEBUG_OVERRIDES.clear();
							ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] Debug render mappings reset."));
							return 1;
						}))
				.then(Commands.literal("unset")
						.then(Commands.argument("entity", StringArgumentType.word())
								.suggests(ENTITY_SUGGESTIONS)
								.executes(ctx -> {
									ResourceLocation entityId = parseEntityId(ctx);
									if (entityId == null) {
										return 0;
									}
									DEBUG_OVERRIDES.remove(entityId);
									ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] " + entityId + " uses its default mapping."));
									return 1;
								})))
				.then(Commands.literal("off")
						.then(Commands.argument("entity", StringArgumentType.word())
								.suggests(ENTITY_SUGGESTIONS)
								.executes(ctx -> {
									ResourceLocation entityId = parseEntityId(ctx);
									if (entityId == null) {
										return 0;
									}
									DEBUG_OVERRIDES.put(entityId, RenderBinding.disabled());
									ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] " + entityId + " YSM rendering disabled."));
									return 1;
								})))
				.then(Commands.literal("set")
						.then(Commands.argument("entity", StringArgumentType.word())
								.suggests(ENTITY_SUGGESTIONS)
								.then(Commands.argument("model", StringArgumentType.word())
										.suggests(MODEL_SUGGESTIONS)
										.executes(ctx -> setMapping(ctx, TEXTURE_DEFAULT))
										.then(Commands.argument("texture", StringArgumentType.word())
												.executes(ctx -> setMapping(ctx, StringArgumentType.getString(ctx, "texture"))))))));
	}

	private static int showStatus(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		source.sendSystemMessage(Component.literal("[YH/YSM] Yes Steve Model loaded: " + LOADED));
		source.sendSystemMessage(Component.literal("[YH/YSM] Default mappings:"));
		DEFAULT_BINDINGS.forEach((entityId, binding) -> source.sendSystemMessage(Component.literal(formatStatusLine(entityId, binding))));
		if (DEBUG_OVERRIDES.isEmpty()) {
			source.sendSystemMessage(Component.literal("[YH/YSM] Debug overrides: none"));
			return 1;
		}
		source.sendSystemMessage(Component.literal("[YH/YSM] Debug overrides:"));
		DEBUG_OVERRIDES.forEach((entityId, binding) -> source.sendSystemMessage(Component.literal(formatStatusLine(entityId, binding))));
		return 1;
	}

	private static String formatStatusLine(ResourceLocation entityId, RenderBinding binding) {
		if (!binding.enabled()) {
			return "  " + entityId + " -> off";
		}
		return "  " + entityId + " -> " + binding.modelId() + " / " + binding.textureName();
	}

	private static int setMapping(CommandContext<CommandSourceStack> ctx, String textureName) {
		ResourceLocation entityId = parseEntityId(ctx);
		if (entityId == null) {
			return 0;
		}
		String modelId = StringArgumentType.getString(ctx, "model");
		if (modelId.isBlank() || textureName.isBlank()) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] Model and texture must not be blank."));
			return 0;
		}
		DEBUG_OVERRIDES.put(entityId, RenderBinding.enabled(modelId, textureName));
		ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] " + entityId + " -> " + modelId + " / " + textureName));
		return 1;
	}

	private static ResourceLocation parseEntityId(CommandContext<CommandSourceStack> ctx) {
		String raw = StringArgumentType.getString(ctx, "entity");
		ResourceLocation entityId = ResourceLocation.tryParse(raw);
		if (entityId == null) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] Invalid entity id: " + raw));
			return null;
		}
		if (!ForgeRegistries.ENTITY_TYPES.containsKey(entityId)) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] Unknown entity type: " + entityId));
			return null;
		}
		return entityId;
	}

	private record RenderBinding(String modelId, String textureName, boolean enabled) {

		private static RenderBinding enabled(String modelId, String textureName) {
			return new RenderBinding(modelId, textureName, true);
		}

		private static RenderBinding disabled() {
			return new RenderBinding("", "", false);
		}
	}
}
