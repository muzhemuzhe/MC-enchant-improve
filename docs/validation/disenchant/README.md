# 祛魔台0.10.0专项验收

真实Minecraft Java26.1.2 / JDK25.0.4.1+1 / Fabric Loader0.19.5 / API0.155.3+26.1.2 / Loom1.18.4 / Gradle9.7.1。本目录fabric-loader.json、fabric-api.pom与fabric-loom.pom重新从官方metadata/Maven取得。使用本地Loom官方无混淆源码核对26.1.2 API；没有旧Yarn映射或外部模组源码复制。

## 最终执行

PowerShell工作目录为项目根：

```powershell
$env:JAVA_HOME=(Resolve-Path '.tools/jdk-25.0.4.1+1').Path
.\gradlew.bat build -x test runGametest runClientGametest -PdisenchantOnly -PdisenchantIntegrated --console=plain
.\gradlew.bat assemble processGametestResources --console=plain
```

最终完整执行日志 `release-validation.log`：15/15真实服务端GameTests（10祛魔、4相关旧设备、1原版）；真实OpenGL客户端入口 `DisenchantClientGameTest` 全流程。`-x test`明确跳过历史JUnit，未跑全量概率/遗迹/附魔效果套。打包命令不带筛选参数，恢复默认测试metadata，不重新执行历史套。正式JAR与资源/版本/测试类隔离审计见artifact-audit.json。

## 已执行覆盖

| 范围 | 实际验证 |
| --- | --- |
| 规则/组件 | 多条普通附魔过滤、诅咒隐藏与服务端拒绝；V→IV、III→II、II→I、I→I；其余附魔、damage137、自定义名称、repair_cost19、Lore及有字符串/整数的CustomData保持；准备服务不改原输入 |
| 注册兼容 | 全部17种differentenchant附魔使用各自supported_items；独立differentenchant_tests命名空间probe IV→III；curse_probe仍留在装备、不可提取；非法等级/书类型/伪选择拒绝 |
| 成本 | 原生工作台配方真实取出核心，恰好四回响碎片+其余五槽被消耗；提取一本书及3～6级经验；客户端40→34→30→27且书5→4→3→2 |
| 事务 | 预览/选择不扣料；鼠标/右键/Shift/数字键/副手领取；快速25次点击只结算一次；满背包、占用快捷栏、不兼容鼠标、经验不足均不扣；创造克隆/丢弃/双击不能拿预览；原地组件变更取消选择 |
| 玩家/机器 | 两个真实生存ServerPlayer对象同台独立输入/选择；外人不能选/领取；破坏失效；同址重建UUID不同，旧菜单仍无效；重复removed不多返还 |
| 多方块 | 全18部件逐个破坏恢复其余原方块；每部件原生loot恰好对应原料；18份NBT保存/加载；实际铁镐/空手/创造破坏掉落；爆炸总材料不增加；重复/缺块成形不变更；未加载远处成形拒绝且区块仍不存在 |
| 恢复 | 抑制移除回调后23tick完整性恢复；序列化迟加载孤立部件重插只恢复自身原状态，不覆盖已恢复邻居；真实客户端整机磁盘重进UUID一致 |
| 实机GUI | 生存放置核心、空手潜行实际组装、右击开菜单；原生Shift放入、三条/六条列表及滚动最后一行、鼠标选择/直接领取/Shift；中英名称/Tooltip；诅咒保留且不可选；借火II→I预览 |
| 实机断线/拆解 | 带未领取预览断线，磁盘重开：背包+掉落实体总量一致、完整原装备组件及XP保留、展示不返还；破坏部件后正常客户端自动关菜单、输入数量不变 |
| 相关旧设备 | 原附魔机正常形成/拆解及原版台禁用、随机真实付费充核/Lv.2重复生产、指定批量/30级规则、研究台双玩家直接领取与破坏返还 |

GameTest中的两玩家是同一真实服务端内的独立连接/ServerPlayer对象，没有声称两台独立客户端高延迟实测。跨区块迟加载用保存部件重插模拟，另有真实整机磁盘重载；未完成长期真实卸载压力/强杀恢复/第三方整合包穷举。未加载块不获取的API边界与检查可在DisenchantStructure中直接核对。

## 实机截图（17张）

全部来自Fabric原生客户端测试截图，不是渲染示意或合成图。以下相对链接是仓库文档链接：

- [01 3×3×2成形外观](../../screenshots/disenchant/disenchant-01-assembled-exterior.png)
- [02 多附魔输入/未选择](../../screenshots/disenchant/disenchant-02-multiple-enchantments-en.png)
- [02b 六条附魔列表](../../screenshots/disenchant/disenchant-02b-six-choices-en.png)
- [02c 滚动后选中末条](../../screenshots/disenchant/disenchant-02c-scrolled-last-choice-en.png)
- [03 锋利V选择/IV预览](../../screenshots/disenchant/disenchant-03-selected-preview-en.png)
- [04 英文结果Tooltip](../../screenshots/disenchant/disenchant-04-result-tooltip-en.png)
- [05 英文含诅咒装备](../../screenshots/disenchant/disenchant-05-curse-equipment-tooltip-en.png)
- [06 直接拿取后的真实产物](../../screenshots/disenchant/disenchant-06-direct-result-pickup.png)
- [07 中文剩余附魔](../../screenshots/disenchant/disenchant-07-remainder-zh.png)
- [08 原装备诅咒/名称保留](../../screenshots/disenchant/disenchant-08-preserved-curse-tooltip-zh.png)
- [09 耐久III→II中文Tooltip](../../screenshots/disenchant/disenchant-09-unbreaking-result-tooltip-zh.png)
- [10 经验修补I保持I](../../screenshots/disenchant/disenchant-10-mending-I-preview-zh.png)
- [11 仅剩诅咒不可选](../../screenshots/disenchant/disenchant-11-curse-only-no-selection-zh.png)
- [12 仅剩诅咒装备Tooltip](../../screenshots/disenchant/disenchant-12-curse-only-equipment-zh.png)
- [13 失落附魔借火预览](../../screenshots/disenchant/disenchant-13-lost-enchantment-preview-zh.png)
- [14 磁盘重进的完整结构](../../screenshots/disenchant/disenchant-14-disk-reconnected-structure.png)
- [15 实际拆解并关闭菜单](../../screenshots/disenchant/disenchant-15-dismantled-safe-return.png)

230×222容器、标准槽与背包；列表最多四行，底部不重叠。英文标题不溢出，未选择侧提示精简为Choose one；完整长名字由Tooltip提供。选中行使用深色文本，避免原版附魔名灰色样式降低可读性。关键界面已查看，原版最高I附魔显示名称但省略罗马I属于原版行为，存储等级另有实际断言。

## 中间日志与修正

- first-compile.log：26.1.2 Player没有旧displayClientMessage方法，改为源码已有sendSystemMessage。
- test-compile.log：测试中Java var复合声明编译失败，拆分声明。
- first-server.log：首轮9/9；first-client.log：首轮实机全流程通过。
- final-scoped.log：新增第二把六附魔测试剑后，重连夹具错误选择了第一把剑进行组件比较而失败；原输入与物品总量没有丢失。按借火II标识选择原装备后通过。
- final-validation.log：11/11及实机全流程通过；final-integration.log：15/15；final-client-layout.log：精简英文侧提示后实机再通过。
- final-sync-client.log：原生DataSlot费用与选择状态等待通过；随后截图发现字体尚未完成一帧绘制，final-screenshots.log将拍摄前等待延至8tick后39秒全流程通过，费用与文字清晰。没有修改游戏截图内容。
- release-validation.log：补充非空CustomData/Lore实际组件保存后最终15/15与实机全流程通过。

原始日志保留Minecraft/Fabric的Realms开发账户认证、旧显示选项、原有附魔缺失标签翻译等非测试失败信息；未修改原始日志行尾空格。源码/文档差异检查排除.log原件，未改Git空白配置。没有运行模拟器替代Minecraft。
