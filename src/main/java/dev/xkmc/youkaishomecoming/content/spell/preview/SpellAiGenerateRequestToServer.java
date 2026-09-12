package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.compat.llmcore.YhLlmCoreBridge;
import dev.xkmc.youkaishomecoming.compat.llmcore.YhSpellDraftValidator;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellCardType;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SerialClass
public class SpellAiGenerateRequestToServer extends SerialPacketBase {
	public static final int MAX_CHUNK_CHARS = 28_000;
	private static final Map<String, Assembly> ASSEMBLIES = new ConcurrentHashMap<>();
	@SerialClass.SerialField public String prompt = "";
	@SerialClass.SerialField public String operation = "modify";
	@SerialClass.SerialField public String cardType = "normal";
	@SerialClass.SerialField public String currentJson = "";
	@SerialClass.SerialField public int transferId;
	@SerialClass.SerialField public int totalChunks;
	@SerialClass.SerialField public int chunkIndex;
	@SerialClass.SerialField public String chunk = "";

	@Deprecated public SpellAiGenerateRequestToServer() {}

	public SpellAiGenerateRequestToServer(String prompt, String operation, String cardType, String currentJson) {
		this.prompt = prompt;
		this.operation = operation;
		this.cardType = cardType;
		this.currentJson = currentJson;
	}

	public static SpellAiGenerateRequestToServer chunk(SpellAiGenerateRequestToServer base,
			int transferId, int chunkIndex, int totalChunks, String chunk) {
		SpellAiGenerateRequestToServer packet = new SpellAiGenerateRequestToServer(base.prompt, base.operation,
				base.cardType, "");
		packet.transferId = transferId;
		packet.chunkIndex = chunkIndex;
		packet.totalChunks = totalChunks;
		packet.chunk = chunk;
		return packet;
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		ServerPlayer player = context.getSender();
		if (player == null) return;
		context.enqueueWork(() -> {
			if (totalChunks > 0) {
				if (chunkIndex < 0 || chunkIndex >= totalChunks || totalChunks > 128) return;
				String key = player.getUUID() + ":" + transferId;
				Assembly assembly = ASSEMBLIES.computeIfAbsent(key, ignored -> new Assembly(prompt, operation, cardType, totalChunks));
				if (assembly.total != totalChunks) return;
				if (assembly.parts[chunkIndex] == null) assembly.received++;
				assembly.parts[chunkIndex] = chunk == null ? "" : chunk;
				if (assembly.received < assembly.total) return;
				ASSEMBLIES.remove(key);
				StringBuilder json = new StringBuilder();
				for (String part : assembly.parts) json.append(part == null ? "" : part);
				executeRequest(player, assembly.prompt, assembly.operation, assembly.cardType, json.toString());
				return;
			}
			executeRequest(player, prompt, operation, cardType, currentJson);
		});
	}

	private static void executeRequest(ServerPlayer player, String prompt, String operation,
			String cardType, String currentJson) {
			if (prompt == null || prompt.isBlank() || prompt.length() > 4000) {
				YoukaisHomecoming.HANDLER.toClientPlayer(new SpellAiGenerateResultToClient(false, "", "Prompt is empty or too long"), player);
				return;
			}
			YhLlmCoreBridge.generate(player, prompt, operation, cardType, currentJson)
					.whenComplete((result, error) -> player.server.execute(() -> {
						if (error != null) {
							YoukaisHomecoming.HANDLER.toClientPlayer(new SpellAiGenerateResultToClient(false, "", error.getMessage()), player);
						} else if (result == null || !result.success()) {
							YoukaisHomecoming.HANDLER.toClientPlayer(new SpellAiGenerateResultToClient(false, "", result == null ? "LLM request failed" : result.error()), player);
						} else {
							YhSpellDraftValidator.Result checked = YhSpellDraftValidator.validate(result.content());
							if (!checked.acceptedForPreview()) {
								String detail = checked.diagnostics().isEmpty() ? checked.status().name() : checked.diagnostics().get(0);
								YoukaisHomecoming.HANDLER.toClientPlayer(new SpellAiGenerateResultToClient(false, "", "AI draft rejected: " + detail), player);
							} else if (checked.definition().itemForm.cardType() != SpellCardType.byName(cardType)) {
								YoukaisHomecoming.HANDLER.toClientPlayer(new SpellAiGenerateResultToClient(false, "", "AI draft card type does not match the selected editor type"), player);
							} else {
								YoukaisHomecoming.HANDLER.toClientPlayer(new SpellAiGenerateResultToClient(true, checked.normalizedJson(), "Draft analyzed; review and save manually"), player);
							}
						}
					}));
	}

	private static final class Assembly {
		final String prompt;
		final String operation;
		final String cardType;
		final int total;
		final String[] parts;
		int received;
		Assembly(String prompt, String operation, String cardType, int total) {
			this.prompt = prompt;
			this.operation = operation;
			this.cardType = cardType;
			this.total = total;
			this.parts = new String[total];
		}
	}
}
