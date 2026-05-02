import { httpClient } from '@/shared/api/httpClient'
import type { ApiResponse } from '@/shared/types/ApiResponse'
import type { DocumentMetadata, DocumentType } from './documentsApi.types'

const triggerBlobDownload = (blob: Blob, filename: string) => {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.rel = 'noopener'
  anchor.style.display = 'none'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

export const documentsApi = {
  listByClaimId: (claimId: string) =>
    httpClient
      .get<ApiResponse<DocumentMetadata[]>>(`/documents/claim/${claimId}`)
      .then((r) => r.data),

  /**
   * Fetches file bytes with JWT (required). Do not use metadata.downloadUrl in href/window.open — no Bearer is sent.
   */
  downloadDocumentWithAuth: async (documentId: string, filename: string) => {
    const response = await httpClient.get<Blob>(`/documents/${documentId}/file`, {
      responseType: 'blob',
      timeout: 120_000,
    })
    triggerBlobDownload(response.data, filename)
  },

  /**
   * Opens image/video/PDF in a new tab after an authenticated fetch (object URL).
   */
  openDocumentInNewTabWithAuth: async (documentId: string) => {
    const response = await httpClient.get<Blob>(`/documents/${documentId}/file`, {
      responseType: 'blob',
      timeout: 120_000,
    })
    const url = URL.createObjectURL(response.data)
    window.open(url, '_blank', 'noopener,noreferrer')
    window.setTimeout(() => URL.revokeObjectURL(url), 120_000)
  },

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
