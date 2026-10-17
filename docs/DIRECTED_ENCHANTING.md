# 第四轮指定附魔原型

铭纹石：**Runic Stone / 铭纹石 / `differentenchant:runic_stone`**。默认堆叠64，暂用 RARE 名称颜色和原创16×16石片/刻纹图；不代表最终掉落概率与经济。通过创造材料栏或 `/give @s differentenchant:runic_stone 5` 取得。本轮没有配方、遗迹或要塞战利品注入。

## 操作

1. 右击完整机器打开现有原版风格界面。点击 Directed / 指定附魔。
2. 左槽放一件装备，第二槽放铭纹石。可用已经随机附魔过的装备；换模式不转换材料，需要自行取回青金石再放铭纹石。
3. 右侧滚轮浏览附魔池。每行直接显示规划后的名称与等级：锋利 III 点一次 `+` 显示锋利 IV，点一次 `−` 回到锋利 III；没有原附魔时只显示名称，分配后显示 I。不再显示箭头路径。长名称截断时优先保留等级后缀；悬停可看注册说明、下一步费用及禁用原因。超长名称按 Shift+滚轮完整读取。
4. `+` 分配一颗石头的一步升级；`−` 撤销同一附魔最后规划的一步。没有时从 I 开始；已有时逐级提升；不超过原正常最大等级，不允许与原有或计划附魔冲突。
5. 下方显示已分配/实际石头数和实际经验等级合计。按界面修正要求，不再显示“需30级”的提示；现有服务端门槛保持。点击 **Enchant / 附魔** 才一次扣费并修改物品；没有“确认”或“应用”按钮。

每个池项复用原版棕纸横条，悬停提亮并加浅金像素边。未分配时减号灰置，达到上限时隐藏加号，铭纹石额度用尽或附魔互斥时加号灰置；取消步骤后收到服务端新快照即恢复可分配的加号。没有新增假槽位、测试数据或现代风格面板。

普通指定 tooltip 宽164 GUI像素、正文最多5行；超长注册说明默认只露出简短标题与 Shift 提示，Shift 时分页读取完整文字。提示固定在池与费用区域下方，不遮住候选、加减或附魔按钮；会临时遮住部分背包，这与物品悬停提示一样仅在悬停时出现。注册表 description 用作名称/说明来源，不硬编码原版效果或假定第三方必有独立效果说明。完整文字不因视觉截断而丢失。

五颗最多五步。取走部分石头会撤销最后分配的超额步骤，补回石头只恢复可分配额度；原计划不会暗中自动恢复。换装备、物品组件变化、关闭/重进、配置/数据包 reload 清空未支付规划。机器等级变化保留合法目标但重算费用。经验不足可先规划，但最终按钮不可执行。

指定模式对创造玩家也按颗数和经验等级支付。测试可使用 `/experience add @s 60 levels`；这里的 levels 是经验等级，不是经验点数。

## 临时费用：每一步5–8经验等级

令原注册权重 w≥1、本步目标 L、注册正常最大 M、机器等级 T；M=1 时 x=0，否则 x=(L−1)/(M−1)。

```text
step = clamp(ceil(base + rarity / w + target * x - tier_discount * (T-1)), 5, 8)
batch = 所有计划步骤 step 之和
必须 player.level >= 30 且 player.level >= batch
实际扣除 batch 个等级及步骤数颗铭纹石；不检查扣后剩余30级
```

`config/differentenchant.properties` 新字段（旧配置缺少时采用同样默认值，使用原管理员 `/differentenchant reload`）：

|字段|临时默认|
|---|---|
|directed.base|5|
|directed.rarity|1.5|
|directed.target|1.5|
|directed.tier_discount|0.5|
|directed.allow_treasure|false|
|directed.allow_curses|false|

四个数值参数要求有限、0..8；无论怎样配置，每步最终始终限制在5..8。30门槛固定。权重低的附魔、较高目标等级可更贵；高机器等级降低或维持可比较步骤费用，触及5级后收益平台化。书架不参与指定费用。宝藏/诅咒字段为独立、默认关闭的准入选项；没有为本轮默认玩法开放其新来源。

例：原版锋利 III→V 共两步、两颗石头；默认 T1 为7+7=14级，T5为5+5=10级。批量五步可能超过30级，因此到达门槛也不代表一定付得起。真实客户端本轮测试 T1 五步33级，切T5后25级；35级玩家支付后剩10级。公式是可调原型，不是长期平衡定案。高等级的折扣可能偏强，稀有度权重并不等价于实际战斗价值，仍需试玩。

## 合法性、同步与提交

- `DirectedCandidates` 使用完整动态注册表及 `isSupportedItem`，不使用固定原版列表或随机 `IN_ENCHANTING_TABLE` 筛选。保留原版存储组件约束，因此支持已有附魔、盾牌等不能在随机桌附魔的装备；不扩大注册表声明的适配。专用禁用标签为 `differentenchant:excluded_from_directed`。
- 默认过滤 TREASURE、CURSE 及禁用标签；已有这些附魔不会被删除。候选及原附魔、规划之间均查原版双向 exclusive_set。已有非法组合拒绝继续规划，不清理或“修复”原物品。
- `DirectedEnchantPolicy` 无 Minecraft 依赖；按有序步骤计算目标与逐步费用。`DirectedSession` 为每名玩家的当前菜单维护规划与全局单调64位版本，重放加号和最终操作均拒绝。
- S2C 快照带容器、随机报价上下文版本、规划版本、输入及材料副本、实际总费用、服务端可用标记、完整合法候选行。名称由客户端同步注册表解析。C2S 只有动作、候选 ID 和版本，没有客户端目标、费用或结果物品。
- 客户端只有输入、材料、报价版本相符时才能操作；等待回复时保持已知行显示但禁用加减，避免连续未确认操作。候选按注册 ID 稳定排序；滚动不改变规划或随机结果。协议上限4 MiB、读列表上限16,384行，当前仅用原版及测试注册夹具验证，没有声称无限规模或大整合包压力通过。
- 最终操作重查当前所有者、活跃容器、所在维度、距离/整机 UUID/加载状态、当前输入对象与组件、注册表/标签/配置、全部计划、实际石头、30级门槛及完整费用。先从原 ItemStack.copy 创建输出，用 EnchantmentHelper.updateEnchantments 的 Mutable.set 只修改目标级别，再一次提交槽位和费用；不清空或重建 NBT/其他组件。
- 随机概率策略、私有盐和种子服务没有改动。指定成功只用 giveExperienceLevels 支付等级，不重置随机种子。两个模式不能绕过对方的材料与执行检查。

## 资源与截图

- 新物品定义 `assets/differentenchant/items/runic_stone.json`、`models/item/runic_stone.json`、`textures/item/runic_stone.png`。
- 程序生成源 `tools/generate-runic-stone.ps1`，使用 System.Drawing 绘制原创像素，不复制第三方纹理。
- 原版 GUI 底板、书本、字体、按钮继续运行时引用，没有更改机器模型。

[英文五步规划](screenshots/phase4/18-directed-five-planned-en.png) · [费用悬停](screenshots/phase4/19-directed-batch-cost-tooltip.png) · [中文指定界面](screenshots/phase4/20-directed-five-planned-zh.png) · [真实结果](screenshots/phase4/21-directed-committed-item.png) · [第三方长名称](screenshots/phase4/22-directed-modded-long-tooltip.png) · [超长名称末页](screenshots/phase4/24-directed-long-tooltip-last-page.png) · [列表滚动与加减](screenshots/phase4/23-directed-scrolled-modded-list.png)

以上为第四轮初版历史截图；0.4.1 新版、加减前后、预算恢复和短 tooltip 对比见 [GUI_POLISH.md](GUI_POLISH.md)。没有修改槽位坐标或机器模型，没有新增 GUI PNG；仅修改屏幕绘制、翻译与客户端验证。

## 验证范围

纯 Java 单元测试覆盖额度、撤销恢复、多种附魔、逐级与上限、互斥、每步边界/等级折扣、合计费用、非法配置；真实服务端测试覆盖原版及第三方物品/标签、非随机池候选、现有诅咒保留、身份/上下文/多人隔离、错误材料/不足资源/过期请求、复制后组件保留、创造也支付。真实客户端通过鼠标加减、原版材料槽数量变化、模式切换、机器切级、费用支付、关闭重开和磁盘重连，再次检查完整结果和资源；中文/英文和长列表均截图。

仍需手动验收：两台独立客户端的高延迟/断线压力、满背包/死亡关闭、真实第三方大模组的特殊限制、非默认字体与资源包。注册标签测试夹具不能代表所有模组内部拦截或不可修改标记。完整命令、实测数量及原始结果见 DEVLOG 与 `docs/validation/phase4/`。

祛魔在未来独立机器实现，见 [FUTURE_DISENCHANTING_MACHINE](FUTURE_DISENCHANTING_MACHINE.md)。本轮没有第五阶段功能。
