# Anime Wiki — AniList Backend Migration Design

**Date:** 2026-07-28
**Status:** Approved design; implementation plan to be written next
**Product:** Anime Wiki Android app
**Scope:** Feature parity migration of the current data backend from the Jikan REST API (unofficial MyAnimeList) to the AniList GraphQL API.

## 1. Objective

Replace the app's remote data source (Jikan `/v4`, REST) with the AniList GraphQL API, at **feature parity** with what ships today, so the app no longer depends on the public `api.jikan.moe` host or on MyAnimeList upstream stability.

Everything currently shipped must keep working with the same user experience: the Discover ranking, text search, the R1 filters (format, genre, and — redefined — content maturity), anime details, local favorites, and the weekly top-anime notification. No roadmap features (R2–R11) are added here.

## 2. Why AniList

- Free public read access with **no API key** for the data this app needs (search, browse, details, genres). OAuth is only required for user-account mutations, which the app does not use — favorites remain local.
- Independent of MyAnimeList's availability and actively maintained.
- One GraphQL request can fetch related data (relations, characters, staff), which future roadmap releases (R4, R6, R7, R10, R11) can all build on.
- Endpoint: `POST https://graphql.anilist.co`.

## 3. Locked decisions

These were agreed during brainstorming and are fixed for this migration:

1. **Scope:** feature parity only. No new screens or roadmap features.
2. **GraphQL client:** Apollo Kotlin (typed codegen from the schema + `.graphql` operations, backed by OkHttp).
3. **Offline cache:** keep Room. Apollo is the network layer only. Room continues to store favorites and the offline-first ranking cache; the existing `RemoteMediator` is preserved and simply fed by Apollo.
4. **Age rating:** AniList has no MAL-style rating (G/PG/PG-13/R+/Rx). The R1 age-rating filter is replaced by a single **"adult content" toggle** mapped to AniList's `isAdult`.
5. **Execution:** big-bang swap behind the domain seam, on a branch. The domain models and `AnimeRepository` shape stay stable; only `data/remote` (and the filter domain/UI touched by decisions 4 and the genre change) changes.
6. **Canonical id / favorites continuity:** adopt the **AniList `Media.id`** as the canonical id (always present, no cross-namespace collision), plus a **one-time favorites migration** keyed on `idMal` (see §6.3).

## 4. Constraints and principles

- AniList is read-only for this app; favorites and personal state stay local (Room).
- Respect AniList's rate limit (~90 requests/minute plus a short-window burst limiter that responds with HTTP 429). Prefer single combined queries over many small ones; request only the fields the UI renders.
- GraphQL fields are nullable by nature. Malformed or incomplete entries must be skipped without discarding valid siblings (same tolerance discipline already applied to the Jikan DTOs).
- The default ranking remains offline-first through the existing Room cache; a failed refresh never replaces valid cached content with an empty result.
- Filtered/search feeds remain network-backed (as in R1).
- The Room schema does not change: AniList values are mapped to fit the current `AnimeEntity`/`FavoriteEntity` columns, so no Room migration is required (except the favorites id rewrite in §6.3, which is a data migration, not a schema change).
- Every behavior change follows red-green TDD; before handoff, run the full unit suite, compile the app, run Detekt, and validate the flows on a device or emulator.

## 5. Architecture and seam

The **domain layer is the stable contract.** UI, ViewModel, and Room depend only on it, so the backend swap is confined below it.

```
UI (Compose)  →  TopAnimeViewModel  →  AnimeRepository  ── stable seam ──┐
                                                                         │
        ┌──────────────── below the seam: this migration ───────────────┘
        │
  Apollo (AniList)  ─┬─→  TopAnimeRemoteMediator  →  Room (AnimeEntity)  [offline-first ranking]
                     ├─→  AnimeSearchPagingSource  →  domain            [network-backed search/filters]
                     ├─→  getAnimeDetails(id)       →  domain
                     └─→  getAnimeGenres()          →  in-memory cache
  Room (favorites)  ── unchanged, local only ──
```

`AnimeRepository` keeps its method shapes:
- `topAnime(): Flow<PagingData<Anime>>`
- `searchAnime(criteria: AnimeBrowseCriteria): Flow<PagingData<Anime>>`
- `getAnimeDetails(id: Int): Anime`
- `getAnimeGenres(forceRefresh: Boolean = false): List<AnimeGenre>`

## 6. Components

### 6.1 Add

- Apollo Gradle plugin and `com.apollographql.apollo:apollo-runtime` in the version catalog and `app/build.gradle.kts`.
- AniList `schema.graphqls` (downloaded via the Apollo plugin) under the configured Apollo service directory.
- GraphQL operations:
  - `TopAnime.graphql` — `Page.media(type: ANIME, sort: SCORE_DESC)` with pagination.
  - `SearchAnime.graphql` — `Page.media(type: ANIME, search:, format:, genre_in:, isAdult:, sort:)`.
  - `AnimeDetails.graphql` — `Media(id:, type: ANIME)` with the full field set the details screen renders.
  - `GenreCollection.graphql` — `GenreCollection` (returns `[String]`).
- `di/AniListModule.kt` — provides `ApolloClient` built on an OkHttp client with the logging interceptor and the retry/backoff interceptor (§8).
- Mappers from Apollo-generated types to domain (`Anime`, `AnimeGenre`) and to `AnimeEntity`.
- `RetryInterceptor` (OkHttp) — retries HTTP 429 and 5xx with exponential backoff; unit-tested.
- A one-time favorites migration component (§6.3).
- HTML-stripping helper for AniList `description` (§6.2).

### 6.2 Modify

- `data/repository/AnimeRepository.kt` — call AniList through Apollo; keep the Room `Pager` + `RemoteMediator` for the ranking and the network-backed search `PagingSource`.
- `data/paging/TopAnimeRemoteMediator.kt` and `data/paging/AnimeSearchPagingSource.kt` — fetch via Apollo; map results; skip invalid entries.
- `domain/model/AnimeFilters.kt` — `genreIds: Set<Int>` → `genres: Set<String>`; remove `rating: AnimeAgeRating`; add `adultContent: Boolean` (default `false`). Update `activeCount`, `isEmpty`, and the criteria/query building accordingly.
- `domain/model/AnimeGenre.kt` — identified by name (AniList `GenreCollection` returns only names; no id/count).
- `domain/model/AnimeFormat.kt` — `apiValue` becomes the AniList `MediaFormat` wire value (`TV`, `MOVIE`, `OVA`, `ONA`, `SPECIAL`, `MUSIC`).
- `domain/model/AnimeAgeRating.kt` — **removed** (replaced by the `adultContent` boolean).
- `ui/screens/topAnime/components/AnimeFilterSheet.kt`, `AnimeFilterBar.kt`, `AnimeFilterLabels.kt` — replace the rating selector with the adult-content toggle; genre selection keyed by name.
- `ui/screens/topAnime/TopAnimeViewModel.kt` — filter state uses `adultContent` and string genres; behavior otherwise unchanged.
- `data/notification/TopAnimeSyncWorker.kt` — fetch the #1 anime via the AniList top query.
- `di/NetworkModule.kt` — provide `ApolloClient`; remove Retrofit/Jikan providers.
- `data/mapper/AnimeMapper.kt`, `AnimeGenreMapper.kt` — map from Apollo types; keep the entity mapping for Room.
- `res/values/strings.xml`, `res/values-en/strings.xml` — adult-content toggle labels; remove age-rating strings.
- `README.md` — document the AniList backend and the parity scope.

### 6.3 Data mapping details

| Domain field | AniList `Media` source | Note |
|---|---|---|
| `id` | `id` | Canonical id is the AniList id. |
| `title` | `title.english ?: title.romaji` | Fall back to romaji when English is absent. |
| `imageUrl` | `coverImage.extraLarge ?: coverImage.large` | |
| `score` (0–10 Double) | `averageScore` (0–100 Int) | Divide by 10.0; null → null. |
| `episodes` | `episodes` | |
| `type` | `format` (`MediaFormat`) | Map enum to display string. |
| `year` | `seasonYear ?: startDate.year` | |
| `synopsis` | `description` | **Strip HTML tags** before storing/showing. |
| `genres` | `genres` (`[String]`) | |
| `studios` | `studios.nodes[].name` | |
| `status` | `status` (`MediaStatus`) | Map enum to display string. |
| `aired` | `startDate` / `endDate` | Format to the existing display string. |
| `duration` | `duration` (minutes) | Format to the existing display string. |
| `rank` | `rankings` (rated, allTime) | Optional; null when absent. |
| `trailerYoutubeId` | `trailer { id site }` | Use `id` only when `site == "youtube"`. |

**Top ranking:** Jikan `/top/anime` is replaced by `Page.media(type: ANIME, sort: SCORE_DESC)`, which mirrors a highest-rated ranking.

**Search + filters:** `media(type: ANIME, search: $q, format: $format, genre_in: $genres, isAdult: $adult, sort: SCORE_DESC)`. The `search` argument is omitted when the query is blank (filter-only browse); each filter argument is omitted when unset.

**Pagination:** AniList wraps lists in `Page { pageInfo { currentPage, lastPage, hasNextPage, total } media { ... } }`. `hasNextPage` drives the paging `nextKey`, exactly as the current `pagination.has_next_page` does.

**Favorites continuity (one-time migration):** existing `FavoriteEntity` rows are keyed by MAL id. On the first launch of the migrated build, for each existing favorite the app queries `Media(idMal: <oldId>, type: ANIME) { id title ... }`, rewrites the row with the AniList id and refreshed denormalized fields, and marks the migration done via a DataStore flag. Favorites whose `idMal` is not found on AniList are dropped. If the device is offline at first launch, the migration is deferred and retried on a later launch (the flag is only set once it completes). New favorites always use the AniList id.

### 6.4 Remove

- `data/remote/JikanApi.kt` and `data/remote/dto/*` (Jikan DTOs).
- Retrofit / kotlinx-serialization-converter dependencies, once no code references them. (kotlinx.serialization may remain if still used elsewhere; Retrofit is removed.)

Room entities, DAOs, and the favorites feature are otherwise unchanged.

## 7. Error handling

- Extend the existing `ui/common/LoadErrorType.toLoadErrorType()` classifier to Apollo exceptions: `ApolloNetworkException` (wrapping `IOException`) → `NO_CONNECTION`; GraphQL errors and non-2xx HTTP → `SERVER`. The offline/server banner and full-screen states from the previous work remain unchanged.
- Mapping is tolerant: mappers return null for entries missing an essential field (id, title, or image) and those are filtered out, never crashing a whole page.

## 8. Rate limiting and retry

- Add a `RetryInterceptor` to the OkHttp client that backs Apollo. It retries HTTP **429** and **5xx** with exponential backoff (base 1s, doubling, capped at 3 retries), closing each intermediate response. Only idempotent GET/POST-query traffic is involved (AniList is read-only here), so retries are safe.
- This is the retry/backoff resilience previously discussed, placed at the correct layer for AniList's burst limiter.

## 9. Testing strategy

Red-green TDD throughout. Focused tests per unit:

- **Mappers:** Apollo type → domain and → `AnimeEntity`, with fixtures for null/missing fields and HTML in `description`. Use Apollo's generated test/data builders.
- **Queries / PagingSource:** Apollo `MockServer` (or `TestNetworkTransport`) asserting the outgoing variables (`search`, `format`, `genre_in`, `isAdult`, `page`) and pagination (`hasNextPage` → `nextKey`).
- **Repository:** genre catalog mapping and in-memory cache; criteria → query-variable translation; favorites migration by `idMal` (found, not-found, offline-deferred).
- **ViewModel:** filter state with string genres and the adult-content toggle; existing query/debounce behavior preserved.
- **Error classifier:** Apollo exceptions → `LoadErrorType`.
- **Retry interceptor:** success passthrough, retry-then-success, give-up after max retries, exponential delays, non-retryable status passthrough, 429 retried.
- **Verification:** automated tests use `MockServer`; a manual smoke test runs against the live (auth-free) AniList API and on an emulator.

## 10. Scope boundaries (out of scope)

- No roadmap features (R2–R11): no manga, voice actors, relations, recommendations, characters/staff, episodes, streaming, seasons, or roulette. These get their own specs later and will benefit from AniList's richer graph.
- No Apollo normalized cache adoption; Room remains the cache/persistence layer.
- No account login / AniList OAuth; favorites stay local.
- No UI redesign; the filter sheet changes only where the rating selector becomes the adult-content toggle and genres become name-based.

## 11. Risks and notes

- **Ranking definition shift:** AniList `SCORE_DESC` is not identical to MAL's top ranking, so the exact order of the Discover list will differ. This is expected and acceptable at parity.
- **`description` HTML:** AniList descriptions contain HTML/markup; the strip helper must be covered by tests to avoid leaking tags into the UI.
- **`idMal` gaps:** a small number of AniList entries lack `idMal`; the favorites migration drops any existing favorite that cannot be matched. Mainstream titles are unaffected.
- **Apollo codegen in CI:** the build gains a schema/codegen step; the plan must ensure the schema is committed so builds are reproducible offline.
- **Rate limit during paging:** rapid scroll could approach the burst limit; the existing append delay plus the retry interceptor mitigate this.

## 12. Planning boundary

This document defines the migration design. The implementation plan (task-by-task, TDD, with commits) is written next via the writing-plans skill and will sequence the work as: dependency/codegen setup → domain/filter model changes → mappers → queries and paging → repository and genres → notification worker → filter UI (adult toggle) → favorites migration → DI swap and Jikan removal → documentation and full verification.
