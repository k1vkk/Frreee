export function resolveDefaultApiUrl() {
  const envUrl = (import.meta.env.VITE_API_BASE_URL || '').trim().replace(/\/+$/, '');
  if (envUrl && !envUrl.includes('yourdomain.com') && !envUrl.includes('localhost')) {
    return envUrl;
  }
  if (typeof window !== 'undefined' && window.location.protocol === 'https:') {
    return window.location.origin;
  }
  return localStorage.getItem('nexora_admin_api_url') || '';
}

export async function apiRequest(baseUrl, path, { method = 'GET', token = '', adminKey = '', body = null } = {}) {
  const cleanBase = String(baseUrl || '').trim().replace(/\/+$/, '');
  if (!cleanBase.startsWith('https://')) {
    throw new Error('Production HTTPS Backend API URL is required (must start with https://).');
  }
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (adminKey) {
    headers['x-admin-key'] = adminKey;
    headers['x-admin-secret'] = adminKey;
  }

  const res = await fetch(`${cleanBase}${path}`, {
    method,
    headers,
    ...(body ? { body: JSON.stringify(body) } : {})
  });
  const text = await res.text();
  let json = {};
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    json = {};
  }
  if (!res.ok) {
    throw new Error(json.error || `HTTP ${res.status}`);
  }
  return json;
}

export function formatBytes(bytes) {
  const n = Number(bytes || 0);
  if (n >= 1024 ** 4) return `${(n / 1024 ** 4).toFixed(2)} TB`;
  if (n >= 1024 ** 3) return `${(n / 1024 ** 3).toFixed(2)} GB`;
  if (n >= 1024 ** 2) return `${(n / 1024 ** 2).toFixed(2)} MB`;
  if (n >= 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${n} B`;
}
