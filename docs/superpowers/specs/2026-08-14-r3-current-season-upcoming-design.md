# R3 — Home with Current Season and Upcoming Shelves (Design)

**Date:** 2026-08-14
**Status:** Approved design; implementation plan to follow
**Product:** Anime Wiki Android app
**Roadmap:** `docs/superpowers/specs/2026-07-22-anime-wiki-product-roadmap-design.md` (R3)

## 1. Objective

Deliver R3 ("current season and upcoming premieres") as a new **Home**
destination: a showcase of horizontal shelves that sits alongside the existing
Discover listing rather than reshaping it. The current Discover screen (search,
filters, organization mode) is unchanged.

This differs from the original roadmap wording, which assumed Jikan
`/seasons/*` endpoints and folded the modes into Discover. R3 is delivered
against the AniList GraphQL backend, and the modes live on their own screen.

## 2. Scope

**In scope**

- A new bottom-navigation destination **Home**, set as the start destination.
  Bottom bar becomes Home · Discover · Favorites.
- Four horizontal shelves on Home, each a bounded preview (~25 items),
  horizontally scrollable, tapping a card opens the existing details screen:
  1. **This season** — current season media.
  2. **Upcoming** — next season media (premieres not yet released).
  3. **Top** — score ranking, reusing the existing Discover ranking data.
  4. **Trending** — AniList `TRENDING_DESC`.
- Independent per-shelf loading, error, and retry.
- Offline-first: each shelf's last successful list is cached in Room and shown
  on cold start; Room favorites are preserved via a real migration.

**Out of scope (recorded for the roadmap, not built here)**

- "See all" / full paginated browsing per shelf (a dedicated season/upcoming
  list screen). The shelf headers include a chevron affordance as a visual
  placeholder, but it is inert in this release.
- Weekly airing calendar and arbitrary `season/{year}` browsing.
- Any change to Discover, Favorites, Settings, or the details screen beyond
  navigation wiring.

## 3. Constraints and principles

- Each result-page load performs exactly one GraphQL HTTP request; shelves do
  not chain dependent requests.
- A failure in one shelf must not affect the others (roadmap acceptance
  criterion for R3).
- Valid cached content is never replaced by an empty or failed response.
- The four shelves refresh independently and in parallel on Home entry.
- All shelf requests send `isAdult = false`, matching the app-wide default.
- Room favorites must survive the schema change (a real `MIGRATION_3_4`, not a
  destructive reset).
- All user-facing text is bilingual: strings are added to both
  `res/values/strings.xml` (Portuguese, default) and `res/values-en/strings.xml`
  (English). No hardcoded strings.
- Each release uses TDD and must pass unit tests, compilation, and Detekt.

## 4. Navigation

`AnimeWikiNavHost.kt` is the only navigation file. Changes:

- Add `Tabs.HOME = "home"`.
- Add a `TabItem(Tabs.HOME, R.string.tab_home, Icons.Default.Home)` as the first
  entry in `tabs`.
- Add `composable(Tabs.HOME) { HomeScreen(onAnimeClick = onAnimeClick, onSettingsClick = onSettingsClick) }`.
- Change the inner `NavHost` `startDestination` from `Tabs.TOP` to `Tabs.HOME`.

Discover keeps `Tabs.TOP` and its `tab_discover` label. `onAnimeClick` and
`onSettingsClick` are the existing callbacks, reused as-is. The notification
deep link (`animewiki://details/{id}`) is unaffected — it targets the root
`details/{id}` route, not a tab.

## 5. Data layer

### 5.1 Shelf model

```kotlin
enum class HomeShelf { THIS_SEASON, UPCOMING, TOP, TRENDING }
```

A shelf's rendered content is a `List<Anime>` (the existing domain model,
already used by cards and details). No new domain fields are required for the
card itself; eyebrow text is derived (see 6.2).

### 5.2 Season resolution

A pure `SeasonResolver` computes the current and next AniList season from a
supplied year/month, with no dependency on the system clock (the clock is read
at the call site and passed in), so it is unit-testable:

- Boundaries: Winter = Jan–Mar, Spring = Apr–Jun, Summer = Jul–Sep,
  Fall = Oct–Dec.
- `next(FALL, year)` rolls over to `(WINTER, year + 1)`.
- Output maps to AniList `MediaSeason` (`WINTER`/`SPRING`/`SUMMER`/`FALL`) and
  an `Int` season year.

### 5.3 GraphQL

- New `SeasonAnime.graphql`: a `Page` query parameterized by `season`,
  `seasonYear`, `sort`, `isAdult`, `page`, `perPage`, returning the existing
  `animeCacheFields` fragment plus `nextAiringEpisode { episode airingAt }` for
  the "This season" eyebrow. One query serves three shelves via different args:
  - This season → `season = current`, `seasonYear = current`, `sort = POPULARITY_DESC`.
  - Upcoming → `season = next`, `seasonYear = next`, `sort = POPULARITY_DESC`,
    plus a not-yet-released constraint (`status: NOT_YET_RELEASED`).
  - Trending → no season constraint, `sort = TRENDING_DESC`.
- **Top** reuses the existing ranking data. Its shelf reads the first ~25 rows
  from the existing `anime` Room table (populated by `TopAnimeRemoteMediator`),
  so it is already offline-capable and adds no new network path.

`perPage` is capped at 25. Requests fetch a single page (no pagination).

### 5.4 Caching (Room, migration-safe)

- New entity `HomeShelfItemEntity` in a `home_shelf_item` table:
  primary key `(shelf, position)`, storing the anime fields needed to render a
  card and open details (mirrors the columns already in `AnimeEntity`). A
  `HomeShelfDao` supports observing a shelf, and replacing a shelf's rows in one
  transaction (delete-by-shelf then insert). This table holds only the three
  network-backed shelves (This season, Upcoming, Trending); **Top is not stored
  here** — it maps the existing `anime` ranking table and reuses its cache.
- Bump `AppDatabase` to **version 4**; register `MIGRATION_3_4` that runs
  `CREATE TABLE home_shelf_item (...)`. The existing
  `fallbackToDestructiveMigration(dropAllTables = true)` remains as a safety net
  for unforeseen version gaps, but the explicit 3→4 migration means an in-place
  upgrade preserves `anime`, `remote_key`, and `favorite` data.
- Enable `exportSchema = true` and add the Room schema directory so the
  migration test can validate the schema.
- The repository is **cache-first** per shelf: it emits the cached rows
  immediately, triggers a network refresh, and on success replaces only that
  shelf's rows in a transaction. A failed or empty refresh leaves the cache
  intact (Top follows the ranking's own cache-first behavior).

### 5.5 Repository surface (additions only)

`AnimeRepository` gains:

- `observeHomeShelf(shelf): Flow<List<Anime>>` — cache-backed stream. For Top,
  this maps the first N rows of the existing ranking table.
- `refreshHomeShelf(shelf)` — performs the one GraphQL request (or, for Top, a
  ranking refresh) and rewrites the shelf's cache; surfaces failure to the
  caller for per-shelf error state.

## 6. UI layer

### 6.1 Screen and state

- `HomeScreen` + `HomeViewModel`.
- `HomeViewModel` exposes one `StateFlow<HomeUiState>` where
  `HomeUiState` holds four independent `ShelfState` values:
  `ShelfState = Loading | Content(List<Anime>) | Error`. Each shelf observes its
  cache and drives its own refresh; a retry re-runs only that shelf.

### 6.2 Components

- `ShelfRow`: a section with a localized title, an inert chevron affordance
  (future "see all"), and a `LazyRow` of cards. Handles the three shelf states
  inline (skeleton row / cards / compact error+retry).
- `ShelfAnimeCard`: compact fixed-width card (~110dp, 2:3 poster, eyebrow +
  title + score), following the existing `DetailsMediaCard` pattern.
- **Eyebrow per shelf** (omitted when the underlying data is absent):
  - This season → next episode + weekday when `nextAiringEpisode` is present
    (e.g. "Ep 6 · Fri" / "Ep 6 · Sex").
  - Upcoming → premiere season + year (e.g. "Fall 2026" / "Outono 2026").
  - Top → rank (`#1`).
  - Trending → "Trending" / "Em alta".

Full metadata (status, studio, aired) continues to live on the details screen;
shelves stay compact.

### 6.3 Strings (bilingual)

New keys in both `values/strings.xml` and `values-en/strings.xml`:

| Key | en | pt |
| --- | --- | --- |
| `tab_home` | Home | Início |
| `home_shelf_this_season` | This season | Esta temporada |
| `home_shelf_upcoming` | Upcoming | Próximas estreias |
| `home_shelf_top` | Top | Top |
| `home_shelf_trending` | Trending | Em alta |
| `home_eyebrow_trending` | Trending | Em alta |
| `home_shelf_retry` | Couldn't load. Try again | Não carregou. Tente de novo |
| `home_eyebrow_episode` | Ep %1$d · %2$s | Ep %1$d · %2$s |
| `home_eyebrow_premiere` | %1$s %2$d | %1$s %2$d |

Season display names (Winter/Spring/Summer/Fall ↔ Inverno/Primavera/Verão/Outono)
and weekday abbreviations use string resources / platform localization; no
season or weekday name is hardcoded.

## 7. Error handling

- Per-shelf isolation: a network/GraphQL failure renders that `ShelfRow` in its
  Error state with a retry that re-runs only that shelf. The other shelves are
  unaffected.
- Offline / cold start: cache-first means each shelf shows its last successful
  list; a shelf with no cache shows Error + retry.
- Failures are classified as server problems (not "offline") via the existing
  `LoadErrorType` / `toLoadErrorType`.
- Malformed items are skipped without dropping valid siblings; a shelf whose
  refresh yields zero usable items keeps its previous cache.

## 8. Testing strategy (TDD)

- `SeasonResolverTest` — current/next season, and the Dec→(Winter, year+1)
  rollover.
- Shelf mapper tests — skip malformed/null entries without dropping valid ones;
  eyebrow derivation for each shelf type, including absent `nextAiringEpisode`.
- `HomeViewModelTest` — the four shelves load and fail independently; retry
  targets a single shelf; cache-first emission precedes refresh.
- Repository tests — cache-first emits cache then rewrites only the target
  shelf's rows; a failed refresh preserves the cache; Top maps ranking rows.
- **Room migration test** `MIGRATION_3_4` — `home_shelf_item` is created and
  existing `anime` / `remote_key` / `favorite` data survives.
- Detekt and a debug APK build. Final manual validation on the user's phone.

## 9. Acceptance criteria

- Home is the start destination; bottom bar shows Home · Discover · Favorites;
  Discover is unchanged.
- The four shelves page independently and refresh in parallel on entry.
- A failure in one shelf does not affect the others; each has its own retry.
- The latest successful list for each shelf remains available offline.
- Upgrading from schema v3 to v4 preserves favorites and the ranking cache.
- All new user-facing text is present in both the English and Portuguese string
  resources.

## 10. Later extensions (not in this release)

- Per-shelf "See all" opening a dedicated paginated list screen.
- `season/{year}` browsing and a weekly airing calendar.
