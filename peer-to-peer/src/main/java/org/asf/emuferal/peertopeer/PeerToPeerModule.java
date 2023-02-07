package org.asf.emuferal.peertopeer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;

import org.asf.centuria.Centuria;
import org.asf.centuria.entities.players.Player;
import org.asf.centuria.modules.ICenturiaModule;
import org.asf.centuria.modules.eventbus.EventListener;
import org.asf.centuria.modules.events.players.PlayerJoinEvent;
import org.asf.centuria.modules.events.players.PlayerLeaveEvent;
import org.asf.centuria.modules.events.servers.APIServerStartupEvent;
import org.asf.centuria.networking.chatserver.ChatServer;
import org.asf.centuria.networking.gameserver.GameServer;
import org.asf.centuria.packets.smartfox.ISmartfoxPacket;
import org.asf.centuria.packets.xt.gameserver.inventory.InventoryItemDownloadPacket;
import org.asf.centuria.packets.xt.gameserver.object.ObjectDeletePacket;
import org.asf.centuria.packets.xt.gameserver.room.RoomJoinPacket;
import org.asf.emuferal.peertopeer.accounts.PeerToPeerAccountManager;
import org.asf.emuferal.peertopeer.apioverride.SocialSystemOverride;
import org.asf.emuferal.peertopeer.io.PacketReader;
import org.asf.emuferal.peertopeer.p2pplayerinfo.P2PSmartfoxClient;
import org.asf.emuferal.peertopeer.p2pserver.PeerToPeerChatServer;
import org.asf.emuferal.peertopeer.p2pserver.PeerToPeerGameServer;
import org.asf.emuferal.peertopeer.packets.NexusPacket;
import org.asf.emuferal.peertopeer.packets.P2PNexusPacketType;
import org.asf.emuferal.peertopeer.packets.impl.ChatPacket;
import org.asf.emuferal.peertopeer.packets.impl.InventoryItemRequestPacket;
import org.asf.emuferal.peertopeer.packets.impl.InventoryItemUpdatePacket;
import org.asf.emuferal.peertopeer.packets.impl.JoinRoomPacket;
import org.asf.emuferal.peertopeer.packets.impl.LevelUpdatePacket;
import org.asf.emuferal.peertopeer.packets.impl.PlayerJoinPacket;
import org.asf.emuferal.peertopeer.packets.impl.PlayerLeavePacket;
import org.asf.emuferal.peertopeer.packets.impl.PlayerLookInfoPacket;
import org.asf.emuferal.peertopeer.packets.impl.PlayerObjectCreatePacket;
import org.asf.emuferal.peertopeer.packets.impl.PlayerObjectRemovePacket;
import org.asf.emuferal.peertopeer.packets.impl.PlayerObjectUpdatePacket;
import org.asf.emuferal.peertopeer.packets.impl.PlayerSanctuarySelectPacket;
import org.asf.emuferal.peertopeer.packets.impl.RequestAllPlayersPacket;
import org.asf.emuferal.peertopeer.players.P2PPlayer;
import org.asf.nexus.NexusClient;
import org.asf.nexus.NexusClient.Packet;

public class PeerToPeerModule implements ICenturiaModule {

	private static ArrayList<P2PPlayer> players = new ArrayList<P2PPlayer>();
	private static ArrayList<NexusPacket> packets = new ArrayList<NexusPacket>();
	public static ArrayList<Runnable> listUpdateEvents = new ArrayList<Runnable>();
	public static NexusClient connector;

	@Override
	public String id() {
		return "peer-to-peer";
	}

	@Override
	public String version() {
		return "1.0.0.A1";
	}

	@Override
	public void init() {
		// Main init method
		PeerToPeerWindow win = new PeerToPeerWindow();
		win.start();

		// Register packets
		packets.add(new RequestAllPlayersPacket());
		packets.add(new PlayerJoinPacket());
		packets.add(new PlayerObjectRemovePacket());
		packets.add(new PlayerLeavePacket());
		packets.add(new PlayerLookInfoPacket());
		packets.add(new PlayerObjectCreatePacket());
		packets.add(new PlayerObjectUpdatePacket());
		packets.add(new PlayerSanctuarySelectPacket());
		packets.add(new InventoryItemUpdatePacket());
		packets.add(new InventoryItemRequestPacket());
		packets.add(new JoinRoomPacket());
		packets.add(new LevelUpdatePacket());
		packets.add(new ChatPacket());
	}

	@Override
	public void postInit() {
		// Assign account manager
		new PeerToPeerAccountManager().assign();
	}

	@Override
	public GameServer replaceGameServer(ServerSocket sock) {
		return new PeerToPeerGameServer(sock);
	}

	@Override
	public ChatServer replaceChatServer(ServerSocket sock) {
		return new PeerToPeerChatServer(sock);
	}

	//
	// API
	//

	/**
	 * Retrieves all peer-to-peer players
	 * 
	 * @return Array of P2PPlayer instances
	 */
	public static P2PPlayer[] getPlayers() {
		while (true) {
			try {
				return players.toArray(t -> new P2PPlayer[t]);
			} catch (ConcurrentModificationException e) {
			}
		}
	}

	/**
	 * Retrieves players by id
	 * 
	 * @param id Player id
	 * @return P2PPlayer instance or null
	 */
	public static P2PPlayer getPlayer(String id) {
		for (P2PPlayer plr : getPlayers()) {
			if (plr.id.equals(id))
				return plr;
		}
		return null;
	}

	/**
	 * Retrieves players by name
	 * 
	 * @param name Player name
	 * @return P2PPlayer instance or null
	 */
	public static P2PPlayer getByDisplayName(String name) {
		for (P2PPlayer plr : getPlayers()) {
			if (plr.displayName.equals(name))
				return plr;
		}
		return null;
	}

	/**
	 * Broadcasts a packet
	 * 
	 * @param packet Smartfox packet to broadcast
	 */
	public static void broadcast(ISmartfoxPacket packet) {
		if (Centuria.gameServer == null)
			return;
		for (Player plr : Centuria.gameServer.getPlayers()) {
			if (!(plr.client instanceof P2PSmartfoxClient))
				plr.client.sendPacket(packet);
		}
	}

	/**
	 * Broadcasts a packet
	 * 
	 * @param packet Smartfox packet to broadcast
	 * @param room   Room the player is in
	 */
	public static void broadcast(ISmartfoxPacket packet, String room) {
		if (Centuria.gameServer == null)
			return;
		for (Player plr : Centuria.gameServer.getPlayers()) {
			if (plr.room != null && plr.room.equals(room))
				if (!(plr.client instanceof P2PSmartfoxClient))
					plr.client.sendPacket(packet);
		}
	}

	/**
	 * Broadcasts a packet
	 * 
	 * @param packet Smartfox packet to broadcast
	 * @param room   Room the player is in
	 * @param sender Packet sender
	 */
	public static void broadcast(ISmartfoxPacket packet, String room, String sender) {
		if (Centuria.gameServer == null)
			return;
		for (Player plr : Centuria.gameServer.getPlayers()) {
			if (plr.room != null && plr.room.equals(room) && !plr.account.getAccountID().equals(sender))
				if (!(plr.client instanceof P2PSmartfoxClient))
					plr.client.sendPacket(packet);
		}
	}

	/**
	 * Sends a nexus packet
	 * 
	 * @param id     Packet ID
	 * @param packet Packet bytes
	 */
	public static void sendNexusPacket(P2PNexusPacketType id, byte[] packet) {
		if (connector != null && connector.isConnected()) {
			try {
				connector.sendPacket(id.ordinal(), packet);
			} catch (IOException e) {
			}
		}
	}

	/**
	 * Sends a nexus packet
	 * 
	 * @param packet Packet to send
	 */
	public static void sendNexusPacket(NexusPacket packet) {
		sendNexusPacket(packet.type(), packet.build());
	}

	/**
	 * Adds a player
	 * 
	 * @param plr Player to add
	 */
	public static void addPlayer(P2PPlayer plr) {
		// Add player
		players.add(plr);

		// Check local
		if (!plr.isLocal) {
			// Its not, get the P2P game server and create a client
			PeerToPeerGameServer srv = (PeerToPeerGameServer) Centuria.gameServer;
			srv.playerJoin(plr);
		}

		// Call update events
		Runnable[] events;
		while (true) {
			try {
				events = listUpdateEvents.toArray(t -> new Runnable[t]);
				break;
			} catch (ConcurrentModificationException e) {
			}
		}
		for (Runnable r : events)
			r.run();
	}

	/**
	 * Removes a player
	 * 
	 * @param player Player to remove
	 */
	public static void removePlayer(P2PPlayer player) {
		// Add player
		players.remove(player);

		// Check local
		if (!player.isLocal) {
			// Its not, get the P2P game server and remove client
			PeerToPeerGameServer srv = (PeerToPeerGameServer) Centuria.gameServer;
			srv.playerLeave(player);

			// Remove players from their sanctuary
			for (Player plr : srv.getPlayers()) {
				if (!(plr.client instanceof P2PSmartfoxClient)) {
					if (plr.levelType == 2 && plr.room.equals("sanctuary_" + player.id)) {
						RoomJoinPacket packet = new RoomJoinPacket();
						packet.levelID = 820;
						new Thread(() -> {
							try {
								Centuria.systemMessage(plr, player.displayName
										+ " has left the server.\nYou will be moved to city fera as their sanctuary no longer exists.",
										true);
								Thread.sleep(5000);
								packet.handle(plr.client);
							} catch (IOException | InterruptedException e) {
							}
						}).start();
					}
				}
			}
		}

		// Remove from client
		broadcast(new ObjectDeletePacket(player.id));

		// Call update events
		Runnable[] events;
		while (true) {
			try {
				events = listUpdateEvents.toArray(t -> new Runnable[t]);
				break;
			} catch (ConcurrentModificationException e) {
			}
		}
		for (Runnable r : events)
			r.run();
	}

	//
	// Events
	//

	public static void handleNexusPacket(Packet packet) {
		if (packet.getId() <= P2PNexusPacketType.values().length) {
			P2PNexusPacketType type = P2PNexusPacketType.values()[packet.getId()];
			ByteArrayInputStream strm = new ByteArrayInputStream(packet.getPayload());
			PacketReader reader = new PacketReader(strm);

			// Find packet
			for (NexusPacket pkt : packets) {
				if (pkt.type() == type) {
					NexusPacket pk = pkt.newInstance();
					try {
						pk.parse(reader);
						pk.handle(packet);
					} catch (Exception e) {
					}
				}
			}
		}
	}

	public static void onConnect() {
		// Send local players over the nexus
		for (P2PPlayer plr : getPlayers()) {
			if (!plr.isLocal)
				continue;

			// Create packet
			PlayerJoinPacket packet = new PlayerJoinPacket();
			packet.id = plr.id;
			packet.name = plr.displayName;
			packet.currentLookID = plr.currentLook;
			packet.currentSanctuaryLookID = plr.currentSanctuaryLook;
			sendNexusPacket(packet);

			// Send look if info is present
			if (plr.look != null) {
				PlayerLookInfoPacket pkt = new PlayerLookInfoPacket();
				pkt.id = plr.id;
				pkt.avatar = plr.look;
				pkt.currentLookID = plr.currentLook;
				sendNexusPacket(pkt);
			}

			// Sync position
			if (plr.position != null) {
				PlayerObjectCreatePacket sync = new PlayerObjectCreatePacket();
				sync.id = plr.id;
				sync.action = plr.action;
				sync.room = plr.room;
				sync.position = plr.position;
				sync.rotation = plr.rotation;
				sendNexusPacket(sync);
			}
		}

		// Send player request
		sendNexusPacket(new RequestAllPlayersPacket());
	}

	public static void onDisconnect() {
		// Remove all
		ArrayList<String> clientsRemoved = new ArrayList<String>();
		for (P2PPlayer plr : getPlayers()) {
			if (plr.isLocal)
				continue;
			if (clientsRemoved.contains(plr.clientID))
				continue;
			onDisconnect(plr.clientID);
			clientsRemoved.add(plr.clientID);
		}
	}

	public static void onDisconnect(String id) {
		// Remove players
		for (P2PPlayer player : getPlayers()) {
			if (player.isLocal || !player.clientID.equals(id))
				continue;

			// Remove
			players.remove(player);

			// Check local
			if (!player.isLocal) {
				// Its not, get the P2P game server and remove client
				PeerToPeerGameServer srv = (PeerToPeerGameServer) Centuria.gameServer;
				srv.playerLeave(player);

				// Remove players from their sanctuary
				for (Player plr : srv.getPlayers()) {
					if (!(plr.client instanceof P2PSmartfoxClient)) {
						if (plr.levelType == 2 && plr.room.equals("sanctuary_" + player.id)) {
							RoomJoinPacket packet = new RoomJoinPacket();
							packet.levelID = 820;
							new Thread(() -> {
								try {
									Centuria.systemMessage(plr, player.displayName
											+ " has left the server.\nYou will be moved to city fera as their sanctuary no longer exists.",
											true);
									Thread.sleep(5000);
									packet.handle(plr.client);
								} catch (IOException | InterruptedException e) {
								}
							}).start();
						}
					}
				}
			}

			// Send removed packet
			broadcast(new ObjectDeletePacket(player.id));
		}

		// Call update events
		Runnable[] events;
		while (true) {
			try {
				events = listUpdateEvents.toArray(t -> new Runnable[t]);
				break;
			} catch (ConcurrentModificationException e) {
			}
		}
		for (Runnable r : events)
			r.run();
	}

	@EventListener
	public void joined(PlayerJoinEvent event) {
		if (event.getClient() instanceof P2PSmartfoxClient)
			return;
		// Setup P2P player info
		P2PPlayer plr = new P2PPlayer();
		plr.player = event.getPlayer();
		plr.id = event.getAccount().getAccountID();
		plr.displayName = event.getAccount().getDisplayName();
		plr.isLocal = true;
		if (plr.player.account.getSaveSpecificInventory().getSanctuaryAccessor()
				.getSanctuaryLook(plr.player.account.getActiveSanctuaryLook()) == null) {
			if (plr.player.account.getSaveSpecificInventory().getSanctuaryAccessor().getFirstSanctuaryLook() == null) {
				// Simulate ILT
				InventoryItemDownloadPacket pkt = new InventoryItemDownloadPacket();
				try {
					pkt.parse("%xt%ilt%201%");
					pkt.handle(plr.player.client);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			plr.player.activeSanctuaryLook = plr.player.account.getSaveSpecificInventory().getSanctuaryAccessor()
					.getFirstSanctuaryLook().get("id").getAsString();
			plr.player.account.setActiveSanctuaryLook(plr.player.activeSanctuaryLook);
		} else
			plr.player.activeSanctuaryLook = plr.player.account.getActiveSanctuaryLook();
		plr.currentLook = event.getPlayer().activeLook;
		plr.currentSanctuaryLook = event.getPlayer().activeSanctuaryLook;
		addPlayer(plr);

		// Send player join
		PlayerJoinPacket packet = new PlayerJoinPacket();
		packet.id = plr.id;
		packet.name = plr.displayName;
		packet.currentSanctuaryLookID = plr.currentSanctuaryLook;
		packet.currentLookID = plr.currentLook;
		sendNexusPacket(packet);
	}

	@EventListener
	public void left(PlayerLeaveEvent event) {
		if (event.getClient() instanceof P2PSmartfoxClient)
			return;
		// Remove P2P player info
		P2PPlayer plr = getPlayer(event.getAccount().getAccountID());
		if (plr != null)
			players.remove(plr);

		// Call update events
		Runnable[] events;
		while (true) {
			try {
				events = listUpdateEvents.toArray(t -> new Runnable[t]);
				break;
			} catch (ConcurrentModificationException e) {
			}
		}
		for (Runnable r : events)
			r.run();

		// Send player leave
		PlayerLeavePacket packet = new PlayerLeavePacket();
		packet.id = plr.id;
		sendNexusPacket(packet);
	}

	@EventListener
	public void apiSetup(APIServerStartupEvent event) {
		event.getServer().registerProcessor(new SocialSystemOverride());
	}
}
