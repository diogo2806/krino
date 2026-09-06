export type MonitoringSchool = { id: number; code: string; name: string; };
export type MonitoringClass = { id: number; name: string; stage: string; };
export type MonitoringContext = { academicYear: number; networkView: boolean; networkManage: boolean; schools: MonitoringSchool[]; };
export type SourceMetric = { sourceCode: string; sourceLabel: string; totalStudents: number; studentsWithResults: number; assessmentsWithResults: number; coveragePercent?: number | null; achievementPercent?: number | null; };
export type MonitoringSummary = { level: string; academicYear: number; period?: number; schoolId?: number; schoolName?: string; classId?: number; className?: string; sources: SourceMetric[]; };
export type TrendPoint = { period: number; sources: SourceMetric[]; };
export type BreakdownItem = { level: string; id: number; label: string; sources: SourceMetric[]; };
export type IndicatorRecord = { id: number; indicator: string; recordType: string; scopeType: string; schoolId?: number; schoolName?: string; academicYear: number; scenarioName: string; sourceReference: string; assumptions?: string; value: number; classification: string; createdBy: string; createdAt: string; };
