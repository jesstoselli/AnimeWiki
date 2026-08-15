# Details Relations and Recommendations Implementation Plan

**Goal:** Upgrade anime details with reusable expandable sections, related titles,
and contextual recommendations.

**Architecture:** Extend the existing `AnimeDetails` GraphQL request so primary
details, relations, and recommendations arrive together. Map secondary content
to small domain previews and render it through one reusable expandable-section
component. Room fallback remains valid with empty secondary lists.

**Spec:** `docs/superpowers/specs/2026-07-22-anime-wiki-product-roadmap-design.md`

## Constraints

- One GraphQL HTTP request for an anime details load.
- Synopsis and Information start expanded.
- Related titles, Recommendations, and future sections start collapsed.
- Empty sections are omitted.
- Anime targets open anime details; unsupported media remain labeled and inert.
- No emulator run; final manual validation happens on the user's phone.

## Tasks

- [x] Extend `AnimeDetails.graphql` with relations and top recommendations.
- [x] Add media-preview, relation, and recommendation domain models.
- [x] Map valid items, skip malformed/self/duplicate entries, and preserve Room fallback.
- [x] Add a reusable expandable details section with accessible expand/collapse state.
- [x] Reshape the existing synopsis and information into expandable sections.
- [x] Add compact horizontal related/recommendation cards and anime navigation.
- [x] Add focused mapper/state tests and run unit tests, Detekt, and APK build.
- [ ] Provide a short phone-test checklist before merge.
