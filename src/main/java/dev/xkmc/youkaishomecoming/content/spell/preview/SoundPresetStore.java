package dev.xkmc.youkaishomecoming.content.spell.preview;

import net.minecraftforge.fml.loading.FMLPaths;

import java.util.List;

/** Client-only sound-event presets shared by worlds and servers. */
public final class SoundPresetStore {
	private static final ClientStringPresetStore STORE = new ClientStringPresetStore(
			FMLPaths.CONFIGDIR.get().resolve("youkaishomecoming_sound_presets.json"), 64, 256);

	private SoundPresetStore() {
	}

	public static List<String> list() {
		return STORE.list();
	}

	public static void save(String sound) {
		STORE.save(sound);
	}

	public static void remove(String sound) {
		STORE.remove(sound);
	}
}
