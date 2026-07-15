package dev.xkmc.youkaishomecoming.content.spell.market;

import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SpellMarketBuiltinTags {

	public static final String SOURCE_PREFIX = "source:";
	public static final String CHARACTER_PREFIX = "char:";

	public static final List<SourceTag> SOURCES = List.of(
			source("other", "Other"),
			source("seihou", "Seihou Project"),
			source("decimal", "Decimal Works"),
			source("th01"), source("th02"), source("th03"), source("th04"), source("th05"),
			source("th06"), source("th07"), source("th08"), source("th09"), source("th10"),
			source("th11"), source("th12"), source("th13"), source("th14"), source("th15"),
			source("th16"), source("th17"), source("th18"), source("th19"), source("th20")
	);

	public static final List<CharacterTag> CHARACTERS = List.of(
			character("hakurei_reimu", "Reimu Hakurei"),
			character("kirisame_marisa", "Marisa Kirisame"),
			character("rumia", "Rumia", "source:th06"),
			character("daiyousei", "Daiyousei", "source:th06"),
			character("cirno", "Cirno", "source:th06"),
			character("hong_meiling", "Hong Meiling", "source:th06"),
			character("koakuma", "Koakuma", "source:th06"),
			character("patchouli_knowledge", "Patchouli Knowledge", "source:th06"),
			character("izayoi_sakuya", "Sakuya Izayoi", "source:th06"),
			character("remilia_scarlet", "Remilia Scarlet", "source:th06"),
			character("flandre_scarlet", "Flandre Scarlet", "source:th06"),
			character("letty_whiterock", "Letty Whiterock"),
			character("chen", "Chen"),
			character("alice_margatroid", "Alice Margatroid"),
			character("lily_white", "Lily White"),
			character("lunasa_prismriver", "Lunasa Prismriver"),
			character("merlin_prismriver", "Merlin Prismriver"),
			character("lyrica_prismriver", "Lyrica Prismriver"),
			character("konpaku_youmu", "Youmu Konpaku"),
			character("saigyouji_yuyuko", "Yuyuko Saigyouji"),
			character("yakumo_ran", "Ran Yakumo"),
			character("yakumo_yukari", "Yukari Yakumo"),
			character("wriggle_nightbug", "Wriggle Nightbug"),
			character("mystia_lorelei", "Mystia Lorelei"),
			character("kamishirasawa_keine", "Keine Kamishirasawa"),
			character("inaba_tewi", "Tewi Inaba"),
			character("reisen_udongein_inaba", "Reisen Udongein Inaba"),
			character("yagokoro_eirin", "Eirin Yagokoro"),
			character("houraisan_kaguya", "Kaguya Houraisan"),
			character("fujiwara_no_mokou", "Fujiwara no Mokou"),
			character("shameimaru_aya", "Aya Shameimaru"),
			character("medicine_melancholy", "Medicine Melancholy"),
			character("onozuka_komachi", "Komachi Onozuka"),
			character("shiki_eiki", "Eiki Shiki"),
			character("aki_shizuha", "Shizuha Aki"),
			character("aki_minoriko", "Minoriko Aki"),
			character("kagiyama_hina", "Hina Kagiyama"),
			character("kawashiro_nitori", "Nitori Kawashiro"),
			character("inubashiri_momiji", "Momiji Inubashiri"),
			character("kochiya_sanae", "Sanae Kochiya"),
			character("yasaka_kanako", "Kanako Yasaka"),
			character("moriya_suwako", "Suwako Moriya"),
			character("kisume", "Kisume"),
			character("kurodani_yamame", "Yamame Kurodani"),
			character("mizuhashi_parsee", "Parsee Mizuhashi"),
			character("hoshiguma_yuugi", "Yuugi Hoshiguma"),
			character("komeiji_satori", "Satori Komeiji"),
			character("kaenbyou_rin", "Rin Kaenbyou"),
			character("reiuji_utsuho", "Utsuho Reiuji"),
			character("komeiji_koishi", "Koishi Komeiji"),
			character("nazrin", "Nazrin"),
			character("tatara_kogasa", "Kogasa Tatara"),
			character("kumoi_ichirin", "Ichirin Kumoi"),
			character("unzan", "Unzan"),
			character("murasa_minamitsu", "Minamitsu Murasa"),
			character("toramaru_shou", "Shou Toramaru"),
			character("hijiri_byakuren", "Byakuren Hijiri"),
			character("houjuu_nue", "Nue Houjuu"),
			character("sunny_milk", "Sunny Milk"),
			character("luna_child", "Luna Child"),
			character("star_sapphire", "Star Sapphire"),
			character("kasodani_kyouko", "Kyouko Kasodani"),
			character("miyako_yoshika", "Yoshika Miyako"),
			character("kaku_seiga", "Seiga Kaku"),
			character("soga_no_tojiko", "Tojiko Soga"),
			character("mononobe_no_futo", "Futo Mononobe"),
			character("toyosatomimi_no_miko", "Toyosatomimi no Miko"),
			character("futatsuiwa_mamizou", "Mamizou Futatsuiwa"),
			character("wakasagihime", "Wakasagihime"),
			character("sekibanki", "Sekibanki"),
			character("imaizumi_kagerou", "Kagerou Imaizumi"),
			character("tsukumo_benben", "Benben Tsukumo"),
			character("tsukumo_yatsuhashi", "Yatsuhashi Tsukumo"),
			character("kijin_seija", "Seija Kijin"),
			character("sukuna_shinmyoumaru", "Shinmyoumaru Sukuna"),
			character("horikawa_raiko", "Raiko Horikawa"),
			character("seiran", "Seiran"),
			character("ringo", "Ringo"),
			character("doremy_sweet", "Doremy Sweet"),
			character("kishin_sagume", "Sagume Kishin"),
			character("clownpiece", "Clownpiece"),
			character("junko", "Junko"),
			character("hecatia_lapislazuli", "Hecatia Lapislazuli"),
			character("eternity_larva", "Eternity Larva"),
			character("sakata_nemuno", "Nemuno Sakata"),
			character("komano_aunn", "Aunn Komano"),
			character("yatadera_narumi", "Narumi Yatadera"),
			character("teireida_mai", "Mai Teireida"),
			character("nishida_satono", "Satono Nishida"),
			character("matara_okina", "Okina Matara"),
			character("ebisu_eika", "Eika Ebisu"),
			character("ushizaki_urumi", "Urumi Ushizaki"),
			character("niwatari_kutaka", "Kutaka Niwatari"),
			character("kicchou_yachie", "Yachie Kicchou"),
			character("joutouguu_mayumi", "Mayumi Joutouguu"),
			character("haniyasushin_keiki", "Keiki Haniyasushin"),
			character("kurokoma_saki", "Saki Kurokoma"),
			character("goutokuji_mike", "Mike Goutokuji"),
			character("yamashiro_takane", "Takane Yamashiro"),
			character("komakusa_sannyo", "Sannyo Komakusa"),
			character("tamatsukuri_misumaru", "Misumaru Tamatsukuri"),
			character("kudamaki_tsukasa", "Tsukasa Kudamaki"),
			character("iizunamaru_megumu", "Megumu Iizunamaru"),
			character("tenkyuu_chimata", "Chimata Tenkyuu"),
			character("himemushi_momoyo", "Momoyo Himemushi"),
			character("son_biten", "Biten Son"),
			character("mitsugashira_enoko", "Enoko Mitsugashira"),
			character("tenkajin_chiyari", "Chiyari Tenkajin"),
			character("yomotsu_hisami", "Hisami Yomotsu"),
			character("nippaku_zanmu", "Zanmu Nippaku"),
			character("chirizuka_ubame", "Ubame Chirizuka", "source:th20"),
			character("houjuu_chimi", "Chimi Houjuu", "source:th20"),
			character("michigami_nareko", "Nareko Michigami", "source:th20"),
			character("asama_yuiman", "Yuiman Asama", "source:th20"),
			character("watatsuki_no_toyohime", "Toyohime Watatsuki", "source:th20"),
			character("iwanaga_ariya", "Ariya Iwanaga", "source:th20"),
			character("watari_nina", "Nina Watari", "source:th20"),
			character("ibuki_suika", "Suika Ibuki"),
			character("hinanawi_tenshi", "Tenshi Hinanawi"),
			character("nagae_iku", "Iku Nagae"),
			character("himekaidou_hatate", "Hatate Himekaidou"),
			character("ibaraki_kasen", "Kasen Ibaraki"),
			character("usami_sumireko", "Sumireko Usami"),
			character("hata_no_kokoro", "Hata no Kokoro"),
			character("motoori_kosuzu", "Kosuzu Motoori"),
			character("hieda_no_akyuu", "Hieda no Akyuu"),
			character("morichika_rinnosuke", "Rinnosuke Morichika")
	);

	public static SourceTag source(String id) {
		return new SourceTag(id, null);
	}

	public static SourceTag source(String id, String defaultName) {
		return new SourceTag(id, defaultName);
	}

	public static CharacterTag character(String id, String englishName, String... sources) {
		return new CharacterTag(id, englishName, List.of(sources));
	}

	public static String normalize(String tag) {
		if (tag == null) return "";
		String trimmed = tag.trim();
		String lower = trimmed.toLowerCase(Locale.ROOT);
		if (lower.startsWith(SOURCE_PREFIX) || lower.startsWith(CHARACTER_PREFIX)) {
			return lower;
		}
		return trimmed;
	}

	public static boolean isBuiltin(String tag) {
		String normalized = normalize(tag);
		return normalized.startsWith(SOURCE_PREFIX) || normalized.startsWith(CHARACTER_PREFIX);
	}

	public static MutableComponent display(String tag) {
		String normalized = normalize(tag);
		if (normalized.startsWith(SOURCE_PREFIX)) {
			SourceTag source = findSource(normalized);
			if (source != null) {
				return source.display();
			}
			return Component.literal(normalized.substring(SOURCE_PREFIX.length()).toUpperCase(Locale.ROOT));
		}
		if (normalized.startsWith(CHARACTER_PREFIX)) {
			String id = normalized.substring(CHARACTER_PREFIX.length());
			CharacterTag character = findCharacter(id);
			if (character != null) {
				return Component.translatable(character.translationKey());
			}
		}
		return Component.literal(tag == null ? "" : tag);
	}

	public static SourceTag findSource(String tag) {
		String normalized = normalize(tag);
		for (SourceTag source : SOURCES) {
			if (source.tag().equals(normalized) || source.id().equals(normalized)) {
				return source;
			}
		}
		return null;
	}

	public static CharacterTag findCharacter(String id) {
		String normalized = normalize(id);
		if (normalized.startsWith(CHARACTER_PREFIX)) {
			normalized = normalized.substring(CHARACTER_PREFIX.length());
		}
		for (CharacterTag character : CHARACTERS) {
			if (character.id().equals(normalized)) {
				return character;
			}
		}
		return null;
	}

	public static List<CharacterTag> charactersForSource(String sourceTag) {
		String normalized = normalize(sourceTag);
		if (!normalized.startsWith(SOURCE_PREFIX)) {
			return CHARACTERS;
		}
		List<CharacterTag> filtered = new ArrayList<>();
		for (CharacterTag character : CHARACTERS) {
			if (character.sources().contains(normalized)) {
				filtered.add(character);
			}
		}
		return filtered.isEmpty() ? CHARACTERS : filtered;
	}

	public static String resolveSearchQuery(String query) {
		String normalized = normalize(query);
		if (normalized.isBlank()) return normalized;
		if (normalized.startsWith(SOURCE_PREFIX) || normalized.startsWith(CHARACTER_PREFIX)) {
			return normalized;
		}
		String lower = normalized.toLowerCase(Locale.ROOT);
		for (SourceTag source : SOURCES) {
			if (source.matchesExact(lower)) {
				return source.tag();
			}
		}
		for (CharacterTag character : CHARACTERS) {
			if (character.matchesExact(lower)) {
				return character.tag();
			}
		}
		if (lower.length() >= 2) {
			for (SourceTag source : SOURCES) {
				if (source.matchesPartial(lower)) {
					return source.tag();
				}
			}
			for (CharacterTag character : CHARACTERS) {
				if (character.matchesPartial(lower)) {
					return character.tag();
				}
			}
		}
		return query;
	}

	private static boolean containsIgnoreCase(String value, String query) {
		return value != null && value.toLowerCase(Locale.ROOT).contains(query);
	}

	private static boolean equalsIgnoreCase(String value, String query) {
		return value != null && value.toLowerCase(Locale.ROOT).equals(query);
	}

	public record SourceTag(String id, String defaultName) {
		public String tag() {
			return SOURCE_PREFIX + id;
		}

		public String translationKey() {
			return YoukaisHomecoming.MODID + ".spell_market.source." + id;
		}

		public MutableComponent display() {
			if (defaultName != null) {
				return Component.translatable(translationKey());
			}
			return Component.literal(id.toUpperCase(Locale.ROOT));
		}

		public boolean matchesExact(String query) {
			return tag().equals(query) || id.equals(query) ||
					equalsIgnoreCase(defaultName, query) ||
					display().getString().toLowerCase(Locale.ROOT).equals(query);
		}

		public boolean matchesPartial(String query) {
			return tag().contains(query) || id.contains(query) ||
					containsIgnoreCase(defaultName, query) ||
					display().getString().toLowerCase(Locale.ROOT).contains(query);
		}
	}

	public record CharacterTag(String id, String englishName, List<String> sources) {
		public String tag() {
			return CHARACTER_PREFIX + id;
		}

		public String translationKey() {
			return YoukaisHomecoming.MODID + ".spell_market.char." + id;
		}

		public MutableComponent display() {
			return Component.translatable(translationKey());
		}

		public boolean matchesExact(String query) {
			return tag().equals(query) || id.equals(query) ||
					englishName.toLowerCase(Locale.ROOT).equals(query) ||
					display().getString().toLowerCase(Locale.ROOT).equals(query);
		}

		public boolean matchesPartial(String query) {
			return tag().contains(query) || id.contains(query) ||
					englishName.toLowerCase(Locale.ROOT).contains(query) ||
					display().getString().toLowerCase(Locale.ROOT).contains(query);
		}
	}

}
