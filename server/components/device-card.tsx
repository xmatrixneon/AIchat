'use client'

import React from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Battery,
  Wifi,
  Signal,
  Clock,
  MessageSquare,
  MoreHorizontal,
  PhoneForwarded,
  PhoneOff,
  Send,
} from 'lucide-react'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Device } from '@/types/device'

interface DeviceCardProps {
  device: Device
  onEdit?: (device: Device) => void
  onDelete?: (device: Device) => void
  onToggleStatus?: (device: Device) => void
  onCallForwarding?: (
    deviceId: string,
    simSlot: number,
    action: 'forward' | 'deactivate',
    currentNumber?: string,
  ) => void
  onSendSms?: (
    deviceId: string,
    simSlot: number,
  ) => void
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

const STATUS_DOT: Record<string, string> = {
  online: 'bg-green-500',
  offline: 'bg-gray-400',
  error: 'bg-red-500',
}

const BATTERY_COLOR = (level?: number) => {
  if (level == null) return 'text-gray-400'
  if (level > 60) return 'text-green-500'
  if (level > 20) return 'text-yellow-500'
  return 'text-red-500'
}

function SignalBars({ strength }: { strength: number }) {
  return (
    <div className="flex items-end gap-px">
      {Array.from({ length: 4 }, (_, i) => (
        <div
          key={i}
          className={`w-1 rounded-sm ${i < strength ? 'bg-green-500' : 'bg-gray-200'}`}
          style={{ height: `${(i + 1) * 3 + 2}px` }}
        />
      ))}
    </div>
  )
}

function formatTimeSince({ minutes, hours, days }: Device['timeSinceLastSeen']) {
  if (days > 0) return `${days}d ago`
  if (hours > 0) return `${hours}h ago`
  if (minutes > 0) return `${minutes}m ago`
  return 'Just now'
}

// ─── Component ────────────────────────────────────────────────────────────────

export function DeviceCard({ device, onCallForwarding, onSendSms }: DeviceCardProps) {
  const isOnline = device.status === 'online'

  return (
    <Card className="hover:shadow-md transition-shadow">
      {/* Header */}
      <CardHeader className="px-4 pt-4 pb-3">
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-2 min-w-0">
            <span className={`mt-0.5 w-2.5 h-2.5 rounded-full flex-shrink-0 ${STATUS_DOT[device.status] ?? 'bg-gray-400'}`} />
            <div className="min-w-0">
              <CardTitle className="text-sm font-semibold leading-tight truncate">{device.name}</CardTitle>
              <p className="text-xs text-muted-foreground mt-0.5 truncate">
                #{device.deviceId.slice(-8)}
                {device.deviceModel && ` · ${device.manufacturer ?? ''} ${device.deviceModel}`.trim()}
              </p>
            </div>
          </div>

          {isOnline && onCallForwarding && device.sims.length > 0 && (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon" className="h-7 w-7 flex-shrink-0">
                  <MoreHorizontal className="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="min-w-44">
                {device.sims.map((sim) => (
                  <React.Fragment key={sim.slot}>
                    {sim.isActive && (
                      <DropdownMenuItem
                        onClick={() => onSendSms?.(device.deviceId, sim.slot - 1)}
                      >
                        <Send className="h-4 w-4 mr-2" />
                        <span className="flex-1">Send SMS via SIM {sim.slot}</span>
                      </DropdownMenuItem>
                    )}
                    <DropdownMenuItem
                      onClick={() => onCallForwarding(device.deviceId, sim.slot - 1, 'forward', sim.callForwardingTo)}
                    >
                      <PhoneForwarded className="h-4 w-4 mr-2" />
                      <span className="flex-1">{sim.callForwardingActive ? 'Edit' : 'Forward'} SIM {sim.slot}</span>
                      {sim.callForwardingActive && (
                        <span className="text-xs text-muted-foreground">→ {sim.callForwardingTo?.replace('+91 ', '')}</span>
                      )}
                    </DropdownMenuItem>
                    {sim.callForwardingActive && (
                      <DropdownMenuItem
                        className="text-red-600 focus:text-red-600"
                        onClick={() => onCallForwarding(device.deviceId, sim.slot - 1, 'deactivate')}
                      >
                        <PhoneOff className="h-4 w-4 mr-2" />
                        Unforward SIM {sim.slot}
                      </DropdownMenuItem>
                    )}
                  </React.Fragment>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
          )}
        </div>
      </CardHeader>

      <CardContent className="px-4 pb-4 space-y-3">
        {/* Status indicators */}
        <div className="flex items-center gap-4 text-xs text-muted-foreground">
          <div className="flex items-center gap-1">
            <Battery className={`h-3.5 w-3.5 ${BATTERY_COLOR(device.batteryLevel)}`} />
            <span className="font-medium text-foreground">{device.batteryLevel ?? 'N/A'}%</span>
            {device.isCharging && <span className="w-1.5 h-1.5 bg-green-500 rounded-full animate-pulse" />}
          </div>
          <div className="flex items-center gap-1">
            {device.networkType === 'wifi' ? <Wifi className="h-3.5 w-3.5" /> : <Signal className="h-3.5 w-3.5" />}
            <span className="capitalize">{device.networkType}</span>
          </div>
          <SignalBars strength={device.signalStrength} />
        </div>

        {/* SIM cards */}
        {device.sims.length > 0 && (
          <div className="space-y-1.5">
            <p className="text-xs font-medium text-muted-foreground">SIM cards</p>
            {device.sims.map((sim) => (
              <div
                key={sim.slot}
                className="flex items-center justify-between gap-2 px-2.5 py-2 rounded-md border bg-muted/30"
              >
                <div className="flex items-center gap-1.5 flex-wrap min-w-0">
                  <Badge variant={sim.isActive ? 'default' : 'outline'} className="text-xs h-5 px-1.5">
                    SIM {sim.slot}{sim.phoneNumber && `: ${sim.phoneNumber}`}
                  </Badge>
                  {sim.callForwardingActive && (
                    <Badge
                      variant="secondary"
                      className="text-xs h-5 px-1.5 gap-1 cursor-pointer hover:bg-blue-100 max-w-[140px]"
                      onClick={() => onCallForwarding?.(device.deviceId, sim.slot - 1, 'forward', sim.callForwardingTo)}
                    >
                      <PhoneForwarded className="h-2.5 w-2.5 flex-shrink-0" />
                      <span className="truncate">→ {sim.callForwardingTo || 'Unknown'}</span>
                    </Badge>
                  )}
                </div>

                {isOnline && sim.isActive && onCallForwarding && (
                  <div className="flex items-center gap-1 flex-shrink-0">
                    {sim.callForwardingActive ? (
                      <>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="h-6 px-1.5 text-xs text-blue-600 hover:text-blue-700 hover:bg-blue-50"
                          onClick={() => onCallForwarding(device.deviceId, sim.slot - 1, 'forward', sim.callForwardingTo)}
                        >
                          <PhoneForwarded className="h-3 w-3 mr-1" />Edit
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="h-6 px-1.5 text-xs text-red-600 hover:text-red-700 hover:bg-red-50"
                          onClick={() => onCallForwarding(device.deviceId, sim.slot - 1, 'deactivate')}
                        >
                          <PhoneOff className="h-3 w-3 mr-1" />Off
                        </Button>
                      </>
                    ) : (
                      <>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="h-6 px-1.5 text-xs text-blue-600 hover:text-blue-700 hover:bg-blue-50"
                          onClick={() => onCallForwarding(device.deviceId, sim.slot - 1, 'forward')}
                        >
                          <PhoneForwarded className="h-3 w-3 mr-1" />Forward
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="h-6 px-1.5 text-xs text-green-600 hover:text-green-700 hover:bg-green-50"
                          onClick={() => onSendSms?.(device.deviceId, sim.slot - 1)}
                        >
                          <Send className="h-3 w-3 mr-1" />SMS
                        </Button>
                      </>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}

        {/* Footer */}
        <div className="flex items-center justify-between text-xs text-muted-foreground pt-0.5">
          <div className="flex items-center gap-1">
            <MessageSquare className="h-3.5 w-3.5" />
            <span>{device.recentMessages} msg (24h)</span>
          </div>
          <div className="flex items-center gap-1">
            <Clock className="h-3.5 w-3.5" />
            <span>{formatTimeSince(device.timeSinceLastSeen)}</span>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}