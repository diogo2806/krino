import { useEffect, useState, type FormEvent } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { SelectField } from '../form/SelectField';
import { TextField } from '../form/TextField';
import { Modal } from '../modal/Modal';
import type { Component, Professional, SchoolClass } from './types';

type Props = { open: boolean; schoolClass?: SchoolClass; professionals: Professional[]; components: Component[]; onClose: () => void; onSaved: () => void; };

export function TeacherAssignmentDialog({ open, schoolClass, professionals, components, onClose, onSaved }: Props) {
  const [professionalId, setProfessionalId] = useState(''); const [componentId, setComponentId] = useState(''); const [validFrom, setValidFrom] = useState(`${schoolClass?.academicYear ?? new Date().getFullYear()}-01-01`); const [validUntil, setValidUntil] = useState(''); const [error, setError] = useState('');
  useEffect(() => { setProfessionalId(professionals[0]?.id.toString() ?? ''); setComponentId(components[0]?.id.toString() ?? ''); setValidFrom(`${schoolClass?.academicYear ?? new Date().getFullYear()}-01-01`); setValidUntil(''); setError(''); }, [open, schoolClass, professionals, components]);
  const submit = async (event: FormEvent) => { event.preventDefault(); if (!schoolClass) return; setError(''); try { await apiRequest('/secretaria/teacher-assignments', { method: 'POST', body: JSON.stringify({ professionalId: Number(professionalId), classId: schoolClass.id, componentId: Number(componentId), validFrom, validUntil: validUntil || null }) }); onSaved(); onClose(); } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível atribuir o professor.'); } };
  return <Modal open={open} title={`Atribuir professor · ${schoolClass?.name ?? ''}`} onClose={onClose} footer={<><Button type="button" onClick={onClose}>Cancelar</Button><Button type="submit" variant="primary" form="teacher-assignment-form">Atribuir professor</Button></>}><form id="teacher-assignment-form" className="form-stack" onSubmit={submit}><SelectField name="professionalId" label="Profissional da educação" value={professionalId} onChange={(event) => setProfessionalId(event.target.value)} options={professionals.filter((professional) => professional.active).map((professional) => ({ value: professional.id.toString(), label: `${professional.name} · ${professional.professionalType}` }))} required /><SelectField name="componentId" label="Componente curricular" value={componentId} onChange={(event) => setComponentId(event.target.value)} options={components.map((component) => ({ value: component.id.toString(), label: component.name }))} required /><TextField name="validFrom" label="Início da vigência" type="date" value={validFrom} onChange={(event) => setValidFrom(event.target.value)} required /><TextField name="validUntil" label="Fim da vigência" type="date" value={validUntil} onChange={(event) => setValidUntil(event.target.value)} />{error ? <p className="form-error" role="alert">{error}</p> : null}</form></Modal>;
}
