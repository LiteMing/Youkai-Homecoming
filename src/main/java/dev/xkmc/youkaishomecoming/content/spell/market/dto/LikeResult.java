package dev.xkmc.youkaishomecoming.content.spell.market.dto;

/**
 * 点赞结果枚举，区分三种状态：
 * SUCCESS - 点赞成功（服务器返回 200）
 * ALREADY_LIKED - 该符卡已被点赞（服务器返回 400）
 * ERROR - 网络或其他错误
 */
public enum LikeResult {
    SUCCESS,
    ALREADY_LIKED,
    ERROR
}
