import { Plus, Trash2 } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Button } from '../button/Button';
import { TextField } from '../form/TextField';
import { StateMessage } from '../state/StateMessage';
import type { QuestionInput, QuestionView } from './types';

type Props = {
  questions: QuestionView[];
  onSave: (questions: QuestionInput[]) => Promise<void>;
};

type DraftQuestion = {
  key: number;
  sequenceNumber: string;
  descriptor: string;
  skill: string;
  correctOption: string;
};

function toDraft(question: QuestionView, key: number): DraftQuestion {
  return {
    key,
    sequenceNumber: question.sequenceNumber.toString(),
    descriptor: question.descriptor,
    skill: question.skill,
    correctOption: question.correctOption,
  };
}

export function AssessmentQuestionEditor({ questions, onSave }: Props) {
  const nextKey = useRef(1);
  const [rows, setRows] = useState<DraftQuestion[]>([]);
  const [errors, setErrors] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const nextRows = questions.map((question) => toDraft(question, nextKey.current++));
    setRows(nextRows);
    setErrors([]);
  }, [questions]);

  const addQuestion = () => {
    const used = rows.map((row) => Number(row.sequenceNumber)).filter(Number.isInteger);
    const sequence = used.length ? Math.max(...used) + 1 : 1;
    setRows((current) => [...current, { key: nextKey.current++, sequenceNumber: sequence.toString(), descriptor: '', skill: '', correctOption: '' }]);
  };

  const updateQuestion = (key: number, field: keyof Omit<DraftQuestion, 'key'>, value: string) => {
    setRows((current) => current.map((row) => row.key === key ? { ...row, [field]: value } : row));
    setErrors([]);
  };

  const validate = () => {
    const messages: string[] = [];
    const sequences = new Map<number, number[]>();

    if (rows.length === 0) messages.push('Adicione ao menos uma questão antes de salvar.');

    rows.forEach((row, index) => {
      const position = index + 1;
      const sequence = Number(row.sequenceNumber);
      if (!Number.isInteger(sequence) || sequence < 1) messages.push(`Questão ${position} · Número: informe um número inteiro maior que zero.`);
      else sequences.set(sequence, [...(sequences.get(sequence) ?? []), position]);
      if (!row.descriptor.trim()) messages.push(`Questão ${position} · Descritor: informe o descritor.`);
      if (row.descriptor.trim().length > 180) messages.push(`Questão ${position} · Descritor: use no máximo 180 caracteres.`);
      if (!row.skill.trim()) messages.push(`Questão ${position} · Habilidade: informe a habilidade.`);
      if (row.skill.trim().length > 300) messages.push(`Questão ${position} · Habilidade: use no máximo 300 caracteres.`);
      if (!row.correctOption.trim()) messages.push(`Questão ${position} · Alternativa correta: informe a resposta correta.`);
      if (row.correctOption.trim().length > 20) messages.push(`Questão ${position} · Alternativa correta: use no máximo 20 caracteres.`);
    });

    sequences.forEach((positions, sequence) => {
      if (positions.length > 1) messages.push(`Número ${sequence}: está repetido nas questões ${positions.join(' e ')}.`);
    });

    setErrors(messages);
    return messages.length === 0;
  };

  const save = async () => {
    if (!validate()) return;
    const payload: QuestionInput[] = rows.map((row) => ({
      sequenceNumber: Number(row.sequenceNumber),
      descriptor: row.descriptor.trim(),
      skill: row.skill.trim(),
      correctOption: row.correctOption.trim().toUpperCase(),
    }));

    setSaving(true);
    try {
      await onSave(payload);
      setErrors([]);
    } catch {
      // A página apresenta a mensagem devolvida pela API sem descartar o que foi digitado.
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="assessment-editor" aria-labelledby="assessment-question-editor-title">
      <div className="assessment-section__heading">
        <div>
          <h3 id="assessment-question-editor-title">Questões e habilidades</h3>
          <p>Adicione as questões da avaliação e informe descritor, habilidade e alternativa correta.</p>
        </div>
        <Button type="button" variant="ghost" onClick={addQuestion}><Plus aria-hidden="true" size={17} />Adicionar questão</Button>
      </div>

      {errors.length ? <StateMessage kind="error" title="Revise as questões" message={errors.join(' ')} /> : null}
      {rows.length === 0 ? <StateMessage title="Nenhuma questão adicionada" message="Use Adicionar questão para começar a configurar o gabarito oficial." /> : null}

      <div className="assessment-question-list">
        {rows.map((row, index) => (
          <article className="assessment-question-row" key={row.key}>
            <TextField name={`questionSequence-${row.key}`} label="Número" type="number" min={1} value={row.sequenceNumber} onChange={(event) => updateQuestion(row.key, 'sequenceNumber', event.target.value)} aria-label={`Número da questão ${index + 1}`} required />
            <TextField name={`questionDescriptor-${row.key}`} label="Descritor" maxLength={180} value={row.descriptor} onChange={(event) => updateQuestion(row.key, 'descriptor', event.target.value)} required />
            <TextField name={`questionSkill-${row.key}`} label="Habilidade" maxLength={300} value={row.skill} onChange={(event) => updateQuestion(row.key, 'skill', event.target.value)} required />
            <TextField name={`questionCorrect-${row.key}`} label="Alternativa correta" maxLength={20} value={row.correctOption} onChange={(event) => updateQuestion(row.key, 'correctOption', event.target.value.toUpperCase())} required />
            <Button type="button" variant="ghost" className="assessment-question-remove" aria-label={`Remover questão ${row.sequenceNumber || index + 1}`} title={`Remover questão ${row.sequenceNumber || index + 1}`} onClick={() => setRows((current) => current.filter((item) => item.key !== row.key))}><Trash2 aria-hidden="true" size={17} />Remover</Button>
          </article>
        ))}
      </div>

      <div className="assessment-actions"><Button type="button" variant="primary" disabled={saving} onClick={() => void save()}>{saving ? 'Salvando questões...' : 'Salvar questões e gabarito oficial'}</Button></div>
    </section>
  );
}
