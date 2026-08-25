export const PROTECTED_ROUTE_FALLBACK = '/workspace'

export type ProtectedRouteEntry = {
  description: string
  navLabel: string
  path: string
  title: string
}

export const protectedRouteEntries: ProtectedRouteEntry[] = [
  {
    path: '/workspace',
    navLabel: '项目空间',
    title: '项目空间壳体',
    description: '真实项目列表、最近访问和切换逻辑将在后续任务中实现；本任务仅交付工程壳与路由骨架。'
  },
  {
    path: '/dashboard',
    navLabel: '项目总览',
    title: '项目总览驾驶舱',
    description: '当前页面只提供壳体、布局与状态组件占位；真实数据与交互行为由后续任务接管。'
  },
  {
    path: '/meetings',
    navLabel: '会议协同',
    title: '会议协同占位页',
    description: '会议预约、议程、纪要和决议数据将在后续任务中实现；本任务仅交付工程壳与路由骨架。'
  },
  {
    path: '/tasks',
    navLabel: '任务闭环',
    title: '任务闭环占位页',
    description: '任务列表、状态流转和催办行为将在后续任务中实现；本任务仅交付工程壳与路由骨架。'
  },
  {
    path: '/files',
    navLabel: '文件空间',
    title: '文件空间占位页',
    description: '文件上传、版本、筛选和权限数据将在后续任务中实现；本任务仅交付工程壳与路由骨架。'
  },
  {
    path: '/components',
    navLabel: '部件追溯',
    title: '部件追溯占位页',
    description: '部件树、责任矩阵和关联时间线将在后续任务中实现；本任务仅交付工程壳与路由骨架。'
  },
  {
    path: '/models/FV-2026-001',
    navLabel: '数模查看',
    title: '数模查看占位页',
    description: 'STEP 解析、查看器和批注能力将在后续任务中实现；本任务仅交付工程壳与路由骨架。'
  },
  {
    path: '/admin/audit',
    navLabel: '系统审计',
    title: '系统审计占位页',
    description: '审计日志、筛选器和明细行为将在后续任务中实现；本任务仅交付工程壳与路由骨架。'
  }
]

const publicPaths = new Set(['/', '/login'])
const exactProtectedPaths = new Set(
  protectedRouteEntries
    .map((entry) => entry.path)
    .filter((path) => !path.startsWith('/models/'))
)
const protectedRouteLookup = new Map(protectedRouteEntries.map((entry) => [entry.path, entry]))
const modelRoutePattern = /^\/models\/[^/?#]+$/

export function getProtectedRouteForPath(pathname: string) {
  if (modelRoutePattern.test(pathname)) {
    return protectedRouteLookup.get('/models/FV-2026-001')
  }

  return protectedRouteLookup.get(pathname)
}

export function sanitizeProtectedRedirect(rawRedirect: string | null) {
  if (!rawRedirect) {
    return PROTECTED_ROUTE_FALLBACK
  }

  try {
    const parsed = new URL(rawRedirect, 'https://aecp.local')

    if (parsed.origin !== 'https://aecp.local') {
      return PROTECTED_ROUTE_FALLBACK
    }

    if (publicPaths.has(parsed.pathname)) {
      return PROTECTED_ROUTE_FALLBACK
    }

    if (exactProtectedPaths.has(parsed.pathname) || modelRoutePattern.test(parsed.pathname)) {
      return `${parsed.pathname}${parsed.search}${parsed.hash}`
    }

    return PROTECTED_ROUTE_FALLBACK
  } catch {
    return PROTECTED_ROUTE_FALLBACK
  }
}
