import { NextResponse } from "next/server";
import connectDB from "@/lib/db"
import Orders from "@/models/Orders";

export async function GET() {
  try {
    await connectDB();
    const orders = await Orders.find({});
    return NextResponse.json(orders);
  } catch (error) {
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}

export async function POST(req) {
  try {
    await connectDB();
    const data = await req.json();

    const newOrder = await Orders.create(data);

    return NextResponse.json(newOrder, { status: 201 });
  } catch (error) {
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}
