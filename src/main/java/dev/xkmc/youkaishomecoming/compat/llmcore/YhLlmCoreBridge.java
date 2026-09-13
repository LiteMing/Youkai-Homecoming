package dev.xkmc.youkaishomecoming.compat.llmcore;

import com.mojang.logging.LogUtils;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Optional reflective bridge so YH remains runnable without llmcore-mod. */
@Mod.EventBusSubscriber(modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class YhLlmCoreBridge {
	private static final String PURPOSE = "YH_SPELL_GENERATION";

	private YhLlmCoreBridge() {}

	@SubscribeEvent
	public static void commonSetup(FMLCommonSetupEvent event) {
		// The console must list the purpose before the first request, even when
		// generation is disabled or the shared server runtime is not installed yet.
		event.enqueueWork(YhLlmCoreBridge::registerPurpose);
	}

	static boolean registerPurpose() {
		try {
			Class<?> metaType = Class.forName("vibe.liteming.llmcore.PurposeMeta");
			Object meta = metaType.getConstructor(String.class, String.class, String.class,
					String.class, boolean.class).newInstance(PURPOSE, "YH Spell Generation",
					"Create or modify a draft danmaku spell card", YoukaisHomecoming.MODID, false);
			return (boolean) Class.forName("vibe.liteming.llmcore.PurposeRegistry")
					.getMethod("register", metaType).invoke(null, meta);
		} catch (ClassNotFoundException ignored) {
			return false;
		} catch (ReflectiveOperationException | LinkageError error) {
			LogUtils.getLogger().warn("Unable to register YH spell generation with LLM Core", error);
			return false;
		}
	}

	public static CompletableFuture<Result> generate(ServerPlayer player, String prompt, String operation,
			String cardType, String currentJson) {
		if (!YHModConfig.COMMON.spellAiGenerationEnabled.get()) {
			return CompletableFuture.completedFuture(Result.failure("YH spell AI is disabled by server config"));
		}
		try {
			registerPurpose();
			Class<?> runtimeType = Class.forName("vibe.liteming.llmcore.SharedLlmRuntime");
			Optional<?> runtime = (Optional<?>) runtimeType.getMethod("current").invoke(null);
			Object orchestrator = runtime.orElse(null);
			if (orchestrator == null) return CompletableFuture.completedFuture(Result.failure("LLM Core is not installed or configured"));

			String header = YHModConfig.COMMON.spellAiHeaderPrompt.get();
			String report = YhSpellCapabilityReport.current().toJson().toString();
			String system = (header == null ? "" : header.trim())
					+ "\n" + packagedSystemPrompt()
					+ "\nRuntime capability report:\n" + report;
			String user = "operation=" + operation + " (create uses a new youkaishomecoming:ai_ id; modify preserves the current id)"
					+ "\ncard_type=" + cardType
					+ "\ncustom_request=" + prompt + "\ncurrent_json=" + (currentJson == null ? "" : currentJson);

			Object request = createRequest(orchestrator, player.getUUID().toString(), player.getName().getString(), system, user);
			CompletableFuture<?> future = (CompletableFuture<?>) orchestrator.getClass()
					.getMethod("send", request.getClass()).invoke(orchestrator, request);
			return future.thenApply(response -> {
					try {
						boolean success = (boolean) response.getClass().getMethod("success").invoke(response);
						if (!success) return Result.failure(String.valueOf(response.getClass().getMethod("error").invoke(response)));
						return Result.success(String.valueOf(response.getClass().getMethod("content").invoke(response)));
					} catch (ReflectiveOperationException error) {
						return Result.failure(errorMessage(error));
					}
				});
		} catch (ClassNotFoundException error) {
			return CompletableFuture.completedFuture(Result.failure("LLM Core is not installed or its API is incompatible"));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
			return CompletableFuture.completedFuture(Result.failure(errorMessage(error)));
		}
	}

	/** Build the final routed request before asking its runtime for a billing ceiling. */
	static Object createRequest(Object orchestrator, String playerId, String playerName, String system, String user)
			throws ReflectiveOperationException {
		Class<?> messageType = Class.forName("vibe.liteming.llmcore.LlmMessage");
		Object systemMessage = messageType.getConstructor(String.class, String.class).newInstance("system", system);
		Object userMessage = messageType.getConstructor(String.class, String.class).newInstance("user", user);
		String requestId = UUID.randomUUID().toString();
		Class<?> contextType = Class.forName("vibe.liteming.llmcore.LlmRequestContext");
		Object context = contextType.getConstructor(String.class, String.class, String.class, String.class,
				String.class, String.class, String.class, boolean.class)
				.newInstance(requestId, PURPOSE, playerId, "minecraft:player", playerName, "yh-spell-editor", "", true);
		Class<?> requestType = Class.forName("vibe.liteming.llmcore.LlmRequest");
		Object request = requestType.getMethod("routed", List.class, contextType)
				.invoke(null, List.of(systemMessage, userMessage), context);
		// Count the configured provider/credential fallback chain and its actual
		// input/output reservation. Never silently send an unbilled request if the
		// installed billing API is incompatible or estimation fails.
		Object budget = orchestrator.getClass().getMethod("estimateWorstCaseBudget", requestType)
				.invoke(orchestrator, request);
		int maxCalls = Math.max(1, (int) budget.getClass().getMethod("maxCalls").invoke(budget));
		long maxTokens = Math.max(1L, (long) budget.getClass().getMethod("maxTokens").invoke(budget));
		Class<?> billingType = Class.forName("vibe.liteming.llmcore.LlmBillingContext");
		Object billing = billingType.getMethod("player", String.class, String.class, int.class, long.class)
				.invoke(null, playerId, requestId, maxCalls, maxTokens);
		return requestType.getMethod("withBillingContext", billingType).invoke(request, billing);
	}

	private static String errorMessage(Throwable error) {
		while (error instanceof InvocationTargetException && error.getCause() != null) error = error.getCause();
		return error.getMessage();
	}

	private static String packagedSystemPrompt() {
		try (InputStream stream = YhLlmCoreBridge.class.getResourceAsStream(
				"/data/youkaishomecoming/llm/spell_generation_system.txt")) {
			if (stream != null) return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
		} catch (Exception ignored) {
		}
		return "Return one JSON spell object only. The server validates the draft before use.";
	}

	public record Result(boolean success, String content, String error) {
		static Result success(String content) { return new Result(true, content, ""); }
		static Result failure(String error) { return new Result(false, "", error == null ? "LLM Core unavailable" : error); }
	}
}
