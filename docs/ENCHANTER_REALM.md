# 附魔者维度 · 第一阶段（0.14.0）

附魔者维度 / Enchanter’s Realm 是真实的原生噪声维度原型。连续大陆、丘陵山脊、河流与低洼水体为主体，少数独立高空地貌点缀其上。此阶段只建立世界与基础视觉，没有正式生存入口、矿物玩法、生物、遗迹、附魔或机器升级。

[实际客户端截图、测试命令与限制](validation/realm/README.md)。美术仍待人工审核，青木是开发代号。

## 进入与返回

管理员权限等级 2，玩家执行：

```text
/differentenchant dimension enter
/differentenchant dimension return
```

`enter` 先用生成器高度查询检查最多 13 个固定大陆候选点；高度查询不请求区块。选定后只显式请求一个 FULL 目标区块，随后在该区块的有限 16×16 区域检查干燥支撑、两格头部空间、世界边界和碰撞。过滤树叶、树冠原木及危险地面/植物/火焰。原生区块管线会准备正常生成所需依赖，不添加持久票、强加载集合或后台搜索。找不到安全点就失败，不填地、不清空方块，也不把玩家丢到空中。首次进入可能有一次正常地形加载停顿。

服务器以 UUID 保存来源维度、双精度坐标及视角到世界级 `data/differentenchant/realm_return.dat`，成功传送才保存。已在境域内重复 `enter` 不覆盖来源。`return` 优先恢复精确位置；原位置受阻时只在原坐标区块内寻找安全支撑，无安全位置则保留锚点并报错。原维度缺失或传送失败也保留锚点。成功返回才删除记录。没有记录时尝试主世界原点附近的一个区块，仍遵守相同检查。返回记录按世界隔离，复用既有原生 SavedData 的损坏文件保护，不覆盖无法读取的旧文件。

成功往返使用原生零速度传送，并显式清理此前累计的摔落距离，避免管理员在坠落中测试入境后继承旧摔落伤害。

没有传送门、入口物品、机器等级绑定、自动触发或生存权限放宽。床不能在无夜晚境域中设重生点，开发阶段使用返回命令。

## 注册清单

| 类型 | 注册 ID | 作用 |
| --- | --- | --- |
| Dimension / DimensionType / NoiseSettings | `differentenchant:enchanter_realm` | 独立世界、天空与密度路由 |
| Biome | `differentenchant:mint_fields` | 青绿原野，约三个区块一次树木尝试 |
| Biome | `differentenchant:azure_woodland` | 青林，每区块七次树木尝试，受空间筛选 |
| Feature / ConfiguredFeature | `differentenchant:azure_tree` | 专用有枝青木生成器 |
| Block + Item | `differentenchant:mint_turf` | 薄荷草地表，保留 GrassBlock 原生行为 |
| Block + Item | `differentenchant:realm_loam` | 灰紫褐沃土，地表下约三层 |
| Block + Item | `differentenchant:realm_chalkstone` | 青灰岩体，山地露岩 |
| Block + Item | `differentenchant:azure_log` | 浅青灰原木，支持 X/Y/Z 轴 |
| Block + Item | `differentenchant:azure_leaves` | 深青蓝叶，原生叶距/衰败/透光 |

放置特征还有 `field_trees`、`woodland_trees`、`meadow_grass`、`meadow_flowers`、`ivory_flowers`、`pink_flowers`。噪声参数为 `land`、`hills`、`ridges`、`river`、`islands`、`climate`；二维大陆高度缓存为 `differentenchant:land_height`。JSON 位于 `data/differentenchant/dimension*` 与 `worldgen/`。只向原版工具/土壤/原木/树叶方块标签追加新方块；不覆盖原版维度、噪声设置、生物群系或结构标签。

## 地形与生态

原生 `minecraft:noise` 生成器、`minecraft:multi_noise` 生物群系源和数据驱动密度函数。世界高度 -64..319，海平面 63，底部基岩。

大陆高度由大尺度起伏、较小丘陵及平方山脊噪声组合；独立河谷噪声把低地切向约 56 米的缓变河床，原生水填充海平面以下空间，形成河道和洼地水体。没有复制主世界密度/洞穴路由后仅换草色。当前地下为实心岩层，没有新增洞穴、矿物或刷怪生态。

高空地貌是第二个原生密度叶瓣，与大陆密度取最大值：二维高阈值区域噪声减去围绕约 Y=190 的平方高度项。区域边缘与上下轮廓逐渐收尖，原生跨区块采样/插值负责连续性；没有随机撒方块、tick 扫描、装饰期搭建平台或逐区块截断岛屿。地表规则同样覆盖浮岛，合适时自然生成草、花和青木。浮岛频率是噪声面积比例，没有每 N 块必有一岛的承诺。

湿度连续噪声在 0.05 附近划分原野与青林，其他气候轴覆盖完整范围。两区共用地形、草地和装饰类型，仅树木密度及原生小草颜色轻微变化，边界主要表现为疏密变化。薄荷草本身使用固定色像素纹理，**尚未实现草方块按生物群系逐像素变色或额外植被种类**。原生小草为淡黄绿，两区共用绒球葱、粉色郁金香和滨菊做少量点缀；水为蓝绿。

Y>=122 的部分噪声斑块露出灰岩，土层与草地分色，避免每座山完全同色。新增七张 16px 纹理是原版纹理的临时调色资源，可由 `tools/generate-realm.py` 重建；不是最终原创美术。来源见 [THIRD_PARTY_NOTICES](../THIRD_PARTY_NOTICES.md)。

## 青木生成边界

`AzureTreeShape` 是只依赖种子的普通 Java 几何服务。树干约 8–11 格，有最多一格偏移、连通木质拐点；四向枝桠的长度/高程/朝向随机，横枝末端起叶，中心及枝端各有不规则圆形两层叶簇。上下叶层、偏移和缺角构成错层树冠，少量一格外露根。不是原版橡树生成器或立方体叶冠。没有大型古树、粗直柱或几十种巨树。

`AzureTreeFeature` 负责土壤标签、全形状预检、合法写入窗口和提交。半径最多 7 格、最高点不超过基部 14 格；遇到水、固体、既有树叶或空间不足整棵拒绝，不清除原地形或相邻树冠。只使用 WorldGenRegion 正常依赖窗口。木质连通与枝轴由测试覆盖；叶距 1..6，支持砍树后的原生衰败，不产生持续叶粒子。暂时没有树苗/木板/木材经济，基础方块可以从自然环境采集或创造栏测试。

后续古树可增加独立形状服务和配置，保留同一个预检/写入职责边界；当前没有未使用的巨树桩代码。

## 天空

按 26.1.2 官方源码使用 `has_fixed_time`、空 `timelines`、不绑定 `default_clock`，以及原生 `EnvironmentAttributes`。固定青蓝天顶 `#88c9e0`、淡紫远雾 `#c9c7e7`、柔和云色、Y=224 云层、15 级天空光，环境亮度 0.16。原版天空/雾混合形成渐变，没有额外全屏 shader、天空纹理或大量粒子。普通云层仍会缓慢移动。

唯一客户端接入 `RealmSkyMixin` 在原生 `SkyRenderer.renderSunMoonAndStars` 入口按当前维度 ID 跳过日月星体绘制，保留原生天空底色、云和雾。主世界、下界和末地不走此条件。主世界时钟可正常切到午夜，境域仍保持明亮。没有魔法光带、极光、定制云网格或复杂渲染管线，这些只属于后续方向。

## 验证与存档范围

构建和原生服务端/客户端入口为 `tools/validate-realm.ps1 -Stage build|server|client`。`-Prealm` 只选择专项测试，不进入正式 JAR。客户端新建正常主世界预设的测试世界，种子 2026102001；截图没有使用 `/place`、手搭场景或 Realm 方块改写。摄影仅调整玩家视角、创造飞行和界面隐藏。

测试存档位于忽略的 `build/client-gametest/saves/`，没有修改 `run/` 或任何已有玩家存档。此维度首次公开，生成参数仍处于美术审查阶段；下一阶段修改密度时应建立新开发世界，已有生成区块不会自动重写。当前验收不代表多版本升级后的新旧边界承诺。
