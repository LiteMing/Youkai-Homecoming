package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID)
public class SpellPreviewClientHandler {

	private static final Map<Integer, Assembly> ASSEMBLIES = new ConcurrentHashMap<>();
	private static final SpellAiGenerateResultToClient.Collector AI_RESULTS = new SpellAiGenerateResultToClient.Collector();
	private static final Map<Integer, String> AI_ORIGINALS = new java.util.HashMap<>();
	private static final Map<Integer, SpellAiComparison> AI_COMPARISONS = new java.util.HashMap<>();
	private static Integer pendingComparison;

	public static void open(OpenSpellPreviewToClient packet) {
		Minecraft.getInstance().execute(() -> openOnClient(packet));
	}

	public static void onChunk(SpellPreviewChunkToClient packet) {
		Minecraft.getInstance().execute(() -> handleChunk(packet));
	}

	public static void onAiResult(SpellAiGenerateResultToClient packet) {
		Minecraft.getInstance().execute(() -> {
			var mc = Minecraft.getInstance();
			if (mc.player == null) return;
			var result = AI_RESULTS.accept(packet);
			if (result == null) return;
			var comparison = completeAiRequest(result);
			deliverAiResult(result, mc.keyboardHandler::setClipboard, message -> {
				if (comparison != null) message = message.copy().append(" ").append(Component.translatable(
						"youkaishomecoming.spell_editor.ai.view_changes").withStyle(style -> style.withColor(ChatFormatting.AQUA)
						.withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/yhspellai diff " + result.transferId))));
				mc.player.displayClientMessage(message, false);
			});
		});
	}

	static void rememberAiRequest(int transferId, String operation, String originalJson) {
		if ("modify".equals(operation)) AI_ORIGINALS.put(transferId, originalJson);
	}

	static SpellAiComparison completeAiRequest(SpellAiGenerateResultToClient result) {
		String original = AI_ORIGINALS.remove(result.transferId);
		if (!result.success || original == null) return null;
		var comparison = new SpellAiComparison(original, result.json);
		AI_COMPARISONS.put(result.transferId, comparison);
		return comparison;
	}

	@SubscribeEvent
	public static void registerAiCommands(RegisterClientCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("yhspellai").then(Commands.literal("diff")
				.then(Commands.argument("request", IntegerArgumentType.integer()).executes(context -> {
					pendingComparison = IntegerArgumentType.getInteger(context, "request");
					return 1;
				}))));
	}

	@SubscribeEvent
	public static void openAiComparison(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END || pendingComparison == null) return;
		var comparison = AI_COMPARISONS.get(pendingComparison);
		pendingComparison = null;
		var mc = Minecraft.getInstance();
		if (mc.player == null) return;
		if (comparison != null) mc.setScreen(new SpellAiDiffScreen(mc.screen, comparison));
		else mc.player.displayClientMessage(Component.translatable("youkaishomecoming.spell_editor.ai.diff_unavailable"), false);
	}

	static void deliverAiResult(SpellAiGenerateResultToClient result, Consumer<String> clipboard, Consumer<Component> chat) {
		if (result.success) {
			clipboard.accept(result.json);
			chat.accept(result.message.isBlank()
					? Component.translatable("youkaishomecoming.spell_editor.message.ai_copied")
					: Component.translatable("youkaishomecoming.spell_editor.message.ai_needs_repair")
							.append(" ").append(Component.translatable("youkaishomecoming.spell_editor.ai.repair_feedback")
									.withStyle(style -> style.withColor(ChatFormatting.YELLOW).withUnderlined(true)
											.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(result.message))))));
		} else {
			chat.accept(result.message.isBlank()
					? Component.translatable("youkaishomecoming.spell_editor.message.ai_failed_generic")
					: Component.translatable("youkaishomecoming.spell_editor.message.ai_failed", result.message));
		}
	}

	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		AI_RESULTS.clear();
		AI_ORIGINALS.clear();
		AI_COMPARISONS.clear();
		pendingComparison = null;
		ASSEMBLIES.clear();
	}

	private static void openOnClient(OpenSpellPreviewToClient packet) {
		Minecraft mc = Minecraft.getInstance();
		if (packet.draft) {
			mc.setScreen(SpellPreviewScreen.createDraftEditor());
			return;
		}
		// Legacy single-packet path (definitionJson) if still present
		if (packet.definitionJson != null && !packet.definitionJson.isBlank()) {
			SpellDefinition def = parseJson(packet.spellId, packet.definitionJson);
			if (def != null) {
				openDefinition(def);
				return;
			}
		}
		// Id-only open (used for legacy_ticker spells — factory cannot survive JSON)
		if (packet.spellId != null && !packet.spellId.isBlank()) {
			ResourceLocation id = ResourceLocation.tryParse(packet.spellId);
			if (id != null) {
				SpellDefinition local = SpellRegistry.get(id);
				if (local != null) {
					openLocalDefinition(local);
					return;
				}
			}
		}
		if (mc.player != null) {
			mc.player.displayClientMessage(Component.translatable(
					"youkaishomecoming.spell_editor.message.unknown_spell", packet.spellId), false);
		}
	}

	private static void handleChunk(SpellPreviewChunkToClient packet) {
		if (packet.totalChunks <= 0 || packet.chunkIndex < 0 || packet.chunkIndex >= packet.totalChunks) {
			YoukaisHomecoming.LOGGER.warn("[SpellPreview] bad chunk meta id={} idx={}/{}",
					packet.spellId, packet.chunkIndex, packet.totalChunks);
			return;
		}
		Assembly ass = ASSEMBLIES.computeIfAbsent(packet.transferId,
				id -> new Assembly(packet.spellId, packet.totalChunks));
		if (ass.total != packet.totalChunks || !ass.spellId.equals(packet.spellId)) {
			// Stale / overlapping transfer — replace
			ass = new Assembly(packet.spellId, packet.totalChunks);
			ASSEMBLIES.put(packet.transferId, ass);
		}
		ass.parts[packet.chunkIndex] = packet.chunk != null ? packet.chunk : "";
		ass.received++;
		if (ass.received < ass.total) return;

		ASSEMBLIES.remove(packet.transferId);
		StringBuilder sb = new StringBuilder(ass.total * SpellPreviewChunkToClient.MAX_CHUNK_CHARS);
		for (int i = 0; i < ass.total; i++) {
			if (ass.parts[i] == null) {
				YoukaisHomecoming.LOGGER.warn("[SpellPreview] missing chunk {}/{} for {}",
						i, ass.total, ass.spellId);
				return;
			}
			sb.append(ass.parts[i]);
		}
		SpellDefinition def = parseJson(ass.spellId, sb.toString());
		if (def == null) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) {
				mc.player.displayClientMessage(Component.translatable(
						"youkaishomecoming.spell_editor.message.decode_failed", ass.spellId), false);
			}
			return;
		}
		openDefinition(def);
	}

	private static void openDefinition(SpellDefinition definition) {
		// Do not overwrite a live legacy_ticker definition with a decoded empty shell
		if (definition.hasLegacyTicker()) {
			SpellDefinition local = SpellRegistry.get(definition.id);
			if (local != null && local.hasLegacyTicker()) {
				openLocalDefinition(local);
				return;
			}
		}
		SpellRegistry.register(definition);
		Minecraft.getInstance().setScreen(new SpellPreviewScreen(definition));
	}

	/** Open preview without re-registering (keeps non-serializable factory intact). */
	private static void openLocalDefinition(SpellDefinition definition) {
		Minecraft.getInstance().setScreen(new SpellPreviewScreen(definition));
	}

	private static SpellDefinition parseJson(String spellId, String body) {
		try {
			var json = JsonParser.parseString(body);
			return SpellDefinition.CODEC.parse(JsonOps.INSTANCE, json)
					.resultOrPartial(msg -> YoukaisHomecoming.LOGGER.warn(
							"Failed to open spell preview {}: {}", spellId, msg))
					.orElse(null);
		} catch (Exception e) {
			YoukaisHomecoming.LOGGER.warn("Failed to decode spell preview {}", spellId, e);
			return null;
		}
	}

	private static final class Assembly {
		final String spellId;
		final int total;
		final String[] parts;
		int received;

		Assembly(String spellId, int total) {
			this.spellId = spellId != null ? spellId : "";
			this.total = total;
			this.parts = new String[total];
		}
	}
}
