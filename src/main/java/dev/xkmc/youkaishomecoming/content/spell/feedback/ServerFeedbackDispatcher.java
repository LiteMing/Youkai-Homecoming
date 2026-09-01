package dev.xkmc.youkaishomecoming.content.spell.feedback;

import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Queues and merges presentation cues until the end of the current server tick. */
@Mod.EventBusSubscriber(modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerFeedbackDispatcher {
	private static final Map<MinecraftServer, Map<UUID, List<FeedbackCue>>> QUEUES = new IdentityHashMap<>();

	private ServerFeedbackDispatcher() {
	}

	public static void enqueue(ServerLevel level, ServerPlayer player, FeedbackCue cue) {
		MinecraftServer server = level.getServer();
		if (server == null || player == null || cue == null) return;
		Map<UUID, List<FeedbackCue>> byPlayer = QUEUES.computeIfAbsent(server, ignored -> new LinkedHashMap<>());
		List<FeedbackCue> cues = byPlayer.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>());
		merge(cues, cue);
	}

	private static void merge(List<FeedbackCue> cues, FeedbackCue cue) {
		if (cue instanceof SoundCue sound) {
			for (int i = 0; i < cues.size(); i++) {
				if (!(cues.get(i) instanceof SoundCue old) || !old.soundId().equals(sound.soundId())
						|| old.source() != sound.source() || old.position() == null || sound.position() == null
						|| old.position().distanceToSqr(sound.position()) > 4) continue;
				cues.set(i, new SoundCue(old.soundId(), old.source(), old.origin(), old.position(),
						Math.max(old.volume(), sound.volume()), old.pitch(), Math.max(old.radius(), sound.radius()),
						old.attenuation() && sound.attenuation()));
				return;
			}
		} else if (cue instanceof CameraShakeCue shake) {
			for (int i = 0; i < cues.size(); i++) {
				if (!(cues.get(i) instanceof CameraShakeCue old) || !old.channel().equals(shake.channel())
						|| old.position() == null || shake.position() == null
						|| old.position().distanceToSqr(shake.position()) > 16) continue;
				cues.set(i, new CameraShakeCue(old.origin(), old.position(),
						Math.max(old.intensity(), shake.intensity()), Math.max(old.duration(), shake.duration()),
						Math.max(old.frequency(), shake.frequency()), Math.max(old.radius(), shake.radius()),
						old.falloff(), old.channel()));
				return;
			}
		}
		if (cues.size() < YHModConfig.COMMON.feedbackMaxCuesPerObserverTick.get()) cues.add(cue);
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		MinecraftServer server = event.getServer();
		Map<UUID, List<FeedbackCue>> byPlayer = QUEUES.remove(server);
		if (byPlayer == null) return;
		for (var entry : byPlayer.entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player != null && !entry.getValue().isEmpty()) {
				YoukaisHomecoming.HANDLER.toClientPlayer(new SpellFeedbackPacket(entry.getValue()), player);
			}
		}
	}
}
