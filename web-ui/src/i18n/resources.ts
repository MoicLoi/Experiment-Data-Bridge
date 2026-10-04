import { agentTranslations } from '../features/agent/translations'
import { workflowTranslations } from '../features/workflows/translations'
import { phraseTranslations } from '../features/workflows/phraseTranslations'
import { adminTranslations } from '../features/admin/translations'
import { operationsTranslations } from '../features/operationsTranslations'

export const resources = {
  'zh-CN': {
    translation: {
      adminConsole: adminTranslations['zh-CN'],
      workflowEditor: workflowTranslations['zh-CN'],
      workflowPhrase: phraseTranslations['zh-CN'],
      phrase: { execute: '执行', convert: '格式转换', analysis: '分析', generate: '生成', report: '报表', copy: '复制上下文' },
      agent: agentTranslations['zh-CN'],
      operations: operationsTranslations['zh-CN'],
      'task.duration': '执行时长',
      common: { appName: '实验数据桥', console: '控制台', loading: '正在加载…', retry: '重试', unavailable: '即将开放', signOut: '退出登录' },
      auth: { title: '连接你的实验工作区', subtitle: '使用 EDB 账户进入统一任务与数据控制台。', username: '用户名', password: '密码', remember: '在此设备上保持登录', submit: '登录', submitting: '正在登录…', invalid: '用户名或密码错误', sessionExpired: '登录已过期，请重新登录' },
      nav: { overview: '总览', tasks: '任务', datafiles: '数据文件', workflows: '工作流', agent: '智能规划', admin: '系统管理' },
      shell: { environment: '运行环境', apiConnected: 'API 已配置', apiBase: 'API 地址', theme: '主题', language: '语言', light: '浅色', dark: '深色', system: '跟随系统' },
      overview: { eyebrow: 'EDB 0.25', title: '科研自动化，从一个清晰的工作台开始。', description: '任务、数据制品、工作流与受控 Agent 共用同一套权限、审计和运行底座。', statusTitle: '基础连接已就绪', statusBody: 'HTTP、SSE、登录状态、主题与多语言基础设施已经装配。', taskTitle: '任务控制台已就绪', taskBody: '查看任务列表、实时进度和完整时间线，并执行取消与管理员重试。', openTasks: '打开任务控制台' },
      task: { title: '任务控制台', subtitle: '查看队列、运行进度与完整生命周期。', project: '项目', selectProject: '选择一个项目', allStatuses: '全部状态', refresh: '刷新', live: '实时连接', reconnecting: '正在重连', empty: '当前筛选条件下没有任务', task: '任务', type: '类型', priority: '优先级', attempts: '尝试次数', updated: '更新时间', details: '任务详情', timeline: '生命周期', cancel: '请求取消', retry: '管理员重新入队', cancelling: '正在取消…', retrying: '正在重新入队…', previous: '上一页', next: '下一页', page: '第 {{current}} / {{total}} 页', progress: '进度', error: '执行错误', result: '执行结果', noSelection: '选择一个任务查看详情', loadingTimeline: '正在加载时间线…', eventActor: '操作用户 #{{id}}', status: { PENDING:'等待中', QUEUED:'已入队', RUNNING:'运行中', RETRYING:'等待重试', COMPLETED:'已完成', FAILED:'失败', TIMED_OUT:'超时', CANCELLED:'已取消' } },
      datafile: { title:'数据文件', subtitle:'上传、转换、分析并追踪每一份派生制品。', project:'项目', upload:'上传文件', uploading:'正在上传…', choose:'选择文件', empty:'此项目还没有数据文件', files:'文件', size:'大小', created:'创建时间', details:'文件详情', download:'下载', format:'格式', kind:'类型', status:'状态', origin:'来源', checksum:'SHA-256', storage:'存储', parents:'父级文件', producingTask:'生成任务', metadata:'结构化元数据', operations:'处理操作', convert:'转换', analysis:'分析', report:'报表', targetFormat:'目标格式', handler:'分析 Handler', definition:'报表定义', parameters:'参数', parameterHint:'每行输入 key=value', priority:'优先级', submit:'提交任务', submitting:'正在提交…', submitted:'任务 #{{id}} 已提交', artifacts:'派生制品', conversions:'转换结果', analyses:'分析结果', reports:'报表制品', noArtifacts:'尚无此类制品', unavailable:'当前没有可用能力', selectFile:'选择一个文件查看详情', uploadSuccess:'文件上传成功', downloadStarted:'下载已开始', advancedJson:'高级 JSON', formMode:'返回表单', parameterJsonInvalid:'参数 JSON 格式无效', parameterObjectRequired:'参数 JSON 必须是对象' },
      errors: { unknown: '发生未知错误', network: '无法连接 EDB 服务', forbidden: '没有权限执行此操作', notFound: '请求的资源不存在', server: '服务暂时不可用' },
      locale: { zhCN: '简体中文', enUS: 'English (US)' },
    },
  },
  'en-US': {
    translation: {
      adminConsole: adminTranslations['en-US'],
      workflowEditor: workflowTranslations['en-US'],
      workflowPhrase: phraseTranslations['en-US'],
      phrase: { execute: 'Execute', convert: 'Format conversion', analysis: 'Analysis', generate: 'Generate', report: 'Report', copy: 'Copy context' },
      agent: agentTranslations['en-US'],
      operations: operationsTranslations['en-US'],
      'task.duration': 'Duration',
      common: { appName: 'Experiment Data Bridge', console: 'Console', loading: 'Loading…', retry: 'Retry', unavailable: 'Coming soon', signOut: 'Sign out' },
      auth: { title: 'Connect to your research workspace', subtitle: 'Use your EDB account to enter the unified task and data console.', username: 'Username', password: 'Password', remember: 'Keep me signed in on this device', submit: 'Sign in', submitting: 'Signing in…', invalid: 'Invalid username or password', sessionExpired: 'Your session expired. Please sign in again.' },
      nav: { overview: 'Overview', tasks: 'Tasks', datafiles: 'Data files', workflows: 'Workflows', agent: 'Agent planning', admin: 'Administration' },
      shell: { environment: 'Environment', apiConnected: 'API configured', apiBase: 'API base', theme: 'Theme', language: 'Language', light: 'Light', dark: 'Dark', system: 'System' },
      overview: { eyebrow: 'EDB 0.25', title: 'Research automation starts with one clear workspace.', description: 'Tasks, data artifacts, workflows and the controlled Agent share one permission, audit and runtime foundation.', statusTitle: 'Foundation connected', statusBody: 'HTTP, SSE, authentication, theme and localization infrastructure are ready.', taskTitle: 'Task console is ready', taskBody: 'Inspect task lists, live progress and complete timelines, then cancel or requeue with administrator permission.', openTasks: 'Open task console' },
      task: { title: 'Task console', subtitle: 'Inspect queues, live progress and the complete lifecycle.', project: 'Project', selectProject: 'Select a project', allStatuses: 'All statuses', refresh: 'Refresh', live: 'Live', reconnecting: 'Reconnecting', empty: 'No tasks match the current filters', task: 'Task', type: 'Type', priority: 'Priority', attempts: 'Attempts', updated: 'Updated', details: 'Task details', timeline: 'Lifecycle', cancel: 'Request cancellation', retry: 'Admin requeue', cancelling: 'Cancelling…', retrying: 'Requeueing…', previous: 'Previous', next: 'Next', page: 'Page {{current}} / {{total}}', progress: 'Progress', error: 'Execution error', result: 'Result', noSelection: 'Select a task to view details', loadingTimeline: 'Loading timeline…', eventActor: 'Actor #{{id}}', status: { PENDING:'Pending', QUEUED:'Queued', RUNNING:'Running', RETRYING:'Retrying', COMPLETED:'Completed', FAILED:'Failed', TIMED_OUT:'Timed out', CANCELLED:'Cancelled' } },
      datafile: { title:'Data files', subtitle:'Upload, transform, analyze and trace every derived artifact.', project:'Project', upload:'Upload file', uploading:'Uploading…', choose:'Choose file', empty:'This project has no data files yet', files:'Files', size:'Size', created:'Created', details:'File details', download:'Download', format:'Format', kind:'Kind', status:'Status', origin:'Origin', checksum:'SHA-256', storage:'Storage', parents:'Parent files', producingTask:'Producing task', metadata:'Structured metadata', operations:'Operations', convert:'Convert', analysis:'Analysis', report:'Report', targetFormat:'Target format', handler:'Analysis handler', definition:'Report definition', parameters:'Parameters', parameterHint:'Enter one key=value per line', priority:'Priority', submit:'Submit task', submitting:'Submitting…', submitted:'Task #{{id}} submitted', artifacts:'Derived artifacts', conversions:'Conversions', analyses:'Analyses', reports:'Reports', noArtifacts:'No artifacts of this type yet', unavailable:'No capability is currently available', selectFile:'Select a file to view details', uploadSuccess:'File uploaded', downloadStarted:'Download started', advancedJson:'Advanced JSON', formMode:'Back to form', parameterJsonInvalid:'Parameter JSON is invalid', parameterObjectRequired:'Parameter JSON must be an object' },
      errors: { unknown: 'An unknown error occurred', network: 'Cannot reach the EDB service', forbidden: 'You do not have permission for this action', notFound: 'The requested resource was not found', server: 'The service is temporarily unavailable' },
      locale: { zhCN: '简体中文', enUS: 'English (US)' },
    },
  },
} as const

export type SupportedLocale = keyof typeof resources
export const supportedLocales: SupportedLocale[] = ['zh-CN', 'en-US']
