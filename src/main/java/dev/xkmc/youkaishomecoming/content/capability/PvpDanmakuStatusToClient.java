package dev.xkmc.youkaishomecoming.content.capability;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraftforge.network.NetworkEvent;

@SerialClass
public class PvpDanmakuStatusToClient extends SerialPacketBase {

	@SerialClass.SerialField
	public int entityId;
	@SerialClass.SerialField
	public String name;
	@SerialClass.SerialField
	public int life;
	@SerialClass.SerialField
	public int bomb;
	@SerialClass.SerialField
	public int maxLife;
	@SerialClass.SerialField
	public int maxBomb;
	@SerialClass.SerialField
	public boolean active;

	@Deprecated
	public PvpDanmakuStatusToClient() {
	}

	private PvpDanmakuStatusToClient(int entityId, String name, int life, int bomb, int maxLife, int maxBomb, boolean active) {
		this.entityId = entityId;
		this.name = name;
		this.life = life;
		this.bomb = bomb;
		this.maxLife = maxLife;
		this.maxBomb = maxBomb;
		this.active = active;
	}

	public static PvpDanmakuStatusToClient status(int entityId, String name, int life, int bomb, int maxLife, int maxBomb) {
		return new PvpDanmakuStatusToClient(entityId, name, life, bomb, maxLife, maxBomb, true);
	}

	public static PvpDanmakuStatusToClient clearAll() {
		return new PvpDanmakuStatusToClient(-1, "", 0, 0, 0, 0, false);
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		PvpDanmakuStatusOverlay.update(this);
	}

}
