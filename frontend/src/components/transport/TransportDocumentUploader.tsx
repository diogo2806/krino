import { useState } from 'react';
import { apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';

type Props = {
  requestId: number;
  hasPhoto: boolean;
  hasEnrollmentProof: boolean;
  onUploaded: () => Promise<void>;
};

export function TransportDocumentUploader({ requestId, hasPhoto, hasEnrollmentProof, onUploaded }: Props) {
  const [photo, setPhoto] = useState<File>();
  const [proof, setProof] = useState<File>();
  const [sending, setSending] = useState<'PHOTO' | 'ENROLLMENT_PROOF'>();

  const upload = async (type: 'PHOTO' | 'ENROLLMENT_PROOF', file?: File) => {
    if (!file) return;
    setSending(type);
    try {
      const form = new FormData();
      form.append('file', file);
      await apiRequest(`/transport/requests/${requestId}/documents/${type}`, { method: 'POST', body: form });
      if (type === 'PHOTO') setPhoto(undefined); else setProof(undefined);
      await onUploaded();
    } finally {
      setSending(undefined);
    }
  };

  return <section className="transport-documents" aria-labelledby="transport-documents-title">
    <div className="transport-section-heading"><div><h2 id="transport-documents-title">Documentos</h2><p className="muted">Envie os dois arquivos antes de enviar a solicitação para análise. O limite é de 5 MB por arquivo.</p></div></div>
    <div className="transport-document-grid">
      <article className="transport-document-card">
        <strong>Foto do estudante</strong>
        <span>{hasPhoto ? 'Arquivo enviado' : 'Arquivo pendente'}</span>
        <label className="field"><span className="field__label">Selecionar foto</span><input className="input" type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => setPhoto(event.target.files?.[0])} /></label>
        <Button type="button" onClick={() => void upload('PHOTO', photo)} disabled={!photo || Boolean(sending)}>{sending === 'PHOTO' ? 'Enviando...' : hasPhoto ? 'Substituir foto' : 'Enviar foto'}</Button>
      </article>
      <article className="transport-document-card">
        <strong>Comprovante de matrícula</strong>
        <span>{hasEnrollmentProof ? 'Arquivo enviado' : 'Arquivo pendente'}</span>
        <label className="field"><span className="field__label">Selecionar comprovante</span><input className="input" type="file" accept="application/pdf,image/jpeg,image/png" onChange={(event) => setProof(event.target.files?.[0])} /></label>
        <Button type="button" onClick={() => void upload('ENROLLMENT_PROOF', proof)} disabled={!proof || Boolean(sending)}>{sending === 'ENROLLMENT_PROOF' ? 'Enviando...' : hasEnrollmentProof ? 'Substituir comprovante' : 'Enviar comprovante'}</Button>
      </article>
    </div>
  </section>;
}
