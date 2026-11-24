#!/usr/bin/env python3
from __future__ import annotations
import argparse, json
from pathlib import Path
from urllib.parse import urlparse

def validate_url(value: str) -> str:
    parsed = urlparse(value)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise SystemExit("target_url must be a complete http:// or https:// URL")
    return value

def run_playwright(url: str, screenshot: Path) -> dict:
    from playwright.sync_api import sync_playwright
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        try:
            page = browser.new_page(viewport={"width": 1365, "height": 900})
            response = page.goto(url, wait_until="domcontentloaded", timeout=45000)
            if response is None:
                raise RuntimeError("The browser did not receive an HTTP response")
            if response.status >= 400:
                raise RuntimeError(f"Target returned HTTP {response.status}")
            page.locator("body").wait_for(state="visible", timeout=15000)
            screenshot.parent.mkdir(parents=True, exist_ok=True)
            page.screenshot(path=str(screenshot), full_page=True)
            return {"engine":"Playwright","status":response.status,"final_url":page.url,
                    "title":page.title(),"screenshot":str(screenshot),"result":"passed"}
        finally:
            browser.close()

def run_selenium(url: str, screenshot: Path) -> dict:
    from selenium import webdriver
    from selenium.webdriver.chrome.options import Options
    from selenium.webdriver.support.ui import WebDriverWait
    options = Options()
    for flag in ("--headless=new","--no-sandbox","--disable-dev-shm-usage","--disable-gpu","--window-size=1365,900"):
        options.add_argument(flag)
    driver = webdriver.Chrome(options=options)
    try:
        driver.set_page_load_timeout(45)
        driver.get(url)
        WebDriverWait(driver, 30).until(
            lambda current: current.execute_script("return document.readyState") == "complete"
        )
        screenshot.parent.mkdir(parents=True, exist_ok=True)
        if not driver.save_screenshot(str(screenshot)):
            raise RuntimeError("Selenium could not save the browser screenshot")
        return {"engine":"Selenium","final_url":driver.current_url,"title":driver.title,
                "ready_state":driver.execute_script("return document.readyState"),
                "screenshot":str(screenshot),"result":"passed"}
    finally:
        driver.quit()

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("engine", choices=("playwright","selenium"))
    parser.add_argument("url")
    parser.add_argument("screenshot")
    args = parser.parse_args()
    url = validate_url(args.url)
    screenshot = Path(args.screenshot)
    result = run_playwright(url,screenshot) if args.engine == "playwright" else run_selenium(url,screenshot)
    print(json.dumps(result,ensure_ascii=False,indent=2))

if __name__ == "__main__":
    main()
