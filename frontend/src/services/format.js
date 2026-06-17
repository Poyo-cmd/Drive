export function formatBytes(bytes) {
  if (!bytes || bytes <= 0) return '—'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  const value = bytes / Math.pow(1024, i)
  return `${value.toFixed(value >= 10 || i === 0 ? 0 : 1)} ${units[i]}`
}

export function formatDate(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString('es-CL', {
    day: '2-digit',
    month: 'short',
    year: 'numeric'
  })
}

// Glifo segun el tipo de contenido. Simple pero suficiente para el clon.
export function glyphFor(node) {
  if (node.folder) return '📁'
  const ct = node.contentType || ''
  if (ct.startsWith('image/')) return '🖼️'
  if (ct.startsWith('video/')) return '🎬'
  if (ct.startsWith('audio/')) return '🎵'
  if (ct.includes('pdf')) return '📕'
  if (ct.includes('zip') || ct.includes('compressed')) return '🗜️'
  if (ct.includes('json') || ct.includes('javascript') || ct.includes('xml')) return '🧩'
  if (ct.startsWith('text/')) return '📄'
  return '📦'
}
