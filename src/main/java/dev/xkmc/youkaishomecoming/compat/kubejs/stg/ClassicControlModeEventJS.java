package dev.xkmc.youkaishomecoming.compat.kubejs.stg;

import dev.latvian.mods.kubejs.event.EventJS;
import dev.xkmc.youkaishomecoming.compat.stg.event.ClassicControlModeEvent;

public final class ClassicControlModeEventJS extends EventJS {

	private final boolean previousClassic;
	private final boolean classic;

	public ClassicControlModeEventJS(ClassicControlModeEvent event) {
		previousClassic = event.wasClassic();
		classic = event.isClassic();
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
		return previousClassic ? "classic" : "modern";
	}

	public String getMode() {
		return classic ? "classic" : "modern";
	}
}
