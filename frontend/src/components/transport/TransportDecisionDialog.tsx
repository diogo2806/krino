import { useState, type FormEvent } from 'react';
import { Button } from '../button/Button';
import { TextField } from '../form/TextField';
import { Modal } from '../modal/Modal';

type Decision = 'APPROVE' | 'ADJUST' | 'DENY';
type Props = { open: boolean; decision: Decision; saving: boolean; onClose: () => void; onConfirm: (payload: { reason?: string; validUntil?: string }) => Promise<void>; };

const copy: Record<Decision, { title: string; submit: string }> = {
  APPROVE: { title: 'Aprovar solicitação', submit: 'Aprovar' },
  ADJUST: { title: 'Solicitar ajuste', submit: 'Solicitar ajuste' },
  DENY: { title: 'Negar solicitação', submit: 'Negar' },
};

export function TransportDecisionDialog({ open, decision, saving, onClose, onConfirm }: Props) {
  const [reason, setReason] = useState('');
  const [validUntil, setValidUntil] = useState('');

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    await onConfirm(decision === 'APPROVE' ? { validUntil } : { reason });
  };

  return <Modal open={open} title={copy[decision].title} onClose={onClose} footer={<><Button type="button" onClick={onClose} disabled={saving}>Cancelar</Button><Button type="submit" form="transport-decision-form" variant="primary" disabled={saving}>{saving ? 'Salvando...' : copy[decision].submit}</Button></>}>
    <form id="transport-decision-form" className="form-stack" onSubmit={submit}>
      {decision === 'APPROVE' ? <TextField name="transportValidUntil" label="Validade da carteirinha" type="date" value={validUntil} onChange={(event) => setValidUntil(event.target.value)} required /> : <label className="field"><span className="field__label">Motivo</span><textarea className="textarea" value={reason} onChange={(event) => setReason(event.target.value)} required maxLength={1000} rows={5} /><span className="field__hint">Explique de forma objetiva o que precisa ser corrigido ou por que a solicitação foi negada.</span></label>}
    </form>
  </Modal>;
}
