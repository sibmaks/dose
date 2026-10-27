# UI consistency fixes

Follow-up to the UI audit of revision `28010b4` on 26 October 2026. Each issue is implemented, verified and committed separately. Existing visual hierarchy and context-specific variants are preserved.

Shared roles and usage guidance are recorded in [UI design conventions](ui-design-system.md).

| Issue | Scope | Status |
|---|---|---|
| UI-01 | Small-text contrast on hero, status, overlap and widget surfaces | Complete |
| UI-02 | Native widget dose action hit areas | Complete |
| UI-03 | Visible validation for non-positive dosage | Complete |
| UI-04 | Empty custom pause end state | Complete |
| UI-05 | Accessible switch names | Complete |
| UI-06 | Semantic selection for custom choices | Complete |
| UI-07 | Medication wizard keyboard and footer layout | Complete |
| UI-08 | Large-text navigation and compact layouts | Complete |
| UI-09 | Widget appearance policy and updates | Complete |
| UI-10 | Shared semantic color ownership | Complete |
| UI-11 | Shared accessible picker fields | Complete |
| UI-12 | Shared typography roles | Complete |
| UI-13 | Named geometry conventions | Complete |

## Verification

Commands use JDK 21 and the cached Gradle distribution. Runtime observations are recorded separately from source and unit-test checks. Device-specific behavior is not inferred from unit-test results.

### UI-01

- Reused full-opacity, contrast-validated text on brand, status and resolved-overlap surfaces; aligned compact-widget violet with the app hero.
- Added shared status color pairs and contrast checks against shipped widget XML.
- Verification: four theme/contrast tests, debug APK assembly and lint passed. Current emulator build checked for hero appearance; populated detail and launcher-widget visuals remain unverified.

### UI-02

- Expanded widget Taken/Skip hit areas to at least 48 x 48 dp and increased row height/capacity thresholds together. Smaller widgets retain the compact layout until a complete expanded row fits.
- Verification: widget-state and shipping-layout geometry tests (9 total) plus debug assembly passed. Launcher hit testing remains device-host dependent.

### UI-03

- One amount-validation result drives both navigation blocking and field error/supporting text, preserving free-form dosage descriptions.
- Verification: three focused tests and debug assembly passed. A guarded unsaved emulator replay confirmed the localized error for amount 0, then discarded the draft.

### UI-04

- Unchosen custom ends are nullable; the date stays meaningful and the time displays a localized placeholder instead of formatting epoch zero. Save stays disabled and an unchosen time is not immediately shown as an invalid range.
- Verification: 11 pause-window tests and debug assembly passed. Guarded emulator replay confirmed no 1970 value, a Choose a time placeholder, and disabled Save; no pause was saved.

### UI-05

- Shared labeled switches keep their contextual accessible name inside a single switch action, without a separately clickable indicator. Medication activation uses the medication name; instructions and reminder switches use localized labels.
- Verification: debug assembly passed. Emulator hierarchy confirmed each reminder switch action contains its name and exposes checked state, with only one click action. TalkBack speech was not exercised.

### UI-06

- Custom exclusive choices now expose radio selection; independently selected weekdays/month days expose checkbox state. Explicit groups separate start/end presets and other exclusive option sets. Visual indicators do not add separate actions.
- Verification: debug assembly passed. Emulator hierarchy/replay verified selected pause/unit choices, exclusive selection changes, and simultaneous checked Mon/Tue states. Unsaved flows were discarded; TalkBack speech remains untested.

### UI-07

- The full-width dialog explicitly opts into window insets and safe-drawing padding, enabling the appropriate platform resize/inset path instead of an unspecified IME mode.
- Verification: debug assembly passed. On API 27, Continue was visible above the IME and successfully advanced while the keyboard was open. Modern Android IME animation behavior remains a runtime coverage limit.

#### Height recovery follow-up — 27 October 2026

- The original check missed the return to full height. Reproduction showed later steps retaining a 939 px card after keyboard dismissal, compared with 1,530 px before typing. The retained editable page could also leave the old field visible beneath the next step's heading.
- The dialog now owns its editor state and focus handling. A full-window outer container applies system/IME insets around the fractional-height card, and only the active step renders its input fields. Horizontal swipes use the existing step validation and cannot dismiss the first step or save from the final step; hardware Back follows the same previous-step rules as the footer.
- Added `scripts/check_medication_editor_ime.py`, an ADB regression for an installed English debug build. It uses an unsaved draft, compares card bounds before/during/after typing, checks Continue and keyboard Back dismissal, validates swipe gating, and returns through every step with hardware Back. It writes XML/screenshots to a temporary directory and never presses Save.
- Verification: 236 unit tests, debug assembly and lint passed. On the API 27 emulator at both 100% and 200% text, the card returned from 899 px while typing to its original 1,627 px on Form, Dosage, Schedule and Details, including repeated keyboard opening/dismissal, validated swipes and hardware Back. The original font setting was restored and all drafts were discarded.

Run from the repository root with `adb` on PATH and the app on a main tab:

```sh
python3 scripts/check_medication_editor_ime.py --serial emulator-5554
```

Use `--adb /path/to/adb` when needed. Close any existing draft first; the check requires English labels and a closed keyboard. It does not change language, theme or font settings.

### UI-08

- Navigation labels stay on one line with ellipsis when necessary; their full accessible text is retained. Choice cells grow with text, unit/weekday columns adapt, month-day columns respect a 48 dp cell minimum, presets wrap, and paired fields stack for large text/narrow content.
- Verification: debug assembly passed. Emulator checks at 200% confirmed one-line navigation and a scrollable two-column unit grid with growing cells. Text size was restored and the draft discarded. Full locale/tablet/landscape coverage remains outstanding.

### UI-09

- Expanded widgets follow System/Light/Dark; compact widgets retain the same fixed violet hero as Today. Theme selection refreshes existing widgets immediately. Configuration changes refresh widgets while the process is running; API 31+ text actions retain day/night colors for host reapplication. Native resources cover light/dark backgrounds and actions.
- Verification: 13 palette/layout/widget-state tests, debug assembly and lint passed. A temporary API 27 launcher widget was resized to expanded mode and visibly refreshed from light to dark after app theme selection. System appearance was restored and the test widget removed. Populated widget rows and other launcher hosts remain unverified visually.

### UI-10

- Consolidated success, attention, resolved, warning, snooze, pause, eyebrow and invalid-field colors in one semantic palette. Feature screens no longer define hex colors or light/dark pairs. Native reminder popup colors now come from the same Material scheme; platform dialog themes reuse fixed light/dark resource roles.
- Verification: four expanded palette/contrast/resource-parity tests and debug assembly passed. Intentional branded surfaces, user-selected medication colors, and the functional color wheel remain distinct. Native reminder popup rendering was source verified, not visually exercised.

### UI-11

- Scheduling, intake history and pause editing share one outlined picker field using Material label/border decoration, the theme interactive outline, scalable height, associated label/value and error semantics, optional icons and an independent clear action. Date formats and validation rules remain owned by each flow.
- Verification: debug assembly and lint passed. Emulator replay confirmed label/value descriptions, a working native date-picker action, empty-time semantics, and stacked date/time fields at 200% text. Final field decoration was visually checked at normal size; history data-specific states remain source verified.

### UI-12

- Introduced the app's complete Material typography and named text roles, including line heights. Consolidated half-size caption variants, retained the heading/hero hierarchy, and reused role sizes in the native reminder popup and named XML widget dimensions.
- Verification: theme/contrast tests and debug assembly passed. Emulator replay checked normal picker decoration and 200% navigation, dosage choices and stacked picker fields. Native text still follows Android font scaling; brand lettering and the compact widget clock retain their explicit display roles.

### UI-13

- Added shared spacing, radii, shapes and minimum-control dimensions; installed the Material shapes and reused the roles across Compose and the native reminder popup. Consolidated adjacent card/button/badge variants, matched editable and picker-field shapes, and retained named shell/hero/native-surface variants.
- Widget XML now uses named geometry resources. The shipping-layout test resolves these resources, verifies physical actions and row capacity, and checks shared XML/Compose dimension parity. Added design conventions covering color, text, geometry, semantics and adaptive layouts.
- Verification: all 236 unit tests, debug APK assembly and lint passed. Final API 27 emulator checks covered the normal home screen and picker fields, 200% navigation/dosage choices/stacked fields, native date-picker opening, and advancing the medication wizard with the keyboard open. Drafts were discarded and the original font/theme settings restored.

### Typography and reminder-card refinement — 27 October 2026

- The typography extraction had replaced Material's explicit sans-serif family with the platform default and omitted its tracking/platform metrics. Shared styles now inherit the Material body base, including its original 0.5 sp tracking; unweighted small labels use regular weight.
- Notification-status panels now use the reminder cards' borderless 14 dp shape and 12 dp inset. Removed the separate green/purple strokes and extra title padding; the plain status description uses the same caption role and 4 dp title gap as the reminder choices.
- Verification: all 236 unit tests, debug assembly and lint passed in the current working tree. API 27 screenshots and hierarchy checks at 100%/200% text confirmed matching title/description left edges and a borderless inactive-status panel. The active-pause variant was source checked. The original 100% font setting was restored.

## Final validation

`JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --no-daemon`

- 236 tests in 32 suites; no failures or errors. Lint completes without errors; non-blocking warnings remain in the project.
- Runtime evidence is from the existing API 27 phone emulator. Modern Android IME behavior, TalkBack speech, populated overlay/widget rows, other launcher hosts, every locale, landscape and tablet layouts remain coverage limits. Source checks and unit tests are not substitutes for those observations.
