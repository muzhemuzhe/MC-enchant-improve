# 0.4.3 独立附魔台禁用验证

生产改动仅为既有UseBlockCallback：目标原版附魔台双端消费普通使用；主手潜行双空手继续服务器形成服务。非原版台PASS，潜行持物时原版已跳过方块打开菜单，允许继续物品放置。原版配方、BlockEntity、采集/掉落和未来工作方块注册基础不变，没有职业、POI或交易实现。

最终22项JUnit、31项真实服务端和5项实际OpenGL客户端通过，具体命令与耗时见DEVLOG。

- `baseline.log`：原372b40a的构建与27服务端基线。
- `initial-server.log`：保留首次旁观者替身断言失败。原版mock固定gameMode()=CREATIVE，因此改用真实客户端旁观者覆盖；没有取消该安全要求。
- `full-validation.log`：31项服务端与5项真实客户端全部完成。
- `final-server.log`：最终服务端测试源码重新通过。
- XML：单元与服务端详细结果。`balance-comparison.json`：原2,700,000次固定种子模拟的28份输出与0.4.2一致。

服务端新增测试实际走ServerPlayerGameMode入口，非只调用一个测试回调；验证普通双手/持物、生存创造冒险、成功失败组装、双空手要求、副手独立包不组装、重复不换UUID、拆解恢复后仍禁用。真实原配方匹配/输出、实际放置/邻接放置/物品数量、正确工具掉落与创造不掉落、铁砧及砂轮菜单均通过。既有附魔书、随机与指定流程回归。

客户端除当前屏幕和服务器容器外，还监听原版附魔Screen初始化次数为0，覆盖短暂闪开的情况。对当前库存容器发送无效附魔按钮请求，确认不会创建服务器附魔菜单或扣费。原随机/指定两个完整客户端流程仍执行。Shift使用已有实际键盘输入；物品槽Shift转移沿用已记录的测试API修饰键处理。

[创造普通右击](../../screenshots/standalone-table/standalone-01-creative-no-menu.png) · [生存普通右击](../../screenshots/standalone-table/standalone-02-survival-no-menu.png) · [不完整组装](../../screenshots/standalone-table/standalone-03-incomplete-no-menu.png) · [拆解恢复](../../screenshots/standalone-table/standalone-04-restored-no-menu.png) · [恢复后失败组装](../../screenshots/standalone-table/standalone-05-restored-incomplete-no-menu.png) · [生存重新组装后机器GUI](../../screenshots/standalone-table/standalone-06-survival-reassembled-machine-gui.png) · [实际旁观者](../../screenshots/standalone-table/standalone-07-spectator-no-menu.png)

日志仅规范化开发机绝对路径。截图是真实客户端输出，没有人工逐步鼠标验收或整合包/高延迟联机验证。村民原有交易未改代码，但本轮没有做实际交易流程测试。
