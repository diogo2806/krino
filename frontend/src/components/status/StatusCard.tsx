type StatusCardProps = { label: string; status: 'OK' | 'VERIFICANDO' | 'ERRO'; };

export function StatusCard({ label, status }: StatusCardProps) {
  return <article className="status-card" aria-label={`${label}: ${status}`}><span className="status-card__label">{label}</span><strong className={`status-card__value status-card__value--${status.toLowerCase()}`}>{status}</strong></article>;
}
