# Historical candidate10 codec reference

本目录保留旧候选的固定 JAR 对照源码与 CC0 许可，用于追溯解码经验。**不属于当前 fork 可运行验收入口**。当前 fork compile.args 不含原 NetMusic 业务 JAR，因此原脚本的固定 JAR 搜索会失败；不能添加原业务 JAR 掩盖此差异。新 fork 的 PCM 验证应由当前产品测试和原生播放验收承担。本次未运行这两个历史脚本，不能引用旧回执宣称新 fork 通过。

以下是迁移前历史说明：

# Offline fixed-JAR codec probes

The following instructions describe the historical muxi-auth candidate10 layout. The two Java probes and two Python runners are temporary QA tooling, not production classes or a registered test suite. Old Documents/Codex sources remain historical; this migration does not delete them.

All four location arguments are explicit and required by both runners:

```text
python music-compat/qa/offline10/codec/run_probe.py --product-build <existing-build> --output <owned-codec-output> --ffmpeg <existing-offline-ffmpeg> --jdk <jdk21-root>
python music-compat/qa/offline10/codec/run_actual_private_probe.py --product-build <existing-build> --output <same-owned-codec-output> --ffmpeg <same-offline-ffmpeg> --jdk <jdk21-root>
```

The input product build must provide its own `compile.args`; the actual-product probe also requires `classes/net/muxigame/musiccompat/burn/PrivatePcmStream.class`. Classpath comes from that build, with no r1 or Documents-source dependency. The fixed NetMusic1.5.2 JAR is selected from the actual build classpath and must have SHA256 `731fba4b4d47256d5f44f8392b093b53c7d1c650b66111eda75b820f7f89a14b`. Existing SDKs and original JARs are read, never copied or downloaded.

Generated WAV/MP3, temporary classes, argfiles, receipts and logs go only to the explicit output directory. Output under this canonical source directory or under the input product build is rejected. Source is not copied to output. The first runner generates owned 0.5-second 44.1kHz stereo 440Hz/660Hz sine audio, encodes a small MP3 with the supplied ffmpeg, compiles the SPI probe, and compares ordinary classpath / without-NetMusic / unchanged automatic-module loading. The second runner consumes those existing output fixtures and original-decoder baseline; it does not encode new audio. It loads the actual product classes first and checks the loaded class hash against the supplied build, then compares every PCM byte hash and frame count with the original Manager decoder. Baseline JAR/ffmpeg and fixture hashes must match. Both runners have bounded process timeouts and start no MC, platform login, network or OpenAL.

The Java SPI probe mirrors the two PCM conversions in private getStream and tests mono/stereo through both raw AudioSystem InputStream and original AudioStreamHandlerManager.handle(fileURL). Its zero-read diagnostic is bounded to 50 consecutive reads with 2ms delay. It is not a replacement product reader. The actual-product probe constructs real PrivatePcmStream and PrivateSoundState, consumes buffers until product null EOF, and never supplies a stub implementation.

## Experience from the completed candidate10 proof

These are historical measurements, not claims that this relocated runner was rerun or that every future product passes.

- Self-authored CC0 input: 0.5 seconds, 22050 frames; WAV 88244 bytes and MP3 9238 bytes with the previously used ffmpeg SHA256 `2ce797a0f88d7f067180338fb227f7b1928ea727bd9a4d7a1d022f7c52af71a3`.
- Actual MP3 SPI identified MPEG1L3, 44.1kHz stereo and produced signed 16-bit little-endian PCM. Raw InputStream produced 25344 frames; original Manager produced 24192 frames, 48384/96768 mono/stereo PCM bytes. Both returned one terminal 0 then -1. The pre-repair PrivatePcmStream rejected that zero; the owner corrected bounded zero-read handling.
- Ordinary-classpath WAV failed during initial format identification, before conversion: the rejecting JAAD AAC reader's nested mark/reset left the input at offset 12, then the duplicate reader at 24. The individual JDK WAV reader and without-NetMusic control accepted the same valid WAV. The unchanged NetMusic automatic-module run changed effective provider ordering and all eight WAV/MP3 × raw/Manager × mono/stereo cases reached EOF. No global SPI modification was needed or performed.
- Candidate10 r3 actual-product probe passed four WAV/MP3 × mono/stereo cases, Java exit 0. WAV had 22050 frames; MP3 had 24192. Every output byte hash matched the original-Manager baseline. Loaded PrivatePcmStream.class SHA256 was `d1be884bc3de2107c2c4a3eb11b6d302ddc223eb2e12bd48d850a36ef1080968`, matching that class inside r3 JAR SHA256 `9df0608dadcf94a7d2229ee76e7e265e38ec8a29ea5384cef619e918aedb0216`.

Minimal future evidence is the source plus fixture license, summary, and actual-private-receipt. WAV/MP3, compiled probe classes, argfiles, complete read traces and original logs are regenerable artifacts, not mandatory retained sources. Do not copy old runtime artifacts into this directory. No decoded PCM output file is created; decoded bytes are streamed and hashed in memory. These offline results do not establish MC/OpenAL, production account, upload, private network or release acceptance. They add no AAC/M4A support claim.
