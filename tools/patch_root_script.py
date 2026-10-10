#!/usr/bin/env python3
"""等长改写预构建 native 里内置的 root 脚本（字符串偏移不变）。

相对参考版原脚本的改动：
  1. debug root 终端：脚本一启动就开一个文件轮询式 root 执行通道
     （app 写 .gl_cmd，后台循环用 /system/bin/sh 执行、结果追加到 .gl_out）
  2. KMI：vivo 自研内核（uname 无 android 训练词）回退 `5.15-android13-8`
  3. 模块加载：直装分支先 `ksud late-load`，仅当其退出码非 0 才回落 `insmod`
  4. `KSU_ENFORCE=9` 为哨兵值：只保留临时 root（跳过 policy fixup 与 KSU 加载）

约束：脚本是 .so 中的单个字面量，改长会移动后续字符串 → 必须等长
（末尾补空格，sh 安全）；它同时是 snprintf 格式串，`%s/%d` 的**顺序**必须与
原脚本一致，且新增文本不得含 '%' 之外的转义问题。

    python3 tools/patch_root_script.py native-prebuilt/ghostlock-vivo-neo9.so
"""
from __future__ import annotations

import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

MAGIC = b"#!/system/bin/sh"

# 占位符顺序必须与原生 snprintf 参数一致：
# HOME_DIR, LOG, SAFE_MODE, KSU_KO, KSU_ENFORCE, KSU_APK, VRPATCH_KO, DEBUG_DIR
SCRIPT = """#!/system/bin/sh
HOME_DIR='%s'
LOG='%s'
SAFE_MODE=%d
KSUD="$HOME_DIR/ksud"
KSU_KO='%s'
KSU_ENFORCE=%s
KSU_APK='%s'
VRPATCH_KO='%s'
echo "[*] root script start uid=$(id -u) euid=$(id -u)" >"$LOG"
chmod 644 "$LOG" 2>/dev/null
echo "[*] seccomp=$(grep Seccomp /proc/self/status 2>/dev/null | tr '\\n' ' ')" >>"$LOG"
# manager selection comes from the app (.gl_ksu_pkg / .gl_kmi)
PKG=$(cat "$HOME_DIR/.gl_ksu_pkg" 2>/dev/null | tr -d ' \\n')
KMIV=$(cat "$HOME_DIR/.gl_kmi" 2>/dev/null | tr -d ' \\n')
[ -n "$KMIV" ] || KMIV=android13-5.15
if [ -n "$PKG" ]; then
  KSUD=$(find /data/app -name libksud.so 2>/dev/null | grep "$PKG" | head -1)
fi
if [ ! -x "$KSUD" ]; then
  for p in me.weishu.kernelsu.pr me.weishu.kernelsu com.rifsxd.ksunext com.sukisu.ultra com.suki.sukisu com.resukisu.resukisu com.kowx712.supermanager com.sevenk.core.pr com.sevenk.core; do
    KSUD=$(find /data/app -path "*/$p*/lib/arm64/libksud.so" 2>/dev/null | head -1)
    [ -x "$KSUD" ] && break
  done
fi
if [ -z "$KSUD" ]; then KSUD=/data/local/tmp/ksud; fi
if [ ! -x "$KSUD" ]; then KSUD=/data/adb/ksu/bin/ksud; fi
[ -n "$PKG" ] || PKG=$(echo "$KSUD" | sed -n 's|.*/data/app/[^/]*/\\([^/]*\\)[^/]*/lib/.*|\\1|p' | sed 's|-[A-Za-z0-9_=~-]*$||')
[ -n "$PKG" ] || PKG=me.weishu.kernelsu
echo "[*] manager=$PKG kmi=$KMIV" >>"$LOG"
echo "[*] ksud=$KSUD" >>"$LOG"
echo "[*] uname=$(uname -r)" >>"$LOG"
if [ "$(id -u)" -ne 0 ]; then
  echo '[!] temp su unavailable; aborting' >>"$LOG"
  exit 1
fi
D="$HOME_DIR"
: >"$D/.gl_out" 2>/dev/null
chmod 666 "$D/.gl_out" 2>/dev/null
rm -f "$D/.gl_cmd" "$D/.gl_cmd.new" "$D/.gl_run" 2>/dev/null
echo "[*] debug terminal uid=$(id -u)" >>"$LOG"
# Commands run in the adb-like shell context (u:r:shell:s0) instead of the
# inherited untrusted_app domain: the transition goes through attr/exec, works
# while W1 leaves SELinux permissive and falls back otherwise.
(
  while :; do
    if [ -s "$D/.gl_cmd" ]; then
      mv "$D/.gl_cmd" "$D/.gl_run" 2>/dev/null && {
        echo "\\$ $(cat "$D/.gl_run" 2>/dev/null)" >>"$D/.gl_out" 2>/dev/null
        ( echo -n u:r:shell:s0 >/proc/self/attr/exec 2>/dev/null
          exec /system/bin/sh "$D/.gl_run" ) >>"$D/.gl_out" 2>&1
        echo "" >>"$D/.gl_out" 2>/dev/null
        rm -f "$D/.gl_run"
      }
    fi
    sleep 0.3
  done
) &
echo "# $(uname -r)" >"$HOME_DIR/.ghostlock_iomem.new"
if cat /proc/iomem >>"$HOME_DIR/.ghostlock_iomem.new" 2>/dev/null && grep -q 'System RAM' "$HOME_DIR/.ghostlock_iomem.new"; then
  mv "$HOME_DIR/.ghostlock_iomem.new" "$HOME_DIR/.ghostlock_iomem"
  chmod 644 "$HOME_DIR/.ghostlock_iomem" 2>/dev/null
  echo "[*] iomem cache: cached $(wc -c <"$HOME_DIR/.ghostlock_iomem") bytes" >>"$LOG"
else
  rm -f "$HOME_DIR/.ghostlock_iomem.new"
  echo '[!] iomem cache: /proc/iomem read failed' >>"$LOG"
fi
DEBUG_DIR="%s"
dump_debug() {
  [ -n "$DEBUG_DIR" ] || return 0
  mkdir -p "$DEBUG_DIR/pstore" 2>/dev/null || { echo "[!] debug dump: cannot write $DEBUG_DIR" >>"$LOG"; return 0; }
  {
    echo '== uname -a =='
    uname -a
    echo '== /proc/version =='
    cat /proc/version
    echo '== /proc/modules =='
    cat /proc/modules
    echo '== selinux =='
    getenforce 2>/dev/null
  } >"$DEBUG_DIR/kernel-info.txt" 2>&1
  if ! dmesg >"$DEBUG_DIR/kernel-dmesg.log" 2>&1; then
    echo '[!] dmesg unavailable' >>"$DEBUG_DIR/kernel-dmesg.log"
  fi
  cp /proc/iomem "$DEBUG_DIR/iomem.txt" 2>/dev/null
  cp "$LOG" "$DEBUG_DIR/ksu.log" 2>/dev/null
  echo "[*] debug dump written to $DEBUG_DIR" >>"$LOG"
}
echo 0 > /sys/fs/selinux/checkreqprot 2>/dev/null
echo 0 > /proc/sys/kernel/kptr_restrict 2>/dev/null
if grep -q '^kernelsu[[:space:]]' /proc/modules 2>/dev/null; then
  echo '[+] KernelSU already loaded' >>"$LOG"
fi
if [ "$SAFE_MODE" = "1" ]; then
  echo "[*] safe mode: disabling modules" >>"$LOG"
  n=0
  for m in /data/adb/modules/*/; do
    [ -d "$m" ] || continue
    if touch "${m}disable" 2>/dev/null; then
      n=$((n+1))
      echo "  disabled ${m}" >>"$LOG"
    fi
  done
fi
POLICY=$(mktemp "$HOME_DIR/.ghostlock_policy.XXXXXX") || {
  echo '[!] cannot create policy dump' >>"$LOG"
  exit 1
}
trap 'rm -f "$POLICY"; dump_debug' EXIT
prepare_policy() {
  cat /sys/fs/selinux/policy >"$POLICY" || return 1
  HEADER=$(od -An -tx1 -N24 "$POLICY" | tr -d ' \\n')
  case "$HEADER" in
    8cff7cf9080000005345204c696e7578????????????????) ;;
    *) echo '[!] invalid policy header'; return 1 ;;
  esac
  CONFIG=$(od -An -tu1 -j23 -N1 "$POLICY") || return 1
  [ -n "$CONFIG" ] || return 1
  CONFIG=$(printf '\\\\0%%03o' "$((CONFIG | 192))" || return 1)
  printf '%%b' "$CONFIG" | dd of="$POLICY" bs=1 seek=23 count=1 conv=notrunc
}
FIXUP_RC=1
for i in $(seq 1 10); do
  echo "[*] fixup: attempt $i" >>"$LOG"
  if ! prepare_policy >>"$LOG" 2>&1; then
    sleep 2
    continue
  fi
  BEFORE_POLICYLOAD=$(od -An -tu4 -j12 -N4 /sys/fs/selinux/status 2>/dev/null | tr -d ' ')
  load_policy "$POLICY" >>"$LOG" 2>&1 &
  LPID=$!
  (sleep 8; kill -9 $LPID 2>/dev/null) &
  SPID=$!
  wait $LPID 2>/dev/null
  FIXUP_RC=$?
  kill $SPID 2>/dev/null
  if [ "$FIXUP_RC" -eq 0 ]; then
    AFTER_POLICYLOAD=$(od -An -tu4 -j12 -N4 /sys/fs/selinux/status 2>/dev/null | tr -d ' ')
    echo "[*] policyload before=$BEFORE_POLICYLOAD after=$AFTER_POLICYLOAD" >>"$LOG"
    if [ -n "$AFTER_POLICYLOAD" ] && [ "$AFTER_POLICYLOAD" != "$BEFORE_POLICYLOAD" ]; then
      break
    fi
    echo '[!] load_policy returned success without updating SELinux status' >>"$LOG"
    FIXUP_RC=1
  fi
  sleep 2
done
echo "[*] policy fixup rc=$FIXUP_RC" >>"$LOG"
if [ "$FIXUP_RC" -eq 0 ]; then
# vrpatch first (neutralises vr.ko for every task), then KSU late-load via the
# manager's own ksud. KSU_ENFORCE=9 stops after vrpatch: temporary root only.
if [ ! -x "$KSUD" ]; then
  echo '[!] ksud missing' >>"$LOG"
  exit 1
fi
chmod 755 "$KSUD" 2>/dev/null
if [ -n "$VRPATCH_KO" ] && [ -f "$VRPATCH_KO" ]; then
  echo "[*] vrpatch: $VRPATCH_KO" >>"$LOG"
  "$KSUD" insmod "$VRPATCH_KO" >>"$LOG" 2>&1
  echo "[*] vrpatch exit=$?" >>"$LOG"
fi
if [ "$KSU_ENFORCE" = "9" ]; then
  echo '[*] temp root only: vrpatch loaded, KSU skipped' >>"$LOG"
else
  echo "[*] late-load pkg=$PKG kmi=$KMIV" >>"$LOG"
  "$KSUD" late-load --allow-shell --package-name "$PKG" --kmi "$KMIV" >>"$LOG" 2>&1
  echo "[*] late-load exit=$?" >>"$LOG"
  KSU_READY=0
  for i in $(seq 1 50); do
    if grep -q kernelsu /proc/modules 2>/dev/null; then KSU_READY=1; break; fi
    sleep 0.1
  done
  if [ "$KSU_READY" -ne 1 ]; then
    echo '[!] KernelSU module not loaded' >>"$LOG"
    exit 1
  fi
  echo '[+] KernelSU module loaded' >>"$LOG"
fi
else
  echo '[!] fixup failed; SELinux left permissive' >>"$LOG"
fi
if [ "$KSU_ENFORCE" = "0" ]; then
  echo '[*] KSU_ENFORCE=0: switching permissive' >>"$LOG"
  echo 0 > /sys/fs/selinux/enforce 2>/dev/null
fi
echo '[*] script done' >>"$LOG"
"""


def main() -> int:
    path = Path(sys.argv[1] if len(sys.argv) > 1
                else "native-prebuilt/ghostlock-vivo-neo9.so")
    blob = bytearray(path.read_bytes())
    start = blob.find(MAGIC)
    if start < 0:
        raise SystemExit("root script literal not found")
    end = blob.find(b"\x00", start)
    old = bytes(blob[start:end])

    new = SCRIPT.encode()
    residual = SCRIPT.replace("%s", "").replace("%d", "").replace("%%", "")
    if "%" in residual:
        raise SystemExit("unexpected '%' left in the script template")
    if len(new) > len(old):
        raise SystemExit(f"patched script too long: {len(new)} > {len(old)} bytes "
                         "(trim the template)")
    # 保留原有占位符顺序校验
    import re
    a = re.findall(rb"%[sd]", old)
    b = re.findall(rb"%[sd]", new)
    if a != b:
        raise SystemExit(f"placeholder sequence changed: {a} -> {b}")

    padded = new + b" " * (len(old) - len(new))

    with tempfile.NamedTemporaryFile("w", suffix=".sh", delete=False) as fh:
        fh.write(padded.decode())
        tmp = fh.name
    check = subprocess.run(["sh", "-n", tmp], capture_output=True)
    Path(tmp).unlink()
    if check.returncode != 0:
        raise SystemExit(f"patched script failed 'sh -n': {check.stderr.decode()}")

    backup = path.with_suffix(path.suffix + ".bak")
    if not backup.exists():
        shutil.copyfile(path, backup)
    blob[start:end] = padded
    path.write_bytes(blob)
    print(f"[patch] script {len(old)} -> {len(padded)} bytes (equal length, "
          f"{len(old) - len(new)} B padding)")
    print(f"[patch] wrote {path} (pristine backup: {backup.name})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
