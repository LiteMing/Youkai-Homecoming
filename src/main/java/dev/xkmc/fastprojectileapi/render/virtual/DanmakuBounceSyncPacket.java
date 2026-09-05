package dev.xkmc.fastprojectileapi.render.virtual;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

@SerialClass
public class DanmakuBounceSyncPacket extends SerialPacketBase {

	public enum ResetKind {
		BOUNCE,
		HOLD,
		CONTINUE
	}

	@SerialClass.SerialField
	private int entityId;
	@SerialClass.SerialField
	private double posX, posY, posZ;
	@SerialClass.SerialField
	private double velX, velY, velZ;
	@SerialClass.SerialField
	private int bounceCount;
	@SerialClass.SerialField
	private ResetKind resetKind = ResetKind.BOUNCE;

	public DanmakuBounceSyncPacket() {
	}

	public DanmakuBounceSyncPacket(int entityId, Vec3 pos, Vec3 vel, int bounceCount) {
		this(entityId, pos, vel, bounceCount, ResetKind.BOUNCE);
	}

	public DanmakuBounceSyncPacket(int entityId, Vec3 pos, Vec3 vel, int bounceCount, ResetKind resetKind) {
		this.entityId = entityId;
		this.posX = pos.x;
		this.posY = pos.y;
		this.posZ = pos.z;
		this.velX = vel.x;
		this.velY = vel.y;
		this.velZ = vel.z;
		this.bounceCount = bounceCount;
		this.resetKind = resetKind != null ? resetKind : ResetKind.BOUNCE;
	}

	@Override
	public void handle(NetworkEvent.Context ctx) {
		var level = Minecraft.getInstance().level;
		if (level == null) return;
		var cache = ClientDanmakuCache.get(level);
		var e = cache.get(entityId);
		if (e != null) {
			Vec3 pos = new Vec3(posX, posY, posZ);
			Vec3 vel = new Vec3(velX, velY, velZ);
			if (e instanceof dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity ide) {
				if (resetKind == ResetKind.HOLD) {
					ide.enterHoldState(pos, vel);
				} else if (resetKind == ResetKind.CONTINUE) {
					ide.applyContinueState(pos, vel);
				} else {
					ide.applyBounceState(pos, vel, bounceCount);
				}
			} else {
				e.setPosRaw(posX, posY, posZ);
				e.snapMotionAndRotation(vel);
			}
		}
	}
}
