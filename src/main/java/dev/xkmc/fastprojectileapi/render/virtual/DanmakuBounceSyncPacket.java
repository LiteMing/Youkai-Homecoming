package dev.xkmc.fastprojectileapi.render.virtual;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

@SerialClass
public class DanmakuBounceSyncPacket extends SerialPacketBase {

	@SerialClass.SerialField
	private int entityId;
	@SerialClass.SerialField
	private double posX, posY, posZ;
	@SerialClass.SerialField
	private double velX, velY, velZ;

	public DanmakuBounceSyncPacket() {
	}

	public DanmakuBounceSyncPacket(int entityId, Vec3 pos, Vec3 vel) {
		this.entityId = entityId;
		this.posX = pos.x;
		this.posY = pos.y;
		this.posZ = pos.z;
		this.velX = vel.x;
		this.velY = vel.y;
		this.velZ = vel.z;
	}

	@Override
	public void handle(NetworkEvent.Context ctx) {
		var level = Minecraft.getInstance().level;
		if (level == null) return;
		var cache = ClientDanmakuCache.get(level);
		var e = cache.get(entityId);
		if (e != null) {
			e.setPosRaw(posX, posY, posZ);
			e.setDeltaMovement(velX, velY, velZ);
			e.lerpMotion(velX, velY, velZ);
		}
	}
}
