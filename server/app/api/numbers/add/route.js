import connectDB from "@/lib/db"
import Numbers from "@/models/Numbers"
import Countires from "@/models/Countires"
import { NextResponse } from "next/server"
import { verify } from "@/lib/verify"
import mongoose from "mongoose"

export async function POST(req) {
  try {
    await connectDB()

    // Auth verification
    try {
      await verify(req)
    } catch (err) {
      return NextResponse.json({ error: err.error }, { status: err.status || 401 })
    }

    const body = await req.json()
    const { number, countryid, multiuse, multigap, active } = body

    // Validate required fields
    if (!number || !countryid) {
      return NextResponse.json({ success: false, error: "Missing required fields" }, { status: 400 })
    }

    // Validate ObjectId format
    if (!mongoose.Types.ObjectId.isValid(countryid)) {
      return NextResponse.json({ success: false, error: "Invalid country ID format" }, { status: 400 })
    }

    // Check if country exists
    const countryExists = await Countires.findById(countryid)
    if (!countryExists) {
      return NextResponse.json({ success: false, error: "Country not found" }, { status: 404 })
    }

    // Check for duplicate number
    const existing = await Numbers.findOne({ number })
    if (existing) {
      return NextResponse.json({ success: false, error: "Number already exists" }, { status: 409 })
    }

    const newNumber = new Numbers({
      number,
      countryid,
      multiuse,
      multigap,
      active,
    })

    await newNumber.save()

    return NextResponse.json({ success: true, number: newNumber }, { status: 201 })
  } catch (error) {
    console.error("Error creating number:", error)
    return NextResponse.json({ error: "Unexpected server error" }, { status: 500 })
  }
}
