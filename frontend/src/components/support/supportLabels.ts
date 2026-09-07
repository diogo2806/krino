import type { SupportSeverity, SupportStatus } from './types';

export function severityLabel(severity: SupportSeverity) {
  return severity === 'CRITICAL' ? 'Crítico' : severity === 'MEDIUM' ? 'Médio' : 'Baixo';
}

export function statusLabel(status: SupportStatus) {
  return status === 'OPEN' ? 'Aberto'
    : status === 'IN_PROGRESS' ? 'Em atendimento'
      : status === 'WAITING_REQUESTER' ? 'Aguardando solicitante'
        : status === 'RESOLVED' ? 'Resolvido'
          : 'Encerrado';
}

export function eventLabel(eventType: string) {
  return eventType === 'CREATED' ? 'Chamado aberto'
    : eventType === 'MESSAGE' ? 'Interação'
      : eventType === 'STATUS_CHANGED' ? 'Status alterado'
        : eventType === 'SEVERITY_CHANGED' ? 'Criticidade alterada'
          : eventType === 'RESOLUTION_RECORDED' ? 'Solução atualizada'
            : 'Atualização';
}

export function formatSupportDate(value?: string | null) {
  if (!value) return 'Não registrado';
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(parsed);
}
