import { BookOpen } from 'lucide-react';
import { useState } from 'react';
import { Modal } from '../modal/Modal';

export type ManualSection = { title: string; content: string; };
type ScreenManualProps = { screenName: string; sections: ManualSection[]; };

export function ScreenManual({ screenName, sections }: ScreenManualProps) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button className="icon-button" type="button" aria-label={`Abrir Manual da Tela: ${screenName}`} title="Manual da Tela" onClick={() => setOpen(true)}>
        <BookOpen aria-hidden="true" size={20} /><span>Manual</span>
      </button>
      <Modal open={open} title={`Manual da Tela: ${screenName}`} onClose={() => setOpen(false)}>
        <div className="manual-content">
          {sections.map((section) => <section key={section.title}><h3>{section.title}</h3><p>{section.content}</p></section>)}
        </div>
      </Modal>
    </>
  );
}
