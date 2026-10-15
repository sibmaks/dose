# Language picker flags

SVG source: https://github.com/hampusborgos/country-flags/tree/c09927e63705529bbf59ca6684cd9b23225dddad/svg

Downloaded on 2026-10-15. Upstream describes the flag artwork as public domain, originally from Wikimedia Commons. See https://github.com/hampusborgos/country-flags/blob/c09927e63705529bbf59ca6684cd9b23225dddad/README.md for attribution and usage notes.

Android vector drawables in `app/src/main/res/drawable/flag_*.xml` are generated from these SVGs with Android SDK Svg2Vector. Georgia’s nested clipping and Uzbekistan’s nested star references are represented explicitly in their Android vector files because Svg2Vector drops those SVG constructs. Intrinsic sizes are normalized to 36dp wide while preserving each flag’s aspect ratio and vector viewport. These are visual language hints, not an exhaustive mapping between countries and languages. English uses the UK flag; Romanian uses Moldova's flag to match the requested language set. Native language names remain the authoritative labels.
