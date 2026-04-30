import { httpClient } from '@/shared/api/httpClient'
import type { ApiResponse } from '@/shared/types/ApiResponse'
import type { DocumentMetadata, DocumentType } from './documentsApi.types'

export const documentsApi = {
  listByClaimId: (claimId: string) =>
    httpClient
      .get<ApiResponse<DocumentMetadata[]>>(`/documents/claim/${claimId}`)
      .then((r) => r.data),

  uploadDocument: (claimId: string, file: File, documentType: DocumentType) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('documentType', documentType)
    formData.append('claimId', claimId)
    return httpClient
      .post<ApiResponse<DocumentMetadata>>('/documents/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data)
  },

  deleteDocument: (documentId: string) =>
    httpClient.delete<ApiResponse<void>>(`/documents/${documentId}`).then((r) => r.data),
}
