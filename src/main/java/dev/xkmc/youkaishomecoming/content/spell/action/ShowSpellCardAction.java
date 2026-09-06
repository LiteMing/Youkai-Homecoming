package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.LivingCardHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Shows the current spell as a floating DynamicSpellItem card in front of the caster. */
public record ShowSpellCardAction(int duration, double radius, Hand hand,
		double offsetRight, double offsetUp, double offsetForward,
		int holdTicks, int throwTicks, int floatTicks) implements SpellAction {

	private static final AtomicLong SEQUENCE = new AtomicLong();
	private static final int BASE_DURATION = 40;
	private static final int DEFAULT_HOLD_TICKS = 10;
	private static final int DEFAULT_THROW_TICKS = 8;
	private static final int DEFAULT_FLOAT_TICKS = 22;

	public static final Codec<ShowSpellCardAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("duration", -1).forGetter(ShowSpellCardAction::duration),
			Codec.DOUBLE.optionalFieldOf("radius", 64.0).forGetter(ShowSpellCardAction::radius),
			Hand.CODEC.optionalFieldOf("hand", Hand.RANDOM).forGetter(ShowSpellCardAction::hand),
			Codec.DOUBLE.optionalFieldOf("offset_right", 0.0).forGetter(ShowSpellCardAction::offsetRight),
			Codec.DOUBLE.optionalFieldOf("offset_up", 0.0).forGetter(ShowSpellCardAction::offsetUp),
			Codec.DOUBLE.optionalFieldOf("offset_forward", 0.0).forGetter(ShowSpellCardAction::offsetForward),
			Codec.INT.optionalFieldOf("hold_ticks", -1).forGetter(ShowSpellCardAction::holdTicks),
			Codec.INT.optionalFieldOf("throw_ticks", -1).forGetter(ShowSpellCardAction::throwTicks),
			Codec.INT.optionalFieldOf("float_ticks", -1).forGetter(ShowSpellCardAction::floatTicks)
	).apply(i, ShowSpellCardAction::new));

	public ShowSpellCardAction(int duration, double radius) {
		this(duration, radius, Hand.RANDOM, 0, 0, 0, -1, -1, -1);
	}

	public static ShowSpellCardAction defaults() {
		return new ShowSpellCardAction(-1, 64.0, Hand.RANDOM, 0, 0, 0,
				DEFAULT_HOLD_TICKS, DEFAULT_THROW_TICKS, DEFAULT_FLOAT_TICKS);
	}

	@Override
	public void execute(SpellContext ctx) {
		if (!(ctx.self().level() instanceof ServerLevel level)) return;
		ResourceLocation spellId = ctx.definition().id;
		if (spellId == null) return;
		LivingEntity presenter = presentationEntity(ctx);
		Timeline timeline = timeline();
		boolean rightHand = switch (hand) {
			case RANDOM -> presenter.getRandom().nextBoolean();
			case LEFT -> false;
			case RIGHT -> true;
		};
		var packet = new SpellCardPresentationToClient(presenter.getId(), spellId.toString(),
				level.getGameTime(), timeline.holdTicks(), timeline.throwTicks(), timeline.floatTicks(),
				rightHand, offsetRight, offsetUp, offsetForward, SEQUENCE.incrementAndGet());
		Set<UUID> sent = new HashSet<>();
		double maxDistance = Math.max(0, radius);
		double maxDistanceSqr = maxDistance * maxDistance;
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(presenter) <= maxDistanceSqr) send(packet, player, sent);
		}
		if (ctx.holder().targetEntity() instanceof ServerPlayer target && target.level() == level)
			send(packet, target, sent);
	}

	/** Clears an outstanding card immediately when a higher-priority result takes over. */
	public static void clear(LivingEntity entity) {
		if (!(entity.level() instanceof ServerLevel level)) return;
		var packet = SpellCardPresentationToClient.clear(entity.getId(), level.getGameTime(), SEQUENCE.incrementAndGet());
		for (ServerPlayer player : level.players())
			YoukaisHomecoming.HANDLER.toClientPlayer(packet, player);
	}

	public Timeline timeline() {
		if (holdTicks >= 0 || throwTicks >= 0 || floatTicks >= 0) {
			int hold = holdTicks < 0 ? DEFAULT_HOLD_TICKS : Math.max(0, holdTicks);
			int throwing = throwTicks < 0 ? DEFAULT_THROW_TICKS : Math.max(0, throwTicks);
			int floating = floatTicks < 0 ? DEFAULT_FLOAT_TICKS : Math.max(0, floatTicks);
			return ensureVisible(hold, throwing, floating);
		}
		int total = Math.max(1, duration < 0 ? YHModConfig.COMMON.spellCardPresentationTicks.get() : duration);
		int hold = Math.round(total * (float) DEFAULT_HOLD_TICKS / BASE_DURATION);
		int throwing = Math.round(total * (float) DEFAULT_THROW_TICKS / BASE_DURATION);
		return ensureVisible(hold, throwing, Math.max(0, total - hold - throwing));
	}

	private static Timeline ensureVisible(int hold, int throwing, int floating) {
		return hold + throwing + floating > 0 ? new Timeline(hold, throwing, floating) : new Timeline(0, 0, 1);
	}

	private static LivingEntity presentationEntity(SpellContext ctx) {
		if (ctx.host() != null) return ctx.host().spellCircleDisplayEntity();
		if (ctx.holder() instanceof LivingCardHolder holder) return holder.shooter();
		return ctx.self();
	}

	public ShowSpellCardAction withHand(Hand value) {
		return new ShowSpellCardAction(duration, radius, value, offsetRight, offsetUp, offsetForward,
				holdTicks, throwTicks, floatTicks);
	}

	public ShowSpellCardAction withRadius(double value) {
		return new ShowSpellCardAction(duration, value, hand, offsetRight, offsetUp, offsetForward,
				holdTicks, throwTicks, floatTicks);
	}

	public ShowSpellCardAction withOffsets(double right, double up, double forward) {
		return new ShowSpellCardAction(duration, radius, hand, right, up, forward,
				holdTicks, throwTicks, floatTicks);
	}

	public ShowSpellCardAction withTimeline(Integer hold, Integer throwing, Integer floating) {
		Timeline current = timeline();
		return new ShowSpellCardAction(-1, radius, hand, offsetRight, offsetUp, offsetForward,
				hold == null ? current.holdTicks() : Math.max(0, hold),
				throwing == null ? current.throwTicks() : Math.max(0, throwing),
				floating == null ? current.floatTicks() : Math.max(0, floating));
	}

	private static void send(SpellCardPresentationToClient packet, ServerPlayer player, Set<UUID> sent) {
		if (sent.add(player.getUUID())) YoukaisHomecoming.HANDLER.toClientPlayer(packet, player);
	}

	public record Timeline(int holdTicks, int throwTicks, int floatTicks) {
		public int totalTicks() {
			return holdTicks + throwTicks + floatTicks;
		}
	}

	public enum Hand implements StringRepresentable {
		RANDOM("random"), LEFT("left"), RIGHT("right");

		public static final Codec<Hand> CODEC = StringRepresentable.fromEnum(Hand::values);
		private final String name;

		Hand(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}
}
