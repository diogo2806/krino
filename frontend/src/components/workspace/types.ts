export type SchoolScopeAccess = {
  schoolCode: string;
  permissions: string[];
};

export type AccessContext = {
  userId: number;
  username: string;
  displayName: string;
  permissions: string[];
  networkPermissions: string[];
  schoolScopes: string[];
  schoolAccess: SchoolScopeAccess[];
};
