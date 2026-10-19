# 0.11.0两台工作站多方块验收

Minecraft Java26.1.2、JDK25.0.4.1+1、Loader0.19.5、Fabric API0.155.3+26.1.2、Loom1.18.4、Gradle9.7.1。官方metadata/POM本轮重新取得并保存在同目录；代码使用本地Loom官方无混淆依赖源码。

## 精确命令与结果

```powershell
$env:JAVA_HOME=(Resolve-Path '.tools/jdk-25.0.4.1+1').Path
.\gradlew.bat build -x test runGametest runClientGametest -PworkstationStructures --console=plain
node tools/verify-ruins.mjs
.\gradlew.bat assemble processGametestResources --console=plain
```

最终完整日志final-validation.log：**1分34秒成功，32/32服务端与两段真实OpenGL客户端全部通过**。32项为12新结构专项、10祛魔既有事务、6研究既有事务/持久化/原生铁砧、3原附魔机相关集成及1原版。未跑历史概率/遗迹/全部效果套；-x test明确跳过JUnit。最后打包不带筛选标记，恢复默认测试metadata，不执行全套历史测试。默认测试入口已加入新结构测试。

服务端覆盖：

| 范围 | 已执行内容 |
| --- | --- |
| 材料/缺块 | 两套固定结构全部18格逐一缺失拒绝，失败前后方块不变；成熟簇与三种芽区分，六向只接受UP |
| 簇状态/恢复 | 含水完整状态与原生小碰撞一致；下层先恢复；30次组装/拆除无免费簇/碎片；破坏其下方支撑仅原版一次2碎片，不同时保留悬空簇 |
| 讲台 | 四位置分别错向拒绝，修正成功；整体四旋转正确；HAS_BOOK true及假false/真实Book仍在均拒绝，原BE/NBT/书不变；空讲台BE原生NBT重载与恢复 |
| 部件/掉落 | 两机全18位置破坏恢复；原loot转发一次；铁镐/空手/创造、爆炸材料及簇+碎片总量守恒；旧0.10祛魔18份旧原料恢复，不免费换材 |
| 有效性 | 单研究桌无权限/无产物；两Kind互不识别；缺块/同址重建UUID/成形讲台朝向被改立即拒绝待领取研究，不扣材料；完整性检查恢复保存状态 |
| 区块与保存 | 两台机器实际建在四区块交界，测试夹具显式加载后撤票，等待真实原生区块卸载；查询仍不取得缺块；重载后UUID与18份状态保留，可安全拆解。生产代码不加票/加载区块 |
| 祛魔 | 普通/失落/第三方注册附魔提取与降级；诅咒拒绝；完整名称、耐久、修复费用、Lore、非空CustomData保留；容量/XP、快速点击、Shift、数字键、副手、克隆/双击、双玩家/关闭安全 |
| 研究 | 未知原生同步仅普通书无隐藏结果；未知卷轴同图标；拿结果才消耗；卷轴保留书组件；已知重复/A+B交换、两玩家、发现/大NBT保存、恶意旧动作和满容量拒绝 |
| 获取 | 研究核心原生APA/PSP/APA配方（混合木板标签）及四回响祛魔核心原生合成；真实loot抽样与原生箱子开出首张卷轴 |
| 原设备 | 原附魔机形成拆解、随机付费充核/Lv.2重复、指定批量/30级规则不变 |

## 真实客户端

WorkstationClientGameTest：生存空手潜行组装两机；真实数字键选工具与空栏，真实按住攻击键挖掉祛魔边部件；四个原版朝上成熟簇恢复且没有碎片掉落。原生FlyTowardsPositionParticle来自四个角，保留同一个Particle引用验证其到核心的水平距离递减；GUI打开/拆除清除已有粒子。particles.csv取自原生粒子实际位置，sample是采样编号，同一corner可包含不同批次存活粒子，不当作唯一轨迹或绝对游戏时间。

真实祛魔GUI选择锋利V、Shift领取IV，书2→1、经验40→34、装备只失锋利而诅咒保留。研究台单块未组装不开GUI、错向讲台组装拒绝，修正后原生组装/打开；未知悬停???、直接拿取成功、已知交换预览、失败卷轴保留原书。带预览断线并实际关闭/重开磁盘存档，两机UUID、物品总数、研究世界快照完整相同；之后破坏研究讲台自动关GUI并返还输入。

ResearchClientGameTest再完整运行既有流程于新结构上：普通生存放置同一研究台控制器、空手组装、鼠标/Shift材料与结果、未知高级Tooltip、已知重复、卷轴数据保留、6条发现滚动、中英、断线磁盘重进与预览恢复。两个报告为client.txt与research-client.txt，均使用真正Minecraft渲染及原生菜单网络包，非模拟器。

## 34张本轮截图

以下17张新结构/粒子/交互场景：

- [01 祛魔组装前](../../screenshots/workstation-structures/workstation-01-disenchanter-before-assembly.png)
- [02 祛魔组装后](../../screenshots/workstation-structures/workstation-02-disenchanter-assembled.png)
- [03 紫色汇聚帧A](../../screenshots/workstation-structures/workstation-03-purple-convergence-a.png)
- [04 紫色汇聚帧B](../../screenshots/workstation-structures/workstation-04-purple-convergence-b.png)
- [05 祛魔GUI](../../screenshots/workstation-structures/workstation-05-disenchanting-gui.png)
- [06 祛魔实际结算](../../screenshots/workstation-structures/workstation-06-disenchanting-committed.png)
- [07 挖边拆解后四簇恢复](../../screenshots/workstation-structures/workstation-07-disenchanter-restored-clusters.png)
- [08 研究台完整原结构/向内讲台](../../screenshots/workstation-structures/workstation-08-research-frame-inward-lecterns.png)
- [09 研究台成形](../../screenshots/workstation-structures/workstation-09-research-machine-assembled.png)
- [10 讲台朝中心](../../screenshots/workstation-structures/workstation-10-research-lectern-facing-center.png)
- [11 多方块研究GUI未知书](../../screenshots/workstation-structures/workstation-11-research-unknown-output.png)
- [12 悬停未知](../../screenshots/workstation-structures/workstation-12-research-unknown-tooltip.png)
- [13 直接领取研究产物](../../screenshots/workstation-structures/workstation-13-research-direct-result.png)
- [14 已知配方预览](../../screenshots/workstation-structures/workstation-14-research-known-preview.png)
- [15 卷轴与原书保留](../../screenshots/workstation-structures/workstation-15-research-scroll-books-preserved.png)
- [16 磁盘重进后研究机](../../screenshots/workstation-structures/workstation-16-reloaded-research-machine.png)
- [17 研究机拆解/材料返还](../../screenshots/workstation-structures/workstation-17-research-restored-and-inputs-returned.png)

另17张research-gui-*.png为同次运行的原研究流程，包含中英未知/已知Tooltip、F3+H仅???、成功/卷轴、滚动发现与重连。完整文件尺寸/哈希索引为screenshots.json。关键界面已查看，无新面板/按钮/稀有度显示，槽/背包不重叠。照片中的少量独立石英块是测试用取景站台，不属于机器结构；没有后期补字或改图。

## 卷轴实际分布

固定RandomSource(20261019)，每表2048次原生getRandomItems：图书馆682（约33.30%），中型study164（约8.01%），small=0、research辅助=0。scroll-loot.json为原始计数。原生ChestBlockEntity按真实延迟loot种子开箱也取得卷轴；图书馆原书/纸仍存在，不替换原表。资源中小型没有新增卷轴入口，非仅依赖有限样本推断0。

## 保留范围与边界

ruins-preservation.log确认原遗迹NBT/几何指纹与容器坐标不变；preservation.json核对原附魔机、研究策略/存档、17种附魔定义及相关未改源码。只为中型study增加独立低机会池、原版图书馆追加池，不改世界生成。artifact-audit.json记录正式JAR版本/资源一致/无测试夹具与SHA-256。

没有宣称两台独立客户端高延迟、整合包所有扳手/流体模组、长时间极限卸载或断电强杀已测试。带书讲台按明确规则拒绝，而非假装支持迁移书本。数据包完全替换图书馆表时尊重覆盖，需该包自行兼容卷轴。

## 中间失败日志

first-server.log修正26.1 ChunkPos为record及构造参数；first-client/second-client分别修正测试正则Java转义、AABB使用Vec3。third-client/fifth-validation中的再组装失败来自测试仅修改服务端选中栏，客户端仍持物；改为原生数字键选择空栏、潜行后重新瞄准控制器，并保留返回装备后通过。fourth-validation为误把无工具落簇期望成4碎片，核对原生loot改为2后通过。second-server29/29、fifth/final-server-build31/31、sixth-client两段实机通过；最终final-validation32/32及两段实机完整通过。原始失败/成功日志保留，不修改日志行尾空格；差异检查只排除.log原件。没有把编译成功等同玩法验证。
