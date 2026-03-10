import mongoose from 'mongoose';

const OrdersSchema = new mongoose.Schema({
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
  dialcode: {
    type: Number,
    required: true,
  },
  isused: {
    type: Boolean,
    default: false,
  },
  ismultiuse: {
    type: Boolean,
    default: true,    
  },
  nextsms: {
    type: Boolean,
    default: false,
  },
  message: {
    type: [String],
    default: [],
  },
  keywords: {
  type: [String],
  default: [],
  },
  formate: {
  type: [String],
  default: [],
  required: true,  
  },
  maxmessage : {
    type: Number,
  default: 0,
  },
  active: {
    type: Boolean,
    default: true,
  },
}, {
  timestamps: true
});

const Orders = mongoose.models.Orders || mongoose.model('Orders', OrdersSchema);

export default Orders;
