# 预览模式性能审查与改进方案

## 已发现问题

### 1. on_hurt 反馈环导致实体指数爆炸 (已修复)

**现象**: 153 tick 内生成 1,350,000+ 实体, 11GB 内存耗尽, 游戏卡死

**根因**: 预览的 `onTargetHit` 回调触发 `runtime.hurt()` → on_hurt actions 激活 border → 每tick 8发 BALL → 命中 target → 再次 hurt → 指数爆炸

**修复**:
- `SpellRuntime.hurt()` 增加 20 tick 冷却, 防止反馈环
- `PreviewCardHolder.shoot()` 增加 50,000 实体安全上限, 超限拒绝新实体 + 自动暂停

### 2. 预览实体生命周期管理无上限

当前 `localEntities` 是无界 ArrayList, 没有任何背压机制。符卡设计不当可能快速填满内存。

## 性能瓶颈分析

### 当前架构

```
VirtualSpellScene.doTick()
  → SpellRuntime.tick(holder)          // 执行符卡逻辑, 发射弹幕
  → PreviewCardHolder.tick()           // 遍历所有实体:
      for each entity:
        ++tickCount
        sp.tick()                       // SimplifiedProjectile.tick()
        if (!isValid()) afterExpiry     // onExpiry 链
      flush pendingEntities
      hit detection loop                // O(N) 遍历检查碰撞
```

### 瓶颈 1: O(N) 全量遍历

每 tick 遍历所有存活实体。当存活实体数 > 10,000 时明显卡顿。
- `SimplifiedProjectile.tick()`: 位置更新 + mover 计算
- hit detection: AABB 检查对每个实体

### 瓶颈 2: 实体对象分配

每个弹幕创建一个 `ItemDanmakuEntity` (extends Entity)。Entity 对象较重 (~500 bytes)。
10,000 弹幕 ≈ 5MB 对象, GC 压力显著。

### 瓶颈 3: onExpiry 链式创建

3级 onExpiry 链 (如 reimu): expandRing → homingTrail → finalHoming。
每发弹幕过期时创建新弹幕, 突发 spike 在特定 tick。

### 瓶颈 4: DataDrivenTrailAction 变量快照

每个带 onExpiry 的弹幕创建 `DataDrivenTrailAction`, 其中 `new HashMap<>(runtime.getVariables())` 拷贝所有运行时变量。当变量多 + 弹幕多时, 大量小 HashMap 对象。

## Spark Profiling 实测数据 (3 万弹幕, i5-12400)

### 客户端 (Render Thread) — 帧率 ~30fps

测试条件: 3 万存活弹幕, 12 代 i5-12400 (6P+4E), 分配 7GB 堆

| 热点 | 占帧时间 | 说明 |
|------|---------|------|
| `putSortedQuadIndices` (透明排序) | **9.34%** | O(N log N) 距离排序, **已修复: sortOnUpload=false** |
| `flushPreviewQueue` (P5 buffer 填充) | 7.26% | ParallelBufferFiller 路径 |
| `EntityRenderDispatcher.getRenderer()` | 6.41% | MC 内部: 每实体查渲染器 Map |
| `ProjTypeHolder.create()` | 5.20% | PoseStack → Matrix4f 的 create 阶段 |
| `BufferUploader.drawWithShader()` | 4.20% | GPU 上传 + draw call |
| `BaseProjectile.tick()` | 8.40% | 弹幕物理 tick (位置/mover) |
| `SpatialHash.insert()` | 4.35% | **已修复: 回退为直接遍历** (单 target 下是负优化) |
| SpatialHash.query() | 0.03% | 查询本身极快, 但 insert 开销远超收益 |
| `setQuadSorting` | 0.99% | 排序设置 |

**关键发现**:
1. 渲染占帧时间 ~44%, tick 占 ~14%. 渲染是主瓶颈
2. 透明排序是单项最大热点 (9.34%), 弹幕无需精确深度排序 → 已关闭
3. P5 多线程 buffer 填充 (7.26%) 有效但非主要瓶颈
4. `EntityRenderDispatcher.getRenderer()` (6.41%) 是 MC 内部查找, 每个实体都调用, 难以优化
5. P2 SpatialHash 是净负优化: insert 4.35% vs query 0.03% → 已回退
6. CPU 利用率仅 25% (~4 线程满载), 说明瓶颈不在 CPU 计算力而在 MC 串行管线 + GPU 提交

### 服务端 (Server Thread) — 实际弹幕战场景

| 热点 | 占 Server Thread | 说明 |
|------|-----------------|------|
| `DanmakuProxyEntity.tick()` | **26.74%** | 弹幕 tick 总开销 |
| `ProjectileHitHelper.getHitResultOnMoveVector()` | **17.36%** | 碰撞检测 — 最大热点 |
| `IEntityCache.foreach()` | 11.42% | 遍历附近实体做碰撞 |
| `EntityStorageCache.get()` | 5.56% | 缓存查询 |
| `Level.getGameTime()` | **5.55%** | 每次 cache.get() 检查时间戳 |
| `DerivedLevelData.getGameTime()` | 5.51% | getGameTime 的实际实现 |
| `BaseProjectile.onHit()` | 5.12% | 命中后处理 |
| `DanmakuManager.send()` | 1.15% | 网络同步 |
| `SpellRuntime.tick()` | 0.87% | 符卡逻辑本身很轻 |

**关键发现**:
1. 碰撞检测 (17.36%) 是服务端最大热点, 其中实体遍历 (11.42%) 是主要开销
2. `Level.getGameTime()` 被调用频率极高 (5.55%), 因为 `EntityStorageCache.get()`
   每次都检查时间戳判断缓存是否过期. 对 3 万弹幕每 tick 调用 3 万次
3. 网络同步 (1.15%) 开销可控
4. 符卡逻辑本身 (0.87%) 几乎不占资源, 瓶颈全在物理层

## 改进方案 (按优先级排序)

### P0: 安全锁 (已实现)

- [x] 实体数量硬上限 50,000, 超限自动暂停 + 拒绝新实体
- [x] on_hurt 冷却 20 tick
- [x] UI 显示 "SAFETY LIMIT" 警告

### P1: 轻量级预览实体

**目标**: 预览中用轻量 struct 替代完整 Entity 对象

```java
record PreviewBullet(
    Vec3 pos, Vec3 vel, int life, int tickCount,
    YHDanmaku.Bullet type, DyeColor color,
    @Nullable MoverInstance mover,
    @Nullable DataDrivenTrailAction afterExpiry
)
```

**收益**: 对象大小从 ~500 bytes 降到 ~100 bytes, 减少 80% 内存。
**代价**: 需要独立的 tick/render 逻辑, 不能复用 Entity 子类方法。
**工作量**: 大 (需重构 PreviewCardHolder + render pipeline)

### P2: 空间分区碰撞检测 (已实现后回退)

已实现 SpatialHash (4格 cell), 但 spark profiling 显示是**净负优化**:
- `SpatialHash.insert()` 占帧时间 4.35% (3 万次 HashMap.computeIfAbsent + new ArrayList)
- `SpatialHash.query()` 仅占 0.03%
- 单 target 场景下, 每 tick 重建网格的开销远超 O(N) 扫描的节省

**已回退为直接遍历**。SpatialHash.java 保留供多 target 场景备用。
**教训**: 空间索引结构的 build 成本需要被 query 节省的时间摊销。单查询场景不适用。

### P3: 实体对象池化

复用已过期的 `ItemDanmakuEntity` 对象, 而非 new + GC:

```java
class EntityPool<T extends Entity> {
    Queue<T> pool;
    T acquire() { return pool.isEmpty() ? create() : pool.poll(); }
    void release(T entity) { reset(entity); pool.offer(entity); }
}
```

**收益**: 大幅减少 GC 压力, 对长时间运行的预览效果显著
**工作量**: 中 (需确保 Entity 状态正确重置)

### P4: 变量快照优化 (已实现)

`DataDrivenTrailAction` 的 `new HashMap<>(runtime.getVariables())` 改为 `Map.copyOf()`:
- JDK 紧凑不可变 Map 实现, 变量数 1-5 个时使用特化 Map0/Map1/MapN
- 避免 HashMap 的 16 槽桶数组 + Entry 对象分配
- `execute()` 中保存/恢复路径同样改用 `Map.copyOf()`

**收益**: 减少小对象分配, 改善 GC
**状态**: 已完成

### P5: 多线程并行 Buffer 填充 (已实现)

采用方案 B (线程局部 byte[] + 合并), 已实现并修复:
- `ParallelBufferFiller`: 阈值 2000, max 4 线程, ForkJoinPool.commonPool()
- 各弹幕类型的 Ins 新增 `texToArray(byte[], int)` 用于并行安全写入
- `BulkDataWriter.bulkWrite()`: 逐顶点写入 BufferBuilder (必须逐顶点前进 nextElementByte)
- 异常时自动 fallback 到单线程

**Spark 实测**: `flushPreviewQueue` 占帧时间 7.26%。并行化有效减少了矩阵乘法时间,
但合并阶段 (bulkWrite 逐顶点写 BufferBuilder) 仍是串行的, 这是 MC BufferBuilder API 的限制。

**实际收益有限的原因**: buffer 填充 (7.26%) 不是帧时间的主要部分;
透明排序 (9.34%)、EntityRenderDispatcher 查找 (6.41%)、GPU 上传 (4.20%) 等开销更大。
真正的渲染侧加速需要 P6 (GPU 实例化) 来绕过整个 MC 渲染管线。

**状态**: 已完成 (P5fix2 已修复架构缺陷)

### P5fix2: 全局统一并行 fill (已实现)

**问题**: 原 P5 的并行化是 per-type 独立执行的。每种弹幕类型的 `start()` 各自调用 `ParallelBufferFiller.fill()`, 独立做一次 ForkJoin 分组。当多个大类型同时存在时 (如 40000 + 40000), 做两次 ForkJoin 而非全局统一一次。

**实现**:
1. `ParallelBufferFiller` 新增 `submit()` + `joinAndMerge()` + `joinAndMergeAll()` — 支持 submit/join 分离
2. `RenderableDanmakuType` 新增 `getTex()` + `submitFill()` — 每类型自定义 submit 逻辑
3. `RenderQueue.flush()` 重构为两阶段: 所有类型先统一 submit, 再全局 `joinAndMergeAll()`
4. CrossProjectileType (8 vertices/entry) 和 AnimatedProjectileType (需要 frameCount 参数) 各自覆写 `submitFill()`

**效果**: 所有类型的 ForkJoin 任务在同一个 join 点统一等待, ForkJoinPool 可以在所有类型间交错调度工作。

### PH: 渲染前置 fill + 延迟 join (已实现)

**思路**: 在 `AFTER_SKY` 阶段调用 `renderAll()` + `submitFills()` 启动并行计算,
ForkJoinPool 线程立即开始工作, 与 render thread 后续的 solid blocks/cutouts/entities 渲染并行进行。
到 `AFTER_ENTITIES` 阶段时 `joinAndFlush()` 等待完成并上传。

**实现**:
1. `RenderLevelStageEvent` 监听从 `AFTER_ENTITIES` 扩展到 `AFTER_SKY`
2. `AFTER_SKY`: `ClientDanmakuCache.renderAll()` 收集 Ins → `RenderQueue.submitFills()` 提交并行任务
3. `AFTER_ENTITIES`: `RenderQueue.joinAndFlush()` 等待并合并 SOLID/TRANSPARENT 结果
4. `AFTER_PARTICLES`: `RenderQueue.joinAndFlush()` 处理 ADDITIVE 类型
5. 预览模式 (`flushPreviewQueue`) 保留同步 `flush()` (submitFills + joinAndFlush)

### P6: GPU 实例化渲染 (Instanced Rendering)

**目标**: 用 `glDrawArraysInstanced` 替代当前的 CPU 顶点展开, 将矩阵乘法移至 GPU

#### 为什么当前管线是实例化的教科书案例

当前做法: CPU 把同一个 unit quad 重复展开 N 次写入 buffer, 每个顶点做一次
`new Vector4f().mul(Matrix4f)` — 10,000 弹幕 = 40,000 次矩阵乘法 + 960KB CPU→GPU 传输。

实例化做法: 上传 unit quad 一次 (4 顶点 = 96 bytes, 永驻 GPU), 每帧只上传 N 个
instance data, 一次 draw call 完成。

关键发现: `SimpleProjectileType.create()` 中 `set3x3(identity * scale)` 故意丢弃所有旋转,
mat4 实际结构为 `diag(s,s,s,1) + translation(tx,ty,tz)` — 仅 4 个有效值。
**不需要上传完整 mat4 (64 bytes), 只需上传压缩后的 per-instance data。**

| 当前管线数据 | 实例化适配条件 |
|-------------|--------------|
| 所有弹幕共享同一 unit quad (-0.5..0.5, 4顶点) | 同一几何体渲染 N 次 |
| mat4 中仅 position + scale 有效 (billboard 类型) | per-instance data 最低 **20 bytes** |
| CPU 每帧写 96N bytes 展开顶点 | 实例化后降为 **20~32N bytes**, 减少 67~79% |
| 使用标准 MC shader (position_tex_color) | 需自定义 shader (GPU 端重建矩阵) |
| 无任何 GL 实例化代码 | 从零实现 |

#### 适用范围与 per-instance 数据分析

各类型 `create()` 中 Matrix4f 的实际构成:

| 弹幕类型 | mat4 实际结构 | 真正需要的 per-instance data | 大小 | vs mat4 (68B) |
|---------|-------------|---------------------------|------|--------------|
| Simple | `diag(s) + translate(pos)` — `set3x3` 丢弃旋转 | `vec3 pos + float scale + int color` | **20B** | -71% |
| Rotating | 同上 + Z轴旋转 | + `float zAngle` | **24B** | -65% |
| Animated | 同 Simple | + `int frame` (UV offset) | **24B** | -65% |
| Swinging | 完整旋转链: RotY * RotX * RotZ * RotX(tilt) * Scale | `vec3 pos, float scale, float yaw, float pitch, float corkscrewAngle, int color` (tilt/size 为类型常量 uniform) | **28B** | -59% |
| Cross | RotY * RotX, 第二面 = 第一面 * RotZ(90) | 见下方分析 | — | 见下方 |
| Butterfly | RotY * RotX * RotZ(wingFlap), 每实体 2 个 Ins | 见下方分析 | — | 见下方 |
| 激光类型 | 已分解为基向量, 非 mat4 | N/A — 几何体不统一 | N/A | 保留原管线 |

#### 关键限制: 不同类型使用不同的基础几何体

**并非所有弹幕类型共享同一个 unit quad。** 存在两种不同的基础平面:

| 基础平面 | `vertex()` 写入坐标 | 使用类型 |
|---------|-------------------|---------|
| XY 平面 (billboard) | `(x-0.5, y-0.5, 0.0)` | Simple, Rotating, Animated |
| XZ 平面 (飞行方向) | `(x-0.5, 0.0, y-0.5)` | Swinging, Cross, Butterfly |

此外, Butterfly 的几何体与标准 unit quad **形状不同**:
- 左翅: x 范围 `[0, 0.5]` → 偏移后 `[-0.5, 0.0]` (半宽 quad)
- 右翅: x 范围 `[0.5, 1.0]` → 偏移后 `[0.0, 0.5]` (半宽 quad)
- UV 也对应半幅: 左翅 u=`[0, 0.5]`, 右翅 u=`[0.5, 1.0]`
- 每实体产生 2 个 Ins, 各自不同的 Matrix4f (不同翅膀扇动角度)

#### 逐类型实例化可行性 (严格评估)

| 类型 | 可行性 | 说明 |
|------|--------|------|
| **Simple** | **完全可行** | 标准 XY quad, 1 quad/entity, 标准 UV |
| **Rotating** | **完全可行** | 同 Simple, 仅需额外 per-instance zAngle |
| **Animated** | **可行, shader 需 UV 计算** | 标准 XY quad, 但 V 坐标按 frame 分片, shader 需 per-instance frame 值 |
| **Swinging** | **可行, shader 较复杂** | 标准 XZ quad, 但需 GPU 重建 RotY*RotX*RotZ*RotX(tilt)*Scale |
| **Cross** | **有开销** | 每实体 2 quad (m4b = m4a * RotZ(90)). 要么实例数翻倍, 要么 2 次 instanced draw |
| **Butterfly** | **不适合简单实例化** | 几何体是半宽 quad (非标准 unit quad), 每实体 2 个不同矩阵, UV 范围也不同 |
| **激光类型** | **不适合** | 几何体完全不统一 |

#### 建议分级策略

**第一优先级 — 完全适配, 覆盖大部分弹幕:**
- Simple + Rotating + Animated → 共用 XY plane VAO, 收益最大
- 需要 1 个自定义 shader (billboard + 可选 Z 旋转 + 可选 frame UV)

**第二优先级 — 需要独立 shader:**
- Swinging → 用 XZ plane VAO, shader 重建完整旋转链
- Cross → 用 XZ plane VAO, 每实体拆成 2 个 instance (第二个自动加 RotZ(90))

**保留原有管线:**
- Butterfly → 几何体不统一, 强行实例化得不偿失, 保留 BulkDataWriter 路径
- 激光类型 → 保留 BulkDataWriter 路径

**结论**:
- 4 种类型 (Simple/Rotating/Animated/Swinging) 完全适合实例化, 占弹幕总量大多数
- Cross 可实例化但有额外开销 (实例数翻倍或多次 draw call)
- Butterfly 和激光类型不适合, 保留原有批量渲染
- GPU 矩阵重建成本极低 (几次 sin/cos, GPU 最擅长此类并行计算)

#### 实施方案

**1. 自定义 Shader — Billboard 类型 (Simple/Rotating/Animated)**

```glsl
// danmaku_billboard.vsh
#version 150

// 共享几何体 (unit quad, 永驻 GPU)
in vec3 Position;       // 4 个顶点: (-0.5,-0.5,0), (0.5,-0.5,0), (0.5,0.5,0), (-0.5,0.5,0)
in vec2 UV0;            // 对应 UV

// per-instance data (压缩, 每实例仅 24 bytes)
in vec4 InstancePosScale;  // xyz = view-space position, w = uniform scale
in float InstanceZAngle;   // Z轴旋转角 (Simple 传 0, Rotating 传角度)
in int InstanceColor;      // ABGR packed color

uniform mat4 ProjMat;

out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    // GPU 端重建变换: translate(pos) * rotZ(angle) * scale(s)
    float s = InstancePosScale.w;
    float c = cos(InstanceZAngle);
    float sn = sin(InstanceZAngle);
    vec3 scaled = vec3(
        Position.x * s * c - Position.y * s * sn,
        Position.x * s * sn + Position.y * s * c,
        Position.z * s
    );
    vec3 worldPos = scaled + InstancePosScale.xyz;
    gl_Position = ProjMat * vec4(worldPos, 1.0);
    texCoord0 = UV0;
    // 解包 ABGR int -> vec4
    vertexColor = vec4(
        float(InstanceColor & 0xFF) / 255.0,
        float((InstanceColor >> 8) & 0xFF) / 255.0,
        float((InstanceColor >> 16) & 0xFF) / 255.0,
        float((InstanceColor >> 24) & 0xFF) / 255.0
    );
}
```

```glsl
// danmaku_billboard.fsh
#version 150
uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec4 texColor = texture(Sampler0, texCoord0);
    if (texColor.a < 0.01) discard;
    fragColor = texColor * vertexColor;
}
```

**2. 飞行方向类型的 Shader (Swinging/Cross/Butterfly)**

需要更复杂的矩阵重建, 但仍远小于上传 mat4:

```glsl
// danmaku_flight.vsh (伪代码)
in vec4 InstancePosScale;    // xyz = position, w = scale
in vec3 InstanceAngles;      // x = yaw, y = pitch, z = type-specific angle
in int InstanceColor;

uniform float TiltAngle;     // 类型常量, uniform 而非 per-instance
uniform float ExtraScale;    // 类型常量

void main() {
    // GPU 上重建: translate * scale * rotY * rotX * rotZ * rotX(tilt) * scale(extra)
    mat3 rot = rotY(InstanceAngles.x) * rotX(InstanceAngles.y)
             * rotZ(InstanceAngles.z) * rotX(TiltAngle);
    vec3 worldPos = rot * (Position * InstancePosScale.w * ExtraScale)
                  + InstancePosScale.xyz;
    gl_Position = ProjMat * vec4(worldPos, 1.0);
}
```

**3. CPU 端架构**

```java
class InstancedDanmakuRenderer {
    int quadVAO, quadVBO;       // unit quad (永驻 GPU)
    int instanceVBO;            // per-instance data (每帧 GL_STREAM_DRAW)

    void setup() {
        // 上传 unit quad: 4 vertices * (vec3 pos + vec2 uv)
        // glVertexAttribPointer(0, 3, FLOAT, ...) // Position
        // glVertexAttribPointer(1, 2, FLOAT, ...) // UV

        // instance attributes (compact layout, 24 bytes/instance for billboard):
        // slot 2: vec4 posScale  (16 bytes) -- glVertexAttribDivisor(2, 1)
        // slot 3: float zAngle   (4 bytes)  -- glVertexAttribDivisor(3, 1)
        // slot 4: int color      (4 bytes)  -- glVertexAttribDivisor(4, 1)
    }

    void render(List<CompactIns> instances) {
        // 1. 打包 -- 每 instance 仅 24 bytes (vs 旧方案 68 bytes)
        FloatBuffer buf = ...;
        for (CompactIns ins : instances) {
            buf.put(ins.tx).put(ins.ty).put(ins.tz).put(ins.scale);
            buf.put(ins.zAngle);
            buf.put(Float.intBitsToFloat(ins.color));
        }

        // 2. 上传 + draw
        glBufferData(GL_ARRAY_BUFFER, buf, GL_STREAM_DRAW);
        glDrawArraysInstanced(GL_QUADS, 0, 4, instances.size());
    }
}

// 紧凑 instance 数据, 替代原有的 Ins(Matrix4f, color)
record CompactIns(float tx, float ty, float tz,
                  float scale, float zAngle, int color) {}
```

**4. 与 MC 渲染系统的集成**

- 在 `ProjectileRenderHelper.flush()` 中, 对支持实例化的类型走 `InstancedDanmakuRenderer`,
  其余类型 (激光) 走原有 `BulkDataWriter` 路径
- 需要在 instanced draw 前后正确管理 MC 的 RenderSystem 状态
  (blend mode, depth test, cull, shader program)
- 透明度排序: TRANSPARENT 和 ADDITIVE 分两次 instanced draw, 各自设置 blend state
- `create()` 阶段不再构造 Matrix4f, 改为直接提取 position + scale (+ 角度), 构造 CompactIns

#### P5 (多线程) 与 P6 (实例化) 的关系

| 维度 | P5 多线程 buffer | P6 GPU 实例化 (压缩) |
|------|-----------------|---------------------|
| CPU 矩阵乘法 | 仍 40,000 次, 分摊到多核 | **零** — GPU shader 从压缩数据重建 |
| CPU→GPU 数据量 | 96N bytes (完整顶点) | **20~28N bytes** (压缩 instance data), **-71~79%** |
| CPU 端工作 | Matrix4f 拷贝 + 行列式 + 缩放提取 + Vector4f 乘法 | 仅提取 pos/scale/angle, 打包到 FloatBuffer |
| Draw call | 不变 (MC 批量提交) | 每种弹幕类型 1 次 instanced call |
| 理论加速上限 | ~3-4x (受核心数限制) | **10x+** (CPU 渲染工作接近零) |
| 实现复杂度 | 中 | **大** (自定义 shader + VBO/VAO + GL 状态管理) |
| 兼容性风险 | 低 (纯 Java 层) | 中 (绕过 MC 渲染系统, shader mod 兼容) |
| 覆盖范围 | 所有类型 (弹幕+激光) | 仅 billboard 弹幕 (~95% 数量) |

**建议实施顺序**:
1. **先 P5**: 短期落地, 覆盖全类型, 验证瓶颈确实在渲染阶段
2. **后 P6**: 在 P5 基础上, 对弹幕类型升级为实例化; 激光类型保留 P5 多线程路径

如果实测 P5 后渲染已不再是瓶颈 (瓶颈转移到 tick/create), 可跳过 P6。
如果 P5 仍不够 (弹幕数 > 20,000 时仍卡), 则 P6 是终极方案。

**收益**: 弹幕数 10,000+ 时渲染耗时从 ~5ms 降到 < 0.5ms
**工作量**: 大 (自定义 shader + GL 代码 + MC 渲染状态管理 + 测试)

#### 光影/渲染 mod 兼容性策略

当前弹幕渲染已与 Embeddium/Oculus 存在不兼容 (如半透明排序)。
实例化会进一步绕过 MC 标准渲染管线, 兼容性问题不可避免。

**策略**: 分层处理, 不追求所有光影都兼容。

| 层级 | 对象 | 做法 |
|------|------|------|
| 基础层 | Embeddium (开源) | 参照其代码主动打补丁, 确保基本渲染正确 |
| 基础层 | Oculus (开源) | 同上, 确保 shader pipeline 不冲突 |
| 光影层 | 各具体光影包 | 每个光影的实现不同, 需逐一测试; 不兼容的由光影侧适配 |

**注**: 当前已有的半透明排序不兼容问题, 在实例化方案中也需要一并处理。
Embeddium 开源可查, 必要时通过 mixin 注入兼容逻辑。

### P7: 其他渲染优化

- 距离 LOD: 远距离弹幕降低渲染精度或合并
- 视锥剔除: 只渲染摄像机可见的弹幕

**工作量**: 中

## 关于多线程

### Tick 逻辑: 不建议多线程

| 因素 | 说明 |
|------|------|
| Entity 非线程安全 | position, velocity, tickCount 等字段无同步 |
| Minecraft 主线程约束 | Level, AABB, BlockPos 等 API 必须在主线程调用 |
| 数据竞争风险 | onExpiry 链触发新弹幕, 共享 runtime 变量, 极难正确同步 |
| 虚拟线程 (Loom) | 适合 I/O 密集, 对 CPU 密集的弹幕物理无优势 |

### 渲染 Buffer 填充: 建议实施 (参考 Mad Particles)

**结论: 渲染阶段的并行 buffer 填充是可行且值得实施的优化。**

Mad Particles 已验证此方案: 多线程并行填充顶点数据, 粒子数破万后 4 线程有显著提升。
本项目的 FastProjectileAPI 渲染管线具备相同的并行化条件。

#### 当前渲染热路径分析

```
RenderQueue.flush(buffer)                         // ProjectileRenderHelper.java:91
  → ProjTypeHolder.type.start(buffer, list)       // 对每种弹幕类型批量渲染
      → BulkDataWriter wraps buffer
      → for each Ins in list:                     // ← 热循环, N = 该类型实体数
          ins.tex(vc)                             // 每个 Ins 写 4 个顶点 (弹幕) 或 16-128 个 (激光)
            → vc.addVertex(Matrix4f, x, y, z, u, v, col)  // BulkDataWriter.java:20
                → new Vector4f(x,y,z,1).mul(m4)           // ← CPU 热点: 矩阵*向量乘法
                → putFloat * 5 + putByte * 4               // 直接写入 ByteBuffer
```

对 10,000 弹幕, 每帧渲染阶段:
- 40,000 次 `Vector4f * Matrix4f` 乘法 (每弹幕 4 顶点)
- 10,000 次 Matrix4f 拷贝 + 行列式提取 (create 阶段)
- 40,000 次直接 buffer 写入 (24 bytes/顶点, 共 ~960KB)

#### 并行化条件验证

| 条件 | 状态 | 依据 |
|------|------|------|
| 每个实体的计算互相独立 | OK | create() 只读实体状态, tex() 只读自身 Matrix4f |
| 热路径是纯数学运算 | OK | Matrix4f * Vector4f + putFloat, 无 MC API 调用 |
| 已有直接 buffer 访问 | OK | BufferBuilderAccessor mixin 绕过 VertexConsumer 链 |
| 每个实体顶点数确定 | OK | 弹幕固定 4 顶点, 激光固定 16-128 顶点 |
| buffer 填充阶段不调用 GL | OK | OpenGL 状态仅在 RenderType setup/teardown 时改变 |

#### 实施方案

**方案 A: 分段写入同一 buffer (推荐)**

```java
// 在 RenderableProjectileType.start() 中:
void start(BufferSource buffer, List<Ins> list) {
    int totalVertices = list.size() * VERTICES_PER_ENTITY;
    VertexConsumer vc = buffer.getBuffer(renderType);
    // 预分配: 确保 buffer 容量足够 totalVertices * 24 bytes

    int threads = Math.min(4, Runtime.getRuntime().availableProcessors());
    int chunkSize = (list.size() + threads - 1) / threads;

    // 每个线程写入 buffer 的不同偏移段
    ForkJoinPool.commonPool().submit(() ->
        IntStream.range(0, threads).parallel().forEach(t -> {
            int from = t * chunkSize;
            int to = Math.min(from + chunkSize, list.size());
            int byteOffset = from * VERTICES_PER_ENTITY * STRIDE;
            for (int i = from; i < to; i++) {
                list.get(i).tex(byteOffset, backingBuffer);
                byteOffset += VERTICES_PER_ENTITY * STRIDE;
            }
        })
    ).get();

    // 更新 BufferBuilder 的 nextElementByte 和 vertices 计数
}
```

需要新增一个 `tex(int offset, ByteBuffer buf)` 方法, 将顶点直接写入指定偏移量。
ByteBuffer 的不同位置可以安全并发写入, 无需同步。

**方案 B: 线程局部 buffer + 合并**

```java
void start(BufferSource buffer, List<Ins> list) {
    int threads = Math.min(4, Runtime.getRuntime().availableProcessors());
    int chunkSize = (list.size() + threads - 1) / threads;

    // 每个线程填充独立的临时 byte[]
    byte[][] segments = new byte[threads][];
    IntStream.range(0, threads).parallel().forEach(t -> {
        int from = t * chunkSize;
        int to = Math.min(from + chunkSize, list.size());
        byte[] seg = new byte[(to - from) * VERTICES_PER_ENTITY * STRIDE];
        for (int i = from; i < to; i++) {
            list.get(i).texToArray(seg, (i - from) * VERTICES_PER_ENTITY * STRIDE);
        }
        segments[t] = seg;
    });

    // 主线程合并到 BufferBuilder
    VertexConsumer vc = buffer.getBuffer(renderType);
    for (byte[] seg : segments) {
        backingBuffer.put(seg);  // 顺序写入
    }
}
```

内存开销略高 (临时 byte[]), 但无需操作 BufferBuilder 内部偏移。

#### 阈值与降级

- 弹幕数 < 2,000: 单线程 (线程调度开销超过收益)
- 弹幕数 2,000 ~ 5,000: 2 线程
- 弹幕数 > 5,000: 4 线程
- 运行时检测: 首次渲染时 benchmark 单线程 vs 多线程, 自动选择

#### 实施注意事项

1. **BufferBuilder 非线程安全**: 方案 A 需绕过 BufferBuilder, 直接操作底层 ByteBuffer;
   方案 B 使用独立 byte[] 规避此问题
2. **PoseStack 不可共享**: 若并行化 create() 阶段, 每线程需独立 PoseStack 副本
3. **线程池复用**: 使用 `ForkJoinPool.commonPool()` 或模组初始化时创建的固定线程池,
   绝不在每帧创建新线程
4. **Ins 列表已排序**: flush 时按 ProjTypeHolder 顺序遍历, 并行化在单个类型的列表内部进行,
   不破坏类型间的渲染顺序
5. **激光类型**: DoubleLayerLaserType/PencilLayerLaserType 已在 create() 阶段预分解矩阵为
   基向量, tex() 中不做矩阵乘法, 并行收益较小; 优先并行化弹幕类型

#### 预期收益

| 弹幕数 | 单线程渲染 (估) | 4线程渲染 (估) | 加速比 |
|--------|----------------|---------------|--------|
| 1,000 | ~0.5ms | ~0.5ms (不启用) | 1x |
| 5,000 | ~2.5ms | ~1.0ms | ~2.5x |
| 10,000 | ~5ms | ~1.8ms | ~2.8x |
| 20,000 | ~10ms | ~3.5ms | ~2.9x |
| 50,000 | ~25ms | ~8ms | ~3.1x |

注: 实际加速比受内存带宽、缓存局部性、线程调度开销影响, 不会达到理论 4x。

## 新发现的优化方向 (基于 Spark Profiling)

### PA: 关闭弹幕透明排序 (已实现)

`DanmakuRenderStates.create()` 中 `sortOnUpload` 从 `type != SOLID` 改为 `false`。

- `putSortedQuadIndices` 占帧时间 9.34%, 对 3 万 quad 做 O(N log N) 距离排序
- 弹幕是小粒子, 深度排序顺序对视觉效果影响极小
- **已实现, 预期节省 ~9% 帧时间**

### PB: 减少 EntityRenderDispatcher.getRenderer() 调用 (待实施)

spark 显示 6.41%。每个实体渲染时都要通过 Map 查找对应的 Renderer。
3 万弹幕都是同一种 EntityType, 但 MC 每次都查一遍。

**可能方案**:
- 预览模式下缓存 Renderer 引用, 跳过 `EntityRenderDispatcher.getRenderer()` 查找
- 在 `OrthographicViewport.renderEntity()` 中, 对已知类型直接调用对应 Renderer
- **工作量**: 小
- **预期收益**: ~5-6% 帧时间

### PB3: Billboard 类型完全跳过 PoseStack (待实施)

第二次 spark profiling 显示 `renderDanmakuFast()` 占帧时间 **22.50%**,
其中 `ProjTypeHolder.create()` 仅 4.96%, **剩余 ~17.5% 在 PoseStack 操作上**。

**根因**: `renderDanmakuFast` 对每个弹幕做:
```
pushPose → translate(ex, ey, ez) → scale(s, s, s) → create() 取 m30/m31/m32 → popPose
```
但 billboard 类型 (Simple/Rotating/Animated) 的 create() 只从 pose 矩阵中读取
translation 列 (m30/m31/m32) + cbrt(determinant) 作为 scale。
**这些值在进 PoseStack 之前就已知** — 构造整个矩阵再读回来完全是浪费。

**方案**: 对 billboard 类型, `renderDanmakuFast` 直接计算:
```java
float tx = (float) Mth.lerp(pTick, e.xOld, e.getX());
float ty = (float) (Mth.lerp(pTick, e.yOld, e.getY()) + e.getBbHeight() / 2);
float tz = (float) Mth.lerp(pTick, e.zOld, e.getZ());
float scale = e.scale();
int color = ...;
// 直接构造 Ins, 零 PoseStack 操作
holder.accept(new SimpleProjectileType.Ins(tx, ty, tz, scale, color));
```

对旋转类型 (Swinging/Cross/Butterfly), 保持当前 PoseStack 路径。

**`ProjTypeHolder.create()` 仍占 4.96% 的原因**:
PB2 延后了 Matrix4f 构造, 但 `determinant3x3()` 仍在 create() 中执行。
`renderDanmakuFast` 做 `poseStack.scale(entity.scale())` 把 scale 编码进矩阵,
然后 `create()` 用 `cbrt(|det3x3()|)` 从矩阵反算回 scale — **编码+解码完全多余**。
`entity.scale()` 的值本来就知道, 不需要经过矩阵。

**进一步: 所有这些计算都可以移到多线程中**。
单线程循环只需收集每个弹幕的原始数据 (entity position, scale, color, type),
view transform + Ins 构造 + texToArray 全部在 ParallelBufferFiller 中并行执行。

**实施方案**:
1. 渲染循环前, 提取 view matrix 一次: `Matrix4f viewMat = poseStack.last().pose()` (只读)
2. 单线程循环收集 RawDanmaku(worldX, worldY, worldZ, scale, color, typeHolder)
3. ParallelBufferFiller 中: viewPos = viewMat × worldPos, 然后直接写顶点

对 billboard 类型, 整个 create + texToArray 链变为:
```
// 并行阶段, 每个线程:
float vx = viewMat.m00*wx + viewMat.m10*wy + viewMat.m20*wz + viewMat.m30;
float vy = viewMat.m01*wx + viewMat.m11*wy + viewMat.m21*wz + viewMat.m31;
float vz = viewMat.m02*wx + viewMat.m12*wy + viewMat.m22*wz + viewMat.m32;
// 然后 4 个顶点: (vx ± scale*0.5, vy ± scale*0.5, vz) → writeVertex
```
零 PoseStack, 零 Matrix4f 对象, 零 determinant, 纯标量运算。

对旋转类型 (Swinging/Cross/Butterfly), 需要 view matrix 参与旋转链,
可以传入 viewMat 在并行阶段做矩阵乘法。

**注意**: PoseStack 在进入 renderDanmakuFast 时已包含 view 变换 (ortho/perspective + pan + rotation)。
billboard create() 读的 m30/m31/m32 是 view-space position, 不是 world position。
提取 viewMat 后对每个弹幕做 `viewMat * vec4(worldPos, 1)` 即可得到 view-space position。

**预期收益**: ~15-20% 帧时间 (消除 PoseStack push/translate/scale/pop × N + determinant × N)
**工作量**: 中 (需要区分 billboard vs 旋转类型, 提取 view matrix, 重构收集+并行流程)

### PE: 非预览客户端渲染路径优化 (已实现)

**重要**: 之前所有 PB 系列优化只改了预览模式 (`OrthographicViewport`),
**未覆盖实际游戏中的 `ClientDanmakuCache` 渲染路径**。这是两套独立的入口。

Spark 非预览 17 万弹幕客户端热点:

| 热点 | 原占比 | 状态 | 说明 |
|------|--------|------|------|
| `ClientDanmakuCache.tick()` | **34.34%** | **PE-1 已优化** | 虚拟化弹幕整体并行 tick (ForkJoinPool, 阈值 2000) |
| `ClientDanmakuCache.renderAll()` | **16.09%** | **PE-2 已优化** | billboard 跳过 PoseStack (同 PB3), viewMat 标量运算 |
| `ItemDanmakuRenderer.render()` | 7.70% | **PE-2 覆盖** | billboard 不再经过 render(), 直接构造 Ins |
| `EraseDanmakuToClient.handle()` | **10.41%** | **PE-3 已优化** | 批量 erase 包, N 弹幕消亡 = 1 个 BatchEraseDanmakuToClient |
| `flushPreviewQueue` | 3.30% | — | buffer 填充, 后续 P6 解决 |
| `endLastBatch` | 1.73% | — | GPU 提交, 后续 P6 解决 |

**PE-1: 客户端并行 tick** (已实现)
- 虚拟化弹幕不在 world 中 (`isAddedToWorld() == false`), tick 完全自包含
- `setPosRaw` 直接赋值不走 world 索引, onHit 客户端是 no-op, 每个 mover 是独立实例
- 因此整个 `e.tick()` 可安全并行, 无需 compute/apply 拆分
- 实现: `ClientDanmakuCache.tick()` → ForkJoinPool 4 线程, 阈值 2000
- 事后单线程移除无效实体

**PE-2: 客户端渲染 PoseStack 跳过** (已实现)
- `ClientDanmakuCache.renderAll()` 中对 billboard 类型 (Simple/Rotating/Animated):
  预提取 viewMat, 直接 `viewMat × (camRelativePos, 1)` → 构造 Ins, 零 PoseStack
- 非 billboard 类型 (Swinging/Cross/Butterfly) fallback 到原始 PoseStack 路径
- 同时内联了 PB 的 renderer 缓存 (跳过 getRenderer 查找)

**PE-3: 批量 erase 包** (已实现)
- 原问题: 每个弹幕消亡单独一个 `EraseDanmakuToClient` 网络包, 万级包的分发开销占 10%
- 新建 `BatchEraseDanmakuToClient`: `int[] ids` + `long killMask` (bitset)
- `DanmakuManager.erase()` 改为缓冲到 `IntArrayList`, 在 `tickDanmaku()` / `eraseAllDanmaku()` / `SpellContainer.clear()` 末尾调 `flushErases()` 一次性发送
- N 弹幕消亡 → 1 个批量包, 预期从 ~10% 降到 <1%

### PC: 服务端碰撞检测优化 (已实现)

spark 显示碰撞检测占服务端 17.36%, 其中:
- `IEntityCache.foreach()` 11.42% — 遍历附近实体
- `EntityStorageCache.get()` 5.56% — 缓存时间戳检查

**`Level.getGameTime()` 被调用 3 万次/tick 占 5.55%**。
`EntityStorageCache.get()` 每次检查 `level.getGameTime()` 判断缓存过期。
对于同一 tick 内的 3 万个弹幕, gameTime 完全相同, 但每次都重新获取。

**实现**:
- `EntityStorageCache.get(ServerLevel, long)` 新增 gameTime 参数重载, 直接使用传入值而非调用 `sl.getGameTime()`
- `UserCacheHolder.setGameTime(long)` — tick 开始时由调用者缓存一次 gameTime
- `UserMatrixCache(ServerLevel, x, y, z, long)` — 同样接受预取的 gameTime
- `ParallelDanmakuTicker.tickAll()` 在入口处调用一次 `sl.getGameTime()`, 分发给 `cacheHolder.setGameTime()` 和 `EntityStorageCache.get(sl, gameTime)`

**收益**: 消除同一 tick 内 N 次 `Level.getGameTime()` 调用 (N = 当前弹幕数)
**预期收益**: 服务端 ~5% 帧时间

### PG: 服务端弹幕 tick 4步并行拆分 (已实现并完成语义收口)

`DanmakuProxyEntity.tick()` / `YoukaiEntity.tickDanmaku()` 占服务端 **26.74%**,
其中碰撞检测 (`ProjectileHitHelper.getHitResultOnMoveVector`) 占 **17.36%**。

**当前架构**: 原始单线程循环 (`e.tick()` 包含全部逻辑) 拆为 4 步:

| 步骤 | 线程模型 | 工作内容 |
|------|---------|---------|
| Step 1 | **并行** | 基于当前 `deltaMovement` 计算碰撞范围 (`src/dst/searchBox`) |
| Step 2 | 单线程 | `setOldPosAndRot` + `++tickCount` + `baseTick` + 方块碰撞 (`level.clip`) + 缓存目标实体数据 |
| Step 3 | **并行** | 对缓存的 `CachedTarget` 做 AABB 碰撞检测 |
| Step 4 | 单线程 | `doGraze()` + `onHit()` 回调 + `computeMove/applyMove` + 生命周期检查 + `terminate()` + 列表维护 |

**关键设计**:
- Step 3 使用 `CachedTarget(entity, boundingBox, deltaMovement)` 快照, 避免并行读取 Entity 可变状态
- Step 2 预取 gameTime 传给 `UserCacheHolder` 和 `EntityStorageCache`, 消除 N 次 `Level.getGameTime()`
- 阈值 500 弹幕, 低于阈值走顺序 fallback; 最多 4 线程
- `boolean[] removeFlag` 字段由调用方 (`YoukaiEntity`/`DanmakuProxyEntity`) 持有, `eraseAllDanmaku()` 设置后 Step 4 立即中断

**本轮审查/收口修正**:
1. `ParallelDanmakuTicker` 从一个大方法拆成 Step 1/2/3/4 与若干小辅助方法, 方便 Spark 直接定位瓶颈
2. 初始 WIP 中曾尝试把 `computeMove()` 放进并行 Step 1, 但审查发现这不成立:
   `BaseProjectile.computeMove()` 最终会走 `updateVelocity()`, 而 `ItemDanmakuEntity.updateVelocity()`
   会执行 `onTrail` 和其他可能带副作用的逻辑, 不能假设是纯数学
3. 因此 `computeMove()/applyMove()` 已移回 Step 4 主线程执行, 并恢复为与原始 `BaseProjectile.tick()`
   一致的时序: `baseTick` → 碰撞/回调 → 移动 → 生命周期/卸载检查
4. 并行路径已补回原本遗漏的行为:
   - `alterHitBox()` / graze 逻辑
   - `doGraze()` 回调改回主线程执行
   - `baseTick()` / `checkBelowWorld()` 语义
   - 移动后的未加载区块 / 非 ticking 实体清理
   - `isRemoved()` 虚拟弹幕及时从列表移除
5. `SectionCache` 现在会过滤弱加载 section 中非 ticking 的实体, 避免弹幕打到本不该参与服务端 tick 的目标

**实现文件**:
- `ParallelDanmakuTicker` (新建): 完整 4 步逻辑
- `YoukaiEntity.tickDanmaku()`: 替换为 `ParallelDanmakuTicker.tickAll()`
- `DanmakuProxyEntity.tickDanmaku()`: 同上
- 两个实体类 `eraseAllDanmaku()` 新增 `removeDanmakuFlag[0] = true`

**修复的隐患** (实现过程中发现):
1. `tickParallel` 原本是一个大方法, 现已拆成 step 级方法, 方便 profiling 与后续继续改 Step 2
2. `onHit()` 内调用 `eraseAllDanmaku` 后继续执行后续逻辑 — 在 `onHit` 后立即检查 `removeFlag`
3. 初始并行化错误地把 `computeMove()` 当成纯函数 — 已改回主线程执行, 保证 `onTrail` 等副作用顺序正确
4. 并行路径遗漏 `baseTick` / graze / 未加载区块清理 / `isRemoved()` 列表清理 — 已全部补回

**当前结论**:
- PG 这一轮已经从“代码可跑”收口到“语义基本对齐原始单线程路径, 可继续 profile”
- 但主程序员指出的真正瓶颈仍然成立: Step 2 依旧是 `per-projectile foreach(section cache)`
- 也就是说, PG 解决的是“骨架与并行拆分”, 还没解决“SectionCache 扫描方式本身”的结构性开销

**预期收益**: 服务端 tick 并行化, 3万弹幕时 Step 1/3 可 4 线程并行; 总体服务端 tick 开销预期降低 30-50%

### PD: 服务端碰撞检测分区 (待评估)

`IEntityCache.foreach()` 占 11.42%。当前碰撞检测遍历每个弹幕附近的所有实体。
已有 `SectionCache` / `EntityStorageCache` 做空间分区, 但开销仍高。

**主方向已明确**:
- 不是继续在 Step 2 里做 `per-projectile foreach`
- 而是改成 **section 级别的一次缓存 / 一次快照**

**建议方案**:
1. Step 1 (并行): 只计算每发弹幕将触及哪些 section, 用 `AtomicBitSet` / long-key set 记录 touched sections
2. Step 2 (单线程): 按 touched sections 一次性读取 `SectionCache`, 冻结为本 tick 的 section/entity 快照
3. Step 3 (并行): 每发弹幕只遍历自己命中的 section 快照, 做 AABB 碰撞检测
4. Step 4 (单线程): 保持当前回调/移动/生命周期执行顺序不变

**目标**:
- 把“每发弹幕都去扫 section cache”改成“每 tick 每个 section 最多读一次”
- 为后续主程序员提到的 `AtomicBitSet` 路线铺路

**工作量**: 中-大
**预期收益**: 服务端 ~10%, 且能显著降低 Spark 中 `IEntityCache.foreach()` 的存在感

## 当前改进状态总览

| 编号 | 方案 | 状态 | 实测效果 |
|------|------|------|---------|
| P0 | 安全锁 | **已完成** | 防止实体指数爆炸 |
| P1 | 轻量级预览实体 | 暂缓 | 工作量大, 需重构整个 tick/render 管线 |
| P2 | 空间分区碰撞检测 | **已实现后回退** | 单 target 下净负优化 (insert 4.35% > query 0.03%) |
| P3 | 实体对象池化 | 暂缓 | 与 P1 互斥, Entity 状态重置风险高 |
| P4 | 变量快照优化 | **已完成** | HashMap → Map.copyOf(), 减少小对象分配 |
| P5 | 多线程 buffer 填充 | **已完成** (见 P5fix2) | 占帧时间 7.26%, 有效但非主瓶颈 |
| P5fix | bulkWrite 崩溃修复 | **已完成** | putByte offset 语义错误导致崩溃, 改为逐顶点写入 |
| P5fix2 | 全局统一并行 fill | **已实现** | 所有类型统一 submit + 全局 joinAndMergeAll |
| PH | 渲染前置 fill + 延迟 join | **已实现** | AFTER_SKY 启动 fill, AFTER_ENTITIES join |
| P6 | GPU 实例化 | 待实施 | 可绕过 MC 渲染管线, 消除排序/查找/上传开销 |
| P7 | 其他渲染优化 | 待实施 | LOD + 视锥剔除 |
| PA | 关闭透明排序 | **已完成** | 省 ~9% 帧时间 (最大单项优化, 关闭 sortOnUpload) |
| PB | 缓存 Renderer 查找 | **已完成** | 省 ~6% 帧时间 (renderDanmakuFast 跳过 getRenderer) |
| PB2 | 延后 pose 计算到并行阶段 | **已完成** | billboard Ins 从 ~80B 降到 ~24B, create 零堆分配 |
| PB3 | billboard 完全跳过 PoseStack (预览) | **已实现** | 预览 renderDanmakuDirect: viewMat×worldPos 直接构造 Ins, 预期省 ~15-20% |
| PE-1 | 客户端并行 tick | **已实现** | 虚拟化弹幕整体并行 tick (ForkJoinPool 4线程), 原占 34.34% |
| PE-2 | 客户端渲染 PoseStack 跳过 | **已实现** | renderAll() billboard 直接构造 Ins, 原占 16.09% + 7.70% |
| PE-3 | 批量 erase 包 | **已实现** | BatchEraseDanmakuToClient, N次消亡→1包, 原占 10.41% |
| PF | 网络包拆分 | **已实现** | DanmakuManager.send() 按 2000/包拆分, 修复 8 万弹幕 1MB 崩溃 |
| PG | 服务端 tick 4步并行拆分 | **已实现并收口** | ParallelDanmakuTicker 已拆成 step 方法, 恢复主线程副作用/移动时序/卸载语义 |
| PC | 服务端 getGameTime 缓存 | **已实现** | EntityStorageCache/UserCacheHolder/UserMatrixCache 统一接受预取 gameTime, 消除 N 次调用 |
| PD | 服务端碰撞分区优化 | 待评估 | 预期省服务端 ~10%, 工作量中-大 |

## 提交历史

| Commit | 说明 |
|--------|------|
| `944f4223f` | P5: ParallelBufferFiller + 6 种弹幕类型 texToArray |
| `a6438353c` | P5fix: 异常降级 fallback + 移除未使用 import |
| `cca8ee3b1` | P2 + P4: SpatialHash + Map.copyOf() |
| `d7391a18c` | P5fix: bulkWrite 逐顶点写入修复崩溃 + 诊断日志 |
| `ed6d06e56` | chore: 移除临时诊断代码 |
| `5488a5406` | PA: sortOnUpload=false + P2 回退 SpatialHash |
| `4cf0a819f` | PB: renderDanmakuFast 缓存 Renderer |
| `6712c4c94` | PB2: billboard Ins 延后 pose 计算, 零堆分配 |
| `dc025992d` | GC: ParallelBufferFiller byte[] 缓冲复用 (效果有限) |

## 优化效果估算 (3 万弹幕基准)

基于 spark profiling 修复前占比, 各优化的帧时间节省:

| 优化 | 消除的热点 | 原占比 | 预期帧时间节省 |
|------|-----------|--------|--------------|
| PA 关闭透明排序 | putSortedQuadIndices | 9.34% | **~9%** |
| PB 缓存 Renderer | EntityRenderDispatcher.getRenderer | 6.41% | **~6%** |
| PB2 延后 pose 计算 | ProjTypeHolder.create 中的 Matrix4f 构造 | 5.20% (部分) | **~3-4%** |
| P5 多线程 buffer | flushPreviewQueue 中矩阵乘法 | 7.26% (部分) | **~2-3%** |
| P4 Map.copyOf | DataDrivenTrailAction HashMap 分配 | (GC 压力) | GC 改善 |
| P2 回退 SpatialHash | SpatialHash.insert 负优化 | -4.35% | **+4%** (回退收益) |
| **合计** | | | **~24-26%** |

### 实测结果 (i5-12400, 7GB 堆)

**32k 弹幕**: 优化后 **45-50fps** (优化前 ~30fps, 提升 ~60%)
- 无 GC 帧: 6-7ms avg, 8-9ms max
- GC spike: 每 ~5 秒一次, 紧邻两帧 max 22ms
- CPU 利用率 ~25% (4 线程满载)

**170k 弹幕**: 1-3fps, avg 46.6ms, GC max 660ms
- 此量级下瓶颈已转移到 Entity.tick() 和 GC
- byte[] 缓冲复用对 GC 改善不明显 — 主要 GC 来源是 Entity 系统内部
  (Vec3, AABB, DeltaMovement 等每 tick 分配的临时对象, ~5-10MB/帧)
- 170k 实体超出当前架构的合理范围, 需 P1 (轻量实体) 或 P6 (GPU 实例化) 才能根本改善

### GC 问题根因分析

32k 弹幕每帧堆分配估算:
| 分配源 | 对象数 | 大小 | 总量 |
|--------|--------|------|------|
| Entity.tick() 内部 Vec3/AABB | ~32k | ~32-64B | **~1-2 MB** |
| Ins record (billboard 类型) | ~32k | ~24B | **~0.8 MB** |
| Ins record (旋转类型, 含 Matrix4f) | 比例较小 | ~80B | ~0.1 MB |
| byte[] 缓冲 (已复用) | 0 | 0 | **0** |
| ArrayList grow/copy | 偶发 | 变量 | 小 |

**主要 GC 来源是 MC Entity 系统本身** (Vec3/AABB/DeltaMovement 等不可变对象的每 tick 分配)。
这是架构层面的限制, 只有 P1 (用轻量 struct 替代 Entity) 才能根本解决。
P6 (GPU 实例化) 可以消除 Ins 对象分配, 但不解决 tick 侧的 Entity 分配。

### 当前优化已触及的天花板

- **帧率天花板**: 32k 弹幕 ~50fps (无 GC 帧 6-7ms) — 受 MC 主线程串行限制
- **GC 天花板**: Entity 系统每 tick 的临时对象分配无法在当前架构下消除
- **弹幕量级天花板**: ~5 万是当前架构的实用上限, 17 万需要根本性重构 (P1/P6)

### 170k 弹幕压力分解 (待 PB3 实施后重新测量)

| 指标 | 当前状况 | 瓶颈所在 | 解决方案 |
|------|---------|---------|---------|
| 渲染逻辑 | renderDanmakuFast 22.5% (PoseStack 冗余 ~15%) | pushPose/translate/scale/popPose × 17 万 + determinant × 17 万 | PB3: 跳过 PoseStack, 提取 viewMat 并行 transform |
| 客户端 tick | BaseProjectile.tick() 8.4% | Entity 物理 tick: position/mover/expiry | P1: 轻量 struct 替代 Entity |
| Buffer 上传 | endBatch → drawWithShader 4.2% | 170k×4 顶点 ×24B = 16MB/帧 GPU 上传 | P6: GPU 实例化, 降到 ~3.4MB instance data |
| 服务端 | 碰撞检测 17.36%, getGameTime 5.55% | 每弹幕 tick 遍历附近实体 + 时间戳检查 | PC/PD: 缓存 gameTime + 碰撞分区优化 |
| GC 压力 | 32k: 每 5s 22ms spike; 170k: max 660ms | Entity.tick() 内 Vec3/AABB 临时对象 (~5-10MB/帧) | P1: 轻量 struct 消除 Entity 内部分配 |

### determinant3x3 优化的历史说明

`SimpleProjectileType.create()` 中 `cbrt(|det3x3|)` + `set3x3(identity*scale)` 的原始目的:
用一次行列式计算从 PoseStack 矩阵中提取 uniform scale, 避免反解旋转分量。
在单线程 PoseStack 流程中这是合理的优化。
PB3 跳过 PoseStack 后, scale 直接从 `entity.scale()` 获取, 此 trick 不再需要。

下一步实施路线:
1. ~~**PB3 跳过 PoseStack**: 已实现 (预览模式)~~
2. ~~**PF 网络包拆分**: 已实现~~
3. ~~**PE-1 客户端并行 tick**: 已实现 (虚拟化弹幕直接整体并行)~~
4. ~~**PE-2 客户端渲染 PoseStack 跳过**: 已实现 (renderAll billboard 直接构造 Ins)~~
5. ~~**PE-3 批量 erase 包**: 已实现 (BatchEraseDanmakuToClient)~~
6. ~~**P5fix2 全局统一并行 fill**: 已实现~~
7. ~~**PH 渲染前置 fill + 延迟 join**: 已实现~~
8. ~~**PG 服务端 tick 多线程拆分**~~: 已集成并完成语义收口, 当前可继续基于 Spark 定位 Step 2 热点
9. ~~**PC 服务端 getGameTime 缓存**~~: 已实现 — `UserCacheHolder.setGameTime()` 每 tick 一次,
   `UserMatrixCache(sl, x, y, z, gameTime)` / `EntityStorageCache.get(sl, gameTime)` 接受预取值
10. **PD 服务端碰撞分区优化**: 下一步优先做 section 级缓存/快照, 考虑 `AtomicBitSet`
11. **P6 GPU 实例化 (shader)**: 大工程, 服务端优化完成后再启动
12. **P1 轻量实体**: 解决 GC 根因, 与 P6 配合可支撑 10 万+

主程序员评估: 不搞 shader 的情况下 17 万弹幕 60fps 渲染侧问题不大,
当前瓶颈在服务端和 GC, 应先解决这些再启动 shader 大工程。

### 服务端多线程 tick 拆分设计 (PG)

`BaseProjectile.tick()` 客户端服务端写在一起, 拆分后两边共用同一套 API。

已暴露的 API:
- `BaseProjectile.computeMove()`: 当前**不再被视为通用可并行 API**, 因为 `updateVelocity()` 可能触发 `onTrail` 等副作用
- `BaseProjectile.applyMove(ProjectileMovement)`: 应用到 entity 状态, 须单线程

客户端虚拟化弹幕不需要拆分 (不在 world 中, 整个 tick 可并行),
服务端弹幕需要 4 步拆分 (碰撞检测涉及 Level API, 不线程安全)。

#### ParallelDanmakuTicker 实现 (已集成, 并完成一轮审查修正)

核心类: `ParallelDanmakuTicker.java` — 公共并行 tick 工具类, 替代 `YoukaiEntity` 和 `DanmakuProxyEntity`
中相同的 `tickDanmaku()` 逻辑。

**入口**: `tickAll(sl, allDanmakus, temp, toBeSent, removeFlag, cacheHolder, self)`
- 阈值 `PARALLEL_THRESHOLD = 500`: 低于此数走顺序路径, 避免线程调度开销
- 阈值以上走 4 步并行拆分, ForkJoinPool.commonPool(), max 4 线程

**4 步拆分**:
1. **Step 1 (多线程)**: 仅根据当前 `deltaMovement` 计算 `src/dst/searchBox`
   - 不再在此阶段调用 `computeMove()`
   - 原因是 `updateVelocity()` 可能触发 `onTrail` 等副作用, 不满足“纯数学”假设
2. **Step 2 (单线程)**: `setOldPosAndRot` + `++tickCount` + `baseTick()` + `level.clip()` + 实体碰撞数据缓存
   - `IEntityCache.foreach()` 获取候选实体, 快照为 `CachedTarget(entity, boundingBox, deltaMovement)`
   - 快照数据不可变, 可安全在多线程中使用
3. **Step 3 (多线程)**: 用缓存的 `CachedTarget` 做碰撞判定
   - `checkHitCached()` 线程安全版本, 不直接访问 Level 实体
   - graze 只在此阶段计算结果, 真正的 `doGraze()` 回到主线程执行
4. **Step 4 (单线程)**: `onHit()` 回调 + `computeMove()`/`applyMove()` + lifetime 检查 + `terminate()` + 列表维护

**审查后的额外修正**:
- 并行路径补回 `alterHitBox()` / graze / `baseTick()` / 未加载区块清理 / `isRemoved()` 列表清理
- `SectionCache` 过滤弱加载 section 中非 ticking 实体
- 当前 Step 2 仍然是 `per-projectile foreach`, 所以真正的结构性优化要放到下一步 PD

**与 PC 的协同**:
- 入口处调用 `cacheHolder.setGameTime(sl.getGameTime())` 缓存 gameTime
- `EntityStorageCache.get(sl, gameTime)` 预热缓存
- `UserMatrixCache(sl, x, y, z, gameTime)` 使用预取值

**异常处理**:
- Step 1 失败: 整体回退到顺序路径
- Step 3 失败: 跳过碰撞判定 (碰撞结果全为 null), 不影响后续主线程移动

Mover 线程安全性:
| Mover | 安全? | 原因 |
|-------|-------|------|
| RotateMover, RectMover, BezierMover, PolarMover, ZeroMover | **安全** | 纯数学, 无共享状态 |
| FixedDirMover | 条件安全 | 取决于包装的 mover |
| CompositeMover | **不安全** | move() 修改自身 index 字段 (但每弹幕独立实例, 无跨实体竞争) |
| AttachedMover, AttachedFreeRotMover | **不安全** | 读 owner entity 共享状态 (position/rotation) |

**审查后更新**:
- 上述结论只适用于“读取当前 `deltaMovement` / 做只读碰撞检测”
- **不再把 `computeMove()` 视为通用可并行 API**
- 如果后续还要把 mover 逻辑重新移回并行阶段, 必须先把 `updateVelocity()` 中的 `onTrail`/runtime 副作用剥离

## 合理的弹幕数量级参考

| 符卡复杂度 | 每 tick 新增 | 峰值存活 | 内存 (Entity) |
|-----------|------------|---------|--------------|
| 简单 (fairy) | 1-5 | 50-200 | < 1MB |
| 中等 (cirno) | 3-10 | 200-800 | 1-4MB |
| 复杂 (reimu) | 20-60 | 500-2000 | 2-10MB |
| 极端 (sakuya) | 50-100 | 2000-5000 | 10-25MB |
| 危险 (>5000) | >100 | >5000 | >25MB |
| 安全锁触发 | — | 50,000 | ~250MB |
