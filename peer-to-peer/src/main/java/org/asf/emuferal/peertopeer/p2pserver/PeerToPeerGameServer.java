package org.asf.emuferal.peertopeer.p2pserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

import org.asf.centuria.accounts.AccountManager;
import org.asf.centuria.accounts.CenturiaAccount;
import org.asf.centuria.data.XtReader;
import org.asf.centuria.data.XtWriter;
import org.asf.centuria.entities.generic.Vector3;
import org.asf.centuria.entities.generic.Velocity;
import org.asf.centuria.entities.objects.WorldObjectMoveNodeData;
import org.asf.centuria.entities.objects.WorldObjectPositionInfo;
import org.asf.centuria.entities.players.Player;
import org.asf.centuria.enums.objects.WorldObjectMoverNodeType;
import org.asf.centuria.enums.trading.TradeValidationType;
import org.asf.centuria.networking.gameserver.GameServer;
import org.asf.centuria.networking.smartfox.SmartfoxClient;
import org.asf.centuria.packets.smartfox.ISmartfoxPacket;
import org.asf.centuria.packets.xt.gameserver.avatar.AvatarActionPacket;
import org.asf.centuria.packets.xt.gameserver.avatar.AvatarLookGetPacket;
import org.asf.centuria.packets.xt.gameserver.avatar.AvatarLookSavePacket;
import org.asf.centuria.packets.xt.gameserver.avatar.AvatarObjectInfoPacket;
import org.asf.centuria.packets.xt.gameserver.avatar.AvatarSelectLookPacket;
import org.asf.centuria.packets.xt.gameserver.object.ObjectUpdatePacket;
import org.asf.centuria.packets.xt.gameserver.room.RoomJoinPacket;
import org.asf.centuria.packets.xt.gameserver.sanctuary.SanctuaryLookLoadPacket;
import org.asf.centuria.packets.xt.gameserver.sanctuary.SanctuaryLookSavePacket;
import org.asf.centuria.packets.xt.gameserver.sanctuary.SanctuaryLookSwitchPacket;
import org.asf.centuria.packets.xt.gameserver.sanctuary.SanctuaryUpgradeCompletePacket;
import org.asf.centuria.packets.xt.gameserver.trade.TradeInitiateFailPacket;
import org.asf.centuria.packets.xt.gameserver.trade.TradeInitiatePacket;
import org.asf.centuria.packets.xt.gameserver.world.WorldReadyPacket;
import org.asf.emuferal.peertopeer.PeerToPeerModule;
import org.asf.emuferal.peertopeer.p2pplayerinfo.P2PSmartfoxClient;
import org.asf.emuferal.peertopeer.packets.impl.PlayerLookInfoPacket;
import org.asf.emuferal.peertopeer.packets.impl.PlayerObjectCreatePacket;
import org.asf.emuferal.peertopeer.packets.impl.PlayerObjectRemovePacket;
import org.asf.emuferal.peertopeer.packets.impl.PlayerObjectUpdatePacket;
import org.asf.emuferal.peertopeer.packets.impl.PlayerSanctuarySelectPacket;
import org.asf.emuferal.peertopeer.players.P2PPlayer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class PeerToPeerGameServer extends GameServer {

	public PeerToPeerGameServer(ServerSocket socket) {
		super(socket);
	}

	@Override
	public boolean handlePacket(String packet, SmartfoxClient client) throws IOException {
		ISmartfoxPacket sPacket = this.parsePacketPayload(packet, ISmartfoxPacket.class);
		boolean success = false;
		if (sPacket != null) {

			// Overrides
			if (sPacket instanceof AvatarLookGetPacket) {
				// Avatar get
				XtReader rd = new XtReader(packet);
				rd.read();
				String user = rd.read();
				P2PPlayer player = PeerToPeerModule.getPlayer(user);
				if (player != null) {
					try {
						// Send response
						XtWriter writer = new XtWriter();
						writer.writeString("alg");
						writer.writeInt(-1);
						// Compress and send look
						JsonObject look = new JsonObject();
						look.addProperty("gender", 0);
						look.add("info", JsonParser.parseString(player.look));
						ByteArrayOutputStream op = new ByteArrayOutputStream();
						GZIPOutputStream gz = new GZIPOutputStream(op);
						gz.write(look.toString().getBytes("UTF-8"));
						gz.close();
						op.close();
						writer.writeString(Base64.getEncoder().encodeToString(op.toByteArray()));
						writer.writeString(""); // data suffix
						client.sendPacket(writer.encode());
					} catch (IOException e) {
					}
					return true;
				}
			} else if (sPacket instanceof TradeInitiatePacket) {
				// Trade initiate
				TradeInitiatePacket initiate = (TradeInitiatePacket) sPacket;

				String user = initiate.inboundUserId;
				P2PPlayer player = PeerToPeerModule.getPlayer(user);
				if (player != null && !player.isLocal) {
					// Fail
					TradeInitiateFailPacket pk = new TradeInitiateFailPacket();
					pk.player = user;
					pk.tradeValidationType = TradeValidationType.User_Not_Avail;
					client.sendPacket(pk);
					return true;
				}
			}

			success = super.handlePacket(packet, client);
			onReceive(client, sPacket, packet);
		}
		return success;
	}

	private void onReceive(SmartfoxClient client, ISmartfoxPacket packet, String rawPacket) {

		// Handle game client packets
		if (packet != null) {
			if (packet instanceof WorldReadyPacket) {
				// World Ready

				// Get player
				P2PPlayer player = PeerToPeerModule.getPlayer(((Player) client.container).account.getAccountID());

				// Send destroy
				PlayerObjectRemovePacket d = new PlayerObjectRemovePacket();
				d.id = player.id;
				PeerToPeerModule.sendNexusPacket(d);

				// Assign info
				player.roomReady = true;
				player.player.roomReady = true;
				player.levelID = player.player.levelID;
				player.levelType = player.player.levelType;
				player.action = player.player.lastAction;
				player.position = player.player.lastPos;
				player.rotation = player.player.lastRot;
				player.room = player.player.room;

				// Find player look
				String look = player.player.activeLook;
				JsonObject lookData = player.player.account.getSaveSpecificInventory().getAccessor()
						.findInventoryObject("avatars", look);
				if (lookData != null) {
					player.look = lookData.get("components").getAsJsonObject().get("AvatarLook").getAsJsonObject()
							.get("info").getAsJsonObject().toString();

					// Send packet
					PlayerLookInfoPacket pkt = new PlayerLookInfoPacket();
					pkt.id = player.id;
					pkt.avatar = player.look;
					pkt.currentLookID = player.currentLook;
					PeerToPeerModule.sendNexusPacket(pkt);
				}

				// Sync others
				for (P2PPlayer plr : PeerToPeerModule.getPlayers()) {
					if (plr.room != null && plr != player && plr.room.equals(player.room)) {
						// Create object info
						AvatarObjectInfoPacket pkt = new AvatarObjectInfoPacket();

						// Object creation parameters
						pkt.id = plr.id;
						pkt.defId = 852;
						pkt.ownerId = plr.id;

						pkt.lastMove = new WorldObjectMoveNodeData();
						pkt.lastMove.serverTime = System.currentTimeMillis() / 1000;
						pkt.lastMove.positionInfo = new WorldObjectPositionInfo(plr.position.x, plr.position.y,
								plr.position.z, plr.rotation.x, plr.rotation.y, plr.rotation.z, plr.rotation.w);
						pkt.lastMove.velocity = new Velocity();
						pkt.lastMove.nodeType = WorldObjectMoverNodeType.InitPosition;
						pkt.lastMove.actorActionType = plr.action;

						// Look and name
						pkt.look = JsonParser.parseString(plr.look).getAsJsonObject();
						pkt.displayName = plr.displayName;
						pkt.unknownValue = 0;

						// Broadcast in room
						PeerToPeerModule.broadcast(pkt, player.room);
					}
				}

				// Sync coordinates
				PlayerObjectCreatePacket sync = new PlayerObjectCreatePacket();
				sync.action = player.action;
				sync.position = player.position;
				sync.rotation = player.rotation;
				sync.room = player.room;
				sync.id = player.id;
				sync.levelID = player.levelID;
				sync.levelType = player.levelType;
				PeerToPeerModule.sendNexusPacket(sync);
			} else if (packet instanceof RoomJoinPacket) {
				// Room join

				// Get player
				P2PPlayer player = PeerToPeerModule.getPlayer(((Player) client.container).account.getAccountID());

				// Send destroy
				PlayerObjectRemovePacket pkt = new PlayerObjectRemovePacket();
				pkt.id = player.id;
				PeerToPeerModule.sendNexusPacket(pkt);

				// Remove sync
				player.roomReady = false;
				player.action = 0;
			} else if (packet instanceof AvatarSelectLookPacket || packet instanceof AvatarLookSavePacket) {
				// Customize

				// Get player
				P2PPlayer player = PeerToPeerModule.getPlayer(((Player) client.container).account.getAccountID());

				// Assign info
				player.action = 0;

				// Find player look
				String look = player.player.activeLook;
				player.currentLook = player.player.activeLook;
				JsonObject lookData = player.player.account.getSaveSpecificInventory().getAccessor()
						.findInventoryObject("avatars", look).get("components").getAsJsonObject().get("AvatarLook")
						.getAsJsonObject().get("info").getAsJsonObject();
				if (lookData != null) {
					player.look = lookData.toString();

					// Send packet
					PlayerLookInfoPacket pkt = new PlayerLookInfoPacket();
					pkt.id = player.id;
					pkt.avatar = player.look;
					pkt.currentLookID = player.currentLook;
					PeerToPeerModule.sendNexusPacket(pkt);
				}

				// Sync coordinates
				PlayerObjectCreatePacket sync = new PlayerObjectCreatePacket();
				sync.action = player.action;
				sync.position = player.position;
				sync.rotation = player.rotation;
				sync.room = player.room;
				sync.id = player.id;
				PeerToPeerModule.sendNexusPacket(sync);
			} else if (packet instanceof AvatarActionPacket) {
				// Avatar action

				// Get playerx
				P2PPlayer player = PeerToPeerModule.getPlayer(((Player) client.container).account.getAccountID());
				XtReader rd = new XtReader(rawPacket);
				rd.read();
				String action = rd.read();
				switch (action) {
				case "8930": { // Sleep
					player.action = 40;
					break;
				}
				case "9108": { // Tired
					player.action = 41;
					break;
				}
				case "9116": { // Sit
					player.action = 60;
					break;
				}
				case "9121": { // Mad
					player.action = 70;
					break;
				}
				case "9122": { // Excite
					player.action = 80;
					break;
				}
				case "9143": { // Sad
					player.action = 180;
					break;
				}
				case "9151": { // Flex
					player.action = 200;
					break;
				}
				case "9190": { // Play
					player.action = 210;
					break;
				}
				case "9147": { // Scared
					player.action = 190;
					break;
				}
				case "9139": { // Eat
					player.action = 170;
					break;
				}
				case "9131": { // Yes
					player.action = 110;
					break;
				}
				case "9135": { // No
					player.action = 120;
					break;
				}
				}

				// Create sync packet
				if (player.room != null) {
					PlayerObjectUpdatePacket pkt = new PlayerObjectUpdatePacket();
					pkt.id = player.id;
					pkt.room = player.room;
					pkt.action = player.action;
					pkt.mode = 4;
					pkt.position = player.position;
					pkt.heading = new Vector3(0, 0, 0);
					pkt.rotation = player.rotation;
					pkt.speed = 0;
					PeerToPeerModule.sendNexusPacket(pkt);
				}

			} else if (packet instanceof ObjectUpdatePacket) {
				// Object update
				ObjectUpdatePacket update = (ObjectUpdatePacket) packet;

				// Get player
				P2PPlayer player = PeerToPeerModule.getPlayer(((Player) client.container).account.getAccountID());
				if (player != null) {
					// Sync to memory
					if (update.mode == 4)
						player.action = update.action;
					player.position = update.position;
					player.rotation = update.rotation;

					// Create sync packet
					if (player.roomReady) {
						PlayerObjectUpdatePacket pkt = new PlayerObjectUpdatePacket();
						pkt.id = player.id;
						pkt.room = player.room;
						pkt.action = update.action;
						pkt.mode = update.mode;
						pkt.position = update.position;
						pkt.heading = update.heading;
						pkt.rotation = update.rotation;
						pkt.speed = update.speed;
						PeerToPeerModule.sendNexusPacket(pkt);
					}
				}
			} else if (packet instanceof SanctuaryLookLoadPacket || packet instanceof SanctuaryLookSavePacket
					|| packet instanceof SanctuaryLookSwitchPacket
					|| packet instanceof SanctuaryUpgradeCompletePacket) {
				// Sanctuary updates

				// Get player
				P2PPlayer player = PeerToPeerModule.getPlayer(((Player) client.container).account.getAccountID());
				player.currentSanctuaryLook = ((Player) client.container).activeSanctuaryLook;

				// Sync
				PlayerSanctuarySelectPacket pkt = new PlayerSanctuarySelectPacket();
				pkt.id = player.id;
				pkt.currentLookID = player.currentSanctuaryLook;
				PeerToPeerModule.sendNexusPacket(pkt);
			}
		}
	}

	@Override
	public Player getPlayer(String id) {
		Player player = super.getPlayer(id);
		if (player != null && player.client instanceof P2PSmartfoxClient) {
			P2PPlayer plr = ((P2PSmartfoxClient) player.client).getPlayer();

			// Sync
			player.activeLook = plr.currentLook;
			player.activeSanctuaryLook = plr.currentSanctuaryLook;
			player.lastPos = plr.position;
			player.lastRot = plr.rotation;
			player.levelID = plr.levelID;
			player.levelType = plr.levelType;
			player.lastAction = plr.action;
			player.room = plr.room;
			player.roomReady = plr.roomReady;
			player.wasInChat = true;
		}
		return player;
	}

	@Override
	public Player[] getPlayers() {
		Player[] players = super.getPlayers();
		for (Player player : players) {
			if (player.client instanceof P2PSmartfoxClient) {
				P2PPlayer plr = ((P2PSmartfoxClient) player.client).getPlayer();

				// Sync
				player.activeLook = plr.currentLook;
				player.activeSanctuaryLook = plr.currentSanctuaryLook;
				player.lastPos = plr.position;
				player.lastRot = plr.rotation;
				player.levelID = plr.levelID;
				player.levelType = plr.levelType;
				player.lastAction = plr.action;
				player.room = plr.room;
				player.roomReady = plr.roomReady;
				player.wasInChat = true;
			}
		}
		return players;
	}

	public void playerJoin(P2PPlayer plr) {
		CenturiaAccount acc = AccountManager.getInstance().getAccount(plr.id);
		P2PSmartfoxClient client = new P2PSmartfoxClient(plr);
		Player player = loginPlayer(acc, client);
		plr.player = player;
	}

	public void playerLeave(P2PPlayer plr) {
		if (!plr.isLocal)
			playerLeft(plr.player);
	}

}
