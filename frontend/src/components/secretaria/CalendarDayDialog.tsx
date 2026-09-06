import { useEffect, useState, type FormEvent } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { TextField } from '../form/TextField';
import { Modal } from '../modal/Modal';
import type { CalendarDay } from './types';

type Props = { open: boolean; schoolId: number; day?: CalendarDay; onClose: () => void; onSaved: () => void; };

export function CalendarDayDialog({ open, schoolId, day, onClose, onSaved }: Props) {
  const [academicDate, setAcademicDate] = useState(''); const [schoolDay, setSchoolDay] = useState(true); const [description, setDescription] = useState(''); const [error, setError] = useState('');
  useEffect(() => { setAcademicDate(day?.academicDate ?? ''); setSchoolDay(day?.schoolDay ?? true); setDescription(day?.description ?? ''); setError(''); }, [open, day]);
  const submit = async (event: FormEvent) => { event.preventDefault(); setError(''); try { await apiRequest('/secretaria/calendar', { method: 'POST', body: JSON.stringify({ schoolId, academicDate, schoolDay, description: description || null }) }); onSaved(); onClose(); } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível salvar o dia do calendário.'); } };
  return <Modal open={open} title={day ? 'Editar calendário escolar' : 'Adicionar data ao calendário'} onClose={onClose} footer={<><Button type="button" onClick={onClose}>Cancelar</Button><Button type="submit" variant="primary" form="calendar-form">Salvar data</Button></>}><form id="calendar-form" className="form-stack" onSubmit={submit}><TextField name="academicDate" label="Data" type="date" value={academicDate} onChange={(event) => setAcademicDate(event.target.value)} required /><label className="check-field"><input type="checkbox" checked={schoolDay} onChange={(event) => setSchoolDay(event.target.checked)} /><span>Dia letivo</span></label><TextField name="description" label="Descrição" value={description} onChange={(event) => setDescription(event.target.value)} placeholder="Início do ano, feriado, sábado letivo..." />{error ? <p className="form-error" role="alert">{error}</p> : null}</form></Modal>;
}
