# 刻录功能直接源码整合

2026-10-15。基于官方 `1.21` 提交 `b44ee583db5f946229c08371aac99aeae166c31d`，在本仓 `dev` 直接整合已保全的音乐刻录功能。历史 `muxi-auth/music-compat` 未由本子任务修改；本记录不代表 fork 已发布或通过原生复验。

## 直接改动

- `CDBurnerMenu`、`ComputerMenu` 构造时直接以 `BurnLockedItemHandler` 包装原始 input/output，保持原验证器、回调和 slot 对同一 handler 的引用。`clicked`、`quickMoveStack`、`removed` 直接实现原锁盘及关页解绑/清槽行为；`setSongInfo` 直接拒绝未验证写入。
- `SetMusicIDMessage.handle` 在调用菜单之前验证写入权限，避免第三方菜单 HEAD 注入在方法体守卫前处理旧网络包。已验证烧录继续由 `BurnServer` 的 receipt/escrow 事务完成。
- `CDBurnerMenuScreen`、`ComputerMenuScreen` 直接持有刻录 UI 状态，并在 `init/render/removed` 调用受控流程。后台任务仍由连接持有，关页只解绑视图；默认授权、记忆、登录、本地选择器、自动元数据、只读选项、进度、取消和重开恢复均从既有实现直接合入。
- 原刻录按钮直接调用受控入口，避免第三方 `handleCraftButton` HEAD/RETURN 下载逻辑先于验证任务。保留原方法名称作为第三方/QA ABI，方法体委托同一受控入口；服务未就绪时拒绝刻录，不退回未验证旧下载路径。

## 移除和保留

已删除本仓复制来的自身注入类：`BurnGateMixin`、`CDBurnerHandlersMixin`、`ComputerHandlersMixin`、`BurnMenuLockMixin`、`BurnScreenMixin`、`ComputerBurnScreenMixin`。构建 Mixin 清单必须同步去掉这些名字以及 `BurnMenuAccess`，由主协调任务维护。

`net.muxigame.musiccompat.burn.BurnMenuAccess` 为**普通 Java 接口**；两菜单直接实现 `muxiBurn$output()`。方法 ABI 保留，产品、测试和 QA 引用同步更新。它不能位于已声明的 Mixin 包，即使不列入配置也会触发 `IllegalClassLoadError`。2026-10-15 fork slot-r2 原生启动暴露此问题，已从旧 mixin 包迁出；旧失败日志保留，修复需新构建和原生启动复验。`BurnPlayerListAccess` 仍访问 Minecraft 第三方玩家保存 API，可以保留；播放相关注入由另一任务整合。

## 验证

对真实上游类执行 Java 21 编译：两屏幕、两菜单、普通接口和旧包入口通过。使用真实官方 IPN 2.0.4 和 JetBrains annotations 24.1.0 依赖，没有虚拟占位类。

扩展现有 `BurnLockedHandlerTest`，实际构造两种菜单，验证锁盘 handler、真实 slot 引用、拒绝未验证 `setSongInfo` 和清槽；共 **32 检查通过**，其中新增 8 检查直接覆盖 fork 菜单。原 21 个 Java 测试套件均保留，没有另建一套替代测试。局部日志位于忽略目录 `build/burn-integration-check/`；完整产品源码构建、全 21 套件与最终产物 SHA 由统一构建记录提供。

尚须用 fork 完整产品在统一测试环境再次执行原生 UI/盘锁/关页矩阵。历史 candidate12 的 16/16 原生关页证据属于整合前适配层，不能直接当作本次源码重构后的原生结论。

## Playlist 构造器覆盖修复（2026-10-15）

r4 原生诊断在 `insert:true` 确认菜单已锁、托管盘 1 张，但输入 handler 是 Playlist 注入的匿名类，输出仍为 BurnLockedItemHandler；模拟插入未拒绝。Playlist 4.3 两个 CanBurnerCDList Mixin 都通过字段初始化写 input，覆盖了过早创建的包装。两菜单现先保留原初始化，再在构造器 body、addSlot 前调用私有方法包装最终 handler。字段写入放入私有方法，避免 Mixin 在构造器寻找字段初始化写入时把重新包装也当作注入锚点。槽位捕获与公开 getter 使用同一包装，第三方 validator/callback 继续委托。新增真实菜单 slot handler identity 与外部 validator/模拟插入委托检查；必须新构建后原生复验，不能用离线测试代替 Playlist 变换验证。
