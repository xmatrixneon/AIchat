// app/api/overview/chart/route.js
import { NextResponse } from "next/server";
import Orders from "@/models/Orders";
import dbConnect from "@/lib/db";
import { verify } from "@/lib/verify"

export async function GET(req) {
  try {
    await dbConnect();
try {
      await verify(req)
    } catch (err) {
      return NextResponse.json({ error: err.error }, { status: err.status || 401 })
    }

    // Calculate IST start date for last 7 days
    const now = new Date();
    const istOffset = 5.5 * 60 * 60 * 1000;
    const istNow = new Date(now.getTime() + istOffset);

    const sevenDaysAgoIST = new Date(istNow);
    sevenDaysAgoIST.setDate(sevenDaysAgoIST.getDate() - 6);
    sevenDaysAgoIST.setHours(0, 0, 0, 0);

    // Convert back to UTC for Mongo query
    const sevenDaysAgoUTC = new Date(sevenDaysAgoIST.getTime() - istOffset);

    const stats = await Orders.aggregate([
      {
        $match: {
          createdAt: { $gte: sevenDaysAgoUTC }
        }
      },
      {
        $group: {
          _id: {
            year: { $year: { date: "$createdAt", timezone: "Asia/Kolkata" } },
            month: { $month: { date: "$createdAt", timezone: "Asia/Kolkata" } },
            day: { $dayOfMonth: { date: "$createdAt", timezone: "Asia/Kolkata" } }
          },
          totalSuccessOrders: {
            $sum: { $cond: [{ $eq: ["$isused", true] }, 1, 0] }
          },
          totalUnsuccessOrders: {
            $sum: { $cond: [{ $eq: ["$isused", false] }, 1, 0] }
          },
          usedNumbersSet: { $addToSet: "$number" }
        }
      },
      {
        $addFields: {
          usedNumbers: { $size: "$usedNumbersSet" }
        }
      },
      { $sort: { "_id.year": 1, "_id.month": 1, "_id.day": 1 } }
    ]);

    const result = stats.map(item => {
      // Construct IST date
      const date = new Date(Date.UTC(item._id.year, item._id.month - 1, item._id.day));
      return {
        date: date.toISOString().split("T")[0], // YYYY-MM-DD
        totalSuccessOrders: item.totalSuccessOrders,
        totalUnsuccessOrders: item.totalUnsuccessOrders,
        usedNumbers: item.usedNumbers
      };
    });

    return NextResponse.json(result);
  } catch (error) {
    console.error("Chart API error:", error);
    return NextResponse.json({ error: "Server error" }, { status: 500 });
  }
}
