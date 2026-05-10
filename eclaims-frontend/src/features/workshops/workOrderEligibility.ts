/**
 * Partner workshops may only create work orders after adjustor approval (claim APPROVED or later payment states).
 */
export const canWorkshopSubmitWorkOrderForClaim = (status: string): boolean =>
  status === 'APPROVED' || status === 'PAYMENT_INITIATED' || status === 'SETTLED'
