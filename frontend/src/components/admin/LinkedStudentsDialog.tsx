import { useEffect, useMemo, useState } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { TextField } from '../form/TextField';
import { Modal } from '../modal/Modal';
import { StateMessage } from '../state/StateMessage';
import type { User } from './types';

type StudentOption = { id: number; registration: string; name: string; schoolName?: string; className?: string; };
type Props = { open: boolean; user?: User; onClose: () => void; };

export function LinkedStudentsDialog({ open, user, onClose }: Props) {
  const [linked, setLinked] = useState<StudentOption[]>([]); const [catalog, setCatalog] = useState<StudentOption[]>([]); const [search, setSearch] = useState(''); const [error, setError] = useState(''); const [loading, setLoading] = useState(false);

  async function load(term = search) {
    if (!user) return;
    setLoading(true); setError('');
    try {
      const [nextLinked, nextCatalog] = await Promise.all([
        apiRequest<StudentOption[]>(`/admin/users/${user.id}/linked-students`),
        apiRequest<StudentOption[]>(`/admin/users/${user.id}/linked-students/catalog${term.trim() ? `?search=${encodeURIComponent(term.trim())}` : ''}`),
      ]);
      setLinked(nextLinked); setCatalog(nextCatalog);
    } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível carregar os vínculos de estudantes.'); }
    finally { setLoading(false); }
  }

  useEffect(() => { if (open) { setSearch(''); void load(''); } }, [open, user?.id]);
  const linkedIds = useMemo(() => new Set(linked.map((item) => item.id)), [linked]);

  async function link(studentId: number) { if (!user) return; try { await apiRequest(`/admin/users/${user.id}/linked-students/${studentId}`, { method: 'POST' }); await load(); } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível vincular o estudante.'); } }
  async function unlink(studentId: number) { if (!user) return; try { await apiRequest(`/admin/users/${user.id}/linked-students/${studentId}`, { method: 'DELETE' }); await load(); } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível remover o vínculo.'); } }

  return <Modal open={open} title={`Vincular estudantes${user ? ` · ${user.displayName}` : ''}`} onClose={onClose} footer={<Button type="button" variant="ghost" onClick={onClose}>Fechar</Button>}><div className="form-stack">
    <form className="toolbar__filters" onSubmit={(event) => { event.preventDefault(); void load(); }}><TextField name="linkedStudentSearch" label="Buscar estudante" placeholder="Nome ou matrícula" value={search} onChange={(event) => setSearch(event.target.value)} /><Button type="submit">Buscar</Button></form>
    {error ? <StateMessage kind="error" title="Não foi possível concluir a operação" message={error} /> : null}
    <section><h3>Estudantes vinculados</h3>{linked.length === 0 ? <p className="muted">Nenhum estudante vinculado a esta conta.</p> : <div className="family-link-list">{linked.map((item) => <div key={item.id}><span><strong>{item.name}</strong><small>{item.registration} · {item.className ?? 'Sem turma ativa'} · {item.schoolName ?? 'Sem unidade ativa'}</small></span><Button type="button" variant="ghost" onClick={() => void unlink(item.id)}>Remover vínculo</Button></div>)}</div>}</section>
    <section><h3>Estudantes disponíveis</h3>{loading ? <p className="muted">Carregando...</p> : <div className="family-link-list">{catalog.filter((item) => !linkedIds.has(item.id)).map((item) => <div key={item.id}><span><strong>{item.name}</strong><small>{item.registration} · {item.className ?? 'Sem turma ativa'} · {item.schoolName ?? 'Sem unidade ativa'}</small></span><Button type="button" onClick={() => void link(item.id)}>Vincular</Button></div>)}</div>}</section>
  </div></Modal>;
}
