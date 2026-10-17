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

## 第二轮 · A / 实现前检查与最小改造计划

2026-10-16 重新阅读规则、设计、日志和全部生产/测试源码，重跑 `gradlew.bat build runGametest --console=plain` 成功（`.tools/phase2-baseline.log`）。没有将上轮历史日志当作本轮验收。现有实体没有机器等级字段；右击只返回状态文字，没有附魔入口或库存。

计划保留原有形成/拆解/模型，新增纯 Java `RandomEnchantPolicy`、配置、注册表适配器和服务端 `EnchantmentMenu` 子类。沿用原版菜单类型、两个槽、三项选项、客户端界面、同步/归还物品机制；仅形成机器打开该子类，普通附魔台不变，无 Mixin。门槛等级与实际扣除等级明确分开，保持原版 1/2/3 青金石及等级消耗。等级和概率系数来自服务端配置，开发命令仅供管理员调整控制器等级并持久化。

先实现并证明完整合法支持集，再运行固定种子抽样，最后接游戏。种类权重采用原注册权重的可调幂次，等级使用对归一化等级的指数倾斜，两者独立；多重附魔逐次剔除全部已选附魔的互斥候选。候选只从当前 `in_enchanting_table` 标签中读取，检查物品 primary/support、treasure/curse 和自定义禁用标签，不列举原版附魔名。

书架仍用原版 BOOKSHELF_OFFSETS、power provider/transmitter 标签；自机部件在中间通道中特判为可传导，否则形成后的实体环使下层书架永远无效。不修改全局 vanilla 标签。只查已加载区块。菜单每次提交检查完整结构、机器 UUID、距离、物品、书架/等级和费用；服务器缓存确定性选项，世界持久化私有盐 + 玩家原版附魔种子用于避免关闭/重开/拆装机器免费重抽。客户端只收到原版费用和一个提示，不收到完整抽取结果或私有盐。

B–D 实际完成后的小范围 UI 调整：若直接沿用 vanilla MenuType，无法添加同步的 tier/shelves（原版客户端没有对应 DataSlot）。因此注册专属 MenuType，菜单和画面仍分别继承原版 EnchantmentMenu/EnchantmentScreen，增加 tier/shelves 同步与动态标题，随后补充报价版本和外观种子同步（见 E）；不访问私有字段、不添加 Mixin、不影响未形成附魔台。4 项原结构单元测试与 5 项概率测试成功；真实注册表钻石剑 6 组×100,000 抽样成功，详见 ENCHANTING_BALANCE.md。

## 第二轮 · B–D / 独立算法与模拟结果

- 实际顺序为先完成 policy/配置及单元测试，再添加真实注册表适配与 600,000 次模拟，最后接菜单。没有把概率公式塞到 Mixin/GUI。
- 种类原生权重的幂次与归一化等级的指数倾斜分开配置；低 T、零 B 也保留每个合法候选全部合法等级。解析支持集、增益单调性和异常值边界均有测试；有限抽样只作补充。
- 固定真实钻石剑、选项三、seed=20261016，T=1/3/5 × B=0/15，每组 100,000；另有纯 Java 合成池 3 组×100,000 并逐次重放验证。没有用随机客户端存档的 seed 作为模拟 seed。
- T1/B0 首个多等级附魔恰为最高级 4.075%，T5/B0 为 37.796%；T1/B15 为 19.582%。首个原始权重<=2 的比例分别 21.848%、29.143%、27.093%。这些不是“锋利 V 总概率”，完整统计口径和逐等级表在 `ENCHANTING_BALANCE.md`。
- 实际支持集从 in_enchanting_table 来，排除 treasure、curse 和可扩展 excluded_from_random 标签。非书物品检查 primary/support、组件与已有附魔；选择中检查所有已选附魔的双向互斥，无原版名称白名单。

## 第二轮 · E / 游戏接入与正确性

- 新的 MenuType 使用原版双槽/三选项/提示/青金石图标/书动画/背包交互和关闭归还机制；普通附魔台不变。原有静态模型文件未修改。T/B 动态标题为唯一视觉扩展。
- controller 新增持久化 machine_tier，旧存档默认 1；配置控制默认值和上限。权限 2 命令 `/better_enchant tier <partPos> <tier>` 及 `/better_enchant reload` 可用于比较；只查已加载整机，不自动载入区块。
- 每名玩家各自的槽和报价。完整结果只由服务器抽取并缓存；客户端同步费用、一个真实附魔提示、T/B、外观 seed 和报价版本。屏幕 5/15/30 是需要达到的等级，实际花 1/2/3 等级和青金石，tooltip 明确显示；创造免费。
- 为避免上下文更新与网络到达间的旧点击误提交，补充 29 位版本+2 位选项的标准 button VarInt 编码，拆成两个 DataSlot 同步版本。版本不是抽取种子；成功、输入/书架/tier/配置/数据包变化后旧请求一律拒绝。最后实际提交重查物品、费用、合法池、互斥和机器 UUID/完整性。
- 世界 SavedData 中保存私有盐，与玩家 UUID 和原版种子混合；不使用机器坐标/UUID重抽，同物品同上下文关闭/重开/拆装机器保持报价。成功更新原版玩家 seed；不把私有盐发客户端。
- 源码核对发现新玩家未保存的 enchantmentSeed 可为 0，原版 load 会把 0 当哨兵重新生成。本轮客户端保存重进测试先捕获了预览变化错误，现首次报价前用原版零费用接口初始化哨兵，再验证玩家 seed、私有盐混合结果和报价在磁盘重进后均不变。
- 数据包成功 reload 的 Fabric lifecycle 事件使候选缓存失效；重算仍使用原随机流，提交仍重查当前标签。事件失效与旧报价拒绝有服务端测试；并未动态加载一整个外部模组整合包。

## 第二轮 · F / 确切命令与实际结果

沿用第一轮独立 JDK 25.0.4.1+1、Gradle 9.7.1、Loom 1.18.4、Loader 0.19.5、API 0.155.3+26.1.2；没有升级/重写构建映射。版本号增至 0.2.0。

```powershell
$env:JAVA_HOME = (Resolve-Path '.tools/jdk-25.0.4.1+1').Path
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
.\gradlew.bat build runGametest --console=plain
.\gradlew.bat test --console=plain
.\gradlew.bat runGametest --console=plain
.\gradlew.bat clean build runGametest runClientGametest --console=plain
# 之后补充数据包缓存失效、外观种子同步、费用 tooltip 验证，再跑最终命令：
.\gradlew.bat build runGametest runClientGametest --console=plain
```

最终命令于 2026-10-16 14:39 完成，**BUILD SUCCESSFUL in 46s**。清理后的完整构建与客户端流程此前也成功，最终修改后重新执行所有相关检查：

- JUnit：9 项通过（4 结构、5 概率/配置），包括极端合法参数无概率下溢、非法权重拒绝、种类/等级增益独立、兼容组合与确定性重放。
- 真正 Minecraft 服务端：**14 项项目测试 + 1 项原版测试，15 项全部通过**。原 7 项结构/掉落/爆炸/存档回归；1 项实际注册表模拟；6 项菜单、费用、报价、多人、书架、命令、tier 存档和兼容性测试。
- 真正 OpenGL 客户端：2 项官方 Fabric Client GameTest 都运行完成。保留原形成/保存重进/拆解；新增通过实际右击开界面、真实 container input 网络移动物品、真实鼠标点击选项、旧 button 网络包拒绝、T/B 实时同步、保存重进预览不变、40→37 经验等级、12→9 青金石、结果移回背包再保存重进仅一把带附魔剑。
- 4 张新增 1280×720 截图逐张检查：`05-random-enchanting-offers`、`06-live-machine-tier`、`07-enchanted-item-result`、`08-enchanting-cost-tooltip`。最后一张实际显示提示附魔和 3 青金石/3 等级。不是生成图或 GUI 模拟。
- 测试夹具额外注册一个带 enchantable 组件的物品和五个测试附魔；通过标签获得原版锋利及自定义合法附魔，拒绝 treasure/curse/显式禁用/不支持物品附魔。夹具只在 gametest sourceSet，发布 JAR 不包含。
- 本轮没有额外手工操作普通 runClient；实际启动和操作由 runClientGametest 完成，未声称两台独立客户端手动联机测试。

保留的问题与修复：

1. 首次 screen 注册时泛型推断不接受 EnchantmentMenu 子类画面；按原版 MenuAccess<EnchantmentMenu> 显式注册工厂后编译成功，无反射或 Mixin。
2. 测试夹具最初误用 enchantable/sword 标签；检查 26.1.2 游戏数据确认锋利 primary 为 enchantable/melee_weapon，改正夹具后通过，没有放宽生产适配规则。
3. 客户端测试最初用了旧 ClickType/handleInventoryMouseClick 名称，编译失败；检查目标 JAR 后改为实际 ContainerInput/handleContainerInput，最终编译和网络测试通过。
4. 首次真正保存重进失败暴露上述 0 seed 哨兵问题；修复后重复运行通过，未删除断言或用测试固定 seed 掩盖。
5. clean 后 GameTest 首启日志含找不到 server.properties/eula.txt 的初始提示，Fabric 测试服务端随后正常创建并完成；官方 Client GameTest 仍含 Anisotropic Filtering=0 选项报错及开发身份 Realms 提示，无本模组异常或缺失模型错误。原始日志保留，不把日志无 ERROR 作为通过依据。

证据：`docs/validation/phase2/` 包含本轮 baseline、最终全流程日志、两个 JUnit XML、GameTest XML、完整固定 seed 模拟输出。发布文件 `build/libs/better-enchant-0.2.0.jar`，SHA-256 `d3da74b17a248a136117561ddb0f064eec35a0d6822a0affaad02fd3f48eac7e`。

仍需手动/后续验证：真实单侧 chunk 卸载恢复；两台独立客户端的延迟/断线压力；满背包关闭；常用保护/改菜单模组整合；所有物品种类与实际第三方大模组的玩法平衡；崩溃/断电跨区块一致性。双服务端玩家菜单测试已经通过，但不等于以上联机压力测试。正常存档、跨四区块客户端结构、seed/tier 和结果物品保存已实际测过。

临时参数与未定机制全部在 `ENCHANTING_BALANCE.md`；没有实现指定模式、原创附魔、额外宝藏获取、升级材料、能源或新机器模型。使用原本 Git 作者/提交者，创建本地提交，不 push。

## 第三轮 · 开始 / 最小必要改造（实施前记录）

2026-10-16：重新阅读规则、设计、概率参数和实际源码。基线 build runGametest 已通过。第二轮已经缓存三个完整结果，继续沿用其候选池、概率分布、私有世界盐、玩家 seed、费用校验和版本化点击，不重写算法与机器结构。

需要的改造：保留原版容器的双槽、转移与归还语义，仅共享并重定位槽位坐标；独立 AbstractContainerScreen 绘制黑曜石/紫色/金边像素界面。新增纯 Java 渐进揭示策略和按报价版本同步的 S2C 预览快照，只发已揭示的真实前缀及是否隐藏，不泄露隐藏结果或私有种子。宽 312、高 236，适配原版最小 320×240 GUI 画布。指定模式仅显示未开放说明，不新增槽位或物品。验证将覆盖原回归测试、预览与提交一致、不同等级、断线存档和真实客户端截图。

## 第三轮 · 用户修正视觉方向（再次改动前）

用户检查后要求停止扩展紫黑 UI，改为“原版附魔台的增强版”。旧版客户端全部功能检查已经通过，但美术方向不被接受，不以测试通过代替视觉验收。保留一张旧版截图 `docs/screenshots/phase3-before/obsidian-panel.png` 作对比。

本次仅修正画面与共享几何：缩至 260×208；移除紫黑纹理、金边、独立设备面板和背包装饰。用原版附魔台资源引用绘制米灰底板、内凹槽、棕色附魔选项、绿色等级数字和书本；符文占选项正文，结果只在悬停 tooltip 中。等级放标题边，书架用槽边小字；保留低调模式标签。服务器报价、揭示策略、种子、费用和协议均不改。调整测试里的旧几何并再次启动真实客户端验收。

## 第三轮 · 最终交付与复验

最终采用原版附魔台增强方向：260×208 米灰底板、原版像素凹槽、棕色符文横条、右侧绿色等级、纸页书本。原版的淡紫悬停高亮按资源原样保留。已移除大面积紫黑背景、金边、线路和独立设备面板；不再常驻任何真实附魔名称。模式标签为低调文字，机器等级放标题边，实际有效书架数放槽下，揭示数量在 tooltip。底部 9×3 背包和快捷栏直接嵌入同一底板，无装饰侧框。

主要代码/资源清单与新旧截图对照见 [GUI_PREVIEW.md](GUI_PREVIEW.md)。修改界面 MachineEnchantmentScreen、注册 BetterEnchantClient 和 BetterEnchant；新增共享 MachineGuiLayout、纯 Java PreviewPolicy、S2C EnchantingPreviewPayload；菜单仅加槽位重定位及预览发送/匹配。翻译为 en_us/zh_cn。**没有新增 PNG，没有改机器模型，没有更改 RandomEnchantPolicy、EnchantingSeedData 或临时概率参数。** 原版资源只运行时引用，不随 JAR 分发。

同步：从服务端已缓存完整结果提取稳定前缀，只发已揭示 ID/等级和 hidden 标记。容器 ID + 版本 + 输入副本三项匹配后才展示并允许点击，跨包暂态提示同步中。完整隐藏结果和私盐不发客户端。使用原报价版本按钮、原服务端费用/合法性校验和提交；没有用于展示的第二次随机抽取。T1/2=1，T3/4最多2，T5+全部；不足时不补条目。关闭/重开/取出放回/正常保存重进的同上下文内容与顺序稳定。

实际运行（JDK 25.0.4.1+1；固定原依赖；版本 0.3.0）：

```powershell
.\gradlew.bat build runGametest --console=plain                 # 开始时基线成功
.\gradlew.bat build runGametest runClientGametest --console=plain # 新版第一次全流程成功，57s
.\gradlew.bat clean build runGametest runClientGametest --console=plain
# clean 构建和全部服务端通过，但最后客户端拾取/放回间隔落入原版双击窗口，下面修复测试后复跑：
.\gradlew.bat build runGametest runClientGametest --console=plain # 最终成功，58s，15:30:57
.\gradlew.bat build --console=plain                             # 只清理行尾空行后，最终打包成功
```

- JUnit **11 项全通过**：原结构 4、原概率/配置 5、预览/共享几何 2。纯揭示策略对 0..100 条结果和 1..10 等级检查稳定前缀、无虚构条目；几何检查 38 个槽位、三选项互不重叠并适配最小画布。
- 真实服务端 **16 项项目测试 + 1 项原版测试，17 项全通过**。保留结构/掉落/爆炸/存档/支付/多人隔离全部回归；新增 5 等级×3选项对比预览和完整提交结果、取出重插/重开顺序稳定、已附魔输出无新报价、注册表 codec 往返、错误容器/旧版本/新输入不展示旧预览，以及 50 条协议容量测试。
- 真实 OpenGL 客户端 **3 项 Client GameTest 完成**，非 GUI mock。普通右击开关、真实槽位拾取/放回、Shift 转移、数字热键；三个选项分别用实际鼠标点击并对比每条已揭示结果、实际 XP/青金石；不足资源不支付不换 seed；已附魔重放不扣费；tier 1..5 与书架 15→14 同步、旧请求拒绝；保存重进完整揭示列表及顺序不变；最终物品及费用正常落盘。
- 用测试模组真实注册物品和五个长名称附魔，实际产生 **4 条附魔结果**，tooltip 自动换行、滚轮可达末页。原版物品和真实注册池测试均保留。夹具不在发布 JAR。
- 实际 reload 中文资源，等待资源覆盖层完全关闭后，640×480 下重新检查真实装备槽点击，最终关闭正确返回 3 件附魔物品，累计 40→34 等级、12→6 青金石。
- 固定 seed 的真实钻石剑 **6×100,000** 抽样再次通过；汇总与全部 6 份明细与第二轮 **7 文件 SHA-256 完全一致**，见 validation/phase3/balance-comparison.json，证明本轮没有改变该池概率输出。

失败与处理均保留：初次编译将翻译 helper 声明为 Component 而非 MutableComponent，修正后通过；测试循环 lambda 捕获修改为 final row。Fabric TestInput 对鼠标固定 modifiers=0，Shift 测试改为显式带修饰键的原版屏幕事件，再走原版容器与真实网络，不改生产代码。中文资源 reload 的 future 完成时仍可能有淡出覆盖层，测试补等 overlay=null。小窗口的两次同槽点击过快触发原版双击收集，测试现等待超过 250ms 再放回；没有削弱断言或修改原版交互。

截图在 docs/screenshots/phase3/，旧版对照在 phase3-before/obsidian-panel.png。已查看常态、费用和真实结果、五档机器、长名称首页/末页、占位提示及中文小窗口；完整记录 18 张，其中前 4 张保留原结构回归。原始最终日志、clean 测试失败原因、3 个 JUnit XML、GameTest XML 与全部固定 seed 输出在 docs/validation/phase3/。

发布文件 build/libs/better-enchant-0.3.0.jar，SHA-256 **22f24c0fd16ba8a803360249d0376306536424083c66b2cef3cd3bd5007a10a9**。本轮仍使用官方 Client GameTest 自动操作实际客户端，没有声称人工鼠标验收；未验证两台独立客户端的延迟/断线压力、满背包关闭、非默认字体/资源包、真实大整合包冲突。正常保存重进、双服务端玩家隔离、现有机器外观和形成/拆解已经实际回归。

指定附魔、魔法石和其他扩展均未实现。使用原 Git 作者/提交者创建本地提交，不 push。

## 第四轮 · 开始 / 最小改造计划（实施前记录）

读取实际规则、设计、第三轮 GUI/菜单/协议与测试；工作区从 d5f1415 干净开始。保留原随机算法和 3×3×2 模型，只扩展菜单的服务端指定模式会话。复用第二材料槽（青金石/铭纹石），保留原槽位索引、归还语义和随机模式费用。指定候选从完整注册表的 supported_items 获取，默认排除宝藏、诅咒及专用禁用标签，绝不把随机模式候选白名单当指定池。

新增纯 Java 逐步规划/互斥/费用策略。每次加减只更新服务端有版本的规划，不改物品或扣费；输入身份/组件变化清空，铭纹石减少则撤销末尾超额步骤，机器等级变化重算费用，数据包/配置变化使报价失效。最终附魔重查所有上下文、至少30级且足够支付、铭纹石和完整结果合法性；只复制原 ItemStack 并更新 ENCHANTMENTS，统一扣费提交，不清空其他组件。指定操作（包括创造模式）仍按颗与等级支付，创造仅用于取得测试材料。

GUI 沿用已确定的原版风格，在右区加入滚动列表、加减和“附魔 / Enchant”；随机符文与真实渐进 tooltip 不变。铭纹石用原创程序化16像素图、无配方/遗迹。祛魔明确属于未来另一台独立多方块机器，本轮不加祛魔页签或功能。

## 第四轮 · 最终实现与实际验收

版本0.4.0。铭纹石 ID better_enchant:runic_stone，中文铭纹石、英文 Runic Stone，默认堆叠64、RARE名称颜色和原创16×16像素图；创造材料栏可取，实际客户端通过原版 give 命令取得。生成脚本 tools/generate-runic-stone.ps1 随源码保存；没有配方、遗迹、要塞掉落注入或原创附魔。

指定模式在原版风格右区启用：完整候选列表滚轮浏览，名称/当前→目标、按条件出现的加减号，底部实际石头和经验费用，最终按钮仅“附魔 / Enchant”。左槽/材料槽/背包坐标与第三轮一致，材料槽同时接受两类材料但按模式验证，不自动转换或丢弃。超长完整名称可 Shift+滚轮查看，超过X的模组等级有数字回退。中文、英文和测试模组6条长名称列表均有真实截图。

原随机算法、种子服务、机器模型未改。新增 DirectedEnchantPolicy（独立纯 Java）、DirectedCandidates（完整注册表 supported_items、存储组件与标签）、DirectedSession（服务端有版本规划）、两个 action/preview payload。C2S不接收目标等级/费用/完整计划；服务端自己逐步分配、重算、检查。正常最大、全部互斥、30级门槛和足够合计经验、实际石头、当前活跃菜单/所有者/维度、机器UUID、原输入对象及组件都重查；先复制原 ItemStack 并仅更新目标附魔组件，再一次支付提交。其他附魔、自定义名称、耐久、自定义组件实际保留。指定成功不旋转原随机种子。

临时费用、配置和全部状态转换详见 DIRECTED_ENCHANTING.md。每步 clamp(ceil(5 + 1.5/weight + 1.5*normalized_target - 0.5*(tier-1)),5,8)，各步合计。最低30级是门槛，不是一次扣30或要求扣后仍30。创造指定也实际支付；原随机创造规则保留。已有宝藏/诅咒保留，但默认不增加或升级它们；独立准入配置和 excluded_from_directed 标签用于后续调整。

换装备/原输入组件变化清空规划；材料减少撤销最新超额步骤、补回不自动恢复；机器切级重算合法计划、旧费用请求失效；关闭/正常断线重连清空未支付规划并由原版归还暂存物品；数据包/配置成功 reload 清空规划。所有加减及最终操作带服务端64位版本，重复加号、旧提交和关闭后请求不重复执行。

最终使用原固定版本 JDK25 / Minecraft26.1.2 / Loom1.18.4 / Loader0.19.5 / API0.155.3+26.1.2。命令与原始日志保存在 docs/validation/phase4/：

```powershell
.\gradlew.bat build runGametest --console=plain                 # 开始基线成功，13s
.\gradlew.bat build runGametest --console=plain                 # 指定单元/服务端及原回归成功
.\gradlew.bat build runGametest runClientGametest --console=plain # 原3个客户端回归成功，59s
.\gradlew.bat build runGametest runClientGametest --console=plain # 新第4个完整交互成功，1m26s
.\gradlew.bat clean build runGametest runClientGametest --console=plain # 清理后完整流程成功，1m30s
.\gradlew.bat build runGametest runClientGametest --console=plain # 最终活跃容器/维度校验后再通过，1m29s
.\gradlew.bat build --console=plain                             # 最后只更新模组描述并打包，成功
```

实际结果：

- **JUnit 17项，0失败**：原11项回归；新增6项纯指定策略测试，包括1/5颗额度、多种分配、撤销恢复、III→V和上限、已有/规划双向互斥、每步5–8、机器折扣、逐步合计和非法参数。
- **真实服务端23项，0失败**（22项目+1原版）：保留原17项，新增6项指定测试。一颗仅一步、五颗最多五步、重复ADD拒绝、多个附魔规划、30门槛与大批量不足不支付、材料变化裁剪及旧请求拒绝、已有III→V精确费用、其他附魔及CUSTOM_NAME/DAMAGE/CUSTOM_DATA完整保留、重放无二次扣费、装备身份即使内容相同也清空、切级重算、reload、关闭/重开/坏机器、多玩家隔离、切模式后随机仍可用、盾牌、移除存储组件、预存非法组合拒绝、创造实际支付；注册物品/非随机池附魔、互斥、排除标签及已有诅咒保留，codec往返和非法动作均通过。
- **真实OpenGL客户端4项全部完成**。前三项结构/保存/拆解、随机符文/渐进预览/三选项/费用/物品/种子/中英文小窗口回归保留；第四项使用实际界面点击、原版槽位与网络、真实世界保存重进执行指定全流程。
- 新客户端完整过程：从原版 give 取得5颗、实际Shift放入铭纹石槽、加减和新I→II→I、已有锋利III→V、规划多个种类，五次耗尽后全部加号隐藏。实际右键取走3颗使槽中剩2、规划裁至前2；旧提交包不扣费，实际放回后只有额度恢复。机器T1→T5五步费用 **33→25**，规划目标保持，关闭不支付、材料归还。磁盘重进计划为空，再规划；**35级支付25级及5颗后剩10级**。锋利V、耐久III、火焰附加I、抢夺I与原击退I正确共存，名称、耐久11和第三方CUSTOM_DATA标记保留。重复提交不扣费，已附魔物品随机模式拒绝，最后再次重进仍只有一件结果、0颗及正确经验。
- 额外真实模组式物品/6条附魔列表包含不在随机标签中的附魔、1,100余字符描述；完整 tooltip 首页和 Shift滚轮末页（可见 END OF REGISTRY DESCRIPTION）、列表末尾的加减和实际自定义附魔I提交均截图，不是客户端假预览。测试夹具不进入发布JAR。
- 原真实钻石剑固定seed模拟6×100,000次通过；汇总和6份逐附魔等级明细与第三轮 **7文件SHA-256完全一致**，见 balance-comparison.json。没有通过提高原 enchanting power 或改变随机候选范围实现指定模式。

本轮初次编译遇到 CreativeModeTabEvents 的旧包名，查实际JAR改为 creativetab.v1 后通过。源码核对还发现用随机 ENCHANTABLE 组件限制指定模式会错误排除盾牌；在接客户端前改用原版存储组件+supported_items并新增真实测试，未放宽随机规则。原TestInput的Shift修饰键问题继续使用已记录的测试屏幕事件办法；其他点击/滚轮/数量变化均用实际TestInput。没有删减失败断言，也没有用假客户端结果替代服务端结果。

证据：docs/screenshots/phase4/ 含7张新指定截图和1张随机回归；docs/validation/phase4/ 含基线、清理后全流程、最终全流程和打包日志、4份JUnit XML、GameTest XML及全部概率输出。逐张查看了英文规划、费用提示、中文规划、实际结果、模组长名称首页/末页及滚动列表。构建后的JAR不包含测试模组或测试附魔资源。

发布 build/libs/better-enchant-0.4.0.jar，SHA-256 **6b8369aa98151eaa44fe1b3edfec5fc646cea29311156199d2790138cfdfb2bb**。

仍需人工/整合包验证：两台独立客户端延迟/掉线压力、死亡或满背包关闭、真实大模组对特殊附魔的额外限制、非默认字体和资源包。客户端自动测试的关闭/正常保存重连与双服务端玩家隔离不能替代这些压力测试。铭纹石来源和临时折扣仍待试玩平衡，没有擅自确定长期掉落/升级经济。

祛魔决定已写入设计笔记与 FUTURE_DISENCHANTING_MACHINE.md：未来独立多方块机器、独立模型与GUI，制造更难，单条提取成书、降一级但I不降、移除选中而保留其他；诅咒/非标准/不可移除标记另做兼容研究。**当前没有祛魔页签或运行时代码**。没有开始第五阶段。继续使用原Git用户身份本地提交，不push。

## 2026-10-16 · 第四轮界面修正 · 0.4.1

阅读现有规则、设计、概率和开发记录，检查真实 Screen、共享几何、服务端 Row/规划协议与客户端测试。开始基线 build/runGametest 成功。现有服务端可以支持此次修正，无需结构改写；仅改客户端绘制、本地化与验证，槽位与命中几何保持。没有改变随机概率、种子、预览缓存、指定事务或机器外观，没有开始第五阶段。

随机选项保留原版棕纸/禁用/悬停 sprite、银河字母、青金石图标和右侧要求等级。这次资源不足也有悬停高亮与浅金像素边；红色数字/底边表示经验不足、蓝底边表示青金石不足，两项不足用双色细线，tooltip 分别解释。没有有效报价时不画假符文。指定区改为单行纸质滚动附魔池，直接显示服务端规划 target；III点加号显示IV、减号回III，没有时仅名称、分配后I。不再画箭头。计划增加用暗绿字，hover提亮；无规划时灰置减号，上限隐藏加号，预算/互斥限制灰置加号，撤销后恢复。

费用区只保留铭纹石已用/实际数量、实际经验等级总费用与“附魔”；按此次要求去掉指定“需30级”文字，现有服务端门槛保持。背包/快捷栏与两个输入槽未改，无新GUI PNG。tooltip使用原版样式，置于池/费用区下方，不遮住条目、加减或附魔按钮，悬停时会覆盖部分背包。指定宽164 GUI像素、正文最多5行，长说明默认简短，Shift+滚轮读完整注册description；随机宽180、最多8行正文，普通滚轮读全部费用与真实预览。不限制结果数、不丢掉名称，不臆造第三方效果说明。

实际执行（JDK25、MC26.1.2与原固定Fabric版本）：

```powershell
$env:JAVA_HOME = (Resolve-Path '.tools/jdk-25.0.4.1+1').Path
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
.\gradlew.bat build runGametest --console=plain                 # 基线成功，13s
.\gradlew.bat build runGametest runClientGametest --console=plain # 首轮成功，1m31s
.\gradlew.bat build runGametest runClientGametest --console=plain # 最终成功，1m31s
```

17项JUnit、23项真实服务端（22项目+1原版）及4项真实OpenGL客户端测试全部通过。客户端新增实际III/IV/III标签断言、新附魔名称/I/名称往返；灰置加号真实点击不产生新版本或超额计划，取消后其他加号恢复。原五颗多附魔、材料取放、切级重算、关闭/磁盘重连、精确支付、数据组件保存与重放拒绝均回归。随机三个选项逐个实际点击、结果/费用、等级/书架刷新、种子保存均通过，新增经验不足、材料不足及两者都不足的实际截图。原600,000次真实钻石剑模拟7份输出SHA-256与0.4.0完全相同。

第一次检查截图发现随机测试长说明仍撑大旧tooltip，因此同轮也限制其宽高并放在选项下方，最终全流程重新通过。指定长说明末页可见 `END OF REGISTRY DESCRIPTION`，完整文本未丢失。逐张检查普通随机悬停、III/IV/III、预算禁用/恢复、中文池、短tooltip和Shift末页；没有用编译或合成图替代GUI验证。

18张真实截图在 `docs/screenshots/ui-polish/`，新旧与加减对比见 [GUI_POLISH.md](GUI_POLISH.md)。原始基线、首轮/最终日志、JUnit/GameTest XML、概率哈希比较见 `docs/validation/ui-polish/`。发布 `build/libs/better-enchant-0.4.1.jar`，SHA-256 **e8a257e997714f0407972bee72048a2a89f3d5a68f6cfb5572b07af4873e1618**；JAR不含测试模组/资源。

仍需人工验证非默认字体/资源包、真实整合包和高延迟双客户端。没有声称这些场景或人工鼠标验收已通过。使用现有Git身份本地提交，不push。

## 2026-10-17 · 整机书架检测与36架收益 · 实施记录

检查发现旧扫描使用 controller 的原版 y=0/1 offsets，而机器实际位于 y=-1/0；Context 和 bookshelf_scale 又限制到32，默认书架收益15封顶。此次最小改造是抽出纯Java BookshelfScan，按结构占地生成外扩两格、同两层的80个唯一位置，适配器仅读取已加载位置的原版提供者标签，最多计36。没有改结构、碰撞体、指定30级门槛、合法池、随机源或缓存协议。

保留旧默认0..15线性增益。bookshelf_scale 改为基础收益转折点（默认15，范围1..36）；转折之后使用 bookshelf_tail_gain * ln(B / scale)，临时默认系数0.5（可配0.01..1）。实际B先封顶36。后段每架边际收益递减，默认36的等效书架因子约1.4377，仍需试玩；这不是长期数值定案。旧配置的scale=15无需迁移即可有16..36收益。计划以解析概率、固定种子模拟、整机GameTest与双层实际客户端布局验证后记录真实结果。

实现与实际验证完成：

- `BookshelfScan` 从结构坐标推导范围，得到x/z=-3..3减去3×3占地、y=-1/0的80个唯一候选。`MachineAccess` 读取原版提供者标签，不再读取传导标签，不要求空气。最多36，不把36误写为候选位置数或固定模板。
- 核对目标版源码后，将初版hasChunkAt+Level.getBlockState进一步改为getChunkNow+LevelChunk.getBlockState。前者只能保证holder/ticket存在，过渡状态下常规getChunk仍可能等待/添加ticket；最终实现只查已就绪FULL区块，没有加载请求或阻塞等待。追加真实未加载坐标测试并重新运行完整客户端回归。
- Context拒绝负数、将所有非负超额输入封顶36，配置尺度支持1..36。缺少新后段键时取0.5，旧配置不需要删除/迁移。扫描、参数、上下文和GUI同步没有32截断或15硬封顶；保留的15是基础转折点与原经验门槛配置，不是新书架上限。
- GUI仅将有效数显示为B/36，悬停说明超额无收益。现有服务器菜单每tick更新计数/报价/缓存及客户端预览；36→37保持同一版本，36→35换版本并拒绝旧请求，恢复36保持原源随机流和完整结果。没有修改随机抽取流程、候选/互斥、种子服务、预览协议、费用或指定30级门槛/GUI隐藏设计。

执行JDK25与原固定依赖，实际命令和结果：

```powershell
$env:JAVA_HOME = (Resolve-Path '.tools/jdk-25.0.4.1+1').Path
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
.\gradlew.bat build runGametest --console=plain                 # 基线通过，23s
.\gradlew.bat build runGametest --console=plain                 # 新扫描/概率/26服务端测试通过，23s
.\gradlew.bat build runGametest runClientGametest --console=plain # 首次5项真实客户端完成，1m47s
.\gradlew.bat build runGametest --console=plain                 # 追加未加载保护后27服务端通过，17s
.\gradlew.bat build runGametest runClientGametest --console=plain # 最终非阻塞适配器完整通过，1m46s
```

最终 **22项JUnit、27项真实服务端（26项目+1原版）、5项真实OpenGL客户端全部通过**。没有靠编译替代GUI验证。原结构72组合形成/拆解、爆炸掉落、存档恢复与随机/指定完整流程均回归。新增扫描测试包括上下层、触机、第二圈/角落、四个真实方向、一个物理位置只计一次、0/1/15/16/32/35/36/37/60/80原版与真实注册模组标签书架、范围外和无空气通道。未加载真实坐标扫描后仍未加载且loaded chunk数不变；纯Java另验证混合加载视图从不读取缺失位置。

概率按真实钻石剑8种合法池，固定seed20261016、选项三，T=1/3/5 × B=0/1/15/16/32/35/36/37/80，各100,000次，共 **2,700,000次**。每次校验物品/等级/互斥；逐架严格改善与非零由解析单元断言保证。原0/15六份逐级txt与0.4.1 SHA-256相同；36/37/80三个tier的全部逐级输出与总统计相同。T1首个多等级附魔达到自身最高级比例由B15的19.582%升至B36的32.421%；T5由66.128%升至74.580%，不能把它解释为锋利V概率或完美平衡。全部数据及临时参数见 ENCHANTING_BALANCE.md。

第五个客户端入口用实际世界开口式图书室：两层各18架，含触机侧墙/后墙和第二后排。截图显示真实36架与36/36界面；添加第37架不换报价，移除上层或下层各一架都变35并同步，15→16→36也实时更新；所有网络预览逐项匹配服务器快照。实际鼠标选择后结果与完整预览一致，支付3经验等级+3青金石，正常关闭不重复支付。实际磁盘重进后机器与36架、一个结果和资源保留；拆机器仍保留全部外部书架。书架布置由测试夹具放入真实世界，交互走真实菜单与网络，不声称人工逐块建造/手动验收。

10张新截图在 `docs/screenshots/bookshelves/`；基线、各次完整日志、JUnit/GameTest XML、27组模拟与哈希对照在 `docs/validation/bookshelves/`。日志按公共规则仅将本机仓库绝对路径改为相对路径。检查了世界双层结构、15/16/35/36与超额37、封顶提示、真实结果。发布 `build/libs/better-enchant-0.4.2.jar`，SHA-256 **9f38ace88c10052dd60c2df11b6ad46a5ccd0312af27e26a4ca54c3e05394902**，不含测试模组/资源。

剩余边界：真实大整合包/资源包、非默认字体、高延迟双客户端、玩家远离造成的完整区块卸载/返回压力仍需人工验证。getChunkNow保护及远处未加载坐标断言不替代整套压力测试。后段强度仍是临时可配置原型，不改长期经济，也不改碰撞、模型和存储格式。现有Git身份本地提交，本轮不push。

## 2026-10-17 · 禁用原版附魔台独立使用 · 0.4.3

起点为尚未推送的372b40a，工作区干净；保留该提交，不reset或改写历史。先读规则与最新交互、结构、菜单和测试，并跑现有构建/服务端基线。没有结构改写，只扩展既有UseBlockCallback，不引入Mixin或菜单类级封禁。

以固定Fabric interaction模块5.2.3+07b380be4c的官方源码和26.1.2真实ServerPlayerGameMode核对返回值：目标不是原版ENCHANTING_TABLE时PASS；是原版台时普通操作双端SUCCESS消费，客户端发送使用包、服务器阻止原版打开菜单。仅主手+潜行+双空手+mayBuild在服务器继续原形成服务，成功/失败都消费，失败不会回落原版界面。旁观者服务器FAIL。潜行持物精确匹配原版suppressed block use条件，PASS保留ItemStack.useOn和邻接放置；不能把所有原版台交互一律FAIL而阻断组装或正常建造。

方块注册、原配方、原生方块实体/书本、放置、破坏、战利品均保持；恢复的原版台自动适用同一禁用规则。不触及MachinePartBlock或MachineEnchantmentMenu，后者继承原版EnchantmentMenu仍可正常打开并使用。未更改铁砧、砂轮、附魔书或村民交易代码。只记录未来附魔师工作方块方向，没有注册新职业、POI、交易、升级或维度。

```powershell
$env:JAVA_HOME = (Resolve-Path '.tools/jdk-25.0.4.1+1').Path
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
.\gradlew.bat build runGametest --console=plain                 # 基线通过，15s
.\gradlew.bat build runGametest --console=plain                 # 首次新测试，旁观者替身断言失败，17s
.\gradlew.bat build runGametest runClientGametest --console=plain # 修正验证路径后全通过，1m51s
.\gradlew.bat build runGametest --console=plain                 # 最终测试源码通过，16s
```

首次失败不是通过删掉旁观者覆盖解决：核对GameTestHelper发现原版mock player覆写gameMode()固定CREATIVE，设置其gameMode组件并不能使实体isSpectator真实为true。生存/创造/冒险的原版交互与abilities测试保留，将旁观者改为真实客户端玩家验证；客户端实际旁观者右击同样没有菜单。

最终 **22项JUnit、31项服务端（30项目+1原版）、5项真实OpenGL客户端通过**。新增服务端4项用真实ServerPlayerGameMode.useItemOn入口走Fabric生产注入，覆盖双手和不同持物、无菜单按钮不能附魔/扣费、完整/不完整/副手单独/副手持物组装、重复形成不换UUID、机器菜单仍可打开、拆解后普通和失败组装均禁用；原配方matches/assemble、实际生存放置及消耗、潜行在台旁放置、钻石镐每台一份掉落、创造放置不耗及破坏不掉；铁砧和砂轮实际菜单仍打开。原附魔书、随机/指定费用、库存、网络、书架及存档回归保留。

实际客户端扩展原MachineClientGameTest：创造/生存普通右击、不完整组装、正常磁盘重进后拆解恢复、修复后生存重新组装并打开自定义界面，另有真实旁观者。监听ScreenEvents.BEFORE_INIT断言原版EnchantmentScreen初始化次数为0，检查没有闪开；发送当前库存容器的伪造附魔按钮请求后，服务器仍无附魔菜单且经验不变。其余4个客户端入口执行随机三选项、指定批量附魔、真实材料槽、正确扣费/重放拒绝、预览一致和36架布局等完整流程。没有把编译当作GUI验证，也未声称人工手动或高延迟双客户端测试。

原27组×100,000的概率输出28份文件（含汇总）与0.4.2逐文件SHA-256完全一致。新增7张截图在 `docs/screenshots/standalone-table/`，基线、首次失败、完整客户端与最终服务端日志、XML和哈希对照见 `docs/validation/standalone-table/`。日志仅按公共规则规范化本机路径。发布 `build/libs/better-enchant-0.4.3.jar`，SHA-256 **a30379b6b9612fd88e8ad16768679b3d439aaba8527fbae445af89cbc78c3710**，不含测试模组/资源。

本次只修改独立原版台交互，保留原Git身份追加本地提交，不push。真实整合包中其他模组的事件优先级、网络高延迟/掉线仍需后续联合验证。

## 2026-10-17 · 第五轮 / 实施前：共鸣核心持久库存

基于本地4cb6eaf，保留372b40a及原版独立附魔台禁用规则。`build runGametest`基线通过（16s）。最小必要架构调整：原菜单的装备/材料两槽是关闭即返还的临时库存，不能保存安装的核心。因此仅给控制器增加一个持久核心槽，菜单末尾追加共享槽（保留原38槽编号），客户端用原生容器同步物品组件。拆解在还原控制器前先清空核心槽再掉落；未加载控制器的残留部分在恢复加载后走现有完整性修复，不主动加载区块。旧存档无该字段按空槽读取。

充能仅接在随机/指定成功提交内部，沿用现有报价、种子与计划校验，不改概率模型。上限本轮固定12；只允许实际等级1变2，其余等级不变。关闭菜单不返还共享核心。纹理使用可复现的原创16×16像素脚本，配方使用原生Ingredient的书/附魔书集合。

## 2026-10-17 · 第五轮完成：共鸣核心 · 0.5.0

在动手前记录上述必要持久库存调整。核对Fabric 26.1.2官方组件文档及固定依赖的DataComponentType、ItemStack、SimpleContainer、Ingredient、CraftingMenu、EnchantmentMenu和Item.appendHoverText源码。继续同一套已确认兼容的26.1.2/JDK25/Fabric版本，没有换映射或引入旧版NBT写法。

新增普通/谐调共鸣核心、中英文、两份原创16×16像素贴图及可复现脚本。原生配方严格是 `CAC / ALA / CAC`：四铁锭、四本普通书或任意附魔书、一青金石块，产出一个0点核心；书位允许混用、多附魔书，材料正常消耗。普通堆叠1，成品默认64，无直接成品配方。

ResonancePolicy是纯Java有界能量/首次升级策略。Integer组件better_enchant:resonance_energy同时持久化与原生网络同步；控制器唯一核心槽持久保存，菜单追加索引38，不动原38槽编号。服务端在原随机/指定成功支付和输出提交内部充能：随机实付1/2/3，指定实付合计，创造均不产生。12点满转成品并只将真实等级1设2；Lv.2..5只生产，不升级。旧存档缺字段读空，异常整数计算饱和，不因解码拒绝而丢整件物品。关闭GUI不返还核心，拆解先清空再掉落，重复恢复回调和旧菜单不能再拿；未加载控制器仍等待原完整性恢复，不主动加载。

GUI在背包左侧留白布置独立槽、小进度条和数值；随机/指定同用，不加标签、不挤占原输入/列表/按钮。正常核心铁框深蓝，成品金框亮青，提示拆为短行避免英文超宽。完成有附魔粒子和升级音效，原概率与费用通过同步等级自然更新，30级服务端门槛及GUI隐藏保持。现有原版独立台禁用回调、完整18格碰撞和36架扫描未回退。

确切命令与日志在 [validation/resonance/README.md](validation/resonance/README.md)：基线16s，核心接入18s，新服务端16s；首个完整客户端编译1s失败（测试误用private Inventory.items），改为public getItem遍历；首轮38服务端+6实际客户端2m20s通过；最终生产代码/23截图的25单元、39服务端、6实际客户端2m26s通过。最后补充高等级/自定义名称和跨玩家升级旧报价断言，最终build runGametest再17s通过。失败日志保留，没有通过访问改写或删检查规避。

自动化实际覆盖16种原生书配方及结果槽消耗、0/1/2/3/5–8/批量费用、11/12与饱和、创造与失败无产能、首次和重复生产、组件网络/存档/旧存档、双玩家共享槽/Shift/重放、18部件逐个拆解、创造与真实爆炸。原27×100,000概率实验28份输出与0.4.3的SHA-256完全一致。

真实OpenGL客户端完整0→3→6、关闭/磁盘重进/跨机转移仍6、9→11→12升级2、指定批量再次成品仍2、实际存档重进、两个成品正常堆叠、控制器区块实际卸载(getChunkNow=null)/返回和拆解单份7点核心均通过。7点卸载夹具明确由服务器注入，仅测持久库存；此前升级由实际随机支付完成。截图23张在docs/screenshots/resonance，含中英文物品tooltip、可用随机选项0/6/11、升级成品和指定重复产出。

尚未覆盖两台独立客户端高延迟/断网、完整整合包、单侧区块长时间缺失时序及强制崩溃一致性；没有把服务器双玩家或编译当作这些实测。实现细节见 [RESONANCE_CORES.md](RESONANCE_CORES.md)。只追加现有Git身份本地提交，保留先前两个未推送提交，不push，不实现研究台、Lv.3+、祛魔或新维度。

## 2026-10-17 · 正式重命名 / 实施前

从本地ce00558开始，工作区干净，main比origin/main领先3个提交（共鸣核心、原版独立台禁用、36架）；不reset或回退。现有origin为ssh://git@ssh.github.com:443/muzhemuzhe/MC-enchant-improve.git，按用户要求保持仓库及remote原样。此次仅命名迁移：展示Differentenchant、ID/资源differentenchant、包dev.differentenchant、入口DifferentEnchant/ DifferentEnchantClient、配置differentenchant.properties及产物differentenchant-0.5.0.jar。不改变业务计算、GUI几何、12点容量、30级门槛、模型或碰撞。

必要路径迁移包括三个Java源集、资源/数据目录、测试夹具及配置默认资源。历史DEVLOG正文、validation原日志/测试XML、截图、prompts和官方接口研究保留，避免篡改历史；当前安装说明与功能设计文档更新。无需旧注册ID/旧配置迁移，不加别名或数据修复。清理生成构建目录以防旧类/资源混包，旧发布JAR及开发配置先归档在忽略的.tools/rename-backup内，不保留在发布目录或当前config中。用户已明确要求本次完成后push，使用原Git身份提交并推送现有远程。

## 2026-10-17 · 正式重命名完成 · Differentenchant 0.5.0

生产、单元、GameTest三个源集改为dev.differentenchant，入口DifferentEnchant与DifferentEnchantClient。Fabric/Loom ID、assets/data、注册物品/方块/BlockEntity/Menu、翻译键、三个Payload、世界私盐SavedData、能量组件、标签、配方、命令、配置及模型/物品引用统一differentenchant；自有兼容性夹具统一differentenchant_tests。根项目、archive及group更新，运行JAR为differentenchant-0.5.0.jar。原版minecraft和外部第三方标识/许可未更改。

改名前 `.\gradlew.bat build runGametest --console=plain` 16s通过。改名后 `.\gradlew.bat clean build runGametest runClientGametest --console=plain` 2m28s通过，**25单元、40服务端（39项目+1原版）、6真实OpenGL客户端入口**全部完成。新增命名GameTest核对加载器元数据、每类注册ID、Payload、SavedData、新配置实际文件及全量设置；真实客户端中英文均断言Differentenchant标题及新命名空间资源存在。

实际重新创建世界，形成/拆解、随机/指定收费与真实预览、36架动态更新、铭纹石、核心16配方组合和成功充能/转化/仅升2、保存磁盘退出重进、等级/核心状态与跨机转移、完整区块卸载/返回均回归。界面/字体/布局不动，命名后的真实截图9张见docs/screenshots/rename。没有把旧存档“不兼容”当作跳过新世界持久化验证的理由。

64份生产文件逐项对照ce00558并规范化允许的命名替换，额外业务差异0；3张PNG blob不变。JAR条目及class/JSON/properties无旧标识，元数据/入口正确，不含测试模组。27×100,000模拟的28份输出与改名前SHA-256相同。旧发布JAR均归档而非留在build/libs，两个当前测试config目录都只生成differentenchant.properties。核心容量保持原先固定12，没有编造旧版本并不存在的容量配置或改变费用。当前README同步更正“无共享库存/无生存升级”的过时文字。

日志包含clean首次运行的原生server.properties/eula默认文件提示，以及既有客户端各向异性选项/离线Realms认证提示；未发现模组缺模型、缺贴图、注册冲突、旧类加载或网络ID错误，未为隐藏日志而修改游戏。详见 [RENAMING.md](RENAMING.md)、[测试与审计](validation/rename/README.md)。历史DEVLOG、旧任务书、旧validation与截图保持原文；本机checkout文件夹及忽略缓存也不影响新运行标识。高延迟双独立客户端/整合包边界不冒充实测。

git ls-remote确认原main仍356b859；origin保持ssh://git@ssh.github.com:443/muzhemuzhe/MC-enchant-improve.git。使用原muzhemuzhe身份正常追加提交，不改历史，无额外署名，按本轮明确授权推送当前main（包含先前保留的三个本地提交）。不实施任何后续玩法。

## 2026-10-17 · 遗迹试作 / 实施前

基于88b8900、工作区干净；先读规则/命名/设计/当前源码，build runGametest基线15s通过。现有工程没有结构生成或战利品模块。此次必要新增独立ruins包及NBT模板/数据包，不改已有附魔、GUI、12点核心、原版台拦截和机器碰撞。采用目标26.1.2原生Structure/TemplateStructurePiece和数据驱动StructureSet；运行时不手写建筑逐块逻辑，仅选址、有限地基和数据标记初始化。模板用可复现的原创建筑脚本生成，保留尺寸、房间、楼梯路径清单，便于回归与后续编辑。只两种建筑：小档案遗迹（地表/埋藏两个放置版本）与三层工坊；不增加大型建筑/研究台/升级路线。本轮本地提交，不push。

## 2026-10-17 · 遗迹第一轮完成 · Differentenchant 0.6.0

核对固定26.1.2的Structure、StructureTemplate、TemplateStructurePiece、WorldGenRegion、ChiseledBookShelfBlockEntity、SpawnerBlockEntity、LootParams及原版结构/战利品JSON，沿用当前Fabric/JDK组合。新增独立ruins注册、九点噪声地形选址、原生分区块模板放置/持久化、数据标记内容初始化及最多6格地板支撑。没有监听区块加载再搭建筑，也没有添加主动区块ticket。新增注册调用以外，既有生产Java文件未改。

原创建筑脚本tools/generate-ruins.mjs可重复生成三份原生压缩NBT（DataVersion4790）、清单和数据包。两种建筑：12×10×6小档案遗迹有地表/封闭埋藏版本，无笼、两箱、研究桌椅/书架/损坏基座；20×15×15工坊有侧翼/前塔/连续木坡顶、三层纹样地面/柱子/家具、一层三普通箱和唯一僵尸笼、二层缺中心附魔台和两块紫水晶的现有机器/四组雕纹书架/两研究箱、三层书房/床/两雕纹书架/三高价值箱。两圈3×3半砖螺旋连接三层，不使用梯子或飞行。

StructureSet候选spacing/separation分别20/8、24/10、36/16区块；平原、森林、针叶林、草甸为主，地下额外兼容繁茂/溶洞。地表九点最大坡差4格、最低不低于海平面，埋藏14–32格、坡差12；不保证固定间距或旧区块补生成。三张表原生exclusion_zone避开村庄候选10区块，原因是下述真实自然生成冲突。7张模块化loot_table，附魔书查询in_enchanting_table标签；雕纹书架生成时抽2–4本放入唯一槽位并保存。铭纹石为各层低概率额外奖励，仅三层1/50普通0点核心，不产谐调核心，12点充能不动。材料与收益仅为试玩初稿。

实测发现并修复：首次GameTest错误使用ChunkPos(BlockPos)，编译3s失败后按目标API修正；首次自然生成调用书架setItem触发ProtoChunk实体尚无Level的NPE，改为原生库存列表+WorldGenRegion更新occupied属性；第二次客户端相机gamemode控制台没有目标导致死亡，修正真实玩家模式并检查存活；第三次原生村庄地基覆盖工坊笼，读取实际区块结构引用确认后添加村庄避让。失败日志均保留，没有在截图前手工补结构。定向客户端通过后发现屋顶悬空，模板补成连续坡顶并再次完整回归。

基线build runGametest15s、首次接入18s、新服务端16s通过；遗迹定向客户端1m8s通过。楼梯出口修复后，完整命令 `.\gradlew.bat build runGametest runClientGametest --console=plain` 以3m9s运行27项JUnit、42项服务端（41项目+1原版）及7个真实OpenGL客户端入口；原6入口完整回归。随后只补小建筑门廊/扶壁/屋檐资源，再执行27单元/42服务端及单独遗迹客户端，验证最终模板并保存截图；之后恢复7入口并build processGametestResources，不删除其他测试。确切用时见 [完整日志](validation/ruins/full-validation.log) 与 [最终模板日志](validation/ruins/final-template-validation.log)。27×100,000机器概率实验28份输出与0.5.0 SHA-256完全相同，未改随机/指定/GUI/核心/30级门槛/18格碰撞/原版台规则。

正常新世界种子20261017，原生定位并自然生成森林小遗迹(-184,65,1448)、地下(-568,46,1368；地面65)、工坊(-1672,65,-1640)，没有用/place或人工测试搭建冒充自然生成。森林树叶遮挡较多，最终定位同一种子的平原小遗迹(1688,66,1288)拍摄，未清树。小遗迹补了浅门廊/扶壁/屋檐，尺寸仍12×10×6。旁观者/夜视仅用于室内取景，实际结构与光源未改。出口复核发现原楼梯靠外墙、围栏封住楼层出口，内移一格并开放东侧走廊；补测真实玩家不飞不跳走完17个螺旋半格路径点，分别走出二/三层进入房间再返回。原生笼实际刷出僵尸；正常磁盘保存退出重进后书架完整物品数据逐字相同，并再次核对工坊。最终截图10张见docs/screenshots/ruins，覆盖两个小版本、外观、三层、楼梯和重进。

详见 [RUINS.md](RUINS.md) 与 [测试记录](validation/ruins/README.md)。仍需更多种子/整合包、坡地入口/树木邻接、地下发现率、狭窄楼梯和奖励密度试玩，未声称全面平衡或人工双客户端验证。旧0.5.0产物移至忽略的.tools/ruins-backup，发布目录仅0.6.0运行包与sources。使用原Git身份本地提交，不push，不扩展大型遗迹、研究台、Lv.3+、新维度或其他阶段。

## 2026-10-17 · 遗迹第二轮 / 实施前

基于e73f9ea，main保留上一轮未推送提交，工作区干净。此次只重做两种遗迹的建筑源与模板：主视觉转为白桦/苍白橡木框架、大窗、斜顶、前廊与研究内饰；石材限地基/柱脚/局部地面，保留容器、书架内容、三层功能、坏机器与可走螺旋。为避免平顶小地堡和竖直石塔，地表小屋适度增高屋顶，中型增加分段山墙、侧翼及小型采光顶；地下版独立做封闭木构档案室，不直接复制地表屋顶。模板仍用原生Structure流程，不改附魔、GUI、概率/费用、核心或原版台规则。

按用户要求简化验证：增加只影响测试资源的-PruinsOnly开关，每次细调只build+遗迹服务端/客户端入口，最终提交前才执行一次完整必要回归。大型约100×80×80宫殿仅记录主殿/侧殿/前庭/尖塔的浅色附魔文明方向，不注册第三种建筑，不实现新机制。

## 2026-10-17 · 遗迹第二轮完成 · Differentenchant 0.6.1

外墙从石砖/深板岩和铁栅改为白桦原木/木板、苍白橡木木板/去皮浅色梁柱及玻璃。地表小屋12×10×8，完整阶梯斜顶、前廊/高窗、书桌/讲台/紫水晶角；地下12×10×6独立做封闭档案室，低木梁、额外讲台、白地毯和外侧苔石保护层，不直接复制地表屋顶。工坊20×15×19保留三层主楼，左翼降为两层档案翼；高低山墙、交叉门厅顶、突出采光塔、白桦框架/大窗形成学院式轮廓，石材退为地基、柱脚、局部地面。原木梯轴、白桦半砖/栏杆沿用上一轮真实可走路径与二/三层出口。

工坊一层仍唯一僵尸笼、工作桌/分隔储物/三箱；二层仍缺中心台及两角紫水晶的坏机器、四组雕纹书架/两研究箱，补讲台、酿造台、材料桶/紫水晶靠窗角，避开楼梯；三层书房挪入主楼，保留两组雕纹书架、三高值箱、床/桌/蓝毯，补阅读讲台。小型无笼/两箱/一组雕纹书架保留。初版截图发现苍白橡木树皮偏暗及塔顶半砖留半格空隙，改浅色去皮梁柱并填实支座后冻结模板，没有转入大规模美术或新玩法。

全部生产Java对照e73f9ea无改动，所有战利品表、StructureSet/Structure与生物群系标签也无差异。只改原创建筑脚本、三份NBT/清单及测试/文档/版本，不改GUI/字体、机器碰撞、附魔概率/指定收费、12点核心、36架或原版独立台规则。大型约100×80×80宫殿只记入 [FUTURE_PALACE_RUIN.md](FUTURE_PALACE_RUIN.md)，没有第三种建筑模板或注册。

验证按本轮要求缩小：两次 `build runGametest runClientGametest -PruinsOnly --console=plain` 分别1m19s、1m17s通过，每次27项JUnit/3服务端（2遗迹+1原版）/1遗迹客户端；没有每次重跑附魔菜单和2,700,000次实际池模拟。过滤仅作用于生成的测试fabric.mod.json，参数是Gradle资源任务输入，原测试入口文件不改。

最终只尝试一次完整 `build runGametest runClientGametest --console=plain`，2m35s：27单元与42服务端通过，原6客户端入口完成，最后遗迹客户端在新“有窗”断言失败，整轮命令失败，不冒充全通过。查26.1.2源码确认TemplateStructurePiece.postProcess在设置中保留最后一个区块的BoundingBox；新增模板守卫误用了该范围，天然多区块小屋的其他窗户未被扫描。改为复制设置并清空测试扫描边界，保持生产分块设置原样。随后只定向复查build/3服务端/遗迹客户端，1m15s通过，再build processGametestResources约1s恢复全7入口。没有再跑原6个附魔客户端或全套模拟。最终覆盖是原6入口+修正后遗迹入口跨运行成功，原失败日志保留。

普通主世界种子20261017，原生自然生成：地表1688,66,1288、埋藏-568,46,1368（地表65）、工坊-1672,65,-1640；未用/place或手工搭建替代。实际检查三模板四方向、容器/随机附魔书/occupied、注册与加载、缺件机器、真实僵尸出现、螺旋17半格及两层房间进出、不飞不跳、书架磁盘重进数据一致。风格守卫确认浅色木材主导、无铁栅、地表有玻璃；截图目视检查新斜顶/侧翼/内饰及无明显缺块/空气切割。正常取景用旁观者/夜视，不清树或修图。12张最终实拍在docs/screenshots/ruins-style，含额外门厅与二层工具角；旧截图原样保留。

27×100,000原概率模拟仅在综合时重跑一次，28份报告SHA-256与0.6.0相同。三份模板可复现、运行JAR与源NBT逐字节一致、测试内容不打包。详情见 [RUINS.md](RUINS.md) 与 [本轮验证](validation/ruins-style/README.md)，包含失败原因、所有命令和分次覆盖范围。仍需更多种子/坡地/森林、地下发现率及奖励密度试玩，尚非最终建筑或平衡；不声称人工双客户端或整合包验收。旧0.6.0发布包归档至忽略的.tools/ruins-style-backup，保留此前本地提交，按原Git身份再本地提交，不push。
