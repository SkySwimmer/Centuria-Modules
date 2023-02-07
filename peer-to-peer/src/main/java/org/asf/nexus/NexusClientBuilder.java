package org.asf.nexus;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import javax.net.ssl.SSLSocketFactory;

/**
 * 
 * Nexus Client Builder
 * 
 * @author Sky Swimmer
 *
 */
public class NexusClientBuilder {

	private String channel;

	private String address = "localhost";
	private int port = 14524;

	private boolean connectAsync;
	private boolean autoReconnect;

	private String serverListURL = "https://aerialworks.ddns.net/nexus/list";

	private boolean isServer = false;
	private boolean tryServerList = true;

	NexusClientBuilder serverDownlink() {
		isServer = true;
		return this;
	}

	/**
	 * Defines the Nexus Server List URL (for server detection)
	 * 
	 * @param url New server list URL
	 * @return Current builder
	 */
	public NexusClientBuilder withListURL(String url) {
		serverListURL = url;
		return this;
	}

	/**
	 * Enables automatic reconnecting
	 * 
	 * @return Current builder
	 */
	public NexusClientBuilder autoReconnect() {
		connectAsync = true;
		autoReconnect = true;
		return this;
	}

	/**
	 * Disables the server list scan for nexus servers
	 * 
	 * @return Current builder
	 */
	public NexusClientBuilder noServerListScan() {
		tryServerList = false;
		return this;
	}

	/**
	 * Assigns the server address (implies noServerListScan)
	 * 
	 * @param address Nexus server address
	 * @return Current builder
	 */
	public NexusClientBuilder withAddress(String address) {
		this.address = address;
		tryServerList = false;
		return this;
	}

	/**
	 * Assigns the server port (implies noServerListScan)
	 * 
	 * @param port Nexus server port
	 * @return Current builder
	 */
	public NexusClientBuilder withPort(int port) {
		this.port = port;
		tryServerList = false;
		return this;
	}

	/**
	 * Assigns the nexus channel
	 * 
	 * @param channel Nexus channel
	 * @return Current builder
	 */
	public NexusClientBuilder withChannel(String channel) {
		if (!isServer && channel.equalsIgnoreCase("*"))
			throw new IllegalArgumentException("Illegal channel");
		this.channel = channel;
		return this;
	}

	/**
	 * Builds and starts the nexus client
	 * 
	 * @return New NexusClient instance
	 */
	public NexusClient build() throws IOException {
		if (channel == null)
			throw new IllegalArgumentException("No channel assigned");
		NexusClient.NexusClientHooks hooks = NexusClient.create();

		// Attempt to connect
		hooks.setChannel(channel);
		if (!this.connectAsync)
			connect(hooks);
		else {
			Thread th = new Thread(() -> {
				while (true) {
					try {
						connect(hooks);

						// Auto reconnect
						if (autoReconnect) {
							hooks.getClient().addDisconnectionEventHandler(t -> {
								if (t.equals(hooks.getClient().getConnectionID())
										&& !hooks.getClient().isDisconnecting()) {
									// Reconnect
									Thread th2 = new Thread(() -> {
										while (true) {
											try {
												Thread.sleep(30000);
											} catch (InterruptedException e1) {
											}
											try {
												connect(hooks);
												break;
											} catch (IOException e) {
											}
										}
									}, "Nexus Connector");
									th2.setDaemon(true);
									th2.start();
								}
							});
						}

						break;
					} catch (IOException e) {
						if (!autoReconnect)
							return;
						try {
							Thread.sleep(30000);
						} catch (InterruptedException e1) {
						}
					}
				}
			}, "Nexus Connector");
			th.setDaemon(true);
			th.start();
		}

		return hooks.getClient();
	}

	private class ServerAddress {
		public String address = null;
		public int port = -1;
	}

	private static class NexusInstance {
		public String address = null;
		public int port = 0;

		public int scanProtocolVersion() {
			// Try partial handshake

			// Ping
			if (ping() == -1 || address == null)
				return -1;

			// Contact server
			try {
				Socket sock = SSLSocketFactory.getDefault().createSocket();
				sock.connect(new InetSocketAddress(address, port), 1000);
				if (sock.isConnected()) {
					byte[] magic = "NEXUS_LN".getBytes("UTF-8");
					sock.getOutputStream().write(magic);
					NexusClient.writeInt(NexusClient.PROTOCOL_VERSION, sock.getOutputStream());

					// Try to handshake with protocol 3
					if (sock.getInputStream().read() != 3) {
						sock.close();
						return -1;
					}
					for (int i = 0; i < magic.length; i++) {
						int b = sock.getInputStream().read();
						if (magic[i] != b) {
							// Incompatible
							sock.close();
							return -1;
						}
					}

					// Read server protocol
					int serverProtocol = NexusClient.readInt(sock.getInputStream());
					sock.close();
					return serverProtocol;
				}
				sock.close();
			} catch (IOException e) {
				return -1;
			}
			return -1;
		}

		public long ping() {
			long ping = -1;

			try {
				long pingStart = System.currentTimeMillis();
				Socket sock = SSLSocketFactory.getDefault().createSocket();
				sock.connect(new InetSocketAddress(address, port), 1000);
				if (sock.isConnected()) {
					sock.close();
					long cping = System.currentTimeMillis() - pingStart;
					if (cping < ping || ping == -1)
						ping = cping;
				}
				sock.close();
			} catch (IOException e) {
			}

			return ping;
		}
	}

	private void connect(NexusClient.NexusClientHooks hooks) throws IOException {
		NexusClient client = hooks.getClient();

		boolean foundServer = false;
		ServerAddress addr = new ServerAddress();

		// Server list scan
		if (!foundServer && tryServerList) {
			// Server list-based detector
			try {
				InputStream strm = new URL(serverListURL).openStream();
				String data = "";
				while (true) {
					int i = strm.read();
					if (i == -1)
						break;

					data += (char) (byte) i;
				}
				strm.close();

				ArrayList<NexusInstance> instances = new ArrayList<NexusInstance>();
				for (String server : data.split("\n")) {
					if (server.contains("]:")) {
						String serverAddr = server.substring(1);
						serverAddr = serverAddr.substring(0, serverAddr.lastIndexOf("]:"));
						int serverPort = Integer.valueOf(server.substring(server.lastIndexOf("]:") + 2));

						NexusInstance inst = new NexusInstance();
						inst.port = serverPort;
						inst.address = serverAddr;
						if (inst.scanProtocolVersion() == NexusClient.PROTOCOL_VERSION)
							instances.add(inst);
					}
				}

				instances.sort((t1, t2) -> Long.compare(t1.ping(), t2.ping()));
				for (NexusInstance inst : instances) {
					long ping = inst.ping();
					if (ping != -1) {
						addr.address = inst.address;
						addr.port = inst.port;
						foundServer = true;
						break;
					}
				}
			} catch (Exception e) {
			}
		}

		// Assign port and address
		String address = this.address;
		int port = this.port;
		if (foundServer) {
			address = addr.address;
			port = addr.port;
		}

		// Connect
		Socket socket = SSLSocketFactory.getDefault().createSocket(Inet4Address.getByName(address), port);
		hooks.start(socket, false);
		if (socket.getInputStream().read() != 0) {
			socket.close();
			throw new IOException("Handshake failure");
		}

		// Write channel
		NexusClient.writeString(client.getChannel(), socket.getOutputStream());

		// Read connection ID
		String connID = NexusClient.readString(socket.getInputStream());
		hooks.setConnectionID(connID);

		// Start handler
		hooks.handlePackets(socket, null, isServer);
	}

}
