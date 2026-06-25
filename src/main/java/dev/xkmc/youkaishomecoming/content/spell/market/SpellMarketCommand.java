package dev.xkmc.youkaishomecoming.content.spell.market;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SpellMarketCommand {

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
				Commands.literal("yhspell")
						.then(Commands.literal("market")
								.executes(SpellMarketCommand::openMarket)
								.then(Commands.literal("upload")
										.then(Commands.argument("spell_id", StringArgumentType.string())
												.executes(SpellMarketCommand::openUpload)))
		);
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
			ctx.getSource().sendFailure(Component.literal("Invalid spell ID: " + spellId));
			return 0;
		}
		SpellDefinition def = SpellRegistry.get(id);
		if (def == null) {
			ctx.getSource().sendFailure(Component.literal("Spell not found: " + spellId));
			return 0;
		}
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.setScreen(new SpellUploadDialog(mc.screen, def)));
		return 1;
	}

}
