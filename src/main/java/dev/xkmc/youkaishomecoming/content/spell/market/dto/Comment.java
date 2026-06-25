package dev.xkmc.youkaishomecoming.content.spell.market.dto;

import com.google.gson.annotations.SerializedName;

public class Comment {

	@SerializedName(value = "uuid", alternate = {"id", "comment_uuid"})
	public String uuid;

	@SerializedName("author_name")
	public String authorName;

	@SerializedName("author_uuid")
	public String authorUuid;

	@SerializedName("content")
	public String content;

	@SerializedName(value = "image_url", alternate = {"image", "imageUrl"})
	public String imageUrl;

	@SerializedName(value = "timestamp", alternate = {"created_at", "upload_date"})
	public long timestamp;

}
