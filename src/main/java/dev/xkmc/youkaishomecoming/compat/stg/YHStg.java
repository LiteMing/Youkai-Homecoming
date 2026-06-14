package dev.xkmc.youkaishomecoming.compat.stg;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class YHStg {

	private static final String[] PLAYER_METHODS = {
			"getMinecraftPlayer", "minecraftPlayer", "getPlayer", "player", "getEntity", "entity"
	};
	private static final String[] PLAYER_FIELDS = {
			"minecraftPlayer", "player", "entity"
	};

	private YHStg() {
	}

	public static String getMode(Object player) {
		return YHStgApi.getMode(requirePlayer(player)).commandName();
	}

	public static void setMode(Object player, String mode) {
		YHStgApi.setMode(requirePlayer(player), StgCombatMode.fromName(mode));
	}

	public static int getLife(Object player) {
		return YHStgApi.getLife(requirePlayer(player));
	}

	public static int getBomb(Object player) {
		return YHStgApi.getBomb(requirePlayer(player));
	}

	public static int getPower(Object player) {
		return YHStgApi.getPower(requirePlayer(player));
	}

	public static int getPoints(Object player) {
		return YHStgApi.getPoints(requirePlayer(player));
	}

	public static void setLife(Object player, int amount) {
		YHStgApi.setLife(requirePlayer(player), amount);
	}

	public static void setBomb(Object player, int amount) {
		YHStgApi.setBomb(requirePlayer(player), amount);
	}

	public static void setPower(Object player, int amount) {
		YHStgApi.setPower(requirePlayer(player), amount);
	}

	public static void setPoints(Object player, int amount) {
		YHStgApi.setPoints(requirePlayer(player), amount);
	}

	public static void addLife(Object player, int amount) {
		YHStgApi.addLife(requirePlayer(player), amount);
	}

	public static void addBomb(Object player, int amount) {
		YHStgApi.addBomb(requirePlayer(player), amount);
	}

	public static void addPower(Object player, int amount) {
		YHStgApi.addPower(requirePlayer(player), amount);
	}

	public static void addPoints(Object player, int amount) {
		YHStgApi.addPoints(requirePlayer(player), amount);
	}

	public static boolean tryManualBomb(Object player) {
		return YHStgApi.tryManualBomb(requirePlayer(player));
	}

	public static int eraseActiveDanmaku(Object player, double radius, boolean sessionsOnly) {
		return YHStgApi.eraseActiveDanmaku(requirePlayer(player), radius, sessionsOnly);
	}

	public static boolean isInDanmakuSession(Object player) {
		return YHStgApi.isInDanmakuSession(requirePlayer(player));
	}

	private static ServerPlayer requirePlayer(Object value) {
		ServerPlayer player = resolvePlayer(value, 0);
		if (player == null) {
			throw new IllegalArgumentException("Expected a server player for YHStg API, got " +
					(value == null ? "null" : value.getClass().getName()));
		}
		return player;
	}

	@Nullable
	private static ServerPlayer resolvePlayer(Object value, int depth) {
		if (value == null || depth > 4) return null;
		if (value instanceof ServerPlayer player) return player;
		if (value instanceof Entity entity && entity.level().isClientSide()) return null;
		for (String name : PLAYER_METHODS) {
			ServerPlayer player = invokePlayerMethod(value, name, depth);
			if (player != null) return player;
		}
		for (String name : PLAYER_FIELDS) {
			ServerPlayer player = readPlayerField(value, name, depth);
			if (player != null) return player;
		}
		return null;
	}

	@Nullable
	private static ServerPlayer invokePlayerMethod(Object value, String name, int depth) {
		try {
			Method method = value.getClass().getMethod(name);
			if (method.getParameterCount() != 0) return null;
			return resolvePlayer(method.invoke(value), depth + 1);
		} catch (ReflectiveOperationException | SecurityException ignored) {
			return null;
		}
	}

	@Nullable
	private static ServerPlayer readPlayerField(Object value, String name, int depth) {
		try {
			Field field = value.getClass().getField(name);
			return resolvePlayer(field.get(value), depth + 1);
		} catch (ReflectiveOperationException | SecurityException ignored) {
			return null;
		}
	}

}
