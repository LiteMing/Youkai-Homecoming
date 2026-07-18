package dev.xkmc.youkaishomecoming.content.spell.market;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MarketImportManifest {

	public Map<String, Entry> entries = new LinkedHashMap<>();

	public static class Entry {
		public String marketUuid = "";
		public String localSpellId = "";
		public List<String> exactTags = List.of();
		public String managedTag = "";
		public long updatedAt;
		public String contentHash = "";
		public long importTime;

		public Entry() {
		}
	}
}
