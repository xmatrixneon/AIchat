import connectDB from '@/lib/db';
import Device from '@/models/Device';
import Message from '@/models/Message';
import { NextResponse } from 'next/server';

export async function GET(request) {
  try {
    await connectDB();

    const { searchParams } = new URL(request.url);
    const timeframe = searchParams.get('timeframe') || '24h';

    // Calculate date range
    const now = new Date();
    let startDate = new Date();

    switch (timeframe) {
      case '1h':
        startDate.setHours(now.getHours() - 1);
        break;
      case '24h':
        startDate.setDate(now.getDate() - 1);
        break;
      case '7d':
        startDate.setDate(now.getDate() - 7);
        break;
      case '30d':
        startDate.setDate(now.getDate() - 30);
        break;
      default:
        startDate.setDate(now.getDate() - 1);
    }

    // Get device stats
    const totalDevices = await Device.countDocuments();
    const onlineDevices = await Device.countDocuments({ status: 'online' });
    const offlineDevices = await Device.countDocuments({ status: 'offline' });
    const activeDevices = await Device.countDocuments({
      lastSeen: { $gte: startDate }
    });

    // Get message stats
    const totalMessages = await Message.countDocuments({
      time: { $gte: startDate }
    });

    // Messages by hour (for chart)
    const messagesByHour = await Message.aggregate([
      {
        $match: {
          time: { $gte: startDate }
        }
      },
      {
        $group: {
          _id: {
            $dateToString: {
              format: '%Y-%m-%d %H:00',
              date: '$time'
            }
          },
          count: { $sum: 1 }
        }
      },
      {
        $sort: { _id: 1 }
      },
      {
        $limit: 24
      }
    ]);

    // Top senders
    const topSenders = await Message.aggregate([
      {
        $match: {
          time: { $gte: startDate }
        }
      },
      {
        $group: {
          _id: '$sender',
          count: { $sum: 1 }
        }
      },
      {
        $sort: { count: -1 }
      },
      {
        $limit: 10
      }
    ]);

    // Messages by device
    const messagesByDevice = await Message.aggregate([
      {
        $match: {
          time: { $gte: startDate },
          'metadata.deviceId': { $exists: true }
        }
      },
      {
        $group: {
          _id: '$metadata.deviceId',
          count: { $sum: 1 }
        }
      },
      {
        $sort: { count: -1 }
      },
      {
        $limit: 10
      }
    ]);

    return NextResponse.json({
      success: true,
      data: {
        devices: {
          total: totalDevices,
          online: onlineDevices,
          offline: offlineDevices,
          active: activeDevices
        },
        messages: {
          total: totalMessages,
          byHour: messagesByHour.map(item => ({
            time: item._id,
            count: item.count
          })),
          topSenders: topSenders.map(item => ({
            sender: item._id,
            count: item.count
          })),
          byDevice: messagesByDevice.map(item => ({
            deviceId: item._id,
            count: item.count
          }))
        },
        timeframe,
        startDate,
        endDate: now
      }
    });

  } catch (error) {
    console.error('Error fetching stats:', error);
    return NextResponse.json(
      { success: false, error: error.message },
      { status: 500 }
    );
  }
}
