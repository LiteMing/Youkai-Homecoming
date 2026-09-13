package dev.xkmc.youkaishomecoming.compat.llmcore;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellJsonChecker;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

/** Bounded generation / editor-checker / revision loop; no game-state mutation. */
final class YhSpellGenerationAgent {

	private YhSpellGenerationAgent() {}

	static CompletableFuture<YhLlmCoreBridge.Result> generate(int maxRevisions,
			BiFunction<String, String, CompletableFuture<YhLlmCoreBridge.Result>> generator, BooleanSupplier active) {
		return next(generator, active, maxRevisions, "", "");
	}

	private static CompletableFuture<YhLlmCoreBridge.Result> next(
			BiFunction<String, String, CompletableFuture<YhLlmCoreBridge.Result>> generator,
			BooleanSupplier active, int revisionsLeft, String previous, String feedback) {
		// Keep parsing and any immediately-completed provider responses off the server tick.
		return CompletableFuture.completedFuture(previous).thenComposeAsync(ignored -> {
			if (!active.getAsBoolean()) return CompletableFuture.completedFuture(YhLlmCoreBridge.Result.failure("Player disconnected"));
			CompletableFuture<YhLlmCoreBridge.Result> request;
			try {
				request = generator.apply(previous, boundedFeedback(feedback));
			} catch (RuntimeException error) {
				request = CompletableFuture.failedFuture(error);
			}
			return request.handle((result, error) -> error == null && result != null ? result
					: YhLlmCoreBridge.Result.failure(error == null ? "Empty LLM response" : error.getMessage()));
		}).thenComposeAsync(result -> {
			if (!active.getAsBoolean()) return CompletableFuture.completedFuture(YhLlmCoreBridge.Result.failure("Player disconnected"));
			if (!result.success()) {
				return CompletableFuture.completedFuture(previous.isBlank() ? result
						: unfinished(previous, feedback + "\nAutomatic repair stopped: " + result.error()));
			}
			String draft = result.content();
			if (draft.length() > SpellJsonChecker.MAX_JSON_LENGTH) {
				return CompletableFuture.completedFuture(unfinished(draft,
						"Draft exceeds the Raw JSON editor's character limit; automatic repair stopped"));
			}
			var checked = SpellJsonChecker.check(draft);
			if (checked.clean()) return CompletableFuture.completedFuture(result);
			String diagnostic = draft.isBlank() && !feedback.isBlank()
					? "Empty LLM response.\n" + feedback : checked.feedback();
			// A blank response must not erase the preceding draft or the player's clipboard.
			String retained = draft.isBlank() ? previous : draft;
			if (revisionsLeft <= 0) {
				return CompletableFuture.completedFuture(retained.isBlank()
						? YhLlmCoreBridge.Result.failure(diagnostic)
						: unfinished(retained, "Automatic repair limit reached.\n" + diagnostic));
			}
			return next(generator, active, revisionsLeft - 1, retained, diagnostic);
		});
	}

	private static YhLlmCoreBridge.Result unfinished(String draft, String diagnostic) {
		return new YhLlmCoreBridge.Result(true, draft, diagnostic);
	}

	private static String boundedFeedback(String feedback) {
		if (feedback.length() <= SpellJsonChecker.MAX_JSON_LENGTH) return feedback;
		int end = SpellJsonChecker.MAX_JSON_LENGTH;
		if (Character.isHighSurrogate(feedback.charAt(end - 1))) end--;
		return feedback.substring(0, end);
	}
}
