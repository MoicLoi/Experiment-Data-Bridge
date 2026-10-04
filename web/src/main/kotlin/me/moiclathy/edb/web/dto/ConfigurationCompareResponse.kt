package me.moiclathy.edb.web.dto

import me.moiclathy.edb.core.config.compare.model.ConfigurationCompareChange
import me.moiclathy.edb.core.config.compare.model.ConfigurationCompareResult

data class ConfigurationCompareResponse(

    val sourceSnapshotId: Long,

    val targetSnapshotId: Long,

    val sourceVersion: Int,

    val targetVersion: Int,

    val identical: Boolean,

    val changes: List<ConfigurationCompareChangeResponse>
) {

    companion object {

        fun from(
            result: ConfigurationCompareResult
        ): ConfigurationCompareResponse {

            return ConfigurationCompareResponse(
                sourceSnapshotId =
                    result.sourceSnapshot.id!!,

                targetSnapshotId =
                    result.targetSnapshot.id!!,

                sourceVersion =
                    result.sourceSnapshot.version,

                targetVersion =
                    result.targetSnapshot.version,

                identical =
                    result.isIdentical,

                changes =
                    result.changes.map(
                        ConfigurationCompareChangeResponse::from
                    )
            )
        }
    }
}

data class ConfigurationCompareChangeResponse(

    val path: String,

    val type: String,

    val sourceValue: Any?,

    val targetValue: Any?
) {

    companion object {

        fun from(
            change: ConfigurationCompareChange
        ): ConfigurationCompareChangeResponse {

            return ConfigurationCompareChangeResponse(
                path = change.path,
                type = change.type.name,
                sourceValue = change.sourceValue,
                targetValue = change.targetValue
            )
        }
    }
}