package dev.twelveoclock.supernovae.net

import dev.twelveoclock.supernovae.api.Database
import dev.twelveoclock.supernovae.ext.*
import dev.twelveoclock.supernovae.proto.DBProto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import me.camdenorrb.netlius.Netlius
import me.camdenorrb.netlius.net.Client
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KProperty1

// This will be the high level remote implementation
// TODO: Make a high level for local as-well
class DBClient(val host: String, val port: Int) {

    val waiterMutex = Mutex()

    @Volatile
    var messageWaiter: Continuation<DBProto.Message.Reader>? = null

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

        client = Netlius.client(host, port, Long.MAX_VALUE)

        readTask = GlobalScope.launch(Dispatchers.IO) {
            while (client.channel.isOpen) {

                val message = client.suspendReadNovaeMessage()

                if (message.isBlob && message.blob.list.firstOrNull()?.isUpdateNotification == true) {

                    message.blob.list.map { it.updateNotification }.forEach { notification ->

                        if (IS_DEBUGGING) {
                            println("[C SuperNovae] Received update: ${notification.tableName} ${notification.row} ${notification.type}")
                        }
                        notificationListeners[notification.tableName.toString()]?.forEach {
                            it(notification)
                        }
                    }

                    continue
                }

                messageWaiter?.resume(message)
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
        messageWaiter = null

        if (waiterMutex.isLocked) {
            waiterMutex.unlock()
        }

        isConnected = false
    }

    suspend fun waitForReply(): DBProto.Message.Reader {
        return suspendCoroutine {
            messageWaiter = it
        }
    }

    suspend inline fun <reified R, reified MV, MK : KProperty1<R, MV>> selectTable(
        name: String,
        mainKey: MK,
        rowSerializer: KSerializer<R> = serializer(),
        keySerializer: KSerializer<MV> = serializer()
    ): Table<R, MV, MK> {

        waiterMutex.withLock {

            client.sendSelectTable(name)
            val response = waitForReply(/*DBProto.Message.Which.SELECT_TABLE_RESPONSE*/).selectTableResponse

            return Table(this, name, mainKey, response.shouldCacheAll, rowSerializer, keySerializer)
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

    suspend fun selectDB(name: String) {

        if (IS_DEBUGGING) {
            println("[C SuperNovae] selectDB($name)")
        }

        waiterMutex.withLock {
            client.sendSelectDB(name)
        }
    }

    suspend fun createDB(name: String) {

        if (IS_DEBUGGING) {
            println("[C SuperNovae] createDB($name)")
        }

        waiterMutex.withLock {
            client.sendCreateDB(name)
        }
    }

    suspend fun deleteDB(name: String) {

        if (IS_DEBUGGING) {
            println("[C SuperNovae] deleteDB($name)")
        }

        waiterMutex.withLock {
            client.sendDeleteDB(name)
        }
    }


    // Can't make this an inner class due to issue: https://youtrack.jetbrains.com/issue/KT-12126
    class Table<R, MV, MK : KProperty1<R, MV>>(
        val dbClient: DBClient,
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

            dbClient.waiterMutex.withLock {
                dbClient.client.sendLoadTable(name)
            }
        }

        suspend fun load(filter: Database.Filter) {

            if (IS_DEBUGGING) {
                println("[C SuperNovae ($name)] load()")
            }

            dbClient.waiterMutex.withLock {
                dbClient.client.sendLoadTable(name)
            }
        }

        suspend fun unload() {

            if (IS_DEBUGGING) {
                println("[C SuperNovae ($name)] unload()")
            }

            dbClient.waiterMutex.withLock {
                dbClient.client.sendUnloadTable(name)
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

        suspend fun listenToUpdates(listener: (type: DBProto.UpdateType, row: R) -> Unit): (DBProto.UpdateNotification.Reader) -> Unit {

            if (IS_DEBUGGING) {
                println("[C SuperNovae ($name)] listenToUpdates($listener)")
            }

            val dbListener: (DBProto.UpdateNotification.Reader) -> Unit = {

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

        suspend fun removeListenToUpdates(listener: (DBProto.UpdateNotification.Reader) -> Unit) {

            if (IS_DEBUGGING) {
                println("[C SuperNovae ($name)] removeListenToUpdates($listener)")
            }

            dbClient.waiterMutex.withLock {

                dbClient.notificationListeners[name]?.remove(listener)

                if (dbClient.notificationListeners[name]?.isEmpty() == true) {
                    dbClient.client.sendRemoveListenToTable(name)
                }
            }
        }

        suspend fun selectRow(key: MV, onlyCheckCache: Boolean = shouldCacheAll, loadIntoCache: Boolean = false): R? {

            if (IS_DEBUGGING) {
                println("[C SuperNovae ($name)] selectRow($key, $onlyCheckCache, $loadIntoCache)")
            }

            val filters = listOf(
                Database.Filter(mainKeyProperty.name, DBProto.Check.EQUAL, JSON.encodeToJsonElement(keySerializer, key))
            )


            dbClient.waiterMutex.withLock {

                dbClient.client.sendSelectRows(name, filters, onlyCheckCache, loadIntoCache, 1)

                val rowText = dbClient.waitForReply(/*DBProto.Message.Which.BLOB*/).blob.list.firstOrNull()?.selectRowResponse?.row?.toString()
                        ?: return null

                return JSON.decodeFromString(rowSerializer, rowText)
            }
        }

        suspend fun selectAllRows(onlyInCache: Boolean = false): List<R> {

            if (IS_DEBUGGING) {
                println("[C SuperNovae ($name)] selectAllRows($onlyInCache)")
            }

            dbClient.waiterMutex.withLock {

                dbClient.client.sendSelectAllRows(name, onlyInCache)

                return dbClient.waitForReply(/*DBProto.Message.Which.BLOB*/).blob.list.map {
                    JSON.decodeFromString(rowSerializer, it.selectRowResponse.row.toString())
                }
            }
        }

        suspend fun selectRows(filters: List<Database.Filter>, amountOfRows: Int = 0, onlyCheckCache: Boolean = shouldCacheAll, loadIntoCache: Boolean = false): List<R> {

            if (IS_DEBUGGING) {
                println("[C SuperNovae ($name)] selectRows($filters)")
            }

            dbClient.waiterMutex.withLock {

                dbClient.client.sendSelectRows(name, filters, onlyCheckCache, loadIntoCache, amountOfRows)

                return dbClient.waitForReply(/*DBProto.Message.Which.BLOB*/).blob.list.map {
                    JSON.decodeFromString(rowSerializer, it.selectRowResponse.row.toString())
                }
            }
        }


        suspend fun insertRow(row: R, shouldCache: Boolean = shouldCacheAll) {

            if (IS_DEBUGGING) {
                println("[C SuperNovae ($name)] insertRow($row)")
            }

            val rowAsString = JSON.encodeToString(rowSerializer, row)

            dbClient.waiterMutex.withLock {
                dbClient.client.sendInsertRow(name, rowAsString, shouldCache)
            }
        }

        suspend fun deleteRow(keyValue: MV) {

            if (IS_DEBUGGING) {
                println("[C SuperNovae ($name)] deleteRow($keyValue)")
            }

            val filters = listOf(
                Database.Filter(mainKeyProperty.name, DBProto.Check.EQUAL, JSON.encodeToJsonElement(keySerializer, keyValue))
            )

            dbClient.waiterMutex.withLock {
                dbClient.client.sendDeleteRows(name, filters, 1)
            }
        }

        suspend fun deleteRows(filters: List<Database.Filter>, amountOfRows: Int = 0) {

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

            val filter = Database.Filter(mainKeyProperty.name, DBProto.Check.EQUAL, JSON.encodeToJsonElement(keySerializer, mainKeyProperty.get(row)))

            // Don't use mutex here
            updateRows(filter, newValueProperty, newValueProperty.get(row), serializer, 1, onlyCheckCache)
        }

        suspend inline fun <reified T> updateRow(keyValue: MV, newValueProperty: KProperty1<R, T>, newValue: T, serializer: KSerializer<T> = serializer(), onlyCheckCache: Boolean = shouldCacheAll) {

            if (IS_DEBUGGING) {
                println("[C SuperNovae ($name)] updateRow[1]($keyValue, $newValueProperty, ${newValueProperty.name}, $newValue, $serializer)")
            }

            val filter = Database.Filter(mainKeyProperty.name, DBProto.Check.EQUAL, JSON.encodeToJsonElement(keySerializer, keyValue))

            // Don't use mutex here
            updateRows(filter, newValueProperty, newValue, serializer, 1, onlyCheckCache)
        }

        suspend inline fun <reified T> updateRows(filter: Database.Filter, property: KProperty1<R, T>, newValue: T, serializer: KSerializer<T> = serializer(), amountOfRows: Int = 0, onlyCheckCache: Boolean = shouldCacheAll) {

            if (IS_DEBUGGING) {
                println("[C SuperNovae ($name)] updateRows($filter, ${property.name}, $newValue, $serializer)")
            }

            dbClient.waiterMutex.withLock {
                dbClient.client.sendUpdateRows(
                    name,
                    property.name,
                    JSON.encodeToString(serializer, newValue),
                    filter,
                    amountOfRows,
                    onlyCheckCache
                )
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