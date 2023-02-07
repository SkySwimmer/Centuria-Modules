package org.asf.nexus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.function.Consumer;

/**
 * 
 * Nexus Client Connector
 * 
 * @author Sky Swimmer
 *
 */
public class NexusClient {

	private String channel;
	private Socket socket;
	private String connectionID;
	private boolean disconnecting = false;
	private boolean sending = false;
	private long lastPing = -1;

	/**
	 * Nexus protocol version, currently version 4
	 */
	public static final int PROTOCOL_VERSION = 4;

	private ArrayList<ConnectionEvent> connectedEvent = new ArrayList<ConnectionEvent>();
	private ArrayList<ConnectionEvent> disconnectedEvent = new ArrayList<ConnectionEvent>();
	private ArrayList<PacketEvent> packetEvents = new ArrayList<PacketEvent>();
	private ArrayList<PacketEvent> directPacketEvents = new ArrayList<PacketEvent>();

	private NexusClient() {
	}

	class NexusClientHooks {
		public NexusClient getClient() {
			return NexusClient.this;
		}

		public void start(Socket sock, boolean server) throws IOException {
			// Perform handshake
			byte[] magic = "NEXUS_LN".getBytes("UTF-8");
			if (server) {
				for (int i = 0; i < magic.length; i++) {
					int b = sock.getInputStream().read();
					if (magic[i] != b) {
						// Incompatible
						sock.getOutputStream().write(1);
						sock.close();
						throw new IOException("Incompatible remote connection");
					}
				}

				// Read client protocol
				int clientProtocol = NexusClient.readInt(sock.getInputStream());
				if (clientProtocol != PROTOCOL_VERSION) {
					// Incompatible
					sock.getOutputStream().write(1);
					sock.close();
					throw new IOException("Incompatible remote connection");
				}
				sock.getOutputStream().write(3);

				sock.getOutputStream().write(magic);
				NexusClient.writeInt(PROTOCOL_VERSION, sock.getOutputStream());

				// Try to handshake with protocol 3
				if (sock.getInputStream().read() != 3) {
					// Incompatible
					sock.close();
					throw new IOException("Incompatible remote connection");
				}
			} else {
				sock.getOutputStream().write(magic);
				NexusClient.writeInt(PROTOCOL_VERSION, sock.getOutputStream());

				// Try to handshake with protocol 3
				if (sock.getInputStream().read() != 3) {
					// Incompatible
					sock.close();
					throw new IOException("Incompatible remote connection");
				}

				for (int i = 0; i < magic.length; i++) {
					int b = sock.getInputStream().read();
					if (magic[i] != b) {
						// Incompatible
						sock.getOutputStream().write(1);
						sock.close();
						throw new IOException("Incompatible remote connection");
					}
				}

				// Read server protocol
				int serverProtocol = NexusClient.readInt(sock.getInputStream());
				if (serverProtocol != PROTOCOL_VERSION) {
					// Incompatible
					sock.getOutputStream().write(1);
					sock.close();
					throw new IOException("Incompatible remote connection");
				}
				sock.getOutputStream().write(3);
			}

			// Write success
			sock.getOutputStream().write(0);
		}

		public void setChannel(String channel) {
			getClient().channel = channel;
		}

		public void setConnectionID(String id) {
			getClient().connectionID = id;
		}

		public void handlePackets(Socket sock, Consumer<PkData> handler, boolean allowBroadcast) {
			NexusClient client = getClient();
			client.socket = sock;
			client.lastPing = System.currentTimeMillis();

			// Handle packets
			Thread th = new Thread(() -> {
				while (client.isConnected()) {
					try {
						// Read packet
						int type = sock.getInputStream().read();
						if (type == -1) {
							throw new IOException("Closed socket");
						}
						PacketType packetType = PacketType.fromValue((byte) type);
						if (packetType == PacketType.PING && (System.currentTimeMillis() - lastPing > 1000)) {
							lastPing = System.currentTimeMillis();

							// Send ping
							PkData ping = new PkData();
							ping.type = PacketType.PING;
							try {
								sendPacketInternal(ping, false);
							} catch (IOException e) {
								// Call disconnect
								if (!disconnecting && socket != null)
									onDisconnect(connectionID);
								socket = null;
								disconnecting = false;
								break;
							}
						} else if (packetType != PacketType.PING) {
							// Decode packet
							PkData packet = new PkData();
							packet.type = packetType;

							// Check type
							if (packetType == PacketType.DATA) {
								boolean hasTarget = sock.getInputStream().read() == 1;
								if (hasTarget)
									packet.target = readString(sock.getInputStream());
								packet.id = readInt(sock.getInputStream());
								packet.payload = readBytes(sock.getInputStream());
							}
							packet.source = readString(sock.getInputStream());
							if (channel.equals("*"))
								packet.channel = readString(sock.getInputStream());

							// Handle packet
							if (handler != null)
								handler.accept(packet);
							else {
								// Check type
								if (packetType == PacketType.CONNECT) {
									onConnect(packet.source);
								} else if (packetType == PacketType.DISCONNECT) {
									onDisconnect(packet.source);
								} else if (packetType == PacketType.DATA) {
									// Data packet
									PacketEvent[] events;
									if (packet.target != null) {
										while (true) {
											try {
												events = client.directPacketEvents.toArray(t -> new PacketEvent[t]);
												break;
											} catch (ConcurrentModificationException e) {
											}
										}
									} else {
										while (true) {
											try {
												events = client.packetEvents.toArray(t -> new PacketEvent[t]);
												break;
											} catch (ConcurrentModificationException e) {
											}
										}
									}
									for (PacketEvent ev : events) {
										Packet pk = new Packet(packet);
										ev.handle(pk);
									}
								}
							}
						}
					} catch (IOException e) {
						// Call disconnect
						if (!disconnecting && socket != null)
							onDisconnect(connectionID);
						socket = null;
						disconnecting = false;
						break;
					}
				}
			}, "Nexus Input Thread");
			th.setDaemon(true);
			th.start();

			// Ping thread
			th = new Thread(() -> {
				while (client.isConnected()) {
					long cTime = System.currentTimeMillis();
					long timeSincePing = cTime - client.lastPing;
					if (timeSincePing >= (10 * 1000)) {
						// Send ping
						PkData ping = new PkData();
						ping.type = PacketType.PING;
						try {
							sendPacketInternal(ping, false);
						} catch (IOException e) {
							// Call disconnect
							if (!disconnecting && socket != null)
								onDisconnect(connectionID);
							socket = null;
							disconnecting = false;
							break;
						}

						// Wait for ping
						int i = 0;
						while (isConnected()) {
							cTime = System.currentTimeMillis();
							timeSincePing = cTime - client.lastPing;
							if (timeSincePing < (10 * 1000)) {
								break;
							}
							if (timeSincePing >= (30 * 1000)) {
								break;
							}

							i++;
							if (i == 10) {
								// Send ping
								ping = new PkData();
								ping.type = PacketType.PING;
								try {
									sendPacketInternal(ping, false);
								} catch (IOException e) {
									// Call disconnect
									if (!disconnecting && socket != null)
										onDisconnect(connectionID);
									socket = null;
									disconnecting = false;
									break;
								}
							}
							try {
								Thread.sleep(100);
							} catch (InterruptedException e) {
							}
						}
						if (timeSincePing >= (30 * 1000)) {
							// Call disconnect
							if (!disconnecting && socket != null)
								onDisconnect(connectionID);
							try {
								socket.close();
							} catch (Exception e) {
							}
							socket = null;
							disconnecting = false;
							break;
						}
					}
				}
			}, "Nexus Ping Thread");
			th.setDaemon(true);
			th.start();
		}
	}

	static NexusClientHooks create() {
		return new NexusClient().createHooks();
	}

	private NexusClientHooks createHooks() {
		return new NexusClientHooks();
	}

	static enum PacketType {
		DATA(0), DISCONNECT(1), CONNECT(2), PING(3), SWITCHCHANNEL(4);

		byte val;

		PacketType(int val) {
			this.val = (byte) val;
		}

		public byte getValue() {
			return val;
		}

		public static PacketType fromValue(byte value) {
			for (PacketType type : values())
				if (type.val == value)
					return type;
			return null;
		}
	}

	/**
	 * Packet Event Interface
	 */
	public static interface PacketEvent {
		public void handle(Packet packet);
	}

	/**
	 * Connection Event Interface
	 */
	public static interface ConnectionEvent {
		public void handle(String connectionID);
	}

	/**
	 * Internal packet info
	 */
	static class PkData {
		public int id;
		public PacketType type;
		public byte[] payload;
		public String target = null;
		public String source = null;
		public String channel = null;
	}

	/**
	 * 
	 * Nexus Packet Type
	 * 
	 * @author Sky Swimmer
	 *
	 */
	public class Packet {
		private PkData data;

		Packet(PkData data) {
			this.data = data;
		}

		PkData getRaw() {
			return data;
		}

		/**
		 * Retrieves the sender ID
		 * 
		 * @return Sender ID string
		 */
		public String getSender() {
			return data.source;
		}

		/**
		 * Sends a packet back to the sender
		 * 
		 * @param id     Packet ID
		 * @param packet Packet bytes to send
		 * @throws IOException If the connection is closed while sending
		 */
		public void reply(int id, byte[] packet) throws IOException {
			if (!isConnected())
				throw new IOException("Stream closed");
			PkData d = new PkData();
			d.id = id;
			d.payload = packet;
			d.type = PacketType.DATA;
			d.target = data.source;
			d.channel = getRaw().channel;
			sendPacketInternal(d, false);
		}

		/**
		 * Retrieves the packet payload
		 * 
		 * @return Packet payload bytes
		 */
		public byte[] getPayload() {
			return data.payload;
		}

		/**
		 * Retrieves the packet id
		 * 
		 * @return Packet id
		 */
		public int getId() {
			return data.id;
		}
	}

	/**
	 * Adds a connection event handler
	 * 
	 * @param handler Handler to add
	 */
	public void addConnectionEventHandler(ConnectionEvent handler) {
		connectedEvent.add(handler);
	}

	/**
	 * Removes a connection event handler
	 * 
	 * @param handler Handler to remove
	 */
	public void removeConnectionEventHandler(ConnectionEvent handler) {
		if (connectedEvent.contains(handler))
			connectedEvent.remove(handler);
	}

	/**
	 * Adds a disconnection event handler
	 * 
	 * @param handler Handler to add
	 */
	public void addDisconnectionEventHandler(ConnectionEvent handler) {
		disconnectedEvent.add(handler);
	}

	/**
	 * Removes a disconnection event handler
	 * 
	 * @param handler Handler to remove
	 */
	public void removeDisconnectionEventHandler(ConnectionEvent handler) {
		if (disconnectedEvent.contains(handler))
			disconnectedEvent.remove(handler);
	}

	/**
	 * Adds a packet event handler
	 * 
	 * @param handler Handler to add
	 */
	public void addPacketEventHandler(PacketEvent handler) {
		packetEvents.add(handler);
	}

	/**
	 * Adds a direct packet event handler
	 * 
	 * @param handler Handler to add
	 */
	public void addDirectPacketEventHandler(PacketEvent handler) {
		directPacketEvents.add(handler);
	}

	/**
	 * Checks if the client is connected
	 * 
	 * @return True if connected, false otherwise
	 */
	public boolean isConnected() {
		return socket != null && socket.isConnected() && !disconnecting;
	}

	/**
	 * Retrieves the current connection's ID
	 * 
	 * @return Connection ID string
	 */
	public String getConnectionID() {
		return connectionID;
	}

	/**
	 * Retrieves the connection channel
	 * 
	 * @return Connection channel ID
	 */
	public String getChannel() {
		return channel;
	}

	/**
	 * Switches channels
	 * 
	 * @param channel New channel ID
	 * @throws IOException If sending fails
	 */
	public void switchChannel(String channel) throws IOException {
		if (!isConnected())
			throw new IOException("Stream closed");
		if (channel.equals("*"))
			throw new IllegalArgumentException("Illegal channel ID");
		PkData d = new PkData();
		d.source = channel;
		d.type = PacketType.SWITCHCHANNEL;
		sendPacketInternal(d, false);
		this.channel = channel;
	}

	/**
	 * Sends a packet
	 * 
	 * @param id      Packet ID
	 * @param payload Packet payload
	 * @throws IOException If sending fails
	 */
	public void sendPacket(int id, byte[] payload) throws IOException {
		if (!isConnected())
			throw new IOException("Stream closed");
		PkData d = new PkData();
		d.id = id;
		d.payload = payload;
		d.type = PacketType.DATA;
		sendPacketInternal(d, false);
	}

	/**
	 * Stops the client
	 */
	public void stop() {
		if (!isConnected())
			throw new IllegalStateException("Not connected");
		disconnecting = true;

		// Send disconnect
		PkData disconnect = new PkData();
		disconnect.type = PacketType.DISCONNECT;
		try {
			sendPacketInternal(disconnect, true);
		} catch (IOException e) {
		}

		// Fire events
		onDisconnect(connectionID);

		// Disconnect
		try {
			socket.close();
		} catch (Exception e) {
		}
		socket = null;
		disconnecting = false;
	}

	/**
	 * Checks if the client is disconnecting through stop()
	 * 
	 * @return True if disconnecting, false otherwise
	 */
	public boolean isDisconnecting() {
		return disconnecting;
	}

	private void onDisconnect(String id) {
		// Get events
		ConnectionEvent[] events;
		while (true) {
			try {
				events = disconnectedEvent.toArray(t -> new ConnectionEvent[t]);
				break;
			} catch (ConcurrentModificationException e) {
			}
		}

		// Run events
		for (ConnectionEvent ev : events) {
			ev.handle(id);
		}
	}

	private void onConnect(String id) {
		// Get events
		ConnectionEvent[] events;
		while (true) {
			try {
				events = connectedEvent.toArray(t -> new ConnectionEvent[t]);
				break;
			} catch (ConcurrentModificationException e) {
			}
		}

		// Run events
		for (ConnectionEvent ev : events) {
			ev.handle(id);
		}
	}

	private void sendPacketInternal(PkData packet, boolean ignoreDisconnecting) throws IOException {
		while (sending) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
			}
			if (socket == null || !socket.isConnected())
				throw new IOException("Stream closed");
			if (!ignoreDisconnecting && disconnecting)
				throw new IOException("Stream closed");
		}
		sending = true;
		if (packet.source == null)
			packet.source = connectionID;
		if (packet.channel == null)
			packet.channel = channel;

		// Write packet
		try {
			socket.getOutputStream().write(packet.type.val);
			if (packet.type != PacketType.PING) {
				if (packet.type == PacketType.DATA) {
					socket.getOutputStream().write(packet.target == null ? 0 : 1);
					if (packet.target != null) {
						writeString(packet.target, socket.getOutputStream());
					}
					writeInt(packet.id, socket.getOutputStream());
					writeBytes(packet.payload, socket.getOutputStream());
				}
				writeString(packet.source, socket.getOutputStream());
				if (channel.equals("*")) {
					writeString(packet.channel, socket.getOutputStream());
				}
			}
		} catch (Exception e) {
			sending = false;

			// Call disconnect
			boolean closing = socket == null;
			if (!disconnecting && socket != null)
				onDisconnect(connectionID);
			socket = null;
			disconnecting = false;

			if (!closing)
				throw e;
		}

		sending = false;
	}

	static void writeString(String str, OutputStream target) throws IOException {
		writeBytes(str.getBytes("UTF-8"), target);
	}

	static void writeBytes(byte[] bytes, OutputStream target) throws IOException {
		writeInt(bytes.length, target);
		target.write(bytes);
	}

	static void writeInt(int i, OutputStream target) throws IOException {
		target.write(ByteBuffer.allocate(4).putInt(i).array());
	}

	static int readInt(InputStream strm) throws IOException {
		return ByteBuffer.wrap(strm.readNBytes(4)).getInt();
	}

	static byte[] readBytes(InputStream strm) throws IOException {
		return strm.readNBytes(readInt(strm));
	}

	static String readString(InputStream strm) throws IOException {
		return new String(readBytes(strm), "UTF-8");
	}

	void sendDisconnected(String connID) {
		if (!isConnected())
			return;
		PkData d = new PkData();
		d.source = connID;
		d.type = PacketType.DISCONNECT;
		try {
			sendPacketInternal(d, false);
		} catch (IOException e) {
		}
	}

	void sendConnected(String connID) {
		if (!isConnected())
			return;
		PkData d = new PkData();
		d.source = connID;
		d.type = PacketType.CONNECT;
		try {
			sendPacketInternal(d, false);
		} catch (IOException e) {
		}
	}

	public void sendPacket(PkData packet) throws IOException {
		if (!isConnected())
			throw new IOException("Stream closed");
		sendPacketInternal(packet, false);
	}
}
