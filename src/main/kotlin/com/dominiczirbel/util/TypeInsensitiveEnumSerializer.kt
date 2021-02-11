package com.dominiczirbel.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.reflect.KClass

/**
 * A [KSerializer] serializes and deserializes [Enum]s based on their [Enum.name], ignoring case.
 *
 * The default serializer requires that the decoded value matches the enum value's name (or its SerialName) exactly, and
 * so it cannot handle cases where the JSON value may be sometimes uppercase and sometimes lowercase.
 */
abstract class TypeInsensitiveEnumSerializer<E : Enum<E>>(private val enumClass: KClass<E>) : KSerializer<E> {
    override val descriptor = PrimitiveSerialDescriptor(enumClass.simpleName.orEmpty(), PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: E) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): E {
        return java.lang.Enum.valueOf(enumClass.java, decoder.decodeString().toUpperCase())
    }
}
