import { apiGet, apiPost, httpClient } from '../client'

export type FeedbackChannel = 'WEB' | 'PHONE' | 'SMS' | 'COUNTER' | 'INTERNAL'
export type FeedbackStatus = 'SUBMITTED' | 'ACCEPTED' | 'PROCESSING' | 'NEED_MORE_INFO' | 'RESOLVED' | 'CLOSED' | 'REJECTED' | 'CANCELLED'
export type FeedbackUrgency = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'
export type FeedbackReportType = 'WALL_CRACK' | 'SURFACE_FALLING' | 'WATER_LEAKAGE' | 'ILLEGAL_MODIFICATION' | 'FIRE_ACCESS' | 'OTHER'
export type FeedbackReinspectionStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'

export interface PublicFeedbackCommunity { communityId: string; communityName: string; administrativeRegion?: string; address?: string }
export interface PublicFeedbackBuilding { buildingId: string; buildingCode: string; buildingName: string; address?: string }
export interface FeedbackImage { assetId: string; originalFilename: string; contentType: 'image/jpeg' | 'image/png' | 'image/webp'; fileSize: number; createdAt: string }
export interface CreateFeedbackPayload { communityId: string; buildingId?: string; reportType: FeedbackReportType; description: string; urgency: FeedbackUrgency; reporterName?: string; contactPhone?: string; contactEmail?: string; contactConsent: boolean; locationText?: string; feedbackChannel?: Exclude<FeedbackChannel, 'WEB'> }
export interface FeedbackCreatedResult { reportId: string; reportCode: string; trackingSecret: string; status: FeedbackStatus; feedbackChannel: FeedbackChannel; submittedAt: string; maxImageCount: number; disclaimer: string }
export interface FeedbackImageUploadResult extends FeedbackImage { imageCount: number; remainingSlots: number }
export interface FeedbackEvent { eventType: string; fromStatus?: FeedbackStatus; toStatus?: FeedbackStatus; message?: string; createdAt: string }
export interface PublicFeedbackDetail { reportId: string; reportCode: string; reportType: FeedbackReportType; description: string; status: FeedbackStatus; urgency: FeedbackUrgency; feedbackChannel: FeedbackChannel; reporterName?: string; contactPhone?: string; contactEmail?: string; locationText?: string; handlingSummary?: string; submittedAt: string; handledAt?: string; closedAt?: string; communityName: string; buildingName?: string; events: FeedbackEvent[]; images: FeedbackImage[]; imageCount: number; maxImageCount: number; disclaimer: string }
export interface FeedbackManagementRow extends Omit<PublicFeedbackDetail, 'events' | 'images' | 'disclaimer' | 'maxImageCount'> { communityId: string; buildingId?: string; imageCount: number; reinspectionTaskId?: string; reinspectionTaskCode?: string; reinspectionStatus?: FeedbackReinspectionStatus }
export interface FeedbackPage { content: FeedbackManagementRow[]; page: { page: number; size: number; totalElements: number; totalPages: number } }
export interface FeedbackManagementQuery { status?: FeedbackStatus; feedbackChannel?: FeedbackChannel; communityId?: string; buildingId?: string; keyword?: string; submittedFrom?: string; submittedTo?: string; page?: number; size?: number }
export interface UpdateFeedbackStatusPayload { status: FeedbackStatus; handlingSummary?: string; message?: string; publicVisible?: boolean }
export interface SubmitFeedbackRectificationPayload { handlingSummary: string; message?: string }
export interface FeedbackRectificationResult { reportId: string; reportCode?: string; status: 'RESOLVED'; rectificationEvidenceCount: number; formalRiskChanged: false; nextStep?: string }
export interface FeedbackReinspection { reportId?: string; reportCode?: string; taskId: string; taskCode?: string; buildingId?: string; inspectionType?: string; status: FeedbackReinspectionStatus; reused?: boolean; formalRiskChanged?: false; formalRiskNotice?: string }
export interface CompleteFeedbackReinspectionPayload { passed: boolean; summary: string }
export interface FeedbackReinspectionResult { reportId: string; reportCode?: string; status: FeedbackStatus; taskId: string; taskCode?: string; reinspectionPassed: boolean; formalRiskChanged: false; nextStep?: string }

export function listPublicFeedbackCommunities(): Promise<PublicFeedbackCommunity[]> { return apiGet('/api/v1/public/feedback/communities') }
export function listPublicFeedbackBuildings(communityId: string): Promise<PublicFeedbackBuilding[]> { return apiGet(`/api/v1/public/feedback/communities/${communityId}/buildings`) }
export function createPublicFeedback(payload: CreateFeedbackPayload): Promise<FeedbackCreatedResult> { return apiPost('/api/v1/public/feedback/reports', payload) }
export function uploadPublicFeedbackImage(reportCode: string, trackingSecret: string, file: File): Promise<FeedbackImageUploadResult> { const formData=new FormData(); formData.append('trackingSecret',trackingSecret); formData.append('file',file,file.name); return apiPost(`/api/v1/public/feedback/reports/${encodeURIComponent(reportCode)}/images`,formData) }
export function listPublicFeedbackImages(reportCode: string, trackingSecret: string): Promise<FeedbackImage[]> { return apiGet(`/api/v1/public/feedback/reports/${encodeURIComponent(reportCode)}/images`,{trackingSecret}) }
export async function fetchPublicFeedbackImageBlobUrl(reportCode: string, trackingSecret: string, assetId: string): Promise<string> { const response=await httpClient.get<Blob>(`/api/v1/public/feedback/reports/${encodeURIComponent(reportCode)}/images/${assetId}/content`,{params:{trackingSecret},responseType:'blob'}); return URL.createObjectURL(response.data) }
export function trackPublicFeedback(reportCode: string, trackingSecret: string): Promise<PublicFeedbackDetail> { return apiGet(`/api/v1/public/feedback/reports/${encodeURIComponent(reportCode)}`,{trackingSecret}) }
export function listFeedbackReports(params: FeedbackManagementQuery = {}): Promise<FeedbackPage> { return apiGet('/api/v1/feedback/reports',{...params}) }
export function listFeedbackImages(reportId: string): Promise<FeedbackImage[]> { return apiGet(`/api/v1/feedback/reports/${reportId}/images`) }
export function createManualFeedback(payload: CreateFeedbackPayload): Promise<FeedbackCreatedResult> { return apiPost('/api/v1/feedback/reports/manual',payload) }
export function updateFeedbackStatus(reportId: string, payload: UpdateFeedbackStatusPayload): Promise<Record<string, unknown>> { return apiPost(`/api/v1/feedback/reports/${reportId}/status`,payload) }
export function submitFeedbackRectification(reportId: string, payload: SubmitFeedbackRectificationPayload): Promise<FeedbackRectificationResult> { return apiPost(`/api/v1/feedback/reports/${reportId}/rectification/submit`,payload) }
export function createFeedbackReinspection(reportId: string): Promise<FeedbackReinspection> { return apiPost(`/api/v1/feedback/reports/${reportId}/reinspection`,{}) }
export function getFeedbackReinspection(reportId: string): Promise<FeedbackReinspection | null> { return apiGet(`/api/v1/feedback/reports/${reportId}/reinspection`) }
export function completeFeedbackReinspection(reportId: string, payload: CompleteFeedbackReinspectionPayload): Promise<FeedbackReinspectionResult> { return apiPost(`/api/v1/feedback/reports/${reportId}/reinspection/result`,payload) }
