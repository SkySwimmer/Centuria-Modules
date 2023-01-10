package org.asf.nexus;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URL;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.Enumeration;

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
	private boolean tryMulticastScan = true;

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
	 * Disables the lan scan for nexus servers
	 * 
	 * @return Current builder
	 */
	public NexusClientBuilder noMulticastScan() {
		tryMulticastScan = false;
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
	 * Assigns the server address (implies noMulticastScan and noServerListScan)
	 * 
	 * @param address Nexus server address
	 * @return Current builder
	 */
	public NexusClientBuilder withAddress(String address) {
		this.address = address;
		tryMulticastScan = false;
		tryServerList = false;
		return this;
	}

	/**
	 * Assigns the server port (implies noMulticastScan and noServerListScan)
	 * 
	 * @param port Nexus server port
	 * @return Current builder
	 */
	public NexusClientBuilder withPort(int port) {
		this.port = port;
		tryMulticastScan = false;
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
		private String reachableAddress = null;
		public String[] addresses = null;
		public int port = 0;

		public String findAddress() {
			if (reachableAddress != null)
				return reachableAddress;
			for (String addr : addresses) {
				try {
					Socket sock = SSLSocketFactory.getDefault().createSocket();
					sock.connect(new InetSocketAddress(addr, port), 1000);
					if (!sock.isConnected()) {
						sock.close();
						reachableAddress = addr;
						return addr;
					}
					sock.close();
				} catch (IOException e) {
					break;
				}
			}
			return null;
		}

		public int scanProtocolVersion() {
			// Try partial handshake

			// Ping
			if (ping() == -1)
				return -1;

			// Contact server
			try {
				Socket sock = SSLSocketFactory.getDefault().createSocket();
				sock.connect(new InetSocketAddress(reachableAddress, port), 1000);
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

			for (String addr : addresses) {
				try {
					long pingStart = System.currentTimeMillis();
					Socket sock = SSLSocketFactory.getDefault().createSocket();
					sock.connect(new InetSocketAddress(addr, port), 1000);
					if (sock.isConnected()) {
						sock.close();
						long cping = System.currentTimeMillis() - pingStart;
						if (cping < ping || ping == -1) {
							ping = cping;
							reachableAddress = addr;
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
					break;
				}
			}

			return ping;
		}
	}

	private void connect(NexusClient.NexusClientHooks hooks) throws IOException {
		NexusClient client = hooks.getClient();

		boolean foundServer = false;
		ServerAddress addr = new ServerAddress();

		// Try multicast
		if (tryMulticastScan) {
			// Multicast detector
			try {
				ArrayList<String> msgs = new ArrayList<String>();
				ArrayList<NexusInstance> instances = new ArrayList<NexusInstance>();

				InetSocketAddress grp = new InetSocketAddress(InetAddress.getByName("224.0.2.232"), 14524);
				MulticastSocket sock = new MulticastSocket(14524);
				sock.setSoTimeout(3000);

				Enumeration<NetworkInterface> interfaces_enumeration = NetworkInterface.getNetworkInterfaces();
				ArrayList<NetworkInterface> interfaces = new ArrayList<NetworkInterface>();
				while (interfaces_enumeration.hasMoreElements()) {
					interfaces.add(interfaces_enumeration.nextElement());
				}

				for (NetworkInterface i : interfaces)
					try {
						sock.joinGroup(grp, i);
					} catch (Exception e) {
					}

				for (int i = 0; i < 5; i++) {
					byte[] buf = new byte[10 * 1024];
					try {
						DatagramPacket pkt = new DatagramPacket(buf, buf.length);
						sock.receive(pkt);

						String message = new String(pkt.getData(), 0, pkt.getLength(), "UTF-8");
						if (!msgs.contains(message)) {
							msgs.add(message);
							i = 0;

							if (message.split("\n").length == 3) {
								String addrs = message.split("\n")[0];
								String port = message.split("\n")[1];
								String cid = message.split("\n")[2];

								if (client.getConnectionID() != null && !cid.equals(client.getConnectionID()))
									continue;

								if (port.matches("^[0-9]+$")) {
									msgs.add(message);
									NexusInstance inst = new NexusInstance();
									inst.port = Integer.parseInt(port);
									inst.addresses = addrs.split("\0");
									if (inst.scanProtocolVersion() == NexusClient.PROTOCOL_VERSION)
										instances.add(inst);
								}
							}
						}
					} catch (IOException | BufferUnderflowException e) {
						break;
					}
					try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
					}
				}

				for (NetworkInterface i : interfaces)
					try {
						sock.leaveGroup(grp, i);
					} catch (Exception e) {
					}
				sock.close();

				instances.sort((t1, t2) -> Long.compare(t1.ping(), t2.ping()));
				for (NexusInstance inst : instances) {
					long ping = inst.ping();
					if (ping != -1) {
						addr.address = inst.findAddress();
						addr.port = inst.port;
						foundServer = true;
						break;
					}
				}
			} catch (IOException e) {
			}
		}

		// Server list scan
		if ((!foundServer || !tryMulticastScan) && tryServerList) {
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
						inst.addresses = new String[] { serverAddr };
						if (inst.scanProtocolVersion() == NexusClient.PROTOCOL_VERSION)
							instances.add(inst);
					}
				}

				instances.sort((t1, t2) -> Long.compare(t1.ping(), t2.ping()));
				for (NexusInstance inst : instances) {
					long ping = inst.ping();
					if (ping != -1) {
						addr.address = inst.findAddress();
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
