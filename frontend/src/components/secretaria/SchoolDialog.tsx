import { useEffect, useState, type FormEvent } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { TextField } from '../form/TextField';
import { Modal } from '../modal/Modal';
import type { School } from './types';

type Props = { open: boolean; school?: School; onClose: () => void; onSaved: () => void; };

export function SchoolDialog({ open, school, onClose, onSaved }: Props) {
  const [code, setCode] = useState(''); const [name, setName] = useState(''); const [address, setAddress] = useState(''); const [error, setError] = useState('');
  useEffect(() => { setCode(school?.code ?? ''); setName(school?.name ?? ''); setAddress(school?.address ?? ''); setError(''); }, [open, school]);
  const submit = async (event: FormEvent) => { event.preventDefault(); setError(''); try { await apiRequest(school ? `/secretaria/schools/${school.id}` : '/secretaria/schools', { method: school ? 'PUT' : 'POST', body: JSON.stringify({ code, name, address: address || null }) }); onSaved(); onClose(); } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível salvar a unidade escolar.'); } };
  return <Modal open={open} title={school ? 'Editar unidade escolar' : 'Nova unidade escolar'} onClose={onClose} footer={<><Button type="button" onClick={onClose}>Cancelar</Button><Button type="submit" variant="primary" form="school-form">Salvar unidade</Button></>}><form id="school-form" className="form-stack" onSubmit={submit}><TextField name="code" label="Código da unidade" value={code} onChange={(event) => setCode(event.target.value)} required /><TextField name="name" label="Nome da unidade escolar" value={name} onChange={(event) => setName(event.target.value)} required /><TextField name="address" label="Endereço" value={address} onChange={(event) => setAddress(event.target.value)} />{error ? <p className="form-error" role="alert">{error}</p> : null}</form></Modal>;
}
