package dev.xkmc.youkaishomecoming.compat.exposure;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.render.virtual.DanmakuManager;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuProxyEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.EntitySpellProxyEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.IYHDanmaku;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.GeneralYoukaiEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DanmakuItem;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import io.github.mortuusars.exposure.forge.api.event.FrameAddedEvent;
import io.github.mortuusars.exposure.forge.api.event.ModifyFrameDataEvent;
import io.github.mortuusars.exposure.item.CameraItem;
import io.github.mortuusars.exposure.util.Fov;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * Exposure mod compatibility: erase danmaku within the camera's field of view when a photo is taken.
 *
 * Two event hooks:
 * 1. ModifyFrameDataEvent — fires BEFORE frame is serialized to film.
 *    Used to: write danmaku erase stats + youkai modelId into the frame NBT.
 * 2. FrameAddedEvent (LOW priority) — fires AFTER frame is saved.
 *    Used to: erase danmaku, apply camera CD, send client notification.
 *
 * This ordering ensures the NBT is written before serialization, and danmaku
 * are erased after the photo captures them visually.
 */
public class ExposureCompat {

	/** Maximum range to search for danmaku owners and world danmaku. */
	private static final double SEARCH_RANGE = 128.0;

	/** Maximum number of danmaku to erase per photo to avoid performance spikes. */
	private static final int MAX_ERASE_PER_SHOT = 500;

	/**
	 * Cached erase result from ModifyFrameDataEvent, consumed by FrameAddedEvent.
	 * Safe because both events fire on the same server thread in sequence.
	 */
	private static EraseResult pendingResult = null;

	/**
	 * Phase 1: Compute what danmaku are in the frustum and write stats to frame NBT.
	 * Fires BEFORE addFrameToFilm — our modifications will be serialized into the film.
	 */
	@SubscribeEvent
	public static void onModifyFrameData(ModifyFrameDataEvent event) {
		ServerPlayer player = event.player;
		if (!(player.level() instanceof ServerLevel level)) return;

		// Enrich youkai entities in frame with model ID
		enrichYoukaiEntities(event.frame, event.entitiesInFrame);

		// Reconstruct camera frustum
		Vec3 eyePos = player.getEyePosition();
		Vec3 lookDir = player.getLookAngle();
		float fovDegrees = getCameraFov(event.cameraStack);
		DanmakuFrustum frustum = new DanmakuFrustum(eyePos, lookDir, fovDegrees);

		AABB searchBox = AABB.ofSize(eyePos, SEARCH_RANGE * 2, SEARCH_RANGE * 2, SEARCH_RANGE * 2);
		EraseResult result = new EraseResult();

		// Count danmaku in frustum (don't erase yet — photo needs to capture them first)
		for (YoukaiEntity youkai : level.getEntitiesOfClass(YoukaiEntity.class, searchBox)) {
			youkai.countDanmakuInFrustum(frustum, result.remaining(MAX_ERASE_PER_SHOT), result);
			if (result.getTotal() >= MAX_ERASE_PER_SHOT) break;
		}
		if (result.getTotal() < MAX_ERASE_PER_SHOT) {
			for (DanmakuProxyEntity proxy : level.getEntitiesOfClass(DanmakuProxyEntity.class, searchBox)) {
				proxy.countDanmakuInFrustum(frustum, result.remaining(MAX_ERASE_PER_SHOT), result);
				if (result.getTotal() >= MAX_ERASE_PER_SHOT) break;
			}
		}
		if (result.getTotal() < MAX_ERASE_PER_SHOT) {
			for (EntitySpellProxyEntity proxy : level.getEntitiesOfClass(EntitySpellProxyEntity.class, searchBox)) {
				proxy.countDanmakuInFrustum(frustum, result.remaining(MAX_ERASE_PER_SHOT), result);
				if (result.getTotal() >= MAX_ERASE_PER_SHOT) break;
			}
		}
		if (result.getTotal() < MAX_ERASE_PER_SHOT) {
			for (Entity entity : level.getEntities(player, searchBox, e -> e instanceof IYHDanmaku)) {
				if (result.getTotal() >= MAX_ERASE_PER_SHOT) break;
				if (entity instanceof SimplifiedProjectile) {
					if (frustum.contains(entity.position())) {
						String[] typeAndColor = getDanmakuTypeAndColor(entity);
						result.record(typeAndColor[0], typeAndColor[1]);
					}
				}
			}
		}

		// Write stats to frame NBT (will be serialized into film)
		result.writeToFrame(event.frame);

		// Cache result for the FrameAddedEvent to actually erase
		pendingResult = result;
	}

	/**
	 * Phase 2: Actually erase the danmaku and apply camera effects.
	 * Fires AFTER the frame is saved to film — danmaku were captured in the photo.
	 */
	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onFrameAdded(FrameAddedEvent event) {
		ServerPlayer player = event.player;
		if (!(player.level() instanceof ServerLevel level)) return;

		EraseResult result = pendingResult;
		pendingResult = null;

		if (result == null || result.isEmpty()) return;

		// Now actually erase the danmaku
		Vec3 eyePos = player.getEyePosition();
		Vec3 lookDir = player.getLookAngle();
		float fovDegrees = getCameraFov(event.cameraStack);
		DanmakuFrustum frustum = new DanmakuFrustum(eyePos, lookDir, fovDegrees);
		AABB searchBox = AABB.ofSize(eyePos, SEARCH_RANGE * 2, SEARCH_RANGE * 2, SEARCH_RANGE * 2);

		for (YoukaiEntity youkai : level.getEntitiesOfClass(YoukaiEntity.class, searchBox)) {
			youkai.eraseDanmakuInFrustum(frustum, player, MAX_ERASE_PER_SHOT);
		}
		for (DanmakuProxyEntity proxy : level.getEntitiesOfClass(DanmakuProxyEntity.class, searchBox)) {
			proxy.eraseDanmakuInFrustum(frustum, player, MAX_ERASE_PER_SHOT);
		}
		for (EntitySpellProxyEntity proxy : level.getEntitiesOfClass(EntitySpellProxyEntity.class, searchBox)) {
			proxy.eraseDanmakuInFrustum(frustum, player, MAX_ERASE_PER_SHOT);
		}
		for (Entity entity : level.getEntities(player, searchBox, e -> e instanceof IYHDanmaku)) {
			if (entity instanceof SimplifiedProjectile proj) {
				if (frustum.contains(entity.position())) {
					proj.markErased(true);
				}
			}
		}

		DanmakuManager.flushErases();

		// Apply camera cooldown and deactivate
		applyCameraEffects(player, event.cameraStack);

		// Send score notification to client
		YoukaisHomecoming.HANDLER.toClientPlayer(
				new DanmakuPhotoToClient(result.getTotal(), result.calculateScore()), player);
	}

	/**
	 * Enrich youkai entities in the frame with their model IDs.
	 * Uses the entity list directly from the event (already resolved server-side).
	 */
	private static void enrichYoukaiEntities(CompoundTag frame, List<Entity> entitiesInFrame) {
		if (!frame.contains("Entities", Tag.TAG_LIST)) return;
		ListTag entityTags = frame.getList("Entities", Tag.TAG_COMPOUND);

		for (Entity entity : entitiesInFrame) {
			if (entity instanceof GeneralYoukaiEntity youkai) {
				String modelId = youkai.getModelId();
				if (modelId.isEmpty()) continue;

				// Find the matching tag entry and add ModelId
				for (int i = 0; i < entityTags.size(); i++) {
					CompoundTag tag = entityTags.getCompound(i);
					if (tag.getString("Id").equals("youkaishomecoming:" + net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(youkai.getType()).getPath())) {
						tag.putString("ModelId", modelId);
						break;
					}
				}
			}
		}
	}

	/**
	 * Apply camera cooldown and optionally deactivate the viewfinder.
	 */
	private static void applyCameraEffects(ServerPlayer player, ItemStack cameraStack) {
		if (cameraStack.getItem() instanceof CameraItem cameraItem) {
			if (YHModConfig.COMMON.exposureDeactivateAfterShot.get()) {
				cameraItem.deactivate(player, cameraStack);
			}
			int cooldown = YHModConfig.COMMON.exposureCameraCooldown.get();
			if (cooldown > 0) {
				player.server.tell(new net.minecraft.server.TickTask(
						player.server.getTickCount() + 1,
						() -> player.getCooldowns().addCooldown(cameraItem, cooldown)
				));
			}
		}
	}

	/**
	 * Extract type name and color name from a danmaku entity.
	 */
	public static String[] getDanmakuTypeAndColor(Entity entity) {
		if (entity instanceof ItemDanmakuEntity ide) {
			if (ide.getItem().getItem() instanceof DanmakuItem item) {
				return new String[]{item.type.name(), item.color.getName()};
			}
		}
		var key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
		String typeName = key != null ? key.getPath() : "unknown";
		return new String[]{typeName, ""};
	}

	/**
	 * Extract the camera's field of view in degrees from the camera item stack.
	 */
	private static float getCameraFov(ItemStack cameraStack) {
		if (cameraStack.getItem() instanceof CameraItem cameraItem) {
			float focalLength = cameraItem.getFocalLength(cameraStack);
			double fov = Fov.focalLengthToFov(focalLength, 36);
			return (float) (fov / 1.142857f);
		}
		return 70f;
	}
}
