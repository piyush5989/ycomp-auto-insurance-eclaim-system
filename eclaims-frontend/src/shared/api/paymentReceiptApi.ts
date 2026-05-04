import { httpClient } from '@/shared/api/httpClient'

const getFilenameFromDisposition = (disposition?: string, fallback?: string) => {
  if (!disposition) return fallback ?? 'payment-receipt.pdf'
  const match = disposition.match(/filename="([^"]+)"/i)
  return match?.[1] ?? fallback ?? 'payment-receipt.pdf'
}

export const downloadClaimReceiptPdf = async (claimId: string) => {
  const response = await httpClient.get(`/payments/claims/${claimId}/receipt.pdf`, {
    responseType: 'blob',
  })

  const disposition = response.headers['content-disposition']
  const filename = getFilenameFromDisposition(disposition, `receipt-${claimId}.pdf`)
  const blob = new Blob([response.data], { type: 'application/pdf' })
  const url = window.URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  window.URL.revokeObjectURL(url)
}
