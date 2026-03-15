export interface DeviceSim {
  slot: number
  phoneNumber?: string
  carrier?: string
  signalStrength?: number
  networkType?: string
  isActive: boolean
  callForwardingActive?: boolean
  callForwardingTo?: string
}

export interface Device {
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
  sims: DeviceSim[]
  recentMessages: number
  timeSinceLastSeen: { minutes: number; hours: number; days: number }
}

export interface DeviceStats {
  total: number
  online: number
  offline: number
  error: number
  totalMessages: number
}