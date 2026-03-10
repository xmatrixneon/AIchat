import { NextResponse } from "next/server";
import dbConnect from "@/lib/db";
import Orders from "@/models/Orders";
import Countries from "@/models/Countires";
import Services from "@/models/Service";

export async function GET(req) {
  try {
    await dbConnect();

    const { searchParams } = new URL(req.url);
    const from = searchParams.get("from");
    const to = searchParams.get("to");

    const query = {};

    if (from || to) {
      // custom range
      query.createdAt = {};
      if (from) query.createdAt.$gte = new Date(from);
      if (to) {
        const end = new Date(to);
        end.setHours(23, 59, 59, 999);
        query.createdAt.$lte = end;
      }
    } else {
      // Default: Today’s orders in Indian Standard Time (IST)
      const now = new Date();

      // Convert current UTC time to IST (UTC+5:30)
      const istOffset = 5.5 * 60 * 60 * 1000; // 5 hours 30 minutes in ms
      const istNow = new Date(now.getTime() + istOffset);

      // Start of today in IST
      const istStart = new Date(
        istNow.getFullYear(),
        istNow.getMonth(),
        istNow.getDate(),
        0,
        0,
        0,
        0
      );

      // End of today in IST
      const istEnd = new Date(
        istNow.getFullYear(),
        istNow.getMonth(),
        istNow.getDate(),
        23,
        59,
        59,
        999
      );

      // Convert IST boundaries back to UTC for MongoDB comparison
      const utcStart = new Date(istStart.getTime() - istOffset);
      const utcEnd = new Date(istEnd.getTime() - istOffset);

      query.createdAt = { $gte: utcStart, $lte: utcEnd };
    }

    const orders = await Orders.find(query)
      .populate({
        path: "countryid",
        model: Countries,
        select: "name flag code dial active",
      })
      .populate({
        path: "serviceid",
        model: Services,
        select: "name code image",
      })
      .sort({ createdAt: -1 });

    return NextResponse.json(
      { success: true, count: orders.length, orders },
      { status: 200 }
    );
  } catch (error) {
    console.error("Error fetching order history:", error);
    return NextResponse.json(
      { success: false, message: "Failed to fetch order history" },
      { status: 500 }
    );
  }
}
