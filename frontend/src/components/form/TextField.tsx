import type { InputHTMLAttributes } from 'react';

type TextFieldProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  hint?: string;
};

export function TextField({ label, hint, id, ...props }: TextFieldProps) {
  const fieldId = id ?? `field-${props.name}`;
  return <label className="field" htmlFor={fieldId}><span className="field__label">{label}</span><input id={fieldId} className="input" {...props} />{hint ? <span className="field__hint">{hint}</span> : null}</label>;
}
