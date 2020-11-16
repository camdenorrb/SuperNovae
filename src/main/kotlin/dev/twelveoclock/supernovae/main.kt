package dev.twelveoclock.supernovae

import dev.twelveoclock.supernovae.proto.CapnProto
import org.capnproto.MessageReader
import org.capnproto.ReaderOptions

// This should be used for starting the server as a program
fun main(args: Array<String>) {

    val message = CapnProto.SelectKey.factory.build {
        setTableName("Meow")
        setKeyColumnValue("Meow")
    }

    MessageReader(message.segmentsForOutput, ReaderOptions.DEFAULT_READER_OPTIONS)


}