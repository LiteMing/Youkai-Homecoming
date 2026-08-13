package dev.xkmc.youkaishomecoming.content.entity.fairy;

import dev.xkmc.youkaishomecoming.content.entity.UntargetedPlayerSpellHostile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Generic combat fairy; named Touhou characters remain neutral until engaged. */
public class SmallFairyEntity extends FairyEntity implements UntargetedPlayerSpellHostile {

	public SmallFairyEntity(EntityType<? extends SmallFairyEntity> type, Level level) {
		super(type, level);
	}

}
