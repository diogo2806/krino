import { useEffect, useState, type FormEvent } from 'react';
import { Button } from '../button/Button';
import { SelectField } from '../form/SelectField';
import { TextField } from '../form/TextField';
import { Modal } from '../modal/Modal';
import type { SlaPolicy } from './types';

type Props = {
  open: boolean;
  policy?: SlaPolicy;
  onClose: () => void;
  onSave: (severity: string, payload: { countingRule: string; warningMinutes?: number; businessTimezone?: string; workdayStart?: string; workdayEnd?: string; businessDays?: string; }) => Promise<void>;
};

const severityLabel: Record<string, string> = { CRITICAL: 'Crítico', MEDIUM: 'Médio', LOW: 'Baixo' };

export function SlaPolicyDialog({ open, policy, onClose, onSave }: Props) {
  const [countingRule, setCountingRule] = useState('UNDEFINED');
  const [warningMinutes, setWarningMinutes] = useState('');
  const [timezone, setTimezone] = useState('');
  const [workdayStart, setWorkdayStart] = useState('');
  const [workdayEnd, setWorkdayEnd] = useState('');
  const [businessDays, setBusinessDays] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open || !policy) return;
    setCountingRule(policy.countingRule);
    setWarningMinutes(policy.warningMinutes?.toString() ?? '');
    setTimezone(policy.businessTimezone ?? '');
    setWorkdayStart(policy.workdayStart?.slice(0, 5) ?? '');
    setWorkdayEnd(policy.workdayEnd?.slice(0, 5) ?? '');
    setBusinessDays(policy.businessDays?.split(',').filter(Boolean) ?? []);
    setError('');
  }, [open, policy]);

  if (!policy) return null;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (countingRule === 'BUSINESS' && (!timezone.trim() || !workdayStart || !workdayEnd || businessDays.length === 0)) {
      setError('Para horas úteis, informe fuso horário, jornada e ao menos um dia útil.');
      return;
    }
    setSaving(true); setError('');
    try {
      await onSave(policy.severity, {
        countingRule,
        warningMinutes: warningMinutes ? Number(warningMinutes) : undefined,
        businessTimezone: countingRule === 'BUSINESS' ? timezone.trim() : undefined,
        workdayStart: countingRule === 'BUSINESS' ? workdayStart : undefined,
        workdayEnd: countingRule === 'BUSINESS' ? workdayEnd : undefined,
        businessDays: countingRule === 'BUSINESS' ? businessDays.join(',') : undefined,
      });
      onClose();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Não foi possível salvar a regra de contagem.');
    } finally { setSaving(false); }
  };

  return <Modal open={open} title={`Regra de contagem · ${severityLabel[policy.severity]}`} onClose={onClose} footer={<><Button type="button" variant="ghost" onClick={onClose}>Cancelar</Button><Button type="submit" form="sla-policy-form" variant="primary" disabled={saving}>{saving ? 'Salvando...' : 'Salvar regra'}</Button></>}>
    <form id="sla-policy-form" className="support-form" onSubmit={submit}>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <div className="support-contract-sla">
        <strong>Prazos contratuais</strong>
        <span>Resposta: {policy.responseMinutes} min</span>
        <span>Solução: {policy.solutionMinutes} min</span>
      </div>
      <SelectField name="slaCountingRule" label="Regra de contagem" value={countingRule} onChange={(event) => setCountingRule(event.target.value)} options={[
        { value: 'UNDEFINED', label: 'Não definida · exibir somente os tempos contratuais' },
        { value: 'ELAPSED', label: 'Horas corridas' },
        { value: 'BUSINESS', label: 'Horas úteis conforme calendário configurado' },
      ]} />
      <TextField name="slaWarningMinutes" label="Avisar risco com antecedência (minutos)" type="number" min={1} value={warningMinutes} onChange={(event) => setWarningMinutes(event.target.value)} hint="Opcional. Define quando o chamado passa a aparecer como Em risco antes do vencimento." />
      {countingRule === 'BUSINESS' ? <div className="support-business-rule">
        <TextField name="slaTimezone" label="Fuso horário" value={timezone} onChange={(event) => setTimezone(event.target.value)} placeholder="Ex.: America/Sao_Paulo" hint="Use um identificador de fuso horário válido." required />
        <div className="form-grid">
          <TextField name="slaWorkdayStart" label="Início da jornada" type="time" value={workdayStart} onChange={(event) => setWorkdayStart(event.target.value)} required />
          <TextField name="slaWorkdayEnd" label="Fim da jornada" type="time" value={workdayEnd} onChange={(event) => setWorkdayEnd(event.target.value)} required />
        </div>
        <SelectField name="slaBusinessDays" label="Dias úteis" multiple size={7} value={businessDays} onChange={(event) => setBusinessDays(Array.from(event.target.selectedOptions, (option) => option.value))} options={[
          { value: 'MONDAY', label: 'Segunda-feira' }, { value: 'TUESDAY', label: 'Terça-feira' }, { value: 'WEDNESDAY', label: 'Quarta-feira' }, { value: 'THURSDAY', label: 'Quinta-feira' }, { value: 'FRIDAY', label: 'Sexta-feira' }, { value: 'SATURDAY', label: 'Sábado' }, { value: 'SUNDAY', label: 'Domingo' },
        ]} />
      </div> : null}
      <p className="muted support-form__note">A alteração vale apenas para novos chamados ou para chamados cuja criticidade seja alterada depois desta configuração. Chamados existentes preservam a regra usada quando foram abertos.</p>
    </form>
  </Modal>;
}
