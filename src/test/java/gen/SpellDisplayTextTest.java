package gen;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDisplay;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Display text must survive Forge formatting and server/client language differences. */
public final class SpellDisplayTextTest {

	private static int checks;

	public static void main(String[] args) {
		Language original = Language.getInstance();
		try {
			Language.inject(language(Map.of(), original));
			testLiteralText();
			testLanguageSwitch(original);
		} finally {
			Language.inject(original);
		}
		System.out.println("SpellDisplayTextTest: all " + checks + " checks passed");
	}

	private static void testLiteralText() {
		for (String text : List.of("", "1234", "红魔「不夜城」", "{", "ab{", "ab{ ", "ab{0", "ab{0,",
				"{0}", "}", "{\"name\":\"红魔\"}", "100%", "50%%", "%s", "%1$s", "%d",
				"Remilia's {0} 50%%", "第一行\n第二行", "spell.yh_test.missing")) {
			SpellDisplay display = display(text, text);
			equals("literal name", text, display.displayName().getString());
			equals("literal description", text, display.displayDesc().getString());
			equals("literal text after component serialization", text, roundTrip(display.displayName()).getString());
		}
	}

	private static void testLanguageSwitch(Language original) {
		String nameKey = "spell.yh_test.name";
		String descKey = "spell.yh_test.description";
		SpellDisplay display = display(nameKey, descKey);
		// Construct and serialize on a side without these translations, as a server does.
		Component name = roundTrip(display.displayName());
		Component desc = roundTrip(display.displayDesc());
		equals("missing name translation", nameKey, name.getString());
		equals("missing description translation", descKey, desc.getString());

		Language.inject(language(Map.of(nameKey, "Scarlet Moon", descKey, "Remilia's spell card"), original));
		equals("client English name", "Scarlet Moon", name.getString());
		equals("client English description", "Remilia's spell card", desc.getString());
		Language.inject(language(Map.of(nameKey, "红月", descKey, "蕾米莉亚的符卡"), original));
		equals("client Chinese name after switching language", "红月", name.getString());
		equals("client Chinese description after switching language", "蕾米莉亚的符卡", desc.getString());
		Language.inject(language(Map.of(), original));
		equals("name fallback after removing translation", nameKey, name.getString());
		equals("description fallback after removing translation", descKey, desc.getString());
	}

	private static SpellDisplay display(String name, String desc) {
		return new SpellDisplay(name, desc, Optional.empty(), Optional.empty());
	}

	private static Component roundTrip(Component component) {
		Component decoded = Component.Serializer.fromJson(Component.Serializer.toJson(component));
		if (decoded == null) throw new AssertionError("component serialization returned null");
		return decoded;
	}

	private static Language language(Map<String, String> translations, Language original) {
		return new Language() {
			@Override
			public String getOrDefault(String key, String fallback) {
				return translations.getOrDefault(key, fallback);
			}

			@Override
			public boolean has(String key) {
				return translations.containsKey(key);
			}

			@Override
			public boolean isDefaultRightToLeft() {
				return false;
			}

			@Override
			public FormattedCharSequence getVisualOrder(FormattedText text) {
				return original.getVisualOrder(text);
			}
		};
	}

	private static void equals(String label, String expected, String actual) {
		if (!expected.equals(actual)) {
			throw new AssertionError(label + ": expected <" + expected + ">, got <" + actual + ">");
		}
		checks++;
	}
}
