package dev.xkmc.youkaishomecoming.compat.stg.event;

import net.minecraftforge.eventbus.api.Event;

/**
 * Client-side notification that the server-confirmed planar control mode changed.
 * This event is not cancelable and is posted only for actual state transitions.
 */
public final class ClassicControlModeEvent extends Event {

	private final boolean previousClassic;
	private final boolean classic;

	public ClassicControlModeEvent(boolean previousClassic, boolean classic) {
		this.previousClassic = previousClassic;
		this.classic = classic;
	}

	public boolean wasClassic() {
		return previousClassic;
	}

	public boolean isClassic() {
		return classic;
	}

	public boolean isModern() {
		return !classic;
	}

	public String getPreviousMode() {
		return modeName(previousClassic);
	}

	public String getMode() {
		return modeName(classic);
	}

	private static String modeName(boolean classic) {
		return classic ? "classic" : "modern";
	}
}
