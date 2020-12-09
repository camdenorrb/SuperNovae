package dev.twelveoclock.supernovae.net

import dev.twelveoclock.supernovae.api.Database
import dev.twelveoclock.supernovae.ext.build
import dev.twelveoclock.supernovae.ext.filter
import dev.twelveoclock.supernovae.ext.suspendReadNovaeMessage
import dev.twelveoclock.supernovae.ext.suspendSendNovaeMessage
import dev.twelveoclock.supernovae.proto.DBProto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import me.camdenorrb.netlius.Netlius
import me.camdenorrb.netlius.net.Client
import org.capnproto.MessageBuilder
import java.io.File

//import me.camdenorrb.netlius.net.Client as NetClient

class DBServer(
    val host: String,
    val port: Int,
    val serverFolder: File
) {

    val netServer = Netlius.server(host, port, false, Long.MAX_VALUE)

    // Name lowercase -> Database
    // TODO: Load current ones
    val databases = mutableMapOf<String, Database>()

    val selectedDatabase = mutableMapOf<Client, Database>()

    val changeListeners = mutableMapOf<Client, MutableMap<Database, MutableSet<Database.Table>>>()


    var isRunning = false
        private set


    init {

        serverFolder.mkdirs()

        netServer.onConnect {
            connectHandler(it)
        }

        serverFolder.listFiles()?.filter { it.isDirectory }?.forEach {
            databases[it.name] = Database(it)
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

                when (message.which()!!) {
                    DBProto.Message.Which.CREATE_DB -> client.createDB(message.createDb)
                    DBProto.Message.Which.CREATE_TABLE -> client.createTable(message.createTable)
                    DBProto.Message.Which.SELECT_DB -> client.selectDB(message.selectDb)
                    DBProto.Message.Which.SELECT_TABLE -> client.selectTable(message.selectTable)
                    DBProto.Message.Which.SELECT_ALL_ROWS -> client.selectAllRows(message.selectAllRows)
                    DBProto.Message.Which.SELECT_ROWS -> client.selectRows(message.selectRows)
                    DBProto.Message.Which.INSERT_ROW -> client.insertRow(message.insertRow)
                    DBProto.Message.Which.UPDATE_ROWS -> client.updateRows(message.updateRows)
                    DBProto.Message.Which.CACHE_ROWS -> client.cacheRows(message.cacheRows)
                    DBProto.Message.Which.CACHE_TABLE -> client.cacheTable(message.cacheTable)
                    DBProto.Message.Which.UNCACHE_ROWS -> client.uncacheRows(message.uncacheRows)
                    DBProto.Message.Which.UNCACHE_TABLE -> client.uncacheTable(message.uncacheTable)
                    DBProto.Message.Which.DELETE_DB -> client.deleteDB(message.deleteDb)
                    DBProto.Message.Which.DELETE_ROWS -> client.deleteRows(message.deleteRows)
                    DBProto.Message.Which.DELETE_TABLE -> client.deleteTable(message.deleteTable)
                    DBProto.Message.Which.CLEAR_TABLE -> client.clearTable(message.clearTable)
                    DBProto.Message.Which.LISTEN_TO_TABLE -> client.listenToTable(message.listenToTable)
                    DBProto.Message.Which.REMOVE_LISTEN_TO_TABLE -> client.removeListenToTable(message.removeListenToTable)
                    DBProto.Message.Which.REMOVE_ALL_LISTEN_TO_TABLES -> client.removeAllListenToTables(message.removeAllListenToTables)
                    DBProto.Message.Which.UPDATE_NOTIFICATION -> error("Unexpected update notification.")
                    DBProto.Message.Which.SELECT_ROW_RESPONSE -> error("Unexpected select row response.")
                    DBProto.Message.Which.SELECT_TABLE_RESPONSE -> error("Unexpected select table response.")
                    DBProto.Message.Which.BLOB -> error("Unexpected blob.")
                    DBProto.Message.Which._NOT_IN_SCHEMA -> error("Unknown message received.")
                }
            } catch (ex: Exception) {
                // Ignored
            }

        }

        if (IS_DEBUGGING) {
            println("Disconnected")
        }

        selectedDatabase.remove(client)
        changeListeners.remove(client)
    }


    private fun Client.createDB(message: DBProto.CreateDB.Reader) {

        val databaseName = message.databaseName.toString()

        check(!databaseName.contains(File.separator)) {
            "Can't create a database with a name that contains a path separator character '${databaseName}'"
        }

        check(databaseName.isNotBlank()) {
            "Can't create a database with a blank name"
        }

        if (databaseName !in databases) {
            val database = Database(File(serverFolder, databaseName))
            databases[databaseName] = database
        }
    }

    private fun Client.createTable(message: DBProto.CreateTable.Reader) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] createTable(${message.tableName}, ${message.keyColumn}, ${message.shouldCacheAll})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to create a table."
        }

        if (message.tableName.toString() !in selectedDatabase.tables) {
            selectedDatabase.createTable(message.tableName.toString(), message.shouldCacheAll, message.keyColumn.toString())
        }
    }


    private fun Client.selectDB(message: DBProto.SelectDB.Reader) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] selectDB(${message.databaseName})")
        }

        selectedDatabase[this] = databases.getValue(message.databaseName.toString())
    }

    private fun Client.deleteDB(message: DBProto.DeleteDB.Reader) {
        databases.remove(message.databaseName.toString())
        //selectedDatabase[this] = databases.getValue(message.databaseName.toString().toLowerCase())
    }

    private suspend fun Client.selectTable(message: DBProto.SelectTable.Reader) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] selectTable(${message.tableName})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to create a table."
        }

        val table = checkNotNull(selectedDatabase.tables[message.tableName.toString()]) {
            "The selected table ${message.tableName} does not exist."
        }

        val responseMessage = DBProto.Message.factory.build { builder ->
            builder.initSelectTableResponse().apply {
                setTableName(table.name)
                setKeyColumn(table.keyColumn)
                shouldCacheAll = table.shouldCacheAll
            }
        }

        suspendSendNovaeMessage(responseMessage)
    }

    private suspend fun Client.deleteRows(message: DBProto.DeleteRows.Reader) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] deleteRows(${message.tableName}, ${message.filters.joinToString { "${it.columnName} ${it.check} ${it.compareToValue}" }}, ${message.amountOfRows}, ${message.onlyCheckCache})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString()]) {
            "Unable to find table '${message.tableName}'."
        }

        var selectedRows: List<JsonObject>? = null

        message.filters.forEach {
            val filter = Database.Filter.fromCapnProtoReader(it)
            selectedRows = selectedRows?.filter(filter) ?: selectedTable.filter(filter, message.amountOfRows, message.onlyCheckCache)
        }

        val filteredRows = selectedRows ?: return

        filteredRows.forEach {
            selectedTable.delete(it[selectedTable.keyColumn].toString())
        }

        sendNotification(selectedDatabase, selectedTable, filteredRows, DBProto.UpdateType.MODIFICATION)
    }

    private suspend fun Client.selectAllRows(message: DBProto.SelectAllRows.Reader) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] selectAllRows(${message.tableName})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString()]) {
            "Unable to find table '${message.tableName}'."
        }

        val selectedRows = selectedTable.getAllRows(message.onlyInCache)

        val responseMessage = DBProto.Message.factory.build { builder ->
            builder.initBlob().also {

                val messages = it.initList(selectedRows.size)

                selectedRows.forEachIndexed { index, row ->
                    messages.setWithCaveats(DBProto.Message.factory, index, MessageBuilder().initRoot(DBProto.Message.factory).also {
                        it.initSelectRowResponse().apply {
                            setRow(row.toString())
                        }
                    }.asReader())
                }
            }
        }

        suspendSendNovaeMessage(responseMessage)
    }

    private suspend fun Client.selectRows(message: DBProto.SelectRows.Reader) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] selectRows(${message.tableName}, ${message.filters.joinToString { "${it.columnName} ${it.check} ${it.compareToValue}" }}, ${message.amountOfRows}, ${message.loadIntoCache}, ${message.onlyCheckCache})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString()]) {
            "Unable to find table '${message.tableName}'."
        }

        var selectedRows: List<JsonObject>? = null

        message.filters.forEach {
            val filter = Database.Filter.fromCapnProtoReader(it)
            selectedRows = selectedRows?.filter(filter) ?: selectedTable.filter(filter, message.amountOfRows, message.onlyCheckCache)
        }

        val responseMessage = DBProto.Message.factory.build { builder ->
            builder.initBlob().also {

                val messages = it.initList(selectedRows?.size ?: 0)

                selectedRows?.forEachIndexed { index, row ->
                    messages.setWithCaveats(DBProto.Message.factory, index, MessageBuilder().initRoot(DBProto.Message.factory).also {
                        it.initSelectRowResponse().apply {
                            setRow(row.toString())
                        }
                    }.asReader())
                }
            }
        }

        suspendSendNovaeMessage(responseMessage)
    }

    private suspend fun Client.insertRow(message: DBProto.InsertRow.Reader) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] insertRow(${message.tableName}, ${message.row}, ${message.shouldCache})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString()]) {
            "Unable to find table '${message.tableName}'."
        }

        val jsonObject = JSON.decodeFromString(JsonObject.serializer(), message.row.toString())
        selectedTable.insert(jsonObject, message.shouldCache)
        sendNotification(selectedDatabase, selectedTable, listOf(jsonObject), DBProto.UpdateType.INSERT)
    }

    // TODO: Make responses for everything
    private suspend fun Client.updateRows(message: DBProto.UpdateRows.Reader) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] updateRows(${message.tableName}, ${message.filter.run { "$columnName $check $compareToValue" }}, ${message.columnName}, ${message.value}, ${message.amountOfRows}, ${message.onlyCheckCache})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString()]) {
            "Unable to find table '${message.tableName}'."
        }

        val rows = selectedTable.filter(
            Database.Filter.fromCapnProtoReader(message.filter),
            message.amountOfRows.takeIf { it != 0 },
            message.onlyCheckCache
        )

        if (rows.isEmpty()) {
            return
        }

        selectedTable.update(
            rows,
            message.columnName.toString(),
            message.value.toString(),
        )

        val newRows = rows.map {
            selectedTable.get(it.getValue(selectedTable.keyColumn))!!
        }

        sendNotification(selectedDatabase, selectedTable, newRows, DBProto.UpdateType.MODIFICATION)
    }


    private fun Client.cacheRows(message: DBProto.CacheRows.Reader) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] selectRows(${message.tableName}, ${message.filter}, ${message.onlyCheckCache})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString()]) {
            "Unable to find table '${message.tableName}'."
        }

        selectedTable.cache(Database.Filter.fromCapnProtoReader(message.filter))
    }

    private fun Client.cacheTable(message: DBProto.CacheTable.Reader) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] cacheTable(${message.tableName})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString()]) {
            "Unable to find table '${message.tableName}'."
        }

        selectedTable.cacheAllRows()
    }

    private fun Client.uncacheRows(message: DBProto.UncacheRows.Reader) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] uncacheRows(${message.tableName}, ${message.filter}, ${message.onlyCheckCache})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString()]) {
            "Unable to find table '${message.tableName}'."
        }

        selectedTable.uncache(Database.Filter.fromCapnProtoReader(message.filter))
    }

    private fun Client.uncacheTable(message: DBProto.UncacheTable.Reader) {


        if (IS_DEBUGGING) {
            println("[S SuperNovae] uncacheTable(${message.tableName})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString()]) {
            "Unable to find table '${message.tableName}'."
        }

        selectedTable.uncacheAllRows()
    }

    private fun Client.deleteTable(message: DBProto.DeleteTable.Reader) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] deleteTable(${message.tableName})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        selectedDatabase.deleteTable(message.tableName.toString())
    }

    private fun Client.clearTable(message: DBProto.ClearTable.Reader) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] clearTable(${message.tableName})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString()]) {
            "Unable to find table '${message.tableName}'."
        }

        selectedTable.clear()
    }

    private fun Client.listenToTable(message: DBProto.ListenToTable.Reader) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] listenToTable(${message.tableName})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString()]) {
            "Unable to find table '${message.tableName}'."
        }

        changeListeners.getOrPut(this, { mutableMapOf() }).getOrPut(selectedDatabase, { mutableSetOf() }).add(selectedTable)
    }

    private fun Client.removeListenToTable(message: DBProto.RemoveListenToTable.Reader) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] removeListenToTable(${message.tableName})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString()]) {
            "Unable to find table '${message.tableName}'."
        }

        changeListeners[this]?.get(selectedDatabase)?.remove(selectedTable)
    }

    private fun Client.removeAllListenToTables(message: DBProto.RemoveAllListenToTables.Reader) {

        if (IS_DEBUGGING) {
            println("[S SuperNovae] removeAllListenToTable()")
        }

        changeListeners.remove(this)
    }

    private suspend fun sendNotification(database: Database, table: Database.Table, rows: List<JsonObject>, type: DBProto.UpdateType) {

        val message = buildNotificationMessage(table.name, rows, type)

        changeListeners
            .filter { it.key.channel.isOpen }
            .filter { it.value[database]?.contains(table) == true }.keys
            .forEach { it.suspendSendNovaeMessage(message) }
    }

    private fun buildNotificationMessage(tableName: String, rows: List<JsonObject>, type: DBProto.UpdateType): MessageBuilder {
        return DBProto.Message.factory.build { builder ->
            builder.initBlob().also { it ->

                val messages = it.initList(rows.size)

                rows.forEachIndexed { index, row ->
                    messages.setWithCaveats(
                        DBProto.Message.factory,
                        index,
                        MessageBuilder().initRoot(DBProto.Message.factory).also {
                            it.initUpdateNotification().apply {
                                setTableName(tableName)
                                setType(type)
                                setRow(row.toString())
                            }
                        }.asReader()
                    )
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