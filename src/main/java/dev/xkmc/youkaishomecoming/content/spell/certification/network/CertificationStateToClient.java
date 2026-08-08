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
	@Nullable
	public String failReason = null;

	public CertificationStateToClient() {
	}

	private CertificationStateToClient(int entityId, CertificationState state, int elapsedTicks,
									   int targetTicks, @Nullable String failReason) {
		this.entityId = entityId;
		this.state = state.name();
		this.elapsedTicks = elapsedTicks;
		this.targetTicks = targetTicks;
		this.failReason = failReason;
	}

	public static void send(SpellCertificationEntity entity, CertificationState state,
							int elapsedTicks, int targetTicks, @Nullable String failReason) {
		var packet = new CertificationStateToClient(entity.getId(), state, elapsedTicks, targetTicks, failReason);
		YoukaisHomecoming.HANDLER.toTrackingPlayers(packet, entity);
		ServerPlayer author = entity.controller() == null ? null : entity.controller().author();
		if (author != null) {
			YoukaisHomecoming.HANDLER.toClientPlayer(packet, author);
		}
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		CertificationClientHandler.acceptState(entityId, state, elapsedTicks, targetTicks, failReason);
	}
}
