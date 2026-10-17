# 源码研究记录 · 2026-10-16

## Immersive Engineering — 结构转换和恢复

阅读：`IETemplateMultiblock.java`，尤其 `replaceStructureBlock(...)` 和 `disassemble(...)`。

关键思想：先匹配模板；在形成时将原方块替换为带 master/slave 与朝向信息的部件；记录可恢复的旧状态；拆解时按朝向重新摆回原方块；防止拆解引起额外递归。它是较复杂的 NeoForge 工业模组，**本项目只学习分层设计思路**，不照搬类或代码。

- https://github.com/BluSunrize/ImmersiveEngineering/blob/master/src/main/java/blusunrize/immersiveengineering/common/blocks/multiblocks/IETemplateMultiblock.java

## Meridian — 原版附魔系统覆写

阅读：`EnchantmentTableBlockMixin.java`、`MeridianEnchantmentMenu.java` 和 `design/SPEC.md`。

关键思想：通过注入原版 `EnchantingTableBlock#getMenuProvider` 使用自定义菜单；菜单逻辑继承 `EnchantmentMenu`，可以保留基本槽位等机制；概率计算独立于菜单和 GUI；仍从游戏注册表取附魔。兼容性的成本之一是任何其他修改同一 menu provider 的模组可能冲突。

**Meridian 的源码目标是 Fabric 1.21.1，不应当把它的 API 名或 Gradle 构建方式原样用于 26.1.2。**

- https://github.com/rfizzle/meridian/blob/main/src/main/java/com/rfizzle/meridian/mixin/EnchantmentTableBlockMixin.java
- https://github.com/rfizzle/meridian/blob/main/src/main/java/com/rfizzle/meridian/enchanting/MeridianEnchantmentMenu.java
- https://github.com/rfizzle/meridian/blob/main/design/SPEC.md

## Fabric 26.1.2 官方文档

26.1 改为无混淆官方名称，Java 25，新的 `net.fabricmc.fabric-loom`。26.1 的渲染层/Model Loading API 经历较大变化，别复制旧教程的渲染代码。在实际实现时使用当前 26.1.2 文档与依赖源码核实。

- https://fabricmc.net/2026/03/14/261.html
- https://github.com/FabricMC/fabric-docs/blob/main/versions/26.1.2/develop/blocks/block-entity-renderer.md
- https://fabricmc.net/develop/template/

## 第一轮实际核对的 26.1.2 接口与构建（2026-10-16）

官方元数据与 example-mod `26.1.2` 分支确认：Minecraft 26.1.2、Loader 0.19.5（stable）、API 0.155.3+26.1.2、Loom 1.18.4（stable）、Gradle 9.7.1。构建没有 mappings、Yarn、modImplementation 或 remapJar。使用 JDK 25.0.4.1+1；官方 Adoptium 包元数据及 SHA-256 见 `research/jdk-package.json`。

- https://meta.fabricmc.net/v2/versions/loader/26.1.2
- https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml
- https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml
- https://github.com/FabricMC/fabric-example-mod/tree/26.1.2
- https://github.com/FabricMC/fabric-docs/blob/main/versions/26.1.2/develop/blocks/first-block.md
- https://github.com/FabricMC/fabric-docs/blob/main/versions/26.1.2/develop/blocks/block-entities.md

除官方教程外，还检查了实际解析的游戏 JAR 的 javap 签名、Loom 反编译源码及 Fabric API sources JAR：

| 接口 | 实际用途 / 注意事项 |
| --- | --- |
| `Identifier`, `ResourceKey`, `Properties.setId` | 先提供注册 key，再构造技术方块；没有沿用旧 ResourceLocation 名称。 |
| `UseBlockCallback` | 主手交互回调；两手空、潜行、非旁观、允许建造时，服务端尝试形成。 |
| `FabricBlockEntityTypeBuilder` / `BaseEntityBlock` | 技术部件实体、codec、静态 JSON 模型和服务端 ticker；不实现 BER。 |
| `saveAdditional(ValueOutput)` / `loadAdditional(ValueInput)` | 通过 BlockState、BlockPos、UUIDUtil、CompoundTag 的 CODEC 保存原状态、原实体、身份和位置。 |
| `BlockEntity.preRemoveSideEffects` | LevelChunk 替换方块并移除旧实体前触发，覆盖玩家破坏、爆炸、外部替换等路径。不沿用旧版 onRemove。 |
| `BlockState.getDrops(LootParams.Builder)` | 转用原块战利品表，保留工具和爆炸参数；不另外手动生成物品。 |
| `ServerPlayerGameMode.destroyBlock` | 移除前捕获原实体，移除后按工具与创造模式决定掉落，测试调用真实入口。 |
| `hasChunkAt` + `UPDATE_CLIENTS / UPDATE_KNOWN_SHAPE` | 全部区块加载预检查，批量写入后通知已加载邻居，不申请 chunk ticket。 |
| `net.fabricmc.fabric.api.gametest.v1.GameTest` | 当前 Fabric 注解、fabric-gametest entrypoint，测试模组独立于发布 JAR。 |
| `FabricClientGameTest` / `TestWorldSave.open` | 真实渲染客户端按键和网络形成，关闭世界后磁盘重进、拆解、截图。 |

依赖源码模块：GameTest API `4.0.18+867b87624c`、Client GameTest API `5.1.0+0a5283664c`。客户端测试接口标记 experimental，仅用于开发验收。

发现该游戏版本 GameTestHelper.relativePos() 使用 rotation+180 的转换，在本次未旋转模板中并不与 absolutePos() 互逆；掉落断言改为真实世界 AABB 查询，没有为适应测试修改掉落逻辑。临时教程与反编译源码仅保存在忽略目录 .tools/，不随源码提交。

## 第二轮：随机附魔实际接口核对

继续使用本地实际 26.1.2 依赖源码与 javap，并查阅 [26.1.2 Fabric Block Entities 文档](https://docs.fabricmc.net/26.1.2/develop/blocks/block-entities)。没有从 1.21 菜单示例移植实现。

- `EnchantmentMenu`：原版双槽、三个 costs/clue、DataSlot 同步、quickMoveStack、removed/clearContainer；以子类最小替换报价和提交，使用自有 MenuType。注册工厂采用原版 MenuAccess<EnchantmentMenu> 泛型，画面复用 EnchantmentScreen。
- `Enchantment.isPrimaryItem` 包含 supportedItems 判断；`Enchantment.areCompatible` 对两侧 exclusiveSet 检查。原版候选 getAvailableEnchantmentResults 按 getMinCost/getMaxCost 截断等级，因此没有直接调用它或提高 enchanting power 作为新模型。
- 当前 `EnchantmentTags.IN_ENCHANTING_TABLE / TREASURE / CURSE` 和物品 `DataComponents.ENCHANTABLE`；书本原版例外保留。锋利的实际 JSON primary 为 `#minecraft:enchantable/melee_weapon`、supported 为 `#minecraft:enchantable/sharp_weapon`，非旧教程的 sword 标签。
- `EnchantingTableBlock.BOOKSHELF_OFFSETS` 与 provider/transmitter 标签；自机通道处理另见 balance 文档。
- `Player.onEnchantmentPerformed` 消耗等级并更新原版 seed；Player.loadAdditional 对 XpSeed=0 重新生成，必须处理新玩家首次存档的哨兵。
- `SavedDataType` 接受 Identifier、Supplier、Codec；存储实际类型为 `SavedDataStorage`（不是旧版 DimensionDataStorage）。本轮 SavedData 保存私有盐，并在真实客户端退出/重进验证。
- `ServerboundContainerButtonClickPacket` 的 buttonId 使用 VAR_INT，可编码报价版本；DataSlot 同步为短整数，版本拆低/高段再恢复。没有让客户端提交附魔 ID、等级或费用。
- `MultiPlayerGameMode.handleContainerInput` / `ContainerInput.QUICK_MOVE` 是目标版真实物品移动接口；界面使用 `GuiGraphicsExtractor`、`extractLabels` 和 `MouseButtonEvent`，没有旧 renderBg/ClickType API。
- `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` 是本版权限接口；Fabric `ServerLifecycleEvents.END_DATA_PACK_RELOAD` 在成功时使候选缓存失效。

## 第三轮：自定义 GUI 与真实预览

核对 [26.1.2 Networking](https://docs.fabricmc.net/26.1.2/develop/networking) 及 [26.1.2 Custom Screens](https://docs.fabricmc.net/26.1.2/develop/rendering/gui/custom-screens)，具体签名仍以已固定的实际游戏/Fabric JAR 与生成源码为准。

- `PayloadTypeRegistry.clientboundPlay().register`、`CustomPacketPayload`、`StreamCodec<RegistryFriendlyByteBuf,...>`、`ServerPlayNetworking.send/canSend`；客户端 `ClientPlayNetworking.registerGlobalReceiver` 的处理器在客户端线程执行。
- `ItemStack.OPTIONAL_STREAM_CODEC` 用当前连接注册表同步报价输入物品；附魔使用 Identifier+等级，仅传揭示条目。客户端从同步注册表取 `Enchantment.getFullname`，支持数据包/模组名称。
- `Slot.x/y` 为 final；原版 EnchantmentMenu 的两个 Slot 仅覆盖数量、放入过滤与空图标，没有输出 onTake 副作用。保留原容器/索引/规则并重定位，原 quickMoveStack/removed 继续工作。
- `AbstractContainerScreen(menu,inventory,title,width,height)`、`extractBackground/extractLabels/extractTooltip`、`GuiGraphicsExtractor.fill/textWithWordWrap/setTooltipForNextFrame`、`MouseButtonEvent`、`mouseScrolled` 为目标版 GUI API。原版 `EnchantmentNames` 提供纯装饰符文，与结果算法无关。
- 当前 Fabric Client GameTest API 5.1.0 的 TestInputImpl.pressOrReleaseKey 对鼠标始终构造 `MouseButtonInfo(button,0)`，holdShift 不影响事件 modifiers；26.1.2 原版菜单读取 event.hasShiftDown。测试中对 Shift 转移显式传 `MouseButtonInfo(0,1)` 到真实屏幕，再走原版 slotClicked 和真实网络，普通鼠标、数字热键、关闭和滚轮仍走官方 TestInput。生产代码不绕过原版键鼠规则。

第三轮视觉修正实际引用原版 `enchanting_table.png` 的背景/槽位区域、`container/enchanting_table` GUI sprites、`ModelLayers.BOOK`/BookModel 和原版书本纹理。区域绘制保持四像素外框厚度，扩展中间底板；没有重新分发 PNG。GuiGraphicsExtractor.book 的 viewport 是绝对屏幕坐标，不随 pose 变换，因此书本在弹出 pose 后以 leftPos/topPos 绘制，槽位和按钮仍使用统一局部几何。

## 第四轮：注册、存储和指令接口

复查 [Fabric 26.1.2 First Item](https://docs.fabricmc.net/26.1.2/develop/items/first-item) 和 [Networking](https://docs.fabricmc.net/26.1.2/develop/networking)，再以固定依赖 JAR/生成源码核对签名：

- 注册 `Item.Properties.setId`、`CreativeModeTabEvents.modifyOutputEvent`。实际 API 包为 `api.creativetab.v1`，不是旧 itemgroup 包；第一次编译发现并按实际 JAR 修正。26.1.2 同时需要 `assets/.../items` 客户端物品定义及 `models/item` 纹理模型。
- `Enchantment.isSupportedItem` 用于指定适配；`isPrimaryItem` 与 ENCHANTABLE 资格仍仅用于原随机策略。盾牌/鞘翅没有随机桌 ENCHANTABLE，不应因此拒绝其合法指定升级。
- `EnchantmentHelper.canStoreEnchantments`、`getEnchantmentsForCrafting`、`updateEnchantments(ItemStack, Consumer<Mutable>)` 和 `ItemEnchantments.Mutable.set`。update 以原组件创建 mutable，只写回附魔组件；原 ItemStack.copy 保留其他组件。本轮未拷贝原版函数源码。
- `Player.giveExperienceLevels` 支付等级且不重置随机种子，与原随机 onEnchantmentPerformed 的功能不同。门槛与合计费用分别校验。
- `PayloadTypeRegistry.clientboundPlay().registerLarge`、64位版本和 serverbound 注册/处理器；处理器在服务器线程内执行，当前容器/所有者再次验证。
- `Component.translatableWithFallback` 在原版没有等级翻译键时回退数字，避免模组超过 X 的正常等级把列表撑坏。InputConstants.isKeyDown(Window,key) 用于 Shift+滚轮阅读极长 tooltip，保留原版库存键鼠。

## 整机书架修正（0.4.2）

继续使用已固定的26.1.2依赖，直接核对本机依赖生成源码：`StructurePattern`明确controller在上层、另一层y=-1，旧版原版offsets y=0/1与整机高度不一致。当前 `BlockTags.ENCHANTMENT_POWER_PROVIDER` 仍是书架提供者标签；保留此标签而去掉机器菜单的空气/传导条件，不修改原版台全局行为。

`LevelReader.hasChunkAt(BlockPos)`只查visible holder和FULL ticket级别，不能单独证明FULL数据已就绪；`Level.getBlockState`随后会调用常规getChunk，可能等待/添加UNKNOWN ticket。因此本次扫描最终使用 `ServerChunkCache.getChunkNow`：只查看FULL缓存或holder.getChunkIfPresent，不创建ticket、不等待任务；缺失则跳过，存在时直接从LevelChunk.getBlockState读取。纯Java测试混合已加载/未加载视图，真实GameTest另对远处全部未加载坐标扫描并核对已加载区块数不变；没有以此声称完整玩家卸载/重载压力测试已覆盖。
