# Task 1 Report: Define immutable filter and browse-criteria contracts

## Status

DONE_WITH_CONCERNS

Task 1 was implemented exactly as specified in the brief and committed as:

`3728c20 feat: define anime discovery filters`

## Implementation

Added the immutable domain contracts under `app/src/main/java/com/example/animewiki/domain/model/`:

- `AnimeFormat.kt`: enum with the six Jikan format API values.
- `AnimeAgeRating.kt`: enum with the six Jikan age-rating API values.
- `AnimeFilters.kt`: immutable data class with optional format/rating, genre IDs, empty-state detection, active-criterion count, and sorted genre query serialization.
- `AnimeBrowseCriteria.kt`: immutable data class with a private constructor, trimmed query factory, filter composition, and default-feed detection.

Added `app/src/test/java/com/example/animewiki/domain/model/AnimeFiltersTest.kt` with the four focused domain tests from the brief.

## TDD Evidence

### RED

Command:

`./gradlew testDebugUnitTest --tests "com.example.animewiki.domain.model.AnimeFiltersTest"`

Result: compilation failed as expected because `AnimeFormat`, `AnimeAgeRating`, `AnimeFilters`, and `AnimeBrowseCriteria` did not exist. The compiler reported unresolved references for those four types.

### GREEN

The same focused command was run after the minimal implementation.

Result: `BUILD SUCCESSFUL`; the focused test task completed with zero failures.

## Verification

Focused test:

`./gradlew testDebugUnitTest --tests "com.example.animewiki.domain.model.AnimeFiltersTest"`

Result: passed, `BUILD SUCCESSFUL`.

Full unit suite:

`./gradlew testDebugUnitTest`

Result: passed, `BUILD SUCCESSFUL`.

Additional self-review checks:

- `git diff --check`: passed.
- Confirmed only the five Task 1 files were included in commit `3728c20`.
- Confirmed the implementation matches the exact interfaces and values in the brief.

## Self-review

The contracts use Kotlin `data class`/`enum class` value semantics and expose only immutable `val` properties. `genresQuery` sorts IDs before joining them, `activeCount` counts format and rating independently plus each genre ID, and the factory trims the query before calculating default-feed state. No unrelated files or behavior were changed.

## Concerns

The focused compile emits a Kotlin warning because the required `data class AnimeBrowseCriteria private constructor(...)` generates a public `copy()` method whose visibility is broader than its private primary constructor. The warning is from the exact contract requested in the brief and did not fail either test run. The build also emits existing environment/toolchain warnings about compileSdk 36 versus the tested Android Gradle Plugin version and deprecated Gradle features.

## Review Fix

### Findings addressed

- Replaced `AnimeBrowseCriteria`'s data class with a value-semantics class whose only construction path is `create(query, filters)`, which trims the query. Structural `equals`, `hashCode`, and `toString` preserve the public value API without exposing `copy()`.
- Replaced `AnimeFilters`' data class with a value-semantics class that defensively snapshots `genreIds` into an unmodifiable set. The constructor shape, readable properties, derived values, structural equality, and required `copy(format, rating, genreIds)` API are preserved.
- Added regression coverage for source `MutableSet` mutation, criteria identity, normalized creation, structural equality, and filter copying.

### TDD evidence

#### RED

Command:

`./gradlew testDebugUnitTest --tests "com.example.animewiki.domain.model.AnimeFiltersTest"`

Result: compilation completed and the test task failed as expected: `7 tests completed, 1 failed`; `source genre mutations do not change filters or criteria identity` failed because the original data class aliased the source mutable set.

#### GREEN

Focused command:

`./gradlew testDebugUnitTest --tests "com.example.animewiki.domain.model.AnimeFiltersTest"`

Result: `BUILD SUCCESSFUL`; 7 tests passed. The Kotlin public-copy visibility warning no longer appears. Existing AGP compileSdk and Gradle deprecation warnings remain.

Full unit suite:

`./gradlew testDebugUnitTest`

Result: `BUILD SUCCESSFUL`; zero failures.

Additional verification:

- `git diff --check`: passed.
- Architecture review found no DRY, abstraction, design-pattern, code-smell, or efficiency finding requiring a change for these small value objects.

### Commit and files

Commit: `fix: preserve immutable anime discovery criteria` (the report is included in the final commit).

Files changed:

- `app/src/main/java/com/example/animewiki/domain/model/AnimeBrowseCriteria.kt`
- `app/src/main/java/com/example/animewiki/domain/model/AnimeFilters.kt`
- `app/src/test/java/com/example/animewiki/domain/model/AnimeFiltersTest.kt`
