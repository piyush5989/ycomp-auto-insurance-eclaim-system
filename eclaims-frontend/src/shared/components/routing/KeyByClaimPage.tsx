import type { ComponentType } from 'react'
import { useParams } from 'react-router-dom'

/**
 * Remounts the page when :claimId changes so React state and effects cannot leak across claims.
 */
export const KeyByClaimPage = ({ Page }: { Page: ComponentType }) => {
  const { claimId } = useParams<{ claimId: string }>()
  if (!claimId) return null
  return <Page key={claimId} />
}
