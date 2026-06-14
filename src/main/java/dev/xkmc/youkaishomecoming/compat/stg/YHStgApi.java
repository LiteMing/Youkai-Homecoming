package dev.xkmc.youkaishomecoming.compat.stg;

import dev.xkmc.youkaishomecoming.compat.stg.event.StgBombEvent;
import dev.xkmc.youkaishomecoming.compat.stg.event.StgResourceEvent;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;

import java.util.Objects;

public final class YHStgApi {

	private static final int RESOURCE_UNIT = 5;
	private static final int POWER_UNIT = 100;

	private YHStgApi() {
	}

	public static StgCombatMode getMode(ServerPlayer player) {
		return cap(player).getStgCombatMode();
	}

	public static void setMode(ServerPlayer player, StgCombatMode mode) {
		var cap = cap(player);
		cap.setStgCombatMode(Objects.requireNonNull(mode, "mode"));
		cap.sync();
	}

	public static int getLife(ServerPlayer player) {
		return cap(player).getLife() / RESOURCE_UNIT;
	}

	public static int getBomb(ServerPlayer player) {
		return cap(player).getBomb() / RESOURCE_UNIT;
	}

	public static int getPower(ServerPlayer player) {
		return cap(player).getPower() / POWER_UNIT;
	}

	public static void setLife(ServerPlayer player, int displayLife) {
		setResource(player, StgResourceEvent.Resource.LIFE, toInternal(displayLife, RESOURCE_UNIT));
	}

	public static void setBomb(ServerPlayer player, int displayBomb) {
		setResource(player, StgResourceEvent.Resource.BOMB, toInternal(displayBomb, RESOURCE_UNIT));
	}

	public static void setPower(ServerPlayer player, int displayPower) {
		setResource(player, StgResourceEvent.Resource.POWER, toInternal(displayPower, POWER_UNIT));
	}

	public static void addLife(ServerPlayer player, int amount) {
		addResource(player, StgResourceEvent.Resource.LIFE, amount, RESOURCE_UNIT);
	}

	public static void addBomb(ServerPlayer player, int amount) {
		addResource(player, StgResourceEvent.Resource.BOMB, amount, RESOURCE_UNIT);
	}

	public static void addPower(ServerPlayer player, int amount) {
		addResource(player, StgResourceEvent.Resource.POWER, amount, POWER_UNIT);
	}

	public static boolean tryManualBomb(ServerPlayer player) {
		var cap = cap(player);
		if (!cap.useBomb()) {
			return false;
		}
		int erased = cap.eraseActiveDanmaku(0, true);
		cap.sync();
		MinecraftForge.EVENT_BUS.post(new StgBombEvent.Manual(player, erased));
		return true;
	}

	public static int eraseActiveDanmaku(ServerPlayer player, double radius, boolean sessionsOnly) {
		return cap(player).eraseActiveDanmaku(Math.max(0, radius), sessionsOnly);
	}

	public static boolean isInDanmakuSession(ServerPlayer player) {
		return cap(player).isInSession();
	}

	public static boolean isWeak(ServerPlayer player) {
		return cap(player).isWeak();
	}

	/**
	 * Entry point for external danmaku-like projectiles.
	 * Returns false when the hit should be ignored by YH danmaku combat rules.
	 */
	public static boolean tryPlayerDanmakuHit(ServerPlayer player, LivingEntity target) {
		Objects.requireNonNull(target, "target");
		return cap(player).shouldHurt(target);
	}

	private static GrazeCapability cap(ServerPlayer player) {
		Objects.requireNonNull(player, "player");
		return GrazeCapability.HOLDER.get(player);
	}

	private static void addResource(ServerPlayer player, StgResourceEvent.Resource resource, int amount, int unit) {
		var cap = cap(player);
		int oldValue = getInternal(cap, resource);
		setResource(player, resource, clampInternal((long) oldValue + (long) amount * unit));
	}

	private static void setResource(ServerPlayer player, StgResourceEvent.Resource resource, int internalValue) {
		var cap = cap(player);
		int oldValue = getInternal(cap, resource);
		int newValue = clampInternal(internalValue);
		setInternal(cap, resource, newValue);
		cap.sync();
		MinecraftForge.EVENT_BUS.post(new StgResourceEvent(player, resource, oldValue, newValue, unit(resource)));
	}

	private static int getInternal(GrazeCapability cap, StgResourceEvent.Resource resource) {
		return switch (resource) {
			case LIFE -> cap.getLife();
			case BOMB -> cap.getBomb();
			case POWER -> cap.getPower();
		};
	}

	private static void setInternal(GrazeCapability cap, StgResourceEvent.Resource resource, int value) {
		switch (resource) {
			case LIFE -> cap.setLife(value);
			case BOMB -> cap.setBomb(value);
			case POWER -> cap.setPower(value);
		}
	}

	private static int unit(StgResourceEvent.Resource resource) {
		return resource == StgResourceEvent.Resource.POWER ? POWER_UNIT : RESOURCE_UNIT;
	}

	private static int toInternal(int displayValue, int unit) {
		return displayValue <= 0 ? 0 : clampInternal((long) displayValue * unit);
	}

	private static int clampInternal(long value) {
		if (value <= 0) return 0;
		return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
	}

}
