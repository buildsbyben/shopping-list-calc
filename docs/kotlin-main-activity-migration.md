# MainActivity Kotlin migration

This review branch is stacked on `refactor/kotlin-storage` (PR #15). It converts
MainActivity only; SettingsActivity, resources, dependencies, versioning, preference
keys and storage schemas are unchanged. This is not a production release.

## Behavior preserved

- Existing UI labels, layout dimensions, colors, dialogs and menu actions.
- Mutable item reference identity, duplicate-name matching, ordering and completion.
- Java ASCII/control-character trim and regex semantics in pasted list names.
- Quantity/weight validation, Double calculations and quick-cents formatting.
- Save/load/add/replace/overwrite/delete dialogs and current-cart isolation.
- Lifecycle settings reload, IME field navigation and delayed keyboard/scroll work.
- Long-press drag, insertion placeholder, drop/cancel and edge autoscroll callbacks.

The drag holders remain ordinary classes, not data classes. List-index removals
use `removeAt`; item removals retain reference identity. Nullable fields and
initialization guards preserve the original lifecycle checks.

## Automated checks

Ten new Robolectric interaction tests run against the original Java implementation
before conversion, then unchanged against Kotlin. Alongside the existing 33 tests,
they cover cart editing/reopening, duplicate rows, weighted/completed items,
quick entry and IME Next, pasted lists, clear/delete/cancel, saved-list dialogs,
settings reload and staged reorder Save/Cancel. The harness drains the main queue
for dialog onShow/button callbacks; production behavior was not altered to fix tests.

```sh
./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleR8Smoke assembleRelease --no-daemon --max-workers=2
```

Use JDK 17 and Android SDK 35. Linked-worktree unsigned release builds are build
checks, not F-Droid reproducibility or release-signing evidence.

## Results

- Java baseline: all 43 tests passed; debug and optimized smoke builds passed.
- Kotlin: all 43 tests passed unchanged. Android lint: zero errors, ten warnings
  (one monochrome launcher icon warning and nine SetTextI18n warnings for the
  preserved UI strings; no string changes are part of this migration).
- Clean debug, optimized R8 smoke and unsigned release builds passed. Debug APK
  signing certificate matches the previous storage test build.
- Source parity audit: all 88 method names/counts and every string literal match
  the Java baseline. Manual review covered numeric conversions, callback returns,
  list removal overloads, item identity, lifecycle guards and drag/IME paths.

## Phone checklist

Install the development APK over the existing development app without uninstalling
or clearing data. The production application is separate and remains untouched.

1. Check that the existing cart, saved lists and settings survived the update.
2. Edit duplicate-name rows; complete/restore items; try quantity and weight modes.
3. Use keyboard Next through name/price/weight fields, including the last row, in
   normal and quick-entry modes. Check focus, scrolling and keyboard visibility.
4. Reorder a long list by dragging both rows and handles. Drag near both screen
   edges for autoscroll. Check Save and Cancel, including identical item names.
5. Edit/paste a list; save/load/add/replace/edit/delete a saved template. Check that
   cancelling dialogs changes nothing and saved templates do not lose cart data.
6. Close/reopen, return from Settings and rotate the phone; check layout and totals.

Robolectric does not prove real-device drag gestures, keyboard/insets, visual parity
or installed-APK upgrade persistence. Those checks remain pending on the phone.
