# Task 3 Report — Anime Discovery Filters

## Resultado

`AnimeBrowseCriteria` agora percorre o contrato Retrofit, `AnimeSearchPagingSource`,
`AnimeRepository` e o call site de busca atual do `TopAnimeViewModel`. O feed padrão
continua usando `repository.topAnime()` quando a consulta está vazia.

## RED

Foi criado primeiro `AnimeSearchPagingSourceTest` com dois cenários:

- consulta normalizada com formato, classificação e gêneros ordenados;
- busca somente por filtro, com `query = null` e sem parâmetro `q`.

Comando executado:

```bash
./gradlew testDebugUnitTest --tests "com.example.animewiki.data.paging.AnimeSearchPagingSourceTest"
```

Resultado esperado e observado: `BUILD FAILED` na compilação do teste, pois
`AnimeSearchPagingSource` ainda aceitava `String` e `JikanApi.searchAnime` não
possuía os parâmetros `type`, `rating` e `genres`.

## GREEN

Implementação mínima aplicada:

- `JikanApi.searchAnime` recebe `q`, `type`, `rating` e `genres` anuláveis;
- o PagingSource deriva esses valores de `AnimeBrowseCriteria` e usa argumentos nomeados;
- `genresQuery` do contrato de domínio fornece a ordem determinística;
- o repositório recebe `AnimeBrowseCriteria`;
- a busca do ViewModel cria critérios a partir da consulta existente.

Comandos executados:

```bash
./gradlew testDebugUnitTest --tests "com.example.animewiki.data.paging.AnimeSearchPagingSourceTest"
./gradlew testDebugUnitTest
git diff --check
```

Resultados: ambos os comandos Gradle terminaram com `BUILD SUCCESSFUL`; o teste
focado executou os dois cenários sem falhas; `git diff --check` não reportou erros.

## Arquivos modificados

- `app/src/main/java/com/example/animewiki/data/remote/JikanApi.kt`
- `app/src/main/java/com/example/animewiki/data/paging/AnimeSearchPagingSource.kt`
- `app/src/main/java/com/example/animewiki/data/repository/AnimeRepository.kt`
- `app/src/main/java/com/example/animewiki/ui/screens/topAnime/TopAnimeViewModel.kt`
- `app/src/test/java/com/example/animewiki/data/paging/AnimeSearchPagingSourceTest.kt`
- `.superpowers/sdd/task-3-report.md`

## Self-review

Verificado que `q` é `null` para critérios filter-only, `genres` é entregue pelo
valor ordenado do domínio e todos os parâmetros Retrofit introduzidos são
anuláveis. Nenhuma chamada ao Jikan ao vivo foi realizada.

## Commit

Mensagem planejada: `feat: apply filters to anime paging requests`

## Preocupações

O Gradle reporta avisos preexistentes sobre `compileSdk = 36` com Android Gradle
Plugin 8.8.2 e recursos descontinuados para o Gradle 10; não fazem parte do escopo
da Task 3.
