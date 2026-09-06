import { useEffect, useState } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Modal } from '../modal/Modal';
import { StateMessage } from '../state/StateMessage';
import { DataTable, type DataColumn } from '../table/DataTable';
import type { Student } from './types';

type Movement = { id: number; enrollmentId: number; movementType: string; effectiveDate: string; destinationClassId?: number; notes?: string; createdBy: string; };
type Props = { student?: Student; onClose: () => void; };

const labels: Record<string, string> = { TRANSFER: 'Transferência', CLASS_CHANGE: 'Troca de turma', DEATH: 'Falecimento' };

export function MovementHistoryDialog({ student, onClose }: Props) {
  const [rows, setRows] = useState<Movement[]>([]); const [loading, setLoading] = useState(false); const [error, setError] = useState('');
  useEffect(() => { if (!student) return; setLoading(true); setError(''); apiRequest<Movement[]>(`/secretaria/enrollments/student/${student.id}/movements`).then(setRows).catch((exception) => setError(exception instanceof Error ? exception.message : 'Não foi possível consultar as movimentações.')).finally(() => setLoading(false)); }, [student]);
  const columns: DataColumn<Movement>[] = [{ key: 'type', header: 'Movimentação', render: (row) => labels[row.movementType] ?? row.movementType }, { key: 'date', header: 'Data de efeito', render: (row) => new Date(`${row.effectiveDate}T00:00:00`).toLocaleDateString('pt-BR') }, { key: 'notes', header: 'Observação', render: (row) => row.notes || '—' }, { key: 'actor', header: 'Registrado por', render: (row) => row.createdBy }];
  return <Modal open={Boolean(student)} title={`Movimentações · ${student?.name ?? ''}`} onClose={onClose}>{loading ? <StateMessage title="Carregando movimentações" /> : error ? <StateMessage kind="error" title="Não foi possível carregar" message={error} /> : rows.length ? <DataTable rows={rows} columns={columns} rowKey={(row) => row.id} /> : <StateMessage title="Sem movimentações" message="Não há transferência, troca de turma ou falecimento registrado para este estudante." />}</Modal>;
}
