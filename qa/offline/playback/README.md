# Playback offline probes

Python 驱动测试已迁入当前 fork；QA10Probe.java 是历史 candidate10 辅助探针，不纳入当前统一 runner，迁移后未复验。

这些小测试只验证 QA 判定和传输边界，不启动 MC，不进行网络请求，不代表原生验收。

- `QA10Probe.java`：21 项 signed-media guard、歌词 future 状态、账号计数和 180 条容量边界检查。
- `short_driver_test.py`：9 项音频/歌词独立判定、实际 future 预算和清理前状态保全检查。导入相邻 `qa/entry042` 中的真实驱动；模拟 coordinator、日志和结果仅写 `TemporaryDirectory`，结束即清理。

在仓库根运行 Python 模型：

```powershell
python -B qa/offline/playback/short_driver_test.py
```

Java 探针使用 JDK 21。复用当前授权 QA builder 输出目录的 `classes` 和 `compile.args` 内 `-classpath` 值；该值已经包含产品 JAR、Minecraft 与音乐依赖，不另复制 pack、SDK、JAR 或 class。由调用者提供本次 `TemporaryDirectory`，仅将探针编译结果写入其中：

```text
javac -encoding UTF-8 -proc:none -classpath "<qa-build/classes>;<compile.args中的classpath>" -d "<TemporaryDirectory>" qa/offline/playback/QA10Probe.java
java -classpath "<TemporaryDirectory>;<qa-build/classes>;<compile.args中的classpath>" QA10Probe
```

保留源码即可复跑；不要将临时 class、argfile、模拟 JSON 或日志加入源码树。`QA10Probe` 输出 21 checks / networkCalls=0 / mcStarted=false；Python 输出 JSON 摘要，详细日志仅在测试执行期间位于 Temp。
