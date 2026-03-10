import connectDB from "@/lib/db"
import Message from "@/models/Message"
import { NextResponse } from "next/server"
import { verify } from "@/lib/verify"
import mongoose from "mongoose"

export async function POST(req) {
  try {
    await connectDB()

    // 🔐 Auth verification
    try {
      await verify(req)
    } catch (err) {
      return NextResponse.json({ error: err.error }, { status: err.status || 401 })
    }

    const body = await req.json()
    const { sender, receiver, port, time, message } = body

    // ✅ Validate required fields
    if (!sender || !receiver || !message) {
      return NextResponse.json(
        { success: false, error: "Missing required fields" },
        { status: 400 }
      )
    }

    // ✅ If time is provided, validate it
    let parsedTime = new Date()
    if (time) {
      parsedTime = new Date(time)
      if (isNaN(parsedTime.getTime())) {
        return NextResponse.json(
          { success: false, error: "Invalid time format" },
          { status: 400 }
        )
      }
    }

    // ✅ Create new message (no duplicate check)
    const newMessage = new Message({
      sender,
      receiver,
      port,
      time: parsedTime,
      message,
    })

    await newMessage.save()

    return NextResponse.json({ success: true, message: newMessage }, { status: 201 })
  } catch (error) {
    console.error("Error creating message:", error)
    return NextResponse.json({ error: "Unexpected server error" }, { status: 500 })
  }
}

// GET route for listing last 200 messages from last 2 days (IST)
export async function GET() {
  try {
    await connectDB()

    // ✅ Fetch last 200 messages, newest first
    const messages = await Message.find({})
      .sort({ createdAt: -1 }) // newest first
      .limit(200) // limit to 200 messages

    return NextResponse.json({ success: true, data: messages }, { status: 200 })
  } catch (error) {
    console.error("Error fetching messages:", error)
    return NextResponse.json({ error: "Unexpected server error" }, { status: 500 })
  }
}