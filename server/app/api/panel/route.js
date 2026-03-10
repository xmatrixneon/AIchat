import connectDB from "@/lib/db";
import Panel from "@/models/Panel";
import { NextResponse } from "next/server";
import { verify } from "@/lib/verify"; // if you want JWT check

// Get panel (code 2 only)
export async function GET(req) {
  await connectDB();
  try {
    const panel = await Panel.findOne({ code: 1 });
    return NextResponse.json(panel || {});
  } catch (err) {
    return NextResponse.json({ error: err.message }, { status: 500 });
  }
}

// Add or update panel (code 2 only)
export async function POST(req) {
  await connectDB();
  try {
    await verify(req); // 🔐 check token

    const { url } = await req.json();
    const panel = await Panel.findOneAndUpdate(
      { code: 1 },
      { code: 1, url },
      { new: true, upsert: true }
    );
    return NextResponse.json(panel);
  } catch (err) {
    return NextResponse.json({ error: err.message }, { status: 500 });
  }
}
