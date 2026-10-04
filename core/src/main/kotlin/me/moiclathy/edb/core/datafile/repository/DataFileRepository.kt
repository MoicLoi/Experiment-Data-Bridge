package me.moiclathy.edb.core.datafile.repository

import me.moiclathy.edb.core.datafile.model.DataFile

interface DataFileRepository {
    fun save(dataFile: DataFile): DataFile
    fun findById(id: Int): DataFile?
    fun findByProjectId(projectId: Int): List<DataFile>
    fun update(dataFile: DataFile): DataFile
}

/** Database-side index used to reconcile persisted references with physical storage. */
fun interface DataFileStorageReferenceIndex {
    fun findRetainedStorageKeys(provider: String): Set<String>
}
