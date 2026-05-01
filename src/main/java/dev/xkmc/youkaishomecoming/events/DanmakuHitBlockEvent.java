package dev.xkmc.youkaishomecoming.events;

import dev.xkmc.youkaishomecoming.content.entity.danmaku.YHBaseDanmakuEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

public class DanmakuHitBlockEvent extends Event {

	private final YHBaseDanmakuEntity danmaku;
	@Nullable
	private final Entity caster;
	private final BlockHitResult hitResult;
	@Nullable
	private final ResourceLocation spellId;
	@Nullable
	private final ResourceLocation phaseId;

	public DanmakuHitBlockEvent(YHBaseDanmakuEntity danmaku, @Nullable Entity caster, BlockHitResult hitResult,
								@Nullable ResourceLocation spellId, @Nullable ResourceLocation phaseId) {
		this.danmaku = danmaku;
		this.caster = caster;
		this.hitResult = hitResult;
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

	public BlockHitResult getHitResult() {
		return hitResult;
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
