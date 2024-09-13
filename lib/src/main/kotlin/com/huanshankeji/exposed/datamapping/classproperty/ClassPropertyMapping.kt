package com.huanshankeji.exposed.datamapping.classproperty

import com.huanshankeji.BidirectionalConversion
import com.huanshankeji.exposed.datamapping.DataMapper
import com.huanshankeji.exposed.datamapping.NullableDataMapper
import com.huanshankeji.exposed.datamapping.classproperty.OnDuplicateColumnPropertyNames.CHOOSE_FIRST
import com.huanshankeji.exposed.datamapping.classproperty.OnDuplicateColumnPropertyNames.THROW
import com.huanshankeji.exposed.datamapping.classproperty.PropertyColumnMapping.*
import com.huanshankeji.kotlin.reflect.fullconcretetype.FullConcreteTypeClass
import com.huanshankeji.kotlin.reflect.fullconcretetype.FullConcreteTypeProperty1
import com.huanshankeji.kotlin.reflect.fullconcretetype.fullConcreteTypeClassOf
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.typeOf
import kotlin.sequences.Sequence

// Our own class mapping implementation using reflection which should be adapted using annotation processors and code generation in the future.


// TODO: unify type parameter names

typealias PropertyColumnMappings<Data> = List<PropertyColumnMapping<Data, *>>
//typealias LessStrictlyTypedPropertyColumnMappings<Data> = List<PropertyColumnMapping<Data, Any?>>
/** In the order of the constructor arguments. */
typealias ClassPropertyColumnMappings<Data> = PropertyColumnMappings<Data>

/*
TODO (ADT normalization) consider decoupling/removing `property` and `Data` from this class and renaming it to `ColumnMapping`
 and add a `PropertyColumnMapping` containing the property and the `ColumnMapping`.
 However, after the refactor, `ColumnMapping` will still be coupled with `ClassPropertyColumnMappings` which is coupled with `PropertyColumnMapping`,
 so I am not sure whether this is necessary.
*/
sealed class PropertyColumnMapping<Data : Any, PropertyData>(val fctProperty: FullConcreteTypeProperty1<Data, PropertyData>) {
    class SqlPrimitive<Data : Any, PropertyData>(
        fctProperty: FullConcreteTypeProperty1<Data, PropertyData>,
        val column: Column<PropertyData>
    ) : PropertyColumnMapping<Data, PropertyData>(fctProperty)

    class NestedClass<Data : Any, PropertyData>(
        fctProperty: FullConcreteTypeProperty1<Data, PropertyData>,
        val nullability: Nullability<PropertyData>,
        val adt: Adt<PropertyData & Any>
    ) : PropertyColumnMapping<Data, PropertyData>(fctProperty) {
        sealed class Nullability<PropertyData> {
            class NonNullable<NotNullPropertyData : Any> : Nullability<NotNullPropertyData>()
            class Nullable<NotNullPropertyData : Any>(val whetherNullDependentColumn: Column<*>) :
                Nullability<NotNullPropertyData?>()
        }

        // ADT: algebraic data type
        sealed class Adt<NotNullPropertyData : Any> {
            class Product<NotNullPropertyData : Any>(val nestedMappings: ClassPropertyColumnMappings<NotNullPropertyData>) :
                Adt<NotNullPropertyData>()

            class Sum<NotNullPropertyData : Any, CaseValue>(
                val subclassMap: Map<KClass<out NotNullPropertyData>, Product<out NotNullPropertyData>>,
                val sumTypeCaseConfig: SumTypeCaseConfig<NotNullPropertyData, CaseValue>
            ) : Adt<NotNullPropertyData>() {
                val columnsForAllSubclasses = buildSet {
                    for (subclassProductMapping in subclassMap.values)
                        addAll(subclassProductMapping.nestedMappings.getColumnSet())
                }.toList()
            }
        }
    }

    class Custom<Data : Any, PropertyData>(
        fctProperty: FullConcreteTypeProperty1<Data, PropertyData>,
        val nullableDataMapper: NullableDataMapper<PropertyData>
    ) : PropertyColumnMapping<Data, PropertyData>(fctProperty)

    class Skip<Data : Any, PropertyData>(fctProperty: FullConcreteTypeProperty1<Data, PropertyData>) :
        PropertyColumnMapping<Data, PropertyData>(fctProperty)
}

class SumTypeCaseConfig<SuperclassData : Any, CaseValue>(
    val caseValueColumn: Column<CaseValue>,
    val caseValueKClassConversion: BidirectionalConversion<CaseValue, KClass<out SuperclassData>>,
    /*
    val caseValueToClass: (CaseValue) -> KClass<out SuperclassData>,
    /**
     * The [KClass] parameter is null to set a default value for the case value column when the corresponding data is `null`.
     */
    val classToCaseValue: (KClass<out SuperclassData>?) -> CaseValue
    */
)


// see: https://kotlinlang.org/docs/basic-types.html, https://www.postgresql.org/docs/current/datatype.html
// Types that are commented out are not ensured to work yet.
val defaultNotNullExposedSqlPrimitiveClasses = listOf(
    Byte::class, Short::class, Int::class, Long::class, /*BigInteger::class,*/
    UByte::class, UShort::class, UInt::class, ULong::class,
    Float::class, Double::class, /*BigDecimal::class,*/
    Boolean::class,
    ByteArray::class,
    //Char::class,
    String::class,
    // types related to time and date
)

private fun KClass<*>.isEnumClass() =
    isSubclassOf(Enum::class)

fun KClass<*>.isExposedSqlPrimitiveType(): Boolean =
    this in defaultNotNullExposedSqlPrimitiveClasses || isEnumClass()

fun KType.isExposedSqlPrimitiveType() =
    (classifier as KClass<*>).isExposedSqlPrimitiveType()

class ColumnWithPropertyName(val propertyName: String, val column: Column<*>)

fun getColumnsWithPropertyNamesWithoutTypeParameter(
    table: Table, clazz: KClass<out Table> = table::class
): Sequence<ColumnWithPropertyName> =
    getColumnProperties(clazz).map {
        @Suppress("UNCHECKED_CAST")
        ColumnWithPropertyName(it.name, (it as KProperty1<Any, Column<*>>)(table))
    }

enum class OnDuplicateColumnPropertyNames {
    CHOOSE_FIRST, THROW
}

fun getColumnByPropertyNameMap(
    tables: List<Table>,
    onDuplicateColumnPropertyNames: OnDuplicateColumnPropertyNames = CHOOSE_FIRST // defaults to `CHOOSE_FIRST` because there are very likely to be duplicate columns when joining table
): Map<String, Column<*>> {
    val columnsMap = tables.asSequence()
        .flatMap { table -> getColumnsWithPropertyNamesWithoutTypeParameter(table) }
        .groupBy { it.propertyName }
    return columnsMap.mapValues {
        it.value.run {
            when (onDuplicateColumnPropertyNames) {
                CHOOSE_FIRST -> first()
                THROW -> single()
            }
                .column
        }
    }
}

private val logger = LoggerFactory.getLogger("class property mapping") // TODO: rename

// Open or abstract but not sealed classes are not supported after this refactor because non-sealed subclasses are not supported yet in `FullConcreteTypeClass`.

// TODO remove or support open and abstract classes again
private fun KClass<*>.isInheritable() =
    /*isOpen || isAbstract ||*/ isSealed

// TODO remove or support open and abstract classes again
private fun KClass<*>.isAbstractOrSealed() =
    /*isAbstract ||*/ isSealed

/**
 * @param skip both writing and reading. Note that the property type need not be nullable if it's only used for writing.
 * @param whetherNullDependentColumn required for nullable properties.
 * @param adt sub-configs for properties and subclasses.
 */
// TODO refactor the mutually exclusive arguments to sum type constructors (sealed subclasses)
class PropertyColumnMappingConfig<P>(
    type: KType,
    val skip: Boolean = false,
    // TODO Support query-only mappers, and update-only mappers. However this may require HKT or unsafe casting.
    val customMapper: NullableDataMapper<P>? = null,
    usedForQuery: Boolean = true,
    val columnPropertyName: String? = null, // TODO: use the property directly instead of the name string
    val whetherNullDependentColumn: Column<*>? = null, // for query
    /* TODO: whether it's null can depend on all columns:
        the property is null if when all columns are null (warn if some columns are not null),
        or a necessary column is null,
        in both cases of which warn if all nested properties are nullable */
    val adt: Adt<P & Any>? = null, // for query and update
) {
    init {
        // perform the checks

        // TODO log property information instead of just property return type information in the messages
        if (type.isMarkedNullable) {
            if (skip && whetherNullDependentColumn !== null || adt !== null)
                logger.warn("${::whetherNullDependentColumn.name} and ${::adt.name} are unnecessary when ${::skip.name} is configured to true for $type.")
        } else {
            // Non-nullable properties can be skipped when updating but not when querying.
            if (usedForQuery)
                require(!skip)
            require(whetherNullDependentColumn === null) {
                "`whetherNullDependentColumn` should be null for a not-null type $type"
            }
        }


        if (type.isExposedSqlPrimitiveType()) {
            if (whetherNullDependentColumn !== null)
                logger.warn("${::whetherNullDependentColumn} is set for a primitive type $type and will be ignored.")
            if (adt !== null)
                logger.warn("${::adt} is set for a primitive type $type and will be ignored.")
        }
        @Suppress("UNCHECKED_CAST")
        val clazz = type.classifier as KClass<P & Any>
        when (adt) {
            is Adt.Product -> require(clazz.isFinal || clazz.isOpen) { "the class $clazz must be instantiable (final or open) to be treated as a product type" }
            is Adt.Sum<*, *> -> require(clazz.isInheritable()) { "the class $clazz must be inheritable (open, abstract, or sealed) to be treated as a sum type" }
            null -> {}
        }
    }

    companion object {
        inline fun <reified PropertyData> create(
            skip: Boolean = false,
            customMapper: NullableDataMapper<PropertyData>? = null,
            usedForQuery: Boolean = true,
            columnPropertyName: String? = null, // TODO: use the column property
            nullDependentColumn: Column<*>? = null,
            adt: Adt<PropertyData & Any>? = null
        ) =
            PropertyColumnMappingConfig(
                typeOf<PropertyData>(), skip, customMapper, usedForQuery, columnPropertyName, nullDependentColumn, adt
            )
    }

    // ADT: algebraic data type
    sealed class Adt<Data : Any> {
        class Product<Data : Any>(val nestedConfigMap: PropertyColumnMappingConfigMap<Data>) :
            Adt<Data>()

        class Sum<Data : Any, CaseValue>(
            clazz: KClass<Data>,
            val subclassProductConfigMapOverride: Map<KClass<out Data>, Product<out Data>>, // TODO: why can't a sum type nest another sum type?
            val sumTypeCaseConfig: SumTypeCaseConfig<Data, CaseValue>
        ) : Adt<Data>() {
            init {
                require(subclassProductConfigMapOverride.keys.all { !it.isInheritable() && it.isSubclassOf(clazz) })
            }

            companion object {
                inline fun <reified Data : Any, CaseValue> createForSealed(
                    subclassProductConfigMapOverride: Map<KClass<out Data>, Product<out Data>> = emptyMap(),
                    sumTypeCaseConfig: SumTypeCaseConfig<Data, CaseValue>
                ): Sum<Data, CaseValue> {
                    val clazz = Data::class
                    require(clazz.isSealed)
                    return Sum(clazz, subclassProductConfigMapOverride, sumTypeCaseConfig)
                }

                // TODO remove or support open and abstract classes again
                inline fun <reified Data : Any, CaseValue> createForAbstract(
                    subclassProductConfigMap: Map<KClass<out Data>, Product<out Data>>,
                    sumTypeCaseConfig: SumTypeCaseConfig<Data, CaseValue>
                ): Sum<Data, CaseValue> {
                    val clazz = Data::class
                    require(clazz.isAbstract)
                    return Sum(clazz, subclassProductConfigMap, sumTypeCaseConfig)
                }
            }
        }

        // not needed
        //class Enum<Data : kotlin.Enum<*>, CaseValue> : Adt<Data>()
    }
}

typealias PropertyColumnMappingConfigMap2<Data /*: Any*/, PropertyReturnType> = Map<KProperty1<Data, PropertyReturnType>, PropertyColumnMappingConfig<PropertyReturnType>>
// TODO Constrain the property return type and the config type parameter to be the same. Consider using builder DSLs.
typealias PropertyColumnMappingConfigMap<Data /*: Any*/> = PropertyColumnMappingConfigMap2<Data, *>

private fun KClass<*>.isObject() =
    objectInstance !== null

private fun <Data : Any> doGetDefaultClassPropertyColumnMappings(
    fullConcreteTypeClass: FullConcreteTypeClass<Data>,
    tables: List<Table>, // for error messages only
    columnByPropertyNameMap: Map<String, Column<*>>, // TODO: refactor as `Data` may be a sum type
    propertyColumnMappingConfigMapOverride: PropertyColumnMappingConfigMap<Data> = emptyMap(),
    customMappings: PropertyColumnMappings<Data> = emptyList()
    /* TODO Constructing `FullConcreteTypeProperty1` seems complicated after the code is refactored.
        Consider refactoring `PropertyColumnMapping` with one extra `Property` type parameter and apply simple `KProperty` for `customMappings`,
        or merging it into config. */
): ClassPropertyColumnMappings<Data> {
    val customMappingPropertySet = customMappings.asSequence().map { it.fctProperty }.toSet()

    val dataFctMemberPropertyMap = fullConcreteTypeClass.memberProperties.asSequence()
        .filterNot { it in customMappingPropertySet }
        .associateBy { it.kProperty.name }
    val customMappingMap = customMappings.associateBy { it.fctProperty.kProperty.name }

    val kClass = fullConcreteTypeClass.kClass
    return if (kClass.isObject()) // mainly for case objects of sealed classes
        emptyList() // TODO: use `null`
    else (kClass.primaryConstructor
        ?: throw IllegalArgumentException("$kClass must have a primary constructor with all the properties to be mapped to columns to be mapped as a product type"))
        .parameters.map {
            val name = it.name!!

            val customMapping = customMappingMap[name]
            if (customMapping !== null)
                return@map customMapping

            val fctProperty = dataFctMemberPropertyMap.getOrElse(name) {
                throw IllegalArgumentException("primary constructor parameter `$it` is not a property in the class `$kClass`")
            }
            val kProperty = fctProperty.kProperty
            require(it.type == kProperty.returnType) {
                "primary constructor parameter `$it` and property `$kProperty` have different types"
            }

            // This function is added to introduce a new type parameter `PropertyData` to constrain the types better.
            fun <PropertyData> typeParameterHelper(fctProperty: FullConcreteTypeProperty1<Data, PropertyData>): PropertyColumnMapping<Data, PropertyData> {
                @Suppress("NAME_SHADOWING")
                val kProperty = fctProperty.kProperty
                val config =
                    propertyColumnMappingConfigMapOverride[kProperty] as PropertyColumnMappingConfig<PropertyData>?
                if (config?.skip == true)
                    return Skip(fctProperty)
                config?.customMapper?.let {
                    return Custom(fctProperty, it)
                }

                val columnPropertyName = config?.columnPropertyName ?: name
                val propertyReturnTypeFctClass = fctProperty.returnType
                val propertyReturnTypeKClass = propertyReturnTypeFctClass.kClass
                val propertyReturnTypeKType = propertyReturnTypeFctClass.kType

                return if (propertyReturnTypeKClass.isExposedSqlPrimitiveType())
                    @Suppress("UNCHECKED_CAST")
                    SqlPrimitive(
                        fctProperty, columnByPropertyNameMap.getOrElse(columnPropertyName) {
                            throw IllegalArgumentException("column with property name `$columnPropertyName` for class property `$kProperty` does not exist in the tables `$tables`")
                        } as Column<PropertyData>
                    )
                else {
                    val isNullable = propertyReturnTypeKType.isMarkedNullable

                    @Suppress("UNCHECKED_CAST")
                    val nullability =
                        (
                                if (isNullable)
                                /*
                                I first had the idea of finding a default `nullDependentColumn` but it seems difficult to cover all kinds of cases.

                                There are 3 ways I can think of to find the default `nullDependentColumn` in the corresponding columns mapped by the properties:
                                1. find the first non-nullable column;
                                1. find the first column that's a primary key;
                                1. find the first non-nullable column with the suffix "id".

                                They all have their drawbacks.
                                The first approach is too unpredictable, adding or removing properties can affect which column to choose.
                                Both the second and the third approach can't deal with the case where the column is not within the mapped columns,
                                which happens when selecting a small portion of the fields as data.
                                 */
                                    NestedClass.Nullability.Nullable<PropertyData & Any>(
                                        config?.whetherNullDependentColumn
                                            ?: throw IllegalArgumentException("`PropertyColumnMappingConfig::nullDependentColumn` has to be specified for `$kProperty` because its return type `$propertyReturnTypeFctClass` is a nullable nested data type")
                                    )
                                else
                                    NestedClass.Nullability.NonNullable<PropertyData & Any>()
                                )
                                as NestedClass.Nullability<PropertyData>


                    @Suppress("UNCHECKED_CAST")
                    val adtConfig = config?.adt as PropertyColumnMappingConfig.Adt<PropertyData & Any>?
                    val adt = if (propertyReturnTypeKClass.isAbstractOrSealed()) {
                        //requireNotNull(adtConfig)
                        require(adtConfig is PropertyColumnMappingConfig.Adt.Sum<*, *>)
                        adtConfig as PropertyColumnMappingConfig.Adt.Sum<PropertyData & Any, *>
                        val subclassProductConfigMapOverride = adtConfig.subclassProductConfigMapOverride

                        val sealedLeafFctSubclasses =
                            propertyReturnTypeFctClass.sealedLeafSubclasses() // TODO: also support direct sealed subtypes
                        val subclassProductNestedConfigMapMapOverride =
                            subclassProductConfigMapOverride.mapValues { it.value.nestedConfigMap }
                        val subclassProductNestedConfigMapMap =
                            if (propertyReturnTypeKClass.isSealed)
                                sealedLeafFctSubclasses.asSequence().map { it.kClass }.associateWith {
                                    emptyMap<KProperty1<out PropertyData & Any, *>, PropertyColumnMappingConfig<*>>()
                                } + subclassProductNestedConfigMapMapOverride
                            else {
                                require(subclassProductConfigMapOverride.isNotEmpty()) { "A custom config needs to be specified for a non-sealed abstract class $propertyReturnTypeKType" }
                                subclassProductNestedConfigMapMapOverride
                            }

                        NestedClass.Adt.Sum(
                            sealedLeafFctSubclasses.associate {
                                fun <SubclassData : PropertyData & Any> typeParameterHelper(
                                    fullConcreteTypeClass: FullConcreteTypeClass<SubclassData>,
                                    configMap: PropertyColumnMappingConfigMap<SubclassData>
                                ): NestedClass.Adt.Product<SubclassData> =
                                    NestedClass.Adt.Product(
                                        doGetDefaultClassPropertyColumnMappings(
                                            fullConcreteTypeClass,
                                            tables, columnByPropertyNameMap,
                                            configMap
                                        )
                                    )

                                @Suppress("NAME_SHADOWING")
                                val kClass = it.kClass
                                @Suppress("UNCHECKED_CAST")
                                kClass to typeParameterHelper(
                                    it as FullConcreteTypeClass<PropertyData & Any>,
                                    subclassProductNestedConfigMapMap[kClass] as PropertyColumnMappingConfigMap<PropertyData & Any>
                                )
                            },
                            adtConfig.sumTypeCaseConfig
                        )
                    } else {
                        require(adtConfig is PropertyColumnMappingConfig.Adt.Product?)
                        NestedClass.Adt.Product(
                            doGetDefaultClassPropertyColumnMappings(
                                propertyReturnTypeFctClass,
                                tables, columnByPropertyNameMap,
                                (adtConfig?.nestedConfigMap ?: emptyMap())
                            )
                        )
                    }

                    NestedClass(fctProperty, nullability, adt)
                }
            }
            typeParameterHelper(fctProperty)
        }
}

fun <Data : Any> getDefaultClassPropertyColumnMappings(
    fullConcreteTypeClass: FullConcreteTypeClass<Data>,
    tables: List<Table>,
    onDuplicateColumnPropertyNames: OnDuplicateColumnPropertyNames = CHOOSE_FIRST, // TODO consider removing this default argument as there is one for joins now
    propertyColumnMappingConfigMapOverride: PropertyColumnMappingConfigMap<Data> = emptyMap(),
    customMappings: PropertyColumnMappings<Data> = emptyList()
): ClassPropertyColumnMappings<Data> =
    doGetDefaultClassPropertyColumnMappings(
        fullConcreteTypeClass,
        tables, getColumnByPropertyNameMap(tables, onDuplicateColumnPropertyNames),
        propertyColumnMappingConfigMapOverride,
        customMappings
    )

// TODO: decouple query mapper and update mapper.
/** Supports classes with nested composite class properties and multiple tables */
class ReflectionBasedClassPropertyDataMapper<Data : Any>(
    val fullConcreteTypeClass: FullConcreteTypeClass<Data>,
    val classPropertyColumnMappings: ClassPropertyColumnMappings<Data>,
) : DataMapper<Data> {
    override val neededColumns = classPropertyColumnMappings.getColumnSet().toList()
    override fun resultRowToData(resultRow: ResultRow): Data =
        constructDataWithResultRow(fullConcreteTypeClass, classPropertyColumnMappings, resultRow)

    override fun setUpdateBuilder(data: Data, updateBuilder: UpdateBuilder<*>) {
        setUpdateBuilder(classPropertyColumnMappings, data, updateBuilder)
    }
}


private fun <Data : Any> constructDataWithResultRow(
    fctClass: FullConcreteTypeClass<Data>,
    classPropertyColumnMappings: ClassPropertyColumnMappings<Data>,
    resultRow: ResultRow
): Data =
    fctClass.kClass.primaryConstructor!!.call(*classPropertyColumnMappings.map {
        fun <PropertyReturnT> typeParameterHelper(
            propertyColumnMapping: PropertyColumnMapping<Data, PropertyReturnT>,
            nestedFctClass: FullConcreteTypeClass<PropertyReturnT & Any>
        ) =
            when (propertyColumnMapping) {
                is SqlPrimitive -> resultRow.getValue(propertyColumnMapping.column)
                is NestedClass -> {
                    fun constructNotNullData() =
                        when (val adt = propertyColumnMapping.adt) {
                            is NestedClass.Adt.Product ->
                                constructDataWithResultRow(nestedFctClass, adt.nestedMappings, resultRow)

                            is NestedClass.Adt.Sum<PropertyReturnT & Any, *> -> {
                                fun <CaseValue, SubclassData : PropertyReturnT & Any> typeParameterHelper(sum: NestedClass.Adt.Sum<PropertyReturnT & Any, CaseValue>): SubclassData {
                                    val subclass = with(sum.sumTypeCaseConfig) {
                                        caseValueKClassConversion.to(resultRow[caseValueColumn])
                                    }
                                    @Suppress("UNCHECKED_CAST")
                                    return constructDataWithResultRow(
                                        subclass as FullConcreteTypeClass<SubclassData>,
                                        sum.subclassMap.getValue(subclass).nestedMappings as ClassPropertyColumnMappings<SubclassData>,
                                        resultRow
                                    )
                                }
                                typeParameterHelper(adt)
                            }
                        }

                    when (val nullability = propertyColumnMapping.nullability) {
                        is NestedClass.Nullability.NonNullable -> constructNotNullData()
                        is NestedClass.Nullability.Nullable<*> -> if (resultRow[nullability.whetherNullDependentColumn] !== null) constructNotNullData() else null
                    }
                }

                is Custom -> propertyColumnMapping.nullableDataMapper.resultRowToData(resultRow)
                is Skip -> null
            }
        @Suppress("UNCHECKED_CAST")
        typeParameterHelper(
            it as PropertyColumnMapping<Data, Any?>,
            it.fctProperty.returnType
        )
    }.toTypedArray())

fun <Data : Any> setUpdateBuilder(
    classPropertyColumnMappings: ClassPropertyColumnMappings<Data>, data: Data, updateBuilder: UpdateBuilder<*>
) {
    for (propertyColumnMapping in classPropertyColumnMappings) {
        fun <PropertyData> typeParameterHelper(propertyColumnMapping: PropertyColumnMapping<Data, PropertyData>) {
            val propertyData = propertyColumnMapping.fctProperty.kProperty(data)
            when (propertyColumnMapping) {
                is SqlPrimitive ->
                    updateBuilder[propertyColumnMapping.column] = propertyData

                is NestedClass -> {
                    // `propertyColumnMapping.nullability` is not needed here
                    when (val adt = propertyColumnMapping.adt) {
                        is NestedClass.Adt.Product -> {
                            val nestedMappings = adt.nestedMappings
                            if (propertyData !== null)
                                setUpdateBuilder(nestedMappings, propertyData, updateBuilder)
                            else
                                setUpdateBuilderColumnsToNullsWithMappings(nestedMappings, updateBuilder)
                        }

                        is NestedClass.Adt.Sum<PropertyData & Any, *> -> {
                            fun <CaseValue> typeParameterHelper() {
                                @Suppress("UNCHECKED_CAST")
                                adt as NestedClass.Adt.Sum<PropertyData & Any, CaseValue>
                                with(adt) {
                                    if (propertyData !== null) {
                                        // TODO: it seems to be a compiler bug that the non-null assertion is needed here. see: https://youtrack.jetbrains.com/issue/KT-37878/No-Smart-cast-for-class-literal-reference-of-nullable-generic-type.
                                        val propertyDataClass = propertyData!!::class
                                        with(sumTypeCaseConfig) {
                                            updateBuilder[caseValueColumn] =
                                                caseValueKClassConversion.from(propertyDataClass)
                                        }
                                        fun <SubclassData : PropertyData & Any> typeParameterHelper(
                                            subclassMapping: NestedClass.Adt.Product<SubclassData>,
                                            propertyData: SubclassData
                                        ) =
                                            setUpdateBuilder(
                                                subclassMapping.nestedMappings, propertyData, updateBuilder
                                            )
                                        @Suppress("UNCHECKED_CAST")
                                        typeParameterHelper(
                                            subclassMap.getValue(propertyDataClass) as NestedClass.Adt.Product<PropertyData & Any>,
                                            propertyData
                                        )
                                    } else {
                                        with(sumTypeCaseConfig) {
                                            @Suppress("UNCHECKED_CAST")
                                            updateBuilder[caseValueColumn as Column<Any?>] = null
                                        }
                                        setUpdateBuilderColumnsToNulls(columnsForAllSubclasses, updateBuilder)
                                    }
                                }
                            }
                            typeParameterHelper<Any?>()
                        }
                    }
                }

                is Custom ->
                    propertyColumnMapping.nullableDataMapper.setUpdateBuilder(propertyData, updateBuilder)

                is Skip -> {}
            }
        }

        typeParameterHelper(propertyColumnMapping)
    }
}

fun PropertyColumnMapping<*, *>.forEachColumn(block: (Column<*>) -> Unit) =
    when (this) {
        is SqlPrimitive -> block(column)
        is NestedClass -> {
            when (nullability) {
                is NestedClass.Nullability.NonNullable -> {}
                is NestedClass.Nullability.Nullable -> block(nullability.whetherNullDependentColumn)
            }
            when (adt) {
                is NestedClass.Adt.Product -> adt.nestedMappings.forEachColumn(block)
                is NestedClass.Adt.Sum<*, *> -> {
                    block(adt.sumTypeCaseConfig.caseValueColumn)
                    adt.subclassMap.values.forEach { it.nestedMappings.forEachColumn(block) }
                }
            }
        }

        is Custom -> nullableDataMapper.neededColumns.forEach(block)
        is Skip -> {}
    }

fun ClassPropertyColumnMappings<*>.forEachColumn(block: (Column<*>) -> Unit) {
    for (propertyColumnMapping in this)
        propertyColumnMapping.forEachColumn(block)
}

fun setUpdateBuilderColumnsToNullsWithMappings(
    classPropertyColumnMappings: ClassPropertyColumnMappings<*>, updateBuilder: UpdateBuilder<*>
) =
    classPropertyColumnMappings.forEachColumn {
        @Suppress("UNCHECKED_CAST")
        updateBuilder[it as Column<Any?>] = null
    }

fun setUpdateBuilderColumnsToNulls(columns: List<Column<*>>, updateBuilder: UpdateBuilder<*>) {
    for (column in columns)
        @Suppress("UNCHECKED_CAST")
        updateBuilder[column as Column<Any?>] = null
}

fun ClassPropertyColumnMappings<*>.getColumnSet(): Set<Column<*>> =
    buildSet { forEachColumn { add(it) } }

// TODO add a version of `reflectionBasedClassPropertyDataMapper` that takes column properties and make the following 2 functions depend on it

inline fun <reified Data : Any> reflectionBasedClassPropertyDataMapper(
    tables: List<Table>,
    onDuplicateColumnPropertyNames: OnDuplicateColumnPropertyNames = CHOOSE_FIRST, // TODO consider removing this default argument as there is one for joins now
    propertyColumnMappingConfigMapOverride: PropertyColumnMappingConfigMap<Data> = emptyMap(),
    customMappings: PropertyColumnMappings<Data> = emptyList() // TODO consider removing this parameter if possible
): ReflectionBasedClassPropertyDataMapper<Data> {
    val fullConcreteTypeClass = fullConcreteTypeClassOf<Data>()
    return ReflectionBasedClassPropertyDataMapper(
        fullConcreteTypeClass, getDefaultClassPropertyColumnMappings(
            fullConcreteTypeClass,
            tables, onDuplicateColumnPropertyNames, propertyColumnMappingConfigMapOverride, customMappings
        )
    )
}

inline fun <reified Data : Any/*, TableT : Table*/> reflectionBasedClassPropertyDataMapper(
    table: Table,
    propertyColumnMappingConfigMapOverride: PropertyColumnMappingConfigMap<Data> = emptyMap(),
    customMappings: PropertyColumnMappings<Data> = emptyList()
) =
    reflectionBasedClassPropertyDataMapper(listOf(table), THROW, propertyColumnMappingConfigMapOverride, customMappings)

/**
 * A shortcut for [Join]s.
 */
inline fun <reified Data : Any> reflectionBasedClassPropertyDataMapper(
    join: Join,
    propertyColumnMappingConfigMapOverride: PropertyColumnMappingConfigMap<Data> = emptyMap(),
    customMappings: PropertyColumnMappings<Data> = emptyList()
) =
    reflectionBasedClassPropertyDataMapper(
        join.targetTables(), CHOOSE_FIRST, propertyColumnMappingConfigMapOverride, customMappings
    )

// not completely implemented yet
private inline fun <reified Data : Any> reflectionBasedClassPropertyDataMapper(
    queryAlias: QueryAlias,
    propertyColumnMappingConfigMapOverride: PropertyColumnMappingConfigMap<Data> = emptyMap(),
    customMappings: PropertyColumnMappings<Data> = emptyList()
): ReflectionBasedClassPropertyDataMapper<Data> =
    reflectionBasedClassPropertyDataMapper(
        queryAlias.query.targets, CHOOSE_FIRST, propertyColumnMappingConfigMapOverride, customMappings
    ).run {
        TODO("map the columns to alias columns")
    }
