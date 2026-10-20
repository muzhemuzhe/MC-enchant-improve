# 0.13.0六项原生进度验收 · 2026-10-20

基线16de1c08231fc8701bbe018eafba9d1708aec083。Minecraft26.1.2 / JDK25.0.4.1 / Loader0.19.5 / Fabric API0.155.3+26.1.2 / Loom1.18.4 / Gradle9.7.1。依赖未升级；对应官方[Loader元数据](../enchanter/fabric-loader.json)、[API POM](../enchanter/fabric-api.pom)、[Loom POM](../enchanter/fabric-loom.pom)保留。实际API直接检查本地该版本官方sources JAR中的InventoryChangeTrigger、ServerPlayer、ServerPlayerGameMode、ServerGamePacketListenerImpl、PlayerAdvancements、AdvancementVisibilityEvaluator及原版root JSON。

## 实际执行

PowerShell先设置JAVA_HOME为仓库.tools/jdk-25.0.4.1+1。没有用模拟GUI或构建成功代替游戏运行。

| 命令 | 实际结果 |
| --- | --- |
| .\gradlew.bat compileJava --console=plain | 首次生产代码编译通过 |
| .\gradlew.bat runGametest -Padvancements --console=plain | 分阶段38项、补断线后39项通过；初期夹具玩家站在待放方块内/核心朝向断言过严已修正，原始失败日志保留 |
| .\gradlew.bat build -x test runGametest -Padvancements --console=plain | **最终15秒，51/51项服务端通过**。[完整日志](server-integrated.log)、[原生XML](gametest-results.xml) |
| .\gradlew.bat runClientGametest -Padvancements --console=plain | **最终1分20秒，真实OpenGL客户端完整流程通过**，含两张新世界及一次原世界磁盘重开。[日志](client-capture.log)、[客户端记录](client-report.txt) |
| .\gradlew.bat assemble processGametestResources --console=plain | **1秒通过**；恢复默认测试入口并生成最终JAR。[日志](package.log) |

-x test跳过历史JUnit/概率套。本轮选择6项新进度、12结构、5自动组装、6研究、3原机集成、10祛魔、8职业/交易/结晶及1原版测试，没有重跑全部历史附魔效果/遗迹生成套。

## 服务端断言

- 三台机器各以中心、非中心上层、下层作为最后原版物品，由真实ServerPlayer.gameMode.useItemOn放置，共9种玩家归属情形；形成前不授予，成功后仅完成者获得。旧自动组装测试仍对三机全部18格做54种最后格位验证，错讲台/晶簇拒绝、修正、相邻重叠、卸载/重载与不强制加载均通过。
- 两名玩家在同一延迟队列中先后放材料，最后完成者独得；命令/测试夹具形成无人得，非玩家最终变更清除旧归属。玩家在形成前断线仍授予并通过原生JSON保存/重读。
- 实际拆解再由同一玩家补核心，机器重新形成而已完成criterion时间戳不变。无关邻居更新也不重授。
- 核心在机器库存不授予；实际Shift取出授予；原生交易结果预览不授予、实际交易授予。掉落实体真正被玩家拾取才授予卷轴；另一玩家档案独立。
- 未发现卷轴配方只预览未知书；光标放满其他物品拒绝交付时不授予/不扣料。真实领取后立即同时获得卷轴与失败进度，恰好消耗一核心，原两书完整保留；成功研究、破坏后取消均不算失败。
- 六份数据均为有显示的TASK、无奖励。单独授予最深失败节点，原生可见性算法仍包含未完成根/祖先，不自动完成；原生save/reload保持。
- 原有组件/诅咒限制、经验费用、产物容量/快速点击/Shift/数字键/两玩家菜单事务、研究发现持久化与12点充能相关回归继续通过。

## 真实客户端

**23张1280×720原始PNG**，未经编辑、裁切、加字；[完整文件及SHA-256](screenshots.json)。主要实拍：

| 内容 | 截图 |
| --- | --- |
| 实际图书馆战利品取到光标、中文通知 | [01](../../screenshots/advancements/advancements-01-scroll-cursor-toast-zh.png) |
| 首先完成卷轴、根未完成仍出现独立页面 | [02](../../screenshots/advancements/advancements-02-child-before-root-zh.png) |
| 原生交易真实领取核心与通知 | [03](../../screenshots/advancements/advancements-03-traded-core-cursor-toast-zh.png) |
| 最后放中心附魔台、讲台、下层紫晶，三机实际形成 | [附魔机](../../screenshots/advancements/advancements-10-actual-formation-toast-zh.png)、[研究台](../../screenshots/advancements/advancements-11-actual-formation-toast-zh.png)、[祛魔机](../../screenshots/advancements/advancements-12-actual-formation-toast-zh.png) |
| 真实未知预览不触发、领取后失败通知 | [预览](../../screenshots/advancements/advancements-21-unknown-preview-no-failure.png)、[领取](../../screenshots/advancements/advancements-22-committed-failure-toast-zh.png) |
| 六项完成、原生中文/英文页面 | [中文](../../screenshots/advancements/advancements-30-complete-tree-zh.png)、[英文](../../screenshots/advancements/advancements-33-complete-tree-en.png) |
| 每项英文Tooltip、原版/模组图标 | 文件名34-*-tooltip-en，共六张 |
| 整个集成服务端停止、磁盘重开后进度仍全完成 | [40](../../screenshots/advancements/advancements-40-after-real-server-restart-en.png) |
| 新世界无旧进度，首次失败同时触发两项英文通知 | [50](../../screenshots/advancements/advancements-50-first-scroll-and-failure-two-toasts-en.png) |
| 先完成最深节点：根、研究台仍灰色未完成 | [51](../../screenshots/advancements/advancements-51-deepest-child-without-formed-parents-zh.png) |

物品放置、箱子/商人/研究菜单点击、打开原生进度界面均使用实际客户端输入与正常网络处理；页面选中和语言切换使用原生客户端API。结构/材料及测试用村民由夹具准备；探索箱使用真正stronghold_library战利品表，未搭研究台即可取得首张卷轴。第二新世界的研究框架由无玩家夹具形成，因此只验证实际失败操作，不冒充玩家组装；正好证明未获得形成进度也能取得失败进度。没有使用/advancement grant完成客户端六项。

## 打包和范围

正式JAR：build/libs/differentenchant-0.13.0.jar；SHA-256 **a5c7270a275beb6bce9f16cfd6357559211ccd4adf519786af2f0fc3d80718aa**。[打包核对](package-audit.json)：169份非版本资源逐一SHA一致，id/version正确，无测试类/夹具或测试资源。

[生产文件保留审计](production-preservation.json)：基线253份src/main已有文件中248份内容不变，仅自动队列、研究提交后通知、两语言追加键及Mixin清单五份已有文件变化；文本比较规范化换行，二进制逐字节比较。三台模型、碰撞、配方/研究种子/冻结成果、全部17种附魔、交易/价格和遗迹/战利品保持原内容。新加六份进度、最小服务器桥接类，没有新玩家进度持久化。

未发现本轮功能阻塞。多人独立性采用独立服务端GameTest中的多名原生ServerPlayer；未跑两台独立外部客户端高延迟网络、其他整合包穷举或进程强杀，正常退出/完整集成服务端停止重开已实测。原版首次未完成任何一项时不显示本页，完成任意一项后显示，是原生可见性语义。
