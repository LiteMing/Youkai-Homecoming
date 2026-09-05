package dev.xkmc.youkaishomecoming.content.entity.youkai;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.events.ClientEventHandlers;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

@SerialClass
public class SpellStateToClient extends SerialPacketBase {

	@SerialClass.SerialField
	public int entityId;

	@SerialClass.SerialField
	@Nullable
	public ResourceLocation spellId;

	@SerialClass.SerialField
	@Nullable
	public ResourceLocation phaseId;

	@SerialClass.SerialField
	public int phaseTick;

	@SerialClass.SerialField
	public boolean inDanmakuCombat;
	@SerialClass.SerialField
	public int spellMaxHealth;
	@SerialClass.SerialField
	public int spellElapsedTicks;
	@SerialClass.SerialField
	public int spellDurationTicks;
	@SerialClass.SerialField
	public int spellHealthTotal;
	@SerialClass.SerialField
	public int spellHealthCompleted;
	@SerialClass.SerialField
	public int spellHealthSegmentCount;
	@SerialClass.SerialField
	public int[] spellHealthSegments;

	public SpellStateToClient() {
	}

	public SpellStateToClient(int entityId,
							  @Nullable ResourceLocation spellId,
							  @Nullable ResourceLocation phaseId,
							  int phaseTick,
							  boolean inDanmakuCombat,
							  int spellMaxHealth, int spellElapsedTicks, int spellDurationTicks,
							  int spellHealthTotal, int spellHealthCompleted, int spellHealthSegmentCount,
							  int[] spellHealthSegments) {
		this.entityId = entityId;
		this.spellId = spellId;
		this.phaseId = phaseId;
		this.phaseTick = phaseTick;
		this.inDanmakuCombat = inDanmakuCombat;
		this.spellMaxHealth = spellMaxHealth;
		this.spellElapsedTicks = spellElapsedTicks;
		this.spellDurationTicks = spellDurationTicks;
		this.spellHealthTotal = spellHealthTotal;
		this.spellHealthCompleted = spellHealthCompleted;
		this.spellHealthSegmentCount = spellHealthSegmentCount;
		this.spellHealthSegments = spellHealthSegments == null ? new int[0] : spellHealthSegments.clone();
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		ClientEventHandlers.setSpellState(entityId, spellId, phaseId, phaseTick, inDanmakuCombat,
				spellMaxHealth, spellElapsedTicks, spellDurationTicks,
				spellHealthTotal, spellHealthCompleted, spellHealthSegmentCount, spellHealthSegments);
	}
}
