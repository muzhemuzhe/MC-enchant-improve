# Differentenchant 0.5.0 重命名验证

起点为本地ce00558，保留三个未推送提交。先读取规则/当前设计与源码，执行git status、log、remote确认干净的main；没有以远程旧版本覆盖本地。

```powershell
$env:JAVA_HOME = (Resolve-Path '.tools/jdk-25.0.4.1+1').Path
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
.\gradlew.bat build runGametest --console=plain                  # 迁移前基线，16s通过
.\gradlew.bat clean build runGametest runClientGametest --console=plain # 正式迁移后，2m28s通过
```

**25项JUnit、40项服务端（39项目+1原版）、6个真实26.1.2 OpenGL客户端入口全部通过**。没有把未运行测试列为通过。src/gametest/resources的入口、夹具及注册表ID均是新命名；测试模组不进入发布JAR。

- 新增NamingGameTests独立检查Fabric加载differentenchant及展示名、方块、BlockEntity、Menu、铭纹石、两种核心、能量组件、三个Payload、世界私盐SavedData的规范ID；新配置文件实际存在，parse后的全部键值与服务端当前配置相同。固定12点容量、30级门槛不改。
- 全部结构方向/18部件拆解、正常存档及恢复、生存/创造掉落、爆炸、原版独立台禁用与原版其他工作台回归保留。
- 实际随机三选项、物品适配/互斥、费用/预览/种子与重放拒绝、指定加减批量/30级门槛/铭纹石和兼容性夹具、36架上下层/贴边/第二圈/动态变化均通过。
- 共鸣核心的16种混合书配方及原生结果槽材料消耗、初始0、按实付经验收集、11/12到12、仅Lv.1→2、Lv.2..5重复生产不升级、物品/世界存档、转移、共享槽位/重放/Shift/拆解均回归。
- 客户端新世界真实GUI取放、随机/指定网络请求与实际收费、核心完整0→3→6→9→11→12升级和第二颗成品生产继续执行；磁盘保存重进、等级和核心持久化、实际区块卸载/返回也继续执行。不是只加载旧测试存档。
- 中英文资源重载后都断言container.differentenchant.title为Differentenchant，并检查客户端Fabric加载ID，以及3个物品PNG/定义、18个模型、blockstate与两个语言文件实际存在。真实截图检查机器、物品与GUI；未修改文字重影或布局。

## 审计和概率

`identity-audit.json`：64份生产文件对照ce00558，规范化已授权命名替换后内容相同，额外业务改动0；3份物品PNG blob相同。新运行JAR的全部条目和class/JSON/properties文本没有旧包名或旧命名空间，Fabric元数据/入口正确，排除测试内容。发布目录只有differentenchant-0.5.0.jar及sources，旧发布JAR和开发配置已移到忽略的.tools/rename-backup，不参与发布/当前运行。

`balance-comparison.json`：27组×100,000实际注册表模拟的28份输出与改名前0.5.0逐文件SHA-256相同。默认配置数值、全部生产算法及几何文件只有命名变化，不以概率抽样代替源文件比较。

`baseline.log`、`full-validation.log`和XML保留实际结果。clean后的原生测试服务器首次找不到server.properties/eula会记录创建默认文件的信息，客户端测试环境另有既有的各向异性选项与离线Realms认证提示；这些不是模组资源缺失。没有发现模组缺失模型/贴图、注册冲突、旧类加载或Payload名称不匹配错误。没有为隐藏这些提示更改客户端选项或游戏逻辑。

## 截图

| 实际场景 | 图片 |
| --- | --- |
| 形成后的机器模型 | [机器](../../screenshots/rename/01-formed-machine.png) |
| 英文随机选项与悬停 | [随机界面](../../screenshots/rename/02-random-en.png) |
| 中文新标题和核心 | [0点](../../screenshots/rename/03-random-core-zero-zh.png)、[6点](../../screenshots/rename/04-core-mid-zh.png) |
| 核心充满、Lv.2和成品 | [升级](../../screenshots/rename/05-core-full-tier2-zh.png)、[物品提示](../../screenshots/rename/07-attuned-item-zh.png) |
| 英文指定界面 | [指定](../../screenshots/rename/06-directed-en.png) |
| 新世界磁盘及区块重载 | [存档重进](../../screenshots/rename/08-saved-reloaded-tier2-en.png)、[区块重载](../../screenshots/rename/09-chunk-reloaded-core-en.png) |

这些9张为本次真实客户端输出，不覆盖历史图片。对应SHA-256见screenshots.json。完整迁移与旧名保留理由见 [RENAMING](../../RENAMING.md)。

高延迟双独立客户端、整合包与单侧区块异常时序仍是原有人工测试边界；本轮没有将它们冒充通过。旧注册ID世界与旧配置明确不兼容；新世界持久化已实际验证。
