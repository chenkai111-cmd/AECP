import { FormEvent, useCallback, useEffect, useState } from 'react'

import {
  addOrganizationMember,
  ApiError,
  listOrganizationMembers,
  OrganizationMember,
  OrganizationRole,
  OrganizationUserCandidate,
  removeOrganizationMember,
  searchOrganizationMemberCandidates,
} from '../lib/api'

const DEFAULT_ORGANIZATION_ID = 'ORG-DEMO-COMAC'
const roleOptions: Array<{ label: string; value: OrganizationRole }> = [
  { label: '组织管理员', value: 'ORGANIZATION_ADMIN' },
  { label: '项目负责人', value: 'PROJECT_MANAGER' },
  { label: '工程师', value: 'ENGINEER' },
  { label: '审计查看者', value: 'AUDITOR' }
]
const roleLabel = new Map(roleOptions.map((role) => [role.value, role.label]))

function friendlyError(error: unknown) {
  if (error instanceof ApiError && error.status === 401) return '登录会话已失效，请重新登录。'
  if (error instanceof ApiError && error.status === 403) return '当前账号没有成员管理权限。'
  if (error instanceof ApiError) return error.message
  return '成员服务暂时不可用，请稍后重试。'
}

export function OrganizationMembersPage({ organizationId = DEFAULT_ORGANIZATION_ID }: { organizationId?: string }) {
  const [members, setMembers] = useState<OrganizationMember[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [employeeNo, setEmployeeNo] = useState('')
  const [candidates, setCandidates] = useState<OrganizationUserCandidate[]>([])
  const [selectedCandidate, setSelectedCandidate] = useState<OrganizationUserCandidate | null>(null)
  const [existingMemberDialog, setExistingMemberDialog] = useState<OrganizationUserCandidate | null>(null)
  const [searching, setSearching] = useState(false)
  const [newRole, setNewRole] = useState<OrganizationRole>('ENGINEER')
const [pendingUserId, setPendingUserId] = useState<string | null>(null)

  const loadMembers = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const result = await listOrganizationMembers(organizationId)
      setMembers(result.items)

    } catch (loadError) {
      setError(friendlyError(loadError))
    } finally {
      setLoading(false)
    }
  }, [organizationId])

  useEffect(() => { void loadMembers() }, [loadMembers])

  async function handleSearch() {
    const query = employeeNo.trim()
    if (!query) { setError('请输入用户工号。'); return }
    setSearching(true)
    setError(null)
    setNotice(null)
    setSelectedCandidate(null)
    try {
      const result = await searchOrganizationMemberCandidates(organizationId, query)
      if (!result.length) {
        setCandidates([])
        setError('未找到匹配的用户，请确认工号。')
      } else if (result.length === 1 && result[0].already_member) {
        setCandidates([])
        setExistingMemberDialog(result[0])
      } else {
        setCandidates(result)
      }
    } catch (searchError) {
      setCandidates([])
      setError(friendlyError(searchError))
    } finally {
      setSearching(false)
    }
  }

  async function handleAdd(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedCandidate) { setError('请先搜索并选择用户。'); return }
    setPendingUserId('new')
    setError(null)
    setNotice(null)
    try {
      await addOrganizationMember(organizationId, selectedCandidate.user_id, newRole)
      setEmployeeNo('')
      setCandidates([])
      setSelectedCandidate(null)
      setNotice('成员已添加。')
      await loadMembers()
    } catch (addError) {
      setError(friendlyError(addError))
    } finally {
      setPendingUserId(null)
    }
  }

async function handleRemove(userId: string) {
    if (!window.confirm('确定要移除这名组织成员吗？')) return
    setPendingUserId(userId); setError(null); setNotice(null)
    try { await removeOrganizationMember(organizationId, userId); setNotice('成员已移除。'); await loadMembers() }
    catch (removeError) { setError(friendlyError(removeError)) }
    finally { setPendingUserId(null) }
  }

  return (
    <main className="workspace-page organization-members-page">
      <section className="member-toolbar" aria-label="新增组织成员">
        <div className="toolbar-heading">
          <span className="toolbar-index">01</span>
          <div><p className="section-kicker">成员配置</p><h2>添加协作成员</h2><p>按工号查找用户，再分配组织角色。</p></div>
        </div>
        <form className="member-add-form" onSubmit={handleAdd}>
          <div className="member-search-field">
            <label htmlFor="member-employee-no">用户工号</label>
            <div className="member-search-row">
              <input id="member-employee-no" aria-label="用户工号" onChange={(event) => { setEmployeeNo(event.target.value); setCandidates([]); setSelectedCandidate(null) }} placeholder="例如 A-1001" value={employeeNo} />
              <button className="secondary-action" disabled={searching} onClick={() => void handleSearch()} type="button">{searching ? '查询中…' : '搜索用户'}</button>
            </div>
            {candidates.length > 0 && <div className="member-candidate-list" aria-label="匹配用户">
              {candidates.map((candidate) => <button className={selectedCandidate?.user_id === candidate.user_id ? 'member-candidate selected' : 'member-candidate'} disabled={candidate.already_member} key={candidate.user_id} onClick={() => { if (!candidate.already_member) setSelectedCandidate(candidate) }} type="button"><strong>{candidate.display_name}</strong><span>{candidate.employee_no}</span><small>{candidate.already_member ? '已是组织成员' : selectedCandidate?.user_id === candidate.user_id ? '已选择' : '选择'}</small></button>)}
            </div>}
          </div>
          <label>成员角色<select aria-label="新成员角色" onChange={(event) => setNewRole(event.target.value as OrganizationRole)} value={newRole}>{roleOptions.map((role) => <option key={role.value} value={role.value}>{role.label}</option>)}</select></label>
          <button className="primary-action" disabled={pendingUserId === 'new' || !selectedCandidate} type="submit">{pendingUserId === 'new' ? '添加中…' : '添加成员'}</button>
        </form>
      </section>

      {existingMemberDialog && <div className="member-dialog-backdrop" role="presentation"><section aria-labelledby="existing-member-title" aria-modal="true" className="member-dialog" role="dialog"><span className="dialog-mark">!</span><p className="section-kicker">成员状态</p><h2 id="existing-member-title">该用户已加入组织</h2><p><strong>{existingMemberDialog.display_name}</strong>（{existingMemberDialog.employee_no}）已是当前组织成员，无需重复添加。</p><button className="primary-action" onClick={() => setExistingMemberDialog(null)} type="button">知道了</button></section></div>}
{selectedCandidate && <p className="selected-candidate">已选择：<strong>{selectedCandidate.display_name}</strong>（{selectedCandidate.employee_no}）</p>}
      {notice && <p className="inline-notice success-notice">{notice}</p>}
      {error && <p className="inline-notice error-notice" role="alert">{error}</p>}

      <section aria-label="组织成员列表" className="member-list-panel">
        <div className="member-list-heading"><div className="toolbar-heading"><span className="toolbar-index">02</span><div><p className="section-kicker">成员目录</p><h2>{loading ? '正在读取组织成员…' : members.length ? `共 ${members.length} 名成员` : '暂无成员'}</h2></div></div><button className="secondary-action" onClick={() => void loadMembers()} type="button">刷新列表</button></div>
        {loading ? <div className="member-loading" role="status"><span className="loading-dot" />正在读取组织成员…</div> : members.length ? <div className="member-table-wrap"><table className="member-table"><thead><tr><th>成员</th><th>工号</th><th>角色</th><th>加入时间</th><th>操作</th></tr></thead><tbody>{members.map((member) => <tr key={member.user_id}><td><div className="member-profile"><span className="member-avatar">{member.display_name.slice(-1)}</span><span><strong>{member.display_name}</strong><small>{member.username}</small></span></div></td><td><code>{member.username}</code></td><td><div className="role-control"><span className="role-label">{roleLabel.get(member.role)}</span></div></td><td className="joined-date">{new Date(member.joined_at).toLocaleDateString('zh-CN')}</td><td><div className="member-actions"><button aria-label={`移除-${member.user_id}`} className="danger-action compact-action" disabled={pendingUserId === member.user_id} onClick={() => void handleRemove(member.user_id)} type="button">移除</button></div></td></tr>)}</tbody></table></div> : <div className="member-empty"><strong>还没有成员</strong><span>从上方添加第一位协作成员。</span></div>}
      </section>
    </main>
  )
}