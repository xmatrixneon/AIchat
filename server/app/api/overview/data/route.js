// app/api/overview/route.js
import { NextResponse } from "next/server";
import Numbers from "@/models/Numbers"; // adjust path
import Orders from "@/models/Orders";   // adjust path
import dbConnect from "@/lib/db";       // your db connection util
import CronStatus  from "@/models/Cron";
export async function GET() {
  try {
    await dbConnect(); // ensure MongoDB is connected

    // Total numbers in Numbers collection
    const totalNumbers = await Numbers.countDocuments();

    // Active orders (all, not unique)
    const activeOrders = await Orders.countDocuments({ active: true });

    // Occupied numbers (unique)
    const occupiedNumbers = await Orders.aggregate([
      { $match: { active: true } },
      { $group: { _id: "$number" } },
      { $count: "count" }
    ]);

    const occupied = occupiedNumbers.length > 0 ? occupiedNumbers[0].count : 0;

    // Total activations (isused = true)
    const totalActivations = await Orders.countDocuments({ isused: true });
const cron = await CronStatus.findOne({ name: "fetchOrders" });
    let lastcron = "-";
    if (cron?.lastRun) {
      lastcron = new Date(cron.lastRun).toLocaleString("en-IN", {
        timeZone: "Asia/Kolkata",
        hour12: true,
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
      });
    }
    // Response
    return NextResponse.json({
      totalNumbers,
      activeOrders,                // total active orders
      occupiedNumbers: occupied,   // unique active numbers
      availableNumbers: totalNumbers - occupied,
      totalActivations,             // orders marked as used
      lastcron: lastcron
    });
  } catch (error) {
    console.error("Overview API error:", error);
    return NextResponse.json({ error: "Server error" }, { status: 500 });
  }
}
