package dev.xkmc.youkaishomecoming.compat.llmcore;

import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Optional reflective bridge so YH remains runnable without llmcore-mod. */
public final class YhLlmCoreBridge {
	private static final String PURPOSE = "YH_SPELL_GENERATION";

	private YhLlmCoreBridge() {}

	public static CompletableFuture<Result> generate(ServerPlayer player, String prompt, String operation,
			String cardType, String currentJson) {
		if (!YHModConfig.COMMON.spellAiGenerationEnabled.get()) {
			return CompletableFuture.completedFuture(Result.failure("YH spell AI is disabled by server config"));
		}
		try {
			Class<?> runtimeType = Class.forName("vibe.liteming.llmcore.SharedLlmRuntime");
			Object optional = runtimeType.getMethod("current").invoke(null);
			Object orchestrator = optional.getClass().getMethod("orElse", Object.class).invoke(optional, (Object) null);
			if (orchestrator == null) return CompletableFuture.completedFuture(Result.failure("LLM Core is not installed or configured"));

			String header = YHModConfig.COMMON.spellAiHeaderPrompt.get();
			String report = YhSpellCapabilityReport.current().toJson().toString();
			String system = (header == null ? "" : header.trim())
					+ "\n" + packagedSystemPrompt()
					+ "\nRuntime capability report:\n" + report;
			String user = "operation=" + operation + " (create uses a new youkaishomecoming:ai_ id; modify preserves the current id)"
					+ "\ncard_type=" + cardType
					+ "\ncustom_request=" + prompt + "\ncurrent_json=" + (currentJson == null ? "" : currentJson);

			Class<?> messageType = Class.forName("vibe.liteming.llmcore.LlmMessage");
			Object systemMessage = messageType.getConstructor(String.class, String.class).newInstance("system", system);
			Object userMessage = messageType.getConstructor(String.class, String.class).newInstance("user", user);
			Class<?> contextType = Class.forName("vibe.liteming.llmcore.LlmRequestContext");
			Object context = contextType.getConstructor(String.class, String.class, String.class, String.class,
					String.class, String.class, String.class, boolean.class)
					.newInstance(UUID.randomUUID().toString(), PURPOSE, player.getUUID().toString(),
							"minecraft:player", player.getName().getString(), "yh-spell-editor", "", true);
			Class<?> requestType = Class.forName("vibe.liteming.llmcore.LlmRequest");
			Object request = requestType.getMethod("routed", List.class, contextType)
					.invoke(null, List.of(systemMessage, userMessage), context);
			try {
				Class<?> billingType = Class.forName("vibe.liteming.llmcore.LlmBillingContext");
				Object billing = billingType.getMethod("player", String.class, String.class, int.class, long.class)
						.invoke(null, player.getUUID().toString(), UUID.randomUUID().toString(), 1, 12000L);
				request = requestType.getMethod("withBillingContext", billingType).invoke(request, billing);
			} catch (ReflectiveOperationException ignored) {
				// Older llmcore builds can still be used; their request has no billing API.
			}
			Object future = orchestrator.getClass().getMethod("send", requestType).invoke(orchestrator, request);
			Method thenApply = future.getClass().getMethod("thenApply", java.util.function.Function.class);
			@SuppressWarnings("unchecked")
			CompletableFuture<Result> result = (CompletableFuture<Result>) thenApply.invoke(future,
				(java.util.function.Function<Object, Result>) response -> {
					try {
						boolean success = (boolean) response.getClass().getMethod("success").invoke(response);
						if (!success) return Result.failure(String.valueOf(response.getClass().getMethod("error").invoke(response)));
						return Result.success(String.valueOf(response.getClass().getMethod("content").invoke(response)));
					} catch (ReflectiveOperationException error) {
						return Result.failure(error.getMessage());
					}
				});
			return result;
		} catch (ReflectiveOperationException | RuntimeException error) {
			return CompletableFuture.completedFuture(Result.failure(error.getMessage()));
		}
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
