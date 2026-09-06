import { Printer } from 'lucide-react';
import { useEffect, useState, type CSSProperties } from 'react';
import { apiBlob } from '../../shared/api/client';
import { Button } from '../button/Button';
import { StateMessage } from '../state/StateMessage';
import type { TransportCard, TransportDay } from './types';

type Props = { card: TransportCard; };

const dayLabels: Record<TransportDay, string> = {
  MONDAY: 'Seg', TUESDAY: 'Ter', WEDNESDAY: 'Qua', THURSDAY: 'Qui', FRIDAY: 'Sex', SATURDAY: 'Sáb', SUNDAY: 'Dom',
};

export function TransportCardPanel({ card }: Props) {
  const [photoUrl, setPhotoUrl] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    let objectUrl = '';
    setError('');
    void apiBlob(card.photoPath).then((blob) => {
      if (!active) return;
      objectUrl = URL.createObjectURL(blob);
      setPhotoUrl(objectUrl);
    }).catch((exception) => {
      if (active) setError(exception instanceof Error ? exception.message : 'Não foi possível carregar a foto da carteirinha.');
    });
    return () => { active = false; if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [card.photoPath]);

  const request = card.request;
  const cssVariables = { '--transport-accent': card.art.accentColor } as CSSProperties;

  return <section className="transport-card-section" aria-labelledby="transport-card-title">
    <div className="transport-section-heading"><div><h2 id="transport-card-title">Carteirinha universitária</h2><p className="muted">Documento disponível para impressão enquanto a solicitação estiver aprovada e dentro da validade.</p></div><Button type="button" onClick={() => window.print()}><Printer aria-hidden="true" size={18} />Imprimir carteirinha</Button></div>
    {error ? <StateMessage kind="error" title="Não foi possível carregar a carteirinha" message={error} /> : null}
    <article className="transport-card" style={cssVariables}>
      <header className="transport-card__header"><strong>{card.art.headerText}</strong><span>{card.art.name}</span></header>
      <div className="transport-card__body">
        <div className="transport-card__photo">{photoUrl ? <img src={photoUrl} alt={`Foto de ${request.fullName}`} /> : <span>Carregando foto...</span>}</div>
        <div className="transport-card__data">
          <h3>{request.fullName}</h3>
          <p><strong>Documento:</strong> {request.personalDocument}</p>
          <p><strong>Curso:</strong> {request.courseName}</p>
          <p><strong>Instituição:</strong> {request.institutionName}</p>
          <p><strong>Dias autorizados:</strong> {request.days.map((day) => dayLabels[day]).join(', ')}</p>
          <p><strong>Validade:</strong> {request.validUntil ? new Date(`${request.validUntil}T12:00:00`).toLocaleDateString('pt-BR') : 'Não informada'}</p>
        </div>
      </div>
      {card.art.footerText ? <footer className="transport-card__footer">{card.art.footerText}</footer> : null}
    </article>
  </section>;
}
