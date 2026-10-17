# 0.5.0 共鸣核心验证

基于本地4cb6eaf，保留372b40a的36架及独立原版附魔台禁用提交。JDK25、Minecraft26.1.2、原固定Fabric工具链。没有重写随机算法或改30级服务端门槛。

最终 **25项JUnit、39项服务端（38项目+1原版）、6个真实OpenGL客户端入口通过**。共鸣核心新增3个单元测试及8个服务端GameTest，另新增一个完整客户端入口。测试模组和夹具不打入发布JAR。

```powershell
$env:JAVA_HOME = (Resolve-Path '.tools/jdk-25.0.4.1+1').Path
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
.\gradlew.bat build runGametest --console=plain                  # 原基线，16s
.\gradlew.bat build runGametest --console=plain                  # 核心接入，18s
.\gradlew.bat build runGametest --console=plain                  # 新服务端测试，16s
.\gradlew.bat build runGametest runClientGametest --console=plain # 测试源码编译诊断，1s失败
.\gradlew.bat build runGametest runClientGametest --console=plain # 修复后，2m20s通过
.\gradlew.bat build runGametest runClientGametest --console=plain # 最终生产代码/截图，2m26s通过
.\gradlew.bat build runGametest --console=plain                  # 补充断言后，17s通过
```

首次客户端测试编译发现26.1.2的Inventory.items是private；改为公开getItem遍历检查成品堆叠，无需生产Mixin或访问改写。相应失败日志保留。最终服务端补充Lv.2..5产出不升级、转化保留自定义名称及另一个玩家旧报价被拒绝的断言；生产代码和客户端测试未再改变。

## 覆盖

- 配方：所有16种书/附魔书混合，每本附魔书存有锋利V和耐久III；native RecipeManager匹配和assemble，并通过实际CraftingMenu结果槽Shift取走，九个材料槽均恰好消耗。纸/金锭替代失败；产物1枚、能量0、无附魔。成品默认堆叠64。
- 支付：随机逐个1/2/3，实际附魔累计到11，再越界充满只得12；指定单步5–8及批量合计，预览/加减/29级失败不充能。无核心正常附魔但不追溯；两种创造操作均不产能，指定仍保持既有实际付费规则。
- 生命周期：Lv.1首次完成升2并保留一枚成品；Lv.2重复不升3，Lv.2..5均保留等级；成品移除充能组件、保留自定义名称。物品组件的原生网络Codec、控制器存档、旧字段缺失、异常整数饱和均验证。
- 共享库存：真实服务端双玩家菜单读同一安装槽，第二人取走后第一人不能重复拿；关闭/重复关闭不返还共享槽；成品堆叠Shift安装仅一枚；失效槽拒绝取放。成功随机/指定请求重放均不多付/多充；升级使另一位玩家的旧报价失效且同步新等级/成品。
- 拆解：逐个18部件破坏、重复回调、旧菜单、创造破坏控制器及真实TNT型爆炸均恰好掉落一枚核心，保留能量，不改变原材料规则。
- 原随机/指定、真实预览、书架36、独立原版台禁用、生存/创造组装拆解、存档和渲染完整回归。

客户端从实际生存玩家界面普通点击装入零能核心，通过实际随机选项收集0→3→6。关闭重开和真实磁盘存档重进仍为6；Shift取出，转移另一台机器后仍为6；继续实际附魔6→9→11→12，另一台Lv.1变Lv.2，原机器仍Lv.1。移走成品，安装新核心，指定三步实际支付后再得一枚成品，机器仍2。再次真实存档重进保持2与成品，取出后两个成品在原生背包合并为一组2枚。

同一入口在实际玩家远离2048格后断言控制器getChunkNow为null，再返回重新打开，检查安装中的7点测试夹具和Lv.2。这个7点核心由服务器夹具注入，专门测卸载/拆解，不冒充之前升级的正常生产结果；前面的完整0→12均来自真实成功随机操作。两台机器中心分别(15,-59,15)与(27,-59,15)，结构跨区块。最后破坏外围部件并检查仅掉落一枚7点核心，客户端自动关闭失效菜单。

原27组×100,000概率实验继续执行，28份输出与0.4.3逐文件SHA-256相同，见 `balance-comparison.json`。日志仅规范化仓库本机绝对路径；XML、summary和截图哈希是原始结果的可查索引。

## 实际客户端截图

| 场景 | 图片 |
| --- | --- |
| 两种物品及说明 | [普通核心](../../screenshots/resonance/resonance-02-item-core-tooltip-zh.png)、[谐调核心中文](../../screenshots/resonance/resonance-12-item-attuned-tooltip-zh.png)、[英文](../../screenshots/resonance/resonance-16-item-attuned-tooltip-en.png) |
| 充能前、随机选项可用 | [0/12](../../screenshots/resonance/resonance-20-before-active-random-zero.png) |
| 充能中 | [6/12](../../screenshots/resonance/resonance-21-middle-active-random-6.png)、[11/12](../../screenshots/resonance/resonance-22-before-final-active-random-11.png) |
| 实际Lv.1→Lv.2与成品 | [12/12、附魔结果与Lv.2](../../screenshots/resonance/resonance-23-upgrade-enchanted-result-tier2.png) |
| 重进和跨机器 | [磁盘重进6点](../../screenshots/resonance/resonance-07-disk-reload-still-6.png)、[另一机器6点](../../screenshots/resonance/resonance-08-transfer-other-machine-6.png) |
| 指定批量重复生产 | [规划不产能](../../screenshots/resonance/resonance-13-directed-repeat-plan-no-charge.png)、[第二颗成品、仍Lv.2](../../screenshots/resonance/resonance-14-repeat-attuned-still-tier2.png) |
| 卸载与拆解 | [真实区块重载7点](../../screenshots/resonance/resonance-18-chunk-reload-partial-7.png)、[拆解掉落](../../screenshots/resonance/resonance-19-dismantled-core-drop.png) |

全部23张见 `docs/screenshots/resonance/`，没有合成或伪造游戏画面。核心像素源和生成器另在主资源与tools中。

仍需人工整合包试玩、两台独立客户端的高延迟/断网压力，以及控制器单侧区块缺失时长时间延迟恢复的专门压力验证。本轮已做真实完整机器区块卸载/返回与正常磁盘重连；这不等于覆盖所有区块时序或操作系统强制崩溃。服务器双玩家测试不冒充双客户端网络实测。
