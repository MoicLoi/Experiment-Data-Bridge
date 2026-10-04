import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import type { JsonValue } from '../workflows/types'
import type { DataFileTabularProfile, StructuredParameterValues } from './types'

type Method='welch_t_test'|'mann_whitney_u'|'fisher_exact'|'spearman'
type Analysis=Record<string,JsonValue>&{name:string;method:Method}

const LABELS:Record<Method,string>={welch_t_test:'Welch t 检验',mann_whitney_u:'Mann–Whitney U 检验',fisher_exact:'Fisher 精确检验',spearman:'Spearman 相关'}

export function StatisticsParameterForm({profile,value,onChange}:{profile:DataFileTabularProfile|null;value:StructuredParameterValues;onChange:(value:StructuredParameterValues)=>void}){
  const {i18n}=useTranslation();const zh=i18n.resolvedLanguage?.toLowerCase().startsWith('zh')
  const templates:Array<{method:Method;title:string;description:string}>=zh?[
    {method:'welch_t_test',title:'两组均值比较',description:'Welch t 检验，比较两个独立组的数值均值。'},
    {method:'mann_whitney_u',title:'两组非参数比较',description:'Mann–Whitney U 检验，比较两个独立组的分布。'},
    {method:'fisher_exact',title:'分类变量关联',description:'Fisher 精确检验，分析两个二分类变量。'},
    {method:'spearman',title:'数值相关分析',description:'Spearman 相关，分析两个数值变量的单调关系。'},
  ]:[
    {method:'welch_t_test',title:'Compare two means',description:'Welch t-test for two independent groups.'},
    {method:'mann_whitney_u',title:'Non-parametric comparison',description:'Mann–Whitney U test for two independent groups.'},
    {method:'fisher_exact',title:'Categorical association',description:'Fisher exact test for two binary variables.'},
    {method:'spearman',title:'Numeric correlation',description:'Spearman correlation for two numeric variables.'},
  ]
  const analyses=Array.isArray(value.analyses)?value.analyses.filter(item=>item&&typeof item==='object'&&!Array.isArray(item)) as Analysis[]:[]
  const numeric=profile?.columns.filter(item=>item.type==='NUMBER').map(item=>item.name)??[]
  const columns=profile?.columns.map(item=>item.name)??[]
  const valuesFor=(column:JsonValue|undefined)=>profile?.columns.find(item=>item.name===column)?.values??[]
  const make=(method:Method='welch_t_test'):Analysis=>{
    const groupColumn=profile?.columns.find(item=>item.values.length>=2)?.name??columns[0]??''
    const groups=valuesFor(groupColumn)
    const valueColumn=numeric[0]??''
    const secondColumn=profile?.columns.find(item=>item.name!==groupColumn&&item.values.length>=2)?.name??groupColumn
    return method==='spearman'?{name:LABELS[method],method,xColumn:numeric[0]??'',yColumn:numeric[1]??numeric[0]??''}:method==='fisher_exact'?{name:LABELS[method],method,rowColumn:groupColumn,rowValues:groups.slice(0,2),columnColumn:secondColumn,columnValues:valuesFor(secondColumn).slice(0,2)}:{name:LABELS[method],method,groupColumn,groupA:groups[0]??'',groupB:groups[1]??'',valueColumn,alternative:'two-sided'}
  }
  useEffect(()=>{if(profile&&analyses.length===0)onChange({...value,outputFileName:typeof value.outputFileName==='string'?value.outputFileName:'statistics.json',analyses:[make()]})},[profile]) // initialize once after profile discovery
  const commit=(next:Analysis[])=>onChange({...value,analyses:next})
  const patch=(index:number,next:Partial<Analysis>)=>commit(analyses.map((item,i)=>i===index?Object.fromEntries(Object.entries({...item,...next}).filter(([,entry])=>entry!==undefined)) as Analysis:item))
  const changeMethod=(index:number,method:Method)=>commit(analyses.map((item,i)=>i===index?make(method):item))
  return <div className="statistics-form">
    <section className="statistics-templates" aria-label={zh?'常用分析模板':'Common analysis templates'}>
      <header><strong>{zh?'常用模板':'Common templates'}</strong><small>{zh?'模板根据当前数据列自动预填，可在提交前修改。':'Templates are prefilled from the current columns and remain editable.'}</small></header>
      <div>{templates.map(template=><button type="button" key={template.method} onClick={()=>commit([make(template.method)])}><strong>{template.title}</strong><small>{template.description}</small></button>)}</div>
      <p>{zh?'提示：模板参数基于常见数据分析场景，不代表绝对真实或适用于所有实验；请结合实验设计、样本分布和专业判断确认。':'Template parameters reflect common large-dataset scenarios. They are not guaranteed to represent the actual study; verify them against the study design, distribution, and domain judgment.'}</p>
    </section>
    <label><span>输出文件名</span><input value={typeof value.outputFileName==='string'?value.outputFileName:'statistics.json'} onChange={event=>onChange({...value,outputFileName:event.target.value})}/></label>
    {!profile&&<p className="schema-empty">正在读取表格列信息…</p>}
    {analyses.map((item,index)=><section className="statistics-analysis" key={index}>
      <header><strong>分析 {index+1}</strong><button type="button" className="danger-button" disabled={analyses.length===1} onClick={()=>commit(analyses.filter((_,i)=>i!==index))}>删除</button></header>
      <label><span>分析名称</span><input value={item.name} onChange={event=>patch(index,{name:event.target.value})}/></label>
      <label><span>分析方法</span><select value={item.method} onChange={event=>changeMethod(index,event.target.value as Method)}>{Object.entries(LABELS).map(([id,label])=><option value={id} key={id}>{label}</option>)}</select></label>
      {(item.method==='welch_t_test'||item.method==='mann_whitney_u')&&<>
        <Column label="分组列" value={item.groupColumn} columns={columns} onChange={column=>{const choices=valuesFor(column);patch(index,{groupColumn:column,groupA:choices[0]??'',groupB:choices[1]??''})}}/>
        <Choice label="组 A" value={item.groupA} choices={valuesFor(item.groupColumn)} onChange={groupA=>patch(index,{groupA})}/>
        <Choice label="组 B" value={item.groupB} choices={valuesFor(item.groupColumn)} onChange={groupB=>patch(index,{groupB})}/>
        <Column label="数值列" value={item.valueColumn} columns={numeric} onChange={valueColumn=>patch(index,{valueColumn})}/>
        <label><span>备择假设</span><select value={String(item.alternative??'two-sided')} onChange={event=>patch(index,{alternative:event.target.value})}><option value="two-sided">双侧</option><option value="less">小于</option><option value="greater">大于</option></select></label>
      </>}
      {item.method==='fisher_exact'&&<>
        <Column label="行变量" value={item.rowColumn} columns={columns} onChange={rowColumn=>patch(index,{rowColumn,rowValues:valuesFor(rowColumn).slice(0,2)})}/>
        <TwoChoices label="行值" values={item.rowValues} choices={valuesFor(item.rowColumn)} onChange={rowValues=>patch(index,{rowValues})}/>
        <Column label="列变量" value={item.columnColumn} columns={columns} onChange={columnColumn=>patch(index,{columnColumn,columnValues:valuesFor(columnColumn).slice(0,2)})}/>
        <TwoChoices label="列值" values={item.columnValues} choices={valuesFor(item.columnColumn)} onChange={columnValues=>patch(index,{columnValues})}/>
      </>}
      {item.method==='spearman'&&<><Column label="X 数值列" value={item.xColumn} columns={numeric} onChange={xColumn=>patch(index,{xColumn})}/><Column label="Y 数值列" value={item.yColumn} columns={numeric} onChange={yColumn=>patch(index,{yColumn})}/></>}
    </section>)}
    <button type="button" className="secondary-button" disabled={!profile} onClick={()=>commit([...analyses,make()])}>＋ 添加分析</button>
  </div>
}

function Column({label,value,columns,onChange}:{label:string;value:JsonValue|undefined;columns:string[];onChange:(value:string)=>void}){return <label><span>{label}</span><select value={String(value??'')} onChange={event=>onChange(event.target.value)}><option value="">请选择</option>{columns.map(item=><option key={item}>{item}</option>)}</select></label>}
function Choice({label,value,choices,onChange}:{label:string;value:JsonValue|undefined;choices:string[];onChange:(value:string)=>void}){return <label><span>{label}</span><select value={String(value??'')} onChange={event=>onChange(event.target.value)}><option value="">请选择</option>{choices.map(item=><option key={item}>{item}</option>)}</select></label>}
function TwoChoices({label,values,choices,onChange}:{label:string;values:JsonValue|undefined;choices:string[];onChange:(value:string[])=>void}){const selected=Array.isArray(values)?values.map(String):[];return <div className="statistics-pair"><span>{label}</span><Choice label="值 1" value={selected[0]} choices={choices} onChange={v=>onChange([v,selected[1]??''])}/><Choice label="值 2" value={selected[1]} choices={choices} onChange={v=>onChange([selected[0]??'',v])}/></div>}
