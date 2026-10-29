# 附魔者维度矿物与附魔采集 · 0.19.0

三种资源依赖手中的魔法，不依赖镐或工具材质。仅增加矿物、基础材料和采集规则；大陆/悬岛/深渊密度、缓坡、树木、天空、原有附魔玩法保持。没有矿物工具、装备、加工、冶炼、升级配方、新附魔或新进度。

## 注册与规则

下列ID均以`differentenchant:`开头。三种方块有对应同ID的BlockItem供创造模式放置；正常生存采集产出表中基础材料，不掉方块本体。

| 方块 / 名称 | 基础材料掉落 | 合格附魔（I及以上） |
| --- | --- | --- |
| `luminous_stone` / 浮辉石 / Luminous Stone | `luminous_fragment` 浮辉石碎片 / Luminous Fragment，1份 | 保护、火焰保护、爆炸保护、弹射物保护、摔落缓冲 |
| `harmonic_silver_ore` / 谐银矿 / Harmonic Silver Ore | `raw_harmonic_silver` 谐银原矿 / Raw Harmonic Silver，1份 | 火矢、火焰附加、火焰保护 |
| `rift_crystal_cluster` / 裂空晶簇 / Rift Crystal Cluster | `rift_crystal` 裂空晶体 / Rift Crystal，1份 | 正式`lost_enchantments`标签中的任意失落附魔 |

保护和火焰使用`mines_luminous_stone`、`mines_harmonic_silver`两份原生enchantment标签，可由数据包追加。火焰保护同时属于两者，允许同时采两矿。裂空直接复用已有`lost_enchantments`，其四个分级标签仍是正式分类来源；不维护17项名称副本，也不把原版treasure/mending等当作失落魔法。

`EnchantmentGate.allows(stack, tag)`是可复用资格接口。读取该物品的`ENCHANTMENTS`及`STORED_ENCHANTMENTS`两种原生组件，检查已绑定的正式注册Holder及标签、正等级（原生组件合法范围1..255）。没有额外最高等级门槛，不要求物品属于任何工具类别。附魔书、主手持有的护甲/武器、带原生组件的模组物品均可；不读取穿戴槽、背包或副手，也不读取名称、Tooltip或光效。

## 服务器提交与开采速度

原生`ServerPlayerGameMode.destroyBlock`在实际移除成功后，向`playerDestroy`传入当次主手的服务器快照。`EnchantedHarvest`只在这条玩家提交路径、资格符合且非创造/旁观时生成一份资源；快照在原生耐久损坏前保留，因此最后一点耐久的有效物品仍能取得该次资源。换成无效物品后完成破坏不能继承先前资格，没有自定义客户端掉落请求。

所有通用`getDrops`路径强制为空，并配套三份空基础block loot table。爆炸（即使由合格玩家引发）、活塞破坏、流体/环境移除、失去支撑、`/loot`或含玩家/工具的普通loot上下文均不会获得材料。精准、时运不能绕过门槛；合格时同样固定一份，当前不另给矿石本体、额外数量或经验。`Block.popResource`保留原生block_drops游戏规则。

方块不设置`requiresCorrectToolForDrops`，也不添加材质/镐级别标签。两个矿石及晶簇共享进度：合格1/30每刻（理想30刻/1.5秒），不合格1/90（约4.5秒）。刻数不受工具材质、原版效率或飞行/水下挖掘惩罚影响，方便后续单独平衡；网络延迟可使实测略长。客户端复用同一原生同步标签显示进度，服务器仍做最终资格判定。未改其他方块挖掘速度。

## 自然生成参数

只在本维度的`mint_fields`和`azure_woodland`加入新特征，不改原植被stage9或任何密度数据。`realm_ore`继承原生OreFeature的三维矿脉算法，另加明确维度守卫；`realm_ore_replaceables`只含原有`realm_chalkstone`和`realm_slate`，不能向空气、水或土壤中撒矿。

| 放置特征 | 每区块尝试 | Y均匀范围 | 配置矿脉大小 / 临空丢弃概率 |
| --- | ---: | --- | --- |
| `luminous_mainland` | 12 | -48..112 | 7 / .25 |
| `luminous_islands` | 7 | 136..224 | 7 / .25 |
| `silver_deep` | 6 | -48..48 | 5 / .15 |
| `silver_islands` | 4 | 128..204 | 5 / .15 |

configured feature为`luminous_stone`、`harmonic_silver_ore`。矿石位于原本存在的岩体中，能适配大陆深处、有限岛体和天然断面；频率是尝试数，遇到虚空/非岩石会失败，不保证每区块固定矿量。

`rift_crystals`位于stage7，每区块1/3概率执行一次有界扫描：最多12根本区块内部柱，Y238降至36，最多放3簇。下向表面要求支撑Y≥132，侧壁要求Y≤96，少量上向岩棚要求Y≤80并额外1/8筛选。只尝试现有岩石邻接的干燥空气，检查`ensureCanWrite`及真实支撑。支撑XZ局部2..13、晶簇在1..14，写入及支撑都在本区块内，不强加载相邻区块，不做世界tick扫描。随机源来自原生特征种子。

晶簇继承26.1.2原生AmethystClusterBlock的六向FACING、旋转、镜像、WATERLOGGED与支撑更新；采用独立模型/纹理，并非换色紫水晶模型。支撑失效直接成为空气，不发资源；矿石活塞反应BLOCK，晶簇DESTROY但无环境掉落。没有晶簇自动生长/再生、附带母岩或隐含资源复制循环。

## 资源与扩展范围

`tools/mineral_resources.py`生成三类定义、六张原创16px像素纹理和独立多棱晶刃模型，未读取/采样其他图片或模组资源。`generate-realm.py`最后调用它，因此完整资源重跑不会抹掉矿物。方块放入NATURAL_BLOCKS，三种材料放入INGREDIENTS，均有中英文名称；没有配方/用途占位实现。

采集规则集中在Gate及Harvest；方块只声明类别和资源，生成代码单独分为原生矿脉守卫与晶面特征。未来扩展附魔集合只需标签；未来容器可复用资格接口，本轮没有实现任何容器系统。

旧区块不补矿、不改写；旧世界可正常加载，新资源出现在新生成区块。实际0.18测试存档副本已验证：原区块(0,32)无新增矿物，远处新块(256,256)自然出现78个新矿物方块，原岩层指纹保持；原测试存档文件哈希不变。

[实际测试、原始截图与已知限制](validation/minerals/README.md)。底部摄影使用原版夜视，场景没有放置矿物、清空岩层或加照明方块；库存展示使用明确标注的测试物品夹具。
