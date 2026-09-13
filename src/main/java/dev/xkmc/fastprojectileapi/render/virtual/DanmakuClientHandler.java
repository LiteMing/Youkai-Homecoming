package dev.xkmc.fastprojectileapi.render.virtual;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
class DanmakuClientHandler {

	public static @Nullable Entity create(EntityType<?> type) {
		var level = Minecraft.getInstance().level;
		if (level == null) return null;
		return type.create(level);
	}

	public static void add(SimplifiedProjectile sp) {
		var level = Minecraft.getInstance().level;
		if (level == null) return;
		ClientDanmakuCache.get(level).add(sp);
	}

	public static void erase(int id, boolean kill) {
		var level = Minecraft.getInstance().level;
		if (level == null) return;
		ClientDanmakuCache.get(level).erase(id, kill);
	}

	public static void applyMotionReset(int id, Vec3 pos, Vec3 vel, int bounceCount,
			DanmakuBounceSyncPacket.ResetKind resetKind) {
		var level = Minecraft.getInstance().level;
		if (level == null) return;
		var e = ClientDanmakuCache.get(level).get(id);
		if (e == null) return;
		if (e instanceof ItemDanmakuEntity ide) {
			if (resetKind == DanmakuBounceSyncPacket.ResetKind.HOLD) {
				ide.enterHoldState(pos, vel);
			} else if (resetKind == DanmakuBounceSyncPacket.ResetKind.CONTINUE) {
				ide.applyContinueState(pos, vel);
			} else {
				ide.applyBounceState(pos, vel, bounceCount);
			}
		} else {
			e.setPosRaw(pos.x, pos.y, pos.z);
			e.snapMotionAndRotation(vel);
		}
	}

}
