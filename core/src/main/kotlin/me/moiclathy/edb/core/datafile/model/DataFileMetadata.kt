package me.moiclathy.edb.core.datafile.model

import java.math.BigDecimal

sealed interface DataFileMetadataValue {
    data class StringValue(val value: String) : DataFileMetadataValue
    class NumberValue(val value: BigDecimal) : DataFileMetadataValue {
        override fun equals(other: Any?): Boolean =
            other is NumberValue && value.compareTo(other.value) == 0

        override fun hashCode(): Int = value.stripTrailingZeros().hashCode()

        override fun toString(): String = "NumberValue(value=$value)"
    }
    data class BooleanValue(val value: Boolean) : DataFileMetadataValue
    data class ObjectValue(val values: Map<String, DataFileMetadataValue>) : DataFileMetadataValue {
        init {
            require(values.keys.none { it.isBlank() }) { "Metadata object keys must not be blank." }
        }
    }

    data class ArrayValue(val values: List<DataFileMetadataValue>) : DataFileMetadataValue
    data object NullValue : DataFileMetadataValue
}

data class DataFileMetadata(
    val values: Map<String, DataFileMetadataValue> = emptyMap()
) {
    init {
        require(values.keys.none { it.isBlank() }) { "Metadata keys must not be blank." }
    }

    companion object {
        val EMPTY = DataFileMetadata()
    }
}
