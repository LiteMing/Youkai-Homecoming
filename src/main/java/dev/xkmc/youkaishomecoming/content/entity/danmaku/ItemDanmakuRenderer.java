package dev.xkmc.youkaishomecoming.content.entity.danmaku;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.render.core.ProjectileRenderHelper;
import dev.xkmc.fastprojectileapi.render.core.ProjectileRenderer;
import dev.xkmc.fastprojectileapi.render.core.DanmakuRenderStates;
import dev.xkmc.youkaishomecoming.content.client.DanmakuClientState;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DanmakuItem;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;

@OnlyIn(Dist.CLIENT)
public class ItemDanmakuRenderer<T extends ItemDanmakuEntity> extends EntityRenderer<T> implements ProjectileRenderer<T> {

	public ItemDanmakuRenderer(EntityRendererProvider.Context pContext) {
		super(pContext);
	}

	protected int getBlockLightLevel(T e, BlockPos pPos) {
		return e.fullBright() ? 15 : super.getBlockLightLevel(e, pPos);
	}

	@Override
	public double fading(SimplifiedProjectile e) {
		// In preview mode, always full opacity
		if (ProjectileRenderHelper.cameraOrientationOverride != null) return 1;
		if (entityRenderDispatcher.camera.getEntity() == e.getOwner()) {
			double dist = entityRenderDispatcher.camera.getPosition().distanceTo(e.position());
			double fading = YHModConfig.CLIENT.selfDanmakuFading.get();
			return Math.min((dist - 2) / 12, 1) * fading;
		}
		double fading = YHModConfig.CLIENT.farDanmakuFading.get();
		double global = DanmakuClientState.isLocalPlayerSuppressed() ? YHModConfig.CLIENT.selfDanmakuFading.get() : 1;
		global = Math.min(global, DanmakuRenderStates.localPlayerDamageVisibility(e));
		if (fading == 0) return global;
		double dist = entityRenderDispatcher.camera.getPosition().distanceTo(e.position());
		double start = YHModConfig.CLIENT.fadingStart.get();
		double end = YHModConfig.CLIENT.fadingEnd.get();
		if (dist < start) return global;
		return (1 - Math.min((dist - start) / (end - start), 1) * fading) * global;
	}

	@Override
	public int color(SimplifiedProjectile e, float pTick) {
		return e instanceof ItemDanmakuEntity danmaku ? danmaku.getRenderTint(pTick) : 0xffffffff;
	}

	public boolean shouldRender(T e, Frustum frustum, double camx, double camy, double camz) {
		Entity cam = this.entityRenderDispatcher.camera.getEntity();
		if (e.getItem().getItem() instanceof DanmakuItem item &&
				item.type.category == YHDanmaku.BulletCategory.GIANT) {
			double radius = e.scale() * 0.5 + 0.25;
			double cy = e.getY() + e.getBbHeight() / 2;
			Vec3 camera = this.entityRenderDispatcher.camera.getPosition();
			double dx = camera.x - e.getX();
			double dy = camera.y - cy;
			double dz = camera.z - e.getZ();
			if (dx * dx + dy * dy + dz * dz <= radius * radius) return true;
			AABB visual = new AABB(
					e.getX() - radius, cy - radius, e.getZ() - radius,
					e.getX() + radius, cy + radius, e.getZ() + radius);
			return frustum.isVisible(visual);
		}
		if (e.getOwner() == cam && e.tickCount < 40) {
			double dh = e.getBbHeight() / 2;
			double dist = cam.getEyePosition().distanceToSqr(e.position().add(0, dh, 0));
			double dy = Math.abs(cam.getEyeY() - e.getY() - dh);
			if (dist <= 12 && (dy <= 0.1 + dh * 2 || dist <= 4)) return false;
		}
		return true;
	}

	@Override
	public Quaternionf cameraOrientation() {
		var override = ProjectileRenderHelper.cameraOrientationOverride;
		return override != null ? override : entityRenderDispatcher.cameraOrientation();
	}

	public void render(T e, float yaw, float pTick, PoseStack pose, MultiBufferSource buffer, int light) {
		render(e, pTick, pose);
	}

	@Override
	public Vec3 getRenderOffset(T e, float f) {
		return new Vec3(0, e.getBbHeight() / 2, 0);
	}

	@Override
	public void render(T e, float pTick, PoseStack pose) {
		if (!(e.getItem().getItem() instanceof DanmakuItem danmaku)) return;
		pose.pushPose();
		float scale = e.scale();
		pose.scale(scale, scale, scale);
		danmaku.getTypeForRender().create(this, e, pose, pTick);
		pose.popPose();
	}

	public ResourceLocation getTextureLocation(T pEntity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}

}
