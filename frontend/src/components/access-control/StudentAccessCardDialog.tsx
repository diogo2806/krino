import { Printer } from 'lucide-react';
import { Button } from '../button/Button';
import { Modal } from '../modal/Modal';
import type { AccessCard } from './types';

type Props = { card?: AccessCard; open: boolean; onClose: () => void; };

export function StudentAccessCardDialog({ card, open, onClose }: Props) {
  const qrSrc = card ? `data:image/svg+xml;charset=utf-8,${encodeURIComponent(card.qrSvg)}` : '';
  return <Modal open={open} title="Carteirinha de entrada e saída" onClose={onClose} footer={<><Button type="button" variant="ghost" onClick={onClose}>Fechar</Button><Button type="button" variant="primary" onClick={() => window.print()} disabled={!card}><Printer aria-hidden="true" size={18} />Imprimir carteirinha</Button></>}>{card ? <article className="student-access-card"><div className="student-access-card__brand"><strong>KRINO</strong><span>Identificação escolar</span></div><div className="student-access-card__body"><img src={qrSrc} alt={`QR Code de ${card.studentName}`} /><div><h3>{card.studentName}</h3><p>Matrícula: {card.registration}</p><p>Turma: {card.className}</p><p>{card.schoolName}</p></div></div><small>Apresente este QR Code para registrar entrada e saída.</small></article> : null}</Modal>;
}
