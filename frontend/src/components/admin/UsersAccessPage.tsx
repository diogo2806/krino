import { KeyRound, LogOut, Plus, Shield, UserCog } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError, apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { ConfirmDialog } from '../dialog/ConfirmDialog';
import { SelectField } from '../form/SelectField';
import { TextField } from '../form/TextField';
import { PageHeader } from '../layout/PageHeader';
import { StateMessage } from '../state/StateMessage';
import { AssignmentDialog } from './AssignmentDialog';
import { PasswordDialog } from './PasswordDialog';
import { RoleDialog } from './RoleDialog';
import type { Permission, Role, User } from './types';
import { UserDialog } from './UserDialog';

type UsersAccessPageProps = { onLogout: () => void; };
type ConfirmAction = { title: string; message: string; label: string; danger?: boolean; run: () => Promise<void>; };
type AccessContext = { userId: number; username: string; displayName: string; networkPermissions: string[]; };

const manualSections = [
  { title: 'Finalidade', content: 'Administrar contas, perfis, permissões e escopos de acesso do KRINO.' },
  { title: 'Campos e filtros', content: 'Buscar localiza usuários por nome ou login. Perfil e escopo refinam a lista. Na área de perfis, as permissões definem o que cada perfil pode executar.' },
  { title: 'Botões e ações', content: 'Novo usuário cria uma conta. Editar altera dados e estado. Atribuir perfil define perfil e escopo. Redefinir senha troca a credencial. Perfis e permissões permite criar e configurar perfis.' },
  { title: 'Regras', content: 'A autorização é validada também no backend. Escopo de unidade não concede acesso a outra escola. Perfis-base podem ser ajustados, mas não excluídos.' },
  { title: 'Permissões', content: 'Somente ações autorizadas no escopo de Rede municipal são apresentadas. O backend continua validando cada operação independentemente da interface.' },
  { title: 'Fluxos', content: 'Crie o usuário, atribua um perfil e selecione o escopo adequado. Para alterar capacidades de um grupo, edite o perfil e suas permissões.' },
  { title: 'Mensagens e estados', content: 'A tela diferencia carregamento, lista vazia, erro de comunicação e acesso negado. Ações destrutivas pedem confirmação.' },
];

export function UsersAccessPage({ onLogout }: UsersAccessPageProps) {
  const [users, setUsers] = useState<User[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [networkPermissions, setNetworkPermissions] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [denied, setDenied] = useState(false);
  const [error, setError] = useState('');
  const [view, setView] = useState<'users' | 'roles'>('users');
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [scopeFilter, setScopeFilter] = useState('');
  const [userDialog, setUserDialog] = useState<{ open: boolean; user?: User }>({ open: false });
  const [assignmentUser, setAssignmentUser] = useState<User>();
  const [passwordUser, setPasswordUser] = useState<User>();
  const [roleDialog, setRoleDialog] = useState<{ open: boolean; role?: Role }>({ open: false });
  const [confirm, setConfirm] = useState<ConfirmAction>();

  const has = useCallback((code: string, current = networkPermissions) => current.includes(code), [networkPermissions]);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    setDenied(false);
    try {
      const context = await apiRequest<AccessContext>('/auth/access-context');
      const currentPermissions = context.networkPermissions;
      setNetworkPermissions(currentPermissions);

      const canReadUsers = currentPermissions.includes('USER_READ');
      const canReadRoles = currentPermissions.includes('ROLE_READ');
      if (!canReadUsers && !canReadRoles) {
        setUsers([]);
        setRoles([]);
        setPermissions([]);
        setDenied(true);
        return;
      }

      const [nextUsers, nextRoles, nextPermissions] = await Promise.all([
        canReadUsers ? apiRequest<User[]>('/admin/users') : Promise.resolve([]),
        canReadRoles ? apiRequest<Role[]>('/admin/roles') : Promise.resolve([]),
        canReadRoles ? apiRequest<Permission[]>('/admin/permissions') : Promise.resolve([]),
      ]);
      setUsers(nextUsers);
      setRoles(nextRoles);
      setPermissions(nextPermissions);
      setView((current) => current === 'users' && !canReadUsers && canReadRoles ? 'roles' : current === 'roles' && !canReadRoles && canReadUsers ? 'users' : current);
    } catch (exception) {
      if (exception instanceof ApiError && exception.status === 401) {
        onLogout();
        return;
      }
      if (exception instanceof ApiError && exception.status === 403) {
        setDenied(true);
        return;
      }
      setError(exception instanceof Error ? exception.message : 'Não foi possível carregar usuários e acessos.');
    } finally {
      setLoading(false);
    }
  }, [onLogout]);

  useEffect(() => { void load(); }, [load]);

  const filteredUsers = useMemo(() => users.filter((user) => {
    const term = search.trim().toLowerCase();
    const matchesSearch = !term || user.displayName.toLowerCase().includes(term) || user.username.toLowerCase().includes(term);
    const matchesRole = !roleFilter || user.assignments.some((assignment) => assignment.roleId.toString() === roleFilter);
    const matchesScope = !scopeFilter || user.assignments.some((assignment) => assignment.scopeType === scopeFilter);
    return matchesSearch && matchesRole && matchesScope;
  }), [users, search, roleFilter, scopeFilter]);

  const executeConfirm = async () => {
    if (!confirm) return;
    try {
      await confirm.run();
      setConfirm(undefined);
      await load();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Não foi possível concluir a ação.');
      setConfirm(undefined);
    }
  };

  const canReadUsers = has('USER_READ');
  const canWriteUsers = has('USER_WRITE');
  const canReadRoles = has('ROLE_READ');
  const canWriteRoles = has('ROLE_WRITE');
  const canAssignScope = has('SCOPE_ASSIGN') && canReadRoles;

  return (
    <main className="app-page">
      <PageHeader
        eyebrow="Administração"
        title="Usuários e acessos"
        description="Contas, perfis, permissões e escopos de acesso."
        manualSections={manualSections}
        actions={<Button type="button" variant="ghost" onClick={onLogout}><LogOut aria-hidden="true" size={18} />Sair</Button>}
      />

      {!loading && !denied ? (
        <nav className="segmented" aria-label="Seções de usuários e acessos">
          {canReadUsers ? <button className={view === 'users' ? 'segmented__item segmented__item--active' : 'segmented__item'} type="button" onClick={() => setView('users')}>Usuários</button> : null}
          {canReadRoles ? <button className={view === 'roles' ? 'segmented__item segmented__item--active' : 'segmented__item'} type="button" onClick={() => setView('roles')}>Perfis e permissões</button> : null}
        </nav>
      ) : null}

      {error ? <StateMessage kind="error" title="Não foi possível concluir a operação" message={error} /> : null}
      {loading ? <StateMessage title="Carregando usuários e acessos" message="Aguarde enquanto os dados são consultados." /> : null}
      {!loading && denied ? <StateMessage title="Acesso não permitido" message="Sua conta não possui permissão municipal para consultar usuários, perfis ou permissões." /> : null}

      {!loading && !denied && view === 'users' && canReadUsers ? (
        <section className="content-panel">
          <div className="toolbar">
            <div className="toolbar__filters">
              <TextField name="search" label="Buscar" placeholder="Nome ou usuário" value={search} onChange={(event) => setSearch(event.target.value)} />
              <SelectField name="roleFilter" label="Perfil" value={roleFilter} onChange={(event) => setRoleFilter(event.target.value)} options={[{ value: '', label: 'Todos' }, ...roles.map((role) => ({ value: role.id.toString(), label: role.name }))]} />
              <SelectField name="scopeFilter" label="Escopo" value={scopeFilter} onChange={(event) => setScopeFilter(event.target.value)} options={[{ value: '', label: 'Todos' }, { value: 'NETWORK', label: 'Rede municipal' }, { value: 'SCHOOL', label: 'Unidade escolar' }, { value: 'USER', label: 'Usuário' }]} />
            </div>
            {canWriteUsers ? <Button type="button" variant="primary" onClick={() => setUserDialog({ open: true })}><Plus aria-hidden="true" size={18} />Novo usuário</Button> : null}
          </div>

          {filteredUsers.length === 0 ? <StateMessage title="Nenhum usuário encontrado" message="Ajuste os filtros ou crie um novo usuário quando possuir permissão para isso." /> : (
            <div className="table-wrap">
              <table className="data-table">
                <thead><tr><th>Usuário</th><th>Perfil e escopo</th><th>Status</th><th>Ações</th></tr></thead>
                <tbody>
                  {filteredUsers.map((user) => (
                    <tr key={user.id}>
                      <td><strong>{user.displayName}</strong><small>{user.username}</small></td>
                      <td><div className="chip-list">{user.assignments.length ? user.assignments.map((assignment) => (
                        <span className="chip" key={assignment.id}>
                          {assignment.roleName} · {assignment.scopeType === 'NETWORK' ? 'Rede' : assignment.scopeType === 'SCHOOL' ? `Escola ${assignment.scopeReference}` : 'Usuário'}
                          {canAssignScope ? <button type="button" aria-label={`Remover ${assignment.roleName}`} title="Remover perfil" onClick={() => setConfirm({ title: 'Remover perfil', message: `Remover o perfil ${assignment.roleName} de ${user.displayName}?`, label: 'Remover perfil', danger: true, run: () => apiRequest(`/admin/users/${user.id}/roles/${assignment.id}`, { method: 'DELETE' }) })}>×</button> : null}
                        </span>
                      )) : <span className="muted">Sem perfil atribuído</span>}</div></td>
                      <td><span className={user.active ? 'status-badge status-badge--active' : 'status-badge'}>{user.active ? 'Ativo' : 'Inativo'}</span></td>
                      <td><div className="row-actions">
                        {canWriteUsers ? <Button type="button" variant="ghost" onClick={() => setUserDialog({ open: true, user })}><UserCog aria-hidden="true" size={16} />Editar</Button> : null}
                        {canAssignScope ? <Button type="button" variant="ghost" onClick={() => setAssignmentUser(user)}><Shield aria-hidden="true" size={16} />Atribuir perfil</Button> : null}
                        {canWriteUsers ? <Button type="button" variant="ghost" onClick={() => setPasswordUser(user)}><KeyRound aria-hidden="true" size={16} />Redefinir senha</Button> : null}
                        {canWriteUsers && user.active ? <Button type="button" variant="danger" onClick={() => setConfirm({ title: 'Desativar usuário', message: `Desativar a conta de ${user.displayName}? O histórico será preservado.`, label: 'Desativar', danger: true, run: () => apiRequest(`/admin/users/${user.id}`, { method: 'DELETE' }) })}>Desativar</Button> : null}
                      </div></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      ) : null}

      {!loading && !denied && view === 'roles' && canReadRoles ? (
        <section className="content-panel">
          <div className="toolbar">
            <div><h2>Perfis e permissões</h2><p className="muted">Perfis-base podem ser ajustados; perfis personalizados também podem ser criados.</p></div>
            {canWriteRoles ? <Button type="button" variant="primary" onClick={() => setRoleDialog({ open: true })}><Plus aria-hidden="true" size={18} />Novo perfil</Button> : null}
          </div>
          <div className="table-wrap">
            <table className="data-table">
              <thead><tr><th>Perfil</th><th>Permissões</th><th>Tipo</th><th>Ações</th></tr></thead>
              <tbody>{roles.map((role) => (
                <tr key={role.id}>
                  <td><strong>{role.name}</strong><small>{role.description}</small></td>
                  <td>{role.permissions.length ? role.permissions.map((permission) => permission.name).join(', ') : 'Sem permissões'}</td>
                  <td>{role.systemRole ? 'Perfil-base' : 'Personalizado'}</td>
                  <td><div className="row-actions">
                    {canWriteRoles ? <Button type="button" variant="ghost" onClick={() => setRoleDialog({ open: true, role })}>Editar</Button> : null}
                    {canWriteRoles && !role.systemRole ? <Button type="button" variant="danger" onClick={() => setConfirm({ title: 'Excluir perfil', message: `Excluir o perfil ${role.name}?`, label: 'Excluir perfil', danger: true, run: () => apiRequest(`/admin/roles/${role.id}`, { method: 'DELETE' }) })}>Excluir</Button> : null}
                  </div></td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        </section>
      ) : null}

      <UserDialog open={userDialog.open} user={userDialog.user} onClose={() => setUserDialog({ open: false })} onSaved={load} />
      <AssignmentDialog open={Boolean(assignmentUser)} user={assignmentUser} roles={roles} onClose={() => setAssignmentUser(undefined)} onSaved={load} />
      <PasswordDialog open={Boolean(passwordUser)} user={passwordUser} onClose={() => setPasswordUser(undefined)} />
      <RoleDialog open={roleDialog.open} role={roleDialog.role} permissions={permissions} onClose={() => setRoleDialog({ open: false })} onSaved={load} />
      <ConfirmDialog open={Boolean(confirm)} title={confirm?.title ?? ''} message={confirm?.message ?? ''} confirmLabel={confirm?.label ?? 'Confirmar'} danger={confirm?.danger} onConfirm={() => void executeConfirm()} onClose={() => setConfirm(undefined)} />
    </main>
  );
}
