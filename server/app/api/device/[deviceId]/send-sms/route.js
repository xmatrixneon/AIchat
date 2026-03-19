import connectDB from '@/lib/db';
import Device from '@/models/Device';
import { NextResponse } from 'next/server';
import { getWsManager } from '@/lib/websocket/manager.js';
import { v4 as uuidv4 } from 'uuid';

/**
 * POST /api/device/[deviceId]/send-sms
 *
 * Send an SMS message through a connected device.
 *
 * Request body:
 * {
 *   phoneNumber: string,  // Required - recipient phone number
 *   message: string,      // Required - message content
 *   simSlot: number       // Optional - SIM slot to use (0 or 1, default: 0)
 * }
 *
 * Response:
 * {
 *   success: true,
 *   messageId: string,
 *   deviceId: string,
 *   phoneNumber: string,
 *   simSlot: number,
 *   timestamp: string
 * }
 */
export async function POST(request, { params }) {
  try {
    await connectDB();

    const { deviceId } = await params;
    const body = await request.json();
    const { phoneNumber, message, simSlot = 0 } = body;

    // Validate required fields
    if (!phoneNumber || typeof phoneNumber !== 'string') {
      return NextResponse.json(
        { success: false, error: 'Phone number is required' },
        { status: 400 }
      );
    }

    if (!message || typeof message !== 'string') {
      return NextResponse.json(
        { success: false, error: 'Message is required' },
        { status: 400 }
      );
    }

    // Validate phone number format (basic check)
    // Allow short codes (3+ digits) and regular phone numbers
    const sanitizedPhone = phoneNumber.replace(/[^+\d]/g, '');
    if (sanitizedPhone.length < 3 || sanitizedPhone.length > 20) {
      return NextResponse.json(
        { success: false, error: 'Invalid phone number format (must be 3-20 digits)' },
        { status: 400 }
      );
    }

    // Validate message length
    if (message.length > 1600) {
      return NextResponse.json(
        { success: false, error: 'Message too long (max 1600 characters)' },
        { status: 400 }
      );
    }

    // Validate SIM slot
    if (typeof simSlot !== 'number' || simSlot < 0 || simSlot > 1) {
      return NextResponse.json(
        { success: false, error: 'SIM slot must be 0 or 1' },
        { status: 400 }
      );
    }

    // Check if device exists
    const device = await Device.findOne({ deviceId });
    if (!device) {
      return NextResponse.json(
        { success: false, error: 'Device not found' },
        { status: 404 }
      );
    }

    // Get WebSocket manager
    const wsMgr = getWsManager();
    if (!wsMgr) {
      return NextResponse.json(
        { success: false, error: 'WebSocket manager not available' },
        { status: 503 }
      );
    }

    // Check if device is online
    if (!wsMgr.isDeviceOnline(deviceId)) {
      return NextResponse.json(
        { success: false, error: 'Device is offline' },
        { status: 400 }
      );
    }

    // Generate message ID for tracking
    const messageId = uuidv4();

    // Send SMS command to device
    const command = {
      type: 'send_sms',
      data: {
        messageId,
        phoneNumber: sanitizedPhone,
        message,
        simSlot,
      },
    };

    const sent = wsMgr.sendToDevice(deviceId, command);
    if (!sent) {
      return NextResponse.json(
        { success: false, error: 'Failed to send command to device' },
        { status: 500 }
      );
    }

    console.log(`📩 SMS send command sent to ${deviceId}: ${sanitizedPhone} via SIM ${simSlot}`);

    return NextResponse.json({
      success: true,
      message: 'SMS send command sent to device',
      data: {
        messageId,
        deviceId,
        phoneNumber: sanitizedPhone,
        message,
        simSlot,
        timestamp: new Date().toISOString(),
      },
    });
  } catch (error) {
    console.error('Error sending SMS command:', error);
    return NextResponse.json(
      { success: false, error: error.message },
      { status: 500 }
    );
  }
}
