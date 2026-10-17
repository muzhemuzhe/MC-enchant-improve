# Differentenchant 正式重命名

本地起点ce00558，工作区干净，保留共鸣核心、原版台禁用和36架三个未推送提交。此次仅重命名，不增加玩法；版本继续0.5.0。

| 项目 | 现用名称 |
| --- | --- |
| 展示名 / 中英文GUI标题 | Differentenchant |
| Fabric Mod ID / 资源与数据包命名空间 | differentenchant |
| Java包 / Gradle group | dev.differentenchant |
| 主入口 | dev.differentenchant.DifferentEnchant |
| 客户端入口 | dev.differentenchant.client.DifferentEnchantClient |
| 发布JAR | differentenchant-0.5.0.jar |
| 默认配置资源 | differentenchant-defaults.properties |
| 游戏目录配置 | config/differentenchant.properties |
| 管理命令 | /differentenchant tier、/differentenchant reload |
| 独立测试模组 | differentenchant_tests（不进入发布JAR） |

## 修改范围与不变量

三个Java源集全部迁移至dev/differentenchant，入口类同时改名。src/main/resources/assets和data下的项目命名空间，以及测试资源的主体与测试命名空间均迁移。翻译键、物品/方块/BlockEntity/Menu、三个Payload、世界私盐SavedData、resonance_energy数据组件、排除标签、核心配方、模型引用、脚本输出路径与Fabric/Loom元数据一起更新。原版minecraft目录与第三方依赖的命名空间保持不变；本项目自建的兼容性测试模组并非外部第三方，随之改为differentenchant_tests。

机器仍是18格3×3×2，原碰撞、模型几何和三张物品PNG没有变化。原GUI坐标、字号、绘制、随机/指定行为、30级服务端门槛及其隐藏、36架曲线、12点核心容量和Lv.1→2规则不变。没有添加新容量配置；以前固定12，本次继续固定12。默认配置每个键值保留，仅文件名和注释命令名变化。

64份生产源文件/资源逐个与ce00558比较，允许的替换只有项目名、包名和命名空间；结果没有额外业务变化。三张PNG的Git blob相同。对新发布JAR的条目、class符号、JSON与properties扫描无旧命名，并验证Fabric元数据和入口；不包含GameTest模组。

旧构建产物先归档至忽略目录.tools/rename-backup/jars，再执行clean重新构建。build/libs只提供新名称的运行JAR与sources JAR。原开发配置先移出活动config目录保存，不读取也不迁移其值；新实例生成新配置。

## 世界与配置边界

这是正式更换Mod ID，不做旧测试世界、旧配置或旧物品ID兼容，也不提供旧ID别名。请用新建世界验证和试玩。新世界正常保存/重进后，18格机器、机器等级、核心物品及能量、已附魔装备和世界私盐仍必须正确。既有磁盘重连、跨机器转移、完整区块卸载/返回以及费用/真实预览回归继续执行。

当前安装说明、功能设计文档和开发脚本均使用新名称。README中同步修正了第五轮已实现但旧段落仍称“没有共享库存/不实现生存升级”的过期说明，没有修改游戏行为。

## 旧名称保留范围

历史DEVLOG段落、docs/validation已有记录（原日志、XML、哈希）、prompts旧任务书和docs/screenshots历史截图保留原文，避免伪造过去的测试或图片。本文与本次日志只在说明“Better Enchant / BetterEnchant / better_enchant / better-enchant / dev.betterenchant → Differentenchant”时引用旧名称；不作为新ID或别名。源目录、当前资源、当前元数据和当前安装命令均不再混用旧名。

Git历史自然仍有旧名。现有本机checkout文件夹名称、.tools内归档及忽略缓存没有改名，它们不是模组运行标识，也不进入发布JAR。GitHub仍为muzhemuzhe/MC-enchant-improve，origin仍为ssh://git@ssh.github.com:443/muzhemuzhe/MC-enchant-improve.git；没有改GitHub仓库名、remote或Git用户身份。

## 测试与截图

确切命令、XML、日志、命名审计与概率输出对照在 [validation/rename/README.md](validation/rename/README.md)。截图由重命名后的真实26.1.2客户端生成，放在 [screenshots/rename](screenshots/rename)，旧截图不覆盖。

仍不把服务端双玩家菜单测试当作两台独立客户端网络压力测试，也不把编译通过当作资源渲染和新世界持久化已验证。本轮不顺带处理文字重影、视觉重做或任何后续玩法。
