import { useEffect, useState, type FormEvent } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { TextField } from '../form/TextField';
import { Modal } from '../modal/Modal';
import type { Professional } from '../secretaria/types';
import type { UserLink } from './types';

type Props = { open: boolean; professional?: Professional; onClose: () => void; };

export function ProfessionalUserLinkDialog({ open, professional, onClose }: Props) {
  const [link, setLink] = useState<UserLink>();
  const [username, setUsername] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open || !professional) return;
    setError(''); setUsername('');
    void apiRequest<UserLink | undefined>(`/secretaria/professionals/${professional.id}/user-link`).then((value) => setLink(value)).catch((exception) => setError(exception instanceof Error ? exception.message : 'Não foi possível consultar o vínculo.'));
  }, [open, professional]);

  async function submit(event: FormEvent) {
    event.preventDefault(); if (!professional) return;
    setSaving(true); setError('');
    try { const next = await apiRequest<UserLink>(`/secretaria/professionals/${professional.id}/user-link`, { method: 'PUT', body: JSON.stringify({ username }) }); setLink(next); setUsername(''); }
    catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível vincular a conta.'); }
    finally { setSaving(false); }
  }

  async function unlink() {
    if (!professional) return;
    setSaving(true); setError('');
    try { await apiRequest(`/secretaria/professionals/${professional.id}/user-link`, { method: 'DELETE' }); setLink(undefined); }
    catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível remover o vínculo.'); }
    finally { setSaving(false); }
  }

  return <Modal open={open} title="Conta do professor" onClose={onClose} footer={<Button type="button" variant="ghost" onClick={onClose}>Fechar</Button>}>
    <div className="form-stack"><p><strong>{professional?.name}</strong></p>{link ? <div className="state-message state-message--success"><strong>Conta vinculada</strong><span>{link.displayName} · {link.username}</span><Button type="button" variant="danger" disabled={saving} onClick={() => void unlink()}>Remover vínculo</Button></div> : <p className="muted">Nenhuma conta está vinculada. O professor só poderá editar o diário quando sua conta de login estiver vinculada ao cadastro profissional.</p>}
      <form className="form-stack" onSubmit={submit}><TextField name="username" label={link ? 'Substituir pela conta' : 'Usuário de login'} placeholder="Ex.: maria.professora" required value={username} onChange={(event) => setUsername(event.target.value)} /><Button type="submit" variant="primary" disabled={saving}>{saving ? 'Salvando...' : link ? 'Substituir vínculo' : 'Vincular conta'}</Button></form>{error ? <p className="form-error">{error}</p> : null}</div>
  </Modal>;
}
