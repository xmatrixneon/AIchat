import { config } from "dotenv";
import mongoose from "mongoose";
import cron from "node-cron";
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import Numbers from "../models/Numbers.js";
import Country from "../models/Countires.js";
import Device from "../models/Device.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

config({ path: join(__dirname, "..", ".env.local") });
config({ path: join(__dirname, "..", ".env") });

const MONGO_URI = process.env.MONGODB_URI || process.env.MONGO_URI;

async function getIndiaId() {
  const country = await Country.findOne({ name: "India" });
  if (!country) {
    throw new Error("India country not found in database. Please create it first.");
  }
  return country._id;
}

async function syncDeviceNumbers() {
  try {
    const fiveMinutesAgo = new Date(Date.now() - 5 * 60 * 1000);

    const activeDevices = await Device.find({ isActive: true });
    const onlineDevices = activeDevices.filter(d => d.lastHeartbeat >= fiveMinutesAgo);
    const offlineDevices = activeDevices.filter(d => d.lastHeartbeat < fiveMinutesAgo);

    console.log(`📱 Processing ${activeDevices.length} devices | Online: ${onlineDevices.length} | Offline: ${offlineDevices.length}`);

    const indiaId = await getIndiaId();
    const allOnlineNumberPorts = new Set();
    let syncedCount = 0;
    let deactivatedCount = 0;

    // ✅ ONLINE devices — upsert numbers
    for (const device of onlineDevices) {
      if (device.status !== 'online') {
        device.status = 'online';
        await device.save();
        console.log(`🟢 Device ${device.deviceId} marked online`);
      }

      for (const sim of device.sims) {
        if (sim.phoneNumber && sim.isActive) {
          const port = `${device.deviceId}-SIM${sim.slot}`;
          allOnlineNumberPorts.add(port);

          let phoneNumber = String(sim.phoneNumber).replace(/\D/g, '');
          if (phoneNumber.length === 12 && phoneNumber.startsWith("91")) {
            phoneNumber = phoneNumber.substring(2);
          }
          phoneNumber = parseInt(phoneNumber);

          if (phoneNumber && phoneNumber.toString().length === 10) {
            await Numbers.findOneAndUpdate(
              { number: phoneNumber },
              {
                $set: {
                  countryid: indiaId,
                  port,
                  operator: sim.carrier || null,
                  signal: sim.signalStrength || 0,
                  active: true,
                  lastRotation: new Date(),
                  locked: false,
                  iccid: sim.iccid || null,
                  imsi: sim.imsi || null,
                }
              },
              { upsert: true, new: true }
            );
            syncedCount++;
            console.log(`✅ Synced number ${phoneNumber} from ${device.deviceId} SIM${sim.slot}`);
          } else {
            console.warn(`⚠️ Invalid number on ${device.deviceId} SIM${sim.slot}: "${sim.phoneNumber}"`);
          }
        }
      }
    }

    // ❌ OFFLINE devices — deactivate only
    for (const device of offlineDevices) {
      if (device.status !== 'offline') {
        device.status = 'offline';
        await device.save();
        console.log(`🔴 Device ${device.deviceId} marked offline`);
      }

      const result = await Numbers.updateMany(
        { port: { $regex: `^${device.deviceId}-SIM` }, active: true },
        { $set: { active: false, signal: 0 } }
      );

      if (result.modifiedCount > 0) {
        console.log(`📴 Deactivated ${result.modifiedCount} numbers for offline device: ${device.deviceId}`);
        deactivatedCount += result.modifiedCount;
      }
    }

    // 🧹 Cleanup stale ports from online devices (SIM removed/disabled)
    const activeNumbersWithPort = await Numbers.find({
      port: { $regex: /^.*-SIM[0-9]+$/ },
      active: true
    });

    for (const num of activeNumbersWithPort) {
      if (!allOnlineNumberPorts.has(num.port)) {
        await Numbers.findByIdAndUpdate(num._id, {
          $set: { active: false, signal: 0 }
        });
        deactivatedCount++;
        console.log(`🗑️ Stale number deactivated: ${num.number} (port: ${num.port})`);
      }
    }

    console.log(`📊 Sync complete | Synced: ${syncedCount} | Deactivated: ${deactivatedCount}`);
    console.log(`[${new Date().toISOString()}] ✅ Done`);

  } catch (err) {
    console.error("❌ Error syncing device numbers:", err.message);
  }
}

async function initialize() {
  console.log("🔄 Initializing status monitoring script...");

  if (!MONGO_URI) {
    throw new Error("MONGODB_URI or MONGO_URI environment variable is not set.");
  }

  await mongoose.connect(MONGO_URI);
  console.log("✅ MongoDB connected");

  cron.schedule("*/15 * * * * *", () => {
    syncDeviceNumbers();
  });

  console.log("✅ Cron started — Device sync every 15 seconds");

  console.log("🔄 Running initial sync...");
  await syncDeviceNumbers();
}

initialize().catch(error => {
  console.error("❌ Failed to initialize:", error);
  process.exit(1);
});
