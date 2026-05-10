import React, { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Shield, UserPlus, CheckCircle, AlertCircle } from 'lucide-react'
import axios from 'axios'

const schema = z.object({
  policyNumber: z
    .string()
    .regex(/^POL-\d{8}$/, 'Policy number must be in format POL-XXXXXXXX'),
  vehicleRegistration: z
    .string()
    .min(2, 'Vehicle registration is required')
    .max(20, 'Max 20 characters'),
  email: z.string().email('Enter a valid email address'),
  password: z
    .string()
    .min(8, 'Password must be at least 8 characters')
    .max(100),
  confirmPassword: z.string(),
}).refine((d) => d.password === d.confirmPassword, {
  message: 'Passwords do not match',
  path: ['confirmPassword'],
})

type RegisterFormData = z.infer<typeof schema>

interface RegisterResponse {
  message: string
  username: string
  customerId: string
}

export default function RegisterPage() {
  const navigate = useNavigate()
  const [success, setSuccess] = useState(false)
  const [serverError, setServerError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormData>({ resolver: zodResolver(schema) })

  const handleRegister = async (data: RegisterFormData) => {
    setServerError(null)
    try {
      // Use axios directly against the Vite proxy path (/api → proxied to :8090)
      // so CORS is not an issue in development.
      const resp = await axios.post<{ data?: RegisterResponse; message?: string }>(
        '/api/v1/onboarding/register',
        {
          policyNumber: data.policyNumber,
          vehicleRegistration: data.vehicleRegistration,
          email: data.email,
          password: data.password,
        },
        { headers: { 'Content-Type': 'application/json' } }
      )
      if (resp.data.data) {
        setSuccess(true)
        setTimeout(() => navigate('/login'), 3000)
      }
    } catch (err: unknown) {
      // Axios errors carry the server response on .response.data
      let message = 'Registration failed. Please check your details and try again.'
      if (axios.isAxiosError(err)) {
        const serverMsg =
          err.response?.data?.message ||
          err.response?.data?.error?.message ||
          err.message
        if (serverMsg) message = serverMsg
      }
      setServerError(message)
    }
  }

  if (success) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-primary-800 to-primary-900 flex items-center justify-center p-4">
        <div className="bg-white rounded-2xl shadow-2xl p-8 w-full max-w-md text-center">
          <CheckCircle className="w-16 h-16 text-green-500 mx-auto mb-4" />
          <h2 className="text-2xl font-bold text-gray-900 mb-2">Account Created!</h2>
          <p className="text-gray-600 mb-4">
            Your account has been set up. You can now sign in with your email and password.
          </p>
          <p className="text-sm text-gray-400">Redirecting to login...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-primary-800 to-primary-900 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-2xl p-8 w-full max-w-md">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-primary-100 rounded-full mb-4">
            <Shield className="w-8 h-8 text-primary-800" />
          </div>
          <h1 className="text-2xl font-bold text-gray-900">Create Your Account</h1>
          <p className="text-gray-500 text-sm mt-2">Register using your existing policy details</p>
        </div>

        {serverError && (
          <div className="flex items-start gap-2 bg-red-50 border border-red-200 text-red-700 rounded-lg p-3 text-sm mb-6">
            <AlertCircle className="w-4 h-4 mt-0.5 shrink-0" />
            <span>{serverError}</span>
          </div>
        )}

        <form onSubmit={handleSubmit(handleRegister)} className="space-y-4" noValidate>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Policy Number
            </label>
            <input
              {...register('policyNumber')}
              placeholder="POL-12345678"
              className="input-field w-full"
              aria-label="Policy number"
            />
            {errors.policyNumber && (
              <p className="text-red-500 text-xs mt-1">{errors.policyNumber.message}</p>
            )}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Vehicle Registration
            </label>
            <input
              {...register('vehicleRegistration')}
              placeholder="ABC-1234"
              className="input-field w-full"
              aria-label="Vehicle registration number"
            />
            {errors.vehicleRegistration && (
              <p className="text-red-500 text-xs mt-1">{errors.vehicleRegistration.message}</p>
            )}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Email Address</label>
            <input
              {...register('email')}
              type="email"
              placeholder="you@example.com"
              className="input-field w-full"
              aria-label="Email address"
            />
            {errors.email && (
              <p className="text-red-500 text-xs mt-1">{errors.email.message}</p>
            )}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Password</label>
            <input
              {...register('password')}
              type="password"
              placeholder="Min. 8 characters"
              className="input-field w-full"
              aria-label="Password"
            />
            {errors.password && (
              <p className="text-red-500 text-xs mt-1">{errors.password.message}</p>
            )}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Confirm Password</label>
            <input
              {...register('confirmPassword')}
              type="password"
              placeholder="Repeat your password"
              className="input-field w-full"
              aria-label="Confirm password"
            />
            {errors.confirmPassword && (
              <p className="text-red-500 text-xs mt-1">{errors.confirmPassword.message}</p>
            )}
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="btn-primary w-full justify-center text-base py-3 mt-2"
          >
            {isSubmitting ? (
              'Creating account...'
            ) : (
              <>
                <UserPlus className="w-5 h-5 mr-2" />
                Create Account
              </>
            )}
          </button>
        </form>

        <p className="text-center text-sm text-gray-500 mt-6">
          Already have an account?{' '}
          <Link to="/login" className="text-primary-700 font-medium hover:underline">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  )
}
