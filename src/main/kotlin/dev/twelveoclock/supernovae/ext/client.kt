package dev.twelveoclock.supernovae.ext

import dev.twelveoclock.supernovae.api.Database
import dev.twelveoclock.supernovae.async.ClientCapnProto
import dev.twelveoclock.supernovae.proto.CapnProto
import me.camdenorrb.netlius.net.Client
import org.capnproto.MessageBuilder
import org.capnproto.ReaderOptions

suspend fun Client.sendNovaeMessage(message: MessageBuilder) {
    ClientCapnProto.push(this, message)
}

suspend fun Client.readNovaeMessage(): CapnProto.Message.Reader {

    //Packed.unpack(this, )
    //Serialize.read()
    return ClientCapnProto.pull(this, ReaderOptions.DEFAULT_READER_OPTIONS)
        .getRoot(CapnProto.Message.factory)


    //return SerializePacked.readFromUnbuffered(ClientByteChannel(this))
    //    .getRoot(CapnProto.Message.factory)


    /*
    return read(readInt()) {
        ArrayInputStream(this).use { stream ->
            SerializePacked.read(stream).getRoot(CapnProto.Message.factory)
        }
    }
    */

    /*
    val segments = Array<ByteBuffer>(readInt()) {
        ByteBuffer.wrap(readBytes(readInt()))
    }

    return MessageReader(segments, ReaderOptions.DEFAULT_READER_OPTIONS)
        .getRoot(CapnProto.Message.factory)
    */
}

suspend fun Client.sendCreateDB(dbName: String) {

    val message = CapnProto.Message.factory.build {
        initCreateDb().apply {
            setDatabaseName(dbName)
        }
    }

    sendNovaeMessage(message)
}

suspend fun Client.sendCreateTable(keyColumn: String, tableName: String, shouldCacheAll: Boolean = false) {

    val message = CapnProto.Message.factory.build {
        initCreateTable().apply {
            setKeyColumn(keyColumn)
            setTableName(tableName)
            setShouldCacheAll(shouldCacheAll)
        }
    }

    sendNovaeMessage(message)
}

suspend fun Client.sendSelectDB(dbName: String) {

    val message = CapnProto.Message.factory.build {
        initSelectDb().apply {
            setDatabaseName(dbName)
        }
    }

    sendNovaeMessage(message)
}

suspend fun Client.sendSelectRows(filters: List<Database.Filter>, tableName: String, onlyCheckCache: Boolean = false, loadIntoCache: Boolean = false, amountOfRows: Int = 0): CapnProto.SelectResponse.Reader {

    val message = CapnProto.Message.factory.build {
        initSelectRows().apply {

            val filterStructList = initFilters(filters.size)

            filters.forEachIndexed { index, filter ->
                filterStructList.setWithCaveats(CapnProto.Filter.factory, index, filter.toCapnProtoReader())
            }

            setTableName(tableName)
            setLoadIntoCache(loadIntoCache)
            setOnlyCheckCache(onlyCheckCache)
            setAmountOfRows(amountOfRows)
        }
    }

    sendNovaeMessage(message)

    return readNovaeMessage().selectResponse
}

/*
suspend fun Client.sendSelectFirst(tableName: String, filters: List<Database.Filter>, onlyCheckCache: Boolean = false, loadIntoCache: Boolean = false): CapnProto.SelectResponse.Reader {

    val message = CapnProto.Message.factory.build {
        initSelectFirst().apply {

            val filterStructList = initFilters(filters.size)

            filters.forEachIndexed { index, filter ->
                filterStructList.setWithCaveats(CapnProto.Filter.factory, index, filter.toCapnProtoReader())
            }

            setTableName(tableName)
            setOnlyCheckCache(onlyCheckCache)
            setLoadIntoCache(loadIntoCache)
        }
    }

    sendNovaeMessage(message)

    return readNovaeMessage().selectResponse
}
*/


/*
suspend fun Client.sendSelectByKey(tableName: String, keyColumnValue: String): CapnProto.SelectResponse.Reader {

    val message = CapnProto.Message.factory.build {
        initSelectRows().apply {
            setTableName(tableName)
            setKeyColumnValue(keyColumnValue)
        }
    }

    sendNovaeMessage(message)

    return readNovaeMessage().apply { println("Here: ${which()}") /*This sucks*/ }.selectResponse
}
*/


/*
suspend fun Client.sendSelectN(tableName: String, filters: List<Database.Filter>, amountOfRows: Int, onlyCheckCache: Boolean = false): CapnProto.SelectResponse.Reader {

    val message = CapnProto.Message.factory.build {
        initSelectN().apply {

            val filterStructList = initFilters(filters.size)

            filters.forEachIndexed { index, filter ->
                filterStructList.setWithCaveats(CapnProto.Filter.factory, index, filter.toCapnProtoReader())
            }

            setTableName(tableName)
            setAmountOfRows(amountOfRows)
            setOnlyCheckCache(onlyCheckCache)
        }
    }

    sendNovaeMessage(message)

    return readNovaeMessage().selectResponse
}
*/

suspend fun Client.sendInsertRow(tableName: String, row: String, shouldCache: Boolean = false) {

    val message = CapnProto.Message.factory.build {
        initInsertRow().apply {
            setTableName(tableName)
            setRow(row)
            setShouldCache(shouldCache)
        }.asReader()
    }

    sendNovaeMessage(message)
}

suspend fun Client.sendUpdateRows(tableName: String, columnName: String, value: String, filter: Database.Filter, amountOfRows: Int, onlyCheckCache: Boolean = false) {

    val message = CapnProto.Message.factory.build {
        initUpdateRows().apply {
            setTableName(tableName)
            setColumnName(columnName)
            setValue(value)
            setFilter(filter.toCapnProtoReader())
            setAmountOfRows(amountOfRows)
            setOnlyCheckCache(onlyCheckCache)
        }.asReader()
    }

    sendNovaeMessage(message)
}

suspend fun Client.sendLoadRows(tableName: String, filter: Database.Filter, onlyCheckCache: Boolean) {

    val message = CapnProto.Message.factory.build {
        initLoadRows().apply {
            setTableName(tableName)
            setFilter(filter.toCapnProtoReader())
            setOnlyCheckCache(onlyCheckCache)
        }.asReader()
    }

    sendNovaeMessage(message)
}

suspend fun Client.sendLoadTable(tableName: String) {

    val message = CapnProto.Message.factory.build {
        initLoadTable().apply {
            setTableName(tableName)
        }.asReader()
    }

    sendNovaeMessage(message)
}