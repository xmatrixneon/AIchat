import mongoose from 'mongoose';

const PanelSchema = new mongoose.Schema({
  code: {
    type: Number,
    required: true,
    unique: true,
  },
  url: {
    type: String,
    required: true, 
  },
});

// Prevent model overwrite in dev
const Panel = mongoose.models.Panel || mongoose.model('Panel', PanelSchema);

export default Panel;
