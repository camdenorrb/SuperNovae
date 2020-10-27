package dev.twelveoclock.supernovae.api

import dev.twelveoclock.supernovae.proto.CapnProto
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.capnproto.MessageBuilder

// Add a way to make a local database setup without a server, kinda like sqlite
class Database {

    fun store()


    class Table<T : Any>(val name: String, val serializer: SerializationStrategy<T>) {

        val rows = mutableListOf<JsonObject>()


        fun insert(value: T) {

            val insert = MessageBuilder().initRoot(CapnProto.Insert.factory).apply {
                setTableName(name)
                setRow(Json.encodeToString(serializer, value))
            }


            insert.
        }

        fun delete(value: T){}



        fun filter(filter: Filter<T, Any>): Request<T> {
            filter.property.get()
        }


        inner class Query<T> {

            val filters = mutableListOf<Filter<T, Any>>()

        }

    }


}