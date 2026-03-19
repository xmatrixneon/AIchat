import { NextResponse } from 'next/server';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import MobileUser from '@/models/MobileUser';
import Token from '@/models/Token';
import connectDB from '@/lib/db';

const JWT_SECRET = process.env.JWT_SECRET || 'your_jwt_secret';
const MOBILE_API_KEY = process.env.MOBILE_API_KEY; // Optional extra security

export async function POST(req) {
  try {
    await connectDB();

    // Parse request body
    let body;
    try {
      body = await req.json();
    } catch (jsonError) {
      return NextResponse.json({ error: 'Invalid JSON format' }, { status: 400 });
    }

    const { email, password, apiKey, device = 'mobile' } = body || {};

    // Optional: Verify API key for additional security
    // This prevents credential stuffing even if email/password leak
    if (MOBILE_API_KEY && apiKey !== MOBILE_API_KEY) {
      return NextResponse.json({ error: 'Invalid client' }, { status: 401 });
    }

    // Validate required fields
    if (!email?.trim() || !password?.trim()) {
      return NextResponse.json(
        { error: 'Missing required fields: email and password are mandatory.' },
        { status: 400 }
      );
    }

    // Email format validation
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      return NextResponse.json({ error: 'Invalid email format' }, { status: 400 });
    }

    // ✅ CRITICAL: Only query MobileUser collection, NOT User collection
    // This ensures complete credential isolation
    const user = await MobileUser.findOne({ email });

    if (!user) {
      return NextResponse.json({ error: 'Invalid email or password' }, { status: 401 });
    }

    // Check if user is active
    if (!user.isActive) {
      return NextResponse.json({ error: 'Account is disabled' }, { status: 403 });
    }

    // Verify password
    const isMatch = await bcrypt.compare(password, user.password);
    if (!isMatch) {
      return NextResponse.json({ error: 'Invalid email or password' }, { status: 401 });
    }

    // Create JWT with mobile-specific claims
    const token = jwt.sign(
      {
        userId: user._id,
        email: user.email,
        type: 'mobile', // ← Distinguishes from admin tokens
        scopes: user.scopes,
      },
      JWT_SECRET,
      { expiresIn: '30d' }
    );

    // Store token with client type
    await Token.deleteMany({ user: user._id, device, clientType: 'mobile' });
    await Token.create({
      user: user._id,
      token,
      device,
      clientType: 'mobile', // ← Mark as mobile token
    });

    // Update last login
    await MobileUser.findByIdAndUpdate(user._id, { lastLoginAt: new Date() });

    return NextResponse.json({
      message: 'Login successful',
      token,
      user: {
        id: user._id,
        name: user.name,
        email: user.email,
        scopes: user.scopes,
      },
    });

  } catch (err) {
    console.error('Mobile Login Error:', err);
    return NextResponse.json({ error: 'Unexpected server error' }, { status: 500 });
  }
}
