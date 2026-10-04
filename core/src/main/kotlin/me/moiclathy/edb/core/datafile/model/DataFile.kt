package me.moiclathy.edb.core.datafile.model

import java.time.Instant

enum class DataFileFormat {
    CSV,
    TSV,
    XLSX,
    JSON,
    HDF5,
    MAT,
    VIDEO,
    IMAGE,
    PDF,
    BINARY,
    UNKNOWN
}

enum class DataFileStatus {
    REGISTERED,
    AVAILABLE,
    PROCESSING,
    FAILED,
    DELETED
}

enum class DataFileKind {
    RAW_DATA,
    DERIVED_DATA,
    ANALYSIS_RESULT,
    REPORT,
    AUXILIARY
}

enum class DataFileOrigin {
    UPLOAD,
    CONVERSION,
    ANALYSIS,
    REPORT_GENERATION,
    EXTERNAL_IMPORT
}

data class DataFileStorageReference(
    val provider: String,
    val key: String
) {
    init {
        require(provider.isNotBlank()) { "Storage provider must not be blank." }
        require(key.isNotBlank()) { "Storage key must not be blank." }
    }
}

data class DataFileProvenance(
    val origin: DataFileOrigin,
    val parentDataFileIds: Set<Int> = emptySet(),
    val producingTaskId: Int? = null,
    val externalSystem: String? = null
) {
    init {
        require(parentDataFileIds.all { it > 0 }) { "Parent data file ids must be positive." }
        require(producingTaskId == null || producingTaskId > 0) {
            "Producing task id must be positive."
        }
        require(externalSystem == null || externalSystem.isNotBlank()) {
            "External system must not be blank."
        }
    }
}

data class DataFile(
    val id: Int? = null,
    val projectId: Int,
    val createdByUserId: Int,
    val kind: DataFileKind,
    val format: DataFileFormat,
    val status: DataFileStatus,
    val originalFileName: String,
    val mediaType: String? = null,
    val sizeBytes: Long? = null,
    val sha256: String? = null,
    val storage: DataFileStorageReference? = null,
    val provenance: DataFileProvenance,
    val metadata: DataFileMetadata = DataFileMetadata.EMPTY,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt
) {
    init {
        require(id == null || id > 0) { "Data file id must be positive." }
        require(projectId > 0) { "Project id must be positive." }
        require(createdByUserId > 0) { "Data file creator user id must be positive." }
        require(originalFileName.isNotBlank()) { "Original file name must not be blank." }
        require('/' !in originalFileName && '\\' !in originalFileName) {
            "Original file name must not contain a path."
        }
        require(mediaType == null || mediaType.isNotBlank()) { "Media type must not be blank." }
        require(sizeBytes == null || sizeBytes >= 0) { "Data file size must not be negative." }
        require(sha256 == null || SHA_256.matches(sha256)) {
            "SHA-256 must contain exactly 64 hexadecimal characters."
        }
        require(!updatedAt.isBefore(createdAt)) { "Updated time must not precede creation time." }
        require(id == null || id !in provenance.parentDataFileIds) {
            "A data file cannot be derived from itself."
        }
    }

    companion object {
        private val SHA_256 = Regex("^[0-9a-fA-F]{64}$")
    }
}
