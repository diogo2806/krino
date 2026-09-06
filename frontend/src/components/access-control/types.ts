export type AccessIdentity = { studentId: number; registration: string; studentName: string; classId: number; className: string; schoolId: number; schoolCode: string; schoolName: string; };
export type AccessCard = { studentId: number; registration: string; studentName: string; className: string; schoolName: string; qrPayload: string; qrSvg: string; };
export type AccessEventRequest = { clientEventId: string; code: string; eventType: 'ENTRY' | 'EXIT'; capturedAt: string; capturedOffline: boolean; sourceType: 'QR' | 'MANUAL'; deviceId: string; };
export type PendingAccessEvent = AccessEventRequest & { identity: AccessIdentity; };
export type AccessEvent = { clientEventId: string; studentId: number; registration: string; studentName: string; classId?: number; className?: string; schoolId: number; schoolName: string; eventType: 'ENTRY' | 'EXIT'; capturedAt: string; receivedAt: string; capturedOffline: boolean; sourceType: 'QR' | 'MANUAL'; synchronizedEvent: boolean; duplicate: boolean; notificationAvailable: boolean; };
export type SyncResult = { clientEventId?: string; synchronizedEvent: boolean; duplicate: boolean; error?: string; event?: AccessEvent; };
