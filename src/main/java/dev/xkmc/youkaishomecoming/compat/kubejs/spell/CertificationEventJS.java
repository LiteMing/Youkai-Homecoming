package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.latvian.mods.kubejs.event.EventJS;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationFailReason;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationState;
import net.minecraft.server.level.ServerPlayer;

/**
 * KubeJS certification lifecycle events (design doc §21): start, success, fail,
 * reward claim. Fired server-side; script can read the state and reason.
 */
public class CertificationEventJS extends EventJS {

	public final ServerPlayer player;
	public final CertificationState state;
	public final String definitionHash;
	public final String failReason;

	public CertificationEventJS(ServerPlayer player, CertificationState state,
								String definitionHash, String failReason) {
		this.player = player;
		this.state = state;
		this.definitionHash = definitionHash;
		this.failReason = failReason;
	}

	public String getState() {
		return state.name();
	}

	public String getDefinitionHash() {
		return definitionHash;
	}

	public String getFailReason() {
		return failReason;
	}

	public boolean failed() {
		return state == CertificationState.FAILED || state == CertificationState.SYSTEM_ERROR;
	}

	public String failureId() {
		return failReason;
	}
}
