package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.mojang.blaze3d.platform.NativeImage;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCardRank;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * 84x128 符卡边框生成与遮罩管理器。
 * 优先读取资源包中的 `youkaishomecoming:textures/gui/spell_card_frame.png`。
 * 若不存在，则自动以程序化方式根据冠位十二阶 (SpellCardRank) 绘制专属东方风边框。
 */
@OnlyIn(Dist.CLIENT)
public final class SpellCardFrameGenerator {

	public static final int CARD_WIDTH = 84;
	public static final int CARD_HEIGHT = 128;

	private static final ResourceLocation FRAME_TEXTURE = new ResourceLocation("youkaishomecoming", "textures/gui/spell_card_frame.png");
	private static final Map<SpellCardRank, ResourceLocation> RANK_DEFAULT_TEXTURES = new EnumMap<>(SpellCardRank.class);

	/**
	 * 获取或生成指定冠位阶梯未拍照空符卡的默认 84x128 材质。
	 */
	public static ResourceLocation getOrCreateDefaultCardTexture(SpellCardRank rank) {
		ResourceLocation loc = RANK_DEFAULT_TEXTURES.get(rank);
		if (loc != null) {
			return loc;
		}
		NativeImage card = generateDefaultBlankCard(rank);
		var dyn = new net.minecraft.client.renderer.texture.DynamicTexture(card);
		loc = Minecraft.getInstance().getTextureManager().register("spell_card_default_" + rank.getSerializedName(), dyn);
		RANK_DEFAULT_TEXTURES.put(rank, loc);
		return loc;
	}

	public static ResourceLocation getOrCreateDefaultCardTexture() {
		return getOrCreateDefaultCardTexture(SpellCardRank.LESSER_WISDOM);
	}

	private static NativeImage generateDefaultBlankCard(SpellCardRank rank) {
		NativeImage frame = getOrCreateFrame(rank);
		NativeImage card = new NativeImage(CARD_WIDTH, CARD_HEIGHT, false);

		int blankFill = rank.footerFill();
		for (int y = 0; y < CARD_HEIGHT; y++) {
			for (int x = 0; x < CARD_WIDTH; x++) {
				int frameColor = frame.getPixelRGBA(x, y);
				int frameA = (frameColor >>> 24) & 0xFF;
				if (frameA == 255) {
					card.setPixelRGBA(x, y, frameColor);
				} else if (frameA == 0) {
					card.setPixelRGBA(x, y, blankFill);
				} else {
					float alpha = frameA / 255.0f;
					float invA = 1.0f - alpha;
					int r = (int) (((frameColor) & 0xFF) * alpha + ((blankFill) & 0xFF) * invA);
					int g = (int) (((frameColor >>> 8) & 0xFF) * alpha + ((blankFill >>> 8) & 0xFF) * invA);
					int b = (int) (((frameColor >>> 16) & 0xFF) * alpha + ((blankFill >>> 16) & 0xFF) * invA);
					card.setPixelRGBA(x, y, (0xFF << 24) | (b << 16) | (g << 8) | r);
				}
			}
		}
		frame.close();
		return card;
	}

	private SpellCardFrameGenerator() {
	}

	public static NativeImage getOrCreateFrame() {
		return getOrCreateFrame(SpellCardRank.LESSER_WISDOM);
	}

	/**
	 * 获取或生成 84x128 的卡牌边框遮罩。
	 */
	public static NativeImage getOrCreateFrame(SpellCardRank rank) {
		// 1. 尝试从资源包读取手绘素材
		try {
			Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(FRAME_TEXTURE);
			if (res.isPresent()) {
				try (InputStream in = res.get().open()) {
					NativeImage custom = NativeImage.read(in);
					if (custom.getWidth() == CARD_WIDTH && custom.getHeight() == CARD_HEIGHT) {
						return custom;
					}
					NativeImage scaled = new NativeImage(CARD_WIDTH, CARD_HEIGHT, false);
					for (int y = 0; y < CARD_HEIGHT; y++) {
						for (int x = 0; x < CARD_WIDTH; x++) {
							int srcX = (int) ((x / (float) CARD_WIDTH) * custom.getWidth());
							int srcY = (int) ((y / (float) CARD_HEIGHT) * custom.getHeight());
							scaled.setPixelRGBA(x, y, custom.getPixelRGBA(srcX, srcY));
						}
					}
					custom.close();
					return scaled;
				}
			}
		} catch (Exception ignored) {
		}

		// 2. 程序化按 Rank 色彩生成矢量东方风边框
		return generateProceduralFrame(rank);
	}

	/**
	 * 程序化绘制 84x128 边框：
	 * 采用对应冠位阶梯的专属外壳、勾边金线和牌头牌底色彩。
	 */
	private static NativeImage generateProceduralFrame(SpellCardRank rank) {
		NativeImage img = new NativeImage(CARD_WIDTH, CARD_HEIGHT, false);

		int outerBorder = rank.outerBorder();
		int goldLine = rank.goldLine();
		int innerDark = rank.footerFill();
		int headerFill = rank.headerFill();

		for (int y = 0; y < CARD_HEIGHT; y++) {
			for (int x = 0; x < CARD_WIDTH; x++) {
				// 最外层 1px 倒角矩形
				boolean isCorner = (x == 0 && (y == 0 || y == CARD_HEIGHT - 1)) ||
						(x == CARD_WIDTH - 1 && (y == 0 || y == CARD_HEIGHT - 1));
				if (isCorner) {
					img.setPixelRGBA(x, y, 0x00000000);
					continue;
				}

				// 外围 1px 细边
				if (x == 0 || x == CARD_WIDTH - 1 || y == 0 || y == CARD_HEIGHT - 1) {
					img.setPixelRGBA(x, y, outerBorder);
					continue;
				}

				// 第 2 像素勾边线
				if (x == 1 || x == CARD_WIDTH - 2 || y == 1 || y == CARD_HEIGHT - 2) {
					img.setPixelRGBA(x, y, goldLine);
					continue;
				}

				// 顶部牌头 (y: 2 ~ 13)
				if (y < 14) {
					if (y == 13) {
						img.setPixelRGBA(x, y, goldLine);
					} else {
						img.setPixelRGBA(x, y, y < 8 ? headerFill : innerDark);
					}
					continue;
				}

				// 底部牌底 (y: 114 ~ 126)
				if (y >= 114) {
					if (y == 114) {
						img.setPixelRGBA(x, y, goldLine);
					} else {
						img.setPixelRGBA(x, y, innerDark);
					}
					continue;
				}

				// 左右边栏装饰框 (x: 2 ~ 3 或 x: 80 ~ 81)
				if (x <= 3 || x >= CARD_WIDTH - 4) {
					img.setPixelRGBA(x, y, outerBorder);
					if (x == 3 || x == CARD_WIDTH - 4) {
						img.setPixelRGBA(x, y, goldLine);
					}
					continue;
				}

				// 视窗边缘 2px 羽化暗角 (4 <= x <= 79, 14 <= y <= 113)
				int distFromEdgeX = Math.min(x - 4, 79 - x);
				int distFromEdgeY = Math.min(y - 14, 113 - y);
				int minDist = Math.min(distFromEdgeX, distFromEdgeY);

				if (minDist == 0) {
					img.setPixelRGBA(x, y, 0x66000000); // 40% 半透暗影
				} else if (minDist == 1) {
					img.setPixelRGBA(x, y, 0x33000000); // 20% 半透暗影
				} else {
					img.setPixelRGBA(x, y, 0x00000000); // 视窗内部完全透明
				}
			}
		}

		return img;
	}
}
