import { Button } from '../button/Button';
import { Modal } from '../modal/Modal';

type ConfirmDialogProps = { open: boolean; title: string; message: string; confirmLabel: string; danger?: boolean; onConfirm: () => void; onClose: () => void; };

export function ConfirmDialog({ open, title, message, confirmLabel, danger = false, onConfirm, onClose }: ConfirmDialogProps) {
  return <Modal open={open} title={title} onClose={onClose} footer={<><Button type="button" onClick={onClose}>Cancelar</Button><Button type="button" variant={danger ? 'danger' : 'primary'} onClick={onConfirm}>{confirmLabel}</Button></>}><p>{message}</p></Modal>;
}
