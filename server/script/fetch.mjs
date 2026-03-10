import { config } from "dotenv";
import mongoose from "mongoose";
import cron from "node-cron";
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import Orders from "../models/Orders.js";
import Message from "../models/Message.js";
import CronStatus from "../models/Cron.js";
import Lock from "../models/Lock.js";

// Get directory path for ES modules
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// Load environment variables from parent directory
config({ path: join(__dirname, "..", ".env.local") });
config({ path: join(__dirname, "..", ".env") });

// 🔗 MongoDB connection - use environment variable
const MONGO_URI = process.env.MONGODB_URI || process.env.MONGO_URI;

// Escape regex special chars
const escapeRegex = (s = "") =>
  s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

function normalizeToSingleLine(str = "") {
  return str
    .replace(/\r?\n|\r/g, " ")  
    .replace(/\s+/g, " ")      
    .trim();
}

// ✅ Smart OTP regex builder (fixed order: escape → insert placeholders)
function buildSmartOtpRegexList(formats) {
  if (!formats || formats.length === 0) return [];
  if (!Array.isArray(formats)) formats = [formats];

  return formats
    .map((format) => {
      format = normalizeToSingleLine(format); // ✅ multiline → single line
      if (!format.includes("{otp}")) return null;

      let pattern = escapeRegex(format);

      pattern = pattern.replace(/\\\{otp\\\}/gi, "(?<otp>[A-Za-z0-9\\-]{3,12})"); 
      pattern = pattern.replace(/\\\{date\\\}/gi, ".*");
      pattern = pattern.replace(/\\\{datetime\\\}/gi, ".*");
      pattern = pattern.replace(/\\\{time\\\}/gi, ".*");
      pattern = pattern.replace(/\\\{random\\\}/gi, "[A-Za-z0-9]{3,15}");
      pattern = pattern.replace(/\\\{.*?\\\}/gi, ".*");

      pattern = pattern
        .replace(/\\s+/g, "\\s*")
        .replace(/\\:/g, "[:：]?")
        .replace(/\\\./g, ".*");

      return new RegExp(pattern, "i");
    })
    .filter(Boolean);
}



// ✅ Keyword filter
function containsKeywords(msg, keywords) {
  if (!keywords || keywords.length === 0) return true;
  return keywords.some((kw) =>
    msg.toLowerCase().includes(kw.toLowerCase())
  );
}

// Connect once
mongoose
  .connect(MONGO_URI)
  .then(() => console.log("✅ MongoDB connected"))
  .catch((e) => {
    console.error("❌ MongoDB connection error:", e);
    process.exit(1);
  });

// Prevent overlapping runs
let running = false;

// Cron every 5 sec
cron.schedule("*/5 * * * * *", async () => {
  if (running) {
    console.log("⏭ Previous run still in progress — skipping this tick");
    return;
  }
  running = true;

  console.log("\n==============================");
  console.log("⏳ Cron start:", new Date().toISOString());

  try {
    const orders = await Orders.find({ active: true });
    console.log(`📦 Found ${orders.length} active orders`);

    for (const order of orders) {
      const now = new Date();
      const ageMinutes = (now - order.createdAt) / (1000 * 60);

      // 1️⃣ Expire after 15 min
      if (ageMinutes > 15) {
        await Orders.updateOne(
          { _id: order._id },
          { $set: { active: false, updatedAt: now } }
        );
        console.log(`   ⌛ Order ${order._id} expired`);
        continue;
      }

   const messageLength = order.message.length;

// ✅ Check message limit
if (order.maxmessage !== 0 && messageLength >= order.maxmessage) {
  console.log("❌ Message limit reached!");
        continue;
} 
      // Handle both old and new number formats for backward compatibility
      const orderNumberStr = order.number.toString();
      let fullNumber, numberWithCountry;
      
      if (order.dialcode === 91 && orderNumberStr.length === 12 && orderNumberStr.startsWith('91')) {
        // Old format: 12-digit number already includes 91
        numberWithCountry = orderNumberStr;
        fullNumber = `+${orderNumberStr}`;
      } else if (order.dialcode === 91 && orderNumberStr.length === 10 && !orderNumberStr.startsWith('91')) {
        // New format: 10-digit number without 91 prefix + 91 dialcode
        numberWithCountry = `91${orderNumberStr}`;
        fullNumber = `+${numberWithCountry}`;
      } else if (order.dialcode === 91 && orderNumberStr.length === 10 && orderNumberStr.startsWith('91')) {
        // Edge case: 10-digit number that already starts with 91 (like 9156789012)
        numberWithCountry = orderNumberStr;
        fullNumber = `+${orderNumberStr}`;
      } else {
        // Other countries or formats
        numberWithCountry = `${order.dialcode}${orderNumberStr}`;
        fullNumber = `+${numberWithCountry}`;
      }
      
      console.log(
        `\n🔍 Order ${order._id} — number: ${order.number} (full: ${fullNumber})`
      );

      const escapedFullNumber = escapeRegex(fullNumber);
      const escapedNumberOnly = escapeRegex(orderNumberStr);
      const escapedNumberWithCountry = escapeRegex(numberWithCountry);

      // Build regex list from templates
      const otpRegexList = buildSmartOtpRegexList(order.formate);

// ✅ Base time = updatedAt ya createdAt
const baseTime = order.updatedAt || order.createdAt;

// ✅ 5 sec kam karke naya sinceTime
const sinceTime = new Date(baseTime.getTime() - 10000);

// ✅ Sirf uske baad ke messages uthao
const timeFilter = {
  $or: [
    { createdAt: { $gt: sinceTime } },
  ],
};


      // Handle receiver matching with and without 91 prefix
      const receiverMatches = [
        { receiver: fullNumber },
        { receiver: order.number.toString() },
        { receiver: numberWithCountry },
      ];
      
      // Also match receivers that might have 91 prefix when order doesn't
      if (order.dialcode === 91 && orderNumberStr.length === 10 && !orderNumberStr.startsWith('91')) {
        receiverMatches.push({ receiver: `91${orderNumberStr}` });
        receiverMatches.push({ receiver: `+91${orderNumberStr}` });
      }
      
      const receiverOrTextFilter = {
        $or: [
          ...receiverMatches,
          { message: new RegExp(escapedFullNumber, "i") },
          { message: new RegExp(escapedNumberOnly, "i") },
          { message: new RegExp(escapedNumberWithCountry, "i") },
        ],
      };

      const messages = await Message.find({
        $and: [receiverOrTextFilter, timeFilter],
      }).sort({ createdAt: 1 });

      console.log(`   ✉️  Matched messages: ${messages.length}`);

      // 🚦 Multi-use logic
      if (order.message.length > 0) {
        if (!order.ismultiuse) {
          console.log("   ⛔ Already has OTP, multiuse=false → skip");
          continue;
        }
        if (!order.nextsms) {
          console.log("   ⏸ Multiuse enabled but nextsms=false → wait");
          continue;
        }
      }

      for (const msg of messages) {
        // 🚫 Skip if already saved
        if (order.message.includes(msg.message)) {
          console.log("      ⏭ Already saved message, skipping");
          continue;
        }

        if (!containsKeywords(msg.message, order.keywords)) {
          console.log("      ❌ Skipped (keywords not matched)");
          continue;
        }

        console.log(`   └ Message from ${msg.sender}`);
        console.log(`      text: ${msg.message}`);

let otpFound = null;

// ✅ normalize message ek line me
const cleanMessage = normalizeToSingleLine(msg.message);

for (const regex of otpRegexList) {
  const m = regex.exec(cleanMessage);
  otpFound = m?.groups?.otp || (m && m[1]) || null;
  if (otpFound) {
    console.log("      ✅ extracted OTP via format regex");
    break;
  }
}


        if (otpFound) {
          const updateFields = {
            updatedAt: new Date(),
            nextsms: false,
          };
          if (order.message.length === 0) {
            updateFields.isused = true;
          const newLock = new Lock({
          number: order.number,
          countryid: order.countryid,
           serviceid: order.serviceid,
           locked: true,
          });

    await newLock.save();
            console.log("      🔒 First OTP received → marking order as used");
          }

          await Orders.updateOne(
            { _id: order._id },
            {
              $set: updateFields,
              $addToSet: { message: msg.message },
            }
          );

          console.log(`      💾 Saved OTP: ${otpFound}`);
          break; // save only one OTP per run
        } else {
          console.log("      ⚠ No OTP found (formats didn’t match)");
        }
      }
    }

    console.log("⏹ Cron finished:", new Date().toISOString());
  } catch (err) {
    console.error("❌ Cron runtime error:", err);
  } finally {
    await CronStatus.findOneAndUpdate(
      { name: "fetchOrders" },
      { lastRun: new Date() },
      { upsert: true, new: true }
    );
    running = false;
  }
});
