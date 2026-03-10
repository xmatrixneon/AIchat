import mongoose from 'mongoose';

const deviceSchema = new mongoose.Schema({
  deviceId: {
    type: String,
    required: true,
    unique: true,
    index: true
  },
  name: {
    type: String,
    default: 'Unknown Device'
  },
  status: {
    type: String,
    enum: ['online', 'offline', 'error'],
    default: 'offline'
  },
  lastSeen: {
    type: Date,
    default: Date.now
  },
  batteryLevel: {
    type: Number,
    min: 0,
    max: 100,
    default: null
  },
  isCharging: {
    type: Boolean,
    default: false
  },
  signalStrength: {
    type: Number,
    min: 0,
    max: 4,
    default: 0
  },
  networkType: {
    type: String,
    enum: ['wifi', 'mobile', 'none'],
    default: 'none'
  },
  sims: [{
    slot: {
      type: Number,
      enum: [1, 2]
    },
    phoneNumber: {
      type: String,
      default: null
    },
    carrier: {
      type: String,
      default: null
    },
    signalStrength: {
      type: Number,
      min: 0,
      max: 4,
      default: 0
    },
    isActive: {
      type: Boolean,
      default: false
    }
  }],
  location: {
    latitude: {
      type: Number,
      default: null
    },
    longitude: {
      type: Number,
      default: null
    },
    accuracy: {
      type: Number,
      default: null
    }
  },
  appVersion: {
    type: String,
    default: null
  },
  osVersion: {
    type: String,
    default: null
  },
  deviceModel: {
    type: String,
    default: null
  },
  manufacturer: {
    type: String,
    default: null
  },
  totalMessagesSent: {
    type: Number,
    default: 0
  },
  totalMessagesReceived: {
    type: Number,
    default: 0
  },
  lastMessageReceived: {
    type: Date,
    default: null
  },
  apiKey: {
    type: String,
    required: false,
    unique: false,
    index: false
  },
  isActive: {
    type: Boolean,
    default: true
  },
  notes: {
    type: String,
    default: null
  },
  registeredAt: {
    type: Date,
    default: Date.now
  },
  lastHeartbeat: {
    type: Date,
    default: Date.now
  }
}, {
  timestamps: true
});

// Index for performance
deviceSchema.index({ status: 1 });
deviceSchema.index({ lastHeartbeat: 1 });
deviceSchema.index({ 'sims.phoneNumber': 1 });

// Method to update device status
deviceSchema.methods.updateStatus = function(status) {
  this.status = status;
  this.lastSeen = new Date();
  return this.save();
};

// Method to add message count
deviceSchema.methods.incrementMessageCount = function(type = 'received') {
  if (type === 'sent') {
    this.totalMessagesSent += 1;
  } else {
    this.totalMessagesReceived += 1;
    this.lastMessageReceived = new Date();
  }
  return this.save();
};

// Method to check if device is online (based on heartbeat)
deviceSchema.methods.isOnline = function() {
  const now = new Date();
  const fiveMinutesAgo = new Date(now.getTime() - 5 * 60 * 1000);
  return this.lastHeartbeat > fiveMinutesAgo;
};

// Static method to find online devices
deviceSchema.statics.findOnlineDevices = function() {
  const fiveMinutesAgo = new Date(Date.now() - 5 * 60 * 1000);
  return this.find({
    lastHeartbeat: { $gt: fiveMinutesAgo },
    isActive: true
  });
};

// Static method to update offline devices
deviceSchema.statics.markOfflineDevices = function() {
  const fiveMinutesAgo = new Date(Date.now() - 5 * 60 * 1000);
  return this.updateMany(
    {
      lastHeartbeat: { $lt: fiveMinutesAgo },
      status: 'online'
    },
    {
      $set: { status: 'offline' }
    }
  );
};

const Device = mongoose.models.Device || mongoose.model('Device', deviceSchema);
export default Device;