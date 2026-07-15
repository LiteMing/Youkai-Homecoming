package dev.xkmc.youkaishomecoming.content.spell.market;

import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 符卡市场本地化文本管理器
 */
@OnlyIn(Dist.CLIENT)
public class SpellMarketLocalization {

	public static MutableComponent title() {
		return YHLangData.MARKET_TITLE.get().copy();
	}

	public static MutableComponent search() {
		return YHLangData.MARKET_SEARCH.get().copy();
	}

	public static MutableComponent refresh() {
		return YHLangData.MARKET_REFRESH.get().copy();
	}

	public static MutableComponent upload() {
		return YHLangData.MARKET_UPLOAD.get().copy();
	}

	public static MutableComponent close() {
		return YHLangData.MARKET_CLOSE.get().copy();
	}

	public static MutableComponent toEditor() {
		return YHLangData.MARKET_TO_EDITOR.get().copy();
	}

	public static MutableComponent prev() {
		return YHLangData.MARKET_PREV.get().copy();
	}

	public static MutableComponent next() {
		return YHLangData.MARKET_NEXT.get().copy();
	}

	public static MutableComponent page(int current, int total) {
		return YHLangData.MARKET_PAGE.get(current, total).copy();
	}

	public static MutableComponent loading() {
		return YHLangData.MARKET_LOADING.get().copy();
	}

	public static MutableComponent noSpells() {
		return YHLangData.MARKET_NO_SPELLS.get().copy();
	}

	public static MutableComponent download() {
		return YHLangData.MARKET_DOWNLOAD.get().copy();
	}

	public static MutableComponent like() {
		return YHLangData.MARKET_LIKE.get().copy();
	}

	public static MutableComponent liked() {
		return YHLangData.MARKET_LIKED.get().copy();
	}

	public static MutableComponent unlike() {
		return YHLangData.MARKET_UNLIKE.get().copy();
	}

	public static MutableComponent likedFilter() {
		return YHLangData.MARKET_LIKED_FILTER.get().copy();
	}

	public static MutableComponent filterTag(String tag) {
		return YHLangData.MARKET_FILTER_TAG.get(tag).copy();
	}

	public static MutableComponent detail() {
		return YHLangData.MARKET_DETAIL.get().copy();
	}

	public static MutableComponent back() {
		return YHLangData.MARKET_BACK.get().copy();
	}

	public static MutableComponent ok() {
		return YHLangData.MARKET_OK.get().copy();
	}

	public static MutableComponent unknown() {
		return YHLangData.MARKET_UNKNOWN.get().copy();
	}

	public static MutableComponent anonymous() {
		return YHLangData.MARKET_ANONYMOUS.get().copy();
	}

	public static MutableComponent authorBy(String name) {
		return YHLangData.MARKET_AUTHOR_BY.get(name).copy();
	}

	public static MutableComponent disabled() {
		return YHLangData.MARKET_DISABLED.get().copy();
	}

	public static MutableComponent comments() {
		return YHLangData.MARKET_COMMENTS.get().copy();
	}

	public static MutableComponent commentCount(int count) {
		return YHLangData.MARKET_COMMENT_COUNT.get(count).copy();
	}

	public static MutableComponent noComments() {
		return YHLangData.MARKET_NO_COMMENTS.get().copy();
	}

	public static MutableComponent commentPlaceholder() {
		return YHLangData.MARKET_COMMENT_PLACEHOLDER.get().copy();
	}

	public static MutableComponent commentImage() {
		return YHLangData.MARKET_COMMENT_IMAGE.get().copy();
	}

	public static MutableComponent commentPost() {
		return YHLangData.MARKET_COMMENT_POST.get().copy();
	}

	public static MutableComponent commentDelete() {
		return YHLangData.MARKET_COMMENT_DELETE.get().copy();
	}

	public static MutableComponent commentFail() {
		return YHLangData.MARKET_COMMENT_FAIL.get().copy();
	}

	public static MutableComponent imageLoading() {
		return YHLangData.MARKET_IMAGE_LOADING.get().copy();
	}

	public static MutableComponent imageUnavailable() {
		return YHLangData.MARKET_IMAGE_UNAVAILABLE.get().copy();
	}

	public static MutableComponent downloadSuccess(String name) {
		return YHLangData.MARKET_DOWNLOAD_SUCCESS.get(name).copy();
	}

	public static MutableComponent downloadFail() {
		return YHLangData.MARKET_DOWNLOAD_FAIL.get().copy();
	}

	public static MutableComponent downloadIncompatible() {
		return YHLangData.MARKET_DOWNLOAD_INCOMPATIBLE.get().copy();
	}

	public static MutableComponent saveFail(String message) {
		return YHLangData.MARKET_SAVE_FAIL.get(message).copy();
	}

	public static MutableComponent downloading(String name) {
		return YHLangData.MARKET_DOWNLOADING.get(name).copy();
	}

	public static MutableComponent deleteFail() {
		return YHLangData.MARKET_DELETE_FAIL.get().copy();
	}

	public static MutableComponent uploadTitle() {
		return YHLangData.MARKET_UPLOAD_TITLE.get().copy();
	}

	public static MutableComponent uploadName() {
		return YHLangData.MARKET_UPLOAD_NAME.get().copy();
	}

	public static MutableComponent uploadDesc() {
		return YHLangData.MARKET_UPLOAD_DESC.get().copy();
	}

	public static MutableComponent uploadAuthor() {
		return YHLangData.MARKET_UPLOAD_AUTHOR.get().copy();
	}

	public static MutableComponent uploadCategory() {
		return YHLangData.MARKET_UPLOAD_CATEGORY.get().copy();
	}

	public static MutableComponent uploadBuiltinTags() {
		return YHLangData.MARKET_UPLOAD_BUILTIN_TAGS.get().copy();
	}

	public static MutableComponent uploadSource() {
		return YHLangData.MARKET_UPLOAD_SOURCE.get().copy();
	}

	public static MutableComponent uploadCharacter() {
		return YHLangData.MARKET_UPLOAD_CHARACTER.get().copy();
	}

	public static MutableComponent uploadNone() {
		return YHLangData.MARKET_UPLOAD_NONE.get().copy();
	}

	public static MutableComponent uploadTags() {
		return YHLangData.MARKET_UPLOAD_TAGS.get().copy();
	}

	public static MutableComponent uploadAddTag() {
		return YHLangData.MARKET_UPLOAD_ADD_TAG.get().copy();
	}

	public static MutableComponent uploadSelect() {
		return YHLangData.MARKET_UPLOAD_SELECT.get().copy();
	}

	public static MutableComponent uploadNoSpells() {
		return YHLangData.MARKET_UPLOAD_NO_SPELLS.get().copy();
	}

	public static MutableComponent uploadChangeSpell() {
		return YHLangData.MARKET_UPLOAD_CHANGE_SPELL.get().copy();
	}

	public static MutableComponent uploadSpell(String spell) {
		return YHLangData.MARKET_UPLOAD_SPELL.get(spell).copy();
	}

	public static MutableComponent uploadAddedTags() {
		return YHLangData.MARKET_UPLOAD_ADDED_TAGS.get().copy();
	}

	public static MutableComponent uploadUploading() {
		return YHLangData.MARKET_UPLOAD_UPLOADING.get().copy();
	}

	public static MutableComponent uploadButton() {
		return YHLangData.MARKET_UPLOAD_BTN.get().copy();
	}

	public static MutableComponent uploadCancel() {
		return YHLangData.MARKET_UPLOAD_CANCEL.get().copy();
	}

	public static MutableComponent uploadSuccess(String uuid) {
		return YHLangData.MARKET_UPLOAD_SUCCESS.get(uuid).copy();
	}

	public static MutableComponent uploadSuccessTitle() {
		return YHLangData.MARKET_UPLOAD_SUCCESS_TITLE.get().copy();
	}

	public static MutableComponent uploadFail() {
		return YHLangData.MARKET_UPLOAD_FAIL.get().copy();
	}

	public static MutableComponent uploadCooldown(long seconds) {
		return YHLangData.MARKET_UPLOAD_COOLDOWN.get(seconds).copy();
	}

	public static MutableComponent validationNoSpell() {
		return YHLangData.MARKET_UPLOAD_VALIDATION_NO_SPELL.get().copy();
	}

	public static MutableComponent validationIdMissing() {
		return YHLangData.MARKET_UPLOAD_VALIDATION_ID_MISSING.get().copy();
	}

	public static MutableComponent validationDisplayMissing() {
		return YHLangData.MARKET_UPLOAD_VALIDATION_DISPLAY_MISSING.get().copy();
	}

	public static MutableComponent validationDisplayNameEmpty() {
		return YHLangData.MARKET_UPLOAD_VALIDATION_DISPLAY_NAME_EMPTY.get().copy();
	}

	public static MutableComponent validationEntryPhaseMissing() {
		return YHLangData.MARKET_UPLOAD_VALIDATION_ENTRY_PHASE_MISSING.get().copy();
	}

	public static MutableComponent validationNoPhases() {
		return YHLangData.MARKET_UPLOAD_VALIDATION_NO_PHASES.get().copy();
	}

	public static MutableComponent validationEntryPhaseNotFound(String phase) {
		return YHLangData.MARKET_UPLOAD_VALIDATION_ENTRY_PHASE_NOT_FOUND.get(phase).copy();
	}

	public static MutableComponent validationNoContent() {
		return YHLangData.MARKET_UPLOAD_VALIDATION_NO_CONTENT.get().copy();
	}

	public static MutableComponent validationInvalidContent(String message) {
		return YHLangData.MARKET_UPLOAD_VALIDATION_INVALID_CONTENT.get(message).copy();
	}

	public static MutableComponent validationNameRequired() {
		return YHLangData.MARKET_UPLOAD_VALIDATION_NAME_REQUIRED.get().copy();
	}

	public static MutableComponent validationDescRequired() {
		return YHLangData.MARKET_UPLOAD_VALIDATION_DESC_REQUIRED.get().copy();
	}

	public static MutableComponent validationTagRequired() {
		return YHLangData.MARKET_UPLOAD_VALIDATION_TAG_REQUIRED.get().copy();
	}

	public static MutableComponent commandInvalidSpell(String spellId) {
		return YHLangData.MARKET_COMMAND_INVALID_SPELL.get(spellId).copy();
	}

	public static MutableComponent commandSpellNotFound(String spellId) {
		return YHLangData.MARKET_COMMAND_SPELL_NOT_FOUND.get(spellId).copy();
	}

	public static MutableComponent commandDisabled() {
		return YHLangData.MARKET_COMMAND_DISABLED.get().copy();
	}

	public static MutableComponent commandTesting() {
		return YHLangData.MARKET_COMMAND_TESTING.get().copy();
	}

	public static MutableComponent commandConnectionFailed() {
		return YHLangData.MARKET_COMMAND_CONNECTION_FAILED.get().copy();
	}

	public static MutableComponent commandConnected(int total) {
		return YHLangData.MARKET_COMMAND_CONNECTED.get(total).copy();
	}

	public static MutableComponent commandReloadEnabled() {
		return YHLangData.MARKET_COMMAND_RELOAD_ENABLED.get().copy();
	}

	public static MutableComponent commandReloadDisabled() {
		return YHLangData.MARKET_COMMAND_RELOAD_DISABLED.get().copy();
	}

	public static MutableComponent errorDisabled() {
		return YHLangData.MARKET_ERROR_DISABLED.get().copy();
	}

	public static MutableComponent errorNetwork() {
		return YHLangData.MARKET_ERROR_NETWORK.get().copy();
	}

	public static MutableComponent tag(String tag) {
		return SpellMarketBuiltinTags.display(tag).copy();
	}

	public static MutableComponent toMarket() {
		return YHLangData.EDITOR_TO_MARKET.get().copy();
	}

	// 分类名称（带本地化支持）
	public static Component[] categories() {
		return new Component[]{
				category("Canon"),
				category("Original"),
				category("Creative"),
				category("Tech Demo"),
				category("Tutorial"),
				category("Challenge"),
				category("Other")
		};
	}

	public static MutableComponent category(String category) {
		if (category == null) {
			return Component.literal("");
		}
		return switch (category) {
			case "Canon" -> YHLangData.MARKET_CATEGORY_CANON.get().copy();
			case "Original" -> YHLangData.MARKET_CATEGORY_ORIGINAL.get().copy();
			case "Creative" -> YHLangData.MARKET_CATEGORY_CREATIVE.get().copy();
			case "Tech Demo" -> YHLangData.MARKET_CATEGORY_TECH_DEMO.get().copy();
			case "Tutorial" -> YHLangData.MARKET_CATEGORY_TUTORIAL.get().copy();
			case "Challenge" -> YHLangData.MARKET_CATEGORY_CHALLENGE.get().copy();
			case "Other" -> YHLangData.MARKET_CATEGORY_OTHER.get().copy();
			default -> Component.literal(category);
		};
	}

}
