# 第六阶段实际验证 · 2026-10-18 · 0.8.0

从本地65e6e45干净工作区开发，未使用远程覆盖。Minecraft26.1.2、Fabric Loader0.19.5、API0.155.3+26.1.2、Loom1.18.4、Gradle9.7.1、JDK25.0.4.1。所有截图来自真实Minecraft客户端，无模拟UI或渲染示意图。

## 命令与最终结果

```powershell
$env:JAVA_HOME=(Resolve-Path '.tools/jdk-25.0.4.1+1').Path
$env:Path="$env:JAVA_HOME/bin;$env:Path"
node tools/generate-research.mjs
.\gradlew.bat build test --tests dev.differentenchant.research.ResearchPolicyTest runGametest -PresearchOnly -PresearchAffected --console=plain
.\gradlew.bat runClientGametest -PresearchOnly --console=plain
.\gradlew.bat assemble processGametestResources --console=plain
```

| 检查 | 实际结果 | 证据 |
| --- | --- | --- |
| 编译/构建 | 最终确认16秒成功；客户端说明换行微调后再次assemble约1秒，完成最终JAR并恢复默认测试入口 | [最终构建](research-final-confirm.log)、[恢复/打包](restore-defaults.log) |
| JUnit | 2项通过；每评级100,000个不同输入键、两世界种子，共800,000次解析；顺序/交换/层内目录排序/非法参数检查 | [JUnit XML](junit-results.xml)、[日志](research-unit.log)、[分布](distribution.csv)、[按产物](coverage.csv) |
| 研究服务端 | 8项新研究/效果/持久化测试 +3项指定的旧功能 +1原版always_pass，12/12通过 | [服务端XML](server-results.xml)、同上构建日志 |
| 客户端完整研究 | 最终48秒成功，只有ResearchClientGameTest；实际生存放置、真实Use开GUI、鼠标与Shift操作、研究数据包、记录滚动、语言重载、保存退出重进、实际装备效果同步 | [最终客户端](research-delivery-client.log)、[逐项实录](client.txt) |
| 包装资源 | 90份资源与源码逐字节一致（fabric版本正常展开），无测试类；JAR SHA-256 c309bebc1127d4ab6e5e314e55c6739a0dc2739069efb59beb945f49557423e6 | [产物审计](artifact-audit.json) |
| 旧系统范围 | 附魔策略/菜单/GUI、核心服务与组件、机器/碰撞及原配置源码未改；4份旧遗迹模板完整哈希不变 | 同上审计；Git差异核对 |

`-PresearchOnly`只选择新功能测试入口；`-PresearchAffected`在提交前额外复用三项已有验收：随机附魔实际扣费充核心至12、Lv.1→2及重复生产；指定III→V批量/30级门槛；形成/拆解与独立原版附魔台禁用。没有运行完整历史概率模拟、GUI、36书架或遗迹回归，也没有把编译成功当作客户端已测。

`ResearchIntegrationGameTests`仅用于这个可选检查，不加入默认入口重复跑原测试。最终已恢复源码的默认入口；本轮执行时未启动默认整套。

## 实际覆盖

- 原生工作台合成，原生ResultSlot实际扣除材料；研究台普通生存右击放置消耗物品，原版资源正确加载。
- 单条附魔书、等级与ID参与配方；多条/普通书明确拒绝。测试用注册的 `differentenchant_tests:directed_probe`书也可解析，不固定原版白名单。
- 无序A+B/B+A相同；换输入顺序不更改答案。未知预览没有结果字段；成功后已知预览、个人记录及卷轴预览正确。
- 成功只消耗两书/一颗现有谐调核心；卷轴只消耗一核心，两书的完整ItemStack（包括自定义名称/修复费用）相同。无核心、结果槽占用、多附魔、过期/重复/外人请求不收费；结果收走后旧请求仍失效。
- 两名真实服务端测试玩家各有独立菜单、UUID记录；一人发现不替另一人解锁，但交换书序后实际产物相同。输入和结果Shift移动、两次关闭、破坏失效后返还只有一份。
- 原地组件修改也使旧版本无效；已经失效的台不接受提交。服务端菜单先检查维度和已加载区块，不因查看菜单获取未加载区块。
- 2,000条发现记录超过单NBT字符串上限，原生compound/list二进制写入/读回成功；已知与尚未发现的映射都保持。保存后移除目录项不重分配已记录答案。
- version999测试文件故意触发原生SavedData加载错误：加载入口拒绝默认替换，关闭存储后原文件字节不变。**该测试的预期ERROR日志不表示资源加载失败**。
- 八种附魔逐一通过原版铁砧，正确付费且不超过设计等级；真实属性改变/卸装恢复、经验5→9、实际夜视/治疗/抗性、真实僵尸漂浮及近战过滤、致命伤害救活/耐久/二次死亡、冷却NBT已执行。详情见[效果清单](../../LOST_RESEARCH.md)。

## 真实客户端流程

普通超平坦世界种子1，UUID043cbf82-02a9-3180-968e-af73411af080，研究台(0,-60,0)。客户端通过原生右击放置，拍原创书桌模型，再实际右击进入专属界面。

两本水下速掘I（`aqua_affinity@1`）首次显示???，研究实际获得守望凝神II（稀有度III）：两书消失，核心8→7。Shift取走结果，再用反向书序投入同种组合，预览直接显示已知结果。

水下速掘I+致密II（`density@2`）固定产出卷轴：核心7→6，名称为“Kept after research”且修复费用17的原书与另一原书均完整保留。之后四条不同配方均通过研究按钮正常支付并获得成果，没有直接调用discover伪造记录。记录页6条发现可滚动，英文与中文显示均正常。

**在研究菜单仍打开、仍有两书/核心时断线**，统计背包+菜单/鼠标持物+附近原生掉落实体总数；磁盘保存并重新进入后总数相同。允许原版断线时掉落，不把“必须进背包”误当复制保护。重新进入时世界快照与个人发现JSON语义完全相同，正常打开研究台再次看到已知预览。

最后给真实玩家装备带潜光的测试头盔并进入真实水体，等正常ServerTickEvents回调施加夜视，客户端实际收到药水状态；没有调用效果函数或/effect命令代替。这一额外装备由测试fixture提供，研究/铁砧流程分别已有专项验证。

## 截图

| 内容 | 真实截图 |
| --- | --- |
| 原创研究台方块 | [01](../../screenshots/research/research-01-table-block.png) |
| 首次未知配方/完整GUI | [02](../../screenshots/research/research-02-unknown-recipe.png) |
| 未知时也可查看通用消耗规则 | [02b](../../screenshots/research/research-02b-material-rules.png) |
| 成功研究/真实结果槽 | [03](../../screenshots/research/research-03-success-result.png) |
| 真实失落书说明、合理换行 | [04](../../screenshots/research/research-04-lost-book-tooltip.png) |
| 再次投入、已知预览 | [05](../../screenshots/research/research-05-known-preview.png) |
| 首次发现记录 | [06](../../screenshots/research/research-06-first-discovery-record.png) |
| 卷轴结果、两书仍在 | [07](../../screenshots/research/research-07-scroll-books-preserved.png) |
| 固定卷轴的已知预览 | [08](../../screenshots/research/research-08-known-scroll-preview.png) |
| 六条英文发现记录 | [09](../../screenshots/research/research-09-records-english.png) |
| 实际滚动后的记录 | [10](../../screenshots/research/research-10-records-scrolled.png) |
| 中文发现记录 | [11](../../screenshots/research/research-11-records-chinese.png) |
| 中文已知预览 | [12](../../screenshots/research/research-12-known-chinese.png) |
| 磁盘重进后的预览 | [13](../../screenshots/research/research-13-reconnected-preview.png) |
| 实际水下夜视同步 | [14](../../screenshots/research/research-14-scheduled-lantern-sight.png) |

## 模拟数据

[distribution.csv](distribution.csv)：每评级100,000配方的稀有度/卷轴/覆盖率/重复率/两种子差异；[coverage.csv](coverage.csv)：各ID与效果等级次数。I/II/III/IV层内允许的3/5/7/8种附魔全部有覆盖，越级次数为0，固定卷轴比例0%/6.116%/5.875%/5.977%。同配方跨12345与98765种子的差异率80.311%/89.985%/90.949%/87.151%。

[vanilla-recipes.csv](vanilla-recipes.csv)另枚举真实原版注册表中的8256个不同书等级组合，不把受控评级输入假称为真实原版分布。原版组合评级数569/3493/3213/981，总计分别覆盖7/13/17/18种结果（含等级/卷轴）。数学支持集由池权重/正常等级正权重验证，抽样/枚举统计不证明永不碰撞；有限产物必然有大量重复，详见机制文档。

## 发现的错误与修正

- [初次Java编译](research-compile-1.log)：26.1.2没有旧EnchantedBookItem静态创建器及SimpleContainer监听器，改用现有官方API `EnchantmentHelper.updateEnchantments`和`setChanged`；[修正编译](research-compile-2.log)通过。资源生成脚本一次JSON对象括号错误也在首次运行时修正，未用空资源绕过。
- [首次服务端](research-server-1.log)四处失败：合成fixture用了NULL位置导致不计算产物；ServerPlayer.tick没有走实体doTick使装备属性/水体状态未更新；新连接尚未发PlayerLoaded而仍无敌。改用真实工作台访问、doTick和原生PlayerLoaded确认；没有移除效果断言或削弱生产效果。[下一轮](research-server-2.log)7项通过。
- [首次客户端编译](research-client-1.log)：泛型computeOnServer与StringBuilder.append重载推断冲突，用显式long局部变量修正。
- [首次实际客户端](research-client-2.log)：对草地方块中心瞄准，射线先撞近一格地面而放错坐标。改瞄准实际顶面，保留正常生存放置、扣物品断言；[之后](research-client-3.log)44秒完整流程通过。
- [存档压力测试](research-server-3.log)：NBT byte转换成JSON数字，Gson默认boolean读取失败；补上0/1严格转换，[下一轮](research-server-4.log)通过大记录二进制检查。
- 最终改为世界级SavedData与存在文件保护后，真实磁盘客户端再次通过；未初始化快照也不允许当成新数据重建。后续只缩短新失落说明tooltip行宽，最终客户端48秒验证并重新打包，没有重做原GUI。
- 其他中间日志按`research-*`保留在本目录，包含逐阶段成功结果。原生Mojang认证公钥网络超时和MockPlayer弃用警告不是本模组的资源/业务错误，未把这些日志删去或隐藏。

## 变更概要与边界

新增 `research/`纯Java映射/配置、世界持久化入口、记录、四槽菜单、双向payload、独立block与四个服务端事件效果；新增 `client/ResearchScreen`。主入口只接新模块，客户端入口加新菜单/消息/失落说明换行。

新增原生方块模型/贴图、合成/掉落、八个enchantment、四级标签与两个语言；原随机/指定排除标签只追加lost集合，原始附魔种类/概率与费用服务未改。`tools/generate-research.mjs`是原创建筑/贴图/数据源。版本0.8.0；旧0.7.0产物归档至忽略目录，发布目录只保留新版本。

没有实现Lv.3升级或Lv.4互斥解除。没有改任何已审核遗迹的NBT、世界生成/loot、机器模型/碰撞或原版台交互。未重新运行完整旧概率/GUI/36架/遗迹测试；未逐种做长期生存/PvP或外部模组整包兼容，漂浮验证的是原生命中管线而非真实箭矢全飞行。现有世界不会自动吸收未来新增输出目录，需要日后明确版本迁移。数值与名称仍为原型，不能把本轮通过理解为已最终平衡。
