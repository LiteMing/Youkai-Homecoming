package dev.xkmc.youkaishomecoming.content.spell.market;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SpellMarketCommand {

	private static final SuggestionProvider<CommandSourceStack> SPELL_SUGGESTIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggestResource(SpellRegistry.getAll().keySet(), builder);

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(root("yhmarket"));
		dispatcher.register(root("spellmarket"));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
		return Commands.literal(name)
				.executes(SpellMarketCommand::openMarket)
				.then(Commands.literal("upload")
						.then(Commands.argument("spell_id", StringArgumentType.string())
								.suggests(SPELL_SUGGESTIONS)
								.executes(SpellMarketCommand::openUpload)))
				.then(Commands.literal("test")
						.executes(SpellMarketCommand::testConnection))
				.then(Commands.literal("reload")
						.executes(SpellMarketCommand::reloadConfig));
	}

	private static int openMarket(CommandContext<CommandSourceStack> ctx) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.setScreen(new SpellMarketScreen(mc.screen)));
		return 1;
	}

	private static int openUpload(CommandContext<CommandSourceStack> ctx) {
		String spellId = StringArgumentType.getString(ctx, "spell_id");
		ResourceLocation id = ResourceLocation.tryParse(spellId);
		if (id == null) {
			ctx.getSource().sendFailure(SpellMarketLocalization.commandInvalidSpell(spellId));
			return 0;
		}
		SpellDefinition def = SpellRegistry.get(id);
		if (def == null) {
			ctx.getSource().sendFailure(SpellMarketLocalization.commandSpellNotFound(spellId));
			return 0;
		}
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.setScreen(new SpellUploadDialog(mc.screen, def)));
		return 1;
	}

	private static int testConnection(CommandContext<CommandSourceStack> ctx) {
		SpellMarketManager manager = SpellMarketManager.getInstance();
		if (!manager.isEnabled() || manager.getAPI() == null) {
			ctx.getSource().sendFailure(SpellMarketLocalization.commandDisabled());
			return 0;
		}
		SpellMarketAPI api = manager.getAPI();
		ctx.getSource().sendSuccess(SpellMarketLocalization::commandTesting, false);
		api.getSpellList(1, 1, null, null).thenAccept(response -> {
			Minecraft mc = Minecraft.getInstance();
			mc.execute(() -> {
				if (mc.player == null) return;
				if (response == null) {
					mc.player.displayClientMessage(SpellMarketLocalization.commandConnectionFailed(), false);
				} else {
					mc.player.displayClientMessage(SpellMarketLocalization.commandConnected(response.total), false);
				}
			});
		});
		return 1;
	}

	private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
		SpellMarketManager.getInstance().reload();
		boolean enabled = SpellMarketManager.getInstance().isEnabled();
		if (enabled) {
			ctx.getSource().sendSuccess(SpellMarketLocalization::commandReloadEnabled, false);
		} else {
			ctx.getSource().sendSuccess(SpellMarketLocalization::commandReloadDisabled, false);
		}
		return 1;
	}

}
