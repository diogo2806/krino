export type AssessmentStage = 'DIAGNOSTIC' | 'MONITORING' | 'FINAL';
export type AssessmentStatus = 'PREPARATION' | 'READY' | 'APPLIED' | 'PROCESSING' | 'PROCESSED' | 'CLOSED';
export type AssessmentTab = 'assessments' | 'organization' | 'answer-sheets' | 'processing' | 'results';

export type AssessmentView = {
  id: number;
  name: string;
  stage: AssessmentStage;
  academicYear: number;
  gradeStage: string;
  componentId?: number;
  componentName?: string;
  status: AssessmentStatus;
  applicationManual?: string;
  instructions?: string;
  schoolCount: number;
  classCount: number;
  studentCount: number;
  questionCount: number;
};

export type SchoolOption = { id: number; name: string; };
export type ClassOption = { id: number; schoolId: number; name: string; stage: string; academicYear: number; };
export type ComponentOption = { id: number; code: string; name: string; };
export type AssessmentCatalog = { schools: SchoolOption[]; classes: ClassOption[]; components: ComponentOption[]; };

export type QuestionView = { id: number; sequenceNumber: number; descriptor: string; skill: string; correctOption: string; };
export type AssignmentView = { id: number; studentId: number; registration: string; studentName: string; schoolId: number; schoolName: string; classId: number; className: string; attendanceStatus: string; labelCode: string; packageCode: string; };
export type ArtifactView = { type: string; title: string; generatedAt: string; lineCount: number; lines: string[]; };
export type ValidationSummary = { recordsRead: number; valid: number; invalid: number; associationRejected: number; };
export type ProcessingRunView = { id: number; runNumber: number; status: string; validSheets: number; invalidSheets: number; initiatedBy: string; startedAt: string; completedAt?: string; };
export type ImportSummary = { recordsRead: number; valid: number; invalid: number; items: { identifier: string; status: string; message: string; answerSheetId?: number; }[]; };
export type ResultSummaryRow = { keyId: number; label: string; students: number; correctAnswers: number; totalQuestions: number; scorePercent?: number; };
export type SkillSummaryRow = { descriptor: string; skill: string; correctAnswers: number; totalQuestions: number; scorePercent?: number; };
export type OccurrenceView = { id: number; schoolId?: number; schoolName?: string; classId?: number; className?: string; occurredAt: string; description: string; createdBy: string; };
