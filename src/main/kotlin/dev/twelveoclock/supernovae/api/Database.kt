package dev.twelveoclock.supernovae.api

import dev.twelveoclock.supernovae.config.TableConfig
import dev.twelveoclock.supernovae.ext.invoke
import dev.twelveoclock.supernovae.proto.DBProto
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*
import org.capnproto.MessageBuilder
import java.io.File
import kotlin.reflect.KProperty1

// This is gonna be local only, expand upon in Server
data class Database(val folder: File) {

    // Name lowercase -> Table
    val tables = mutableMapOf<String, Table>()


    init {

        folder.mkdirs()

        folder.listFiles()?.filter { it.isDirectory }?.forEach {
            Table.loadFromFolder(it)
        }
    }


    fun createTable(name: String, shouldCacheAll: Boolean, keyColumn: String): Table {
        return Table(name, shouldCacheAll, keyColumn, File(folder, name)).also {
            tables[name] = it
        }
    }

    fun deleteTable(name: String) {
        tables.remove(name)
    }



    // TODO: Make a cache on connect, and update through change listeners - Nvm, this is gonna be only local

    class Table(
        val name: String,
        shouldCacheAll: Boolean,
        val keyColumn: String,
        val folder: File,
    ) {

        // TODO: On set save settings
        var shouldCacheAll = shouldCacheAll
            private set

        // Key Column Value -> Json Object Row
        val cachedRows = mutableMapOf<String, JsonObject>()


        init {

            folder.mkdirs()

            if (shouldCacheAll) {
                cacheAllRows()
            }

            File(folder, "settings.json").writeText(Json.encodeToString(TableConfig.serializer(), TableConfig(keyColumn, shouldCacheAll)))
        }


        fun insert(row: JsonObject, shouldCache: Boolean) {

            // https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/JSON.md#parsing-to-json-element

            /*
            (JSON.parseToJsonElement("") as JsonObject).
            val insert = MessageBuilder().initRoot(CapnProto.Insert.factory).apply {
                setTableName(name)
            }
            */

            File(folder, "${row.getValue(keyColumn)}$FILE_EXTENSION").apply { createNewFile() }.writeText(JSON.encodeToString(JsonObject.serializer(), row))

            if (shouldCache || shouldCacheAll) {
                cachedRows[row[keyColumn].toString()] = row
            }
        }

        fun update(filter: Filter, columnName: String, value: String, amount: Int? = null, onlyCheckCache: Boolean) {
            update(filter(filter, amount, onlyCheckCache), columnName, value)
        }

        fun update(values: List<JsonObject>, columnName: String, value: String) {

            check(columnName != keyColumn) {
                "You currently cannot change the key column value"
            }

            values.forEach {

                val shouldCache = cachedRows.containsKey(it.getValue(keyColumn).toString().toLowerCase())
                val objectAsMap = it.toMutableMap()

                objectAsMap[columnName] = when(objectAsMap[columnName]) {

                    JsonNull -> JsonNull

                    is JsonArray -> JSON.decodeFromString(JsonArray.serializer(), value)
                    is JsonObject -> JSON.decodeFromString(JsonObject.serializer(), value)
                    is JsonPrimitive -> JSON.decodeFromString(JsonPrimitive.serializer(), value)

                    else -> error("Can't update to a value with unregistered type, current value: '${objectAsMap[columnName]}'")
                }

                insert(JsonObject(objectAsMap), shouldCache)
            }
        }


        fun uncache(filter: Filter) {

            if (shouldCacheAll) {
                error("Can't uncache a cacheAll table '$name'")
            }

            filter(filter).forEach {
                cachedRows.remove(it.getValue(keyColumn).toString())
            }
        }

        fun cache(filter: Filter) {

            if (shouldCacheAll) {
                return
            }

            filter(filter).forEach {
                cachedRows[it.getValue(keyColumn).toString()] = it
            }
        }

        fun delete(keyColumnValue: String) {
            cachedRows.remove(keyColumn)
            File(folder, "$keyColumnValue$FILE_EXTENSION").delete()
        }

        // Do optimized search if all is cached, otherwise don't
        fun delete(filter: Filter) {
            filter(filter).forEach {
                delete(it.getValue(keyColumn).toString())
            }
        }

        fun clear() {

            folder.listFiles()?.forEach {
                it.delete()
            }

            cachedRows.clear()
        }

        // TODO: Add a way to specify amount to filter for optimization
        // TODO: Check if keyColumn is the only thing being filtered and do an optimized search if so
        fun filter(filter: Filter, amount: Int? = null, onlyCheckCache: Boolean = false): List<JsonObject> {

            // Do optimized filter
            if (shouldCacheAll || onlyCheckCache) {
                return cachedRows.values.filter {
                    filter.check(it.getValue(filter.columnName), filter.value)
                }
            }

            val result = mutableListOf<JsonObject>()

            // Start with cached rows and move to persistent after
            result += cachedRows.values.filter {
                filter.check(it.getValue(filter.columnName), filter.value)
            }

            if (amount != null && result.size >= amount) {
                return result.take(amount)
            }

            // Check all persistent rows
            result += folder.listFiles()!!
                .filter {
                    // Skip rows that are cached
                    it.nameWithoutExtension !in cachedRows
                }
                .map {
                    JSON.parseToJsonElement(it.readText()) as JsonObject
                }
                .filter {
                    filter.check(it.getValue(filter.columnName), filter.value)
                }

            return if (amount != null) {
                result.take(amount)
            }
            else {
                result
            }
        }


        internal fun uncacheAllRows() {

            if (!shouldCacheAll) {
                return
            }

            cachedRows.clear()

            shouldCacheAll = false
        }

        internal fun cacheAllRows() {
            folder.listFiles()?.forEach {
                JSON.parseToJsonElement(it.readText())
            }
        }


        companion object {

            const val FILE_EXTENSION = ".json"

            internal fun loadFromFolder(folder: File): Table {
                val settings = Json.decodeFromString(TableConfig.serializer(), File(folder, "settings.json").readText())
                return Table(folder.name, settings.shouldCacheAll, settings.keyColumnName, folder)
            }

        }

    }

    companion object {

        val JSON = Json { prettyPrint = true }

    }

    data class Filter(
        val columnName: String,
        val check: DBProto.Check,
        val value: JsonElement
    ) {

        fun toCapnProtoReader(): DBProto.Filter.Reader {
            return MessageBuilder().initRoot(DBProto.Filter.factory).also {
                it.setColumnName(columnName)
                it.check = check
                it.setCompareToValue(value.toString())
            }.asReader()
        }

        companion object {

            // Equals
            fun <T, R> eq(serializer: KSerializer<R>, property: KProperty1<T, R>, value: R): Filter {
                return Filter(property.name, DBProto.Check.EQUAL, Json.encodeToJsonElement(serializer, value))
            }

            // Equals
            fun <T> eq(property: KProperty1<T, String>, value: String): Filter {
                return Filter(property.name, DBProto.Check.EQUAL, Json.encodeToJsonElement(String.serializer(), value))
            }


            // Lesser than
            fun <T, R> lt(serializer: KSerializer<R>, property: KProperty1<T, R>, value: R): Filter {
                return Filter(property.name, DBProto.Check.LESSER_THAN, Json.encodeToJsonElement(serializer, value))
            }

            // Greater than
            fun <T, R> gt(serializer: KSerializer<R>, property: KProperty1<T, R>, value: R): Filter {
                return Filter(property.name, DBProto.Check.GREATER_THAN, Json.encodeToJsonElement(serializer, value))
            }


            // Lesser than or equals
            fun <T, R> lte(serializer: KSerializer<R>, property: KProperty1<T, R>, value: R): Filter {
                return Filter(property.name, DBProto.Check.LESSER_THAN_OR_EQUAL, Json.encodeToJsonElement(serializer, value))
            }

            // Greater than or equals
            fun <T, R> gte(serializer: KSerializer<R>, property: KProperty1<T, R>, value: R): Filter {
                return Filter(property.name, DBProto.Check.GREATER_THAN_OR_EQUAL, Json.encodeToJsonElement(serializer, value))
            }


            fun fromCapnProtoReader(filter: DBProto.Filter.Reader): Filter {

                val columnName = filter.columnName.toString()
                val compareToValue = JSON.parseToJsonElement(filter.compareToValue.toString())

                return Filter(columnName, filter.check, compareToValue)
            }

        }

    }

}