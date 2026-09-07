import { useEffect, useState, type FormEvent } from 'react';
import { Button } from '../button/Button';
import { SelectField } from '../form/SelectField';
import { TextAreaField } from '../form/TextAreaField';
import { TextField } from '../form/TextField';
import { Modal } from '../modal/Modal';
import type { AssessmentCatalog, AssessmentStage } from './types';

type AssessmentPayload = {
  name: string;
  stage: AssessmentStage;
  academicYear: number;
  gradeStage: string;
  componentId?: number;
  applicationManual?: string;
  instructions?: string;
};

type Props = {
  open: boolean;
  catalog?: AssessmentCatalog;
  onClose: () => void;
  onSave: (payload: AssessmentPayload) => Promise<void>;
};

export function AssessmentDialog({ open, catalog, onClose, onSave }: Props) {
  const currentYear = new Date().getFullYear();
  const [name, setName] = useState('');
  const [stage, setStage] = useState<AssessmentStage>('DIAGNOSTIC');
  const [academicYear, setAcademicYear] = useState(currentYear.toString());
  const [gradeStage, setGradeStage] = useState('');
  const [componentId, setComponentId] = useState('');
  const [applicationManual, setApplicationManual] = useState('');
  const [instructions, setInstructions] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setName(''); setStage('DIAGNOSTIC'); setAcademicYear(currentYear.toString()); setGradeStage(''); setComponentId(''); setApplicationManual(''); setInstructions('');
  }, [open, currentYear]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSaving(true);
    try {
      await onSave({
        name: name.trim(),
        stage,
        academicYear: Number(academicYear),
        gradeStage: gradeStage.trim(),
        componentId: componentId ? Number(componentId) : undefined,
        applicationManual: applicationManual.trim() || undefined,
        instructions: instructions.trim() || undefined,
      });
      onClose();
    } finally { setSaving(false); }
  };

  return <Modal open={open} title="Nova Avaliação em Rede" onClose={onClose} footer={<><Button type="button" variant="ghost" onClick={onClose}>Cancelar</Button><Button type="submit" form="assessment-form" variant="primary" disabled={saving}>{saving ? 'Salvando...' : 'Criar avaliação'}</Button></>}>
    <form id="assessment-form" className="form-grid" onSubmit={(event) => void submit(event)}>
      <TextField name="assessmentName" label="Nome da avaliação" required value={name} onChange={(event) => setName(event.target.value)} placeholder="Ex.: Diagnóstica 2026" />
      <SelectField name="assessmentStage" label="Etapa" required value={stage} onChange={(event) => setStage(event.target.value as AssessmentStage)} options={[{ value: 'DIAGNOSTIC', label: 'Diagnóstica' }, { value: 'MONITORING', label: 'Monitoramento' }, { value: 'FINAL', label: 'Final' }]} />
      <TextField name="assessmentYear" label="Ano letivo" required type="number" min="2000" max="2200" value={academicYear} onChange={(event) => setAcademicYear(event.target.value)} />
      <TextField name="assessmentGradeStage" label="Etapa/ano-série" required value={gradeStage} onChange={(event) => setGradeStage(event.target.value)} placeholder="Ex.: 6º ano" hint="Deve corresponder à etapa cadastrada nas turmas que participarão da avaliação." />
      <SelectField name="assessmentComponent" label="Componente curricular" value={componentId} onChange={(event) => setComponentId(event.target.value)} options={[{ value: '', label: 'Avaliação multidisciplinar' }, ...(catalog?.components ?? []).map((component) => ({ value: component.id.toString(), label: component.name }))]} />
      <TextAreaField name="assessmentManual" label="Manual do Aplicador" value={applicationManual} onChange={(event) => setApplicationManual(event.target.value)} placeholder="Informe orientações específicas para aplicação." />
      <TextAreaField name="assessmentInstructions" label="Orientações adicionais" value={instructions} onChange={(event) => setInstructions(event.target.value)} placeholder="Regras, cuidados de sigilo e instruções complementares." />
    </form>
  </Modal>;
}
