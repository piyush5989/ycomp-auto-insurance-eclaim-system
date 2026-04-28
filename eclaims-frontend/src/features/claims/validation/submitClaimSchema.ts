import { z } from 'zod';

export const submitClaimSchema = z.object({
  policyNumber: z
    .string()
    .min(1, 'Policy number is required')
    .regex(/^[A-Z]{3}-\d{8}$/, 'Invalid format. Expected: ABC-12345678'),

  vehicleRegistration: z
    .string()
    .min(1, 'Vehicle registration is required')
    .max(20, 'Must not exceed 20 characters'),

  incidentDate: z
    .string()
    .min(1, 'Incident date is required')
    .refine((d) => new Date(d) <= new Date(), 'Incident date cannot be in the future'),

  incidentLocation: z.string().max(500).optional(),

  description: z.string().max(2000).optional(),

  claimType: z.enum([
    'COLLISION', 'COMPREHENSIVE', 'THEFT', 'FIRE',
    'FLOOD', 'VANDALISM', 'GLASS_DAMAGE', 'ROADSIDE_ASSISTANCE',
  ]),

  policeReportFiled: z.boolean(),

  policeReportNumber: z.string().max(50).optional(),
});

export type SubmitClaimFormData = z.infer<typeof submitClaimSchema>;
