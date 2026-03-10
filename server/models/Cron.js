import mongoose from "mongoose";

const CronStatusSchema = new mongoose.Schema(
  {
    name: {
      type: String,
      required: true,
      unique: true, // Unique identifier for each cron job
    },
    lastRun: {
      type: Date,
      default: null, // Timestamp of the last execution
    },
  },
  { timestamps: true } // optional, keeps createdAt and updatedAt
);

export default mongoose.models.CronStatus ||
  mongoose.model("CronStatus", CronStatusSchema);
