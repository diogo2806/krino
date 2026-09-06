export type TransportStatus = 'DRAFT' | 'SUBMITTED' | 'UNDER_REVIEW' | 'ADJUSTMENT_REQUESTED' | 'APPROVED' | 'DENIED';
export type TransportDay = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';
export type CourseType = 'PROFESSIONALIZING' | 'TECHNICAL' | 'UNIVERSITY';

export type TransportHistory = {
  status: TransportStatus;
  reason?: string | null;
  actorName: string;
  createdAt: string;
};

export type TransportRequest = {
  id: number;
  applicantUserId: number;
  applicantAccountName: string;
  fullName: string;
  personalDocument: string;
  birthDate: string;
  phone?: string | null;
  courseType: CourseType;
  courseName: string;
  institutionName: string;
  days: TransportDay[];
  status: TransportStatus;
  reviewReason?: string | null;
  validUntil?: string | null;
  hasPhoto: boolean;
  hasEnrollmentProof: boolean;
  submittedAt?: string | null;
  reviewedAt?: string | null;
  history: TransportHistory[];
};

export type CardArt = {
  id: number;
  name: string;
  headerText: string;
  footerText?: string | null;
  accentColor: string;
  approved: boolean;
  approvedAt?: string | null;
};

export type TransportCard = {
  request: TransportRequest;
  art: CardArt;
  photoPath: string;
};

export type TransportRequestInput = {
  fullName: string;
  personalDocument: string;
  birthDate: string;
  phone: string;
  courseType: CourseType;
  courseName: string;
  institutionName: string;
  days: TransportDay[];
};
