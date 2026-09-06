import { useEffect, useState, type FormEvent } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { TextField } from '../form/TextField';
import { Modal } from '../modal/Modal';
import type { Professional } from './types';

type Props = { open: boolean; schoolId: number; professional?: Professional; onClose: () => void; onSaved: () => void; };

export function ProfessionalDialog({ open, schoolId, professional, onClose, onSaved }: Props) {
  const [registration, setRegistration] = useState(''); const [name, setName] = useState(''); const [professionalType, setProfessionalType] = useState('Professor'); const [error, setError] = useState('');
  useEffect(() => { setRegistration(professional?.registration ?? ''); setName(professional?.name ?? ''); setProfessionalType(professional?.professionalType ?? 'Professor'); setError(''); }, [open, professional]);
  const submit = async (event: FormEvent) => { event.preventDefault(); setError(''); try { await apiRequest(professional ? `/secretaria/professionals/${professional.id}` : '/secretaria/professionals', { method: professional ? 'PUT' : 'POST', body: JSON.stringify({ schoolId, registration, name, professionalType }) }); onSaved(); onClose(); } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível salvar o profissional da educação.'); } };
  return <Modal open={open} title={professional ? 'Editar profissional da educação' : 'Novo profissional da educação'} onClose={onClose} footer={<><Button type="button" onClick={onClose}>Cancelar</Button><Button type="submit" variant="primary" form="professional-form">Salvar profissional</Button></>}><form id="professional-form" className="form-stack" onSubmit={submit}><TextField name="registration" label="Matrícula funcional" value={registration} onChange={(event) => setRegistration(event.target.value)} required /><TextField name="name" label="Nome completo" value={name} onChange={(event) => setName(event.target.value)} required /><TextField name="professionalType" label="Função" value={professionalType} onChange={(event) => setProfessionalType(event.target.value)} placeholder="Professor, educador, secretário..." required />{error ? <p className="form-error" role="alert">{error}</p> : null}</form></Modal>;
}
