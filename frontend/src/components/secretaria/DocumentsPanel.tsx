import { FileText } from 'lucide-react';
import { useState } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { FilterBar } from '../filter/FilterBar';
import { SelectField } from '../form/SelectField';
import { StateMessage } from '../state/StateMessage';
import { DocumentPreviewDialog } from './DocumentPreviewDialog';
import type { SchoolClass, SchoolDocument, Student } from './types';

type Props = { schoolId: number; year: number; classes: SchoolClass[]; students: Student[]; allowed: boolean; };

const documentOptions = [
  ['ENROLLMENT_BOOK', 'Livro de matrícula'], ['ATTENDANCE_DECLARATION', 'Declaração de frequência'], ['ENROLLMENT_DECLARATION', 'Declaração de matrícula'], ['BOLSA_FAMILIA_DECLARATION', 'Declaração para Bolsa Família'], ['GUARDIAN_PROFESSION_DECLARATION', 'Declaração com profissão do responsável'], ['PROVISIONAL_TRANSFER', 'Transferência provisória'], ['INDIVIDUAL_RECORD', 'Ficha individual'], ['FINAL_RESULT_MINUTES', 'Ata de resultado final'], ['SCHOOL_TRANSCRIPT', 'Histórico escolar'], ['CLASS_STUDENT_LIST', 'Lista de estudantes por turma'], ['BLANK_ATTENDANCE_LIST', 'Ata/lista de presença em branco'], ['BLANK_GRADE_SHEET', 'Planilha de notas em branco'], ['BIMESTRAL_COUNCIL_LIST', 'Lista para conselho escolar bimestral'],
] as const;

const studentTypes = new Set(['ATTENDANCE_DECLARATION', 'ENROLLMENT_DECLARATION', 'BOLSA_FAMILIA_DECLARATION', 'GUARDIAN_PROFESSION_DECLARATION', 'PROVISIONAL_TRANSFER', 'INDIVIDUAL_RECORD', 'SCHOOL_TRANSCRIPT']);
const classTypes = new Set(['FINAL_RESULT_MINUTES', 'CLASS_STUDENT_LIST', 'BLANK_ATTENDANCE_LIST', 'BLANK_GRADE_SHEET', 'BIMESTRAL_COUNCIL_LIST']);

export function DocumentsPanel({ schoolId, year, classes, students, allowed }: Props) {
  const [type, setType] = useState('ENROLLMENT_BOOK'); const [studentId, setStudentId] = useState(''); const [classId, setClassId] = useState(''); const [period, setPeriod] = useState('1'); const [document, setDocument] = useState<SchoolDocument>(); const [loading, setLoading] = useState(false); const [error, setError] = useState('');
  if (!allowed) return <StateMessage title="Emissão não permitida" message="Seu perfil não possui permissão para emitir documentos escolares nesta unidade." />;
  const generate = async () => { setLoading(true); setError(''); try { const params = new URLSearchParams({ schoolId: schoolId.toString(), year: year.toString() }); if (studentTypes.has(type)) params.set('studentId', studentId); if (classTypes.has(type)) params.set('classId', classId); if (type === 'BIMESTRAL_COUNCIL_LIST') params.set('period', period); const next = await apiRequest<SchoolDocument>(`/secretaria/documents/${type}?${params}`); setDocument(next); } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível emitir o documento.'); } finally { setLoading(false); } };
  return <section className="content-panel"><FilterBar actions={<Button type="button" variant="primary" disabled={loading || (studentTypes.has(type) && !studentId) || (classTypes.has(type) && !classId)} onClick={() => void generate()}><FileText aria-hidden="true" size={18} />{loading ? 'Gerando...' : 'Gerar documento'}</Button>}><SelectField name="documentType" label="Documento" value={type} onChange={(event) => setType(event.target.value)} options={documentOptions.map(([value, label]) => ({ value, label }))} />{studentTypes.has(type) ? <SelectField name="documentStudent" label="Estudante" value={studentId} onChange={(event) => setStudentId(event.target.value)} options={[{ value: '', label: 'Selecione' }, ...students.map((student) => ({ value: student.id.toString(), label: `${student.name} · ${student.registration}` }))]} /> : null}{classTypes.has(type) ? <SelectField name="documentClass" label="Turma" value={classId} onChange={(event) => setClassId(event.target.value)} options={[{ value: '', label: 'Selecione' }, ...classes.map((item) => ({ value: item.id.toString(), label: item.name }))]} /> : null}{type === 'BIMESTRAL_COUNCIL_LIST' ? <SelectField name="documentPeriod" label="Bimestre" value={period} onChange={(event) => setPeriod(event.target.value)} options={[1, 2, 3, 4].map((value) => ({ value: value.toString(), label: `${value}º bimestre` }))} /> : null}</FilterBar>{error ? <StateMessage kind="error" title="Não foi possível emitir" message={error} /> : <p className="muted">O documento é montado somente com dados persistidos no KRINO. Quando notas ou frequências ainda não foram lançadas, essa ausência é indicada no próprio documento.</p>}<DocumentPreviewDialog document={document} onClose={() => setDocument(undefined)} /></section>;
}
