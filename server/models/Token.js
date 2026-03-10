import mongoose from 'mongoose';

const tokenSchema = new mongoose.Schema({
  user: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true,
  },
  token: {
    type: String,
    required: true,
    unique: true,
  },
  device: {
    type: String,
    default: 'unknown',
  },
  createdAt: {
    type: Date,
    default: Date.now,
    expires: '30d' // Token expires in 30 days (optional)
  },
});

const Token = mongoose.models.Token || mongoose.model('Token', tokenSchema);
export default Token;
