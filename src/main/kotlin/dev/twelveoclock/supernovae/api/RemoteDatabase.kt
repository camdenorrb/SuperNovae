package dev.twelveoclock.supernovae.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import me.camdenorrb.netlius.Netlius
import me.camdenorrb.netlius.net.Client
import kotlin.reflect.KProperty1

class RemoteDatabase(val ip: String, val port: Int) {

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


    inner class Table<T, MV, MK : KProperty1<T, MV>>(
        val name: String,
        val mainKey: MK,
        val rowSerializer: KSerializer<T>,
        val keySerializer: KSerializer<MV>
    ) {

        fun insert(value: T) {

        }

        fun delete(keyValue: MV) {
            Json.encodeToString(keySerializer, keyValue)
            client.sendD
        }

    }

}