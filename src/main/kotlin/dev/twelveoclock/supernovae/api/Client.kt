package dev.twelveoclock.supernovae.api

import dev.twelveoclock.supernovae.ext.*
import dev.twelveoclock.supernovae.proto.CapnProto
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import me.camdenorrb.netlius.Netlius
import me.camdenorrb.netlius.net.Client
import kotlin.reflect.KProperty1

// This will be the high level remote implementation
// TODO: Make a high level for local as-well
class Client(val ip: String, val port: Int) {

    var isConnected = false
        private set

    lateinit var client: Client
        private set


    fun connect() {

        if (isConnected) {
            return
        }

        client = Netlius.client(ip, port)

        isConnected = true
    }

    fun disconnect() {

        if (!isConnected) {
            return
        }

        client.close()

        isConnected = false
    }


    suspend fun <R, MV, MK : KProperty1<R, MV>> selectTable(
        name: String,
        mainKey: MK,
        rowSerializer: KSerializer<R>,
        keySerializer: KSerializer<MV>
    ): Table<R, MV, MK> {
        val response = client.sendSelectTable(name)
        return Table(name, mainKey, response.shouldCacheAll, rowSerializer, keySerializer)
    }

    suspend fun createTable(name: String, keyColumnName: String, shouldCacheAll: Boolean = false) {
        client.sendCreateTable(name, keyColumnName, shouldCacheAll)
    }

    suspend fun deleteTable(name: String) {
        client.sendDeleteTable(name)
    }

    suspend fun selectDB(name: String) {
        client.sendSelectDB(name)
    }

    suspend fun createDB(name: String) {
        client.sendCreateDB(name)
    }

    suspend fun deleteDB(name: String) {
        client.sendDeleteDB(name)
    }


    inner class Table<R, MV, MK : KProperty1<R, MV>>(
        val name: String,
        val mainKey: MK,
        val shouldCacheAll: Boolean,
        val rowSerializer: KSerializer<R>,
        val keySerializer: KSerializer<MV>,
    ) {

        suspend fun load() {
            client.sendCacheTable(name)
        }

        suspend fun unload() {
            client.sendUnloadTable(name)
        }


        suspend fun create() {
            client.sendCreateTable(mainKey.name, name, shouldCacheAll)
        }

        suspend fun delete() {
            client.sendDeleteTable(name)
        }


        suspend fun insertRow(row: R, shouldCache: Boolean = shouldCacheAll) {
            val rowAsString = Json.encodeToString(rowSerializer, row)
            client.sendInsertRow(name, rowAsString, shouldCache)
        }

        suspend fun deleteRow(keyValue: MV) {
            Json.encodeToString(keySerializer, keyValue)
            client.sendDeleteRow(name)
        }

        suspend fun <T> updateRows(key: String, property: KProperty1<R, T>, newValue: T, serializer: KSerializer<T>, amountOfRows: Int = 0, onlyCheckCache: Boolean = shouldCacheAll) {

            val filter = Database.Filter(mainKey.name, CapnProto.Check.EQUAL, JsonPrimitive(key))

            client.sendUpdateRows(
                name,
                property.name,
                Json.encodeToString(serializer, newValue),
                filter,
                amountOfRows,
                onlyCheckCache
            )
        }

    }

}