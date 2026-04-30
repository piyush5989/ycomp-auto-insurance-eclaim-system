export type DocumentType =
  | 'POLICE_REPORT'
  | 'PHOTO_DAMAGE'
  | 'REPAIR_ESTIMATE'
  | 'MEDICAL_REPORT'
  | 'INVOICE'
  | 'OTHER'

export interface DocumentMetadata {
  documentId: string
  claimId: string
  documentType: DocumentType
  filename: string
  contentType: string
  fileSizeBytes: number
  downloadUrl: string
  uploadedByUserId: string
  uploadedAt: string
}
