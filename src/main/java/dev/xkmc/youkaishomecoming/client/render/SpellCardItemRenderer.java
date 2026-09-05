package dev.xkmc.youkaishomecoming.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ForgeRenderTypes;
import org.joml.Matrix4f;

/**
 * 符卡自定义卡面物品渲染器 (BEWLR)。
 * 如果该符卡具有已认证的 hash 或绑定快照，则渲染带快照纹理的双面 84x128 纵向卡牌模型；
 * 否则优雅回退到原版的单色/默认模型。
 */
@OnlyIn(Dist.CLIENT)
public class SpellCardItemRenderer extends BlockEntityWithoutLevelRenderer {

	public static final SpellCardItemRenderer INSTANCE = new SpellCardItemRenderer();

	public SpellCardItemRenderer() {
		super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext transformType,
							 PoseStack poseStack, MultiBufferSource buffer,
							 int packedLight, int packedOverlay) {
		String hash = CertifiedSpellValidator.getCertifiedHash(stack);
		ResourceLocation textureLoc = hash != null && !hash.isBlank()
				? SpellCardTextureCache.getOrRequestCertified(hash)
				: null;

		if (textureLoc == null) {
			var id = DynamicSpellItem.getSpellId(stack);
			if (id != null) {
				textureLoc = SpellCardTextureCache.getLocalBySpellId(id);
			}
		}

		// 如果无快照，正面和背面统一使用程序化生成的该 Rank 专属 84x128 东方风卡牌边框底纹，并带上单色染色
		dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCardRank rank = DynamicSpellItem.getRank(stack);
		ResourceLocation defaultCardTex = dev.xkmc.youkaishomecoming.content.spell.preview.SpellCardFrameGenerator.getOrCreateDefaultCardTexture(rank);
		ResourceLocation cardTexture = textureLoc != null ? textureLoc : defaultCardTex;
		ResourceLocation frameTexture = textureLoc != null
				? dev.xkmc.youkaishomecoming.content.spell.preview.SpellCardFrameGenerator
						.getOrCreateFrameTexture(rank)
				: null;

		// 渲染 84x128 统一比例的双面卡牌
		poseStack.pushPose();

		float w = 84f / 128f * 0.5f;
		float h = 0.5f;
		float thickness = 0.005f;

		// 视角与手持位置变换：统一采用规范的 84x128 卡牌斜持与展示变换
		if (transformType == ItemDisplayContext.GUI) {
			poseStack.translate(0.5, 0.5, 0.0);
			poseStack.scale(0.95f, 0.95f, 0.95f);
		} else if (transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
			poseStack.translate(0.58, 0.7, 0.35);
			poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(270));
			poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-70));
			poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-50));
			poseStack.scale(0.7f, 0.7f, 0.7f);
		} else if (transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
			poseStack.translate(0.42, 0.62, 0.35);
			poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(270));
			poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(80));
			poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(40));
			poseStack.scale(0.7f, 0.7f, 0.7f);
		} else if (transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
			poseStack.translate(0.5, 0.55, 0.5);
			poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(20));
			poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-85));
			poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-45));
			poseStack.scale(0.55f, 0.55f, 0.55f);
		} else if (transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
			poseStack.translate(0.5, 0.55, 0.5);
			poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(20));
			poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(85));
			poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(45));
			poseStack.scale(0.55f, 0.55f, 0.55f);
		} else if (transformType == ItemDisplayContext.GROUND) {
			poseStack.translate(0.5, 0.3, 0.5);
			poseStack.scale(0.5f, 0.5f, 0.5f);
		} else if (transformType == ItemDisplayContext.FIXED) {
			poseStack.translate(0.5, 0.5, 0.5);
			poseStack.scale(0.7f, 0.7f, 0.7f);
		} else {
			poseStack.translate(0.5, 0.5, 0.5);
			poseStack.scale(0.6f, 0.6f, 0.6f);
		}

		RenderType cardRenderType = ForgeRenderTypes.getUnlitTranslucent(cardTexture, false);
		VertexConsumer frontBuilder = buffer.getBuffer(cardRenderType);
		Matrix4f mat = poseStack.last().pose();

		// 正面（弹幕快照或默认 84x128 底纹）
		int color = (textureLoc == null) ? DynamicSpellItem.getColor(stack).argb() : 0xFFFFFFFF;
		int a = (color >>> 24) & 0xFF;
		int r = (color >>> 16) & 0xFF;
		int g = (color >>> 8) & 0xFF;
		int b = color & 0xFF;
		quadColor(frontBuilder, mat, -w, w, -h, h, thickness, 0, 1, 0, 1,
				LightTexture.FULL_BRIGHT, r, g, b, a);

		// 背面（与正面保持一致材质，镜像贴附）
		VertexConsumer backBuilder = buffer.getBuffer(cardRenderType);
		quadColor(backBuilder, mat, w, -w, -h, h, -thickness, 0, 1, 0, 1,
				LightTexture.FULL_BRIGHT, r, g, b, a);

		if (frameTexture != null) {
			RenderType frameRenderType = ForgeRenderTypes.getUnlitTranslucent(frameTexture, false);
			VertexConsumer frameBuilder = buffer.getBuffer(frameRenderType);
			float frameOffset = 0.0005f;
			quadColor(frameBuilder, mat, -w, w, -h, h, thickness + frameOffset,
					0, 1, 0, 1, LightTexture.FULL_BRIGHT, 255, 255, 255, 255);
			quadColor(frameBuilder, mat, w, -w, -h, h, -thickness - frameOffset,
					0, 1, 0, 1, LightTexture.FULL_BRIGHT, 255, 255, 255, 255);
		}

		poseStack.popPose();
	}

	private static void quadColor(VertexConsumer builder, Matrix4f mat,
								 float minX, float maxX, float minY, float maxY, float z,
								 float minU, float maxU, float minV, float maxV, int light,
								 int r, int g, int b, int a) {
		builder.vertex(mat, minX, minY, z).color(r, g, b, a).uv(minU, maxV).overlayCoords(0, 10).uv2(light).normal(0, 0, 1).endVertex();
		builder.vertex(mat, maxX, minY, z).color(r, g, b, a).uv(maxU, maxV).overlayCoords(0, 10).uv2(light).normal(0, 0, 1).endVertex();
		builder.vertex(mat, maxX, maxY, z).color(r, g, b, a).uv(maxU, minV).overlayCoords(0, 10).uv2(light).normal(0, 0, 1).endVertex();
		builder.vertex(mat, minX, maxY, z).color(r, g, b, a).uv(minU, minV).overlayCoords(0, 10).uv2(light).normal(0, 0, 1).endVertex();
	}
}
