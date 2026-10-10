# 设备只读提取 → Profile 优化工作流（iQOO Neo9 12GB / 16GB）

目标：已 root 的设备上做**只读提取**，生成/校准与设备完全一致的 GhostLock
kernel profile，解决"部分同型号设备一次都没命中"的问题。

## 为什么会一次都不命中

profile 的所有 image 域偏移（`offset.*`、`task_struct.*`、`cred.*`）在
native 端经 `kernel_phys_load` / `kernel_phys_offset` 换算成直映射地址：

```
physical = kernel_phys_load + (image_offset)
direct   = (physical - kernel_phys_offset) | PAGE_OFFSET
```

任何一项与该设备实际值不符，W1/W2/W3 写到的就不是目标对象 → 0 命中。
最常见根因：

| 根因 | 影响 | 用本工作流如何发现 |
|---|---|---|
| `kernel_phys_load` 不是 0xA8000000 | 全部地址错位，必然 0 命中 | `/proc/iomem` 的 Kernel code 实测 |
| `kernel_phys_offset` 非 0x80000000 | 直映射整体平移 | `/proc/iomem` 最低 System RAM |
| 内核 build 不同（同版本号不同 ab/增量） | 符号偏移漂移 | `make_profile.py` 的差分报告 |
| 命中窗口太小（忙设备时序更差） | 偶发 0 命中 | `--tune` 放大重试预算 |

RAM 12GB/16GB 本身**不改变** `kernel_phys_load` / `kernel_phys_offset`
（都是 bootloader/SoC 决定），只扩大直映射范围——内置扫描窗口
（`KERNELSNITCH_IDENTITY_END`）对 16GB 足够。两档若固件相同，一份 profile
即可覆盖；若不同，diff 报告会直接显示差异项。

## 操作步骤

```sh
# 1) 设备端只读提取（12GB 与 16GB 各一台各跑一次）
adb push ghostlock-dump.sh /data/local/tmp/
adb shell su -c "sh /data/local/tmp/ghostlock-dump.sh /data/local/tmp/ghostlock-dump"
adb pull /data/local/tmp/ghostlock-dump ./dump-12gb
#   需要 cred 模板复核时加 --boot（多导出 boot.img，其余仍然只读）：
adb shell su -c "sh /data/local/tmp/ghostlock-dump.sh /data/local/tmp/ghostlock-dump --boot"

# 2) 主机端生成 profile（复刻 ghostlock-extract 的推导规则）
python3 tools/device-extract/make_profile.py \
    --dump dump-12gb \
    --base 5.15.197-g708015331567-dirty.conf \
    --tune -o neo9-12gb.conf

# 3) 对比两档
diff neo9-12gb.conf neo9-16gb.conf   # 或看 make_profile.py 打印的差分报告
```

- **无差异**：把任一份作为该 release 的正式 profile（内置或导入）。
- **有差异**：说明两档固件的地址布局不同。App 按 `uname -r` 精确匹配、
  一个 release 只能挂一份 profile——把多数档位做成正式 profile，
  少数档位的差异项（通常只有 `kernel_phys_load`）用 app 的
  **高级覆盖页**在该设备上单独覆盖。

## 与现有配置的产物关系

- `app/src/main/assets/kernel_profiles/5.15.197-g708015331567-dirty.conf`
  是当前基准（已 root 设备实测偏移 + 显式 `kernel_phys_offset` +
  `usage_offset = 0` + 放大后的重试预算），已注册进 `index.conf`。
- `make_profile.py --base` 以该文件为回退源：BTF 缺字段时沿用基准，
  差分报告列出所有漂移项，两台同型号设备的报告对比即是漂移定位工具。

## 字段来源速查

| Profile 字段 | 提取来源 |
|---|---|
| `offset.init_task/init_cred/root_task_group/selinux_*/slide_*` | `/proc/kallsyms` 符号地址 − `_text`（slide_boot_id=`sysctl_bootid`、slide_loggers_0_1=`loggers`+0x10、selinux_enforcing=`selinux_state`） |
| `offset.empty_zero_page` | kallsyms `empty_zero_page` |
| `kernel_phys_load` | iomem `Kernel code` 起点 − (stext − text)，KASLR slide 在差值中抵消 |
| `kernel_phys_offset` | iomem 最低 `System RAM` 起点 |
| `task_struct.*` / `cred.copy_size` / `kernelsnitch.mm_struct_sz` | `/sys/kernel/btf/vmlinux`（脚本内置最小 BTF 解析） |
| `cred.{usage,caps,ref*}` | base conf（设备已验证）；`--boot` 时用镜像内 `init_cred` 字节复核 |
| `execution.*` | base conf；`--tune` 放大 w1/w2/w3 与 heap 重试 |

注意：`cred.refN_image` 存镜像域指针，native 侧经 `data_alias()` 换算；
低于 `KIMAGE_TEXT_BASE` 的值会被归零，这是与参考机型（5.15.41）一致的既定
行为，refN 的具体数值不影响命中率，不需要按设备重提。

## KSU 管理器随包安装

GhostLock app 已内置原版 KernelSU 管理器（`assets/manager/KernelSU.apk`）。
root 移交阶段要靠已安装管理器里的 `libksud.so` 做 `late-load`，所以主界面
在 **Run 按钮上方**提供"安装 KernelSU 管理器"卡片（已安装则自动隐藏）。
首次安装需授予"安装未知应用"权限，从设置返回后状态自动刷新。
