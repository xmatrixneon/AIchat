import { NextResponse } from 'next/server'
import connectDB from '@/lib/db'
import Service from '@/models/Service'
import { verify } from "@/lib/verify"
export async function GET(req) {
  await connectDB()


   const result = await verify(req);

if (!result.success) {
  return NextResponse.json({ error: result.error }, { status: result.status || 401 });
}
  const services = await Service.find()
  return NextResponse.json(services)
}
