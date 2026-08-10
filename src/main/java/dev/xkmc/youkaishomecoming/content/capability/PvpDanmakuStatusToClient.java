package dev.xkmc.youkaishomecoming.content.capability;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.spell.item.SpellContainer;
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
	@SerialClass.SerialField
	public int spellHealth;
	@SerialClass.SerialField
	public int spellMaxHealth;
	@SerialClass.SerialField
	public int spellElapsedTicks;
	@SerialClass.SerialField
	public int spellDurationTicks;

	@Deprecated
	public PvpDanmakuStatusToClient() {
	}

	private PvpDanmakuStatusToClient(int entityId, String name, int life, int bomb, int maxLife, int maxBomb,
									 boolean active, int spellHealth, int spellMaxHealth,
									 int spellElapsedTicks, int spellDurationTicks) {
		this.entityId = entityId;
		this.name = name;
		this.life = life;
		this.bomb = bomb;
		this.maxLife = maxLife;
		this.maxBomb = maxBomb;
		this.active = active;
		this.spellHealth = spellHealth;
		this.spellMaxHealth = spellMaxHealth;
		this.spellElapsedTicks = spellElapsedTicks;
		this.spellDurationTicks = spellDurationTicks;
	}

	public static PvpDanmakuStatusToClient status(int entityId, String name, int life, int bomb,
										 int maxLife, int maxBomb, SpellContainer.ActiveSpellStatus spell) {
		return new PvpDanmakuStatusToClient(entityId, name, life, bomb, maxLife, maxBomb, true,
				spell.health(), spell.maxHealth(), spell.elapsedTicks(), spell.durationTicks());
	}

	public static PvpDanmakuStatusToClient clearAll() {
		return new PvpDanmakuStatusToClient(-1, "", 0, 0, 0, 0, false, 0, 0, 0, 0);
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		PvpDanmakuStatusOverlay.update(this);
	}

}
