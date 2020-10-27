package dev.twelveoclock.supernovae.api

import dev.twelveoclock.supernovae.proto.CapnProto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.capnproto.MessageBuilder

class Table<T : Any> {

    val rows = mutableListOf<JsonObject>()


    fun insert(value: T) {

        Json.
        val insert = MessageBuilder().initRoot(CapnProto.Insert.factory)
            .setRow()
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