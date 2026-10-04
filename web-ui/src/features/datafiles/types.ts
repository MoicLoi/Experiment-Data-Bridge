import type { Task } from '../tasks/types'
import type { JsonSchema, JsonValue } from '../workflows/types'

export interface DataFile { id:number; projectId:number; createdByUserId:number; kind:string; format:string; status:string; originalFileName:string; mediaType?:string|null; sizeBytes?:number|null; sha256?:string|null; storageProvider?:string|null; origin:string; parentDataFileIds:number[]; producingTaskId?:number|null; externalSystem?:string|null; metadata:Record<string,unknown>; createdAt:string; updatedAt:string }
export interface ConversionOption { name:string; type:'STRING'|'INTEGER'; required:boolean; defaultValue?:string|null; description?:string|null }
export interface ConversionCapability { sourceFormat:string; targetFormat:string; options:ConversionOption[] }
export interface AnalysisParameterContract { id:string; version:number; title:string; description?:string|null; schema:JsonSchema }
export interface AnalysisCapabilityMetadata { sourceType:string;sourceId:string;sourceVersion:string;titleKey:string;descriptionKey?:string|null;cliPath:string[] }
export interface AnalysisHandler { id:string; analysisType:string; tool:string; outputFormat:string; parameterContract?:AnalysisParameterContract|null; capability?:AnalysisCapabilityMetadata|null }
export interface TabularColumnProfile { name:string; type:'NUMBER'|'STRING'; values:string[] }
export interface DataFileTabularProfile { columns:TabularColumnProfile[] }
export type StructuredParameterValues = Record<string, JsonValue>
export interface ReportDefinition { id:string; version:number; title:string; generatorId:string; outputFormat:string; outputFileName:string; defaultParameters:Record<string,string> }
export interface ArtifactGroups { conversions:DataFile[]; analyses:DataFile[]; reports:DataFile[] }
export type SubmittedTask = Task
