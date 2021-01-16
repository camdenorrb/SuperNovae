package dev.twelveoclock.supernovae.async

/*
import dev.twelveoclock.supernovae.project.BYTES_PER_WORD
import me.camdenorrb.kcommons.io.ByteBufferReaderChannel
import me.camdenorrb.netlius.net.Client
import me.camdenorrb.netlius.net.Packet
import org.capnproto.MessageBuilder
import org.capnproto.MessageReader
import org.capnproto.ReaderOptions
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.and
import kotlin.experimental.or

object ClientCapnProto {

    suspend fun pull(client: Client, options: ReaderOptions): MessageReader {

        val segmentCount = client.suspendReadInt()

        check(segmentCount <= 512) {
            "Too many segments"
        }

        val segmentSlices = Array(segmentCount) {
            ByteBuffer.wrap(client.suspendReadBytes(client.suspendReadInt())).order(ByteOrder.LITTLE_ENDIAN)
        }

        return MessageReader(segmentSlices, options)
    }

    suspend fun push(client: Client, message: MessageBuilder) {

        val segments = message.segmentsForOutput

        val packet = Packet()

        // Segment count
        packet.int(segments.size)

        // Segment data
        for (buffer in segments) {
            packet.int(buffer.remaining()).byteBuffer(buffer)
        }

        client.queueAndFlush(packet)
    }

    suspend fun pullPacked(client: Client, options: ReaderOptions): MessageReader {

        val table = unpack(client).order(ByteOrder.LITTLE_ENDIAN)
        val segmentCount = table.int + 1

        check(segmentCount <= 512) {
            "Too many segments"
        }

        // While the size is ignored, it makes the buffer word aligned
        /*
        val segmentSizes = Array(segmentCount) {
            println(table.int)
        }
        */

        val segmentSlices = Array(segmentCount) {
            unpack(client).order(ByteOrder.LITTLE_ENDIAN)
        }

        return MessageReader(segmentSlices, options)
    }

    suspend fun pushPacked(client: Client, message: MessageBuilder) {

        val segments = message.segmentsForOutput
        val tableSize = segments.size + 2 and 1.inv()
        val table = ByteBuffer.allocate(4 * tableSize).order(ByteOrder.LITTLE_ENDIAN)

        // Put segment count
        table.putInt(segments.size - 1)

        // The size of each segment in words
        // This can be removed due to my packing system
        for (segmentIndex in segments.indices) {
            table.putInt(segments[segmentIndex].limit() / BYTES_PER_WORD)
        }

        // Any padding is already zeroed.... somehow

        table.flip()
        pack(table, client)

        for (buffer in segments) {
            pack(buffer, client)
        }
    }

    // TODO: Have a ByteBufferWriterChannel
    // Returns compressed size
    suspend fun pack(byteBuffer: ByteBuffer, client: Client): Int {

        val packet = Packet()
        val startLimit = byteBuffer.limit()

        check(startLimit % 8 == 0) {
            "The byteBuffer isn't divisible by 8, making it not word aligned"
        }

        while (byteBuffer.hasRemaining()) {

            var tag = 0.toByte()

            val bytes = List(8) {
                val byte = byteBuffer.get()
                tag = tag or ((if (byte == 0.toByte()) 0 else 1) shl it).toByte()
                byte
            }

            //println("Out tag: $tag")

            packet.byte(tag)
            bytes.filter { it != 0.toByte() }.forEach(packet::byte)

            when (tag) {

                0x00.toByte() -> {

                    var emptyWordCount = 0.toUByte()

                    while (byteBuffer.hasRemaining() && byteBuffer.long == 0L && emptyWordCount < UByte.MAX_VALUE) {
                        emptyWordCount++
                    }

                    // If there was a non-empty word
                    if (byteBuffer.hasRemaining()) {
                        byteBuffer.position(byteBuffer.position() - Long.SIZE_BYTES)
                    }

                    packet.byte(emptyWordCount.toByte())
                }

                0xff.toByte() -> {

                    val uncompressedWords = mutableListOf<Long>()

                    while (byteBuffer.hasRemaining()) {

                        val word = byteBuffer.long

                        val zeroBytes = (0 until BYTES_PER_WORD).count {
                            val byte = (word shr (Byte.SIZE_BITS * it)).toByte()
                            byte == 0x00.toByte()
                        }

                        if (zeroBytes > 1) {
                            byteBuffer.position(byteBuffer.position() - BYTES_PER_WORD)
                            break
                        }

                        uncompressedWords.add(word)
                    }

                    // TODO: Use varint
                    packet.int(uncompressedWords.size)
                    uncompressedWords.forEach(packet::long)
                }

            }
        }

        val compressedSize = packet.size

        packet.prepend {
            int(startLimit)       // Original size
            //int(compressedSize) // Compressed size
        }

        client.queueAndFlush(packet)

        return compressedSize
    }

    // TODO: Replace outBuffer with a ByteBufferWriterChannel
    suspend fun unpack(input: ByteBufferReaderChannel): ByteBuffer {

        val outBuffer = ByteBuffer.allocate(input.suspendReadInt().also { println(it) })//.order(ByteOrder.LITTLE_ENDIAN)
        //val compressedSize = input.suspendReadInt()
        //val startPosition = outBuffer.position()

        while (outBuffer.hasRemaining()) {

            val tag = input.suspendReadByte()

            // Go through the bits of the tag right to left and figure out which has value and which don't
            repeat(Byte.SIZE_BITS) {

                val bit = tag and (1 shl it).toByte()

                if (bit == 0.toByte()) {
                    outBuffer.put(0)
                }
                else {
                    outBuffer.put(input.suspendReadByte())
                }
            }


            //println("In tag: $tag")

            when (tag) {

                // Followed by the bytes of the words
                0x00.toByte() -> {

                    // Convert to unsigned then times by 8 which gives us length
                    val messageLength = input.suspendReadByte().toUByte()

                    repeat(messageLength.toInt()) {
                        outBuffer.putLong(0)
                    }
                }

                0xff.toByte() -> {

                    // Convert to unsigned then times by 8 which gives us length
                    val messageLength = input.suspendReadInt() * BYTES_PER_WORD

                    input.suspendRead(messageLength) {
                        outBuffer.put(this)
                    }
                }

            }

        }

        return outBuffer.flip()
    }

}
*/
