package com.huanshankeji.exposed.datamapping

import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.Op

fun <Data : Any> ColumnSet.selectWithMapper(mapper: NullableDataQueryMapper<Data>, where: Op<Boolean>? = null) =
    select(mapper.neededColumns)
        .run { where?.let { where(it) } ?: this }
        .asSequence().map { mapper.resultRowToData(it) }
