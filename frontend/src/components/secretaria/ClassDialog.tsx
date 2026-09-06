import { useEffect, useState, type FormEvent } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { SelectField } from '../form/SelectField';
import { TextField } from '../form/TextField';
import { Modal } from '../modal/Modal';
import type { SchoolClass } from './types';

type Props = { open: boolean; schoolId: number; academicYear: number; schoolClass?: SchoolClass; onClose: () => void; onSaved: () => void; };

export function ClassDialog({ open, schoolId, academicYear, schoolClass, onClose, onSaved }: Props) {
  const [name, setName] = useState(''); const [stage, setStage] = useState(''); const [shift, setShift] = useState('MANHÃ'); const [error, setError] = useState('');
  useEffect(() => { setName(schoolClass?.name ?? ''); setStage(schoolClass?.stage ?? ''); setShift(schoolClass?.shift ?? 'MANHÃ'); setError(''); }, [open, schoolClass]);
  const submit = async (event: FormEvent) => { event.preventDefault(); setError(''); try { await apiRequest(schoolClass ? `/secretaria/classes/${schoolClass.id}` : '/secretaria/classes', { method: schoolClass ? 'PUT' : 'POST', body: JSON.stringify({ schoolId, academicYear, name, stage, shift }) }); onSaved(); onClose(); } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível salvar a turma.'); } };
  return <Modal open={open} title={schoolClass ? 'Editar turma' : 'Nova turma'} onClose={onClose} footer={<><Button type="button" onClick={onClose}>Cancelar</Button><Button type="submit" variant="primary" form="class-form">Salvar turma</Button></>}><form id="class-form" className="form-stack" onSubmit={submit}><TextField name="name" label="Nome da turma" value={name} onChange={(event) => setName(event.target.value)} placeholder="5º A" required /><TextField name="stage" label="Etapa / ano" value={stage} onChange={(event) => setStage(event.target.value)} placeholder="5º ano do Ensino Fundamental" required /><SelectField name="shift" label="Turno" value={shift} onChange={(event) => setShift(event.target.value)} options={[{ value: 'MANHÃ', label: 'Manhã' }, { value: 'TARDE', label: 'Tarde' }, { value: 'NOITE', label: 'Noite' }, { value: 'INTEGRAL', label: 'Integral' }]} />{error ? <p className="form-error" role="alert">{error}</p> : null}</form></Modal>;
}
