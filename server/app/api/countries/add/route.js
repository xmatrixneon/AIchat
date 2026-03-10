import connectDB from "@/lib/db"
import Countires from "@/models/Countires"
import { NextResponse } from "next/server"
import { verify } from "@/lib/verify"

export async function POST(req) {
  try {
    await connectDB()

    try {
      await verify(req)
    } catch (err) {
      return NextResponse.json({ error: err.error }, { status: err.status || 401 })
    }

    const body = await req.json()
    const { name, flag, code, dial, active } = body

    if (!name || !flag || !code || !dial) {
      return NextResponse.json({ success: false, error: "Missing required fields" }, { status: 400 })
    }

    // Check for duplicate country code
    const existing = await Countires.findOne({ code })
    if (existing) {
      return NextResponse.json({ success: false, error: "Country code already exists" }, { status: 409 })
    }

    const country = new Countires({
      name,
      flag,
      code,
      dial,
      active, // ← Save active status here
    })

    await country.save()

    return NextResponse.json({ success: true, country }, { status: 201 })
  } catch (error) {
    console.error("Global error:", error)
    return NextResponse.json({ error: "Unexpected server error" }, { status: 500 })
  }
}
