import { useEffect, useState } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { SelectField } from '../form/SelectField';
import { TextAreaField } from '../form/TextAreaField';
import { TextField } from '../form/TextField';
import { StateMessage } from '../state/StateMessage';
import type { CurriculumItem, Diary, Planning } from './types';

type Props = { diary: Diary; curriculum: CurriculumItem[]; };

export function PlanningPanel({ diary, curriculum }: Props) {
  const [planning, setPlanning] = useState<Planning[]>([]); const [period, setPeriod] = useState('1'); const [title, setTitle] = useState(''); const [description, setDescription] = useState(''); const [selectedItems, setSelectedItems] = useState<number[]>([]);
  const [error, setError] = useState(''); const [message, setMessage] = useState(''); const [saving, setSaving] = useState(false);

  async function load(currentPeriod = Number(period)) { const next = await apiRequest<Planning[]>(`/diaries/${diary.id}/planning`); setPlanning(next); apply(next.find((item) => item.period === currentPeriod)); }
  function apply(item?: Planning) { setTitle(item?.title ?? ''); setDescription(item?.description ?? ''); setSelectedItems(item?.curriculumItems.map((value) => value.id) ?? []); }
  useEffect(() => { setPeriod('1'); void load(1).catch((exception) => setError(exception instanceof Error ? exception.message : 'Não foi possível consultar o planejamento.')); }, [diary.id]);

  function changePeriod(value: string) { setPeriod(value); apply(planning.find((item) => item.period === Number(value))); setError(''); setMessage(''); }
  async function save() {
    setSaving(true); setError(''); setMessage('');
    try { await apiRequest(`/diaries/${diary.id}/planning/${period}`, { method: 'PUT', body: JSON.stringify({ title, description, curriculumItemIds: selectedItems }) }); await load(Number(period)); setMessage('Planejamento pedagógico salvo.'); }
    catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível salvar o planejamento.'); }
    finally { setSaving(false); }
  }

  return <section className="diary-section"><div className="diary-section__header"><div><h3>Planejamento pedagógico</h3><p className="muted">Relacione o planejamento às referências curriculares aplicáveis cadastradas pela Administração.</p></div><SelectField name="planningPeriod" label="Período" value={period} onChange={(event) => changePeriod(event.target.value)} options={[1,2,3,4].map((item) => ({ value: item.toString(), label: `${item}º período` }))} /></div>
    {error ? <StateMessage kind="error" title="Não foi possível concluir a operação" message={error} /> : null}{message ? <StateMessage kind="success" title="Planejamento atualizado" message={message} /> : null}
    <div className="form-stack"><TextField name="planningTitle" label="Título" disabled={!diary.editable} value={title} onChange={(event) => setTitle(event.target.value)} /><TextAreaField name="planningDescription" label="Planejamento" disabled={!diary.editable} value={description} onChange={(event) => setDescription(event.target.value)} />
      <fieldset className="permission-list" disabled={!diary.editable}><legend>Referências curriculares</legend>{curriculum.length ? curriculum.map((item) => <label className="check-field" key={item.id}><input type="checkbox" checked={selectedItems.includes(item.id)} onChange={(event) => setSelectedItems((current) => event.target.checked ? [...current, item.id] : current.filter((id) => id !== item.id))} /><span><strong>{item.code} · {item.source}</strong><small>{item.description}</small></span></label>) : <p className="muted">Nenhuma referência curricular foi cadastrada para esta etapa/componente.</p>}</fieldset>
      {diary.editable ? <div className="diary-actions"><Button type="button" variant="primary" disabled={saving || !title.trim() || !description.trim()} onClick={() => void save()}>{saving ? 'Salvando...' : 'Salvar planejamento'}</Button></div> : null}</div>
  </section>;
}
