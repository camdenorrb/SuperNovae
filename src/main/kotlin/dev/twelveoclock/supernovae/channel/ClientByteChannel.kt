package dev.twelveoclock.supernovae.channel

import kotlinx.coroutines.runBlocking
import me.camdenorrb.netlius.net.Client
import me.camdenorrb.netlius.net.Packet
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel

class ClientByteChannel(val client: Client) : WritableByteChannel, ReadableByteChannel {

    override fun write(src: ByteBuffer): Int {

        val remaining = src.remaining()

        runBlocking {
            client.queueAndFlush(Packet().byteBuffer(src))
        }

        return remaining
    }

    override fun read(dst: ByteBuffer): Int {

        val remaining = dst.remaining()

        runBlocking {
            client.readTo(dst.remaining(), dst, 0)
            println(dst)
        }

        println("Here")

        return remaining
    }

    override fun isOpen(): Boolean {
        return client.channel.isOpen
    }

    override fun close() {
        // Do nothing
    }

}