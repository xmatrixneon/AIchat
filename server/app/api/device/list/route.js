import connectDB from '@/lib/db';
import Device from '@/models/Device';
import Message from '@/models/Message';
import { NextResponse } from 'next/server';

export async function GET(request) {
  try {
    await connectDB();
    const { searchParams } = new URL(request.url);
    const page = parseInt(searchParams.get('page') || '1');
    const limit = parseInt(searchParams.get('limit') || '20');
    const sortBy = searchParams.get('sortBy') || 'lastSeen';
    const sortOrder = searchParams.get('sortOrder') === 'asc' ? 1 : -1;
    const status = searchParams.get('status');
    const search = searchParams.get('search');

    let query = {};
    if (status && status !== 'all') query.status = status;
    if (search) {
      query.$or = [
        { name: { $regex: search, $options: 'i' } },
        { deviceId: { $regex: search, $options: 'i' } }
      ];
    }

    const total = await Device.countDocuments(query);
    const pages = Math.ceil(total / limit);

    const rawDevices = await Device.find(query)
      .sort({ [sortBy]: sortOrder })
      .skip((page - 1) * limit)
      .limit(limit)
      .select('-__v -apiKey');

    const now = new Date();
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    // Enrich each device with recentMessages and timeSinceLastSeen
    const devices = await Promise.all(rawDevices.map(async (device) => {
      const recentMessages = await Message.countDocuments({
        'metadata.deviceId': device.deviceId,
        time: { $gte: today }
      });

      const lastSeen = new Date(device.lastSeen);
      const diffMs = now - lastSeen;
      const diffMins = Math.floor(diffMs / 60000);
      const diffHours = Math.floor(diffMins / 60);
      const diffDays = Math.floor(diffHours / 24);

      return {
        ...device.toObject(),
        recentMessages,
        timeSinceLastSeen: {
          minutes: diffMins % 60,
          hours: diffHours % 24,
          days: diffDays
        }
      };
    }));

    // Stats
    const online = await Device.countDocuments({ ...query, status: 'online' });
    const offline = await Device.countDocuments({ ...query, status: 'offline' });
    const error = await Device.countDocuments({ ...query, status: 'error' });
    const totalMessages = await Message.countDocuments({ time: { $gte: today } });

    return NextResponse.json({
      success: true,
      data: {
        devices,
        stats: { total, online, offline, error, totalMessages },
        pagination: { page, pages, limit, total }
      }
    });
  } catch (error) {
    console.error('Error fetching devices:', error);
    return NextResponse.json({ success: false, error: error.message }, { status: 500 });
  }
}
