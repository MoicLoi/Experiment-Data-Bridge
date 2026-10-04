import{useCallback,useState}from'react'
import type{EditorSnapshot}from'./graphModel'
export function useEditorHistory(initial:()=>EditorSnapshot){
 const[past,setPast]=useState<EditorSnapshot[]>([]);const[present,setPresent]=useState<EditorSnapshot>(initial);const[future,setFuture]=useState<EditorSnapshot[]>([])
 const commit=useCallback((next:EditorSnapshot|((current:EditorSnapshot)=>EditorSnapshot))=>{setPresent(current=>{const value=typeof next==='function'?next(current):next;if(JSON.stringify(value)===JSON.stringify(current))return current;setPast(items=>[...items.slice(-79),current]);setFuture([]);return value})},[])
 const undo=useCallback(()=>setPast(items=>{const previous=items.at(-1);if(!previous)return items;setPresent(current=>{setFuture(next=>[current,...next].slice(0,80));return previous});return items.slice(0,-1)}),[])
 const redo=useCallback(()=>setFuture(items=>{const next=items[0];if(!next)return items;setPresent(current=>{setPast(previous=>[...previous,current].slice(-80));return next});return items.slice(1)}),[])
 return{present,commit,undo,redo,canUndo:past.length>0,canRedo:future.length>0}
}
