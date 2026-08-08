package dev.xkmc.youkaishomecoming.content.spell.certification.network;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server → client: a certified spell item was granted (design doc §16, Phase 4).
 * Carries the certificate hash for the client HUD / collection overlay.
 */
@SerialClass
public class CertifiedSpellRewardToClient extends SerialPacketBase {

	@SerialClass.SerialField
	public String definitionHash = "";
	@SerialClass.SerialField
	public String spellName = "";

	public CertifiedSpellRewardToClient() {
	}

	public CertifiedSpellRewardToClient(String definitionHash, String spellName) {
		this.definitionHash = definitionHash;
		this.spellName = spellName;
	}

	public static void send(String definitionHash, String spellName) {
		dev.xkmc.youkaishomecoming.init.YoukaisHomecoming.HANDLER.toAllPlayers(new CertifiedSpellRewardToClient(definitionHash, spellName));
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		CertificationClientHandler.acceptReward(definitionHash, spellName);
	}
}
