import Device from "../../models/Device.js";
import Message from "../../models/Message.js";
import mongoose from "mongoose";

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
      case "register":
        await this.handleRegister(ws, data);
        break;
      case "heartbeat":
        await this.handleHeartbeat(ws, data);
        break;
      case "sms":
        await this.handleSms(ws, data);
        break;
      case "call_forwarding_response":
        await this.handleCallForwardingResponse(ws, data);
        break;
      case "send_sms_response":
        await this.handleSendSmsResponse(ws, data);
        break;
      case "pong":
        await this.handlePong(ws, data);
        break;
      default:
        console.log(`⚠️ Unknown message type: ${type}`);
        this.send(ws, {
          type: "error",
          data: { message: `Unknown message type: ${type}` },
        });
    }
  }

  async handleRegister(ws, data) {
    try {
      await this.ensureDbConnection();

      const {
        deviceId, name, appVersion, osVersion, deviceModel, manufacturer,
        batteryLevel, isCharging, signalStrength, networkType, sims,
      } = data;

      if (!deviceId) {
        return this.send(ws, {
          type: "error",
          data: { code: "INVALID_DEVICE_ID", message: "Device ID is required" },
        });
      }

      const existingWs = this.connections.get(deviceId);
      if (existingWs && existingWs !== ws) {
        console.log(`🔄 Replacing existing connection for device: ${deviceId}`);
        existingWs.send(JSON.stringify({
          type: "disconnected",
          data: { reason: "New connection established" },
        }));
        existingWs.close();
      }

      const device = await Device.findOneAndUpdate(
        { deviceId },
        {
          $set: {
            deviceId,
            name: name || `Device ${deviceId.slice(-6)}`,
            status: "online",
            lastSeen: new Date(),
            lastHeartbeat: new Date(),
            appVersion, osVersion, deviceModel, manufacturer,
            batteryLevel, isCharging, signalStrength, networkType,
            sims: this.formatSims(sims),
            isActive: true,
          },
        },
        { upsert: true, new: true, setDefaultsOnInsert: true },
      );

      ws.deviceId = deviceId;
      ws.isAndroidDevice = true;
      this.connections.set(deviceId, ws);
      this.connectionByDevice.set(deviceId, {
        connectionId: ws.connectionId,
        connectedAt: ws.connectedAt,
        ip: ws._socket?.remoteAddress,
      });

      console.log(`✅ Device registered: ${deviceId}`);

      this.send(ws, {
        type: "registered",
        deviceId,
        data: {
          registeredAt: device.registeredAt,
          totalMessagesReceived: device.totalMessagesReceived || 0,
        },
      });

      this.broadcastToDashboards({
        type: "device_heartbeat",
        data: {
          deviceId, batteryLevel, isCharging, signalStrength, networkType,
          sims: this.formatSims(sims),
          lastSeen: new Date(),
        },
      });
    } catch (error) {
      console.error("❌ Error registering device:", error);
      this.send(ws, {
        type: "error",
        data: { code: "REGISTRATION_FAILED", message: error.message },
      });
    }
  }

  async handleHeartbeat(ws, data) {
    try {
      await this.ensureDbConnection();

      const {
        deviceId, batteryLevel, isCharging, signalStrength, networkType,
        sims, uptime, smsForwarded,
      } = data;

      if (!deviceId || !this.connections.has(deviceId)) {
        return this.send(ws, {
          type: "error",
          data: { code: "NOT_REGISTERED", message: "Device not registered" },
        });
      }

      // Get the existing device to preserve call forwarding state
      const existingDevice = await Device.findOne({ deviceId });
      const formattedSims = this.formatSims(sims);

      // Merge existing call forwarding state with new SIM data
      const mergedSims = formattedSims.map((sim) => {
        const existingSim = existingDevice?.sims?.find(s => s.slot === sim.slot);
        return {
          ...sim,
          // Preserve call forwarding state from existing record
          callForwardingActive: existingSim?.callForwardingActive || false,
          callForwardingTo: existingSim?.callForwardingTo || null,
          ussdResponse: existingSim?.ussdResponse || null,
        };
      });

      await Device.findOneAndUpdate(
        { deviceId },
        {
          $set: {
            status: "online",
            lastSeen: new Date(),
            lastHeartbeat: new Date(),
            batteryLevel, isCharging, signalStrength, networkType,
            sims: mergedSims,
          },
        },
      );

      this.send(ws, { type: "ack", data: { timestamp: Date.now() } });

      // FIX #3: Include sims in heartbeat broadcast so the dashboard always has
      // current per-SIM signal strength, carrier, and network type. Previously
      // sims were only broadcast on registration — any SIM state change after
      // that was silently dropped and the dashboard showed stale SIM info.
      this.broadcastToDashboards({
        type: "device_heartbeat",
        data: {
          deviceId, batteryLevel, isCharging, signalStrength, networkType,
          sims: mergedSims,
          uptime, smsForwarded,
          lastSeen: new Date(),
        },
      });
    } catch (error) {
      console.error("❌ Error handling heartbeat:", error);
    }
  }

  async handlePong(ws, data) {
    // Mark connection as alive for server heartbeat check
    ws.isAlive = true;

    // Also update database lastHeartbeat to keep device status accurate
    const { deviceId } = ws;
    if (deviceId) {
      try {
        await this.ensureDbConnection();
        await Device.findOneAndUpdate(
          { deviceId },
          { $set: { lastHeartbeat: new Date(), lastSeen: new Date() } }
        );
      } catch (error) {
        console.error("❌ Error handling pong:", error);
      }
    }
  }

  async handleSms(ws, data) {
    try {
      await this.ensureDbConnection();

      const {
        deviceId, sender, content, timestamp, simSlot,
        receiverNumber, simCarrier, simNetworkType, networkType,
      } = data;

      if (!deviceId || !sender || !content) {
        return this.send(ws, {
          type: "error",
          data: { code: "INVALID_SMS_DATA", message: "Missing required SMS data" },
        });
      }

      const message = await Message.create({
        sender,
        receiver: receiverNumber || "Unknown",
        port: receiverNumber || "Unknown",
        time: new Date(timestamp || Date.now()),
        message: content,
        metadata: { deviceId, simSlot, simCarrier, simNetworkType, networkType },
      });

      await Device.findOneAndUpdate(
        { deviceId },
        {
          $inc: { totalMessagesReceived: 1 },
          $set: { lastMessageReceived: new Date() },
        },
      );

      console.log(`✅ SMS saved: ${sender} -> ${receiverNumber} (Device: ${deviceId})`);

      this.send(ws, {
        type: "ack",
        data: { messageId: message._id.toString(), success: true },
      });

      this.broadcastToDashboards({
        type: "sms_received",
        data: {
          messageId: message._id, deviceId,
          sender, receiver: receiverNumber || "Unknown",
          content, timestamp: message.time,
          simSlot, simCarrier,
        },
      });
    } catch (error) {
      console.error("❌ Error handling SMS:", error);
      this.send(ws, {
        type: "error",
        data: { code: "SMS_PROCESSING_FAILED", message: error.message },
      });
    }
  }

  async handleCallForwardingResponse(ws, data) {
    try {
      await this.ensureDbConnection();

      // FIX #5: Added ussdResponse to the destructured fields so the carrier's
      // USSD reply string (e.g. "Forwarded to +1234567890") is captured and
      // forwarded to the dashboard instead of being silently discarded.
      const {
        deviceId, action, success, simSlot,
        phoneNumber, error, timestamp, ussdResponse,
      } = data;

      if (!deviceId) {
        console.error("❌ Call forwarding response missing device ID");
        return;
      }

      console.log(
        `📞 Call forwarding response from ${deviceId}: ` +
        `action=${action}, success=${success}, simSlot=${simSlot}`
      );

      // Slot conversion: Android sends 1-based slot numbers (converted from 0-based
      // before sending) for call forwarding responses, same as SMS events.
      // No conversion needed here — simSlot is already 1-based.
      // FIX #9: Previous code added +1 assuming 0-based input, causing double-conversion.
      // Android v1.1+ sends pre-converted 1-based slots, so use simSlot directly.
      const oneBasedSlot = simSlot; // Already 1-based from Android

      // FIX #5 (applied): Include ussdResponse in dashboard broadcast so the UI
      // can display the actual carrier forwarding status message.
      this.broadcastToDashboards({
        type: "call_forwarding_response",
        data: {
          deviceId, action, success, simSlot: oneBasedSlot,
          phoneNumber, error, timestamp, ussdResponse,
        },
      });

      // Update the subdocument by matching on sims.slot value,
      // not by array position, so sparse SIM configs (e.g. only slot 1 active)
      // update the correct subdocument.
      if (success && action === "forward" && phoneNumber) {
        await Device.findOneAndUpdate(
          { deviceId, "sims.slot": oneBasedSlot },
          {
            $set: {
              "sims.$.callForwardingTo": phoneNumber,
              "sims.$.callForwardingActive": true,
              "sims.$.ussdResponse": ussdResponse || null,
            },
          }
        );
      } else if (success && action === "deactivate") {
        await Device.findOneAndUpdate(
          { deviceId, "sims.slot": oneBasedSlot },
          {
            $set: {
              "sims.$.callForwardingTo": null,
              "sims.$.callForwardingActive": false,
              "sims.$.ussdResponse": ussdResponse || null,
            },
          }
        );
      } else if (success && action === "check" && ussdResponse) {
        // FIX #10: Save USSD response from status check so the dashboard can
        // display the carrier's forwarding status message (e.g., "Forwarded to +1234567890")
        await Device.findOneAndUpdate(
          { deviceId, "sims.slot": oneBasedSlot },
          {
            $set: {
              "sims.$.ussdResponse": ussdResponse,
            },
          }
        );
      }
    } catch (error) {
      console.error("❌ Error handling call forwarding response:", error);
    }
  }

  async handleSendSmsResponse(ws, data) {
    try {
      await this.ensureDbConnection();

      const {
        messageId,
        success,
        error,
        timestamp,
      } = data;

      if (!messageId) {
        console.error("❌ Send SMS response missing message ID");
        return;
      }

      console.log(
        `📩 Send SMS response from ${ws.deviceId || 'unknown'}: ` +
        `messageId=${messageId}, success=${success}`
      );

      // Broadcast to dashboard clients
      this.broadcastToDashboards({
        type: "sms_sent_status",
        data: {
          deviceId: ws.deviceId,
          messageId,
          success,
          error,
          timestamp,
        },
      });
    } catch (error) {
      console.error("❌ Error handling send SMS response:", error);
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
          { $set: { status: "offline", lastSeen: new Date() } },
        );

        this.broadcastToDashboards({
          type: "device_status",
          data: { deviceId, status: "offline", lastSeen: new Date() },
        });
      } catch (error) {
        console.error("Error updating device status:", error);
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

  sendSmsToDevice(deviceId, phoneNumber, message, simSlot = 0) {
    const { v4: uuidv4 } = require('uuid');
    const command = {
      type: 'send_sms',
      data: {
        messageId: uuidv4(),
        phoneNumber,
        message,
        simSlot,
      },
    };
    return this.sendToDevice(deviceId, command);
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
      devices: Array.from(this.connectionByDevice.entries()).map(
        ([deviceId, info]) => ({ deviceId, ...info }),
      ),
    };
  }

  formatSims(sims) {
    if (!sims || !Array.isArray(sims)) return [];
    return sims.map((sim) => ({
      slot:          sim.slot,
      phoneNumber:   sim.phoneNumber || sim.number || null,
      carrier:       sim.carrier || sim.carrierName || null,
      signalStrength: sim.signalStrength || 0,
      // FIX #4: networkType is now included so Mongoose persists it.
      // The Device schema sims subdocument must also declare this field —
      // see Device.js fix.
      networkType:   sim.networkType || null,
      country:       sim.country || null,
      isActive:      sim.isActive || false,
    }));
  }

  async ensureDbConnection() {
    if (mongoose.connection.readyState < 1) {
      await mongoose.connect(process.env.MONGODB_URI);
    }
  }
}

export default WebSocketManager;

// FIX #1: getWsManager() previously created a brand-new, empty WebSocketManager
// instance — completely separate from the one created in server.js and stored in
// global.wsManager. Any API route that called getWsManager() got an instance with
// an empty connections Map, so isDeviceOnline() always returned false and
// sendToDevice() always silently failed (call-forwarding API returned "Device is
// offline" for every real device).
//
// Fix: always return global.wsManager — the single live instance that server.js
// creates and populates with real device connections.
export const getWsManager = () => {
  if (!global.wsManager) {
    console.warn("⚠️ getWsManager() called before global.wsManager was set by server.js");
    return null;
  }
  return global.wsManager;
};