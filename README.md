# Muxi NetMusic

本仓是 [TartaricAcid/NetMusic](https://github.com/TartaricAcid/NetMusic) 的正式 [fork](https://github.com/muxigame/NetMusic)，目标为 Minecraft 1.21.1 / NeoForge。保留全部官方 Git 历史，以 `upstream/1.21` 为同步来源，在 `dev` 维护直接源码改动。

原 `muxi-auth/music-compat` 游戏端能力已并入 NetMusic；播放、歌词、解析、菜单和屏幕直接改原类，Minecraft/Playlist 外部挂接仍可能需要 Mixin。玩家包仅使用本 fork 的 NetMusic JAR，不同时安装原 NetMusic 或独立 music-compat。原网络消息、唱片/NBT键及内部Java包名保留兼容；账号后端继续由兄弟 `muxi-auth` 维护。

## 构建与验证

可复跑的精确本机构建入口为 `python -B tools/build_candidate.py --output <新目录> --better-login-jar <固定BetterLogin> --core-jar <固定Core>`。构建从本仓全部业务Java源码生成类；原版NetMusic仅作固定第三方音频库来源，不用其业务class补包。编译依赖、资源和源码哈希写入回执。详细参数以 `--help` 为准。

当前原生工具在 [qa/entry042](qa/entry042/README.md)，只使用父仓统一 `test-environment/`。每轮先构建产品及精确匹配QA，固定SHA，再预检/启动，依次执行关页、旧播放、新播放与晚加入断言。测试结束正常stop并核端口/世界锁。QA JAR永不发布。

本fork已完成全源码构建、16项关页矩阵、双端新旧播放/取盘/晚加入原生验收；结果见 [整合报告](docs/integration-20261015.md)。生产未发布，真实平台账号/VIP/OSS及完整整包仍需对应验收。旧独立模块已从活动目录退休。

## 合并官方更新

开始前读 [项目约定](AGENTS.md)，核对分支、HEAD及工作区，不覆盖在制改动。

```powershell
git fetch upstream
git log --oneline HEAD..upstream/1.21
git diff HEAD...upstream/1.21
```

审阅同一MC/Loader分支的改动后，在干净或已妥善提交的dev上使用 `git merge upstream/1.21`；解决冲突时保留我们的播放/事务契约。更新SOURCE-PROVENANCE、递增自有版本，重新构建及完整双端测试，再push origin dev并单独更新总仓指针。不要直接用GitHub同步按钮覆盖集成分支，不自动跨MC版本升级。

## 来源与许可

准确来源见 [SOURCE-PROVENANCE.json](SOURCE-PROVENANCE.json) 和 [NOTICE-MUXI.md](NOTICE-MUXI.md)。官方作者与soundlibs许可保留；代码与资源分别遵从LICENSE和LICENSE-ASSETS。Playlist/BetterLogin仍是外部依赖，不复制其业务代码或资源。
