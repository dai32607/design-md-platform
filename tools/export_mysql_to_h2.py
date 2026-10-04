# -*- coding: utf-8 -*-
"""从 MySQL design_db 导出全量数据，生成 H2 兼容初始化脚本（schema.sql / data.sql）。

用法：
    python export_mysql_to_h2.py --out <输出目录> [--mysql <mysql.exe>]
                                 [--user root] [--password 123456] [--db design_db]

说明：所有字符串列使用 HEX() 中转 + Python 解码为 UTF-8，彻底规避中文乱码与反斜杠转义问题；
      二进制列（brand.logo_data 等）使用 BIN:<hex> 中转，导出为 X'...' 字面量，保证图片二进制无损。
"""
import argparse
import json
import os
import subprocess
import sys

NL = '~NULL~'


def fetch(mysql, user, password, db, sql):
    """以 UTF-8 解码方式执行查询（禁止用 PowerShell 直显中文，避免乱码）"""
    p = subprocess.run([mysql, "-u" + user, "-p" + password, "--default-character-set=utf8mb4",
                        "-N", "-B", "-e", sql, db],
                       capture_output=True, encoding="utf-8", errors="replace")
    if p.returncode != 0:
        raise RuntimeError((p.stderr or "").strip())
    return p.stdout


def lit(raw):
    """把 HEX/BIN/时间等原始字段值转成标准 SQL 字面量"""
    if raw == NL or raw == '':
        return 'NULL'
    if raw.startswith('BIN:'):
        # 二进制列：BIN:<hex> -> H2/MySQL 通用二进制字面量 X'...'（空内容视为未设置）
        h = raw[4:]
        return "X'%s'" % h if h else 'NULL'
    if raw.startswith('HEX:'):
        s = bytes.fromhex(raw[4:]).decode('utf-8')
    else:
        s = raw
    return "'" + s.replace("'", "''") + "'"


def hexcol(c):
    return "IFNULL(CONCAT('HEX:', HEX(%s)), '%s')" % (c, NL)


def bincol(c):
    """二进制列（BLOB）：以 BIN:<hex> 中转，避免 utf-8 解码失败；NULL 仍用 ~NULL~ 标记"""
    return "IFNULL(CONCAT('BIN:', HEX(%s)), '%s')" % (c, NL)


def plaincol(c):
    return "IFNULL(%s, '%s')" % (c, NL)


def timecol(c):
    return "IFNULL(DATE_FORMAT(%s, '%%Y-%%m-%%d %%H:%%i:%%s'), '%s')" % (c, NL)


TABLES = [
    # name, [(col, kind)], order by
    ("brand", [("id", "plain"), ("name", "hex"), ("logo_url", "hex"),
               ("logo_data", "bin"), ("logo_mime", "hex"), ("logo_update_time", "time"),
               ("source", "hex"),
               ("website", "hex"), ("description", "hex"), ("creator_id", "plain"),
               ("create_time", "time")], "id"),
    ("design_token", [("id", "plain"), ("brand_id", "plain"), ("category", "hex"),
                      ("token_name", "hex"), ("token_value", "hex"), ("description", "hex"),
                      ("create_time", "time")], "id"),
    ("design_file", [("id", "plain"), ("brand_id", "plain"), ("title", "hex"),
                     ("content", "hex"), ("source_url", "hex"), ("create_time", "time")], "id"),
    ("user", [("id", "plain"), ("username", "hex"), ("password", "hex"), ("nickname", "hex"),
              ("role", "hex"), ("create_time", "time")], "id"),
]

DDL = """-- H2 演示库建表脚本（由 MySQL design_db 结构适配生成）
CREATE TABLE IF NOT EXISTS brand (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    logo_url VARCHAR(500),
    logo_data BLOB,
    logo_mime VARCHAR(100),
    logo_update_time TIMESTAMP,
    source VARCHAR(20) NOT NULL DEFAULT 'custom',
    website VARCHAR(500),
    description CLOB,
    creator_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS design_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_id BIGINT NOT NULL,
    category VARCHAR(50) NOT NULL,
    token_name VARCHAR(200) NOT NULL,
    token_value VARCHAR(500) NOT NULL,
    description VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_token_brand_id ON design_token (brand_id);
CREATE INDEX IF NOT EXISTS idx_token_brand_category ON design_token (brand_id, category);

CREATE TABLE IF NOT EXISTS design_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content CLOB,
    source_url VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_file_brand_id ON design_file (brand_id);

CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(100) NOT NULL,
    nickname VARCHAR(50),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_username UNIQUE (username)
);

-- 兼容 MySQL 的 DATE_FORMAT 函数（供统计接口使用）
CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR 'com.designmd.platform.demo.H2CompatFunctions.dateFormat';
"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True, help="H2 初始化脚本输出目录")
    ap.add_argument("--mysql", default=r"%MYSQL_HOME%\bin\mysql.exe")
    ap.add_argument("--user", default="root")
    ap.add_argument("--password", default="123456")
    ap.add_argument("--db", default="design_db")
    args = ap.parse_args()

    out = os.path.abspath(args.out)
    os.makedirs(out, exist_ok=True)

    data_lines = ["-- H2 演示数据（导出自 MySQL %s 全量数据）" % args.db]
    stats = {}
    for tname, cols, order in TABLES:
        sel = ", ".join(
            (hexcol(c) if k == "hex"
             else (bincol(c) if k == "bin" else (timecol(c) if k == "time" else plaincol(c))))
            for c, k in cols)
        sql = "SELECT %s FROM %s ORDER BY %s" % (sel, tname, order)
        raw = fetch(args.mysql, args.user, args.password, args.db, sql)
        rows = [ln.split("\t") for ln in raw.split("\n") if ln != ""]
        colnames = ", ".join(c for c, _ in cols)
        data_lines.append("\n-- %s: %d 行" % (tname, len(rows)))
        if not rows:
            raise RuntimeError("表 %s 无数据，导出中止" % tname)
        for r in rows:
            vals = ", ".join(lit(v) for v in r)
            data_lines.append('INSERT INTO %s (%s) VALUES (%s);' % (tname, colnames, vals))
        stats[tname] = len(rows)
        data_lines.append('ALTER TABLE %s ALTER COLUMN id RESTART WITH %d;'
                          % (tname, max(int(r[0]) for r in rows) + 1))

    schema_path = os.path.join(out, "schema.sql")
    data_path = os.path.join(out, "data.sql")
    with open(schema_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(DDL)
    with open(data_path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(data_lines) + "\n")

    # 附加核验：Logo 二进制覆盖情况（供打包 / 验收核对，不影响导出结果）
    try:
        chk = fetch(args.mysql, args.user, args.password, args.db,
                    "SELECT COUNT(*), "
                    "SUM(CASE WHEN logo_data IS NOT NULL AND LENGTH(logo_data) > 0 THEN 1 ELSE 0 END), "
                    "SUM(CASE WHEN logo_url LIKE 'http%' THEN 1 ELSE 0 END), "
                    "SUM(CASE WHEN logo_url LIKE '/uploads/%' THEN 1 ELSE 0 END) "
                    "FROM brand")
        parts = chk.strip().split("\t")
        print("LOGO_STATS " + json.dumps({
            "brands": int(parts[0]),
            "logo_blob_nonempty": int(parts[1] or 0),
            "logo_url_http": int(parts[2] or 0),
            "logo_url_uploads": int(parts[3] or 0),
        }, ensure_ascii=False))
    except Exception as e:
        print("LOGO_STATS_ERROR " + str(e))

    print("EXPORT_STATS " + json.dumps(stats, ensure_ascii=False))
    print("SCHEMA " + schema_path)
    print("DATA " + data_path + " " + str(os.path.getsize(data_path)))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as e:
        print("EXPORT_ERROR " + str(e))
        sys.exit(1)
