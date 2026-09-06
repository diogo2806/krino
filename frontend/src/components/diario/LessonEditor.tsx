import { useEffect, useMemo, useState } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { SelectField } from '../form/SelectField';
import { TextAreaField } from '../form/TextAreaField';
import { TextField } from '../form/TextField';
import { StateMessage } from '../state/StateMessage';
import type { Diary, Lesson, RosterStudent } from './types';

type Props = { diary: Diary; roster: RosterStudent[]; };

export function LessonEditor({ diary, roster }: Props) {
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [slot, setSlot] = useState('1');
  const [content, setContent] = useState('');
  const [planningNotes, setPlanningNotes] = useState('');
  const [attendance, setAttendance] = useState<Record<number, string>>({});
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  const defaultAttendance = useMemo(() => Object.fromEntries(roster.map((student) => [student.enrollmentId, 'PRESENT'])), [roster]);

  useEffect(() => { setAttendance(defaultAttendance); }, [defaultAttendance, diary.id]);

  useEffect(() => {
    setMessage(''); setError('');
    void apiRequest<Lesson[]>(`/diaries/${diary.id}/lessons?from=${date}&to=${date}`).then((lessons) => {
      const lesson = lessons.find((item) => item.lessonSlot === Number(slot));
      setContent(lesson?.content ?? ''); setPlanningNotes(lesson?.planningNotes ?? '');
      const next = { ...defaultAttendance };
      lesson?.attendance.forEach((item) => { next[item.enrollmentId] = item.status; });
      setAttendance(next);
    }).catch((exception) => setError(exception instanceof Error ? exception.message : 'Não foi possível consultar a aula.'));
  }, [diary.id, date, slot, defaultAttendance]);

  async function save() {
    setSaving(true); setError(''); setMessage('');
    try {
      await apiRequest(`/diaries/${diary.id}/lessons/${date}/${slot}`, { method: 'PUT', body: JSON.stringify({ content, planningNotes, attendance: roster.map((student) => ({ enrollmentId: student.enrollmentId, status: attendance[student.enrollmentId] ?? 'PRESENT' })) }) });
      setMessage('Diário salvo para a data e aula selecionadas.');
    } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível salvar o diário.'); }
    finally { setSaving(false); }
  }

  return <section className="diary-section">
    <div className="form-grid diary-lesson-meta"><TextField name="lessonDate" type="date" label="Data" min={diary.validFrom} max={diary.validUntil} value={date} onChange={(event) => setDate(event.target.value)} /><TextField name="lessonSlot" type="number" min={1} label="Aula nº" value={slot} onChange={(event) => setSlot(event.target.value)} /></div>
    {!diary.editable ? <StateMessage title="Consulta do diário" message="Você pode consultar os lançamentos, mas somente o professor responsável ou um perfil administrativo autorizado pode editá-los." /> : null}
    {error ? <StateMessage kind="error" title="Lançamento bloqueado" message={error} /> : null}{message ? <StateMessage kind="success" title="Diário atualizado" message={message} /> : null}
    <div className="form-grid"><TextAreaField name="content" label="Conteúdo ministrado" placeholder="Descreva o conteúdo trabalhado nesta aula." disabled={!diary.editable} value={content} onChange={(event) => setContent(event.target.value)} /><TextAreaField name="planningNotes" label="Registro de planejamento da aula" placeholder="Registre orientações ou observações pedagógicas da aula." disabled={!diary.editable} value={planningNotes} onChange={(event) => setPlanningNotes(event.target.value)} /></div>
    <h3>Frequência</h3>{roster.length === 0 ? <StateMessage title="Turma sem estudantes ativos" message="Não existem matrículas ativas para registrar frequência." /> : <div className="table-wrap"><table className="data-table"><thead><tr><th>Estudante</th><th>Frequência</th></tr></thead><tbody>{roster.map((student) => <tr key={student.enrollmentId}><td><strong>{student.name}</strong><small>{student.registration}</small></td><td><SelectField name={`attendance-${student.enrollmentId}`} aria-label={`Frequência de ${student.name}`} label="Situação" disabled={!diary.editable} value={attendance[student.enrollmentId] ?? 'PRESENT'} onChange={(event) => setAttendance((current) => ({ ...current, [student.enrollmentId]: event.target.value }))} options={[{ value: 'PRESENT', label: 'Presente' }, { value: 'ABSENT', label: 'Falta' }, { value: 'EXCUSED', label: 'Falta justificada' }]} /></td></tr>)}</tbody></table></div>}
    {diary.editable ? <div className="diary-actions"><Button type="button" variant="primary" disabled={saving || roster.length === 0} onClick={() => void save()}>{saving ? 'Salvando...' : 'Salvar diário'}</Button></div> : null}
  </section>;
}
