const WG_KEY_REGEX = /^[A-Za-z0-9+/]{42}[AEIMQUYcgkosw048]=$/;
const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function isValidWireGuardKey(key) {
  return typeof key === 'string' && WG_KEY_REGEX.test(key.trim());
}

export function isValidUuid(value) {
  return typeof value === 'string' && UUID_REGEX.test(value.trim());
}

export function isValidEmail(email) {
  return typeof email === 'string' && EMAIL_REGEX.test(email.trim()) && email.trim().length <= 254;
}

export function parseEndpoint(endpoint) {
  if (typeof endpoint !== 'string' || !endpoint.includes(':')) return null;
  const trimmed = endpoint.trim();
  const idx = trimmed.lastIndexOf(':');
  const host = trimmed.slice(0, idx).replace(/^\[|\]$/g, '').trim();
  const port = Number(trimmed.slice(idx + 1));
  if (!host || !Number.isInteger(port) || port < 1 || port > 65535) return null;
  return { host, port };
}

export function isAllowedAgentUrl(url) {
  if (typeof url !== 'string' || !url.trim()) return false;
  try {
    const parsed = new URL(url.trim());
    return parsed.protocol === 'http:' || parsed.protocol === 'https:';
  } catch {
    return false;
  }
}
