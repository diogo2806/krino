import { useEffect, useState, type FormEvent } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { TextField } from '../form/TextField';
import { Modal } from '../modal/Modal';
import type { Permission, Role } from './types';

type RoleDialogProps = { open: boolean; role?: Role; permissions: Permission[]; onClose: () => void; onSaved: () => void; };

export function RoleDialog({ open, role, permissions, onClose, onSaved }: RoleDialogProps) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [selected, setSelected] = useState<number[]>([]);
  const [error, setError] = useState('');

  useEffect(() => { setName(role?.name ?? ''); setDescription(role?.description ?? ''); setSelected(role?.permissions.map((permission) => permission.id) ?? []); setError(''); }, [role, open]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    try {
      const saved = role
        ? await apiRequest<Role>(`/admin/roles/${role.id}`, { method: 'PUT', body: JSON.stringify({ name, description }) })
        : await apiRequest<Role>('/admin/roles', { method: 'POST', body: JSON.stringify({ name, description }) });
      await apiRequest(`/admin/roles/${saved.id}/permissions`, { method: 'PUT', body: JSON.stringify({ permissionIds: selected }) });
      onSaved(); onClose();
    } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível salvar o perfil.'); }
  };

  const toggle = (id: number) => setSelected((current) => current.includes(id) ? current.filter((value) => value !== id) : [...current, id]);

  return <Modal open={open} title={role ? 'Editar perfil' : 'Novo perfil'} onClose={onClose} footer={<><Button type="button" onClick={onClose}>Cancelar</Button><Button type="submit" form="role-form" variant="primary">Salvar perfil</Button></>}><form id="role-form" className="form-stack" onSubmit={submit}><TextField name="name" label="Nome do perfil" value={name} onChange={(event) => setName(event.target.value)} required /><label className="field"><span className="field__label">Descrição</span><textarea className="textarea" value={description} onChange={(event) => setDescription(event.target.value)} rows={3} /></label><fieldset className="permission-list"><legend>Permissões</legend>{permissions.map((permission) => <label className="check-field" key={permission.id}><input type="checkbox" checked={selected.includes(permission.id)} onChange={() => toggle(permission.id)} /><span><strong>{permission.name}</strong><small>{permission.description}</small></span></label>)}</fieldset>{error ? <p className="form-error" role="alert">{error}</p> : null}</form></Modal>;
}
