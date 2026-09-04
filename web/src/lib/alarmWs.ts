import { Client } from '@stomp/stompjs'
import type { AlarmActiveMessage } from '../api/alarm'
import { getToken } from './http'

export function subscribeAlarmActive(onMessage: (msg: AlarmActiveMessage) => void): () => void {
  const token = getToken()
  if (!token) return () => {}

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const client = new Client({
    brokerURL: `${protocol}//${window.location.host}/ws`,
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 4000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      client.subscribe('/topic/alarm.active', (frame) => {
        try {
          onMessage(JSON.parse(frame.body) as AlarmActiveMessage)
        } catch {
          /* ignore malformed */
        }
      })
    },
  })
  client.activate()
  return () => {
    void client.deactivate()
  }
}
