import { useState } from 'react';
import { SegmentedTabs } from '../navigation/SegmentedTabs';
import type { AccessContext } from '../workspace/types';
import { AuditDataPage } from './AuditDataPage';
import { UsersAccessPage } from './UsersAccessPage';

type Props = {
  context: AccessContext;
  onLogout: () => void;
};

type AdminView = 'access' | 'audit';

export function AdminWorkspace({ context, onLogout }: Props) {
  const canAccess = context.networkPermissions.some((permission) => ['USER_READ', 'ROLE_READ'].includes(permission));
  const canAudit = context.networkPermissions.some((permission) => ['AUDIT_READ', 'DATA_EXPORT'].includes(permission));
  const [view, setView] = useState<AdminView>(canAccess ? 'access' : 'audit');

  const tabs = [
    ...(canAccess ? [{ value: 'access' as const, label: 'Usuários e acessos' }] : []),
    ...(canAudit ? [{ value: 'audit' as const, label: 'Auditoria e dados' }] : []),
  ];

  return <>
    {tabs.length > 1 ? <div className="workspace-subnav"><SegmentedTabs label="Seções da Administração" tabs={tabs} value={view} onChange={setView} /></div> : null}
    {view === 'access' && canAccess ? <UsersAccessPage onLogout={onLogout} /> : <AuditDataPage context={context} onUnauthorized={onLogout} />}
  </>;
}
