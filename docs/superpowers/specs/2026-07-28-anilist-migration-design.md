# Anime Wiki — AniList Backend Migration Design

**Date:** 2026-07-28
**Status:** Approved; implementation plan rewritten
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
4. **Content maturity:** AniList has no MAL-style rating (G/PG/PG-13/R+/Rx). The R1 age-rating selector is replaced by **"include adult content"**. Off sends `isAdult: false`; on omits `isAdult`, allowing both adult and non-adult results. It never sends `isAdult: true`, which would mean "adult-only". The same policy applies to ranking and search.
5. **Execution:** the release is an atomic backend swap behind the domain seam, developed incrementally on a branch. Jikan remains available only to keep intermediate commits compiling and is removed before merge. No mixed-provider build is shipped.
6. **Canonical id / local-data reset:** adopt the **AniList `Media.id`** as the canonical id. The only current user will perform a clean reinstall and accepts losing the existing ranking cache and favorites. As a safety net for any direct upgrade, bump Room from version 2 to 3; the already-configured destructive fallback recreates the database so MAL ids can never be interpreted as AniList ids.

## 4. Constraints and principles

- AniList is read-only for this app; favorites and personal state stay local (Room).
- Respect the limit advertised by AniList response headers. The documented normal limit is 90 requests/minute, but AniList may temporarily lower it (currently 30 requests/minute) and also applies a burst limiter. Prefer combined/batched queries, honor `Retry-After` and `X-RateLimit-Reset`, and request only fields the app uses.
- GraphQL fields are nullable by nature. Malformed or incomplete entries must be skipped without discarding valid siblings (same tolerance discipline already applied to the Jikan DTOs).
- The default ranking remains offline-first through Room after the provider-boundary database reset; a failed AniList refresh never replaces valid AniList-backed cached content with an empty result.
- Filtered/search feeds remain network-backed (as in R1).
- The Room entity schema does not change. The database version increments solely to force a destructive reset of provider-specific local data on direct upgrades; no custom Room migration or transition state is required.
- Every behavior change follows red-green TDD; before handoff, run the full unit suite, compile the app, run Detekt, and validate the flows on a device or emulator.

## 5. Architecture and seam

The **domain layer is the stable contract.** UI, ViewModel, and Room depend only on it, so the backend swap is confined below it.

```
UI (Compose)  →  TopAnimeViewModel  →  AnimeRepository  ── stable seam ──┐
                                                                         │
        ┌──────────────── below the seam: this migration ───────────────┘
        │
  Apollo (AniList) → response policy ─┬─→ TopAnimeRemoteMediator → Room  [offline-first ranking]
                                      ├─→ AnimeSearchPagingSource         [network-backed search/filters]
                                      ├─→ getAnimeDetails(id)
                                      └─→ getAnimeGenres() → memory cache
  Room v3 (favorites) ──────────────────→ local-only, using AniList ids
```

`AnimeRepository` keeps its method shapes:
- `topAnime(): Flow<PagingData<Anime>>`
- `searchAnime(criteria: AnimeBrowseCriteria): Flow<PagingData<Anime>>`
- `getAnimeDetails(id: Int): Anime?`
- `getAnimeGenres(forceRefresh: Boolean = false): List<AnimeGenre>`

Apollo responses are interpreted in one shared policy before repositories and paging components consume them. The policy distinguishes transport failures (`response.exception`), GraphQL failures (`response.errors`), and usable data. Partial data is accepted when the operation still contains valid sibling items; an operation with no usable data is a server failure.

## 6. Components

### 6.1 Add

- Apollo Gradle plugin and `com.apollographql.apollo:apollo-runtime` in the version catalog and `app/build.gradle.kts`.
- AniList `schema.graphqls` (downloaded via the Apollo plugin) under the configured Apollo service directory.
- GraphQL operations:
  - `TopAnime.graphql` — `Page.media(type: ANIME, sort: SCORE_DESC)` with pagination and the complete field set persisted in `AnimeEntity`, so opening a cached ranking item offline retains today's detail parity.
  - `SearchAnime.graphql` — `Page.media(type: ANIME, search:, format:, genre_in:, isAdult:, sort:)`.
  - `AnimeDetails.graphql` — `Media(id:, type: ANIME)` with the full field set the details screen renders.
  - `GenreCollection.graphql` — aliases the schema field as `genres: GenreCollection` (returns `[String]`).
- `di/AniListModule.kt` — provides `ApolloClient` built on an OkHttp client with the logging interceptor and the retry/backoff interceptor (§8).
- Mappers from Apollo-generated types to domain (`Anime`, `AnimeGenre`) and to `AnimeEntity`.
- `RetryInterceptor` (OkHttp) — retries HTTP 429 and 5xx with exponential backoff; unit-tested.
- A shared Apollo response policy that handles transport exceptions, GraphQL errors, partial data, and missing usable data consistently.
- HTML-stripping helper for AniList `description` (§6.2).

### 6.2 Modify

- `data/repository/AnimeRepository.kt` — call AniList through Apollo; keep the Room `Pager` + `RemoteMediator` for the ranking and the network-backed search `PagingSource`.
- `data/paging/TopAnimeRemoteMediator.kt` and `data/paging/AnimeSearchPagingSource.kt` — fetch via Apollo; map results; skip invalid entries.
- `domain/model/AnimeFilters.kt` — `genreIds: Set<Int>` → `genres: Set<String>`; remove `rating: AnimeAgeRating`; add `includeAdultContent: Boolean` (default `false`). Update `activeCount`, `isEmpty`, and criteria/query translation accordingly.
- `domain/model/AnimeGenre.kt` — identified by name (AniList `GenreCollection` returns only names; no id/count).
- `domain/model/AnimeFormat.kt` — `apiValue` becomes the AniList `MediaFormat` wire value (`TV`, `MOVIE`, `OVA`, `ONA`, `SPECIAL`, `MUSIC`).
- `domain/model/AnimeAgeRating.kt` — **removed** (replaced by the `includeAdultContent` boolean).
- `ui/screens/topAnime/components/AnimeFilterSheet.kt`, `AnimeFilterBar.kt`, `AnimeFilterLabels.kt` — replace the rating selector with the adult-content toggle; genre selection keyed by name.
- `ui/screens/topAnime/TopAnimeViewModel.kt` — filter state uses `includeAdultContent` and string genres; behavior otherwise unchanged.
- `data/notification/TopAnimeSyncWorker.kt` — fetch the #1 anime via the AniList top query.
- `data/local/AppDatabase.kt` — bump the Room version from 2 to 3. `DatabaseModule` already uses `fallbackToDestructiveMigration(dropAllTables = true)`, so a direct upgrade clears ranking, remote keys, and favorites before AniList ids are stored.
- `di/NetworkModule.kt` — remove Retrofit/Jikan providers after the Apollo cutover.
- `data/mapper/AnimeMapper.kt`, `AnimeGenreMapper.kt` — map from Apollo types; keep the entity mapping for Room.
- `res/values/strings.xml`, `res/values-en/strings.xml` — adult-content toggle labels; remove age-rating strings.
- `README.md` — document the AniList backend and the parity scope.

### 6.3 Data mapping details

| Domain field | AniList `Media` source | Note |
|---|---|---|
| `id` | `id` | Canonical id is the AniList id. |
| `title` | first non-blank of `title.english`, `title.romaji` | Reject the item if both are null or blank. |
| `imageUrl` | first non-blank of `coverImage.extraLarge`, `coverImage.large` | Reject the item if both are null or blank. |
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
| `rating` | no equivalent field | Store null; the existing optional details row is omitted. Content maturity remains available through `isAdult` for filtering only. |

**Top ranking:** Jikan `/top/anime` is replaced by `Page.media(type: ANIME, sort: SCORE_DESC)`, which mirrors a highest-rated ranking. Its reusable cache/detail fragment includes every field currently persisted by `TopAnimeRemoteMediator` that AniList can supply (`synopsis`, genres, studios, aired dates, status, duration, rank, and trailer), not only card fields.

**Search + filters:** `media(type: ANIME, search: $q, format: $format, genre_in: $genres, isAdult: $adult, sort: SCORE_DESC)`. The `search` argument is omitted when the query is blank (filter-only browse); each optional filter is omitted when unset. With adult content disabled, `$adult` is `false`; with it enabled, the `isAdult` argument itself is omitted. Ranking follows the same rule.

**Pagination:** AniList wraps lists in `Page { pageInfo { currentPage, lastPage, hasNextPage, total } media { ... } }`. `hasNextPage` drives the paging `nextKey`, exactly as the current `pagination.has_next_page` does.

**Provider-boundary reset:** uninstalling the current app clears its Room database naturally. For a direct upgrade, opening Room version 3 from version 2 invokes the existing destructive fallback and recreates all tables. Ranking rows, remote keys, and favorites from the Jikan build are intentionally discarded together. New favorites always use AniList ids. This avoids an id-namespace collision without migration code or launch-time network access.

### 6.4 Remove

- `data/remote/JikanApi.kt` and `data/remote/dto/*` (Jikan DTOs).
- Retrofit / kotlinx-serialization-converter dependencies, once no code references them. (kotlinx.serialization may remain if still used elsewhere; Retrofit is removed.)

Room entities, DAOs, and the user-facing favorites behavior remain unchanged. Only the database version changes.

## 7. Error handling

- A shared response policy inspects every Apollo response. `response.exception` represents transport/protocol failure; `response.errors` represents GraphQL field errors. Valid partial data may be consumed and invalid siblings skipped, but errors with no usable operation data become a typed server failure.
- Extend the existing `ui/common/LoadErrorType.toLoadErrorType()` classifier: `ApolloNetworkException` (wrapping `IOException`) → `NO_CONNECTION`; typed GraphQL/HTTP/protocol failures → `SERVER`. The offline/server banner and full-screen states remain unchanged.
- Mapping is tolerant: mappers return null for entries missing an essential field (id, non-blank title, or non-blank image) and those entries are filtered out without crashing a whole page.

## 8. Rate limiting and retry

- Add a `RetryInterceptor` to the OkHttp client that backs Apollo. It retries HTTP **429** and retryable **5xx** responses, closing every intermediate response. For 429 it honors `Retry-After` first and `X-RateLimit-Reset` when present; otherwise it uses exponential backoff with jitter (base 1s, capped at 3 retries). Retryable 5xx responses use the same bounded fallback.
- Only read-only GraphQL queries are retried. Keeping query field sets focused reduces request pressure before retry is needed.
- AniList may temporarily lower or suspend its normal capacity, so code must use server-provided timing rather than assuming 90 requests/minute.

## 9. Testing strategy

Red-green TDD throughout. Focused tests per unit:

- **Apollo contract spike (Task 0):** before production implementation, prove the Apollo 5 Gradle schema task, generated accessors/data builders, and the current mock-server or `TestNetworkTransport` artifact/API in a minimal test. This removes version/API assumptions from the implementation plan.
- **Mappers:** Apollo type → domain and → `AnimeEntity`, with fixtures for null/blank required fields and HTML in `description`. Verify that the top-ranking mapping preserves every field currently available from the Room-backed details flow.
- **Response policy:** data-only success, usable partial data plus GraphQL errors, errors with no usable data, and `response.exception`.
- **Queries / PagingSource:** the proven Apollo test transport asserts outgoing variables (`search`, `format`, `genre_in`, `isAdult`, `page`), argument omission when adult content is included, and pagination (`hasNextPage` → `nextKey`).
- **Repository:** genre catalog mapping and in-memory cache; criteria → query-variable translation.
- **Database reset:** Room version is 3 and the existing destructive-fallback configuration is retained. A migration test or instrumentation check verifies that opening a representative version-2 database recreates empty `anime`, `remote_keys`, and `favorites` tables.
- **ViewModel:** filter state with string genres and `includeAdultContent`; existing query/debounce behavior preserved.
- **Error classifier:** Apollo exceptions → `LoadErrorType`.
- **Retry interceptor:** success passthrough, retry-then-success, give-up after max retries, non-retryable status passthrough, response closing, `Retry-After`, `X-RateLimit-Reset`, and jittered fallback.
- **Verification:** automated tests use the transport proven by Task 0; a manual smoke test runs against the live (auth-free) AniList API and on an emulator.

## 10. Scope boundaries (out of scope)

- No roadmap features (R2–R11): no manga, voice actors, relations, recommendations, characters/staff, episodes, streaming, seasons, or roulette. These get their own specs later and will benefit from AniList's richer graph.
- No Apollo normalized cache adoption; Room remains the cache/persistence layer.
- No account login / AniList OAuth; favorites stay local.
- No UI redesign; UI changes are limited to the adult-content toggle and name-based genres.
- No second-provider fallback. This migration removes the known Jikan dependency and keeps the domain seam replaceable, but no external provider can guarantee permanent availability.

## 11. Risks and notes

- **Ranking definition shift:** AniList `SCORE_DESC` is not identical to MAL's top ranking, so the exact order of the Discover list will differ. This is expected and acceptable at parity.
- **`description` HTML:** AniList descriptions contain HTML/markup; the strip helper must be covered by tests to avoid leaking tags into the UI.
- **Intentional local-data loss:** a direct upgrade from the Jikan build deletes cached ranking data and favorites. This is explicitly accepted because the app currently has one user who will perform a clean reinstall; the version bump protects accidental direct upgrades.
- **Id namespace collision:** MAL and AniList both use integers, but the same number can identify different titles. The destructive Room reset prevents legacy ids from surviving the provider boundary.
- **Apollo codegen in CI:** the build gains a schema/codegen step; the plan must ensure the schema is committed so builds are reproducible offline.
- **Rate limit during paging:** AniList's currently degraded limit makes server-directed retry mandatory; rapid scroll can still encounter the burst limiter.
- **Provider longevity:** AniList removes the announced Jikan sunset from this app's critical path, but it remains an external dependency. Committed schema/operations and the domain seam reduce the cost of a future provider change.

## 12. Planning boundary

This document defines the migration design. After design approval, the implementation plan is rewritten task-by-task with red-green TDD and commits in this order:

1. Apollo 5 contract spike and test-tooling proof.
2. Dependency, committed schema, fragments, and operations setup.
3. Shared Apollo response policy and rate-limit retry.
4. Domain/filter semantics and the minimal filter UI change.
5. Tolerant mappers with full ranking-cache detail parity.
6. Search/paging cutover.
7. Ranking, details, genres, and notification cutover.
8. Room version-3 destructive provider-boundary reset.
9. DI cutover, Jikan/Retrofit removal, documentation, and full automated/manual verification.
