'use client'

import React from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Smartphone,
  Battery,
  Wifi,
  Signal,
  MapPin,
  Clock,
  MessageSquare,
  MoreHorizontal,
  Edit,
  Trash2,
  Power
} from 'lucide-react'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useRouter } from 'next/navigation'

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

interface DeviceCardProps {
  device: Device
  onEdit?: (device: Device) => void
  onDelete?: (device: Device) => void
  onToggleStatus?: (device: Device) => void
}

export function DeviceCard({ device, onEdit, onDelete, onToggleStatus }: DeviceCardProps) {
  const router = useRouter()

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'online': return 'bg-green-500'
      case 'offline': return 'bg-gray-500'
      case 'error': return 'bg-red-500'
      default: return 'bg-gray-500'
    }
  }

  const getStatusBadgeVariant = (status: string) => {
    switch (status) {
      case 'online': return 'default'
      case 'offline': return 'secondary'
      case 'error': return 'destructive'
      default: return 'secondary'
    }
  }

  const getBatteryColor = (level?: number) => {
    if (!level) return 'text-gray-400'
    if (level > 60) return 'text-green-500'
    if (level > 20) return 'text-yellow-500'
    return 'text-red-500'
  }

  const getSignalBars = (strength: number) => {
    return Array.from({ length: 4 }, (_, i) => (
      <div
        key={i}
        className={`w-1 h-3 bg-gray-300 rounded-full ${
          i < strength ? 'bg-green-500' : 'bg-gray-300'
        }`}
        style={{ height: `${(i + 1) * 3}px` }}
      />
    ))
  }

  const formatTimeSince = (timeSince: { minutes: number; hours: number; days: number }) => {
    if (timeSince.days > 0) return `${timeSince.days} day${timeSince.days > 1 ? 's' : ''} ago`
    if (timeSince.hours > 0) return `${timeSince.hours} hour${timeSince.hours > 1 ? 's' : ''} ago`
    if (timeSince.minutes > 0) return `${timeSince.minutes} minute${timeSince.minutes > 1 ? 's' : ''} ago`
    return 'Just now'
  }

  const getNetworkIcon = (type: string) => {
    switch (type) {
      case 'wifi': return <Wifi className="h-4 w-4" />
      case 'mobile': return <Signal className="h-4 w-4" />
      default: return <Signal className="h-4 w-4 opacity-30" />
    }
  }

  return (
    <Card className="hover:shadow-md transition-shadow">
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className={`w-3 h-3 rounded-full ${getStatusColor(device.status)}`} />
            <CardTitle className="text-lg">{device.name}</CardTitle>
            <Badge variant={getStatusBadgeVariant(device.status)}>
              {device.status}
            </Badge>
          </div>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="sm">
                <MoreHorizontal className="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={() => router.push(`/dashboard/devices/${device.deviceId}`)}>
                View Details
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => onEdit?.(device)}>
                <Edit className="h-4 w-4 mr-2" />
                Edit
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                onClick={() => onToggleStatus?.(device)}
                className={device.status === 'online' ? 'text-red-600' : 'text-green-600'}
              >
                <Power className="h-4 w-4 mr-2" />
                {device.status === 'online' ? 'Deactivate' : 'Activate'}
              </DropdownMenuItem>
              <DropdownMenuItem
                onClick={() => onDelete?.(device)}
                className="text-red-600"
              >
                <Trash2 className="h-4 w-4 mr-2" />
                Delete
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
        <div className="text-sm text-muted-foreground">
          ID: {device.deviceId.slice(-8)}
          {device.deviceModel && (
            <span className="ml-2">
              {device.manufacturer} {device.deviceModel}
            </span>
          )}
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* Device Status Indicators */}
        <div className="flex items-center justify-between text-sm">
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-1">
              <Battery className={`h-4 w-4 ${getBatteryColor(device.batteryLevel)}`} />
              <span>{device.batteryLevel ?? 'N/A'}%</span>
              {device.isCharging && (
                <div className="w-1 h-1 bg-green-500 rounded-full animate-pulse" />
              )}
            </div>
            <div className="flex items-center gap-1">
              {getNetworkIcon(device.networkType)}
              <span className="capitalize">{device.networkType}</span>
            </div>
            <div className="flex items-center gap-1">
              <div className="flex items-end gap-0.5">
                {getSignalBars(device.signalStrength)}
              </div>
            </div>
          </div>
        </div>

        {/* SIM Information */}
        {device.sims && device.sims.length > 0 && (
          <div className="space-y-1">
            <div className="text-xs font-medium text-muted-foreground">SIM Cards</div>
            <div className="flex gap-2">
              {device.sims.map((sim) => (
                <Badge
                  key={sim.slot}
                  variant={sim.isActive ? 'default' : 'outline'}
                  className="text-xs"
                >
                  SIM {sim.slot}
                  {sim.phoneNumber && `: ${sim.phoneNumber.slice(-4)}`}
                </Badge>
              ))}
            </div>
          </div>
        )}

        {/* Activity Information */}
        <div className="flex items-center justify-between text-sm">
          <div className="flex items-center gap-2">
            <MessageSquare className="h-4 w-4 text-muted-foreground" />
            <span>{device.recentMessages} messages (24h)</span>
          </div>
          <div className="flex items-center gap-1 text-muted-foreground">
            <Clock className="h-4 w-4" />
            <span>{formatTimeSince(device.timeSinceLastSeen)}</span>
          </div>
        </div>

        {/* Last Known Location (if available) */}
        {device.lastSeen && (
          <div className="text-xs text-muted-foreground">
            Last seen: {new Date(device.lastSeen).toLocaleString()}
          </div>
        )}
      </CardContent>
    </Card>
  )
}