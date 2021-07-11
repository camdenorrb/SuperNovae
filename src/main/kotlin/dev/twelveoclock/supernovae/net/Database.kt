package dev.twelveoclock.supernovae.net

import dev.twelveoclock.supernovae.api.FileDatabase
import dev.twelveoclock.supernovae.protocol.ProtocolMessage
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import kotlin.reflect.KProperty1

class Database(val id: Int, val client: Client) {

    // TODO: Store an int id to be sent as a varint to the server on each query

    suspend inline fun <reified R, reified MV, MK : KProperty1<R, MV>> table(
        name: String,
        mainKey: MK,
        rowSerializer: KSerializer<R> = serializer(),
        keySerializer: KSerializer<MV> = serializer()
    ): Table<R, MV, MK> {

        waiterMutex.withLock {

            val queryID = nextQueryID.getAndIncrement()

            sendSelectTable(queryID, name)
            val response = waitForReply<ProtocolMessage.Table.SelectTableResponse>(queryID)

            return Table(name, mainKey, response.shouldCacheAll, rowSerializer, keySerializer)
        }

    }

    suspend fun createTable(name: String, keyColumnName: String, shouldCacheAll: Boolean = false) {

        if (Client.IS_DEBUGGING) {
            println("[C SuperNovae] createTable($name, $keyColumnName, $shouldCacheAll)")
        }

        waiterMutex.withLock {
            sendCreateTable(name, keyColumnName, shouldCacheAll)
        }
    }

    suspend fun deleteTable(name: String) {

        if (Client.IS_DEBUGGING) {
            println("[C SuperNovae] deleteTable($name)")
        }

        waiterMutex.withLock {
            sendDeleteTable(name)
        }
    }



    // Can't make this an inner class due to issue: https://youtrack.jetbrains.com/issue/KT-12126

    inner class Table<R, MV, MK : KProperty1<R, MV>> @PublishedApi internal constructor(
        val name: String,
        val mainKeyProperty: MK,
        val shouldCacheAll: Boolean,
        val rowSerializer: KSerializer<R>,
        val keySerializer: KSerializer<MV>,
    ) {

        suspend fun load() {

            if (Client.IS_DEBUGGING) {
                println("[C SuperNovae ($name)] load()")
            }

            waiterMutex.withLock {
                sendCacheTable(name)
            }
        }

        suspend fun load(filter: FileDatabase.Filter) {

            if (Client.IS_DEBUGGING) {
                println("[C SuperNovae ($name)] load()")
            }

            waiterMutex.withLock {
                sendCacheTable(name)
            }
        }

        suspend fun unload() {

            if (Client.IS_DEBUGGING) {
                println("[C SuperNovae ($name)] unload()")
            }

            waiterMutex.withLock {
                sendUncacheTable(name)
            }
        }


        suspend fun create() {

            if (Client.IS_DEBUGGING) {
                println("[C SuperNovae ($name)] create()")
            }

            waiterMutex.withLock {
                sendCreateTable(mainKeyProperty.name, name, shouldCacheAll)
            }
        }

        suspend fun delete() {

            if (Client.IS_DEBUGGING) {
                println("[C SuperNovae ($name)] delete()")
            }

            waiterMutex.withLock {
                sendDeleteTable(name)
            }
        }

        suspend fun clear() {

            if (Client.IS_DEBUGGING) {
                println("[C SuperNovae ($name)] clear()")
            }

            waiterMutex.withLock {
                sendClearTable(name)
            }
        }

        suspend fun listenToUpdates(listener: (type: ProtocolMessage.UpdateType, row: R) -> Unit): (ProtocolMessage.Table.UpdateNotification) -> Unit {

            if (Client.IS_DEBUGGING) {
                println("[C SuperNovae ($name)] listenToUpdates($listener)")
            }

            val dbListener: (ProtocolMessage.Table.UpdateNotification) -> Unit = {

                //val oldRowValue = JSON.decodeFromString(rowSerializer, it.oldRow.toString())
                val newRowValue = Client.JSON.decodeFromString(rowSerializer, it.row.toString())

                listener(it.type, newRowValue)
            }

            waiterMutex.withLock {

                notificationListeners.getOrPut(name) { mutableListOf() }.add(dbListener)

                if (notificationListeners.getValue(name).size == 1) {
                    sendListenToTable(name)
                }
            }

            return dbListener
        }

        suspend fun removeListenToUpdates(listener: (ProtocolMessage.Table.UpdateNotification) -> Unit) {

            if (Client.IS_DEBUGGING) {
                println("[C SuperNovae ($name)] removeListenToUpdates($listener)")
            }

            waiterMutex.withLock {

                notificationListeners[name]?.remove(listener)

                if (notificationListeners[name]?.isEmpty() == true) {
                    sendStopListeningToTable(name)
                }
            }
        }

        suspend fun selectRow(key: MV, onlyCheckCache: Boolean = shouldCacheAll, loadIntoCache: Boolean = false): R? {

            if (Client.IS_DEBUGGING) {
                println("[C SuperNovae ($name)] selectRow($key, $onlyCheckCache, $loadIntoCache)")
            }

            val filters = listOf(
                FileDatabase.Filter(mainKeyProperty.name, ProtocolMessage.Check.EQUAL, Client.JSON.encodeToJsonElement(keySerializer, key))
            )


            waiterMutex.withLock {

                val queryID = nextQueryID.getAndIncrement()

                sendSelectRows(queryID, name, filters, onlyCheckCache, loadIntoCache, 1)

                val rowText = (waitForReply<ProtocolMessage.Blob>(queryID).messages.firstOrNull() as? ProtocolMessage.Table.SelectRowResponse)?.row?.toString()
                    ?: return null

                return Client.JSON.decodeFromString(rowSerializer, rowText)
            }
        }

        suspend fun selectAllRows(onlyInCache: Boolean = false): List<R> {

            if (Client.IS_DEBUGGING) {
                println("[C SuperNovae ($name)] selectAllRows($onlyInCache)")
            }

            waiterMutex.withLock {

                val queryID = nextQueryID.getAndIncrement()

                sendSelectAllRows(queryID, name, onlyInCache)

                return waitForReply<ProtocolMessage.Blob>(queryID).messages.map {
                    it as ProtocolMessage.Table.SelectRowResponse
                    Json.decodeFromJsonElement(rowSerializer, it.row)
                }
            }
        }

        suspend fun selectRows(vararg filters: FileDatabase.Filter, amountOfRows: Int = 0, onlyCheckCache: Boolean = shouldCacheAll, loadIntoCache: Boolean = false): List<R> {

            if (Client.IS_DEBUGGING) {
                println("[C SuperNovae ($name)] selectRows($filters)")
            }

            waiterMutex.withLock {

                val queryID = nextQueryID.getAndIncrement()

                sendSelectRows(queryID, name, filters.toList(), onlyCheckCache, loadIntoCache, amountOfRows)

                return waitForReply<ProtocolMessage.Blob>(queryID).messages.map {
                    it as ProtocolMessage.Table.SelectRowResponse
                    Json.decodeFromJsonElement(rowSerializer, it.row)
                }
            }
        }


        suspend fun insertRow(row: R, shouldCache: Boolean = shouldCacheAll) {

            if (Client.IS_DEBUGGING) {
                println("[C SuperNovae ($name)] insertRow($row)")
            }

            val rowAsJsonObject = Client.JSON.encodeToJsonElement(rowSerializer, row) as JsonObject

            waiterMutex.withLock {
                sendInsertRow(name, rowAsJsonObject, shouldCache)
            }
        }

        suspend fun deleteRow(keyValue: MV) {

            if (Client.IS_DEBUGGING) {
                println("[C SuperNovae ($name)] deleteRow($keyValue)")
            }

            val filters = listOf(
                FileDatabase.Filter(mainKeyProperty.name, ProtocolMessage.Check.EQUAL, Client.JSON.encodeToJsonElement(keySerializer, keyValue))
            )

            waiterMutex.withLock {
                sendDeleteRows(name, filters, 1)
            }
        }

        suspend fun deleteRows(filters: List<FileDatabase.Filter>, amountOfRows: Int = 0) {

            if (Client.IS_DEBUGGING) {
                println("[C SuperNovae ($name)] deleteRows($filters, $amountOfRows)")
            }

            waiterMutex.withLock {
                sendDeleteRows(name, filters, amountOfRows)
            }
        }


        // TODO: Take a list of updates
        suspend inline fun <reified T> updateRow(row: R, newValueProperty: KProperty1<R, T>, serializer: KSerializer<T> = serializer(), onlyCheckCache: Boolean = shouldCacheAll) {

            if (Client.IS_DEBUGGING) {
                println("[C SuperNovae ($name)] updateRow[0]($row, $newValueProperty, ${newValueProperty.name}, $serializer, $onlyCheckCache)")
            }

            val filter = FileDatabase.Filter(mainKeyProperty.name, ProtocolMessage.Check.EQUAL, Client.JSON.encodeToJsonElement(keySerializer, mainKeyProperty.get(row)))

            // Don't use mutex here
            updateRows(filter, newValueProperty, newValueProperty.get(row), serializer, 1, onlyCheckCache)
        }

        suspend inline fun <reified T> updateRow(keyValue: MV, newValueProperty: KProperty1<R, T>, newValue: T, serializer: KSerializer<T> = serializer(), onlyCheckCache: Boolean = shouldCacheAll) {

            if (Client.IS_DEBUGGING) {
                println("[C SuperNovae ($name)] updateRow[1]($keyValue, $newValueProperty, ${newValueProperty.name}, $newValue, $serializer)")
            }

            val filter = FileDatabase.Filter(mainKeyProperty.name, ProtocolMessage.Check.EQUAL, Client.JSON.encodeToJsonElement(keySerializer, keyValue))

            // Don't use mutex here
            updateRows(filter, newValueProperty, newValue, serializer, 1, onlyCheckCache)
        }

        suspend inline fun <reified T> updateRows(filter: FileDatabase.Filter, property: KProperty1<R, T>, newValue: T, serializer: KSerializer<T> = serializer(), amountOfRows: Int = 0, onlyCheckCache: Boolean = shouldCacheAll) {

            if (Client.IS_DEBUGGING) {
                println("[C SuperNovae ($name)] updateRows($filter, ${property.name}, $newValue, $serializer)")
            }

            waiterMutex.withLock {
                sendUpdateRows(
                    name,
                    filter,
                    property.name,
                    Client.JSON.encodeToJsonElement(serializer, newValue),
                    amountOfRows,
                    onlyCheckCache
                )
            }
        }

    }

}