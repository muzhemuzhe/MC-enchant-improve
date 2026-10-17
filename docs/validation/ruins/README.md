# 遗迹试作 · Differentenchant 0.6.0

起点88b8900，main与origin/main一致且工作区干净。保留已有共鸣核心、36架、原版台禁用和正式命名；没有reset、修改remote或push。只增加两种建筑，小建筑有两种放置版本。

## 实际执行

```powershell
$env:JAVA_HOME = (Resolve-Path '.tools/jdk-25.0.4.1+1').Path
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
.\gradlew.bat build runGametest --console=plain
node tools/generate-ruins.mjs
.\gradlew.bat build runGametest runClientGametest --console=plain
```

完整7客户端入口在楼梯出口修复后运行3m9s通过（full-validation.log）。随后只调整小遗迹的门廊/扶壁/屋檐资源，又执行相同命令，暂将客户端入口缩至RuinClientGameTest复查最终模板与截图；27项JUnit和42项服务端仍全部运行，最终1m19s通过。结束后恢复全部7入口，并执行 `build processGametestResources`，1s通过。对应[最终模板验证](final-template-validation.log)及[入口恢复构建](metadata-restored.log)；提交的测试元数据保留7入口，没有删回归测试。

固定Minecraft26.1.2、Loader0.19.5、API0.155.3+26.1.2、Loom1.18.4、Gradle9.7.1与JDK25。**27项JUnit、42项服务端GameTest（41项目+1原版）、7个真实OpenGL客户端测试入口通过**。这是启动实际Minecraft、正常世界生成与真实玩家移动，不是渲染模拟；也不声称人工鼠标试玩或独立双客户端联机。

- 原6个客户端入口完整保留：形成/拆解、真实随机预览及扣费、指定计划/网络提交、36架变化、共鸣核心充能与仅升Lv.2、世界和区块重载。既有生产源码仅初始化类新增一次遗迹注册，其余附魔、GUI、核心、碰撞、配置保持原样。
- 新增纯Java选址测试：九点地形、坡度、海平面、埋深14–32及世界底界。服务端对3模板×4方向验证尺寸、容器、书架书本及occupied状态、刷怪笼类型、不可形成的缺件机器、螺旋每步头部空间、Piece序列化及书架物品序列化。
- 原生实际注册表小/高层箱子各抽2,000次，固定种子20261017，验证较高层收益、普通核心低概率与初始0点、不产生谐调核心。奖励仍是临时探索参数，不是完整经济平衡结论。
- 27组×100,000原附魔概率模拟重跑，28份报告与0.5.0的SHA-256逐文件相同，见[balance-comparison.json](balance-comparison.json)。没有借遗迹开发修改机器概率公式。
- 模板源重新生成后三份NBT的SHA-256不变；运行JAR含3模板/7战利品表，测试夹具与测试模组不打包。详见[summary.json](summary.json)、[templates.json](templates.json)、JUnit XML及[服务端XML](gametest-results.xml)。

## 正常世界自然生成

新建默认主世界、种子 **20261017**，开启原生结构生成，普通难度。通过原生findNearestMapStructure定位持久StructureStart，再加载对应区域；**没有使用/place、人工搭建或测试夹具替代自然生成**。相机用旁观者传送及夜视查看室内，不改建筑、地形、光源或战利品；夜视不改变刷怪光照。

| 放置ID | 模板原点 | 旋转 | 验证内容 |
| --- | --- | --- | --- |
| small_archive | 1688,66,1288 | 无 | 平原地表、完整墙顶、内饰、两箱、带书雕纹书架、无笼 |
| buried_archive | -568,46,1368 | 无 | 地表高度65，屋顶最高51，封闭房间及真实书本 |
| abandoned_workshop | -1672,65,-1640 | 180° | 三层、各层容器/书架、缺件机器、僵尸笼 |

原点不是玩家入口，旋转后范围见[natural-placements.json](natural-placements.json)。初次森林小遗迹(-184,65,1448)已通过但树叶遮挡较多，最终定位同一种子另一处平原自然生成建筑拍摄，没有清树或人工替换。三层工坊在真实客户端逐半格按前进键走完17个螺旋路径点，**未飞行、未跳跃**，还分别走出二、三层楼梯，进入房间后返回；附近实际出现原生僵尸。最后正常保存退出并重新打开同一世界，核对一组雕纹书架完整序列化数据逐字相等，并再次核对全部工坊内容，没有重新抽书。

| 场景 | 真实截图 |
| --- | --- |
| 小遗迹地表自然环境 | [外景](../../screenshots/ruins/ruins-01-small-surface-natural.png)、[正面](../../screenshots/ruins/ruins-01a-small-surface-front.png) |
| 小遗迹地表室内 | [研究角](../../screenshots/ruins/ruins-02-small-surface-interior.png) |
| 小遗迹地下 | [封闭室内](../../screenshots/ruins/ruins-03-small-underground-natural.png) |
| 中型工坊外观 | [外景](../../screenshots/ruins/ruins-04-workshop-exterior-natural.png) |
| 工坊一层 | [工作区与僵尸笼](../../screenshots/ruins/ruins-05-workshop-first-floor-spawner.png) |
| 工坊二层 | [中心台/两角紫水晶缺失](../../screenshots/ruins/ruins-06-workshop-second-broken-machine.png) |
| 工坊三层 | [藏书/管理者书房](../../screenshots/ruins/ruins-07-workshop-third-library.png) |
| 走完楼梯 | [到达三层](../../screenshots/ruins/ruins-08-spiral-walked-to-third-floor.png) |
| 保存重进 | [持久书架](../../screenshots/ruins/ruins-09-reloaded-shelf-books.png) |

这些截图来自最终模板的真实客户端复查；[screenshots.json](screenshots.json)保存原始输出文件名、时间和SHA-256。第8张是最后路径点面向楼梯轴柱的原始视角，具体通行由各路径点及进出房间的坐标断言验证。没有修图或用模型渲染图冒充游戏截图。

## 实际发现与修复

保留失败日志；只将最后成功运行列为验收结果。

1. 首次新GameTest编译3s失败：旧式ChunkPos(BlockPos)不适用26.1.2，查真实构造器后改为区块X/Z，未删测试。
2. 首次正常世界生成暴露ProtoChunk中的雕纹书架没有绑定Level；直接setItem会走依赖Level的原生交互状态更新并NPE。改为生成阶段填写原生库存列表，通过WorldGenRegion更新六个occupied属性。首次运行因异常无法继续而手动停止测试客户端。修复后真实区块生成、存档与重进验证通过，不靠只在ServerLevel上放模板的测试掩盖问题。
3. 第二次客户端运行2m39s失败：服务端控制台gamemode未指定玩家，相机仍受重力导致死亡。改为实际ServerPlayer.setGameMode并在取景前检查存活/无菜单；不修改生产逻辑。
4. 第三次2m48s失败：自然生成村庄地基覆盖了工坊刷怪笼。读取实际存档确认同区有village_taiga与工坊，给三张StructureSet添加原生minecraft:villages候选10区块exclusion_zone。没有禁用村庄，也没有在截图前人工补回笼子。
5. 遗迹定向客户端1m8s通过后，截图发现屋顶有悬空层，建筑源填实为连续木坡顶；调整二层及小遗迹取景，再运行完整7入口。螺旋步行、真实僵尸出现与存档重进断言保留。
6. 出口静态复核发现只沿螺旋行走的测试不够：原位置的外墙与围栏挡住楼层出口。将楼梯整体内移一格、开放东侧走廊，补测两层走出楼梯/进入房间/返回。小遗迹加浅门廊、扶壁与屋檐，仍保持12×10×6。再次执行最终完整回归；此前通过不冒充这些新检查的结果。

基线15s通过，初次接入18s通过，新服务端16s通过；完整最终命令及用时见[full-validation.log](full-validation.log)，失败与定向成功日志同目录。既有客户端各向异性选项/离线Realms认证日志仍存在，没有为隐藏提示改动客户端设置；未发现本模组缺资源或世界生成启动错误。

## 初版限制

这是一个种子的正常世界实测加四方向服务端模板测试，不代表所有种子、生物群系或整合包均已覆盖。森林树叶会邻接建筑，少量树冠可能被结构截断；坡地入口、低矮支撑台、与其他第三方结构的重叠仍须继续观察。地下遗迹是封闭房间，不提供直通地表入口，发现率待试玩。3×3楼梯可走但较窄；本轮未做最终建筑美术和奖励经济平衡。所有生成频率是候选区域设置，不保证固定距离或既有区块补生成。

没有新增第三种大型遗迹、研究台、Lv.3+、维度、Boss、原创附魔或特殊怪物机制。没有改既有GUI、字体、12点核心、指定30级服务端门槛或原版台禁用规则。本轮仅本地提交。
