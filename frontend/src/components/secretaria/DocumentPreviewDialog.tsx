import { Printer } from 'lucide-react';
import { Button } from '../button/Button';
import { Modal } from '../modal/Modal';
import { DataTable, type DataColumn } from '../table/DataTable';
import type { SchoolDocument } from './types';

type Row = { id: number; values: string[]; };
type Props = { document?: SchoolDocument; onClose: () => void; };

export function DocumentPreviewDialog({ document, onClose }: Props) {
  const rows: Row[] = document?.rows.map((values, id) => ({ id, values })) ?? [];
  const columns: DataColumn<Row>[] = document?.headers.map((header, index) => ({ key: `${header}-${index}`, header, render: (row) => row.values[index] ?? '' })) ?? [];
  return <Modal open={Boolean(document)} title={document?.title ?? 'Documento escolar'} onClose={onClose} footer={<><Button type="button" onClick={onClose}>Fechar</Button><Button type="button" variant="primary" onClick={() => window.print()}><Printer aria-hidden="true" size={18} />Imprimir</Button></>}><article className="school-document"><header><h3>{document?.title}</h3><p>{document?.subtitle}</p></header>{document?.paragraphs.map((paragraph, index) => <p key={`${index}-${paragraph}`}>{paragraph}</p>)}{rows.length > 0 ? <DataTable rows={rows} columns={columns} rowKey={(row) => row.id} /> : null}<footer>Gerado pelo KRINO em {document ? new Date(document.generatedAt).toLocaleString('pt-BR') : ''}.</footer></article></Modal>;
}
