# Home Season Shelves (R3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Home destination that shows four independent horizontal shelves
(This season, Upcoming, Top, Trending) backed by AniList, offline-first via a
migration-safe Room table that preserves favorites.

**Architecture:** Each shelf loads through its own path with its own
loading/error/retry state. Three network shelves (This season, Upcoming,
Trending) use one parameterized GraphQL query and cache into a new
`home_shelf_item` Room table (cache-first). The Top shelf reuses the existing
ranking `anime` table. A `HomeShelfRepository` owns the data; a `HomeViewModel`
exposes per-shelf state; Home becomes the start destination.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Apollo Kotlin 5,
Room 2.7 (with a real 3→4 migration + schema export), Coroutines/Flow, JUnit4 +
MockK + Turbine + Apollo MockServer + Room testing.

**Spec:** `docs/superpowers/specs/2026-08-14-r3-current-season-upcoming-design.md`

---

## File Structure

**New (main):**
- `app/src/main/graphql/SeasonAnime.graphql` — season/upcoming/trending query.
- `domain/model/AnimeSeason.kt` — `WINTER|SPRING|SUMMER|FALL`.
- `domain/model/HomeShelf.kt` — `THIS_SEASON|UPCOMING|TOP|TRENDING`.
- `domain/model/HomeShelfAnime.kt` — compact card model + eyebrow inputs.
- `domain/SeasonResolver.kt` — pure current-season calc (`SeasonYear`).
- `data/local/entity/HomeShelfItemEntity.kt` — `home_shelf_item` row.
- `data/local/dao/HomeShelfDao.kt` — observe/replace a shelf.
- `data/local/Migrations.kt` — `MIGRATION_3_4`.
- `data/mapper/HomeShelfMapper.kt` — graphql/entity ↔ `HomeShelfAnime`, season maps.
- `data/repository/HomeShelfRepository.kt` — observe + refresh per shelf.
- `di/TimeModule.kt` — provides `java.time.Clock`.
- `ui/screens/home/HomeScreen.kt`, `HomeViewModel.kt`, `HomeUiState.kt`.
- `ui/screens/home/components/ShelfRow.kt`, `ShelfAnimeCard.kt`, `ShelfEyebrow.kt`.

**Modified (main):**
- `app/build.gradle.kts` — Room schema export (KSP arg) + androidTest assets.
- `data/local/AppDatabase.kt` — version 4, new entity + `homeShelfDao()`, export on.
- `data/local/dao/AnimeDao.kt` — `observeTop(limit)`.
- `di/DatabaseModule.kt` — `addMigrations`, provide `HomeShelfDao`.
- `ui/navigation/AnimeWikiNavHost.kt` — Home tab + start destination.
- `res/values/strings.xml` and `res/values-en/strings.xml` — new keys.

**New/modified (test):**
- `test/domain/SeasonResolverTest.kt` (new)
- `test/data/mapper/HomeShelfMapperTest.kt` (new)
- `test/data/repository/HomeShelfRepositoryTest.kt` (new)
- `test/ui/screens/home/HomeViewModelTest.kt` (new)
- `test/data/local/AppDatabaseVersionTest.kt` (modify → expect 4)
- `androidTest/data/local/Migration3To4Test.kt` (new, device-gated)

---

## Task 1: Season domain + resolver

**Files:**
- Create: `app/src/main/java/com/example/animewiki/domain/model/AnimeSeason.kt`
- Create: `app/src/main/java/com/example/animewiki/domain/SeasonResolver.kt`
- Test: `app/src/test/java/com/example/animewiki/domain/SeasonResolverTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.animewiki.domain

import com.example.animewiki.domain.model.AnimeSeason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SeasonResolverTest {

    @Test
    fun `winter spans january to march`() {
        listOf(1, 2, 3).forEach { month ->
            assertEquals(
                SeasonYear(AnimeSeason.WINTER, 2026),
                SeasonResolver.current(2026, month)
            )
        }
    }

    @Test
    fun `spring summer fall boundaries`() {
        assertEquals(AnimeSeason.SPRING, SeasonResolver.current(2026, 4).season)
        assertEquals(AnimeSeason.SPRING, SeasonResolver.current(2026, 6).season)
        assertEquals(AnimeSeason.SUMMER, SeasonResolver.current(2026, 7).season)
        assertEquals(AnimeSeason.SUMMER, SeasonResolver.current(2026, 9).season)
        assertEquals(AnimeSeason.FALL, SeasonResolver.current(2026, 10).season)
        assertEquals(AnimeSeason.FALL, SeasonResolver.current(2026, 12).season)
    }

    @Test
    fun `year is carried through unchanged`() {
        assertEquals(2031, SeasonResolver.current(2031, 8).year)
    }

    @Test
    fun `month out of range is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SeasonResolver.current(2026, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SeasonResolver.current(2026, 13)
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.animewiki.domain.SeasonResolverTest"`
Expected: FAIL to compile — `AnimeSeason`, `SeasonResolver`, `SeasonYear` unresolved.

- [ ] **Step 3: Write minimal implementation**

`AnimeSeason.kt`:

```kotlin
package com.example.animewiki.domain.model

enum class AnimeSeason { WINTER, SPRING, SUMMER, FALL }
```

`SeasonResolver.kt`:

```kotlin
package com.example.animewiki.domain

import com.example.animewiki.domain.model.AnimeSeason

data class SeasonYear(val season: AnimeSeason, val year: Int)

object SeasonResolver {
    fun current(year: Int, month: Int): SeasonYear {
        val season = when (month) {
            1, 2, 3 -> AnimeSeason.WINTER
            4, 5, 6 -> AnimeSeason.SPRING
            7, 8, 9 -> AnimeSeason.SUMMER
            10, 11, 12 -> AnimeSeason.FALL
            else -> throw IllegalArgumentException("month must be 1..12, was $month")
        }
        return SeasonYear(season, year)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.example.animewiki.domain.SeasonResolverTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/animewiki/domain app/src/test/java/com/example/animewiki/domain
git commit -m "feat: add AnimeSeason and SeasonResolver"
```

---

## Task 2: Shelf domain models + GraphQL query

No unit test — this task adds the `HomeShelf`/`HomeShelfAnime` models and the
`SeasonAnime.graphql` operation; verification is Apollo codegen + compile. The
mapping logic is tested in Task 3.

**Files:**
- Create: `app/src/main/java/com/example/animewiki/domain/model/HomeShelf.kt`
- Create: `app/src/main/java/com/example/animewiki/domain/model/HomeShelfAnime.kt`
- Create: `app/src/main/graphql/SeasonAnime.graphql`

- [ ] **Step 1: Add the domain models**

`HomeShelf.kt`:

```kotlin
package com.example.animewiki.domain.model

enum class HomeShelf { THIS_SEASON, UPCOMING, TOP, TRENDING }
```

`HomeShelfAnime.kt`:

```kotlin
package com.example.animewiki.domain.model

data class HomeShelfAnime(
    val id: Int,
    val title: String,
    val imageUrl: String,
    val score: Double?,
    val season: AnimeSeason? = null,
    val year: Int? = null,
    val rank: Int? = null,
    val nextEpisode: Int? = null,
    val nextAiringAtSeconds: Long? = null
)
```

- [ ] **Step 2: Add the GraphQL query**

`SeasonAnime.graphql` (reuses the existing `AnimeCardFields` fragment and adds
`season` + `nextAiringEpisode` for the eyebrows):

```graphql
query SeasonAnime(
  $page: Int!,
  $perPage: Int!,
  $season: MediaSeason,
  $seasonYear: Int,
  $status: MediaStatus,
  $sort: [MediaSort!]!,
  $isAdult: Boolean
) {
  Page(page: $page, perPage: $perPage) {
    pageInfo { currentPage hasNextPage }
    media(
      type: ANIME,
      season: $season,
      seasonYear: $seasonYear,
      status: $status,
      sort: $sort,
      isAdult: $isAdult
    ) {
      ...AnimeCardFields
      season
      nextAiringEpisode { episode airingAt }
    }
  }
}
```

- [ ] **Step 3: Generate Apollo models and compile**

Run: `./gradlew generateApolloSources compileDebugKotlin`
Expected: SUCCESS; `com.example.animewiki.graphql.SeasonAnimeQuery` is generated
with `Media.animeCardFields`, `Media.season`, and
`Media.nextAiringEpisode { episode, airingAt }`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/animewiki/domain/model/HomeShelf.kt \
        app/src/main/java/com/example/animewiki/domain/model/HomeShelfAnime.kt \
        app/src/main/graphql/SeasonAnime.graphql
git commit -m "feat: add home shelf models and SeasonAnime query"
```

---

## Task 3: Shelf mappers

Maps graphql/entity data to `HomeShelfAnime` and back. The entity type is
created in Task 5; to keep this task self-contained the entity is created here
and reused later (Task 5 only wires it into the database).

**Files:**
- Create: `app/src/main/java/com/example/animewiki/data/local/entity/HomeShelfItemEntity.kt`
- Create: `app/src/main/java/com/example/animewiki/data/mapper/HomeShelfMapper.kt`
- Test: `app/src/test/java/com/example/animewiki/data/mapper/HomeShelfMapperTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.animewiki.data.mapper

import com.example.animewiki.data.local.entity.AnimeEntity
import com.example.animewiki.data.local.entity.HomeShelfItemEntity
import com.example.animewiki.domain.model.AnimeSeason
import com.example.animewiki.domain.model.HomeShelf
import com.example.animewiki.domain.model.HomeShelfAnime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeShelfMapperTest {

    private fun shelfAnime() = HomeShelfAnime(
        id = 1,
        title = "Frieren",
        imageUrl = "https://img/1.jpg",
        score = 9.1,
        season = AnimeSeason.FALL,
        year = 2026,
        rank = null,
        nextEpisode = 6,
        nextAiringAtSeconds = 1_700_000_000L
    )

    @Test
    fun `domain to entity carries shelf key and position`() {
        val entity = shelfAnime().toEntity(HomeShelf.THIS_SEASON, position = 3)

        assertEquals("THIS_SEASON", entity.shelf)
        assertEquals(3, entity.position)
        assertEquals(1, entity.id)
        assertEquals("FALL", entity.season)
        assertEquals(6, entity.nextEpisode)
        assertEquals(1_700_000_000L, entity.nextAiringAtSeconds)
    }

    @Test
    fun `entity round-trips back to domain`() {
        val restored = shelfAnime().toEntity(HomeShelf.UPCOMING, 0).toShelfAnime()

        assertEquals(shelfAnime(), restored)
    }

    @Test
    fun `unknown stored season maps to null instead of crashing`() {
        val entity = HomeShelfItemEntity(
            shelf = "TRENDING", position = 0, id = 2, title = "X",
            imageUrl = "https://img/2.jpg", score = null, season = "GARBAGE",
            year = null, rank = null, nextEpisode = null, nextAiringAtSeconds = null
        )

        assertNull(entity.toShelfAnime().season)
    }

    @Test
    fun `anime ranking entity maps to a shelf anime with its rank`() {
        val entity = AnimeEntity(
            id = 7, title = "Top One", imageUrl = "https://img/7.jpg", score = 9.5,
            episodes = 12, type = "TV", year = 2025, synopsis = null,
            genres = emptyList(), studios = emptyList(), aired = null, status = null,
            rating = null, duration = null, rank = 1, trailerYoutubeId = null,
            pageIndex = 0
        )

        val mapped = entity.toShelfAnime()

        assertEquals(7, mapped.id)
        assertEquals(1, mapped.rank)
        assertEquals(9.5, mapped.score)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.animewiki.data.mapper.HomeShelfMapperTest"`
Expected: FAIL to compile — `HomeShelfItemEntity`, `toEntity`, `toShelfAnime` unresolved.

- [ ] **Step 3: Write minimal implementation**

`HomeShelfItemEntity.kt`:

```kotlin
package com.example.animewiki.data.local.entity

import androidx.room.Entity

@Entity(tableName = "home_shelf_item", primaryKeys = ["shelf", "position"])
data class HomeShelfItemEntity(
    val shelf: String,
    val position: Int,
    val id: Int,
    val title: String,
    val imageUrl: String,
    val score: Double?,
    val season: String?,
    val year: Int?,
    val rank: Int?,
    val nextEpisode: Int?,
    val nextAiringAtSeconds: Long?
)
```

`HomeShelfMapper.kt`:

```kotlin
package com.example.animewiki.data.mapper

import com.example.animewiki.data.local.entity.AnimeEntity
import com.example.animewiki.data.local.entity.HomeShelfItemEntity
import com.example.animewiki.domain.model.AnimeSeason
import com.example.animewiki.domain.model.HomeShelf
import com.example.animewiki.domain.model.HomeShelfAnime
import com.example.animewiki.graphql.SeasonAnimeQuery
import com.example.animewiki.graphql.type.MediaSeason

fun SeasonAnimeQuery.Medium.toShelfAnime(): HomeShelfAnime? {
    val card = animeCardFields.toDomain() ?: return null
    return HomeShelfAnime(
        id = card.id,
        title = card.title,
        imageUrl = card.imageUrl,
        score = card.score,
        season = season.toDomainSeason(),
        year = card.year,
        rank = null,
        nextEpisode = nextAiringEpisode?.episode,
        nextAiringAtSeconds = nextAiringEpisode?.airingAt?.toLong()
    )
}

fun HomeShelfAnime.toEntity(shelf: HomeShelf, position: Int) = HomeShelfItemEntity(
    shelf = shelf.name,
    position = position,
    id = id,
    title = title,
    imageUrl = imageUrl,
    score = score,
    season = season?.name,
    year = year,
    rank = rank,
    nextEpisode = nextEpisode,
    nextAiringAtSeconds = nextAiringAtSeconds
)

fun HomeShelfItemEntity.toShelfAnime() = HomeShelfAnime(
    id = id,
    title = title,
    imageUrl = imageUrl,
    score = score,
    season = season?.toAnimeSeasonOrNull(),
    year = year,
    rank = rank,
    nextEpisode = nextEpisode,
    nextAiringAtSeconds = nextAiringAtSeconds
)

fun AnimeEntity.toShelfAnime() = HomeShelfAnime(
    id = id,
    title = title,
    imageUrl = imageUrl,
    score = score,
    year = year,
    rank = rank
)

fun AnimeSeason.toMediaSeason(): MediaSeason = when (this) {
    AnimeSeason.WINTER -> MediaSeason.WINTER
    AnimeSeason.SPRING -> MediaSeason.SPRING
    AnimeSeason.SUMMER -> MediaSeason.SUMMER
    AnimeSeason.FALL -> MediaSeason.FALL
}

private fun MediaSeason?.toDomainSeason(): AnimeSeason? = when (this) {
    MediaSeason.WINTER -> AnimeSeason.WINTER
    MediaSeason.SPRING -> AnimeSeason.SPRING
    MediaSeason.SUMMER -> AnimeSeason.SUMMER
    MediaSeason.FALL -> AnimeSeason.FALL
    MediaSeason.UNKNOWN__, null -> null
}

private fun String.toAnimeSeasonOrNull(): AnimeSeason? =
    AnimeSeason.entries.firstOrNull { it.name == this }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.example.animewiki.data.mapper.HomeShelfMapperTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/animewiki/data/local/entity/HomeShelfItemEntity.kt \
        app/src/main/java/com/example/animewiki/data/mapper/HomeShelfMapper.kt \
        app/src/test/java/com/example/animewiki/data/mapper/HomeShelfMapperTest.kt
git commit -m "feat: add home shelf mappers"
```

---

## Task 4: Enable Room schema export and capture the v3 baseline

The migration test (Task 6) needs the exported v3 and v4 schema JSONs. Schema
export is currently off, so this task turns it on and captures the **v3**
baseline before any schema change. Do this task while `AppDatabase` is still at
version 3.

**Files:**
- Modify: `app/build.gradle.kts`
- Create (generated, committed): `app/schemas/com.example.animewiki.data.local.AppDatabase/3.json`

- [ ] **Step 1: Add the KSP schema location and androidTest assets**

In `app/build.gradle.kts`, inside the `android { defaultConfig { … } }` block add
nothing; instead add a top-level `ksp { … }` block and extend the androidTest
source set. Add after the `kotlin { … }` block:

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

androidComponents {
    onVariants(selector().all()) { /* no-op: keeps AGP happy if referenced */ }
}
```

Then, inside `android { … }`, add the assets source dir so
`MigrationTestHelper` can find the schemas:

```kotlin
    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }
```

(If the `androidComponents` snippet causes an unused-import or empty-block Detekt
finding, omit it — only the `ksp { arg(...) }` and the `sourceSets` change are
required.)

- [ ] **Step 2: Generate the v3 schema**

Run: `./gradlew clean kspDebugKotlin`
Expected: SUCCESS and a new file
`app/schemas/com.example.animewiki.data.local.AppDatabase/3.json` exists.

Run: `ls app/schemas/com.example.animewiki.data.local.AppDatabase/`
Expected: `3.json`

- [ ] **Step 3: Commit the baseline**

```bash
git add app/build.gradle.kts app/schemas/com.example.animewiki.data.local.AppDatabase/3.json
git commit -m "build: enable Room schema export and capture v3 baseline"
```

---

## Task 5: Room v4 — shelf table, DAO, migration, wiring

**Files:**
- Create: `app/src/main/java/com/example/animewiki/data/local/dao/HomeShelfDao.kt`
- Create: `app/src/main/java/com/example/animewiki/data/local/Migrations.kt`
- Modify: `app/src/main/java/com/example/animewiki/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/example/animewiki/data/local/dao/AnimeDao.kt`
- Modify: `app/src/main/java/com/example/animewiki/di/DatabaseModule.kt`
- Modify (test): `app/src/test/java/com/example/animewiki/data/local/AppDatabaseVersionTest.kt`

- [ ] **Step 1: Update the failing version test**

Replace the body of `AppDatabaseVersionTest.kt`:

```kotlin
package com.example.animewiki.data.local

import androidx.room.RoomOpenDelegate
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDatabaseVersionTest {
    @Test
    fun `database is at migration-safe version four`() {
        val createOpenDelegate = AppDatabase_Impl::class.java
            .getDeclaredMethod("createOpenDelegate")
            .apply { isAccessible = true }
        val delegate = createOpenDelegate.invoke(AppDatabase_Impl()) as RoomOpenDelegate

        assertEquals(4, delegate.version)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.animewiki.data.local.AppDatabaseVersionTest"`
Expected: FAIL — expected 4 but was 3.

- [ ] **Step 3: Add the DAO, migration, and wire the database**

`HomeShelfDao.kt`:

```kotlin
package com.example.animewiki.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.animewiki.data.local.entity.HomeShelfItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeShelfDao {
    @Query("SELECT * FROM home_shelf_item WHERE shelf = :shelf ORDER BY position ASC")
    fun observeShelf(shelf: String): Flow<List<HomeShelfItemEntity>>

    @Query("DELETE FROM home_shelf_item WHERE shelf = :shelf")
    suspend fun clearShelf(shelf: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HomeShelfItemEntity>)

    @Transaction
    suspend fun replaceShelf(shelf: String, items: List<HomeShelfItemEntity>) {
        clearShelf(shelf)
        insertAll(items)
    }
}
```

`Migrations.kt`:

```kotlin
package com.example.animewiki.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `home_shelf_item` (" +
                "`shelf` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                "`id` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                "`imageUrl` TEXT NOT NULL, `score` REAL, `season` TEXT, " +
                "`year` INTEGER, `rank` INTEGER, `nextEpisode` INTEGER, " +
                "`nextAiringAtSeconds` INTEGER, " +
                "PRIMARY KEY(`shelf`, `position`))"
        )
    }
}
```

Add to `AnimeDao.kt` (new import `kotlinx.coroutines.flow.Flow`):

```kotlin
    @Query("SELECT * FROM anime ORDER BY pageIndex ASC LIMIT :limit")
    fun observeTop(limit: Int): kotlinx.coroutines.flow.Flow<List<AnimeEntity>>
```

Update `AppDatabase.kt`:

```kotlin
@Database(
    entities = [
        AnimeEntity::class,
        RemoteKeyEntity::class,
        FavoriteEntity::class,
        HomeShelfItemEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao
    abstract fun remoteKeyDao(): RemoteKeyDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun homeShelfDao(): HomeShelfDao
}
```

(Add imports for `HomeShelfItemEntity` and `HomeShelfDao`.)

Update `DatabaseModule.kt` — add the migration and provide the DAO:

```kotlin
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "animewiki.db")
            .addMigrations(MIGRATION_3_4)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideHomeShelfDao(db: AppDatabase): HomeShelfDao = db.homeShelfDao()
```

(Add imports for `MIGRATION_3_4` and `HomeShelfDao`.)

- [ ] **Step 4: Run the version test + generate the v4 schema**

Run: `./gradlew testDebugUnitTest --tests "com.example.animewiki.data.local.AppDatabaseVersionTest"`
Expected: PASS

Run: `ls app/schemas/com.example.animewiki.data.local.AppDatabase/`
Expected: `3.json` and `4.json`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/animewiki/data/local \
        app/src/main/java/com/example/animewiki/di/DatabaseModule.kt \
        app/src/test/java/com/example/animewiki/data/local/AppDatabaseVersionTest.kt \
        app/schemas/com.example.animewiki.data.local.AppDatabase/4.json
git commit -m "feat: add home_shelf_item table with 3->4 migration"
```

---

## Task 6: Migration instrumented test (device-gated)

Verifies the whole point of the "migration-safe" choice: favorites survive and
the new table appears. This is an **instrumented** test (runs on a device or
emulator via `connectedDebugAndroidTest`), consistent with `FavoriteDaoTest`.

**Files:**
- Create: `app/src/androidTest/java/com/example/animewiki/data/local/Migration3To4Test.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.example.animewiki.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration3To4Test {

    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate3To4_keepsFavoritesAndAddsShelfTable() {
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                "INSERT INTO favorite " +
                    "(id, title, imageUrl, score, year, type, favoritedAt) " +
                    "VALUES (52991, 'Frieren', 'https://img/1.jpg', 9.1, 2023, 'TV', 1000)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)

        migrated.query("SELECT title FROM favorite WHERE id = 52991").use { favorites ->
            assertTrue(favorites.moveToFirst())
            assertEquals("Frieren", favorites.getString(0))
        }
        migrated.query("SELECT count(*) FROM home_shelf_item").use { shelf ->
            assertTrue(shelf.moveToFirst())
            assertEquals(0, shelf.getInt(0))
        }
        migrated.close()
    }
}
```

- [ ] **Step 2: Run the test on a device/emulator**

Run: `./gradlew connectedDebugAndroidTest --tests "com.example.animewiki.data.local.Migration3To4Test"`
Expected: PASS. (If no device is attached, this is the one test deferred to the
user's phone; note that in the task's commit message.)

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/example/animewiki/data/local/Migration3To4Test.kt
git commit -m "test: verify 3->4 migration preserves favorites"
```

---

## Task 7: HomeShelfRepository

Owns per-shelf observe + refresh. Network shelves are cache-first; TOP does a
non-destructive page-1 upsert into the ranking `anime` table so it is populated
even when the user lands on Home first, without clearing Discover's deeper pages.
A `transaction` lambda is injected (like `TopAnimeRemoteMediator`) so the TOP
path is unit-testable with a mocked database.

**Files:**
- Create: `app/src/main/java/com/example/animewiki/di/TimeModule.kt`
- Create: `app/src/main/java/com/example/animewiki/data/repository/HomeShelfRepository.kt`
- Test: `app/src/test/java/com/example/animewiki/data/repository/HomeShelfRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.animewiki.data.repository

import com.apollographql.apollo.ApolloClient
import com.apollographql.mockserver.MockResponse
import com.apollographql.mockserver.MockServer
import com.example.animewiki.data.local.AppDatabase
import com.example.animewiki.data.local.dao.HomeShelfDao
import com.example.animewiki.data.local.entity.HomeShelfItemEntity
import com.example.animewiki.domain.model.HomeShelf
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class HomeShelfRepositoryTest {

    private val homeShelfDao: HomeShelfDao = mockk(relaxed = true)
    private val db: AppDatabase = mockk {
        every { homeShelfDao() } returns this@HomeShelfRepositoryTest.homeShelfDao
    }
    private val fixedClock: Clock =
        Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC)

    private fun repository(client: ApolloClient) =
        HomeShelfRepository(client, db, fixedClock)

    private fun <T> withServer(response: String, block: suspend (ApolloClient) -> T): T =
        runBlocking {
            val server = MockServer()
            server.enqueue(MockResponse.Builder().body(response).build())
            val client = ApolloClient.Builder().serverUrl(server.url()).build()
            try {
                block(client)
            } finally {
                client.close()
                server.close()
            }
        }

    @Test
    fun `refresh trending stores mapped media and skips malformed entries`() = runTest {
        val body = """
        {"data":{"Page":{"pageInfo":{"currentPage":1,"hasNextPage":false},"media":[
          {"id":1,"title":{"english":"Alpha","romaji":"Alpha"},
           "coverImage":{"extraLarge":"https://img/1.jpg","large":null},
           "averageScore":88,"episodes":12,"format":"TV","seasonYear":2026,
           "isAdult":false,"season":"SUMMER","nextAiringEpisode":null},
          {"id":2,"title":{"english":null,"romaji":null},
           "coverImage":{"extraLarge":null,"large":null},
           "averageScore":null,"episodes":null,"format":null,"seasonYear":null,
           "isAdult":false,"season":null,"nextAiringEpisode":null}
        ]}}}
        """.trimIndent()

        withServer(body) { client ->
            val stored = slot<List<HomeShelfItemEntity>>()
            coVerify(exactly = 0) { homeShelfDao.replaceShelf(any(), any()) }

            repository(client).refresh(HomeShelf.TRENDING)

            coVerify(exactly = 1) {
                homeShelfDao.replaceShelf("TRENDING", capture(stored))
            }
            assertEquals(1, stored.captured.size)
            assertEquals("Alpha", stored.captured[0].title)
            assertEquals(0, stored.captured[0].position)
        }
    }

    @Test
    fun `observe trending maps cached rows to shelf anime`() = runTest {
        every { homeShelfDao.observeShelf("TRENDING") } returns flowOf(
            listOf(
                HomeShelfItemEntity(
                    shelf = "TRENDING", position = 0, id = 5, title = "Cached",
                    imageUrl = "https://img/5.jpg", score = 8.0, season = "FALL",
                    year = 2026, rank = null, nextEpisode = null,
                    nextAiringAtSeconds = null
                )
            )
        )

        val items = repository(mockk()).observe(HomeShelf.TRENDING).first()

        assertEquals(1, items.size)
        assertEquals("Cached", items[0].title)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.animewiki.data.repository.HomeShelfRepositoryTest"`
Expected: FAIL to compile — `HomeShelfRepository` unresolved.

- [ ] **Step 3: Write minimal implementation**

`TimeModule.kt`:

```kotlin
package com.example.animewiki.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
```

`HomeShelfRepository.kt`:

```kotlin
package com.example.animewiki.data.repository

import androidx.room.withTransaction
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.example.animewiki.data.local.AppDatabase
import com.example.animewiki.data.local.entity.RemoteKeyEntity
import com.example.animewiki.data.mapper.toEntity
import com.example.animewiki.data.mapper.toMediaSeason
import com.example.animewiki.data.mapper.toShelfAnime
import com.example.animewiki.data.remote.AniListGraphQlException
import com.example.animewiki.data.remote.dataOrAniListError
import com.example.animewiki.domain.HomeShelfSize
import com.example.animewiki.domain.SeasonResolver
import com.example.animewiki.domain.model.HomeShelf
import com.example.animewiki.domain.model.HomeShelfAnime
import com.example.animewiki.graphql.SeasonAnimeQuery
import com.example.animewiki.graphql.TopAnimeQuery
import com.example.animewiki.graphql.type.MediaSort
import com.example.animewiki.graphql.type.MediaStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeShelfRepository internal constructor(
    private val apolloClient: ApolloClient,
    private val db: AppDatabase,
    private val clock: Clock,
    private val transaction: suspend (suspend () -> Unit) -> Unit
) {
    @Inject
    constructor(apolloClient: ApolloClient, db: AppDatabase, clock: Clock) : this(
        apolloClient = apolloClient,
        db = db,
        clock = clock,
        transaction = { block -> db.withTransaction { block() } }
    )

    fun observe(shelf: HomeShelf): Flow<List<HomeShelfAnime>> = when (shelf) {
        HomeShelf.TOP ->
            db.animeDao().observeTop(HomeShelfSize.VALUE).map { list ->
                list.map { it.toShelfAnime() }
            }
        else ->
            db.homeShelfDao().observeShelf(shelf.name).map { list ->
                list.map { it.toShelfAnime() }
            }
    }

    suspend fun refresh(shelf: HomeShelf) {
        if (shelf == HomeShelf.TOP) refreshTop() else refreshNetworkShelf(shelf)
    }

    private suspend fun refreshNetworkShelf(shelf: HomeShelf) {
        val media = apolloClient.query(seasonQueryFor(shelf))
            .execute()
            .dataOrAniListError()
            .Page
            ?.media
            .orEmpty()
        val items = media.mapNotNull { it?.toShelfAnime() }
            .distinctBy(HomeShelfAnime::id)
            .mapIndexed { index, anime -> anime.toEntity(shelf, index) }
        if (items.isEmpty()) {
            throw AniListGraphQlException("AniList ${shelf.name} response had no usable media")
        }
        db.homeShelfDao().replaceShelf(shelf.name, items)
    }

    private suspend fun refreshTop() {
        val page = apolloClient.query(
            TopAnimeQuery(page = 1, perPage = HomeShelfSize.VALUE, isAdult = Optional.present(false))
        ).execute().dataOrAniListError().Page
        val entities = page?.media.orEmpty()
            .mapIndexedNotNull { index, media -> media?.animeCacheFields?.toEntity(index) }
        if (entities.isEmpty()) {
            throw AniListGraphQlException("AniList top response had no usable media")
        }
        val hasNext = page?.pageInfo?.hasNextPage == true
        transaction {
            db.animeDao().upsertAll(entities)
            db.remoteKeyDao().upsertAll(
                entities.map {
                    RemoteKeyEntity(
                        animeId = it.id,
                        prevKey = null,
                        nextKey = if (hasNext) 2 else null
                    )
                }
            )
        }
    }

    private fun seasonQueryFor(shelf: HomeShelf): SeasonAnimeQuery = when (shelf) {
        HomeShelf.THIS_SEASON -> {
            val today = LocalDate.now(clock)
            val current = SeasonResolver.current(today.year, today.monthValue)
            SeasonAnimeQuery(
                page = 1,
                perPage = HomeShelfSize.VALUE,
                season = Optional.present(current.season.toMediaSeason()),
                seasonYear = Optional.present(current.year),
                status = Optional.absent(),
                sort = listOf(MediaSort.POPULARITY_DESC),
                isAdult = Optional.present(false)
            )
        }
        HomeShelf.UPCOMING -> SeasonAnimeQuery(
            page = 1,
            perPage = HomeShelfSize.VALUE,
            season = Optional.absent(),
            seasonYear = Optional.absent(),
            status = Optional.present(MediaStatus.NOT_YET_RELEASED),
            sort = listOf(MediaSort.POPULARITY_DESC),
            isAdult = Optional.present(false)
        )
        HomeShelf.TRENDING -> SeasonAnimeQuery(
            page = 1,
            perPage = HomeShelfSize.VALUE,
            season = Optional.absent(),
            seasonYear = Optional.absent(),
            status = Optional.absent(),
            sort = listOf(MediaSort.TRENDING_DESC),
            isAdult = Optional.present(false)
        )
        HomeShelf.TOP -> error("TOP uses the ranking table, not the season query")
    }
}
```

Add the shared size constant `HomeShelfSize.kt` in `domain`:

```kotlin
package com.example.animewiki.domain

object HomeShelfSize {
    const val VALUE = 25
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.example.animewiki.data.repository.HomeShelfRepositoryTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/animewiki/di/TimeModule.kt \
        app/src/main/java/com/example/animewiki/data/repository/HomeShelfRepository.kt \
        app/src/main/java/com/example/animewiki/domain/HomeShelfSize.kt \
        app/src/test/java/com/example/animewiki/data/repository/HomeShelfRepositoryTest.kt
git commit -m "feat: add HomeShelfRepository with cache-first shelves"
```

---

## Task 8: HomeViewModel

Exposes a per-shelf state map. Each shelf observes its cache (emitting Content
when non-empty) and refreshes independently; a refresh failure only surfaces as
Error when there is no cached content. Retry re-runs a single shelf.

**Files:**
- Create: `app/src/main/java/com/example/animewiki/ui/screens/home/HomeUiState.kt`
- Create: `app/src/main/java/com/example/animewiki/ui/screens/home/HomeViewModel.kt`
- Test: `app/src/test/java/com/example/animewiki/ui/screens/home/HomeViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.animewiki.ui.screens.home

import app.cash.turbine.test
import com.example.animewiki.data.repository.HomeShelfRepository
import com.example.animewiki.domain.model.HomeShelf
import com.example.animewiki.domain.model.HomeShelfAnime
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private fun anime(id: Int) =
        HomeShelfAnime(id = id, title = "A$id", imageUrl = "https://img/$id.jpg", score = 8.0)

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun repository(): HomeShelfRepository = mockk(relaxed = true) {
        HomeShelf.entries.forEach { every { observe(it) } returns MutableStateFlow(emptyList()) }
        coEvery { refresh(any()) } returns Unit
    }

    @Test
    fun `each shelf reaches content independently from its cache`() = runTest {
        val repo = repository()
        every { repo.observe(HomeShelf.TRENDING) } returns flowOf(listOf(anime(1)))
        val viewModel = HomeViewModel(repo)

        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state.getValue(HomeShelf.TRENDING) is ShelfState.Content)
            assertEquals(
                1,
                (state.getValue(HomeShelf.TRENDING) as ShelfState.Content).items.size
            )
        }
    }

    @Test
    fun `a shelf whose refresh fails with empty cache shows error, others unaffected`() = runTest {
        val repo = repository()
        every { repo.observe(HomeShelf.UPCOMING) } returns flowOf(emptyList())
        coEvery { repo.refresh(HomeShelf.UPCOMING) } throws RuntimeException("boom")
        every { repo.observe(HomeShelf.TRENDING) } returns flowOf(listOf(anime(2)))
        val viewModel = HomeViewModel(repo)

        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state.getValue(HomeShelf.UPCOMING) is ShelfState.Error)
            assertTrue(state.getValue(HomeShelf.TRENDING) is ShelfState.Content)
        }
    }

    @Test
    fun `retry re-runs only the requested shelf`() = runTest {
        val repo = repository()
        val viewModel = HomeViewModel(repo)
        advanceUntilIdle()

        viewModel.retry(HomeShelf.TOP)
        advanceUntilIdle()

        io.mockk.coVerify(atLeast = 2) { repo.refresh(HomeShelf.TOP) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.animewiki.ui.screens.home.HomeViewModelTest"`
Expected: FAIL to compile — `HomeViewModel`, `ShelfState` unresolved.

- [ ] **Step 3: Write minimal implementation**

`HomeUiState.kt`:

```kotlin
package com.example.animewiki.ui.screens.home

import com.example.animewiki.domain.model.HomeShelf
import com.example.animewiki.domain.model.HomeShelfAnime

sealed interface ShelfState {
    data object Loading : ShelfState
    data class Content(val items: List<HomeShelfAnime>) : ShelfState
    data object Error : ShelfState
}

typealias HomeUiState = Map<HomeShelf, ShelfState>
```

`HomeViewModel.kt`:

```kotlin
package com.example.animewiki.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.animewiki.data.repository.HomeShelfRepository
import com.example.animewiki.domain.model.HomeShelf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HomeShelfRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(
        HomeShelf.entries.associateWith { ShelfState.Loading }
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        HomeShelf.entries.forEach { shelf ->
            observe(shelf)
            refresh(shelf)
        }
    }

    fun retry(shelf: HomeShelf) = refresh(shelf)

    private fun observe(shelf: HomeShelf) {
        viewModelScope.launch {
            repository.observe(shelf).collect { items ->
                if (items.isNotEmpty()) set(shelf, ShelfState.Content(items))
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun refresh(shelf: HomeShelf) {
        viewModelScope.launch {
            if (!hasContent(shelf)) set(shelf, ShelfState.Loading)
            try {
                repository.refresh(shelf)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!hasContent(shelf)) set(shelf, ShelfState.Error)
            }
        }
    }

    private fun hasContent(shelf: HomeShelf) = _uiState.value[shelf] is ShelfState.Content

    private fun set(shelf: HomeShelf, state: ShelfState) {
        _uiState.update { current -> current + (shelf to state) }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.example.animewiki.ui.screens.home.HomeViewModelTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/animewiki/ui/screens/home/HomeUiState.kt \
        app/src/main/java/com/example/animewiki/ui/screens/home/HomeViewModel.kt \
        app/src/test/java/com/example/animewiki/ui/screens/home/HomeViewModelTest.kt
git commit -m "feat: add HomeViewModel with per-shelf state"
```

---

## Task 9: Home UI + strings

Compose screen with four shelves. No new unit test (UI composition); verified by
compile + the final Detekt/APK build in Task 10. Uses only string resources.

**Files:**
- Create: `app/src/main/java/com/example/animewiki/ui/screens/home/components/ShelfAnimeCard.kt`
- Create: `app/src/main/java/com/example/animewiki/ui/screens/home/components/ShelfEyebrow.kt`
- Create: `app/src/main/java/com/example/animewiki/ui/screens/home/components/ShelfRow.kt`
- Create: `app/src/main/java/com/example/animewiki/ui/screens/home/HomeScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`

- [ ] **Step 1: Add strings (both languages)**

Append to `res/values-en/strings.xml` (inside `<resources>`):

```xml
    <!-- Home shelves -->
    <string name="tab_home">Home</string>
    <string name="home_title">Home</string>
    <string name="home_shelf_this_season">This season</string>
    <string name="home_shelf_upcoming">Upcoming</string>
    <string name="home_shelf_top">Top</string>
    <string name="home_shelf_trending">Trending</string>
    <string name="home_eyebrow_trending">Trending</string>
    <string name="home_eyebrow_rank">#%1$d</string>
    <string name="home_eyebrow_episode">Ep %1$d · %2$s</string>
    <string name="home_eyebrow_premiere">%1$s %2$d</string>
    <string name="home_shelf_error">Couldn\'t load. Try again</string>
    <string name="season_winter">Winter</string>
    <string name="season_spring">Spring</string>
    <string name="season_summer">Summer</string>
    <string name="season_fall">Fall</string>
```

Append to `res/values/strings.xml` (Portuguese):

```xml
    <!-- Home shelves -->
    <string name="tab_home">Início</string>
    <string name="home_title">Início</string>
    <string name="home_shelf_this_season">Esta temporada</string>
    <string name="home_shelf_upcoming">Próximas estreias</string>
    <string name="home_shelf_top">Top</string>
    <string name="home_shelf_trending">Em alta</string>
    <string name="home_eyebrow_trending">Em alta</string>
    <string name="home_eyebrow_rank">#%1$d</string>
    <string name="home_eyebrow_episode">Ep %1$d · %2$s</string>
    <string name="home_eyebrow_premiere">%1$s %2$d</string>
    <string name="home_shelf_error">Não carregou. Tente de novo</string>
    <string name="season_winter">Inverno</string>
    <string name="season_spring">Primavera</string>
    <string name="season_summer">Verão</string>
    <string name="season_fall">Outono</string>
```

- [ ] **Step 2: Add the eyebrow helper**

`ShelfEyebrow.kt` (returns the localized eyebrow for a card, or null to hide it):

```kotlin
package com.example.animewiki.ui.screens.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.animewiki.R
import com.example.animewiki.domain.model.AnimeSeason
import com.example.animewiki.domain.model.HomeShelf
import com.example.animewiki.domain.model.HomeShelfAnime
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun shelfEyebrow(shelf: HomeShelf, anime: HomeShelfAnime): String? = when (shelf) {
    HomeShelf.THIS_SEASON -> episodeEyebrow(anime)
    HomeShelf.UPCOMING -> premiereEyebrow(anime)
    HomeShelf.TOP -> anime.rank?.let { stringResource(R.string.home_eyebrow_rank, it) }
    HomeShelf.TRENDING -> stringResource(R.string.home_eyebrow_trending)
}

@Composable
private fun episodeEyebrow(anime: HomeShelfAnime): String? {
    val episode = anime.nextEpisode ?: return null
    val airingAt = anime.nextAiringAtSeconds ?: return null
    val weekday = Instant.ofEpochSecond(airingAt)
        .atZone(ZoneId.systemDefault())
        .dayOfWeek
        .getDisplayName(TextStyle.SHORT, Locale.getDefault())
    return stringResource(R.string.home_eyebrow_episode, episode, weekday)
}

@Composable
private fun premiereEyebrow(anime: HomeShelfAnime): String? {
    val season = anime.season ?: return null
    val year = anime.year ?: return null
    val seasonName = stringResource(
        when (season) {
            AnimeSeason.WINTER -> R.string.season_winter
            AnimeSeason.SPRING -> R.string.season_spring
            AnimeSeason.SUMMER -> R.string.season_summer
            AnimeSeason.FALL -> R.string.season_fall
        }
    )
    return stringResource(R.string.home_eyebrow_premiere, seasonName, year)
}
```

- [ ] **Step 3: Add the card**

`ShelfAnimeCard.kt`:

```kotlin
package com.example.animewiki.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.animewiki.domain.model.HomeShelf
import com.example.animewiki.domain.model.HomeShelfAnime

@Composable
internal fun ShelfAnimeCard(
    shelf: HomeShelf,
    anime: HomeShelfAnime,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = Modifier.width(120.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AsyncImage(
                model = anime.imageUrl,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            )
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                shelfEyebrow(shelf, anime)?.let { eyebrow ->
                    Text(
                        text = eyebrow,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                anime.score?.let {
                    Text(
                        text = "★ ${"%.2f".format(it)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 4: Add the shelf row**

`ShelfRow.kt`:

```kotlin
package com.example.animewiki.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.animewiki.R
import com.example.animewiki.domain.model.HomeShelf
import com.example.animewiki.ui.screens.home.ShelfState

@Composable
internal fun ShelfRow(
    shelf: HomeShelf,
    title: String,
    state: ShelfState,
    onAnimeClick: (Int) -> Unit,
    onRetry: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        when (state) {
            is ShelfState.Loading -> ShelfPlaceholder()
            is ShelfState.Error -> ShelfError(onRetry)
            is ShelfState.Content -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                items(state.items, key = { it.id }) { anime ->
                    ShelfAnimeCard(shelf, anime) { onAnimeClick(anime.id) }
                }
            }
        }
    }
}

@Composable
private fun ShelfPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("…", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ShelfError(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.home_shelf_error))
        }
    }
}
```

- [ ] **Step 5: Add the screen**

`HomeScreen.kt`:

```kotlin
package com.example.animewiki.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.animewiki.R
import com.example.animewiki.domain.model.HomeShelf
import com.example.animewiki.ui.components.AnimeWikiScaffold
import com.example.animewiki.ui.screens.home.components.ShelfRow

@Composable
fun HomeScreen(
    onAnimeClick: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AnimeWikiScaffold(
        title = stringResource(R.string.home_title),
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.top_anime_settings)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            HomeShelf.entries.forEach { shelf ->
                ShelfRow(
                    shelf = shelf,
                    title = stringResource(shelf.titleRes()),
                    state = state.getValue(shelf),
                    onAnimeClick = onAnimeClick,
                    onRetry = { viewModel.retry(shelf) }
                )
            }
        }
    }
}

private fun HomeShelf.titleRes(): Int = when (this) {
    HomeShelf.THIS_SEASON -> R.string.home_shelf_this_season
    HomeShelf.UPCOMING -> R.string.home_shelf_upcoming
    HomeShelf.TOP -> R.string.home_shelf_top
    HomeShelf.TRENDING -> R.string.home_shelf_trending
}
```

- [ ] **Step 6: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: SUCCESS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/animewiki/ui/screens/home \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-en/strings.xml
git commit -m "feat: add Home screen with season shelves"
```

---

## Task 10: Wire Home into navigation + full verification

**Files:**
- Modify: `app/src/main/java/com/example/animewiki/ui/navigation/AnimeWikiNavHost.kt`

- [ ] **Step 1: Add the Home tab as the start destination**

In `AnimeWikiNavHost.kt`:

Add to `object Tabs`:

```kotlin
    const val HOME = "home"
```

Add the import and icon:

```kotlin
import androidx.compose.material.icons.filled.Home
import com.example.animewiki.ui.screens.home.HomeScreen
```

Prepend the Home tab to `tabs`:

```kotlin
private val tabs = listOf(
    TabItem(Tabs.HOME, R.string.tab_home, Icons.Default.Home),
    TabItem(Tabs.TOP, R.string.tab_discover, Icons.Default.Explore),
    TabItem(Tabs.FAVORITES, R.string.tab_favorites, Icons.Default.Favorite)
)
```

In `MainTabs`, change the inner `NavHost` start destination and add the Home
composable:

```kotlin
        NavHost(
            navController = tabNavController,
            startDestination = Tabs.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Tabs.HOME) {
                HomeScreen(
                    onAnimeClick = onAnimeClick,
                    onSettingsClick = onSettingsClick
                )
            }
            composable(Tabs.TOP) {
                TopAnimeScreen(
                    onAnimeClick = onAnimeClick,
                    onSettingsClick = onSettingsClick
                )
            }
            composable(Tabs.FAVORITES) {
                FavoritesScreen(onAnimeClick = onAnimeClick)
            }
        }
```

- [ ] **Step 2: Run the full unit-test suite**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL — all existing and new JVM tests pass.

- [ ] **Step 3: Run Detekt**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL (autoCorrect handles formatting; fix any structural
findings inline, e.g. long methods).

- [ ] **Step 4: Build the debug APK**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/animewiki/ui/navigation/AnimeWikiNavHost.kt
git commit -m "feat: make Home the start destination"
```

- [ ] **Step 6: Phone-test checklist (manual, on the user's device)**

Before merge, verify on the phone:
- Home is the first tab on launch; bottom bar shows Home · Discover · Favorites.
- All four shelves populate; each scrolls horizontally; a tap opens details.
- Eyebrows read correctly per shelf (episode/day, premiere season+year, #rank, Trending).
- Airplane mode after a successful load: shelves still show their last content
  (Top from the ranking cache; the three network shelves from `home_shelf_item`).
- Force one shelf to fail (airplane mode on a cold install): that shelf shows the
  retry control while the others behave independently.
- Switch device language pt↔en: all shelf titles, eyebrows, and the tab label
  localize.
- Favorites saved before updating are still present after the v3→v4 upgrade.

---

## Self-Review

**Spec coverage:**
- Navigation / Home start destination (spec §4) → Task 10. ✓
- Four shelves, bounded preview, tap→details (spec §2, §6) → Tasks 8, 9. ✓
- Independent per-shelf fetch (Approach B, spec §5.1) → Tasks 7, 8. ✓
- `SeasonAnime.graphql` optional season/status, reuse `AnimeCardFields` +
  `nextAiringEpisode` (spec §5.3) → Task 2. ✓
- Season resolution, current only (spec §5.2) → Task 1. ✓
- Top reuses ranking table (spec §5.4) → Task 7 (`refreshTop`, `observeTop`). ✓
- Room cache, migration-safe, favorites preserved, schema export (spec §5.4) →
  Tasks 4, 5, 6. ✓
- Cache-first repository (spec §5.5) → Task 7. ✓
- Per-shelf error isolation + offline (spec §7) → Tasks 8, 9. ✓
- Bilingual strings incl. season/weekday localization (spec §6.3) → Tasks 1, 9. ✓
- Tests: resolver, mapper, viewmodel, repository, migration (spec §8) →
  Tasks 1, 3, 6, 7, 8. ✓
- Acceptance criteria (spec §9) → Task 10 phone checklist + unit tests. ✓

**Type consistency:** `HomeShelf` (THIS_SEASON/UPCOMING/TOP/TRENDING),
`HomeShelfAnime`, `ShelfState` (Loading/Content/Error), `HomeShelfSize.VALUE`
(25), `HomeShelfDao.replaceShelf`/`observeShelf`, `AnimeDao.observeTop`,
`toEntity(shelf, position)` / `toShelfAnime()` are used identically across
Tasks 3, 5, 7, 8, 9.

**Placeholder scan:** none — every step has concrete code and commands.

**Note on the TOP shelf:** the spec's "reuse the ranking table" is honored by a
non-destructive page-1 upsert in `refreshTop()` (Task 7), which populates the
`anime` table when the user lands on Home first without clearing Discover's
deeper pages.
