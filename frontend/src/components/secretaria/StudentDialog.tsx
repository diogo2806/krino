import { useEffect, useState, type FormEvent } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { TextField } from '../form/TextField';
import { Modal } from '../modal/Modal';
import type { Student } from './types';

type Props = { open: boolean; schoolId: number; student?: Student; onClose: () => void; onSaved: () => void; };

export function StudentDialog({ open, schoolId, student, onClose, onSaved }: Props) {
  const [registration, setRegistration] = useState(''); const [name, setName] = useState(''); const [birthDate, setBirthDate] = useState(''); const [guardianName, setGuardianName] = useState(''); const [guardianProfession, setGuardianProfession] = useState(''); const [error, setError] = useState('');
  useEffect(() => { setRegistration(student?.registration ?? ''); setName(student?.name ?? ''); setBirthDate(student?.birthDate ?? ''); setGuardianName(student?.guardianName ?? ''); setGuardianProfession(student?.guardianProfession ?? ''); setError(''); }, [open, student]);
  const submit = async (event: FormEvent) => { event.preventDefault(); setError(''); try { await apiRequest(student ? `/secretaria/students/${student.id}` : '/secretaria/students', { method: student ? 'PUT' : 'POST', body: JSON.stringify({ schoolId, registration, name, birthDate: birthDate || null, guardianName: guardianName || null, guardianProfession: guardianProfession || null }) }); onSaved(); onClose(); } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível salvar o estudante.'); } };
  return <Modal open={open} title={student ? 'Editar estudante' : 'Novo estudante'} onClose={onClose} footer={<><Button type="button" onClick={onClose}>Cancelar</Button><Button type="submit" variant="primary" form="student-form">Salvar estudante</Button></>}><form id="student-form" className="form-stack" onSubmit={submit}><TextField name="registration" label="Matrícula" value={registration} onChange={(event) => setRegistration(event.target.value)} required /><TextField name="name" label="Nome completo" value={name} onChange={(event) => setName(event.target.value)} required /><TextField name="birthDate" label="Data de nascimento" type="date" value={birthDate} onChange={(event) => setBirthDate(event.target.value)} /><TextField name="guardianName" label="Responsável legal" value={guardianName} onChange={(event) => setGuardianName(event.target.value)} /><TextField name="guardianProfession" label="Profissão do responsável" value={guardianProfession} onChange={(event) => setGuardianProfession(event.target.value)} />{error ? <p className="form-error" role="alert">{error}</p> : null}</form></Modal>;
}
