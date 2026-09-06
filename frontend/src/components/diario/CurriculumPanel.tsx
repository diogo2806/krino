import { useState, type FormEvent } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { TextAreaField } from '../form/TextAreaField';
import { TextField } from '../form/TextField';
import { StateMessage } from '../state/StateMessage';
import type { CurriculumItem, Diary } from './types';

type Props = { diary: Diary; items: CurriculumItem[]; canManage: boolean; onReload: () => Promise<void>; };

export function CurriculumPanel({ diary, items, canManage, onReload }: Props) {
  const [source, setSource] = useState(''); const [code, setCode] = useState(''); const [description, setDescription] = useState(''); const [error, setError] = useState(''); const [message, setMessage] = useState(''); const [saving, setSaving] = useState(false);
  async function submit(event: FormEvent) { event.preventDefault(); setSaving(true); setError(''); setMessage(''); try { await apiRequest(`/diaries/${diary.id}/curriculum`, { method: 'POST', body: JSON.stringify({ source, code, description }) }); setSource(''); setCode(''); setDescription(''); await onReload(); setMessage('Referência curricular cadastrada.'); } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível cadastrar a referência curricular.'); } finally { setSaving(false); } }
  return <section className="diary-section"><div><h3>Currículo aplicável</h3><p className="muted">O KRINO não inventa conteúdo curricular. Esta lista reúne somente referências cadastradas a partir de conteúdo validado pela Administração para a etapa e o componente deste diário.</p></div>{error ? <StateMessage kind="error" title="Não foi possível concluir a operação" message={error} /> : null}{message ? <StateMessage kind="success" title="Currículo atualizado" message={message} /> : null}
    {items.length ? <div className="curriculum-list">{items.map((item) => <article className="curriculum-item" key={item.id}><div><strong>{item.code}</strong><span>{item.source}</span></div><p>{item.description}</p></article>)}</div> : <StateMessage title="Nenhuma referência curricular cadastrada" message="A Administração ainda não cadastrou referências validadas para esta etapa e componente." />}
    {canManage ? <form className="form-stack curriculum-form" onSubmit={submit}><h4>Cadastrar referência validada</h4><div className="form-grid"><TextField name="curriculumSource" label="Fonte" placeholder="Ex.: documento validado pela Administração" required value={source} onChange={(event) => setSource(event.target.value)} /><TextField name="curriculumCode" label="Código/identificação" required value={code} onChange={(event) => setCode(event.target.value)} /></div><TextAreaField name="curriculumDescription" label="Descrição" required value={description} onChange={(event) => setDescription(event.target.value)} /><Button type="submit" variant="primary" disabled={saving}>{saving ? 'Cadastrando...' : 'Cadastrar referência'}</Button></form> : null}
  </section>;
}
