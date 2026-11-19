# AUTU-LIO — Build modes for on-device ML vision

## Development builds (default)

`AUTU_LIO_ENABLE_ML_VISION` defaults to `false`. The development APK does not compile the optional ML Kit source set and does not include these dependencies or their bundled native libraries/models:

- Text recognition (OCR)
- Image labeling
- Object detection
- QR-code and barcode scanning

The main Accessibility implementation remains available. It can still read text and content descriptions exposed by the active app's accessibility tree, locate accessible labels, use screen coordinates, read screen colors, take screenshots for the explicitly enabled Gemini Live session, and perform the existing permitted Android actions.

Text drawn only into a game canvas, video frame, or custom view may not be exposed by Accessibility. OCR and the image/QR/object functions are intentionally unavailable in the development build; calling one of those functions returns a clear message that the ML vision module is disabled.

## Public release build

When AUTU-LIO is ready for public distribution, run the `AUTU-LIO Android APK` workflow from `main` and set its `include_ml_vision` input to `true`. The workflow then:

1. Adds `app/src/mlVision/java` to the Android main source set.
2. Adds the four ML Kit dependencies from `gradle/libs.versions.toml`.
3. Sets `BuildConfig.ML_VISION_ENABLED` to `true`.
4. Packages the ML Kit native libraries and bundled models required by those features.

Keep the input `false` during development to produce the smaller APK. The ML Kit implementation and version catalog entries remain in the repository, so enabling vision later does not require rewriting those features.

## Local Gradle builds

Development (default):

```sh
gradle :app:assembleDebug
```

Public feature set:

```sh
gradle :app:assembleRelease -PAUTU_LIO_ENABLE_ML_VISION=true
```

Release signing still follows the existing four-secret release-signing configuration.