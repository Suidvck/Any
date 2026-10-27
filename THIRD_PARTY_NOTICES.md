# Third-party notices

Any is MIT-licensed (see [LICENSE](LICENSE)). It bundles the following
third-party assets.

---

## Font Awesome Free 6

- **Bundled files:**
  [`fa-solid-900.ttf`](app/src/main/assets/fonts/fa-solid-900.ttf) and
  [`fa-regular-400.ttf`](app/src/main/assets/fonts/fa-regular-400.ttf)
- **Project:** <https://fontawesome.com>
- **Copyright:** Copyright (c) 2024 Fonticons, Inc.
- **Icons:** CC BY 4.0 — <https://creativecommons.org/licenses/by/4.0/>
- **Font software:** SIL Open Font License 1.1, with Reserved Font Name
  "Font Awesome" — <https://scripts.sil.org/OFL>
- **Full license text:**
  [`app/src/main/assets/fonts/LICENSE.txt`](app/src/main/assets/fonts/LICENSE.txt),
  also shipped inside the APK next to the fonts, and at
  <https://fontawesome.com/license/free>.

Any uses the solid and regular icon fonts only: it renders Font Awesome glyphs
through its own `FaIcons` helper and does not include Font Awesome's CSS or
JavaScript.

Attribution: icons by [Font Awesome](https://fontawesome.com), licensed under
CC BY 4.0; the Font Awesome Free font files are licensed under the SIL Open
Font License 1.1.

---

## Jsoup

- **Project:** <https://jsoup.org>
- **Copyright:** Copyright (c) 2009-2024 Jonathan Hedley
- **License:** MIT — <https://jsoup.org/license>
- **Used for:** parsing HTML from the live web-search feature before results
  are handed to the AI. Jsoup does not make network requests itself; Any only
  feeds it the pages fetched for a search query.

