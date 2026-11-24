# Automation toolkit

This repository includes manual-only browser and Android automation. It does not run on each push and does not build or publish release APKs.

## Browser test (Playwright / Selenium)
1. Open Actions and select Manual automation toolkit.
2. Choose browser.
3. Enter a public HTTP(S) URL.
4. Choose both, playwright, or selenium, then run.
5. Download the screenshot artifact from the completed run.

Do not put passwords, session tokens, or signed secret URLs into workflow inputs.

## Android test (Appium)
This test uses an Android emulator and a prebuilt APK; it does not compile an APK.
1. Make an already-built APK available at a direct public HTTPS URL.
2. Select Manual automation toolkit, choose android, and provide apk_url.
3. Run the workflow.

It installs the APK on an API 35 emulator, launches the app through Appium/UiAutomator2, confirms the app package is foregrounded, and uploads a screenshot.

Expected package: com.autu.lio
Expected launcher activity: com.autu.lio.MainActivity

Use only a public, non-secret APK URL. Do not place credentials or signed secret URLs into workflow inputs.

## Cost and safety
- Manual trigger only; no automatic CI usage on normal pushes.
- Standard GitHub-hosted runners for public repositories are eligible for free usage under GitHub policy.
- Playwright, Selenium, Appium, and UiAutomator2 are open-source and the workflow does not call TinyFish or paid AI APIs.
- Appium uses an emulator, not the user's physical phone.
- Release APK builds remain separate and must follow the existing release workflow.
