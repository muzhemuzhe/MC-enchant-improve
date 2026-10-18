# 中型遗迹正式落地 · Differentenchant0.6.2

2026-10-18。已接受的23×18×20中型研究馆正式作为`differentenchant:abandoned_workshop`地表遗迹实现；不再设计建筑外形。小屋固定13×11×10，地下模板12×10×6。本轮只做遗迹相关验证，没有运行附魔概率、菜单、UI、升级系统完整旧回归。

## 实现与资源

- 原生结构：`src/main/resources/data/differentenchant/structure/ruins/workshop.nbt`。尺寸、所有方块坐标和状态与用户接受版本一致，几何SHA-256为`b7fcb92b0cba37b8564168fa4d095088b4489da73ac396651dfa5be85678f819`。
- 原点为模板(0,0,0)、Y0基准，默认正门向北，门位于(11,1,1)，原生Rotation围绕原点旋转；保存Piece位置/旋转。完整原点、楼层及内容见[RUINS.md](../../RUINS.md)。
- 沿用`worldgen/structure/abandoned_workshop.json`→`differentenchant:ruins/workshop`和`worldgen/structure_set/abandoned_workshop.json`。原StructureType/PieceType已接线，本轮没有重复注册。中型36/16区块，小型20/8；地表森林/白桦森林/平原标签、九点选址、最大坡差4和有限6格地基支撑均保留。
- 一层3普通箱与唯一原生僵尸笼；二层缺中心附魔台与两角紫水晶的机器、档案/材料翼、4组雕纹书架/2研究箱；三层书房/床/家具、2组高等级书架，1主study奖励箱和2辅助research箱。
- 本轮只改两只三层辅助箱的LootTable，原表内容不改：书/随机附魔书/青金石/经验瓶/少量铁/低概率铭纹石；主奖励保留1/50零能量普通核心，没有谐调核心或新占位物。全部箱8只、书架6组，各书架2–4本真正随机附魔书。
- `tools/ruins-locks.json`记录已接受几何指纹；`tools/verify-ruins.mjs`解析真实压缩NBT校验几何、内容引用和自然放置接线。生成脚本自动核对，Node.js不成为玩家或Gradle构建依赖。

## 真实运行结果

| 执行 | 结果与范围 |
| --- | --- |
| 初次build命令 | 3s编译失败：测试中Gson addProperty重载与泛型computeOnServer联合推断不明确；改为显式String局部变量。[失败原日志](compile-initial-failed.log)保留 |
| build + 指定JUnit + runGametest，`-PruinsOnly` | 12s成功。2项地形JUnit、2项项目遗迹服务端测试+1项原版测试。[日志](build-server.log)、[JUnit XML](unit-results.xml)、[服务端XML](server-results.xml) |
| runClientGametest，`-PmediumReview` | 2m41s成功。真实OpenGL客户端四个旋转下分别开门、走21个螺旋点、进出二层两翼、进入三层并打开奖励箱；不飞、不跳。[日志](client-flat.log)、[四向结果](rotations.json) |
| runClientGametest，`-PruinsOnly` | 1m24s成功。普通主世界原生定位/生成三种放置；中型自然实例开门/三层/两翼/开箱、原生笼实际产生僵尸、磁盘保存退出重进。[日志](client-natural.log)、[原生坐标](natural-placements.json) |
| generate-ruins + verify-ruins + assemble/processGametestResources | 生成和指纹核对通过，增量打包约1s。恢复全部默认测试入口。[指纹](geometry-locks.json)、[打包日志](package.log) |

服务端验证真实模板四向旋转、门的FACING、唯一笼/实体ID、缺件机器不可组装、8箱非空loot、6书架真实存储附魔/occupied同步、楼梯头部空间、Piece保存恢复及书架序列化。原生loot表另以固定种子抽样，核对高层奖励与罕见0能量核心。没有执行本模组随机附魔机器的整套概率实验。

平地四向通过的Rotation为NONE、CLOCKWISE_90、CLOCKWISE_180、COUNTERCLOCKWISE_90，所有实际使用请求均由服务端处理。三次旋转复制走同一`RuinTraversal`助手，使用原生坐标变换，不为每个朝向编造不同路线。自然实例从门外台阶开始验证入口和内部；未声称所有随机地形上的草地到台阶都经过人工验收。

普通世界种子**20261017**：

| 放置 | 原点 | 朝向/地形 |
| --- | --- | --- |
| 小型地表 | 1688,66,1288 | NONE |
| 地下档案室 | -568,46,1368 | NONE，地面Y65 |
| 中型研究馆 | **-1672,65,-1640** | **CLOCKWISE_180，minecraft:plains** |

自然生成没有调用`/place`或人工模板命令替代。测试通过原生查找取得真实StructureStart，玩家传送到目标附近使正常区块生成完成，再检查模板与内容。中型夜间近笼等待实际出现僵尸，随后恢复白天；没有改刷怪参数、添加怪物或强制调用spawn。磁盘重进前后比较全部**8箱+6书架=14个原生方块实体的完整序列化库存**，逐项相同，而非只检查“非空”。

自然生成取景用了旁观者/夜视以便观察；没有清树、修改建筑、地形或修图。夜视不改变原生刷怪光照。日志保留测试环境Realms认证/各向异性选项提示与生成时短暂超时警告，未阻止上述检查。

## 截图

前六张来自白天晴天、空旷超平坦真实客户端（无夜视）。第七张为上述种子中原生生成实例，周围真实地形未清理。

| 要求 | 原图 |
| --- | --- |
| 正面 | [01-front.png](../../screenshots/medium-ruin/01-front.png) |
| 45度 | [02-diagonal.png](../../screenshots/medium-ruin/02-diagonal.png) |
| 侧面 | [03-side.png](../../screenshots/medium-ruin/03-side.png) |
| 一层 | [04-first-floor.png](../../screenshots/medium-ruin/04-first-floor.png) |
| 二层机器 | [05-second-floor.png](../../screenshots/medium-ruin/05-second-floor.png) |
| 三层书房 | [06-third-floor.png](../../screenshots/medium-ruin/06-third-floor.png) |
| 实际自然生成 | [07-natural-world.png](../../screenshots/medium-ruin/07-natural-world.png) |

补充：[缺件俯视](../../screenshots/medium-ruin/08-machine-details.png)、[真实主奖励箱打开](../../screenshots/medium-ruin/09-reward-open.png)、[自然实例二层](../../screenshots/medium-ruin/10-natural-second-floor.png)。原始输出名与SHA-256见[screenshots.json](screenshots.json)。

## 复现命令与包

```powershell
$env:JAVA_HOME = (Resolve-Path '.tools/jdk-25.0.4.1+1').Path
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
node tools/generate-ruins.mjs
node tools/verify-ruins.mjs
.\gradlew.bat build test --tests dev.differentenchant.ruins.RuinPlacementPolicyTest runGametest -PruinsOnly --console=plain
.\gradlew.bat runClientGametest -PmediumReview --console=plain
.\gradlew.bat runClientGametest -PruinsOnly --console=plain
.\gradlew.bat assemble processGametestResources --console=plain
```

运行包`build/libs/differentenchant-0.6.2.jar`，SHA-256：`44c669b077ce7a6dc3c5090e85d8b2d9c4071c292a39441357570978d8628341`。核对全部55份运行资源与源码字节一致，元数据id/version正确，未包含GameTest、RuinTraversal或测试模组类。源NBT完整SHA-256见[audit.json](audit.json)。旧0.6.1两个包移入忽略的`.tools/medium-final/old-libs`保留，运行产物目录只留0.6.2及sources。

生产Java、附魔/GUI/网络/升级/核心、机器外观与碰撞未改；两种已接受建筑的几何均未改变。未进行完整附魔机制回归、整合包/双客户端测试或多种子地形扫描。后续仍需试玩森林树木邻接、更多坡地入口与奖励密度，不以一个种子声称全面地形兼容；本轮不扩展新阶段。
