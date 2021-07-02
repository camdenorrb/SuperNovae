package dev.twelveoclock.supernovae.net

import dev.twelveoclock.supernovae.api.FileDatabase
import dev.twelveoclock.supernovae.ext.filter
import dev.twelveoclock.supernovae.ext.suspendReadNovaeMessage
import dev.twelveoclock.supernovae.ext.suspendSendNovaeMessage
import dev.twelveoclock.supernovae.protocol.ProtocolMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import me.camdenorrb.netlius.Netlius
import me.camdenorrb.netlius.net.Client
//import org.capnproto.MessageBuilder
import java.io.EOFException
import java.io.File
import java.nio.channels.AsynchronousCloseException

class Server(
    val host: String,
    val port: Int,
    val serverFolder: File
) {

    val netServer = Netlius.server(host, port, false, Long.MAX_VALUE)

    // Name lowercase -> Database
    // TODO: Load current ones
    val databases = mutableMapOf<String, FileDatabase>()

    val selectedDatabase = mutableMapOf<Client, FileDatabase>()

    val changeListeners = mutableMapOf<Client, MutableMap<FileDatabase, MutableSet<FileDatabase.Table>>>()


    var isRunning = false
        private set


    init {

        serverFolder.mkdirs()

        netServer.onConnect {
            connectHandler(it)
        }

        serverFolder.listFiles()?.filter { it.isDirectory }?.forEach {
            databases[it.name] = FileDatabase(it)
        }
    }


    fun start() {

        if (isRunning) {
            return
        }

        netServer.start()

        isRunning = true
    }

    fun stop() {

        if (!isRunning) {
            return
        }

        netServer.stop()
        
        isRunning = false
    }


    private suspend fun connectHandler(client: Client) {

        while (client.channel.isOpen) {

            try {

                val message = client.suspendReadNovaeMessage()

                when (message) {
                    is ProtocolMessage.DB.Create -> client.createDB(message)
                    is ProtocolMessage.DB.Select -> client.selectDB(message)
                    is ProtocolMessage.DB.Delete -> client.deleteDB(message)
                    is ProtocolMessage.Table.Create -> client.createTable(message)
                    is ProtocolMessage.Table.Select -> client.selectTable(message)
                    is ProtocolMessage.Table.Cache -> client.cacheTable(message)
                    is ProtocolMessage.Table.UnCache -> client.uncacheTable(message)
                    is ProtocolMessage.Table.Clear -> client.clearTable(message)
                    is ProtocolMessage.Table.StartListening -> client.listenToTable(message)
                    is ProtocolMessage.Table.StopListening -> client.removeListenToTable(message)
                    is ProtocolMessage.Table.Delete -> client.deleteTable(message)
                    is ProtocolMessage.Table.InsertRow -> client.insertRow(message)
                    is ProtocolMessage.Table.CacheRows -> client.cacheRows(message)
                    is ProtocolMessage.Table.UpdateRows -> client.updateRows(message)
                    is ProtocolMessage.StopListeningToAllTables -> client.removeAllListenToTables(message)
                    is ProtocolMessage.Table.UncacheRows -> client.uncacheRows(message)
                    is ProtocolMessage.Table.SelectAllRows -> client.selectAllRows(message)
                    is ProtocolMessage.Table.SelectRows -> client.selectRows(message)
                    is ProtocolMessage.Table.DeleteRows -> client.deleteRows(message)
                    is ProtocolMessage.Blob -> error("Unexpected blob.")
                    is ProtocolMessage.Table.SelectRowResponse -> error("Unexpected select row response.")
                    is ProtocolMessage.Table.SelectTableResponse -> error("Unexpected select table response.")
                    is ProtocolMessage.Table.UpdateNotification -> error("Unexpected update notification.")
                }
            }
            catch (ex: EOFException) {
                // Ignored
            }
            catch (ex: AsynchronousCloseException) {
                // Ignored
            }
            catch (ex: Exception) {
                ex.printStackTrace()
            }

        }

        if (IS_DEBUGGING) {
            println("Disconnected")
        }

        selectedDatabase.remove(client)
        changeListeners.remove(client)
    }


    private fun Client.createDB(message: ProtocolMessage.DB.Create) {

        val databaseName = message.databaseName

        check(!databaseName.contains(File.separator)) {
            "Can't create a database with a name that contains a path separator character '${databaseName}'"
        }

        check(databaseName.isNotBlank()) {
            "Can't create a database with a blank name"
        }

        if (databaseName !in databases) {
            val database = FileDatabase(File(serverFolder, databaseName))
            databases[databaseName] = database
        }
    }

    private fun Client.createTable(message: ProtocolMessage.Table.Create) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] createTable(${message.tableName}, ${message.keyColumn}, ${message.shouldCacheAll})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to create a table."
        }

        if (message.tableName !in selectedDatabase.tables) {
            selectedDatabase.createTable(message.tableName, message.shouldCacheAll, message.keyColumn)
        }
    }


    private fun Client.selectDB(message: ProtocolMessage.DB.Select) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] selectDB(${message.databaseName})")
        }

        selectedDatabase[this] = databases.getValue(message.databaseName)
    }

    private fun Client.deleteDB(message: ProtocolMessage.DB.Delete) {
        databases.remove(message.databaseName)
        //selectedDatabase[this] = databases.getValue(message.databaseName.toString().toLowerCase())
    }

    private suspend fun Client.selectTable(message: ProtocolMessage.Table.Select) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] selectTable(${message.tableName})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to create a table."
        }

        val table = checkNotNull(selectedDatabase.tables[message.tableName]) {
            "The selected table ${message.tableName} does not exist."
        }

        suspendSendNovaeMessage(ProtocolMessage.QueryResponse(message.queryID, ProtocolMessage.Table.SelectTableResponse(table.name, table.keyColumn, table.shouldCacheAll)))
    }

    private suspend fun Client.deleteRows(message: ProtocolMessage.Table.DeleteRows) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] deleteRows(${message.tableName}, ${message.filters.joinToString { "${it.columnName} ${it.check} ${it.value}" }}, ${message.amountOfRows}, ${message.onlyCheckCache})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName]) {
            "Unable to find table '${message.tableName}'."
        }

        var selectedRows: List<JsonObject>? = null

        message.filters.forEach {
            selectedRows = selectedRows?.filter(it) ?: selectedTable.filter(it, message.amountOfRows, message.onlyCheckCache)
        }

        val filteredRows = selectedRows ?: return

        filteredRows.forEach {
            selectedTable.delete(it[selectedTable.keyColumn].toString())
        }

        sendNotification(selectedDatabase, selectedTable, filteredRows, ProtocolMessage.UpdateType.DELETION)
    }

    private suspend fun Client.selectAllRows(message: ProtocolMessage.Table.SelectAllRows) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] selectAllRows(${message.tableName})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName]) {
            "Unable to find table '${message.tableName}'."
        }

        val selectedRows = selectedTable.getAllRows(message.onlyCheckCache)

        val rowResponses = selectedRows.map {
            ProtocolMessage.Table.SelectRowResponse(message.queryID, it)
        }

        suspendSendNovaeMessage(ProtocolMessage.QueryResponse(message.queryID, ProtocolMessage.Blob(rowResponses)))
    }

    private suspend fun Client.selectRows(message: ProtocolMessage.Table.SelectRows) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] selectRows(${message.tableName}, ${message.filters.joinToString { "${it.columnName} ${it.check} ${it.value}" }}, ${message.amountOfRows}, ${message.loadIntoCache}, ${message.onlyCheckCache})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName]) {
            "Unable to find table '${message.tableName}'."
        }

        var selectedRows: List<JsonObject>? = null

        message.filters.forEach {
            selectedRows = selectedRows?.filter(it) ?: selectedTable.filter(it, message.amountOfRows, message.onlyCheckCache)
        }

        val rowResponses = selectedRows?.map {
            ProtocolMessage.Table.SelectRowResponse(message.queryID, it)
        } ?: emptyList()

        suspendSendNovaeMessage(ProtocolMessage.QueryResponse(message.queryID, ProtocolMessage.Blob(rowResponses)))
    }

    private suspend fun Client.insertRow(message: ProtocolMessage.Table.InsertRow) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] insertRow(${message.tableName}, ${message.row}, ${message.shouldCache})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName]) {
            "Unable to find table '${message.tableName}'."
        }

        val jsonObject = JSON.decodeFromString(JsonObject.serializer(), message.row.toString())

        val updateType = if (selectedTable.get(jsonObject.getValue(selectedTable.keyColumn)) == null) {
            ProtocolMessage.UpdateType.INSERT
        }
        else {
            ProtocolMessage.UpdateType.MODIFICATION
        }

        selectedTable.insert(jsonObject, message.shouldCache)
        sendNotification(selectedDatabase, selectedTable, listOf(jsonObject), updateType)
    }

    // TODO: Make responses for everything
    private suspend fun Client.updateRows(message: ProtocolMessage.Table.UpdateRows) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] updateRows(${message.tableName}, ${message.filter.run { "$columnName $check $value" }}, ${message.columnName}, ${message.value}, ${message.amountOfRows}, ${message.onlyCheckCache})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName]) {
            "Unable to find table '${message.tableName}'."
        }

        val rows = selectedTable.filter(
            message.filter,
            message.amountOfRows,
            message.onlyCheckCache
        )

        if (rows.isEmpty()) {
            return
        }

        selectedTable.update(
            rows,
            message.columnName,
            message.value.toString(),
        )

        val newRows = rows.map {
            selectedTable.get(it.getValue(selectedTable.keyColumn))!!
        }

        sendNotification(selectedDatabase, selectedTable, newRows, ProtocolMessage.UpdateType.MODIFICATION)
    }


    private fun Client.cacheRows(message: ProtocolMessage.Table.CacheRows) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] selectRows(${message.tableName}, ${message.filter})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName]) {
            "Unable to find table '${message.tableName}'."
        }

        selectedTable.cache(message.filter)
    }

    private fun Client.cacheTable(message: ProtocolMessage.Table.Cache) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] cacheTable(${message.tableName})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName]) {
            "Unable to find table '${message.tableName}'."
        }

        selectedTable.cacheAllRows()
    }

    private fun Client.uncacheRows(message: ProtocolMessage.Table.UncacheRows) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] uncacheRows(${message.tableName}, ${message.filter})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName]) {
            "Unable to find table '${message.tableName}'."
        }

        selectedTable.uncache(message.filter)
    }

    private fun Client.uncacheTable(message: ProtocolMessage.Table.UnCache) {


        if (IS_DEBUGGING) {
            println("[S SuperNovae] uncacheTable(${message.tableName})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName]) {
            "Unable to find table '${message.tableName}'."
        }

        selectedTable.uncacheAllRows()
    }

    private fun Client.deleteTable(message: ProtocolMessage.Table.Delete) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] deleteTable(${message.tableName})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        selectedDatabase.deleteTable(message.tableName)
    }

    private fun Client.clearTable(message: ProtocolMessage.Table.Clear) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] clearTable(${message.tableName})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName]) {
            "Unable to find table '${message.tableName}'."
        }

        selectedTable.clear()
    }

    private fun Client.listenToTable(message: ProtocolMessage.Table.StartListening) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] listenToTable(${message.tableName})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName]) {
            "Unable to find table '${message.tableName}'."
        }

        changeListeners.getOrPut(this, { mutableMapOf() }).getOrPut(selectedDatabase, { mutableSetOf() }).add(selectedTable)
    }

    private fun Client.removeListenToTable(message: ProtocolMessage.Table.StopListening) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] removeListenToTable(${message.tableName})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName]) {
            "Unable to find table '${message.tableName}'."
        }

        changeListeners[this]?.get(selectedDatabase)?.remove(selectedTable)
    }

    private fun Client.removeAllListenToTables(message: ProtocolMessage.StopListeningToAllTables) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] removeAllListenToTable()")
        }

        changeListeners.remove(this)
    }

    private suspend fun sendNotification(database: FileDatabase, table: FileDatabase.Table, rows: List<JsonObject>, type: ProtocolMessage.UpdateType) {

        val message = buildNotificationMessage(table.name, rows, type)

        changeListeners
            .filter { it.key.channel.isOpen }
            .filter { it.value[database]?.contains(table) == true }.keys
            .forEach { it.suspendSendNovaeMessage(message) }
    }

    private fun buildNotificationMessage(tableName: String, rows: List<JsonObject>, type: ProtocolMessage.UpdateType): ProtocolMessage {

        val notificationRows = rows.map {
            ProtocolMessage.Table.UpdateNotification(tableName, type, it)
        }

        return ProtocolMessage.Blob(notificationRows)
    }

    companion object {

        const val IS_DEBUGGING = true

        val JSON = Json {
            encodeDefaults = true
        }

    }

}