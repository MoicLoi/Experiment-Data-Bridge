package me.moiclathy.edb.core.datafile.upload

import me.moiclathy.edb.core.database.table.DataFileUploadSessionsTable as T
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class MySqlResumableUploadSessionRepository:ResumableUploadSessionRepository{
    override fun create(value:ResumableUploadSession)=transaction{T.insert{it[id]=value.id;it[projectId]=value.projectId;it[userId]=value.userId;it[fileName]=value.fileName;it[mediaType]=value.mediaType;it[totalBytes]=value.totalBytes;it[offsetBytes]=value.offsetBytes;it[status]=value.status;it[createdAt]=value.createdAt;it[updatedAt]=value.updatedAt};value}
    override fun find(id:String)=transaction{T.selectAll().where{T.id eq id}.limit(1).singleOrNull()?.model()}
    override fun updateOffset(id:String,expectedOffset:Long,newOffset:Long,updatedAt:Instant)=transaction{T.update({(T.id eq id) and (T.offsetBytes eq expectedOffset) and (T.status eq ResumableUploadStatus.OPEN)}){it[offsetBytes]=newOffset;it[T.updatedAt]=updatedAt}==1}
    override fun updateStatus(id:String,expected:ResumableUploadStatus,status:ResumableUploadStatus,updatedAt:Instant)=transaction{T.update({(T.id eq id) and (T.status eq expected)}){it[T.status]=status;it[T.updatedAt]=updatedAt}==1}
    private fun ResultRow.model()=ResumableUploadSession(this[T.id],this[T.projectId],this[T.userId],this[T.fileName],this[T.mediaType],this[T.totalBytes],this[T.offsetBytes],this[T.status],this[T.createdAt],this[T.updatedAt])
}
