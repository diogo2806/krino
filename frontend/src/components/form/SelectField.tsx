import type { SelectHTMLAttributes } from 'react';

type Option = { value: string; label: string; };
type SelectFieldProps = SelectHTMLAttributes<HTMLSelectElement> & { label: string; options: Option[]; };

export function SelectField({ label, options, id, ...props }: SelectFieldProps) {
  const fieldId = id ?? `field-${props.name}`;
  return <label className="field" htmlFor={fieldId}><span className="field__label">{label}</span><select id={fieldId} className="select" {...props}>{options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>;
}
