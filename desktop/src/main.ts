import { invoke } from '@tauri-apps/api/core'
import './style.css'

type Phase='STARTING'|'READY'|'FAILED'|'STOPPED'
interface ServerStatus { phase:Phase;managed:boolean;pid:number|null;serverUrl:string;logDirectory:string;message:string|null }

const root=document.querySelector<HTMLElement>('#app')!
root.innerHTML=`<section class="launcher"><header><div class="mark">EDB</div><div><span>EXPERIMENT DATA BRIDGE</span><h1>Starting local workspace</h1></div></header><div class="status"><i></i><strong id="phase">Preparing server…</strong><p id="message">Loading configuration and modules.</p></div><div class="progress"><b></b></div><footer><button id="logs">Open logs</button><button id="retry" hidden>Try again</button><small>EDB Desktop · local server mode</small></footer></section>`

const phase=document.querySelector<HTMLElement>('#phase')!
const message=document.querySelector<HTMLElement>('#message')!
const retry=document.querySelector<HTMLButtonElement>('#retry')!
const launcher=document.querySelector<HTMLElement>('.launcher')!
let navigating=false
let readySamples=0

async function refresh(){
  try{
    const status=await invoke<ServerStatus>('server_status')
    launcher.dataset.phase=status.phase.toLowerCase();phase.textContent=label(status.phase)
    message.textContent=status.message??(status.managed?'Desktop is managing the local EDB server.':'Connected to an existing EDB server.')
    retry.hidden=status.phase!=='FAILED'&&status.phase!=='STOPPED'
    readySamples=status.phase==='READY'?readySamples+1:0
    if(readySamples>=3&&!navigating){navigating=true;location.replace(status.serverUrl)}
  }catch(error){launcher.dataset.phase='failed';phase.textContent='Desktop runtime unavailable';message.textContent=String(error);retry.hidden=false}
}

function label(value:Phase){return({STARTING:'Starting EDB Server…',READY:'Workspace ready',FAILED:'Server failed to start',STOPPED:'Server stopped'} as const)[value]}
document.querySelector('#logs')?.addEventListener('click',()=>void invoke('open_log_directory'))
retry.addEventListener('click',async()=>{retry.hidden=true;await invoke('start_server');await refresh()})
void refresh();window.setInterval(()=>void refresh(),500)
