package dev.xkmc.youkaishomecoming.content.spell.certification.network;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.entity.youkai.SpellCertificationEntity;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationState;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Server → client projection of the certification state machine (design doc §18).
 * Client never infers battle state; it only renders what this packet carries.
 */
@SerialClass
public class CertificationStateToClient extends SerialPacketBase {

	@SerialClass.SerialField
	public int entityId = -1;
	@SerialClass.SerialField
	public String state = CertificationState.DRAFT.name();
	@SerialClass.SerialField
	public int elapsedTicks = 0;
	@SerialClass.SerialField
	public int targetTicks = 0;
	@SerialClass.SerialField
	public int healthTotal = 0;
	@SerialClass.SerialField
	public int healthLeft = 0;
	@SerialClass.SerialField
	public int segmentMaxHealth = 0;
	@SerialClass.SerialField
	public int completedHealth = 0;
	@SerialClass.SerialField
	// l2serial 1.2.4 only allocates an incoming array when the field is null.
	public int[] healthSegments;
	@SerialClass.SerialField
	@Nullable
	public String failReason = null;
	/** True when this packet was sent to the trial author (D4: own trial state). */
	@SerialClass.SerialField
	public boolean mine = false;

	public CertificationStateToClient() {
	}

	private CertificationStateToClient(int entityId, CertificationState state, int elapsedTicks,
									   int targetTicks, int healthTotal, int healthLeft,
									   int segmentMaxHealth, int completedHealth, int[] healthSegments,
									   @Nullable String failReason, boolean mine) {
		this.entityId = entityId;
		this.state = state.name();
		this.elapsedTicks = elapsedTicks;
		this.targetTicks = targetTicks;
		this.healthTotal = healthTotal;
		this.healthLeft = healthLeft;
		this.segmentMaxHealth = segmentMaxHealth;
		this.completedHealth = completedHealth;
		this.healthSegments = healthSegments == null ? new int[0] : healthSegments.clone();
		this.failReason = failReason;
		this.mine = mine;
	}

	public static void send(SpellCertificationEntity entity, CertificationState state,
							int elapsedTicks, int targetTicks, int healthTotal,
							int healthLeft, int segmentMaxHealth, int completedHealth,
							int[] healthSegments, @Nullable String failReason) {
		ServerPlayer author = entity.controller() == null ? null : entity.controller().author();
		if (author != null) {
			YoukaisHomecoming.HANDLER.toClientPlayer(
					new CertificationStateToClient(entity.getId(), state, elapsedTicks, targetTicks,
							healthTotal, healthLeft, segmentMaxHealth, completedHealth,
							healthSegments, failReason, true),
					author);
		}
		if (YHModConfig.COMMON.certificationPublicRendering.get()) {
			YoukaisHomecoming.HANDLER.toTrackingPlayers(
					new CertificationStateToClient(entity.getId(), state, elapsedTicks, targetTicks,
							healthTotal, healthLeft, segmentMaxHealth, completedHealth,
							healthSegments, failReason, false),
					entity);
		}
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		CertificationClientHandler.acceptState(entityId, state, elapsedTicks, targetTicks,
				healthTotal, healthLeft, segmentMaxHealth, completedHealth,
				healthSegments, failReason, mine);
	}
}
