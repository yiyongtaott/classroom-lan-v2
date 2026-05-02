import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * 在线玩家列表 + 三状态（任务 3）。
 *
 * 字段：
 *   id, name, hostname, ip, avatar, status (ONLINE/PAGE_CLOSED/OFFLINE)
 *   backendAlive, wsAlive, pageActive
 */
export const useUserListStore = defineStore('userList', () => {
  const users = ref([])

  function _ensureUser(id) {
    let u = users.value.find(x => x.id === id)
    if (!u) {
      u = {
        id,
        name: '?',
        backendAlive: false,
        wsAlive: false,
        pageActive: false
      }
      users.value.push(u)
    }
    return u
  }

  /** 用 player[] 替换；保留已有的 backendAlive/wsAlive/pageActive 字段。 */
  function applyPlayers(list) {
    if (!Array.isArray(list)) return
    const map = new Map(users.value.map(u => [u.id, u]))
    const next = list.map(p => {
      const prev = map.get(p.id)
      return {
        ...p,
        backendAlive: prev?.backendAlive ?? false,
        wsAlive: prev?.wsAlive ?? false,
        pageActive: prev?.pageActive ?? false
      }
    })
    users.value = next
  }

  /** 单个用户增量更新（BUG-02 改名 / 改头像）。 */
  function updateUser(payload) {
    if (!payload || !payload.id) return
    const u = users.value.find(x => x.id === payload.id)
    if (!u) return
    if (payload.name !== undefined) u.name = payload.name
    if (payload.avatar !== undefined) u.avatar = payload.avatar
    if (payload.hostname !== undefined) u.hostname = payload.hostname
    if (payload.ip !== undefined) u.ip = payload.ip
    if (payload.status !== undefined) u.status = payload.status
  }

  /** 三状态圆点（任务 3）。 */
  function updateStatus(payload) {
    if (!payload || !payload.userId) return
    // userId 在后端是 player.id（注意：UserStatusService 的 record.userId 也存的是 player.id）
    const u = users.value.find(x => x.id === payload.userId)
    if (!u) return
    if (payload.backendAlive !== undefined) u.backendAlive = !!payload.backendAlive
    if (payload.wsAlive !== undefined) u.wsAlive = !!payload.wsAlive
    if (payload.pageActive !== undefined) u.pageActive = !!payload.pageActive
  }

  /** init 快照中的 userStatuses（playerId → record）。 */
  function applyStatusMap(map) {
    if (!map) return
    for (const id of Object.keys(map)) {
      const r = map[id]
      const u = users.value.find(x => x.id === id)
      if (!u) continue
      u.backendAlive = !!r.backendAlive
      u.wsAlive = !!r.wsAlive
      u.pageActive = !!r.pageActive
    }
  }

  function find(id) { return users.value.find(u => u.id === id) }
  const onlineCount = computed(() =>
    users.value.filter(u => u.status === 'ONLINE' || u.wsAlive).length
  )

  return { users, onlineCount, applyPlayers, updateUser, updateStatus, applyStatusMap, find }
})
