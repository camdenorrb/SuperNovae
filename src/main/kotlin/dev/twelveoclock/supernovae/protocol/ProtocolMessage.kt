package dev.twelveoclock.supernovae.protocol

import dev.twelveoclock.supernovae.api.Database
import dev.twelveoclock.supernovae.serializer.JsonElementStringSerializer
import dev.twelveoclock.supernovae.serializer.JsonObjectStringSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
sealed class ProtocolMessage {

    // TODO: Change this when queries get more complex
    interface Query {
        val queryID: Int
    }

    @Serializable
    class QueryResponse(
        val queryID: Int,
        val innerMessage: ProtocolMessage
    ) : ProtocolMessage()


    // Make this use generics when KotlinX supports it - NVM, it shouldn't be generic, ex advanced queries
    @Serializable
    data class Blob(
        val messages: List<ProtocolMessage> = emptyList()
    ) : ProtocolMessage()

    @Serializable
    object StopListeningToAllTables : ProtocolMessage()

    @Serializable
    sealed class DB : ProtocolMessage() {

        @Serializable
        data class Create(
            val databaseName: String
        ) : DB()

        @Serializable
        data class Delete(
            val databaseName: String
        ) : DB()

        @Serializable
        data class Select(
            val databaseName: String
        ) : DB()

    }

    @Serializable
    sealed class Table : ProtocolMessage() {

        @Serializable
        data class Create(
            val tableName: String,
            val keyColumn: String,
            val shouldCacheAll: Boolean = false
        ) : Table()

        @Serializable
        data class Delete(
            val tableName: String
        ) : Table()

        @Serializable
        data class Select(
            override val queryID: Int,
            val tableName: String
        ) : Table(), Query

        @Serializable
        data class Clear(
            val tableName: String
        ) : Table()

        @Serializable
        data class Cache(
            val tableName: String
        ) : Table()

        @Serializable
        data class UnCache(
            val tableName: String
        ) : Table()

        @Serializable
        data class StartListening(
            val tableName: String
        ) : Table()

        @Serializable
        data class StopListening(
            val tableName: String
        ) : Table()

        @Serializable
        data class InsertRow(
            val tableName: String,
            @Serializable(JsonObjectStringSerializer::class) val row: JsonObject,
            val shouldCache: Boolean = false
        ) : Table()

        @Serializable
        data class UpdateRows(
            val tableName: String,
            val filter: Database.Filter,
            val columnName: String,
            @Serializable(JsonElementStringSerializer::class) val value: JsonElement,
            val amountOfRows: Int = -1,
            val onlyCheckCache: Boolean = false
        ) : Table()

        // This should only check flat files
        @Serializable
        data class CacheRows(
            val tableName: String,
            val filter: Database.Filter
        ) : Table()

        // This should only check cache
        @Serializable
        data class UncacheRows(
            val tableName: String,
            val filter: Database.Filter
        ) : Table()

        @Serializable
        data class SelectAllRows(
            override val queryID: Int,
            val tableName: String,
            val onlyCheckCache: Boolean = false,
        ) : Table(), Query

        @Serializable
        data class SelectRows(
            override val queryID: Int,
            val tableName: String,
            val filters: List<Database.Filter>,
            val onlyCheckCache: Boolean = false,
            val loadIntoCache: Boolean = false,
            val amountOfRows: Int = -1
        ) : Table(), Query

        // This should only check cache
        @Serializable
        data class DeleteRows(
            val tableName: String,
            val filters: List<Database.Filter>,
            val amountOfRows: Int = -1,
            val onlyCheckCache: Boolean = false
        ) : Table()

        @Serializable
        data class SelectRowResponse(
            val queryID: Int,
            @Serializable(JsonObjectStringSerializer::class) val row: JsonObject
        ) : Table()

        @Serializable
        data class SelectTableResponse(
            val tableName: String,
            val keyColumn: String,
            val shouldCacheAll: Boolean
        ) : Table()

        @Serializable
        data class UpdateNotification(
            val tableName: String,
            val type: UpdateType,
            @Serializable(JsonObjectStringSerializer::class) val row: JsonObject
        ) : Table()

    }


    @Serializable
    enum class UpdateType {
        MODIFICATION,
        INSERT,
        DELETION
    }

    @Serializable
    enum class Check {
        EQUAL,
        LESSER_THAN,
        GREATER_THAN,
        LESSER_THAN_OR_EQUAL,
        GREATER_THAN_OR_EQUAL
    }

}