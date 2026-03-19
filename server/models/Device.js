import mongoose from 'mongoose';

const deviceSchema = new mongoose.Schema({
  deviceId: {
    type: String,
    required: true,
    unique: true,
    index: true,
  },
  name: {
    type: String,
    default: 'Unknown Device',
  },
  status: {
    type: String,
    enum: ['online', 'offline', 'error'],
    default: 'offline',
  },
  lastSeen: {
    type: Date,
    default: Date.now,
  },
  batteryLevel: {
    type: Number,
    min: 0,
    max: 100,
    default: null,
  },
  isCharging: {
    type: Boolean,
    default: false,
  },
  signalStrength: {
    type: Number,
    min: 0,
    max: 4,
    default: 0,
  },
  networkType: {
    type: String,
    enum: ['wifi', 'mobile', 'none'],
    default: 'none',
  },
  sims: [{
    slot: {
      // Slots are 1-based to match the Android WebSocketClient convention:
      // Android converts 0-based slot → 1-based before sending SMS events.
      // Call forwarding responses are also normalised to 1-based in manager.js.
      type: Number,
      enum: [1, 2],
    },
    phoneNumber: {
      type: String,
      default: null,
    },
    carrier: {
      type: String,
      default: null,
    },
    signalStrength: {
      type: Number,
      min: 0,
      max: 4,
      default: 0,
    },
    // FIX #4: Added networkType to the sims subdocument. Previously this field
    // was sent by Android (both in SMS events and heartbeats) and mapped by
    // formatSims(), but Mongoose strict mode silently dropped it because it was
    // not declared here. Per-SIM radio type (2G/3G/LTE/5G NR) is now persisted.
    networkType: {
      type: String,
      default: null,
    },
    isActive: {
      type: Boolean,
      default: false,
    },
    callForwardingActive: {
      type: Boolean,
      default: false,
    },
    callForwardingTo: {
      type: String,
      default: null,
    },
    // FIX #5: Added ussdResponse so the raw carrier reply string from a USSD
    // status check (e.g. "Forwarded to +1234567890") can be persisted per-SIM.
    // This field is populated by handleCallForwardingResponse when action="check".
    ussdResponse: {
      type: String,
      default: null,
    },
  }],
  location: {
    latitude:  { type: Number, default: null },
    longitude: { type: Number, default: null },
    accuracy:  { type: Number, default: null },
  },
  appVersion:    { type: String, default: null },
  osVersion:     { type: String, default: null },
  deviceModel:   { type: String, default: null },
  manufacturer:  { type: String, default: null },
  totalMessagesSent:     { type: Number, default: 0 },
  totalMessagesReceived: { type: Number, default: 0 },
  lastMessageReceived:   { type: Date,   default: null },
  apiKey:   { type: String, required: false, unique: false, index: false },
  isActive: { type: Boolean, default: true },
  notes:    { type: String,  default: null },
  registeredAt:  { type: Date, default: Date.now },
  lastHeartbeat: { type: Date, default: Date.now },
}, {
  timestamps: true,
});

deviceSchema.index({ status: 1 });
deviceSchema.index({ lastHeartbeat: 1 });
deviceSchema.index({ 'sims.phoneNumber': 1 });
// FIX #8: Index on sims.slot supports the positional $ operator queries used in
// handleCallForwardingResponse — findOneAndUpdate({ "sims.slot": N }, { "sims.$": ... })
// performs a collection scan without this index on large device collections.
deviceSchema.index({ 'sims.slot': 1 });

deviceSchema.methods.updateStatus = function (status) {
  this.status = status;
  this.lastSeen = new Date();
  return this.save();
};

deviceSchema.methods.incrementMessageCount = function (type = 'received') {
  if (type === 'sent') {
    this.totalMessagesSent += 1;
  } else {
    this.totalMessagesReceived += 1;
    this.lastMessageReceived = new Date();
  }
  return this.save();
};

deviceSchema.methods.isOnline = function () {
  // 60 second timeout to reduce device status flip-flopping
  const offlineTimeout = new Date(Date.now() - 60 * 1000);
  return this.lastHeartbeat > offlineTimeout;
};

deviceSchema.statics.findOnlineDevices = function () {
  // 60 second timeout to reduce device status flip-flopping
  const offlineTimeout = new Date(Date.now() - 60 * 1000);
  return this.find({ lastHeartbeat: { $gt: offlineTimeout }, isActive: true });
};

deviceSchema.statics.markOfflineDevices = function () {
  // 60 second timeout to reduce device status flip-flopping
  const offlineTimeout = new Date(Date.now() - 60 * 1000);
  return this.updateMany(
    { lastHeartbeat: { $lt: offlineTimeout }, status: 'online' },
    { $set: { status: 'offline' } },
  );
};

const Device = mongoose.models.Device || mongoose.model('Device', deviceSchema);
export default Device;