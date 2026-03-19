#!/usr/bin/env node
/**
 * Script to create a mobile app user
 * Usage: node script/create-mobile-user.mjs <email> <password> <name>
 *
 * Example:
 *   node script/create-mobile-user.mjs mobile@example.com mypassword123 "Mobile User"
 */

import mongoose from 'mongoose';
import bcrypt from 'bcryptjs';
import 'dotenv/config';

// Simple MobileUser schema (inline for script)
const mobileUserSchema = new mongoose.Schema({
  name: { type: String, required: true },
  email: { type: String, required: true, unique: true, lowercase: true },
  password: { type: String, required: true },
  allowedDevices: [{ type: String }],
  scopes: { type: [String], default: ['devices:read', 'messages:read', 'sms:send', 'call:manage', 'ws:connect'] },
  isActive: { type: Boolean, default: true },
  createdAt: { type: Date, default: Date.now },
  lastLoginAt: { type: Date },
});

const MobileUser = mongoose.models.MobileUser || mongoose.model('MobileUser', mobileUserSchema);

async function createMobileUser() {
  const args = process.argv.slice(2);

  if (args.length < 3) {
    console.log('Usage: node script/create-mobile-user.mjs <email> <password> <name>');
    console.log('Example: node script/create-mobile-user.mjs mobile@example.com mypassword123 "Mobile User"');
    process.exit(1);
  }

  const [email, password, name] = args;

  try {
    await mongoose.connect(process.env.MONGODB_URI);
    console.log('✅ Connected to MongoDB');

    // Check if user already exists
    const existing = await MobileUser.findOne({ email });
    if (existing) {
      console.log('❌ User with this email already exists');
      process.exit(1);
    }

    // Hash password
    const hashedPassword = await bcrypt.hash(password, 10);

    // Create user
    const user = await MobileUser.create({
      name,
      email,
      password: hashedPassword,
    });

    console.log('✅ Mobile user created successfully!');
    console.log('-----------------------------------');
    console.log(`ID: ${user._id}`);
    console.log(`Name: ${user.name}`);
    console.log(`Email: ${user.email}`);
    console.log(`Scopes: ${user.scopes.join(', ')}`);
    console.log('-----------------------------------');
    console.log('\n📝 Use these credentials in your Expo app.');
    console.log('⚠️  These credentials will NOT work on the web admin panel.');

    process.exit(0);
  } catch (error) {
    console.error('❌ Error:', error.message);
    process.exit(1);
  }
}

createMobileUser();
