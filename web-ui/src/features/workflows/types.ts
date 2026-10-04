export type JsonValue=string|number|boolean|null|JsonValue[]|{[key:string]:JsonValue}
export interface JsonSchema { type?:string|string[]; title?:string; description?:string; default?:JsonValue; enum?:JsonValue[]; minimum?:number; maximum?:number; minLength?:number; properties?:Record<string,JsonSchema>; required?:string[]; items?:JsonSchema; additionalProperties?:boolean|JsonSchema; allOf?:JsonSchema[]; anyOf?:JsonSchema[]; oneOf?:JsonSchema[]; 'x-ui'?:{order?:string[]} }
export interface StepCapabilityItem { id:string;titleKey:string;descriptionKey?:string|null;cliPath:string[];sourceType:string;sourceId:string;sourceVersion:string;phrasePatterns:PhrasePattern[] }
export interface PhrasePatternToken { type:'LITERAL'|'ENUM'|'PARAMETER';name?:string|null;i18nKey?:string|null;source?:string|null;optional:boolean }
export interface PhrasePattern { id:string;stepType:string;stepVersion:string;tokens:PhrasePatternToken[] }
export interface StepCatalogItem { type:string; version:string; source:string; title:string; description?:string|null; schemaAvailable:boolean; schemaUrl?:string|null;capabilities:StepCapabilityItem[];phrasePatterns:PhrasePattern[] }
export interface WorkflowBinding { parameterPath:string; contextKey:string; required:boolean }
export interface WorkflowOutputBinding { sourceContextKey:string; targetContextKey:string; required:boolean }
export interface WorkflowStep { id:string; type:string; version:string; order:number; dependsOn:string[]; parameters:Record<string,JsonValue>; contextBindings:WorkflowBinding[]; outputBindings:WorkflowOutputBinding[]; policy:{timeoutMs?:number|null;retry:{maxAttempts:number;initialDelayMs:number;backoffMultiplier:number;maxDelayMs:number};failureStrategy:'STOP_WORKFLOW'|'CONTINUE'} }
export interface WorkflowIr { schemaVersion:1;id:string;version:string;description?:string|null;inputs:Array<Record<string,JsonValue>>;outputs:Array<Record<string,JsonValue>>;steps:WorkflowStep[] }
export interface WorkflowCompilation { schemaVersion:number;workflowId:string;workflowVersion:string;description?:string|null;stepIds:string[];executionOrder:string[];registered:boolean }
export interface WorkflowTemplate { schemaVersion:1;id:string;version:string;title:string;description?:string|null;category:string;tags:string[];workflow:WorkflowIr }
export interface PhraseDefinition { id:number;projectId:number;definitionId:string;version:number;document:{schemaVersion:1;actions:Array<Record<string,JsonValue>>};sha256:string;createdBy:number;createdAt:string }
