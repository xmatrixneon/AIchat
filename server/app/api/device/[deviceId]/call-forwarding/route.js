import connectDB from '@/lib/db';
import Device from '@/models/Device';
import { NextResponse } from 'next/server';
// FIX #1: Import getWsManager from manager.js, which now correctly returns
// global.wsManager — the single live instance created by server.js that holds
// all real device connections. The previous approach of accessing global.wsManager
// directly in this file was fragile (requires the global to be set first) and the
// module-level wsManager variable was sometimes null on cold API route invocations.
import { getWsManager } from '@/lib/websocket/manager.js';

/**
 * POST /api/device/[deviceId]/call-forwarding
 *
 * Send a call forwarding command to a device.
 *
 * Request body:
 * {
 *   action: "forward" | "deactivate" | "check",
 *   phoneNumber: string (required for "forward" action),
 *   simSlot: number (0 or 1, 0-based, matches Android slot index)
 * }
 */
export async function POST(request, { params }) {
  try {
    await connectDB();

    const { deviceId } = await params;
    const body = await request.json();
    const { action, phoneNumber, simSlot = 0 } = body;

    const device = await Device.findOne({ deviceId });
    if (!device) {
      return NextResponse.json(
        { success: false, error: 'Device not found' },
        { status: 404 }
      );
    }

    const wsMgr = getWsManager();
    if (!wsMgr) {
      return NextResponse.json(
        { success: false, error: 'WebSocket manager not available' },
        { status: 503 }
      );
    }

    if (!wsMgr.isDeviceOnline(deviceId)) {
      return NextResponse.json(
        { success: false, error: 'Device is offline' },
        { status: 400 }
      );
    }

    const validActions = ['forward', 'deactivate', 'check'];
    if (!validActions.includes(action)) {
      return NextResponse.json(
        { success: false, error: `Invalid action. Must be one of: ${validActions.join(', ')}` },
        { status: 400 }
      );
    }

    if (action === 'forward' && (!phoneNumber || typeof phoneNumber !== 'string')) {
      return NextResponse.json(
        { success: false, error: 'Phone number is required for forward action' },
        { status: 400 }
      );
    }

    if (typeof simSlot !== 'number' || simSlot < 0 || simSlot > 1) {
      return NextResponse.json(
        { success: false, error: 'SIM slot must be 0 or 1' },
        { status: 400 }
      );
    }

    const command = {
      type: 'call_forwarding',
      data: {
        action,
        phoneNumber: action === 'forward' ? phoneNumber : null,
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

    console.log(`📞 Call forwarding command sent to ${deviceId}: action=${action}, simSlot=${simSlot}`);

    return NextResponse.json({
      success: true,
      message: 'Call forwarding command sent to device',
      data: {
        deviceId, action, simSlot,
        phoneNumber: action === 'forward' ? phoneNumber : null,
        timestamp: new Date().toISOString(),
      },
    });
  } catch (error) {
    console.error('Error sending call forwarding command:', error);
    return NextResponse.json(
      { success: false, error: error.message },
      { status: 500 }
    );
  }
}