package dev.xkmc.youkaishomecoming.compat.exposure;

import dev.xkmc.youkaishomecoming.compat.stg.YHStgApi;
import dev.xkmc.youkaishomecoming.content.entity.youkai.GeneralYoukaiEntity;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import io.github.mortuusars.exposure.forge.api.event.FrameAddedEvent;
import io.github.mortuusars.exposure.forge.api.event.ModifyEntityInFrameDataEvent;
import io.github.mortuusars.exposure.forge.api.event.ModifyFrameExtraDataEvent;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.item.camera.CameraItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Exposure 1.9 compatibility for danmaku photography and spell replication. */
public final class ExposureCompat {

	/** Pre-serialization candidates, consumed by the matching frame-added event. */
	private static final Map<UUID, EraseResult> PENDING_RESULTS = new HashMap<>();

	private ExposureCompat() {
	}

	@SubscribeEvent
	public static void onModifyFrameExtraData(ModifyFrameExtraDataEvent event) {
		ServerPlayer player = event.getCameraHolder().getServerPlayerExecutingExposure().orElse(null);
		if (player == null || !(player.level() instanceof ServerLevel level)) return;

		PENDING_RESULTS.remove(player.getUUID());
		if (YHStgApi.getBomb(player) < 1.0) {
			new EraseResult().writeToFrame(event.getData());
			return;
		}

		double fov = event.getCaptureProperties().fov().orElseGet(
				() -> cameraFov(level, event.getCamera()));
		EraseResult result = DanmakuCaptureService.collect(player, (float) fov);
		result.writeToFrame(event.getData());
		if (!result.isEmpty()) PENDING_RESULTS.put(player.getUUID(), result);
	}

	/** Exposure 1.9 stores per-entity extension data through a dedicated event. */
	@SubscribeEvent
	public static void onModifyEntityInFrameData(ModifyEntityInFrameDataEvent event) {
		if (event.getEntityInFrame() instanceof GeneralYoukaiEntity youkai) {
			String modelId = youkai.getModelId();
			if (!modelId.isEmpty()) event.getData().putString("ModelId", modelId);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onFrameAdded(FrameAddedEvent event) {
		ServerPlayer player = event.getCameraHolder().getServerPlayerExecutingExposure().orElse(null);
		if (player == null || !(player.level() instanceof ServerLevel)) return;

		EraseResult result = PENDING_RESULTS.remove(player.getUUID());
		DanmakuCaptureService.CaptureOutcome outcome = DanmakuCaptureService.commit(player, result);
		if (!outcome.success()) return;

		applyCameraEffects(player, event.getCamera());
		YoukaisHomecoming.HANDLER.toClientPlayer(
				new DanmakuPhotoToClient(outcome.erased(), outcome.score(), encodeFrame(event.getFrame())),
				player);
	}

	@SubscribeEvent
	public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		PENDING_RESULTS.remove(event.getEntity().getUUID());
	}

	private static double cameraFov(ServerLevel level, ItemStack cameraStack) {
		return cameraStack.getItem() instanceof CameraItem cameraItem
				? cameraItem.getFov(level, cameraStack) : 70.0;
	}

	private static void applyCameraEffects(ServerPlayer player, ItemStack cameraStack) {
		if (!(cameraStack.getItem() instanceof CameraItem cameraItem)) return;
		if (YHModConfig.COMMON.exposureDeactivateAfterShot.get()) {
			cameraItem.deactivate(player, cameraStack);
		}
		int cooldown = YHModConfig.COMMON.exposureCameraCooldown.get();
		if (cooldown > 0) {
			player.server.tell(new net.minecraft.server.TickTask(
					player.server.getTickCount() + 1,
					() -> player.getCooldowns().addCooldown(cameraItem, cooldown)));
		}
	}

	private static CompoundTag encodeFrame(Frame frame) {
		Tag encoded = Frame.CODEC.encodeStart(NbtOps.INSTANCE, frame).result().orElse(null);
		return encoded instanceof CompoundTag compound ? compound : new CompoundTag();
	}
}
