# NetMusic 源码整合与原生验收（2026-10-15）

已将独立 music-compat 的游戏端能力直接并入正式 [NetMusic fork](https://github.com/muxigame/NetMusic)，保留官方历史，以 upstream/1.21 的 b44ee583db5f946229c08371aac99aeae166c31d 为基线。开发分支 dev；账号后端继续在兄弟 muxi-auth。当前生产未发布。

## 实现与维护

播放状态、PCM解码、歌词、异步过期回调、两类刻录菜单及屏幕直接在NetMusic源码实现。删除11个针对自身类的Mixin，仅保留5个Minecraft/Playlist外部桥接。内部旧Java包名、网络消息和NBT键保持兼容；不再注册独立muxi_music_compat模组。普通BurnMenuAccess接口位于burn包，不能放在Mixin包。

Playlist会在构造阶段替换输入handler；两菜单在字段初始化完成后、建立slot之前调用私有方法包装最终handler，保留第三方validator和callback。不能退回字段初始化时提前包装，也不能把包装赋值直接放成构造器最后的同名PUTFIELD锚点。

唯一已验证构建入口为 tools/build_candidate.py；Gradle构建/运行入口明确拒绝并指向它。所有业务类从本仓源码编译，原版JAR仅提供544个白名单第三方soundlibs条目，没有复制原业务class。原资源/SPI/AT及许可保留。上游更新流程见README，未来更新必须重新合并和验收。

## 最终验证

最终source-build-9：版本1.5.2-muxi.1+mc1.21.1，产品SHA256 `dc1e451f210d8471930a3e0f15751daaf6d8a0b393bca34385e16b7648c0f5d4`。191个Java源、21套测试通过，其中真实菜单39项检查。qa/offline的42驱动和111Auth后端检查通过；旧固定JAR codec探针仅历史参考，不计当前通过。

最终原生实例 test-environment/client-3/runs/netmusic-fork-20261015-slot-r5，runId e2bf7b6490824450b554d00d09ca50b9。仅加载fork NetMusic，不装原NetMusic或独立兼容模块。

- 两菜单×四阶段×两关闭方式16/16通过，32次实际screen关闭回调；16张原盘各inventory1/total1/escrow0，后台完成且无残留任务。
- 旧入口双端实际PCM/OpenAL与歌词推进通过，约0.34/0.36→5.30/5.34秒。
- 新入口0/5/15/30秒双端时间约0.10/0.18、5.06/5.18、15.06/15.20、30.10/30.20；取盘40tick后声音、歌词、channel与NOTE清理通过。
- 实际断线再加入后追上正在播放的音频，继续推进通过。
- 服务端、两个客户端、测试后端均exit0；25961/25962 TCP/UDP释放，实际世界session.lock独占核验通过，环境租约释放。

证据文件与SHA见 integration-20261015.json；详细本机回执在 .local-validation/fork-slot-native-20261015-r5/ 和对应实例coordinator。真实PCM/OpenAL及原菜单回调已验，未冒充人耳试听、物理按键/系统关闭按钮、真实账号/VIP/OSS或完整生产整合包验收。QA JAR永不发布。

## 已保留的失败与旧模块退休

早期装配旧fixture路径、普通接口留在Mixin包、Playlist覆盖锁盘handler均有失败记录；定位后修复并使用新产物/新实例复跑。QA5首轮文件共享测试失败保留，未放宽原重试断言；随后同断言通过。临时JSON读取空值已修为等待同runId的新鲜盘数快照。最终r5矩阵和播放均一次通过。

原muxi-auth/music-compat已从活动目录移到总仓私有 .local-operations/retired-music-compat-20261015；1162文件移动前后逐SHA核验，旧history/logs及未提交源码均保留。新源/QA/离线测试在本仓，旧Auth构建入口已转发到本仓tools。Auth app、凭据、后端测试与旧 .local-validation 证据未移走。后续只维护本fork，不恢复第二份活动游戏端源码。

编号环境使用test-environment外层resources/toolchains/cache，client-N仅保存独立运行数据。优先复用空闲环境、忙时才新增；本轮暴露历史owner缺进程创建时间可能误判占用，统一工具另行修复并记录于总仓手册，不能把新建client-3解释为必须每轮新建环境。
