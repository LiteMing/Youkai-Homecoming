package dev.xkmc.youkaishomecoming.content.spell.market.dto;

import com.google.gson.annotations.SerializedName;

public class Comment {

	@SerializedName("author_name")
	public String authorName;

	@SerializedName("content")
	public String content;

	@SerializedName("timestamp")
	public long timestamp;

}
