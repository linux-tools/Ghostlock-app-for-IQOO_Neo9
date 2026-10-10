# GhostLock for iQOO Neo9

> 中文：[README_ZH.md](README_ZH.md)

**本仓库是 [YuKongA/ghostlock-app](https://github.com/YuKongA/ghostlock-app) 的非官方 fork**（上游仓库：<https://github.com/YuKongA/ghostlock-app>；本 fork：<https://github.com/linux-tools/Ghostlock-app-for-IQOO_Neo9>）。

上游是一个通用的 Android 内核提权工具：用 PI-futex 竞争覆盖 waiter，依次完成 W1（SELinux）、W2（凭据/uid 0）、W3（seccomp），最后把 root 移交给 KernelSU 模块加载；内核按精确 `uname -r` 匹配 HOCON profile，未匹配即拒绝运行。本 fork 保留这套机制与全部上游能力，只在其上做 **vivo/iQOO Neo9（PD2338，SM8550）定向适配与命中率调优**，版本号 `1.2-IQOO-Neo9`。

本文只描述 fork 相关部分与上游要点；上游文档（profile 适配、结构、默认值）仍是权威，见下方文档索引。

## Fork 说明

| 项目 | 上游 `YuKongA/ghostlock-app` | 本 fork |
| --- | --- | --- |
| 版本名 | `1.2` | `1.2-IQOO-Neo9` |
| 内置 profile | 通用 release 集合 | 追加 `5.15.197-g708015331567-dirty.conf`（iQOO Neo9，multicast waiter） |
| KernelSU 管理器 | 随包分发管理器 APK、包名固定 | 不随包分发；首页「KSU 管理器」卡片可选 7 种或自定义包名 |
| 激活顺序 | `ksud late-load` | 先 `insmod vrpatch.ko`，再用所选管理器的 `ksud late-load` |
| root 终端 | 无 | `data/RootTerminal.kt`：管理器 `su` / 临时 root 文件通道 / 本地 sh 三后端 |
| 临时 root | 无 | 「仅保留临时 root」：停在 `vrpatch.ko`，保留调试终端 |
| native 产物 | 由 `src/` 构建 | 默认打包 `native-prebuilt/ghostlock-vivo-neo9.so`（含上游 `src/` 没有的 vr guard 与重试版 multicast route） |
| 现场校准 | 无 | `tools/device-extract/`：设备只读提取 → 生成 profile → 差分报告 |
| root 脚本 | 随 native 固定 | `tools/patch_root_script.py` 对预构建 `.so` 内的脚本做等长改写 |

- 非官方、与上游无关联；问题请在本 fork 仓库反馈，不要打扰上游作者。
- 上游新特性不会自动同步；本 fork 只保证自身设备路径可用。
- 许可证沿用上游的 Apache License 2.0（见 [LICENSE](LICENSE)）。

## 支持设备

| 设备 | SoC | `uname -r` | 状态 |
| --- | --- | --- | --- |
| iQOO Neo9（PD2338）12GB / 16GB | SM8550 | `5.15.197-g708015331567-dirty` | 已注册进 `index.conf`；偏移取自己 root 设备的只读提取，命中率优先的重试预算。若你的固件一次都不命中，按[设备端校准](#设备端校准只读提取--profile-生成)重提 |

上游内置的其他 release 仍然可用，但本 fork 不为它们做验证；列表见 [Supported devices](docs/kernel_profiles/SUPPORTED_DEVICES.md)。按项目规范，未过真机门禁的 profile 不标 supported。

## Quick Start

Open **GhostLock** and tap **Run**. The app ships no KernelSU manager: pick your installed manager in the home-screen **KSU manager** card (KernelSU, KernelSU-Next, SukiSU Ultra, ReSukiSU, KowSU, 7kimisu, or a custom package). Activation loads `vrpatch.ko` first and then runs that manager's `ksud late-load --allow-shell --package-name <pkg> --kmi <kmi>`; without a usable `ksud`, W1/W2 still grant uid 0 but no module is loaded. Turn on **Temporary root only** to stop after `vrpatch.ko` and keep the debug root terminal.

The execution chain is a pipeline of three components: a frontend (`root_child` startup/handoff), a backend (the CVE-2026-43499 futex primitive), and a middleware route. The catalogued combinations are instantiated at build time; the resolved profile selects which one runs. The route races two cores: on the 6.6/6.12 tree-waiter kernels the main thread hammers `select` while a consumer thread perturbs the waiter's priority; on the 6.1 compact-waiter kernels it drives `getsockopt(TCP_ZEROCOPY_RECEIVE)` through a punched-hole page; the 5.15 kernels use the multicast waiter. The CPU pair also comes from the resolved profile.

### Debug root terminal

`RootTerminal.kt` offers three back ends and switches automatically: after KernelSU is activated it runs through the manager's `su` (the upstream KSU init reaps the script's own poll loop, so the file channel dies); before activation it uses the temporary-root file channel (app writes `.gl_cmd`, the script's loop runs it with `/system/bin/sh` and appends to `.gl_out`); with no root at all it falls back to the app's own `/system/bin/sh -c`. Output streams back line by line and is capped, so `dmesg` / `logcat` will not stall the UI.

## Documentation

- [Kernel Profile Porting Guide](docs/kernel_profiles/README.md) - add support for a new kernel. GhostLock matches kernels by exact `uname -r` and rejects unsupported builds, showing the status at the top. Built-in profiles live in `app/src/main/assets/kernel_profiles/`: one HOCON file per release, `index.conf` as the runtime index, and `<major.minor>-template.conf` version-family templates.
- [Supported devices](docs/kernel_profiles/SUPPORTED_DEVICES.md) - the built-in kernel list.
- [Shared execution defaults](docs/kernel_profiles/defaults.md) - every execution-tuning field, its default, and why.
- [Profile schema](docs/kernel_profiles/PROFILE_SCHEMA.md) - full profile structure and data flow.
- [MediaTek](docs/kernel_profiles/MEDIATEK.md) - filling the two physical addresses.
- [Adding a component](docs/development/adding-a-component.md) - developer guide for a new native middleware / backend / frontend (Chinese).
- [Device-side calibration](tools/device-extract/README.md) - fork-only: read-only extraction on a rooted phone and profile regeneration (Chinese).

Rows explicitly marked **Shizuku required** run through a shell UserService. Start Shizuku with ADB and tap the status card to grant access; all other rows use the app's normal execution path.

## Build

```sh
./gradlew :app:assembleDebug      # APK under build/app/outputs/apk/debug/
./gradlew :app:testDebugUnitTest  # JVM unit tests (no NDK needed)
```

The arm64 payload defaults to the verified prebuilt `native-prebuilt/ghostlock-vivo-neo9.so`, copied in as `libghostlock.so` and stripped at package time. It carries the vr guard (tracepoint neutralization) and the retrying multicast route that the generic `src/` tree lacks. To build from source instead, remove that file and let the Gradle task compile `src/`:

```sh
make -C src ghostlock              # NDK build -> build/native/ghostlock
make -C src native-host-tests      # host unit tests
make -C src lint-tidy              # clang-tidy, 0 findings expected
```

Gradle picks the NDK from `ANDROID_NDK_HOME` / `ANDROID_NDK_ROOT` / `local.properties` (`ndk.dir`).

### Rewriting the bundled root script

The prebuilt payload embeds the root script as one string literal, so edits must keep its byte length. `tools/patch_root_script.py` rewrites it in place (padding with trailing spaces) and keeps the `snprintf` placeholder order intact:

```sh
python3 tools/patch_root_script.py native-prebuilt/ghostlock-vivo-neo9.so
```

## 设备端校准（只读提取 → profile 生成）

同型号也可能"一次都不命中"：`offset.*` / `task_struct.*` / `cred.*` 都要经 `kernel_phys_load`、`kernel_phys_offset` 换算成直映射地址，任一项与实际布局不符就写不到目标对象。本 fork 提供了在**已 root 设备上只读提取**并重建 profile 的工作流（详见 [tools/device-extract/README.md](tools/device-extract/README.md)）：

```sh
adb push tools/device-extract/ghostlock-dump.sh /data/local/tmp/
adb shell su -c "sh /data/local/tmp/ghostlock-dump.sh /data/local/tmp/ghostlock-dump"
adb pull /data/local/tmp/ghostlock-dump ./dump-12gb

python3 tools/device-extract/make_profile.py \
    --dump dump-12gb \
    --base app/src/main/assets/kernel_profiles/5.15.197-g708015331567-dirty.conf \
    --tune -o neo9-12gb.conf
```

12GB 与 16GB 各跑一次再 diff：无差异即一份 profile 覆盖两档；有差异时把多数档做成正式 profile，少数档用 App 的**高级覆盖页**单独覆盖差异项（通常只有 `kernel_phys_load`）。

## Command-Line Debugging

adb/shell has no seccomp filter, so W3 is skipped - handy for quick verification:

```powershell
make -C src ghostlock
./gradlew exportKernelProfiles
adb push build/native/ghostlock /data/local/tmp/ghostlock
adb push build/kernel-profiles/<release>.bin /data/local/tmp/profile.bin
adb shell chmod 755 /data/local/tmp/ghostlock
adb shell /data/local/tmp/ghostlock --load-prebuilt-profile /data/local/tmp/profile.bin
```

## Offset Extraction

`tools/extract_rs` derives offsets from a `boot.img` (plus optional `xbl_config.img`), a full OTA ZIP, or an `http(s)` URL pointing at one. kallsyms come from `--kallsyms` or are recovered from the image's embedded table. `pselect_waiter_shift` and `off_slide_loggers_0_1` are derived by the built-in arm64 disassembler. MediaTek images have no `xbl_config.img` and usually no BTF: the physical load address is derived from kallsyms `_text` (override with `--phys`).

```powershell
Push-Location tools/extract_rs
cargo build --release
Pop-Location
build/extract/release/ghostlock-extract.exe boot.img --xbl-config xbl_config.img --format conf --out profile.conf
build/extract/release/ghostlock-extract.exe OTA.zip --format conf --out profile.conf
```

`--format conf` is the extractor output: a flattened, self-contained profile (no `include` lines, the shared 6.x credential/KernelSnitch constants inlined, the route selected from `--analysis` evidence unless `--route` overrides it). The extractor emits every field the image actually yields and omits the rest; it never fills gaps from a neighbouring kernel family's guesses (unverified-family 6.6, the default `-2`, the 5.15 multicast constants, or a phys default). Every output is an **unverified candidate**: importable and parseable, with missing or invalid fields blocked by the app's pre-execution validation, so a successful run never implies device support. On 5.x it also derives the credential reference repair from `init_cred` and the multicast geometry from BTF (see `docs/analysis/extractor-5x-derivation-plan.md`). `--format json` stays for the v1 import path. To add a built-in profile, complete and validate the matching version-family template, save it as a standalone `.conf` profile, and add it to `kernel_profiles/index.conf`. The old C `offsets.h` registry is deprecated and removed.

### MediaTek

MediaTek images have no `xbl_config.img` and usually no embedded BTF, so the
extractor cannot derive the two physical addresses (`kernel_phys_load`,
`kernel_phys_offset`) from the image and leaves them `null`. The runtime then
falls back to the SoC formula, which fails at W1 on MediaTek. Fill both by
running the separate `tools/mtk-phys/` extractor on a rooted device (it reads
`/proc/iomem`) and pasting the values into the app's advanced overrides. See
[MEDIATEK.md](docs/kernel_profiles/MEDIATEK.md).

### Preflight

The extractor disassembles `remove_waiter()` before extracting offsets. Kernels with the fix are rejected with exit code `6`; only vulnerable kernels continue.

### On-device analysis

A full OTA can be analyzed entirely on the phone: `boot` plus `xbl_config`
are extracted automatically. Pass `--work-dir` an app-writable dir when
running inside the app sandbox. Cross-compile and push:

```powershell
rustup target add aarch64-linux-android
$ndk = "$env:ANDROID_HOME\ndk\<version>\toolchains\llvm\prebuilt\windows-x86_64\bin"
$env:CC_aarch64_linux_android = "$ndk\aarch64-linux-android35-clang.cmd"
$env:AR_aarch64_linux_android = "$ndk\llvm-ar.exe"
$env:CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER = $env:CC_aarch64_linux_android
Push-Location tools/extract_rs
cargo build --release --target aarch64-linux-android
Pop-Location
adb push build/extract/aarch64-linux-android/release/ghostlock-extract /data/local/tmp/
adb shell /data/local/tmp/ghostlock-extract /sdcard/OTA.zip
```

### Importing offsets without rebuilding the app

New kernels no longer need an app rebuild: tap **Import offsets.conf (HOCON)**
and pick the extractor's flattened `.conf`, or use **Import offsets.json (v1)**
for an older JSON report. v1 JSON is converted in-app, so nothing has to be
pushed to the device: native always starts from the GLK1 document the app sends
on stdin, and matches the current `uname -r` against the resolved profile
before rejecting the kernel. Imports merge across files; a release already
stored prompts before overwrite.

The app can also generate the profile itself — **Parse OTA link** (full OTA ZIP
URL) and **Parse image** (`boot.img` + optional `xbl_config.img`) run the
extractor in-process and write a flattened `.conf` into the app data dir on
success:

```hocon
# GhostLock kernel profile: 6.12.38-android16-5-g844001fb8721-ab14552068-4k (HOCON, self-contained).
release = "6.12.38-android16-5-g844001fb8721-ab14552068-4k"
schema_version = 1
kernel_major = 6
recommend_shizuku = 0
kernel_phys_load = 0xC7800000
route {
  select_stack {
    waiter_shift = 0
  }
}
fallback {
  to = "none"
}
kernelsnitch {
  collisions = 4
}
task_struct {
  prio = 148
  cred = 2304
}
cred {
  caps_offset = 48
  copy_size = 136
  usage_value = 1
  caps_count = 5
  caps_value = -1
}
offset {
  init_task = 37801728
  init_cred = 37891184
}
```

## 来源与许可证

上游本 fork 源自 [YuKongA/ghostlock-app](https://github.com/YuKongA/ghostlock-app)，Apache License 2.0（见 [LICENSE](LICENSE)）。上游自身又基于：

- [NebuSec/CyberMeowfia](https://github.com/NebuSec/CyberMeowfia)
- [JoinChang/ghostlock-oneplus](https://github.com/JoinChang/ghostlock-oneplus)
- [x-spy/CVE-2026-43499-popsicle](https://github.com/x-spy/CVE-2026-43499-popsicle)

本 fork 的设备适配部分（profile、校准工具、管理器选择、root 终端、预构建 payload 及其脚本改写）同样以 Apache License 2.0 发布。仅用于对自己拥有设备的取证与研究；请勿用于未授权设备。
