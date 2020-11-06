package dev.twelveoclock.supernovae.net

import dev.twelveoclock.supernovae.api.Database
import dev.twelveoclock.supernovae.ext.filter
import dev.twelveoclock.supernovae.ext.readNovaeMessage
import dev.twelveoclock.supernovae.ext.sendNovaeMessage
import dev.twelveoclock.supernovae.proto.CapnProto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

        // TODO: A selected database variable

        while (client.channel.isOpen) {

            val message = client.readNovaeMessage()

            when (message.which()!!) {
                CapnProto.Message.Which.CREATE_DB -> client.createDB(message.createDb)
                CapnProto.Message.Which.CREATE_TABLE -> client.createTable(message.createTable)
                CapnProto.Message.Which.SELECT_DB -> client.selectDB(message.selectDb)
                CapnProto.Message.Which.SELECT -> client.select(message.select)
                CapnProto.Message.Which.SELECT_FIRST -> client.selectFirst(message.selectFirst)
                CapnProto.Message.Which.SELECT_KEY -> client.selectKey(message.selectKey)
                CapnProto.Message.Which.SELECT_N -> client.selectN(message.selectN)
                CapnProto.Message.Which.INSERT -> client.insert(message.insert)
                CapnProto.Message.Which.UPDATE -> client.update(message.update)
                CapnProto.Message.Which.SELECT_RESPONSE -> error("Unexpected select response.")
                CapnProto.Message.Which.LOAD_ROWS -> client.loadRows(message.loadRows)
                CapnProto.Message.Which.LOAD_TABLE -> client.loadTable(message.loadTable)
                CapnProto.Message.Which.UNLOAD_ROWS -> client.unloadRows(message.unloadRows)
                CapnProto.Message.Which.UNLOAD_TABLE -> client.unloadTable(message.unloadTable)
                CapnProto.Message.Which._NOT_IN_SCHEMA -> error("Unknown message received.")
            }
        }
    }


    private fun Client.createDB(message: CapnProto.CreateDB.Reader) {
        val database = Database(File(serverFolder, message.databaseName.toString()))
        databases[message.databaseName.toString().toLowerCase()] = database
    }

    private fun Client.createTable(message: CapnProto.CreateTable.Reader) {

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to create a table."
        }

        selectedDatabase.createTable(message.tableName.toString(), message.shouldCacheAll, message.keyColumn.toString())
    }


    private fun Client.selectDB(message: CapnProto.SelectDB.Reader) {
        selectedDatabase[this] = databases.getValue(message.databaseName.toString().toLowerCase())
    }

    private suspend fun Client.select(message: CapnProto.Select.Reader) {

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString().toLowerCase()]) {
            "Unable to find table '${message.tableName}'."
        }

        var selectedRows: List<JsonObject>? = null

        message.filters.forEach {
            val filter = Database.Filter.fromCapnProto(it)
            selectedRows = selectedRows?.filter(filter) ?: selectedTable.filter(filter)
        }

        queueAndFlush(Packet().int(selectedRows?.size ?: 0))

        selectedRows?.forEach {

            val builder = MessageBuilder()
            val selectResponse = builder.initRoot(CapnProto.SelectResponse.factory)

            selectResponse.setRow(it.toString())
            sendNovaeMessage(builder)
        }
    }

    private suspend fun Client.selectFirst(message: CapnProto.SelectFirst.Reader) {

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString().toLowerCase()]) {
            "Unable to find table '${message.tableName}'."
        }

        var selectedRows: List<JsonObject>? = null

        message.filters.forEach {
            val filter = Database.Filter.fromCapnProto(it)
            selectedRows = selectedRows?.filter(filter) ?: selectedTable.filter(filter, 1, message.onlyCheckCache)
        }

        queueAndFlush(Packet().int(if (selectedRows?.isEmpty() == true) 0 else 1))

        val row = selectedRows?.first()

        val builder = MessageBuilder()
        val selectResponse = builder.initRoot(CapnProto.SelectResponse.factory)

        selectResponse.setRow(row.toString())
        sendNovaeMessage(builder)
    }

    private suspend fun Client.selectKey(message: CapnProto.SelectKey.Reader) {

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString().toLowerCase()]) {
            "Unable to find table '${message.tableName}'."
        }

        val keyValuePrimitive = JsonPrimitive(message.keyColumnValue.toString())
        val filter = Database.Filter(selectedTable.keyColumn, CapnProto.Check.EQUAL, keyValuePrimitive)
        val matchedRow = selectedTable.filter(filter, 1).firstOrNull() ?: return

        queueAndFlush(Packet().int(1))

        val builder = MessageBuilder()
        val selectResponse = builder.initRoot(CapnProto.SelectResponse.factory)

        selectResponse.setRow(matchedRow.toString())
        sendNovaeMessage(builder)
    }

    private suspend fun Client.selectN(message: CapnProto.SelectN.Reader) {

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString().toLowerCase()]) {
            "Unable to find table '${message.tableName}'."
        }

        var selectedRows: List<JsonObject>? = null

        message.filters.forEach {
            val filter = Database.Filter.fromCapnProto(it)
            selectedRows = selectedRows?.filter(filter) ?: selectedTable.filter(filter, message.amountOfRows.toInt(), message.onlyCheckCache)
        }

        queueAndFlush(Packet().int(selectedRows?.size ?: 0))

        selectedRows?.forEach {

            val builder = MessageBuilder()
            val selectResponse = builder.initRoot(CapnProto.SelectResponse.factory)

            selectResponse.setRow(it.toString())
            sendNovaeMessage(builder)
        }
    }


    private fun Client.insert(message: CapnProto.Insert.Reader) {

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
    private fun Client.update(message: CapnProto.Update.Reader) {

        val selectedDatabase = checkNotNull(selectedDatabase[this]) {
            "You must select a database in order to select rows in a table."
        }

        val selectedTable = checkNotNull(selectedDatabase.tables[message.tableName.toString().toLowerCase()]) {
            "Unable to find table '${message.tableName}'."
        }

        selectedTable.update(Database.Filter.fromCapnProto(message.filter), message.columnName.toString(), message.value.toString(), message.amountOfRows.takeIf { it != 0 }, message.onlyCheckCache)
    }


    private suspend fun Client.loadRows(message: CapnProto.LoadRows.Reader) {

    }

    private suspend fun Client.loadTable(message: CapnProto.LoadTable.Reader) {

    }


    private suspend fun Client.unloadRows(message: CapnProto.UnloadRows.Reader) {

    }

    private suspend fun Client.unloadTable(message: CapnProto.UnloadTable.Reader) {

    }

}