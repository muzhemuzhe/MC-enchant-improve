# 研究台结果槽交互 · 0.8.1 验收

2026-10-19。基线 `e05b86c`，本轮仅研究GUI、菜单/展示协议、对应语言及测试；世界种子映射、评分/池参数、失落附魔效果、模型与原附魔机未改。

## 实际命令与结果

均使用仓库 `.tools/jdk-25.0.4.1+1` 设置 `JAVA_HOME`，Minecraft 26.1.2、Loader 0.19.5、API 0.155.3+26.1.2、Loom 1.18.4。

```powershell
$env:JAVA_HOME = (Resolve-Path '.tools/jdk-25.0.4.1+1').Path
.\gradlew.bat build -x test runGametest runClientGametest -PresearchGui --console=plain
.\gradlew.bat build -x test runClientGametest -PresearchGui --console=plain
.\gradlew.bat assemble processGametestResources --console=plain
```

- [final-4.log](final-4.log)：1分2秒成功；服务端 **8/8**（7项目+1原版always_pass），真实OpenGL客户端完整流程通过。服务端XML见 [server-final.xml](server-final.xml)。
- 最后检查原版 `GuiGraphicsExtractor` 源码发现，普通 `setTooltipForNextFrame` 不覆盖已经排队的原生物品tooltip。未知书改为在调用super之前单独绘制唯一的 `???`，避免F3+H附加调试行。
- [final-tooltip-5.log](final-tooltip-5.log)：最终源码构建+真实客户端复验50秒成功，增加开启高级tooltip的未知书截图。[客户端断言报告](client-final.txt)。最后一次仅改客户端tooltip，没有重复跑已通过的服务端套。
- [package.log](package.log)：1秒成功，恢复默认测试入口；测试模组未进入发布JAR。未运行历史JUnit/效果/附魔机/遗迹全套；`-x test`明确跳过JUnit，实际业务验证使用本轮原生GameTest。
- [artifact-audit.json](artifact-audit.json)：`differentenchant-0.8.1.jar`，89份非版本化资源逐字节对应源码，版本化fabric.mod.json为0.8.1，无GameTest类/测试模组。SHA-256：`e4fed47453088e5f16dcf797a1ef73c604d68f3f1961cffbc6f7876c19a133bf`。
- 官方元数据重新取得并存档：[Loader](loader-metadata.json)、[API](fabric-api.pom)、[Loom](fabric-loom.pom)。来源：[Fabric Loader版本端点](https://meta.fabricmc.net/v2/versions/loader/26.1.2/0.19.5)、[官方API Maven元数据](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.155.3+26.1.2/fabric-api-0.155.3+26.1.2.pom)、[官方Loom Maven元数据](https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.18.4/fabric-loom-1.18.4.pom)。

## 覆盖与边界

| 要求 | 实际验证 |
| --- | --- |
| 未知书，无空白处???，悬停才显示??? | 客户端真实截图；普通和F3+H悬停仅???，服务端未知展示为普通BOOK，无STORED_ENCHANTMENTS |
| 未知卷轴不暴露 | 原生同步仍普通BOOK；预览协议known为空，无结果ID/等级/scroll字段值；领取后才交付真实卷轴 |
| 无按钮，点击结果直接研究 | 生存正常放台/右击GUI，鼠标实际输入两书、Shift放核心，直接点击输出书，真实结果进入鼠标栈 |
| 成功精确消耗与快速点击 | 两书+一核心；连续20次后续点击没有第二件或额外扣料；已知、A/B互换及双玩家固定答案一致 |
| 卷轴保留两书及全部组件 | 原CUSTOM_NAME、REPAIR_COST等完整ItemStack.matches；左右键/Shift重复每次一核心一卷轴 |
| 满目标不扣料、不发现 | 背包全满、鼠标不兼容、快捷栏占用/满堆叠拒绝；最后一个可合并空间只收到一件 |
| 数字键、副手、双击/克隆/丢弃/拖拽 | 空/可合并快捷栏和副手交付真实物品；双击其他格排除预览，创造克隆和丢弃不能提取预览；输出禁止mayPlace，不接受拖拽 |
| 分页/权限/关闭 | 外人、坏方块、记录页拒绝提交；关闭两次不会重复返还；未知/已知预览从不作为库存返还 |
| 不显示后台稀有度 | Screen无评级/稀有度文本；DisplayResult网络record只有id/level/scroll；记录保留实际附魔等级 |
| 持久化 | 已有2000记录NBT与版本999保护用例通过；真实菜单开着断线，背包+附近实体总数不变，磁盘重进世界快照/UUID发现完全一致 |
| 中英文、滚动和布局 | 六条真实取物发现，滚动offset实际变化；中英物品tooltip与记录截图，无GUI/背包/结果格重叠；长英文记录裁剪并悬停换行 |

世界种子1的本次夹具：水下速掘I+同书→守望凝神II；水下速掘I+致密II→卷轴。输入由测试提供，结果来自生产服务器固定算法，非硬编码产物。额外四条发现也通过真实GUI结果槽提交；未直接调用discover注入记录。

断线时原版可能把临时材料掉在玩家附近。先比较实际背包+掉落实体总数与保存前一致，再把测试玩家移到保存的核心掉落实体处，等待原版拾取后重新放入同一核心验证已知预览；没有注入替代核心。真实客户端是自动化GameTest，未宣称人工手测、两台独立客户端延迟压力或强制崩溃一致性。

## 实机截图

全部是本地真实26.1.2 OpenGL客户端截图，未修改截图图像；历史research目录保留。截图目录为 `docs/screenshots/research-gui/`。

| 画面 | 截图 |
| --- | --- |
| 未发现组合，空白处无??? | [未知研究界面](../../screenshots/research-gui/research-gui-01-unknown.png) |
| 悬停未知书（英/中/高级） | [英文](../../screenshots/research-gui/research-gui-02-unknown-tooltip-en.png) · [中文](../../screenshots/research-gui/research-gui-08-unknown-tooltip-zh.png) · [F3+H](../../screenshots/research-gui/research-gui-02b-unknown-advanced-tooltip-en.png) |
| 直接取结果后 | [两书已消耗，真实书在鼠标上](../../screenshots/research-gui/research-gui-03-direct-pickup.png) |
| 已发现真实书预览（英/中） | [英文预览](../../screenshots/research-gui/research-gui-04-known-preview-en.png) · [英文tooltip](../../screenshots/research-gui/research-gui-05-known-tooltip-en.png) · [中文预览](../../screenshots/research-gui/research-gui-14-known-preview-zh.png) · [中文tooltip](../../screenshots/research-gui/research-gui-15-known-tooltip-zh.png) |
| 未知卷轴仍用书 | [秘密卷轴配方](../../screenshots/research-gui/research-gui-07-secret-scroll.png) |
| 卷轴领取后保留书、已知卷轴预览 | [保留两书](../../screenshots/research-gui/research-gui-09-scroll-preserved.png) · [真实卷轴tooltip](../../screenshots/research-gui/research-gui-10-known-scroll-tooltip-zh.png) |
| 发现记录与实际等级 | [首次记录](../../screenshots/research-gui/research-gui-06-first-record.png) · [中文](../../screenshots/research-gui/research-gui-11-records-zh.png) · [滚动后](../../screenshots/research-gui/research-gui-12-records-scrolled-zh.png) · [英文](../../screenshots/research-gui/research-gui-13-records-en.png) |
| 磁盘重进已知预览 | [重连恢复](../../screenshots/research-gui/research-gui-16-reconnected.png) |

## 保留的失败/中间日志

[compile-1.log](compile-1.log)编译通过；[server-1.log](server-1.log)7/7通过。客户端[client-1.log](client-1.log)是报告StringBuilder泛型append歧义编译失败，显式long修正；[client-2.log](client-2.log)完成界面各步骤后，在核心未拾回时断言已知格有产物失败；[verification-3.log](verification-3.log)新增协议测试服务端8/8通过，但只原地等待拾取仍超时，因为原版掉落散在附近。最终测试移到真实掉落处再拾回，final-4与final-tooltip-5均通过。版本999保护用例预期产生读取错误日志，同时验证原文件未覆盖；不是未解决的服务端失败。
