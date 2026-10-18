# 灯笼遗迹专项验证 · 2026-10-18

环境：Windows、JDK25.0.4.1、Minecraft26.1.2、Loader0.19.5、Fabric API0.155.3+26.1.2、Loom1.18.4、Gradle9.7.1；实际启动原生客户端，非示意图。起点3847c04工作区干净，保留全部旧提交。生产功能见[建筑/生成说明](../../LANTERN_RUIN.md)。

## 最终执行

PowerShell先配置：

```powershell
$env:JAVA_HOME=(Resolve-Path '.tools/jdk-25.0.4.1+1').Path
$env:Path="$env:JAVA_HOME/bin;$env:Path"
node tools/generate-ruins.mjs
node tools/verify-lantern.mjs
.\gradlew.bat build test --tests dev.differentenchant.ruins.LanternCachePolicyTest runGametest -PlanternOnly --console=plain
.\gradlew.bat runClientGametest -PlanternOnly --console=plain
.\gradlew.bat assemble processGametestResources --console=plain
```

| 检查 | 实际结果 | 记录 |
| --- | --- | --- |
| 模板/资源校验 | 33×33×26、八边形旋转对称、1048玻璃、305发光块、16处地毯下正常地板、无固定桶/固定库存；旧三模板完整哈希不变 | [资源](resource-audit.json)、[产物/旧模板](artifact-audit.json) |
| Gradle build +限定JUnit +服务端 | 13秒成功；2项JUnit；1项本模组灯笼GameTest +1原版always_pass；未运行旧附魔测试 | [日志](lantern-final-build.log)、[JUnit XML](junit-results.xml)、[服务端XML](server-results.xml) |
| 独立策略 | 固定种子0..9999：4–7个、无重复、候选可选/可不选、重算稳定、每桶独立loot种子；每点另查1000种子 | 同上JUnit |
| 服务端原生模板 | 四向旋转×正逆区块顺序；先拆分当前chunkBox放置，再从保存NBT重建Piece逆序放置；核对实际桶/地毯、loot引用/种子、容器序列化/真实物品、书架附魔书、楼梯头部空间、门朝向、光源和世界生成注册 | 同上服务端 |
| 最终客户端 | 1分1秒成功，只有LanternClientGameTest入口；白天平地、夜间无夜视、真实开门/步行/两段折返楼梯/三层、实际开桶与loot GUI；正常主世界自然实例和真正磁盘重进 | [最终日志](lantern-client-final.log)、[交互实录](interaction.txt) |
| 恢复默认测试入口 | assemble/processGametestResources约1秒成功，只生成元数据，不执行完整回归 | [日志](restore-defaults.log) |
| 最终JAR | differentenchant-0.7.0.jar，60份资源和源码逐字节一致（fabric版本正常展开），无测试类 | [SHA/资源审计](artifact-audit.json) |

`-PlanternOnly`只更改构建生成的测试模组入口，源码默认入口仍包含既有测试。JUnit的 `--tests` 仅选择新策略两项，不把build隐含执行的旧概率模拟冒充“最小验证”。开发时首次JUnit已通过，最终build复用其UP-TO-DATE结果，没有重复跑10万次附魔分布。

## 实际世界与可复现藏桶

平地模板CacheSeed2026101807，选5桶：局部(13,1,9)、(9,1,19)、(23,1,13)、(19,1,13)、(23,1,19)。所有其余候选保留木板并覆盖棋盘地毯。移开(13,2,9)地毯只用于切开展示，随后真实Use操作打开木桶，客户端看到原生27格界面和书、纸、青金石等loot。

普通主世界种子20261018：自然Piece原点 **(-3816,68,2536)**，CLOCKWISE_90，CacheSeed613543361155470422，选7桶，见交互实录。通过原生注册表+生成器findNearestMapStructure找到start，加载目标区域后直接检查世界生成出的Piece；**没有在普通世界调用模板place或清树、平地、补方块**。整个结构旋转、地毯覆盖、7个延迟loot桶和随机书架正常。关闭世界、保存至磁盘并重新打开后，所有桶完整序列化数据一致，并重新从保存的StructureStart读取CacheSeed核对。

## 真实客户端截图

截图1280×720。01–10为白天/夜间空旷超平坦展示，11为原始普通世界森林边缘地形。无资源包、无夜视、无后期绘图。所有图均来自Fabric ClientGameTest的真实渲染器。

| 视角 | 图 |
| --- | --- |
| 正面 | [01](../../screenshots/lantern/lantern-01-front.png) |
| 45°整体 | [02](../../screenshots/lantern/lantern-02-diagonal.png) |
| 俯视八边形/屋冠 | [03](../../screenshots/lantern/lantern-03-overhead.png) |
| 一层棋盘厅与挑空 | [04](../../screenshots/lantern/lantern-04-ground-hall.png) |
| 二层研究廊 | [05](../../screenshots/lantern/lantern-05-research.png) |
| 顶层光棱/灯冠 | [06](../../screenshots/lantern/lantern-06-luminous-crown.png) |
| 夜间外观 | [07](../../screenshots/lantern/lantern-07-night-exterior.png) |
| 夜间顶层 | [08](../../screenshots/lantern/lantern-08-night-crown.png) |
| 埋桶示意，仅移一块地毯 | [09](../../screenshots/lantern/lantern-09-hidden-barrel-cutaway.png) |
| 实际开桶loot GUI | [10](../../screenshots/lantern/lantern-10-hidden-barrel-loot.png) |
| 普通主世界自然生成 | [11](../../screenshots/lantern/lantern-11-natural-overworld.png) |

## 迭代中出现的失败

- [首次编译](lantern-compile.log)：测试用了旧形式ChunkPos构造/toLong；检查26.1.2实际类后改为 `ChunkPos.containing` / `pack`。生产类编译正常。
- [首次服务端](lantern-server.log)：玻璃数量随意断言>1400失败，真实1048；改为检查>1000且多于柱/木环梁的1.5倍，并以原生NBT脚本独立核实占比62.9%。没有为断言改结构。[修正后12秒成功](lantern-server-2.log)。
- [首次客户端44秒失败](lantern-client.log)：门和第一段楼梯已走通，测试从第二段楼梯侧面走到高边，不能自动跨一整格。路线改为先经北侧平台，从低边正常上楼，没有传送跳过楼梯或取消通行断言；建筑未改。[第二次1分1秒成功](lantern-client-2.log)，再补充磁盘重进Piece种子检查与准确CacheSeed记录，最终0.7.0客户端同样1分1秒成功。
- 两次客户端成功间建筑NBT相同；只是增加测试记录与最终版本元数据，未重设计外观。另有开发时NBT审计调用误把块数量当数组，改读 `nbt.blocks` 后正式脚本通过。

## 主要变更文件

- `tools/lantern-ruin.mjs`、`tools/generate-ruins.mjs`：独立灯笼建筑源、原生NBT/loot/worldgen输出；`tools/verify-lantern.mjs`专项审计。
- `src/main/resources/data/differentenchant/structure/ruins/lantern_sanctum.nbt`、`ruins/blueprints.json`：真实模板与候选/楼梯/灯冠/视角清单。
- 同命名空间 `worldgen/structure/lantern_sanctum.json`、`worldgen/structure_set/lantern_sanctum.json`、`loot_table/chests/lantern_cache.json`：地表接线和独立木桶奖励。
- `src/main/java/dev/differentenchant/ruins/LanternCachePolicy.java`、`RuinPiece.java`、`RuinStructure.java`：稳定子集、Piece保存种子及当前区块藏桶。没有修改其他生产包。
- `LanternCachePolicyTest`、`LanternGameTests`、`LanternClientGameTest`与测试metadata/Gradle专项开关：仅本轮用例。
- README、DESIGN_NOTES、RUINS、DEVLOG、LANTERN_RUIN及本验证目录/11图；版本号0.7.0。

## 范围与限制

未执行随机/指定附魔、GUI、36书架、共鸣核心全套；生产改动仅遗迹包和资源。小屋、中型、地下三份NBT与3847c04逐字节一致，旧loot/世界生成JSON无语义或Git差异。测试仅一普通种子自然实例和一个平地场景；没有多生物群系长期密度统计、极端坡地/林木压力测试、多人服务器压测或新最终平衡。主世界入口仍按现有有限地基策略衔接地形，后续可能需小调。建筑是本轮可玩原型，外观仍可由用户审核；本轮不继续扩展宫殿或附魔系统。
