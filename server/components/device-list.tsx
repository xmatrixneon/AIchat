'use client'

import React, { useState, useEffect, useCallback, useRef } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import {
  Card, CardContent, CardHeader, CardTitle,
} from '@/components/ui/card'
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { DeviceCard } from './device-card'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Smartphone, Search, Wifi, WifiOff, PhoneForwarded, PhoneOff, Send,
} from 'lucide-react'
import { toast } from 'sonner'
import type { Device, DeviceStats } from '@/types/device'

// ─── Helpers ─────────────────────────────────────────────────────────────────

function computeTimeSince(lastSeen: string) {
  const diffMs   = Date.now() - new Date(lastSeen).getTime()
  const diffMins = Math.floor(diffMs / 60_000)
  const diffHrs  = Math.floor(diffMs / 3_600_000)
  const diffDays = Math.floor(diffMs / 86_400_000)
  return {
    minutes: diffMins % 60,
    hours:   diffHrs  % 24,
    days:    diffDays,
  }
}

// ─── Loading skeleton ─────────────────────────────────────────────────────────

function LoadingSkeleton() {
  return (
    <div className="space-y-6">
      <div className="grid gap-3 grid-cols-3">
        {[...Array(3)].map((_, i) => (
          <Card key={i}>
            <CardHeader className="px-4 py-3 pb-1.5"><Skeleton className="h-3.5 w-20" /></CardHeader>
            <CardContent className="px-4 pb-3"><Skeleton className="h-7 w-14" /></CardContent>
          </Card>
        ))}
      </div>
      <div className="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3">
        {[...Array(6)].map((_, i) => (
          <Card key={i}>
            <CardHeader className="px-4 pt-4 pb-3"><Skeleton className="h-5 w-28" /></CardHeader>
            <CardContent className="px-4 pb-4 space-y-2">
              <Skeleton className="h-3.5 w-full" />
              <Skeleton className="h-3.5 w-3/4" />
              <Skeleton className="h-3.5 w-1/2" />
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}

// ─── Component ────────────────────────────────────────────────────────────────

export function DeviceList() {
  const [devices, setDevices]         = useState<Device[]>([])
  const [stats, setStats]             = useState<DeviceStats | null>(null)
  const [loading, setLoading]         = useState(true)
  const [searchTerm, setSearchTerm]   = useState('')
  const [statusFilter, setStatusFilter] = useState('all')
  const [sortBy, setSortBy]           = useState('lastSeen')
  const [page, setPage]               = useState(1)
  const [totalPages, setTotalPages]   = useState(1)
  const [selectedDevice, setSelectedDevice] = useState<Device | null>(null)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)

  const [forwardNumber, setForwardNumber] = useState('')
  const [cfDialog, setCfDialog] = useState<{
    open: boolean; deviceId: string; simSlot: number; action: 'forward' | 'deactivate'
  }>({ open: false, deviceId: '', simSlot: 0, action: 'forward' })

  const [smsDialog, setSmsDialog] = useState<{
    open: boolean
    deviceId: string
    simSlot: number
    deviceName: string
  }>({ open: false, deviceId: '', simSlot: 0, deviceName: '' })
  const [smsPhoneNumber, setSmsPhoneNumber] = useState('')
  const [smsMessage, setSmsMessage] = useState('')
  const [smsSending, setSmsSending] = useState(false)

  const wsRef           = useRef<WebSocket | null>(null)
  const reconnectTimer  = useRef<NodeJS.Timeout | null>(null)

  // ─── REST ──────────────────────────────────────────────────────────────────

  const fetchDevices = useCallback(async () => {
    try {
      setLoading(true)
      const params = new URLSearchParams({
        page: page.toString(), limit: '20', sortBy, sortOrder: 'desc',
        ...(statusFilter !== 'all' && { status: statusFilter }),
        ...(searchTerm && { search: searchTerm }),
      })
      const res    = await fetch(`/api/device/list?${params}`)
      const result = await res.json()
      if (result.success) {
        setDevices(result.data.devices)
        setStats(result.data.stats)
        setTotalPages(result.data.pagination.pages)
      } else {
        toast.error('Failed to fetch devices')
      }
    } catch {
      toast.error('Error fetching devices')
    } finally {
      setLoading(false)
    }
  }, [page, statusFilter, sortBy, searchTerm])

  // ─── Live clock ────────────────────────────────────────────────────────────

  useEffect(() => {
    const id = setInterval(() => {
      setDevices(prev => prev.map(d => ({ ...d, timeSinceLastSeen: computeTimeSince(d.lastSeen) })))
    }, 60_000)
    return () => clearInterval(id)
  }, [])

  // ─── WebSocket ─────────────────────────────────────────────────────────────

  const connectWS = useCallback(() => {
    if (typeof window === 'undefined') return
    if (wsRef.current?.readyState === WebSocket.OPEN) return

    try {
      const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      const ws    = new WebSocket(`${proto}//${window.location.host}/gateway?client=dashboard`)
      wsRef.current = ws

      ws.onopen  = () => {
        if (reconnectTimer.current) { clearTimeout(reconnectTimer.current); reconnectTimer.current = null }
      }

      ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data)

          switch (msg.type) {
            case 'device_status': {
              const { deviceId, name, status, batteryLevel, isCharging, signalStrength, networkType, lastSeen } = msg.data
              setDevices(prev => {
                if (!prev.find(d => d.deviceId === deviceId)) { fetchDevices(); return prev }
                return prev.map(d => d.deviceId !== deviceId ? d : {
                  ...d, status, lastSeen, timeSinceLastSeen: computeTimeSince(lastSeen),
                  ...(batteryLevel   != null && { batteryLevel }),
                  ...(isCharging     != null && { isCharging }),
                  ...(signalStrength != null && { signalStrength }),
                  ...(networkType    != null && { networkType }),
                })
              })
              setStats(prev => {
                if (!prev) return prev
                return {
                  ...prev,
                  online:  status === 'online' ? prev.online + 1 : Math.max(0, prev.online - 1),
                  offline: status === 'online' ? Math.max(0, prev.offline - 1) : prev.offline + 1,
                }
              })
              toast[status === 'online' ? 'success' : 'warning'](`${name || deviceId} is now ${status}`, { duration: 3000 })
              break
            }

            case 'device_heartbeat': {
              const { deviceId, batteryLevel, isCharging, signalStrength, networkType, sims, lastSeen } = msg.data
              setDevices(prev => prev.map(d => d.deviceId !== deviceId ? d : {
                ...d, batteryLevel, isCharging, signalStrength, networkType, lastSeen,
                timeSinceLastSeen: computeTimeSince(lastSeen),
                ...(sims && {
                  sims: d.sims.map(existing => {
                    const updated = sims.find((s: { slot: number }) => s.slot === existing.slot)
                    if (!updated) return existing
                    return {
                      ...existing,
                      carrier:       updated.carrier       ?? existing.carrier,
                      signalStrength: updated.signalStrength ?? existing.signalStrength,
                      networkType:   updated.networkType   ?? existing.networkType,
                      isActive:      updated.isActive      ?? existing.isActive,
                      callForwardingActive: existing.callForwardingActive,
                      callForwardingTo:     existing.callForwardingTo,
                    }
                  }),
                }),
              }))
              break
            }

            case 'sms_received': {
              const { deviceId, sender } = msg.data
              setDevices(prev => prev.map(d => d.deviceId !== deviceId ? d : { ...d, recentMessages: (d.recentMessages || 0) + 1 }))
              setStats(prev => prev ? { ...prev, totalMessages: prev.totalMessages + 1 } : prev)
              toast.info(`New SMS from ${sender}`, { duration: 3000 })
              break
            }

            case 'call_forwarding_response': {
              const { deviceId, action, success, simSlot, phoneNumber, error } = msg.data
              setDevices(prev => prev.map(d => d.deviceId !== deviceId ? d : {
                ...d,
                sims: d.sims.map(sim => sim.slot !== simSlot ? sim : {
                  ...sim,
                  callForwardingActive: success && action === 'forward',
                  callForwardingTo: success && action === 'forward' ? phoneNumber : undefined,
                }),
              }))
              if (success) toast.success(action === 'forward' ? 'Call forwarding activated' : 'Call forwarding deactivated')
              else toast.error(error || 'Call forwarding failed')
              break
            }

            case 'sms_sent_status': {
              const { deviceId, success, error } = msg.data
              if (success) toast.success('SMS sent successfully')
              else toast.error(error || 'Failed to send SMS')
              break
            }
          }
        } catch (e) {
          console.warn('WS parse error:', e)
        }
      }

      ws.onclose = () => { wsRef.current = null; reconnectTimer.current = setTimeout(connectWS, 3000) }
      ws.onerror = () => console.warn('WS error - will reconnect')
    } catch {
      reconnectTimer.current = setTimeout(connectWS, 3000)
    }
  }, [fetchDevices])

  useEffect(() => {
    connectWS()
    return () => {
      if (reconnectTimer.current) clearTimeout(reconnectTimer.current)
      wsRef.current?.close()
    }
  }, [connectWS])

  useEffect(() => { fetchDevices() }, [fetchDevices])

  // ─── Handlers ──────────────────────────────────────────────────────────────

  const sendCF = async (deviceId: string, simSlot: number, action: 'forward' | 'deactivate', phoneNumber?: string) => {
    try {
      const res    = await fetch(`/api/device/${deviceId}/call-forwarding`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action, simSlot, ...(action === 'forward' && { phoneNumber }) }),
      })
      const result = await res.json()
      if (result.success) {
        toast.success(action === 'forward' ? `Forwarding to ${phoneNumber}` : 'Call forwarding deactivated')
        setDevices(prev => prev.map(d => d.deviceId !== deviceId ? d : {
          ...d,
          sims: d.sims.map(sim => sim.slot !== simSlot + 1 ? sim : {
            ...sim,
            callForwardingActive: action === 'forward',
            callForwardingTo: action === 'forward' ? phoneNumber : undefined,
          }),
        }))
      } else {
        toast.error(result.error || 'Failed to send call forwarding command')
      }
    } catch {
      toast.error('Error sending call forwarding command')
    }
  }

  const handleCallForwarding = (deviceId: string, simSlot: number, action: 'forward' | 'deactivate', currentNumber?: string) => {
    if (action === 'forward') {
      setForwardNumber(currentNumber?.replace('+91 ', '') || '')
      setCfDialog({ open: true, deviceId, simSlot, action: 'forward' })
    } else {
      sendCF(deviceId, simSlot, 'deactivate')
    }
  }

  const confirmCF = () => {
    sendCF(cfDialog.deviceId, cfDialog.simSlot, 'forward', '+91 ' + forwardNumber.trim())
    setCfDialog({ open: false, deviceId: '', simSlot: 0, action: 'forward' })
  }

  const handleSendSms = (deviceId: string, simSlot: number) => {
    const device = devices.find(d => d.deviceId === deviceId)
    setSmsDialog({
      open: true,
      deviceId,
      simSlot,
      deviceName: device?.name || deviceId,
    })
    setSmsPhoneNumber('')
    setSmsMessage('')
  }

  const closeSmsDialog = () => {
    setSmsDialog({ open: false, deviceId: '', simSlot: 0, deviceName: '' })
    setSmsPhoneNumber('')
    setSmsMessage('')
  }

  const sendSms = async () => {
    if (!smsPhoneNumber.trim() || !smsMessage.trim()) {
      toast.error('Please enter phone number and message')
      return
    }

    setSmsSending(true)
    try {
      const res = await fetch(`/api/device/${smsDialog.deviceId}/send-sms`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          phoneNumber: '+91 ' + smsPhoneNumber.trim(),
          message: smsMessage.trim(),
          simSlot: smsDialog.simSlot,
        }),
      })
      const result = await res.json()
      if (result.success) {
        toast.success('SMS sent successfully!')
        closeSmsDialog()
      } else {
        toast.error(result.error || 'Failed to send SMS')
      }
    } catch {
      toast.error('Error sending SMS')
    } finally {
      setSmsSending(false)
    }
  }

  const confirmDelete = async () => {
    if (!selectedDevice) return
    try {
      const res    = await fetch(`/api/device/${selectedDevice.deviceId}`, { method: 'DELETE' })
      const result = await res.json()
      if (result.success) {
        toast.success('Device deleted')
        setDeleteDialogOpen(false)
        setSelectedDevice(null)
        fetchDevices()
      } else {
        toast.error(result.error || 'Failed to delete device')
      }
    } catch {
      toast.error('Error deleting device')
    }
  }

  // ─── Render ────────────────────────────────────────────────────────────────

  if (loading && page === 1) return <LoadingSkeleton />

  const STAT_CARDS = stats ? [
    { label: 'Total',   value: stats.total,   icon: <Smartphone className="h-3.5 w-3.5 text-muted-foreground" /> },
    { label: 'Online',  value: stats.online,  icon: <Wifi      className="h-3.5 w-3.5 text-green-500" />,       color: 'text-green-600' },
    { label: 'Offline', value: stats.offline, icon: <WifiOff   className="h-3.5 w-3.5 text-muted-foreground" />, color: 'text-muted-foreground' },
  ] : []

  return (
    <div className="space-y-6">

      {/* Stats */}
      {stats && (
        <div className="grid gap-3 grid-cols-3">
          {STAT_CARDS.map(({ label, value, icon, color }) => (
            <Card key={label}>
              <CardHeader className="flex flex-row items-center justify-between px-4 py-3 pb-1.5 space-y-0">
                <CardTitle className="text-xs font-medium text-muted-foreground">{label}</CardTitle>
                {icon}
              </CardHeader>
              <CardContent className="px-4 pb-3">
                <p className={`text-2xl font-bold ${color ?? ''}`}>{value}</p>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* Filters */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
        <div className="relative flex-1 sm:max-w-xs">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Search devices…"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="pl-9 h-9"
          />
        </div>
        <Select value={statusFilter} onValueChange={setStatusFilter}>
          <SelectTrigger className="w-full sm:w-32 h-9"><SelectValue placeholder="Status" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Status</SelectItem>
            <SelectItem value="online">Online</SelectItem>
            <SelectItem value="offline">Offline</SelectItem>
            <SelectItem value="error">Error</SelectItem>
          </SelectContent>
        </Select>
        <Select value={sortBy} onValueChange={setSortBy}>
          <SelectTrigger className="w-full sm:w-36 h-9"><SelectValue placeholder="Sort by" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="lastSeen">Last Seen</SelectItem>
            <SelectItem value="name">Name</SelectItem>
            <SelectItem value="status">Status</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {/* Grid */}
      {devices.length === 0 && !loading ? (
        <div className="py-16 text-center text-sm text-muted-foreground">No devices found.</div>
      ) : (
        <div className="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3">
          {devices.map((device) => (
            <DeviceCard
              key={device._id}
              device={device}
              onDelete={(d) => { setSelectedDevice(d); setDeleteDialogOpen(true) }}
              onCallForwarding={handleCallForwarding}
              onSendSms={handleSendSms}
            />
          ))}
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex justify-center items-center gap-2">
          <Button variant="outline" size="sm" className="h-8" onClick={() => setPage(p => p - 1)} disabled={page === 1}>
            Previous
          </Button>
          <span className="text-xs text-muted-foreground tabular-nums">{page} / {totalPages}</span>
          <Button variant="outline" size="sm" className="h-8" onClick={() => setPage(p => p + 1)} disabled={page === totalPages}>
            Next
          </Button>
        </div>
      )}

      {/* Call Forwarding Dialog */}
      <Dialog open={cfDialog.open} onOpenChange={(open) => setCfDialog(p => ({ ...p, open }))}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>Forward calls — SIM {cfDialog.simSlot + 1}</DialogTitle>
            <DialogDescription>Enter the number to forward calls to.</DialogDescription>
          </DialogHeader>
          <div className="space-y-3 py-2">
            <Label htmlFor="fwd-number" className="text-sm">Forward to</Label>
            <div className="flex">
              <span className="inline-flex items-center px-3 rounded-l-md border border-r-0 border-input bg-muted text-muted-foreground text-sm">
                +91
              </span>
              <Input
                id="fwd-number"
                placeholder="9876543210"
                value={forwardNumber}
                onChange={(e) => setForwardNumber(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter' && forwardNumber.trim()) confirmCF() }}
                autoFocus
                className="rounded-l-none h-9"
              />
            </div>
            <p className="text-xs text-muted-foreground">10-digit Indian mobile number</p>
          </div>
          <div className="flex flex-col-reverse sm:flex-row justify-end gap-2">
            <Button variant="outline" className="sm:w-auto" onClick={() => setCfDialog({ open: false, deviceId: '', simSlot: 0, action: 'forward' })}>
              Cancel
            </Button>
            <Button disabled={!forwardNumber.trim()} onClick={confirmCF} className="sm:w-auto">
              <PhoneForwarded className="h-4 w-4 mr-2" />Activate
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      {/* Delete Dialog */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>Delete device</DialogTitle>
            <DialogDescription>
              Delete &quot;{selectedDevice?.name}&quot;? This removes all associated messages and cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col-reverse sm:flex-row justify-end gap-2">
            <Button variant="outline" className="sm:w-auto" onClick={() => setDeleteDialogOpen(false)}>Cancel</Button>
            <Button variant="destructive" className="sm:w-auto" onClick={confirmDelete}>Delete</Button>
          </div>
        </DialogContent>
      </Dialog>

      {/* Send SMS Dialog */}
      <Dialog open={smsDialog.open} onOpenChange={(open) => setSmsDialog(p => ({ ...p, open }))}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Send SMS — SIM {smsDialog.simSlot + 1}</DialogTitle>
            <DialogDescription>
              Send SMS message via {smsDialog.deviceName}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label htmlFor="sms-phone" className="text-sm">Phone Number</Label>
              <div className="flex">
                <span className="inline-flex items-center px-3 rounded-l-md border border-r-0 border-input bg-muted text-muted-foreground text-sm">
                  +91
                </span>
                <Input
                  id="sms-phone"
                  placeholder="9876543210"
                  value={smsPhoneNumber}
                  onChange={(e) => setSmsPhoneNumber(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter' && smsMessage.trim()) sendSms() }}
                  autoFocus
                  className="rounded-l-none h-9"
                />
              </div>
              <p className="text-xs text-muted-foreground">10-digit Indian mobile number</p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="sms-message" className="text-sm">Message</Label>
              <Textarea
                id="sms-message"
                placeholder="Enter your message..."
                value={smsMessage}
                onChange={(e) => setSmsMessage(e.target.value)}
                rows={4}
                maxLength={1600}
                className="resize-none"
              />
              <div className="flex justify-between">
                <p className="text-xs text-muted-foreground">
                  {smsMessage.length} / 1600 characters
                </p>
                <p className="text-xs text-muted-foreground">
                  ~{smsMessage.length > 0 ? Math.ceil(smsMessage.length / 160) : 1} part(s)
                </p>
              </div>
            </div>
          </div>
          <div className="flex flex-col-reverse sm:flex-row justify-end gap-2">
            <Button variant="outline" className="sm:w-auto" onClick={closeSmsDialog}>
              Cancel
            </Button>
            <Button
              disabled={smsSending || !smsPhoneNumber.trim() || !smsMessage.trim()}
              onClick={sendSms}
              className="sm:w-auto"
            >
              {smsSending ? (
                <>Sending...</>
              ) : (
                <>
                  <Send className="h-4 w-4 mr-2" />Send SMS
                </>
              )}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}