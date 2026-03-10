import connectDB from "@/lib/db"
import Lock from "@/models/Lock"
import Countires from "@/models/Countires"
import Service from "@/models/Service"
import { NextResponse } from "next/server"
import { verify } from "@/lib/verify"

export async function GET(req) {
  try {
    await connectDB()

    // 🔐 Verify request
    try {
      await verify(req)
    } catch (err) {
      return NextResponse.json(
        { error: err.error },
        { status: err.status || 401 }
      )
    }

    // Fetch locked=true and populate
    const locks = await Lock.find({ locked: true })
      .populate({ path: "countryid", model: Countires, select: "name" })
      .populate({ path: "serviceid", model: Service, select: "name" })
      .sort({ createdAt: -1 })

    // 🔄 Transform response: include only plain countryName & serviceName
    const formatted = locks.map(lock => ({
      _id: lock._id,
      number: lock.number,
      locked: lock.locked,
      country: lock.countryid?.name || null,
      service: lock.serviceid?.name || null,
      createdAt: lock.createdAt,
      updatedAt: lock.updatedAt
    }))

    return NextResponse.json({ success: true, locks: formatted }, { status: 200 })
  } catch (error) {
    console.error("Error fetching locked numbers:", error)
    return NextResponse.json(
      { success: false, error: "Unable to load locked numbers" },
      { status: 500 }
    )
  }
}
