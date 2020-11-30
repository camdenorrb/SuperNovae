package dev.twelveoclock.supernovae.net

import dev.twelveoclock.supernovae.api.Database
import dev.twelveoclock.supernovae.ext.*
import dev.twelveoclock.supernovae.proto.DBProto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import me.camdenorrb.netlius.Netlius
import me.camdenorrb.netlius.net.Client
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KProperty1

// This will be the high level remote implementation
// TODO: Make a high level for local as-well
class DBClient(val host: String, val port: Int) {

    val messageWaiters = mutableMapOf<DBProto.Message.Which, MutableList<Continuation<DBProto.Message.Reader>>>()

    // Table name -> Listeners
    val notificationListeners = mutableMapOf<String, MutableList<(DBProto.UpdateNotification.Reader) -> Unit>>()

    private lateinit var readTask: Job


    var isConnected = false
        private set

    lateinit var client: Client
        private set


    // TODO: Add a suspending version
    fun connect() {

        if (isConnected) {
            return
        }

        client = Netlius.client(host, port)

        readTask = GlobalScope.launch(Dispatchers.IO) {
            while (client.channel.isOpen) {

                val message = client.suspendReadNovaeMessage()

                if (message.isUpdateNotification) {

                    notificationListeners[message.updateNotification.tableName.toString()]?.forEach {
                        it(message.updateNotification)
                    }

                    continue
                }

                val waiter = checkNotNull(messageWaiters[message.which()]?.removeFirstOrNull()) {
                    "Expected a waiter for ${message::class}"
                }

                waiter.resume(message)

                if (messageWaiters[message.which()]?.isEmpty() == true) {
                    messageWaiters.remove(message.which())
                }
            }
        }

        isConnected = true
    }

    fun disconnect() {

        if (!isConnected) {
            return
        }

        readTask.cancel()
        client.close()
        messageWaiters.clear()

        isConnected = false
    }

    suspend fun waitForReply(type: DBProto.Message.Which): DBProto.Message.Reader {
        return suspendCoroutine {
            messageWaiters.getOrPut(type, { mutableListOf() }).add(it)
        }
    }

    suspend fun <R, MV, MK : KProperty1<R, MV>> selectTable(
        name: String,
        mainKey: MK,
        rowSerializer: KSerializer<R>,
        keySerializer: KSerializer<MV>
    ): Table<R, MV, MK> {

        client.sendSelectTable(name)

        val response = waitForReply(DBProto.Message.Which.SELECT_TABLE_RESPONSE).selectTableResponse
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

        suspend fun clear() {
            client.sendClearTable(name)
        }

        fun listen(listener: (newRow: R) -> Unit) {
            notificationListeners.getOrPut(name, { mutableListOf() }).add {

                //val oldRowValue = Json.decodeFromString(rowSerializer, it.oldRow.toString())
                val newRowValue = Json.decodeFromString(rowSerializer, it.newRow.toString())

                listener(newRowValue)
            }
        }

        suspend fun selectRow(key: MV, onlyCheckCache: Boolean = shouldCacheAll, loadIntoCache: Boolean = false): R? {

            val filters = listOf(
                Database.Filter(mainKey.name, DBProto.Check.EQUAL, Json.encodeToJsonElement(keySerializer, key))
            )

            client.sendSelectRows(filters, name, onlyCheckCache, loadIntoCache, 1)

            return Json.decodeFromString(rowSerializer, waitForReply(DBProto.Message.Which.BLOB).blob.list.first().selectRowResponse.row.toString())
        }

        suspend fun selectRows(filters: List<Database.Filter>, amountOfRows: Int = 0, onlyCheckCache: Boolean = shouldCacheAll, loadIntoCache: Boolean = false): List<R> {

            client.sendSelectRows(filters, name, onlyCheckCache, loadIntoCache, amountOfRows)

            return waitForReply(DBProto.Message.Which.BLOB).blob.list.map {
                Json.decodeFromString(rowSerializer, it.selectRowResponse.row.toString())
            }
        }


        suspend fun insertRow(row: R, shouldCache: Boolean = shouldCacheAll) {
            val rowAsString = Json.encodeToString(rowSerializer, row)
            client.sendInsertRow(name, rowAsString, shouldCache)
        }

        suspend fun deleteRow(keyValue: MV) {
            TODO("Implement fully")
            //Json.encodeToString(keySerializer, keyValue)
            client.sendDeleteRow(name)
        }

        suspend fun <T> updateRows(keyValue: MV, property: KProperty1<R, T>, newValue: T, serializer: KSerializer<T>, amountOfRows: Int = 0, onlyCheckCache: Boolean = shouldCacheAll) {

            val filter = Database.Filter(mainKey.name, DBProto.Check.EQUAL, Json.encodeToJsonElement(keySerializer, keyValue))

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