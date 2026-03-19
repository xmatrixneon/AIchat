import { config } from "dotenv";
import mongoose from "mongoose";
// import fetch from "node-fetch"; // Commented out - panel sync disabled
import cron from "node-cron";
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import Numbers from "../models/Numbers.js";
import Country from "../models/Countires.js";
// import Panel from "../models/Panel.js"; // Commented out - panel sync disabled
import Device from "../models/Device.js";

// Get directory path for ES modules
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// Load environment variables from parent directory
config({ path: join(__dirname, "..", ".env.local") });
config({ path: join(__dirname, "..", ".env") });

// 🔗 MongoDB connection - use environment variable
const MONGO_URI = process.env.MONGODB_URI || process.env.MONGO_URI;

// 🧠 Get India country ID (assuming it exists)
async function getIndiaId() {
  const country = await Country.findOne({ name: "India" });
  if (!country) {
    throw new Error("India country not found in database. Please create it first.");
  }
  return country._id;
}

// ⚙️ Fetch panel data (safe + timeout)
async function fetchPanelData(panel) {
  if (!panel.url) {
    console.warn(`⚠️ Panel ${panel.code} has no URL`);
    return [];
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000); // ⏳ 10s timeout

  try {
    const res = await fetch(panel.url, { signal: controller.signal });
    clearTimeout(timeout);

    if (!res.ok) {
      console.warn(`⚠️ [${panel.code}] Bad response: ${res.status}`);
      return [];
    }

    const data = await res.json().catch(() => null);
    if (!data || !data.status) {
      console.warn(`⚠️ [${panel.code}] Invalid JSON format`);
      return [];
    }

    return data.status.map((p) => ({
      panelCode: panel.code,
      inserted: p.inserted,
      sn: p.sn,
      port: p.port,
      iccid: p.iccid,
      imsi: p.imsi,
      opr: p.opr,
      sig: p.sig,
      active: p.active,
      st: p.st,
    }));
  } catch (err) {
    if (err.name === "AbortError") {
      console.warn(`⏰ [${panel.code}] Timed out - skipping`);
    } else {
      console.error(`❌ [${panel.code}] Fetch error:`, err.message);
    }
    return [];
  }
}

// 🚀 Panel sync logic - COMMENTED OUT (DEVICE SYNC ONLY)
// async function syncAllGateways() {
//   try {
//     const indiaId = await getIndiaId();
//     const panels = await Panel.find();
//     if (!panels.length) {
//       console.warn("⚠️ No panels found in database!");
//       return;
//     }

//     console.log(`🌍 Found ${panels.length} panels. Loading data...`);

//     // 1️⃣ Fetch all panels in parallel
//     const allPorts = (await Promise.all(panels.map(fetchPanelData))).flat();

//     // 2️⃣ Filter valid numbers
//     const validPorts = allPorts.filter((p) => p.inserted === 1 && p.sn);

//     if (!validPorts.length) {
//       console.log("⚠️ No valid numbers found in any panel response.");
//       return;
//     }

//     // 3️⃣ Prepare bulk updates
//     const bulkOps = [];
//     const activeNumbers = new Set();

//     for (const p of validPorts) {
//       const isActive = p.st === 3 || p.st === 7;
//       const snStr = String(p.sn);
//       const processedNumber =
//         snStr.length === 12 && snStr.startsWith("91")
//           ? parseInt(snStr.substring(2))
//           : parseInt(snStr);

//       activeNumbers.add(processedNumber);

//       const numberData = {
//         countryid: indiaId,
//         port: p.port,
//         iccid: p.iccid || null,
//         imsi: p.imsi || null,
//         operator: p.opr || null,
//         signal: isActive ? p.sig || 0 : 0,
//         locked: p.active === 0,
//         lastRotation: new Date(),
//         active: isActive,
//       };

//       bulkOps.push({
//         updateOne: {
//           filter: { number: processedNumber },
//           update: { $set: numberData },
//           upsert: true,
//         },
//       });
//     }

//     // 4️⃣ Execute bulk write
//     const result = await Numbers.bulkWrite(bulkOps);
//     console.log(
//       `✅ Bulk upsert complete: ${result.modifiedCount} updated, ${result.upsertedCount} inserted`
//     );

//     // 5️⃣ Mark numbers not found as inactive
//     const resultInactive = await Numbers.updateMany(
//       { number: { $nin: [...activeNumbers] } },
//       { $set: { active: false, signal: 0 } }
//     );

//     console.log(`🔒 Marked ${resultInactive.modifiedCount} numbers as inactive`);
//     console.log(`[${new Date().toISOString()}] ✅ Sync complete`);
//   } catch (err) {
//     console.error("❌ Error syncing all gateways:", err.message);
//   }
// }

// 🔄 Sync device-based numbers from active devices
async function syncDeviceNumbers() {
  try {
    // 60 second timeout to reduce device status flip-flopping
    const offlineTimeout = new Date(Date.now() - 60 * 1000);

    // Find all active devices
    const activeDevices = await Device.find({ isActive: true });
    const offlineDevices = activeDevices.filter(device => device.lastHeartbeat < offlineTimeout);

    console.log(`📱 Processing ${activeDevices.length} active devices (${offlineDevices.length} offline)`);

    // Get India country ID for device numbers
    const indiaId = await getIndiaId();
    const allDeviceNumberPorts = new Set();
    let syncedCount = 0;
    let deactivatedCount = 0;

    // Process each active device
    for (const device of activeDevices) {
      const isOnline = device.lastHeartbeat >= offlineTimeout;

      // Update device status based on heartbeat
      const newStatus = isOnline ? 'online' : 'offline';
      if (device.status !== newStatus) {
        device.status = newStatus;
        await device.save();
      }

      // Process each SIM slot in the device
      for (const sim of device.sims) {
        if (sim.phoneNumber && sim.isActive) {
          const port = `${device.deviceId}-SIM${sim.slot}`;
          allDeviceNumberPorts.add(port);

          // Clean and validate phone number
          let phoneNumber = String(sim.phoneNumber).replace(/\D/g, '');
          if (phoneNumber.length === 12 && phoneNumber.startsWith("91")) {
            phoneNumber = phoneNumber.substring(2);
          }
          phoneNumber = parseInt(phoneNumber);

          if (phoneNumber && phoneNumber.toString().length === 10) {
            // Create/update number record from device SIM
            await Numbers.findOneAndUpdate(
              { number: phoneNumber },
              {
                $set: {
                  countryid: indiaId,
                  port: port,
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
          }
        }
      }

      // Deactivate numbers for offline devices
      if (!isOnline) {
        const result = await Numbers.updateMany(
          {
            port: { $regex: `^${device.deviceId}-SIM` },
            active: true
          },
          {
            $set: { active: false, signal: 0 }
          }
        );

        if (result.modifiedCount > 0) {
          console.log(`📴 Deactivated ${result.modifiedCount} numbers for offline device: ${device.deviceId}`);
          deactivatedCount += result.modifiedCount;
        }
      }
    }

    // Remove device numbers that no longer exist (SIM removed/disabled)
    const existingDeviceNumbers = await Numbers.find({
      port: { $regex: /^.*-SIM[12]$/ }
    });

    for (const num of existingDeviceNumbers) {
      if (!allDeviceNumberPorts.has(num.port)) {
        await Numbers.findByIdAndUpdate(num._id, {
          $set: { active: false, signal: 0 }
        });
        deactivatedCount++;
      }
    }

    console.log(`📊 Device number sync complete:`);
    console.log(`   - Synced/updated: ${syncedCount} numbers from devices`);
    console.log(`   - Deactivated: ${deactivatedCount} numbers`);
    console.log(`   - Online devices: ${activeDevices.length - offlineDevices.length}`);
    console.log(`   - Offline devices: ${offlineDevices.length}`);

  } catch (err) {
    console.error("❌ Error syncing device numbers:", err.message);
  }
}

// 🚀 Initialize and start cron jobs
async function initialize() {
  console.log("🔄 Initializing status monitoring script...");

  // Connect to MongoDB
  await mongoose.connect(MONGO_URI);
  console.log("✅ MongoDB connected");

  console.log("🕒 Starting cron jobs...");

  // 🕒 Panel sync disabled - DEVICE SYNC ONLY
  // cron.schedule("*/30 * * * * *", () => {
  //   syncAllGateways();
  // });

  // 🕒 Run device sync every 15 seconds (real-time device status)
  cron.schedule("*/15 * * * * *", () => {
    syncDeviceNumbers();
  });

  console.log("✅ Cron jobs started successfully");
  console.log("📊 Sync intervals:");
  console.log("   - Panel gateway sync: DISABLED");
  console.log("   - Device number sync: Every 15 seconds (real-time)");

  // Run initial sync - DEVICE SYNC ONLY
  console.log("🔄 Running initial sync...");
  // await syncAllGateways(); // Panel sync disabled
  await syncDeviceNumbers();
}

// Start the script
initialize().catch(error => {
  console.error("❌ Failed to initialize status script:", error);
  process.exit(1);
});
