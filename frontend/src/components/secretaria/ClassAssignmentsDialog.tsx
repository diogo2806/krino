import { Plus, Trash2 } from 'lucide-react';
import { useEffect, useState } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { ConfirmDialog } from '../dialog/ConfirmDialog';
import { Modal } from '../modal/Modal';
import { StateMessage } from '../state/StateMessage';
import { DataTable, type DataColumn } from '../table/DataTable';
import { TeacherAssignmentDialog } from './TeacherAssignmentDialog';
import type { Component, Professional, SchoolClass, TeacherAssignment } from './types';

type Props = { schoolClass?: SchoolClass; professionals: Professional[]; components: Component[]; canWrite: boolean; onClose: () => void; };

export function ClassAssignmentsDialog({ schoolClass, professionals, components, canWrite, onClose }: Props) {
  const [rows, setRows] = useState<TeacherAssignment[]>([]); const [loading, setLoading] = useState(false); const [error, setError] = useState(''); const [adding, setAdding] = useState(false); const [removeId, setRemoveId] = useState<number>();
  const load = async () => { if (!schoolClass) return; setLoading(true); setError(''); try { setRows(await apiRequest<TeacherAssignment[]>(`/secretaria/teacher-assignments?classId=${schoolClass.id}`)); } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível consultar as atribuições.'); } finally { setLoading(false); } };
  useEffect(() => { void load(); }, [schoolClass?.id]);
  const remove = async () => { if (!removeId) return; try { await apiRequest(`/secretaria/teacher-assignments/${removeId}`, { method: 'DELETE' }); setRemoveId(undefined); await load(); } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível remover a atribuição.'); setRemoveId(undefined); } };
  const columns: DataColumn<TeacherAssignment>[] = [{ key: 'professional', header: 'Professor', render: (row) => row.professionalName }, { key: 'component', header: 'Componente', render: (row) => row.componentName }, { key: 'period', header: 'Vigência', render: (row) => `${row.validFrom} até ${row.validUntil || 'sem data final'}` }, { key: 'actions', header: 'Ações', render: (row) => canWrite ? <Button type="button" variant="danger" onClick={() => setRemoveId(row.id)}><Trash2 aria-hidden="true" size={16} />Remover</Button> : null }];
  return <><Modal open={Boolean(schoolClass)} title={`Professores e componentes · ${schoolClass?.name ?? ''}`} onClose={onClose} footer={canWrite ? <Button type="button" variant="primary" onClick={() => setAdding(true)}><Plus aria-hidden="true" size={18} />Atribuir professor</Button> : undefined}>{loading ? <StateMessage title="Carregando atribuições" /> : error ? <StateMessage kind="error" title="Não foi possível carregar" message={error} /> : rows.length ? <DataTable rows={rows} columns={columns} rowKey={(row) => row.id} /> : <StateMessage title="Nenhum professor atribuído" message="A turma ainda não possui vínculo de professor com componente curricular." />}</Modal><TeacherAssignmentDialog open={adding} schoolClass={schoolClass} professionals={professionals} components={components} onClose={() => setAdding(false)} onSaved={() => void load()} /><ConfirmDialog open={Boolean(removeId)} title="Remover atribuição" message="Remover este professor do componente no período informado?" confirmLabel="Remover atribuição" danger onConfirm={() => void remove()} onClose={() => setRemoveId(undefined)} /></>;
}
