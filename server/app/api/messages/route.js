import connectDB from '@/lib/db';
import Message from '@/models/Message';
import { NextResponse } from 'next/server';

export async function GET(request) {
  try {
    await connectDB();

    const { searchParams } = new URL(request.url);
    const limit = parseInt(searchParams.get('limit') || '100');
    const deviceId = searchParams.get('deviceId');
    const sender = searchParams.get('sender');
    const startDate = searchParams.get('startDate');
    const endDate = searchParams.get('endDate');

    let query = {};

    if (deviceId) {
      query['metadata.deviceId'] = deviceId;
    }

    if (sender) {
      query.sender = { $regex: sender, $options: 'i' };
    }

    if (startDate || endDate) {
      query.time = {};
      if (startDate) {
        query.time.$gte = new Date(startDate);
      }
      if (endDate) {
        query.time.$lte = new Date(endDate);
      }
    }

    const messages = await Message.find(query)
      .sort({ time: -1 })
      .limit(limit)
      .select('-__v');

    const total = await Message.countDocuments(query);

    return NextResponse.json({
      success: true,
      data: messages,
      meta: {
        total,
        limit
      }
    });

  } catch (error) {
    console.error('Error fetching messages:', error);
    return NextResponse.json(
      { success: false, error: error.message },
      { status: 500 }
    );
  }
}
