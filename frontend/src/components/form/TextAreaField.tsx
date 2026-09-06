import type { TextareaHTMLAttributes } from 'react';

type TextAreaFieldProps = TextareaHTMLAttributes<HTMLTextAreaElement> & { label: string; hint?: string; };

export function TextAreaField({ label, hint, id, ...props }: TextAreaFieldProps) {
  const fieldId = id ?? `field-${props.name}`;
  return <label className="field" htmlFor={fieldId}><span className="field__label">{label}</span><textarea id={fieldId} className="textarea" rows={4} {...props} />{hint ? <span className="field__hint">{hint}</span> : null}</label>;
}
