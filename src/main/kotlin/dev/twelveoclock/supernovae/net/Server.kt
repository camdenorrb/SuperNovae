package dev.twelveoclock.supernovae.net

import dev.twelveoclock.supernovae.api.Database
import dev.twelveoclock.supernovae.ext.filter
import dev.twelveoclock.supernovae.ext.readNovaeMessage
import dev.twelveoclock.supernovae.ext.sendNovaeMessage
import dev.twelveoclock.supernovae.proto.CapnProto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import me.camdenorrb.netlius.Netlius
import me.camdenorrb.netlius.net.Client
import me.camdenorrb.netlius.net.Packet
import org.capnproto.MessageBuilder
import java.io.File

//import me.camdenorrb.netlius.net.Client as NetClient

class Server(
    val host: String,
    val port: Int,
    val serverFolder: File
) {

    val netServer = Netlius.server(host, port, false)

    // Name lowercase -> Database
    // TODO: Load current ones
    val databases = mutableMapOf<String, Database>()

    val selectedDatabase = mutableMapOf<Client, Database>()


    var isRunning = false
        private set


    init {

        serverFolder.mkdirs()

        netServer.onConnect {
            connectHandler(it)
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

            val message = client.readNovaeMessage()

            when (message.which()!!.also { println(it) }) {
                CapnProto.Message.Which.CREATE_DB -> client.createDB(message.createDb)
                CapnProto.Message.Which.CREATE_TABLE -> client.createTable(message.createTable)
                CapnProto.Message.Which.SELECT_DB -> client.selectDB(message.selectDb)
                CapnProto.Message.Which.SELECT_TABLE -> client.selectTable(message.selectTable)
                CapnProto.Message.Which.SELECT_ROWS -> client.selectRows(message.selectRows)
                CapnProto.Message.Which.INSERT_ROW -> client.insert(message.insertRow)
                CapnProto.Message.Which.UPDATE_ROWS -> client.update(message.updateRows)
                CapnProto.Message.Which.SELECT_ROW_RESPONSE -> error("Unexpected select row response.")
                CapnProto.Message.Which.SELECT_TABLE_RESPONSE -> error("Unexpected select table response.")
                CapnProto.Message.Which.CACHE_ROWS -> client.cacheRows(message.cacheRows)
                CapnProto.Message.Which.CACHE_TABLE -> client.cacheTable(message.cacheTable)
                CapnProto.Message.Which.UNCACHE_ROWS -> client.uncacheRows(message.uncacheRows)
                CapnProto.Message.Which.UNCACHE_TABLE -> client.uncacheTable(message.uncacheTable)
                CapnProto.Message.Which._NOT_IN_SCHEMA -> error("Unknown message received.")
                CapnProto.Message.Which.DELETE_DB -> client.deleteDB(message.deleteDb)
                CapnProto.Message.Which.DELETE_ROWS -> client.deleteRows(message.deleteRows)
                CapnProto.Message.Which.DELETE_TABLE -> client.deleteTable(message.deleteTable)
            }
        }

        selectedDatabase.remove(client)
    }


    private fun Client.createDB(message: CapnProto.CreateDB.Reader) {
        val database = Database(File(serverFolder, message.databaseName.toString()))
        databases[message.databaseName.toString().toLowerCase()] = database
    }

    private fun Client.createTable(message: CapnProto.CreateTable.Reader) {

        if (DEBUG) {
            println("[I] createTable(${message.tableName}, ${message.keyColumn}, ${message.shouldCacheAll})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to create a table."
        }

        selectedDatabase.createTable(message.tableName.toString(), message.shouldCacheAll, message.keyColumn.toString())
    }


    private fun Client.selectDB(message: CapnProto.SelectDB.Reader) {

        if (DEBUG) {
            println("[I] selectDB(${message.databaseName})")
        }

        selectedDatabase[this] = databases.getValue(message.databaseName.toString().toLowerCase())
    }

    private fun Client.deleteDB(message: CapnProto.DeleteDB.Reader) {
        databases.remove(message.databaseName.toString().toLowerCase())
        //selectedDatabase[this] = databases.getValue(message.databaseName.toString().toLowerCase())
    }

    private suspend fun Client.selectTable(message: CapnProto.SelectTable.Reader) {

        if (DEBUG) {
            println("[I] selectTable(${message.tableName})")
        }

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to create a table."
        }

        val table = checkNotNull(selectedDatabase.tables[message.tableName.toString()]) {
            "The selected table ${message.tableName} does not exist."
        }

        TODO("Select Table response")
        //sendNovaeMessage(table.message.tableName.toString())
        //selectedDatabase[this] = databases.getValue(message.databaseName.toString().toLowerCase())
    }

    private fun Client.deleteRows(message: CapnProto.DeleteRows.Reader) {

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString().toLowerCase()]) {
            "Unable to find table '${message.tableName}'."
        }

        var selectedRows: List<JsonObject>? = null

        message.filters.forEach {
            val filter = Database.Filter.fromCapnProtoReader(it)
            selectedRows = selectedRows?.filter(filter) ?: selectedTable.filter(filter, message.amountOfRows, message.onlyCheckCache)
        }

        selectedRows?.forEach {
            selectedTable.delete(it[selectedTable.keyColumn].toString())
        }
    }



    private suspend fun Client.selectRows(message: CapnProto.SelectRows.Reader) {

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString().toLowerCase()]) {
            "Unable to find table '${message.tableName}'."
        }

        var selectedRows: List<JsonObject>? = null

        message.filters.forEach {
            val filter = Database.Filter.fromCapnProtoReader(it)
            selectedRows = selectedRows?.filter(filter) ?: selectedTable.filter(filter, message.amountOfRows, message.onlyCheckCache)
        }

        queueAndFlush(Packet().int(selectedRows?.size ?: 0))

        selectedRows?.forEach {

            val builder = MessageBuilder()
            val selectResponse = builder.initRoot(CapnProto.SelectRowResponse.factory)

            selectResponse.setRow(it.toString())
            sendNovaeMessage(builder)
        }
    }

    private fun Client.insert(message: CapnProto.InsertRow.Reader) {

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString().toLowerCase()]) {
            "Unable to find table '${message.tableName}'."
        }

        val jsonObject = Json.decodeFromString(JsonObject.serializer(), message.row.toString())

        selectedTable.insert(jsonObject, message.shouldCache)
    }

    // TODO: Make responses for everything
    private fun Client.update(message: CapnProto.UpdateRows.Reader) {

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString().toLowerCase()]) {
            "Unable to find table '${message.tableName}'."
        }

        selectedTable.update(
            Database.Filter.fromCapnProtoReader(message.filter),
            message.columnName.toString(),
            message.value.toString(),
            message.amountOfRows.takeIf { it != 0 },
            message.onlyCheckCache
        )
    }


    private fun Client.cacheRows(message: CapnProto.CacheRows.Reader) {

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString().toLowerCase()]) {
            "Unable to find table '${message.tableName}'."
        }

        selectedTable.cache(Database.Filter.fromCapnProtoReader(message.filter))
    }

    private fun Client.cacheTable(message: CapnProto.CacheTable.Reader) {

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString().toLowerCase()]) {
            "Unable to find table '${message.tableName}'."
        }

        selectedTable.cacheAllRows()
    }


    private fun Client.uncacheRows(message: CapnProto.UncacheRows.Reader) {

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString().toLowerCase()]) {
            "Unable to find table '${message.tableName}'."
        }

        selectedTable.uncache(Database.Filter.fromCapnProtoReader(message.filter))
    }

    private fun Client.uncacheTable(message: CapnProto.UncacheTable.Reader) {

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString().toLowerCase()]) {
            "Unable to find table '${message.tableName}'."
        }

        selectedTable.uncacheAllRows()
    }

    private fun Client.deleteTable(message: CapnProto.DeleteTable.Reader) {

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        selectedDatabase.deleteTable(message.tableName.toString())
    }


    companion object {

        const val DEBUG = true

    }

}