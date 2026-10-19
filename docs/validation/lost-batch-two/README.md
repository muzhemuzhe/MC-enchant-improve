# 第二批九种失落附魔 · 0.9.0 实际验收

2026-10-19，基线ba066da，本地最新源码。实现/参数/每种原版入口及玩法边界见[设计与状态](../../LOST_BATCH_TWO.md)。以下是实际执行，不把编译当作效果测试。

## 最终命令与结果

使用仓库.tools/jdk-25.0.4.1+1设置JAVA_HOME，JDK25，Minecraft26.1.2 / Loader0.19.5 / Fabric API0.155.3+26.1.2 / Loom1.18.4；[Java版本](java-version.txt)。[本轮官方Loader元数据](loader-metadata.json)，[官方API元数据](../research-gui/fabric-api.pom)、[官方Loom元数据](../research-gui/fabric-loom.pom)为同日已确认组合；原版方法依据本地Loom生成的官方无混淆依赖源码核对。

~~~powershell
$env:JAVA_HOME = (Resolve-Path '.tools/jdk-25.0.4.1+1').Path
.\gradlew.bat build -x test runGametest runClientGametest -PlostBatchTwo -PlostBatchIntegrated --console=plain
.\gradlew.bat runGametest -PlostBatchTwo -PlostBatchIntegrated --console=plain
.\gradlew.bat runClientGametest -PlostBatchTwo --console=plain
~~~

- [final-4.log](final-4.log)：1分30秒成功，最终生产代码构建、**25/25服务端**、新增效果客户端及原研究台客户端两入口全部通过。
- [final-safety-server.log](final-safety-server.log)：13秒成功；最后补充真实药水组件/不可拾取标记保留与独立旧存档夹具，25/25再次通过。[最终XML](final-server.xml)。
- [final-steering-client.log](final-steering-client.log)：45秒成功，纸鸢实际方向键微调补验（此后final-4已再次包括该用例）。最后补图仅调整看回桥面的相机位置，见[final-lookback-client.log](final-lookback-client.log)。
- 服务端25项=14个第二批项目+6个研究交互/协议/原生铁砧/持久化项目+3个原8种效果项目+1个未来版本文件保护+1个原版always_pass。没有反复执行历史概率/遗迹/机器全套，JUnit由-x test明确跳过。
- 默认测试模组入口已加入两个BatchTwo测试；专用Gradle参数只筛选必要范围，打包时恢复默认入口。测试代码不进入正式JAR。

## 每项如何验证

| 项目 | 实际触发和验收 |
| --- | --- |
| 借火 | 正常生存ServerPlayer真实attack，I/II火时长、连续无冷却、无自身火源不生成、免疫目标保留来源、火焰附加共存；客户端真实鼠标近战后自身熄灭、敌人着火 |
| 回羽 | 原生CrossbowItem.onUseTick装填2,400次；0级对照、三等级、普通/药箭/光灵箭/烟花、多重射击主弹一次决策、快速装填、换手后performShooting、原生INTANGIBLE_PROJECTILE/发射数量检查；实际客户端装填并发射 |
| 疾弦 | 原生releaseUsing射出实体，0/I/II/III满蓄力和提前一刻的速度/暴击；力量/冲击/火矢/无限/耐久共存；客户端拉满、继续保持使用不会自动发射，松手产生暴击箭 |
| 静步印 | ServerLevel.gameEvent真实分发，生存玩家和启用AI的监守者/感测体监听器正反对照；原生接收响应进入尖啸和监守者振动冷却。GameTest显式调用原版接收体以避开测试服调度差异；客户端实际走路不激活、疾跑激活感测体及尖啸链路。其他GameEvent不被过滤 |
| 纸鸢 | 三等级水平阻尼与Y不变、飞行/无潜行/无附魔排除；客户端空中按潜行的连续截图和真实坐标/速度，实际右方向键改变Z落点；配置由服务器同步 |
| 格挡 | 实际箭tick进入原生命中Mixin；普通/带毒药箭/光灵箭，原实体ID/弹药完整组件及拾取标记保留，近地出口向上且下一刻不撞地，侧面/窗口失败走原版，II窗口更长；真实客户端低处来箭反弹向量/位置/归属断言 |
| 逆潮 | 真正三叉戟命中方块、实际活体位移和40刻过期；测试服显式驱动生产效果tick，客户端用正常服务器tick验证。实际原生TridentItem投掷扣一件、忠诚回收一件、激流干燥拒绝/水下旋转无发射物；客户端忠诚回收也断言单份 |
| 倒影 | 实际水体状态、标签限定、半径和8处上限/离水排除；客户端石墙遮挡前后原生发光轮廓可见，出水后立即清除 |
| 星桥 | 由自然支撑→离地跑动触发三实体，原生MoverType.SELF真实落脚、其他玩家穿过、斜向连续逐格碰撞、不可拾取/无地形写入、超时/摘靴清理、noSave、NBT冷却；真实客户端跑过三格空隙站到对岸，磁盘重进无幽灵实体、冷却仍存在 |
| 研究/适配/保存 | 九种新书实际原生铁砧施加，17种目录的旧铁砧用例、原8种效果、未知书/直接结果取物/卷轴/记录中英滚动/断线重进完整通过；17种仍排除原普通随机候选；扩池与已知答案稳定性逐项检查 |

数据/实体/物品由测试设置夹具，真实触发走生产原版入口；没有用客户端预览假物品或粒子替代服务器产物/星桥碰撞。摄像机和初始传送只用于可复现演示，星桥跨越过程没有传送跳过缺口。倒影轮廓是仅本客户端的无交互显示，星桥则是服务器实体。

## 回羽概率与弹药守恒

| 等级 | 正常装填次数 | 实际节省次数 |
| --- | --- | --- |
| 0 | 600 | 0 |
| 1 | 600 | 314 |
| 2 | 600 | 394 |
| 3 | 600 | 445 |

这些是独立的正常装填，非同一已装填弩重复退款。期望节省率分别0、1/2、2/3、3/4，允许有限随机采样误差。特殊弹药正常消耗；多重射击副弹依旧原生不可回收。原始[ammo.csv](ammo.csv)。

## 旧存档迁移与池分布

[迁移报告](migration.txt)：96条旧固定答案和UUID发现，在追加九输出、幂等迁移、JSON及原生NBT重载后全部不变。夹具先清空其他测试的结果缓存再生成旧八项目录的96条固定结果，避免并发用例污染旧存档模拟。

实际枚举8,256个原版单条书等级无序组合：6,151条未固定答案在扩池后变化；每条配方的获取层和卷轴分支与旧目录相同；A+B与B+A和重复解析一致。相邻种子7,801条结果不同。没有修改ResearchPolicy、阈值或池权重。

| 后台分布层 | 旧八项目录次数 | 扩充目录次数 |
| --- | --- | --- |
| I | 3451 | 3451 |
| II（含卷轴） | 3398 | 3398 |
| III | 1163 | 1163 |
| IV | 244 | 244 |
| 卷轴另列 | 457 | 457 |

新九项在同一组配方中的次数：

| 新附魔 | 次数 |
| --- | --- |
| 静步印 | 678 |
| 借火 | 673 |
| 回羽 | 624 |
| 疾弦 | 586 |
| 纸鸢 | 579 |
| 格挡 | 239 |
| 逆潮 | 242 |
| 倒影 | 211 |
| 星桥 | 127 |

全部17种都可由正常原版书输入研究。报告含96条迁移保留的旧固定结果，不应把有限样本的条目频数当作精确概率。完整[旧目录](legacy-pools.csv)、[扩充目录](expanded-pools.csv)、[相邻种子](second-seed-pools.csv)。这些后台统计没有发到游戏GUI。

## 33张真实客户端截图与连续运动

在docs/screenshots/lost-batch-two中保留28张效果/Tooltip/重进截图（包含看回已跨过桥面的补图）及5张研究台截图。均为真实OpenGL客户端PNG，未修改截图；没有视频编码，纸鸢使用连续帧与CSV作为用户允许的替代。

| 演示 | 图像 |
| --- | --- |
| 自身熄灭、目标燃烧 | [借火](../../screenshots/lost-batch-two/lost2-01-borrowed-flame.png) |
| 装填节省与随后发射 | [装填](../../screenshots/lost-batch-two/lost2-02-returning-feather-loaded.png) · [发射](../../screenshots/lost-batch-two/lost2-03-crossbow-fired.png) |
| 完整拉弓与松手 | [拉满](../../screenshots/lost-batch-two/lost2-04-swift-draw-full.png) · [释放](../../screenshots/lost-batch-two/lost2-05-swift-draw-shot.png) |
| 纸鸢连续帧 | [0](../../screenshots/lost-batch-two/lost2-06-kite-frame-0.png) · [4](../../screenshots/lost-batch-two/lost2-06-kite-frame-4.png) · [8](../../screenshots/lost-batch-two/lost2-06-kite-frame-8.png) · [12](../../screenshots/lost-batch-two/lost2-06-kite-frame-12.png) · [方向微调后的落点](../../screenshots/lost-batch-two/lost2-06b-kite-steering.png) |
| 无附魔空中对照 | [0](../../screenshots/lost-batch-two/lost2-06-kite-control-0.png) · [4](../../screenshots/lost-batch-two/lost2-06-kite-control-4.png) · [8](../../screenshots/lost-batch-two/lost2-06-kite-control-8.png) · [12](../../screenshots/lost-batch-two/lost2-06-kite-control-12.png) |
| 石墙后容器的克制轮廓 | [无头盔](../../screenshots/lost-batch-two/lost2-07-reflection-without-helmet.png) · [倒影生效](../../screenshots/lost-batch-two/lost2-08-reflection-hidden-container.png) |
| 低箭精准格挡 | [实际举盾与反弹检查时刻](../../screenshots/lost-batch-two/lost2-09-parry-low-arrow.png) |
| 真实短漩涡与牵引 | [刚命中](../../screenshots/lost-batch-two/lost2-10-undertow-active.png) · [牵引中](../../screenshots/lost-batch-two/lost2-11-undertow-pull.png) |
| 静步/疾跑正反对照 | [未激活](../../screenshots/lost-batch-two/lost2-12-hush-step-sensor-inactive.png) · [疾跑激活](../../screenshots/lost-batch-two/lost2-13-sprint-vibration-control.png) |
| 新九种书/实际等级/无稀有度 | [英文Tooltip](../../screenshots/lost-batch-two/lost2-13b-new-books-tooltip-en_us.png) · [中文Tooltip](../../screenshots/lost-batch-two/lost2-13b-new-books-tooltip-zh_cn.png) |
| 星桥实际跨越 | [边缘生成](../../screenshots/lost-batch-two/lost2-14-star-bridge-edge.png) · [走在桥上](../../screenshots/lost-batch-two/lost2-15-star-bridge-walking.png) · [抵达对岸](../../screenshots/lost-batch-two/lost2-15b-star-bridge-far-ledge.png) · [回看桥面补图](../../screenshots/lost-batch-two/lost2-15c-star-bridge-crossed-lookback.png) |
| 保存/退出/重进无幽灵桥 | [重进](../../screenshots/lost-batch-two/lost2-16-reloaded-no-ghost-bridge.png) |
| 研究台交互原样保留 | [未知](../../screenshots/lost-batch-two/research-gui-01-unknown.png) · [悬停???](../../screenshots/lost-batch-two/research-gui-02-unknown-tooltip-en.png) · [已知预览](../../screenshots/lost-batch-two/research-gui-04-known-preview-en.png) · [已知Tooltip](../../screenshots/lost-batch-two/research-gui-05-known-tooltip-en.png) · [发现记录](../../screenshots/lost-batch-two/research-gui-11-records-zh.png) |

[客户端断言报告](client.txt)、[实际运动坐标/速度](kite-trajectory.csv)。CSV中的tick列是脚本采样标签，截图动作会额外推进游戏刻，不把它当严格绝对tick时间轴；两组使用相同采样/截图步骤。首帧水平速度并非完全相同，因此不把总位移差当精确手感结论；逐帧Y/纵速相同，III水平衰减更快，后续向右移动输入确实改变Z。截图可能在测量后落地，CSV记录的是当时实际客户端状态。

## 边界与保留记录

- 没有两台独立客户端高延迟/丢包测试、长期多人压力或完整整合包/着色器兼容测试；多人服务端检查与真实本地集成服不能冒充这些覆盖。玩法强度与视觉效果仍待用户试玩。
- 空间与作用时限受服务器限制；星桥不写地形且noSave，倒影只发送近处最多8点，不主动加载区块。无操作系统断电跨文件原子保存保证。
- 最终JAR验证见[artifact-audit.json](artifact-audit.json)，102个非版本资源与源码逐字节一致，无测试类，版本0.9.0。SHA-256为633c1d0d306989614ed0d4f43eaf77c9fb97a48ebedf8c5443c75a0b4ee03810。
- 初期编译修正protected random访问、26.1移除的hasImpulse及共享SavedData辅助方法访问范围。最初火焰测试复用有受伤无敌帧的目标，改为正确清除该测试状态；NoAI目标不移动，牵引改用原生有物理的盔甲架；40刻持续场需要超过默认20刻测试上限。模拟玩家固定CREATIVE使监守者拒绝目标，改为正常生存ServerPlayer；并发场景计数改为按主人隔离，不清空其他测试的场。
- 纸鸢首轮截图数量不同造成时间轴不公平，修为相同截图采样，并以实际轨迹说明初速度边界。星桥先通过碰撞再加原版透明显示面，最后补斜向真实碰撞与回看桥面的图；没有把最早仅粒子画面当最终外观。
- 所有中间失败日志保留：compile-*、group-one-*、group-two-*、server-*、client-*及final-1/final-server等；最终成功以final-4/final-safety-server/final-lookback-client为准。version999错误堆栈是预期保护测试，原文件不覆盖，不是未解决异常。

原始日志保留运行器的行尾空格，源码/文档差异检查排除原始.log文件，不改写历史输出。最终按Fabric开发警告补齐新增物品/方块标签的中英文翻译（供配方查看器等模组显示），只重新打包资源，没有改变已验证的玩法代码。
