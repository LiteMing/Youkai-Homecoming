package dev.xkmc.youkaishomecoming.events;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

public class DanmakuBattleExitEvent extends PlayerEvent {

	public enum Reason {
		MANUAL,
		COMBAT_DISABLED,
		TARGET_LOST,
		TARGET_DEFEATED,
		LAST_HIT
	}

	@Nullable
	private final Entity target;
	private final Reason reason;

	public DanmakuBattleExitEvent(Player player, @Nullable Entity target, Reason reason) {
		super(player);
		this.target = target;
		this.reason = reason;
	}

	@Nullable
	public Entity getTarget() {
		return target;
	}

	public Reason getReason() {
		return reason;
	}
}
