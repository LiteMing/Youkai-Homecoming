package dev.xkmc.youkaishomecoming.content.spell.market.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class SpellListEntry {

	@SerializedName("id")
	public int id;

	@SerializedName("uuid")
	public String uuid;

	@SerializedName("name")
	public String name;

	@SerializedName("description")
	public String description;

	@SerializedName("author_name")
	public String authorName;

	@SerializedName("author_uuid")
	public String authorUuid;

	@SerializedName("category")
	public String category;

	@SerializedName("tags")
	public List<String> tags;

	@SerializedName("likes_count")
	public int likesCount;

	@SerializedName("downloads_count")
	public int downloadsCount;

	@SerializedName(value = "comments_count", alternate = {"comment_count"})
	public int commentsCount;

	@SerializedName("upload_date")
	public long uploadDate;

}
