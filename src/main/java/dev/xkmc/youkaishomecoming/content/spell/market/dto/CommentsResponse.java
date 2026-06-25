package dev.xkmc.youkaishomecoming.content.spell.market.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class CommentsResponse {

	@SerializedName("comments")
	public List<Comment> comments;

}
