package me.moiclathy.edb.core.datafile.upload

import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.database.repository.ProjectRepository
import me.moiclathy.edb.core.exception.PermissionDeniedException
import me.moiclathy.edb.core.exception.ProjectNotFoundException
import me.moiclathy.edb.core.security.PermissionChecker
import java.io.InputStream
import java.time.Clock
import java.util.UUID

class ResumableUploadService(private val sessions:ResumableUploadSessionRepository,private val store:ResumableUploadStore,private val uploads:DataFileUploadService,private val projects:ProjectRepository,private val permissions:PermissionChecker,private val policy:DataFileUploadPolicy,private val clock:Clock=Clock.systemUTC()){
    fun create(projectId:Int,userId:Int,fileName:String,mediaType:String?,totalBytes:Long):ResumableUploadSession{writable(projectId,userId);require(totalBytes in 0..policy.maxSizeBytes){"Upload size exceeds configured limit."};val now=clock.instant();val value=ResumableUploadSession(UUID.randomUUID().toString(),projectId,userId,fileName,mediaType,totalBytes,0,ResumableUploadStatus.OPEN,now,now);store.create(value.id);return try{sessions.create(value)}catch(failure:Exception){store.delete(value.id);throw failure}}
    fun get(projectId:Int,userId:Int,id:String):ResumableUploadSession{var value=sessions.find(id)?.takeIf{it.projectId==projectId&&it.userId==userId}?:throw IllegalArgumentException("Upload session was not found.");if(value.status==ResumableUploadStatus.OPEN){val physical=store.size(id);require(physical<=value.totalBytes){"Upload content exceeds declared size."};if(physical!=value.offsetBytes&&sessions.updateOffset(id,value.offsetBytes,physical,clock.instant()))value=checkNotNull(sessions.find(id))};return value}
    fun append(projectId:Int,userId:Int,id:String,offset:Long,input:InputStream):ResumableUploadSession{val current=get(projectId,userId,id);check(current.status==ResumableUploadStatus.OPEN);if(offset!=current.offsetBytes)throw UploadOffsetMismatchException(offset,current.offsetBytes);val next=store.append(id,offset,input,current.totalBytes-offset);check(sessions.updateOffset(id,offset,next,clock.instant())){"Upload session changed concurrently."};return get(projectId,userId,id)}
    fun complete(projectId:Int,userId:Int,id:String):DataFile{val current=get(projectId,userId,id);check(current.status==ResumableUploadStatus.OPEN);require(current.offsetBytes==current.totalBytes){"Upload is incomplete."};val result=store.open(id).use{uploads.upload(DataFileUploadRequest(projectId,userId,current.fileName,it,current.mediaType))};check(sessions.updateStatus(id,ResumableUploadStatus.OPEN,ResumableUploadStatus.COMPLETED,clock.instant()));store.delete(id);return result}
    fun cancel(projectId:Int,userId:Int,id:String){val current=get(projectId,userId,id);if(current.status==ResumableUploadStatus.OPEN)sessions.updateStatus(id,ResumableUploadStatus.OPEN,ResumableUploadStatus.CANCELLED,clock.instant());store.delete(id)}
    private fun writable(projectId:Int,userId:Int){val project=projects.findById(projectId)?:throw ProjectNotFoundException(projectId);if(!permissions.requireWritable(userId,project))throw PermissionDeniedException()}
}
