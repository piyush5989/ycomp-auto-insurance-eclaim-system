import React from 'react';
import { clsx } from 'clsx';
import { getStatusLabel, getStatusColor, type ClaimStatus } from '@/shared/utils/claimStatusLabel';

interface StatusBadgeProps {
  status: ClaimStatus;
  className?: string;
}

export function StatusBadge({ status, className }: StatusBadgeProps) {
  return (
    <span
      className={clsx(
        'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium',
        getStatusColor(status),
        className
      )}
    >
      {getStatusLabel(status)}
    </span>
  );
}

interface BadgeProps {
  children: React.ReactNode;
  variant?: 'default' | 'success' | 'warning' | 'error' | 'info';
  className?: string;
}

const BADGE_VARIANTS = {
  default: 'bg-gray-100 text-gray-700',
  success: 'bg-green-100 text-green-700',
  warning: 'bg-amber-100 text-amber-700',
  error:   'bg-red-100 text-red-700',
  info:    'bg-blue-100 text-blue-700',
};

export function Badge({ children, variant = 'default', className }: BadgeProps) {
  return (
    <span
      className={clsx(
        'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium',
        BADGE_VARIANTS[variant],
        className
      )}
    >
      {children}
    </span>
  );
}
