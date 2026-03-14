import connectDB from "@/lib/db"
import Message from "@/models/Message"
import { NextResponse } from "next/server"
import { verify } from "@/lib/verify"

export async function DELETE(req, { params }) {
  try {
    await connectDB()

    // 🔐 Auth verification
    try {
      await verify(req)
    } catch (err) {
      return NextResponse.json({ error: err.error }, { status: err.status || 401 })
    }

    const { id } = params

    // ✅ Validate ID exists
    if (!id) {
      return NextResponse.json(
        { success: false, error: "Message ID is required" },
        { status: 400 }
      )
    }

    // ✅ Find and delete the message
    const deletedMessage = await Message.findByIdAndDelete(id)

    if (!deletedMessage) {
      return NextResponse.json(
        { success: false, error: "Message not found" },
        { status: 404 }
      )
    }

    return NextResponse.json(
      { success: true, message: "Message deleted successfully" },
      { status: 200 }
    )
  } catch (error) {
    console.error("Error deleting message:", error)
    return NextResponse.json({ error: "Unexpected server error" }, { status: 500 })
  }
}
