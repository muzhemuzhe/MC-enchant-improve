# 第四轮界面交互修正 · 0.4.1

只修改客户端绘制与本地化，没有更改随机分布、预览缓存、规划协议、扣费、物品槽位或3×3×2机器。没有新增或复制 GUI 贴图，继续运行时引用原版资源。

## 随机选项

三条完整棕纸选项使用原版 `enchantment_slot`、`enchantment_slot_disabled` 与 `enchantment_slot_highlighted`，保留银河字母、青金石选项图标、右侧要求等级。悬停加原版高亮与浅金像素边；经验或材料不足时仍高亮当前行，但仍不可点击。

- 可用：棕纸、绿色图标与等级；悬停使用原版淡紫高亮。
- 经验不足：禁用图标、红色要求等级和底边细线，tooltip 明确说明经验不足。
- 青金石不足：禁用图标、暗淡等级和蓝色底边，tooltip 单独说明材料不足。
- 同时不足：红蓝各半底边，同时显示两项原因。
- 没有有效装备/已附魔装备/等待同步：禁用状态；没有有效报价时不画假符文。

随机预览仍来自服务端同一缓存，揭示数量、顺序与最终结果没有变化。tooltip 宽最多180 GUI像素、每页最多8行正文及一行滚轮提示，完整内容可滚轮读取；放在选项下方，避免长名称遮住三条选项。

[普通装备默认](screenshots/ui-polish/ui-random-00-vanilla-equipment.png) · [普通装备悬停](screenshots/ui-polish/ui-random-06-vanilla-hover.png) · [经验不足](screenshots/ui-polish/ui-random-03-missing-experience.png) · [青金石不足](screenshots/ui-polish/ui-random-04-missing-lapis.png) · [两项都不足](screenshots/ui-polish/ui-random-07-both-shortages.png) · [已附魔禁用](screenshots/ui-polish/ui-random-05-enchanted-disabled.png)

## 指定附魔池

每个条目为原版纸质横条，单行名称后直接显示服务端规划等级，取消全部计划后恢复原等级。没有原附魔时仅名称，第一次分配后显示 I。计划增加的结果为暗绿文字，悬停提亮纸面与浅金边。右侧原版小按钮保留；无已分配步骤时减号灰置，上限隐藏加号，额度/互斥禁止时加号灰置。后台等待新快照时同样禁用操作。

|状态|实际客户端截图|
|---|---|
|未规划，锋利 III、0/5颗|[操作前](screenshots/ui-polish/ui-directed-01-before.png)|
|点击 `+`，直接锋利 IV、1/5颗|[加一级](screenshots/ui-polish/ui-directed-02-plus.png)|
|点击 `−`，恢复锋利 III、0/5颗|[撤销后](screenshots/ui-polish/ui-directed-03-minus.png)|
|五颗全部分配，加号灰置或上限隐藏|[预算用尽](screenshots/ui-polish/ui-directed-04-budget-exhausted.png)|
|撤销一步，其他可用加号恢复|[预算恢复](screenshots/ui-polish/ui-directed-05-budget-restored.png)|
|中文多附魔最终计划，无箭头和门槛读数|[指定界面](screenshots/ui-polish/ui-directed-06-chinese.png)|
|原版小幅 tooltip，不覆盖池项|[悬停说明](screenshots/ui-polish/ui-directed-07-tooltip.png)|

下方保留铭纹石已用/实际数量、实际总经验费用与“附魔”按钮。去掉指定模式“需30级”提示；现有服务器30级门槛保持，这次没有重设经济规则。玩家物品栏和快捷栏几何与0.4.0完全相同，沿用灰底与原版凹槽，无独立装饰框。

## 长名称与 tooltip

列表长名称省略时保留规划等级后缀。tooltip 读取客户端同步注册表 description，不生成假效果说明。普通名称自动换行，超长说明默认简短展示；按住 Shift 读取完整名称/说明，Shift+滚轮分页，松开回到简短提示。正文最多5行，另加必要的翻页提示。tooltip 放在池和费用区下方，不挡住条目、加减和最终按钮，悬停期间会覆盖部分物品栏。

[简短提示](screenshots/ui-polish/ui-directed-08-compact-long-name.png) · [Shift完整文本首页](screenshots/ui-polish/ui-directed-09-full-name.png) · [Shift滚轮末页](screenshots/ui-polish/ui-directed-10-name-last-page.png)

旧版为二行箭头和较大默认 tooltip，可对照 [旧指定界面](screenshots/phase4/18-directed-five-planned-en.png) 与 [旧长提示](screenshots/phase4/22-directed-modded-long-tooltip.png)。

## 文件与验证

- `src/main/java/dev/betterenchant/client/MachineEnchantmentScreen.java`：随机状态、池项、最终等级、按钮与有限 tooltip。
- `src/main/resources/assets/better_enchant/lang/{en_us,zh_cn}.json`：实际费用、资源不足与简短长名称提示。
- 三个已有附魔客户端测试：新增实际加减标签断言、预算禁用点击与恢复断言、正常与不足状态截图。服务端与概率实现未修改。
- 版本0.4.1；截图为真实26.1.2 OpenGL客户端测试输出，无合成截图。命令、结果与范围见 DEVLOG 和 `docs/validation/ui-polish/`。

仍需人工验证非默认字体/资源包、真实整合包与高延迟的两台客户端；不把编译成功视为这些场景已通过。
