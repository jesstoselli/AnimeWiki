# Anime Wiki — Product Roadmap Design

**Date:** 2026-07-22
**Status:** Approved roadmap; each release requires its own implementation plan
**Product:** Anime Wiki Android app

## 1. Objective

Evolve Anime Wiki from a sample centered on top anime, search, details, favorites, and weekly notifications into a personal discovery app for anime, manga, and voice actors.

Development will be incremental. Every release must remain useful on its own, preserve offline behavior, and avoid coupling local user data to remote caches.

## 2. Constraints and principles

- Jikan is read-only. Favorites and any future personal state remain local.
- The public API permits up to 3 requests per second and 60 per minute.
- Jikan depends on MyAnimeList and can return intermittent upstream errors.
- Remote data can be missing or null; one malformed entry must not invalidate an entire feed.
- Secondary sections load independently and must not block primary content.
- Cache entries are identified by resource, feed, page, and active filters.
- Valid cached content is never replaced by an empty or failed response.
- Each release uses TDD and must pass unit tests, compilation, and Detekt.

## 3. Product and navigation direction

The current **Top** area evolves into **Discover**. Initially it contains ranking, search, and filters. Later it receives current-season, upcoming, and roulette modes.

The target navigation after the final roadmap phase is:

1. Discover
2. Manga
3. Voice Actors
4. Favorites

Settings remain accessible from the top app bar. The exact final bottom-navigation layout will be validated when Manga and Voice Actors are designed, because Android navigation should reflect the content available at that time rather than reserve empty destinations early.

## 4. Release roadmap

### R1 — Main listing filters: format, rating, and genre

**Goal:** make the main listing a practical discovery surface.

**User experience**

- Rename Top to Discover.
- Add a Filters action and active-filter chips.
- Support format, age rating, and genre.
- Provide Clear all and an explicit Apply action.
- Preserve filters when navigating to details and back.
- Combine text search and filters in one request state.

**Data behavior**

- Continue using `/top/anime` for the unfiltered ranking.
- Use `/anime` for filtered or text-search results.
- Obtain genres from `/genres/anime`.
- Model filters in a single immutable `AnimeFilters` value.
- Derive cache identity from the complete normalized filter set and query.

**Acceptance criteria**

- Filters can be combined and survive paging.
- Clear all restores the default ranking.
- A cached page is never displayed for another filter combination.
- Search, loading, empty, offline, and server-error states remain distinct.

### R2 — Ordering and organization browsing

**Goal:** complete advanced discovery using AniList semantics without turning one
user action into a chain of dependent network requests.

**User experience**

- Add ordering by popularity, score, title, or release date where supported.
- Add an organization browsing mode that presents animation studios and other
  production companies separately, using AniList's `Studio.isAnimationStudio`
  distinction.
- Selecting an organization opens its paginated works in Discover.
- Organization browsing is a distinct mode rather than a filter combined with
  query, genre, format, and adult-content controls. Incompatible controls are
  hidden or cleared when entering this mode.

**Data behavior**

- Use AniList `Studio`/`Page.studios` for organization discovery and
  `Studio.media` for the selected organization's works.
- Use `isAnimationStudio` rather than names to distinguish animation studios
  from other production companies.
- Each result-page load performs exactly one GraphQL HTTP request. Do not first
  fetch media IDs and then issue a second request to apply unrelated media
  filters.
- Studio search/catalog loading is a separate user action and request; selecting
  an organization starts the independently paginated works feed.
- Do not use AniList's `licensedBy`/`licensedById` arguments as studio filters:
  they represent online license providers, not production organizations.
- Keep organization and non-default ordered feeds network-backed in the first
  delivery, matching the existing search/filter behavior. Add bounded caching
  later only if real usage shows it is valuable.

**Acceptance criteria**

- Animation-studio and production-company labels match AniList's explicit
  `isAnimationStudio` value.
- Ordering remains stable across pages in regular discovery and organization
  browsing.
- Every organization works page is loaded with one GraphQL HTTP request.
- Entering and leaving organization mode never leaves hidden regular filters
  accidentally active.
- Switching organization or ordering creates a fresh paging source and never
  reuses results from the previous selection.

### R3 — Current season and upcoming premieres

**Goal:** make Discover useful for regularly checking what is airing and what comes next.

**User experience**

- Add Discover modes: Ranking, Current Season, and Upcoming.
- Highlight season/year, premiere date, airing day, status, studio, and episodes when available.
- Reuse anime cards and details navigation.

**Data behavior**

- Use `/seasons/now` and `/seasons/upcoming`.
- Maintain separate caches for ranking, current season, and upcoming.
- Allow manual refresh without discarding valid cached content on failure.

**Later extension**

- Browse `/seasons/{year}/{season}`.
- Add a weekly airing calendar.

**Acceptance criteria**

- Current and upcoming feeds page independently.
- The latest successful feed remains available offline.
- A failure in one mode does not affect the others.

### R4 — Contextual recommendations

**Goal:** help users find a next anime from the title they are already viewing.

**User experience**

- Add a You may also like section to anime details.
- Show a compact horizontal list with image, title, and recommendation votes.
- Open the recommended anime's details on selection.

**Data behavior**

- Use `/anime/{id}/recommendations`.
- Load recommendations after primary details.
- Cache by source anime ID.
- Remove duplicates and the source anime; prioritize entries with more votes.
- Hide the section when the response is empty.

**Later extension**

- Add a community recommendation feed using `/recommendations/anime` only after contextual recommendations prove useful.

**Acceptance criteria**

- Recommendation failure does not break details.
- Cached recommendations remain visible offline.
- Duplicate, malformed, or self-referential entries are ignored.

### R5 — Anime roulette

**Goal:** provide a lightweight way to discover something unexpected.

**First delivery**

- Use `/random/anime`.
- Add a roulette action in Discover.
- Show a result card with View details, Favorite, and Spin again.
- Avoid repeating the current result when another valid result is returned.

**Filtered extension**

- Reuse R1/R2 filters with `/anime`.
- Choose a valid result page and select an entry locally.
- Support genre, format, rating, minimum score, and status where available.

**Acceptance criteria**

- One spin does not create an uncontrolled request loop.
- Failure preserves the previous valid result and offers retry.
- The simple roulette ships before filter-aware roulette.

### R6 — Details: franchises and continuations

**Goal:** expose the relationship between entries without claiming a complete franchise graph.

**User experience**

- Add a Related titles section grouped by relation type: prequel, sequel, spin-off, side story, adaptation, and others.
- Open anime relations in anime details and manga relations in manga details once the manga area exists.

**Data behavior**

- Use `/anime/{id}/relations`.
- Cache by anime ID.
- Treat the returned relations as the relations exposed by the source page, not a complete franchise tree.

**Acceptance criteria**

- Relation type and media type are explicit.
- Unsupported relation targets remain non-crashing and clearly labeled.

### R7 — Details: characters, cast, and staff

**Goal:** connect anime to its principal characters, voice cast, and relevant production staff.

**User experience**

- Show main characters first and provide View all.
- Initially prioritize Japanese voice actors while preserving language data for future filtering.
- Show a concise staff section for major roles.

**Data behavior**

- Use `/anime/{id}/characters` and `/anime/{id}/staff`.
- Load sections independently and cache by anime ID.

**Acceptance criteria**

- Character and staff failures do not affect details or each other.
- Selecting a voice actor can navigate to the Voice Actors area once R11 exists.

### R8 — Details: episodes

**Goal:** expose episode metadata without turning Anime Wiki into a playback tracker.

**User experience**

- Show episode number, titles, air date, and filler/recap flags when supplied.
- Paginate long-running shows.

**Data behavior**

- Use `/anime/{id}/episodes` and `/anime/{id}/episodes/{episode}`.
- Cache by anime ID and page.

**Acceptance criteria**

- Long episode lists page correctly.
- Missing titles or dates display graceful fallbacks.
- No personal progress tracking is implied by this release.

### R9 — Details: where to watch

**Goal:** surface streaming links provided by the data source.

**User experience**

- Add an Available links section.
- Open external links through an explicit user action.
- Do not claim regional availability or completeness.

**Data behavior**

- Use `/anime/{id}/streaming`.
- Validate URLs before exposing them.

**Acceptance criteria**

- Empty streaming data hides the section.
- Invalid links are ignored.
- Copy communicates that availability can vary by region.

### R10 — Manga area

**Goal:** introduce manga as a first-class media area without forcing anime and manga into one oversized domain model.

**First delivery**

- Manga destination.
- Top manga, search, details, and manga favorites.
- Manga-specific fields such as chapters, volumes, publication status, authors, and serialization.

**Later delivery**

- Filters, recommendations, relations, characters, and manga roulette.

**Architecture**

- Share generic visual primitives only where semantics match.
- Keep anime and manga domain models and repositories separate.
- Allow relations to navigate across media types.

**Acceptance criteria**

- Anime and manga caches and favorites remain isolated.
- Cross-media navigation is explicit and type-safe.

### R11 — Voice Actors area

**Goal:** make voice actors discoverable as people with careers and roles, not only nested metadata.

**User experience**

- Voice Actors destination with search and popular people.
- Person details with image, biography, roles, characters, and associated anime.
- Navigation between anime, characters, and voice actors.

**Data behavior**

- Use `/people`, `/people/{id}`, `/people/{id}/full`, `/people/{id}/voices`, `/people/{id}/anime`, and `/people/{id}/pictures` as applicable.
- Preserve the distinction between a person, a character, and a voice role.

**Acceptance criteria**

- Search and details tolerate missing biographies and images.
- Voice roles preserve language and character/anime identity.
- Navigation does not confuse person IDs, character IDs, and anime IDs.

## 5. Details-screen loading strategy

The expanded details screen is section-based:

1. Information and synopsis
2. Franchise relations
3. Recommendations
4. Characters and staff
5. Episodes
6. Available streaming links

Primary details load first. Secondary sections load only when needed, have independent state, and use independent caches. No secondary error replaces the primary details screen.

### Expandable-section interaction

The details expansion begins with synopsis, information, related titles, and
contextual recommendations. Cover, title, score, rank, format, year, genres,
and favorite action remain permanently visible.

- Synopsis and Information start expanded.
- Related titles, Recommendations, and future secondary sections start collapsed.
- Each section expands and collapses independently.
- Expansion state is retained while the user remains on the current anime.
- Empty secondary sections are omitted instead of rendering empty accordions.
- The same reusable section component will later host characters, episodes,
  and streaming links so the screen does not become an unstructured long page.

For the first upgraded delivery, AniList relations and recommendations are
included in the existing anime-details GraphQL request. This avoids extra
network calls while preserving cache-first fallback for the primary details.

## 6. Testing strategy

Each release includes:

- Serialization tests with complete, null, missing, empty, and malformed fixtures.
- Mapper tests that skip invalid entries without dropping valid siblings.
- Repository tests for request parameters, cache keys, refresh, and failure fallback.
- Paging tests where pagination is involved.
- ViewModel tests for filter preservation and independent section state.
- Room migration tests for every schema change.
- Focused Compose or instrumented tests for critical user flows.

Before release, run the complete unit test suite, compile the debug app, run Detekt, and manually validate the affected flow on a device or emulator.

## 7. Delivery order and relative size

| Release | Feature | Relative size |
| --- | --- | --- |
| R1 | Format, rating, and genre filters | Medium |
| R2 | Ordering and organization browsing | Medium |
| R3 | Current season and upcoming premieres | Medium |
| R4 | Contextual recommendations | Small/medium |
| R5 | Simple and filtered roulette | Small |
| R6 | Franchises and continuations | Medium |
| R7 | Characters, cast, and staff | Medium/large |
| R8 | Episodes | Medium |
| R9 | Where to watch | Small/medium |
| R10 | Manga area | Large |
| R11 | Voice Actors area | Large |

## 8. Planning boundary

This document defines product sequence and architectural direction. Implementation plans are written one release at a time, beginning with R1. Later releases may be refined using evidence from shipped functionality, but their order remains the one approved here unless the user explicitly reprioritizes it.
