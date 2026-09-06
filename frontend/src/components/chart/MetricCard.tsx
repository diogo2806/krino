type Props = { label: string; value: string; detail?: string; };

export function MetricCard({ label, value, detail }: Props) {
  return <article className="metric-card"><span className="metric-card__label">{label}</span><strong className="metric-card__value">{value}</strong>{detail ? <span className="metric-card__detail">{detail}</span> : null}</article>;
}
