import { useMutation, useQueryClient } from '@tanstack/react-query';
import { claimsApi } from '../api/claimsApi';
import type { ClaimSubmissionRequest } from '../api/claimsApi.types';

export function useSubmitClaim() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: ClaimSubmissionRequest) => claimsApi.submit(payload),
    onSuccess: () => {
      // Invalidate claims list so it refreshes after new submission
      queryClient.invalidateQueries({ queryKey: ['claims', 'my-claims'] });
    },
  });
}
