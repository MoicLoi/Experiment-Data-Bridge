package me.moiclathy.edb.web.dto

import kotlinx.serialization.Serializable
import me.moiclathy.edb.core.datafile.upload.ResumableUploadSession

@Serializable data class CreateResumableUploadRequest(val fileName:String,val sizeBytes:Long,val mediaType:String?=null)
@Serializable data class ResumableUploadResponse(val uploadId:String,val projectId:Int,val fileName:String,val mediaType:String?,val sizeBytes:Long,val offsetBytes:Long,val status:String,val createdAt:String,val updatedAt:String){companion object{fun from(value:ResumableUploadSession)=ResumableUploadResponse(value.id,value.projectId,value.fileName,value.mediaType,value.totalBytes,value.offsetBytes,value.status.name,value.createdAt.toString(),value.updatedAt.toString())}}
