import React, { useEffect, useRef, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Bell, CheckCheck, ChevronRight, X } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { notificationsApi } from '@/features/notifications/api/notificationsApi'
import type { CustomerNotification } from '@/features/notifications/api/notificationsApi.types'
import { useAuth } from '@/shared/auth/KeycloakProvider'

const POLL_INTERVAL_MS = 30_000 // 30s poll — upgrade to SSE/WebSocket at scale

const TYPE_LABEL: Record<CustomerNotification['type'], string> = {
  CLAIM_SUBMITTED:     'Claim Submitted',
  CLAIM_STATUS_CHANGED: 'Claim Update',
  REPAIR_STATUS_UPDATED: 'Repair Update',
  PAYMENT_CONFIRMED:   'Payment Confirmed',
}

export default function NotificationBell() {
  const { authenticated } = useAuth()
  const [open, setOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement>(null)
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const { data } = useQuery({
    queryKey: ['notifications-unread-count'],
    queryFn: () => notificationsApi.listMine(true),
    enabled: authenticated,
    refetchInterval: POLL_INTERVAL_MS,
    select: (res) => res.data ?? [],
  })

  const { data: allNotifications } = useQuery({
    queryKey: ['notifications-list'],
    queryFn: () => notificationsApi.listMine(false),
    enabled: open && authenticated,
    refetchInterval: open ? POLL_INTERVAL_MS : false,
    select: (res) => res.data ?? [],
  })

  const markReadMutation = useMutation({
    mutationFn: (id: string) => notificationsApi.markRead(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications-unread-count'] })
      queryClient.invalidateQueries({ queryKey: ['notifications-list'] })
    },
  })

  const unreadCount = data?.length ?? 0

  // Close on outside click
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const handleNotificationClick = (notification: CustomerNotification) => {
    if (!notification.read) {
      markReadMutation.mutate(notification.id)
    }
    if (notification.claimId) {
      navigate(`/customer/claims/${notification.claimId}`)
      setOpen(false)
    }
  }

  const handleMarkAllRead = () => {
    data?.forEach((n) => markReadMutation.mutate(n.id))
  }

  if (!authenticated) return null

  return (
    <div ref={dropdownRef} className="relative">
      <button
        onClick={() => setOpen((prev) => !prev)}
        className="relative p-2 rounded-lg text-gray-500 hover:bg-gray-100 transition-colors focus:outline-none focus:ring-2 focus:ring-primary-400"
        aria-label={`Notifications — ${unreadCount} unread`}
        tabIndex={0}
      >
        <Bell className="w-5 h-5" />
        {unreadCount > 0 && (
          <span className="absolute -top-0.5 -right-0.5 flex h-4 w-4 items-center justify-center rounded-full bg-red-500 text-white text-[10px] font-bold leading-none">
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 mt-2 w-80 sm:w-96 bg-white rounded-xl shadow-xl border border-gray-200 z-50 overflow-hidden">
          <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100">
            <h3 className="font-semibold text-gray-900 text-sm">Notifications</h3>
            <div className="flex items-center gap-2">
              {unreadCount > 0 && (
                <button
                  onClick={handleMarkAllRead}
                  className="text-xs text-primary-700 hover:underline flex items-center gap-1"
                  aria-label="Mark all as read"
                >
                  <CheckCheck className="w-3.5 h-3.5" />
                  Mark all read
                </button>
              )}
              <button
                onClick={() => setOpen(false)}
                className="p-1 rounded hover:bg-gray-100 text-gray-400"
                aria-label="Close notifications"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          </div>

          <div className="max-h-96 overflow-y-auto divide-y divide-gray-50">
            {!allNotifications || allNotifications.length === 0 ? (
              <div className="py-12 text-center text-gray-400 text-sm">
                <Bell className="w-8 h-8 mx-auto mb-2 opacity-40" />
                No notifications yet
              </div>
            ) : (
              allNotifications.map((n) => (
                <button
                  key={n.id}
                  onClick={() => handleNotificationClick(n)}
                  className={`w-full text-left px-4 py-3 hover:bg-gray-50 transition-colors flex items-start gap-3 ${
                    !n.read ? 'bg-primary-50' : ''
                  }`}
                  aria-label={n.title}
                >
                  <div className={`mt-0.5 w-2 h-2 rounded-full shrink-0 ${!n.read ? 'bg-primary-600' : 'bg-transparent'}`} />
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-xs font-medium text-primary-700">
                        {TYPE_LABEL[n.type] ?? n.type}
                      </span>
                      <span className="text-xs text-gray-400 shrink-0">
                        {new Date(n.createdAt).toLocaleDateString()}
                      </span>
                    </div>
                    <p className="font-medium text-gray-900 text-sm leading-snug mt-0.5">{n.title}</p>
                    <p className="text-xs text-gray-500 mt-0.5 line-clamp-2">{n.message}</p>
                  </div>
                  {n.claimId && <ChevronRight className="w-4 h-4 text-gray-300 shrink-0 mt-0.5" />}
                </button>
              ))
            )}
          </div>

          {allNotifications && allNotifications.length > 0 && (
            <div className="px-4 py-2 border-t border-gray-100 text-center">
              <span className="text-xs text-gray-400">Showing last 20 notifications</span>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
