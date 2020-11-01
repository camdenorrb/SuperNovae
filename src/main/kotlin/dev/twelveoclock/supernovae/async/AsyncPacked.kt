package dev.twelveoclock.supernovae.async

import me.camdenorrb.netlius.net.Client
import java.nio.ByteBuffer

// A conversion from CapnProto's implementation that is async
// https://capnproto.org/encoding.html
// Packed is a simple algorithm that deflates 0's
object AsyncPacked {

    suspend fun compress(byteBuffer: ByteBuffer) {

        val length = byteBuffer.remaining()
        val slowBuffer = ByteBuffer.allocate(20)

        var inPtr = byteBuffer.position()
        val inEnd = inPtr + length

        while (inPtr < inEnd) {

            if (byteBuffer.remaining() < 10) {
                //# Oops, we're out of space. We need at least 10
                //# bytes for the fast path, since we don't
                //# bounds-check on every byte.
                if (out === slowBuffer) {
                    val oldLimit = out.limit()
                    out.limit(out.position())
                    out.rewind()
                    this.inner.write(out)
                    out.limit(oldLimit)
                }
                out = slowBuffer
                out.rewind()
            }

            val tagPos = out.position()
            out.position(tagPos + 1)
            var curByte: Byte = inBuf.get(inPtr)


            val bit0 = if (curByte.toInt() != 0) 1.toByte() else 0.toByte()
            out.put(curByte)
            out.position(out.position() + bit0 - 1)
            inPtr += 1
            curByte = inBuf.get(inPtr)
            val bit1 = if (curByte.toInt() != 0) 1.toByte() else 0.toByte()
            out.put(curByte)
            out.position(out.position() + bit1 - 1)
            inPtr += 1
            curByte = inBuf.get(inPtr)
            val bit2 = if (curByte.toInt() != 0) 1.toByte() else 0.toByte()
            out.put(curByte)
            out.position(out.position() + bit2 - 1)
            inPtr += 1
            curByte = inBuf.get(inPtr)
            val bit3 = if (curByte.toInt() != 0) 1.toByte() else 0.toByte()
            out.put(curByte)
            out.position(out.position() + bit3 - 1)
            inPtr += 1
            curByte = inBuf.get(inPtr)
            val bit4 = if (curByte.toInt() != 0) 1.toByte() else 0.toByte()
            out.put(curByte)
            out.position(out.position() + bit4 - 1)
            inPtr += 1
            curByte = inBuf.get(inPtr)
            val bit5 = if (curByte.toInt() != 0) 1.toByte() else 0.toByte()
            out.put(curByte)
            out.position(out.position() + bit5 - 1)
            inPtr += 1
            curByte = inBuf.get(inPtr)
            val bit6 = if (curByte.toInt() != 0) 1.toByte() else 0.toByte()
            out.put(curByte)
            out.position(out.position() + bit6 - 1)
            inPtr += 1
            curByte = inBuf.get(inPtr)
            val bit7 = if (curByte.toInt() != 0) 1.toByte() else 0.toByte()
            out.put(curByte)
            out.position(out.position() + bit7 - 1)
            inPtr += 1
            val tag = (bit0 shl 0 or (bit1 shl 1) or (bit2 shl 2) or (bit3 shl 3) or
                    (bit4 shl 4) or (bit5 shl 5) or (bit6 shl 6) or (bit7 shl 7))
            out.put(tagPos, tag)
            if (tag.toInt() == 0) {
                //# An all-zero word is followed by a count of
                //# consecutive zero words (not including the first
                //# one).
                val runStart = inPtr
                var limit = inEnd
                if (limit - inPtr > 255 * 8) {
                    limit = inPtr + 255 * 8
                }
                while (inPtr < limit && inBuf.getLong(inPtr) == 0L) {
                    inPtr += 8
                }
                out.put(((inPtr - runStart) / 8).toByte())
            } else if (tag == 0xff.toByte()) {
                //# An all-nonzero word is followed by a count of
                //# consecutive uncompressed words, followed by the
                //# uncompressed words themselves.

                //# Count the number of consecutive words in the input
                //# which have no more than a single zero-byte. We look
                //# for at least two zeros because that's the point
                //# where our compression scheme becomes a net win.
                val runStart = inPtr
                var limit = inEnd
                if (limit - inPtr > 255 * 8) {
                    limit = inPtr + 255 * 8
                }
                while (inPtr < limit) {
                    var c: Byte = 0
                    for (ii in 0..7) {
                        (c += (if (inBuf.get(inPtr).toInt() == 0) 1 else 0).toByte()).toByte()
                        inPtr += 1
                    }
                    if (c >= 2) {
                        //# Un-read the word with multiple zeros, since
                        //# we'll want to compress that one.
                        inPtr -= 8
                        break
                    }
                }
                val count = inPtr - runStart
                out.put((count / 8).toByte())
                if (count <= out.remaining()) {
                    //# There's enough space to memcpy.
                    inBuf.position(runStart)
                    val slice: ByteBuffer = inBuf.slice()
                    slice.limit(count)
                    out.put(slice)
                } else {
                    //# Input overruns the output buffer. We'll give it
                    //# to the output stream in one chunk and let it
                    //# decide what to do.
                    if (out === slowBuffer) {
                        val oldLimit = out.limit()
                        out.limit(out.position())
                        out.rewind()
                        this.inner.write(out)
                        out.limit(oldLimit)
                    }
                    inBuf.position(runStart)
                    val slice: ByteBuffer = inBuf.slice()
                    slice.limit(count)
                    while (slice.hasRemaining()) {
                        this.inner.write(slice)
                    }
                    out = this.inner.getWriteBuffer()
                }
            }
        }

        if (out === slowBuffer) {
            out.limit(out.position())
            out.rewind()
            this.inner.write(out)
        }

        inBuf.position(inPtr)
        return length
    }

    suspend fun read(client: Client) {

    }

}