type StateMessageProps = {
  kind?: 'info' | 'error' | 'success';
  title: string;
  message?: string;
};

export function StateMessage({ kind = 'info', title, message }: StateMessageProps) {
  return <div className={`state-message state-message--${kind}`} role={kind === 'error' ? 'alert' : 'status'}><strong>{title}</strong>{message ? <span>{message}</span> : null}</div>;
}
