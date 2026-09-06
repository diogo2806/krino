import { useState, type FormEvent } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { TextField } from '../form/TextField';
import { Modal } from '../modal/Modal';
import type { User } from './types';

type PasswordDialogProps = { open: boolean; user?: User; onClose: () => void; };

export function PasswordDialog({ open, user, onClose }: PasswordDialogProps) {
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const submit = async (event: FormEvent) => {
    event.preventDefault(); if (!user) return;
    try { await apiRequest(`/admin/users/${user.id}/password`, { method: 'POST', body: JSON.stringify({ newPassword: password }) }); setPassword(''); onClose(); }
    catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível redefinir a senha.'); }
  };
  return <Modal open={open} title={`Redefinir senha de ${user?.displayName ?? ''}`} onClose={onClose} footer={<><Button type="button" onClick={onClose}>Cancelar</Button><Button type="submit" form="password-form" variant="primary">Redefinir senha</Button></>}><form id="password-form" className="form-stack" onSubmit={submit}><TextField name="newPassword" type="password" label="Nova senha" value={password} onChange={(event) => setPassword(event.target.value)} minLength={12} hint="Use pelo menos 12 caracteres." required />{error ? <p className="form-error" role="alert">{error}</p> : null}</form></Modal>;
}
