export interface TokenResponse { accessToken: string; refreshToken: string; tokenType: string; expiresIn: number; }
export interface RagSource { citation: string; excerpt: string; sourceType: string; sourceId: string; title: string; path: string; page: number | null; line: number | null; url: string; }
export interface RagAnswer { answer: string; confidence: 'LOW' | 'MEDIUM' | 'HIGH'; sources: RagSource[]; }
export interface TraceItem { sourceType: string; externalId: string; repository: string; title: string; path: string; url: string | null; excerpt: string; relationship: string; justification: string; }
export interface FileHistory { path: string; related: TraceItem[]; }
export interface ImpactResult { impacted: Array<{ path: string; source: TraceItem; justification: string }>; }
export interface ProfileCitation { authority: string; url: string; excerpt: string; }
export interface ProfileReport { answers: Record<string, string | number | null>; score: number; category: string; justification: string; breakdown: Array<{ criterion: string; points: number; maximum: number; explanation: string }>; limitations: string; warning: string; citations: ProfileCitation[]; }
export interface ProfileTurn { sessionId: string; assistantMessage: string; missingCriterion: string | null; whyQuestionAsked: string; complete: boolean; rationale: ProfileCitation; profile: ProfileReport | null; }
export interface ProfileDashboard { activeSessions: number; completedProfiles: number; prudent: number; balanced: number; dynamic: number; }
