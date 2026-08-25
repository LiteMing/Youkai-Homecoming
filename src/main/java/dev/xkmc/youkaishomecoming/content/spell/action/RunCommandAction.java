package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.util.SpellTextResolver;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public record RunCommandAction(Mode mode, HitContext hitContext, String command) implements SpellAction {

	public static final Codec<RunCommandAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Mode.CODEC.optionalFieldOf("mode", Mode.AS_CASTER).forGetter(RunCommandAction::mode),
			HitContext.CODEC.optionalFieldOf("hit_context", HitContext.DEFAULT).forGetter(RunCommandAction::hitContext),
			Codec.STRING.fieldOf("command").forGetter(RunCommandAction::command)
	).apply(i, RunCommandAction::new));

	public RunCommandAction(Mode mode, String command) {
		this(mode, HitContext.DEFAULT, command);
	}

	@Override
	public void execute(SpellContext ctx) {
		LivingEntity caster = ctx.self();
		if (caster.level().isClientSide()) {
			return;
		}
		var server = caster.getServer();
		if (server == null || command == null || command.isBlank()) {
			return;
		}
		CommandSourceStack source = switch (mode) {
			case AS_CASTER -> caster.createCommandSourceStack()
					.withPermission(2)
					.withSuppressedOutput();
			case CONSOLE -> server.createCommandSourceStack()
					.withSuppressedOutput();
			case NON_CHEAT -> caster.createCommandSourceStack()
					.withSuppressedOutput();
		};
String cmd = SpellTextResolver.resolve(command, ctx);
		if (cmd == null || cmd.isBlank()) {
			return;
		}
		source = applyHitContext(source, ctx);
		if (source == null) {
			return;
		}
		server.getCommands().performPrefixedCommand(source, stripLeadingSlash(cmd));
	}

	@Nullable
	private CommandSourceStack applyHitContext(CommandSourceStack source, SpellContext ctx) {
		if (hitContext == HitContext.DEFAULT) {
			return source;
		}
		if (!(ctx.holder() instanceof TrailCardHolder trail)) {
			return null;
		}
		Entity hitEntity = trail.hitEntity();
		return switch (hitContext) {
			case DEFAULT -> source;
			case AS_HIT_ENTITY -> trail.hitType() == TrailCardHolder.HitType.ENTITY && hitEntity != null
					? source.withEntity(hitEntity) : null;
			case AT_ENTITY_POS -> trail.hitType() == TrailCardHolder.HitType.ENTITY && hitEntity != null
					? source.withPosition(hitEntity.position()) : null;
			case AT_BLOCK_POS -> trail.hitType() == TrailCardHolder.HitType.BLOCK
					? source.withPosition(trail.center()) : null;
		};
	}

	private static String stripLeadingSlash(String command) {
		String trimmed = command.trim();
		return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
	}

	public enum Mode implements StringRepresentable {
		AS_CASTER("as_caster"),
		CONSOLE("console"),
		NON_CHEAT("non_cheat");

		public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

		private final String serializedName;

		Mode(String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return serializedName;
		}
	}

	public enum HitContext implements StringRepresentable {
		DEFAULT("default"),
		AS_HIT_ENTITY("as_hit_entity"),
		AT_ENTITY_POS("at_entity_pos"),
		AT_BLOCK_POS("at_block_pos");

		public static final Codec<HitContext> CODEC = StringRepresentable.fromEnum(HitContext::values);

		private final String serializedName;

		HitContext(String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return serializedName;
		}
	}
}
