#!/system/bin/sh
# GhostLock 设备只读提取脚本（iQOO Neo9 / SM8550, android13-5.15）
#
# 在已 root 的设备上运行（只读 + kptr 临时放行，结束后恢复）：
#   adb push ghostlock-dump.sh /data/local/tmp/
#   adb shell su -c "sh /data/local/tmp/ghostlock-dump.sh /data/local/tmp/ghostlock-dump"
#   adb pull /data/local/tmp/ghostlock-dump
#
# 产物：
#   uname.txt version.txt props.txt meminfo.txt   设备身份 / RAM 容量（区分 12GB / 16GB）
#   kallsyms.txt                                  符号表（偏移量生成的核心依据）
#   iomem.txt                                     Kernel code / System RAM（kernel_phys_load / kernel_phys_offset）
#   vmlinux.btf                                   BTF（task_struct / cred / mm_struct 结构偏移）
#   dmesg.txt                                     启动日志（辅助核对物理加载地址）
#   boot.img                                      可选（--boot），用于从镜像读 init_cred 推导 cred 模板
#
# 全程只读提取；唯一写操作是 kptr_restrict 的临时放行并在退出时恢复原值。

OUT="${1:-/data/local/tmp/ghostlock-dump}"
DUMP_BOOT=0
for arg in "$@"; do
    [ "$arg" = "--boot" ] && DUMP_BOOT=1
done

mkdir -p "$OUT" || { echo "FATAL: cannot create $OUT (run as root)"; exit 1; }
KP_FILE=/proc/sys/kernel/kptr_restrict
KP_SAVED=$(cat "$KP_FILE" 2>/dev/null || echo "?")
restored=0
restore_kp() {
    if [ "$restored" = "0" ] && [ "$KP_SAVED" != "?" ] && [ "$KP_SAVED" != "0" ]; then
        echo "$KP_SAVED" > "$KP_FILE" 2>/dev/null && echo "kptr_restrict restored to $KP_SAVED"
        restored=1
    fi
}
trap restore_kp EXIT INT TERM

note() { echo "[dump] $*"; }

# ---------- 1. 设备身份 ----------
uname -a > "$OUT/uname.txt" 2>&1
cat /proc/version > "$OUT/version.txt" 2>&1
getprop > "$OUT/props.txt" 2>&1
{
    echo "ro.product.device=$(getprop ro.product.device)"
    echo "ro.product.model=$(getprop ro.product.model)"
    echo "ro.soc.model=$(getprop ro.soc.model)"
    echo "ro.soc.manufacturer=$(getprop ro.soc.manufacturer)"
    echo "ro.board.platform=$(getprop ro.board.platform)"
    echo "ro.build.display.id=$(getprop ro.build.display.id)"
    echo "ro.build.version.incremental=$(getprop ro.build.version.incremental)"
    echo "ro.boot.mem=$(getprop ro.boot.mem)"
    echo "ro.boot.ramdump=$(getprop ro.boot.ramdump)"
} > "$OUT/props.key.txt" 2>&1
grep -E '^(MemTotal|MemAvailable|MemFree):' /proc/meminfo > "$OUT/meminfo.txt" 2>&1
RELEASE=$(cut -d' ' -f3 /proc/version 2>/dev/null)
note "release=$RELEASE"

# ---------- 2. kptr_restrict 放行 ----------
kptr_ok=0
if [ "$(id -u)" = "0" ]; then
    if echo 0 > "$KP_FILE" 2>/dev/null; then
        note "kptr_restrict: $KP_SAVED -> 0"
    else
        note "WARN: cannot write kptr_restrict (SELinux?); relying on CAP_SYSLOG"
    fi
else
    note "WARN: not uid 0; kallsyms/iomem addresses may be zeroed"
fi

# ---------- 3. kallsyms ----------
cat /proc/kallsyms > "$OUT/kallsyms.txt" 2>&1
INIT_TASK_ADDR=$(awk '$3=="init_task"{print $1; exit}' "$OUT/kallsyms.txt" 2>/dev/null)
case "$INIT_TASK_ADDR" in
    0000000000000000|00000000|"") note "FAIL: kallsyms addresses are zeroed (kptr_restrict=$KP_SAVED)"; kptr_ok=1 ;;
    *) note "OK: init_task @ $INIT_TASK_ADDR" ;;
esac

# ---------- 4. /proc/iomem ----------
cat /proc/iomem > "$OUT/iomem.txt" 2>&1
KCODE=$(grep 'Kernel code' "$OUT/iomem.txt" 2>/dev/null | head -n 1)
note "kernel code: $KCODE"

# ---------- 5. BTF ----------
if cp /sys/kernel/btf/vmlinux "$OUT/vmlinux.btf" 2>/dev/null; then
    note "OK: BTF $(wc -c < "$OUT/vmlinux.btf") bytes"
else
    note "WARN: /sys/kernel/btf/vmlinux not readable; struct offsets fall back to base conf"
    : > "$OUT/vmlinux.btf"
fi

# ---------- 6. dmesg（尽力） ----------
dmesg > "$OUT/dmesg.txt" 2>&1 || : > "$OUT/dmesg.txt"

# ---------- 7. 可选 boot.img（cred 模板用） ----------
if [ "$DUMP_BOOT" = "1" ]; then
    BOOT_DEV=$(ls /dev/block/by-name/boot* 2>/dev/null | head -n 1)
    if [ -n "$BOOT_DEV" ]; then
        dd if="$BOOT_DEV" of="$OUT/boot.img" bs=4194304 2>>"$OUT/dmesg.txt" \
            && note "OK: boot.img from $BOOT_DEV"
    else
        note "WARN: /dev/block/by-name/boot not found"
    fi
fi

# ---------- 8. 摘要 ----------
{
    echo "release=$RELEASE"
    echo "memtotal=$(grep '^MemTotal:' "$OUT/meminfo.txt" | awk '{print $2}')"
    echo "kallsyms_ok=$([ "$kptr_ok" = "0" ] && echo yes || echo no)"
    echo "kernel_code=$KCODE"
    echo "btf_bytes=$(wc -c < "$OUT/vmlinux.btf")"
    echo "boot_img=$([ -f "$OUT/boot.img" ] && echo yes || echo no)"
} > "$OUT/summary.txt"
restore_kp
echo "[dump] done -> $OUT"
cat "$OUT/summary.txt"
