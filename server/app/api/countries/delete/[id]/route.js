import connectDB from "@/lib/db"
import Countires from "@/models/Countires"
import { NextResponse } from "next/server"
import { verify } from "@/lib/verify"
export async function DELETE(req, context) {
  try {
    await connectDB()
     try {
      await verify(req)
    } catch (err) {
      return NextResponse.json({ error: err.error }, { status: err.status || 401 })
    }
    const { id } = context.params
    const deleted = await Countires.findByIdAndDelete(id)

    if (!deleted) {
      return NextResponse.json({ success: false, error: "Country not found" }, { status: 404 })
    }

    return NextResponse.json({ success: true, message: "Deleted successfully" })
  } catch (error) {
    return NextResponse.json({ success: false, error: "Server error" }, { status: 500 })
  }
}
