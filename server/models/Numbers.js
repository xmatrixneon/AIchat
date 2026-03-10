import mongoose from 'mongoose';

const NumbersSchema = new mongoose.Schema({
  number: {
    type: Number,
    required: true,
    unique: true,
  },
  countryid: {
    type: mongoose.Schema.Types.ObjectId,
    required: true,
    ref: "Country" // optional reference
  },
  multiuse: {
    type: Boolean,
    default: false,
  },
  multigap: {
    type: Number,
    default: 0,
  },
  active: {
    type: Boolean,
    default: true,
  },
  locked: {
    type: Boolean,
    default: false,   // port lock/unlock state
  },
  lastRotation: {
    type: Date,
    default: null,    // last time this number/port was rotated
  },
  iccid: {
    type: String,
    default: null,    // SIM card ICCID
  },
  imsi: {
    type: String,
    default: null,    // SIM IMSI
  },
  operator: {
    type: String,
    default: null,    // Network operator (e.g., Vi, Airtel, Jio)
  },
  signal: {
    type: Number,
    default: 0,       // last known signal strength
  },
  port: {
    type: String,
    default: null,    // Gateway port like "1.01"
  }
});

// Prevent model overwrite in dev
const Numbers = mongoose.models.Numbers || mongoose.model('Numbers', NumbersSchema);

export default Numbers;
