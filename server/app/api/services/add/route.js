import connectDB from "@/lib/db"
import Service from "@/models/Service"
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
    const { name, code, formate, image, keywords, multisms, maxmessage } = body

    if (!name || !code || !formate || !image) {
      return NextResponse.json({ success: false, error: "Missing required fields" }, { status: 400 })
    }

    // Check for duplicate service code
    const existing = await Service.findOne({ code })
    if (existing) {
      return NextResponse.json({ success: false, error: "Service code already exists" }, { status: 409 })
    }

    const service = new Service({
      name,
      code,
      formate,
      image,
      keywords,
      multisms,
      maxmessage
    })

    await service.save()

    return NextResponse.json({ success: true, service }, { status: 201 })
  } catch (error) {
    console.error("Global error:", error)
    return NextResponse.json({ error: "Unexpected server error" }, { status: 500 })
  }
}
