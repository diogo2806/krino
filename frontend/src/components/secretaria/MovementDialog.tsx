import { useEffect, useState, type FormEvent } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { SelectField } from '../form/SelectField';
import { TextField } from '../form/TextField';
import { Modal } from '../modal/Modal';
import type { Enrollment, SchoolClass } from './types';

type Props = { open: boolean; enrollment?: Enrollment; classes: SchoolClass[]; onClose: () => void; onSaved: () => void; };

export function MovementDialog({ open, enrollment, classes, onClose, onSaved }: Props) {
  const [movementType, setMovementType] = useState('TRANSFER'); const [effectiveDate, setEffectiveDate] = useState(new Date().toISOString().slice(0, 10)); const [destinationClassId, setDestinationClassId] = useState(''); const [notes, setNotes] = useState(''); const [error, setError] = useState('');
  useEffect(() => { setMovementType('TRANSFER'); setEffectiveDate(new Date().toISOString().slice(0, 10)); setDestinationClassId(classes.find((item) => item.id !== enrollment?.classId)?.id.toString() ?? ''); setNotes(''); setError(''); }, [open, enrollment, classes]);
  const submit = async (event: FormEvent) => { event.preventDefault(); if (!enrollment) return; setError(''); try { await apiRequest(`/secretaria/enrollments/${enrollment.id}/movements`, { method: 'POST', body: JSON.stringify({ movementType, effectiveDate, destinationClassId: movementType === 'CLASS_CHANGE' ? Number(destinationClassId) : null, notes: notes || null }) }); onSaved(); onClose(); } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível registrar a movimentação.'); } };
  return <Modal open={open} title={`Movimentar ${enrollment?.studentName ?? 'estudante'}`} onClose={onClose} footer={<><Button type="button" onClick={onClose}>Cancelar</Button><Button type="submit" variant="primary" form="movement-form">Registrar movimentação</Button></>}><form id="movement-form" className="form-stack" onSubmit={submit}><SelectField name="movementType" label="Tipo da movimentação" value={movementType} onChange={(event) => setMovementType(event.target.value)} options={[{ value: 'TRANSFER', label: 'Transferência' }, { value: 'CLASS_CHANGE', label: 'Troca de turma' }, { value: 'DEATH', label: 'Falecimento' }]} /><TextField name="effectiveDate" label="Data de efeito" type="date" value={effectiveDate} onChange={(event) => setEffectiveDate(event.target.value)} required />{movementType === 'CLASS_CHANGE' ? <SelectField name="destinationClassId" label="Turma de destino" value={destinationClassId} onChange={(event) => setDestinationClassId(event.target.value)} options={classes.filter((item) => item.id !== enrollment?.classId).map((item) => ({ value: item.id.toString(), label: item.name }))} required /> : null}<label className="field"><span className="field__label">Observação</span><textarea className="textarea" value={notes} onChange={(event) => setNotes(event.target.value)} rows={3} /></label>{error ? <p className="form-error" role="alert">{error}</p> : null}</form></Modal>;
}
