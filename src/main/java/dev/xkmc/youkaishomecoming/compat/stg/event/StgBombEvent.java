package dev.xkmc.youkaishomecoming.compat.stg.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

public abstract class StgBombEvent extends PlayerEvent {

	private final boolean manual;
	@Nullable
	private final LivingEntity source;
	private final int erasedCount;

	protected StgBombEvent(ServerPlayer player, boolean manual, @Nullable LivingEntity source, int erasedCount) {
		super(player);
		this.manual = manual;
		this.source = source;
		this.erasedCount = erasedCount;
	}

	public boolean isManual() {
		return manual;
	}

	public boolean isAuto() {
		return !manual;
	}

	@Nullable
	public LivingEntity getSource() {
		return source;
	}

	public int getErasedCount() {
		return erasedCount;
	}

	public static class Manual extends StgBombEvent {

		public Manual(ServerPlayer player, int erasedCount) {
			super(player, true, null, erasedCount);
		}

	}

	public static class Auto extends StgBombEvent {

		public Auto(ServerPlayer player, @Nullable LivingEntity source, int erasedCount) {
			super(player, false, source, erasedCount);
		}

	}

}
