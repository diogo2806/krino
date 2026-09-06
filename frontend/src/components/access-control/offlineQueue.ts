import type { AccessIdentity, PendingAccessEvent } from './types';

const QUEUE_PREFIX = 'krino_access_control_queue_v1';
const IDENTITY_CACHE_PREFIX = 'krino_access_control_identity_cache_v1';
const DEVICE_KEY = 'krino_access_control_device_id_v1';

type IdentityCache = Record<string, AccessIdentity>;

function safeParse<T>(value: string | null, fallback: T): T {
  if (!value) return fallback;
  try { return JSON.parse(value) as T; } catch { return fallback; }
}

function scopedKey(prefix: string, username: string) {
  return `${prefix}:${encodeURIComponent(username.trim().toLowerCase())}`;
}

export function getDeviceId() {
  const current = localStorage.getItem(DEVICE_KEY);
  if (current) return current;
  const next = crypto.randomUUID();
  localStorage.setItem(DEVICE_KEY, next);
  return next;
}

export function getPendingEvents(username: string): PendingAccessEvent[] {
  return safeParse<PendingAccessEvent[]>(localStorage.getItem(scopedKey(QUEUE_PREFIX, username)), []).sort((a, b) => a.capturedAt.localeCompare(b.capturedAt));
}

export function enqueueEvent(username: string, event: PendingAccessEvent) {
  const queue = getPendingEvents(username);
  if (!queue.some((item) => item.clientEventId === event.clientEventId)) queue.push(event);
  localStorage.setItem(scopedKey(QUEUE_PREFIX, username), JSON.stringify(queue));
  return queue;
}

export function removePendingEvents(username: string, clientEventIds: string[]) {
  const ids = new Set(clientEventIds);
  const next = getPendingEvents(username).filter((event) => !ids.has(event.clientEventId));
  localStorage.setItem(scopedKey(QUEUE_PREFIX, username), JSON.stringify(next));
  return next;
}

export function cacheIdentity(username: string, code: string, identity: AccessIdentity) {
  const key = scopedKey(IDENTITY_CACHE_PREFIX, username);
  const cache = safeParse<IdentityCache>(localStorage.getItem(key), {});
  cache[code.trim()] = identity;
  cache[identity.registration.trim()] = identity;
  localStorage.setItem(key, JSON.stringify(cache));
}

export function findCachedIdentity(username: string, code: string) {
  const cache = safeParse<IdentityCache>(localStorage.getItem(scopedKey(IDENTITY_CACHE_PREFIX, username)), {});
  return cache[code.trim()];
}

export function clearOfflineData(username: string) {
  localStorage.removeItem(scopedKey(QUEUE_PREFIX, username));
  localStorage.removeItem(scopedKey(IDENTITY_CACHE_PREFIX, username));
}
