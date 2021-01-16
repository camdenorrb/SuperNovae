package dev.twelveoclock.supernovae.protocol

import dev.twelveoclock.supernovae.api.Database
import dev.twelveoclock.supernovae.serializer.JsonElementStringSerializer
import dev.twelveoclock.supernovae.serializer.JsonObjectStringSerializer
import dev.twelveoclock.supernovae.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.util.*

@Serializable
sealed class ProtocolMessage {

    // TODO: Change this when queries get more complex
    interface Query {
        val queryUUID: UUID
    }

    interface QueryResponse {
        val queryUUID: UUID
    }


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
            val tableName: String
        ) : Table()

        @Serializable
        data class Clear(
            val tableName: String
        ) : Table()

        @Serializable
        data class Cache(
            val tableName: String
        ) : Table()

        @Serializable
        data class Uncache(
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
            @Serializable(UUIDSerializer::class) override val queryUUID: UUID,
            val tableName: String,
            val onlyCheckCache: Boolean = false,
        ) : Table(), Query

        @Serializable
        data class SelectRows(
            @Serializable(UUIDSerializer::class) override val queryUUID: UUID,
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
            @Serializable(JsonObjectStringSerializer::class) val row: JsonObject
        ) : Table()

        @Serializable
        data class SelectTableResponse(
            @Serializable(UUIDSerializer::class) override val queryUUID: UUID,
            val tableName: String,
            val keyColumn: String,
            val shouldCacheAll: Boolean
        ) : Table(), QueryResponse

        @Serializable
        data class UpdateNotification(
            @Serializable(UUIDSerializer::class) override val queryUUID: UUID,
            val tableName: String,
            val type: UpdateType,
            @Serializable(JsonObjectStringSerializer::class) val row: JsonObject
        ) : Table(), QueryResponse

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