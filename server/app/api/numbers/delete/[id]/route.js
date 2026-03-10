// app/api/numbers/[id]/route.js
import { NextResponse } from "next/server";
import dbConnect from "@/lib/db";
import Numbers from "@/models/Numbers";
import { verify } from "@/lib/verify";

export async function DELETE(req, { params }) {
  try {
    // JWT verification
    await verify(req);

    const { id } = await params;

    if (!id) {
      return NextResponse.json({ error: "Number ID is required" }, { status: 400 });
    }

    await dbConnect();

    const deleted = await Numbers.findByIdAndDelete(id);

    if (!deleted) {
      return NextResponse.json({ error: "Number not found" }, { status: 404 });
    }

    return NextResponse.json({ success: true, message: "Number deleted successfully" });
  } catch (err) {
    console.error(err);
    return NextResponse.json(
      { error: err.error || "Something went wrong" },
      { status: err.status || 500 }
    );
  }
}
