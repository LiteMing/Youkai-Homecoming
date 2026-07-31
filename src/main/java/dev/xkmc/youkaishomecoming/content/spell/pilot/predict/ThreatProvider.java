package dev.xkmc.youkaishomecoming.content.spell.pilot.predict;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

public interface ThreatProvider {

    boolean supports(Entity entity);

    @Nullable
    Threat capture(Entity entity, int horizon);

}
