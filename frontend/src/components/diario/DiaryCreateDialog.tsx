import { useEffect, useState, type FormEvent } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { SelectField } from '../form/SelectField';
import { TextField } from '../form/TextField';
import { Modal } from '../modal/Modal';
import type { Component, Professional, SchoolClass } from '../secretaria/types';
import { ProfessionalUserLinkDialog } from './ProfessionalUserLinkDialog';

type Props = { open: boolean; schoolClass?: SchoolClass; components: Component[]; professionals: Professional[]; onClose: () => void; onSaved: () => Promise<void>; };

const modes = [
  { value: 'EARLY_CHILDHOOD', label: 'Educação Infantil' }, { value: 'LITERACY', label: 'Criança Alfabetizada' }, { value: 'EARLY_YEARS', label: 'Anos Iniciais' }, { value: 'FINAL_YEARS', label: 'Anos Finais' }, { value: 'EJA', label: 'EJA' },
];

export function DiaryCreateDialog({ open, schoolClass, components, professionals, onClose, onSaved }: Props) {
  const [mode, setMode] = useState('EARLY_YEARS'); const [componentId, setComponentId] = useState(''); const [professionalId, setProfessionalId] = useState(''); const [validFrom, setValidFrom] = useState(''); const [validUntil, setValidUntil] = useState('');
  const [error, setError] = useState(''); const [saving, setSaving] = useState(false); const [linkOpen, setLinkOpen] = useState(false);
  useEffect(() => { if (open) { const year = schoolClass?.academicYear ?? new Date().getFullYear(); setMode('EARLY_YEARS'); setComponentId(''); setProfessionalId(''); setValidFrom(`${year}-01-01`); setValidUntil(`${year}-12-31`); setError(''); } }, [open, schoolClass]);
  const selectedProfessional = professionals.find((item) => item.id.toString() === professionalId);

  async function submit(event: FormEvent) {
    event.preventDefault(); if (!schoolClass) return; setSaving(true); setError('');
    try { await apiRequest('/diaries', { method: 'POST', body: JSON.stringify({ classId: schoolClass.id, componentId: componentId ? Number(componentId) : null, mode, responsibleProfessionalId: Number(professionalId), validFrom, validUntil: validUntil || null }) }); await onSaved(); onClose(); }
    catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível criar o diário.'); }
    finally { setSaving(false); }
  }

  const componentRequired = mode === 'FINAL_YEARS' || mode === 'EJA';
  return <><Modal open={open} title="Novo Diário de Classe" onClose={onClose} footer={<><Button type="button" variant="ghost" onClick={onClose}>Cancelar</Button><Button type="submit" form="diary-create-form" variant="primary" disabled={saving}>{saving ? 'Criando...' : 'Criar diário'}</Button></>}>
    <form id="diary-create-form" className="form-stack" onSubmit={submit}><p className="muted">Turma: <strong>{schoolClass?.name ?? 'Selecione uma turma'}</strong></p><SelectField name="mode" label="Modalidade" value={mode} onChange={(event) => setMode(event.target.value)} options={modes} /><SelectField name="component" label={componentRequired ? 'Componente curricular' : 'Componente curricular (opcional)'} required={componentRequired} value={componentId} onChange={(event) => setComponentId(event.target.value)} options={[{ value: '', label: componentRequired ? 'Selecione' : 'Diário integrado' }, ...components.map((item) => ({ value: item.id.toString(), label: item.name }))]} />
      <div className="form-stack"><SelectField name="professional" label="Professor responsável" required value={professionalId} onChange={(event) => setProfessionalId(event.target.value)} options={[{ value: '', label: 'Selecione' }, ...professionals.filter((item) => item.active).map((item) => ({ value: item.id.toString(), label: `${item.name} · ${item.registration}` }))]} />{selectedProfessional ? <Button type="button" variant="ghost" onClick={() => setLinkOpen(true)}>Vincular conta do professor</Button> : null}</div>
      <div className="form-grid"><TextField name="validFrom" type="date" label="Início da vigência" required value={validFrom} onChange={(event) => setValidFrom(event.target.value)} /><TextField name="validUntil" type="date" label="Fim da vigência" value={validUntil} onChange={(event) => setValidUntil(event.target.value)} /></div>{error ? <p className="form-error">{error}</p> : null}
    </form></Modal><ProfessionalUserLinkDialog open={linkOpen} professional={selectedProfessional} onClose={() => setLinkOpen(false)} /></>;
}
