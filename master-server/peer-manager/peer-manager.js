/**
 * FreeTime Master-Server Peer Manager
 * Manages peer connections, routing, and communication
 */

const { v4: uuidv4 } = require('uuid');
const jwt = require('jsonwebtoken');
const axios = require('axios');
const EventEmitter = require('events');

class PeerManager extends EventEmitter {
    constructor(dbConnection) {
        super();
        this.db = dbConnection;
        this.peers = new Map(); // Connected peers
        this.peerRoutes = new Map(); // Routing table
        this.heartbeatInterval = 30000; // 30 seconds
        this.maxRetries = 3;
        this.retryTimeout = 5000;
        
        this.startHeartbeat();
    }

    async registerPeer(peerInfo) {
        try {
            const peer = {
                id: peerInfo.id || uuidv4(),
                address: peerInfo.address,
                port: peerInfo.port,
                name: peerInfo.name || `Peer-${peerInfo.address}`,
                type: peerInfo.type || 'client',
                status: 'connecting',
                capabilities: peerInfo.capabilities || [],
                metadata: peerInfo.metadata || {},
                connected: false,
                lastSeen: new Date(),
                createdAt: new Date(),
                apiKey: peerInfo.apiKey || this.generateApiKey(),
                retryCount: 0
            };

            // Save to database
            await this.db.collection('peers').updateOne(
                { id: peer.id },
                { $set: peer },
                { upsert: true }
            );

            // Add to active peers
            this.peers.set(peer.id, peer);
            
            console.log(`🔗 Peer registered: ${peer.name} (${peer.address}:${peer.port})`);
            this.emit('peerRegistered', peer);
            
            return peer;
        } catch (error) {
            console.error('❌ Failed to register peer:', error);
            throw error;
        }
    }

    async connectPeer(peerId) {
        const peer = this.peers.get(peerId) || await this.getPeerFromDatabase(peerId);
        
        if (!peer) {
            throw new Error(`Peer ${peerId} not found`);
        }

        try {
            // Test peer connectivity
            const response = await axios.get(`http://${peer.address}:${peer.port}/health`, {
                timeout: 5000,
                headers: {
                    'Authorization': `Bearer ${peer.apiKey}`,
                    'User-Agent': 'FreeTime-MasterServer/1.0'
                }
            });

            if (response.status === 200) {
                peer.status = 'connected';
                peer.connected = true;
                peer.lastSeen = new Date();
                peer.retryCount = 0;

                // Update database
                await this.db.collection('peers').updateOne(
                    { id: peer.id },
                    { $set: { 
                        status: 'connected',
                        connected: true,
                        lastSeen: peer.lastSeen,
                        retryCount: 0
                    }}
                );

                // Update local cache
                this.peers.set(peer.id, peer);
                
                console.log(`✅ Peer connected: ${peer.name}`);
                this.emit('peerConnected', peer);
                
                return true;
            }
        } catch (error) {
            peer.status = 'failed';
            peer.connected = false;
            peer.retryCount++;
            
            // Update database
            await this.db.collection('peers').updateOne(
                { id: peer.id },
                { $set: { 
                    status: 'failed',
                    connected: false,
                    retryCount: peer.retryCount
                }}
            );

            console.error(`❌ Failed to connect to peer ${peer.name}:`, error.message);
            this.emit('peerConnectionFailed', peer, error);
            
            // Schedule retry if under max retries
            if (peer.retryCount < this.maxRetries) {
                setTimeout(() => this.connectPeer(peerId), this.retryTimeout);
            }
            
            return false;
        }
    }

    async disconnectPeer(peerId) {
        const peer = this.peers.get(peerId);
        
        if (peer) {
            peer.status = 'disconnected';
            peer.connected = false;
            peer.lastSeen = new Date();

            // Update database
            await this.db.collection('peers').updateOne(
                { id: peer.id },
                { $set: { 
                    status: 'disconnected',
                    connected: false,
                    lastSeen: peer.lastSeen
                }}
            );

            // Remove from active peers
            this.peers.delete(peerId);
            
            console.log(`🔌 Peer disconnected: ${peer.name}`);
            this.emit('peerDisconnected', peer);
            
            return true;
        }
        
        return false;
    }

    async routeMessage(fromPeerId, toPeerId, message) {
        const fromPeer = this.peers.get(fromPeerId);
        const toPeer = this.peers.get(toPeerId) || await this.getPeerFromDatabase(toPeerId);
        
        if (!fromPeer || !toPeer) {
            throw new Error('Peer not found for message routing');
        }

        if (!toPeer.connected) {
            throw new Error(`Target peer ${toPeer.name} is not connected`);
        }

        try {
            const messageData = {
                id: uuidv4(),
                fromPeer: fromPeerId,
                toPeer: toPeerId,
                type: message.type || 'data',
                payload: message.payload,
                timestamp: new Date(),
                routing: {
                    hops: 0,
                    path: [fromPeerId]
                }
            };

            // Send message to target peer
            const response = await axios.post(
                `http://${toPeer.address}:${toPeer.port}/message`,
                messageData,
                {
                    timeout: 10000,
                    headers: {
                        'Authorization': `Bearer ${toPeer.apiKey}`,
                        'Content-Type': 'application/json',
                        'User-Agent': 'FreeTime-MasterServer/1.0'
                    }
                }
            );

            // Store message in database
            await this.db.collection('peer_messages').insertOne(messageData);

            console.log(`📨 Message routed: ${fromPeer.name} → ${toPeer.name}`);
            this.emit('messageRouted', messageData);
            
            return messageData;
        } catch (error) {
            console.error(`❌ Failed to route message from ${fromPeer.name} to ${toPeer.name}:`, error.message);
            this.emit('messageRoutingFailed', { fromPeer, toPeer, message, error });
            throw error;
        }
    }

    async broadcastMessage(fromPeerId, message, excludePeers = []) {
        const fromPeer = this.peers.get(fromPeerId);
        const connectedPeers = Array.from(this.peers.values())
            .filter(peer => peer.connected && peer.id !== fromPeerId && !excludePeers.includes(peer.id));

        const results = [];
        
        for (const peer of connectedPeers) {
            try {
                const result = await this.routeMessage(fromPeerId, peer.id, message);
                results.push({ peerId: peer.id, success: true, result });
            } catch (error) {
                results.push({ peerId: peer.id, success: false, error: error.message });
            }
        }

        console.log(`📡 Broadcast completed: ${results.filter(r => r.success).length}/${results.length} peers reached`);
        this.emit('messageBroadcasted', { fromPeerId, message, results });
        
        return results;
    }

    async getPeerStatus(peerId) {
        const peer = this.peers.get(peerId) || await this.getPeerFromDatabase(peerId);
        
        if (!peer) {
            return null;
        }

        // Check if peer is still responsive
        if (peer.connected) {
            try {
                const response = await axios.get(`http://${peer.address}:${peer.port}/health`, {
                    timeout: 3000,
                    headers: {
                        'Authorization': `Bearer ${peer.apiKey}`,
                        'User-Agent': 'FreeTime-MasterServer/1.0'
                    }
                });

                peer.lastSeen = new Date();
                peer.status = 'connected';
            } catch (error) {
                peer.status = 'unresponsive';
                peer.connected = false;
            }
        }

        return {
            id: peer.id,
            name: peer.name,
            address: peer.address,
            port: peer.port,
            type: peer.type,
            status: peer.status,
            connected: peer.connected,
            lastSeen: peer.lastSeen,
            capabilities: peer.capabilities,
            metadata: peer.metadata
        };
    }

    async getAllPeers() {
        const peers = [];
        
        // Get connected peers from memory
        for (const peer of this.peers.values()) {
            peers.push(await this.getPeerStatus(peer.id));
        }
        
        // Get all peers from database
        const dbPeers = await this.db.collection('peers').find({}).toArray();
        
        // Merge and deduplicate
        const allPeers = new Map();
        peers.forEach(peer => allPeers.set(peer.id, peer));
        dbPeers.forEach(peer => {
            if (!allPeers.has(peer.id)) {
                allPeers.set(peer.id, {
                    id: peer.id,
                    name: peer.name,
                    address: peer.address,
                    port: peer.port,
                    type: peer.type,
                    status: peer.status,
                    connected: peer.connected,
                    lastSeen: peer.lastSeen,
                    capabilities: peer.capabilities,
                    metadata: peer.metadata
                });
            }
        });
        
        return Array.from(allPeers.values());
    }

    async removePeer(peerId) {
        try {
            // Disconnect if connected
            await this.disconnectPeer(peerId);
            
            // Remove from database
            const result = await this.db.collection('peers').deleteOne({ id: peerId });
            
            if (result.deletedCount > 0) {
                console.log(`🗑️  Peer removed: ${peerId}`);
                this.emit('peerRemoved', peerId);
                return true;
            }
            
            return false;
        } catch (error) {
            console.error(`❌ Failed to remove peer ${peerId}:`, error);
            throw error;
        }
    }

    startHeartbeat() {
        setInterval(async () => {
            const peers = Array.from(this.peers.values());
            
            for (const peer of peers) {
                if (peer.connected) {
                    try {
                        const response = await axios.get(`http://${peer.address}:${peer.port}/health`, {
                            timeout: 3000,
                            headers: {
                                'Authorization': `Bearer ${peer.apiKey}`,
                                'User-Agent': 'FreeTime-MasterServer/1.0'
                            }
                        });

                        peer.lastSeen = new Date();
                        peer.status = 'connected';
                    } catch (error) {
                        peer.status = 'unresponsive';
                        peer.connected = false;
                        
                        // Update database
                        await this.db.collection('peers').updateOne(
                            { id: peer.id },
                            { $set: { 
                                status: 'unresponsive',
                                connected: false,
                                lastSeen: new Date()
                            }}
                        );

                        console.log(`⚠️  Peer unresponsive: ${peer.name}`);
                        this.emit('peerUnresponsive', peer);
                    }
                }
            }
        }, this.heartbeatInterval);
    }

    async getPeerFromDatabase(peerId) {
        const peer = await this.db.collection('peers').findOne({ id: peerId });
        return peer;
    }

    generateApiKey() {
        return jwt.sign(
            { 
                id: uuidv4(),
                type: 'peer',
                generated: new Date().toISOString()
            },
            process.env.JWT_SECRET || 'default-secret',
            { expiresIn: '1y' }
        );
    }

    async getNetworkStats() {
        const allPeers = await this.getAllPeers();
        const connected = allPeers.filter(p => p.connected);
        const byType = allPeers.reduce((acc, peer) => {
            acc[peer.type] = (acc[peer.type] || 0) + 1;
            return acc;
        }, {});

        return {
            total: allPeers.length,
            connected: connected.length,
            disconnected: allPeers.length - connected.length,
            byType,
            uptime: process.uptime()
        };
    }
}

module.exports = PeerManager;
