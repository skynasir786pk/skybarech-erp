# Design system

Use the SkyBarech blue/purple mark, deep ink text, white cards, rounded 16–18dp panels and concise status colors.

v1.3.26: Android defaults to light mode, including existing installs through a one-time preference migration. Remote shop colors may tint the light workspace but must not force dark surfaces. Blue brand splash/welcome transitions retain readable system bar icons.

Login uses a compact brand row and white form. Show extra illustration only when sufficient height is available; keep scrolling as an accessibility/keyboard fallback. Desktop authentication owns its light palette, uses a 45/55 form/illustration split and hides the illustration below 961px. Four PIN boxes always read left to right, including Urdu. Filled boxes indicate input, not successful authentication.

Onboarding transitions use keyed AnimatedContent (fade and directional slide) with bounded synchronous step changes. POS selection uses a blue surface, border, check and live quantity. Do not show a success toast on every product tap. Keep checkout outside the scrollable product list.

Mobile POS prioritizes the product list. The persistent footer shows current cart quantity, products and total, followed by Review & Save Bill. Receipts show the logo/mark, shop name, address, phone, invoice details, line items and total in the same visual style on Android and Desktop.
