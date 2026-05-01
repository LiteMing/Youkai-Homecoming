package dev.xkmc.youkaishomecoming.events;

import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;

public class DanmakuBattleEnterEvent extends PlayerEvent {

	private final YoukaiEntity target;

	public DanmakuBattleEnterEvent(Player player, YoukaiEntity target) {
		super(player);
		this.target = target;
	}

	public YoukaiEntity getTarget() {
		return target;
	}
}
