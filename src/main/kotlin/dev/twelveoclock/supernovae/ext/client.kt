package dev.twelveoclock.supernovae.ext

import dev.twelveoclock.supernovae.api.Database
import dev.twelveoclock.supernovae.protocol.ProtocolMessage
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.protobuf.ProtoBuf
import me.camdenorrb.netlius.net.Client
import me.camdenorrb.netlius.net.Packet

suspend fun Client.suspendSendNovaeMessage(message: ProtocolMessage) {
    val byteArray = ProtoBuf.encodeToByteArray(ProtocolMessage.serializer(), message)
    queueAndFlush(Packet().int(byteArray.size).bytes(byteArray))
}

suspend fun Client.suspendReadNovaeMessage(): ProtocolMessage {
    val bytes = suspendReadBytes(suspendReadInt())
    return ProtoBuf.decodeFromByteArray(ProtocolMessage.serializer(), bytes)
}

suspend fun Client.sendCreateDB(dbName: String) {
    suspendSendNovaeMessage(ProtocolMessage.DB.Create(dbName))
}

suspend fun Client.sendCreateTable(tableName: String, keyColumn: String, shouldCacheAll: Boolean = false) {
    suspendSendNovaeMessage(ProtocolMessage.Table.Create(tableName, keyColumn, shouldCacheAll))
}

suspend fun Client.sendDeleteDB(dbName: String) {
    suspendSendNovaeMessage(ProtocolMessage.DB.Delete(dbName))
}

suspend fun Client.sendSelectDB(dbName: String) {
    suspendSendNovaeMessage(ProtocolMessage.DB.Select(dbName))
}

suspend fun Client.sendSelectAllRows(tableName: String, onlyInCache: Boolean = false) {
    suspendSendNovaeMessage(ProtocolMessage.Table.SelectAllRows(tableName, onlyInCache))
}

suspend fun Client.sendSelectRows(tableName: String, filters: List<Database.Filter>, onlyCheckCache: Boolean = false, loadIntoCache: Boolean = false, amountOfRows: Int = -1) {
    suspendSendNovaeMessage(ProtocolMessage.Table.SelectRows(tableName, filters, onlyCheckCache, loadIntoCache, amountOfRows))
}

suspend fun Client.sendDeleteRows(tableName: String, filters: List<Database.Filter>, amountOfRows: Int = -1) {
    suspendSendNovaeMessage(ProtocolMessage.Table.DeleteRows(tableName, filters, amountOfRows))
}

suspend fun Client.sendInsertRow(tableName: String, row: JsonObject, shouldCache: Boolean = false) {
    suspendSendNovaeMessage(ProtocolMessage.Table.InsertRow(tableName, row, shouldCache))
}

suspend fun Client.sendUpdateRows(tableName: String, filter: Database.Filter, columnName: String, row: JsonElement, amountOfRows: Int, onlyCheckCache: Boolean = false) {
    suspendSendNovaeMessage(ProtocolMessage.Table.UpdateRows(tableName, filter, columnName, row, amountOfRows, onlyCheckCache))
}

suspend fun Client.sendCacheRows(tableName: String, filter: Database.Filter) {
    suspendSendNovaeMessage(ProtocolMessage.Table.CacheRows(tableName, filter))
}

suspend fun Client.sendCacheTable(tableName: String) {
    suspendSendNovaeMessage(ProtocolMessage.Table.Cache(tableName))
}

suspend fun Client.sendSelectTable(tableName: String) {
    suspendSendNovaeMessage(ProtocolMessage.Table.Select(tableName))
}

suspend fun Client.sendUncacheTable(tableName: String) {
    suspendSendNovaeMessage(ProtocolMessage.Table.Uncache(tableName))
}

suspend fun Client.sendDeleteTable(tableName: String) {
    suspendSendNovaeMessage(ProtocolMessage.Table.Delete(tableName))
}

suspend fun Client.sendClearTable(tableName: String) {
    suspendSendNovaeMessage(ProtocolMessage.Table.Clear(tableName))
}

suspend fun Client.sendStopListeningToTable(tableName: String) {
    suspendSendNovaeMessage(ProtocolMessage.Table.StopListening(tableName))
}

suspend fun Client.sendListenToTable(tableName: String) {
    suspendSendNovaeMessage(ProtocolMessage.Table.StartListening(tableName))
}