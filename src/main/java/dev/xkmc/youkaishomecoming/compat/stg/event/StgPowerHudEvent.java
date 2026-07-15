package dev.xkmc.youkaishomecoming.compat.stg.event;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;

public class StgPowerHudEvent extends PlayerEvent {

	private boolean showPower;

	public StgPowerHudEvent(Player player, boolean showPower) {
		super(player);
		this.showPower = showPower;
	}

	public boolean shouldShowPower() {
		return showPower;
	}

	public void setShowPower(boolean showPower) {
		this.showPower = showPower;
	}

}
