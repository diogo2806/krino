export type TrendPointValue = { label: string; value?: number | null; };

type Props = { title: string; points: TrendPointValue[]; emptyMessage: string; };

export function TrendLineChart({ title, points, emptyMessage }: Props) {
  const valid = points.map((point, index) => ({ ...point, index })).filter((point) => point.value != null);
  if (valid.length === 0) return <figure className="chart-card"><figcaption>{title}</figcaption><p className="muted">{emptyMessage}</p></figure>;
  const x = (index: number) => 50 + index * (420 / Math.max(1, points.length - 1));
  const y = (value: number) => 145 - Math.max(0, Math.min(100, value)) * 1.05;
  const formatPercent = (value: number) => `${value.toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}%`;
  const polyline = valid.map((point) => `${x(point.index)},${y(point.value!)}`).join(' ');
  const description = valid.map((point) => `${point.label}: ${formatPercent(point.value!)}`).join('; ');
  return <figure className="chart-card"><figcaption>{title}</figcaption><svg className="trend-chart" viewBox="0 0 520 190" role="img" aria-label={`${title}. ${description}`}><line x1="50" y1="145" x2="470" y2="145" className="trend-chart__axis" /><line x1="50" y1="40" x2="50" y2="145" className="trend-chart__axis" /><polyline points={polyline} className="trend-chart__line" />{valid.map((point) => <g key={point.label}><circle cx={x(point.index)} cy={y(point.value!)} r="5" className="trend-chart__point" /><text x={x(point.index)} y="168" textAnchor="middle" className="trend-chart__label">{point.label}</text><text x={x(point.index)} y={y(point.value!) - 10} textAnchor="middle" className="trend-chart__value">{formatPercent(point.value!)}</text></g>)}</svg></figure>;
}
