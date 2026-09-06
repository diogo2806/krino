import { X } from 'lucide-react';
import { useEffect, useId, useRef, type ReactNode } from 'react';

type ModalProps = {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
};

export function Modal({ open, title, onClose, children, footer }: ModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog ref={dialogRef} className="modal" aria-labelledby={titleId} onCancel={(event) => { event.preventDefault(); onClose(); }}>
      <div className="modal__header">
        <h2 id={titleId}>{title}</h2>
        <button className="icon-button icon-button--only" type="button" aria-label="Fechar" title="Fechar" onClick={onClose}>
          <X aria-hidden="true" size={20} />
        </button>
      </div>
      <div className="modal__content">{children}</div>
      {footer ? <div className="modal__footer">{footer}</div> : null}
    </dialog>
  );
}
