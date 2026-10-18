# 小型研究小屋 · 保留风格的微调放大

基于上一版未提交候选，仅放大地表小屋。不改变浅色木材/石英、双坡木屋顶、门廊和阅读窗的设计方向，不修改地下或中型建筑。当前仍是视觉评审候选，未提交/push，未更新正式版本或发布JAR。

## 尺度对比

| 项目 | 上一版候选 | 本版 |
| --- | --- | --- |
| 模板外包（宽×深×高） | 12×10×10 | 13×11×10 |
| 实际方块外轮廓（含挑檐/前阶） | 11×10 | 12×11 |
| 主屋墙内净平面（家具摆放前） | 5×5 | 6×6 |
| 主屋室内净高 | 4 | 4 |
| 门廊宽度 | 3 | 4 |

模板占地增加约19.2%，实际外轮廓占地增加20%；不是把长宽各放大20%，也没有扩大到中型建筑。主屋内部净平面增加44%，将增量优先用于家具间距与通道，不增加家具密度。

主屋右墙和后墙各外移一格，前窗间距拉开，侧窗重新分段。屋面两侧仍每格上升一格，屋顶保持原高度，用两格宽半砖脊接合偶数宽的主屋；前后山墙高窗相应拓宽。没有把原屋顶硬拉出两格宽台阶，也没有增加厚实屋面填充。前廊两柱间更宽，右侧阅读窗与其讲台/紫水晶角后移；后墙书架、工作桌和椅子按新室内尺寸重新定位，两箱错开留出通道。材料和附魔点缀数量基本沿用上一版。

## 实际客户端截图

全部为Minecraft Java26.1.2真实OpenGL客户端截图：白天、空旷超平坦世界，无夜视、无树木遮挡、无图像处理。测试专用命令走原生RuinPiece放置和容器初始化路径，非建筑渲染器或模拟图。本轮按要求用命令放置评审，不声称完成自然地形生成验收。

| 视角 | 本版 | 上一版 |
| --- | --- | --- |
| 正面 | [01-front.png](01-front.png) | [原正面](../small-cottage-candidate/01-front.png) |
| 侧面 | [02-side.png](02-side.png) | [原侧面](../small-cottage-candidate/02-side.png) |
| 45°整体 | [03-diagonal.png](03-diagonal.png) | [原45°](../small-cottage-candidate/03-diagonal.png) |
| 屋顶俯视 | [04-roof.png](04-roof.png) | [原屋顶](../small-cottage-candidate/04-roof.png) |
| 室内 | [05-interior.png](05-interior.png) | [原室内](../small-cottage-candidate/05-interior.png) |

补充：[阅读窗](05a-reading-bay.png)、[第一箱实际打开](06-first-chest-open.png)、[第二箱实际打开](07-second-chest-open.png)。原始截图路径与SHA-256见[screenshots.json](screenshots.json)。相机为适配新增体积略退后，并非完全同机位像素对比。

## 快速验证

```powershell
$env:JAVA_HOME = (Resolve-Path '.tools/jdk-25.0.4.1+1').Path
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
node tools/generate-ruins.mjs
.\gradlew.bat classes gametestClasses runClientGametest -PsmallReview --console=plain
```

首次编译/客户端检查36秒通过，见[首次日志](client-initial.log)。在同一模板上补充工作桌和出门验证后再运行一次，32秒通过，见[最终日志](client-review.log)。只启用小屋评审入口，未运行JUnit、服务端GameTest、完整附魔回归或中型建筑测试，没有执行完整build/发布打包。随后`processGametestResources`成功恢复默认测试入口。

实际检查：原生模板/资源/战利品/雕纹书架内容加载；无刷怪笼；玩家真实使用开门，不飞不跳从草地上前阶进入；沿通路走到两只箱子并通过使用数据包打开服务端菜单，原生战利品非空；工作桌打开真实合成菜单；最后步行穿过门廊回到室外。见[交互记录](interaction.txt)。目视五个视角未见明显缺块或屋面连接错误，家具未阻断实测路径。

[audit.json](audit.json)记录前后尺寸和模板哈希：中型、地下模板与b0eb40f逐字节一致，中型清单未变；生产Java、战利品、世界生成配置和标签无差异。上一版评审包保留。此次不重新验收自然地形、地下埋藏或完整存档；是否保持小屋感、是否足够舒展，等待用户看图确认后再决定下一步。
