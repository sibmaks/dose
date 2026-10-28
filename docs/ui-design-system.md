# UI design conventions

Use the shared roles below before adding a screen-specific value. Keep medication colors, the functional color wheel and intentional branded surfaces distinct from status colors.

## Color and appearance

- `Theme.kt` owns the Material light/dark schemes. Use Material roles or the existing `Panel`, `SoftPanel`, `TextPrimary`, `TextMuted` and `Violet` accessors.
- `SemanticColors.kt` owns status, warning, snooze, pause and invalid-field pairs. Use a foreground together with its matching container; do not add alpha to small status text.
- The native reminder popup reads the same schemes. Native XML colors mirror the shared roles; `ThemeTest` verifies their parity and actual widget text/background contrast.
- Expanded widgets follow the app's System/Light/Dark selection. Compact widgets keep the fixed violet Today hero treatment.

## Text

`Typography.kt` installs `DoseTypography` in `DoseTheme`.

Text roles inherit Material's sans-serif family, tracking and platform text metrics, then apply their size, line height and weight. Plain small copy uses regular weight; titles and selected/action text use their explicit emphasis. Build styles from the Material base so consolidating sizes does not change font resolution or tighten secondary copy.

Prefer `DoseText` roles for the app's existing hierarchy:

| Use | Role | Size / line height (sp) |
|---|---|---|
| Choice labels, navigation, badges | `label` | 11 / 16 |
| Captions, metadata | `caption` | 12 / 18 |
| Supporting descriptions | `supporting` | 13 / 18 |
| Body copy | `body` | 14 / 20 |
| Item names | `itemTitle` | 15 / 22 |
| Input text | `input` | 16 / 24 |
| Context headings | `contextTitle` | 16 / 24 |
| Emphasized text | `emphasis` | 18 / 24 |
| Section, detail, shell headings | `sectionTitle`, `detailTitle`, `shellTitle` | 19 / 26, 20 / 28, 21 / 28 |
| Page heading | `pageTitle` | 31 / 40 |
| Next-dose clock | `heroTime` | 38 / 46 |

Brand lettering, empty-hero headings, metrics and tracked eyebrows have explicit display roles. The compact native widget clock uses 36 sp to fit its host surface. Native widget text sizes live in `values/dimens.xml`; native popup sizes read `DoseText`. Keep sizes in sp and allow content to grow or scroll when text scales. A local weight override may strengthen an action or selected value without inventing a new size.

## Geometry

`Geometry.kt` owns `DoseSpacing`, `DoseRadii`, `DoseShapes`, `DoseDimensions` and the installed Material shapes.

- Default spacing: 4, 8, 12, 16, 20 and 24 dp. Named 6 dp compact gaps, 10 dp control gaps and 14 dp card insets retain the existing dense layouts.
- Main pages use the 21 dp `pageGutter`. Full-screen shells use the 12 dp `shellMargin`, 20 dp `shellInset` and 26 dp shell shape. The branded hero keeps its 23 dp inset and 27 dp radius.
- Reuse shapes by purpose: badge 8, choice 12, button 14, field 15, tile 16, panel 18 and card 20 dp. Pills use a fully rounded shape. Avoid adjacent one-off variants such as 19 versus 20 dp cards.
- Reminder-status panels use the same borderless button shape and 12 dp inset as reminder-choice cards. Plain title/caption stacks use a 4 dp gap; semantic container/text colors communicate the status.
- Editable fields explicitly use the same field shape as `OutlinedPickerField`. Small icon tiles can use the inset tile shape to keep fill inside a border.
- Interactive controls have a 48 dp minimum target. Segments, dialog actions, primary actions and fields retain their named 50, 51, 53 and 56 dp minimum heights; these are minimums, not clipping constraints.
- Native popup shapes and spacing read the same Kotlin roles. Its 24 dp outer panel and 28 dp action radius are intentional native-surface variants.
- Widget geometry uses named XML dimensions. The row's 128 dp content plus 8 dp gap must agree with `DoseWidgetState.ROW_HEIGHT`; heading, actions, padding and expanded capacity are checked against the shipped layouts in `WidgetLayoutTest`. Update both the layout and capacity model when this contract changes.

Keep optical adjustments, icon artwork, drawing geometry and responsive breakpoints local when they are specific to that context. Promote a value to a shared role when it represents a repeated design decision, rather than assigning every pixel an arbitrary name.

## Interaction and adaptation

- `LabeledSwitch` gives each switch one action with its contextual name.
- `selectionControl` gives exclusive choices radio semantics and independent choices checkbox semantics. Put exclusive sets in separate selectable groups and keep indicators decorative.
- `OutlinedPickerField` associates label, value and error with a picker action. It supplies Material field decoration without editable-text or keyboard semantics.
- `ResponsiveFields` stacks paired controls for narrow content or large text. Use wrapping or adaptive grids for choice sets and preserve the full accessible navigation label when visible text is truncated.
- `MedicationScheduleSummary` aligns dose, recurrence, time and date fields across a medication's schedules using measured text widths. Date ranges can wrap; larger text uses shared dose/day and time/date columns, and very narrow content stacks the fields. Prose summaries reuse the same localized fields, with separators added by the formatter.
- Full-width medication dialogs use a full-window outer container for safe-drawing/IME insets, with the fractional card inside it. Keep only the active step's input fields in composition. Verify both footer reachability while typing and recovery to the original card height after the keyboard closes; `scripts/check_medication_editor_ime.py` covers these paths and validated swipe/Back navigation.
