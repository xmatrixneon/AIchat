import mongoose from 'mongoose';

const ServiceSchema = new mongoose.Schema({
  name: {
    type: String,
    required: true,
  },
  image: {
    type: String,
    required: true,
  },
  code: {
    type: String,
    required: true,
    unique: true,
  },
  formate: {
  type: [String],
  default: [],
  required: true,  
  },
  multisms: {
   type: Boolean,
   default: true,
  },
  keywords: {
  type: [String],
  default: [],
  },
  maxmessage : {
  type: Number,
  default: 0,
  },
  active: {
   type: Boolean,
   default: true,
  }
});

const Service = mongoose.models.Service || mongoose.model('Service', ServiceSchema);

export default Service;
