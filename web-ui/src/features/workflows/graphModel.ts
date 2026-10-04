import type { WorkflowIr } from './types'

export interface GraphPoint { x:number;y:number }
export interface GraphNode { id:string;type:string;version:string;position:GraphPoint }
export interface GraphEdge { id:string;source:string;target:string }
export interface WorkflowGraph { nodes:GraphNode[];edges:GraphEdge[] }
export interface EditorSnapshot { workflow:WorkflowIr;positions:Record<string,GraphPoint> }

export function irToGraph(workflow:WorkflowIr,positions:Record<string,GraphPoint>={}):WorkflowGraph{
 const nodes=workflow.steps.map((step,index)=>({id:step.id,type:step.type,version:step.version,position:positions[step.id]??defaultPosition(index)}))
 const edges=workflow.steps.flatMap(step=>step.dependsOn.map(source=>({id:`${source}->${step.id}`,source,target:step.id})))
 return{nodes,edges}
}
export function graphDependencies(workflow:WorkflowIr,edges:GraphEdge[]):WorkflowIr{return{...workflow,steps:workflow.steps.map(step=>({...step,dependsOn:edges.filter(edge=>edge.target===step.id).map(edge=>edge.source)}))}}
export function connectGraph(graph:WorkflowGraph,source:string,target:string):WorkflowGraph{
 if(source===target||graph.edges.some(edge=>edge.source===source&&edge.target===target)||createsCycle(graph.edges,source,target))throw new Error('This connection would create a workflow cycle.')
 return{...graph,edges:[...graph.edges,{id:`${source}->${target}`,source,target}]}
}
export function removeGraphEdge(graph:WorkflowGraph,id:string):WorkflowGraph{return{...graph,edges:graph.edges.filter(edge=>edge.id!==id)}}
export function createsCycle(edges:GraphEdge[],source:string,target:string){
 const adjacency=new Map<string,string[]>();for(const edge of edges){const list=adjacency.get(edge.source)??[];list.push(edge.target);adjacency.set(edge.source,list)}
 const pending=[target],seen=new Set<string>();while(pending.length){const current=pending.pop()!;if(current===source)return true;if(seen.has(current))continue;seen.add(current);pending.push(...(adjacency.get(current)??[]))}return false
}
export function defaultPosition(index:number):GraphPoint{return{x:50+(index%4)*230,y:60+Math.floor(index/4)*180}}
export function snapPoint(point:GraphPoint,grid=20):GraphPoint{return{x:Math.max(0,Math.round(point.x/grid)*grid),y:Math.max(0,Math.round(point.y/grid)*grid)}}
export function autoLayout(graph:WorkflowGraph):Record<string,GraphPoint>{
 const depths=new Map<string,number>();const depth=(id:string,trail=new Set<string>()):number=>{if(depths.has(id))return depths.get(id)!;if(trail.has(id))return 0;const incoming=graph.edges.filter(edge=>edge.target===id);const value=incoming.length?1+Math.max(...incoming.map(edge=>depth(edge.source,new Set([...trail,id])))):0;depths.set(id,value);return value}
 graph.nodes.forEach(node=>depth(node.id));const rows=new Map<number,string[]>();graph.nodes.forEach(node=>{const level=depths.get(node.id)??0;rows.set(level,[...(rows.get(level)??[]),node.id])})
 const positions:Record<string,GraphPoint>={};[...rows.entries()].sort(([a],[b])=>a-b).forEach(([level,ids])=>ids.forEach((id,row)=>{positions[id]={x:60+level*260,y:60+row*170}}));return positions
}
