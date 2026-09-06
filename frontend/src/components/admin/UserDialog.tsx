import { useEffect, useState, type FormEvent } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { TextField } from '../form/TextField';
import { Modal } from '../modal/Modal';
import type { User } from './types';

type UserDialogProps = { open: boolean; user?: User; onClose: () => void; onSaved: () => void; };

export function UserDialog({ open, user, onClose, onSaved }: UserDialogProps) {
  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [active, setActive] = useState(true);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => { setUsername(user?.username ?? ''); setDisplayName(user?.displayName ?? ''); setPassword(''); setActive(user?.active ?? true); setError(''); }, [user, open]);

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('');
    try {
      if (user) {
        await apiRequest(`/admin/users/${user.id}`, { method: 'PUT', body: JSON.stringify({ username, displayName, active }) });
      } else {
        await apiRequest('/admin/users', { method: 'POST', body: JSON.stringify({ username, displayName, password }) });
      }
      onSaved(); onClose();
    } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível salvar o usuário.'); }
    finally { setSaving(false); }
  };

  return <Modal open={open} title={user ? 'Editar usuário' : 'Novo usuário'} onClose={onClose} footer={<><Button type="button" onClick={onClose}>Cancelar</Button><Button type="submit" variant="primary" form="user-form" disabled={saving}>{saving ? 'Salvando...' : 'Salvar usuário'}</Button></>}><form id="user-form" className="form-stack" onSubmit={submit}><TextField name="displayName" label="Nome" value={displayName} onChange={(event) => setDisplayName(event.target.value)} required /><TextField name="username" label="Usuário" value={username} onChange={(event) => setUsername(event.target.value)} required />{user ? <label className="check-field"><input type="checkbox" checked={active} onChange={(event) => setActive(event.target.checked)} /><span>Conta ativa</span></label> : <TextField name="password" label="Senha inicial" type="password" value={password} onChange={(event) => setPassword(event.target.value)} minLength={12} hint="Use pelo menos 12 caracteres." required />}{error ? <p className="form-error" role="alert">{error}</p> : null}</form></Modal>;
}
