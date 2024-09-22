package com.huanshankeji.exposed.datamapping.classproperty

import com.huanshankeji.exposed.datamapping.selectWithMapper
import com.huanshankeji.exposed.datamapping.updateBuilderSetter
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

// copied and adapted from https://jetbrains.github.io/Exposed/deep-dive-into-dao.html

object Directors : IntIdTable("directors") {
    val directorId = id
    val name = varchar("name", 50)
}

object Films : IntIdTable() {
    val filmId = id
    val sequelId = integer("sequel_id").uniqueIndex()
    val name = varchar("name", 50)
    val directorId = integer("director_id").references(Directors.directorId)
}

val filmsLeftJoinDirectors = Films leftJoin Directors


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

fun main() {
    // TODO create a database with Testcontainers and connect
    transaction {
        val directorId = 1
        val director = Director(directorId, "George Lucas")
        Directors.insert(Mappers.director.updateBuilderSetter(director))

        val episodeIFilmDetails = FilmDetails(1, "Star Wars: Episode I – The Phantom Menace", directorId)
        Films.insert(Mappers.filmDetailsWithDirectorId.updateBuilderSetter(episodeIFilmDetails)) // insert without the ID since it's `AUTO_INCREMENT`

        val filmId = 2
        val episodeIIFilmDetails = FilmDetails(2, "Star Wars: Episode II – Attack of the Clones", directorId)
        val filmWithDirectorId =
            FilmWithDirectorId(filmId, episodeIIFilmDetails)
        Films.insert(Mappers.filmWithDirectorId.updateBuilderSetter(filmWithDirectorId)) // insert with the ID

        val fullFilm = with(Mappers.fullFilm) {
            resultRowToData(filmsLeftJoinDirectors.select(neededColumns).where(Films.filmId eq filmId).single())
        }
        // not available yet, available soon in 0.2.0
        val fullFilms =
            filmsLeftJoinDirectors.selectWithMapper(Mappers.fullFilm, Films.filmId inList listOf(1, 2)).toList()
    }
}
