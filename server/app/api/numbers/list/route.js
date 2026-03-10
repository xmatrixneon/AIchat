// app/api/numbers/route.js
import { NextResponse } from "next/server";
import dbConnect from "@/lib/db";
import Numbers from "@/models/Numbers";
import Countries from "@/models/Countires"; // your Countries model
import { verify } from "@/lib/verify";

export async function GET(req) {
  try {
    // JWT verification
    await verify(req);

    // Connect to MongoDB
    await dbConnect();

    // Fetch only active numbers and populate country
    const numbers = await Numbers.find({ active: true })
      .populate({
        path: "countryid",        // field in Numbers schema
        model: Countries,         // Countries model
        select: "name flag code", // select only needed fields
      })
      .lean();

    return NextResponse.json({ success: true, data: numbers });
  } catch (err) {
    console.error(err);
    return NextResponse.json(
      { error: err.error || "Something went wrong" },
      { status: err.status || 500 }
    );
  }
}
