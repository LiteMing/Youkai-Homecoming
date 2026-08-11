package dev.xkmc.youkaishomecoming.content.spell;

import net.minecraft.ChatFormatting;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Team;

public final class SpellProgressColor {

	private SpellProgressColor() {
	}

	public static int outlineRgb(Entity entity, int fallback) {
		Team team = entity.getTeam();
		Integer color = team == null ? null : team.getColor().getColor();
		return color == null ? fallback : color & 0xFFFFFF;
	}

	public static BossEvent.BossBarColor bossBarColor(Entity entity, BossEvent.BossBarColor fallback) {
		Team team = entity.getTeam();
		if (team == null) return fallback;
		ChatFormatting color = team.getColor();
		return switch (color) {
			case DARK_BLUE, BLUE, DARK_AQUA, AQUA -> BossEvent.BossBarColor.BLUE;
			case DARK_GREEN, GREEN -> BossEvent.BossBarColor.GREEN;
			case DARK_RED, RED -> BossEvent.BossBarColor.RED;
			case DARK_PURPLE, LIGHT_PURPLE -> BossEvent.BossBarColor.PURPLE;
			case GOLD, YELLOW -> BossEvent.BossBarColor.YELLOW;
			case BLACK, DARK_GRAY, GRAY, WHITE -> BossEvent.BossBarColor.WHITE;
			default -> fallback;
		};
	}
}
