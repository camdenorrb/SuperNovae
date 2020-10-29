package dev.twelveoclock.supernovae.net

import dev.twelveoclock.supernovae.proto.CapnProto
import kotlin.reflect.KProperty1
import me.camdenorrb.netlius.net.Client as NetClient

class Client internal constructor(val netClient: NetClient) {

    suspend fun sendMessage(message: CapnProto.Message.Reader) {
        message.data.
        // TODO: Send size + CapnProto message
        message.
    }

    suspend fun readMessage(): CapnProto.Message {

    }


    data class Filter<T, R>(
        val property: KProperty1<T, R>,
        val check: CapnProto.Check,
        val compareTo: R
    ) {

        companion object {

            // Equals
            fun <T, R> eq(property: KProperty1<T, R>, value: R): Filter<T, R> {
                return Filter(property, CapnProto.Check.EQUAL, value)
            }


            // Lesser than
            fun <T, R> lt(property: KProperty1<T, R>, value: R): Filter<T, R> {
                return Filter(property, CapnProto.Check.LESSER_THAN, value)
            }

            // Greater than
            fun <T, R> gt(property: KProperty1<T, R>, value: R): Filter<T, R> {
                return Filter(property, CapnProto.Check.GREATER_THAN, value)
            }


            // Lesser than or equals
            fun <T, R> lte(property: KProperty1<T, R>, value: R): Filter<T, R> {
                return Filter(property, CapnProto.Check.LESSER_THAN_OR_EQUAL, value)
            }

            // Greater than or equals
            fun <T, R> gte(property: KProperty1<T, R>, value: R): Filter<T, R> {
                return Filter(property, CapnProto.Check.GREATER_THAN_OR_EQUAL, value)
            }

        }

    }


}