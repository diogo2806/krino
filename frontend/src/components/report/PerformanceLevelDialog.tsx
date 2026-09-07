import { Plus, Trash2 } from 'lucide-react';
import { useEffect, useState } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { Modal } from '../modal/Modal';
import { StateMessage } from '../state/StateMessage';
import type { PerformanceLevel } from './types';

type Props = {
  open: boolean;
  assessmentId: number;
  levels: PerformanceLevel[];
  onClose: () => void;
  onSaved: (levels: PerformanceLevel[]) => void;
};

type EditableLevel = { label: string; minimumPercent: string; maximumPercent: string; };

const defaultLevel = (): EditableLevel => ({ label: '', minimumPercent: '0', maximumPercent: '100' });

export function PerformanceLevelDialog({ open, assessmentId, levels, onClose, onSaved }: Props) {
  const [rows, setRows] = useState<EditableLevel[]>([defaultLevel()]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    setRows(levels.length > 0
      ? levels.map((level) => ({ label: level.label, minimumPercent: level.minimumPercent.toString(), maximumPercent: level.maximumPercent.toString() }))
      : [defaultLevel()]);
    setError('');
  }, [open, levels]);

  const updateRow = (index: number, field: keyof EditableLevel, value: string) => {
    setRows((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, [field]: value } : row));
  };

  const save = async () => {
    setError('');
    const payload = rows.map((row) => ({
      label: row.label.trim(),
      minimumPercent: Number(row.minimumPercent),
      maximumPercent: Number(row.maximumPercent),
    }));
    if (payload.some((row) => !row.label || !Number.isFinite(row.minimumPercent) || !Number.isFinite(row.maximumPercent))) {
      setError('Preencha o nome e os percentuais mínimo e máximo de todas as faixas.');
      return;
    }
    if (payload.some((row) => row.minimumPercent < 0 || row.maximumPercent > 100 || row.minimumPercent > row.maximumPercent)) {
      setError('Cada faixa deve estar entre 0% e 100%, e o mínimo não pode superar o máximo.');
      return;
    }
    const ordered = [...payload].sort((left, right) => left.minimumPercent - right.minimumPercent);
    if (ordered.some((row, index) => index > 0 && row.minimumPercent <= ordered[index - 1].maximumPercent)) {
      setError('As faixas de desempenho não podem se sobrepor.');
      return;
    }
    setSaving(true);
    try {
      const saved = await apiRequest<PerformanceLevel[]>(`/reports/assessments/${assessmentId}/performance-levels`, {
        method: 'PUT',
        body: JSON.stringify(payload),
      });
      onSaved(saved);
      onClose();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Não foi possível salvar os níveis de desempenho.');
    } finally {
      setSaving(false);
    }
  };

  return <Modal open={open} title="Níveis de desempenho da avaliação" onClose={onClose} footer={<><Button type="button" variant="ghost" onClick={onClose}>Cancelar</Button><Button type="button" variant="primary" disabled={saving} onClick={() => void save()}>{saving ? 'Salvando...' : 'Salvar faixas'}</Button></>}>
    <div className="report-level-dialog">
      <p className="muted">Defina faixas sem sobreposição, entre 0% e 100%. A ordem exibida segue a ordem das linhas.</p>
      {error ? <StateMessage kind="error" title="Não foi possível salvar" message={error} /> : null}
      <div className="report-level-list">
        {rows.map((row, index) => <div className="report-level-row" key={index}>
          <label className="field"><span className="field__label">Nível</span><input className="input" value={row.label} maxLength={100} onChange={(event) => updateRow(index, 'label', event.target.value)} placeholder="Ex.: Adequado" /></label>
          <label className="field"><span className="field__label">Mínimo (%)</span><input className="input" type="number" min="0" max="100" step="0.01" value={row.minimumPercent} onChange={(event) => updateRow(index, 'minimumPercent', event.target.value)} /></label>
          <label className="field"><span className="field__label">Máximo (%)</span><input className="input" type="number" min="0" max="100" step="0.01" value={row.maximumPercent} onChange={(event) => updateRow(index, 'maximumPercent', event.target.value)} /></label>
          <Button type="button" variant="ghost" className="report-level-remove" disabled={rows.length === 1} aria-label={`Remover faixa ${index + 1}`} title="Remover faixa" onClick={() => setRows((current) => current.filter((_, rowIndex) => rowIndex !== index))}><Trash2 aria-hidden="true" size={18} />Remover</Button>
        </div>)}
      </div>
      <Button type="button" variant="secondary" onClick={() => setRows((current) => [...current, defaultLevel()])}><Plus aria-hidden="true" size={18} />Adicionar faixa</Button>
    </div>
  </Modal>;
}
