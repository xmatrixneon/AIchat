import { useEffect, useRef, useCallback } from 'react'

interface DeviceStatusEvent {
  type: 'device_status'
  data: {
    deviceId: string
    name?: string
    status: 'online' | 'offline'
    batteryLevel?: number
    isCharging?: boolean
    signalStrength?: number
    networkType?: string
    lastSeen: string
  }
}

interface DeviceHeartbeatEvent {
  type: 'device_heartbeat'
  data: {
    deviceId: string
    batteryLevel: number
    isCharging: boolean
    signalStrength: number
    networkType: string
    lastSeen: string
  }
}

interface SmsReceivedEvent {
  type: 'sms_received'
  data: {
    messageId: string
    deviceId: string
    sender: string
    receiver: string
    content: string
    timestamp: string
    simSlot: number
    simCarrier?: string
  }
}

type WsEvent = DeviceStatusEvent | DeviceHeartbeatEvent | SmsReceivedEvent

interface UseDeviceWebSocketOptions {
  onDeviceStatusChange?: (data: DeviceStatusEvent['data']) => void
  onDeviceHeartbeat?: (data: DeviceHeartbeatEvent['data']) => void
  onSmsReceived?: (data: SmsReceivedEvent['data']) => void
}

export function useDeviceWebSocket({
  onDeviceStatusChange,
  onDeviceHeartbeat,
  onSmsReceived
}: UseDeviceWebSocketOptions) {
  const wsRef = useRef<WebSocket | null>(null)
  const reconnectTimer = useRef<NodeJS.Timeout | null>(null)

  const connect = useCallback(() => {
    try {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      const wsUrl = `${protocol}//${window.location.host}/gateway?client=dashboard`

      const ws = new WebSocket(wsUrl)
      wsRef.current = ws

      ws.onopen = () => {
        console.log('📊 Dashboard WebSocket connected')
        if (reconnectTimer.current) {
          clearTimeout(reconnectTimer.current)
          reconnectTimer.current = null
        }
      }

      ws.onmessage = (event) => {
        try {
          const message: WsEvent = JSON.parse(event.data)

          switch (message.type) {
            case 'device_status':
              onDeviceStatusChange?.(message.data)
              break
            case 'device_heartbeat':
              onDeviceHeartbeat?.(message.data)
              break
            case 'sms_received':
              onSmsReceived?.(message.data)
              break
          }
        } catch (e) {
          console.error('Error parsing WebSocket message:', e)
        }
      }

      ws.onclose = () => {
        console.log('📊 Dashboard WebSocket disconnected, reconnecting in 3s...')
        reconnectTimer.current = setTimeout(connect, 3000)
      }

      ws.onerror = (error) => {
        console.error('📊 Dashboard WebSocket error:', error)
      }

    } catch (error) {
      console.error('Failed to connect WebSocket:', error)
      reconnectTimer.current = setTimeout(connect, 3000)
    }
  }, [onDeviceStatusChange, onDeviceHeartbeat, onSmsReceived])

  useEffect(() => {
    connect()
    return () => {
      if (reconnectTimer.current) clearTimeout(reconnectTimer.current)
      wsRef.current?.close()
    }
  }, [connect])
}