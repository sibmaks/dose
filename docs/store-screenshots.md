# RuStore screenshots

The Russian gallery contains 10 phone and 10 tablet images, each 1080×1920 JPG.
The captures use the actual app with fictional medication/history fixtures.
Headlines and backgrounds are composed around the app; the only image crop
removes Android's status and navigation bars. UI pixels keep their proportions.

Generated files are under `store-assets/rustore/`, so a Gradle clean keeps them:

- `dose-rustore-ru.zip`: upload bundle, containing numbered phone/tablet folders,
  preview sheets, upload notes, and the provenance manifest.
- `ru/raw/phone/` and `ru/raw/tablet/`: original PNGs, retained outside the ZIP.
- `ru/phone/` and `ru/tablet/`: finished upload images.
- `ru/preview-phone.jpg`, `ru/preview-tablet.jpg`: full contact sheets.

Both device sequences introduce the dashboard, medications, schedules, dosage,
forms, native popup, detailed intake history, report, themes, and languages.
Tablet screens use a native 1200×1920 display at 320 dpi, giving
the application a 600 dp width. These are separate captures, not phone enlargements.

## Regeneration

Dependencies: Python 3, Pillow, macOS Avenir Next font, Android SDK platform tools,
and a rooted Android 8.1/API 27 emulator. The renderer uses no network services.

Use a dedicated, disposable AVD called `DoseStore` on port **5580**. The capture
script refuses to seed a different AVD or serial. Seeding replaces Dose's private
preferences and changes only that emulator's clock, language, display, and
animation settings. It never changes the production app or default emulator.

1. Build the current debug APK with `./gradlew :app:assembleDebug`.
2. Start the dedicated AVD with a native **1080×1920** display, for example
   `emulator -avd DoseStore -port 5580 -no-snapshot -skin 1080x1920`.
3. Install `app/build/outputs/apk/debug/app-debug.apk` on `emulator-5580`.
4. Run `python3 scripts/capture_store_screenshots.py set --device phone`.
5. Stop that AVD, then restart it with `-skin 1200x1920` for a native tablet
   display. A `wm size` override alone is insufficient on this emulator version.
6. Run `python3 scripts/capture_store_screenshots.py set --device tablet`.
7. Run `python3 scripts/render_store_screenshots.py`.
8. Inspect both preview sheets and representative full-size JPGs before upload.

The capture script sets the sample date to 3 November 2026 and uses a due
13:00 reminder for the popup frame. It disables feed banners for that frame so
the popup remains unobscured. It uses the real opt-in reminder service without
adding capture-only code to the app. The popup's tinted outer panel, outline,
and dimmed backdrop are part of the shipping UI. Its sample clock is set before
opening Today so the underlying page and popup both show the same due state.
The PNG header and emulator's physical
display dimensions are checked to prevent phone captures being used as tablet
sources accidentally. The manifest records source/output SHA-256 values and
the source APK and Git revision.

## Store constraints

Upload only the numbered JPGs in the matching device folder, in numeric order,
with portrait orientation. Each image is RGB, exactly 9:16, and below 3 MB;
this meets both the supplied 5 MB limit and the stricter phone limit in the
publication guide. At least three screenshots are required for each device
type that is included. Preview/contact sheets are not upload assets.

Sources: [RuStore publication guide](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/app-publication)
and [screenshot upload specification](https://www.rustore.ru/help/work-with-rustore-api/api-upload-publication-app/apk-screens-upload).

No store upload or publication is performed by these scripts.
