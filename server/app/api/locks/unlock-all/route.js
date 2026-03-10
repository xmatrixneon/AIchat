import connectDB from "@/lib/db"
import Lock from "@/models/Lock"
import Service from "@/models/Service"
import { NextResponse } from "next/server"

export async function POST(req) {
  try {
    await connectDB()

    const { service } = await req.json()
    if (!service) {
      return NextResponse.json({ error: "Service name is required" }, { status: 400 })
    }

    // Find the service by name to get its ObjectId
    const serviceDoc = await Service.findOne({ name: service })
    if (!serviceDoc) {
      return NextResponse.json({ error: "Service not found" }, { status: 404 })
    }

    // Unlock all locks for the given service by ObjectId
    const result = await Lock.updateMany(
      { serviceid: serviceDoc._id, locked: true },
      { locked: false }
    )

    if (result.modifiedCount === 0) {
      return NextResponse.json({ error: "No locks found for this service or already unlocked" }, { status: 404 })
    }

    return NextResponse.json({ 
      message: `Successfully unlocked ${result.modifiedCount} locks for service: ${service}`,
      unlockedCount: result.modifiedCount
    })
  } catch (err) {
    console.error("Bulk unlock error:", err)
    return NextResponse.json({ error: "Internal server error" }, { status: 500 })
  }
}
