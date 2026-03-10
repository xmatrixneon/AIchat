// app/api/overview/active-orders/route.js
import { NextResponse } from "next/server";
import Orders from "@/models/Orders"; 
import Services from "@/models/Service"; 
import dbConnect from "@/lib/db";
import { verify } from "@/lib/verify"

export async function GET(req) {
  try {
    await dbConnect();

     // Auth verification
    try {
      await verify(req)
    } catch (err) {
      return NextResponse.json({ error: err.error }, { status: err.status || 401 })
    }
    // Fetch active orders
    const activeOrders = await Orders.find({ active: true })
      .sort({ createdAt: -1 })
      .lean();

    // Fetch service names in bulk
    const serviceIds = activeOrders.map(o => o.serviceid).filter(Boolean);
    const services = await Services.find({ _id: { $in: serviceIds } }).lean();
    const serviceMap = new Map(services.map(s => [s._id.toString(), s.name]));

    // Convert UTC → IST helper
    const toIST = (date) => {
      if (!date) return null;
      return new Date(date).toLocaleString("en-IN", {
        timeZone: "Asia/Kolkata",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit"
      });
    };

    const result = activeOrders.map(order => ({
      id: order._id,
      number: order.number,
      serviceName: order.serviceid
        ? serviceMap.get(order.serviceid.toString()) || "Unknown"
        : "Unknown",
      dialcode: order.dialcode,
      isused: order.isused,
      ismultiuse: order.ismultiuse,
      nextsms: order.nextsms,
      messageCount: order.message?.length || 0,
      keywords: order.keywords,
      formate: order.formate,
      createdAt: toIST(order.createdAt),
      updatedAt: toIST(order.updatedAt)
    }));

    return NextResponse.json(result);
  } catch (error) {
    console.error("Active Orders API error:", error);
    return NextResponse.json({ error: "Server error" }, { status: 500 });
  }
}
