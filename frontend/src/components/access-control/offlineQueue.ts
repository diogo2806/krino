import type { AccessIdentity, PendingAccessEvent } from './types';

const QUEUE_KEY = 'krino_access_control_queue_v1';
const IDENTITY_CACHE_KEY = 'krino_access_control_identity_cache_v1';
const DEVICE_KEY = 'krino_access_control_device_id_v1';

type IdentityCache = Record<string, AccessIdentity>;

function safeParse<T>(value: string | null, fallback: T): T {
  if (!value) return fallback;
  try { return JSON.parse(value) as T; } catch { return fallback; }
}

export function getDeviceId() {
  const current = localStorage.getItem(DEVICE_KEY);
  if (current) return current;
  const next = crypto.randomUUID();
  localStorage.setItem(DEVICE_KEY, next);
  return next;
}

export function getPendingEvents(): PendingAccessEvent[] {
  return safeParse<PendingAccessEvent[]>(localStorage.getItem(QUEUE_KEY), []).sort((a, b) => a.capturedAt.localeCompare(b.capturedAt));
}

export function enqueueEvent(event: PendingAccessEvent) {
  const queue = getPendingEvents();
  if (!queue.some((item) => item.clientEventId === event.clientEventId)) queue.push(event);
  localStorage.setItem(QUEUE_KEY, JSON.stringify(queue));
  return queue;
}

export function removePendingEvents(clientEventIds: string[]) {
  const ids = new Set(clientEventIds);
  const next = getPendingEvents().filter((event) => !ids.has(event.clientEventId));
  localStorage.setItem(QUEUE_KEY, JSON.stringify(next));
  return next;
}

export function cacheIdentity(code: string, identity: AccessIdentity) {
  const cache = safeParse<IdentityCache>(localStorage.getItem(IDENTITY_CACHE_KEY), {});
  cache[code.trim()] = identity;
  cache[identity.registration.trim()] = identity;
  localStorage.setItem(IDENTITY_CACHE_KEY, JSON.stringify(cache));
}

export function findCachedIdentity(code: string) {
  const cache = safeParse<IdentityCache>(localStorage.getItem(IDENTITY_CACHE_KEY), {});
  return cache[code.trim()];
}
