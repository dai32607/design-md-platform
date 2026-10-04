# -*- coding: utf-8 -*-
"""组装 DesignMD 离线演示包，并分发到桌面文件夹 / 桌面 zip / 附加输出目录。

用法：
    python pack_demo.py --jar <jar或target目录> --runtime <精简JRE目录>
                        --template <包模板目录> --data-db <H2数据库文件>
                        --demo-dir <桌面包目录> --zip <桌面zip路径>
                        --work <构建工作目录> [--extra-output <目录> ...]

约定：工作目录内生成 build 包（DesignMD-Demo），zip 从最终包目录打包，确保内容一致。
"""
import argparse
import glob
import json
import os
import shutil
import sys
import time
import zipfile

SKIP_SUFFIX = (".lock.db", ".trace.db", ".temp")

# 打包脚本本身只作 tools\demo-package 下的同源模板，不进入演示包
TEMPLATE_EXCLUDE = ("打包演示包.bat",)


def log(msg):
    print(msg, flush=True)


def resolve_jar(path):
    if os.path.isfile(path):
        return path
    cands = [p for p in glob.glob(os.path.join(path, "*.jar")) if not p.endswith(".original")]
    if not cands:
        raise RuntimeError("未在 %s 下找到可运行的 jar" % path)
    return max(cands, key=os.path.getmtime)


def copy_tree_overwrite(src, dst, only_if_missing=False, exclude=()):
    n = 0
    for root, dirs, files in os.walk(src):
        rel = os.path.relpath(root, src)
        tgt = dst if rel == "." else os.path.join(dst, rel)
        os.makedirs(tgt, exist_ok=True)
        for f in files:
            if f in exclude:
                continue
            s = os.path.join(root, f)
            d = os.path.join(tgt, f)
            if only_if_missing and os.path.exists(d):
                continue
            shutil.copy2(s, d)
            n += 1
    return n


def runtime_matches(src, dst):
    """精简运行时只与 JDK 版本有关，release 一致且文件集合完整才视为可复用，
    避免目标目录残留历史残缺副本（release 相同但缺 conf/legal/lib 等）时被误判跳过。"""
    a, b = os.path.join(src, "release"), os.path.join(dst, "release")
    if not (os.path.isfile(a) and os.path.isfile(b)):
        return False
    with open(a, "rb") as f1, open(b, "rb") as f2:
        if f1.read() != f2.read():
            return False
    src_files = set()
    for root, dirs, files in os.walk(src):
        for f in files:
            src_files.add(os.path.relpath(os.path.join(root, f), src).replace("\\", "/"))
    dst_files = set()
    for root, dirs, files in os.walk(dst):
        for f in files:
            dst_files.add(os.path.relpath(os.path.join(root, f), dst).replace("\\", "/"))
    return src_files == dst_files


def sync_package(pkg, dst, runtime_src):
    """把 build 包内容覆盖到目标包目录；uploads 只补缺失文件，运行时不重复复制。"""
    os.makedirs(dst, exist_ok=True)
    files = copy_tree_overwrite(pkg, dst)
    up_files = copy_tree_overwrite(os.path.join(pkg, "uploads"),
                                   os.path.join(dst, "uploads"), only_if_missing=True)
    rt_dst = os.path.join(dst, "runtime")
    if runtime_matches(runtime_src, rt_dst):
        runtime_state = "reused"
    else:
        copy_tree_overwrite(runtime_src, rt_dst)
        runtime_state = "copied"
    return files + up_files, runtime_state


def make_zip(src_dir, zip_path):
    if os.path.exists(zip_path):
        os.remove(zip_path)
    count = 0
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for root, dirs, files in os.walk(src_dir):
            for f in files:
                if f.endswith(SKIP_SUFFIX):
                    continue
                full = os.path.join(root, f)
                rel = os.path.relpath(full, src_dir).replace("\\", "/")
                z.write(full, "DesignMD-Demo/" + rel)
                count += 1
    return count


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--project", required=True)
    ap.add_argument("--work", required=True)
    ap.add_argument("--template", required=True)
    ap.add_argument("--jar", required=True)
    ap.add_argument("--runtime", required=True)
    ap.add_argument("--data-db", required=True)
    ap.add_argument("--demo-dir", required=True)
    ap.add_argument("--zip", required=True)
    ap.add_argument("--extra-output", action="append", default=[])
    args = ap.parse_args()

    started = time.time()
    jar = resolve_jar(args.jar)
    if not os.path.isfile(jar):
        raise RuntimeError("jar 不存在：%s" % jar)
    if not os.path.isfile(args.data_db) or os.path.getsize(args.data_db) == 0:
        raise RuntimeError("H2 数据文件无效：%s" % args.data_db)
    if not os.path.isdir(args.runtime):
        raise RuntimeError("精简运行时不完整：%s" % args.runtime)

    work = os.path.abspath(args.work)
    pkg = os.path.join(work, "DesignMD-Demo")
    if os.path.isdir(pkg):
        shutil.rmtree(pkg)  # 每次重新组装，避免上次残留在包内的文件混入
    os.makedirs(pkg, exist_ok=True)

    # 1) 模板（启动/停止脚本、README、uploads 说明；打包脚本仅作同源模板，不进入演示包）
    copy_tree_overwrite(args.template, pkg, exclude=TEMPLATE_EXCLUDE)
    # 2) 程序包
    os.makedirs(os.path.join(pkg, "app"), exist_ok=True)
    shutil.copy2(jar, os.path.join(pkg, "app", "design-md-platform.jar"))
    # 3) 演示数据库 + 恢复用备份
    os.makedirs(os.path.join(pkg, "data"), exist_ok=True)
    shutil.copy2(args.data_db, os.path.join(pkg, "data", "design_db.mv.db"))
    shutil.copy2(args.data_db, os.path.join(pkg, "data", "design_db.mv.db.bak"))

    log("[pkg] 组装完成：%s" % pkg)

    # 4) 分发到桌面包目录
    demo_dir = os.path.abspath(args.demo_dir)
    n, rt = sync_package(pkg, demo_dir, args.runtime)
    log("[demo] 已更新 %s（写入 %d 个文件，运行时 %s）" % (demo_dir, n, rt))

    # 5) 从最终包目录打 zip
    zip_path = os.path.abspath(args.zip)
    zc = make_zip(demo_dir, zip_path)
    log("[zip] 已生成 %s（%d 个文件，%d 字节）" % (zip_path, zc, os.path.getsize(zip_path)))

    # 6) 附加输出目录（会话 output / 项目 output）
    extras = []
    for out in args.extra_output:
        out = os.path.abspath(out)
        if not out or out == demo_dir:
            continue
        en, ert = sync_package(pkg, out, args.runtime)
        ezip = os.path.join(out, "DesignMD-Demo.zip")
        shutil.copy2(zip_path, ezip)
        extras.append({"dir": out, "files": en, "runtime": ert, "zip": ezip})
        log("[copy] 已更新输出副本 %s" % out)

    def stamp(p):
        return time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(os.path.getmtime(p)))

    result = {
        "jar": jar,
        "jar_size": os.path.getsize(jar),
        "data_db": args.data_db,
        "data_db_size": os.path.getsize(args.data_db),
        "demo_dir": demo_dir,
        "zip": zip_path,
        "zip_size": os.path.getsize(zip_path),
        "files_in_zip": zc,
        "runtime": rt,
        "extras": extras,
        "mtime": {"jar": stamp(jar), "demo_jar": stamp(os.path.join(demo_dir, "app", "design-md-platform.jar")),
                  "demo_db": stamp(os.path.join(demo_dir, "data", "design_db.mv.db")),
                  "zip": stamp(zip_path)},
        "seconds": round(time.time() - started, 1),
    }
    print("PACK_RESULT " + json.dumps(result, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as e:
        print("PACK_ERROR " + str(e))
        sys.exit(1)
