package dev.xkmc.youkaishomecoming.content.spell.market.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class SpellListResponse {

	@SerializedName("spells")
	public List<SpellListEntry> spells;

	@SerializedName("total")
	public int total;

	@SerializedName("page")
	public int page;

	@SerializedName("per_page")
	public int perPage;

}
