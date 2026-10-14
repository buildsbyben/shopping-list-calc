# Kotlin storage migration

Converts only `ShoppingListStore` from Java to Kotlin, stacked on the helper
migration. MainActivity, resources, dependencies, versioning and data schemas
remain unchanged. Ben passed the helper development APK's device checklist
before this step.

## Compatibility

- Preference keys, Float/Boolean/String/Int types, defaults and asynchronous
  `apply()` writes are unchanged. Tax and budget retain Float precision.
- Item JSON keys, field defaults, order and duplicates remain unchanged.
- Invalid item JSON clears the entire destination; corrupt saved-list entries
  retain the valid prefix, matching the previous behavior.
- Non-finite numeric items are skipped while other items continue saving.
- Saved-list names use Java's ASCII/control-character trim semantics, not
  Kotlin's broader Unicode whitespace trimming.
- Currency separators still use their first character, with the same fallbacks.
- Java callers keep the same method names and signatures.

## Verification

Three added regression tests pass on the Java parent and Kotlin implementation:
Java trim/partial corrupt lists, invalid numeric item writes, Float precision and
multi-character separators. The full suite contains 33 tests.

Run with JDK 17 and Android SDK 35:

```sh
./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleR8Smoke assembleRelease --no-daemon --max-workers=2
```

## Phone check

Before installing, leave a recognizable cart, saved list and custom settings in
the existing development app. Install this development APK **over that app**;
do not uninstall or clear its data. The `.dev` app remains separate from the
production app.

1. Confirm the existing cart, saved lists, tax/budget, currency, entry modes and
   weight unit survive the update.
2. Edit items, save/load a list, and change settings. Close and reopen the app;
   confirm everything persists.
3. Clear the cart and reload a saved list; verify saved templates are unaffected.

Automated reopening tests do not prove an installed-APK upgrade. Device upgrade
validation is pending. MainActivity migration, production release and F-Droid
reproducibility are outside this step. Linked worktree release builds are only
build checks, not release/reproducibility evidence.
