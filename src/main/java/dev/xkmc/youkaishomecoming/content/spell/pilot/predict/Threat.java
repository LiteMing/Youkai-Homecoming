package dev.xkmc.youkaishomecoming.content.spell.pilot.predict;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

public record Threat(int entityId, ThreatFrame[] frames, ThreatSemantic semantic, @Nullable Entity source, float damage) {

}
