# UI/UX audit — v1.3.26

## Implemented

- Android login: removed the fixed 190dp illustration, reduced repeated controls, constrained form width, compact fingerprint option. Additional branding is conditional on available height. Scroll remains available for keyboards, small screens and enlarged fonts; no absolute guarantee of zero scrolling under accessibility settings.
- Android light appearance: one-time migration of the previous dark default, compatible remote palette filtering, light app header and bottom navigation. Intentional blue splash/welcome screens keep contrasting system bar icons.
- Home: rounded light greeting panel, smaller module grid tiles and shorter bottom bar.
- Onboarding: keyed text/icon transitions; synchronous bounded navigation prevents queued Next taps from exceeding the final page. Gradient action exposes button semantics.
- POS: animated blue selection, check icon, live quantity and stock-aware add button. Checkout stays outside the product list. Long names get two lines. Selection feedback replaces repetitive success toasts; stock errors remain.
- PIN: exactly four digits retained; Urdu decoration uses left-to-right order; password semantics mask the field; filled cells no longer imply the PIN was authenticated.
- Notifications: top placement retained, removed 48dp maximum height that could clip errors.
- Fingerprint enrollment: waits for Dashboard, preventing it from covering the welcome transition.
- Desktop login: light scoped variables, 45/55 layout, responsive form-only mode below 961px, aligned four-cell PIN entry, shop/cloud/device illustration, reduced-motion support and cache version update.
- Added Urdu translations for new header, login and selection labels.

## Validation

- JavaScript syntax check passed.
- Eight existing desktop PIN regressions passed, including leading-zero PIN and stale password-mode state.
- All five Android source XML resources parsed successfully.
- Final Android v1.3.26 Gradle assembleDebug succeeded on GitHub run 36183935959 (commit 43d8b00), including translation/system-bar changes.
- Windows x64 NSIS installer built successfully as SkyBarech-ERP-Setup-1.3.26.exe.
- Packaging audit found Desktop build/icon.ico was excluded by the global build-folder ignore rule. Explicit exceptions now retain the required icon files in Git and source archives.
- Browser and native visual inspection both blocked by the computer-use runtime error: `failed to write kernel assets: The system cannot find the path specified. (os error 3)`.

## Device acceptance still needed

No physical-device, screenshot or printer verification is claimed. Check Android at 360×640 and 412×915, English/Urdu, keyboard open and enlarged text. Confirm login controls remain reachable; no horizontally clipped fields; selected product quantity increments once; checkout remains reachable with a long catalog; rapid Next/Back and fingerprint welcome flow behave correctly. Check desktop at 1366×768, 1024×768 and enlarged display scaling.

Existing camera scanning, Bluetooth printing, cloud synchronization and sale persistence were not changed by this presentation update. They still require real-device/integration verification. Other modules received shared light theme/navigation styling; this is not a full functional audit of every ERP workflow.
