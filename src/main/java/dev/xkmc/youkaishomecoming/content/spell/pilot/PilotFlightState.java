package dev.xkmc.youkaishomecoming.content.spell.pilot;

/** Player flight intent survives vanilla clearing the flying flag on brief ground contact. */
public final class PilotFlightState {

	private boolean flying;
	private int supportedSince = -1;

	public boolean update(int tick, boolean flightAvailable, boolean currentlyFlying, boolean supported) {
		if (!flightAvailable) {
			reset();
			return false;
		}
		if (currentlyFlying || !supported) flying = true;
		if (!supported) supportedSince = -1;
		else if (supportedSince < 0 || tick < supportedSince) supportedSince = tick;
		return flying;
	}

	public void takeOff() {
		flying = true;
		supportedSince = -1;
	}

	/** Keep flight through the committed route; only a settled, safe ground course may land. */
	public boolean landIfReady(int tick, int commitTicks, boolean wantsLift, boolean safeGroundCourse) {
		if (!flying || supportedSince < 0 || wantsLift || !safeGroundCourse
				|| tick - supportedSince + 1 < Math.max(1, commitTicks)) return false;
		flying = false;
		return true;
	}

	public void reset() {
		flying = false;
		supportedSince = -1;
	}
}
