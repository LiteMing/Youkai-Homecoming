package dev.xkmc.youkaishomecoming.content.entity.danmaku;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.render.core.ProjectileRenderHelper;
import dev.xkmc.fastprojectileapi.render.core.ProjectileRenderer;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.item.danmaku.LaserItem;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;

@OnlyIn(Dist.CLIENT)
public class ItemLaserRenderer<T extends ItemLaserEntity> extends EntityRenderer<T> implements ProjectileRenderer<T> {

	public ItemLaserRenderer(EntityRendererProvider.Context pContext) {
		super(pContext);
	}

	protected int getBlockLightLevel(T e, BlockPos pPos) {
		return e.fullBright() ? 15 : super.getBlockLightLevel(e, pPos);
	}

	@Override
	public boolean shouldRender(T pLivingEntity, Frustum pCamera, double pCamX, double pCamY, double pCamZ) {
		return true;
	}

	@Override
	public double fading(SimplifiedProjectile e) {
		// In preview mode, always full opacity
		if (ProjectileRenderHelper.cameraOrientationOverride != null) return 1;
		if (entityRenderDispatcher.camera.getEntity() == e.getOwner()) {
			return YHModConfig.CLIENT.selfDanmakuFading.get();
		}
		return GrazeHelper.globalForbidTime > 0 ? YHModConfig.CLIENT.selfDanmakuFading.get() : 1;
	}

	@Override
	public Quaternionf cameraOrientation() {
		var override = ProjectileRenderHelper.cameraOrientationOverride;
		return override != null ? override : entityRenderDispatcher.cameraOrientation();
	}

	@Override
	public Vec3 getRenderOffset(T e, float f) {
		return new Vec3(0, e.getBbHeight() / 2, 0);
	}

	public void render(T e, float yaw, float pTick, PoseStack pose, MultiBufferSource buffer, int light) {
		render(e, pTick, pose);
	}

	@Override
	public void render(T e, float pTick, PoseStack pose) {
		if (!(e.getItem().getItem() instanceof LaserItem danmaku)) return;
		if (e.tickCount < 2) return;
		pose.pushPose();
		float scale = e.percentOpen(pTick);
		// Interpolate the physical direction vector instead of the Euler angles:
		// near the vertical pole, yaw/pitch can flip ~180° between ticks while the
		// true direction barely moves, and lerping the raw angles then renders
		// mid-frames pointing the wrong way (visible as long spikes cutting into
		// the pattern). Vector interpolation stays on the short arc between the
		// two real directions.
		Vec3 oldDir = Vec3.directionFromRotation(e.xRotO, e.yRotO);
		Vec3 newDir = Vec3.directionFromRotation(e.getXRot(), e.getYRot());
		Vec3 renderDir = oldDir.scale(1.0 - pTick).add(newDir.scale(pTick));
		if (renderDir.lengthSqr() < 1.0E-12) {
			renderDir = newDir;
		} else {
			renderDir = renderDir.normalize();
		}
		double horizontal = renderDir.horizontalDistance();
		float renderYaw = (float) (-Mth.atan2(renderDir.x, renderDir.z) * Mth.RAD_TO_DEG);
		float renderPitch = (float) (-Mth.atan2(renderDir.y, horizontal) * Mth.RAD_TO_DEG);
		pose.mulPose(Axis.YP.rotationDegrees(-renderYaw));
		pose.mulPose(Axis.XP.rotationDegrees(renderPitch + 90));
		pose.scale(e.getBbWidth() * scale, e.effectiveLength(pTick), e.getBbWidth() * scale);
		danmaku.getTypeForRender().create(this, e, pose, pTick);
		pose.popPose();
	}

	public ResourceLocation getTextureLocation(T pEntity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}

}
