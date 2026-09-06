import { BookOpen, X } from 'lucide-react';
import { useRef } from 'react';

export type ManualSection = { title: string; content: string; };
type ScreenManualProps = { screenName: string; sections: ManualSection[]; };

export function ScreenManual({ screenName, sections }: ScreenManualProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  return (
    <>
      <button className="icon-button" type="button" aria-label={`Abrir Manual da Tela: ${screenName}`} title="Manual da Tela" onClick={() => dialogRef.current?.showModal()}>
        <BookOpen aria-hidden="true" size={20} /><span>Manual</span>
      </button>
      <dialog ref={dialogRef} className="manual-dialog" aria-labelledby="screen-manual-title">
        <div className="manual-dialog__header">
          <h2 id="screen-manual-title">Manual da Tela: {screenName}</h2>
          <button className="icon-button icon-button--only" type="button" aria-label="Fechar manual" title="Fechar manual" onClick={() => dialogRef.current?.close()}><X aria-hidden="true" size={20} /></button>
        </div>
        <div className="manual-dialog__content">
          {sections.map((section) => <section key={section.title}><h3>{section.title}</h3><p>{section.content}</p></section>)}
        </div>
      </dialog>
    </>
  );
}
