# AnimeWiki 🌸

> A modern Android sample app exploring the [Jikan API](https://jikan.moe/) (an unofficial MyAnimeList wrapper). Built around offline-first caching, reactive search, weekly background notifications, and a custom Material 3 design system.

![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.04-4285F4?logo=jetpackcompose&logoColor=white)
![Material](https://img.shields.io/badge/Material%203-F48FB1?logo=materialdesign&logoColor=white)
![Min SDK](https://img.shields.io/badge/Min%20SDK-24-34A853)
![Target SDK](https://img.shields.io/badge/Target%20SDK-36-34A853)
![License](https://img.shields.io/badge/License-MIT-blue)

---

## 🎬 Demo

<!--
  To embed the video: on GitHub, drag the .mp4 file into the description of any
  issue/PR — GitHub uploads it to their CDN and returns a URL like
  https://github.com/user-attachments/assets/xxxxxxxx.mp4
  Paste that URL here as plain text (no Markdown) and GitHub renders an inline player.
-->

https://github.com/user-attachments/assets/25c90d25-fa4a-48b1-a2e5-d2a1e1d17fef

---

## 📱 Screenshots

<table>
  <tr>
    <td><img src="app/docs/screenshots/top_anime.png" width="220" alt="Top anime grid"/></td>
    <td><img src="app/docs/screenshots/details.png" width="220" alt="Anime details"/></td>
    <td><img src="app/docs/screenshots/search.png" width="220" alt="Search in action"/></td>
    <td><img src="app/docs/screenshots/offline.png" width="220" alt="Offline state"/></td>
  </tr>
  <tr>
    <td align="center"><sub>Top anime grid</sub></td>
    <td align="center"><sub>Details screen</sub></td>
    <td align="center"><sub>Search with debounce</sub></td>
    <td align="center"><sub>Offline-first</sub></td>
  </tr>
</table>

---

## ✨ Features

- **Top anime grid** with infinite scroll via Paging 3 + RemoteMediator
- **Rich details screen** with synopsis, genres, studios, airing info, and score
- **Reactive search** that debounces user input and swaps data sources on the fly
- **Pull-to-refresh** on the main grid
- **Offline-first** — cached content is shown instantly on cold start, with a discreet banner when the network is unreachable
- **Favorites tab** with a dedicated screen, persisted in Room and observed reactively
- **Settings screen** with light/dark/system theme selector, persisted in DataStore and reflected app-wide in real time
- **Weekly background notification** (every Monday at 9 AM) with the new #1 anime, scheduled via WorkManager — tapping the notification deep-links straight into the details screen
- **Custom adaptive launcher icon** with a monochrome layer for Android 13+ themed icons
- **Bilingual** (Portuguese and English) with automatic locale detection
- **Custom "Sakura Dream" Material 3 theme** — light and dark schemes plus the Quicksand typeface

---

## 🛠️ Tech Stack

| Category | Library |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose (with nested NavHost + deep links) |
| DI | Hilt (via KSP, no KAPT) |
| Networking | Retrofit 2 + OkHttp + Kotlinx Serialization |
| Image loading | Coil 3 |
| Paging | Paging 3 with `RemoteMediator` |
| Local storage | Room 2.7 |
| Preferences | DataStore (Preferences flavor) |
| Background work | WorkManager + Hilt-Work |
| Async | Kotlin Coroutines + Flow |
| Static analysis | Detekt + ktlint rules (via `detekt-formatting`) |
| Testing | JUnit 4 + MockK + Turbine + kotlinx-coroutines-test + Room in-memory |
| Build | Gradle Kotlin DSL + Version Catalog |

---

## 🏗️ Architecture

Layered, single-module, MVVM with a unidirectional data flow.

```
┌─────────────────────────────────────────────────────────┐
│  UI (Compose)                                           │
│  Screens · Components · AnimeWikiScaffold · Theme       │
└───────────────────────────┬─────────────────────────────┘
                            │ StateFlow<UiState> · PagingData
┌───────────────────────────┴─────────────────────────────┐
│  ViewModel                                              │
│  Orchestrates query state, switches data sources        │
└───────────────────────────┬─────────────────────────────┘
                            │ Flow<PagingData<Anime>>
┌───────────────────────────┴─────────────────────────────┐
│  Data                                                   │
│  ┌─────────────┐      ┌──────────────────────────────┐  │
│  │ Repository  │◄────▶│ RemoteMediator + PagingSource│  │
│  └──────┬──────┘      └──────┬──────────────────┬────┘  │
│         │                    │                  │       │
│    ┌────▼────┐    ┌────▼─────┐    ┌────▼────┐    ┌──────▼──────┐ │
│    │  Room   │    │ Retrofit │    │  Coil   │    │  DataStore  │ │
│    └─────────┘    └──────────┘    └─────────┘    └─────────────┘ │
└─────────────────────────────────────────────────────────┘

           ┌─────────────────────────────────┐
           │  WorkManager (every Monday 9am) │
           │  TopAnimeSyncWorker             │
           └────────────┬────────────────────┘
                        ↓ uses Hilt-injected
           ┌─────────────────────────────────┐
           │  JikanApi + NotificationHelper  │
           │  → posts notification with deep │
           │    link animewiki://details/{id}│
           └─────────────────────────────────┘
```

- **The UI never talks to the network directly.** It observes `Flow<PagingData>` from the ViewModel.
- **Room is the single source of truth for the top anime list.** `RemoteMediator` fills and refreshes it from the Jikan API.
- **Search results are transient** (no caching) — a lightweight `PagingSource` fetches directly from the API.
- **Favorites live in their own Room table** — a separate, reactive `Flow<List<Anime>>` so the cache layer can be invalidated without losing user data.
- **DataStore** holds user preferences (theme mode, notification opt-in) and is observed at app boot to drive `AnimeWikiTheme` reactively.
- **WorkManager** runs `TopAnimeSyncWorker` on a 7-day schedule, gated by `NetworkType.CONNECTED`. The Worker is `@HiltWorker`-annotated so it gets the same `JikanApi` and dependencies as the rest of the app.

---

## 💡 Implementation Highlights

### Offline-first pagination with `RemoteMediator`

The top grid doesn't just cache a few items — it **persists paginated state** (`prev`/`next` page keys per anime) so scrolling, closing the app, and reopening offline all behave naturally.

```kotlin
override suspend fun load(
    loadType: LoadType,
    state: PagingState<Int, AnimeEntity>
): MediatorResult {
    val page = when (loadType) {
        LoadType.REFRESH -> 1
        LoadType.APPEND -> state.lastItemOrNull()
            ?.let { db.remoteKeyDao().getKey(it.id)?.nextKey }
            ?: return MediatorResult.Success(endOfPaginationReached = true)
        LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
    }

    return try {
        val response = api.getTopAnime(page = page, limit = state.config.pageSize)
        db.withTransaction {
            if (loadType == LoadType.REFRESH) {
                db.remoteKeyDao().clearAll()
                db.animeDao().clearAll()
            }
            db.remoteKeyDao().upsertAll(/* ... */)
            db.animeDao().upsertAll(/* ... */)
        }
        MediatorResult.Success(endOfPaginationReached = !response.pagination.hasNextPage)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        MediatorResult.Error(e)
    }
}
```

### Reactive search with five Flow operators working together

The search field emits to a `StateFlow<String>`. A pipeline debounces keystrokes, swaps data sources on an empty query, and cancels in-flight requests when the query changes:

```kotlin
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
val animeList: Flow<PagingData<Anime>> = _query
    .debounce { q -> if (q.isBlank()) 0L else 400L }
    .distinctUntilChanged()
    .flatMapLatest { q ->
        if (q.isBlank()) repository.topAnime()
        else repository.searchAnime(q.trim())
    }
    .cachedIn(viewModelScope)
```

`flatMapLatest` guarantees that typing "fr" → "fri" → "frie" only keeps the last subscription alive; `cachedIn` survives configuration changes.

### Background work with Hilt-injected `CoroutineWorker` + deep linking

A `@HiltWorker` fetches the weekly #1 anime, posts a notification, and uses a deep link intent so tapping it opens the details screen — even from a cold start.

```kotlin
@HiltWorker
class TopAnimeSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val api: JikanApi,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val top = api.getTopAnime(page = 1, limit = 1).data?.firstOrNull()
            ?: return Result.retry()

        notificationHelper.showWeeklyTopAnime(
            animeId = top.malId,
            title = top.titleEnglish ?: top.title,
            score = "%.2f".format(top.score)
        )
        return Result.success()
    }
}
```

The deep link is wired into the navigation graph:

```kotlin
composable(
    route = Routes.DETAILS,
    arguments = listOf(navArgument("id") { type = NavType.IntType }),
    deepLinks = listOf(
        navDeepLink { uriPattern = "animewiki://details/{id}" }
    )
) { /* … */ }
```

### Custom Material 3 color scheme

The "Sakura Dream" palette is tuned so user-chosen brand colors become `primaryContainer` / `secondaryContainer` / `tertiaryContainer`, while derived darker tones drive `primary` / `secondary` / `tertiary` and meet WCAG AA contrast against `onPrimary` text.

```kotlin
private val SakuraLightColors = lightColorScheme(
    primary = SakuraRose,           // deep rose — WCAG AA against white
    primaryContainer = SakuraPink,  // the brand-defining #F48FB1 — soft, gentle
    secondary = LavenderPlum,
    secondaryContainer = LavenderMist,
    tertiary = MatchaDeepGreen,
    tertiaryContainer = MatchaGreen,
    background = CreamShell,
    /* … */
)
```

---

## 🌐 Internationalization

All user-facing strings live in `res/values/strings.xml` (Portuguese, default) and `res/values-en/strings.xml` (English). Android picks the right file based on the device's locale — `pt-*` falls into the default, `en-*` switches to the English bundle, anything else falls back to Portuguese.

Adding a third language is purely additive: create `res/values-<lang>/strings.xml` with the same keys translated.

---

## 🧪 Testing

The project uses a layered testing strategy that touches every part of the stack:

| Test class | Type | What it validates |
|---|---|---|
| `AnimeMapperTest` | JVM unit | Pure DTO ↔ Entity ↔ Domain conversions, edge cases like missing fields |
| `AnimeRepositoryTest` | JVM unit (MockK) | Favorite toggle delegates to DAO, `getAnimeDetails` falls back to cache when network fails |
| `TopAnimeViewModelTest` | JVM unit (Turbine) | `StateFlow<String>` query state behaves through `onQueryChange` and `clearQuery` |
| `FavoriteDaoTest` | Instrumented (Room in-memory) | DAO insert / observe / delete / replace-on-conflict / ordering |

Run unit tests:

```bash
./gradlew testDebugUnitTest
```

Run instrumented tests (requires a connected device or running emulator):

```bash
./gradlew connectedDebugAndroidTest
```

---

## 🧹 Static Analysis

Detekt (with the `detekt-formatting` plugin embedding ktlint rules) runs across the codebase. The configuration in `config/detekt/detekt.yml` is tuned for Compose — it disables noisy rules like `MagicNumber` for `dp` values and ignores `@Composable` for `FunctionNaming` (since Composables use PascalCase by convention).

Run a check:

```bash
./gradlew detekt
```

The `autoCorrect = true` flag means most formatting fixes are applied in place; only structural findings (long methods, complex expressions) require manual attention.

---

## 📁 Project Structure

```
app/src/main/java/com/example/animewiki/
├── data/
│   ├── local/           # Room: entities, DAOs, converters, AppDatabase
│   ├── remote/          # Retrofit + Kotlinx Serialization DTOs
│   ├── paging/          # RemoteMediator + transient SearchPagingSource
│   ├── mapper/          # DTO ↔ Entity ↔ Domain conversions
│   ├── repository/      # Exposes Flow<PagingData> to the UI
│   ├── preferences/     # DataStore-backed user preferences
│   └── notification/    # Worker, NotificationHelper, NotificationScheduler
├── domain/
│   └── model/           # Plain Kotlin models, free of any framework
├── di/                  # Hilt modules (Network, Database)
├── ui/
│   ├── theme/           # Sakura Dream: colors, typography, shapes
│   ├── components/      # Shared composables (AnimeWikiScaffold)
│   ├── navigation/      # Nested NavHost + bottom bar + deep links
│   ├── AppViewModel.kt  # Top-level ViewModel for theme observation
│   └── screens/
│       ├── topAnime/    # Top anime grid + search + its components
│       ├── details/     # Anime details + favorite toggle
│       ├── favorites/   # Favorites tab
│       └── settings/    # Theme + notification preferences
├── AnimeWikiApp.kt      # @HiltAndroidApp + Configuration.Provider
└── MainActivity.kt      # @AndroidEntryPoint host
```

---

## 🚀 Getting Started

```bash
git clone https://github.com/jesstoselli/animewiki.git
cd animewiki
./gradlew assembleDebug
```

Open in Android Studio Ladybug or newer, wait for the Gradle sync, and run on any emulator or device with **API 24+**.

The Jikan API requires no authentication or API key.

To exercise the weekly notification flow without waiting until Monday, open **Settings → Notifications**, enable the toggle, then tap **Test notification now** — it enqueues a one-shot run of the same Worker.

---

## 🗺️ Roadmap

- [x] Compose UI + Material 3 custom theme
- [x] Paging 3 on the top anime grid
- [x] Details screen with navigation
- [x] Offline-first caching (Room + RemoteMediator)
- [x] Reactive search with debounce
- [x] Pull-to-refresh
- [x] Favorites screen (local CRUD)
- [x] Internationalization (pt-BR default, en as secondary)
- [x] User preferences via DataStore (theme toggle)
- [x] Weekly top-anime push notification (WorkManager + Notifications)
- [x] Deep link from notification to details
- [x] Custom adaptive launcher icon (with monochrome themed icon)
- [x] Static analysis with detekt + ktlint
- [x] Unit and instrumented tests across data and presentation layers

---

## 🙏 Credits

- **Data**: [Jikan API v4](https://jikan.moe/) — unofficial MyAnimeList REST wrapper
- **Posters & metadata**: © [MyAnimeList](https://myanimelist.net/) and respective rights holders
- **Typeface**: [Quicksand](https://fonts.google.com/specimen/Quicksand) via Google Fonts

---

## 📄 License

MIT. See [LICENSE](LICENSE) for details.

---

<sub>Built as a learning / portfolio project. Not affiliated with MyAnimeList or Jikan.</sub>
