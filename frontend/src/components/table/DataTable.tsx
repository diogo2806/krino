import type { ReactNode } from 'react';

export type DataColumn<T> = { key: string; header: string; render: (row: T) => ReactNode; };
type DataTableProps<T> = { rows: T[]; columns: DataColumn<T>[]; rowKey: (row: T) => string | number; };

export function DataTable<T>({ rows, columns, rowKey }: DataTableProps<T>) {
  return <div className="table-wrap"><table className="data-table"><thead><tr>{columns.map((column) => <th key={column.key}>{column.header}</th>)}</tr></thead><tbody>{rows.map((row) => <tr key={rowKey(row)}>{columns.map((column) => <td key={column.key}>{column.render(row)}</td>)}</tr>)}</tbody></table></div>;
}
