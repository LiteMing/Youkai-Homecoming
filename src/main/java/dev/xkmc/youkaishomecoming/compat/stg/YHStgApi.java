package dev.xkmc.youkaishomecoming.compat.stg;

import dev.xkmc.youkaishomecoming.compat.stg.event.StgBombEvent;
import dev.xkmc.youkaishomecoming.compat.stg.event.StgPowerHudEvent;
import dev.xkmc.youkaishomecoming.compat.stg.event.StgResourceEvent;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DanmakuItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.ISpellItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.LaserItem;
import dev.xkmc.youkaishomecoming.init.data.YHTagGen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import java.util.Objects;

public final class YHStgApi {

	private static final int RESOURCE_UNIT = 5;
	private static final int POWER_UNIT = 100;
	private static final int POINTS_UNIT = 100;

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

	public static double getLife(ServerPlayer player) {
		return fromInternal(getLifeRaw(player), RESOURCE_UNIT);
	}

	public static double getBomb(ServerPlayer player) {
		return fromInternal(getBombRaw(player), RESOURCE_UNIT);
	}

	public static double getPower(ServerPlayer player) {
		return fromInternal(getPowerRaw(player), POWER_UNIT);
	}

	public static double getPoints(ServerPlayer player) {
		return fromInternal(getPointsRaw(player), POINTS_UNIT);
	}

	public static int getLifeRaw(ServerPlayer player) {
		return cap(player).getLife();
	}

	public static int getBombRaw(ServerPlayer player) {
		return cap(player).getBomb();
	}

	public static int getPowerRaw(ServerPlayer player) {
		return cap(player).getPower();
	}

	public static int getPointsRaw(ServerPlayer player) {
		return cap(player).getPoints();
	}

	public static void setLife(ServerPlayer player, double displayLife) {
		setResource(player, StgResourceEvent.Resource.LIFE, toInternal(displayLife, RESOURCE_UNIT));
	}

	public static void setBomb(ServerPlayer player, double displayBomb) {
		setResource(player, StgResourceEvent.Resource.BOMB, toInternal(displayBomb, RESOURCE_UNIT));
	}

	public static void setPower(ServerPlayer player, double displayPower) {
		setResource(player, StgResourceEvent.Resource.POWER, toInternal(displayPower, POWER_UNIT));
	}

	public static void setPoints(ServerPlayer player, double points) {
		setResource(player, StgResourceEvent.Resource.POINTS, toInternal(points, POINTS_UNIT));
	}

	public static void setLifeRaw(ServerPlayer player, int internalLife) {
		setResource(player, StgResourceEvent.Resource.LIFE, internalLife);
	}

	public static void setBombRaw(ServerPlayer player, int internalBomb) {
		setResource(player, StgResourceEvent.Resource.BOMB, internalBomb);
	}

	public static void setPowerRaw(ServerPlayer player, int internalPower) {
		setResource(player, StgResourceEvent.Resource.POWER, internalPower);
	}

	public static void setPointsRaw(ServerPlayer player, int internalPoints) {
		setResource(player, StgResourceEvent.Resource.POINTS, internalPoints);
	}

	public static void addLife(ServerPlayer player, double amount) {
		addResource(player, StgResourceEvent.Resource.LIFE, amount, RESOURCE_UNIT);
	}

	public static void addBomb(ServerPlayer player, double amount) {
		addResource(player, StgResourceEvent.Resource.BOMB, amount, RESOURCE_UNIT);
	}

	public static void addPower(ServerPlayer player, double amount) {
		addResource(player, StgResourceEvent.Resource.POWER, amount, POWER_UNIT);
	}

	public static void addPoints(ServerPlayer player, double amount) {
		addResource(player, StgResourceEvent.Resource.POINTS, amount, POINTS_UNIT);
	}

	public static void addLifeRaw(ServerPlayer player, int internalAmount) {
		addResourceRaw(player, StgResourceEvent.Resource.LIFE, internalAmount);
	}

	public static void addBombRaw(ServerPlayer player, int internalAmount) {
		addResourceRaw(player, StgResourceEvent.Resource.BOMB, internalAmount);
	}

	public static void addPowerRaw(ServerPlayer player, int internalAmount) {
		addResourceRaw(player, StgResourceEvent.Resource.POWER, internalAmount);
	}

	public static void addPointsRaw(ServerPlayer player, int internalAmount) {
		addResourceRaw(player, StgResourceEvent.Resource.POINTS, internalAmount);
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
		return cap(player).isInDanmakuCombat();
	}

	public static boolean isForcedDanmakuCombat(ServerPlayer player) {
		return cap(player).isForcedDanmakuCombat();
	}

	/** True when the player has at least one active Youkai STG session. */
	public static boolean hasActiveYoukaiSession(ServerPlayer player) {
		return cap(player).isInSession();
	}

	/**
	 * Snapshot of current STG opponent entity UUIDs (Youkai sessions + PvP opponents).
	 * Empty when not in danmaku combat.
	 */
	public static java.util.List<java.util.UUID> getOpponentIds(ServerPlayer player) {
		return cap(player).snapshotOpponents().ids();
	}

	/**
	 * Loaded living opponents for the player's current STG combat snapshot.
	 * Prefer this over raw UUIDs for dialogue / targeting hooks.
	 */
	public static java.util.List<LivingEntity> getOpponents(ServerPlayer player) {
		return cap(player).snapshotOpponents().entities();
	}

	public static void setDanmakuCombat(ServerPlayer player, boolean enabled) {
		var cap = cap(player);
		// Debug/admin path: bypass spell-card requirement; disable clears full combat state
		if (!enabled) {
			cap.clearCombatState(true);
			return;
		}
		cap.setForcedDanmakuCombat(true, true);
		cap.sync();
	}

	/**
	 * Full danmaku battle defeat flow: clear sessions, reset life/bomb/power to
	 * defaults, apply weak/beaten and fire {@code StgCombatEvent.Defeat}. Used by
	 * the certification No-Hit failure so a failed attempt behaves like a lost
	 * battle.
	 */
	public static void defeat(ServerPlayer player) {
		cap(player).defeat(null);
	}

	public static boolean isWeak(ServerPlayer player) {
		return cap(player).isWeak();
	}

	public static boolean shouldShowPower(Player player) {
		Objects.requireNonNull(player, "player");
		boolean show = shouldShowPowerForStack(player.getMainHandItem()) ||
				shouldShowPowerForStack(player.getOffhandItem());
		var event = new StgPowerHudEvent(player, show);
		MinecraftForge.EVENT_BUS.post(event);
		return event.shouldShowPower();
	}

	public static boolean shouldShowPowerForStack(ItemStack stack) {
		if (stack.isEmpty()) return false;
		return stack.is(YHTagGen.DANMAKU_SHOOTER) ||
				stack.getItem() instanceof DanmakuItem ||
				stack.getItem() instanceof LaserItem ||
				stack.getItem() instanceof ISpellItem;
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

	private static void addResource(ServerPlayer player, StgResourceEvent.Resource resource, double amount, int unit) {
		addResourceRaw(player, resource, toInternalDelta(amount, unit));
	}

	private static void addResourceRaw(ServerPlayer player, StgResourceEvent.Resource resource, int internalAmount) {
		if (resource == StgResourceEvent.Resource.POINTS) {
			addPointsResource(player, internalAmount);
			return;
		}
		var cap = cap(player);
		int oldValue = getInternal(cap, resource);
		setResource(player, resource, clampInternal((long) oldValue + internalAmount));
	}

	private static void addPointsResource(ServerPlayer player, int internalAmount) {
		var cap = cap(player);
		int oldLife = cap.getLife();
		int oldBomb = cap.getBomb();
		int oldPoints = cap.getPoints();
		cap.addPoints(internalAmount);
		int newLife = cap.getLife();
		int newBomb = cap.getBomb();
		int newPoints = cap.getPoints();
		cap.sync();
		postResourceEvent(player, StgResourceEvent.Resource.POINTS, oldPoints, newPoints);
		if (oldBomb != newBomb) {
			postResourceEvent(player, StgResourceEvent.Resource.BOMB, oldBomb, newBomb);
		}
		if (oldLife != newLife) {
			postResourceEvent(player, StgResourceEvent.Resource.LIFE, oldLife, newLife);
		}
	}

	private static void setResource(ServerPlayer player, StgResourceEvent.Resource resource, int internalValue) {
		var cap = cap(player);
		int oldValue = getInternal(cap, resource);
		int newValue = clampInternal(internalValue);
		setInternal(cap, resource, newValue);
		newValue = getInternal(cap, resource);
		cap.sync();
		postResourceEvent(player, resource, oldValue, newValue);
	}

	private static void postResourceEvent(ServerPlayer player, StgResourceEvent.Resource resource, int oldValue, int newValue) {
		MinecraftForge.EVENT_BUS.post(new StgResourceEvent(player, resource, oldValue, newValue, unit(resource)));
	}

	private static int getInternal(GrazeCapability cap, StgResourceEvent.Resource resource) {
		return switch (resource) {
			case LIFE -> cap.getLife();
			case BOMB -> cap.getBomb();
			case POWER -> cap.getPower();
			case POINTS -> cap.getPoints();
		};
	}

	private static void setInternal(GrazeCapability cap, StgResourceEvent.Resource resource, int value) {
		switch (resource) {
			case LIFE -> cap.setLife(value);
			case BOMB -> cap.setBomb(value);
			case POWER -> cap.setPower(value);
			case POINTS -> cap.setPoints(value);
		}
	}

	private static int unit(StgResourceEvent.Resource resource) {
		return switch (resource) {
			case POWER -> POWER_UNIT;
			case POINTS -> POINTS_UNIT;
			case LIFE, BOMB -> RESOURCE_UNIT;
		};
	}

	private static double fromInternal(int value, int unit) {
		return value / (double) unit;
	}

	private static int toInternal(double displayValue, int unit) {
		if (!Double.isFinite(displayValue)) {
			throw new IllegalArgumentException("STG resource value must be finite");
		}
		return displayValue <= 0 ? 0 : clampInternal(Math.round(displayValue * unit));
	}

	private static int toInternalDelta(double displayValue, int unit) {
		if (!Double.isFinite(displayValue)) {
			throw new IllegalArgumentException("STG resource delta must be finite");
		}
		return clampInternalDelta(Math.round(displayValue * unit));
	}

	private static int clampInternal(long value) {
		if (value <= 0) return 0;
		return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
	}

	private static int clampInternalDelta(long value) {
		if (value > Integer.MAX_VALUE) return Integer.MAX_VALUE;
		if (value < Integer.MIN_VALUE) return Integer.MIN_VALUE;
		return (int) value;
	}

}
