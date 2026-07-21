package dev.xkmc.youkaishomecoming.init.data;

import com.tterrag.registrate.providers.RegistrateLangProvider;
import com.tterrag.registrate.util.entry.RegistryEntry;
import dev.xkmc.youkaishomecoming.content.spell.market.SpellMarketBuiltinTags;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import dev.xkmc.youkaishomecoming.init.registrate.YHEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

public enum YHLangData {
	CHANCE_EFFECT("tooltip.chance", "%1$s with %2$s%% chance", 2, ChatFormatting.GRAY),
	PLACE("tooltip.place", "Can be placed on ground", 0, ChatFormatting.GRAY),
	FLESH_TASTE_HUMAN("tooltip.taste_human", "Unappealing smell...", 0, ChatFormatting.GRAY),
	FLESH_TASTE_HALF_YOUKAI("tooltip.taste_half_youkai", "Strange flavor...", 0, ChatFormatting.GRAY),
	FLESH_TASTE_YOUKAI("tooltip.taste_youkai", "Delicious!", 0, ChatFormatting.GRAY),
	DANMAKU_DAMAGE("tooltip.danmaku_damage", "Deals %s damage on hit", 1, ChatFormatting.BLUE),
	DANMAKU_BYPASS("tooltip.danmaku_bypass", "Bypasses entities", 0, ChatFormatting.DARK_AQUA),
	SPELL_TARGET("tooltip.spell_target", "Requires targeting an entity to activate", 0, ChatFormatting.RED),
	SPELL_COST("tooltip.spell_cost", "Costs %s %s", 2, ChatFormatting.GRAY),
	KETTLE_INFO("tooltip.kettle", "Right click with water bucket or water bottle to fill water", 0, ChatFormatting.GRAY),
	DRYING_RACK("tooltip.drying_rack", "Only works directly under the sun", 0, ChatFormatting.GRAY),
	CAMELLIA("tooltip.camellia", "Prevent Phantom spawn when equipped", 0, ChatFormatting.GRAY),
	FLASK_OF("flask.of", "Flask Of %s", 1, null),
	FLASK_INFO_DRINK("flask.info_drink", "Stores 4 bottles of drink. Consume 0.2 bottles per slip", 0, ChatFormatting.GRAY),
	FLASK_INFO_SAUCE("flask.info_sauce", "Stores 4 bottles of sauce. Consume 0.2 bottles per use on Pots and Cuisine Table", 0, ChatFormatting.GRAY),
	FLASK_CONTENT("flask.content", "Content: %s", 1, ChatFormatting.GRAY),
	FLASK_USE("flask.use", "Remaining Use: %s/%s", 2, ChatFormatting.GRAY),

	MOON_LANTERN_PLACE("tooltip.moon_lantern_place", "Udumbara within 3x3x3 blocks below this latern will grow as night as if they can see full moon.", 0, ChatFormatting.GRAY),
	MOON_LANTERN_HOLD("tooltip.moon_lantern_hold", "When holding in hand, Udumbara effect will always trigger at night regardless if player can see full moons", 0, ChatFormatting.GRAY),

	FLESH_NAME_HUMAN("flesh_human", "Weird Meat", 0, null),
	FLESH_NAME_YOUKAI("flesh_youkai", "Flesh", 0, null),

	FERMENT_PROGRESS("fermenting_progress", "Fermenting: %s", 1, ChatFormatting.GRAY),
	CUISINE_ALLOW("cuisine_allow", "Next Step:", 0, ChatFormatting.GRAY),
	CUISINE_EXTRA("cuisine_extra", "And %s more...", 1, ChatFormatting.GRAY),
	HEAT_PROGRESS("heat_progress", "Heating: %s", 1, ChatFormatting.GRAY),
	BREWING_PROGRESS("brewing_progress", "Brewing: %s", 1, ChatFormatting.GRAY),
	COOKING_PROGRESS("cooking_progress", "Cooking: %s", 1, ChatFormatting.GRAY),
	STEAMER_TOO_MANY("steamer.too_many", "Too many racks!", 0, ChatFormatting.RED),
	STEAMER_NO_WATER("steamer.no_water", "Next Step: Add water", 0, ChatFormatting.GRAY),
	STEAMER_NO_HEAT("steamer.no_heat", "Next Step: Put heat source beneath", 0, ChatFormatting.GRAY),
	STEAMER_NO_RACK("steamer.no_rack", "Next Step: Add steam racks", 0, ChatFormatting.GRAY),
	STEAMER_NO_CAP("steamer.no_cap", "Cap top rack to steam faster", 0, ChatFormatting.GRAY),

	JEI_MOKA("jei.moka", "Coffee Brewing", 0, null),
	JEI_KETTLE("jei.kettle", "Tea Brewing", 0, null),
	JEI_RACK("jei.rack", "Drying", 0, null),
	JEI_FERMENT("jei.ferment", "Fermenting", 0, null),
	JEI_BASIN("jei.basin", "Basin", 0, null),
	JEI_STEAM("jei.steam", "Steaming", 0, null),
	JEI_CUISINE("jei.cuisine", "Cuisine", 0, null),
	JEI_COOKING("jei.cooking", "Cooking", 0, null),

	OBTAIN("obtain", "Source: ", 0, ChatFormatting.GRAY),
	UNKNOWN("unknown", "???", 0, ChatFormatting.GRAY),
	USAGE("usage", "Usage: ", 0, ChatFormatting.GRAY),

	OBTAIN_FLESH("obtain_flesh", "Kill human mobs with knife while in %s or %s effect", 2, ChatFormatting.GRAY),
	OBTAIN_BLOOD("obtain_blood", "Kill human mobs with knife and have glass bottle in off hand while in %s or %s effect", 2, ChatFormatting.GRAY),
	OBTAIN_FAIRY_ICE("obtain_fairy_ice", "Rarely dropped when you got hit by Cirno's Danmaku while wearing full leather suits. Dropped from Cirno. Could be obtained by trading with Cirno as well.", 0, ChatFormatting.GRAY),
	USAGE_FAIRY_ICE("usage_fairy_ice", "Throw to deal damage and freeze target.", 0, ChatFormatting.GRAY),
	OBTAIN_FROZEN_FROG("obtain_frozen_frog", "Dropped when Cirno freezes a frog. Rarely dropped from Cirno when defeated with Danmaku.", 0, ChatFormatting.GRAY),
	USAGE_FROZEN_FROG("usage_frozen_frog", "Throw toward target to summon a frog.", 0, ChatFormatting.GRAY),
	USAGE_DANMAKU("usage_danmaku", "While in %s or %s effect, or equip touhou hats, you can shoot danmaku", 2, ChatFormatting.GRAY),

	USAGE_STRAW_HAT("usage_straw_hat", "While in %s or %s effect, you can equip it on frogs to allow them to eat raiders", 2, ChatFormatting.GRAY),
	OBTAIN_SUWAKO_HAT("obtain_suwako_hat", "Drops when frog with hat eats %s different kinds of raiders in front of villagers", 1, ChatFormatting.GRAY),
	OBTAIN_KOISHI_HAT("obtain_koishi_hat", "Drops when blocking Koishi attacks %s times in a row", 1, ChatFormatting.GRAY),
	OBTAIN_RUMIA_HAIRBAND("obtain_rumia_hairband", "Drops when player defeat Ex. Rumia with Danmaku", 0, ChatFormatting.GRAY),
	USAGE_RUMIA_HAIRBAND("usage_rumia_hairband", "Drops heads when killing mobs. Flesh and blood drops no longer require knife (bonus when still using knife).", 0, ChatFormatting.GRAY),
	OBTAIN_REIMU_HAIRBAND("obtain_reimu_hairband", "Feed Reimu a variety of food", 0, ChatFormatting.GRAY),
	USAGE_REIMU_HAIRBAND("usage_reimu_hairband", "Enables creative flight. Your danmaku damage bypasses magical protection.", 0, ChatFormatting.GRAY),
	OBTAIN_CIRNO_HAIRBAND("obtain_cirno_hairband", "Trade with Cirno", 0, ChatFormatting.GRAY),
	USAGE_CIRNO_HAIRBAND("usage_cirno_hairband", "Your magic damage freezes target (and frogs).", 0, ChatFormatting.GRAY),
	USAGE_FAIRY_WINGS("usage_fairy_wings", "When you have %s, enables creative flight.", 1, ChatFormatting.GRAY),

	CONSTANT_EFFECT("constant_effect", "Grants constant %s when applicable.", 1, ChatFormatting.GRAY),
	DANMAKU_SUPPORT_1("no_consume_1", "Allows using %s danmaku without consumption.", 1, ChatFormatting.GRAY),
	DANMAKU_SUPPORT_2("no_consume_2", "Allows using %s and %s danmaku without consumption.", 2, ChatFormatting.GRAY),

	REIMU_FLESH("reimu_flesh", "Reimu: You shall not eat it. Last warning.", 0, ChatFormatting.RED),
	REIMU_WARN("reimu_warn", "Reimu: Drink some tea and keep your sanity. Last warning.", 0, ChatFormatting.RED),
	KOISHI_REIMU("koishi_reimu", "Reimu: ???", 0, ChatFormatting.RED),

	EDITOR_RESET("custom_spell.reset", "Reset", 0, null),
	INVALID_TIME("custom_spell.invalid_time", "Max duration of %s allowed. Current duration: %s", 2, ChatFormatting.RED),

	// Spell Market
	MARKET_TITLE("spell_market.title", "Spell Card Market", 0, null),
	STG_DEFEAT("message.stg_defeat", "Spell card duel defeated", 0, ChatFormatting.RED),
	STG_NO_SLEEP("message.stg_no_sleep", "Cannot sleep during a spell card duel", 0, ChatFormatting.RED),
	MARKET_SEARCH("spell_market.search", "Search...", 0, null),
	MARKET_REFRESH("spell_market.refresh", "Refresh", 0, null),
	MARKET_UPLOAD("spell_market.upload", "Upload", 0, null),
	MARKET_CLOSE("spell_market.close", "Close", 0, null),
	MARKET_TO_EDITOR("spell_market.to_editor", "Editor", 0, null),
	MARKET_PREV("spell_market.prev", "Prev", 0, null),
	MARKET_NEXT("spell_market.next", "Next", 0, null),
	MARKET_PAGE("spell_market.page", "Page %s / %s", 2, null),
	MARKET_LOADING("spell_market.loading", "Loading...", 0, null),
	MARKET_NO_SPELLS("spell_market.no_spells", "No spells found", 0, null),
	MARKET_DOWNLOAD("spell_market.download", "Download", 0, null),
	MARKET_LIKE("spell_market.like", "Like", 0, null),
	MARKET_LIKED("spell_market.liked", "Liked", 0, null),
	MARKET_UNLIKE("spell_market.unlike", "Unlike", 0, null),
	MARKET_LIKED_FILTER("spell_market.filter_liked", "Liked", 0, null),
	MARKET_FILTER_TAG("spell_market.filter_tag", "Tag: %s (click to clear)", 1, ChatFormatting.GREEN),
	MARKET_DETAIL("spell_market.detail", "Details", 0, null),
	MARKET_BACK("spell_market.back", "Back", 0, null),
	MARKET_OK("spell_market.ok", "OK", 0, null),
	MARKET_UNKNOWN("spell_market.unknown", "Unknown", 0, null),
	MARKET_ANONYMOUS("spell_market.anonymous", "Anonymous", 0, null),
	MARKET_AUTHOR_BY("spell_market.author_by", "by %s", 1, null),
	MARKET_DISABLED("spell_market.disabled", "Disabled", 0, ChatFormatting.RED),
	MARKET_COMMENTS("spell_market.comments", "Comments", 0, null),
	MARKET_COMMENT_COUNT("spell_market.comment_count", "Comments: %s", 1, null),
	MARKET_NO_COMMENTS("spell_market.no_comments", "No comments yet", 0, null),
	MARKET_COMMENT_PLACEHOLDER("spell_market.comment.placeholder", "Write a comment...", 0, null),
	MARKET_COMMENT_IMAGE("spell_market.comment.image", "Image URL (optional)", 0, null),
	MARKET_COMMENT_POST("spell_market.comment.post", "Post", 0, null),
	MARKET_COMMENT_DELETE("spell_market.comment.delete", "Delete", 0, null),
	MARKET_COMMENT_FAIL("spell_market.comment.fail", "Comment request failed", 0, ChatFormatting.RED),
	MARKET_IMAGE_LOADING("spell_market.image.loading", "Loading image...", 0, null),
	MARKET_IMAGE_UNAVAILABLE("spell_market.image.unavailable", "Image unavailable", 0, ChatFormatting.RED),
	MARKET_DOWNLOAD_SUCCESS("spell_market.download_success", "Downloaded: %s\nSaved to world storage.\nUse Editor > Export to share globally.", 1, ChatFormatting.GREEN),
	MARKET_DOWNLOAD_FAIL("spell_market.download_fail", "Download failed", 0, ChatFormatting.RED),
	MARKET_DOWNLOAD_INCOMPATIBLE("spell_market.download_incompatible", "Download failed.\nThis spell's format may be incompatible.\nCheck game log for parse errors.", 0, ChatFormatting.RED),
	MARKET_SAVE_FAIL("spell_market.save_fail", "Save failed: %s\nCheck game log for details.", 1, ChatFormatting.RED),
	MARKET_DOWNLOADING("spell_market.downloading", "Downloading: %s", 1, null),
	MARKET_DELETE_FAIL("spell_market.delete_fail", "Delete failed. You may not be authorized.", 0, ChatFormatting.RED),
	MARKET_UPLOAD_TITLE("spell_market.upload.title", "Upload Spell", 0, null),
	MARKET_UPLOAD_NAME("spell_market.upload.name", "Name:", 0, null),
	MARKET_UPLOAD_DESC("spell_market.upload.desc", "Description:", 0, null),
	MARKET_UPLOAD_AUTHOR("spell_market.upload.author", "Author:", 0, null),
	MARKET_UPLOAD_CATEGORY("spell_market.upload.category", "Category:", 0, null),
	MARKET_UPLOAD_BUILTIN_TAGS("spell_market.upload.builtin_tags", "Built-in tags:", 0, null),
	MARKET_UPLOAD_SOURCE("spell_market.upload.source", "Source:", 0, null),
	MARKET_UPLOAD_CHARACTER("spell_market.upload.character", "Character:", 0, null),
	MARKET_UPLOAD_NONE("spell_market.upload.none", "None", 0, null),
	MARKET_UPLOAD_TAGS("spell_market.upload.tags", "Tags:", 0, null),
	MARKET_UPLOAD_ADD_TAG("spell_market.upload.add_tag", "Add Tag", 0, null),
	MARKET_UPLOAD_SELECT("spell_market.upload.select", "Select a spell to upload:", 0, null),
	MARKET_UPLOAD_NO_SPELLS("spell_market.upload.no_spells", "No spells available. Create or load a spell first.", 0, ChatFormatting.RED),
	MARKET_UPLOAD_CHANGE_SPELL("spell_market.upload.change_spell", "< Change Spell", 0, null),
	MARKET_UPLOAD_SPELL("spell_market.upload.spell", "Spell: %s", 1, null),
	MARKET_UPLOAD_ADDED_TAGS("spell_market.upload.added_tags", "Added tags:", 0, null),
	MARKET_UPLOAD_UPLOADING("spell_market.upload.uploading", "Uploading...", 0, ChatFormatting.YELLOW),
	MARKET_UPLOAD_BTN("spell_market.upload.button", "Upload", 0, null),
	MARKET_UPLOAD_CANCEL("spell_market.upload.cancel", "Cancel", 0, null),
	MARKET_UPLOAD_SUCCESS("spell_market.upload.success", "Upload successful! UUID: %s", 1, ChatFormatting.GREEN),
	MARKET_UPLOAD_SUCCESS_TITLE("spell_market.upload.success_title", "Upload Successful!", 0, ChatFormatting.GREEN),
	MARKET_UPLOAD_FAIL("spell_market.upload.fail", "Upload failed", 0, ChatFormatting.RED),
	MARKET_UPLOAD_COOLDOWN("spell_market.upload.cooldown", "Wait %s seconds before uploading again", 1, ChatFormatting.YELLOW),
	MARKET_UPLOAD_VALIDATION_NO_SPELL("spell_market.upload.validation.no_spell", "No spell selected", 0, ChatFormatting.RED),
	MARKET_UPLOAD_VALIDATION_ID_MISSING("spell_market.upload.validation.id_missing", "Spell ID is missing", 0, ChatFormatting.RED),
	MARKET_UPLOAD_VALIDATION_DISPLAY_MISSING("spell_market.upload.validation.display_missing", "Spell display info is missing", 0, ChatFormatting.RED),
	MARKET_UPLOAD_VALIDATION_DISPLAY_NAME_EMPTY("spell_market.upload.validation.display_name_empty", "Spell display name is empty", 0, ChatFormatting.RED),
	MARKET_UPLOAD_VALIDATION_ENTRY_PHASE_MISSING("spell_market.upload.validation.entry_phase_missing", "Entry phase is not set", 0, ChatFormatting.RED),
	MARKET_UPLOAD_VALIDATION_NO_PHASES("spell_market.upload.validation.no_phases", "Spell has no phases", 0, ChatFormatting.RED),
	MARKET_UPLOAD_VALIDATION_ENTRY_PHASE_NOT_FOUND("spell_market.upload.validation.entry_phase_not_found", "Entry phase %s not found in spell phases", 1, ChatFormatting.RED),
	MARKET_UPLOAD_VALIDATION_NO_CONTENT("spell_market.upload.validation.no_content", "Spell has no actions or transitions in any phase", 0, ChatFormatting.RED),
	MARKET_UPLOAD_VALIDATION_INVALID_CONTENT("spell_market.upload.validation.invalid_content", "Invalid spell content: %s", 1, ChatFormatting.RED),
	MARKET_UPLOAD_VALIDATION_NAME_REQUIRED("spell_market.upload.validation.name_required", "Name is required", 0, ChatFormatting.RED),
	MARKET_UPLOAD_VALIDATION_DESC_REQUIRED("spell_market.upload.validation.desc_required", "Description is required", 0, ChatFormatting.RED),
	MARKET_UPLOAD_VALIDATION_TAG_REQUIRED("spell_market.upload.validation.tag_required", "At least one tag is required", 0, ChatFormatting.RED),
	MARKET_CATEGORY_CANON("spell_market.category.canon", "Canon", 0, null),
	MARKET_CATEGORY_ORIGINAL("spell_market.category.original", "Original", 0, null),
	MARKET_CATEGORY_CREATIVE("spell_market.category.creative", "Creative", 0, null),
	MARKET_CATEGORY_TECH_DEMO("spell_market.category.tech_demo", "Tech Demo", 0, null),
	MARKET_CATEGORY_TUTORIAL("spell_market.category.tutorial", "Tutorial", 0, null),
	MARKET_CATEGORY_CHALLENGE("spell_market.category.challenge", "Challenge", 0, null),
	MARKET_CATEGORY_OTHER("spell_market.category.other", "Other", 0, null),
	MARKET_COMMAND_INVALID_SPELL("spell_market.command.invalid_spell", "Invalid spell ID: %s", 1, ChatFormatting.RED),
	MARKET_COMMAND_SPELL_NOT_FOUND("spell_market.command.spell_not_found", "Spell not found: %s", 1, ChatFormatting.RED),
	MARKET_COMMAND_DISABLED("spell_market.command.disabled", "Spell market is disabled. Check config: spell_market.enabled", 0, ChatFormatting.RED),
	MARKET_COMMAND_TESTING("spell_market.command.testing", "Testing connection to spell market server...", 0, ChatFormatting.GRAY),
	MARKET_COMMAND_CONNECTION_FAILED("spell_market.command.connection_failed", "Spell market connection failed. Check logs for details.", 0, ChatFormatting.RED),
	MARKET_COMMAND_CONNECTED("spell_market.command.connected", "Spell market connected! Total spells available: %s", 1, ChatFormatting.GREEN),
	MARKET_COMMAND_RELOAD_ENABLED("spell_market.command.reload_enabled", "Spell market config reloaded. Feature is enabled.", 0, ChatFormatting.GREEN),
	MARKET_COMMAND_RELOAD_DISABLED("spell_market.command.reload_disabled", "Spell market config reloaded. Feature is disabled.", 0, ChatFormatting.YELLOW),
	MARKET_ERROR_DISABLED("spell_market.error.disabled", "Market is disabled in config", 0, ChatFormatting.RED),
	MARKET_ERROR_NETWORK("spell_market.error.network", "Network error. Please check connection.", 0, ChatFormatting.RED),
	EDITOR_TO_MARKET("spell_editor.to_market", "Market", 0, null);

	private final String key, def;
	private final int arg;
	private final ChatFormatting format;


	YHLangData(String key, String def, int arg, @Nullable ChatFormatting format) {
		this.key = YoukaisHomecoming.MODID + "." + key;
		this.def = def;
		this.arg = arg;
		this.format = format;
	}

	public static String asId(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	public MutableComponent get(Object... args) {
		if (args.length != arg)
			throw new IllegalArgumentException("for " + name() + ": expect " + arg + " parameters, got " + args.length);
		MutableComponent ans = Component.translatable(key, args);
		if (format != null) {
			return ans.withStyle(format);
		}
		return ans;
	}

	public String key() {
		return key;
	}

	public static void genLang(RegistrateLangProvider pvd) {
		pvd.add(YoukaisHomecoming.MODID + ".subtitle.deer_ambient", "Deer baahs");
		pvd.add(YoukaisHomecoming.MODID + ".subtitle.deer_hurt", "Deer hurts");
		pvd.add(YoukaisHomecoming.MODID + ".subtitle.deer_death", "Deer dies");
		for (YHLangData lang : YHLangData.values()) {
			pvd.add(lang.key, lang.def);
		}
		for (SpellMarketBuiltinTags.SourceTag source : SpellMarketBuiltinTags.SOURCES) {
			if (source.defaultName() != null) {
				pvd.add(source.translationKey(), source.defaultName());
			}
		}
		for (SpellMarketBuiltinTags.CharacterTag character : SpellMarketBuiltinTags.CHARACTERS) {
			pvd.add(character.translationKey(), character.englishName());
		}
		pvd.add(YoukaisHomecoming.MODID + ".subtitle.koishi_ring", "Koishi Phone Call");
		pvd.add(YoukaisHomecoming.MODID + ".subtitle.graze", "Danmaku Graze");
		pvd.add(YoukaisHomecoming.MODID + ".subtitle.miss", "Danmaku Hit Player");
		pvd.add("death.attack.koishi_attack", "Koishi stabbed %s in the back");
		pvd.add("death.attack.koishi_attack.player", "%2$s stabbed %1$s in the back");
		pvd.add("death.attack.rumia_attack", "%s is eaten by Rumia");
		pvd.add("death.attack.rumia_attack.player", "%s is eaten by %s");
		pvd.add("death.attack.danmaku", "%s lost the danmaku battle");
		pvd.add("death.attack.danmaku.player", "%s lost the danmaku battle to %s");
		pvd.add("death.attack.abyssal_danmaku", "%s lost the danmaku battle");
		pvd.add("death.attack.abyssal_danmaku.player", "%s lost the danmaku battle to %s");

		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.bullet.title", "Bullet Type");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.color.title", "Bullet Color");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.branches", "Branch Count");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.branches.desc", "Number of branches of bullets to shoot");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.steps", "Step Count");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.steps.desc", "Number of bullets per branch");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.delay", "Step Delay");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.delay.desc", "Delay in ticks per step");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.range", "Bullet Range");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.range.desc", "Distance for bullet to fly before vanishing");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.randomizedRange", "Range Variation");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.randomizedRange.desc", "Variation of bullet range in percentage, plus or minus");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.branchAngle", "Branch Angle Offset");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.branchAngle.desc", "Horizontal angle difference between adjacent branches, in degree");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.stepAngle", "Step Angle Offset");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.stepAngle.desc", "Horizontal angle difference between adjacent steps, in degree");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.stepVerticalAngle", "Step Vertical Angle Offset");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.stepVerticalAngle.desc", "Vertical angle difference between adjacent steps, in degree");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.randomizedAngle", "Angle Variation");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.randomizedAngle.desc", "Variation of bullet direction in degree, both horizontal and vertical, plus or minus");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.speed", "Bullet Speed");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.speed.desc", "Bullet speed in block per tick");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.speedFirst", "First Step Speed");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.speedFirst.desc", "Bullet speed in block per tick for first step");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.speedLast", "Last Step Speed");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.speedLast.desc", "Bullet speed in block per tick for last step");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.randomizedSpeed", "Speed Variation");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.randomizedSpeed.desc", "Variation of bullet speed in percentage, plus or minus");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.turnTime", "Turn Time");
		pvd.add(YoukaisHomecoming.MODID + ".custom_spell.turnTime.desc", "Time in tick after which bullet will redirect toward target");
		genSpellTemplateLang(pvd);
		pvd.add("key.categories.youkaishomecoming", "Youkai Homecoming");
		pvd.add("key.youkaishomecoming.open_spell_editor", "Open Spell Editor");

		// Spell editor help panel
		genSpellEditorHelp(pvd);

		// Exposure compat
		pvd.add("exposure.youkaishomecoming.danmaku_count", "Danmaku: %d");
		pvd.add("exposure.youkaishomecoming.score", "Score: %d");

		for (var e : YHDanmaku.Bullet.values()) {
			var name = e.name().toLowerCase(Locale.ROOT);
			pvd.add(YoukaisHomecoming.MODID + ".custom_spell.bullet." + name,
					RegistrateLangProvider.toEnglishName(name));
		}

		for (var e : DyeColor.values()) {
			var name = e.getName();
			pvd.add(YoukaisHomecoming.MODID + ".custom_spell.color." + name,
					RegistrateLangProvider.toEnglishName(name));
		}

		List<Item> list = List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.TIPPED_ARROW);
		for (RegistryEntry<? extends Potion> ent : YHEffects.POTION_LIST) {
			for (Item item : list) {
				String pref = item.getDescriptionId();
				String[] prefs = pref.split("\\.");
				String str = ent.get().getName(item.getDescriptionId() + ".effect.");
				String[] ids = ent.get().getEffects().get(0).getDescriptionId().split("\\.");
				String id = ids[ids.length - 1];
				String name = RegistrateLangProvider.toEnglishName(id);
				String pref_name = RegistrateLangProvider.toEnglishName(prefs[prefs.length - 1]);
				if (item == Items.TIPPED_ARROW) pref_name = "Arrow";
				pvd.add(str, pref_name + " of " + name);
			}
		}
	}

	private static void genSpellTemplateLang(RegistrateLangProvider pvd) {
		String prefix = YoukaisHomecoming.MODID + ".spell_template.";
		pvd.add(prefix + "basic.name", "Basic Spell Template");
		pvd.add(prefix + "basic.desc", "A minimal periodic ring pattern for starting a new spell.");
		pvd.add(prefix + "basic.node.interval", "Fire every 20 ticks");
		pvd.add(prefix + "basic.node.fire_ring", "Basic ring danmaku");
		pvd.add(prefix + "ring.name", "Rotating Ring Template");
		pvd.add(prefix + "ring.desc", "Demonstrates color cycling and dynamic angle expressions.");
		pvd.add(prefix + "ring.node.interval", "High-frequency interval trigger");
		pvd.add(prefix + "ring.node.rotating_ring", "Color ring rotating with tick");
		pvd.add(prefix + "mover.name", "Mover Template");
		pvd.add(prefix + "mover.desc", "Demonstrates formula mover and explicit radian trig functions.");
		pvd.add(prefix + "mover.node.interval", "Periodically spawn mover bullets");
		pvd.add(prefix + "mover.node.sine_wave", "Sine-wave bullet path");
		pvd.add(prefix + "shooter.name", "Shooter Template");
		pvd.add(prefix + "shooter.desc", "Demonstrates shooter lifetime, movement, and internal firing logic.");
		pvd.add(prefix + "shooter.node.interval", "Periodically spawn shooters");
		pvd.add(prefix + "shooter.node.spawn_shooter", "Spawn orbiting shooter");
		pvd.add(prefix + "shooter.node.shooter_tick", "Shooter internal interval");
		pvd.add(prefix + "shooter.node.shooter_fire", "Shooter fires ring danmaku");
		pvd.add(prefix + "command.name", "Command Action Template");
		pvd.add(prefix + "command.desc", "Demonstrates run_command action combined with visible danmaku.");
		pvd.add(prefix + "command.node.run_command", "Run particle command as caster");
		pvd.add(prefix + "command.node.interval", "Periodic support danmaku");
		pvd.add(prefix + "command.node.fire_ring", "Visible danmaku after command");
	}

	private static void genSpellEditorHelp(RegistrateLangProvider pvd) {
		String p = YoukaisHomecoming.MODID + ".spell_editor.help.";
		pvd.add(p + "title", "Spell Editor Help");
		int i = 0;
		pvd.add(p + "line." + i++, "\u00A7e\u00A7l--- Hotkeys ---");
		pvd.add(p + "line." + i++, "");
		pvd.add(p + "line." + i++, "\u00A7fSpace       \u00A77Play / Pause");
		pvd.add(p + "line." + i++, "\u00A7fR           \u00A77Reset to tick 0");
		pvd.add(p + "line." + i++, "\u00A7fRight       \u00A77Step forward 1 tick");
		pvd.add(p + "line." + i++, "\u00A7fDel/Bksp    \u00A77Delete selected node");
		pvd.add(p + "line." + i++, "");
		pvd.add(p + "line." + i++, "\u00A7fCtrl+Z      \u00A77Undo");
		pvd.add(p + "line." + i++, "\u00A7fCtrl+Y      \u00A77Redo");
		pvd.add(p + "line." + i++, "\u00A7fCtrl+C      \u00A77Copy node");
		pvd.add(p + "line." + i++, "\u00A7fCtrl+X      \u00A77Cut node");
		pvd.add(p + "line." + i++, "\u00A7fCtrl+V      \u00A77Paste node");
		pvd.add(p + "line." + i++, "\u00A7fCtrl+Up     \u00A77Move node up");
		pvd.add(p + "line." + i++, "\u00A7fCtrl+Down   \u00A77Move node down");
		pvd.add(p + "line." + i++, "\u00A7fCtrl+D      \u00A77Enable / Disable node");
		pvd.add(p + "line." + i++, "\u00A7fCtrl+E      \u00A77Collapse / Expand selected subtree");
		pvd.add(p + "line." + i++, "\u00A7fCtrl+Sh+E   \u00A77Collapse All / Expand All");
		pvd.add(p + "line." + i++, "\u00A7fCtrl+N      \u00A77Toggle custom node name display");
		pvd.add(p + "line." + i++, "\u00A7fCtrl+B      \u00A77Toggle [+] buttons: all / selected only");
		pvd.add(p + "line." + i++, "");
		pvd.add(p + "line." + i++, "\u00A7e\u00A7l--- Mouse ---");
		pvd.add(p + "line." + i++, "");
		pvd.add(p + "line." + i++, "\u00A76Action Tree:");
		pvd.add(p + "line." + i++, "\u00A7f  Click node      \u00A77Select & edit (shows [+] buttons)");
		pvd.add(p + "line." + i++, "\u00A7f  Double-click     \u00A77Rename (Enter confirm, Esc cancel)");
		pvd.add(p + "line." + i++, "\u00A7f  Click \u25BC/\u25B6       \u00A77Collapse / Expand subtree");
		pvd.add(p + "line." + i++, "\u00A7f  Drag node        \u00A77Drag & drop to reorder or move into branch");
		pvd.add(p + "line." + i++, "\u00A7f  Click [+]        \u00A77Add new node to section / branch");
		pvd.add(p + "line." + i++, "");
		pvd.add(p + "line." + i++, "\u00A76Properties Panel:");
		pvd.add(p + "line." + i++, "\u00A7f  [Disable]        \u00A77Disable node (skipped at runtime)");
		pvd.add(p + "line." + i++, "\u00A7f  [Delete]         \u00A77Delete node");
		pvd.add(p + "line." + i++, "\u00A7f  Ctrl+Click \u00A7b$var\u00A7f  \u00A77Jump to variable definition");
		pvd.add(p + "line." + i++, "\u00A7f  Tab              \u00A77Expression autocomplete");
		pvd.add(p + "line." + i++, "\u00A7f  Scroll wheel     \u00A77Scroll property list (drag scrollbar)");
		pvd.add(p + "line." + i++, "");
		pvd.add(p + "line." + i++, "\u00A763D Viewport (Orthographic):");
		pvd.add(p + "line." + i++, "\u00A7f  Left-drag        \u00A77Move target position");
		pvd.add(p + "line." + i++, "\u00A7f  Middle-drag      \u00A77Pan camera on view plane");
		pvd.add(p + "line." + i++, "\u00A7f  Right-drag       \u00A77Rotate camera");
		pvd.add(p + "line." + i++, "\u00A7f  Scroll wheel     \u00A77Zoom");
		pvd.add(p + "line." + i++, "");
		pvd.add(p + "line." + i++, "\u00A763D Viewport (Perspective):");
		pvd.add(p + "line." + i++, "\u00A7f  Left-click       \u00A77Enter free-look (hides cursor)");
		pvd.add(p + "line." + i++, "\u00A7f  WASD/Space/Shift \u00A77Move camera (in free-look)");
		pvd.add(p + "line." + i++, "\u00A7f  Mouse move       \u00A77Rotate view (in free-look)");
		pvd.add(p + "line." + i++, "\u00A7f  Scroll wheel     \u00A77Adjust fly speed");
		pvd.add(p + "line." + i++, "\u00A7f  Right-drag       \u00A77Orbit (pivot rotation)");
		pvd.add(p + "line." + i++, "\u00A7f  Middle-drag      \u00A77Pan on view plane");
		pvd.add(p + "line." + i++, "\u00A7f  Esc              \u00A77Exit free-look / Exit perspective");
		pvd.add(p + "line." + i++, "");
		pvd.add(p + "line." + i++, "\u00A7e\u00A7l--- Toolbar ---");
		pvd.add(p + "line." + i++, "");
		pvd.add(p + "line." + i++, "\u00A7fTop/Front/Side    \u00A77Orthographic preset angles");
		pvd.add(p + "line." + i++, "\u00A7fPersp/Ortho       \u00A77Toggle perspective / orthographic");
		pvd.add(p + "line." + i++, "\u00A7fBindTgt/Unbind    \u00A77Bind/unbind target to camera (perspective)");
		pvd.add(p + "line." + i++, "\u00A7f\u25B6All / \u25BCAll       \u00A77Collapse All / Expand All in tree");
		pvd.add(p + "line." + i++, "\u00A7f[+]:Sel/All       \u00A77[+] buttons: selected only / show all");
		pvd.add(p + "line." + i++, "\u00A7fApply             \u00A77Apply & save spell to all entities using it");
		pvd.add(p + "line." + i++, "\u00A7fExport            \u00A77Export globally for all saves");
		pvd.add(p + "line." + i++, "\u00A7fReset             \u00A77Reset to built-in default");
		pvd.add(p + "line." + i++, "\u00A7fAuto:ON/OFF       \u00A77Auto replay preview after edit");
		pvd.add(p + "line." + i++, "\u00A7fFocusTgt/Cstr     \u00A77Center viewport on target / caster");
		pvd.add(p + "line." + i++, "\u00A7fRstTgtPos/CstrPos \u00A77Reset target / caster to default position");
		pvd.add(p + "line." + i++, "");
		pvd.add(p + "line." + i++, "\u00A7e\u00A7l--- Mover Types ---");
		pvd.add(p + "line." + i++, "");
		pvd.add(p + "line." + i++, "\u00A7fnone              \u00A77Default straight flight");
		pvd.add(p + "line." + i++, "\u00A7facceleration      \u00A77Constant acceleration");
		pvd.add(p + "line." + i++, "\u00A7frotate            \u00A77Rotation");
		pvd.add(p + "line." + i++, "\u00A7fpolar             \u00A77Polar coordinate motion");
		pvd.add(p + "line." + i++, "\u00A7fzero              \u00A77Stationary");
		pvd.add(p + "line." + i++, "\u00A7fbezier            \u00A77Cubic bezier curve path");
		pvd.add(p + "line." + i++, "");
		pvd.add(p + "line." + i++, "\u00A7e\u00A7l--- Expression Syntax ---");
		pvd.add(p + "line." + i++, "");
		pvd.add(p + "line." + i++, "\u00A77Operators: \u00A7f+ - * / %  \u00A77Brackets: \u00A7f( )");
		pvd.add(p + "line." + i++, "\u00A77Variables: \u00A7b$wave  $i  $ver");
		pvd.add(p + "line." + i++, "\u00A77Functions: \u00A7erand\u00A7f(min,max)  \u00A7esqrt\u00A7f(x)");
		pvd.add(p + "line." + i++, "\u00A77           \u00A7esin\u00A7f(x,amp?,phase?)  \u00A7ecos\u00A7f(...)");
		pvd.add(p + "line." + i++, "\u00A77           \u00A7elerp\u00A7f(start,end,dur)");
		pvd.add(p + "line." + i++, "\u00A77           \u00A7ehp\u00A7f(full,empty)  \u00A7etick_mod\u00A7f(n)");
		pvd.add(p + "line." + i++, "\u00A77Keywords:  \u00A7etick  total_tick  distance");
		pvd.add(p + "line." + i++, "");
		pvd.add(p + "line." + i++, "\u00A7e\u00A7l--- Syntax Highlighting ---");
		pvd.add(p + "line." + i++, "");
		pvd.add(p + "line." + i++, "\u00A7b$variable        \u00A77Light blue");
		pvd.add(p + "line." + i++, "\u00A7erand() sqrt()    \u00A77Functions = yellow");
		pvd.add(p + "line." + i++, "\u00A7etick distance    \u00A77Keywords = yellow");
		pvd.add(p + "line." + i++, "\u00A7e(  \u00A7c(  \u00A7a(  \u00A79(  \u00A77Brackets = rainbow (when valid)");
	}

}
