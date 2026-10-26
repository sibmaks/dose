# UI consistency fixes

Follow-up to the UI audit of revision `28010b4` on 26 October 2026. Each issue is implemented, verified and committed separately. Existing visual hierarchy and context-specific variants are preserved.

| Issue | Scope | Status |
|---|---|---|
| UI-01 | Small-text contrast on hero, status, overlap and widget surfaces | Complete |
| UI-02 | Native widget dose action hit areas | Complete |
| UI-03 | Visible validation for non-positive dosage | Complete |
| UI-04 | Empty custom pause end state | Complete |
| UI-05 | Accessible switch names | Complete |
| UI-06 | Semantic selection for custom choices | Pending |
| UI-07 | Medication wizard keyboard and footer layout | Pending |
| UI-08 | Large-text navigation and compact layouts | Pending |
| UI-09 | Widget appearance policy and updates | Pending |
| UI-10 | Shared semantic color ownership | Pending |
| UI-11 | Shared accessible picker fields | Pending |
| UI-12 | Shared typography roles | Pending |
| UI-13 | Named geometry conventions | Pending |

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
