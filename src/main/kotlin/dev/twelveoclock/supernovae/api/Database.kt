package dev.twelveoclock.supernovae.api

import dev.twelveoclock.supernovae.ext.invoke
import dev.twelveoclock.supernovae.proto.CapnProto
import kotlinx.serialization.json.*
import java.io.File

// This is gonna be local only, expand upon in Server
data class Database(val folder: File) {

    // Name lowercase -> Table
    val tables = mutableMapOf<String, Table>()


    init {
        folder.mkdirs()
    }


    fun createTable(name: String, shouldCacheAll: Boolean, keyColumn: String): Table {
        return Table(name, shouldCacheAll, keyColumn, File(folder, name)).also {
            tables[name.toLowerCase()] = it
        }
    }

    fun deleteTable(name: String) {
        tables.remove(name.toLowerCase())
    }

    // TODO: Make a cache on connect, and update through change listeners - Nvm, this is gonna be only local

    data class Table(
        val name: String,
        val shouldCacheAll: Boolean,
        val keyColumn: String,
        val folder: File,
    ) {

        // Key Column Value -> Json Object Row
        val cachedRows = mutableMapOf<String, JsonObject>()


        init {

            folder.mkdirs()

            if (shouldCacheAll) {
                cacheAllRows()
            }
        }


        fun insert(row: JsonObject, shouldCache: Boolean) {

            // https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/json.md#parsing-to-json-element

            /*
            (Json.parseToJsonElement("") as JsonObject).
            val insert = MessageBuilder().initRoot(CapnProto.Insert.factory).apply {
                setTableName(name)
            }
            */

            File(folder, "${row.getValue(keyColumn)}$FILE_EXTENSION").apply { createNewFile() }.writeText(row.toString())

            if (shouldCache || shouldCacheAll) {
                cachedRows[row[keyColumn].toString()] = row
            }
        }

        // Might not be possible?
        fun update(filter: Filter, columnName: String, value: String, amount: Int? = null, onlyCheckCache: Boolean) {

            check(columnName != keyColumn) {
                "You currently cannot change the key column value"
            }

            filter(filter, amount, onlyCheckCache).forEach {

                val shouldCache = cachedRows.containsKey(it.getValue(keyColumn).toString().toLowerCase())
                val objectAsMap = it.toMutableMap()

                objectAsMap[columnName] = when(objectAsMap[columnName]) {

                    JsonNull -> JsonNull

                    is JsonArray -> Json.decodeFromString(JsonArray.serializer(), value)
                    is JsonObject -> Json.decodeFromString(JsonObject.serializer(), value)
                    is JsonPrimitive -> Json.decodeFromString(JsonPrimitive.serializer(), value)

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

            // Check all persistent rows
            result += folder.listFiles()!!
                .filter {
                    // Skip rows that are cached
                    it.nameWithoutExtension !in cachedRows
                }
                .map {
                    Json.parseToJsonElement(it.readText()) as JsonObject
                }
                .filter {
                    filter.check(it.getValue(filter.columnName), filter.value)
                }

            return result
        }


        private fun cacheAllRows() {
            folder.listFiles()!!.forEach {
                Json.parseToJsonElement(it.readText())
            }
        }


        companion object {

            const val FILE_EXTENSION = ".json"

        }

    }

    companion object {

        fun loadFromFolder() {

        }

    }

    data class Filter(
        val columnName: String,
        val check: CapnProto.Check,
        val value: JsonElement
    ) {

        companion object {

            fun fromCapnProto(filter: CapnProto.Filter.Reader): Filter {

                val columnName = filter.columnName.toString()
                val compareToValue = Json.parseToJsonElement(filter.compareToValue.toString())

                return Filter(columnName, filter.check, compareToValue)
            }

        }

    }

}