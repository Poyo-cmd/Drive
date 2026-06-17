const BASE = '/api'
const TOKEN_KEY = 'drive_token'
const USER_KEY = 'drive_user'

// --- sesion ---
export const session = {
  token: () => localStorage.getItem(TOKEN_KEY),
  username: () => localStorage.getItem(USER_KEY),
  isAuthed: () => !!localStorage.getItem(TOKEN_KEY),
  set(token, username) {
    localStorage.setItem(TOKEN_KEY, token)
    localStorage.setItem(USER_KEY, username)
  },
  clear() {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }
}

function authHeaders(extra = {}) {
  const token = session.token()
  return token ? { ...extra, Authorization: `Bearer ${token}` } : extra
}

async function handle(res) {
  if (res.status === 401) {
    // token vencido o invalido: cerramos sesion y volvemos al login
    session.clear()
    window.location.reload()
    throw new Error('Sesion expirada')
  }
  if (!res.ok) {
    let detail = res.statusText
    try {
      const body = await res.json()
      detail = body.message || detail
    } catch (_) {
      /* sin cuerpo JSON */
    }
    throw new Error(detail)
  }
  return res.status === 204 ? null : res.json()
}

export const auth = {
  register(username, password) {
    return fetch(`${BASE}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    }).then(handle)
  },
  login(username, password) {
    return fetch(`${BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    }).then(handle)
  }
}

export const api = {
  list(parentId = 'root') {
    return fetch(`${BASE}/files?parentId=${encodeURIComponent(parentId)}`, {
      headers: authHeaders()
    }).then(handle)
  },

  listTrash() {
    return fetch(`${BASE}/files/trash`, { headers: authHeaders() }).then(handle)
  },

  createFolder(name, parentId = 'root') {
    return fetch(`${BASE}/files/folders`, {
      method: 'POST',
      headers: authHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ name, parentId })
    }).then(handle)
  },

  upload(file, parentId = 'root', onProgress) {
    return new Promise((resolve, reject) => {
      const form = new FormData()
      form.append('file', file)
      form.append('parentId', parentId)

      const xhr = new XMLHttpRequest()
      xhr.open('POST', `${BASE}/files`)
      const token = session.token()
      if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)
      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable && onProgress) {
          onProgress(Math.round((e.loaded / e.total) * 100))
        }
      }
      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) resolve(JSON.parse(xhr.responseText))
        else if (xhr.status === 401) {
          session.clear()
          window.location.reload()
        } else reject(new Error('No se pudo subir ' + file.name))
      }
      xhr.onerror = () => reject(new Error('Error de red al subir ' + file.name))
      xhr.send(form)
    })
  },

  rename(id, name) {
    return fetch(`${BASE}/files/${id}`, {
      method: 'PATCH',
      headers: authHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ name })
    }).then(handle)
  },

  trash(id) {
    return fetch(`${BASE}/files/${id}`, { method: 'DELETE', headers: authHeaders() }).then(handle)
  },

  restore(id) {
    return fetch(`${BASE}/files/${id}/restore`, { method: 'POST', headers: authHeaders() }).then(handle)
  },

  deleteForever(id) {
    return fetch(`${BASE}/files/${id}?permanent=true`, {
      method: 'DELETE',
      headers: authHeaders()
    }).then(handle)
  },

  emptyTrash() {
    return fetch(`${BASE}/files/trash`, { method: 'DELETE', headers: authHeaders() }).then(handle)
  },

  // La descarga necesita el header Authorization, asi que va por fetch + blob
  // en vez de una navegacion directa del navegador.
  async download(node) {
    const res = await fetch(`${BASE}/files/${node.id}/download`, { headers: authHeaders() })
    if (!res.ok) {
      if (res.status === 401) {
        session.clear()
        window.location.reload()
      }
      throw new Error('No se pudo descargar ' + node.name)
    }
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = node.name
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  }
}
