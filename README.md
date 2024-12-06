# Exposed GADT mapping

[![Maven Central](https://img.shields.io/maven-central/v/com.huanshankeji/exposed-gadt-mapping)](https://search.maven.org/artifact/com.huanshankeji/exposed-gadt-mapping)

A library based on [Exposed](https://github.com/JetBrains/Exposed) [DSL](https://github.com/JetBrains/Exposed/wiki/DSL) providing mappings between data entities and tables with support for GADT (generalized algebraic data type), aka features including nested properties of composite class types, type parameters and their type inference, and sealed classes

This project is an attempt to provide an alternative to [Exposed DAO](https://github.com/JetBrains/Exposed/wiki/DAO) while supporting some more advanced functional programming features. See JetBrains/Exposed#24 for more details.

## Add to your dependencies

### The Maven coordinate

```kotlin
"com.huanshankeji:exposed-gadt-mapping:$libraryVersion"
```

### **Important note**

As Exposed is a library that has not reached stability yet and often has incompatible changes, you are recommended to stick to the same version of Exposed used by this library. The current version is v0.56.0.

## Basic usage guide

Please note that these APIs are far from stable. There are going to be refactors in future releases.

[Check out the API documentation here.](https://huanshankeji.github.io/exposed-gadt-mapping/.)

### Table and data definitions

#### Tables and joins

```kotlin
typealias DirectorId = Int

class Director(val directorId: DirectorId, val name: String)

class FilmDetails<DirectorT>(
    val sequelId: Int,
    val name: String,
    val director: DirectorT
)
typealias FilmDetailsWithDirectorId = FilmDetails<DirectorId>

typealias FilmId = Int

class Film<DirectorT>(val filmId: FilmId, val filmDetails: FilmDetails<DirectorT>)
typealias FilmWithDirectorId = Film<DirectorId>
typealias FullFilm = Film<Director>
```

#### Data entities and attributes

```kotlin
typealias DirectorId = Int

class Director(val directorId: DirectorId, val name: String)

class FilmDetails<DirectorT>(
    val sequelId: Int,
    val name: String,
    val director: DirectorT
)
typealias FilmDetailsWithDirectorId = FilmDetails<DirectorId>

typealias FilmId = Int

class Film<DirectorT>(val filmId: FilmId, val filmDetails: FilmDetails<DirectorT>)
typealias FilmWithDirectorId = Film<DirectorId>
typealias FullFilm = Film<Director>
```

A nested composite class property can either map to flattened fields or a table referenced by a foreign key: `FilmDetails` is a nested class in `Film`, but the corresponding table `Films` has the `FilmDetails` members/fields flattened directly instead of referencing a corresponding table for `FilmDetails` with a foreign key; on the contrary, a `director : Director` member of `FilmDetails<Director>` maps to the `Directors` table referenced.

As laid out above in the code, a recommended approach to define data types is to make necessary use of type parameters to improve code reuse.

### Create mappers

You can create mappers with the overloaded `reflectionBasedClassPropertyDataMapper` functions. Pass the `propertyColumnMappingConfigMapOverride` parameter to override the default options.

```kotlin
object Mappers {
    val director = reflectionBasedClassPropertyDataMapper<Director>(Directors)
    val filmDetailsWithDirectorId = reflectionBasedClassPropertyDataMapper<FilmDetailsWithDirectorId>(
        Films,
        propertyColumnMappingConfigMapOverride = mapOf(
            // The default name is the property name "director", but there is no column property with such a name, therefore we need to pass a custom name.
            FilmDetailsWithDirectorId::director to PropertyColumnMappingConfig.create<DirectorId>(columnPropertyName = Films::directorId.name)
        )
    )
    val filmWithDirectorId = reflectionBasedClassPropertyDataMapper<FilmWithDirectorId>(
        Films,
        propertyColumnMappingConfigMapOverride = mapOf(
            FilmWithDirectorId::filmDetails to PropertyColumnMappingConfig.create<FilmDetailsWithDirectorId>(
                // You can pass a nested custom mapper.
                customMapper = filmDetailsWithDirectorId
            )
        )
    )
    val fullFilm = reflectionBasedClassPropertyDataMapper<FullFilm>(
        filmsLeftJoinDirectors,
        propertyColumnMappingConfigMapOverride = mapOf(
            FullFilm::filmDetails to PropertyColumnMappingConfig.create(
                adt = PropertyColumnMappingConfig.Adt.Product(
                    mapOf(
                        // Because `name` is a duplicate name column so a custom mapper has to be passed here, otherwise the `CHOOSE_FIRST` option maps the data property `Director::name` to the wrong column `Films::name`.
                        FilmDetails<Director>::director to PropertyColumnMappingConfig.create<Director>(customMapper = director)
                    )
                )
            )
        )
    )
}
```

### CRUD operations

Call `updateBuilderSetter` to get a setter lambda to pass to `insert` or `update`. Call `selectWithMapper` to execute a query with a mapper.

```kotlin
val directorId = 1
val director = Director(directorId, "George Lucas")
Directors.insert(Mappers.director.updateBuilderSetter(director))

val episodeIFilmDetails = FilmDetails(1, "Star Wars: Episode I – The Phantom Menace", directorId)
Films.insert(Mappers.filmDetailsWithDirectorId.updateBuilderSetter(episodeIFilmDetails)) // insert without the ID since it's `AUTO_INCREMENT`

val filmId = 2
val episodeIIFilmDetails = FilmDetails(2, "Star Wars: Episode II – Attack of the Clones", directorId)
val filmWithDirectorId = FilmWithDirectorId(filmId, episodeIIFilmDetails)
Films.insert(Mappers.filmWithDirectorId.updateBuilderSetter(filmWithDirectorId)) // insert with the ID

val fullFilm = with(Mappers.fullFilm) {
    resultRowToData(filmsLeftJoinDirectors.select(neededColumns).where(Films.filmId eq filmId).single())
}
// not available yet, available soon in 0.2.0
val fullFilms =
    filmsLeftJoinDirectors.selectWithMapper(Mappers.fullFilm, Films.filmId inList listOf(1, 2)).toList()
```
