import { useEffect, useState, type FormEvent } from 'react';
import { Button } from '../button/Button';
import { SelectField } from '../form/SelectField';
import { TextField } from '../form/TextField';
import type { CourseType, TransportDay, TransportRequest, TransportRequestInput } from './types';

type Props = {
  request?: TransportRequest;
  defaultName: string;
  saving: boolean;
  onSave: (input: TransportRequestInput) => Promise<void>;
};

const dayOptions: Array<{ value: TransportDay; label: string }> = [
  { value: 'MONDAY', label: 'Segunda' },
  { value: 'TUESDAY', label: 'Terça' },
  { value: 'WEDNESDAY', label: 'Quarta' },
  { value: 'THURSDAY', label: 'Quinta' },
  { value: 'FRIDAY', label: 'Sexta' },
  { value: 'SATURDAY', label: 'Sábado' },
  { value: 'SUNDAY', label: 'Domingo' },
];

export function TransportRequestForm({ request, defaultName, saving, onSave }: Props) {
  const [fullName, setFullName] = useState(request?.fullName ?? defaultName);
  const [personalDocument, setPersonalDocument] = useState(request?.personalDocument ?? '');
  const [birthDate, setBirthDate] = useState(request?.birthDate ?? '');
  const [phone, setPhone] = useState(request?.phone ?? '');
  const [courseType, setCourseType] = useState<CourseType>(request?.courseType ?? 'UNIVERSITY');
  const [courseName, setCourseName] = useState(request?.courseName ?? '');
  const [institutionName, setInstitutionName] = useState(request?.institutionName ?? '');
  const [days, setDays] = useState<TransportDay[]>(request?.days ?? []);

  useEffect(() => {
    setFullName(request?.fullName ?? defaultName);
    setPersonalDocument(request?.personalDocument ?? '');
    setBirthDate(request?.birthDate ?? '');
    setPhone(request?.phone ?? '');
    setCourseType(request?.courseType ?? 'UNIVERSITY');
    setCourseName(request?.courseName ?? '');
    setInstitutionName(request?.institutionName ?? '');
    setDays(request?.days ?? []);
  }, [request, defaultName]);

  const toggleDay = (day: TransportDay) => setDays((current) => current.includes(day) ? current.filter((item) => item !== day) : [...current, day]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    await onSave({ fullName, personalDocument, birthDate, phone, courseType, courseName, institutionName, days });
  };

  return <form className="transport-form form-stack" onSubmit={submit}>
    <div className="form-grid">
      <TextField name="transportFullName" label="Nome completo" value={fullName} onChange={(event) => setFullName(event.target.value)} required maxLength={180} />
      <TextField name="transportDocument" label="Documento pessoal" value={personalDocument} onChange={(event) => setPersonalDocument(event.target.value)} required maxLength={40} hint="Informe o documento pessoal que deverá constar na identificação do estudante." />
      <TextField name="transportBirthDate" label="Data de nascimento" type="date" value={birthDate} onChange={(event) => setBirthDate(event.target.value)} required />
      <TextField name="transportPhone" label="Telefone" value={phone} onChange={(event) => setPhone(event.target.value)} maxLength={40} />
      <SelectField name="transportCourseType" label="Tipo de curso" value={courseType} onChange={(event) => setCourseType(event.target.value as CourseType)} options={[
        { value: 'PROFESSIONALIZING', label: 'Profissionalizante' },
        { value: 'TECHNICAL', label: 'Técnico' },
        { value: 'UNIVERSITY', label: 'Universitário' },
      ]} />
      <TextField name="transportCourseName" label="Curso" value={courseName} onChange={(event) => setCourseName(event.target.value)} required maxLength={180} />
      <TextField name="transportInstitution" label="Instituição de ensino" value={institutionName} onChange={(event) => setInstitutionName(event.target.value)} required maxLength={180} />
    </div>
    <fieldset className="transport-days">
      <legend>Dias em que o transporte é necessário</legend>
      <div className="transport-days__options">{dayOptions.map((day) => <label className="check-field" key={day.value}><input type="checkbox" checked={days.includes(day.value)} onChange={() => toggleDay(day.value)} /><span>{day.label}</span></label>)}</div>
    </fieldset>
    <div className="transport-actions"><Button type="submit" variant="primary" disabled={saving || days.length === 0}>{saving ? 'Salvando...' : request ? 'Salvar ajustes' : 'Salvar solicitação'}</Button></div>
  </form>;
}
