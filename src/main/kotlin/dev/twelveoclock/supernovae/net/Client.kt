package dev.twelveoclock.supernovae.net

import dev.twelveoclock.supernovae.api.FileDatabase
import dev.twelveoclock.supernovae.ext.*
import dev.twelveoclock.supernovae.protocol.ProtocolMessage
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import me.camdenorrb.netlius.Netlius
import me.camdenorrb.netlius.net.Client
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KProperty1

// This will be the high level remote implementation
// TODO: Make a high level for local as-well
class Client(val host: String, val port: Int) {

    val waiterMutex = Mutex()

    @PublishedApi
    internal val nextQueryID = atomic(0)

    var messageWaiters = ConcurrentHashMap<Int, Continuation<ProtocolMessage>>()

    // Table name -> Listeners
    val notificationListeners = mutableMapOf<String, MutableList<(ProtocolMessage.Table.UpdateNotification) -> Unit>>()


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

        client = Netlius.client(host, port, Long.MAX_VALUE)

        runBlocking {
            readTask = launch(Dispatchers.IO) {
                while (client.channel.isOpen) {

                    val message = client.suspendReadNovaeMessage()

                    if (message is ProtocolMessage.Blob && message.messages.firstOrNull() is ProtocolMessage.Table.UpdateNotification) {

                        //@Suppress("UNCHECKED_CAST")
                        //message as ProtocolMessage.Blob<ProtocolMessage.Table.UpdateNotification>

                        message.messages.forEach { notification ->

                            notification as ProtocolMessage.Table.UpdateNotification

                            if (IS_DEBUGGING) {
                                println("[C SuperNovae] Received update: ${notification.tableName} ${notification.row} ${notification.type}")
                            }
                            notificationListeners[notification.tableName]?.forEach {
                                it(notification)
                            }
                        }

                    } else if (message is ProtocolMessage.QueryResponse) {

                        val waiter = checkNotNull(messageWaiters.remove(message.queryID)) {
                            "Could not get a waiter for query id ${message.queryID}"
                        }

                        try {
                            waiter.resume(message.innerMessage)
                        } catch (ex: IllegalStateException) {
                            println(message)
                            ex.printStackTrace()
                        }
                    }
                }
            }
        }

        isConnected = true
    }

    fun disconnect() {

        if (!isConnected) {
            return
        }

        messageWaiters.forEach {
            it.value.resumeWithException(IllegalStateException("Client has been disconnected"))
        }

        readTask.cancel()
        client.close()
        messageWaiters.clear()

        if (waiterMutex.isLocked) {
            waiterMutex.unlock()
        }

        isConnected = false
    }

    suspend fun <M : ProtocolMessage> waitForReply(queryID: Int): M {
        return suspendCoroutine<ProtocolMessage> {
            messageWaiters[queryID] = it
        } as M
    }


    suspend fun database(name: String): Database {

        if (IS_DEBUGGING) {
            println("[C SuperNovae] database($name)")
        }

        waiterMutex.withLock {
            client.sendSelectDB(name)
        }
    }

    /*
    suspend fun createDB(name: String) {

        if (IS_DEBUGGING) {
            println("[C SuperNovae] createDB($name)")
        }

        waiterMutex.withLock {
            client.sendCreateDB(name)
        }
    }
    */

    suspend fun deleteDatabase(name: String) {

        if (IS_DEBUGGING) {
            println("[C SuperNovae] deleteDB($name)")
        }

        waiterMutex.withLock {
            client.sendDeleteDB(name)
        }
    }

    companion object {

        const val IS_DEBUGGING = true

        val JSON = Json {
            encodeDefaults = true
        }

    }



    inner class Database {

        suspend inline fun <reified R, reified MV, MK : KProperty1<R, MV>> table(
            name: String,
            mainKey: MK,
            rowSerializer: KSerializer<R> = serializer(),
            keySerializer: KSerializer<MV> = serializer()
        ): Client.Table<R, MV, MK> {

            waiterMutex.withLock {

                val queryID = nextQueryID.getAndIncrement()

                client.sendSelectTable(queryID, name)
                val response = waitForReply<ProtocolMessage.Table.SelectTableResponse>(queryID)

                return Client.Table(this, name, mainKey, response.shouldCacheAll, rowSerializer, keySerializer)
            }

        }

        suspend fun createTable(name: String, keyColumnName: String, shouldCacheAll: Boolean = false) {

            if (IS_DEBUGGING) {
                println("[C SuperNovae] createTable($name, $keyColumnName, $shouldCacheAll)")
            }

            waiterMutex.withLock {
                client.sendCreateTable(name, keyColumnName, shouldCacheAll)
            }
        }

        suspend fun deleteTable(name: String) {

            if (IS_DEBUGGING) {
                println("[C SuperNovae] deleteTable($name)")
            }

            waiterMutex.withLock {
                client.sendDeleteTable(name)
            }
        }



        // Can't make this an inner class due to issue: https://youtrack.jetbrains.com/issue/KT-12126
        inner class Table<R, MV, MK : KProperty1<R, MV>>(
            val name: String,
            val mainKeyProperty: MK,
            val shouldCacheAll: Boolean,
            val rowSerializer: KSerializer<R>,
            val keySerializer: KSerializer<MV>,
        ) {

            suspend fun load() {

                if (IS_DEBUGGING) {
                    println("[C SuperNovae ($name)] load()")
                }

                waiterMutex.withLock {
                    dbClient.client.sendCacheTable(name)
                }
            }

            suspend fun load(filter: FileDatabase.Filter) {

                if (IS_DEBUGGING) {
                    println("[C SuperNovae ($name)] load()")
                }

                dbClient.waiterMutex.withLock {
                    dbClient.client.sendCacheTable(name)
                }
            }

            suspend fun unload() {

                if (IS_DEBUGGING) {
                    println("[C SuperNovae ($name)] unload()")
                }

                dbClient.waiterMutex.withLock {
                    dbClient.client.sendUncacheTable(name)
                }
            }


            suspend fun create() {

                if (IS_DEBUGGING) {
                    println("[C SuperNovae ($name)] create()")
                }

                dbClient.waiterMutex.withLock {
                    dbClient.client.sendCreateTable(mainKeyProperty.name, name, shouldCacheAll)
                }
            }

            suspend fun delete() {

                if (IS_DEBUGGING) {
                    println("[C SuperNovae ($name)] delete()")
                }

                dbClient.waiterMutex.withLock {
                    dbClient.client.sendDeleteTable(name)
                }
            }

            suspend fun clear() {

                if (IS_DEBUGGING) {
                    println("[C SuperNovae ($name)] clear()")
                }

                dbClient.waiterMutex.withLock {
                    dbClient.client.sendClearTable(name)
                }
            }

            suspend fun listenToUpdates(listener: (type: ProtocolMessage.UpdateType, row: R) -> Unit): (ProtocolMessage.Table.UpdateNotification) -> Unit {

                if (IS_DEBUGGING) {
                    println("[C SuperNovae ($name)] listenToUpdates($listener)")
                }

                val dbListener: (ProtocolMessage.Table.UpdateNotification) -> Unit = {

                    //val oldRowValue = JSON.decodeFromString(rowSerializer, it.oldRow.toString())
                    val newRowValue = JSON.decodeFromString(rowSerializer, it.row.toString())

                    listener(it.type, newRowValue)
                }

                dbClient.waiterMutex.withLock {

                    dbClient.notificationListeners.getOrPut(name, { mutableListOf() }).add(dbListener)

                    if (dbClient.notificationListeners.getValue(name).size == 1) {
                        dbClient.client.sendListenToTable(name)
                    }
                }

                return dbListener
            }

            suspend fun removeListenToUpdates(listener: (ProtocolMessage.Table.UpdateNotification) -> Unit) {

                if (IS_DEBUGGING) {
                    println("[C SuperNovae ($name)] removeListenToUpdates($listener)")
                }

                dbClient.waiterMutex.withLock {

                    dbClient.notificationListeners[name]?.remove(listener)

                    if (dbClient.notificationListeners[name]?.isEmpty() == true) {
                        dbClient.client.sendStopListeningToTable(name)
                    }
                }
            }

            suspend fun selectRow(key: MV, onlyCheckCache: Boolean = shouldCacheAll, loadIntoCache: Boolean = false): R? {

                if (IS_DEBUGGING) {
                    println("[C SuperNovae ($name)] selectRow($key, $onlyCheckCache, $loadIntoCache)")
                }

                val filters = listOf(
                    FileDatabase.Filter(mainKeyProperty.name, ProtocolMessage.Check.EQUAL, JSON.encodeToJsonElement(keySerializer, key))
                )


                dbClient.waiterMutex.withLock {

                    val queryID = dbClient.nextQueryID.getAndIncrement()

                    dbClient.client.sendSelectRows(queryID, name, filters, onlyCheckCache, loadIntoCache, 1)

                    val rowText = (dbClient.waitForReply<ProtocolMessage.Blob>(queryID).messages.firstOrNull() as? ProtocolMessage.Table.SelectRowResponse)?.row?.toString()
                        ?: return null

                    return JSON.decodeFromString(rowSerializer, rowText)
                }
            }

            suspend fun selectAllRows(onlyInCache: Boolean = false): List<R> {

                if (IS_DEBUGGING) {
                    println("[C SuperNovae ($name)] selectAllRows($onlyInCache)")
                }

                dbClient.waiterMutex.withLock {

                    val queryID = dbClient.nextQueryID.getAndIncrement()

                    dbClient.client.sendSelectAllRows(queryID, name, onlyInCache)

                    return dbClient.waitForReply<ProtocolMessage.Blob>(queryID).messages.map {
                        it as ProtocolMessage.Table.SelectRowResponse
                        Json.decodeFromJsonElement(rowSerializer, it.row)
                    }
                }
            }

            suspend fun selectRows(vararg filters: FileDatabase.Filter, amountOfRows: Int = 0, onlyCheckCache: Boolean = shouldCacheAll, loadIntoCache: Boolean = false): List<R> {

                if (IS_DEBUGGING) {
                    println("[C SuperNovae ($name)] selectRows($filters)")
                }

                dbClient.waiterMutex.withLock {

                    val queryID = dbClient.nextQueryID.getAndIncrement()

                    dbClient.client.sendSelectRows(queryID, name, filters.toList(), onlyCheckCache, loadIntoCache, amountOfRows)

                    return dbClient.waitForReply<ProtocolMessage.Blob>(queryID).messages.map {
                        it as ProtocolMessage.Table.SelectRowResponse
                        Json.decodeFromJsonElement(rowSerializer, it.row)
                    }
                }
            }


            suspend fun insertRow(row: R, shouldCache: Boolean = shouldCacheAll) {

                if (IS_DEBUGGING) {
                    println("[C SuperNovae ($name)] insertRow($row)")
                }

                val rowAsJsonObject = JSON.encodeToJsonElement(rowSerializer, row) as JsonObject

                dbClient.waiterMutex.withLock {
                    dbClient.client.sendInsertRow(name, rowAsJsonObject, shouldCache)
                }
            }

            suspend fun deleteRow(keyValue: MV) {

                if (IS_DEBUGGING) {
                    println("[C SuperNovae ($name)] deleteRow($keyValue)")
                }

                val filters = listOf(
                    FileDatabase.Filter(mainKeyProperty.name, ProtocolMessage.Check.EQUAL, JSON.encodeToJsonElement(keySerializer, keyValue))
                )

                dbClient.waiterMutex.withLock {
                    dbClient.client.sendDeleteRows(name, filters, 1)
                }
            }

            suspend fun deleteRows(filters: List<FileDatabase.Filter>, amountOfRows: Int = 0) {

                if (IS_DEBUGGING) {
                    println("[C SuperNovae ($name)] deleteRows($filters, $amountOfRows)")
                }

                dbClient.waiterMutex.withLock {
                    dbClient.client.sendDeleteRows(name, filters, amountOfRows)
                }
            }


            // TODO: Take a list of updates
            suspend inline fun <reified T> updateRow(row: R, newValueProperty: KProperty1<R, T>, serializer: KSerializer<T> = serializer(), onlyCheckCache: Boolean = shouldCacheAll) {

                if (IS_DEBUGGING) {
                    println("[C SuperNovae ($name)] updateRow[0]($row, $newValueProperty, ${newValueProperty.name}, $serializer, $onlyCheckCache)")
                }

                val filter = FileDatabase.Filter(mainKeyProperty.name, ProtocolMessage.Check.EQUAL, JSON.encodeToJsonElement(keySerializer, mainKeyProperty.get(row)))

                // Don't use mutex here
                updateRows(filter, newValueProperty, newValueProperty.get(row), serializer, 1, onlyCheckCache)
            }

            suspend inline fun <reified T> updateRow(keyValue: MV, newValueProperty: KProperty1<R, T>, newValue: T, serializer: KSerializer<T> = serializer(), onlyCheckCache: Boolean = shouldCacheAll) {

                if (IS_DEBUGGING) {
                    println("[C SuperNovae ($name)] updateRow[1]($keyValue, $newValueProperty, ${newValueProperty.name}, $newValue, $serializer)")
                }

                val filter = FileDatabase.Filter(mainKeyProperty.name, ProtocolMessage.Check.EQUAL, JSON.encodeToJsonElement(keySerializer, keyValue))

                // Don't use mutex here
                updateRows(filter, newValueProperty, newValue, serializer, 1, onlyCheckCache)
            }

            suspend inline fun <reified T> updateRows(filter: FileDatabase.Filter, property: KProperty1<R, T>, newValue: T, serializer: KSerializer<T> = serializer(), amountOfRows: Int = 0, onlyCheckCache: Boolean = shouldCacheAll) {

                if (IS_DEBUGGING) {
                    println("[C SuperNovae ($name)] updateRows($filter, ${property.name}, $newValue, $serializer)")
                }

                dbClient.waiterMutex.withLock {
                    dbClient.client.sendUpdateRows(
                        name,
                        filter,
                        property.name,
                        JSON.encodeToJsonElement(serializer, newValue),
                        amountOfRows,
                        onlyCheckCache
                    )
                }
            }

        }

    }

}