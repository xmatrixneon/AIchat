import connectDB from '@/lib/db';
import Device from '@/models/Device';
import Message from '@/models/Message';
import { NextResponse } from 'next/server';

export async function GET(request, { params }) {
  try {
    await connectDB();
    const { deviceId } = await params;

    const device = await Device.findOne({ deviceId }).select('-__v -apiKey');
    if (!device) {
      return NextResponse.json(
        { success: false, error: 'Device not found' },
        { status: 404 }
      );
    }

    const recentMessages = await Message.find({ 'metadata.deviceId': deviceId })
      .sort({ time: -1 })
      .limit(50)
      .select('-__v');

    const totalMessages = await Message.countDocuments({ 'metadata.deviceId': deviceId });

    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const messagesToday = await Message.countDocuments({
      'metadata.deviceId': deviceId,
      time: { $gte: today },
    });

    return NextResponse.json({
      success: true,
      data: { device, recentMessages, stats: { totalMessages, messagesToday } },
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
    const { deviceId } = await params;
    const body = await request.json();

    // FIX #6: Strip all fields that must never be overwritten by an API caller.
    // The original did a direct $set of the raw request body, meaning any caller
    // could overwrite deviceId, registeredAt, totalMessagesReceived, apiKey, etc.
    // Only allow the fields that represent user-editable device metadata.
    const {
      // Immutable / system-managed — always excluded
      deviceId:             _deviceId,
      registeredAt:         _registeredAt,
      createdAt:            _createdAt,
      updatedAt:            _updatedAt,
      apiKey:               _apiKey,
      totalMessagesSent:    _totalMessagesSent,
      totalMessagesReceived:_totalMessagesReceived,
      lastMessageReceived:  _lastMessageReceived,
      lastHeartbeat:        _lastHeartbeat,
      lastSeen:             _lastSeen,
      status:               _status,
      // Everything else is caller-editable
      ...allowedFields
    } = body;

    if (Object.keys(allowedFields).length === 0) {
      return NextResponse.json(
        { success: false, error: 'No editable fields provided' },
        { status: 400 }
      );
    }

    const device = await Device.findOneAndUpdate(
      { deviceId },
      { $set: allowedFields },
      { new: true, runValidators: true }
    ).select('-__v -apiKey');

    if (!device) {
      return NextResponse.json(
        { success: false, error: 'Device not found' },
        { status: 404 }
      );
    }

    return NextResponse.json({ success: true, data: device });
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
    const { deviceId } = await params;

    const device = await Device.findOneAndDelete({ deviceId });
    if (!device) {
      return NextResponse.json(
        { success: false, error: 'Device not found' },
        { status: 404 }
      );
    }

    await Message.deleteMany({ 'metadata.deviceId': deviceId });

    return NextResponse.json({ success: true, message: 'Device deleted successfully' });
  } catch (error) {
    console.error('Error deleting device:', error);
    return NextResponse.json(
      { success: false, error: error.message },
      { status: 500 }
    );
  }
}