package dev.twelveoclock.supernovae.api

import dev.twelveoclock.supernovae.proto.CapnProto
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.capnproto.MessageBuilder
import java.io.File

// Add a way to make a local database setup without a server, kinda like sqlite
class Database {

    val tables = mutableListOf<Table<*>>()


    fun <T : Any> createTable(name: String): Table<T> {

    }

    fun deleteTable(name: String) {

    }

    // TODO: Make a cache on connect, and update through change listeners
    fun listTables(): List<Table<*>> {

    }


    inner class Table<T : Any>(
        val name: String,
        val serializer: SerializationStrategy<T>,
    ) {

        val rows = mutableListOf<JsonObject>()


        fun insert(value: T) {

            val insert = MessageBuilder().initRoot(CapnProto.Insert.factory).apply {
                setTableName(name)
                setRow(Json.encodeToString(serializer, value))
            }


            insert.
        }

        fun delete(value: T) {

        }



        fun filter(filter: Filter<T, Any>): Request<T> {
            filter.property.get()
        }


        inner class Query<T> {

            val filters = mutableListOf<Filter<T, Any>>()

        }

    }


}