import jwt from 'jsonwebtoken';
import User from '@/models/User';
import MobileUser from '@/models/MobileUser';
import Token from '@/models/Token';
import connectDB from '@/lib/db';

const JWT_SECRET = process.env.JWT_SECRET;

export async function verify(req, options = {}) {
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

    const tokenRecord = await Token.findOne({ token });
    if (!tokenRecord) {
      return {
        success: false,
        status: 401,
        error: 'Token not found (maybe revoked)',
      };
    }

    // Check if token type matches expected type
    const { requireAdmin = false, requireMobile = false, requiredScope = null } = options;

    if (requireAdmin && tokenRecord.clientType === 'mobile') {
      return {
        success: false,
        status: 403,
        error: 'Access denied: Admin access required',
      };
    }

    if (requireMobile && tokenRecord.clientType !== 'mobile') {
      return {
        success: false,
        status: 403,
        error: 'Access denied: Mobile access required',
      };
    }

    // Fetch user based on token type
    let user;
    if (tokenRecord.clientType === 'mobile') {
      user = await MobileUser.findById(decoded.userId).select('-password');

      // Check scope if required
      if (requiredScope && user && !user.scopes?.includes(requiredScope)) {
        return {
          success: false,
          status: 403,
          error: `Access denied: Missing required scope '${requiredScope}'`,
        };
      }
    } else {
      user = await User.findById(decoded.userId).select('-password');
    }

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
      clientType: tokenRecord.clientType,
      scopes: decoded.scopes || null,
    };

  } catch (err) {
    return {
      success: false,
      status: 500,
      error: err?.message || 'Internal server error',
    };
  }
}
