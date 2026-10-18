# 中型遗迹 · 已通过审核并正式固化

2026-10-18，用户接受本目录的最新体块版本，进入结构与世界生成落地，不再继续修改外形。正式实现版本Differentenchant0.6.2。

- 模板`differentenchant:ruins/workshop`，放置结构`differentenchant:abandoned_workshop`。
- 23×18×20（宽×深×高）；局部(0,0,0)为模板原点，Y0为地基基准，默认正门向北/负Z；原生四向Rotation围绕模板原点变换。
- 中央三层研究厅、两侧两层研究翼、前凸门厅和四组高低屋面；三层书房进入主坡顶。不得继续重做体块、比例或材料方向。
- 接受时完整NBT SHA-256：`80c7bcb61f90dcfcf7586099eb9fbd2d4c6bf97a8b4a1fdc439cff4d58013a1a`。
- 固化后的几何SHA-256：`b7fcb92b0cba37b8564168fa4d095088b4489da73ac396651dfa5be85678f819`，覆盖尺寸、所有方块坐标和状态，忽略方块实体loot元数据。记录于`tools/ruins-locks.json`，生成脚本会核对。

正式落地只将三层两只辅助箱从study改为research，保留(12,11,6)一只主study奖励箱，因此完整NBT哈希变化，但所有方块坐标/状态和建筑外观与接受版本一致。正式内容、自然生成和测试见[RUINS.md](../../RUINS.md)与[0.6.2验证](../../validation/medium-ruin/README.md)。此前评审截图、失败记录和已否定方案仍保留历史意义。
