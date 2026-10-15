# 当前离线回归与历史参考

从 netmusic 根目录运行：

```powershell
../muxi-auth/.venv/Scripts/python.exe -B qa/offline/run_validation.py --output-dir .local-validation/offline-regression-new --ffmpeg <已有且已核验的FFmpeg.exe>
```

入口运行当前 `qa/entry042` 的刻录、播放、生命周期判定回归，以及兄弟 `muxi-auth` 的十组后端音乐测试。依赖默认取当前 Python site-packages，可重复传 `--deps-dir`。后端源码明确解析为兄弟 muxi-auth，不复制账号服务，不读取真实账号或调用真实 OSS。输出只接受本仓 `.local-validation` 内不存在的新子目录；不启动 Minecraft。

产品的完整 Java suite 与源码哈希由 `tools/build_candidate.py` 负责，需单独执行并保存 artifact.json。本入口不会重建、替换被冻结产品，也不会把历史构建记录计为本轮通过。原生验收仍使用 `qa/entry042` 与父仓统一测试环境。

## 保留的历史源码

- `codec/`：candidate10 固定原 NetMusic 1.5.2 JAR 的解码对照探针、许可及经验。当前 fork 构建排除了原业务 JAR，因此旧自动模块对照命令不能直接用于当前 fork；详见该目录 README。统一 runner 不调用它们。
- `playback/QA10Probe.java`：旧 candidate10 的独立辅助探针，迁移后未重新验证；统一 runner 不调用。

保留以上源码用于追溯，不保留生成音频、class、依赖、日志或旧测试环境到本目录。旧候选的硬编码 SHA/统计不能解释为当前 fork 验收。
