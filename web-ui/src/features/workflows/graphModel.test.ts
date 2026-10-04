import{describe,expect,it}from'vitest'
import{autoLayout,connectGraph,graphDependencies,irToGraph,snapPoint}from'./graphModel'
import type{WorkflowIr,WorkflowStep}from'./types'
const step=(id:string,dependsOn:string[]=[]):WorkflowStep=>({id,type:'test',version:'1',order:0,dependsOn,parameters:{},contextBindings:[],outputBindings:[],policy:{timeoutMs:null,retry:{maxAttempts:1,initialDelayMs:1000,backoffMultiplier:2,maxDelayMs:60000},failureStrategy:'STOP_WORKFLOW'}})
const workflow:WorkflowIr={schemaVersion:1,id:'graph-test',version:'1',inputs:[],outputs:[],steps:[step('a'),step('b',['a']),step('c')]}
describe('Workflow graph adapter',()=>{
 it('round-trips IR dependencies without storing positions in IR',()=>{const graph=irToGraph(workflow,{a:{x:9,y:12}});expect(graph.nodes[0]?.position).toEqual({x:9,y:12});expect(graph.edges).toEqual([{id:'a->b',source:'a',target:'b'}]);expect(graphDependencies(workflow,graph.edges).steps[1]?.dependsOn).toEqual(['a'])})
 it('rejects duplicate and cyclic connections',()=>{const graph=irToGraph(workflow);expect(()=>connectGraph(graph,'a','b')).toThrow();const connected=connectGraph(graph,'b','c');expect(()=>connectGraph(connected,'c','a')).toThrow(/cycle/)})
 it('snaps coordinates and lays dependencies into later columns',()=>{expect(snapPoint({x:29,y:31})).toEqual({x:20,y:40});const positions=autoLayout(irToGraph(workflow));expect(positions.b!.x).toBeGreaterThan(positions.a!.x);expect(positions.c).toBeDefined()})
})
