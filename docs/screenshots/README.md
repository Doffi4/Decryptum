# Screenshot provenance

`vault.png`, `totp.png`, `generator.png` and `autofill.png` existed before the current public-presentation work. Exact capture build/device and account provenance have not been established. They are historical app images, not current device verification or Stitch designs.

The Phase 5 site/README use vault and TOTP only, labelled as earlier interface images. No password, seed or private key is visible in those two images; names/counts in the vault are not user/download metrics. The website converts them to WebP without changing interface content. Autofill (account addresses) and generator (open password value) are excluded from the new public presentation.

Replace with current light/dark screenshots after Phase 6 device testing using an isolated, disposable vault and synthetic identities. Record APK hash, revision, Android/device, locale, theme, capture command and synthetic fixture. Do not defeat screenshot protection on a real user vault for marketing.

`icon.webp` is a web-size export of the current adaptive icon's foreground paths/linear gradients and background (`drawable/ic_launcher_foreground.xml`, `ic_launcher_background.xml`). The raster mipmap still contained an Android placeholder, so it was not used as the public brand icon. The authored SVG export is retained in `website/public/assets/icon.svg`; no Android icon resources were changed.
