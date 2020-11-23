package dev.twelveoclock.supernovae.ext

import org.capnproto.MessageBuilder
import org.capnproto.StructBuilder
import org.capnproto.StructFactory

inline fun <B : StructBuilder> StructFactory<B, *>.build(init: (builder: B) -> Unit): MessageBuilder {
    return MessageBuilder().apply {
        init(initRoot(this@build))
    }
}