export type ProgressBarItem = { label: string; value?: number | null; detail?: string; };

type Props = { title: string; items: ProgressBarItem[]; emptyMessage: string; };

export function ProgressBarChart({ title, items, emptyMessage }: Props) {
  const available = items.filter((item) => item.value != null);
  return <figure className="chart-card"><figcaption>{title}</figcaption>{available.length === 0 ? <p className="muted">{emptyMessage}</p> : <div className="progress-chart">{available.map((item) => { const formatted = `${item.value!.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`; return <div className="progress-chart__item" key={item.label}><div className="progress-chart__header"><span>{item.label}</span><strong>{formatted}</strong></div><progress max={100} value={Math.max(0, Math.min(100, item.value!))} aria-label={`${item.label}: ${formatted}`} />{item.detail ? <small>{item.detail}</small> : null}</div>; })}</div>}</figure>;
}
