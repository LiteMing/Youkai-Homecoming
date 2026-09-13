package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.electronwill.nightconfig.core.CommentedConfig;
import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Actual packet serialization and delivery callbacks, without a game window or system clipboard. */
public final class SpellAiDeliveryTest {
	private static int checks;

	public static void main(String[] args) {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		var config = CommentedConfig.inMemory();
		YHModConfig.COMMON_SPEC.correct(config);
		YHModConfig.COMMON_SPEC.setConfig(config);
		check("default output budget is at least 10000", YHModConfig.COMMON.spellAiMaxOutputTokens.get() >= 10_000);
		unchangedText();
		chunkedResults();
		interleavedResults();
		failedRequest();
		requestSnapshots();
		comparison();
		System.out.println("SpellAiDeliveryTest: " + checks + " checks passed");
	}

	private static void unchangedText() {
		for (String text : List.of("{\"type\":\"experimental_fire\"}", "```json\n{\"partial\":true}\n```", "not JSON", "{\"on_tick\":[")) {
			var clipboard = new AtomicReference<>("previous clipboard");
			var chat = new ArrayList<Component>();
			var collector = new SpellAiGenerateResultToClient.Collector();
			var packet = new SpellAiGenerateResultToClient(1, true, text, "");
			SpellPreviewClientHandler.deliverAiResult(collector.accept(roundTrip(packet)), clipboard::set, chat::add);
			check("the importer receives unmodified generator text", text.equals(clipboard.get()));
			check("success is announced without an editor screen", chat.size() == 1 && key(chat.get(0)).endsWith(".ai_copied"));
		}
	}

	private static void chunkedResults() {
		int limit = SpellPreviewChunkToClient.MAX_CHUNK_CHARS;
		String text = "a".repeat(limit - 1) + "\uD83C\uDF19" + "符卡".repeat(40_000);
		var packets = new SpellAiGenerateResultToClient(2, true, text, "").split();
		check("long output uses multiple packets", packets.size() > 1);
		var collector = new SpellAiGenerateResultToClient.Collector();
		var clipboard = new AtomicReference<>("previous clipboard");
		var chat = new ArrayList<Component>();
		for (int i = 0; i < packets.size(); i++) {
			var packet = packets.get(i);
			check("each result string fits the transport limit", packet.json.length() <= limit);
			var complete = collector.accept(roundTrip(packet));
			if (complete != null) SpellPreviewClientHandler.deliverAiResult(complete, clipboard::set, chat::add);
			if (i + 1 < packets.size()) check("partial transfers do not overwrite the clipboard or announce success",
					clipboard.get().equals("previous clipboard") && chat.isEmpty());
		}
		check("serialized long Unicode result survives exactly", text.equals(clipboard.get()));
		check("complete transfer announces once", chat.size() == 1);
		collector.accept(roundTrip(packets.get(0)));
		collector.clear();
		for (int i = 1; i < packets.size(); i++) check("logout discards incomplete transfers", collector.accept(roundTrip(packets.get(i))) == null);
	}

	private static void interleavedResults() {
		String a = "A".repeat(40_000), b = "B".repeat(40_000);
		var first = new SpellAiGenerateResultToClient(3, true, a, "").split();
		var second = new SpellAiGenerateResultToClient(4, true, b, "").split();
		var collector = new SpellAiGenerateResultToClient.Collector();
		check("requests have separate transfer identities", first.get(0).transferId != second.get(0).transferId);
		check("first request remains pending", collector.accept(roundTrip(first.get(0))) == null);
		check("second request remains pending", collector.accept(roundTrip(second.get(0))) == null);
		check("second request completes with its own result", b.equals(collector.accept(roundTrip(second.get(1))).json));
		check("first request completes with its own result", a.equals(collector.accept(roundTrip(first.get(1))).json));
	}

	private static void failedRequest() {
		String error = "Provider unavailable. ".repeat(2000);
		var collector = new SpellAiGenerateResultToClient.Collector();
		var clipboard = new AtomicReference<>("keep this");
		var chat = new ArrayList<Component>();
		for (var packet : new SpellAiGenerateResultToClient(5, false, "", error).split()) {
			var complete = collector.accept(roundTrip(packet));
			if (complete != null) SpellPreviewClientHandler.deliverAiResult(complete, clipboard::set, chat::add);
		}
		check("failed request preserves existing clipboard", clipboard.get().equals("keep this"));
		check("failure is announced once", chat.size() == 1 && key(chat.get(0)).endsWith(".ai_failed"));
		check("failure details survive transport", ((TranslatableContents) chat.get(0).getContents()).getArgs()[0].equals(error));
	}

	private static void requestSnapshots() {
		SpellPreviewClientHandler.rememberAiRequest(10, "modify", "original A");
		SpellPreviewClientHandler.rememberAiRequest(11, "modify", "original B");
		var b = SpellPreviewClientHandler.completeAiRequest(new SpellAiGenerateResultToClient(11, true, "modified B", ""));
		var a = SpellPreviewClientHandler.completeAiRequest(new SpellAiGenerateResultToClient(10, true, "modified A", ""));
		check("out-of-order completion compares the matching request snapshot", b.before().equals("original B") && b.after().equals("modified B"));
		check("later workspace changes do not replace the original snapshot", a.before().equals("original A") && a.after().equals("modified A"));
		SpellPreviewClientHandler.rememberAiRequest(12, "create", "current workspace");
		check("creation does not claim to modify the current workspace", SpellPreviewClientHandler.completeAiRequest(new SpellAiGenerateResultToClient(12, true, "created", "")) == null);
		SpellPreviewClientHandler.rememberAiRequest(13, "modify", "original");
		check("failure releases its original without creating a comparison", SpellPreviewClientHandler.completeAiRequest(new SpellAiGenerateResultToClient(13, false, "", "error")) == null);
		SpellPreviewClientHandler.rememberAiRequest(14, "modify", "original");
		SpellPreviewClientHandler.onLogout(null);
		check("logout releases request snapshots", SpellPreviewClientHandler.completeAiRequest(new SpellAiGenerateResultToClient(14, true, "late", "")) == null);
	}

	private static void comparison() {
		check("formatting and key order do not count as edits", new SpellAiComparison("{\"speed\":1,\"count\":2}", "{ \"count\": 2, \"speed\": 1.0 }").changes().isEmpty());
		var field = new SpellAiComparison("{\"speed\":1}", "{\"speed\":2}").changes();
		check("modified field shows path and both values", field.size() == 1 && field.get(0).path().equals("$.speed") && field.get(0).before().equals("1") && field.get(0).after().equals("2"));
		var added = new SpellAiComparison("{}", "{\"speed\":2}").changes();
		check("new field is marked as added", added.size() == 1 && added.get(0).before() == null && added.get(0).after().equals("2"));
		var removed = new SpellAiComparison("{\"speed\":2}", "{}").changes();
		check("removed field retains its former value", removed.size() == 1 && removed.get(0).before().equals("2") && removed.get(0).after() == null);
		var inserted = new SpellAiComparison("[1,2,3,4]", "[1,9,8,2,3,4]").changes();
		check("inserting actions does not relabel all following actions", inserted.size() == 2 && inserted.stream().allMatch(c -> c.before() == null));
		var deleted = new SpellAiComparison("[1,9,8,2,3,4]", "[1,2,3,4]").changes();
		check("deleting actions retains unchanged anchors", deleted.size() == 2 && deleted.stream().allMatch(c -> c.after() == null));
		var duplicate = new SpellAiComparison("[1,1,2]", "[1,1,1,2]").changes();
		check("duplicate unchanged actions stay aligned", duplicate.size() == 1 && duplicate.get(0).before() == null);
		var partial = new SpellAiComparison("{\"speed\":1}", "not JSON").changes();
		check("unparseable text stays visible without a generation gate", partial.size() == 1 && partial.get(0).after().equals("not JSON"));
	}

	private static SpellAiGenerateResultToClient roundTrip(SpellAiGenerateResultToClient packet) {
		var buffer = new FriendlyByteBuf(Unpooled.buffer());
		try {
			packet.write(buffer);
			var decoded = SerialPacketBase.serial(SpellAiGenerateResultToClient.class, buffer);
			check("packet consumes its full encoded payload", buffer.readableBytes() == 0);
			return decoded;
		} finally { buffer.release(); }
	}

	private static String key(Component component) { return ((TranslatableContents) component.getContents()).getKey(); }
	private static void check(String label, boolean pass) { if (!pass) throw new AssertionError(label); checks++; }
}
