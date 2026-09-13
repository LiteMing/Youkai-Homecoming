package dev.xkmc.youkaishomecoming.compat.llmcore;

import com.electronwill.nightconfig.core.CommentedConfig;
import dev.xkmc.youkaishomecoming.content.spell.SpellTestBootstrap;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellJsonChecker;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellJsonCheckerTest;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Real game checker with controlled asynchronous provider responses; no remote calls. */
public final class YhSpellGenerationAgentTest {
	private static final String VALID = SpellJsonCheckerTest.spell("{\"type\":\"noop\"}");
	private static final String INVALID = SpellJsonCheckerTest.spell("{\"type\":\"unknown_action\"}");
	private static int checks;

	public static void main(String[] args) throws Exception {
		if (SpellTestBootstrap.enter(YhSpellGenerationAgentTest.class, args)) return;
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		var config = CommentedConfig.inMemory();
		YHModConfig.COMMON_SPEC.correct(config);
		YHModConfig.COMMON_SPEC.setConfig(config);
		check("default allows two revisions", YHModConfig.COMMON.spellAiMaxRevisions.get() == 2);
		List<String> drafts = new ArrayList<>(), feedback = new ArrayList<>();
		var first = run(2, drafts, feedback, YhLlmCoreBridge.Result.success(VALID));
		check("valid first draft ends the loop and keeps exact text", drafts.size() == 1 && first.success() && first.content().equals(VALID) && first.error().isEmpty());
		drafts.clear(); feedback.clear();
		String scarlet = SpellJsonCheckerTest.scarletGungnirDraft();
		var reportedDraft = run(2, drafts, feedback, YhLlmCoreBridge.Result.success(scarlet));
		check("reported nested-condition draft is delivered unchanged without a wasted repair", drafts.size() == 1
				&& reportedDraft.success() && reportedDraft.content().equals(scarlet) && reportedDraft.error().isEmpty());
		drafts.clear(); feedback.clear();
		var repaired = run(2, drafts, feedback, YhLlmCoreBridge.Result.success(INVALID), YhLlmCoreBridge.Result.success(VALID));
		check("checker feedback triggers one revision", drafts.size() == 2 && repaired.content().equals(VALID) && repaired.error().isEmpty());
		check("revision receives previous full draft and real node diagnostics", drafts.get(1).equals(INVALID)
				&& feedback.get(1).contains("unknown_action") && feedback.get(1).contains("on_tick"));
		drafts.clear(); feedback.clear();
		var exhausted = run(2, drafts, feedback, YhLlmCoreBridge.Result.success(INVALID), YhLlmCoreBridge.Result.success(INVALID), YhLlmCoreBridge.Result.success(INVALID));
		check("two revisions mean three requests at most", drafts.size() == 3);
		check("exhaustion delivers the draft and diagnostics", exhausted.success() && exhausted.content().equals(INVALID) && exhausted.error().contains("unknown_action"));
		drafts.clear(); feedback.clear();
		var single = run(0, drafts, feedback, YhLlmCoreBridge.Result.success(INVALID));
		check("zero revisions still reports checker feedback", drafts.size() == 1 && single.success() && !single.error().isEmpty());
		drafts.clear(); feedback.clear();
		var failure = run(2, drafts, feedback, YhLlmCoreBridge.Result.failure("provider unavailable"));
		check("first provider failure does not retry or create a clipboard draft", drafts.size() == 1 && !failure.success() && failure.content().isEmpty());
		drafts.clear(); feedback.clear();
		var interrupted = run(2, drafts, feedback, YhLlmCoreBridge.Result.success(INVALID), YhLlmCoreBridge.Result.failure("provider unavailable"));
		check("provider failure during repair keeps the draft and both diagnostics", interrupted.success() && interrupted.content().equals(INVALID)
				&& interrupted.error().contains("unknown_action") && interrupted.error().contains("provider unavailable"));
		drafts.clear(); feedback.clear();
		var empty = run(0, drafts, feedback, YhLlmCoreBridge.Result.success(""));
		check("empty result cannot clear the clipboard", !empty.success() && empty.content().isEmpty());
		drafts.clear(); feedback.clear();
		var blankRepair = run(1, drafts, feedback, YhLlmCoreBridge.Result.success(INVALID), YhLlmCoreBridge.Result.success(""));
		check("blank repair cannot erase the preceding draft", blankRepair.success() && blankRepair.content().equals(INVALID));
		drafts.clear(); feedback.clear();
		String large = "x".repeat(SpellJsonChecker.MAX_JSON_LENGTH + 1);
		var tooLarge = run(2, drafts, feedback, YhLlmCoreBridge.Result.success(large));
		check("over-capacity draft is preserved without an unbudgeted repair", drafts.size() == 1 && tooLarge.content().equals(large) && !tooLarge.error().isBlank());
		asynchronousAndDisconnect();
		System.out.println("YhSpellGenerationAgentTest: " + checks + " checks passed");
	}

	private static YhLlmCoreBridge.Result run(int revisions, List<String> drafts, List<String> feedback,
			YhLlmCoreBridge.Result... replies) throws Exception {
		return YhSpellGenerationAgent.generate(revisions, (previous, diagnostic) -> {
			drafts.add(previous); feedback.add(diagnostic);
			if (drafts.size() > replies.length) throw new AssertionError("unexpected provider request");
			return CompletableFuture.completedFuture(replies[drafts.size() - 1]);
		}, () -> true).get(10, TimeUnit.SECONDS);
	}

	private static void asynchronousAndDisconnect() throws Exception {
		var active = new AtomicBoolean(true);
		var calls = new AtomicInteger();
		var sent = new CountDownLatch(1);
		var provider = new CompletableFuture<YhLlmCoreBridge.Result>();
		var task = YhSpellGenerationAgent.generate(2, (previous, feedback) -> {
			calls.incrementAndGet(); sent.countDown(); return provider;
		}, active::get);
		check("background request is submitted", sent.await(5, TimeUnit.SECONDS));
		check("generation returns while provider is pending", !task.isDone());
		active.set(false);
		provider.complete(YhLlmCoreBridge.Result.success(INVALID));
		check("disconnect suppresses additional repair calls", !task.get(5, TimeUnit.SECONDS).success() && calls.get() == 1);
		var failedFuture = YhSpellGenerationAgent.generate(2, (previous, feedback) -> CompletableFuture.failedFuture(new IllegalStateException("fixture exception")), () -> true);
		check("exceptional completion becomes a reported failure", !failedFuture.get(5, TimeUnit.SECONDS).success());
	}

	private static void check(String label, boolean pass) { if (!pass) throw new AssertionError(label); checks++; }
}
