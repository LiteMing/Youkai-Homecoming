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

	public int getOldDisplay() {
		return oldInternal / displayUnit;
	}

	public int getNewDisplay() {
		return newInternal / displayUnit;
	}

	public enum Resource {
		LIFE, BOMB, POWER, POINTS
	}

}
