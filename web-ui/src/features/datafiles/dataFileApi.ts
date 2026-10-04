import type { ApiClient } from '../../lib/api'
import type { AnalysisHandler, ArtifactGroups, ConversionCapability, DataFile, DataFileTabularProfile, ReportDefinition, StructuredParameterValues, SubmittedTask } from './types'

const base = (projectId:number) => `/api/v1/projects/${projectId}`
const fileBase = (projectId:number,dataFileId:number) => `${base(projectId)}/data-files/${dataFileId}`
export const listDataFiles = (api:ApiClient,projectId:number) => api.get<DataFile[]>(`${base(projectId)}/data-files`)
export const getDataFile = (api:ApiClient,projectId:number,id:number) => api.get<DataFile>(fileBase(projectId,id))
export async function uploadDataFile(api:ApiClient,projectId:number,file:File) { const form=new FormData(); form.append('file',file,file.name); const response=await api.raw(`${base(projectId)}/data-files`,{method:'POST',body:form}); return response.json() as Promise<DataFile> }
export interface ResumableUploadProgress { uploadedBytes:number;totalBytes:number;percent:number;paused:boolean }
interface UploadSession { uploadId:string;offsetBytes:number;sizeBytes:number;status:string }
export class ResumableUploadController {
  private paused=false;private abort?:AbortController;private lastOffset=0
  constructor(private api:ApiClient,private projectId:number,private file:File,private onProgress:(value:ResumableUploadProgress)=>void,private chunkSize=4*1024*1024){}
  pause(){this.paused=true;this.abort?.abort();this.report(this.lastOffset,true)}
  async start():Promise<DataFile>{this.paused=false;let session=await this.session();let offset=session.offsetBytes;this.report(offset,false);while(offset<this.file.size){if(this.paused)throw new DOMException('Upload paused','AbortError');const end=Math.min(offset+this.chunkSize,this.file.size);let attempt=0;while(true){this.abort=new AbortController();try{const response=await this.api.raw(`${base(this.projectId)}/data-files/upload-sessions/${session.uploadId}`,{method:'PATCH',headers:{'Content-Type':'application/offset+octet-stream','Upload-Offset':String(offset)},body:this.file.slice(offset,end),signal:this.abort.signal});offset=Number(response.headers.get('Upload-Offset')??end);break}catch(failure){if(this.paused)throw failure;if(++attempt>=3)throw failure;await new Promise(resolve=>setTimeout(resolve,250*2**attempt));session=await this.head(session.uploadId);offset=session.offsetBytes}}this.persist(session.uploadId);this.report(offset,false)}const completed=await this.api.post<DataFile>(`${base(this.projectId)}/data-files/upload-sessions/${session.uploadId}/complete`,{});localStorage.removeItem(this.key());return completed}
  private async session(){const existing=localStorage.getItem(this.key());if(existing){try{return await this.head(existing)}catch{localStorage.removeItem(this.key())}}const value=await this.api.post<UploadSession>(`${base(this.projectId)}/data-files/upload-sessions`,{fileName:this.file.name,sizeBytes:this.file.size,mediaType:this.file.type||null});this.persist(value.uploadId);return value}
  private async head(id:string):Promise<UploadSession>{const response=await this.api.raw(`${base(this.projectId)}/data-files/upload-sessions/${id}`,{method:'HEAD'});return{uploadId:id,offsetBytes:Number(response.headers.get('Upload-Offset')??0),sizeBytes:Number(response.headers.get('Upload-Length')??this.file.size),status:response.headers.get('Upload-Status')??'OPEN'}}
  private key(){return`edb.upload.${this.projectId}.${this.file.name}.${this.file.size}.${this.file.lastModified}`}
  private persist(id:string){localStorage.setItem(this.key(),id)}
  private report(uploadedBytes:number,paused:boolean){this.lastOffset=uploadedBytes;this.onProgress({uploadedBytes,totalBytes:this.file.size,percent:this.file.size?Math.round(uploadedBytes*100/this.file.size):100,paused})}
}
interface ResumableWritable { write:(data:Uint8Array)=>Promise<void>;seek?:(position:number)=>Promise<void>;close:()=>Promise<void> }
type FilePickerWindow = Window & { showSaveFilePicker?: (options: { suggestedName: string }) => Promise<{ createWritable: (options?:{keepExistingData?:boolean}) => Promise<ResumableWritable> }> }
export async function downloadDataFile(api:ApiClient,file:DataFile) {
  const picker=(window as FilePickerWindow).showSaveFilePicker
  if (picker) {
    const handle=await picker({suggestedName:file.originalFileName})
    const writable=await handle.createWritable({keepExistingData:true});let offset=0;let attempts=0
    try{while(file.sizeBytes==null||offset<file.sizeBytes){try{const response=await api.raw(`${fileBase(file.projectId,file.id)}/download`,offset?{headers:{Range:`bytes=${offset}-`}}:undefined);if(!response.body)throw new Error('Download response has no body.');writable.seek&&await writable.seek(offset);const reader=response.body.getReader();while(true){const {done,value}=await reader.read();if(done)break;await writable.write(value);offset+=value.byteLength}attempts=0;if(file.sizeBytes==null||offset>=file.sizeBytes)break}catch(failure){if(++attempts>=3)throw failure;await new Promise(resolve=>setTimeout(resolve,500*2**attempts))}}}finally{await writable.close()}
    return
  }
  const response=await api.raw(`${fileBase(file.projectId,file.id)}/download`)
  const blob=await response.blob(); const url=URL.createObjectURL(blob); const anchor=document.createElement('a'); anchor.href=url; anchor.download=file.originalFileName; anchor.click(); window.setTimeout(()=>URL.revokeObjectURL(url),1_000)
}
export const conversionTargets=(api:ApiClient,file:DataFile)=>api.get<ConversionCapability[]>(`${fileBase(file.projectId,file.id)}/conversion-targets`)
export const submitConversion=(api:ApiClient,file:DataFile,targetFormat:string,options:Record<string,string>,priority=50)=>api.post<SubmittedTask>(`${fileBase(file.projectId,file.id)}/conversions`,{targetFormat,options,priority})
export const analysisHandlers=(api:ApiClient,projectId:number)=>api.get<AnalysisHandler[]>(`${base(projectId)}/analysis-handlers`)
export const tabularProfile=(api:ApiClient,file:DataFile)=>api.get<DataFileTabularProfile>(`${fileBase(file.projectId,file.id)}/tabular-profile`)
export const submitAnalysis=(api:ApiClient,file:DataFile,handlerId:string,parameters:Record<string,string>,priority=50,parameterValues?:StructuredParameterValues)=>api.post<SubmittedTask>(`${base(file.projectId)}/analyses`,{handlerId,sourceDataFileIds:[file.id],parameters:parameterValues?{}:parameters,parameterValues,priority})
export const reportDefinitions=(api:ApiClient,projectId:number)=>api.get<ReportDefinition[]>(`${base(projectId)}/reports/definitions`)
export const submitReport=(api:ApiClient,file:DataFile,definitionId:string,parameters:Record<string,string>,priority=50)=>api.post<SubmittedTask>(`${base(file.projectId)}/reports`,{definitionId,sourceDataFileIds:[file.id],parameters,priority})
export async function artifacts(api:ApiClient,file:DataFile):Promise<ArtifactGroups>{ const prefix=fileBase(file.projectId,file.id); const [conversions,analyses,reports]=await Promise.all([api.get<DataFile[]>(`${prefix}/conversions`),api.get<DataFile[]>(`${prefix}/analyses`),api.get<DataFile[]>(`${prefix}/reports`)]); return {conversions,analyses,reports} }
