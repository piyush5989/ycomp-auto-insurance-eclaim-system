import type { Config } from 'tailwindcss';

export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: {
          50:  '#e3f2fd',
          100: '#bbdefb',
          500: '#2196f3',
          600: '#1e88e5',
          700: '#1976d2',
          800: '#1565c0',  // Brand primary
          900: '#0d47a1',
        },
        status: {
          submitted:         '#3b82f6',
          assigned:          '#8b5cf6',
          under_survey:      '#f59e0b',
          surveyed:          '#f97316',
          under_adjudication:'#ef4444',
          approved:          '#10b981',
          rejected:          '#dc2626',
          settled:           '#059669',
          withdrawn:         '#6b7280',
          payment_initiated: '#0ea5e9',
        }
      }
    }
  },
  plugins: []
} satisfies Config;
