import { createServer } from 'http';
import { parse } from 'url';
import next from 'next';
import { WebSocketServer } from 'ws';
import { v4 as uuidv4 } from 'uuid';
import mongoose from 'mongoose';
import WebSocketManager from './lib/websocket/manager.js';

const dev = process.env.NODE_ENV !== 'production';
const hostname = '0.0.0.0';
const port = process.env.PORT || 3000;

// Create the single authoritative WebSocketManager instance and store it
// globally so API routes can access it via getWsManager() → global.wsManager.
//
// FIX #1 + #7: The original code imported { getWsManager } from manager.js and
// then re-exported it. That function created an isolated empty instance, not this
// one. All API routes that called getWsManager() got a manager with no connections.
// The fix is to set global.wsManager here before any API route can run, and have
// getWsManager() in manager.js return global.wsManager exclusively.
const wsManager = new WebSocketManager();
global.wsManager = wsManager;

// MongoDB Connection
async function connectDB() {
  if (mongoose.connection.readyState >= 1) return;
  try {
    await mongoose.connect(process.env.MONGODB_URI);
    console.log('✅ MongoDB connected');
  } catch (error) {
    console.error('❌ MongoDB connection error:', error);
  }
}

const app = next({ dev, hostname, port });
const handle = app.getRequestHandler();

app.prepare().then(() => {
  const server = createServer(async (req, res) => {
    try {
      const parsedUrl = parse(req.url, true);
      await handle(req, res, parsedUrl);
    } catch (err) {
      console.error('Error occurred handling', req.url, err);
      res.statusCode = 500;
      res.end('internal server error');
    }
  });

  const wss = new WebSocketServer({ noServer: true });

  server.on('upgrade', (request, socket, head) => {
    const { pathname } = parse(request.url, true);
    if (pathname === '/gateway') {
      wss.handleUpgrade(request, socket, head, (ws) => {
        wss.emit('connection', ws, request);
      });
    } else {
      socket.destroy();
    }
  });

  wss.on('connection', async (ws, request) => {
    const connectionId = uuidv4();
    const clientIp = request.socket.remoteAddress;
    const { query } = parse(request.url, true);

    console.log(`📱 New WebSocket connection: ${connectionId} from ${clientIp}`);

    ws.connectionId = connectionId;
    ws.isAlive = true;
    ws.deviceId = null;
    ws.connectedAt = new Date();

    const isDashboard = query.client === 'dashboard';

    if (isDashboard) {
      console.log(`📊 Dashboard client connected: ${connectionId}`);
      ws.isDashboard = true;
      wsManager.addDashboardClient(ws);

      ws.on('pong', () => { ws.isAlive = true; });
      ws.on('close', () => {
        console.log(`📊 Dashboard client disconnected: ${connectionId}`);
        wsManager.removeDashboardClient(ws);
      });
      ws.on('error', (error) => {
        console.error(`❌ Dashboard WebSocket error for ${connectionId}:`, error.message);
        wsManager.removeDashboardClient(ws);
      });
      return;
    }

    ws.isAndroidDevice = true;

    ws.send(JSON.stringify({
      type: 'connected',
      connectionId,
      timestamp: Date.now(),
    }));

    ws.on('message', async (data) => {
      try {
        const message = JSON.parse(data.toString());
        await wsManager.handleMessage(ws, message);
      } catch (error) {
        console.error('❌ Error processing message:', error);
        ws.send(JSON.stringify({
          type: 'error',
          data: { message: 'Invalid message format' },
        }));
      }
    });

    ws.on('pong', () => { ws.isAlive = true; });
    ws.on('close', () => {
      console.log(`🔌 Connection closed: ${connectionId}`);
      wsManager.handleDisconnect(ws);
    });
    ws.on('error', (error) => {
      console.error(`❌ WebSocket error for ${connectionId}:`, error.message);
    });
  });

  const heartbeatInterval = setInterval(() => {
    wss.clients.forEach((ws) => {
      if (!ws.isAlive) {
        console.log(`💀 Terminating dead connection: ${ws.connectionId}`);
        if (ws.isDashboard) {
          wsManager.removeDashboardClient(ws);
        } else {
          wsManager.handleDisconnect(ws);
        }
        return ws.terminate();
      }
      ws.isAlive = false;
      ws.ping();
    });
  }, 30000);

  wss.on('close', () => clearInterval(heartbeatInterval));

  server.listen(port, async () => {
    await connectDB();
    console.log(`🚀 Server ready on http://${hostname}:${port}`);
    console.log(`🔌 WebSocket ready on ws://${hostname}:${port}/gateway`);
    console.log(`📊 Dashboard WS: ws://${hostname}:${port}/gateway?client=dashboard`);
  });
});

// FIX #7: Do NOT re-export getWsManager from here. The version in manager.js
// now returns global.wsManager correctly. Re-exporting the old isolated-instance
// version here would just propagate the bug to any consumer that imported from
// server.js instead of manager.js.
export { wsManager };