package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;

public record RunCommandAction(Mode mode, String command) implements SpellAction {

	public static final Codec<RunCommandAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Mode.CODEC.optionalFieldOf("mode", Mode.AS_CASTER).forGetter(RunCommandAction::mode),
			Codec.STRING.fieldOf("command").forGetter(RunCommandAction::command)
	).apply(i, RunCommandAction::new));

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
		server.getCommands().performPrefixedCommand(source, stripLeadingSlash(command));
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
}
