import jwt from 'jsonwebtoken';
import User from '@/models/User';
import Token from '@/models/Token';
import connectDB from '@/lib/db';

const JWT_SECRET = process.env.JWT_SECRET;

export async function verify(req) {
  try {
    await connectDB();

    const authHeader = req.headers.get('authorization');
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return {
        success: false,
        status: 401,
        error: 'Unauthorized: No token provided',
      };
    }

    const token = authHeader.split(' ')[1];

    let decoded;
    try {
      decoded = jwt.verify(token, JWT_SECRET);
    } catch (err) {
      return {
        success: false,
        status: 401,
        error: 'Invalid or expired token',
      };
    }

    const exists = await Token.findOne({ token });
    if (!exists) {
      return {
        success: false,
        status: 401,
        error: 'Token not found (maybe revoked)',
      };
    }

    const user = await User.findById(decoded.userId).select('-password');
    if (!user) {
      return {
        success: false,
        status: 404,
        error: 'User not found',
      };
    }

    return {
      success: true,
      user,
      token,
    };

  } catch (err) {
    return {
      success: false,
      status: 500,
      error: err?.message || 'Internal server error',
    };
  }
}
