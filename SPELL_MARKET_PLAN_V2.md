# 符卡市场功能实现计划 v2

## 修正的需求

### 1. 命令和入口
- ✅ `/yhmarket` - 打开市场GUI
- ✅ `/spellmarket` - 打开市场GUI
- ⚠️ 不注册客户端 `/yhspell` 根命令，避免和现有服务端 `/yhspell` 命令树冲突
- ✅ 从编辑器和市场界面都有切换按钮

### 2. 界面切换
- 编辑器右上角添加 "Market" 按钮 → 打开市场界面
- 市场界面右上角添加 "Editor" 按钮 → 返回编辑器
- 两个按钮在相同位置，实现无缝切换

### 3. 符卡保存位置
- ❌ ~~下载后自动保存到全局~~
- ✅ 下载后保存到**存档级别**（世界专属）
- ✅ 提示玩家：需要全局共享请在编辑器中使用"Export Global"按钮

### 4. 本地化
- ✅ 完整的中英文GUI本地化
- ✅ 使用YHLangData枚举系统

### 5. 客户端限流
- ✅ 防止频繁请求
- ✅ 上传冷却：每分钟最多1次
- ✅ 点赞冷却：每个符卡只能点赞一次（本地缓存）
- ✅ 搜索防抖：500ms延迟

---

## 需要添加的本地化键（YHLangData.java）

```java
// 符卡市场相关
MARKET_TITLE("spell_market.title", "Spell Card Market", 0, null),
MARKET_SEARCH("spell_market.search", "Search...", 0, null),
MARKET_REFRESH("spell_market.refresh", "Refresh", 0, null),
MARKET_UPLOAD("spell_market.upload", "Upload", 0, null),
MARKET_CLOSE("spell_market.close", "Close", 0, null),
MARKET_TO_EDITOR("spell_market.to_editor", "Editor", 0, null),
MARKET_PREV("spell_market.prev", "Previous", 0, null),
MARKET_NEXT("spell_market.next", "Next", 0, null),
MARKET_PAGE("spell_market.page", "Page %s / %s", 2, null),
MARKET_LOADING("spell_market.loading", "Loading...", 0, null),
MARKET_NO_SPELLS("spell_market.no_spells", "No spells found", 0, null),
MARKET_DOWNLOAD("spell_market.download", "Download", 0, null),
MARKET_LIKE("spell_market.like", "Like", 0, null),
MARKET_LIKED("spell_market.liked", "Liked", 0, null),
MARKET_FILTER_TAG("spell_market.filter_tag", "Filtering by tag: %s (click to clear)", 1, ChatFormatting.GREEN),

// 下载提示
MARKET_DETAIL("spell_market.detail", "Details", 0, null),
MARKET_BACK("spell_market.back", "Back", 0, null),
MARKET_COMMENTS("spell_market.comments", "Comments", 0, null),
MARKET_NO_COMMENTS("spell_market.no_comments", "No comments yet", 0, null),
MARKET_COMMENT_PLACEHOLDER("spell_market.comment.placeholder", "Write a comment...", 0, null),
MARKET_COMMENT_IMAGE("spell_market.comment.image", "Image URL (optional)", 0, null),
MARKET_COMMENT_POST("spell_market.comment.post", "Post", 0, null),
MARKET_COMMENT_DELETE("spell_market.comment.delete", "Delete", 0, null),
MARKET_COMMENT_FAIL("spell_market.comment.fail", "Comment request failed", 0, ChatFormatting.RED),
MARKET_IMAGE_LOADING("spell_market.image.loading", "Loading image...", 0, null),
MARKET_IMAGE_UNAVAILABLE("spell_market.image.unavailable", "Image unavailable", 0, ChatFormatting.RED),
MARKET_DOWNLOAD_SUCCESS("spell_market.download_success", "Downloaded: %s\nSaved to world storage.\nUse Editor > Export to share globally.", 1, ChatFormatting.GREEN),
MARKET_DOWNLOAD_FAIL("spell_market.download_fail", "Failed to download spell", 0, ChatFormatting.RED),
MARKET_DOWNLOADING("spell_market.downloading", "Downloading: %s", 1, null),

// 上传相关
MARKET_UPLOAD_TITLE("spell_market.upload.title", "Upload Spell", 0, null),
MARKET_UPLOAD_NAME("spell_market.upload.name", "Name:", 0, null),
MARKET_UPLOAD_DESC("spell_market.upload.desc", "Description:", 0, null),
MARKET_UPLOAD_AUTHOR("spell_market.upload.author", "Author:", 0, null),
MARKET_UPLOAD_CATEGORY("spell_market.upload.category", "Category:", 0, null),
MARKET_UPLOAD_TAGS("spell_market.upload.tags", "Tags:", 0, null),
MARKET_UPLOAD_ADD_TAG("spell_market.upload.add_tag", "Add Tag", 0, null),
MARKET_UPLOAD_BTN("spell_market.upload.button", "Upload", 0, null),
MARKET_UPLOAD_CANCEL("spell_market.upload.cancel", "Cancel", 0, null),
MARKET_UPLOAD_SUCCESS("spell_market.upload.success", "Upload successful!\nSpell ID: %s", 1, ChatFormatting.GREEN),
MARKET_UPLOAD_FAIL("spell_market.upload.fail", "Upload failed: %s", 1, ChatFormatting.RED),
MARKET_UPLOAD_COOLDOWN("spell_market.upload.cooldown", "Please wait %s seconds before uploading again", 1, ChatFormatting.YELLOW),

// 错误信息
MARKET_ERROR_DISABLED("spell_market.error.disabled", "Market is disabled in config", 0, ChatFormatting.RED),
MARKET_ERROR_NETWORK("spell_market.error.network", "Network error. Please check your connection.", 0, ChatFormatting.RED),

// 编辑器切换按钮
EDITOR_TO_MARKET("spell_editor.to_market", "Market", 0, null),
```

---

## 实现步骤

### Phase 1: 基础架构
1. 添加本地化键到YHLangData.java
2. 添加zh_cn翻译到LangData生成器
3. 创建SpellMarketAPI（客户端限流版）
4. 创建DTO类（复用之前的）
5. 创建SpellMarketManager（单例）

### Phase 2: 市场界面
1. 创建SpellMarketScreen
   - 使用本地化文本
   - 添加"Editor"切换按钮
   - 下载保存到存档级别
   - 搜索防抖功能
2. 创建SpellUploadDialog
   - 本地化表单
   - 上传冷却检查

### Phase 3: 编辑器集成
1. 在SpellPreviewScreen添加"Market"按钮
   - 位置：右上角工具栏
   - 点击：打开SpellMarketScreen，传入当前定义
2. 修改保存逻辑
   - 下载的符卡使用CustomSpellCircleStorage.saveWorld()
   - 提示信息使用本地化

### Phase 4: 限流实现
```java
public class SpellMarketRateLimiter {
    private long lastUploadTime = 0;
    private static final long UPLOAD_COOLDOWN_MS = 60000; // 1分钟

    private Map<String, Long> likedSpells = new HashMap<>();
    private static final long LIKE_COOLDOWN_MS = 1000; // 1秒

    private long lastSearchTime = 0;
    private static final long SEARCH_DEBOUNCE_MS = 500; // 500ms防抖

    public boolean canUpload() {
        long now = System.currentTimeMillis();
        return (now - lastUploadTime) >= UPLOAD_COOLDOWN_MS;
    }

    public long getUploadCooldownRemaining() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastUploadTime;
        return Math.max(0, UPLOAD_COOLDOWN_MS - elapsed) / 1000;
    }

    public void markUpload() {
        lastUploadTime = System.currentTimeMillis();
    }

    public boolean canLike(String uuid) {
        Long lastLike = likedSpells.get(uuid);
        if (lastLike == null) return true;
        long now = System.currentTimeMillis();
        return (now - lastLike) >= LIKE_COOLDOWN_MS;
    }

    public void markLike(String uuid) {
        likedSpells.put(uuid, System.currentTimeMillis());
    }
}
```

### Phase 5: 评论与详情页
1. 创建 SpellDetailScreen
   - 从市场列表点击 Details 进入
   - 拉取 `/spells/{uuid}/comments`
   - 支持文字评论输入
   - 支持可选外链图片 URL
2. 评论撤销
   - DTO 支持 comment uuid / author_uuid
   - 客户端仅显示本人评论的 Delete
   - 通过 `/spells/{spell_uuid}/comments/{comment_uuid}` 删除
3. 外链图床预览
   - 客户端异步下载 http/https 图片
   - 使用 DynamicTexture 注册到客户端渲染
   - 不上传、不缓存到 VPS
   - 图片过大、不可解码、非 http/https 时降级为链接文本
4. 后端待部署
   - 当前线上 `/comments` 仅支持 GET
   - 需要补 POST / DELETE 才能真正发布和撤销评论

---

## 文件清单

### 新增文件
```
src/main/java/dev/xkmc/youkaishomecoming/content/spell/market/
├── dto/
│   ├── SpellListEntry.java
│   ├── SpellListResponse.java
│   ├── SpellDetail.java
│   ├── Comment.java
│   ├── CommentsResponse.java
│   ├── LikeResult.java
│   └── UploadResponse.java
├── LikedSpellsStore.java            # 本地点赞状态缓存
├── MarketImageCache.java            # 外链图片异步预览
├── SpellMarketAPI.java              # HTTP客户端（带限流）
├── SpellMarketManager.java          # 单例管理器
├── SpellMarketRateLimiter.java     # 限流器 ✅ NEW
├── SpellDetailScreen.java           # 详情和评论GUI
├── SpellMarketScreen.java           # 市场GUI（中英文）
└── SpellUploadDialog.java           # 上传对话框（中英文）
```

### 修改文件
```
src/main/java/dev/xkmc/youkaishomecoming/
├── init/data/YHLangData.java                       # 添加本地化键
├── init/data/YHModConfig.java                      # 市场配置
└── content/spell/preview/SpellPreviewScreen.java   # 添加Market按钮
```

---

## 测试清单

- [ ] `/yhmarket` 打开市场
- [ ] `/spellmarket` 打开市场
- [ ] 编辑器"Market"按钮工作
- [ ] 市场"Editor"按钮返回编辑器
- [ ] 下载符卡保存到存档
- [ ] 中英文切换正常
- [ ] 上传冷却60秒生效
- [ ] 点赞只能点一次
- [ ] 搜索防抖500ms
- [ ] 标签筛选功能
- [ ] 详情页评论列表
- [ ] 评论发布/撤销（需要后端 POST/DELETE）
- [ ] 外链图片预览
- [ ] 网络错误提示

---

## 下一步

由于token限制，建议你：

1. **先实现本地化**：在YHLangData.java添加所有市场相关的键
2. **创建限流器**：SpellMarketRateLimiter.java
3. **复用API和DTO**：从之前的代码复制核心逻辑
4. **实现GUI**：使用本地化文本，添加切换按钮
5. **测试完整流程**

需要我继续实现具体代码吗？
