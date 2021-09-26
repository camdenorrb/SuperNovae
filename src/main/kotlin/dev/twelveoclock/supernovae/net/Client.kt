package dev.twelveoclock.supernovae.net

import dev.twelveoclock.supernovae.api.FileDatabase
import dev.twelveoclock.supernovae.protocol.ProtocolMessage
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.protobuf.ProtoBuf
import me.camdenorrb.netlius.Netlius
import me.camdenorrb.netlius.net.Client
import me.camdenorrb.netlius.net.Packet
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

// This will be the high level remote implementation
// TODO: Make a high level for local as-well
class Client(val host: String, val port: Int) {

    val waiterMutex = Mutex()

    val messageQueue = mutableListOf<ProtocolMessage>()

    var messageWaiters = ConcurrentHashMap<Int, Continuation<ProtocolMessage>>()

    // Table name -> Listeners
    val notificationListeners = mutableMapOf<String, MutableList<(ProtocolMessage.Table.UpdateNotification) -> Unit>>()


    @PublishedApi
    internal val nextQueryID = atomic(0)


    var isConnected = false
        private set

    lateinit var client: Client
        private set


    private val packetHandleTask = runBlocking {
        launch(Dispatchers.IO, CoroutineStart.UNDISPATCHED) {
            while (client.channel.isOpen) {
                handleNextMessage()
            }
        }
    }


    // TODO: Add a suspending version
    fun connect() {

        if (isConnected) {
            return
        }

        client = Netlius.client(host, port, Long.MAX_VALUE)
        packetHandleTask.start()

        isConnected = true
    }

    fun disconnect() {

        if (!isConnected) {
            return
        }

        messageWaiters.forEach {
            it.value.resumeWithException(IllegalStateException("Client has been disconnected"))
        }

        packetHandleTask.cancel()
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


    suspend fun suspendSendNovaeMessage(message: ProtocolMessage) {
        val byteArray = ProtoBuf.encodeToByteArray(ProtocolMessage.serializer(), message)
        client.queueAndFlush(Packet().int(byteArray.size).bytes(byteArray))
    }

    suspend fun suspendReadNovaeMessage(): ProtocolMessage {
        val bytes = client.suspendReadBytes(client.suspendReadInt())
        return ProtoBuf.decodeFromByteArray(ProtocolMessage.serializer(), bytes)
    }

    suspend fun createDB(dbName: String) {
        suspendSendNovaeMessage(ProtocolMessage.DB.Create(nextQueryID.getAndIncrement(), dbName))
    }

    suspend fun createTable(tableName: String, keyColumn: String, shouldCacheAll: Boolean = false) {
        suspendSendNovaeMessage(ProtocolMessage.Table.Create(nextQueryID.getAndIncrement(), tableName, keyColumn, shouldCacheAll))
    }

    suspend fun deleteDB(dbName: String) {
        suspendSendNovaeMessage(ProtocolMessage.DB.Delete(nextQueryID.getAndIncrement(), dbName))
    }

    suspend fun selectDB(dbName: String): Int {
        suspendSendNovaeMessage(ProtocolMessage.DB.Select(nextQueryID.getAndIncrement(), dbName))
        suspendReadNovaeMessage() as
    }

    suspend fun selectAllRows(tableName: String, onlyInCache: Boolean = false) {
        suspendSendNovaeMessage(ProtocolMessage.Table.SelectAllRows(nextQueryID.getAndIncrement(), tableName, onlyInCache))
    }

    suspend fun selectRows(tableName: String, filters: List<FileDatabase.Filter>, onlyCheckCache: Boolean = false, loadIntoCache: Boolean = false, amountOfRows: Int = -1) {
        suspendSendNovaeMessage(ProtocolMessage.Table.SelectRows(nextQueryID.getAndIncrement(), tableName, filters, onlyCheckCache, loadIntoCache, amountOfRows))
    }

    suspend fun deleteRows(tableName: String, filters: List<FileDatabase.Filter>, amountOfRows: Int = -1) {
        suspendSendNovaeMessage(ProtocolMessage.Table.DeleteRows(nextQueryID.getAndIncrement(), tableName, filters, amountOfRows))
    }

    suspend fun insertRow(tableName: String, row: JsonObject, shouldCache: Boolean = false) {
        suspendSendNovaeMessage(ProtocolMessage.Table.InsertRow(nextQueryID.getAndIncrement(), tableName, row, shouldCache))
    }

    suspend fun updateRows(tableName: String, filter: FileDatabase.Filter, columnName: String, row: JsonElement, amountOfRows: Int, onlyCheckCache: Boolean = false) {
        suspendSendNovaeMessage(ProtocolMessage.Table.UpdateRows(nextQueryID.getAndIncrement(), tableName, filter, columnName, row, amountOfRows, onlyCheckCache))
    }

    suspend fun cacheRows(tableName: String, filter: FileDatabase.Filter) {
        suspendSendNovaeMessage(ProtocolMessage.Table.CacheRows(nextQueryID.getAndIncrement(), tableName, filter))
    }

    suspend fun cacheTable(tableName: String) {
        suspendSendNovaeMessage(ProtocolMessage.Table.Cache(nextQueryID.getAndIncrement(), tableName))
    }

    suspend fun selectTable(tableName: String) {
        suspendSendNovaeMessage(ProtocolMessage.Table.Select(nextQueryID.getAndIncrement(), tableName))
    }

    suspend fun uncacheTable(tableName: String) {
        suspendSendNovaeMessage(ProtocolMessage.Table.UnCache(nextQueryID.getAndIncrement(), tableName))
    }

    suspend fun deleteTable(tableName: String) {
        suspendSendNovaeMessage(ProtocolMessage.Table.Delete(nextQueryID.getAndIncrement(), tableName))
    }

    suspend fun clearTable(tableName: String) {
        suspendSendNovaeMessage(ProtocolMessage.Table.Clear(nextQueryID.getAndIncrement(), tableName))
    }

    suspend fun stopListeningToTable(tableName: String) {
        suspendSendNovaeMessage(ProtocolMessage.Table.StopListening(nextQueryID.getAndIncrement(), tableName))
    }

    suspend fun listenToTable(tableName: String) {
        suspendSendNovaeMessage(ProtocolMessage.Table.StartListening(nextQueryID.getAndIncrement(), tableName))
    }


    suspend fun database(name: String): Database {

        if (IS_DEBUGGING) {
            println("[C SuperNovae] database($name)")
        }

        val databaseID = waiterMutex.withLock {
            selectDB(name)
        }

        return Database(databaseID, this)
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
            deleteDB(name)
        }
    }


    private suspend fun handleNextMessage() {

        val message = suspendReadNovaeMessage()

        if (message is ProtocolMessage.QueryResponse) {

            val waiter = checkNotNull(messageWaiters.remove(message.messageID)) {
                "Could not get a waiter for query id ${message.messageID}"
            }

            try {
                waiter.resume(message.innerMessage)
            }
            catch (ex: IllegalStateException) {
                println(message)
                ex.printStackTrace()
            }
        }
        else if (message is ProtocolMessage.Blob && message.messages.firstOrNull() is ProtocolMessage.Table.UpdateNotification) {
            message.messages.forEach { notification ->

                notification as ProtocolMessage.Table.UpdateNotification

                if (IS_DEBUGGING) {
                    println("[C SuperNovae] Received update: ${notification.tableName} ${notification.row} ${notification.type}")
                }

                notificationListeners[notification.tableName]?.forEach {
                    it(notification)
                }
            }
        }
    }

    companion object {

        const val IS_DEBUGGING = true

        val JSON = Json {
            encodeDefaults = true
        }

    }

}