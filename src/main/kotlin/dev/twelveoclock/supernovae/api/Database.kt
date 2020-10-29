package dev.twelveoclock.supernovae.api

import dev.twelveoclock.supernovae.proto.CapnProto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

        fun filter(filter: Filter): List<JsonObject> {

            // Do optimized filter
            if (shouldCacheAll) {
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

        private operator fun CapnProto.Check.invoke(value1: JsonElement, value2: JsonElement): Boolean {

            if (this == CapnProto.Check.EQUAL) {
                return value1 == value2
            }

            val value1Number = (value1 as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return false
            val value2Number = (value2 as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return false

            return when (this) {

                CapnProto.Check.GREATER_THAN -> value1Number > value2Number
                CapnProto.Check.LESSER_THAN -> value1Number < value2Number
                CapnProto.Check.LESSER_THAN_OR_EQUAL -> value1Number <= value2Number
                CapnProto.Check.GREATER_THAN_OR_EQUAL -> value1Number >= value2Number

                else -> false
            }
        }


        companion object {

            const val FILE_EXTENSION = ".json"

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