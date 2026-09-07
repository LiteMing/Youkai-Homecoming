package dev.xkmc.youkaishomecoming.compat.stg;

import dev.xkmc.youkaishomecoming.compat.stg.control.ClassicControlClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Read-only client projection for scripts and client-side integrations. */
@OnlyIn(Dist.CLIENT)
public final class YHStgClient {

	private YHStgClient() {
	}

	public static boolean isClassicControlEnabled() {
		return ClassicControlClient.isEnabled();
	}

	public static String getControlMode() {
		return isClassicControlEnabled() ? "classic" : "modern";
	}
}
