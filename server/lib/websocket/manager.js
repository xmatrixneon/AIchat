import Device from '../../models/Device.js';
import Message from '../../models/Message.js';
import mongoose from 'mongoose';

class WebSocketManager {
  constructor() {
    this.connections = new Map();        // deviceId -> WebSocket (Android devices)
    this.connectionByDevice = new Map(); // deviceId -> connection info
    this.dashboardClients = new Set();   // Browser dashboard clients
  }

  addDashboardClient(ws) {
    this.dashboardClients.add(ws);
    console.log(`📊 Dashboard client connected (total: ${this.dashboardClients.size})`);
  }

  removeDashboardClient(ws) {
    this.dashboardClients.delete(ws);
    console.log(`📊 Dashboard client disconnected (total: ${this.dashboardClients.size})`);
  }

  async handleMessage(ws, message) {
    const { type, data } = message;
    console.log(`📨 [${ws.connectionId}] Received: ${type}`);

    switch (type) {
      case 'register':
        await this.handleRegister(ws, data);
        break;
      case 'heartbeat':
        await this.handleHeartbeat(ws, data);
        break;
      case 'sms':
        await this.handleSms(ws, data);
        break;
      case 'pong':
        ws.isAlive = true;
        break;
      default:
        console.log(`⚠️ Unknown message type: ${type}`);
        this.send(ws, {
          type: 'error',
          data: { message: `Unknown message type: ${type}` }
        });
    }
  }

  async handleRegister(ws, data) {
    try {
      await this.ensureDbConnection();

      const {
        deviceId, name, appVersion, osVersion, deviceModel,
        manufacturer, batteryLevel, isCharging, signalStrength,
        networkType, sims
      } = data;

      if (!deviceId) {
        return this.send(ws, {
          type: 'error',
          data: { code: 'INVALID_DEVICE_ID', message: 'Device ID is required' }
        });
      }

      const existingWs = this.connections.get(deviceId);
      if (existingWs && existingWs !== ws) {
        console.log(`🔄 Replacing existing connection for device: ${deviceId}`);
        existingWs.send(JSON.stringify({
          type: 'disconnected',
          data: { reason: 'New connection established' }
        }));
        existingWs.close();
      }

      const device = await Device.findOneAndUpdate(
        { deviceId },
        {
          $set: {
            deviceId,
            name: name || `Device ${deviceId.slice(-6)}`,
            status: 'online',
            lastSeen: new Date(),
            lastHeartbeat: new Date(),
            appVersion, osVersion, deviceModel, manufacturer,
            batteryLevel, isCharging, signalStrength, networkType,
            sims: this.formatSims(sims),
            isActive: true
          }
        },
        { upsert: true, new: true, setDefaultsOnInsert: true }
      );

      ws.deviceId = deviceId;
      ws.isAndroidDevice = true;
      this.connections.set(deviceId, ws);
      this.connectionByDevice.set(deviceId, {
        connectionId: ws.connectionId,
        connectedAt: ws.connectedAt,
        ip: ws._socket?.remoteAddress
      });

      console.log(`✅ Device registered: ${deviceId}`);

      this.send(ws, {
        type: 'registered',
        deviceId,
        data: {
          registeredAt: device.registeredAt,
          totalMessagesReceived: device.totalMessagesReceived || 0
        }
      });

      this.broadcastToDashboards({
        type: 'device_status',
        data: {
          deviceId,
          name: device.name,
          status: 'online',
          batteryLevel,
          isCharging,
          signalStrength,
          networkType,
          sims: this.formatSims(sims),
          lastSeen: new Date()
        }
      });

    } catch (error) {
      console.error('❌ Error registering device:', error);
      this.send(ws, {
        type: 'error',
        data: { code: 'REGISTRATION_FAILED', message: error.message }
      });
    }
  }

  async handleHeartbeat(ws, data) {
    try {
      await this.ensureDbConnection();

      const {
        deviceId, batteryLevel, isCharging, signalStrength,
        networkType, sims, uptime, smsForwarded
      } = data;

      if (!deviceId || !this.connections.has(deviceId)) {
        return this.send(ws, {
          type: 'error',
          data: { code: 'NOT_REGISTERED', message: 'Device not registered' }
        });
      }

      await Device.findOneAndUpdate(
        { deviceId },
        {
          $set: {
            status: 'online',
            lastSeen: new Date(),
            lastHeartbeat: new Date(),
            batteryLevel, isCharging, signalStrength, networkType,
            sims: this.formatSims(sims)
          }
        }
      );

      this.send(ws, { type: 'ack', data: { timestamp: Date.now() } });

      this.broadcastToDashboards({
        type: 'device_heartbeat',
        data: {
          deviceId,
          batteryLevel,
          isCharging,
          signalStrength,
          networkType,
          lastSeen: new Date()
        }
      });

    } catch (error) {
      console.error('❌ Error handling heartbeat:', error);
    }
  }

  async handleSms(ws, data) {
    try {
      await this.ensureDbConnection();

      const {
        deviceId, sender, content, timestamp,
        simSlot, receiverNumber, simCarrier, simNetworkType, networkType
      } = data;

      if (!deviceId || !sender || !content) {
        return this.send(ws, {
          type: 'error',
          data: { code: 'INVALID_SMS_DATA', message: 'Missing required SMS data' }
        });
      }

      // Store sender and receiver exactly as received — no normalization
      const message = await Message.create({
        sender: sender,
        receiver: receiverNumber || 'Unknown',
        port: receiverNumber || 'Unknown',
        time: new Date(timestamp || Date.now()),
        message: content,
        metadata: {
          deviceId,
          simSlot,
          simCarrier,
          simNetworkType,
          networkType
        }
      });

      await Device.findOneAndUpdate(
        { deviceId },
        {
          $inc: { totalMessagesReceived: 1 },
          $set: { lastMessageReceived: new Date() }
        }
      );

      console.log(`✅ SMS saved: ${sender} -> ${receiverNumber} (Device: ${deviceId})`);

      this.send(ws, {
        type: 'ack',
        data: { messageId: message._id.toString(), success: true }
      });

      this.broadcastToDashboards({
        type: 'sms_received',
        data: {
          messageId: message._id,
          deviceId,
          sender: sender,
          receiver: receiverNumber || 'Unknown',
          content,
          timestamp: message.time,
          simSlot,
          simCarrier
        }
      });

    } catch (error) {
      console.error('❌ Error handling SMS:', error);
      this.send(ws, {
        type: 'error',
        data: { code: 'SMS_PROCESSING_FAILED', message: error.message }
      });
    }
  }

  async handleDisconnect(ws) {
    const { deviceId } = ws;

    if (deviceId && ws.isAndroidDevice) {
      this.connections.delete(deviceId);
      this.connectionByDevice.delete(deviceId);

      try {
        await this.ensureDbConnection();
        await Device.findOneAndUpdate(
          { deviceId },
          { $set: { status: 'offline', lastSeen: new Date() } }
        );

        this.broadcastToDashboards({
          type: 'device_status',
          data: { deviceId, status: 'offline', lastSeen: new Date() }
        });

      } catch (error) {
        console.error('Error updating device status:', error);
      }

      console.log(`🔌 Device disconnected: ${deviceId}`);
    }
  }

  broadcastToDashboards(message) {
    const json = JSON.stringify(message);
    let sent = 0;
    this.dashboardClients.forEach((client) => {
      if (client.readyState === 1) {
        client.send(json);
        sent++;
      }
    });
    console.log(`📢 Broadcasted ${message.type} to ${sent} dashboard clients`);
  }

  send(ws, message) {
    if (ws.readyState === 1) {
      ws.send(JSON.stringify(message));
    }
  }

  sendToDevice(deviceId, message) {
    const ws = this.connections.get(deviceId);
    if (ws && ws.readyState === 1) {
      this.send(ws, message);
      return true;
    }
    return false;
  }

  getOnlineDevices() {
    return Array.from(this.connections.keys());
  }

  isDeviceOnline(deviceId) {
    return this.connections.has(deviceId);
  }

  getStats() {
    return {
      totalConnections: this.connections.size,
      devices: Array.from(this.connectionByDevice.entries()).map(([deviceId, info]) => ({
        deviceId, ...info
      }))
    };
  }

  formatSims(sims) {
    if (!sims || !Array.isArray(sims)) return [];
    return sims.map(sim => ({
      slot: sim.slot,
      phoneNumber: sim.phoneNumber || sim.number || null,
      carrier: sim.carrier || sim.carrierName || null,
      signalStrength: sim.signalStrength || 0,
      isActive: sim.isActive || false
    }));
  }

  async ensureDbConnection() {
    if (mongoose.connection.readyState < 1) {
      await mongoose.connect(process.env.MONGODB_URI);
    }
  }
}

export default WebSocketManager;