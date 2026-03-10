import mongoose from 'mongoose';

const CountiresSchema = new mongoose.Schema({
  name: {
    type: String,
    required: true,
  },
  flag: {
    type: String,
    required: true,
  },
  code: {
    type: String,
    required: true,
    unique: true,
  },
  dial: {
    type: Number,
    required: true,
  },
  active: {
   type: Boolean,
   default: true,
  }
});

const Countires = mongoose.models.Countires || mongoose.model('Countires', CountiresSchema);

export default Countires;
