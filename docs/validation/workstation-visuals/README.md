# 工作站成形外观验收 · 2026-10-19 · 0.11.1

基线09abcbce3d022adb787d669f9456d72d848f4d75；Minecraft26.1.2、JDK25.0.4.1、Loader0.19.5、Fabric API0.155.3+26.1.2、Loom1.18.4、Gradle9.7.1。依赖未改，官方版本核对记录沿用[0.11.0验证包](../workstation-structures/README.md)。

## 实际执行

每次执行均设置JAVA_HOME为本地.tools/jdk-25.0.4.1+1：

| 命令 | 结果与日志 |
| --- | --- |
| node tools/generate-workstation-structures.mjs | 36模型、两blockstate、两原创贴图、Java碰撞，共41输出再次生成SHA完全一致。[模型审计](model-audit.json) |
| .\gradlew.bat build -x test runClientGametest -PworkstationVisuals --console=plain | 第一轮43秒失败：新摄影夹具在远处右键矮小祛魔台，超出生存触及距离。[原始日志](first-client.log) |
| 同上 | 改正常近距离操作，同时改进整页倾斜书页、哑光金饰与同面细线闪烁，39秒通过。[实机日志](refined-client.log) |
| .\gradlew.bat build -x test runGametest runClientGametest -PworkstationVisuals -PworkstationStructures --console=plain | **1分56秒通过32/32服务端与三段真实客户端流程**。[完整日志](integrated.log)、[原生XML](gametest-results.xml) |
| .\gradlew.bat assemble processGametestResources --console=plain | 1秒成功，恢复默认完整测试入口。[打包日志](final-package.log) |

-x test明确跳过历史JUnit。本次没有跑完整历史概率/遗迹/全部附魔效果回归；GameTest实际启动独立服务端，ClientGameTest实际启动Minecraft OpenGL客户端并运行原生输入、世界和菜单，没有以编译替代游戏验证。

## 三段客户端与32项服务端

1. WorkstationVisualClientGameTest：1600×1000，同机位组装前/后，真实空手潜行右击、正常近距离打开既有菜单；拆除中心后其余17格原BlockState逐一匹配，放回原核心再组装；真实磁盘退出重进后两机正常。[报告](native-client.txt)。
2. WorkstationClientGameTest：1280×720，真实生存组装、错误讲台方向拒绝/修正、四角实际紫色粒子向心移动及GUI/拆解清理；真实镐挖低导流槽、四簇向上受支撑恢复且无碎片；Shift祛魔保留诅咒并扣一本书/六级经验；研究未知预览/悬停???、直接领取、已发现交换输入预览、卷轴保留两书数据；实际保存重进与带输入破坏返还。[报告](transactions-client.txt)、[实际粒子坐标](particles.csv)。挖掘瞄准从原红石体积中心下移到新导流槽表面，仅修改测试目标。
3. ResearchClientGameTest：原研究台完整中英文GUI/Tooltip、未知卷轴不提前暴露、直接领取、重复发现、记录滚动与断线重进流程在新成形外观上通过。原研究Screen/Menu/Preview没有修改，详细执行见完整日志。

32项为12结构、10祛魔、6研究、3原附魔机相关集成、1原版always_pass。包括逐格缺失/破坏、30次组拆无增料、成熟簇/朝向/含水/小碰撞、讲台四向旋转/空BE/藏书拒绝、原始loot、旧机恢复、UUID/失效交易、真实区块卸载重载且查询不加载区块、材料/经验/组件守恒、诅咒拒绝、满背包/Shift/快捷栏交易、研究成果冻结和原附魔机随机/指定/核心。

## 实拍与审计

[18张截图清单、分辨率和SHA-256](screenshots.json)。14张外观实拍1600×1000，4张1280×720来自本次交易回归。全部是Minecraft原始帧，没有合成或后期修图。摄影隐藏HUD，机器外使用测试屏障支撑机位；正式模组没有屏障或摄影功能。

| 机器 | 材料状态 | 成形状态 | 拆解恢复 |
| --- | --- | --- | --- |
| 研究台 | [组装前](../../screenshots/workstation-visuals/visual-research-01-before.png) | [组装后](../../screenshots/workstation-visuals/visual-research-02-formed.png) | [恢复](../../screenshots/workstation-visuals/visual-research-05-restored.png) |
| 祛魔台 | [组装前](../../screenshots/workstation-visuals/visual-disenchanter-01-before.png) | [组装后](../../screenshots/workstation-visuals/visual-disenchanter-02-formed.png) | [恢复](../../screenshots/workstation-visuals/visual-disenchanter-05-restored.png) |

[祛魔选择](../../screenshots/workstation-visuals/workstation-05-disenchanting-gui.png) · [祛魔结算](../../screenshots/workstation-visuals/workstation-06-disenchanting-committed.png) · [未知研究](../../screenshots/workstation-visuals/workstation-11-research-unknown-output.png) · [真实领取](../../screenshots/workstation-visuals/workstation-13-research-direct-result.png)。

[188份已有生产文件核对](preservation.json)：除了成形模型/blockstate和WorkstationPartBlock的形状分派，其余既有src/main全部与09abcbc一致，文本仅归一化行尾。包括结构形成/恢复、BE存档/同步、两个未组装核心模型、所有菜单/Screen/策略/粒子/17种附魔、配方/loot/原附魔机/遗迹。

正式JAR：build/libs/differentenchant-0.11.1.jar，SHA-256为8fb5cbef8c711035d633c4fde4f718824b201952256694d960e8f0069744bd54。[打包审计](artifact-audit.json)：149份非版本资源与源码逐一匹配，版本/Mod ID正确，无测试类/夹具/存档。历史验证包和截图保留。

边界：倾斜薄书页碰撞为保守包围体近似；晶簇原生体积不变。未测大量并排渲染压力、着色器/完整整合包、两台独立高延迟客户端或断电强杀；正常保存、独立服务端和模拟两玩家事务不能替代这些场景。

最终截图检查后，仅补上阅读翼顶面UV随径向旋转，避免侧向书页拉伸。再次执行build -x test runClientGametest -PworkstationVisuals，39秒通过；碰撞Java输出未变，无须重复32项服务端。最终两机外观图已更新为该版本。[最后实机日志](final-uv-client.log)。随后再次assemble/processGametestResources恢复默认入口，以上JAR哈希为最终UV版本。
