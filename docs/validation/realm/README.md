# 附魔者维度第一阶段验收

版本 **0.14.0**，Minecraft Java **26.1.2 / Fabric / JDK 25**。开发基线 `6c8fc99`；`git fetch origin` 后本地 main 与 origin/main 为 0/0，再创建 `feature/enchanter-dimension`。原有未跟踪 `bin/` 保留、不提交。全部修改仅在开发分支，没有 main 提交、合并或 main 推送。

[世界设计、ID、生成方式及边界](../../ENCHANTER_REALM.md)。PNG 为真实原生 OpenGL Minecraft 客户端直接截图，未拼接、调色、修图或手工搭景。

## 实拍

| 画面 | 原始截图 |
| --- | --- |
| 青绿原野全景 | [01-mint-fields.png](screenshots/01-mint-fields.png) |
| 青林远景 | [02-azure-woodland.png](screenshots/02-azure-woodland.png) |
| 基础青木近景 | [03-azure-tree.png](screenshots/03-azure-tree.png) |
| 山地与河谷 | [04-hills.png](screenshots/04-hills.png) |
| 稀有浮岛远景 | [05-floating-island.png](screenshots/05-floating-island.png) |
| 天空整体效果 | [06-sky-day.png](screenshots/06-sky-day.png) |
| 主世界午夜时的境域 | [07-sky-midnight.png](screenshots/07-sky-midnight.png) |
| 自然河流水体 | [08-river-valley.png](screenshots/08-river-valley.png) |

种子 **2026102001**，正常主世界预设。附魔者维度由独立噪声生成器生成，不是超平坦摄影布景。摄影仅调整玩家位置、创造飞行和隐藏 GUI，未改 Realm 方块。坐标、视角与原始文件哈希见 `screenshots.json`。

## 可复现命令

```powershell
& 'C:/Users/muzhe/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' tools/generate-realm.py
.\tools\validate-realm.ps1 -Stage build -Log realm-build-final
.\tools\validate-realm.ps1 -Stage server -Log realm-server-final
.\tools\validate-realm.ps1 -Stage client -Log realm-client-final
```

脚本使用 `.tools/jdk-25.0.4.1+1`、Gradle 9.7.1 与已有离线依赖缓存，实际执行 `build`、`runGametest -Prealm`、`runClientGametest -Prealm`。客户端为 Fabric 官方 Client GameTest 驱动的真实 Minecraft/OpenGL 进程，含真实网络移动、管理员命令和完整服务器重开，并非渲染模拟。

## 依赖与 API 核对

- [Fabric 官方 Loader 元数据](https://meta.fabricmc.net/v2/versions/loader/26.1.2/0.19.5)，本轮重新获取原文为 [fabric-loader.json](fabric-loader.json)。
- [Loom 1.18.4 官方 Maven](https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.18.4/) 与 [Fabric API 0.155.3+26.1.2 官方 Maven](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.155.3%2B26.1.2/)，沿用已锁定兼容组合。
- Mojang 官方客户端数据、当前 Loom 反编译源码中的 DimensionType、EnvironmentAttributes、BiomeSpecialEffects、SurfaceRules、NoiseBasedChunkGenerator、SkyRenderer、LeavesBlock、WorldGenRegion、ServerPlayer、SavedDataType，以及 Fabric Client GameTest 源码。没有 Yarn 或旧版 remapping。
- 当前版 `noise_router.preliminary_surface_level` 替换了旧初步密度槽，`random_patch` 特征已移除；最终使用当前原生 simple_block + 放置修饰器。首次注册表失败日志保留，编译不能替代此验证。

## 结果与限制

- 正式构建成功，**32/32 JUnit**；一项青木测试覆盖300种形状的连通、叶距、范围与重放。
- 服务端 **47/47**：46项本项目（含新增安全落点/存储Codec）与1项原版；覆盖现有三机、研究、费用与村民交易。
- 真实客户端完整流程成功；同种子独立新世界178176个方块材料指纹一致，保存重开后的地形及返回记录一致，真实下界来源返回成功。
- 再读取两个独立客户端进程的四份真实保存文件，以注册名解析178176个原生区块方块（排除易变的运行时数字ID）：四份SHA-256均为 `bb67fc8691ba5f36270f2cbc294822ef9db6cb8378203f343c8f44ecb90a064e`。见 [原生存档重放审计](saved-seed-replay.json)，可用 `tools/verify-realm-seeds.py` 只读复核。
- 625个区块中心采样：地表含浮岛Y63..195，44个水列，3个稀有浮岛列；大陆最大相邻高度变化内部/边界均3。
- 主世界午夜光照正常下降，Realm固定15；原版三个维度保留minecraft噪声设置，日月隐藏限定Realm。
- 管理员在模拟已有40格摔落距离的状态执行真实Brigadier进入/返回命令，成功落地后累计距离清零、地面状态成立。
- JAR SHA-256 `a0d825a86f2fb74d20ba0c18bcd7ecc3e8b536e78fee017b1ee6a88687e2922c`，225份资源与源码匹配、无测试内容，1002份基线代码/资源/历史材料内容未变。默认测试入口已恢复。

实际最终结果见本目录日志、XML、`client-report.txt`、`artifact-audit.json`。验证覆盖真实进入/移动/返回、正常注册、两种生态、自然树木、山地/水体/稀有浮岛、昼夜隔离、保存退出后重进与返回锚点、下界来源返回，以及同种子独立新世界重放。旧三机/研究/支付与村民交易由服务端回归覆盖。

未进行整合包、外部专用服务器双客户端、高延迟、长时间大规模性能压测、进程强杀/断电或不同种子的大规模统计。同步巡查 625 个完整区块及高度探针时有一次服务端追赶警告，这是测试集中生成引起的停顿，生产代码没有这类巡查循环，不能据此宣称大规模性能完成验收。

远雾较柔和、山坡有较密台阶纹理，树皮与配色仍为临时资源，等待人工审核。本轮没有正式入口、矿石玩法、生物/Boss/文明/建筑/神殿、新附魔、额外进度或 Lv.3–5 升级。

历史失败保留：首次数据包用了已移除特征/旧密度字段；一次坡壁移动夹具失败、一次摄影全下载等待超时；最后一次目检拒收镜头进山坡的黑色近景，改摄影位置并加空气断言后重拍，未编辑图片。开发账号的Realms认证失败和残留测试选项anisotropic=0为环境日志；没有本轮缺失模型、纹理或最终注册表错误。
