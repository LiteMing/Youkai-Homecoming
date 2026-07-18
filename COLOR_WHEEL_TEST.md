# 色轮渲染功能测试指南

## 修改说明

### 修复的问题
**LivingCardHolder.prepareDanmaku()** 之前对所有弹幕强制设置tint，导致传统16色弹幕（BUBBLE, BUTTERFLY等）被错误地应用了ColorProvider的颜色，破坏了原有彩色纹理的显示。

**修复后：** 只有 `TINTED` 和 `FIXED` 模式的弹幕才会设置tint，`DYE_TEXTURES` 模式保持白色tint，让彩色纹理自然显示。

### 新增功能
- **SCALE（鳞弹）** - 现在支持色轮渲染，可以无极变色
- **GIANT_YINYANG（阴阳玉）** - 升级为TINTED模式，支持无极变色

## 弹幕类型分类

### 🎨 传统16色模式（DYE_TEXTURES）
使用16个预制的彩色纹理，不支持运行时颜色变化：
- CIRCLE（圆弹）
- BALL（球弹）
- MENTOS（太鼓弹）
- BUBBLE（太玉）
- BUTTERFLY（蝶弹）
- SPARK（火花）
- STAR（星弹）
- TALISMAN（符弹）
- KUNAI（苦无）
- KNIFE（刀）

### 🌈 色轮渲染模式（TINTED）
使用白色纹理+运行时tint，支持无极变色：
- **SCALE（鳞弹）** ✨新
- **GIANT_YINYANG（阴阳玉）** ✨新

### 🔒 固定纹理模式（FIXED）
特殊弹幕，不使用颜色系统：
- MOON（月球弹）

## 测试步骤

### 1. 传统16色弹幕测试（应保持原样）

在Spell Editor中创建测试符卡：
```json
{
  "actions": [
    {
      "type": "fire_danmaku",
      "bullet": "bubble",
      "color": "red",
      "count": 10,
      "speed": 10,
      "lifetime": 100,
      "pattern": "ring"
    }
  ]
}
```

**预期结果：**
- BUBBLE弹幕显示为**红色纹理**（不是白色+红色tint）
- 切换 `"color"` 到其他颜色（blue, green等），纹理应切换到对应颜色
- 颜色应该鲜艳、饱和，与原版16色一致

**如果失败（弹幕变成灰白色）：**
说明tint仍在影响DYE_TEXTURES模式，检查 `LivingCardHolder.prepareDanmaku()` 的条件判断。

---

### 2. 色轮渲染测试：基础颜色

测试SCALE弹幕的基本色轮功能：
```json
{
  "actions": [
    {
      "type": "fire_danmaku",
      "bullet": "scale",
      "color": "red",
      "count": 10,
      "speed": 10,
      "lifetime": 100,
      "pattern": "ring"
    }
  ]
}
```

**预期结果：**
- SCALE弹幕显示为**红色**（白色纹理+红色tint）
- 切换颜色后，弹幕颜色应立即改变
- 颜色应该均匀、准确

---

### 3. 色轮渲染测试：Indexed模式

测试根据索引选择颜色：
```json
{
  "actions": [
    {
      "type": "repeat",
      "count": 16,
      "index_variable": "i",
      "actions": [
        {
          "type": "fire_danmaku",
          "bullet": "scale",
          "color": {
            "type": "indexed",
            "index": {"type": "variable", "key": "i"},
            "palette": ["red", "orange", "yellow", "lime", "green", "cyan", 
                        "light_blue", "blue", "purple", "magenta", "pink", 
                        "white", "light_gray", "gray", "brown", "black"]
          },
          "count": 1,
          "speed": 10,
          "lifetime": 100,
          "angle_offset": {"type": "mult", "values": [
            {"type": "variable", "key": "i"},
            {"type": "constant", "value": 22.5}
          ]},
          "pattern": "aimed"
        }
      ]
    }
  ]
}
```

**预期结果：**
- 发射16个SCALE弹幕，每个颜色不同
- 颜色应按palette顺序排列成彩虹扇形

---

### 4. 色轮渲染测试：Cycle模式（随时间变色）

测试循环变色：
```json
{
  "actions": [
    {
      "type": "fire_danmaku",
      "bullet": "scale",
      "color": {
        "type": "cycle",
        "palette": ["red", "yellow", "green", "cyan", "blue", "magenta"],
        "interval": 5
      },
      "count": 30,
      "speed": 10,
      "lifetime": 100,
      "pattern": "ring"
    }
  ]
}
```

**预期结果：**
- 每5 tick改变一次颜色
- 颜色按palette循环：红→黄→绿→青→蓝→洋红→红...
- 所有弹幕应**同时**变色（因为它们共享同一个phase tick）

---

### 5. 色轮渲染测试：RandomChoice模式

测试随机颜色：
```json
{
  "actions": [
    {
      "type": "fire_danmaku",
      "bullet": "scale",
      "color": {
        "type": "random_choice",
        "palette": ["red", "blue", "green", "yellow", "magenta", "cyan"]
      },
      "count": 50,
      "speed": 10,
      "lifetime": 100,
      "pattern": "ring"
    }
  ]
}
```

**预期结果：**
- 每个弹幕随机选择一个颜色
- 颜色分布应该随机、均匀
- 重新发射时颜色应该不同

---

### 6. GIANT_YINYANG色轮测试

测试阴阳玉的色轮渲染：
```json
{
  "actions": [
    {
      "type": "fire_danmaku",
      "bullet": "giant_yinyang",
      "color": {
        "type": "cycle",
        "palette": ["red", "orange", "yellow", "green", "cyan", "blue", "purple"],
        "interval": 10
      },
      "count": 1,
      "speed": 5,
      "lifetime": 200,
      "pattern": "aimed"
    }
  ]
}
```

**预期结果：**
- GIANT_YINYANG使用白色纹理+彩虹循环tint
- 每10 tick变换一次颜色
- 纹理应该清晰、无重影

---

### 7. 混合测试：传统16色 + 色轮渲染

确保两种模式可以共存：
```json
{
  "actions": [
    {
      "type": "fire_danmaku",
      "bullet": "bubble",
      "color": "red",
      "count": 20,
      "speed": 8,
      "lifetime": 100,
      "pattern": "ring"
    },
    {
      "type": "fire_danmaku",
      "bullet": "scale",
      "color": {
        "type": "cycle",
        "palette": ["cyan", "magenta"],
        "interval": 5
      },
      "count": 20,
      "speed": 12,
      "lifetime": 100,
      "pattern": "ring"
    }
  ]
}
```

**预期结果：**
- BUBBLE保持固定红色（彩色纹理）
- SCALE在cyan和magenta之间循环
- 两种弹幕互不干扰

---

## 常见问题排查

### 问题1：传统弹幕变成灰白色或单一颜色
**原因：** DYE_TEXTURES模式的弹幕被错误地应用了tint

**检查点：**
1. `LivingCardHolder.prepareDanmaku()` 的条件是否正确
2. `ItemDanmakuEntity.setItem()` 后是否自动从ItemStack读取了颜色
3. `DanmakuItem.getColor()` 对DYE_TEXTURES模式是否返回WHITE

### 问题2：SCALE或GIANT_YINYANG不变色
**原因：** ColorProvider的颜色没有正确传递到实体

**检查点：**
1. `type.usesTint()` 是否返回true
2. `danmaku.setTint(color.argb())` 是否被调用
3. `ItemDanmakuRenderer.color()` 是否返回 `danmaku.getRenderTint()`

### 问题3：颜色不准确或偏灰
**原因：** 纹理不是纯白色，或tint计算有误

**检查点：**
1. `scale/white.png` 和 `giant_yinyang/white.png` 是否为纯白（#FFFFFF）
2. Alpha通道是否正确（应该保留）
3. `DanmakuRenderStates.fading()` 是否正确处理颜色

---

## 成功标准

- ✅ 传统16色弹幕保持原有鲜艳的彩色纹理
- ✅ SCALE和GIANT_YINYANG支持ColorProvider的所有模式
- ✅ Cycle模式的颜色变化流畅、准确
- ✅ Indexed和RandomChoice模式的颜色分布正确
- ✅ 两种模式可以在同一符卡中共存

---

## 后续优化建议

如果色轮渲染效果良好，可以考虑：
1. 为其他弹幕类型添加config选项，让玩家选择使用TINTED还是DYE_TEXTURES
2. 添加HSV色轮动画支持（类似已有的colorAnimation功能）
3. 为TINTED模式添加纹理层叠支持（白色基底+彩色高光层）
