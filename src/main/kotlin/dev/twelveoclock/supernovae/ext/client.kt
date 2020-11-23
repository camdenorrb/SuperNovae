package dev.twelveoclock.supernovae.ext

import dev.twelveoclock.supernovae.api.Database
import dev.twelveoclock.supernovae.async.ClientCapnProto
import dev.twelveoclock.supernovae.proto.CapnProto
import me.camdenorrb.netlius.net.Client
import org.capnproto.MessageBuilder
import org.capnproto.ReaderOptions

suspend fun Client.sendNovaeMessage(message: MessageBuilder) {

    /*
    Files.newByteChannel(Paths.get("output1.bin"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE).use { outputChannel ->
        org.capnproto.Serialize.write(
            outputChannel,
            message
        )
    }*/
    /*
    Files.createDirectories(Paths.get("KatData"))
    val bp = Paths.get("KatData", "output2.bin" + UUID.randomUUID());
    Files.newByteChannel(bp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE).use { outputChannel ->
        org.capnproto.SerializePacked.writeToUnbuffered(
            outputChannel,
            message
        )
    }

    val bytes = Files.readAllBytes(bp)
    //val output1Size = Files.size(Paths.get("output1.bin"))
    queueAndFlush(Packet().int(bytes.size).bytes(bytes))
    */
    ClientCapnProto.push(this, message)
}

suspend fun Client.readNovaeMessage(): CapnProto.Message.Reader {

    /*
    Files.createDirectories(Paths.get("KatData"))
    val bytes = suspendReadBytes(suspendReadInt())
    val bp = Paths.get("KatData", "input2.bin" + UUID.randomUUID());
    Files.newByteChannel(bp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE).use { outputChannel ->
        outputChannel.write(ByteBuffer.wrap(bytes))
    }
    Files.newByteChannel(bp, StandardOpenOption.READ).use { inputStream ->

        val message = SerializePacked.readFromUnbuffered(
            inputStream,
        )

        return message.getRoot(CapnProto.Message.factory)
    }
    */


    return ClientCapnProto.pull(this, ReaderOptions.DEFAULT_READER_OPTIONS)
        .getRoot(CapnProto.Message.factory)

}

suspend fun Client.sendCreateDB(dbName: String) {

    val message = CapnProto.Message.factory.build { builder ->
        builder.initCreateDb().apply {
            setDatabaseName(dbName)
        }
    }

    sendNovaeMessage(message)
}

suspend fun Client.sendCreateTable(tableName: String, keyColumn: String, shouldCacheAll: Boolean = false) {

    val message = CapnProto.Message.factory.build { builder ->
        builder.initCreateTable().apply {
            setKeyColumn(keyColumn)
            setTableName(tableName)
            setShouldCacheAll(shouldCacheAll)
        }
    }

    sendNovaeMessage(message)
}

suspend fun Client.sendDeleteDB(dbName: String) {

    val message = CapnProto.Message.factory.build { builder ->
        builder.initDeleteDb().apply {
            setDatabaseName(dbName)
        }
    }

    sendNovaeMessage(message)
}

suspend fun Client.sendSelectDB(dbName: String) {

    val message = CapnProto.Message.factory.build { builder ->
        builder.initSelectDb().apply {
            setDatabaseName(dbName)
        }
    }

    sendNovaeMessage(message)
}

suspend fun Client.sendSelectRows(filters: List<Database.Filter>, tableName: String, onlyCheckCache: Boolean = false, loadIntoCache: Boolean = false, amountOfRows: Int = 0): List<CapnProto.SelectRowResponse.Reader> {

    val message = CapnProto.Message.factory.build { builder ->
        builder.initSelectRows().apply {

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

    return (1..suspendReadInt()).map {
        readNovaeMessage().selectRowResponse
    }
}

suspend fun Client.sendDeleteRow(tableName: String, amountOfRows: Int = 0) {

    val message = CapnProto.Message.factory.build { builder ->
        builder.initDeleteRows().apply {

            val filterStructList = initFilters(filters.size())

            filters.forEachIndexed { index, filter ->
                filterStructList.setWithCaveats(CapnProto.Filter.factory, index, filter.asReader())
            }

            setTableName(tableName)
            setAmountOfRows(amountOfRows)
        }
    }

    sendNovaeMessage(message)
}

suspend fun Client.sendInsertRow(tableName: String, row: String, shouldCache: Boolean = false) {

    val message = CapnProto.Message.factory.build { builder ->
        builder.initInsertRow().apply {
            setTableName(tableName)
            setRow(row)
            setShouldCache(shouldCache)
        }.asReader()
    }

    sendNovaeMessage(message)
}

suspend fun Client.sendUpdateRows(tableName: String, columnName: String, value: String, filter: Database.Filter, amountOfRows: Int, onlyCheckCache: Boolean = false) {

    val message = CapnProto.Message.factory.build { builder ->
        builder.initUpdateRows().apply {
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

suspend fun Client.sendCacheRows(tableName: String, filter: Database.Filter, onlyCheckCache: Boolean) {

    val message = CapnProto.Message.factory.build { builder ->
        builder.initCacheRows().apply {
            setTableName(tableName)
            setFilter(filter.toCapnProtoReader())
            setOnlyCheckCache(onlyCheckCache)
        }.asReader()
    }

    sendNovaeMessage(message)
}

suspend fun Client.sendCacheTable(tableName: String) {

    val message = CapnProto.Message.factory.build { builder ->
        builder.initCacheTable().apply {
            setTableName(tableName)
        }.asReader()
    }

    sendNovaeMessage(message)
}

suspend fun Client.sendSelectTable(tableName: String): CapnProto.SelectTableResponse.Reader {

    val message = CapnProto.Message.factory.build { builder ->
        builder.initSelectTable().apply {
            setTableName(tableName)
        }.asReader()
    }

    sendNovaeMessage(message)

    return readNovaeMessage().selectTableResponse
}

suspend fun Client.sendUnloadTable(tableName: String) {

    val message = CapnProto.Message.factory.build { builder ->
        builder.initUncacheTable().apply {
            setTableName(tableName)
        }.asReader()
    }

    sendNovaeMessage(message)
}

suspend fun Client.sendDeleteTable(tableName: String) {

    val message = CapnProto.Message.factory.build { builder ->
        builder.initDeleteTable().apply {
            setTableName(tableName)
        }.asReader()
    }

    sendNovaeMessage(message)
}