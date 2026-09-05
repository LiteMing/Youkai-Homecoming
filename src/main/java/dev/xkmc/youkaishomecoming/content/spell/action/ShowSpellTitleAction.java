package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.util.SpellTextResolver;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record ShowSpellTitleAction(String name, String description, int duration, double radius) implements SpellAction {

	/**
	 * The title is owned by {@code SpellDefinition.display.name}. Keep this
	 * legacy component for source/JSON compatibility, but do not serialize or
	 * use it as a second title source.
	 */
	public static final Codec<ShowSpellTitleAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("description", "").forGetter(ShowSpellTitleAction::description),
			Codec.INT.optionalFieldOf("duration", 100).forGetter(ShowSpellTitleAction::duration),
			Codec.DOUBLE.optionalFieldOf("radius", 64.0).forGetter(ShowSpellTitleAction::radius)
	).apply(i, (description, duration, radius) -> new ShowSpellTitleAction("", description, duration, radius)));

	public ShowSpellTitleAction {
		// Older definitions may still provide a custom name. Normalize it away so
		// decoded and newly-created actions share the same single title source.
		name = "";
	}

	@Override
	public void execute(SpellContext ctx) {
		var self = ctx.self();
		if (!(self.level() instanceof ServerLevel level)) {
			return;
		}
		String title = ctx.definition().display.name();
		String desc = description == null || description.isBlank() ? ctx.definition().display.description() : SpellTextResolver.resolve(description, ctx);
		var packet = new SpellTitleToClient(title, desc, Math.max(20, duration));
		Set<UUID> sent = new HashSet<>();
		double maxDist = Math.max(0, radius);
		double maxDistSqr = maxDist * maxDist;
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(self) <= maxDistSqr) {
				send(packet, player, sent);
			}
		}
		if (ctx.holder().targetEntity() instanceof ServerPlayer target) {
			send(packet, target, sent);
		}
	}

	private static void send(SpellTitleToClient packet, ServerPlayer player, Set<UUID> sent) {
		if (sent.add(player.getUUID())) {
			YoukaisHomecoming.HANDLER.toClientPlayer(packet, player);
		}
	}
}
