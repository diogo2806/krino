import { LogOut, School, ShieldCheck } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { ApiError, apiRequest } from '../../shared/api/client';
import { UsersAccessPage } from '../admin/UsersAccessPage';
import { Button } from '../button/Button';
import { SecretariaEscolarPage } from '../secretaria/SecretariaEscolarPage';
import { StateMessage } from '../state/StateMessage';
import type { AccessContext } from './types';

type ApplicationWorkspaceProps = { onLogout: () => void; };
type Module = 'secretaria' | 'admin';

export function ApplicationWorkspace({ onLogout }: ApplicationWorkspaceProps) {
  const [context, setContext] = useState<AccessContext>();
  const [module, setModule] = useState<Module>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadContext = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const next = await apiRequest<AccessContext>('/auth/access-context');
      setContext(next);
      const canSecretaria = next.permissions.some((permission) => permission.startsWith('SCHOOL_'));
      const canAdmin = next.networkPermissions.some((permission) => ['USER_READ', 'ROLE_READ'].includes(permission));
      setModule((current) => current && ((current === 'secretaria' && canSecretaria) || (current === 'admin' && canAdmin)) ? current : canSecretaria ? 'secretaria' : canAdmin ? 'admin' : undefined);
    } catch (exception) {
      if (exception instanceof ApiError && exception.status === 401) {
        onLogout();
        return;
      }
      setError(exception instanceof Error ? exception.message : 'Não foi possível carregar seu contexto de acesso.');
    } finally {
      setLoading(false);
    }
  }, [onLogout]);

  useEffect(() => { void loadContext(); }, [loadContext]);

  if (loading) return <main className="app-page"><StateMessage title="Carregando seu acesso" message="Aguarde enquanto as permissões são verificadas." /></main>;
  if (error) return <main className="app-page"><StateMessage kind="error" title="Não foi possível abrir o KRINO" message={error} /><Button type="button" onClick={() => void loadContext()}>Tentar novamente</Button></main>;
  if (!context || !module) return <main className="app-page"><StateMessage title="Nenhum módulo disponível" message="Sua conta está ativa, mas ainda não possui permissão para um módulo do sistema." /><Button type="button" variant="ghost" onClick={onLogout}><LogOut aria-hidden="true" size={18} />Sair</Button></main>;

  const canSecretaria = context.permissions.some((permission) => permission.startsWith('SCHOOL_'));
  const canAdmin = context.networkPermissions.some((permission) => ['USER_READ', 'ROLE_READ'].includes(permission));

  return (
    <>
      <nav className="workspace-nav" aria-label="Módulos do KRINO">
        <div className="workspace-nav__modules">
          {canSecretaria ? <button className={module === 'secretaria' ? 'workspace-nav__item workspace-nav__item--active' : 'workspace-nav__item'} type="button" onClick={() => setModule('secretaria')}><School aria-hidden="true" size={18} />Secretaria Escolar</button> : null}
          {canAdmin ? <button className={module === 'admin' ? 'workspace-nav__item workspace-nav__item--active' : 'workspace-nav__item'} type="button" onClick={() => setModule('admin')}><ShieldCheck aria-hidden="true" size={18} />Administração</button> : null}
        </div>
        {module === 'secretaria' ? <Button type="button" variant="ghost" onClick={onLogout}><LogOut aria-hidden="true" size={18} />Sair</Button> : null}
      </nav>
      {module === 'secretaria' ? <SecretariaEscolarPage context={context} onUnauthorized={onLogout} /> : <UsersAccessPage onLogout={onLogout} />}
    </>
  );
}
