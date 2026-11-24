#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, time
from pathlib import Path
from appium import webdriver
from appium.options.android import UiAutomator2Options
from selenium.webdriver.support.ui import WebDriverWait

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--package", required=True)
    parser.add_argument("--activity", required=True)
    parser.add_argument("--screenshot", default="artifacts/appium-smoke.png")
    parser.add_argument("--server", default="http://127.0.0.1:4723")
    args = parser.parse_args()
    options = UiAutomator2Options()
    options.load_capabilities({
        "platformName":"Android","automationName":"UiAutomator2",
        "deviceName":"GitHub Actions Android Emulator","appPackage":args.package,
        "appActivity":args.activity,"noReset":True,"newCommandTimeout":120
    })
    driver = webdriver.Remote(command_executor=args.server, options=options)
    try:
        driver.implicitly_wait(5)
        WebDriverWait(driver, 60).until(lambda current: current.current_package == args.package)
        driver.activate_app(args.package)
        time.sleep(3)
        active_package = driver.current_package
        active_activity = driver.current_activity
        if active_package != args.package:
            raise RuntimeError(f"Expected foreground package {args.package}, got {active_package}")
        screenshot = Path(args.screenshot)
        screenshot.parent.mkdir(parents=True, exist_ok=True)
        if not driver.save_screenshot(str(screenshot)):
            raise RuntimeError("Appium could not save the application screenshot")
        print(json.dumps({"package":active_package,"activity":active_activity,
            "device":driver.capabilities.get("deviceName"),"screenshot":str(screenshot),
            "result":"passed"},ensure_ascii=False,indent=2))
    finally:
        driver.quit()

if __name__ == "__main__":
    main()
