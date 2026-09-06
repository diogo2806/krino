import { useEffect, useState, type FormEvent } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { SelectField } from '../form/SelectField';
import { TextField } from '../form/TextField';
import { Modal } from '../modal/Modal';
import type { SchoolClass, Student } from './types';

type Props = { open: boolean; students: Student[]; classes: SchoolClass[]; initialStudentId?: number; onClose: () => void; onSaved: () => void; };

export function EnrollmentDialog({ open, students, classes, initialStudentId, onClose, onSaved }: Props) {
  const [studentId, setStudentId] = useState(''); const [classId, setClassId] = useState(''); const [enrollmentType, setEnrollmentType] = useState('ENROLLMENT'); const [enrollmentDate, setEnrollmentDate] = useState(new Date().toISOString().slice(0, 10)); const [error, setError] = useState('');
  useEffect(() => { setStudentId(initialStudentId?.toString() ?? students[0]?.id.toString() ?? ''); setClassId(classes[0]?.id.toString() ?? ''); setEnrollmentType('ENROLLMENT'); setError(''); }, [open, students, classes, initialStudentId]);
  const submit = async (event: FormEvent) => { event.preventDefault(); setError(''); try { await apiRequest('/secretaria/enrollments', { method: 'POST', body: JSON.stringify({ studentId: Number(studentId), classId: Number(classId), enrollmentType, enrollmentDate }) }); onSaved(); onClose(); } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível registrar a matrícula.'); } };
  return <Modal open={open} title="Matrícula / rematrícula" onClose={onClose} footer={<><Button type="button" onClick={onClose}>Cancelar</Button><Button type="submit" variant="primary" form="enrollment-form">Registrar matrícula</Button></>}><form id="enrollment-form" className="form-stack" onSubmit={submit}><SelectField name="studentId" label="Estudante" value={studentId} onChange={(event) => setStudentId(event.target.value)} options={students.map((student) => ({ value: student.id.toString(), label: `${student.name} · ${student.registration}` }))} required /><SelectField name="classId" label="Turma" value={classId} onChange={(event) => setClassId(event.target.value)} options={classes.map((item) => ({ value: item.id.toString(), label: item.name }))} required /><SelectField name="enrollmentType" label="Tipo" value={enrollmentType} onChange={(event) => setEnrollmentType(event.target.value)} options={[{ value: 'ENROLLMENT', label: 'Matrícula' }, { value: 'REENROLLMENT', label: 'Rematrícula' }]} /><TextField name="enrollmentDate" label="Data da matrícula" type="date" value={enrollmentDate} onChange={(event) => setEnrollmentDate(event.target.value)} required />{error ? <p className="form-error" role="alert">{error}</p> : null}</form></Modal>;
}
