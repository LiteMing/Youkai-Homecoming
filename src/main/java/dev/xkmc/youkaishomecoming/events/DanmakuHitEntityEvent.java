package dev.xkmc.youkaishomecoming.events;

import dev.xkmc.youkaishomecoming.content.entity.danmaku.YHBaseDanmakuEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

public class DanmakuHitEntityEvent extends Event {

	private final YHBaseDanmakuEntity danmaku;
	@Nullable
	private final Entity caster;
	private final Entity hitEntity;
	@Nullable
	private final ResourceLocation spellId;
	@Nullable
	private final ResourceLocation phaseId;

	public DanmakuHitEntityEvent(YHBaseDanmakuEntity danmaku, @Nullable Entity caster, Entity hitEntity,
								 @Nullable ResourceLocation spellId, @Nullable ResourceLocation phaseId) {
		this.danmaku = danmaku;
		this.caster = caster;
		this.hitEntity = hitEntity;
		this.spellId = spellId;
		this.phaseId = phaseId;
	}

	public YHBaseDanmakuEntity getDanmaku() {
		return danmaku;
	}

	@Nullable
	public Entity getCaster() {
		return caster;
	}

	public Entity getHitEntity() {
		return hitEntity;
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
