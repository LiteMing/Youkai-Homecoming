package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.google.gson.JsonParser;
import dev.xkmc.youkaishomecoming.content.spell.SpellTestBootstrap;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Runs the real editor checker/Codec, without a world, certification, or a provider. */
public final class SpellJsonCheckerTest {
	private static int checks;

	public static void main(String[] args) throws Exception {
		if (SpellTestBootstrap.enter(SpellJsonCheckerTest.class, args)) return;
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		var config = CommentedConfig.inMemory();
		YHModConfig.COMMON_SPEC.correct(config);
		YHModConfig.COMMON_SPEC.setConfig(config);
		bundledExamples();
		for (String text : new String[]{"not JSON", "```json\n{}\n```", "{\"phases\":[", "[]"}) {
			check("unreadable text produces feedback", !SpellJsonChecker.check(text).clean()
					&& !SpellJsonChecker.check(text).feedback().isBlank());
		}
		String valid = spell("{\"type\":\"fire_danmaku\",\"bullet\":\"ball\",\"color\":\"red\",\"count\":4,\"speed\":0.4,\"lifetime\":60}");
		check("plain complete spell is clean", SpellJsonChecker.check(valid).clean());
		var unknownField = JsonParser.parseString(valid).getAsJsonObject();
		unknownField.addProperty("unrecognized_field", 3);
		var dropped = SpellJsonChecker.check(unknownField.toString());
		check("silently dropped fields are diagnosed by path", !dropped.clean() && dropped.feedback().contains("$.unrecognized_field"));
		var unknownNode = SpellJsonChecker.check(spell("{\"type\":\"noop\"},{\"type\":\"not_an_action\"},{\"type\":\"noop\"}"));
		check("unknown node can be recovered", !unknownNode.clean() && unknownNode.salvage() != null && unknownNode.salvage().brokenCount() == 1);
		var actions = unknownNode.salvage().definition().phases.values().iterator().next().onTick;
		check("recovery retains both working siblings", actions.size() == 3 && actions.get(1) instanceof SpellActions.BrokenAction);
		check("node feedback identifies its action path", unknownNode.feedback().contains("on_tick[1]") && unknownNode.feedback().contains("not_an_action"));
		var broken = SpellJsonChecker.check(spell("{\"type\":\"broken\",\"raw\":\"original fragment\",\"error\":\"bad node\"}"));
		check("existing placeholders request repair instead of claiming success", !broken.clean() && broken.salvage() != null);
		var invalidProvider = SpellJsonChecker.check(valid.replace("\"speed\":0.4", "\"speed\":\"missing_function(2)\""));
		check("invalid provider produces repair feedback", !invalidProvider.clean() && !invalidProvider.feedback().isBlank());
		check("experimental laser drafts are not subject to certification", SpellJsonChecker.check(spell(
				"{\"type\":\"fire_laser\",\"color\":\"red\",\"lifetime\":60,\"length\":10}")).clean());
		check("operator draft is not an approval gate", SpellJsonChecker.check(spell(
				"{\"type\":\"freeze_on_tick\",\"duration\":20}")).clean());
		check("decode alias for scale remains editable", SpellJsonChecker.check(valid.replace("\"speed\":0.4", "\"size\":2,\"speed\":0.4")).clean());
		check("scale canonical field remains editable", SpellJsonChecker.check(valid.replace("\"speed\":0.4", "\"base_scale\":1,\"speed\":0.4")).clean());
		System.out.println("SpellJsonCheckerTest: " + checks + " checks passed");
	}

	private static void bundledExamples() throws Exception {
		String resource = "/data/youkaishomecoming/llm/spell_generation_system.txt";
		try (var input = SpellJsonCheckerTest.class.getResourceAsStream(resource)) {
			String prompt = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			String examples = prompt.substring(prompt.indexOf("# Construction examples"));
			var matcher = Pattern.compile("```json\\s*([\\s\\S]*?)```").matcher(examples);
			int count = 0;
			var errors = new java.util.ArrayList<String>();
			while (matcher.find()) {
				var result = SpellJsonChecker.check(matcher.group(1));
				count++;
				if (!result.clean()) errors.add("example " + count + ": " + result.feedback());
				else checks++;
			}
			check("bundled examples are accepted by editor checker: " + String.join("\n", errors), errors.isEmpty());
			check("all six bundled examples were checked", count == 6);
		}
	}

	public static String spell(String actions) {
		return "{\"id\":\"youkaishomecoming:checker_test\",\"display\":{\"name\":\"Checker test\"},"
				+ "\"entry_phase\":\"youkaishomecoming:checker_test/main\",\"phases\":{\"youkaishomecoming:checker_test/main\":{"
				+ "\"id\":\"youkaishomecoming:checker_test/main\",\"on_tick\":[" + actions + "]}}}";
	}

	private static void check(String label, boolean pass) { if (!pass) throw new AssertionError(label); checks++; }
}
