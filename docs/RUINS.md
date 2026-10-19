# 主世界遗迹（固定建筑与灯笼原型）

0.11.0仅在中型study主奖励表追加1/12失落卷轴池；要塞图书馆独立1/3作为主要来源，小型与research辅助箱不添加。全部遗迹NBT、几何与世界生成保持原样，当前来源与原生抽样见[两机结构文档](WORKSTATION_STRUCTURES.md)。下文保留0.7建筑验收记录。

已接受的两种建筑：小型附魔研究屋（地表/地下两个模板）和中型废弃附魔研究馆，外观继续锁定。0.7.0另按用户要求新增[大型灯笼原型](LANTERN_RUIN.md)，与百格宫殿规划分开；没有研究台、新维度、新怪物种类或附魔机制改动。下文旧建筑的尺寸、功能、奖励及验证记录保持原意。

## 注册与模板

| 放置ID | 原生模板 | 宽×深×高 | 自然位置 | 候选spacing/separation |
| --- | --- | --- | --- | --- |
| `differentenchant:small_archive` | `differentenchant:ruins/small_surface` | 13×11×10 | 主世界地表 | 20/8区块 |
| `differentenchant:buried_archive` | `differentenchant:ruins/small_buried` | 12×10×6 | 主世界地下 | 24/10区块 |
| `differentenchant:abandoned_workshop` | `differentenchant:ruins/workshop` | 23×18×20 | 主世界地表 | 36/16区块 |
| `differentenchant:lantern_sanctum` | `differentenchant:ruins/lantern_sanctum` | 33×33×26 | 主世界地表 | 48/24区块（临时） |

原生NBT在`src/main/resources/data/differentenchant/structure/ruins/`，DataVersion4790；`worldgen/structure/abandoned_workshop.json`引用当前workshop模板，使用原有`differentenchant:ruin` StructureType和PieceType；`worldgen/structure_set/abandoned_workshop.json`提供原生random_spread放置。沿用已存在的接线，没有新增一套并行生成器或重复注册类型。

`tools/generate-ruins.mjs`是可复现的原创建筑源；`ruins/blueprints.json`记录容器、楼梯、出入口、检查点和取景坐标。已接受的几何指纹在`tools/ruins-locks.json`，`tools/verify-ruins.mjs`解读真实压缩NBT，核对尺寸、所有方块坐标/状态、容器与书架内容接线、森林/平原范围和中型更稀疏的配置。生成脚本完成后自动验证；Gradle和玩家运行不依赖Node.js。

## 固定原点、朝向与空间

模板局部原点(0,0,0)，Y0为地基基准；默认正门朝北/负Z，中型门下半部位于(11,1,1)。引擎围绕模板原点应用NONE/CLOCKWISE_90/CLOCKWISE_180/COUNTERCLOCKWISE_90，旋转后的包围盒可延伸至锚点负方向，不把锚点误当所有朝向的西北角。门、箱子、玻璃、床与楼梯方块状态由原生模板系统旋转；放置位置与Rotation由原生Piece保存。

中型实际外轮廓约23×17×19.5。中央三层研究厅x6–16/z4–15，两侧两层研究翼x1–6和x16–21/z7–15，门厅x9–13/z1–3。中央前出、侧翼退后，各自山墙屋顶，第三层进入主坡顶；浅木主体、石英轮廓、少量石基和单块外侧紫水晶标记。固定外观见[接受记录](reviews/medium-research-massing/ACCEPTED.md)。

楼层脚面Y1/6/11。一、二层净高四格，三层随斜顶抬高，局部木梁下约三格。后侧3×3螺旋20次半格上升，共21个路径点；二层南出口、三层西出口连通房间。两翼一、二层均有实际拱口连接主厅，第三层不延伸到翼部屋顶。

- 一层危险层：前门厅/公共前厅，西翼旧实验室内唯一**原版僵尸刷怪笼**、废弃桌/未燃蜡烛、旧书架/蛛网，东翼接待/阅读与工具区；3只普通箱。
- 二层核心研究层：中轴缺件3×3×2机器、地面嵌纹/研究大窗，西档案翼、东材料/酿造翼，4组雕纹书架、书桌/讲台/桶、2只研究箱。机器有9块磨制深板岩、4块黑曜石、2块紫水晶；中心(11,7,8)附魔台与(10,7,7)/(12,7,9)两角紫水晶缺失，不能组装。
- 三层私人/奖励层：坡顶书房、床、桌椅、讲台、地毯、普通书架及2组高等级雕纹书架。**(12,11,6)为唯一主study奖励箱**，另两只辅助箱使用research。箱子坐标、家具和外观均未移动。

小屋固定NBT SHA-256仍为`d0ce4b86d9f298e160e8258dbcdd315574f59706251447a8c4dc025dffb63d2c`。中型几何SHA-256为`b7fcb92b0cba37b8564168fa4d095088b4489da73ac396651dfa5be85678f819`。本轮只调整两只辅助箱的LootTable元数据，完整中型NBT现为`123d8b165c060244f38fdda5aa1b0f357eaa73f73dbbfc319bee2a415e035835`，几何指纹与接受版本相同。

## 世界生成与地形

适用范围由`#differentenchant:ruin_land`管理：平原、向日葵平原、原版`#minecraft:is_forest`、`#minecraft:is_taiga`和草甸。已核对固定26.1.2原生is_forest数据，包含森林、白桦森林、古老白桦森林等；不把第三方森林写成固定Java名单。

中型候选区域间距36区块（576格），separation16区块（256格）；小型为20/8。在相同筛选条件下中型候选密度约为小型31%，实际还受生物群系、坡度和避让影响，不保证固定距离或每个候选区域必有建筑。三种放置均保留原生exclusion_zone，避开村庄候选10区块；salt分别1017341/1017342/1017343。

地表按旋转后的模板包围盒进行九点噪声地形采样，选择最高采样地面；不低于原生海平面，最大坡差4格，避免明显悬崖/水面。实际石砖地基单元向下最多支撑6格。地下档案室位于最低采样地面下14–32格，坡差不超过12格。没有生产代码中的主动区块加载、加载事件搭建或地形大平整。

普通新世界种子20261017已验证：中型原点(-1672,65,-1640)，CLOCKWISE_180，平原，原生结构开始记录/实际生成，未调用place模板代替。自然实例可进入、可走楼梯/两翼/三层；原生笼实际产生僵尸，保存退出重进后8箱和6书架完整库存相同。更多种子、森林树木紧贴和坡地门口仍需后续试玩，不声称所有地形都完美。已生成区块不会主动重建或重抽内容。

管理员查找：`/locate structure differentenchant:abandoned_workshop`；小型使用`...:small_archive`/`...:buried_archive`。开发调试可用原版`/place structure`，但本轮自然生成验证没有用它替代原生生成。

## 战利品与书架内容

保留七张原生loot_table：small/common/research/study箱子表与books_small/books_medium/books_high书架表。常规材料为书、纸、青金石、少量铁锭、经验瓶与随机附魔书；原奖励主体保留；0.11仅study追加失落卷轴，无免费谐调核心。

| 箱子tier | 本中型数量 | 原版附魔书power区间 | 独立铭纹石机会 | 独立普通核心机会 |
| --- | --- | --- | --- | --- |
| common | 一层3 | 10–20 | 1/12 | 无 |
| research | 二层2、三层辅助2 | 16–26 | 1/8 | 无 |
| study | 三层主奖励1 | 22–30，附魔书权重更高 | 1/4 | 1/50，初始能量0 |

每箱另有独立1/32青金石块机会。随机奖励允许波动，不保证每次高级箱必出稀有物。这些是原生结构loot函数的临时参数，不是本模组附魔机器概率模型，也没有修改12点核心规则。

雕纹书架生成时通过上方DATA标记抽2–4本真实附魔书，分配互不重复的原生槽并同步occupied属性；二层4组用15–25、三层2组用22–30原生附魔power。`enchant_with_levels`查询`#minecraft:in_enchanting_table`，不硬编码原版附魔名单。原生结构按区块初始化，不在玩家打开/取书/重进时重新抽取；书、箱子内容由正常方块实体存档。生成中ProtoChunk无Level时直接填原生库存并由WorldGenRegion更新占槽，避免调用实时setItem路径。

## 本轮验证与产物

仅2项遗迹地形JUnit、2项项目遗迹服务端GameTest（另1原版测试），以及平地四向客户端与普通世界自然生成客户端；未运行随机附魔概率、GUI、升级全套回归。固定建筑、门朝向、原生容器/书架、缺件机器、实际通路、真实刷怪和14个库存磁盘重进均有专项结果。曾有一次测试代码Gson重载推断编译失败，修正后全部指定检查通过，原失败日志保留。

0.6.2产物`build/libs/differentenchant-0.6.2.jar`，原生模板与源文件逐字节一致、测试类不打包。七张交付截图和日志/指纹/确切命令见[本轮验证](validation/medium-ruin/README.md)。旧候选、否定记录及0.6.0/0.6.1日志截图保留历史意义；当前不继续改外观，不自动开发大型遗迹或下一阶段。
