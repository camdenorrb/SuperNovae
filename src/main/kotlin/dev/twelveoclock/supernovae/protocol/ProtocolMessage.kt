package dev.twelveoclock.supernovae.protocol

import dev.twelveoclock.supernovae.api.FileDatabase
import dev.twelveoclock.supernovae.serializer.JsonElementStringSerializer
import dev.twelveoclock.supernovae.serializer.JsonObjectStringSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
sealed class ProtocolMessage {

    abstract val messageID: Int


    // Response message ID will be the same and the query ID
    /*
    @Serializable
    data class QueryResponse(
        override val messageID: Int,
        val innerMessage: ProtocolMessage
    ) : ProtocolMessage()
    */

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
            override val messageID: Int,
            val databaseName: String
        ) : DB()

        @Serializable
        data class Delete(
            override val messageID: Int,
            val databaseName: String
        ) : DB()

        @Serializable
        data class Select(
            override val messageID: Int,
            val databaseName: String
        ) : DB()

    }

    @Serializable
    sealed class Table : ProtocolMessage() {

        @Serializable
        data class Create(
            override val messageID: Int,
            val tableName: String,
            val keyColumn: String,
            val shouldCacheAll: Boolean = false
        ) : Table()

        @Serializable
        data class Delete(
            override val messageID: Int,
            val tableName: String
        ) : Table()

        @Serializable
        data class Select(
            override val messageID: Int,
            val tableName: String
        ) : Table()

        @Serializable
        data class Clear(
            override val messageID: Int,
            val tableName: String
        ) : Table()

        @Serializable
        data class Cache(
            override val messageID: Int,
            val tableName: String
        ) : Table()

        @Serializable
        data class UnCache(
            override val messageID: Int,
            val tableName: String
        ) : Table()

        @Serializable
        data class StartListening(
            override val messageID: Int,
            val tableName: String
        ) : Table()

        @Serializable
        data class StopListening(
            override val messageID: Int,
            val tableName: String
        ) : Table()

        @Serializable
        data class InsertRow(
            override val messageID: Int,
            val tableName: String,
            @Serializable(JsonObjectStringSerializer::class) val row: JsonObject,
            val shouldCache: Boolean = false
        ) : Table()

        @Serializable
        data class UpdateRows(
            override val messageID: Int,
            val tableName: String,
            val filter: FileDatabase.Filter,
            val columnName: String,
            @Serializable(JsonElementStringSerializer::class) val value: JsonElement,
            val amountOfRows: Int = -1,
            val onlyCheckCache: Boolean = false
        ) : Table()

        // This should only check flat files
        @Serializable
        data class CacheRows(
            override val messageID: Int,
            val tableName: String,
            val filter: FileDatabase.Filter
        ) : Table()

        // This should only check cache
        @Serializable
        data class UncacheRows(
            override val messageID: Int,
            val tableName: String,
            val filter: FileDatabase.Filter
        ) : Table()

        @Serializable
        data class SelectAllRows(
            override val messageID: Int,
            val tableName: String,
            val onlyCheckCache: Boolean = false,
        ) : Table()

        @Serializable
        data class SelectRows(
            override val messageID: Int,
            val tableName: String,
            val filters: List<FileDatabase.Filter>,
            val onlyCheckCache: Boolean = false,
            val loadIntoCache: Boolean = false,
            val amountOfRows: Int = -1
        ) : Table()

        // This should only check cache
        @Serializable
        data class DeleteRows(
            override val messageID: Int,
            val tableName: String,
            val filters: List<FileDatabase.Filter>,
            val amountOfRows: Int = -1,
            val onlyCheckCache: Boolean = false
        ) : Table()

        @Serializable
        data class SelectRowResponse(
            override val messageID: Int,
            @Serializable(JsonObjectStringSerializer::class) val row: JsonObject
        ) : Table()

        @Serializable
        data class SelectTableResponse(
            override val messageID: Int,
            val tableName: String,
            val keyColumn: String,
            val shouldCacheAll: Boolean
        ) : Table()

        @Serializable
        data class UpdateNotification(
            override val messageID: Int,
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