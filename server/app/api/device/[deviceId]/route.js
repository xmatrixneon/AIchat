import connectDB from '@/lib/db';
import Device from '@/models/Device';
import Message from '@/models/Message';
import { NextResponse } from 'next/server';

export async function GET(request, { params }) {
  try {
    await connectDB();

    const { deviceId } = params;

    const device = await Device.findOne({ deviceId }).select('-__v -apiKey');

    if (!device) {
      return NextResponse.json(
        { success: false, error: 'Device not found' },
        { status: 404 }
      );
    }

    // Get recent messages for this device
    const recentMessages = await Message.find({
      'metadata.deviceId': deviceId
    })
      .sort({ time: -1 })
      .limit(50)
      .select('-__v');

    // Get message stats
    const totalMessages = await Message.countDocuments({
      'metadata.deviceId': deviceId
    });

    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const messagesToday = await Message.countDocuments({
      'metadata.deviceId': deviceId,
      time: { $gte: today }
    });

    return NextResponse.json({
      success: true,
      data: {
        device,
        recentMessages,
        stats: {
          totalMessages,
          messagesToday
        }
      }
    });

  } catch (error) {
    console.error('Error fetching device:', error);
    return NextResponse.json(
      { success: false, error: error.message },
      { status: 500 }
    );
  }
}

export async function PUT(request, { params }) {
  try {
    await connectDB();

    const { deviceId } = params;
    const body = await request.json();

    const device = await Device.findOneAndUpdate(
      { deviceId },
      { $set: body },
      { new: true, runValidators: true }
    ).select('-__v -apiKey');

    if (!device) {
      return NextResponse.json(
        { success: false, error: 'Device not found' },
        { status: 404 }
      );
    }

    return NextResponse.json({
      success: true,
      data: device
    });

  } catch (error) {
    console.error('Error updating device:', error);
    return NextResponse.json(
      { success: false, error: error.message },
      { status: 500 }
    );
  }
}

export async function DELETE(request, { params }) {
  try {
    await connectDB();

    const { deviceId } = params;

    const device = await Device.findOneAndDelete({ deviceId });

    if (!device) {
      return NextResponse.json(
        { success: false, error: 'Device not found' },
        { status: 404 }
      );
    }

    // Optionally delete associated messages
    await Message.deleteMany({ 'metadata.deviceId': deviceId });

    return NextResponse.json({
      success: true,
      message: 'Device deleted successfully'
    });

  } catch (error) {
    console.error('Error deleting device:', error);
    return NextResponse.json(
      { success: false, error: error.message },
      { status: 500 }
    );
  }
}
