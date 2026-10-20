# 六项原生进度 · 0.13.0

使用Minecraft26.1.2原生进度页面、任务边框、通知、聊天提示和每世界/每玩家进度JSON。只有下列六项，没有额外根节点、挑战、隐藏内容或奖励。数据位于src/main/resources/data/differentenchant/advancement；中文/英文文本位于原语言文件。

| ID（differentenchant命名空间） | 中文标题 / 描述 | 英文标题 | 图标 / 真正触发点 |
| --- | --- | --- | --- |
| old_arts_reborn | 旧术新生 / 首次组装附魔机 | Old Arts Reborn | 原版附魔台；自动形成服务成功提交完整机器 |
| attuned_core | 魔力？！ / 获得第一颗谐调的共鸣核心 | Magic?! | 谐调核心；真正取得物品 |
| lost_chapter | 遗失的篇章 / 获得第一张失落卷轴 | A Lost Chapter | 失落卷轴；真正取得物品 |
| place_of_learning | 求知之所 / 首次组装失落魔法研究台 | A Place of Learning | 研究台核心；自动形成完整机器成功 |
| price_of_knowledge | 知识的代价 / 首次研究失败 | The Price of Knowledge | 失落卷轴；失败研究真实交付和扣料提交成功 |
| disenchant | 祛魔 / 首次组装祛魔机 | Disenchant | 祛魔核心；自动形成完整机器成功 |

## 页面和先后顺序

旧术新生为可见根；魔力？！、遗失的篇章、祛魔是直接子节点；求知之所在遗失的篇章之后，知识的代价在求知之所之后。这仅决定布局。

原生AdvancementVisibilityEvaluator递归把“已有完成后代”的祖先发送给客户端，即使祖先没有完成。第一次取得任何一项后，该独立页面就能出现，根仍可以是灰色未完成。若六项全部未完成，原版会暂不显示这个页面；不增加自动完成的第七节点，也不免费授予旧术新生。原生页签使用根标题“旧术新生 / Old Arts Reborn”和附魔台图标。

## 服务端入口

- 物品定义使用minecraft:inventory_changed的单物品条件。机器库存、箱内未领取战利品、商人结果预览、研究未知书/已知卷轴预览都不属于玩家背包。
- 原版ServerPlayer的容器监听器只对玩家背包槽通知该条件，不监听光标。AdvancementCursorMixin在ServerGamePacketListenerImpl.handleContainerClick返回后，只对真实服务端光标上的谐调核心/卷轴补调同一个原生触发器。它不信任客户端传来的物品摘要，也不读取输出预览槽。普通拿取、Shift入背包、数字键交换沿用原生菜单。
- AssemblyActorMixin以MixinExtras0.5.5的WrapMethod包住ServerPlayerGameMode.useItemOn，用finally恢复短暂线程作用域。Level.setBlock现有通知把最近一次实际材料变更的发起者带入原候选队列；去重和下一tick验证不改。非玩家变更清除旧归属，区块扫描仅观察而不覆盖最近玩家变更。完成者可以是最后放中心、边、下层材料的玩家，不按距离找人。无玩家的命令/世界生成/载入形成仍正常工作，但不授予旁观者。
- AutoAssembly只在原FormationService/WorkstationStructure.form成功后授予对应committed条件。队列短暂保留真实玩家引用，若玩家恰在这一个tick前已断线，仍向其原生PlayerAdvancements授予并调用原生save，避免登出保存早于形成提交。队列在消费、世界卸载、服务器停止时清理，没有新增玩家进度存档。
- ResearchMenu在真实产物交付、材料消耗、发现记录完成后，通知原生物品条件；仅result.scroll()分支授予知识的代价。普通鼠标、Shift、数字键共用同一提交点。它不按持有卷轴推测失败，不改变世界种子固定答案、概率、材料、记录或事务。
- 四项提交类定义使用minecraft:impossible，由ModAdvancements明确调用原生award(holder,"committed")。没有自定义触发器注册、网络授予包或重复玩家档案。原生进度已完成后再次award不更新时间戳，不重复通知；数据包/管理员撤销后按原生语义可重新获得。

## 验证

[服务端GameTest、真实客户端、原始截图与日志](validation/advancements/README.md)。没有改变原附魔、研究、祛魔、职业交易、结构材料或机器模型/碰撞。没有把历史全套效果/世界生成概率测试重复运行作为本轮验收。
