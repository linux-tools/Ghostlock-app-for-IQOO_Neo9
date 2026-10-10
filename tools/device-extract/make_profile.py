#!/usr/bin/env python3
"""GhostLock profile 生成器：从 rooted 设备的只读提取产物生成/优化 kernel profile。

复刻 tools/extract_rs（ghostlock-extract）的推导规则，但数据源改为设备侧
/proc/kallsyms + /proc/iomem + /sys/kernel/btf/vmlinux（ghostlock-dump.sh 产出），
不再依赖 OTA/镜像文件。用法：

    python3 make_profile.py --dump <dump目录> \
        [--base <现有 .conf>] [--boot <boot.img>] \
        [-o <输出 .conf>] [--tune]

- offset.*          kallsyms 符号地址 - _text（镜像域偏移，与 KASLR slide 无关）
- kernel_phys_load  Kernel code 起点 - (stext - text)（slide 在差值中抵消）
- kernel_phys_offset最低 System RAM 物理起点（DRAM 基址，决定 image→直映射换算）
- task_struct/cred  BTF 结构成员偏移（缺 BTF 时回退 base conf）
- cred 模板         base conf 为准；--boot 提供镜像时用 init_cred 字节复核 refs/caps
- --tune            按"命中率优先"适度放大 W1/W2/W3 与 heap 重试预算

同型号 12GB / 16GB 各跑一次 dump、各生成一份 profile，diff 报告会显示两档差异；
若两档唯一差异只是 RAM 容量，通常 kernel_phys_load/kernel_phys_offset 相同，
一份 profile 即可覆盖两档（差异时用 app 高级覆盖页处理少数机型）。
"""
from __future__ import annotations

import argparse
import gzip
import os
import re
import struct
import sys

STEXT = "_stext"
TEXT = "_text"

# 与 tools/extract_rs/src/symbols.rs 的 SYMBOLS 表一致（key = profile 字段）
SYMBOLS = {
    "init_task": "init_task",
    "init_cred": "init_cred",
    "root_task_group": "root_task_group",
    "selinux_enforcing": "selinux_state",
    "selinux_blob_sizes": "selinux_blob_sizes",
    "security_hook_heads": "security_hook_heads",  # 可选符号
    "slide_nfulnl_logger": "nfulnl_logger",
    "slide_boot_id": "sysctl_bootid",
}
OPTIONAL_SYMBOLS = {"security_hook_heads"}
SLIDE_LOGGERS_SYM = "loggers"          # off_slide_loggers_0_1 = addr(loggers) + 0x10 - _text
SLIDE_LOGGERS_DELTA = 0x10
EMPTY_ZERO_PAGE_SYM = "empty_zero_page"

TASK_FIELDS = [
    "prio", "normal_prio", "sched_task_group", "pi_lock", "pi_waiters",
    "pi_top_task", "pi_blocked_on", "pid", "tgid", "atomic_flags",
    "real_cred", "cred", "comm", "tasks", "seccomp",
]
CRED_PTR_MEMBERS = ("security", "user", "user_ns", "group_info",
                    "session_keyring", "process_keyring", "thread_keyring",
                    "request_key_auth")

# BTF kinds（include/uapi/linux/btf.h）
KIND_INT, KIND_PTR, KIND_ARRAY = 1, 2, 3
KIND_STRUCT, KIND_UNION, KIND_ENUM, KIND_FWD = 4, 5, 6, 7
KIND_TYPEDEF, KIND_VOLATILE, KIND_CONST, KIND_RESTRICT = 8, 9, 10, 11
KIND_FUNC, KIND_FUNC_PROTO, KIND_VAR, KIND_DATASEC = 12, 13, 14, 15
KIND_DECL_TAG, KIND_TYPE_TAG, KIND_ENUM64 = 17, 18, 19
MODIFIERS = {KIND_TYPEDEF, KIND_VOLATILE, KIND_CONST, KIND_RESTRICT}


def warn(msg: str) -> None:
    print(f"[make_profile] WARN: {msg}", file=sys.stderr)


def info(msg: str) -> None:
    print(f"[make_profile] {msg}")


# ---------------------------------------------------------------- kallsyms

def parse_kallsyms(path: str) -> dict[str, set[int]]:
    syms: dict[str, set[int]] = {}
    with open(path, "r", errors="replace") as fh:
        for line in fh:
            parts = line.split()
            if len(parts) < 3:
                continue
            try:
                addr = int(parts[0], 16)
            except ValueError:
                continue
            if addr:
                syms.setdefault(parts[2], set()).add(addr)
    return syms


def unique(syms: dict[str, set[int]], name: str) -> int | None:
    addrs = syms.get(name)
    if not addrs:
        return None
    if len(addrs) > 1:
        warn(f"symbol {name} is ambiguous ({len(addrs)} sites); skipped")
        return None
    return next(iter(addrs))


# ---------------------------------------------------------------- iomem

def parse_iomem(path: str) -> tuple[int | None, int | None]:
    """返回 (kernel_code_start, phys_offset)。地址为 0（未放行）时按 None 处理。"""
    kcode = None
    ram_min = None
    pat = re.compile(r"^([0-9a-f]+)-([0-9a-f]+) : (.*)$")
    with open(path, "r", errors="replace") as fh:
        for line in fh:
            m = pat.match(line.strip())
            if not m:
                continue
            start, name = int(m.group(1), 16), m.group(3).strip()
            if kcode is None and name == "Kernel code" and start:
                kcode = start
            if name == "System RAM" and start:
                ram_min = start if ram_min is None else min(ram_min, start)
    return kcode, ram_min


# ---------------------------------------------------------------- BTF（最小实现）

class Btf:
    """只解析需要的东西：命名 struct/union 的 size 与成员位偏移、modifier 指向。"""

    def __init__(self, blob: bytes) -> None:
        magic, _ver, _flags, hdr_len = struct.unpack_from("<HBBI", blob, 0)
        if magic != 0xEB9F:
            raise ValueError("bad BTF magic")
        type_off, type_len, str_off, _str_len = struct.unpack_from("<IIII", blob, 8)
        self.blob = blob
        self.types_base = hdr_len + type_off
        self.types_end = self.types_base + type_len
        self.strs = blob[hdr_len + str_off:]
        self.types: list[dict] = []
        self.by_name: dict[str, int] = {}
        off = self.types_base
        while off + 8 <= self.types_end:
            name_off, info = struct.unpack_from("<II", blob, off)
            kind = (info >> 24) & 0x1F
            vlen = info & 0xFFFF
            pos, size, members = off + 8, 0, []
            if kind in (KIND_STRUCT, KIND_UNION):
                (size,) = struct.unpack_from("<I", blob, pos)
                pos += 4
                for _ in range(vlen):
                    m_name, m_type, m_bit = struct.unpack_from("<III", blob, pos)
                    pos += 12
                    members.append((self._str(m_name), m_type, m_bit & 0xFFFFFF))
            elif kind in (KIND_INT, KIND_PTR, KIND_FUNC, KIND_DECL_TAG):
                pos += 4
            elif kind == KIND_ARRAY:
                pos += 12
            elif kind == KIND_ENUM:
                pos += 8 * vlen
            elif kind == KIND_FUNC_PROTO:
                pos += 8 * vlen
            elif kind == KIND_VAR:
                pos += 4
            elif kind == KIND_DATASEC:
                pos += 12 * vlen
            elif kind == KIND_ENUM64:
                pos += 12 * vlen
            rec = {
                "name": self._str(name_off), "kind": kind, "size": size,
                "members": members, "target": -1,
            }
            if kind in MODIFIERS or kind in (KIND_PTR, KIND_FUNC):
                (rec["target"],) = struct.unpack_from("<I", blob, off + 8)
            self.types.append(rec)
            if kind == KIND_STRUCT and rec["name"] and rec["name"] not in self.by_name:
                self.by_name[rec["name"]] = len(self.types) - 1
            off = pos

    def _str(self, off: int) -> str:
        end = self.strs.find(b"\0", off)
        return self.strs[off:end].decode("utf-8", "replace") if end >= 0 else ""

    def struct(self, name: str) -> dict | None:
        idx = self.by_name.get(name)
        return None if idx is None else self.types[idx]

    def size(self, name: str) -> int | None:
        s = self.struct(name)
        return None if s is None else s["size"]

    def field(self, struct_name: str, field: str) -> int | None:
        s = self.struct(struct_name)
        if s is None:
            return None
        for m_name, _m_type, m_bit in s["members"]:
            if m_name == field:
                if m_bit % 8:
                    warn(f"{struct_name}.{field} is a bitfield; skipped")
                    return None
                return m_bit // 8
        return None

    def _resolve_kind(self, type_id: int) -> int:
        for _ in range(32):
            if not 0 < type_id <= len(self.types):
                return -1
            rec = self.types[type_id - 1]
            if rec["kind"] in MODIFIERS:
                type_id = rec["target"]
                continue
            return rec["kind"]
        return -1

    def ptr_member_offsets(self, struct_name: str, only: tuple[str, ...]) -> list[int]:
        s = self.struct(struct_name)
        if s is None:
            return []
        out = []
        for m_name, m_type, m_bit in s["members"]:
            if m_bit % 8 or m_name not in only:
                continue
            if self._resolve_kind(m_type) == KIND_PTR:
                out.append((m_bit // 8, m_name))
        return sorted(out)


def load_btf(path: str) -> Btf | None:
    try:
        with open(path, "rb") as fh:
            return Btf(fh.read())
    except (OSError, ValueError) as exc:
        warn(f"BTF unavailable ({exc}); struct offsets fall back to base conf")
        return None


# ---------------------------------------------------------------- base conf

def parse_conf(path: str) -> dict[str, object]:
    """极简 HOCON 子集：key = value / 花括号嵌套，值取原始 token。"""
    root: dict[str, object] = {}
    stack = [root]
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        for raw in fh:
            line = raw.split("#", 1)[0].split("//", 1)[0].strip()
            if not line:
                continue
            if line == "}":
                if len(stack) > 1:
                    stack.pop()
                continue
            m = re.match(r"^([A-Za-z0-9_.]+)\s*=\s*(.+?),?$", line)
            if m:
                key, val = m.group(1), m.group(2).strip()
                if val.startswith('"') and val.endswith('"'):
                    parsed: object = val[1:-1]
                elif val == "null":
                    parsed = None
                elif val in ("{}", "{ }"):
                    parsed = {}
                else:
                    try:
                        parsed = int(val)
                    except ValueError:
                        parsed = val
                cur = stack[-1]
                name = key.split(".")[-1]
                if isinstance(cur, dict):
                    cur[name] = parsed
                continue
            m = re.match(r"^([A-Za-z0-9_]+)\s*\{\s*$", line)
            if m and isinstance(stack[-1], dict):
                child: dict[str, object] = {}
                stack[-1][m.group(1)] = child
                stack.append(child)
                continue
    return root


def conf_get(conf: dict, dotted: str):
    cur: object = conf
    for part in dotted.split("."):
        if not isinstance(cur, dict) or part not in cur:
            return None
        cur = cur[part]
    return cur


def i64(u: int) -> int:
    return u - (1 << 64) if u >= (1 << 63) else u


# ---------------------------------------------------------------- boot.img

ARM64_MAGIC = b"ARM\x64"


def extract_image(path: str) -> bytes | None:
    """从 boot.img 中解出 arm64 Image（gzip 包裹或裸 Image）。"""
    with open(path, "rb") as fh:
        data = fh.read()
    pos = 0
    while True:
        pos = data.find(b"\x1f\x8b\x08", pos)
        if pos < 0:
            break
        try:
            image = gzip.decompress(data[pos:])
            if image[0x38:0x3C] == ARM64_MAGIC:
                return image
        except (OSError, EOFError):
            pass
        pos += 1
    if ARM64_MAGIC in data[:0x10000] and data[0x38:0x3C] == ARM64_MAGIC:
        return data  # 裸 Image 直接当 boot 传入
    return None


def derive_cred_from_image(image: bytes, init_cred_off: int, cred_size: int,
                           btf: Btf | None) -> tuple[dict, bool]:
    """从镜像 init_cred 字节推导 caps 运行与 ref 指针（复刻 derive_cred_5x）。"""
    start = init_cred_off
    end = start + cred_size
    if start <= 0 or end > len(image):
        warn("init_cred outside image; keep base cred template")
        return {}, False
    blob = image[start:end]

    def read(off: int) -> int:
        return struct.unpack_from("<Q", blob, off)[0]

    # caps：cap_inheritable .. cap_ambient+8 内首个非零等值 run
    caps_off = caps_cnt = caps_val = None
    if btf is not None:
        cs = btf.field("cred", "cap_inheritable")
        ce = btf.field("cred", "cap_ambient")
        if cs is not None:
            ce = (ce + 8) if ce is not None else (cs + 5 * 8)
            ce = min(ce, cred_size)
            cur = cs
            while cur + 8 <= ce:
                slot = read(cur)
                if caps_off is None:
                    if slot:
                        caps_off, caps_val, caps_cnt = cur, slot, 1
                elif slot == caps_val:
                    caps_cnt += 1
                else:
                    break
                cur += 8
    out: dict = {}
    if caps_off is not None:
        out["caps_offset"] = caps_off
        out["caps_count"] = caps_cnt
        out["caps_value"] = caps_val

    # refs：指针成员中非零 kernel VA 的前 4 个（按偏移序）
    slots = []
    if btf is not None:
        ptr_offs = btf.ptr_member_offsets("cred", CRED_PTR_MEMBERS)
    else:
        ptr_offs = [(off, name) for off, name in
                    [(96, "session_keyring"), (104, "process_keyring"),
                     (112, "thread_keyring"), (120, "request_key_auth"),
                     (128, "security"), (136, "user"), (144, "user_ns"),
                     (152, "group_info")]]
    for off, _name in ptr_offs:
        if off + 8 > cred_size:
            continue
        val = read(off)
        if val and (val >> 48) == 0xFFFF:
            slots.append((off, val))
    for i, (off, val) in enumerate(slots[:4]):
        out[f"ref{i}_offset"] = off
        out[f"ref{i}_image"] = i64(val)
    if not slots:
        warn("no reference pointers found in init_cred image bytes")
        return out, bool(caps_off)
    out["copy_size"] = cred_size
    out["usage_offset"] = 0  # 5.x: atomic_long_t usage @0
    out["usage_value"] = 256
    return out, True


# ---------------------------------------------------------------- 生成

TUNE = {
    "execution.stages.w1_attempts": (15, 24),
    "execution.stages.w2_attempts": (15, 24),
    "execution.stages.w3_attempts": (6, 10),
    "execution.stages.w3_chain_rounds": (3, 4),
    "execution.heap.prepare_max_attempts": (4, 6),
    "execution.heap.kernelsnitch_timeout_ms": (60000, 90000),
}


def next_pow2(v: int) -> int:
    p = 1
    while p < v:
        p <<= 1
    return p


def set_dotted(conf: dict, dotted: str, value) -> None:
    parts = dotted.split(".")
    cur = conf
    for part in parts[:-1]:
        cur = cur.setdefault(part, {})
    cur[parts[-1]] = value


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--dump", required=True, help="ghostlock-dump.sh 产物目录")
    ap.add_argument("--base", help="现有 .conf（route/kernelsnitch/cred/execution 的回退来源）")
    ap.add_argument("--boot", help="boot.img（可选，复核 init_cred 字节）")
    ap.add_argument("-o", "--out", help="输出 .conf（缺省打印到 stdout）")
    ap.add_argument("--tune", action="store_true", help="放大重试预算（命中率优先）")
    args = ap.parse_args()

    dump = args.dump
    release = ""
    try:
        with open(f"{dump}/uname.txt", errors="replace") as fh:
            parts = fh.read().strip().split()
            if len(parts) >= 3:
                release = parts[2]
    except OSError:
        pass
    if not release:
        with open(f"{dump}/version.txt", errors="replace") as fh:
            m = re.search(r"Linux version (\S+)", fh.read())
            release = m.group(1) if m else ""
    if not release:
        warn("cannot determine uname -r")
        return 2

    syms = parse_kallsyms(f"{dump}/kallsyms.txt")
    text = unique(syms, TEXT)
    stext = unique(syms, STEXT)
    if text is None or stext is None:
        warn("kallsyms addresses are zeroed or _text/_stext missing; "
             "re-run ghostlock-dump.sh with working kptr relaxation")
        return 2
    base_addr = text
    slide_note = stext - text
    info(f"_text @ {text:#x}, (stext-text) = {slide_note:#x}")

    offsets: dict[str, int | None] = {}
    for key, sym in SYMBOLS.items():
        addr = unique(syms, sym)
        if addr is None:
            if key in OPTIONAL_SYMBOLS:
                offsets[key] = None
            else:
                warn(f"symbol {sym} missing -> offset.{key} = null")
                offsets[key] = None
        else:
            offsets[key] = addr - base_addr
    loggers = unique(syms, SLIDE_LOGGERS_SYM)
    offsets["slide_loggers_0_1"] = (loggers + SLIDE_LOGGERS_DELTA - base_addr) if loggers else None
    ezp = unique(syms, EMPTY_ZERO_PAGE_SYM)
    if ezp is None:
        warn("symbol empty_zero_page missing -> offset.empty_zero_page = null")
    offsets["empty_zero_page"] = ezp - base_addr if ezp else None

    # phys
    kcode, phys_off = parse_iomem(f"{dump}/iomem.txt")
    phys_load = None
    if kcode is not None:
        phys_load = kcode - slide_note
        info(f"kernel_phys_load = {phys_load:#x} (Kernel code {kcode:#x} - slide {slide_note:#x})")
    else:
        warn("/proc/iomem 'Kernel code' unreadable -> kernel_phys_load = null（沿用 SoC 公式）")
    if phys_off is None:
        warn("/proc/iomem 'System RAM' unreadable -> kernel_phys_offset = null（沿用编译默认 0x80000000）")

    # BTF → task_struct / cred / kernelsnitch
    btf = None
    btf_path = f"{dump}/vmlinux.btf"
    if os.path.getsize(btf_path) > 0:
        btf = load_btf(btf_path)
    task_struct: dict[str, int] = {}
    if btf is not None:
        for f in TASK_FIELDS:
            v = btf.field("task_struct", f)
            if v is None:
                warn(f"task_struct.{f} missing from BTF")
            else:
                task_struct[f] = v
        info(f"task_struct offsets from BTF: {task_struct}")

    # base conf
    base = parse_conf(args.base) if args.base else {}
    base_task = conf_get(base, "task_struct") or {}
    cred = dict(conf_get(base, "cred") or {}) if base else {}
    if not cred:
        cred = {
            "copy_size": 176, "usage_offset": 0, "usage_value": 256,
            "caps_offset": 48, "caps_count": 3, "caps_value": 0x1FFFFFFFFFF,
            "ref_count": 4,
            "ref0_offset": 128, "ref1_offset": 136, "ref2_offset": 144, "ref3_offset": 152,
            "ref0_image": None, "ref1_image": None, "ref2_image": None, "ref3_image": None,
        }
        warn("no base conf; using 5.15.41-verified cred template skeleton")

    if btf is not None:
        cred_size = btf.size("cred")
        if cred_size and cred_size != conf_get(base, "cred.copy_size"):
            info(f"cred size BTF={cred_size}, base={conf_get(base, 'cred.copy_size')}")
    if conf_get(cred, "usage_offset") is None:
        cred["usage_offset"] = 0

    # boot.img 复核 cred
    if args.boot:
        image = extract_image(args.boot)
        if image:
            init_cred_off = offsets.get("init_cred")
            if init_cred_off:
                size = (btf.size("cred") if btf else None) or 176
                derived, _ok = derive_cred_from_image(image, init_cred_off, size, btf)
                if derived:
                    info(f"cred template from init_cred image bytes: "
                         f"{ {k: (hex(v) if isinstance(v, int) and v > 512 else v) for k, v in derived.items()} }")
                    cred.update(derived)
            else:
                warn("init_cred offset unknown; boot.img not used")
        else:
            warn("no arm64 Image found in boot.img")

    nref = sum(1 for i in range(4) if cred.get(f"ref{i}_image") is not None)
    cred["ref_count"] = min(4, max(nref, 1))

    # kernelsnitch
    mm_sz = 1024
    if btf is not None:
        s = btf.size("mm_struct")
        if s:
            mm_sz = min(1024, next_pow2(s)) if s <= 1024 else next_pow2(s)
    kernelsnitch = dict(conf_get(base, "kernelsnitch") or {"collisions": 8})
    kernelsnitch.setdefault("collisions", 8)
    if conf_get(base, "kernelsnitch.mm_struct_sz") in (None, 0):
        kernelsnitch["mm_struct_sz"] = mm_sz
    else:
        kernelsnitch["mm_struct_sz"] = conf_get(base, "kernelsnitch.mm_struct_sz")

    route = conf_get(base, "route") or {
        "multicast_waiter": {
            "compact_waiter": 1, "waiter_off": 80, "buffer_size": 264,
            "task_offset": 48, "lock_offset": 56,
        }
    }
    fallback = conf_get(base, "fallback") or {"to": "none"}
    execution = conf_get(base, "execution")
    if args.tune:
        execution = dict(execution or {})
        for dotted, (lo, hi) in TUNE.items():
            cur = conf_get(base, dotted) if base else None
            if cur is None:
                cur = lo
            rel = dotted.split(".", 1)[1]  # 相对 execution 块
            set_dotted(execution, rel, max(int(cur), hi))
    elif not execution:
        execution = None  # 交给 resolver 的预设

    # ---- 组装 HOCON ----
    def fmt(v):
        if v is None:
            return "null"
        if isinstance(v, bool):
            return "1" if v else "0"
        if isinstance(v, str):
            return f'"{v}"'
        if isinstance(v, int) and v >= (1 << 63):
            return str(i64(v))
        return str(v)

    lines: list[str] = []
    lines.append("# GhostLock kernel profile — generated by tools/device-extract/make_profile.py")
    lines.append("# from a rooted-device read-only dump (kallsyms + /proc/iomem + BTF).")
    lines.append(f'release = "{release}"')
    lines.append("schema_version = 1")
    lines.append("kernel_major = 5")
    lines.append("")
    lines.append("route {")
    rname, rbody = next(iter(route.items()))
    lines.append(f"  {rname} {{")
    for k, v in rbody.items():
        lines.append(f"    {k} = {fmt(v)}")
    lines.append("  }")
    lines.append("}")
    lines.append("fallback {")
    lines.append(f'  to = "{fallback.get("to", "none")}"')
    lines.append("}")
    lines.append("")
    lines.append("kernelsnitch {")
    for k, v in kernelsnitch.items():
        lines.append(f"  {k} = {fmt(v)}")
    lines.append("}")
    lines.append("")
    lines.append("task_struct {")
    merged_task = dict(base_task)
    merged_task.update(task_struct)
    for k in TASK_FIELDS:
        if k in merged_task:
            lines.append(f"  {k} = {fmt(merged_task[k])}")
        else:
            warn(f"task_struct.{k} unavailable -> null (import will flag invalid)")
            lines.append(f"  {k} = null")
    lines.append("}")
    lines.append("")
    lines.append("cred {")
    for k in ["copy_size", "usage_offset", "usage_value", "caps_offset", "caps_count",
              "caps_value", "ref_count",
              "ref0_offset", "ref0_image", "ref1_offset", "ref1_image",
              "ref2_offset", "ref2_image", "ref3_offset", "ref3_image"]:
        if k in cred:
            lines.append(f"  {k} = {fmt(cred[k])}")
    lines.append("}")
    lines.append("")
    lines.append("offset {")
    order = ["init_task", "init_cred", "empty_zero_page", "root_task_group",
             "selinux_enforcing", "selinux_blob_sizes", "security_hook_heads",
             "slide_nfulnl_logger", "slide_boot_id", "slide_loggers_0_1"]
    for k in order:
        if k in offsets:
            lines.append(f"  {k} = {fmt(offsets[k])}")
    lines.append("}")
    lines.append("")
    lines.append(f"kernel_phys_load = {fmt(phys_load)}")
    lines.append(f"kernel_phys_offset = {fmt(phys_off)}")
    lines.append(f"recommend_shizuku = {fmt(conf_get(base, 'recommend_shizuku') or 0)}")
    if execution:
        lines.append("")
        lines.append("execution {")

        def emit(node: dict, depth: int) -> None:
            pad = "  " * depth
            for k, v in node.items():
                if isinstance(v, dict):
                    if not v:
                        continue
                    lines.append(f"{pad}{k} {{")
                    emit(v, depth + 1)
                    lines.append(f"{pad}}}")
                else:
                    lines.append(f"{pad}{k} = {fmt(v)}")

        emit(execution, 1)
        lines.append("}")
    lines.append("")

    text_out = "\n".join(lines)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as fh:
            fh.write(text_out)
        info(f"profile written: {args.out}")
    else:
        print(text_out)

    # ---- 差分报告 ----
    if base:
        print("\n=== 与 base conf 的差异 ===")
        pairs = []
        for k, v in offsets.items():
            pairs.append((f"offset.{k}", conf_get(base, f"offset.{k}"), v))
        for f in TASK_FIELDS:
            pairs.append((f"task_struct.{f}", conf_get(base, f"task_struct.{f}"),
                          merged_task.get(f)))
        pairs.append(("kernel_phys_load", conf_get(base, "kernel_phys_load"), phys_load))
        pairs.append(("kernel_phys_offset", conf_get(base, "kernel_phys_offset"), phys_off))
        for key, old, new in pairs:
            if old != new:
                print(f"  {key}: {old} -> {new}")
        changed = sum(1 for _k, o, n in pairs if o != n)
        if changed == 0:
            print("  （无差异：当前 profile 的偏移量与设备实测一致）")
        else:
            print(f"  共 {changed} 项差异。同型号设备命中率低时，请用该设备自己的 dump 重跑本工具，"
                  "两份 profile 的差异处即为该机型偏移漂移的来源。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
