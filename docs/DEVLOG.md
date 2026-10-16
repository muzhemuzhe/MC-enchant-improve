# 开发记录

## 2026-10-16 · 第一轮 / M0：官方接口研究与工程基线

初始目录仅含任务文档，没有 Git 仓库或 Java 工程。先保留文档，依据 Fabric 官方 `fabric-example-mod` 的 `26.1.2` 分支建立最小工程；这是首次建项，不迁移旧版映射配置。

- 官方 26.1 公告明确要求 Java 25、无混淆插件 `net.fabricmc.fabric-loom`，使用普通 `implementation`。不声明 mappings，不使用 remapJar。
- 2026-10-16 查询官方元数据：MC 26.1.2，稳定 Loader 0.19.5，API 0.155.3+26.1.2；模板 Gradle 9.7.1。选 Loom 稳定版 1.18.4（同一官方 Maven 元数据可用版本），避免模板的 1.18-SNAPSHOT。
- 系统 PATH 为 Temurin 17.0.20；从 Adoptium 官方 API 获取独立 Temurin JDK 25，验证 SHA-256，放入忽略目录 `.tools/`，不修改系统 Java 或 Git 用户配置。
- 将继续检查依赖源码中的注册、交互、方块移除、BlockEntity ValueInput/ValueOutput 和 GameTest 接口，再实现。

来源：
- https://fabricmc.net/2026/03/14/261.html
- https://meta.fabricmc.net/v2/versions/loader/26.1.2
- https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml
- https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml
- https://github.com/FabricMC/fabric-example-mod/tree/26.1.2
- https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse

构建和游戏验收结果：待实际执行后追加，不将编译成功视为游戏验收。

## M1：形成/拆解结构设计（实现前）

选择独立纯 Java 模板/校验器，加一个技术部件方块及其持久化 BlockEntity。18 格各自持有原状态、原 BlockEntity 数据、控制器、局部坐标和机器 UUID；控制器额外保存完整快照。理由：仅在 controller 保存数据时，跨区块 controller 卸载或被破坏会使其他部件失去恢复依据。

服务端先只读验证全部已加载格位，再执行不通知邻居的替换；全部快照写好后统一通知。形成期间使用按维度的事务防重入。拆解仅恢复 UUID、控制器和坐标均匹配的部件，绝不覆盖外部替换的新块。技术部件不可被活塞移动，不注册可获取物品。被破坏位置使用原块战利品规则，其余部件原位恢复；创造模式由原版不掉落，爆炸沿原版破坏/战利品入口处理。每秒检查一次完整性，跳过未加载区块；若已加载格位缺失则恢复当前已加载部件，剩余部件加载后重复检查恢复，不添加 chunk ticket。

中途正常存档在服务端线程串行执行，不能插入同步形成事务；对进程崩溃/断电造成的跨区块磁盘写入不提供原子事务保证，需要明确列为限制。不是增加附魔功能的阶段。

## M2：实际实现与资源

- `StructurePattern` / `StructureValidator` 是不引用 Minecraft 的纯 Java 规则；校验先确认所有区块可用，然后才读取 18 格，失败不返回半个 plan。
- `FormationService` 快照全部旧状态及旧方块实体，再替换；初始化 18 个实体完成后才通知邻居。失败时回滚，服务端按 level 防重入。
- `MachineControllerBlockEntity` 每格保存 UUID、controller、rotation、index、local_offset、原 BlockState 和原 BlockEntity NBT；上层中心额外保存 18 格状态。技术方块及非空 UUID 表示形成态，不依赖临时内存索引。
- `DisassemblyService` 仅恢复身份/位置匹配的当前部件，跳过被破坏位置和未加载区块。常规移除入口即时拆解；每秒检查补偿未正常回调的缺失部件。恢复没有额外生成物品。
- 静态资源为原创全机几何切成 18 个 JSON 模型、72 个朝向变体；vanilla 纹理引用，无 BER、无逐帧整机绘制。`node tools/generate-models.mjs` 可复现资源。
- 临时形成态碰撞/选择框为完整格位，统一钻石级镐采集；活塞不可推动。技术部件没有 BlockItem，不提供生成机器的生产指令。
- 未实现任何经验、附魔经济、新附魔、概率算法、GUI 或菜单接管。

## M3：实际命令、失败与修复

所有下面的 Gradle 命令均在仓库根目录 PowerShell 中运行，使用当前会话的环境变量，不更改系统 Java：

```powershell
$env:JAVA_HOME = (Resolve-Path '.tools/jdk-25.0.4.1+1').Path
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
```

环境：Temurin **25.0.4.1+1**，Gradle **9.7.1**，Loom **1.18.4**，Loader **0.19.5**，API **0.155.3+26.1.2**，Minecraft **26.1.2**。服务端和客户端日志实际确认 `java 25`，`Mappings not present!` 是目标版本无混淆的正常行为。

| 实际命令 / 操作 | 结果 |
| --- | --- |
| `gradlew.bat build genSources --console=plain`（首次） | wrapper 下载 Gradle 10 秒连接超时，未运行编译。改 networkTimeout 为 120000。用 Invoke-WebRequest 下载官方分发并核对官方 SHA-256，放入 wrapper 缓存；不是跳过校验或替换镜像。 |
| `.tools/gradle-9.7.1/bin/gradle.bat build genSources --console=plain` | 编译与 4 项单元测试成功，但把 build 和 genSources 放在一起触发 Loom/Gradle 隐式任务依赖校验失败。后续分开运行。 |
| `gradlew.bat build --console=plain` | 成功，真实发布 JAR 生成，4 项 JUnit 全部通过。 |
| `gradlew.bat compileGametestJava runGametest --console=plain`（首次） | 测试 sourceSet 未包含 main Minecraft classpath，编译失败。修正 compile/runtime classpath 后编译通过。 |
| `gradlew.bat build runGametest --console=plain` | 首版 5 项项目 GameTest + 1 项原版测试全通过，验证 72 组四向形成/拆解、18 处缺失、原始掉落和 NBT。 |
| `gradlew.bat runClient --console=plain` | 普通开发客户端真实启动，LWJGL 3.4.1-snapshot、OpenGL 扩展及全部图集创建成功，没有本模组缺失模型/纹理报错；随后由本次任务停止。没有把停留主菜单算成手工游戏验收。 |
| Windows computer-use 初始化（两次） | 均返回 `failed to write kernel assets: 系统找不到指定的路径。 (os error 3)`。未操作窗口；改用已安装 Fabric 官方 Client GameTest 接口验证。 |
| `gradlew.bat genSources --console=plain`（客户端运行期间） | 反编译产生源码，但 Windows 正在运行客户端持有 Minecraft JAR，line map 写回失败。停止客户端后单独重跑成功；没有改生产逻辑绕过文件锁。 |
| `gradlew.bat runClientGametest --console=plain` | 初次通过实际客户端/服务端交互、磁盘保存退出重进、拆解；最初相机太近、`tp @s` 无执行实体导致截图未完整构图，改为测试夹具中 `tp @a`，并在改视角后等待 tick，重新运行截图。 |
| 新增生存/创造/爆炸测试后的 `build runGametest runClientGametest`（一次失败） | 生存掉落断言查错坐标，官方 GameTestHelper.relativePos 在本版本并非 absolutePos 的逆转换。检查实际游戏依赖源码后，改为真实世界坐标 AABB 查询。保留工具与掉落前置条件断言，未改变模组掉落规则。 |
| `gradlew.bat build runGametest runClientGametest --console=plain` | 成功，4 项 JUnit、7 项项目服务端 GameTest + 1 项原版测试、1 项客户端完整生命周期测试通过。 |
| `gradlew.bat runClientGametest --console=plain`（最终截图，14:04） | 成功，真实准星命中附魔台后按潜行与使用键，通过正常网络形成；保存退出再打开，18 格数据和外观保留，重进后实际玩家破坏入口拆解通过。1280×720 四张截图逐张人工视觉检查。 |
| `gradlew.bat build runGametest --console=plain`（最终加强断言，14:05） | 成功，新增空手不掉落和爆炸“原位材料+掉落物”总数上限断言；7 项项目测试及 1 项原版测试全部通过。 |
| `gradlew.bat genSources --console=plain`（最终独立运行） | 成功，6882 cache hits、0 misses，8 秒结束。 |

最终证据保存在 `docs/validation/`：build/server 日志、client 日志、JUnit XML、GameTest XML、genSources 日志。发布 JAR 另检查只有生产类，未包含 GameTest 类或测试模组入口。

客户端日志中的 `Illegal option value 0 for Anisotropic Filtering` 来自官方 Client GameTest 默认选项；Realms 的开发离线身份验证提示同样保留。两者没有导致本次本地测试失败，没有声称在线账号/Realms 通过。

## 验收清单与局限

- [x] 18 格完整形成；逐个缺失/放错不形成。
- [x] 18 个真实技术部件，连贯静态分段模型；实际截图可见差异。
- [x] 四方向 72 组形成/拆解；材料模板四向对称，视觉蓝色标记不对称。
- [x] 重复形成拒绝、原机器 UUID 不变，无新增材料。
- [x] 每个 part 破坏均恢复其余格位；生存正确工具/空手、创造、真实爆炸测试无材料复制。
- [x] NBT round trip 与客户端真正保存退出、磁盘重进后继续拆解。
- [x] `gradlew.bat build` 成功。
- [x] `gradlew.bat runClient` 实际启动；官方 Client GameTest 执行实际游戏测试和截图。
- [x] 客户端测试原点 x/z=0，18 格跨四个区块，正常保存/重进恢复通过。
- [ ] 尚未单独实测真实一侧 chunk 卸载后另一侧被破坏、再加载恢复的完整流程。实现跳过卸载区块，残余部件加载后按已加载缺口恢复；已有完整性补偿测试不等价于真实区块卸载测试。
- [ ] 尚未多人同时操作、联机延迟或与领地保护/附魔台接管模组联合验收。所有变更都串行在服务端线程；没有客户端决定状态，也没有 chunk ticket。
- [ ] 断电/进程崩溃导致跨区块部分写盘不保证原子恢复；仅正常同步事务和正常存档经过验证。不要把此原型用于重要生产存档。

截图已交付在 `docs/screenshots/`：形成前、形成后、磁盘重进后、重进后拆解。下一轮应优先完成真实分区块卸载/恢复、多人/保护兼容测试和碰撞细化，再讨论附魔行为。当前机器不接管原版菜单；拆解后的原版附魔台恢复使用。

按任务书将所有交付创建一次本地 Git 提交；使用既有用户配置 `muzhemuzhe` / `1193069167@qq.com`，不追加归属 trailer，不配置 remote，不 push。
