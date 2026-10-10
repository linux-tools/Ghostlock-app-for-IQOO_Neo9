# GhostLock for iQOO Neo9

> English: [README.md](README.md)

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
| iQOO Neo9（PD2338）12GB / 16GB | SM8550 | `5.15.197-g708015331567-dirty` | 已注册进 `index.conf`；偏移取自己 root 设备的只读提取，重试预算按命中率优先放大。若你的固件一次都不命中，按[设备端校准](#设备端校准只读提取--profile-生成)重提 |

上游内置的其他 release 仍然可用，但本 fork 不为它们做验证；列表见[支持设备列表](docs/kernel_profiles/SUPPORTED_DEVICES_ZH.md)。按项目规范，未过真机门禁的 profile 不标 supported。

## 快速开始

打开 **GhostLock** 点击 **执行**。应用**不再随包分发管理器**：请在首页「KSU 管理器」卡片选择你已安装的管理器（KernelSU、KernelSU-Next、SukiSU Ultra、ReSukiSU、KowSU、7kimisu，或自定义包名）。激活时先加载 `vrpatch.ko`，再用该管理器的 `ksud late-load --allow-shell --package-name <包名> --kmi <KMI>` 拉起 KernelSU；缺可用 `ksud` 时 W1/W2 仍可拿到 uid 0，但不会加载模块。开启「仅保留临时 root」则停在 `vrpatch.ko` 之后，保留调试用 root 终端。

执行链由三类组件构成：frontend（`root_child` 启动/交接）、backend（CVE-2026-43499 futex 原语）与 middleware 路线。**编目组合在构建期实例化，具体运行哪一个由解析后的 profile 选择**。路线是双核竞争：6.6/6.12 树形 waiter 内核上主线程跑 `select` 爆破、consumer 线程扰动 waiter 优先级；6.1 紧凑 waiter 内核上主线程改走 `getsockopt(TCP_ZEROCOPY_RECEIVE)` 打洞页写入；5.15 内核走 multicast waiter 路线。CPU 对同样由解析后的 profile 决定。

### 调试 root 终端

`RootTerminal.kt` 提供三个后端并自动切换：KernelSU 激活后走管理器 `su`（原版 KSU init 会连同脚本自己的文件轮询循环一起收掉，此时文件通道已失效）；激活前走临时 root 文件通道（App 写 `.gl_cmd`，脚本循环用 `/system/bin/sh` 执行并追加到 `.gl_out`）；完全没有 root 时回落到应用自身的 `/system/bin/sh -c`。输出按行流式回传并截断上限，`dmesg` / `logcat` 不会卡住 UI。

## 文档

- [Kernel Profile 适配指南](docs/kernel_profiles/README_ZH.md) —— 如何支持一款新内核。GhostLock 按精确 `uname -r` 匹配，未匹配的内核直接拒绝运行并在 App 顶部显示状态。内置配置位于 `app/src/main/assets/kernel_profiles/`：每个 release 一个 HOCON 文件，`index.conf` 保存运行索引，`<major.minor>-template.conf` 提供各内核大版本模板。
- [支持设备列表](docs/kernel_profiles/SUPPORTED_DEVICES_ZH.md) —— 内置内核清单。
- [公共执行默认值](docs/kernel_profiles/defaults_ZH.md) —— 每个 `execution` 字段的默认值与取舍。
- [Profile 结构文档](docs/kernel_profiles/PROFILE_SCHEMA_ZH.md) —— profile 的完整结构、字段语义与数据流。
- [联发科物理地址](docs/kernel_profiles/MEDIATEK_ZH.md) —— 补两个物理地址。
- [新增组件指南](docs/development/adding-a-component.md) —— 为 native 添加新 middleware / backend / frontend 的开发者指南。
- [设备端校准工作流](tools/device-extract/README.md) —— fork 独有：已 root 设备上的只读提取与 profile 重建。

明确标记为**需要 Shizuku**的固件通过 shell UserService 执行。先使用 ADB 启动 Shizuku，再点击顶部支持状态区域授权；其余固件沿用应用内执行路径。

## 构建

```sh
./gradlew :app:assembleDebug      # APK 在 build/app/outputs/apk/debug/
./gradlew :app:testDebugUnitTest  # JVM 单测（无需 NDK）
```

arm64 payload 默认使用已验证的预构建 `native-prebuilt/ghostlock-vivo-neo9.so`，打包时复制为 `libghostlock.so` 并 strip。它带有上游 `src/` 树里没有的 vr guard（tracepoint 中和）与重试版 multicast route。若要从源码构建，把该文件移出 `native-prebuilt/`，Gradle 会改为编译 `src/`：

```sh
make -C src ghostlock              # NDK 构建 -> build/native/ghostlock
make -C src native-host-tests      # 主机单元测试
make -C src lint-tidy              # clang-tidy，要求 0 findings
```

Gradle 从 `ANDROID_NDK_HOME` / `ANDROID_NDK_ROOT` / `local.properties`（`ndk.dir`）探测 NDK。

### 改写预构建内的 root 脚本

预构建 payload 把 root 脚本作为单个字面量嵌入，因此改写必须保持字节等长。`tools/patch_root_script.py` 原地重写（末尾补空格）并保持 `snprintf` 占位符顺序不变：

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

## 命令行调试

adb/shell 环境无 seccomp 过滤，会跳过 W3，适合快速验证：

```powershell
make -C src ghostlock
./gradlew exportKernelProfiles
adb push build/native/ghostlock /data/local/tmp/ghostlock
adb push build/kernel-profiles/<release>.bin /data/local/tmp/profile.bin
adb shell chmod 755 /data/local/tmp/ghostlock
adb shell /data/local/tmp/ghostlock --load-prebuilt-profile /data/local/tmp/profile.bin
```

## 偏移量提取

`tools/extract_rs` 从 `boot.img`（可加 `xbl_config.img`）、完整 OTA zip 或指向它的 `http(s)` 链接解析偏移量。kallsyms 传 `--kallsyms`，或省略以直接恢复镜像内嵌表。`pselect_waiter_shift` 与 `off_slide_loggers_0_1` 由内置 arm64 反汇编器推导。联发科镜像没有 `xbl_config.img` 且通常无内嵌 BTF：物理加载地址由 kallsyms `_text` 推导（可用 `--phys` 覆盖）。

```powershell
Push-Location tools/extract_rs
cargo build --release
Pop-Location
build/extract/release/ghostlock-extract.exe boot.img --xbl-config xbl_config.img --format conf --out profile.conf
build/extract/release/ghostlock-extract.exe OTA.zip --format conf --out profile.conf
```

提取结果使用 `--format conf` 输出：flatten（无 `include`、凭据/KernelSnitch 常量内联）的自包含 profile。提取器把镜像实际获得的所有字段都写出，未获得的字段直接省略，不会用相邻内核族的猜测值（未验证族的 6.6、缺省 `-2`、5.15 multicast 常量、phys 默认）补齐；route 由 `--analysis` 证据建议、`--route` 可覆盖。输出一律是 **unverified candidate**：可导入、可解析，缺失或无效字段由 App 在执行前校验拦截，不能仅凭生成成功声明设备支持。5.x 还会从 `init_cred` 推导凭据引用修复值、从 BTF 推导 multicast 几何（见 `docs/analysis/extractor-5x-derivation-plan.md`）。`--format json` 保留给 v1 导入路径。新增内置配置时以对应大版本模板为基础补齐和验证字段，再将独立 `.conf` 登记到 `kernel_profiles/index.conf`。旧 C `offsets.h` 注册表已经弃用并移除。

### 联发科

联发科镜像没有 `xbl_config.img`，通常也没有内嵌 BTF，提取器无法从镜像推导两个物理地址
（`kernel_phys_load`、`kernel_phys_offset`），会把它们留成 `null`。运行时按 SoC 公式回退，在联发科上
会在 W1 失败。请先在已 root 的设备上运行单独的 `tools/mtk-phys/` 提取器（读取 `/proc/iomem`），
再把两个值填入 App 的高级参数覆盖。参见 [MEDIATEK_ZH.md](docs/kernel_profiles/MEDIATEK_ZH.md)。

### 前置检查

提取器在提取偏移量前先反汇编 `remove_waiter()`。已包含修复的内核以退出码 `6` 拒绝；仅未修复内核继续。

### 手机端运行

完整 OTA 可直接在手机上分析：传完整包时自动提取 `boot` + `xbl_config`。在 App 沙箱内运行时，`--work-dir` 必须指向 App 可写目录。交叉编译后 push：

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

### 外部导入偏移，免去重新构建应用

新增内核不再需要重新打包 App：点击 **导入 offsets.conf (HOCON)** 选择提取器产出的扁平 `.conf`，旧 JSON 报告仍可通过 **导入 offsets.json (v1)** 导入。v1 JSON 由 App 侧转成 GLK1，无需再把文件推到设备；native 始终只接收 App 经 stdin 传入的 GLK1 文档，并先按当前 `uname -r` 匹配解析后的 profile，匹配成功才视为受支持。多次导入会合并；新文件含已存内核时，App 会先询问是否覆盖。

App 也能直接生成这份 profile：**解析完整包链接**（完整 OTA zip 的 `http(s)` 链接）与 **解析镜像**（`boot.img` + 可选 `xbl_config.img`）都在 App 进程内跑提取器，成功后把一份扁平 `.conf` 写入 App 数据目录：

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
