import { useEffect, useState, type FormEvent } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { SelectField } from '../form/SelectField';
import { TextField } from '../form/TextField';
import { StateMessage } from '../state/StateMessage';
import type { Assessment, Diary, RosterStudent } from './types';

type Props = { diary: Diary; roster: RosterStudent[]; };

export function AssessmentPanel({ diary, roster }: Props) {
  const [assessments, setAssessments] = useState<Assessment[]>([]);
  const [selectedId, setSelectedId] = useState<number>();
  const [title, setTitle] = useState(''); const [period, setPeriod] = useState('1'); const [date, setDate] = useState(''); const [maxScore, setMaxScore] = useState('');
  const [grades, setGrades] = useState<Record<number, string>>({}); const [observations, setObservations] = useState<Record<number, string>>({});
  const [error, setError] = useState(''); const [message, setMessage] = useState(''); const [saving, setSaving] = useState(false);

  async function load(preferId?: number) {
    const next = await apiRequest<Assessment[]>(`/diaries/${diary.id}/assessments`); setAssessments(next);
    const target = next.find((item) => item.id === preferId) ?? next.find((item) => item.id === selectedId);
    if (target) selectAssessment(target);
  }
  useEffect(() => { setSelectedId(undefined); setTitle(''); setDate(''); setGrades({}); setObservations({}); void load().catch((exception) => setError(exception instanceof Error ? exception.message : 'Não foi possível consultar avaliações.')); }, [diary.id]);

  function selectAssessment(item: Assessment) {
    setSelectedId(item.id); setTitle(item.title); setPeriod(item.period.toString()); setDate(item.assessmentDate); setMaxScore(item.maxScore?.toString() ?? '');
    const nextGrades: Record<number, string> = {}; const nextObs: Record<number, string> = {};
    item.grades.forEach((grade) => { nextGrades[grade.enrollmentId] = grade.score?.toString() ?? ''; nextObs[grade.enrollmentId] = grade.observation ?? ''; });
    setGrades(nextGrades); setObservations(nextObs);
  }

  function newAssessment() { setSelectedId(undefined); setTitle(''); setPeriod('1'); setDate(''); setMaxScore(''); setGrades({}); setObservations({}); setError(''); setMessage(''); }

  async function saveAssessment(event: FormEvent) {
    event.preventDefault(); setSaving(true); setError(''); setMessage('');
    try {
      const payload = { period: Number(period), title, assessmentDate: date, maxScore: maxScore ? Number(maxScore) : null };
      const saved = selectedId ? await apiRequest<Assessment>(`/diaries/${diary.id}/assessments/${selectedId}`, { method: 'PUT', body: JSON.stringify(payload) }) : await apiRequest<Assessment>(`/diaries/${diary.id}/assessments`, { method: 'POST', body: JSON.stringify(payload) });
      setSelectedId(saved.id); await load(saved.id); setMessage('Avaliação salva.');
    } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível salvar a avaliação.'); }
    finally { setSaving(false); }
  }

  async function saveGrades() {
    if (!selectedId) return; setSaving(true); setError(''); setMessage('');
    try {
      await apiRequest(`/diaries/${diary.id}/assessments/${selectedId}/grades`, { method: 'PUT', body: JSON.stringify(roster.map((student) => ({ enrollmentId: student.enrollmentId, score: grades[student.enrollmentId] === '' || grades[student.enrollmentId] == null ? null : Number(grades[student.enrollmentId]), observation: observations[student.enrollmentId] ?? '' }))) });
      await load(selectedId); setMessage('Notas atualizadas.');
    } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível salvar as notas.'); }
    finally { setSaving(false); }
  }

  return <section className="diary-section"><div className="diary-section__header"><div><h3>Notas e rendimento</h3><p className="muted">As notas são registradas por avaliação. Nenhuma média oficial é calculada sem regra pedagógica confirmada.</p></div>{diary.editable ? <Button type="button" variant="ghost" onClick={newAssessment}>Nova avaliação</Button> : null}</div>
    {error ? <StateMessage kind="error" title="Não foi possível concluir a operação" message={error} /> : null}{message ? <StateMessage kind="success" title="Dados atualizados" message={message} /> : null}
    {assessments.length ? <div className="diary-assessment-list">{assessments.map((item) => <button key={item.id} type="button" className={selectedId === item.id ? 'diary-assessment-card diary-assessment-card--active' : 'diary-assessment-card'} onClick={() => selectAssessment(item)}><strong>{item.title}</strong><span>{item.period}º período · {new Date(`${item.assessmentDate}T00:00:00`).toLocaleDateString('pt-BR')}</span></button>)}</div> : <StateMessage title="Nenhuma avaliação cadastrada" message="Cadastre uma avaliação para registrar notas e acompanhar o rendimento." />}
    {diary.editable ? <form className="form-stack diary-evaluation-form" onSubmit={saveAssessment}><div className="form-grid"><TextField name="assessmentTitle" label="Avaliação" required value={title} onChange={(event) => setTitle(event.target.value)} /><SelectField name="assessmentPeriod" label="Período" value={period} onChange={(event) => setPeriod(event.target.value)} options={[1,2,3,4].map((item) => ({ value: item.toString(), label: `${item}º período` }))} /><TextField name="assessmentDate" type="date" label="Data" required value={date} onChange={(event) => setDate(event.target.value)} /><TextField name="maxScore" type="number" min="0.01" step="0.01" label="Pontuação máxima (opcional)" value={maxScore} onChange={(event) => setMaxScore(event.target.value)} /></div><Button type="submit" variant="primary" disabled={saving}>{saving ? 'Salvando...' : selectedId ? 'Salvar avaliação' : 'Criar avaliação'}</Button></form> : null}
    {selectedId ? <><h4>Notas dos estudantes</h4><div className="table-wrap"><table className="data-table"><thead><tr><th>Estudante</th><th>Nota</th><th>Observação</th></tr></thead><tbody>{roster.map((student) => <tr key={student.enrollmentId}><td><strong>{student.name}</strong><small>{student.registration}</small></td><td><input className="input diary-grade-input" type="number" min="0" step="0.01" disabled={!diary.editable} value={grades[student.enrollmentId] ?? ''} onChange={(event) => setGrades((current) => ({ ...current, [student.enrollmentId]: event.target.value }))} aria-label={`Nota de ${student.name}`} /></td><td><input className="input" disabled={!diary.editable} value={observations[student.enrollmentId] ?? ''} onChange={(event) => setObservations((current) => ({ ...current, [student.enrollmentId]: event.target.value }))} aria-label={`Observação de ${student.name}`} /></td></tr>)}</tbody></table></div>{diary.editable ? <div className="diary-actions"><Button type="button" variant="primary" disabled={saving} onClick={() => void saveGrades()}>{saving ? 'Salvando...' : 'Salvar notas'}</Button></div> : null}</> : null}
  </section>;
}
