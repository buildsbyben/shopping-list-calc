# Shopping List Calculator

An offline Android shopping-list calculator for personal sideloading. Build a weekly list, enter prices and quantities while shopping, and track the total with sales tax against a budget.

## Features

- Native dark-mode UI
- Offline-only local storage
- Store-order controls, price, and quantity for each item
- Qty and weight modes, including price-per-pound calculation
- Cart check-off that collapses purchased items
- Plain-text list editor, budget, and tax-rate settings
- Running subtotal, tax, total, and remaining-budget calculations

## Build

This is a plain Android Gradle project with no third-party app dependencies.

Prerequisites:

- JDK 17 or newer
- Android SDK Platform 35, with `ANDROID_HOME` pointing to the SDK directory

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Release builds

Release signing credentials are intentionally not stored in this repository. Provide the signing-key path, passwords, and alias through the `SHOPPING_CALC_*` environment variables expected by `app/build.gradle`, then run:

```bash
./gradlew assembleRelease
```

The signed release APK is written to `app/build/outputs/apk/release/app-release.apk`.
