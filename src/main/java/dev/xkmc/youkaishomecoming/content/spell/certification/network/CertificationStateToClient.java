package dev.xkmc.youkaishomecoming.content.spell.certification.network;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.entity.youkai.SpellCertificationEntity;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationState;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
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
	public int breakHpTotalSeconds = 0;
	@SerialClass.SerialField
	public int breakHpLeftSeconds = 0;
	@SerialClass.SerialField
	@Nullable
	public String failReason = null;
	/** True when this packet was sent to the trial author (D4: own trial state). */
	@SerialClass.SerialField
	public boolean mine = false;

	public CertificationStateToClient() {
	}

	private CertificationStateToClient(int entityId, CertificationState state, int elapsedTicks,
									   int targetTicks, int breakHpTotalSeconds, int breakHpLeftSeconds,
									   @Nullable String failReason, boolean mine) {
		this.entityId = entityId;
		this.state = state.name();
		this.elapsedTicks = elapsedTicks;
		this.targetTicks = targetTicks;
		this.breakHpTotalSeconds = breakHpTotalSeconds;
		this.breakHpLeftSeconds = breakHpLeftSeconds;
		this.failReason = failReason;
		this.mine = mine;
	}

	public static void send(SpellCertificationEntity entity, CertificationState state,
							int elapsedTicks, int targetTicks, int breakHpTotalSeconds,
							int breakHpLeftSeconds, @Nullable String failReason) {
		ServerPlayer author = entity.controller() == null ? null : entity.controller().author();
		if (author != null) {
			YoukaisHomecoming.HANDLER.toClientPlayer(
					new CertificationStateToClient(entity.getId(), state, elapsedTicks, targetTicks,
							breakHpTotalSeconds, breakHpLeftSeconds, failReason, true),
					author);
		}
		YoukaisHomecoming.HANDLER.toTrackingPlayers(
				new CertificationStateToClient(entity.getId(), state, elapsedTicks, targetTicks,
						breakHpTotalSeconds, breakHpLeftSeconds, failReason, false),
				entity);
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		CertificationClientHandler.acceptState(entityId, state, elapsedTicks, targetTicks,
				breakHpTotalSeconds, breakHpLeftSeconds, failReason, mine);
	}
}
