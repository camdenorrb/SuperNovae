package dev.twelveoclock.supernovae.ext

import dev.twelveoclock.supernovae.proto.CapnProto
import me.camdenorrb.netlius.Netlius
import me.camdenorrb.netlius.net.Client
import me.camdenorrb.netlius.net.Packet
import org.capnproto.ArrayInputStream
import org.capnproto.ArrayOutputStream
import org.capnproto.MessageBuilder
import org.capnproto.SerializePacked

suspend fun Client.sendNovaeMessage(message: MessageBuilder) {

    byteBufferPool.take(Netlius.DEFAULT_BUFFER_SIZE) {
        SerializePacked.write(ArrayOutputStream(it), message)
        queueAndFlush(Packet().int(it.limit()).byteBuffer(it))
    }

    /*

    SerializePacked.writeToUnbuffered()
    val segments = message.segmentsForOutput
    val tableSize = segments.size + 2 and 1.inv()

    val table = ByteBuffer.allocate(4 * tableSize)
    table.order(ByteOrder.LITTLE_ENDIAN)

    table.putInt(0, segments.size - 1)

    for (i in segments.indices) {
        table.putInt(4 * (i + 1), segments[i].limit() / 8)
    }

    // Any padding is already zeroed.

    // Any padding is already zeroed.
    while (table.hasRemaining()) {
        outputChannel.write(table)
    }

    for (buffer in segments) {
        while (buffer.hasRemaining()) {
            outputChannel.write(buffer)
        }
    }


    SerializePacked.writeToUnbuffered()
    // Retrieve all segments of the message
    val outputSegments = message.segmentsForOutput

    // Send amount of segments
    queueAndFlush(Packet().int(outputSegments.size))

    outputSegments.forEach {
        // Send size of segment + segment data
        queueAndFlush(
            Packet().int(it.limit()).bytes(it.array()),
        )
    }*/
}

suspend fun Client.readNovaeMessage(): CapnProto.Message.Reader {

    return read(readInt()) {
        SerializePacked.read(ArrayInputStream(this)).getRoot(CapnProto.Message.factory)
    }

    /*
    val segments = Array<ByteBuffer>(readInt()) {
        ByteBuffer.wrap(readBytes(readInt()))
    }

    return MessageReader(segments, ReaderOptions.DEFAULT_READER_OPTIONS)
        .getRoot(CapnProto.Message.factory)
    */
}