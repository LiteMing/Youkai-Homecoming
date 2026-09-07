package dev.xkmc.youkaishomecoming.content.spell.client;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraftforge.network.NetworkEvent;

/** Server-authoritative snapshot of spell cards currently visible to one player. */
@SerialClass
public class ActiveSpellHudToClient extends SerialPacketBase {

	@SerialClass
	public static class Entry {
		@SerialClass.SerialField
		public int hostId;
		@SerialClass.SerialField
		public String spellId = "";
		@SerialClass.SerialField
		public String displayName = "";
		@SerialClass.SerialField
		public boolean hostile;
		@SerialClass.SerialField
		public boolean own;

		public Entry() {
		}

		public Entry(int hostId, String spellId, String displayName, boolean hostile, boolean own) {
			this.hostId = hostId;
			this.spellId = spellId == null ? "" : spellId;
			this.displayName = displayName == null ? "" : displayName;
			this.hostile = hostile;
			this.own = own;
		}
	}

	@SerialClass.SerialField
	// l2serial 1.2.4 allocates an incoming object array only when this field is null.
	public Entry[] entries;

	@Deprecated
	public ActiveSpellHudToClient() {
	}

	public ActiveSpellHudToClient(java.util.List<Entry> entries) {
		this.entries = entries == null ? new Entry[0] : entries.toArray(Entry[]::new);
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		ActiveSpellHudOverlay.update(this);
	}
}
