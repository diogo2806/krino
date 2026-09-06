import { useEffect, useState, type FormEvent } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { SelectField } from '../form/SelectField';
import { TextField } from '../form/TextField';
import { Modal } from '../modal/Modal';
import type { Role, User } from './types';

type AssignmentDialogProps = { open: boolean; user?: User; roles: Role[]; onClose: () => void; onSaved: () => void; };

export function AssignmentDialog({ open, user, roles, onClose, onSaved }: AssignmentDialogProps) {
  const [roleId, setRoleId] = useState('');
  const [scopeType, setScopeType] = useState('NETWORK');
  const [scopeReference, setScopeReference] = useState('');
  const [error, setError] = useState('');

  useEffect(() => { setRoleId(roles[0]?.id.toString() ?? ''); setScopeType('NETWORK'); setScopeReference(''); setError(''); }, [open, roles]);
  const submit = async (event: FormEvent) => {
    event.preventDefault(); if (!user) return;
    try {
      await apiRequest(`/admin/users/${user.id}/roles`, { method: 'POST', body: JSON.stringify({ roleId: Number(roleId), scopeType, scopeReference: scopeReference || null }) });
      onSaved(); onClose();
    } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível atribuir o perfil.'); }
  };

  return <Modal open={open} title={`Atribuir perfil a ${user?.displayName ?? ''}`} onClose={onClose} footer={<><Button type="button" onClick={onClose}>Cancelar</Button><Button type="submit" form="assignment-form" variant="primary">Atribuir perfil</Button></>}><form id="assignment-form" className="form-stack" onSubmit={submit}><SelectField name="roleId" label="Perfil" value={roleId} onChange={(event) => setRoleId(event.target.value)} options={roles.map((role) => ({ value: role.id.toString(), label: role.name }))} /><SelectField name="scopeType" label="Escopo de acesso" value={scopeType} onChange={(event) => setScopeType(event.target.value)} options={[{ value: 'NETWORK', label: 'Rede municipal' }, { value: 'SCHOOL', label: 'Unidade escolar' }, { value: 'USER', label: 'Somente o próprio usuário' }]} />{scopeType === 'SCHOOL' ? <TextField name="scopeReference" label="Código da unidade escolar" value={scopeReference} onChange={(event) => setScopeReference(event.target.value)} required /> : null}{error ? <p className="form-error" role="alert">{error}</p> : null}</form></Modal>;
}
