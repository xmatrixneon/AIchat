import { NextResponse } from 'next/server';
import jwt from 'jsonwebtoken';
import Token from '@/models/Token';
import connectDB from '@/lib/db';

const JWT_SECRET = process.env.JWT_SECRET;

export async function POST(req) {
  try {
    await connectDB();

    // Get token from header
    const authHeader = req.headers.get('authorization');
    if (!authHeader?.startsWith('Bearer ')) {
      // No token - just return success (already logged out)
      return NextResponse.json({ success: true, message: 'Logged out' });
    }

    const token = authHeader.split(' ')[1];

    // Try to delete token from database - don't fail if it doesn't exist
    try {
      await Token.deleteOne({ token });
    } catch (e) {
      // Ignore database errors - client-side logout is what matters
    }

    return NextResponse.json({
      success: true,
      message: 'Logged out successfully',
    });

  } catch (err) {
    // Even on error, return success - client-side logout is what matters
    console.error('Mobile Logout Error:', err);
    return NextResponse.json({ success: true, message: 'Logged out' });
  }
}
