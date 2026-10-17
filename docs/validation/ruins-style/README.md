# 遗迹风格第二轮 · Differentenchant 0.6.1

基于本地e73f9ea，工作区起初干净。保留上一轮未推送提交，未reset、改remote或push。本轮改两种建筑的源模板与取景，不改生产Java、GUI、附魔算法、费用、12点核心、机器碰撞或原版附魔台交互。

## 风格与范围

| 项目 | 第一轮 | 第二轮 |
| --- | --- | --- |
| 外墙/框架 | 石砖主墙、深板岩带、铁栅 | 白桦木、苍白橡木板/去皮梁柱、玻璃 |
| 地表小遗迹 | 12×10×6，低平石屋 | 12×10×8，阶梯斜顶、前廊、高窗和讲台研究角 |
| 地下小遗迹 | 同款石屋封门 | 12×10×6，封闭木构档案室、低梁、额外讲台/白地毯，无地表斜顶/玻璃外窗 |
| 中型工坊 | 20×15×15，竖直石壳 | 20×15×19，三层主楼/两层侧翼、错落山墙、前厅与凸出采光塔 |
| 内部内容 | 三层功能与书架、箱子 | 全部保留，补酿造台/材料桶/讲台，三层书房移入主楼 |

石材保留在地基、柱脚、部分地面及地下封堵。坏掉机器仍严格使用原来的磨制深板岩/黑曜石/紫水晶，缺中心附魔台及两个角紫水晶，不能组装。其他室内墙、梁柱、楼板和螺旋踏步转浅色木构。少量蛛网表示废弃，主体完整，没有用大面积坍塌制造轮廓。

主世界放置ID、生物群系、spacing20/24/36与村庄避让、三种书架表/四种箱子表全部不变。小建筑仍无笼；工坊一层唯一原版僵尸笼，二层四组、三层两组雕纹书架，生成时各填2–4本真实附魔书。箱子仍按小型/普通/研究/书房分层，铭纹石和普通核心掉率未动，不提供谐调核心。大型宫殿只记录[方向](../../FUTURE_PALACE_RUIN.md)，无第三种模板或注册。

## 验证方式与实际结果

```powershell
$env:JAVA_HOME = (Resolve-Path '.tools/jdk-25.0.4.1+1').Path
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
node tools/generate-ruins.mjs
.\gradlew.bat build runGametest runClientGametest -PruinsOnly --console=plain
# 仅在最终提交前，去掉参数综合验证一次：
.\gradlew.bat build runGametest runClientGametest --console=plain
```

本轮只跑**两次遗迹专项**，分别1m19s和1m17s通过：每次build（27项JUnit）、2个遗迹项目服务端测试+1个原版测试、1个遗迹客户端入口，不运行其他附魔GameTest和大规模模拟。-PruinsOnly仅过滤生成的测试fabric.mod.json，原始入口文件保持全部测试；Gradle将该参数声明为输入，去掉后会恢复全部7客户端/10服务端入口类，不会把上次过滤状态缓存到完整运行。

最终只尝试一次综合运行，2m35s：**27项JUnit、42项服务端（41项目+1原版）通过，原6个客户端入口完成，第7个遗迹入口因本轮新增测试断言失败，整条命令退出失败**。不能将这次综合写成全部通过。失败原因是原生TemplateStructurePiece.postProcess会保留最后一个生成区块的BoundingBox，新模板风格断言用它扫描整栋模板，漏掉其他区块的窗户；源码和现场截图确认窗户实际存在。修正为复制设置并清除测试扫描边界，不修改生产放置设置。

修测试后仅运行 `build runGametest runClientGametest -PruinsOnly` 定向复查，1m15s通过，3项服务端与遗迹客户端成功，再执行 `build processGametestResources` 约1s恢复全入口；没有重跑已完成的其他6个客户端或全套附魔模拟。因此当前客户端覆盖是6个原入口+修正后的遗迹入口跨运行通过，**不是同一条综合命令7个入口成功退出**。日志见[专项1](preview1.log)、[专项2](preview2.log)、[综合失败原日志](comprehensive-attempt.log)、[最终专项复查](final-recheck.log)及[入口恢复](metadata-restored.log)；XML、模板可复现哈希和[摘要](summary.json)同目录。27×100,000机器概率模拟只在综合时重跑一次，28份输出与上一轮SHA-256相同；不把有限抽样当作算法支持集证明。

遗迹专项使用正常默认主世界、种子20261017、原生自然生成开启。通过原生查找StructureStart并加载区域，没有用/place或人工搭建替代生成。相机旁观者/夜视只用于取景，不改地形和光照方块。自然原点分别为地表小屋1688,66,1288、埋藏档案室-568,46,1368（地表65）、工坊-1672,65,-1640，范围见[natural-placements.json](natural-placements.json)。

实际检查包括：模板/数据包加载、三个Structure及StructureSet注册、四方向模板/容器/书架占用与真实附魔、仅工坊一笼且配置僵尸、机器缺件、螺旋17半格路径与二/三层进出房间，不飞不跳；附近真实出现僵尸，保存退出重进后书架完整数据逐字相同。新增模板守卫确认浅色木材多于石砖、没有铁栅窗、地表版本有真实玻璃窗。人眼检查最终外观/室内截图，未发现新建筑缺块、悬空紫水晶或大块空气切割；这不是只凭编译判断建筑可用。

第一轮专项截图发现未去皮苍白橡木树皮偏暗、塔顶半砖与紫水晶之间有半格空隙；第二轮换浅色去皮梁柱并填实塔顶支座，补门厅和二层工具角取景后冻结模板。没有因此反复运行完整附魔回归。生产Java、全部战利品表、生成参数及标签对照e73f9ea无改动，见summary.json。发布包三份NBT与源文件逐字节一致，测试模组不进入运行JAR。

## 真实截图与历史对照

| 场景 | 第二轮实拍 | 第一轮历史 |
| --- | --- | --- |
| 地表小屋外观 | [浅色斜顶小屋](../../screenshots/ruins-style/ruins-01-small-surface-natural.png)、[前廊](../../screenshots/ruins-style/ruins-01a-small-surface-front.png) | [石屋](../../screenshots/ruins/ruins-01-small-surface-natural.png) |
| 小屋内部 | [书桌与藏书](../../screenshots/ruins-style/ruins-02-small-surface-interior.png) | [原研究角](../../screenshots/ruins/ruins-02-small-surface-interior.png) |
| 埋藏版本 | [封闭木构档案室](../../screenshots/ruins-style/ruins-03-small-underground-natural.png) | [原石室](../../screenshots/ruins/ruins-03-small-underground-natural.png) |
| 工坊外观 | [侧翼/山墙/大窗](../../screenshots/ruins-style/ruins-04-workshop-exterior-natural.png) | [原石制工坊](../../screenshots/ruins/ruins-04-workshop-exterior-natural.png) |
| 工坊一层 | [笼区](../../screenshots/ruins-style/ruins-05-workshop-first-floor-spawner.png)、[门厅研究区](../../screenshots/ruins-style/ruins-05a-workshop-entry-study.png) | [原笼区](../../screenshots/ruins/ruins-05-workshop-first-floor-spawner.png) |
| 工坊二层 | [缺件机器](../../screenshots/ruins-style/ruins-06-workshop-second-broken-machine.png)、[酿造/材料工具角](../../screenshots/ruins-style/ruins-06a-workshop-research-tools.png) | [原机器陈列](../../screenshots/ruins/ruins-06-workshop-second-broken-machine.png) |
| 工坊三层 | [藏书与生活痕迹](../../screenshots/ruins-style/ruins-07-workshop-third-library.png) | [原书房](../../screenshots/ruins/ruins-07-workshop-third-library.png) |

另保留原始步行路径终点截图及存档重进书架截图，供测试追溯。12张原图来源、时间及SHA-256见[screenshots.json](screenshots.json)，没有修图、清理自然树木或用生成效果图冒充游戏。

## 剩余范围

本轮重在纠正风格，尚未完成所有种子/坡地/森林及整合包的建筑融合。树叶邻接、入口高差、地下发现率和奖励密度仍沿用第一轮已知限制；地上斜顶加高、低侧翼与三层主楼是本轮原型，不是最终美术定稿。没有专门人工鼠标试玩、两台独立客户端联机或宫殿生成测试。旧已生成区块不会自动变成新建筑，应探索新区域或新建测试世界。
