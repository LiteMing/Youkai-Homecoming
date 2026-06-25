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

	public static MutableComponent filterTag(String tag) {
		return YHLangData.MARKET_FILTER_TAG.get(tag).copy();
	}

	public static MutableComponent downloadSuccess(String name) {
		return YHLangData.MARKET_DOWNLOAD_SUCCESS.get(name).copy();
	}

	public static MutableComponent downloadFail() {
		return YHLangData.MARKET_DOWNLOAD_FAIL.get().copy();
	}

	public static MutableComponent downloading(String name) {
		return YHLangData.MARKET_DOWNLOADING.get(name).copy();
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

	public static MutableComponent uploadTags() {
		return YHLangData.MARKET_UPLOAD_TAGS.get().copy();
	}

	public static MutableComponent uploadAddTag() {
		return YHLangData.MARKET_UPLOAD_ADD_TAG.get().copy();
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

	public static MutableComponent uploadFail() {
		return YHLangData.MARKET_UPLOAD_FAIL.get().copy();
	}

	public static MutableComponent uploadCooldown(long seconds) {
		return YHLangData.MARKET_UPLOAD_COOLDOWN.get(seconds).copy();
	}

	public static MutableComponent errorDisabled() {
		return YHLangData.MARKET_ERROR_DISABLED.get().copy();
	}

	public static MutableComponent errorNetwork() {
		return YHLangData.MARKET_ERROR_NETWORK.get().copy();
	}

	public static MutableComponent toMarket() {
		return YHLangData.EDITOR_TO_MARKET.get().copy();
	}

	// 分类名称（带本地化支持）
	public static Component[] categories() {
		return new Component[]{
				Component.literal("Original"),
				Component.literal("Creative"),
				Component.literal("Tech Demo"),
				Component.literal("Tutorial"),
				Component.literal("Challenge"),
				Component.literal("Other")
		};
	}

}
