# NetMusic fork 原生验收（QA ONLY）

当前产品源码在本仓 `src/`，账号后端在兄弟 `muxi-auth/app/`。以下当前命令从 `netmusic/` 执行，使用兄弟账号仓的 `.venv` Python。运行时只装本 fork，不再另装原 NetMusic 或 music-compat。

## 2026-10-15 统一环境的当前候选入口

新工作使用 `build_fixture_current.py`、`run_current.py`、`drive_current.py`。下方旧候选、旧目录和排期是历史证据，不能直接重跑；`run_frozen10.py` 的旧固定产物约束保留。

- `build_fixture_current.py --product-build <真实产品构建目录> --expected-product-sha256 <SHA> --output <新QA输出目录>`：核产品源码、固定依赖和实际 JAR，再将产品 SHA 写入 QA JAR 资源。此 JAR 能用于本地原生启动，但构建通过仍不代表 Mixin 或玩法通过。
- `run_current.py --product-build ... --qa-build ... --expected-product-sha256 ... --expected-qa-sha256 ... --mod <每个明确依赖> --name <小写连字符名称> --port <MC端口> --backend-port <后端端口> --inputs <本仓.local-validation内的新清单>`：默认只做预检，并通过总仓 `reserve_slot` 原子预留最低编号空闲 `client-N/runs/<name>`；15 分钟租约写入输入清单，启动使用同一清单认领，正常退出且角色进程均结束后释放。过期须重新预检使用新清单，不复用旧租约。核必需模组闭包、SHA、空闲端口和内存；通过后使用相同参数加 `--start --accept-eula` 实际启动 1 服务端和 2 客户端，默认隐藏。
- 未声明并核验的已有 Java 进程默认拒绝。只有显式 `--coexisting-instance <统一环境内实例>` 才允许已经登记的 loopback 同机实例（可见求生客户端也可并行；音乐自身仍隐藏）：逐个核 owner、runId、PID/createTime、真实完整命令行、cwd、launch.args、端口和已声明内存预算；任何未登记 Java 仍拒绝，已结束实例也不能作为放行依据。并行还须有实测 RAM 与 GPU 余量；稳定 owner/process 进入输入清单，动态资源写 `*-resource-checks.jsonl`，启动时重新检查。此参数只读取其他实例，绝不停止或控制它；对方已结束时优先使用默认无并行路径。`current_scope_test.py` 和 `coexisting_scope_test.py` 共 20 项离线边界测试已通过，不能替代实际并行运行验收。
- 新实例通过统一槽分配器优先复用空闲 `test-environment/client-N/runs/`，已有环境全忙时才新增编号；预检失败释放本次预留，正常预检保留给start，结束后活进程检查通过才释放，资源来自 `resources/client/game/`，Java 来自 `toolchains/`；JAR 经总仓不可变哈希缓存硬链，世界、配置、原生目录和本地测试身份各实例独立。不复制已有账号、生产配置、整个旧整合包或源代码。
- 共享游戏模板、工具链和缓存继续使用外层 `test-environment/resources`、`toolchains`、`cache`；`client-N` 仅存占用元数据和 `runs`，每次运行配置、native、世界独立。当前 Python/Java scope 只允许直接的旧 `runs/<name>` 或 `client-N/runs/<name>`，拒绝别名、嵌套和跨槽 scope；旧证据和正常停机入口保留。
- `drive_current.py --instance-root ... --inputs ... --expected-product-sha256 ... --expected-qa-sha256 ... --run-id <实际owner runId> --suite <matrix|burn|lyrics|lifecycle|network|legacy|playback> -- <原suite参数>`：校验当前scope后复用真实原生断言；加 `--execute` 才发送命令。旧 `command.py` 仍固定历史QA，当前任务不使用它。
- 当前Scope将实际加载的类字节、固定资源与实例内唯一QA JAR对应，核其SHA及owner中的产品/QA、runId、role和工作目录。backend4显式当前SHA/runId参数只接受统一runs；不传这些参数时仍保留历史pin。
- 后端运行真实 `MusicUploads` 与 RSA 回执算法，但账号、歌曲响应、音频和存储均是本地fixture，0 OSS/真实平台请求。原生PCM/OpenAL/菜单证据与真实账号/VIP/OSS证据必须分别记录。
- `current_scope_test.py` 是离线边界回归；其通过不等于真实游戏启动。最终必须保留实际boot、各suite回执、正常停止和退出码。
- `qa_io.atomic_json(path, value)` 用于 host、guest 与 lifecycle 三类命令文件发布。Windows 的短暂文件共享锁只触发对**同一个 pending 文件、同一个命令 ID 和 runId**的原子替换重试，最多 40 次、间隔 25 ms；不会重新生成命令、绕过 ACK 或改变操作含义。其他 I/O 错误立即失败，重试耗尽保留原目标及唯一 pending 文件作为证据。
- `python -B qa/entry042/qa_io_test.py` 的 5 项测试已通过、零跳过：真实 Windows 不共享删除的读句柄释放后成功、持续占用时有界失败、其他错误不重试、精确次数与独立 pending、重试预算检查。首轮原生测试出现的 WinError 5 失败记录保留；这些离线测试不能将该次原生失败改记为通过，仍需新冻结输入后重跑。

## 以下是早期冻结记录

产品固定为 `0.4.3-burn-flow-candidate.1`，SHA256 `12a1c07301f9eec6305023c97a0c4a76ed357cea7c9224e15b72f769fe74adcb`。源码只在既有 muxi-auth/music-compat；测试 addon 永不发布。

当前仅完成编译和离线测试，未启动本候选 MC。父排期为传统跑酷→无尽→Outbreak。音乐继续离线，获得后续独占窗口及输入焦点后才启动，不操作其他 owner 或用户游戏。

运行 build_fixture.py，再运行 prepare_short_run.py。后者仅输出原 BMC runner 命令模板及步骤，不启动进程。独立根位于 better-mc-remake/build/local-mc-debug，UID10000，qa-world，匹配 runId、产品哈希、role/user.dir；新空闲 loopback 端口需窗口开始时检查。维持原 DesktopQA 入口及身份。WMI 2000ms 设置仅适用于原 QA agent，在硬件报告前生效。

所有命令必须经 command.py 的 owned marker 校验。只在 owner marker 建成后复制既有自生成 CC0 WAV（132344 字节、436c5777...9a9985）作为数据。禁止复制账号、配置或源码到运行目录。

`lock-matrix` 在两种真实菜单逐一验证：构造时两 handler 已包装；锁前旧 ItemStack 引用已脱离；重复 begin；普通/shift/拖拽/双击/数字键/副手/丢弃/创造点击；handler 提取与模拟、插入、直接写入、改尺寸、反序列化、读取副本修改；取消后可提取；旧取消不解新锁；锁中关闭按原流程归还；重复 removed 与迟到提交无复制。记录光盘前后计数。该命令会关闭自己的菜单，再打开新菜单继续 UI 验收。

UI 步骤包含默认确认 false、主动确认及取消记忆、已有网易/QQ/B站解析器自动歌名/作者、只读选择与完整 URL 经实际 BetterLogin LoginScreen/原 LoginSuccessScreen 返回保留、原生文件选择/选择器 ×。文件命令调用真实控件，不能声称是物理点击；原生选择器需单独截图记录 OS 输入。

仍须另行实测：实际任务各阶段锁定及进度、失败/取消/超时解锁，断线、死亡/维度变化、菜单替换、服务器正常停止后重新载入的物品计数；本候选 Mixin 在 MC 内应用；正常退出并确认 own port 关闭/session.lock 释放。编译、字节码契约或 lock-matrix 编译不等于上述通过。

Core 账号/token、平台 HTTP/DNS、上传/完成及 consume 回执是明确的本地 fixture；平台身份/VIP/下载授权、后端解码、真实密码学回执、真实 OSS 未在本轮验证。任何未知 HTTP/DNS 拒绝。0 OSS，预算维持63/64。正常退出用 finish 与原 runner stop，超时/强杀不得作为通过。
