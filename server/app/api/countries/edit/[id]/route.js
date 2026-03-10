import connectDB from "@/lib/db"
import Countires from "@/models/Countires"
import { NextResponse } from "next/server"
import { verify } from "@/lib/verify"

export async function PUT(req, context) {
  try {
    await connectDB()

    try {
      await verify(req)
    } catch (err) {
      return NextResponse.json({ error: err.error }, { status: err.status || 401 })
    }
    const id = context.params.id
    const body = await req.json()

    const { name, code, dial, flag, active } = body
    if (!name || !code || !dial || !flag) {
      return NextResponse.json(
        { success: false, error: "Missing required fields" },
        { status: 400 }
      )
    }

    const updated = await Countires.findByIdAndUpdate(
      id,
      { name, code, dial, flag, active },
      { new: true }
    )

    if (!updated) {
      return NextResponse.json({ success: false, error: "Country not found" }, { status: 404 })
    }

    return NextResponse.json({ success: true, country: updated })
  } catch (error) {
    console.error("Update error:", error)
    return NextResponse.json({ success: false, error: "Server error" }, { status: 500 })
  }
}
