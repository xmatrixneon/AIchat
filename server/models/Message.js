import mongoose from 'mongoose';

const MessageSchema = new mongoose.Schema({
  sender: String,
  receiver: String,
  port: String,
  time: Date,
  message: String,
  metadata: {
    deviceId: String,
    simSlot: Number,
    simCarrier: String,
    simNetworkType: String,
    networkType: String,
  }
}, { timestamps: true });

export default mongoose.models.Message || mongoose.model('Message', MessageSchema);