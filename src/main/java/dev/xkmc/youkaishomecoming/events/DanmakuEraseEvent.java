package dev.xkmc.youkaishomecoming.events;

import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

public class DanmakuEraseEvent extends PlayerEvent {

	private final YoukaiEntity attacker;
	private final GrazeCapability.HitType hitType;
	@Nullable
	private final ResourceLocation spellId;
	@Nullable
	private final ResourceLocation phaseId;

	public DanmakuEraseEvent(Player player, YoukaiEntity attacker, GrazeCapability.HitType hitType,
							 @Nullable ResourceLocation spellId, @Nullable ResourceLocation phaseId) {
		super(player);
		this.attacker = attacker;
		this.hitType = hitType;
		this.spellId = spellId;
		this.phaseId = phaseId;
	}

	public YoukaiEntity getAttacker() {
		return attacker;
	}

	public GrazeCapability.HitType getHitType() {
		return hitType;
	}

	@Nullable
	public ResourceLocation getSpellId() {
		return spellId;
	}

	@Nullable
	public ResourceLocation getPhaseId() {
		return phaseId;
	}
}
