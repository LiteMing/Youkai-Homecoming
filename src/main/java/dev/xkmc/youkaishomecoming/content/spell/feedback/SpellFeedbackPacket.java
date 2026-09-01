package dev.xkmc.youkaishomecoming.content.spell.feedback;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.spell.client.CameraShakeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Batched server -> client presentation cues for one observer and one tick. */
@SerialClass
public class SpellFeedbackPacket extends SerialPacketBase {
	@SerialClass.SerialField public byte[] payload = new byte[0];

	public SpellFeedbackPacket() {
	}

	public SpellFeedbackPacket(List<FeedbackCue> cues) {
		this.payload = encode(cues);
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		context.enqueueWork(() -> decodeAndPlay(payload));
		context.setPacketHandled(true);
	}

	private static byte[] encode(List<FeedbackCue> cues) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bytes);
			int count = Math.min(256, cues == null ? 0 : cues.size());
			out.writeByte(1);
			out.writeShort(count);
			for (int i = 0; i < count; i++) writeCue(out, cues.get(i));
			return bytes.toByteArray();
		} catch (IOException ignored) {
			return new byte[0];
		}
	}

	private static void writeCue(DataOutputStream out, FeedbackCue cue) throws IOException {
		if (cue instanceof SoundCue sound) {
			out.writeByte(0);
			writeString(out, sound.soundId().toString());
			out.writeByte(sound.source().ordinal());
			writePosition(out, sound.position());
			out.writeFloat(sound.volume()); out.writeFloat(sound.pitch());
			out.writeDouble(sound.radius()); out.writeBoolean(sound.attenuation());
		} else if (cue instanceof CameraShakeCue shake) {
			out.writeByte(1);
			writePosition(out, shake.position());
			out.writeFloat((float) shake.intensity()); out.writeInt(shake.duration());
			out.writeDouble(shake.frequency()); out.writeDouble(shake.radius());
			out.writeByte(shake.falloff().ordinal()); writeString(out, shake.channel());
		}
	}

	private static void writePosition(DataOutputStream out, Vec3 pos) throws IOException {
		Vec3 value = pos == null ? Vec3.ZERO : pos;
		out.writeDouble(value.x); out.writeDouble(value.y); out.writeDouble(value.z);
	}

	private static void writeString(DataOutputStream out, String value) throws IOException {
		byte[] data = value.getBytes(StandardCharsets.UTF_8);
		int length = Math.min(4096, data.length);
		out.writeShort(length); out.write(data, 0, length);
	}

	private static void decodeAndPlay(byte[] payload) {
		if (payload == null || payload.length < 3) return;
		try {
			DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
			if (in.readUnsignedByte() != 1) return;
			int count = Math.min(256, in.readUnsignedShort());
			Minecraft mc = Minecraft.getInstance(); if (mc.level == null) return;
			for (int i = 0; i < count; i++) {
				int type = in.readUnsignedByte();
				if (type == 0) {
					String id = readString(in); SoundSource source = enumValue(SoundSource.values(), in.readUnsignedByte(), SoundSource.HOSTILE);
					Vec3 pos = readPosition(in); float volume = in.readFloat(); float pitch = in.readFloat();
					double radius = in.readDouble(); boolean attenuation = in.readBoolean();
					ResourceLocation key = ResourceLocation.tryParse(id);
					SoundEvent sound = key == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(key);
					if (sound != null) {
						if (attenuation) mc.level.playLocalSound(pos.x, pos.y, pos.z, sound, source, volume, pitch, false);
						else mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
					}
				} else if (type == 1) {
					Vec3 pos = readPosition(in); float intensity = in.readFloat(); int duration = in.readInt();
					double frequency = in.readDouble(); double radius = in.readDouble();
					CueFalloff falloff = enumValue(CueFalloff.values(), in.readUnsignedByte(), CueFalloff.LINEAR);
					CameraShakeManager.add(new CameraShakeCue(CueOrigin.ACTION, pos, intensity, duration, frequency, radius, falloff, readString(in)));
				} else return;
			}
		} catch (IOException | RuntimeException ignored) {
			// Malformed client feedback must never affect gameplay or disconnect the client.
		}
	}

	private static Vec3 readPosition(DataInputStream in) throws IOException {
		return new Vec3(in.readDouble(), in.readDouble(), in.readDouble());
	}

	private static String readString(DataInputStream in) throws IOException {
		int length = Math.min(4096, in.readUnsignedShort()); byte[] data = in.readNBytes(length);
		return new String(data, StandardCharsets.UTF_8);
	}

	private static <T> T enumValue(T[] values, int ordinal, T fallback) {
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
	}
}
