export type ReportSchoolOption = { id: number; name: string; };
export type ReportAssessmentOption = { id: number; name: string; stage: string; academicYear: number; gradeStage: string; componentName?: string | null; };
export type ReportContext = { networkView: boolean; schools: ReportSchoolOption[]; assessments: ReportAssessmentOption[]; };
export type ReportClassOption = { id: number; schoolId: number; name: string; };
export type ReportStudentOption = { id: number; registration: string; name: string; classId: number; schoolId: number; };
export type ReportFilters = { classes: ReportClassOption[]; students: ReportStudentOption[]; };

export type BreakdownRow = { keyId: number; label: string; participants: number; expectedStudents: number; percentage?: number | null; };
export type SkillRow = { descriptor: string; skill: string; correctAnswers: number; totalQuestions: number; correctPercent?: number | null; performanceLevel: string; };
export type DashboardView = {
  expectedStudents: number;
  participants: number;
  participationPercent?: number | null;
  achievementPercent?: number | null;
  skills: number;
  participationBySchool: BreakdownRow[];
  skillHighlights: SkillRow[];
};

export type SchoolSkillRow = SkillRow & { schoolId: number; schoolName: string; };
export type AlternativeRow = { sequenceNumber: number; descriptor: string; skill: string; correctOption: string; selectedOption: string; responses: number; baseResponses: number; responsePercent?: number | null; };
export type QuestionRow = { sequenceNumber: number; descriptor: string; skill: string; correctAnswers: number; responses: number; correctPercent?: number | null; relativeComplexity: string; };
export type ComponentRow = { componentName: string; students: number; correctAnswers: number; totalQuestions: number; correctPercent?: number | null; };
export type StudentAnswerRow = { sequenceNumber: number; descriptor: string; skill: string; selectedOption?: string | null; correctOption: string; correct: boolean; };
export type InterventionProfile = { studentId: number; registration: string; studentName: string; correctPercent?: number | null; performanceLevel: string; strengths: SkillRow[]; attentionPriorities: SkillRow[]; };
export type PerformanceLevel = { id?: number; label: string; minimumPercent: number; maximumPercent: number; displayOrder?: number; };
