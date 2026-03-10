'use client'

import React, { useState, useEffect, useCallback, useRef } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { DeviceCard } from './device-card'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Smartphone,
  Plus,
  Search,
  RefreshCw,
  Wifi,
  WifiOff,
  AlertTriangle,
  Download,
  Radio
} from 'lucide-react'
import { toast } from 'sonner'

interface Device {
  _id: string
  deviceId: string
  name: string
  status: 'online' | 'offline' | 'error'
  lastSeen: string
  batteryLevel?: number
  isCharging: boolean
  signalStrength: number
  networkType: 'wifi' | 'mobile' | 'none'
  deviceModel?: string
  manufacturer?: string
  sims: Array<{
    slot: number
    phoneNumber?: string
    carrier?: string
    isActive: boolean
  }>
  recentMessages: number
  timeSinceLastSeen: {
    minutes: number
    hours: number
    days: number
  }
}

interface DeviceStats {
  total: number
  online: number
  offline: number
  error: number
  totalMessages: number
}

export function DeviceList() {
  const [devices, setDevices] = useState<Device[]>([])
  const [stats, setStats] = useState<DeviceStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [searchTerm, setSearchTerm] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('all')
  const [sortBy, setSortBy] = useState('lastSeen')
  const [sortOrder, setSortOrder] = useState('desc')
  const [page, setPage] = useState(1)
  const [totalPages, setTotalPages] = useState(1)
  const [selectedDevice, setSelectedDevice] = useState<Device | null>(null)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [wsConnected, setWsConnected] = useState(false)

  const wsRef = useRef<WebSocket | null>(null)
  const reconnectTimer = useRef<NodeJS.Timeout | null>(null)

  // ─── REST API ─────────────────────────────────────────────────
  const fetchDevices = useCallback(async () => {
    try {
      setLoading(true)
      const params = new URLSearchParams({
        page: page.toString(),
        limit: '20',
        sortBy,
        sortOrder,
        ...(statusFilter !== 'all' && { status: statusFilter }),
        ...(searchTerm && { search: searchTerm }),
      })

      const response = await fetch(`/api/device/list?${params}`)
      const result = await response.json()

      if (result.success) {
        setDevices(result.data.devices)
        setStats(result.data.stats)
        setTotalPages(result.data.pagination.pages)
      } else {
        toast.error('Failed to fetch devices')
      }
    } catch (error) {
      console.error('Error fetching devices:', error)
      toast.error('Error fetching devices')
    } finally {
      setLoading(false)
    }
  }, [page, statusFilter, sortBy, sortOrder, searchTerm])

  // ─── WebSocket ───────────────────────────────────────────────
  const connectWebSocket = useCallback(() => {
    if (typeof window === 'undefined') return
    if (wsRef.current?.readyState === WebSocket.OPEN) return

    try {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      const wsUrl = `${protocol}//${window.location.host}/gateway?client=dashboard`
      const ws = new WebSocket(wsUrl)
      wsRef.current = ws

      ws.onopen = () => {
        console.log('📊 Dashboard WebSocket connected')
        setWsConnected(true)
        if (reconnectTimer.current) {
          clearTimeout(reconnectTimer.current)
          reconnectTimer.current = null
        }
      }

      ws.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data)

          switch (message.type) {
            case 'device_status': {
              const {
                deviceId, name, status, batteryLevel,
                isCharging, signalStrength, networkType, lastSeen
              } = message.data

              setDevices(prev => {
                const exists = prev.find(d => d.deviceId === deviceId)
                if (!exists) {
                  fetchDevices()
                  return prev
                }
                return prev.map(d =>
                  d.deviceId === deviceId
                    ? {
                        ...d,
                        status,
                        lastSeen,
                        ...(batteryLevel !== undefined && { batteryLevel }),
                        ...(isCharging !== undefined && { isCharging }),
                        ...(signalStrength !== undefined && { signalStrength }),
                        ...(networkType !== undefined && { networkType }),
                      }
                    : d
                )
              })

              setStats(prev => {
                if (!prev) return prev
                const isNowOnline = status === 'online'
                return {
                  ...prev,
                  online: isNowOnline ? prev.online + 1 : Math.max(0, prev.online - 1),
                  offline: isNowOnline ? Math.max(0, prev.offline - 1) : prev.offline + 1,
                }
              })

              toast[status === 'online' ? 'success' : 'warning'](
                `${name || deviceId} is now ${status}`,
                { duration: 3000 }
              )
              break
            }

            case 'device_heartbeat': {
              const {
                deviceId, batteryLevel, isCharging,
                signalStrength, networkType, lastSeen
              } = message.data

              setDevices(prev => prev.map(d =>
                d.deviceId === deviceId
                  ? { ...d, batteryLevel, isCharging, signalStrength, networkType, lastSeen }
                  : d
              ))
              break
            }

            case 'sms_received': {
              const { deviceId, sender } = message.data

              setDevices(prev => prev.map(d =>
                d.deviceId === deviceId
                  ? { ...d, recentMessages: (d.recentMessages || 0) + 1 }
                  : d
              ))

              setStats(prev => prev
                ? { ...prev, totalMessages: prev.totalMessages + 1 }
                : prev
              )

              toast.info(`New SMS from ${sender}`, { duration: 3000 })
              break
            }
          }
        } catch (e) {
          console.warn('Error parsing WebSocket message:', e)
        }
      }

      ws.onclose = () => {
        console.log('📊 Dashboard WebSocket disconnected, reconnecting in 3s...')
        setWsConnected(false)
        wsRef.current = null
        reconnectTimer.current = setTimeout(() => {
          connectWebSocket()
        }, 3000)
      }

      ws.onerror = () => {
        // console.warn instead of console.error — prevents Next.js unhandled error overlay
        console.warn('📊 Dashboard WebSocket error - will reconnect')
        setWsConnected(false)
      }

    } catch (error) {
      console.warn('Failed to connect WebSocket, retrying in 3s...')
      reconnectTimer.current = setTimeout(() => {
        connectWebSocket()
      }, 3000)
    }
  }, [fetchDevices])

  useEffect(() => {
    connectWebSocket()
    return () => {
      if (reconnectTimer.current) clearTimeout(reconnectTimer.current)
      wsRef.current?.close()
    }
  }, [connectWebSocket])

  useEffect(() => {
    fetchDevices()
  }, [fetchDevices])

  // ─── Handlers ─────────────────────────────────────────────────
  const handleRefresh = () => fetchDevices()

  const handleEditDevice = (device: Device) => {
    toast.info(`Edit device: ${device.name}`)
  }

  const handleDeleteDevice = (device: Device) => {
    setSelectedDevice(device)
    setDeleteDialogOpen(true)
  }

  const confirmDelete = async () => {
    if (!selectedDevice) return
    try {
      const response = await fetch(`/api/device/${selectedDevice.deviceId}`, {
        method: 'DELETE',
      })
      const result = await response.json()
      if (result.success) {
        toast.success('Device deleted successfully')
        setDeleteDialogOpen(false)
        setSelectedDevice(null)
        fetchDevices()
      } else {
        toast.error(result.error || 'Failed to delete device')
      }
    } catch (error) {
      console.error('Error deleting device:', error)
      toast.error('Error deleting device')
    }
  }

  const handleToggleStatus = async (device: Device) => {
    try {
      const newStatus = device.status === 'online' ? 'offline' : 'online'
      const response = await fetch(`/api/device/${device.deviceId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: newStatus }),
      })
      const result = await response.json()
      if (result.success) {
        toast.success(`Device ${newStatus === 'online' ? 'activated' : 'deactivated'} successfully`)
        fetchDevices()
      } else {
        toast.error(result.error || 'Failed to update device status')
      }
    } catch (error) {
      console.error('Error updating device status:', error)
      toast.error('Error updating device status')
    }
  }

  const exportDevices = async () => {
    try {
      const response = await fetch('/api/device/list?limit=1000')
      const result = await response.json()
      if (result.success) {
        const csv = [
          ['Device ID', 'Name', 'Status', 'Last Seen', 'Battery', 'Network', 'Model'].join(','),
          ...result.data.devices.map((device: Device) => [
            device.deviceId,
            device.name,
            device.status,
            new Date(device.lastSeen).toISOString(),
            device.batteryLevel || 'N/A',
            device.networkType,
            device.deviceModel || 'N/A',
          ].join(','))
        ].join('\n')

        const blob = new Blob([csv], { type: 'text/csv' })
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `devices_${new Date().toISOString().split('T')[0]}.csv`
        a.click()
        window.URL.revokeObjectURL(url)
      }
    } catch (error) {
      console.error('Error exporting devices:', error)
      toast.error('Error exporting devices')
    }
  }

  // ─── Loading Skeleton ─────────────────────────────────────────
  if (loading && page === 1) {
    return (
      <div className="space-y-6">
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          {[...Array(4)].map((_, i) => (
            <Card key={i}>
              <CardHeader className="pb-2">
                <Skeleton className="h-4 w-20" />
              </CardHeader>
              <CardContent>
                <Skeleton className="h-8 w-16" />
              </CardContent>
            </Card>
          ))}
        </div>
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {[...Array(6)].map((_, i) => (
            <Card key={i}>
              <CardHeader>
                <Skeleton className="h-6 w-32" />
              </CardHeader>
              <CardContent className="space-y-3">
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="h-4 w-1/2" />
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    )
  }

  // ─── UI ───────────────────────────────────────────────────────
  return (
    <div className="space-y-6">

      {/* Stats Cards */}
      {stats && (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-5">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Total Devices</CardTitle>
              <Smartphone className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{stats.total}</div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Online</CardTitle>
              <Wifi className="h-4 w-4 text-green-500" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-green-600">{stats.online}</div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Offline</CardTitle>
              <WifiOff className="h-4 w-4 text-gray-500" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-gray-600">{stats.offline}</div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Error</CardTitle>
              <AlertTriangle className="h-4 w-4 text-red-500" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-red-600">{stats.error}</div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Messages (24h)</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{stats.totalMessages}</div>
            </CardContent>
          </Card>
        </div>
      )}

      {/* Controls */}
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div className="flex flex-1 flex-col gap-2 md:flex-row md:gap-2">
          <div className="relative flex-1 md:max-w-sm">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search devices..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="pl-10"
            />
          </div>
          <Select value={statusFilter} onValueChange={setStatusFilter}>
            <SelectTrigger className="w-full md:w-32">
              <SelectValue placeholder="Status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Status</SelectItem>
              <SelectItem value="online">Online</SelectItem>
              <SelectItem value="offline">Offline</SelectItem>
              <SelectItem value="error">Error</SelectItem>
            </SelectContent>
          </Select>
          <Select value={sortBy} onValueChange={setSortBy}>
            <SelectTrigger className="w-full md:w-32">
              <SelectValue placeholder="Sort by" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="lastSeen">Last Seen</SelectItem>
              <SelectItem value="name">Name</SelectItem>
              <SelectItem value="status">Status</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-1 text-sm text-muted-foreground">
            <Radio className={`h-3 w-3 ${wsConnected ? 'text-green-500 animate-pulse' : 'text-gray-400'}`} />
            <span>{wsConnected ? 'Live' : 'Offline'}</span>
          </div>
          <Button variant="outline" onClick={handleRefresh} disabled={loading}>
            <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
          <Button variant="outline" onClick={exportDevices}>
            <Download className="h-4 w-4 mr-2" />
            Export
          </Button>
          <Button>
            <Plus className="h-4 w-4 mr-2" />
            Add Device
          </Button>
        </div>
      </div>

      {/* Device Grid */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {devices.map((device) => (
          <DeviceCard
            key={device._id}
            device={device}
            onEdit={handleEditDevice}
            onDelete={handleDeleteDevice}
            onToggleStatus={handleToggleStatus}
          />
        ))}
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex justify-center">
          <div className="flex gap-2">
            <Button
              variant="outline"
              onClick={() => setPage(Math.max(1, page - 1))}
              disabled={page === 1}
            >
              Previous
            </Button>
            <span className="flex items-center px-4 text-sm text-muted-foreground">
              Page {page} of {totalPages}
            </span>
            <Button
              variant="outline"
              onClick={() => setPage(Math.min(totalPages, page + 1))}
              disabled={page === totalPages}
            >
              Next
            </Button>
          </div>
        </div>
      )}

      {/* Empty State */}
      {!loading && devices.length === 0 && (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12">
            <Smartphone className="h-12 w-12 text-muted-foreground mb-4" />
            <h3 className="text-lg font-medium mb-2">No devices found</h3>
            <p className="text-muted-foreground text-center mb-4">
              {searchTerm || statusFilter !== 'all'
                ? 'Try adjusting your search filters'
                : 'Get started by adding your first device'}
            </p>
            {!searchTerm && statusFilter === 'all' && (
              <Button>
                <Plus className="h-4 w-4 mr-2" />
                Add Device
              </Button>
            )}
          </CardContent>
        </Card>
      )}

      {/* Delete Dialog */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Deactivate Device</DialogTitle>
            <DialogDescription>
              Are you sure you want to deactivate "{selectedDevice?.name}"? This will stop
              the device from receiving messages and can be reactivated later.
            </DialogDescription>
          </DialogHeader>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setDeleteDialogOpen(false)}>
              Cancel
            </Button>
            <Button variant="destructive" onClick={confirmDelete}>
              Deactivate
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}