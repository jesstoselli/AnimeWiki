# AniList Backend Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Jikan with AniList GraphQL at current feature parity, preserving the Room-backed offline ranking and local-favorites features while intentionally resetting all Jikan-era local records.

**Architecture:** Apollo Kotlin is added beside Jikan first, behind the existing `AnimeRepository` domain seam. Search is cut over together with the filter-model change; ranking, details, genres, and the notification worker follow in one coherent cutover. Room remains the app cache, but its version moves from 2 to 3 so the existing destructive fallback clears MAL ids before AniList ids are stored. Jikan and Retrofit are removed only after no production code references them.

**Tech Stack:** Kotlin 2.1.0, Android SDK 36/minSdk 24, Jetpack Compose Material 3, Hilt 2.56.2, Apollo Kotlin 5.0.1, Apollo MockServer 0.0.1, OkHttp 4.12.0, Paging 3.3.6, Room 2.7.1, Coroutines 1.9.0, JUnit 4, MockK, Turbine.

## Global Constraints

- Scope is feature parity only; do not add roadmap features.
- Endpoint is `POST https://graphql.anilist.co`; unauthenticated read queries only.
- Keep `AnimeRepository` public method signatures unchanged.
- Keep Room as the ranking cache and favorites store; do not add Apollo normalized caching.
- `includeAdultContent == false` sends `isAdult: false`; `true` omits the argument. Never send `isAdult: true`.
- AniList nullability is tolerated per item: skip entries with blank/missing title or image without dropping valid siblings.
- Apollo `response.exception` is a transport/protocol failure; `response.errors` without usable data is a server failure; usable partial data is accepted.
- Retry 429 and retryable 5xx at most three times, close intermediate responses, honor `Retry-After` then `X-RateLimit-Reset`, and use exponential backoff with jitter only as fallback.
- `TopAnime.graphql` must request every current `AnimeEntity` detail field AniList can provide so cached detail remains useful offline.
- Room version 3 intentionally destroys version-2 ranking, remote keys, and favorites. No favorite migration, `idMal` lookup, transition DataStore, or WorkManager is allowed.
- The committed schema must make normal builds reproducible without contacting AniList.
- Each task uses red-green TDD, ends green, and gets its own commit.

---

## File Structure

**Create**

- `app/src/main/graphql/schema.graphqls` — committed AniList schema.
- `app/src/main/graphql/AnimeFragments.graphql` — card and full cache/detail fragments.
- `app/src/main/graphql/TopAnime.graphql`
- `app/src/main/graphql/SearchAnime.graphql`
- `app/src/main/graphql/AnimeDetails.graphql`
- `app/src/main/graphql/GenreCollection.graphql`
- `app/src/main/java/com/example/animewiki/data/remote/AniListResponse.kt` — shared Apollo response policy.
- `app/src/main/java/com/example/animewiki/data/remote/AniListRetryInterceptor.kt` — bounded server-directed retry.
- `app/src/main/java/com/example/animewiki/data/mapper/AniListMapper.kt` — generated fragments to domain/entity.
- Unit tests named in each task.

**Modify**

- Gradle/version-catalog files for Apollo and test tooling.
- Filter domain models and Compose filter UI.
- Search PagingSource, top RemoteMediator, repository, notification worker, network DI, and error classifier.
- `AppDatabase.kt` from version 2 to 3.
- Portuguese/English strings and `README.md`.

**Remove in the final cutover**

- `JikanApi.kt`, `data/remote/dto/*`, Jikan mapper tests/mappers, `AnimeAgeRating.kt`.
- Retrofit, converter, Kotlin serialization JSON, and Kotlin serialization Gradle plugin if no remaining code uses them.

---

### Task 0: Prove the Apollo 5 toolchain and test contract

**Files:**

- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/graphql/schema.graphqls`
- Create: `app/src/main/graphql/GenreCollection.graphql`
- Create: `app/src/test/java/com/example/animewiki/data/remote/ApolloContractTest.kt`

**Interfaces:**

- Produces generated package `com.example.animewiki.graphql`.
- Produces `GenreCollectionQuery`, Apollo data builders, and a proven `MockServer` setup used by later tests.

- [ ] **Step 1: Add the exact Apollo coordinates**

Add to `gradle/libs.versions.toml`:

```toml
[versions]
apollo = "5.0.1"
apolloMockServer = "0.0.1"

[libraries]
apollo-runtime = { module = "com.apollographql.apollo:apollo-runtime", version.ref = "apollo" }
apollo-testing-support = { module = "com.apollographql.apollo:apollo-testing-support", version.ref = "apollo" }
apollo-mockserver = { module = "com.apollographql.mockserver:apollo-mockserver", version.ref = "apolloMockServer" }

[plugins]
apollo = { id = "com.apollographql.apollo", version.ref = "apollo" }
```

Keep each entry in its existing section; do not create duplicate section headers.

- [ ] **Step 2: Apply and configure Apollo**

Add `alias(libs.plugins.apollo) apply false` to the root `build.gradle.kts`. In `app/build.gradle.kts`, add `alias(libs.plugins.apollo)` and:

```kotlin
apollo {
    service("anilist") {
        packageName.set("com.example.animewiki.graphql")
        generateDataBuilders.set(true)
        introspection {
            endpointUrl.set("https://graphql.anilist.co")
            schemaFile.set(file("src/main/graphql/schema.graphqls"))
        }
    }
}
```

Add:

```kotlin
implementation(libs.apollo.runtime)
testImplementation(libs.apollo.testing.support)
testImplementation(libs.apollo.mockserver)
```

- [ ] **Step 3: Download and commit the schema**

Run:

```bash
./gradlew :app:downloadAnilistApolloSchemaFromIntrospection
test -s app/src/main/graphql/schema.graphqls
```

Expected: both commands exit 0. The second command proves the schema is non-empty.

- [ ] **Step 4: Add the aliased genre operation**

Create `GenreCollection.graphql`:

```graphql
query GenreCollection {
  genres: GenreCollection
}
```

- [ ] **Step 5: Write the contract test**

Create `ApolloContractTest.kt`:

```kotlin
package com.example.animewiki.data.remote

import com.apollographql.apollo.ApolloClient
import com.apollographql.mockserver.MockServer
import com.example.animewiki.graphql.GenreCollectionQuery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ApolloContractTest {
    @Test
    fun `apollo 5 executes generated query through mock server`() = runTest {
        val server = MockServer()
        try {
            server.enqueueString("""{"data":{"genres":["Action","Drama"]}}""")
            val client = ApolloClient.Builder().serverUrl(server.url()).build()

            val response = client.query(GenreCollectionQuery()).execute()

            assertEquals(listOf("Action", "Drama"), response.data?.genres)
            assertEquals(null, response.exception)
        } finally {
            server.stop()
        }
    }
}
```

- [ ] **Step 6: Verify the toolchain**

Run:

```bash
./gradlew :app:generateAnilistApolloSources
./gradlew :app:testDebugUnitTest --tests "*.ApolloContractTest"
```

Expected: both commands succeed. If generated builder/accessor names differ from this plan, inspect generated sources now and update all later snippets consistently before continuing; do not defer this mismatch.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts app/src/main/graphql app/src/test/java/com/example/animewiki/data/remote/ApolloContractTest.kt
git commit -m "chore: prove Apollo AniList toolchain"
```

---

### Task 1: Define the feature-parity GraphQL contract

**Files:**

- Create: `app/src/main/graphql/AnimeFragments.graphql`
- Create: `app/src/main/graphql/TopAnime.graphql`
- Create: `app/src/main/graphql/SearchAnime.graphql`
- Create: `app/src/main/graphql/AnimeDetails.graphql`

**Interfaces:**

- Produces fragments `AnimeCardFields` and `AnimeCacheFields`.
- Produces `TopAnimeQuery`, `SearchAnimeQuery`, and `AnimeDetailsQuery`.

- [ ] **Step 1: Add shared fragments**

Create `AnimeFragments.graphql`:

```graphql
fragment AnimeCardFields on Media {
  id
  title { english romaji }
  coverImage { extraLarge large }
  averageScore
  episodes
  format
  seasonYear
}

fragment AnimeCacheFields on Media {
  id
  title { english romaji }
  coverImage { extraLarge large }
  averageScore
  episodes
  format
  seasonYear
  description(asHtml: false)
  genres
  studios { nodes { name } }
  status
  duration
  startDate { year month day }
  endDate { year month day }
  trailer { id site }
  rankings { rank type allTime }
}
```

- [ ] **Step 2: Add ranking and search operations**

Create `TopAnime.graphql`:

```graphql
query TopAnime($page: Int!, $perPage: Int!, $isAdult: Boolean) {
  Page(page: $page, perPage: $perPage) {
    pageInfo { currentPage hasNextPage }
    media(type: ANIME, sort: [SCORE_DESC], isAdult: $isAdult) {
      ...AnimeCacheFields
    }
  }
}
```

Create `SearchAnime.graphql`:

```graphql
query SearchAnime(
  $page: Int!,
  $perPage: Int!,
  $search: String,
  $format: MediaFormat,
  $genres: [String],
  $isAdult: Boolean
) {
  Page(page: $page, perPage: $perPage) {
    pageInfo { currentPage hasNextPage }
    media(
      type: ANIME,
      search: $search,
      format: $format,
      genre_in: $genres,
      isAdult: $isAdult,
      sort: [SCORE_DESC]
    ) {
      ...AnimeCardFields
    }
  }
}
```

- [ ] **Step 3: Add the details operation**

Create `AnimeDetails.graphql`:

```graphql
query AnimeDetails($id: Int!) {
  Media(id: $id, type: ANIME) {
    ...AnimeCacheFields
  }
}
```

- [ ] **Step 4: Compile the operations**

Run:

```bash
./gradlew :app:generateAnilistApolloSources
find app/build/generated -name "TopAnimeQuery.kt" -o -name "AnimeCacheFields.kt" | sort
```

Expected: codegen succeeds and both generated files are printed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/graphql
git commit -m "feat: define AniList GraphQL operations"
```

---

### Task 2: Add centralized response and retry behavior

**Files:**

- Create: `app/src/main/java/com/example/animewiki/data/remote/AniListResponse.kt`
- Create: `app/src/main/java/com/example/animewiki/data/remote/AniListRetryInterceptor.kt`
- Create: `app/src/test/java/com/example/animewiki/data/remote/AniListResponseTest.kt`
- Create: `app/src/test/java/com/example/animewiki/data/remote/AniListRetryInterceptorTest.kt`
- Modify: `app/src/main/java/com/example/animewiki/ui/common/LoadErrorType.kt`
- Modify: `app/src/test/java/com/example/animewiki/ui/common/LoadErrorTypeTest.kt`
- Create: `app/src/main/java/com/example/animewiki/di/AniListModule.kt`

**Interfaces:**

- Produces `ApolloResponse<D>.dataOrAniListError(): D`.
- Produces `AniListGraphQlException`.
- Produces singleton `ApolloClient` named by type, backed by the retrying OkHttp client.

- [ ] **Step 1: Write response-policy tests**

Use `MockServer` with `GenreCollectionQuery` to cover:

```kotlin
@Test fun `returns data when response also contains field errors`() = runTest {
    server.enqueueString(
        """{"data":{"genres":["Action",null]},"errors":[{"message":"one item failed"}]}"""
    )
    val data = client.query(GenreCollectionQuery()).execute().dataOrAniListError()
    assertEquals("Action", data.genres?.first())
}

@Test fun `throws typed server error when no data is usable`() = runTest {
    server.enqueueString("""{"data":null,"errors":[{"message":"upstream failed"}]}""")
    assertFailsWith<AniListGraphQlException> {
        client.query(GenreCollectionQuery()).execute().dataOrAniListError()
    }
}
```

Also stop the server before a request and assert the thrown value is the response's Apollo exception, not `AniListGraphQlException`.

- [ ] **Step 2: Implement the response policy**

Create `AniListResponse.kt`:

```kotlin
package com.example.animewiki.data.remote

import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation

class AniListGraphQlException(message: String) : RuntimeException(message)

fun <D : Operation.Data> ApolloResponse<D>.dataOrAniListError(): D {
    exception?.let { throw it }
    data?.let { return it }
    val message = errors.orEmpty()
        .joinToString(separator = "; ") { it.message }
        .ifBlank { "AniList response contained no usable data" }
    throw AniListGraphQlException(message)
}
```

- [ ] **Step 3: Write retry tests before implementation**

Use an injected `sleep: (Long) -> Unit`, `nowEpochSeconds: () -> Long`, and `jitter: (Long) -> Long`. Cover:

- 200: one request, no sleep.
- 404: one request, no retry.
- 503 then 200: first response is closed and one jittered fallback delay occurs.
- four 503s: four total attempts and final response returned.
- 429 with `Retry-After: 7`: sleep 7000 ms.
- 429 with `X-RateLimit-Reset: 120` and clock 115: sleep 5000 ms.
- 429 without headers: exponential delays for attempts 0, 1, and 2.

- [ ] **Step 4: Implement bounded server-directed retry**

Create `AniListRetryInterceptor.kt` with this constructor:

```kotlin
class AniListRetryInterceptor(
    private val maxRetries: Int = 3,
    private val baseDelayMillis: Long = 1_000,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000 },
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val jitter: (Long) -> Long = { bound ->
        if (bound <= 1) 0 else kotlin.random.Random.nextLong(bound)
    }
) : Interceptor
```

Its loop must:

```kotlin
var retry = 0
while (true) {
    val response = chain.proceed(chain.request())
    val retryable = response.code == 429 || response.code in 500..599
    if (!retryable || retry == maxRetries) return response

    val fallback = baseDelayMillis * (1L shl retry)
    val delayMillis = if (response.code == 429) {
        response.header("Retry-After")?.toLongOrNull()?.times(1_000)
            ?: response.header("X-RateLimit-Reset")?.toLongOrNull()
                ?.let { ((it - nowEpochSeconds()).coerceAtLeast(0)) * 1_000 }
            ?: fallback + jitter(fallback)
    } else {
        fallback + jitter(fallback)
    }
    response.close()
    sleep(delayMillis)
    retry++
}
```

- [ ] **Step 5: Extend UI error classification**

Change `toLoadErrorType()` to classify both `ApolloNetworkException` and `IOException` as `NO_CONNECTION`; `AniListGraphQlException` and all other errors remain `SERVER`. Add one unit test for each Apollo category.

- [ ] **Step 6: Provide Apollo separately from Jikan**

Create `AniListModule.kt`:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AniListModule {
    @Provides @Singleton
    fun provideApolloClient(): ApolloClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val okHttp = OkHttpClient.Builder()
            .addInterceptor(AniListRetryInterceptor())
            .addInterceptor(logging)
            .build()
        return ApolloClient.Builder()
            .serverUrl("https://graphql.anilist.co")
            .okHttpClient(okHttp)
            .build()
    }
}
```

- [ ] **Step 7: Run focused tests and commit**

```bash
./gradlew :app:testDebugUnitTest --tests "*.AniListResponseTest" --tests "*.AniListRetryInterceptorTest" --tests "*.LoadErrorTypeTest"
git add app/src/main/java/com/example/animewiki/data/remote app/src/main/java/com/example/animewiki/di/AniListModule.kt app/src/main/java/com/example/animewiki/ui/common app/src/test
git commit -m "feat: add resilient AniList client behavior"
```

---

### Task 3: Add tolerant AniList mapping with offline detail parity

**Files:**

- Create: `app/src/main/java/com/example/animewiki/data/mapper/AniListMapper.kt`
- Create: `app/src/test/java/com/example/animewiki/data/mapper/AniListMapperTest.kt`

**Interfaces:**

- Produces `AnimeCardFields.toDomain(): Anime?`.
- Produces `AnimeCacheFields.toDomain(): Anime?`.
- Produces `AnimeCacheFields.toEntity(pageIndex: Int): AnimeEntity?`.

- [ ] **Step 1: Write failing mapper tests with Apollo data builders**

Start with these concrete tests:

```kotlin
@Test
fun `uses nonblank fallbacks and rescales score`() {
    val fragment = AnimeCardFields {
        id = 154587
        title = title {
            english = " "
            romaji = "Sousou no Frieren"
        }
        coverImage = coverImage {
            extraLarge = ""
            large = "https://img.example/frieren.jpg"
        }
        averageScore = 91
    }

    val anime = requireNotNull(fragment.toDomain())

    assertEquals("Sousou no Frieren", anime.title)
    assertEquals("https://img.example/frieren.jpg", anime.imageUrl)
    assertEquals(9.1, anime.score ?: 0.0, 0.001)
}

@Test
fun `rejects blank title and blank image`() {
    val noTitle = AnimeCardFields {
        id = 1
        title = title { english = " "; romaji = "" }
        coverImage = coverImage { large = "https://img.example/1.jpg" }
    }
    val noImage = AnimeCardFields {
        id = 2
        title = title { english = "Valid" }
        coverImage = coverImage { extraLarge = " "; large = "" }
    }

    assertNull(noTitle.toDomain())
    assertNull(noImage.toDomain())
}

@Test
fun `strips description html and accepts youtube trailer case insensitively`() {
    val fragment = AnimeCacheFields {
        id = 154587
        title = title { english = "Frieren: Beyond Journey's End" }
        coverImage = coverImage { extraLarge = "https://img.example/frieren.jpg" }
        description = "Line one.<br><i>Line two.</i> &amp; more"
        genres = listOf("Adventure", "Fantasy")
        trailer = trailer { id = "abc123"; site = "YouTube" }
    }

    val anime = requireNotNull(fragment.toDomain())

    assertEquals("Line one.\nLine two. & more", anime.synopsis)
    assertEquals(listOf("Adventure", "Fantasy"), anime.genres)
    assertEquals("abc123", anime.trailerYoutubeId)
}

@Test
fun `entity keeps complete cache detail fields and no MAL rating`() {
    val fragment = AnimeCacheFields {
        id = 154587
        title = title { english = "Frieren: Beyond Journey's End" }
        coverImage = coverImage { extraLarge = "https://img.example/frieren.jpg" }
        description = "Journey"
        genres = listOf("Adventure", "Fantasy")
        studios = studios {
            nodes = listOf(node { name = "Madhouse" })
        }
        status = MediaStatus.FINISHED
        duration = 24
        startDate = startDate { year = 2023 }
        endDate = endDate { year = 2024 }
        rankings = listOf(
            ranking {
                rank = 1
                type = MediaRankType.RATED
                allTime = true
            }
        )
        trailer = trailer { id = "abc123"; site = "youtube" }
    }

    val entity = requireNotNull(fragment.toEntity(pageIndex = 8))

    assertEquals("Madhouse", entity.studios.single())
    assertEquals("Finished", entity.status)
    assertEquals("24 min per ep", entity.duration)
    assertEquals("2023 - 2024", entity.aired)
    assertEquals(1, entity.rank)
    assertEquals(null, entity.rating)
    assertEquals(8, entity.pageIndex)
}
```

Use the builder/accessor syntax proven in Task 0. If Apollo generates a different singular DSL factory name for `nodes` or `rankings`, correct this test and all matching fixtures immediately in Task 0 based on generated source; do not introduce adapters solely to preserve a guessed builder name.

- [ ] **Step 2: Verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests "*.AniListMapperTest"
```

Expected: compilation fails because the mapper functions do not exist.

- [ ] **Step 3: Implement shared scalar helpers**

In `AniListMapper.kt`, add:

```kotlin
private fun String?.nonBlank(): String? = this?.takeIf(String::isNotBlank)
private fun Int?.scoreToTen(): Double? = this?.div(10.0)

internal fun String.stripAniListHtml(): String =
    replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .trim()
```

Use English then romaji and extra-large then large through `nonBlank()`. Return null when neither required alternative is usable.

- [ ] **Step 4: Implement full mapping**

Map:

- `averageScore / 10.0`.
- format/status enums to the existing English display strings.
- `seasonYear ?: startDate.year`.
- full start/end dates to the existing `aired` display convention.
- `description.stripAniListHtml()`.
- non-null genres and studio names.
- duration as `"$minutes min per ep"`.
- first ranking with `type == RATED && allTime == true`.
- trailer id only when `site.equals("youtube", ignoreCase = true)`.
- `rating = null`.

`toEntity(pageIndex)` must derive from the same full fragment values rather than a card-only mapper.

- [ ] **Step 5: Verify GREEN and commit**

```bash
./gradlew :app:testDebugUnitTest --tests "*.AniListMapperTest"
git add app/src/main/java/com/example/animewiki/data/mapper/AniListMapper.kt app/src/test/java/com/example/animewiki/data/mapper/AniListMapperTest.kt
git commit -m "feat: map AniList media with offline detail parity"
```

---

### Task 4: Cut search and filters over to AniList semantics

**Files:**

- Modify: `domain/model/AnimeFilters.kt`, `AnimeFormat.kt`, `AnimeGenre.kt`
- Delete: `domain/model/AnimeAgeRating.kt`
- Modify: `data/paging/AnimeSearchPagingSource.kt`
- Modify: `data/repository/AnimeRepository.kt`
- Modify: `ui/screens/topAnime/TopAnimeViewModel.kt`
- Modify: `ui/screens/topAnime/components/AnimeFilterSheet.kt`
- Modify: `ui/screens/topAnime/components/AnimeFilterBar.kt`
- Modify: `ui/screens/topAnime/components/AnimeFilterLabels.kt`
- Modify: Portuguese/English `strings.xml`
- Update corresponding domain, paging, ViewModel, and Compose tests.

**Interfaces:**

- Produces immutable `AnimeFilters(format, includeAdultContent, genres: Set<String>)`.
- `AnimeSearchPagingSource` consumes `ApolloClient` and `AnimeBrowseCriteria`.

- [ ] **Step 1: Rewrite filter-model tests first**

Assert:

```kotlin
val filters = AnimeFilters(
    format = AnimeFormat.TV,
    includeAdultContent = true,
    genres = setOf("Fantasy", "Action")
)
assertEquals(4, filters.activeCount)
assertEquals(setOf("Action", "Fantasy"), filters.genres)
assertFalse(filters.isEmpty)
assertTrue(AnimeFilters().isEmpty)
```

Also prove constructor input cannot mutate the stored set.

- [ ] **Step 2: Implement the AniList filter model**

`AnimeFilters` fields become:

```kotlin
class AnimeFilters(
    val format: AnimeFormat? = null,
    val includeAdultContent: Boolean = false,
    genres: Set<String> = emptySet()
)
```

Store an unmodifiable copy. `activeCount` counts format, the adult toggle, and each genre. Update `copy`, equality, hash code, and string output. `AnimeGenre` becomes:

```kotlin
data class AnimeGenre(val name: String)
```

Update format wire values to `TV`, `MOVIE`, `OVA`, `ONA`, `SPECIAL`, and `MUSIC`.

- [ ] **Step 3: Write search query-variable tests**

With MockServer, inspect the recorded request body:

- Default maturity includes `"isAdult":false`.
- Enabled maturity omits the `isAdult` variable/argument value; it never contains `"isAdult":true`.
- Blank query omits `search`.
- `TV` and `["Action","Fantasy"]` are sent.
- `hasNextPage` produces `nextKey = 2`.
- A response containing one malformed and one valid media item returns only the valid item.

- [ ] **Step 4: Cut `AnimeSearchPagingSource` to Apollo**

Construct:

```kotlin
private fun AnimeFormat.toAniListMediaFormat(): MediaFormat = when (this) {
    AnimeFormat.TV -> MediaFormat.TV
    AnimeFormat.MOVIE -> MediaFormat.MOVIE
    AnimeFormat.OVA -> MediaFormat.OVA
    AnimeFormat.ONA -> MediaFormat.ONA
    AnimeFormat.SPECIAL -> MediaFormat.SPECIAL
    AnimeFormat.MUSIC -> MediaFormat.MUSIC
}

SearchAnimeQuery(
    page = page,
    perPage = params.loadSize.coerceAtMost(25),
    search = criteria.query.takeIf(String::isNotBlank)
        ?.let(Optional::present) ?: Optional.absent(),
    format = filters.format?.toAniListMediaFormat()
        ?.let(Optional::present) ?: Optional.absent(),
    genres = filters.genres.sorted().takeIf { it.isNotEmpty() }
        ?.let(Optional::present) ?: Optional.absent(),
    isAdult = if (filters.includeAdultContent) {
        Optional.absent()
    } else {
        Optional.present(false)
    }
)
```

Execute, call `dataOrAniListError()`, require a non-null `Page`, map valid `animeCardFields`, and preserve cancellation/error behavior and the existing 400 ms append delay.

- [ ] **Step 5: Update repository wiring while Jikan still handles other flows**

Inject both `ApolloClient` and `JikanApi` temporarily. Pass Apollo only to `AnimeSearchPagingSource`; leave ranking, details, genres, and worker on Jikan until Task 5. This intermediate commit must compile.

- [ ] **Step 6: Update ViewModel and Compose UI**

- `removeGenre(name: String)` removes by name.
- Genre list keys and selection use `genre.name`.
- Replace the rating choice group with a checkbox/toggle row bound to `includeAdultContent`.
- Add strings `filters_include_adult` in Portuguese and English.
- Add/remove filter chips using string genre names.
- Remove age-rating labels and imports.

- [ ] **Step 7: Run the complete task test slice**

```bash
./gradlew :app:testDebugUnitTest --tests "*.AnimeFiltersTest" --tests "*.AnimeSearchPagingSourceTest" --tests "*.TopAnimeViewModelTest"
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.animewiki.ui.screens.topAnime.components.AnimeFilterSheetTest
```

Expected: all tests pass; if no emulator is connected, record the instrumentation command as pending and run it in Task 8.

- [ ] **Step 8: Commit**

```bash
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: migrate search and filters to AniList"
```

---

### Task 5: Cut ranking, details, genres, and notifications to AniList

**Files:**

- Modify: `data/paging/TopAnimeRemoteMediator.kt`
- Modify: `data/repository/AnimeRepository.kt`
- Modify: `data/notification/TopAnimeSyncWorker.kt`
- Update/create focused tests for mediator, repository, and worker.

**Interfaces:**

- Removes every production constructor dependency on `JikanApi`.
- Preserves all `AnimeRepository` public signatures.

- [ ] **Step 1: Add failing repository and mediator tests**

Test:

- Top page maps full `AnimeCacheFields` into `AnimeEntity`, including synopsis, genres, studios, dates, status, duration, rank, and trailer.
- A failed refresh does not clear valid cached AniList rows.
- `getAnimeDetails(id)` returns network detail when usable and cached detail on transport/server failure.
- Genre response maps nonblank strings, removes duplicates case-sensitively, sorts case-insensitively, caches a defensive snapshot, and coalesces concurrent refresh.
- GraphQL errors with valid sibling media keep valid items.

- [ ] **Step 2: Convert `TopAnimeRemoteMediator`**

Replace `JikanApi` with `ApolloClient`. Build:

```kotlin
TopAnimeQuery(
    page = page,
    perPage = state.config.pageSize.coerceAtMost(25),
    isAdult = Optional.present(false)
)
```

Execute through `dataOrAniListError()`, require `Page`, map `media.orEmpty().mapNotNull { it?.animeCacheFields?.toEntity(...) }`, and derive `hasNext` from `pageInfo?.hasNextPage == true`.

Keep the Room transaction order: only after successful fetch/mapping, clear keys/anime on refresh, then upsert keys/entities. Never clear Room in a catch path.

- [ ] **Step 3: Convert repository details and genres**

Constructor becomes:

```kotlin
class AnimeRepository @Inject constructor(
    private val apolloClient: ApolloClient,
    private val db: AppDatabase,
    private val favoriteDao: FavoriteDao
)
```

Details execute `AnimeDetailsQuery(id)`, map `data.Media?.animeCacheFields`, and fall back to `animeDao().getById(id)` on any non-cancellation failure. Genres execute `GenreCollectionQuery`, map:

```kotlin
data.genres.orEmpty()
    .mapNotNull { it?.takeIf(String::isNotBlank) }
    .distinct()
    .sortedBy(String::lowercase)
    .map(::AnimeGenre)
```

Retain the existing mutex/`CompletableDeferred` cache behavior and replace the Jikan-specific empty-catalog error message.

- [ ] **Step 4: Convert the weekly worker**

Inject `ApolloClient`. Query page 1/perPage 1 with `isAdult = Optional.present(false)`. Use the full fragment's mapped domain title, AniList id, and score. Return `Result.retry()` for no usable item or any exception, preserving cancellation behavior required by `CoroutineWorker`.

- [ ] **Step 5: Run focused and full unit tests**

```bash
./gradlew :app:testDebugUnitTest --tests "*.TopAnimeRemoteMediatorTest" --tests "*.AnimeRepositoryTest" --tests "*.TopAnimeSyncWorkerTest"
./gradlew :app:testDebugUnitTest
```

Expected: green. Production `rg "JikanApi" app/src/main/java` now prints only the Jikan interface and `NetworkModule` provider, not consumers.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java app/src/test
git commit -m "feat: complete AniList data flow cutover"
```

---

### Task 6: Reset provider-specific Room data at version 3

**Files:**

- Modify: `app/src/main/java/com/example/animewiki/data/local/AppDatabase.kt`
- Create: `app/src/test/java/com/example/animewiki/data/local/AppDatabaseVersionTest.kt`

**Interfaces:**

- Produces Room database version 3.
- Relies on the existing `fallbackToDestructiveMigration(dropAllTables = true)` in `DatabaseModule`.

- [ ] **Step 1: Add a failing assertion for the new version**

Create `AppDatabaseVersionTest.kt`:

```kotlin
package com.example.animewiki.data.local

import androidx.room.Database
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDatabaseVersionTest {
    @Test
    fun `provider boundary uses destructive Room version three`() {
        val annotation = AppDatabase::class.java.getAnnotation(Database::class.java)
        assertEquals(3, annotation?.version)
    }
}
```

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "*.AppDatabaseVersionTest"
```

Expected: FAIL because the annotation reports version 2.

- [ ] **Step 2: Bump the database**

Change only:

```kotlin
version = 3
```

Do not add a `Migration(2, 3)`; the destructive fallback is the accepted product behavior. Do not change entity fields or DAOs.

- [ ] **Step 3: Verify the safety-net behavior on a device**

Install the last Jikan build, add a favorite, then install the AniList build over it:

```bash
git worktree add /private/tmp/animewiki-jikan-baseline 440a0d8
./gradlew -p /private/tmp/animewiki-jikan-baseline :app:installDebug
./gradlew :app:installDebug
```

Open the baseline app before the second install, load ranking, and add one favorite. Expected after installing the AniList build over it: the old favorite is absent, no old ranking row opens as an AniList id, and the new ranking repopulates from AniList. Remove the temporary worktree afterward with:

```bash
git worktree remove /private/tmp/animewiki-jikan-baseline
```

- [ ] **Step 4: Run the focused test and commit**

```bash
./gradlew :app:testDebugUnitTest --tests "*.AppDatabaseVersionTest"
git add app/src/main/java/com/example/animewiki/data/local/AppDatabase.kt app/src/test/java/com/example/animewiki/data/local/AppDatabaseVersionTest.kt
git commit -m "chore: reset local data for AniList ids"
```

---

### Task 7: Remove Jikan and obsolete serialization code

**Files:**

- Delete: `data/remote/JikanApi.kt`
- Delete: `data/remote/dto/AnimeDto.kt`, `AnimeDetailsDto.kt`, `AnimeGenreDto.kt`
- Delete/update Jikan-specific mapper files and tests.
- Modify: `di/NetworkModule.kt` or delete it if `AniListModule` fully replaces it.
- Modify: Gradle/version-catalog files.

**Interfaces:**

- Produces a build with no `api.jikan.moe`, Retrofit, Jikan DTO, or MAL-id references.

- [ ] **Step 1: Prove production code no longer needs Jikan**

Run:

```bash
rg -n "Jikan|api\\.jikan|malId|idMal|retrofit2|kotlinx\\.serialization" app/src/main
```

Expected before deletion: matches are limited to files scheduled for removal and old DI.

- [ ] **Step 2: Delete obsolete source and tests**

Remove Jikan API/DTO files, DTO deserialization tests, genre DTO tests, `AnimeGenreMapper.kt` and its test, and the Jikan overloads in `AnimeMapper.kt`. Keep `FavoriteMapper.kt` and `AnimeEntity.toDomain()`.

- [ ] **Step 3: Remove dependencies and plugin**

Remove Retrofit, converter, serialization JSON aliases and dependencies. Remove the Kotlin serialization plugin alias/application only when the previous `rg` proves no remaining serializer usage. Keep OkHttp and logging because Apollo uses them.

- [ ] **Step 4: Make AniList DI the only network module**

Delete old Retrofit/JSON/Jikan providers. It is acceptable to rename `AniListModule.kt` to `NetworkModule.kt`, but there must be exactly one singleton `ApolloClient` provider and no duplicate `OkHttpClient` binding.

- [ ] **Step 5: Verify absence and compile**

```bash
! rg -n "Jikan|api\\.jikan|malId|idMal|retrofit2" app/src/main
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
```

Expected: no search matches and both Gradle commands succeed.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: remove Jikan backend"
```

---

### Task 8: Documentation, static checks, and end-to-end verification

**Files:**

- Modify: `README.md`
- Modify: this plan only to check completed boxes during execution.

**Interfaces:**

- Produces the releasable, verified migration branch.

- [x] **Step 1: Update README**

Document:

- AniList GraphQL backend and auth-free read scope.
- Apollo 5 as network client and Room as offline ranking/favorites store.
- Adult-toggle semantics: disabled excludes adult media; enabled includes both.
- Name-based genres.
- Retry behavior for 429/5xx.
- Intentional loss of old local data at Room v3.
- No roadmap features added by this migration.

- [ ] **Step 2: Run automated verification**

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:compileDebugKotlin
./gradlew :app:detekt
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

Expected: every command succeeds. If no device was available earlier, connect one now; do not mark this step complete without the instrumentation suite.

- [x] **Step 3: Run final source audits**

```bash
! rg -n "Jikan|api\\.jikan|malId|idMal|AnimeAgeRating|adultContent" app/src/main README.md
rg -n "includeAdultContent|isAdult|graphql\\.anilist\\.co|version = 3" app/src/main README.md
git diff --check
git status --short
```

Expected: the negative search is empty; the positive search shows the new model/queries/endpoint/database version; no whitespace errors; only the intended README/plan changes are uncommitted.

- [ ] **Step 4: Manual emulator smoke test**

Verify:

1. Fresh install loads Discover ranking.
2. Search finds a known title.
3. Format and genre filters work.
4. Adult toggle off excludes adult-only results; on does not hide non-adult results.
5. Details show clean synopsis, genres, studios, dates, status, duration, rank, and YouTube trailer when supplied.
6. Favorite add/remove survives process restart.
7. Airplane mode still shows cached ranking/details and a no-connection state for network-only search.
8. A GraphQL/server failure is shown as server error, not offline.
9. Weekly notification opens the AniList-id details route.

- [x] **Step 5: Commit documentation**

```bash
git add README.md docs/superpowers/plans/2026-07-28-anilist-migration.md
git commit -m "docs: document AniList backend migration"
```

- [ ] **Step 6: Request code review**

Invoke `superpowers:requesting-code-review`, address only verified findings through the receiving-code-review workflow, rerun the affected tests, then run the full verification commands again before claiming completion.

---

## Spec Coverage

- Objective/scope/domain seam: Tasks 0–8 and global constraints.
- Apollo/schema/operations: Tasks 0–1.
- Response errors and partial data: Task 2.
- Rate limiting and retry headers: Task 2.
- Full mapping/offline detail parity: Task 3 and Task 5.
- Content maturity and genre semantics: Task 4.
- Search, ranking, details, genres, notification: Tasks 4–5.
- Intentional clean reset/Room v3: Task 6.
- Jikan removal: Task 7.
- Documentation and automated/manual validation: Task 8.
