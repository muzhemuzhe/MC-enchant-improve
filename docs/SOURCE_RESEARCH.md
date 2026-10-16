# 源码研究记录 · 2026-10-16

## Immersive Engineering — 结构转换和恢复

阅读：`IETemplateMultiblock.java`，尤其 `replaceStructureBlock(...)` 和 `disassemble(...)`。

关键思想：先匹配模板；在形成时将原方块替换为带 master/slave 与朝向信息的部件；记录可恢复的旧状态；拆解时按朝向重新摆回原方块；防止拆解引起额外递归。它是较复杂的 NeoForge 工业模组，**本项目只学习分层设计思路**，不照搬类或代码。

- https://github.com/BluSunrize/ImmersiveEngineering/blob/master/src/main/java/blusunrize/immersiveengineering/common/blocks/multiblocks/IETemplateMultiblock.java

## Meridian — 原版附魔系统覆写

阅读：`EnchantmentTableBlockMixin.java`、`MeridianEnchantmentMenu.java` 和 `design/SPEC.md`。

关键思想：通过注入原版 `EnchantingTableBlock#getMenuProvider` 使用自定义菜单；菜单逻辑继承 `EnchantmentMenu`，可以保留基本槽位等机制；概率计算独立于菜单和 GUI；仍从游戏注册表取附魔。兼容性的成本之一是任何其他修改同一 menu provider 的模组可能冲突。

**Meridian 的源码目标是 Fabric 1.21.1，不应当把它的 API 名或 Gradle 构建方式原样用于 26.1.2。**

- https://github.com/rfizzle/meridian/blob/main/src/main/java/com/rfizzle/meridian/mixin/EnchantmentTableBlockMixin.java
- https://github.com/rfizzle/meridian/blob/main/src/main/java/com/rfizzle/meridian/enchanting/MeridianEnchantmentMenu.java
- https://github.com/rfizzle/meridian/blob/main/design/SPEC.md

## Fabric 26.1.2 官方文档

26.1 改为无混淆官方名称，Java 25，新的 `net.fabricmc.fabric-loom`。26.1 的渲染层/Model Loading API 经历较大变化，别复制旧教程的渲染代码。在实际实现时使用当前 26.1.2 文档与依赖源码核实。

- https://fabricmc.net/2026/03/14/261.html
- https://github.com/FabricMC/fabric-docs/blob/main/versions/26.1.2/develop/blocks/block-entity-renderer.md
- https://fabricmc.net/develop/template/

## 第一轮实际核对的 26.1.2 接口与构建（2026-10-16）

官方元数据与 example-mod `26.1.2` 分支确认：Minecraft 26.1.2、Loader 0.19.5（stable）、API 0.155.3+26.1.2、Loom 1.18.4（stable）、Gradle 9.7.1。构建没有 mappings、Yarn、modImplementation 或 remapJar。使用 JDK 25.0.4.1+1；官方 Adoptium 包元数据及 SHA-256 见 `research/jdk-package.json`。

- https://meta.fabricmc.net/v2/versions/loader/26.1.2
- https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml
- https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml
- https://github.com/FabricMC/fabric-example-mod/tree/26.1.2
- https://github.com/FabricMC/fabric-docs/blob/main/versions/26.1.2/develop/blocks/first-block.md
- https://github.com/FabricMC/fabric-docs/blob/main/versions/26.1.2/develop/blocks/block-entities.md

除官方教程外，还检查了实际解析的游戏 JAR 的 javap 签名、Loom 反编译源码及 Fabric API sources JAR：

| 接口 | 实际用途 / 注意事项 |
| --- | --- |
| `Identifier`, `ResourceKey`, `Properties.setId` | 先提供注册 key，再构造技术方块；没有沿用旧 ResourceLocation 名称。 |
| `UseBlockCallback` | 主手交互回调；两手空、潜行、非旁观、允许建造时，服务端尝试形成。 |
| `FabricBlockEntityTypeBuilder` / `BaseEntityBlock` | 技术部件实体、codec、静态 JSON 模型和服务端 ticker；不实现 BER。 |
| `saveAdditional(ValueOutput)` / `loadAdditional(ValueInput)` | 通过 BlockState、BlockPos、UUIDUtil、CompoundTag 的 CODEC 保存原状态、原实体、身份和位置。 |
| `BlockEntity.preRemoveSideEffects` | LevelChunk 替换方块并移除旧实体前触发，覆盖玩家破坏、爆炸、外部替换等路径。不沿用旧版 onRemove。 |
| `BlockState.getDrops(LootParams.Builder)` | 转用原块战利品表，保留工具和爆炸参数；不另外手动生成物品。 |
| `ServerPlayerGameMode.destroyBlock` | 移除前捕获原实体，移除后按工具与创造模式决定掉落，测试调用真实入口。 |
| `hasChunkAt` + `UPDATE_CLIENTS / UPDATE_KNOWN_SHAPE` | 全部区块加载预检查，批量写入后通知已加载邻居，不申请 chunk ticket。 |
| `net.fabricmc.fabric.api.gametest.v1.GameTest` | 当前 Fabric 注解、fabric-gametest entrypoint，测试模组独立于发布 JAR。 |
| `FabricClientGameTest` / `TestWorldSave.open` | 真实渲染客户端按键和网络形成，关闭世界后磁盘重进、拆解、截图。 |

依赖源码模块：GameTest API `4.0.18+867b87624c`、Client GameTest API `5.1.0+0a5283664c`。客户端测试接口标记 experimental，仅用于开发验收。

发现该游戏版本 GameTestHelper.relativePos() 使用 rotation+180 的转换，在本次未旋转模板中并不与 absolutePos() 互逆；掉落断言改为真实世界 AABB 查询，没有为适应测试修改掉落逻辑。临时教程与反编译源码仅保存在忽略目录 .tools/，不随源码提交。
