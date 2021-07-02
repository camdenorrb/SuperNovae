package dev.twelveoclock.supernovae.net

import dev.twelveoclock.supernovae.api.FileDatabase
import dev.twelveoclock.supernovae.ext.*
import dev.twelveoclock.supernovae.protocol.ProtocolMessage
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import kotlin.reflect.KProperty1
