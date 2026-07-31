package org.edranor.leverframe.config

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A custom kotlinx.serialization serializer that gracefully handles unknown enum values
 * by falling back to a default value instead of throwing an exception.
 * Particularly useful for backward compatibility when loading older configurations.
 */
open class EnumFallbackSerializer<T : Enum<T>>(
    private val fallback: T,
    private val enumEntries: List<T>
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("EnumFallbackSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): T {
        val stringValue = decoder.decodeString()
        return enumEntries.find { it.name == stringValue } ?: fallback
    }
}

/** Serializer for TargetType, defaulting to LEVER */
object TargetTypeSerializer : EnumFallbackSerializer<org.edranor.leverframe.domain.engine.TargetType>(org.edranor.leverframe.domain.engine.TargetType.LEVER, org.edranor.leverframe.domain.engine.TargetType.entries)
/** Serializer for LeverType, defaulting to SPARE */
object LeverTypeSerializer : EnumFallbackSerializer<org.edranor.leverframe.domain.engine.LeverType>(org.edranor.leverframe.domain.engine.LeverType.SPARE, org.edranor.leverframe.domain.engine.LeverType.entries)
/** Serializer for RestoreOverride, defaulting to DEFAULT */
object RestoreOverrideSerializer : EnumFallbackSerializer<org.edranor.leverframe.domain.engine.RestoreOverride>(org.edranor.leverframe.domain.engine.RestoreOverride.DEFAULT, org.edranor.leverframe.domain.engine.RestoreOverride.entries)
/** Serializer for BlockMode, defaulting to LOCAL_ONLY */
object BlockModeSerializer : EnumFallbackSerializer<org.edranor.leverframe.domain.engine.BlockMode>(org.edranor.leverframe.domain.engine.BlockMode.LOCAL_ONLY, org.edranor.leverframe.domain.engine.BlockMode.entries)
/** Serializer for NxButtonType, defaulting to NONE */
object NxButtonTypeSerializer : EnumFallbackSerializer<org.edranor.leverframe.domain.engine.NxButtonType>(org.edranor.leverframe.domain.engine.NxButtonType.NONE, org.edranor.leverframe.domain.engine.NxButtonType.entries)
/** Serializer for NxButtonPlacement, defaulting to DEFAULT */
object NxButtonPlacementSerializer : EnumFallbackSerializer<org.edranor.leverframe.domain.engine.NxButtonPlacement>(org.edranor.leverframe.domain.engine.NxButtonPlacement.DEFAULT, org.edranor.leverframe.domain.engine.NxButtonPlacement.entries)
/** Serializer for NxButtonColor, defaulting to BLACK */
object NxButtonColorSerializer : EnumFallbackSerializer<org.edranor.leverframe.domain.engine.NxButtonColor>(org.edranor.leverframe.domain.engine.NxButtonColor.BLACK, org.edranor.leverframe.domain.engine.NxButtonColor.entries)
/** Serializer for SchematicElementType, defaulting to STRAIGHT_H */
object SchematicElementTypeSerializer : EnumFallbackSerializer<org.edranor.leverframe.domain.engine.SchematicElementType>(org.edranor.leverframe.domain.engine.SchematicElementType.STRAIGHT_H, org.edranor.leverframe.domain.engine.SchematicElementType.entries)
/** Serializer for LandscapeSchematicPosition, defaulting to SIDE_BY_SIDE */
object LandscapeSchematicPositionSerializer : EnumFallbackSerializer<org.edranor.leverframe.domain.engine.LandscapeSchematicPosition>(org.edranor.leverframe.domain.engine.LandscapeSchematicPosition.SIDE_BY_SIDE, org.edranor.leverframe.domain.engine.LandscapeSchematicPosition.entries)
