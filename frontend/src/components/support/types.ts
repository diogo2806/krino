export type SupportSeverity = 'CRITICAL' | 'MEDIUM' | 'LOW';
export type SupportStatus = 'OPEN' | 'IN_PROGRESS' | 'WAITING_REQUESTER' | 'RESOLVED' | 'CLOSED';

export type SupportTicket = {
  id: number;
  openedByUserId: number;
  requesterUsername: string;
  requesterName: string;
  subject: string;
  description: string;
  severity: SupportSeverity;
  status: SupportStatus;
  resolution?: string | null;
  openedAt: string;
  firstSupportResponseAt?: string | null;
  resolvedAt?: string | null;
  closedAt?: string | null;
  updatedAt: string;
  responseTargetHours: number;
  solutionTargetHours: number;
  contractualCountingRuleDefined: boolean;
};

export type SupportTicketEvent = {
  id: number;
  actorUsername: string;
  eventType: string;
  previousValue?: string | null;
  newValue?: string | null;
  message?: string | null;
  createdAt: string;
};

export type SupportTicketDetail = { ticket: SupportTicket; events: SupportTicketEvent[] };

export type SupportSummary = {
  total: number;
  open: number;
  inProgress: number;
  waitingRequester: number;
  resolved: number;
  closed: number;
  criticalActive: number;
  mediumActive: number;
  lowActive: number;
};
