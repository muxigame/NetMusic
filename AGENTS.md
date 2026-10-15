# NetMusic fork 工作约定

开始工作先读父仓 `../运维手册.md`、`../OPERATIONS.md`、本仓 README 和 SOURCE-PROVENANCE.json。真实源码仅在已登记的本目录维护。

- `origin` 是 muxigame/NetMusic fork；`upstream` 是 TartaricAcid/NetMusic。开发分支 `dev`，同步目标 `upstream/1.21`。先审 diff 再 merge，不 reset/rebase 覆盖已验证集成，也不把官方其他 MC 版本分支合进来。
- 本仓直接编译 NetMusic 源码，独立 music-compat 已迁入。不能重新同时装原 NetMusic 与 fork，也不能重新引入独立 muxi_music_compat 模组。
- 保留已有网络消息/NBT key；内部 Java 旧包名是兼容接口，不代表仍依赖另一个模组。Minecraft/Playlist 外部桥接与自身源码逻辑要区分。
- 新版本构建输出使用新目录，保存源码/依赖/资源与产物 SHA。QA JAR 不发给玩家。账号后端在兄弟 muxi-auth，凭据/曲库/测试存档不进本仓。
- 原生测试只允许父仓 test-environment/runs，资源/toolchains/cache同父约定；关闭后确认exit0、端口和世界锁释放。发布另按当次授权。
