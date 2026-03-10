import connectDB from "@/lib/db"
import Countires from "@/models/Countires"
import { NextResponse } from "next/server"
import { verify } from "@/lib/verify"

export async function GET(req) {
  try {
    await connectDB()
 try {
      await verify(req)
    } catch (err) {
      return NextResponse.json({ error: err.error }, { status: err.status || 401 })
    }
    const countries = await Countires.find().sort({ name: 1 }) // Optional: filter active and sort by name

    return NextResponse.json({ success: true, countries }, { status: 200 })
  } catch (error) {
    console.error("Error fetching countries:", error)
    return NextResponse.json({ success: false, error: "Unable to load countries" }, { status: 500 })
  }
}
