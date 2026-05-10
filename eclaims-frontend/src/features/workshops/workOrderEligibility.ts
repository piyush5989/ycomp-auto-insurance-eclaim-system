/**
 * Partner workshops may only create work orders when the claim is APPROVED (matches API rule).
 */
export const canWorkshopSubmitWorkOrderForClaim = (status: string): boolean => status === 'APPROVED'
