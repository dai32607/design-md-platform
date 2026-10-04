# -*- coding: utf-8 -*-
"""
DesignMD-Demo 演示包（8090 实例）Playwright 端到端冒烟测试
路径覆盖：登录 -> 浏览品牌列表 -> 打开品牌详情 -> mock AI 生成 -> 查看统计页
约束：全程不向 uploads 上传文件；统计页需 ADMIN，故用 admin 账号独立验证。
用法：python test_smoke.py [--base http://127.0.0.1:8090]
输出：temp 目录下 e2e_result.json + 失败步骤截图
"""
import argparse
import json
import os
import sys
import time
import traceback
from datetime import datetime

try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass

from playwright.sync_api import sync_playwright, TimeoutError as PWTimeout

BASE = "http://127.0.0.1:8090"
# 完整版 Chromium 可执行文件（headless shell 因网络下载受限未装，直接用完整版跑 headless）
CHROME_EXE = os.environ.get(
    "P6_CHROME_EXE",
    r"C:\Users\dyjn0\AppData\Local\ms-playwright\chromium-1243\chrome-win64\chrome.exe",
)
SHOT_DIR = None
results = []


def record(step, ok, detail=""):
    results.append({"step": step, "pass": bool(ok), "detail": str(detail)[:400]})
    print(("[PASS] " if ok else "[FAIL] ") + step + ((" | " + str(detail)) if detail else ""))


def shot(page, name):
    try:
        p = os.path.join(SHOT_DIR, name)
        page.screenshot(path=p, full_page=True)
        print("[SHOT] " + p)
    except Exception as e:
        print("[SHOT_FAIL] " + str(e))


def login(page, username, password):
    page.goto(BASE + "/login", wait_until="domcontentloaded", timeout=60000)
    page.fill('input[placeholder="请输入用户名"]', username, timeout=15000)
    page.fill('input[placeholder="请输入密码"]', password, timeout=15000)
    page.click("button.submit-btn", timeout=15000)
    page.wait_for_function(
        "() => (localStorage.getItem('token') || '') !== ''",
        timeout=20000,
    )


def main():
    global SHOT_DIR
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default=BASE)
    args = parser.parse_args()
    base = args.base.rstrip("/")
    SHOT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "shots")
    os.makedirs(SHOT_DIR, exist_ok=True)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, executable_path=CHROME_EXE, args=["--no-sandbox"])
        ctx = browser.new_context(viewport={"width": 1440, "height": 900}, locale="zh-CN")
        page = ctx.new_page()

        # ---- T1 登录（普通用户 test/see-local-config）----
        try:
            login(page, "test", "see-local-config")
            page.wait_for_url(lambda u: u.rstrip("/").endswith("/") or u.rstrip("/").endswith("/home") or "/login" not in u, timeout=15000)
            record("T1 登录(test/see-local-config)", True, "token 已写入 localStorage，进入首页")
        except Exception as e:
            record("T1 登录(test/see-local-config)", False, "异常: %s" % str(e)[:200])
            shot(page, "T1_login_fail.png")
            ctx.close(); browser.close(); return

        # ---- T2 浏览品牌列表 ----
        try:
            page.goto(base + "/", wait_until="domcontentloaded", timeout=30000)
            page.wait_for_selector(".brand-card", timeout=20000)
            cards = page.locator(".brand-card").count()
            first_name = page.locator(".brand-card h3").first.inner_text(timeout=10000)
            record("T2 品牌列表加载", cards > 0, "品牌卡片数=%d，首个=%s" % (cards, first_name))
        except Exception as e:
            record("T2 品牌列表加载", False, "异常: %s" % str(e)[:200])
            shot(page, "T2_brands_fail.png")

        # ---- T3 打开品牌详情 ----
        try:
            page.locator(".brand-card").first.click(timeout=10000)
            page.wait_for_url(lambda u: "/brand/" in u, timeout=15000)
            detail_name = page.locator(".brand-title-area h2").first.inner_text(timeout=15000)
            page.wait_for_selector(".sidebar-title", timeout=15000)
            record("T3 打开品牌详情", "/brand/" in page.url and bool(detail_name), "URL=%s, 详情品牌=%s" % (page.url, detail_name))
        except Exception as e:
            record("T3 打开品牌详情", False, "异常: %s" % str(e)[:200])
            shot(page, "T3_detail_fail.png")

        # ---- T4 mock AI 生成 ----
        try:
            page.goto(base + "/agent", wait_until="domcontentloaded", timeout=30000)
            page.wait_for_selector('textarea[placeholder="现代极简风格的咖啡品牌"]', timeout=15000)
            brand = "冒烟测试品牌" + datetime.now().strftime("%H%M%S")
            page.fill('textarea[placeholder="现代极简风格的咖啡品牌"]', "现代极简风格的咖啡品牌")
            page.fill('input[placeholder="如：山屿咖啡"]', brand)
            page.click('button:has-text("开始生成")', timeout=10000)
            page.wait_for_selector(".result-card", timeout=90000)
            page.wait_for_selector(".result-card .el-descriptions", timeout=15000)
            txt = page.locator(".result-card").inner_text(timeout=10000)
            mock_tag = page.locator(".result-card .el-tag").first.inner_text(timeout=10000)
            ok = brand in txt and "MOCK" in mock_tag
            record("T4 mock AI 生成", ok, "品牌=%s, 结果标签=%s, Token数已在描述项" % (brand, mock_tag))
            if not ok:
                shot(page, "T4_ai_fail.png")
        except Exception as e:
            record("T4 mock AI 生成", False, "异常: %s" % str(e)[:200])
            shot(page, "T4_ai_fail.png")

        # ---- 登出，admin 登录 ----
        try:
            ctx.clear_cookies()
            page.evaluate("localStorage.clear()")
            login(page, "admin", "see-local-config")
            record("T5 admin 登录", True, "")
        except Exception as e:
            record("T5 admin 登录", False, "异常: %s" % str(e)[:200])
            shot(page, "T5_admin_login_fail.png")

        # ---- T6 查看统计页（数据统计 Tab）----
        try:
            page.goto(base + "/admin", wait_until="domcontentloaded", timeout=30000)
            page.click('.el-tabs__item:has-text("数据统计")', timeout=15000)
            page.wait_for_selector(".stat-card", timeout=20000)
            stat_count = page.locator(".stat-card").count()
            page.wait_for_selector(".chart-card", timeout=15000)
            chart_cards = page.locator(".chart-card").count()
            # 图表 canvas 初始化晚于图表卡片渲染，等待其出现后再计数
            page.wait_for_selector(".chart canvas", timeout=20000)
            canvas = page.locator(".chart canvas").count()
            record("T6 查看统计页", stat_count > 0 and chart_cards > 0 and canvas > 0,
                   "统计卡片=%d, 图表卡=%d, canvas=%d" % (stat_count, chart_cards, canvas))
            shot(page, "T6_stats.png")
        except Exception as e:
            record("T6 查看统计页", False, "异常: %s" % str(e)[:200])
            shot(page, "T6_stats_fail.png")

        browser.close()

    passed = sum(1 for r in results if r["pass"])
    total = len(results)
    summary = {
        "base_url": base,
        "finished_at": datetime.now().isoformat(timespec="seconds"),
        "total": total,
        "passed": passed,
        "failed": total - passed,
        "results": results,
    }
    out = os.path.join(SHOT_DIR, "..", "e2e_result.json")
    out = os.path.abspath(out)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    print("SUMMARY total=%d passed=%d failed=%d" % (total, passed, total - passed))
    print("RESULT_FILE=" + out)
    sys.exit(0 if passed == total else 1)


if __name__ == "__main__":
    main()
