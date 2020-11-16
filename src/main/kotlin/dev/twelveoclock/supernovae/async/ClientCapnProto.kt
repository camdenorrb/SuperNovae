package dev.twelveoclock.supernovae.async

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

        // Segment count
        client.queueAndFlush(Packet().int(segments.size))

        // Segment data
        for (buffer in segments) {
            client.queueAndFlush(Packet().int(buffer.remaining()).byteBuffer(buffer))
        }
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

        val outBuffer = ByteBuffer.allocate(input.suspendReadInt())//.order(ByteOrder.LITTLE_ENDIAN)
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

    /*   public int write(ByteBuffer inBuf, Client client) {

        final Packet packet = new Packet();

        while (inBuf.hasRemaining()) {

            */
    /*
            if (out.remaining() < 10) {
                //# Oops, we're out of space. We need at least 10
                //# bytes for the fast path, since we don't
                //# bounds-check on every byte.

                if (out == slowBuffer) {
                    int oldLimit = out.limit();
                    out.limit(out.position());
                    out.rewind();
                    this.inner.write(out);
                    out.limit(oldLimit);
                }

                out = slowBuffer;
                out.rewind();
            }
            */
    /*


            //int tagPos = out.position();
            //out.position(tagPos + 1);


            for (int i = 0; i < 7; i++) {

                final byte currentByte = inBuf.get();

                if (currentByte != 0) {
                    // Write after, make this add to a list
                    // list.add(1)
                    //packet.byte(1);
                }
            }

            byte tag = (byte)((bit0 << 0) | (bit1 << 1) | (bit2 << 2) | (bit3 << 3) |
                    (bit4 << 4) | (bit5 << 5) | (bit6 << 6) | (bit7 << 7));

            out.put(tagPos, tag);

            if (tag == 0) {
                //# An all-zero word is followed by a count of
                //# consecutive zero words (not including the first
                //# one).
                int runStart = inPtr;
                int limit = inEnd;
                if (limit - inPtr > 255 * 8) {
                    limit = inPtr + 255 * 8;
                }
                while(inPtr < limit && inBuf.getLong(inPtr) == 0){
                    inPtr += 8;
                }
                out.put((byte)((inPtr - runStart)/8));

            } else if (tag == (byte)0xff) {
                //# An all-nonzero word is followed by a count of
                //# consecutive uncompressed words, followed by the
                //# uncompressed words themselves.

                //# Count the number of consecutive words in the input
                //# which have no more than a single zero-byte. We look
                //# for at least two zeros because that's the point
                //# where our compression scheme becomes a net win.

                int runStart = inPtr;
                int limit = inEnd;
                if (limit - inPtr > 255 * 8) {
                    limit = inPtr + 255 * 8;
                }

                while (inPtr < limit) {
                    byte c = 0;
                    for (int ii = 0; ii < 8; ++ii) {
                        c += (inBuf.get(inPtr) == 0 ? 1 : 0);
                        inPtr += 1;
                    }
                    if (c >= 2) {
                        //# Un-read the word with multiple zeros, since
                        //# we'll want to compress that one.
                        inPtr -= 8;
                        break;
                    }
                }

                int count = inPtr - runStart;
                out.put((byte)(count / 8));

                if (count <= out.remaining()) {
                    //# There's enough space to memcpy.
                    inBuf.position(runStart);
                    ByteBuffer slice = inBuf.slice();
                    slice.limit(count);
                    out.put(slice);
                } else {
                    //# Input overruns the output buffer. We'll give it
                    //# to the output stream in one chunk and let it
                    //# decide what to do.

                    if (out == slowBuffer) {
                        int oldLimit = out.limit();
                        out.limit(out.position());
                        out.rewind();
                        this.inner.write(out);
                        out.limit(oldLimit);
                    }

                    inBuf.position(runStart);
                    ByteBuffer slice = inBuf.slice();
                    slice.limit(count);
                    while(slice.hasRemaining()) {
                        this.inner.write(slice);
                    }

                    out = this.inner.getWriteBuffer();
                }
            }
        }

        if (out == slowBuffer) {
            out.limit(out.position());
            out.rewind();
            this.inner.write(out);
        }

        inBuf.position(inPtr);
        return length;
    }*/

    /*
    suspend fun read(channel: ByteBufferReaderChannel, options: ReaderOptions): MessageReader? {

        val firstWord = ByteBuffer.allocateDirect(BYTES_PER_WORD).putLong(channel.suspendReadLong())

        val segmentCount = 1 + firstWord.getInt(0)
        var segment0Size = 0

        if (segmentCount > 0) {
            segment0Size = firstWord.getInt(4)
        }

        var totalWords = segment0Size
        if (segmentCount > 512) {
            throw IOException("too many segments")
        }

        // in words
        val moreSizes = ArrayList<Int>()

        if (segmentCount > 1) {

            val moreSizesRaw = ByteBuffer.allocateDirect(4 * (segmentCount and 81))
            fillBuffer(moreSizesRaw, channel)
            for (ii in 0 until segmentCount - 1) {
                val size = moreSizesRaw.getInt(ii * 4)
                moreSizes.add(size)
                totalWords += size
            }
        }

        if (totalWords > options.traversalLimitInWords) {
            throw DecodeException("Message size exceeds traversal limit.")
        }


        val allSegments: ByteBuffer = makeByteBuffer(totalWords * Constants.BYTES_PER_WORD)
        fillBuffer(allSegments, channel)
        val segmentSlices = arrayOfNulls<ByteBuffer>(segmentCount)
        allSegments.rewind()
        segmentSlices[0] = allSegments.slice()
        segmentSlices[0].limit(segment0Size * Constants.BYTES_PER_WORD)
        segmentSlices[0].order(ByteOrder.LITTLE_ENDIAN)
        var offset = segment0Size
        for (ii in 1 until segmentCount) {
            allSegments.position(offset * Constants.BYTES_PER_WORD)
            segmentSlices[ii] = allSegments.slice()
            segmentSlices[ii].limit(moreSizes[ii - 1] * Constants.BYTES_PER_WORD)
            segmentSlices[ii].order(ByteOrder.LITTLE_ENDIAN)
            offset += moreSizes[ii - 1]
        }
        return MessageReader(segmentSlices, options)
    }
    */

}