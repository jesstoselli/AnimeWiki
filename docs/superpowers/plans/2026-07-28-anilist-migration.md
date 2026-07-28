# AniList Backend Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Jikan REST backend with the AniList GraphQL API (via Apollo Kotlin) at feature parity, keeping Room for offline cache and favorites.

**Architecture:** The domain layer (`Anime`, `AnimeFilters`, `AnimeBrowseCriteria`, `AnimeRepository`) is the stable seam. Only `data/remote` and the filter model/UI change. Apollo is the network layer; Room's `RemoteMediator` and favorites are preserved. Each task ends compiling and green; Jikan is removed only in the final task once nothing references it.

**Tech Stack:** Kotlin 2.1, Jetpack Compose Material 3, Hilt, **Apollo Kotlin 5.0.1** (GraphQL), OkHttp 4.12, Paging 3.3, Room 2.7, Coroutines/StateFlow, JUnit 4, MockK, Turbine, Apollo MockServer + data builders.

**Reference (validated 2026-07-28):** AniList endpoint `POST https://graphql.anilist.co`, no auth for reads, ~90 req/min + burst (429). `Page { pageInfo { currentPage hasNextPage } media(...) }`; `GenreCollection: [String]`; `Media` exposes `id`, `idMal`, `title{english,romaji}`, `coverImage{extraLarge,large}`, `averageScore`(0–100), `episodes`, `format`(MediaFormat), `seasonYear`, `description(asHtml:)`, `genres`, `studios{nodes{name}}`, `status`, `duration`, `startDate/endDate`, `trailer{id,site}`, `rankings{rank,type,allTime}`, `isAdult`.

---

## File Structure

**Create**
- `app/src/main/graphql/schema.graphqls` — AniList schema (downloaded by Apollo).
- `app/src/main/graphql/fragments.graphql` — `AnimeCardFields`, `AnimeDetailsFields`.
- `app/src/main/graphql/TopAnime.graphql`, `SearchAnime.graphql`, `AnimeDetails.graphql`, `AnimeByMalId.graphql`, `GenreCollection.graphql`.
- `app/src/main/java/com/example/animewiki/data/remote/AniListClient` provided in DI.
- `app/src/main/java/com/example/animewiki/data/remote/RetryInterceptor.kt`.
- `app/src/main/java/com/example/animewiki/data/mapper/AniListMapper.kt` — Apollo fragments → domain/entity + helpers.
- `app/src/main/java/com/example/animewiki/data/migration/FavoritesMigration.kt` + `FavoritesMigrationWorker.kt`.
- Tests listed per task.

**Modify**
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — Apollo plugin/deps + codegen config.
- `domain/model/AnimeFilters.kt`, `AnimeFormat.kt`, `AnimeGenre.kt`; delete `AnimeAgeRating.kt`.
- `data/paging/AnimeSearchPagingSource.kt`, `data/paging/TopAnimeRemoteMediator.kt`.
- `data/repository/AnimeRepository.kt`.
- `data/notification/TopAnimeSyncWorker.kt`.
- `di/NetworkModule.kt`.
- `ui/screens/topAnime/TopAnimeViewModel.kt`, `components/AnimeFilterSheet.kt`, `AnimeFilterBar.kt`, `AnimeFilterLabels.kt`.
- `ui/common/LoadErrorType.kt`.
- `data/local/dao/FavoriteDao.kt` (add `getAll`).
- `res/values/strings.xml`, `res/values-en/strings.xml`.
- `README.md`.

**Remove (Task 7)**
- `data/remote/JikanApi.kt`, `data/remote/dto/AnimeDto.kt`, `AnimeDetailsDto.kt`, `AnimeGenreDto.kt`, `data/mapper/AnimeGenreMapper.kt`, the Jikan-specific `toEntity`/`toDomain(AnimeDto)` in `AnimeMapper.kt`, Retrofit dependencies.

---

### Task 1: Add Apollo, download the AniList schema, and configure codegen

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/graphql/GenreCollection.graphql`

- [ ] **Step 1: Add Apollo to the version catalog**

In `gradle/libs.versions.toml`, under `[versions]` add:

```toml
apollo = "5.0.1"
```

Under `[libraries]` add:

```toml
apollo-runtime = { module = "com.apollographql.apollo:apollo-runtime", version.ref = "apollo" }
apollo-mockserver = { module = "com.apollographql.apollo:apollo-mockserver", version.ref = "apollo" }
```

Under `[plugins]` add:

```toml
apollo = { id = "com.apollographql.apollo", version.ref = "apollo" }
```

- [ ] **Step 2: Apply the plugin, add deps, and configure the Apollo service**

In `app/build.gradle.kts`, add to the `plugins { }` block:

```kotlin
alias(libs.plugins.apollo)
```

Add to `dependencies { }`:

```kotlin
implementation(libs.apollo.runtime)
testImplementation(libs.apollo.mockserver)
```

Add a top-level `apollo { }` block (sibling of `android { }`):

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

- [ ] **Step 3: Add the first operation so codegen has an entry point**

Create `app/src/main/graphql/GenreCollection.graphql`:

```graphql
query GenreCollection {
  GenreCollection
}
```

- [ ] **Step 4: Download the schema**

Run:

```bash
./gradlew :app:downloadAnilistApolloSchemaFromIntrospection
```

Expected: `BUILD SUCCESSFUL` and `app/src/main/graphql/schema.graphqls` created. Confirm the file exists and is non-empty:

```bash
test -s app/src/main/graphql/schema.graphqls && echo "schema OK"
```

- [ ] **Step 5: Verify codegen compiles**

Run:

```bash
./gradlew :app:generateAnilistApolloSources
```

Expected: `BUILD SUCCESSFUL`; generated `GenreCollectionQuery` exists under `app/build/generated/source/apollo/`.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/graphql/GenreCollection.graphql app/src/main/graphql/schema.graphqls
git commit -m "chore: add Apollo Kotlin and AniList schema"
```

---

### Task 2: Define GraphQL fragments and operations

**Files:**
- Create: `app/src/main/graphql/fragments.graphql`
- Create: `app/src/main/graphql/TopAnime.graphql`
- Create: `app/src/main/graphql/SearchAnime.graphql`
- Create: `app/src/main/graphql/AnimeDetails.graphql`
- Create: `app/src/main/graphql/AnimeByMalId.graphql`

- [ ] **Step 1: Create the shared fragments**

`app/src/main/graphql/fragments.graphql`:

```graphql
fragment AnimeCardFields on Media {
  id
  idMal
  title { english romaji }
  coverImage { extraLarge large }
  averageScore
  episodes
  format
  seasonYear
}

fragment AnimeDetailsFields on Media {
  id
  idMal
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

- [ ] **Step 2: Create the operations**

`TopAnime.graphql`:

```graphql
query TopAnime($page: Int!, $perPage: Int!) {
  Page(page: $page, perPage: $perPage) {
    pageInfo { currentPage hasNextPage }
    media(type: ANIME, sort: SCORE_DESC) { ...AnimeCardFields }
  }
}
```

`SearchAnime.graphql`:

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
      sort: SCORE_DESC
    ) { ...AnimeCardFields }
  }
}
```

`AnimeDetails.graphql`:

```graphql
query AnimeDetails($id: Int!) {
  Media(id: $id, type: ANIME) { ...AnimeDetailsFields }
}
```

`AnimeByMalId.graphql`:

```graphql
query AnimeByMalId($idMal: Int!) {
  Media(idMal: $idMal, type: ANIME) { ...AnimeCardFields }
}
```

- [ ] **Step 3: Verify codegen**

Run:

```bash
./gradlew :app:generateAnilistApolloSources
```

Expected: `BUILD SUCCESSFUL`; generated `TopAnimeQuery`, `SearchAnimeQuery`, `AnimeDetailsQuery`, `AnimeByMalIdQuery`, and fragment models `AnimeCardFields`, `AnimeDetailsFields` under `app/build/generated/source/apollo/`. Confirm:

```bash
find app/build/generated/source/apollo -name "SearchAnimeQuery.kt" -o -name "AnimeCardFields.kt" | sort
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/graphql/fragments.graphql app/src/main/graphql/TopAnime.graphql app/src/main/graphql/SearchAnime.graphql app/src/main/graphql/AnimeDetails.graphql app/src/main/graphql/AnimeByMalId.graphql
git commit -m "feat: add AniList GraphQL operations and fragments"
```

---

### Task 3: Map AniList fragments to domain and entity

Generated fragment models expose nullable fields. The mapper skips entries missing an essential field (title or image), converts the 0–100 score to 0–10, strips residual HTML from the description, and maps the AniList enums to display strings.

**Files:**
- Create: `app/src/main/java/com/example/animewiki/data/mapper/AniListMapper.kt`
- Test: `app/src/test/java/com/example/animewiki/data/mapper/AniListMapperTest.kt`

- [ ] **Step 1: Write the failing mapper tests (using Apollo data builders)**

`app/src/test/java/com/example/animewiki/data/mapper/AniListMapperTest.kt`:

```kotlin
package com.example.animewiki.data.mapper

import com.example.animewiki.graphql.fragment.AnimeCardFields
import com.example.animewiki.graphql.fragment.AnimeDetailsFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AniListMapperTest {

    @Test
    fun `card maps id title image and rescales score to ten`() {
        val card = AnimeCardFields {
            id = 154587
            idMal = 52991
            title = title { english = "Frieren" }
            coverImage = coverImage { extraLarge = "https://img/large.jpg" }
            averageScore = 91
            episodes = 28
            format = com.example.animewiki.graphql.type.MediaFormat.TV
            seasonYear = 2023
        }

        val anime = card.toDomain()!!

        assertEquals(154587, anime.id)
        assertEquals("Frieren", anime.title)
        assertEquals("https://img/large.jpg", anime.imageUrl)
        assertEquals(9.1, anime.score!!, 0.001)
        assertEquals("TV", anime.type)
        assertEquals(2023, anime.year)
    }

    @Test
    fun `card falls back to romaji title and large cover`() {
        val card = AnimeCardFields {
            id = 1
            title = title { romaji = "Sousou no Frieren" }
            coverImage = coverImage { large = "https://img/small.jpg" }
        }

        val anime = card.toDomain()!!

        assertEquals("Sousou no Frieren", anime.title)
        assertEquals("https://img/small.jpg", anime.imageUrl)
        assertNull(anime.score)
    }

    @Test
    fun `card with no usable title is skipped`() {
        val card = AnimeCardFields {
            id = 1
            coverImage = coverImage { large = "https://img/x.jpg" }
        }

        assertNull(card.toDomain())
    }

    @Test
    fun `card with no image is skipped`() {
        val card = AnimeCardFields {
            id = 1
            title = title { english = "No Image" }
        }

        assertNull(card.toDomain())
    }

    @Test
    fun `details strips html and picks youtube trailer id`() {
        val details = AnimeDetailsFields {
            id = 1
            title = title { english = "Show" }
            coverImage = coverImage { large = "https://img/x.jpg" }
            description = "Line one.<br>\n<i>Line two.</i>"
            genres = listOf("Action", "Fantasy")
            trailer = trailer { id = "abc123"; site = "youtube" }
        }

        val anime = details.toDomain()!!

        assertEquals("Line one.\nLine two.", anime.synopsis)
        assertEquals(listOf("Action", "Fantasy"), anime.genres)
        assertEquals("abc123", anime.trailerYoutubeId)
    }

    @Test
    fun `details ignores non-youtube trailer`() {
        val details = AnimeDetailsFields {
            id = 1
            title = title { english = "Show" }
            coverImage = coverImage { large = "https://img/x.jpg" }
            trailer = trailer { id = "abc123"; site = "dailymotion" }
        }

        assertNull(details.toDomain()!!.trailerYoutubeId)
    }
}
```

- [ ] **Step 2: Run the tests and verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.animewiki.data.mapper.AniListMapperTest"
```

Expected: compilation fails — `toDomain()` for the fragments does not exist.

- [ ] **Step 3: Implement the mapper**

`app/src/main/java/com/example/animewiki/data/mapper/AniListMapper.kt`:

```kotlin
package com.example.animewiki.data.mapper

import com.example.animewiki.data.local.entity.AnimeEntity
import com.example.animewiki.domain.model.Anime
import com.example.animewiki.graphql.fragment.AnimeCardFields
import com.example.animewiki.graphql.fragment.AnimeDetailsFields
import com.example.animewiki.graphql.type.MediaFormat
import com.example.animewiki.graphql.type.MediaRankType
import com.example.animewiki.graphql.type.MediaStatus

private val HTML_TAG = Regex("<[^>]+>")

internal fun String.stripHtml(): String =
    replace("<br>", "\n", ignoreCase = true)
        .replace("<br/>", "\n", ignoreCase = true)
        .replace("<br />", "\n", ignoreCase = true)
        .replace(HTML_TAG, "")
        .trim()

private fun Int?.scoreToTen(): Double? = this?.let { it / 10.0 }

private fun MediaFormat?.displayName(): String? = when (this) {
    MediaFormat.TV -> "TV"
    MediaFormat.TV_SHORT -> "TV Short"
    MediaFormat.MOVIE -> "Movie"
    MediaFormat.SPECIAL -> "Special"
    MediaFormat.OVA -> "OVA"
    MediaFormat.ONA -> "ONA"
    MediaFormat.MUSIC -> "Music"
    else -> null
}

private fun MediaStatus?.displayName(): String? = when (this) {
    MediaStatus.FINISHED -> "Finished"
    MediaStatus.RELEASING -> "Releasing"
    MediaStatus.NOT_YET_RELEASED -> "Not yet released"
    MediaStatus.CANCELLED -> "Cancelled"
    MediaStatus.HIATUS -> "Hiatus"
    else -> null
}

fun AnimeCardFields.toDomain(): Anime? {
    val title = title?.english ?: title?.romaji ?: return null
    val imageUrl = coverImage?.extraLarge ?: coverImage?.large ?: return null
    return Anime(
        id = id,
        title = title,
        imageUrl = imageUrl,
        score = averageScore.scoreToTen(),
        episodes = episodes,
        type = format.displayName(),
        year = seasonYear,
        synopsis = null
    )
}

fun AnimeCardFields.toEntity(pageIndex: Int): AnimeEntity? {
    val title = title?.english ?: title?.romaji ?: return null
    val imageUrl = coverImage?.extraLarge ?: coverImage?.large ?: return null
    return AnimeEntity(
        id = id,
        title = title,
        imageUrl = imageUrl,
        score = averageScore.scoreToTen(),
        episodes = episodes,
        type = format.displayName(),
        year = seasonYear,
        synopsis = null,
        genres = emptyList(),
        studios = emptyList(),
        aired = null,
        status = null,
        rating = null,
        duration = null,
        rank = null,
        trailerYoutubeId = null,
        pageIndex = pageIndex
    )
}

fun AnimeDetailsFields.toDomain(): Anime? {
    val title = title?.english ?: title?.romaji ?: return null
    val imageUrl = coverImage?.extraLarge ?: coverImage?.large ?: return null
    val airedFrom = startDate?.year
    val airedTo = endDate?.year
    val aired = when {
        airedFrom == null -> null
        airedTo == null || airedTo == airedFrom -> airedFrom.toString()
        else -> "$airedFrom - $airedTo"
    }
    val ratedRank = rankings
        ?.filterNotNull()
        ?.firstOrNull { it.type == MediaRankType.RATED && it.allTime == true }
        ?.rank
    val youtubeId = trailer?.takeIf { it.site == "youtube" }?.id
    return Anime(
        id = id,
        title = title,
        imageUrl = imageUrl,
        score = averageScore.scoreToTen(),
        episodes = episodes,
        type = format.displayName(),
        year = seasonYear,
        synopsis = description?.stripHtml()?.takeIf { it.isNotEmpty() },
        genres = genres?.filterNotNull().orEmpty(),
        studios = studios?.nodes?.filterNotNull()?.mapNotNull { it.name }.orEmpty(),
        aired = aired,
        status = status.displayName(),
        rating = null,
        duration = duration?.let { "$it min per ep" },
        rank = ratedRank,
        trailerYoutubeId = youtubeId
    )
}
```

- [ ] **Step 4: Run the tests and verify GREEN**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.animewiki.data.mapper.AniListMapperTest"
```

Expected: `BUILD SUCCESSFUL`, 6 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/animewiki/data/mapper/AniListMapper.kt app/src/test/java/com/example/animewiki/data/mapper/AniListMapperTest.kt
git commit -m "feat: map AniList fragments to domain and entity"
```

---

### Task 4: Add the retry interceptor and classify Apollo errors

**Files:**
- Create: `app/src/main/java/com/example/animewiki/data/remote/RetryInterceptor.kt`
- Modify: `app/src/main/java/com/example/animewiki/ui/common/LoadErrorType.kt`
- Test: `app/src/test/java/com/example/animewiki/data/remote/RetryInterceptorTest.kt`
- Test: `app/src/test/java/com/example/animewiki/ui/common/LoadErrorTypeTest.kt` (add cases)

- [ ] **Step 1: Write the failing RetryInterceptor test**

`app/src/test/java/com/example/animewiki/data/remote/RetryInterceptorTest.kt`:

```kotlin
package com.example.animewiki.data.remote

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class RetryInterceptorTest {

    private val request = Request.Builder().url("https://graphql.anilist.co/").build()

    private fun response(code: Int): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("msg")
        .body("{}".toResponseBody("application/json".toMediaType()))
        .build()

    private fun chain(vararg responses: Response): Interceptor.Chain {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } returnsMany responses.toList()
        return chain
    }

    @Test
    fun `success passes through without retry`() {
        val delays = mutableListOf<Long>()
        val result = RetryInterceptor(sleep = { delays.add(it) }).intercept(chain(response(200)))
        assertEquals(200, result.code)
        assertEquals(emptyList<Long>(), delays)
    }

    @Test
    fun `retries 504 then returns success with exponential backoff`() {
        val delays = mutableListOf<Long>()
        val c = chain(response(504), response(504), response(200))
        val result = RetryInterceptor(maxRetries = 3, baseDelayMillis = 1000, sleep = { delays.add(it) })
            .intercept(c)
        assertEquals(200, result.code)
        verify(exactly = 3) { c.proceed(request) }
        assertEquals(listOf(1000L, 2000L), delays)
    }

    @Test
    fun `gives up after max retries`() {
        val delays = mutableListOf<Long>()
        val c = chain(response(504), response(504), response(504), response(504))
        val result = RetryInterceptor(maxRetries = 3, baseDelayMillis = 1000, sleep = { delays.add(it) })
            .intercept(c)
        assertEquals(504, result.code)
        verify(exactly = 4) { c.proceed(request) }
        assertEquals(listOf(1000L, 2000L, 4000L), delays)
    }

    @Test
    fun `retries rate limiting 429`() {
        val c = chain(response(429), response(200))
        assertEquals(200, RetryInterceptor(sleep = {}).intercept(c).code)
        verify(exactly = 2) { c.proceed(request) }
    }

    @Test
    fun `does not retry non-retryable 404`() {
        val c = chain(response(404))
        assertEquals(404, RetryInterceptor(sleep = {}).intercept(c).code)
        verify(exactly = 1) { c.proceed(request) }
    }
}
```

- [ ] **Step 2: Run and verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.animewiki.data.remote.RetryInterceptorTest"
```

Expected: compilation fails — `RetryInterceptor` does not exist.

- [ ] **Step 3: Implement RetryInterceptor**

`app/src/main/java/com/example/animewiki/data/remote/RetryInterceptor.kt`:

```kotlin
package com.example.animewiki.data.remote

import okhttp3.Interceptor
import okhttp3.Response

class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val retryableStatuses: Set<Int> = setOf(429, 500, 502, 503, 504),
    private val baseDelayMillis: Long = 1000L,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) }
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        var attempt = 0
        while (response.code in retryableStatuses && attempt < maxRetries) {
            response.close()
            sleep(baseDelayMillis shl attempt)
            attempt++
            response = chain.proceed(request)
        }
        return response
    }
}
```

- [ ] **Step 4: Run and verify GREEN**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.animewiki.data.remote.RetryInterceptorTest"
```

Expected: `BUILD SUCCESSFUL`, 5 tests, 0 failures.

- [ ] **Step 5: Add failing Apollo cases to the error classifier test**

Append to `app/src/test/java/com/example/animewiki/ui/common/LoadErrorTypeTest.kt`:

```kotlin
    @Test
    fun `apollo network exception is no connection`() {
        val e = com.apollographql.apollo.exception.ApolloNetworkException("down")
        assertEquals(LoadErrorType.NO_CONNECTION, e.toLoadErrorType())
    }

    @Test
    fun `apollo http exception is server error`() {
        val e = com.apollographql.apollo.exception.ApolloHttpException(
            statusCode = 500,
            headers = emptyList(),
            body = null,
            message = "500"
        )
        assertEquals(LoadErrorType.SERVER, e.toLoadErrorType())
    }
```

- [ ] **Step 6: Run and verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.animewiki.ui.common.LoadErrorTypeTest"
```

Expected: the two new tests fail — `ApolloNetworkException` currently falls through to `SERVER`.

- [ ] **Step 7: Extend the classifier**

Replace the body of `toLoadErrorType()` in `app/src/main/java/com/example/animewiki/ui/common/LoadErrorType.kt` with:

```kotlin
fun Throwable.toLoadErrorType(): LoadErrorType = when (this) {
    is java.io.IOException -> LoadErrorType.NO_CONNECTION
    is com.apollographql.apollo.exception.ApolloNetworkException -> LoadErrorType.NO_CONNECTION
    else -> LoadErrorType.SERVER
}
```

Keep the existing enum and KDoc.

- [ ] **Step 8: Run and verify GREEN**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.animewiki.ui.common.LoadErrorTypeTest"
```

Expected: `BUILD SUCCESSFUL`, all cases pass.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/example/animewiki/data/remote/RetryInterceptor.kt app/src/test/java/com/example/animewiki/data/remote/RetryInterceptorTest.kt app/src/main/java/com/example/animewiki/ui/common/LoadErrorType.kt app/src/test/java/com/example/animewiki/ui/common/LoadErrorTypeTest.kt
git commit -m "feat: add retry interceptor and classify Apollo network errors"
```

---

### Task 5: Provide the ApolloClient (alongside Jikan, temporarily)

Add an `ApolloClient` provider so later tasks can inject it. Jikan providers stay until Task 9 so the app keeps compiling.

**Files:**
- Modify: `app/src/main/java/com/example/animewiki/di/NetworkModule.kt`

- [ ] **Step 1: Add the ApolloClient provider**

Add these imports to `NetworkModule.kt`:

```kotlin
import com.apollographql.apollo.ApolloClient
import com.example.animewiki.data.remote.RetryInterceptor
```

Add inside the `object NetworkModule`:

```kotlin
    private const val ANILIST_URL = "https://graphql.anilist.co"

    @Provides
    @Singleton
    fun provideApolloClient(): ApolloClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val okHttp = OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor())
            .addInterceptor(logging)
            .build()
        return ApolloClient.Builder()
            .serverUrl(ANILIST_URL)
            .okHttpClient(okHttp)
            .build()
    }
```

- [ ] **Step 2: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL` (both `provideJikanApi` and `provideApolloClient` present).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/animewiki/di/NetworkModule.kt
git commit -m "chore: provide AniList ApolloClient"
```

---

### Task 6: Switch the filter model and filtered search to AniList

This task changes the filter domain model to AniList semantics (string genres, `isAdult` toggle, no age rating) and updates every consumer plus the search PagingSource in one compiling unit.

**Files:**
- Modify: `domain/model/AnimeFilters.kt`, `AnimeFormat.kt`; delete `AnimeAgeRating.kt`; modify `AnimeGenre.kt`
- Modify: `data/paging/AnimeSearchPagingSource.kt`, `data/repository/AnimeRepository.kt`
- Modify: `ui/screens/topAnime/TopAnimeViewModel.kt`, `components/AnimeFilterSheet.kt`, `AnimeFilterBar.kt`, `AnimeFilterLabels.kt`
- Modify: `res/values/strings.xml`, `res/values-en/strings.xml`
- Test: `domain/model/AnimeFiltersTest.kt` (rewrite), `data/paging/AnimeSearchPagingSourceTest.kt` (rewrite)

- [ ] **Step 1: Rewrite the domain filter tests (RED)**

Replace `app/src/test/java/com/example/animewiki/domain/model/AnimeFiltersTest.kt` with:

```kotlin
package com.example.animewiki.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeFiltersTest {
    @Test
    fun `genres list is sorted and null when empty`() {
        assertEquals(listOf("Action", "Fantasy"), AnimeFilters(genres = setOf("Fantasy", "Action")).genresList)
        assertNull(AnimeFilters().genresList)
    }

    @Test
    fun `active count includes format adult toggle and each genre`() {
        val filters = AnimeFilters(
            format = AnimeFormat.TV,
            adultContent = true,
            genres = setOf("Action", "Fantasy")
        )
        assertEquals(4, filters.activeCount)
        assertFalse(filters.isEmpty)
    }

    @Test
    fun `default filters are empty`() {
        assertTrue(AnimeFilters().isEmpty)
        assertEquals(0, AnimeFilters().activeCount)
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

- [ ] **Step 2: Run and verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.animewiki.domain.model.AnimeFiltersTest"
```

Expected: compilation fails — `genres`, `adultContent`, `genresList` do not exist.

- [ ] **Step 3: Update the domain models**

Replace `app/src/main/java/com/example/animewiki/domain/model/AnimeFilters.kt` with:

```kotlin
package com.example.animewiki.domain.model

import java.util.Collections

class AnimeFilters(
    val format: AnimeFormat? = null,
    val adultContent: Boolean = false,
    genres: Set<String> = emptySet()
) {
    val genres: Set<String> = Collections.unmodifiableSet(genres.toSet())

    val isEmpty: Boolean
        get() = format == null && !adultContent && genres.isEmpty()

    val activeCount: Int
        get() = (if (format != null) 1 else 0) + (if (adultContent) 1 else 0) + genres.size

    val genresList: List<String>?
        get() = genres.sorted().ifEmpty { null }

    fun copy(
        format: AnimeFormat? = this.format,
        adultContent: Boolean = this.adultContent,
        genres: Set<String> = this.genres
    ): AnimeFilters = AnimeFilters(format, adultContent, genres)

    override fun equals(other: Any?): Boolean =
        this === other || other is AnimeFilters &&
            format == other.format && adultContent == other.adultContent && genres == other.genres

    override fun hashCode(): Int {
        var result = format?.hashCode() ?: 0
        result = 31 * result + adultContent.hashCode()
        result = 31 * result + genres.hashCode()
        return result
    }

    override fun toString(): String =
        "AnimeFilters(format=$format, adultContent=$adultContent, genres=$genres)"
}
```

Replace `app/src/main/java/com/example/animewiki/domain/model/AnimeFormat.kt` with:

```kotlin
package com.example.animewiki.domain.model

enum class AnimeFormat {
    TV, MOVIE, OVA, ONA, SPECIAL, MUSIC
}
```

Replace `app/src/main/java/com/example/animewiki/domain/model/AnimeGenre.kt` with:

```kotlin
package com.example.animewiki.domain.model

data class AnimeGenre(val name: String)
```

Delete the age-rating model:

```bash
git rm app/src/main/java/com/example/animewiki/domain/model/AnimeAgeRating.kt
```

- [ ] **Step 4: Run the domain test and verify GREEN**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.animewiki.domain.model.AnimeFiltersTest"
```

Expected: `BUILD SUCCESSFUL`, 4 tests, 0 failures. (Other modules do not compile yet — that is fixed in the following steps before the task's final commit.)

- [ ] **Step 5: Add the AnimeFormat → MediaFormat mapping**

Append to `app/src/main/java/com/example/animewiki/data/mapper/AniListMapper.kt`:

```kotlin
import com.example.animewiki.domain.model.AnimeFormat

fun AnimeFormat.toMediaFormat(): MediaFormat = when (this) {
    AnimeFormat.TV -> MediaFormat.TV
    AnimeFormat.MOVIE -> MediaFormat.MOVIE
    AnimeFormat.OVA -> MediaFormat.OVA
    AnimeFormat.ONA -> MediaFormat.ONA
    AnimeFormat.SPECIAL -> MediaFormat.SPECIAL
    AnimeFormat.MUSIC -> MediaFormat.MUSIC
}
```

(Place the `import` with the other imports at the top of the file.)

- [ ] **Step 6: Rewrite the search PagingSource test (RED)**

Replace `app/src/test/java/com/example/animewiki/data/paging/AnimeSearchPagingSourceTest.kt` with a MockServer-based test that asserts the outgoing variables:

```kotlin
package com.example.animewiki.data.paging

import androidx.paging.PagingSource
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.testing.MockServer
import com.example.animewiki.domain.model.AnimeBrowseCriteria
import com.example.animewiki.domain.model.AnimeFilters
import com.example.animewiki.domain.model.AnimeFormat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnimeSearchPagingSourceTest {
    private lateinit var server: MockServer
    private lateinit var apollo: ApolloClient

    @Before
    fun setUp() = runTest {
        server = MockServer()
        apollo = ApolloClient.Builder().serverUrl(server.url()).build()
    }

    @After
    fun tearDown() {
        apollo.close()
        server.close()
    }

    private val emptyPage =
        """{"data":{"Page":{"pageInfo":{"currentPage":1,"hasNextPage":false},"media":[]}}}"""

    private fun refresh() = PagingSource.LoadParams.Refresh<Int>(
        key = null, loadSize = 25, placeholdersEnabled = false
    )

    @Test
    fun `sends normalized query and all active filters`() = runTest {
        server.enqueueString(emptyPage)
        val criteria = AnimeBrowseCriteria.create(
            query = " frieren ",
            filters = AnimeFilters(format = AnimeFormat.TV, adultContent = true, genres = setOf("Fantasy", "Action"))
        )

        val result = AnimeSearchPagingSource(apollo, criteria).load(refresh())
        assertTrue(result is PagingSource.LoadResult.Page)

        val body = server.takeRequest().body.utf8()
        assertTrue(body.contains("\"search\":\"frieren\""))
        assertTrue(body.contains("\"format\":\"TV\""))
        assertTrue(body.contains("\"isAdult\":true"))
        assertTrue(body.contains("\"genres\":[\"Action\",\"Fantasy\"]"))
    }

    @Test
    fun `filter-only load omits blank search`() = runTest {
        server.enqueueString(emptyPage)
        val criteria = AnimeBrowseCriteria.create(filters = AnimeFilters(format = AnimeFormat.MOVIE))

        AnimeSearchPagingSource(apollo, criteria).load(refresh())

        val body = server.takeRequest().body.utf8()
        assertTrue(body.contains("\"format\":\"MOVIE\""))
        assertTrue(!body.contains("\"search\":\"\""))
    }
}
```

- [ ] **Step 7: Run and verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.animewiki.data.paging.AnimeSearchPagingSourceTest"
```

Expected: compilation fails — `AnimeSearchPagingSource` still takes `JikanApi`.

- [ ] **Step 8: Rewrite the search PagingSource on Apollo**

Replace `app/src/main/java/com/example/animewiki/data/paging/AnimeSearchPagingSource.kt` with:

```kotlin
package com.example.animewiki.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.example.animewiki.data.mapper.toDomain
import com.example.animewiki.data.mapper.toMediaFormat
import com.example.animewiki.domain.model.Anime
import com.example.animewiki.domain.model.AnimeBrowseCriteria
import com.example.animewiki.graphql.SearchAnimeQuery
import kotlinx.coroutines.delay

class AnimeSearchPagingSource(
    private val apollo: ApolloClient,
    private val criteria: AnimeBrowseCriteria
) : PagingSource<Int, Anime>() {

    override fun getRefreshKey(state: PagingState<Int, Anime>): Int? =
        state.anchorPosition?.let { anchor ->
            val page = state.closestPageToPosition(anchor)
            page?.prevKey?.plus(1) ?: page?.nextKey?.minus(1)
        }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Anime> {
        val page = params.key ?: 1
        return try {
            if (page > 1) delay(400)
            val filters = criteria.filters
            val response = apollo.query(
                SearchAnimeQuery(
                    page = page,
                    perPage = params.loadSize.coerceAtMost(25),
                    search = Optional.presentIfNotNull(criteria.query.ifBlank { null }),
                    format = Optional.presentIfNotNull(filters.format?.toMediaFormat()),
                    genres = Optional.presentIfNotNull(filters.genresList),
                    isAdult = Optional.present(filters.adultContent)
                )
            ).execute()

            if (response.hasErrors()) {
                return LoadResult.Error(RuntimeException(response.errors?.firstOrNull()?.message ?: "GraphQL error"))
            }
            val pageData = response.data?.Page
            val items = pageData?.media?.filterNotNull()?.mapNotNull { it.animeCardFields.toDomain() }.orEmpty()
            val hasNext = pageData?.pageInfo?.hasNextPage == true

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

> Note: the generated accessor for a spread fragment is `it.animeCardFields` (decapitalized fragment name). If codegen differs, use the name shown under `SearchAnimeQuery.Medium`.

- [ ] **Step 9: Point the repository's search at Apollo**

In `app/src/main/java/com/example/animewiki/data/repository/AnimeRepository.kt`:
- Add constructor parameter `private val apollo: ApolloClient` (add `import com.apollographql.apollo.ApolloClient`).
- Change the `searchAnime` factory to `pagingSourceFactory = { AnimeSearchPagingSource(apollo, criteria) }`.

(`topAnime`, `getAnimeDetails`, and `getAnimeGenres` still use `api` — unchanged in this task.)

- [ ] **Step 10: Update the ViewModel filter API**

In `app/src/main/java/com/example/animewiki/ui/screens/topAnime/TopAnimeViewModel.kt`, replace `removeGenre`:

```kotlin
    fun removeGenre(name: String) {
        applyFilters(_filters.value.copy(genres = _filters.value.genres - name))
    }
```

- [ ] **Step 11: Update the filter UI**

Replace `app/src/main/java/com/example/animewiki/ui/screens/topAnime/components/AnimeFilterLabels.kt` with:

```kotlin
package com.example.animewiki.ui.screens.topAnime.components

import androidx.annotation.StringRes
import com.example.animewiki.R
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
```

In `AnimeFilterSheet.kt`, replace the rating `FilterChoiceGroup` item (the `item { FilterChoiceGroup(title = R.string.filters_rating, ...) }` block) with an adult-content toggle item:

```kotlin
        item {
            AdultContentToggle(
                checked = draft.adultContent,
                onCheckedChange = { onDraftChange(draft.copy(adultContent = it)) }
            )
        }
```

Change the genres content branch to key by name and use `draft.genres`:

```kotlin
            is AnimeGenresState.Content -> items(genresState.genres, key = { it.name }) { genre ->
                GenreFilterRow(
                    name = genre.name,
                    selected = genre.name in draft.genres,
                    onSelectedChange = { selected ->
                        val genres = if (selected) draft.genres + genre.name else draft.genres - genre.name
                        onDraftChange(draft.copy(genres = genres))
                    }
                )
            }
```

Add the toggle composable and required imports (`androidx.compose.material3.Switch`) near `FilterSheetActions`:

```kotlin
@Composable
private fun AdultContentToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeightIn(min = 48.dp)
            .toggleable(value = checked, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp)
            .semantics { role = Role.Switch },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.filters_adult), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
```

Remove the now-unused `import com.example.animewiki.domain.model.AnimeAgeRating`.

In `AnimeFilterBar.kt`:
- Change `val genreNames = genres.associateBy(AnimeGenre::id)` to `val genreNames = genres.map { it.name }.toSet()`.
- Replace the rating chip block with an adult chip:

```kotlin
        if (filters.adultContent) {
            RemovableFilterChip(stringResource(R.string.filters_adult)) {
                onChange(filters.copy(adultContent = false))
            }
        }
```

- Replace the genre chips block with:

```kotlin
        filters.genres.sorted().forEach { name ->
            if (name in genreNames) {
                RemovableFilterChip(name) { onChange(filters.copy(genres = filters.genres - name)) }
            }
        }
```

Remove the unused `format.labelRes()` import only if the compiler flags it (format chip stays).

- [ ] **Step 12: Update strings**

In `res/values/strings.xml`, remove the six `filter_rating_*` strings and the `filters_rating` string, and add:

```xml
    <string name="filters_adult">Mostrar conteúdo adulto</string>
```

In `res/values-en/strings.xml`, do the same and add:

```xml
    <string name="filters_adult">Show adult content</string>
```

- [ ] **Step 13: Run the affected tests and compile**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.animewiki.domain.model.AnimeFiltersTest" --tests "com.example.animewiki.data.paging.AnimeSearchPagingSourceTest"
./gradlew :app:compileDebugKotlin
```

Expected: both test classes pass; the app compiles. Fix any remaining references to `rating`/`genreIds`/`AnimeAgeRating` that the compiler reports (search: `git grep -n "genreIds\|AnimeAgeRating\|\.rating"`).

- [ ] **Step 14: Commit**

```bash
git add -A
git commit -m "feat: switch filters and search to AniList (string genres, adult toggle)"
```

---

### Task 7: Move ranking, details, and genres to AniList

**Files:**
- Modify: `data/paging/TopAnimeRemoteMediator.kt`, `data/repository/AnimeRepository.kt`, `data/notification/TopAnimeSyncWorker.kt`
- Test: `data/repository/AnimeRepositoryTest.kt` (genre cache uses Apollo)

- [ ] **Step 1: Rewrite the RemoteMediator on Apollo**

In `app/src/main/java/com/example/animewiki/data/paging/TopAnimeRemoteMediator.kt`:
- Replace the constructor `private val api: JikanApi` with `private val apollo: ApolloClient` (`import com.apollographql.apollo.ApolloClient`, `import com.example.animewiki.graphql.TopAnimeQuery`, `import com.example.animewiki.data.mapper.toEntity`).
- Replace the network call block (the `api.getTopAnime(...)` region) with:

```kotlin
            if (loadType == LoadType.APPEND) delay(400)
            val response = apollo.query(
                TopAnimeQuery(page = page, perPage = state.config.pageSize)
            ).execute()
            if (response.hasErrors()) {
                return MediatorResult.Error(
                    RuntimeException(response.errors?.firstOrNull()?.message ?: "GraphQL error")
                )
            }
            val pageData = response.data?.Page
            val hasNext = pageData?.pageInfo?.hasNextPage == true
            val cards = pageData?.media?.filterNotNull().orEmpty()

            val baseIndex = if (loadType == LoadType.REFRESH) 0 else db.animeDao().maxPageIndex() + 1
            val entities = cards.mapIndexedNotNull { i, m -> m.animeCardFields.toEntity(baseIndex + i) }
```

Keep the existing `db.withTransaction { ... }` block and the `MediatorResult.Success(endOfPaginationReached = !hasNext)` return unchanged.

- [ ] **Step 2: Point the repository at Apollo for ranking, details, and genres**

In `app/src/main/java/com/example/animewiki/data/repository/AnimeRepository.kt`:
- `topAnime()`: `remoteMediator = TopAnimeRemoteMediator(apollo, db)`.
- Add imports: `com.example.animewiki.graphql.AnimeDetailsQuery`, `com.example.animewiki.graphql.GenreCollectionQuery`, `com.example.animewiki.data.mapper.toDomain`, `com.example.animewiki.domain.model.AnimeGenre`.
- Replace `getAnimeDetails` body's network line `api.getAnimeDetails(id).data?.toDomain() ?: cached` with:

```kotlin
            val response = apollo.query(AnimeDetailsQuery(id = id)).execute()
            response.data?.Media?.animeDetailsFields?.toDomain() ?: cached
```

- Replace the genre fetch line inside `getAnimeGenres` (`val genres = api.getAnimeGenres().data.orEmpty().mapNotNull { it.toDomain() }.sortedBy { it.name.lowercase() }`) with:

```kotlin
                val response = apollo.query(GenreCollectionQuery()).execute()
                val names = response.data?.genreCollection?.filterNotNull().orEmpty()
                val genres = names.map { AnimeGenre(it) }.sortedBy { it.name.lowercase() }
```

Update the `check(...)` message to `"AniList returned an empty anime genre catalog"`.

- [ ] **Step 3: Update the notification worker**

In `app/src/main/java/com/example/animewiki/data/notification/TopAnimeSyncWorker.kt`:
- Replace `private val api: JikanApi` with `private val apollo: ApolloClient` (`import com.apollographql.apollo.ApolloClient`, `import com.example.animewiki.graphql.TopAnimeQuery`).
- Replace the fetch block with:

```kotlin
            val response = apollo.query(TopAnimeQuery(page = 1, perPage = 1)).execute()
            val top = response.data?.Page?.media?.firstOrNull()?.animeCardFields ?: return Result.retry()
            val title = top.title?.english?.takeIf { it.isNotBlank() }
                ?: top.title?.romaji
                ?: return Result.retry()
            val score = top.averageScore?.let { "%.2f".format(it / 10.0) } ?: "—"
            val id = top.id
```

Keep the `notificationHelper.showWeeklyTopAnime(...)` call and the `try/catch` unchanged.

- [ ] **Step 4: Update the repository genre-cache test (RED)**

In `app/src/test/java/com/example/animewiki/data/repository/AnimeRepositoryTest.kt`, the `AnimeRepository` constructor now needs an `ApolloClient`. Build one from a `MockServer` in `@Before` and pass it. Replace the genre-catalog test's stub so the genres come from a GraphQL response:

```kotlin
    // in @Before, after creating the mock server:
    // apollo = ApolloClient.Builder().serverUrl(server.url()).build()
    // repository = AnimeRepository(api, db, favoriteDao, apollo)  // match the new constructor order

    @Test
    fun `getAnimeGenres maps sorts and caches`() = runTest {
        server.enqueueString(
            """{"data":{"GenreCollection":["Adventure","Action"]}}"""
        )

        val first = repository.getAnimeGenres()
        val second = repository.getAnimeGenres()

        assertEquals(listOf("Action", "Adventure"), first.map { it.name })
        assertEquals(first, second)
        assertEquals(1, server.takeRequestCount())
    }
```

Remove any test asserting `api.getAnimeGenres()` and the empty-catalog test may keep an empty `GenreCollection` (`{"data":{"GenreCollection":[]}}`) expecting `IllegalStateException`.

- [ ] **Step 5: Run and verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.animewiki.data.repository.AnimeRepositoryTest"
```

Expected: compilation fails — the repository constructor and internals still reference Jikan for these paths.

- [ ] **Step 6: Run and verify GREEN**

After Steps 1–3 are in place:

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.animewiki.data.repository.AnimeRepositoryTest"
```

Expected: `BUILD SUCCESSFUL`, all repository tests pass.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: move ranking, details, and genres to AniList"
```

---

### Task 8: One-time favorites migration by MAL id

**Files:**
- Modify: `data/local/dao/FavoriteDao.kt` (add `getAll`)
- Create: `data/migration/FavoritesMigration.kt`
- Create: `data/migration/FavoritesMigrationWorker.kt`
- Modify: the Application class (enqueue the worker) — `app/src/main/java/com/example/animewiki/<AppClass>.kt`
- Test: `data/migration/FavoritesMigrationTest.kt`

- [ ] **Step 1: Add `getAll` to FavoriteDao**

In `FavoriteDao.kt` add:

```kotlin
    @Query("SELECT * FROM favorites")
    suspend fun getAll(): List<FavoriteEntity>
```

- [ ] **Step 2: Write the failing migration test**

`app/src/test/java/com/example/animewiki/data/migration/FavoritesMigrationTest.kt`:

```kotlin
package com.example.animewiki.data.migration

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.testing.MockServer
import com.example.animewiki.data.local.dao.FavoriteDao
import com.example.animewiki.data.local.entity.FavoriteEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FavoritesMigrationTest {
    private lateinit var server: MockServer
    private lateinit var apollo: ApolloClient
    private val dao: FavoriteDao = mockk(relaxed = true)
    private var migrated = false

    @Before
    fun setUp() = runTest {
        server = MockServer()
        apollo = ApolloClient.Builder().serverUrl(server.url()).build()
    }

    @After
    fun tearDown() { apollo.close(); server.close() }

    private fun migration() = FavoritesMigration(
        apollo, dao,
        isDone = { migrated },
        markDone = { migrated = true }
    )

    @Test
    fun `rewrites favorite from mal id to anilist id`() = runTest {
        coEvery { dao.getAll() } returns listOf(
            FavoriteEntity(id = 52991, title = "Old", imageUrl = "old", score = 9.0, year = 2023, type = "TV")
        )
        server.enqueueString(
            """{"data":{"Media":{"id":154587,"idMal":52991,"title":{"english":"Frieren","romaji":"Sousou"},
               "coverImage":{"extraLarge":"new","large":"new"},"averageScore":91,"episodes":28,
               "format":"TV","seasonYear":2023}}}""".trimIndent()
        )

        migration().migrateIfNeeded()

        coVerify { dao.deleteById(52991) }
        val inserted = slot<FavoriteEntity>()
        coVerify { dao.insert(capture(inserted)) }
        assertEquals(154587, inserted.captured.id)
        assertEquals("Frieren", inserted.captured.title)
        assertEquals(true, migrated)
    }

    @Test
    fun `drops favorite not found on anilist`() = runTest {
        coEvery { dao.getAll() } returns listOf(
            FavoriteEntity(id = 999999, title = "Gone", imageUrl = "x", score = null, year = null, type = null)
        )
        server.enqueueString("""{"data":{"Media":null}}""")

        migration().migrateIfNeeded()

        coVerify { dao.deleteById(999999) }
        coVerify(exactly = 0) { dao.insert(any()) }
        assertEquals(true, migrated)
    }

    @Test
    fun `skips entirely when already done`() = runTest {
        migrated = true
        migration().migrateIfNeeded()
        coVerify(exactly = 0) { dao.getAll() }
    }
}
```

- [ ] **Step 3: Run and verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.animewiki.data.migration.FavoritesMigrationTest"
```

Expected: compilation fails — `FavoritesMigration` does not exist.

- [ ] **Step 4: Implement the migration**

`app/src/main/java/com/example/animewiki/data/migration/FavoritesMigration.kt`:

```kotlin
package com.example.animewiki.data.migration

import com.apollographql.apollo.ApolloClient
import com.example.animewiki.data.local.dao.FavoriteDao
import com.example.animewiki.data.local.entity.FavoriteEntity
import com.example.animewiki.graphql.AnimeByMalIdQuery

class FavoritesMigration(
    private val apollo: ApolloClient,
    private val favoriteDao: FavoriteDao,
    private val isDone: suspend () -> Boolean,
    private val markDone: suspend () -> Unit
) {
    @Suppress("TooGenericExceptionCaught")
    suspend fun migrateIfNeeded() {
        if (isDone()) return
        favoriteDao.getAll().forEach { old ->
            val card = runCatching {
                apollo.query(AnimeByMalIdQuery(idMal = old.id)).execute().data?.Media?.animeCardFields
            }.getOrNull()
            favoriteDao.deleteById(old.id)
            val title = card?.title?.english ?: card?.title?.romaji
            val image = card?.coverImage?.extraLarge ?: card?.coverImage?.large
            if (card != null && title != null && image != null) {
                favoriteDao.insert(
                    FavoriteEntity(
                        id = card.id,
                        title = title,
                        imageUrl = image,
                        score = card.averageScore?.let { it / 10.0 },
                        year = card.seasonYear,
                        type = card.format?.rawValue
                    )
                )
            }
        }
        markDone()
    }
}
```

> Note: if the network is unavailable an `ApolloNetworkException` propagates out of `execute()`; the worker (Step 5) treats that as retryable so `markDone()` is not reached and the migration runs again next launch. `runCatching` here only guards per-item GraphQL nulls, not transport failure — keep the worker's retry.

- [ ] **Step 5: Run and verify GREEN**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.animewiki.data.migration.FavoritesMigrationTest"
```

Expected: `BUILD SUCCESSFUL`, 3 tests, 0 failures.

- [ ] **Step 6: Wire the worker and DataStore flag**

Create `FavoritesMigrationWorker.kt` (a `CoroutineWorker` mirroring `TopAnimeSyncWorker`'s Hilt setup) that reads/writes a boolean `favorites_migrated_to_anilist` in the existing preferences DataStore, builds a `FavoritesMigration`, calls `migrateIfNeeded()`, returns `Result.success()` on success and `Result.retry()` if `migrateIfNeeded()` throws. Enqueue it as a unique one-time `CONNECTED` work request from the Application `onCreate()`, alongside the existing notification scheduling.

- [ ] **Step 7: Compile and commit**

```bash
./gradlew :app:compileDebugKotlin
git add -A
git commit -m "feat: migrate existing favorites from MAL to AniList ids"
```

---

### Task 9: Remove Jikan, verify, and document

**Files:**
- Remove: `data/remote/JikanApi.kt`, `data/remote/dto/AnimeDto.kt`, `AnimeDetailsDto.kt`, `AnimeGenreDto.kt`, `data/mapper/AnimeGenreMapper.kt`
- Modify: `data/mapper/AnimeMapper.kt` (drop the `AnimeDto` extensions; keep `AnimeEntity.toDomain`), `di/NetworkModule.kt`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `README.md`
- Remove obsolete tests: `AnimeMapperTest.kt`, `AnimeGenreMapperTest.kt`, `AnimeDtoDeserializationTest.kt`, `AnimeGenreDtoTest.kt`

- [ ] **Step 1: Delete Jikan sources and their tests**

```bash
git rm app/src/main/java/com/example/animewiki/data/remote/JikanApi.kt \
       app/src/main/java/com/example/animewiki/data/remote/dto/AnimeDto.kt \
       app/src/main/java/com/example/animewiki/data/remote/dto/AnimeDetailsDto.kt \
       app/src/main/java/com/example/animewiki/data/remote/dto/AnimeGenreDto.kt \
       app/src/main/java/com/example/animewiki/data/mapper/AnimeGenreMapper.kt \
       app/src/test/java/com/example/animewiki/data/mapper/AnimeMapperTest.kt \
       app/src/test/java/com/example/animewiki/data/mapper/AnimeGenreMapperTest.kt \
       app/src/test/java/com/example/animewiki/data/remote/dto/AnimeDtoDeserializationTest.kt \
       app/src/test/java/com/example/animewiki/data/remote/dto/AnimeGenreDtoTest.kt
```

(If any listed test file does not exist, skip it.)

- [ ] **Step 2: Trim `AnimeMapper.kt` to just the entity mapper**

Replace `app/src/main/java/com/example/animewiki/data/mapper/AnimeMapper.kt` with only the `AnimeEntity.toDomain()` function (delete the two `AnimeDto` extensions and the `AnimeDto` import):

```kotlin
package com.example.animewiki.data.mapper

import com.example.animewiki.data.local.entity.AnimeEntity
import com.example.animewiki.domain.model.Anime

fun AnimeEntity.toDomain(): Anime = Anime(
    id = id,
    title = title,
    imageUrl = imageUrl,
    score = score,
    episodes = episodes,
    type = type,
    year = year,
    synopsis = synopsis,
    genres = genres,
    studios = studios,
    aired = aired,
    status = status,
    rating = rating,
    duration = duration,
    rank = rank,
    trailerYoutubeId = trailerYoutubeId
)
```

- [ ] **Step 3: Remove Jikan/Retrofit from DI and Gradle**

In `di/NetworkModule.kt`, delete `provideJson`, `provideRetrofit`, `provideJikanApi`, `BASE_URL`, and the Retrofit/Json imports. Keep `provideApolloClient` and `provideOkHttpClient` only if the latter is still referenced (it is not after this change — delete it too). 

In `app/build.gradle.kts` remove the Retrofit/serialization-converter dependencies (`libs.retrofit`, `libs.retrofit.kotlinx.serialization`) if unused elsewhere. Leave `kotlinx-serialization-json` if the preferences DataStore still uses it (`git grep -n "kotlinx.serialization"` to check).

- [ ] **Step 4: Full clean verification**

```bash
./gradlew clean testDebugUnitTest assembleDebug detekt
```

Expected: `BUILD SUCCESSFUL`, zero unit failures, debug APK produced, zero Detekt findings. Resolve any remaining `JikanApi`/DTO references the compiler reports.

- [ ] **Step 5: Update the README**

Add a short section documenting the AniList backend, the parity scope, the offline-first Room ranking, the adult-content toggle (replacing age rating), name-based genres, and the retry-on-5xx/429 behavior.

- [ ] **Step 6: Manual smoke test on an emulator**

Launch the app; confirm: Discover ranking loads (SCORE_DESC), search returns results, format/genre filters and the adult toggle work, details render (synopsis has no HTML tags), favorites persist, and an existing pre-migration favorite is rewritten (or dropped if unmatched). Toggle airplane mode to confirm the offline banner on ranking and the server/no-connection distinction on search.

- [ ] **Step 7: Commit and run review gates**

```bash
git add -A
git commit -m "chore: remove Jikan backend and document AniList migration"
```

Then use `superpowers:requesting-code-review`, address findings via `superpowers:receiving-code-review`, rerun Step 4, and use `superpowers:finishing-a-development-branch` for integration.

---

## Self-Review Notes

- **Spec coverage:** §5 seam → Tasks 6–7 keep the repository shape; §6.1 add → Tasks 1–5, 8; §6.2 modify → Tasks 6–7; §6.3 mapping (id/score/HTML/genres/rating→isAdult/top sort/favorites) → Tasks 3, 6, 8; §7 error handling → Task 4; §8 retry → Task 4/5; §9 testing → tests in Tasks 3–8; §6.4 remove → Task 9. All spec sections map to a task.
- **Type consistency:** fragment accessors assumed as `animeCardFields` / `animeDetailsFields` and `genreCollection`; verify against generated code in Task 2 and adjust the three call sites if codegen names differ (noted inline). `AnimeFilters` uses `genres: Set<String>`, `adultContent`, `genresList` consistently across domain, PagingSource, ViewModel, and UI. `AnimeGenre(name)` used consistently in repository, state, and UI.
- **Known verification point:** the Apollo MockServer artifact coordinate (`com.apollographql.apollo:apollo-mockserver:5.0.1`) and the fragment-accessor names are the two things to confirm against the generated sources in Task 1–2 before relying on them downstream.
