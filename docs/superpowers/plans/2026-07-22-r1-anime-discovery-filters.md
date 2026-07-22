# R1 Anime Discovery Filters Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Evolve the main anime listing into Discover with combinable format, age-rating, and genre filters while preserving the existing ranking, search, paging, and error behavior.

**Architecture:** Keep `/top/anime` and its Room-backed `RemoteMediator` for the unfiltered default feed. Represent `query + filters` as an immutable browse criterion; any non-default criterion creates a network-backed `AnimeSearchPagingSource` using `/anime`. Genre metadata is loaded on demand and cached in memory for the app process; persistent composite feed caching remains R2 scope.

**Tech Stack:** Kotlin 2.1, Android/Jetpack Compose Material 3, Hilt, Retrofit 2.11, kotlinx.serialization 1.7, Paging 3.3, Room 2.7, Coroutines/StateFlow, JUnit 4, MockK, Turbine, Compose UI Test.

## Global Constraints

- Jikan is read-only. Favorites and personal state remain local.
- Respect the public Jikan limit of 3 requests per second and 60 per minute.
- Do not depend on live Jikan availability for automated verification; `/anime` currently returns intermittent upstream 5xx/504 errors.
- Remote DTO fields remain nullable and malformed entries are skipped without discarding valid siblings.
- The default ranking remains offline-first through the existing Room cache.
- Filtered/search feeds are network-backed in R1; persistent cache identity and bounded eviction are implemented in R2.
- A criterion is identified by normalized query, format, rating, and a set of genre IDs; genre query strings are sorted for deterministic requests.
- Every behavior change follows red-green TDD and each task ends with its focused tests passing.
- Before handoff, run the full unit suite, compile the app, run Detekt, and validate the filter flow on a device or emulator.

## File Structure

**Create**

- `app/src/main/java/com/example/animewiki/domain/model/AnimeFormat.kt` — supported format values and Jikan wire values.
- `app/src/main/java/com/example/animewiki/domain/model/AnimeAgeRating.kt` — supported audience ratings and Jikan wire values.
- `app/src/main/java/com/example/animewiki/domain/model/AnimeFilters.kt` — immutable applied-filter state.
- `app/src/main/java/com/example/animewiki/domain/model/AnimeBrowseCriteria.kt` — normalized query plus filters; Paging identity.
- `app/src/main/java/com/example/animewiki/domain/model/AnimeGenre.kt` — genre identity used by domain/UI.
- `app/src/main/java/com/example/animewiki/data/remote/dto/AnimeGenreDto.kt` — tolerant genre response DTOs.
- `app/src/main/java/com/example/animewiki/data/mapper/AnimeGenreMapper.kt` — skips invalid genre entries.
- `app/src/main/java/com/example/animewiki/ui/screens/topAnime/AnimeGenresState.kt` — independent genre catalog state.
- `app/src/main/java/com/example/animewiki/ui/screens/topAnime/components/AnimeFilterLabels.kt` — enum-to-resource mapping.
- `app/src/main/java/com/example/animewiki/ui/screens/topAnime/components/AnimeFilterBar.kt` — filter action and removable applied chips.
- `app/src/main/java/com/example/animewiki/ui/screens/topAnime/components/AnimeFilterSheet.kt` — draft filter selection and apply/clear actions.
- Focused unit and instrumented tests listed in each task.

**Modify**

- `app/src/main/java/com/example/animewiki/data/remote/JikanApi.kt` — genre endpoint and nullable filter query parameters.
- `app/src/main/java/com/example/animewiki/data/paging/AnimeSearchPagingSource.kt` — consume complete criteria rather than query text only.
- `app/src/main/java/com/example/animewiki/data/repository/AnimeRepository.kt` — criteria-based search and genre catalog cache.
- `app/src/main/java/com/example/animewiki/ui/screens/topAnime/TopAnimeViewModel.kt` — applied filters, criteria switching, genre loading.
- `app/src/main/java/com/example/animewiki/ui/screens/topAnime/TopAnimeScreen.kt` — filter UI and filtered-empty behavior.
- `app/src/main/java/com/example/animewiki/ui/screens/topAnime/components/SearchField.kt` — generalize the empty result state.
- `app/src/main/java/com/example/animewiki/ui/navigation/AnimeWikiNavHost.kt` — user-facing Discover tab label/icon.
- `app/src/main/res/values/strings.xml` and `app/src/main/res/values-en/strings.xml` — localized Discover/filter copy.
- `app/build.gradle.kts` — Compose instrumented test dependency already present in the version catalog.
- `README.md` — document R1 behavior and network-only filter limitation.

---

### Task 1: Define immutable filter and browse-criteria contracts

**Files:**
- Create: `app/src/main/java/com/example/animewiki/domain/model/AnimeFormat.kt`
- Create: `app/src/main/java/com/example/animewiki/domain/model/AnimeAgeRating.kt`
- Create: `app/src/main/java/com/example/animewiki/domain/model/AnimeFilters.kt`
- Create: `app/src/main/java/com/example/animewiki/domain/model/AnimeBrowseCriteria.kt`
- Test: `app/src/test/java/com/example/animewiki/domain/model/AnimeFiltersTest.kt`

**Interfaces:**
- Produces: `AnimeFormat.apiValue: String`, `AnimeAgeRating.apiValue: String`, `AnimeFilters`, `AnimeBrowseCriteria.create(query, filters)`, and `AnimeBrowseCriteria.isDefault`.
- Consumers: Tasks 3, 4, 5, 6, and 7.

- [ ] **Step 1: Write the failing domain tests**

```kotlin
package com.example.animewiki.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeFiltersTest {
    @Test
    fun `api values match Jikan query contract`() {
        assertEquals("tv", AnimeFormat.TV.apiValue)
        assertEquals("movie", AnimeFormat.MOVIE.apiValue)
        assertEquals("pg13", AnimeAgeRating.PG13.apiValue)
        assertEquals("r17", AnimeAgeRating.R17.apiValue)
        assertEquals("r", AnimeAgeRating.R_PLUS.apiValue)
    }

    @Test
    fun `genre query is sorted and null when no genre is selected`() {
        assertEquals("1,10,24", AnimeFilters(genreIds = setOf(24, 1, 10)).genresQuery)
        assertNull(AnimeFilters().genresQuery)
    }

    @Test
    fun `active count includes each selected criterion`() {
        val filters = AnimeFilters(
            format = AnimeFormat.TV,
            rating = AnimeAgeRating.PG13,
            genreIds = setOf(1, 10)
        )

        assertEquals(4, filters.activeCount)
        assertFalse(filters.isEmpty)
    }

    @Test
    fun `criteria trims query and identifies default feed`() {
        val default = AnimeBrowseCriteria.create("   ", AnimeFilters())
        val filtered = AnimeBrowseCriteria.create("  frieren  ", AnimeFilters())

        assertTrue(default.isDefault)
        assertEquals("", default.query)
        assertFalse(filtered.isDefault)
        assertEquals("frieren", filtered.query)
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
./gradlew testDebugUnitTest --tests "com.example.animewiki.domain.model.AnimeFiltersTest"
```

Expected: compilation fails because the four domain types do not exist.

- [ ] **Step 3: Add the minimal domain implementation**

```kotlin
// AnimeFormat.kt
package com.example.animewiki.domain.model

enum class AnimeFormat(val apiValue: String) {
    TV("tv"),
    MOVIE("movie"),
    OVA("ova"),
    ONA("ona"),
    SPECIAL("special"),
    MUSIC("music")
}
```

```kotlin
// AnimeAgeRating.kt
package com.example.animewiki.domain.model

enum class AnimeAgeRating(val apiValue: String) {
    G("g"),
    PG("pg"),
    PG13("pg13"),
    R17("r17"),
    R_PLUS("r"),
    RX("rx")
}
```

```kotlin
// AnimeFilters.kt
package com.example.animewiki.domain.model

data class AnimeFilters(
    val format: AnimeFormat? = null,
    val rating: AnimeAgeRating? = null,
    val genreIds: Set<Int> = emptySet()
) {
    val isEmpty: Boolean
        get() = format == null && rating == null && genreIds.isEmpty()

    val activeCount: Int
        get() = listOfNotNull(format, rating).size + genreIds.size

    val genresQuery: String?
        get() = genreIds.sorted().joinToString(",").ifBlank { null }
}
```

```kotlin
// AnimeBrowseCriteria.kt
package com.example.animewiki.domain.model

data class AnimeBrowseCriteria private constructor(
    val query: String,
    val filters: AnimeFilters
) {
    val isDefault: Boolean
        get() = query.isBlank() && filters.isEmpty

    companion object {
        fun create(
            query: String = "",
            filters: AnimeFilters = AnimeFilters()
        ): AnimeBrowseCriteria = AnimeBrowseCriteria(
            query = query.trim(),
            filters = filters
        )
    }
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the Step 2 command. Expected: `BUILD SUCCESSFUL`, 4 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/animewiki/domain/model/AnimeFormat.kt app/src/main/java/com/example/animewiki/domain/model/AnimeAgeRating.kt app/src/main/java/com/example/animewiki/domain/model/AnimeFilters.kt app/src/main/java/com/example/animewiki/domain/model/AnimeBrowseCriteria.kt app/src/test/java/com/example/animewiki/domain/model/AnimeFiltersTest.kt
git commit -m "feat: define anime discovery filters"
```

---

### Task 2: Add a tolerant anime genre catalog contract

**Files:**
- Create: `app/src/main/java/com/example/animewiki/domain/model/AnimeGenre.kt`
- Create: `app/src/main/java/com/example/animewiki/data/remote/dto/AnimeGenreDto.kt`
- Create: `app/src/main/java/com/example/animewiki/data/mapper/AnimeGenreMapper.kt`
- Modify: `app/src/main/java/com/example/animewiki/data/remote/JikanApi.kt`
- Test: `app/src/test/java/com/example/animewiki/data/remote/dto/AnimeGenreDtoTest.kt`
- Test: `app/src/test/java/com/example/animewiki/data/mapper/AnimeGenreMapperTest.kt`

**Interfaces:**
- Produces: `AnimeGenre(id: Int, name: String, count: Int?)`, `AnimeGenreDto.toDomain()`, and `JikanApi.getAnimeGenres()`.
- Consumers: Tasks 4, 5, 6, and 7.

- [ ] **Step 1: Write failing serialization and mapping tests**

```kotlin
package com.example.animewiki.data.remote.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnimeGenreDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `genre response tolerates missing fields`() {
        val response = json.decodeFromString<AnimeGenreListResponseDto>(
            """{"data":[{"mal_id":1,"name":"Action","count":5310},{}]}"""
        )

        assertEquals(2, response.data?.size)
        assertNull(response.data?.get(1)?.malId)
    }
}
```

```kotlin
package com.example.animewiki.data.mapper

import com.example.animewiki.data.remote.dto.AnimeGenreDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnimeGenreMapperTest {
    @Test
    fun `valid genre maps to domain`() {
        val result = AnimeGenreDto(malId = 1, name = "Action", count = 5310).toDomain()

        assertEquals(1, result?.id)
        assertEquals("Action", result?.name)
        assertEquals(5310, result?.count)
    }

    @Test
    fun `missing id or blank name is skipped`() {
        assertNull(AnimeGenreDto(malId = null, name = "Action").toDomain())
        assertNull(AnimeGenreDto(malId = 1, name = "  ").toDomain())
    }
}
```

- [ ] **Step 2: Run both tests and verify RED**

```bash
./gradlew testDebugUnitTest --tests "com.example.animewiki.data.remote.dto.AnimeGenreDtoTest" --tests "com.example.animewiki.data.mapper.AnimeGenreMapperTest"
```

Expected: compilation fails because genre DTO/domain/mapper types do not exist.

- [ ] **Step 3: Add DTO, domain model, mapper, and endpoint**

```kotlin
// AnimeGenre.kt
package com.example.animewiki.domain.model

data class AnimeGenre(
    val id: Int,
    val name: String,
    val count: Int?
)
```

```kotlin
// AnimeGenreDto.kt
package com.example.animewiki.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnimeGenreListResponseDto(
    val data: List<AnimeGenreDto>? = null
)

@Serializable
data class AnimeGenreDto(
    @SerialName("mal_id") val malId: Int? = null,
    val name: String? = null,
    val count: Int? = null
)
```

```kotlin
// AnimeGenreMapper.kt
package com.example.animewiki.data.mapper

import com.example.animewiki.data.remote.dto.AnimeGenreDto
import com.example.animewiki.domain.model.AnimeGenre

fun AnimeGenreDto.toDomain(): AnimeGenre? {
    val id = malId ?: return null
    val normalizedName = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return AnimeGenre(id = id, name = normalizedName, count = count)
}
```

Add to `JikanApi`:

```kotlin
@GET("genres/anime")
suspend fun getAnimeGenres(): AnimeGenreListResponseDto
```

Add the `AnimeGenreListResponseDto` import.

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run the Step 2 command. Expected: `BUILD SUCCESSFUL`, 3 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/animewiki/domain/model/AnimeGenre.kt app/src/main/java/com/example/animewiki/data/remote/dto/AnimeGenreDto.kt app/src/main/java/com/example/animewiki/data/remote/JikanApi.kt app/src/main/java/com/example/animewiki/data/mapper/AnimeGenreMapper.kt app/src/test/java/com/example/animewiki/data/remote/dto/AnimeGenreDtoTest.kt app/src/test/java/com/example/animewiki/data/mapper/AnimeGenreMapperTest.kt
git commit -m "feat: add anime genre catalog contract"
```

---

### Task 3: Send complete browse criteria through the filtered PagingSource

**Files:**
- Modify: `app/src/main/java/com/example/animewiki/data/remote/JikanApi.kt`
- Modify: `app/src/main/java/com/example/animewiki/data/paging/AnimeSearchPagingSource.kt`
- Modify: `app/src/main/java/com/example/animewiki/data/repository/AnimeRepository.kt`
- Modify: `app/src/main/java/com/example/animewiki/ui/screens/topAnime/TopAnimeViewModel.kt`
- Create: `app/src/test/java/com/example/animewiki/data/paging/AnimeSearchPagingSourceTest.kt`

**Interfaces:**
- Consumes: `AnimeBrowseCriteria`, `AnimeFilters.genresQuery`, and enum `apiValue` properties from Task 1.
- Produces: `AnimeSearchPagingSource(api, criteria)`, `repository.searchAnime(criteria)`, and nullable Retrofit query parameters `q`, `type`, `rating`, and `genres`.
- Consumers: Task 5.

- [ ] **Step 1: Write failing PagingSource request tests**

```kotlin
package com.example.animewiki.data.paging

import androidx.paging.PagingSource
import com.example.animewiki.data.remote.JikanApi
import com.example.animewiki.data.remote.dto.AnimeListResponseDto
import com.example.animewiki.data.remote.dto.PaginationDto
import com.example.animewiki.domain.model.AnimeAgeRating
import com.example.animewiki.domain.model.AnimeBrowseCriteria
import com.example.animewiki.domain.model.AnimeFilters
import com.example.animewiki.domain.model.AnimeFormat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeSearchPagingSourceTest {
    private val api: JikanApi = mockk()

    @Test
    fun `load sends normalized query and all active filters`() = runTest {
        stubEmptyPage()
        val criteria = AnimeBrowseCriteria.create(
            query = " frieren ",
            filters = AnimeFilters(
                format = AnimeFormat.TV,
                rating = AnimeAgeRating.PG13,
                genreIds = setOf(10, 1)
            )
        )

        val result = AnimeSearchPagingSource(api, criteria).load(refresh())

        assertTrue(result is PagingSource.LoadResult.Page)
        coVerify(exactly = 1) {
            api.searchAnime(
                query = "frieren",
                page = 1,
                limit = 25,
                type = "tv",
                rating = "pg13",
                genres = "1,10",
                orderBy = "popularity",
                sort = "asc"
            )
        }
    }

    @Test
    fun `filter-only load omits blank q parameter`() = runTest {
        stubEmptyPage()
        val criteria = AnimeBrowseCriteria.create(
            filters = AnimeFilters(format = AnimeFormat.MOVIE)
        )

        AnimeSearchPagingSource(api, criteria).load(refresh())

        coVerify {
            api.searchAnime(
                query = null,
                page = 1,
                limit = 25,
                type = "movie",
                rating = null,
                genres = null,
                orderBy = "popularity",
                sort = "asc"
            )
        }
    }

    private fun stubEmptyPage() {
        coEvery { api.searchAnime(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            AnimeListResponseDto(
                pagination = PaginationDto(hasNextPage = false),
                data = emptyList()
            )
    }

    private fun refresh() = PagingSource.LoadParams.Refresh<Int>(
        key = null,
        loadSize = 25,
        placeholdersEnabled = false
    )
}
```

- [ ] **Step 2: Run the focused test and verify RED**

```bash
./gradlew testDebugUnitTest --tests "com.example.animewiki.data.paging.AnimeSearchPagingSourceTest"
```

Expected: compilation fails because the PagingSource still accepts a `String` and the API has no filter parameters.

- [ ] **Step 3: Extend the Retrofit contract**

Replace `searchAnime` with:

```kotlin
@GET("anime")
suspend fun searchAnime(
    @Query(QUERY) query: String? = null,
    @Query(PAGE) page: Int = 1,
    @Query(LIMIT) limit: Int = 25,
    @Query(TYPE) type: String? = null,
    @Query(RATING) rating: String? = null,
    @Query(GENRES) genres: String? = null,
    @Query(ORDER_BY) orderBy: String = POPULARITY,
    @Query(SORT) sort: String = ASCENDING
): AnimeListResponseDto
```

Add constants:

```kotlin
const val TYPE = "type"
const val RATING = "rating"
const val GENRES = "genres"
```

- [ ] **Step 4: Change the PagingSource to consume criteria**

Replace its constructor and API call with:

```kotlin
class AnimeSearchPagingSource(
    private val api: JikanApi,
    private val criteria: AnimeBrowseCriteria
) : PagingSource<Int, Anime>() {
    // keep getRefreshKey unchanged

    @Suppress("TooGenericExceptionCaught")
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Anime> {
        val page = params.key ?: 1
        return try {
            if (page > 1) delay(400)
            val filters = criteria.filters
            val response = api.searchAnime(
                query = criteria.query.ifBlank { null },
                page = page,
                limit = params.loadSize.coerceAtMost(25),
                type = filters.format?.apiValue,
                rating = filters.rating?.apiValue,
                genres = filters.genresQuery
            )
            val items = response.data.orEmpty().mapNotNull { it.toDomain() }
            val hasNext = response.pagination?.hasNextPage == true
            LoadResult.Page(
                data = items,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (hasNext) page + 1 else null
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
```

Add imports for `AnimeBrowseCriteria` and the existing mapper.

- [ ] **Step 5: Update repository and current ViewModel call sites so the checkpoint compiles**

Replace `AnimeRepository.searchAnime(query: String)` with:

```kotlin
fun searchAnime(criteria: AnimeBrowseCriteria): Flow<PagingData<Anime>> = Pager(
    config = PagingConfig(
        pageSize = 25,
        prefetchDistance = 10,
        enablePlaceholders = false
    ),
    pagingSourceFactory = { AnimeSearchPagingSource(api, criteria) }
).flow.map { pagingData ->
    val seenIds = mutableSetOf<Int>()
    pagingData.filter { anime -> seenIds.add(anime.id) }
}
```

Until Task 5 introduces the combined query/filter flow, change the existing ViewModel search branch to:

```kotlin
repository.searchAnime(AnimeBrowseCriteria.create(q))
```

Add imports for `AnimeBrowseCriteria` in both files.

- [ ] **Step 6: Run the focused test and verify GREEN**

Run the Step 2 command. Expected: `BUILD SUCCESSFUL`, 2 tests, 0 failures.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/animewiki/data/remote/JikanApi.kt app/src/main/java/com/example/animewiki/data/paging/AnimeSearchPagingSource.kt app/src/main/java/com/example/animewiki/data/repository/AnimeRepository.kt app/src/main/java/com/example/animewiki/ui/screens/topAnime/TopAnimeViewModel.kt app/src/test/java/com/example/animewiki/data/paging/AnimeSearchPagingSourceTest.kt
git commit -m "feat: apply filters to anime paging requests"
```

---

### Task 4: Cache the genre catalog in the repository

**Files:**
- Modify: `app/src/main/java/com/example/animewiki/data/repository/AnimeRepository.kt`
- Modify: `app/src/test/java/com/example/animewiki/data/repository/AnimeRepositoryTest.kt`

**Interfaces:**
- Consumes: `AnimeGenreDto.toDomain()`.
- Produces: `getAnimeGenres(forceRefresh: Boolean = false): List<AnimeGenre>`.
- Consumers: Task 5.

- [ ] **Step 1: Add failing genre-cache repository tests**

Add to `AnimeRepositoryTest`:

```kotlin
@Test
fun `getAnimeGenres maps sorts and caches valid genres`() = runTest {
    coEvery { api.getAnimeGenres() } returns AnimeGenreListResponseDto(
        data = listOf(
            AnimeGenreDto(malId = 2, name = "Adventure", count = 20),
            AnimeGenreDto(malId = null, name = "Invalid"),
            AnimeGenreDto(malId = 1, name = "Action", count = 30)
        )
    )

    val first = repository.getAnimeGenres()
    val second = repository.getAnimeGenres()

    assertEquals(listOf("Action", "Adventure"), first.map { it.name })
    assertEquals(first, second)
    coVerify(exactly = 1) { api.getAnimeGenres() }
}

@Test(expected = IllegalStateException::class)
fun `empty genre response is not accepted as a valid catalog`() = runTest {
    coEvery { api.getAnimeGenres() } returns AnimeGenreListResponseDto(data = emptyList())

    repository.getAnimeGenres()
}
```

Add imports for the new DTOs.

- [ ] **Step 2: Run repository tests and verify RED**

```bash
./gradlew testDebugUnitTest --tests "com.example.animewiki.data.repository.AnimeRepositoryTest"
```

Expected: compilation fails because `getAnimeGenres` does not exist.

- [ ] **Step 3: Implement repository criteria and genre caching**

Add to `AnimeRepository`:

```kotlin
private var cachedGenres: List<AnimeGenre>? = null

suspend fun getAnimeGenres(forceRefresh: Boolean = false): List<AnimeGenre> {
    if (!forceRefresh) cachedGenres?.let { return it }
    val genres = api.getAnimeGenres().data.orEmpty()
        .mapNotNull { it.toDomain() }
        .sortedBy { it.name.lowercase() }
    check(genres.isNotEmpty()) { "Jikan returned an empty anime genre catalog" }
    cachedGenres = genres
    return genres
}
```

Add imports for `AnimeGenre` and the genre mapper.

- [ ] **Step 4: Run repository tests and verify GREEN**

Run the Step 2 command. Expected: all repository tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/animewiki/data/repository/AnimeRepository.kt app/src/test/java/com/example/animewiki/data/repository/AnimeRepositoryTest.kt
git commit -m "feat: cache anime genre catalog"
```

---

### Task 5: Orchestrate applied filters and genre loading in the ViewModel

**Files:**
- Create: `app/src/main/java/com/example/animewiki/ui/screens/topAnime/AnimeGenresState.kt`
- Modify: `app/src/main/java/com/example/animewiki/ui/screens/topAnime/TopAnimeViewModel.kt`
- Modify: `app/src/test/java/com/example/animewiki/ui/screens/topAnime/TopAnimeViewModelTest.kt`

**Interfaces:**
- Consumes: `repository.topAnime()`, `repository.searchAnime(criteria)`, and `repository.getAnimeGenres(forceRefresh)`.
- Produces: `filters: StateFlow<AnimeFilters>`, `genresState: StateFlow<AnimeGenresState>`, `applyFilters`, `clearFilters`, `removeGenre`, `loadGenres`, and `retryGenres`.
- Consumers: Tasks 6 and 7.

- [ ] **Step 1: Replace ViewModel tests with criteria and genre-state coverage**

Keep the existing query tests and add:

```kotlin
private val repository: AnimeRepository = mockk {
    every { topAnime() } returns flowOf(PagingData.empty())
    every { searchAnime(any()) } returns flowOf(PagingData.empty())
}

@Test
fun `applying filters switches from top to criteria search immediately`() = runTest {
    val viewModel = TopAnimeViewModel(repository)
    val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        viewModel.animeList.collect()
    }
    advanceUntilIdle()

    viewModel.applyFilters(AnimeFilters(format = AnimeFormat.TV))
    advanceUntilIdle()

    verify { repository.topAnime() }
    verify { repository.searchAnime(match { it.query.isEmpty() && it.filters.format == AnimeFormat.TV }) }
    job.cancel()
}

@Test
fun `clear filters restores empty applied state`() = runTest {
    val viewModel = TopAnimeViewModel(repository)
    viewModel.applyFilters(AnimeFilters(rating = AnimeAgeRating.PG13, genreIds = setOf(1)))

    viewModel.clearFilters()

    assertEquals(AnimeFilters(), viewModel.filters.value)
}

@Test
fun `load genres exposes loading then content`() = runTest {
    val genres = listOf(AnimeGenre(1, "Action", 30))
    coEvery { repository.getAnimeGenres(false) } coAnswers {
        yield()
        genres
    }
    val viewModel = TopAnimeViewModel(repository)

    viewModel.genresState.test {
        assertEquals(AnimeGenresState.Idle, awaitItem())
        viewModel.loadGenres()
        assertEquals(AnimeGenresState.Loading, awaitItem())
        assertEquals(AnimeGenresState.Content(genres), awaitItem())
    }
}

@Test
fun `genre failure remains independent from anime paging`() = runTest {
    coEvery { repository.getAnimeGenres(false) } throws IOException("upstream")
    val viewModel = TopAnimeViewModel(repository)

    viewModel.loadGenres()
    advanceUntilIdle()

    assertTrue(viewModel.genresState.value is AnimeGenresState.Error)
}
```

Add imports for Paging, `flowOf`, `launch`, `UnconfinedTestDispatcher`, MockK verification, filter models, genre model, and `IOException`.

- [ ] **Step 2: Run ViewModel tests and verify RED**

```bash
./gradlew testDebugUnitTest --tests "com.example.animewiki.ui.screens.topAnime.TopAnimeViewModelTest"
```

Expected: compilation fails because filter and genre APIs are missing.

- [ ] **Step 3: Add the independent genre state**

```kotlin
package com.example.animewiki.ui.screens.topAnime

import com.example.animewiki.domain.model.AnimeGenre

sealed interface AnimeGenresState {
    data object Idle : AnimeGenresState
    data object Loading : AnimeGenresState
    data class Content(val genres: List<AnimeGenre>) : AnimeGenresState
    data class Error(val cause: Throwable) : AnimeGenresState
}
```

- [ ] **Step 4: Implement criteria orchestration in the ViewModel**

Use this state and flow shape:

```kotlin
private val _query = MutableStateFlow("")
val query: StateFlow<String> = _query.asStateFlow()

private val _filters = MutableStateFlow(AnimeFilters())
val filters: StateFlow<AnimeFilters> = _filters.asStateFlow()

private val _genresState = MutableStateFlow<AnimeGenresState>(AnimeGenresState.Idle)
val genresState: StateFlow<AnimeGenresState> = _genresState.asStateFlow()

private val criteria = combine(
    _query.debounce { value -> if (value.isBlank()) 0L else 400L },
    _filters
) { query, filters ->
    AnimeBrowseCriteria.create(query, filters)
}.distinctUntilChanged()

val animeList: Flow<PagingData<Anime>> = criteria
    .flatMapLatest { value ->
        if (value.isDefault) repository.topAnime() else repository.searchAnime(value)
    }
    .cachedIn(viewModelScope)

fun applyFilters(filters: AnimeFilters) {
    _filters.value = filters
}

fun clearFilters() {
    _filters.value = AnimeFilters()
}

fun removeGenre(id: Int) {
    _filters.value = _filters.value.copy(genreIds = _filters.value.genreIds - id)
}

@Suppress("TooGenericExceptionCaught")
fun loadGenres(forceRefresh: Boolean = false) {
    if (!forceRefresh && _genresState.value is AnimeGenresState.Content) return
    if (_genresState.value is AnimeGenresState.Loading) return
    viewModelScope.launch {
        _genresState.value = AnimeGenresState.Loading
        _genresState.value = try {
            AnimeGenresState.Content(repository.getAnimeGenres(forceRefresh))
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            AnimeGenresState.Error(error)
        }
    }
}

fun retryGenres() = loadGenres(forceRefresh = true)
```

Keep `onQueryChange` and `clearQuery`. Add imports for `combine`, filter/criteria models, and `launch`.

- [ ] **Step 5: Run ViewModel tests and verify GREEN**

Run the Step 2 command. Expected: all ViewModel tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/animewiki/ui/screens/topAnime/AnimeGenresState.kt app/src/main/java/com/example/animewiki/ui/screens/topAnime/TopAnimeViewModel.kt app/src/test/java/com/example/animewiki/ui/screens/topAnime/TopAnimeViewModelTest.kt
git commit -m "feat: manage anime discovery filter state"
```

---

### Task 6: Build the filter bar and modal sheet

**Required skill at task start:** invoke `frontend-design` before creating the Compose layout, then preserve the approved Material 3 interaction contract below.

**Files:**
- Create: `app/src/main/java/com/example/animewiki/ui/screens/topAnime/components/AnimeFilterLabels.kt`
- Create: `app/src/main/java/com/example/animewiki/ui/screens/topAnime/components/AnimeFilterBar.kt`
- Create: `app/src/main/java/com/example/animewiki/ui/screens/topAnime/components/AnimeFilterSheet.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/androidTest/java/com/example/animewiki/ui/screens/topAnime/components/AnimeFilterSheetTest.kt`

**Interfaces:**
- Consumes: `AnimeFilters`, `AnimeGenre`, and `AnimeGenresState`.
- Produces: `AnimeFilterBar(...)` and `AnimeFilterSheet(...)` callbacks used by Task 7.

- [ ] **Step 1: Add Compose test support and a failing interaction test**

Add to `dependencies`:

```kotlin
androidTestImplementation(libs.androidx.compose.ui.test.junit4)
```

Create:

```kotlin
package com.example.animewiki.ui.screens.topAnime.components

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.animewiki.R
import com.example.animewiki.domain.model.AnimeFilters
import com.example.animewiki.domain.model.AnimeFormat
import com.example.animewiki.ui.screens.topAnime.AnimeGenresState
import com.example.animewiki.ui.theme.AnimeWikiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AnimeFilterSheetTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun selectingFormatOnlyChangesAppliedFiltersAfterApply() {
        var applied = AnimeFilters()
        val applyLabel = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.filters_apply)
        composeRule.setContent {
            AnimeWikiTheme {
                AnimeFilterSheet(
                    appliedFilters = applied,
                    genresState = AnimeGenresState.Content(emptyList()),
                    onDismiss = {},
                    onApply = { applied = it },
                    onRetryGenres = {}
                )
            }
        }

        composeRule.onNodeWithText("TV").performClick().assertIsSelected()
        assertEquals(AnimeFilters(), applied)
        composeRule.onNodeWithText(applyLabel).performClick()
        assertEquals(AnimeFormat.TV, applied.format)
    }
}
```

- [ ] **Step 2: Compile the instrumented test and verify RED**

```bash
./gradlew compileDebugAndroidTestKotlin
```

Expected: compilation fails because `AnimeFilterSheet` does not exist.

- [ ] **Step 3: Add localized resources**

Add Portuguese and matching English strings for:

```xml
<string name="discover_title">Descobrir</string>
<string name="tab_discover">Descobrir</string>
<string name="filters_open">Filtros</string>
<string name="filters_title">Filtrar animes</string>
<string name="filters_format">Formato</string>
<string name="filters_rating">Classificação etária</string>
<string name="filters_genres">Gêneros</string>
<string name="filters_apply">Aplicar</string>
<string name="filters_clear">Limpar tudo</string>
<string name="filters_retry_genres">Tentar carregar gêneros novamente</string>
<string name="filters_genres_error">Não foi possível carregar os gêneros.</string>
<string name="filters_active_count">%1$d filtros ativos</string>
<string name="filters_remove">Remover filtro %1$s</string>
<string name="filter_format_tv">TV</string>
<string name="filter_format_movie">Filme</string>
<string name="filter_format_ova">OVA</string>
<string name="filter_format_ona">ONA</string>
<string name="filter_format_special">Especial</string>
<string name="filter_format_music">Música</string>
<string name="filter_rating_g">Livre</string>
<string name="filter_rating_pg">Infantil</string>
<string name="filter_rating_pg13">13+</string>
<string name="filter_rating_r17">17+</string>
<string name="filter_rating_r_plus">R+</string>
<string name="filter_rating_rx">Adulto</string>
```

Add to `values-en/strings.xml`:

```xml
<string name="discover_title">Discover</string>
<string name="tab_discover">Discover</string>
<string name="filters_open">Filters</string>
<string name="filters_title">Filter anime</string>
<string name="filters_format">Format</string>
<string name="filters_rating">Age rating</string>
<string name="filters_genres">Genres</string>
<string name="filters_apply">Apply</string>
<string name="filters_clear">Clear all</string>
<string name="filters_retry_genres">Try loading genres again</string>
<string name="filters_genres_error">Could not load genres.</string>
<string name="filters_active_count">%1$d active filters</string>
<string name="filters_remove">Remove %1$s filter</string>
<string name="filter_format_tv">TV</string>
<string name="filter_format_movie">Movie</string>
<string name="filter_format_ova">OVA</string>
<string name="filter_format_ona">ONA</string>
<string name="filter_format_special">Special</string>
<string name="filter_format_music">Music</string>
<string name="filter_rating_g">All ages</string>
<string name="filter_rating_pg">Children</string>
<string name="filter_rating_pg13">13+</string>
<string name="filter_rating_r17">17+</string>
<string name="filter_rating_r_plus">R+</string>
<string name="filter_rating_rx">Adult</string>
```

- [ ] **Step 4: Implement resource mapping**

```kotlin
package com.example.animewiki.ui.screens.topAnime.components

import androidx.annotation.StringRes
import com.example.animewiki.R
import com.example.animewiki.domain.model.AnimeAgeRating
import com.example.animewiki.domain.model.AnimeFormat

@StringRes
internal fun AnimeFormat.labelRes(): Int = when (this) {
    AnimeFormat.TV -> R.string.filter_format_tv
    AnimeFormat.MOVIE -> R.string.filter_format_movie
    AnimeFormat.OVA -> R.string.filter_format_ova
    AnimeFormat.ONA -> R.string.filter_format_ona
    AnimeFormat.SPECIAL -> R.string.filter_format_special
    AnimeFormat.MUSIC -> R.string.filter_format_music
}

@StringRes
internal fun AnimeAgeRating.labelRes(): Int = when (this) {
    AnimeAgeRating.G -> R.string.filter_rating_g
    AnimeAgeRating.PG -> R.string.filter_rating_pg
    AnimeAgeRating.PG13 -> R.string.filter_rating_pg13
    AnimeAgeRating.R17 -> R.string.filter_rating_r17
    AnimeAgeRating.R_PLUS -> R.string.filter_rating_r_plus
    AnimeAgeRating.RX -> R.string.filter_rating_rx
}
```

- [ ] **Step 5: Implement the draft-state modal sheet**

`AnimeFilterSheet` must:

```kotlin
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AnimeFilterSheet(
    appliedFilters: AnimeFilters,
    genresState: AnimeGenresState,
    onDismiss: () -> Unit,
    onApply: (AnimeFilters) -> Unit,
    onRetryGenres: () -> Unit
) {
    var draft by remember(appliedFilters) { mutableStateOf(appliedFilters) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.9f)
                .padding(horizontal = 20.dp)
        ) {
            Text(
                stringResource(R.string.filters_title),
                style = MaterialTheme.typography.titleLarge
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            item { FilterSectionTitle(R.string.filters_format) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimeFormat.entries.forEach { format ->
                        FilterChip(
                            selected = draft.format == format,
                            onClick = { draft = draft.copy(format = format.takeUnless { it == draft.format }) },
                            label = { Text(stringResource(format.labelRes())) }
                        )
                    }
                }
            }
            item { FilterSectionTitle(R.string.filters_rating) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimeAgeRating.entries.forEach { rating ->
                        FilterChip(
                            selected = draft.rating == rating,
                            onClick = { draft = draft.copy(rating = rating.takeUnless { it == draft.rating }) },
                            label = { Text(stringResource(rating.labelRes())) }
                        )
                    }
                }
            }
            item { FilterSectionTitle(R.string.filters_genres) }
            when (genresState) {
                AnimeGenresState.Idle, AnimeGenresState.Loading -> item {
                    CircularProgressIndicator()
                }
                is AnimeGenresState.Error -> item {
                    Column {
                        Text(stringResource(R.string.filters_genres_error))
                        TextButton(onClick = onRetryGenres) {
                            Text(stringResource(R.string.filters_retry_genres))
                        }
                    }
                }
                is AnimeGenresState.Content -> items(genresState.genres, key = { it.id }) { genre ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = genre.id in draft.genreIds,
                                onValueChange = { selected ->
                                    val ids = if (selected) draft.genreIds + genre.id else draft.genreIds - genre.id
                                    draft = draft.copy(genreIds = ids)
                                }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = genre.id in draft.genreIds,
                            onCheckedChange = null
                        )
                        Text(genre.name, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { draft = AnimeFilters() },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.filters_clear)) }
                Button(
                    onClick = { onApply(draft) },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.filters_apply)) }
            }
        }
    }
}

@Composable
private fun FilterSectionTitle(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleMedium)
}
```

Add the standard Compose imports required by these declarations.

- [ ] **Step 6: Implement the applied-filter bar**

`AnimeFilterBar` must accept applied state and remove a single criterion immediately:

```kotlin
@Composable
internal fun AnimeFilterBar(
    filters: AnimeFilters,
    genres: List<AnimeGenre>,
    onOpen: () -> Unit,
    onChange: (AnimeFilters) -> Unit,
    modifier: Modifier = Modifier
) {
    val genreNames = genres.associateBy(AnimeGenre::id)
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalButton(onClick = onOpen) {
            Icon(Icons.Default.FilterList, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.filters_open))
            if (filters.activeCount > 0) Text(" (${filters.activeCount})")
        }
        filters.format?.let { format ->
            RemovableFilterChip(stringResource(format.labelRes())) {
                onChange(filters.copy(format = null))
            }
        }
        filters.rating?.let { rating ->
            RemovableFilterChip(stringResource(rating.labelRes())) {
                onChange(filters.copy(rating = null))
            }
        }
        filters.genreIds.sorted().forEach { id ->
            genreNames[id]?.let { genre ->
                RemovableFilterChip(genre.name) {
                    onChange(filters.copy(genreIds = filters.genreIds - id))
                }
            }
        }
    }
}

@Composable
private fun RemovableFilterChip(label: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label) },
        trailingIcon = {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.filters_remove, label)
            )
        }
    )
}
```

- [ ] **Step 7: Compile and run the focused instrumented test**

```bash
./gradlew compileDebugAndroidTestKotlin
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.animewiki.ui.screens.topAnime.components.AnimeFilterSheetTest
```

Expected with a connected device/emulator: 1 test, 0 failures. If no device is connected, the compile command must pass and execution remains part of Task 8 device verification.

- [ ] **Step 8: Commit**

```bash
git add app/build.gradle.kts app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml app/src/main/java/com/example/animewiki/ui/screens/topAnime/components/AnimeFilterLabels.kt app/src/main/java/com/example/animewiki/ui/screens/topAnime/components/AnimeFilterBar.kt app/src/main/java/com/example/animewiki/ui/screens/topAnime/components/AnimeFilterSheet.kt app/src/androidTest/java/com/example/animewiki/ui/screens/topAnime/components/AnimeFilterSheetTest.kt
git commit -m "feat: add anime filter controls"
```

---

### Task 7: Integrate filters into Discover and handle filtered-empty state

**Files:**
- Modify: `app/src/main/java/com/example/animewiki/ui/screens/topAnime/TopAnimeScreen.kt`
- Modify: `app/src/main/java/com/example/animewiki/ui/screens/topAnime/components/SearchField.kt`
- Modify: `app/src/main/java/com/example/animewiki/ui/navigation/AnimeWikiNavHost.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/test/java/com/example/animewiki/ui/screens/topAnime/TopAnimeViewModelTest.kt`

**Interfaces:**
- Consumes: all ViewModel and filter component interfaces from Tasks 5 and 6.
- Produces: user-visible Discover screen with ranking fallback, active chips, modal filters, and correct empty/error states.

- [ ] **Step 1: Add a failing ViewModel regression for combined query and filters**

```kotlin
@Test
fun `query and applied filters form one normalized paging identity`() = runTest {
    val viewModel = TopAnimeViewModel(repository)
    val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        viewModel.animeList.collect()
    }
    viewModel.onQueryChange("  frieren  ")
    viewModel.applyFilters(
        AnimeFilters(rating = AnimeAgeRating.PG13, genreIds = setOf(10, 1))
    )
    advanceTimeBy(401)
    advanceUntilIdle()

    verify {
        repository.searchAnime(match {
            it.query == "frieren" &&
                it.filters.rating == AnimeAgeRating.PG13 &&
                it.filters.genresQuery == "1,10"
        })
    }
    job.cancel()
}
```

- [ ] **Step 2: Run the ViewModel regression and verify its current status**

```bash
./gradlew testDebugUnitTest --tests "com.example.animewiki.ui.screens.topAnime.TopAnimeViewModelTest"
```

Expected before integration: the test passes if Task 5 is correct. If it fails, fix criteria composition before touching UI; do not compensate in the screen.

- [ ] **Step 3: Collect filter and genre state in `TopAnimeScreen`**

Add:

```kotlin
val filters by viewModel.filters.collectAsStateWithLifecycle()
val genresState by viewModel.genresState.collectAsStateWithLifecycle()
var showFilters by rememberSaveable { mutableStateOf(false) }
val genres = (genresState as? AnimeGenresState.Content)?.genres.orEmpty()
```

Below `SearchField`, render:

```kotlin
AnimeFilterBar(
    filters = filters,
    genres = genres,
    onOpen = {
        viewModel.loadGenres()
        showFilters = true
    },
    onChange = viewModel::applyFilters,
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 4.dp)
)
```

At the end of the composable:

```kotlin
if (showFilters) {
    AnimeFilterSheet(
        appliedFilters = filters,
        genresState = genresState,
        onDismiss = { showFilters = false },
        onApply = {
            viewModel.applyFilters(it)
            showFilters = false
        },
        onRetryGenres = viewModel::retryGenres
    )
}
```

- [ ] **Step 4: Generalize the empty state**

Replace `EmptySearchState(query)` with:

```kotlin
@Composable
fun EmptyBrowseState(
    query: String,
    hasActiveFilters: Boolean,
    modifier: Modifier = Modifier
) {
    val title = if (query.isNotBlank()) {
        stringResource(R.string.search_empty_title, query)
    } else {
        stringResource(R.string.filters_empty_title)
    }
    val message = if (hasActiveFilters) {
        stringResource(R.string.filters_empty_message)
    } else {
        stringResource(R.string.search_empty_message)
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.SearchOff, contentDescription = null, modifier = Modifier.padding(bottom = 16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
```

Use it when `isEmpty && refreshState is LoadState.NotLoading && (query.isNotBlank() || !filters.isEmpty)`.

Add localized strings:

```xml
<string name="filters_empty_title">Nenhum anime com esses filtros</string>
<string name="filters_empty_message">Remova ou altere alguns filtros e tente de novo</string>
```

Add to `values-en/strings.xml`:

```xml
<string name="filters_empty_title">No anime match these filters</string>
<string name="filters_empty_message">Remove or change some filters and try again</string>
```

- [ ] **Step 5: Rename user-facing Top navigation to Discover**

- Use `R.string.discover_title` in `TopAnimeScreen`.
- Use `R.string.tab_discover` for the existing `Tabs.TOP` item.
- Change its icon from `Icons.Default.Star` to `Icons.Default.Explore`.
- Keep route and internal class names unchanged in R1 to avoid an unrelated navigation/package refactor.

- [ ] **Step 6: Run focused tests, compile, and Detekt**

```bash
./gradlew testDebugUnitTest --tests "com.example.animewiki.ui.screens.topAnime.TopAnimeViewModelTest"
./gradlew assembleDebug
./gradlew detekt
```

Expected: all three commands exit 0.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/animewiki/ui/screens/topAnime/TopAnimeScreen.kt app/src/main/java/com/example/animewiki/ui/screens/topAnime/components/SearchField.kt app/src/main/java/com/example/animewiki/ui/navigation/AnimeWikiNavHost.kt app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml app/src/test/java/com/example/animewiki/ui/screens/topAnime/TopAnimeViewModelTest.kt
git commit -m "feat: integrate filters into Discover"
```

---

### Task 8: Document and verify the complete R1 release

**Files:**
- Modify: `README.md`
- Verify: all files changed by Tasks 1–7.

**Interfaces:**
- Consumes: complete R1 implementation.
- Produces: verified release candidate and accurate project documentation.

- [ ] **Step 1: Update README feature and architecture sections**

Add this text to the feature and architecture sections, adapting only surrounding heading levels:

```markdown
### Discover filters

The former Top tab is now Discover. Its default ranking remains Room-backed and
offline-first. Format, age-rating, and multi-genre filters can be combined with
the text query; these filtered/search feeds are network-backed in R1. Genre
metadata is cached for the lifetime of the app process.

Jikan depends on MyAnimeList and may return upstream 5xx/504 responses. Anime
Wiki reports those as server problems; they do not imply that a selected filter
is invalid.
```

- [ ] **Step 2: Run the full automated verification from a clean Gradle invocation**

```bash
./gradlew clean testDebugUnitTest assembleDebug detekt compileDebugAndroidTestKotlin
```

Expected: `BUILD SUCCESSFUL`, zero unit failures, debug APK produced, zero Detekt findings, instrumented tests compile.

- [ ] **Step 3: Run the focused instrumented test on a device or emulator**

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.animewiki.ui.screens.topAnime.components.AnimeFilterSheetTest
```

Expected: 1 test, 0 failures.

- [ ] **Step 4: Perform the manual acceptance matrix**

On the app:

1. Open Discover with no filters and confirm cached Top ranking behavior is unchanged.
2. Open Filters, select TV + 13+ + two genres, and confirm nothing changes before Apply.
3. Apply and confirm all selected chips appear and the grid refreshes once.
4. Remove one chip and confirm only that criterion is removed.
5. Enter a text query and confirm it combines with the remaining filters.
6. Open anime details and return; confirm query and applied filters remain.
7. Clear all and confirm the Room-backed ranking returns.
8. Disable connectivity: default ranking shows cache; filtered feed shows the no-connection state.
9. If Jikan returns 5xx/504, confirm the server-error state and verify the outgoing URL in OkHttp logs contains the expected normalized parameters.
10. Switch the device language between Portuguese and English and confirm filter labels and accessibility descriptions are localized.

- [ ] **Step 5: Review the complete diff against the R1 spec**

```bash
git diff HEAD~7 --check
git diff HEAD~7 --stat
git status --short
```

Expected: no whitespace errors; only R1 files and README changed; no uncommitted implementation files before the final documentation commit.

- [ ] **Step 6: Commit documentation**

```bash
git add README.md
git commit -m "docs: document anime discovery filters"
```

- [ ] **Step 7: Invoke review gates**

Use `superpowers:requesting-code-review`, then the mandatory architecture review skill for this repository environment. Address accepted findings through `superpowers:receiving-code-review`, rerun Step 2, and only then use `superpowers:finishing-a-development-branch` for integration choices.
