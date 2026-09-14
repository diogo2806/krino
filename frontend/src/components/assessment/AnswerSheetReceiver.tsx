import { FileUp, Send } from 'lucide-react';
import { useMemo, useState, type ChangeEvent } from 'react';
import { Button } from '../button/Button';
import { SelectField } from '../form/SelectField';
import { TextField } from '../form/TextField';
import { StateMessage } from '../state/StateMessage';
import type { AnswerSheetPayload, AssignmentView, QuestionView } from './types';

type SourceType = 'IMPORT' | 'MANUAL' | 'ONLINE';

type Props = {
  questions: QuestionView[];
  assignments: AssignmentView[];
  onSubmit: (sourceType: SourceType, sheets: AnswerSheetPayload[]) => Promise<void>;
};

type PreviewRow = {
  line: number;
  identifier: string;
  answers: Record<number, string>;
  issues: string[];
};

type FilePreview = {
  fileName: string;
  rows: PreviewRow[];
  errors: string[];
};

function normalizedHeader(value: string) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().toLowerCase().replace(/[\s_-]+/g, '');
}

function countDelimiter(line: string, delimiter: string) {
  let count = 0;
  let quoted = false;
  for (let index = 0; index < line.length; index += 1) {
    const char = line[index];
    if (char === '"') {
      if (quoted && line[index + 1] === '"') index += 1;
      else quoted = !quoted;
    } else if (!quoted && char === delimiter) count += 1;
  }
  return count;
}

function parseDelimitedLine(line: string, delimiter: string) {
  const cells: string[] = [];
  let current = '';
  let quoted = false;

  for (let index = 0; index < line.length; index += 1) {
    const char = line[index];
    if (char === '"') {
      if (quoted && line[index + 1] === '"') {
        current += '"';
        index += 1;
      } else quoted = !quoted;
    } else if (char === delimiter && !quoted) {
      cells.push(current.trim());
      current = '';
    } else {
      current += char;
    }
  }
  cells.push(current.trim());
  return cells;
}

function questionSequenceFromHeader(header: string) {
  const normalized = normalizedHeader(header);
  const value = normalized.replace(/^questao/, '').replace(/^q/, '');
  const sequence = Number(value);
  return Number.isInteger(sequence) && sequence > 0 ? sequence : undefined;
}

function sheetFromIdentifier(identifier: string, answers: Record<number, string>): AnswerSheetPayload {
  const value = identifier.trim();
  return value.toUpperCase().startsWith('AV') ? { labelCode: value, answers } : { registration: value, answers };
}

export function AnswerSheetReceiver({ questions, assignments, onSubmit }: Props) {
  const [sourceType, setSourceType] = useState<SourceType>('IMPORT');
  const [studentAssignmentId, setStudentAssignmentId] = useState('');
  const [onlineAccessCode, setOnlineAccessCode] = useState('');
  const [answers, setAnswers] = useState<Record<number, string>>({});
  const [preview, setPreview] = useState<FilePreview>();
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const questionSequences = useMemo(() => new Set(questions.map((question) => question.sequenceNumber)), [questions]);

  const changeSource = (value: string) => {
    setSourceType(value as SourceType);
    setError('');
    setAnswers({});
    setStudentAssignmentId('');
    setOnlineAccessCode('');
    setPreview(undefined);
  };

  const setAnswer = (sequenceNumber: number, value: string) => {
    setAnswers((current) => ({ ...current, [sequenceNumber]: value.toUpperCase() }));
    setError('');
  };

  const validateAnswers = () => {
    if (questions.length === 0) return 'Cadastre as questões e o gabarito oficial antes de receber respostas.';
    for (const question of questions) {
      const answer = answers[question.sequenceNumber]?.trim();
      if (!answer) return `Informe a resposta da questão ${question.sequenceNumber}.`;
      if (answer.length > 20) return `Questão ${question.sequenceNumber}: a resposta deve ter no máximo 20 caracteres.`;
    }
    return '';
  };

  const submitStructured = async () => {
    const answerError = validateAnswers();
    if (answerError) { setError(answerError); return; }

    let sheet: AnswerSheetPayload;
    if (sourceType === 'MANUAL') {
      const assignment = assignments.find((item) => item.id.toString() === studentAssignmentId);
      if (!assignment) { setError('Selecione o estudante antes de registrar o gabarito.'); return; }
      sheet = { registration: assignment.registration, answers };
    } else {
      if (!onlineAccessCode.trim()) { setError('Informe o código de acesso da segunda chamada/online.'); return; }
      sheet = { onlineAccessCode: onlineAccessCode.trim(), answers };
    }

    setSubmitting(true);
    try {
      await onSubmit(sourceType, [sheet]);
      setAnswers({});
      setStudentAssignmentId('');
      setOnlineAccessCode('');
      setError('');
    } finally {
      setSubmitting(false);
    }
  };

  const buildPreview = async (file: File) => {
    const text = (await file.text()).replace(/^\uFEFF/, '');
    const lines = text.split(/\r?\n/).map((line) => line.trimEnd()).filter((line) => line.trim());
    if (lines.length === 0) {
      setPreview({ fileName: file.name, rows: [], errors: ['O arquivo está vazio.'] });
      return;
    }

    const delimiter = countDelimiter(lines[0], ';') > countDelimiter(lines[0], ',') ? ';' : ',';
    const headers = parseDelimitedLine(lines[0], delimiter);
    const errors: string[] = [];
    if (headers.length < 2) errors.push('O arquivo deve ter a coluna identificador e ao menos uma coluna de questão.');

    const identifierHeader = normalizedHeader(headers[0] ?? '');
    if (!['identificador', 'matricula', 'etiqueta'].includes(identifierHeader)) {
      errors.push('A primeira coluna deve se chamar identificador, matricula ou etiqueta.');
    }

    const columnSequences = headers.slice(1).map((header) => questionSequenceFromHeader(header));
    const seen = new Set<number>();
    columnSequences.forEach((sequence, index) => {
      const original = headers[index + 1];
      if (sequence == null) errors.push(`A coluna "${original}" não identifica uma questão. Use o número da questão no cabeçalho.`);
      else if (!questionSequences.has(sequence)) errors.push(`A coluna "${original}" não corresponde a uma questão configurada nesta avaliação.`);
      else if (seen.has(sequence)) errors.push(`A questão ${sequence} aparece mais de uma vez no cabeçalho.`);
      else seen.add(sequence);
    });
    questions.forEach((question) => {
      if (!seen.has(question.sequenceNumber)) errors.push(`Falta a coluna da questão ${question.sequenceNumber}.`);
    });

    const identifiers = new Map<string, number[]>();
    const rows: PreviewRow[] = lines.slice(1).map((line, rowIndex) => {
      const cells = parseDelimitedLine(line, delimiter);
      const identifier = (cells[0] ?? '').trim();
      const rowIssues: string[] = [];
      const rowAnswers: Record<number, string> = {};
      const lineNumber = rowIndex + 2;

      if (!identifier) rowIssues.push('identificador não informado');
      else identifiers.set(identifier, [...(identifiers.get(identifier) ?? []), lineNumber]);

      columnSequences.forEach((sequence, columnIndex) => {
        if (sequence == null || !questionSequences.has(sequence)) return;
        const answer = (cells[columnIndex + 1] ?? '').trim().toUpperCase();
        rowAnswers[sequence] = answer;
        if (!answer) rowIssues.push(`questão ${sequence} sem resposta`);
        if (answer.length > 20) rowIssues.push(`questão ${sequence} com resposta acima de 20 caracteres`);
      });

      if (identifier && !assignments.some((item) => item.registration === identifier || item.labelCode === identifier)) {
        rowIssues.push('estudante não localizado entre os organizados nesta avaliação');
      }

      return { line: lineNumber, identifier, answers: rowAnswers, issues: rowIssues };
    });

    identifiers.forEach((lineNumbers, identifier) => {
      if (lineNumbers.length > 1) errors.push(`O identificador ${identifier} está repetido nas linhas ${lineNumbers.join(', ')}.`);
    });
    if (rows.length === 0) errors.push('O arquivo possui cabeçalho, mas não possui registros de gabarito.');

    setPreview({ fileName: file.name, rows, errors });
  };

  const selectFile = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    setError('');
    setPreview(undefined);
    if (!file) return;
    try {
      await buildPreview(file);
    } catch {
      setPreview({ fileName: file.name, rows: [], errors: ['Não foi possível ler o arquivo. Confirme se ele está em CSV UTF-8.'] });
    } finally {
      event.target.value = '';
    }
  };

  const confirmImport = async () => {
    if (!preview || preview.errors.length > 0 || preview.rows.length === 0) {
      setError('Revise o arquivo antes de confirmar os gabaritos.');
      return;
    }
    const sheets = preview.rows.map((row) => sheetFromIdentifier(row.identifier, row.answers));
    setSubmitting(true);
    try {
      await onSubmit('IMPORT', sheets);
      setPreview(undefined);
      setError('');
    } finally {
      setSubmitting(false);
    }
  };

  const previewWithIssues = preview?.rows.filter((row) => row.issues.length > 0) ?? [];
  const previewReady = (preview?.rows.length ?? 0) - previewWithIssues.length;

  return (
    <section className="assessment-editor" aria-labelledby="assessment-answer-receiver-title">
      <div className="assessment-section__heading">
        <div>
          <h3 id="assessment-answer-receiver-title">Receber gabaritos</h3>
          <p>Escolha a origem e registre as respostas sem montar códigos ou sequências delimitadas.</p>
        </div>
      </div>

      <SelectField
        name="answerSource"
        label="Origem das respostas"
        value={sourceType}
        onChange={(event) => changeSource(event.target.value)}
        options={[
          { value: 'IMPORT', label: 'Importar arquivo CSV' },
          { value: 'MANUAL', label: 'Inserção manual' },
          { value: 'ONLINE', label: 'Segunda chamada/online' },
        ]}
      />

      {questions.length === 0 ? <StateMessage title="Configure as questões primeiro" message="O recebimento de gabaritos fica disponível depois que o gabarito oficial possui ao menos uma questão." /> : null}
      {error ? <StateMessage kind="error" title="Revise o recebimento" message={error} /> : null}

      {sourceType === 'IMPORT' ? (
        <div className="assessment-upload-flow">
          <label className="field" htmlFor="assessmentAnswerFile">
            <span className="field__label">Arquivo de gabaritos</span>
            <input id="assessmentAnswerFile" className="input" type="file" accept=".csv,text/csv" disabled={questions.length === 0 || submitting} onChange={(event) => void selectFile(event)} />
            <span className="field__hint">Use CSV UTF-8. A primeira coluna deve ser identificador, matricula ou etiqueta; as demais colunas usam os números das questões, por exemplo: identificador,1,2,3.</span>
          </label>

          {preview ? (
            <section className="assessment-import-preview" aria-live="polite">
              <div className="assessment-artifact__heading"><FileUp aria-hidden="true" size={19} /><div><strong>{preview.fileName}</strong><span>Prévia antes da confirmação</span></div></div>
              {preview.errors.length ? <StateMessage kind="error" title="Revise a estrutura do arquivo" message={preview.errors.join(' ')} /> : <div className="assessment-metrics"><article className="assessment-metric"><span>Registros lidos</span><strong>{preview.rows.length.toLocaleString('pt-BR')}</strong></article><article className="assessment-metric"><span>Sem inconsistência local</span><strong>{previewReady.toLocaleString('pt-BR')}</strong></article><article className="assessment-metric"><span>Precisam de revisão</span><strong>{previewWithIssues.length.toLocaleString('pt-BR')}</strong></article></div>}
              {previewWithIssues.length ? <div><strong>Inconsistências encontradas na prévia</strong><ul>{previewWithIssues.slice(0, 50).map((row) => <li key={row.line}>Linha {row.line}{row.identifier ? ` · ${row.identifier}` : ''}: {row.issues.join('; ')}.</li>)}</ul>{previewWithIssues.length > 50 ? <p className="muted">Há mais {previewWithIssues.length - 50} registro(s) com inconsistências. Revise o arquivo antes da confirmação quando necessário.</p> : null}</div> : null}
              {!preview.errors.length ? <p className="muted">A prévia identifica estrutura, respostas ausentes e estudantes conhecidos no escopo carregado. A validação definitiva de associação e processamento continua no backend.</p> : null}
              <div className="assessment-actions"><Button type="button" variant="primary" disabled={submitting || preview.errors.length > 0 || preview.rows.length === 0} onClick={() => void confirmImport()}><Send aria-hidden="true" size={17} />{submitting ? 'Confirmando gabaritos...' : 'Confirmar gabaritos'}</Button></div>
            </section>
          ) : <StateMessage title="Nenhum arquivo selecionado" message="Selecione o CSV. Antes do envio, o KRINO mostrará quantos registros podem seguir e quais precisam de correção." />}
        </div>
      ) : (
        <div className="assessment-structured-entry">
          {sourceType === 'MANUAL' ? <SelectField name="manualAnswerStudent" label="Estudante" value={studentAssignmentId} onChange={(event) => { setStudentAssignmentId(event.target.value); setError(''); }} options={[{ value: '', label: assignments.length ? 'Selecione o estudante' : 'Nenhum estudante organizado' }, ...assignments.map((item) => ({ value: item.id.toString(), label: `${item.studentName} · ${item.registration} · ${item.className}` }))]} />
            : <TextField name="onlineAccessCode" label="Código de acesso" value={onlineAccessCode} onChange={(event) => { setOnlineAccessCode(event.target.value); setError(''); }} maxLength={80} autoComplete="off" hint="Informe o código emitido para segunda chamada/online." required />}

          <div className="assessment-answer-grid">
            {questions.map((question) => <TextField key={question.id} name={`answer-${sourceType}-${question.id}`} label={`Questão ${question.sequenceNumber}`} value={answers[question.sequenceNumber] ?? ''} onChange={(event) => setAnswer(question.sequenceNumber, event.target.value)} maxLength={20} hint={`${question.descriptor} · ${question.skill}`} required />)}
          </div>
          <div className="assessment-actions"><Button type="button" variant="primary" disabled={submitting || questions.length === 0} onClick={() => void submitStructured()}><Send aria-hidden="true" size={17} />{submitting ? 'Registrando gabarito...' : sourceType === 'MANUAL' ? 'Registrar gabarito' : 'Registrar resposta online'}</Button></div>
        </div>
      )}
    </section>
  );
}
