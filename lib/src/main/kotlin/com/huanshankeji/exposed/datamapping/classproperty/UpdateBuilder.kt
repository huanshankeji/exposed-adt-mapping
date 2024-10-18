package com.huanshankeji.exposed.datamapping.classproperty

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.EntityIDColumnType
import org.jetbrains.exposed.sql.statements.UpdateBuilder

/**
 * This is a workaround for a column that is possibly an [EntityID].
 * It seems needed since Exposed 0.53.0.
 */
fun <S> UpdateBuilder<*>.setWithColumnPossiblyBeingEntityId(column: Column<S>, value: S) {
    if (column.columnType is EntityIDColumnType<*>) {
        fun <S : Comparable<S>> typeParameterHelper(column: Column<EntityID<S>>, value: S) {
            this[column] = value
        }
        typeParameterHelper(
            column as Column<EntityID<Comparable<Any>>>, value as Comparable<Any>
        )
    } else
        this[column] = value
}
