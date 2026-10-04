export interface PhraseEntry<T extends string=string>{value:T;labelKey:string;defaultLabel:string}
export const ACTIONS=[{value:'execute',labelKey:'workflowPhrase.action.execute',defaultLabel:'执行'}] satisfies PhraseEntry[]
export const CAPABILITY_KINDS=[{value:'convert',labelKey:'workflowPhrase.capability.convert',defaultLabel:'文件转换'},{value:'analysis',labelKey:'workflowPhrase.capability.analysis',defaultLabel:'使用算法库'},{value:'step',labelKey:'workflowPhrase.capability.step',defaultLabel:'执行插件/步骤'}] satisfies PhraseEntry[]
export const CONVERSION_TARGETS=['CSV','JSON','XLSX','HDF5','MAT'].map(value=>({value,labelKey:`workflowPhrase.format.${value.toLowerCase()}`,defaultLabel:`转换到 ${value}`}))
export const PARAMETER_MODES=[{value:'template',labelKey:'workflowPhrase.parameters.template',defaultLabel:'按模板参数运行'},{value:'custom',labelKey:'workflowPhrase.parameters.custom',defaultLabel:'按自定义参数运行'}] satisfies PhraseEntry[]
