import type { JsonSchema,JsonValue } from './types'

export function SchemaForm({schema,value,onChange}:{schema:JsonSchema;value:Record<string,JsonValue>;onChange:(value:Record<string,JsonValue>)=>void}){
  const properties=schema.properties??{};const required=new Set(schema.required??[])
  if(!Object.keys(properties).length)return <p className="schema-empty">No schema parameters</p>
  const order=schema['x-ui']?.order??[];const entries=Object.entries(properties).sort(([left],[right])=>{const a=order.indexOf(left),b=order.indexOf(right);return (a<0?Number.MAX_SAFE_INTEGER:a)-(b<0?Number.MAX_SAFE_INTEGER:b)})
  return <div className="schema-form">{entries.map(([name,field])=><SchemaField key={name} name={name} schema={field} required={required.has(name)} value={value[name]} onChange={next=>{const copy={...value};if(next===undefined)delete copy[name];else copy[name]=next;onChange(copy)}}/>)}</div>
}

function SchemaField({name,schema,required,value,onChange}:{name:string;schema:JsonSchema;required:boolean;value:JsonValue|undefined;onChange:(value:JsonValue|undefined)=>void}){
  const kind=Array.isArray(schema.type)?schema.type.find(item=>item!=='null'):schema.type
  const id=`schema-${name.replace(/[^a-zA-Z0-9_-]/g,'-')}`
  if(schema.enum)return <label htmlFor={id}><span>{schema.title??name}{required&&<b>*</b>}</span><select id={id} value={value==null?'':String(value)} onChange={event=>onChange(schema.enum?.find(item=>String(item)===event.target.value))}><option value="">Select</option>{schema.enum.map((item,index)=><option key={index} value={String(item)}>{String(item)}</option>)}</select>{schema.description&&<small>{schema.description}</small>}</label>
  if(kind==='boolean')return <label className="schema-check" htmlFor={id}><input id={id} type="checkbox" checked={Boolean(value??schema.default??false)} onChange={event=>onChange(event.target.checked)}/><span>{schema.title??name}{required&&<b>*</b>}</span>{schema.description&&<small>{schema.description}</small>}</label>
  if(kind==='integer'||kind==='number')return <label htmlFor={id}><span>{schema.title??name}{required&&<b>*</b>}</span><input id={id} type="number" min={schema.minimum} max={schema.maximum} step={kind==='integer'?1:'any'} value={value==null?'':String(value)} placeholder={schema.default==null?'':String(schema.default)} onChange={event=>onChange(event.target.value===''?undefined:Number(event.target.value))}/>{schema.description&&<small>{schema.description}</small>}</label>
  if(kind==='object'||kind==='array')return <label htmlFor={id}><span>{schema.title??name}{required&&<b>*</b>}</span><textarea id={id} rows={4} value={value===undefined?'':JSON.stringify(value,null,2)} placeholder={schema.default===undefined?'{}':JSON.stringify(schema.default,null,2)} onChange={event=>{try{onChange(event.target.value.trim()?JSON.parse(event.target.value) as JsonValue:undefined)}catch{/* keep last valid value */}}}/>{schema.description&&<small>{schema.description}</small>}</label>
  return <label htmlFor={id}><span>{schema.title??name}{required&&<b>*</b>}</span><input id={id} type="text" minLength={schema.minLength} value={value==null?'':String(value)} placeholder={schema.default==null?'':String(schema.default)} onChange={event=>onChange(event.target.value===''&&!required?undefined:event.target.value)}/>{schema.description&&<small>{schema.description}</small>}</label>
}
