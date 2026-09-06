export type Permission = { id: number; code: string; name: string; description?: string; };
export type Role = { id: number; name: string; description?: string; systemRole: boolean; permissions: Permission[]; };
export type RoleAssignment = { id: number; roleId: number; roleName: string; scopeType: 'NETWORK' | 'SCHOOL' | 'USER'; scopeReference?: string; };
export type User = { id: number; username: string; displayName: string; active: boolean; assignments: RoleAssignment[]; };
