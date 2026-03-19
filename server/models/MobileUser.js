import mongoose from 'mongoose';

const mobileUserSchema = new mongoose.Schema({
  name: {
    type: String,
    required: true,
  },
  email: {
    type: String,
    required: true,
    unique: true,
    lowercase: true,
  },
  password: {
    type: String,
    required: true,
  },
  // Optional: restrict to specific devices
  allowedDevices: [{
    type: String, // device IDs
  }],
  // Optional: scopes for fine-grained access
  scopes: {
    type: [String],
    default: ['devices:read', 'messages:read', 'sms:send', 'call:manage', 'ws:connect'],
  },
  isActive: {
    type: Boolean,
    default: true,
  },
  createdAt: {
    type: Date,
    default: Date.now,
  },
  lastLoginAt: {
    type: Date,
  },
});

const MobileUser = mongoose.models.MobileUser || mongoose.model('MobileUser', mobileUserSchema);

export default MobileUser;
