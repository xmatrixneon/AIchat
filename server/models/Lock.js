import mongoose from 'mongoose';

const LockSchema = new mongoose.Schema({
  number: {
    type: Number,
    required: true,
  },
  countryid: {
    type: mongoose.Schema.Types.ObjectId,
    required: true,
  },
  serviceid: {
    type: mongoose.Schema.Types.ObjectId,
    required: true,
  },
  locked: {
    type: Boolean,
    default: true,
  },
}, {
  timestamps: true
});

const Orders = mongoose.models.Lock || mongoose.model('Lock', LockSchema);

export default Orders;
