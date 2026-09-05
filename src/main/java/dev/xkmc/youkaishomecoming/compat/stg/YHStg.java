package dev.xkmc.youkaishomecoming.compat.stg;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
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

	public static double getLife(Object player) {
		return YHStgApi.getLife(requirePlayer(player));
	}

	public static double getBomb(Object player) {
		return YHStgApi.getBomb(requirePlayer(player));
	}

	public static double getPower(Object player) {
		return YHStgApi.getPower(requirePlayer(player));
	}

	public static double getPoints(Object player) {
		return YHStgApi.getPoints(requirePlayer(player));
	}

	public static int getLifeRaw(Object player) {
		return YHStgApi.getLifeRaw(requirePlayer(player));
	}

	public static int getBombRaw(Object player) {
		return YHStgApi.getBombRaw(requirePlayer(player));
	}

	public static int getPowerRaw(Object player) {
		return YHStgApi.getPowerRaw(requirePlayer(player));
	}

	public static int getPointsRaw(Object player) {
		return YHStgApi.getPointsRaw(requirePlayer(player));
	}

	public static void setLife(Object player, double amount) {
		YHStgApi.setLife(requirePlayer(player), amount);
	}

	public static void setBomb(Object player, double amount) {
		YHStgApi.setBomb(requirePlayer(player), amount);
	}

	public static void setPower(Object player, double amount) {
		YHStgApi.setPower(requirePlayer(player), amount);
	}

	public static void setPoints(Object player, double amount) {
		YHStgApi.setPoints(requirePlayer(player), amount);
	}

	public static void setLifeRaw(Object player, int amount) {
		YHStgApi.setLifeRaw(requirePlayer(player), amount);
	}

	public static void setBombRaw(Object player, int amount) {
		YHStgApi.setBombRaw(requirePlayer(player), amount);
	}

	public static void setPowerRaw(Object player, int amount) {
		YHStgApi.setPowerRaw(requirePlayer(player), amount);
	}

	public static void setPointsRaw(Object player, int amount) {
		YHStgApi.setPointsRaw(requirePlayer(player), amount);
	}

	public static void addLife(Object player, double amount) {
		YHStgApi.addLife(requirePlayer(player), amount);
	}

	public static void addBomb(Object player, double amount) {
		YHStgApi.addBomb(requirePlayer(player), amount);
	}

	public static void addPower(Object player, double amount) {
		YHStgApi.addPower(requirePlayer(player), amount);
	}

	public static void addPoints(Object player, double amount) {
		YHStgApi.addPoints(requirePlayer(player), amount);
	}

	public static void addLifeRaw(Object player, int amount) {
		YHStgApi.addLifeRaw(requirePlayer(player), amount);
	}

	public static void addBombRaw(Object player, int amount) {
		YHStgApi.addBombRaw(requirePlayer(player), amount);
	}

	public static void addPowerRaw(Object player, int amount) {
		YHStgApi.addPowerRaw(requirePlayer(player), amount);
	}

	public static void addPointsRaw(Object player, int amount) {
		YHStgApi.addPointsRaw(requirePlayer(player), amount);
	}

	public static boolean tryManualBomb(Object player) {
		return YHStgApi.tryManualBomb(requirePlayer(player));
	}

	public static boolean castSpell(Object player) {
		return YHStgApi.castSpell(requirePlayer(player));
	}

	public static boolean castSpell(Object player, Object stack) {
		return YHStgApi.castSpell(requirePlayer(player), requireItemStack(stack));
	}

	public static int eraseActiveDanmaku(Object player, double radius, boolean sessionsOnly) {
		return YHStgApi.eraseActiveDanmaku(requirePlayer(player), radius, sessionsOnly);
	}

	public static boolean isInDanmakuSession(Object player) {
		return YHStgApi.isInDanmakuSession(requirePlayer(player));
	}

	public static boolean isForcedDanmakuCombat(Object player) {
		return YHStgApi.isForcedDanmakuCombat(requirePlayer(player));
	}

	public static void setDanmakuCombat(Object player, boolean enabled) {
		YHStgApi.setDanmakuCombat(requirePlayer(player), enabled);
	}

	public static boolean shouldShowPower(Object player) {
		return YHStgApi.shouldShowPower(requireAnyPlayer(player));
	}

	public static boolean shouldShowPowerForStack(Object stack) {
		return YHStgApi.shouldShowPowerForStack(requireItemStack(stack));
	}

	private static ServerPlayer requirePlayer(Object value) {
		ServerPlayer player = resolvePlayer(value, 0);
		if (player == null) {
			throw new IllegalArgumentException("Expected a server player for YHStg API, got " +
					(value == null ? "null" : value.getClass().getName()));
		}
		return player;
	}

	private static Player requireAnyPlayer(Object value) {
		Player player = resolveAnyPlayer(value, 0);
		if (player == null) {
			throw new IllegalArgumentException("Expected a player for YHStg API, got " +
					(value == null ? "null" : value.getClass().getName()));
		}
		return player;
	}

	private static ItemStack requireItemStack(Object value) {
		ItemStack stack = resolveItemStack(value, 0);
		if (stack == null) {
			throw new IllegalArgumentException("Expected an item stack for YHStg API, got " +
					(value == null ? "null" : value.getClass().getName()));
		}
		return stack;
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
	private static Player resolveAnyPlayer(Object value, int depth) {
		if (value == null || depth > 4) return null;
		if (value instanceof Player player) return player;
		for (String name : PLAYER_METHODS) {
			Player player = invokeAnyPlayerMethod(value, name, depth);
			if (player != null) return player;
		}
		for (String name : PLAYER_FIELDS) {
			Player player = readAnyPlayerField(value, name, depth);
			if (player != null) return player;
		}
		return null;
	}

	@Nullable
	private static ItemStack resolveItemStack(Object value, int depth) {
		if (value == null || depth > 4) return null;
		if (value instanceof ItemStack stack) return stack;
		if (value instanceof ItemLike item) return new ItemStack(item);
		if (value instanceof ResourceLocation id) {
			return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(null);
		}
		if (value instanceof CharSequence text) {
			ResourceLocation id = ResourceLocation.tryParse(text.toString());
			if (id == null) return null;
			return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(null);
		}
		for (String name : new String[]{"getItemStack", "itemStack", "getStack", "stack", "getItem", "item", "getId", "id"}) {
			ItemStack stack = invokeItemStackMethod(value, name, depth);
			if (stack != null) return stack;
		}
		for (String name : new String[]{"itemStack", "stack", "item", "id"}) {
			ItemStack stack = readItemStackField(value, name, depth);
			if (stack != null) return stack;
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
	private static Player invokeAnyPlayerMethod(Object value, String name, int depth) {
		try {
			Method method = value.getClass().getMethod(name);
			if (method.getParameterCount() != 0) return null;
			return resolveAnyPlayer(method.invoke(value), depth + 1);
		} catch (ReflectiveOperationException | SecurityException ignored) {
			return null;
		}
	}

	@Nullable
	private static ItemStack invokeItemStackMethod(Object value, String name, int depth) {
		try {
			Method method = value.getClass().getMethod(name);
			if (method.getParameterCount() != 0) return null;
			return resolveItemStack(method.invoke(value), depth + 1);
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

	@Nullable
	private static Player readAnyPlayerField(Object value, String name, int depth) {
		try {
			Field field = value.getClass().getField(name);
			return resolveAnyPlayer(field.get(value), depth + 1);
		} catch (ReflectiveOperationException | SecurityException ignored) {
			return null;
		}
	}

	@Nullable
	private static ItemStack readItemStackField(Object value, String name, int depth) {
		try {
			Field field = value.getClass().getField(name);
			return resolveItemStack(field.get(value), depth + 1);
		} catch (ReflectiveOperationException | SecurityException ignored) {
			return null;
		}
	}

}
