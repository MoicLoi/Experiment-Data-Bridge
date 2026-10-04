package me.moiclathy.edb.core.datafile.upload

import java.time.Instant

enum class ResumableUploadStatus { OPEN, COMPLETED, CANCELLED }

data class ResumableUploadSession(
    val id:String,
    val projectId:Int,
    val userId:Int,
    val fileName:String,
    val mediaType:String?,
    val totalBytes:Long,
    val offsetBytes:Long,
    val status:ResumableUploadStatus,
    val createdAt:Instant,
    val updatedAt:Instant
)

interface ResumableUploadSessionRepository {
    fun create(value:ResumableUploadSession):ResumableUploadSession
    fun find(id:String):ResumableUploadSession?
    fun updateOffset(id:String,expectedOffset:Long,newOffset:Long,updatedAt:Instant):Boolean
    fun updateStatus(id:String,expected:ResumableUploadStatus,status:ResumableUploadStatus,updatedAt:Instant):Boolean
}

interface ResumableUploadStore {
    fun create(id:String)
    fun append(id:String,expectedOffset:Long,input:java.io.InputStream,maxBytes:Long):Long
    fun open(id:String):java.io.InputStream
    fun size(id:String):Long
    fun delete(id:String):Boolean
}

class UploadOffsetMismatchException(val expected:Long,val actual:Long):IllegalStateException("Upload offset mismatch: expected $expected but current offset is $actual")
