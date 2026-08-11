package dev.xkmc.youkaishomecoming.content.spell.certification;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * No-Hit failure classification (design doc §5.6, D14). SYSTEM_ERROR and server
 * protection aborts always refund in full; normal No-Hit failures and manual
 * aborts use the configured refund ratio.
 */
public enum CertificationFailReason {
	HIT("hit", false),
	OUT_OF_ARENA("out_of_arena", false),
	EXITED("exited", false),
	DEATH("death", false),
	OTHER_BATTLE("other_battle", false),
	DISCONNECT("disconnect", false),
	ILLEGAL_MOVE("illegal_move", false),
	TIMEOUT("timeout", false),
	OTHER_SPELL("other_spell", false),
	BOMB("bomb", false),
	ABORTED("aborted", false),
	SYSTEM_ERROR("system_error", true),
	RUNTIME_LIMIT("runtime_limit", true);

	private final String id;
	private final boolean fullRefund;

	CertificationFailReason(String id, boolean fullRefund) {
		this.id = id;
		this.fullRefund = fullRefund;
	}

	public String id() {
		return id;
	}

	public boolean fullRefund() {
		return fullRefund;
	}

	public MutableComponent displayName() {
		return Component.translatable("youkaishomecoming.cert.fail_reason." + id);
	}

	public static MutableComponent displayName(@Nullable String id) {
		if (id != null) {
			for (CertificationFailReason reason : values()) {
				if (reason.id.equals(id)) return reason.displayName();
			}
		}
		return Component.translatable("youkaishomecoming.cert.fail_reason.unknown");
	}
}
