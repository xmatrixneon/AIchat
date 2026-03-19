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
  const startTime = Date.now();
  const timestamp = new Date().toISOString();

  try {
    // 60 second timeout to reduce device status flip-flopping
    // Devices with heartbeats 30-60s old won't constantly flip between online/offline
    const offlineTimeout = new Date(Date.now() - 60 * 1000);

    const activeDevices = await Device.find({ isActive: true });
    const onlineDevices = activeDevices.filter(d => d.lastHeartbeat >= offlineTimeout);
    const offlineDevices = activeDevices.filter(d => d.lastHeartbeat < offlineTimeout);

    const totalNumbersBefore = await Numbers.countDocuments();
    const activeNumbersBefore = await Numbers.countDocuments({ active: true });

    console.log(`\n${'─'.repeat(55)}`);
    console.log(`🔄 SYNC  ${timestamp}`);
    console.log(`${'─'.repeat(55)}`);
    console.log(`📱 Devices   Total: ${activeDevices.length}  🟢 Online: ${onlineDevices.length}  🔴 Offline: ${offlineDevices.length}`);
    console.log(`📋 Numbers   Total: ${totalNumbersBefore}  ✅ Active: ${activeNumbersBefore}  ❌ Inactive: ${totalNumbersBefore - activeNumbersBefore}`);
    console.log(`${'─'.repeat(55)}`);

    const indiaId = await getIndiaId();
    const allDeviceNumberPorts = new Set();
    const syncedPhoneNumbers = new Set(); // Track all synced phone numbers
    let syncedCount = 0;
    let deactivatedCount = 0;
    let numberChangedCount = 0;
    let statusChangedOnline = 0;
    let statusChangedOffline = 0;

    // First pass: Sync all devices and track their numbers
    for (const device of activeDevices) {
      const isOnline = device.lastHeartbeat >= offlineTimeout;
      const newStatus = isOnline ? 'online' : 'offline';

      if (device.status !== newStatus) {
        device.status = newStatus;
        await device.save();
        if (isOnline) {
          statusChangedOnline++;
          console.log(`🟢 ONLINE   ${device.deviceId} (${device.name || 'unnamed'})`);
        } else {
          statusChangedOffline++;
          console.log(`🔴 OFFLINE  ${device.deviceId} (${device.name || 'unnamed'})`);
        }
      }

      // OFFLINE DEVICES: Deactivate all their numbers immediately
      // and skip syncing to prevent offline device numbers from being stored
      if (!isOnline) {
        // Deactivate all numbers from this offline device
        const result = await Numbers.updateMany(
          { port: { $regex: `^${device.deviceId}-SIM` }, active: true },
          { $set: { active: false, signal: 0 } }
        );
        if (result.modifiedCount > 0) {
          deactivatedCount += result.modifiedCount;
          console.log(`🔌 DEACTIVATED ${result.modifiedCount} numbers from offline device ${device.deviceId}`);
        }
        continue; // Skip syncing numbers from offline devices - don't store/update them
      }

      for (const sim of device.sims) {
        if (sim.phoneNumber && sim.isActive) {
          const port = `${device.deviceId}-SIM${sim.slot}`;
          allDeviceNumberPorts.add(port);

          let phoneNumber = String(sim.phoneNumber).replace(/\D/g, '');
          if (phoneNumber.length === 12 && phoneNumber.startsWith("91")) {
            phoneNumber = phoneNumber.substring(2);
          }
          phoneNumber = parseInt(phoneNumber);

          if (phoneNumber && phoneNumber.toString().length === 10) {
            syncedPhoneNumbers.add(phoneNumber);

            // Deactivate old number if SIM number changed on same port
            const oldNumbers = await Numbers.find({ port, number: { $ne: phoneNumber }, active: true });
            if (oldNumbers.length > 0) {
              await Numbers.updateMany(
                { port, number: { $ne: phoneNumber }, active: true },
                { $set: { active: false, signal: 0 } }
              );
              oldNumbers.forEach(old => {
                console.log(`🔄 SIM SWAP  ${old.number} → ${phoneNumber}  (${port})`);
              });
              numberChangedCount += oldNumbers.length;
            }

            await Numbers.findOneAndUpdate(
              { number: phoneNumber },
              {
                $set: {
                  countryid: indiaId,
                  port,
                  operator: sim.carrier || null,
                  signal: isOnline ? (sim.signalStrength || 0) : 0,
                  active: isOnline,
                  lastRotation: new Date(),
                  locked: false,
                  iccid: sim.iccid || null,
                  imsi: sim.imsi || null
                }
              },
              { upsert: true, new: true }
            );
            syncedCount++;
          } else {
            console.log(`⚠️  INVALID  ${device.deviceId} SIM${sim.slot} → "${sim.phoneNumber}"`);
          }
        }
      }
    }

    // Cleanup stale ports (only deactivate if number was NOT synced in this run)
    const staleNumbers = await Numbers.find({
      port: { $regex: /^.*-SIM[0-9]+$/ },
      active: true
    });

    for (const num of staleNumbers) {
      // Only deactivate if port is stale AND number wasn't synced
      if (!allDeviceNumberPorts.has(num.port) && !syncedPhoneNumbers.has(num.number)) {
        await Numbers.findByIdAndUpdate(num._id, {
          $set: { active: false, signal: 0 }
        });
        deactivatedCount++;
        console.log(`🗑️  STALE    ${num.number}  (${num.port})`);
      }
    }

    const totalNumbersAfter = await Numbers.countDocuments();
    const activeNumbersAfter = await Numbers.countDocuments({ active: true });
    const elapsed = Date.now() - startTime;

    console.log(`${'─'.repeat(55)}`);
    console.log(`✅ DONE  (${elapsed}ms)`);
    console.log(`   Synced:       ${syncedCount} numbers`);
    console.log(`   Deactivated:  ${deactivatedCount} numbers`);
    if (numberChangedCount > 0)
      console.log(`   SIM swaps:    ${numberChangedCount} numbers replaced`);
    if (statusChangedOnline > 0 || statusChangedOffline > 0)
      console.log(`   Status chg:   +${statusChangedOnline} online  -${statusChangedOffline} offline`);
    console.log(`   Numbers now:  Total: ${totalNumbersAfter}  ✅ Active: ${activeNumbersAfter}  ❌ Inactive: ${totalNumbersAfter - activeNumbersAfter}`);
    console.log(`${'─'.repeat(55)}\n`);

  } catch (err) {
    console.error(`\n❌ SYNC ERROR  ${timestamp}`);
    console.error(`   ${err.message}\n`);
  }
}

async function initialize() {
  console.log(`\n${'═'.repeat(55)}`);
  console.log(`🚀 SMS GATEWAY — STATUS MONITOR`);
  console.log(`${'═'.repeat(55)}`);

  if (!MONGO_URI) {
    throw new Error("MONGODB_URI or MONGO_URI environment variable is not set.");
  }

  await mongoose.connect(MONGO_URI);
  console.log(`✅ MongoDB connected`);

  cron.schedule("*/15 * * * * *", () => {
    syncDeviceNumbers();
  });

  console.log(`⏱️  Sync interval: every 15 seconds`);
  console.log(`${'═'.repeat(55)}\n`);

  await syncDeviceNumbers();
}

initialize().catch(error => {
  console.error("❌ Failed to initialize:", error);
  process.exit(1);
});