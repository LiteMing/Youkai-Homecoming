package dev.xkmc.youkaishomecoming.compat.stg.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

public class StgResourceEvent extends PlayerEvent {

	private final Resource resource;
	private final int oldInternal;
	private final int newInternal;
	private final int displayUnit;

	public StgResourceEvent(ServerPlayer player, Resource resource, int oldInternal, int newInternal, int displayUnit) {
		super(player);
		this.resource = resource;
		this.oldInternal = oldInternal;
		this.newInternal = newInternal;
		this.displayUnit = displayUnit;
	}

	public Resource getResource() {
		return resource;
	}

	public int getOldInternal() {
		return oldInternal;
	}

	public int getNewInternal() {
		return newInternal;
	}

	public double getOldDisplayValue() {
		return oldInternal / (double) displayUnit;
	}

	public double getNewDisplayValue() {
		return newInternal / (double) displayUnit;
	}

	public int getDisplayUnit() {
		return displayUnit;
	}

	@Deprecated(forRemoval = false)
	public int getOldDisplay() {
		return oldInternal / displayUnit;
	}

	@Deprecated(forRemoval = false)
	public int getNewDisplay() {
		return newInternal / displayUnit;
	}

	public enum Resource {
		LIFE, BOMB, POWER, POINTS
	}

}
