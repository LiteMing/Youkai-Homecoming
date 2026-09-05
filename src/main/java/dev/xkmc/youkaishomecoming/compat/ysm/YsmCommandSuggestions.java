package dev.xkmc.youkaishomecoming.compat.ysm;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

/** Named providers survive Forge's server/client command-tree merge. No client or model-provider types here. */
public final class YsmCommandSuggestions {
	public enum Kind { TARGETS, CLIP, PARAMETER, VALUE, MODEL, PRESET }
	@FunctionalInterface
	public interface ClientCompleter {
		CompletableFuture<Suggestions> suggest(Kind kind, CommandContext<SharedSuggestionProvider> context,
				SuggestionsBuilder builder) throws CommandSyntaxException;
	}
	private static ClientCompleter client;
	public static final SuggestionProvider<CommandSourceStack> TARGETS = register(Kind.TARGETS);
	public static final SuggestionProvider<CommandSourceStack> CLIPS = register(Kind.CLIP);
	public static final SuggestionProvider<CommandSourceStack> PARAMETERS = register(Kind.PARAMETER);
	public static final SuggestionProvider<CommandSourceStack> VALUES = register(Kind.VALUE);
	public static final SuggestionProvider<CommandSourceStack> MODELS = register(Kind.MODEL);
	public static final SuggestionProvider<CommandSourceStack> PRESETS = register(Kind.PRESET);

	private YsmCommandSuggestions() { }
	public static void init() { }
	static void client(ClientCompleter completer) { client = completer; }

	private static SuggestionProvider<CommandSourceStack> register(Kind kind) {
		return SuggestionProviders.register(new ResourceLocation(YoukaisHomecoming.MODID, "model_" + kind.name().toLowerCase(java.util.Locale.ROOT)),
				(context, builder) -> {
					if (!(context.getSource() instanceof CommandSourceStack source))
						return client == null ? Suggestions.empty() : client.suggest(kind, context, builder);
					if (kind == Kind.TARGETS) return EntityArgument.entities().listSuggestions(context, builder);
					// A dedicated server can suggest shared definitions, but cannot inspect client assets.
					if (source.getServer() == null) return Suggestions.empty();
					var profiles = YsmProfileData.get(source.getServer()).entries();
					String model = argument(context, "model"), parameter = argument(context, "parameter");
					var values = new TreeSet<String>();
					profiles.forEach((id, entry) -> {
						if (kind == Kind.MODEL) values.add(id);
						if (!model.isEmpty() && !model.equals(id)) return;
						entry.profile().presets().forEach((name, preset) -> {
							if (kind == Kind.PRESET) values.add(name);
							if (kind == Kind.CLIP && !preset.clip().isEmpty()) values.add(preset.clip());
							if (kind == Kind.PARAMETER) values.addAll(preset.parameters().keySet());
							if (kind == Kind.VALUE && preset.parameters().containsKey(parameter)) values.add(Float.toString(preset.parameters().get(parameter)));
						});
					});
					return strings(values, builder);
				});
	}

	static String argument(CommandContext<?> context, String name) {
		try { return context.getArgument(name, String.class); }
		catch (IllegalArgumentException ignored) { return ""; }
	}

	static CompletableFuture<Suggestions> strings(Collection<String> values, SuggestionsBuilder builder) {
		return SharedSuggestionProvider.suggest(values.stream().map(StringArgumentType::escapeIfRequired), builder);
	}
}
