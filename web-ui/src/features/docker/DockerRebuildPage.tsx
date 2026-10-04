import{useCallback,useEffect,useState}from'react'
import{useAuth}from'../../auth/AuthProvider'
import{fleet}from'../admin/adminApi'
import type{DockerWorker}from'../admin/types'
import{DockerRebuildPanel}from'./DockerRebuildPanel'
import'./docker.css'
export function DockerRebuildPage(){const{api,user}=useAuth();const[workers,setWorkers]=useState<DockerWorker[]>([]);const load=useCallback(()=>fleet(api).then(v=>setWorkers(v.members)),[api]);useEffect(()=>{if(user?.role==='ADMIN')void load()},[load,user?.role]);if(user?.role!=='ADMIN')return <div className="admin-forbidden"><span>403</span><h1>Administrator only</h1></div>;return <div className="docker-page"><header className="page-heading"><div><span>RUNTIME / DEPLOYMENT</span><h1>Image rebuild</h1><p>Preview and safely deploy a Registry Profile image to a managed Worker.</p></div></header><DockerRebuildPanel workers={workers} onComplete={()=>void load()}/></div>}
