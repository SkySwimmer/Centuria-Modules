package org.asf.emuferal.modules.gcs;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Stream;

import org.asf.centuria.Centuria;
import org.asf.centuria.accounts.AccountManager;
import org.asf.centuria.accounts.CenturiaAccount;
import org.asf.centuria.dms.DMManager;
import org.asf.centuria.dms.PrivateChatMessage;
import org.asf.centuria.modules.ICenturiaModule;
import org.asf.centuria.modules.eventbus.EventListener;
import org.asf.centuria.modules.events.accounts.AccountBanEvent;
import org.asf.centuria.modules.events.accounts.AccountDeletionEvent;
import org.asf.centuria.modules.events.chat.ChatLoginEvent;
import org.asf.centuria.modules.events.chat.ChatMessageReceivedEvent;
import org.asf.centuria.modules.events.chatcommands.ChatCommandEvent;
import org.asf.centuria.modules.events.chatcommands.ModuleCommandSyntaxListEvent;
import org.asf.centuria.networking.chatserver.ChatClient;
import org.asf.centuria.social.SocialManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class GcsForFeralModule implements ICenturiaModule {

	@Override
	public String id() {
		return "gcs-for-feral";
	}

	@Override
	public String version() {
		return "1.0.0.A1";
	}

	@Override
	public void init() {
		// Main init method
	}

	@EventListener
	public void accountBan(AccountBanEvent event) {
		if (event.getAccount().getSaveSharedInventory().containsItem("gcs")) {
			// Player banned, leave all GCs
			JsonArray arr = event.getAccount().getSaveSharedInventory().getItem("gcs").getAsJsonArray();
			for (JsonElement ele : arr) {
				String id = ele.getAsString();

				// Check GC
				if (DMManager.getInstance().dmExists(id) && Stream.of(DMManager.getInstance().getDMParticipants(id))
						.anyMatch(t -> t.equals(event.getAccount().getAccountID())))
					leaveGC(id, event.getAccount());
			}
		}
	}

	@EventListener
	public void accountDelete(AccountDeletionEvent event) {
		if (event.getAccount().getSaveSharedInventory().containsItem("gcs")) {
			// Player banned, leave all GCs
			JsonArray arr = event.getAccount().getSaveSharedInventory().getItem("gcs").getAsJsonArray();
			for (JsonElement ele : arr) {
				String id = ele.getAsString();

				// Check GC
				if (DMManager.getInstance().dmExists(id) && Stream.of(DMManager.getInstance().getDMParticipants(id))
						.anyMatch(t -> t.equals(event.getAccount().getAccountID())))
					leaveGC(id, event.getAccount());
			}
		}
	}

	@EventListener
	public void chatLogin(ChatLoginEvent event) {
		// Called on chat login

		// Finds gcs
		if (event.getAccount().getSaveSharedInventory().containsItem("gcs")) {
			// Join gcs
			ArrayList<JsonElement> toRemove = new ArrayList<JsonElement>();
			JsonArray arr = event.getAccount().getSaveSharedInventory().getItem("gcs").getAsJsonArray();
			for (JsonElement ele : arr) {
				String id = ele.getAsString();

				// Check existence
				if (DMManager.getInstance().dmExists(id)) {
					// Join it
					event.getClient().joinRoom(id, true);
				} else {
					// Remove it
					toRemove.add(ele);
				}
			}

			// Remove nonexistent
			for (JsonElement ele : toRemove)
				arr.remove(ele);

			// Save if needed
			if (toRemove.size() != 0)
				event.getAccount().getSaveSharedInventory().setItem("gcs", arr);
		} else
			event.getAccount().getSaveSharedInventory().setItem("gcs", new JsonArray());
	}

	@EventListener
	public void registerCommands(ModuleCommandSyntaxListEvent event) {
		// Register commands
		event.addCommandSyntaxMessage("gccreate/gcc \"<name>\"");
		event.addCommandSyntaxMessage("gcadd/gca \"<player>\"");
		event.addCommandSyntaxMessage("gckick/gck \"<player>\"");
		event.addCommandSyntaxMessage("gcleave/gcl");
		event.addCommandSyntaxMessage("gclist");
	}

	@EventListener
	public void runCommand(ChatCommandEvent event) {
		// Called to handle commands
		switch (event.getCommandID()) {

		// Create gc
		case "gcc":
		case "gccreate": {
			// Check arguments
			if (event.getCommandArguments().length < 1) {
				event.respond("Missing argument: GC name");
				return;
			}

			// Find GC
			String nm = event.getCommandArguments()[0].trim();
			if (gcNameExists(nm, event.getClient())) {
				event.respond("Error: GC with that name already exists");
				return;
			}
			if (nm.length() < 3) {
				event.respond("Error: name too short");
				return;
			}
			if (nm.length() > 15) {
				event.respond("Error: name too long");
				return;
			}

			// Create GC
			String id = UUID.randomUUID().toString();
			while (DMManager.getInstance().dmExists(id))
				id = UUID.randomUUID().toString();
			DMManager.getInstance().openDM(id, new String[] { "plaintext:[GC] " + nm });

			// Join GC
			joinGC(id, event.getAccount());
			systemMessage(id, event.getAccount().getDisplayName() + " created the GC.");

			// Respond
			event.respond("Success! GC " + nm + " has been created!");

			break;
		}

		// GC list
		case "gclist": {
			String msg = "Group chats you are part of:";
			ArrayList<JsonElement> toRemove = new ArrayList<JsonElement>();
			JsonArray arr = event.getAccount().getSaveSharedInventory().getItem("gcs").getAsJsonArray();
			for (JsonElement ele : arr) {
				String id = ele.getAsString();

				// Check existence
				if (DMManager.getInstance().dmExists(id)) {
					// List it
					String[] participants = DMManager.getInstance().getDMParticipants(id);
					msg += "\n - " + participants[0].substring("plaintext:[GC] ".length()) + " - "
							+ (participants.length - 1) + " participant(s)";
				} else {
					// Remove it
					toRemove.add(ele);
				}
			}

			// Remove nonexistent
			for (JsonElement ele : toRemove)
				arr.remove(ele);

			// Save if needed
			if (toRemove.size() != 0)
				event.getAccount().getSaveSharedInventory().setItem("gcs", arr);
			event.respond(msg);
			break;
		}

		// Invalid commands
		case "gckick":
		case "gck":
		case "gcleave":
		case "gcl":
		case "gcadd":
		case "gca":
			event.respond("Error: this command needs to be run from within a Group Chat.");
			break;

		}
	}

	// Checks GC names
	private boolean gcNameExists(String name, ChatClient client) {
		JsonArray arr = client.getPlayer().getSaveSharedInventory().getItem("gcs").getAsJsonArray();
		for (JsonElement ele : arr) {
			String id = ele.getAsString();

			// Check existence
			if (DMManager.getInstance().dmExists(id)) {
				// List it
				String[] participants = DMManager.getInstance().getDMParticipants(id);
				if (participants[0].substring("plaintext:[GC] ".length()).equalsIgnoreCase(name))
					return true;
			}
		}
		return false;
	}

	// Checks if a conversation is a GC
	private boolean isGCConvo(String convoId, CenturiaAccount acc) {
		if (!acc.getSaveSharedInventory().containsItem("gcs"))
			return false;
		boolean found = false;
		ArrayList<JsonElement> toRemove = new ArrayList<JsonElement>();
		JsonArray arr = acc.getSaveSharedInventory().getItem("gcs").getAsJsonArray();
		for (JsonElement ele : arr) {
			String id = ele.getAsString();

			// Check existence
			if (DMManager.getInstance().dmExists(id)) {
				// Check
				if (id.equals(convoId))
					found = true; // Found it
			} else {
				// Remove it
				toRemove.add(ele);
			}
		}

		// Remove nonexistent
		for (JsonElement ele : toRemove)
			arr.remove(ele);

		// Save if needed
		if (toRemove.size() != 0)
			acc.getSaveSharedInventory().setItem("gcs", arr);
		return found;
	}

	@EventListener
	public void handleMessage(ChatMessageReceivedEvent event) {
		// Called when a message is sent by a player
		if (isGCConvo(event.getConversationId(), event.getAccount())) {
			// isGCConvo a gc
			if (event.getMessage().startsWith(">") && !event.getMessage().startsWith("> ")
					&& !event.getMessage().equals(">")) {
				// Command message
				String cmd = event.getMessage().substring(1);
				ArrayList<String> args = parseCommand(cmd);
				if (args.size() > 0) {
					cmd = args.remove(0);

					// Handle command
					switch (cmd) {

					case "gckick":
					case "gck": {
						// Kick player
						event.cancel();

						// Check if owner
						String owner = DMManager.getInstance().getDMParticipants(event.getConversationId())[1];
						CenturiaAccount ownerAcc = AccountManager.getInstance().getAccount(owner);
						if (!event.getAccount().getAccountID().equals(owner) && ownerAcc != null) {
							// Error
							SimpleDateFormat fmt = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ss");
							fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
							JsonObject res = new JsonObject();
							res.addProperty("conversationType", "private");
							res.addProperty("conversationId", event.getConversationId());
							res.addProperty("message",
									"Issued chat command: " + cmd + ":\n[system] Error: you are not the GC owner, only "
											+ ownerAcc.getDisplayName() + " can use this command.");
							res.addProperty("source", event.getAccount().getAccountID());
							res.addProperty("sentAt", fmt.format(new Date()));
							res.addProperty("eventId", "chat.postMessage");
							res.addProperty("success", true);
							event.getClient().sendPacket(res);
							return;
						}

						// Check arguments
						String[] cmdArgs = args.toArray(t -> new String[t]);
						if (cmdArgs.length < 1) {
							SimpleDateFormat fmt = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ss");
							fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
							JsonObject res = new JsonObject();
							res.addProperty("conversationType", "private");
							res.addProperty("conversationId", event.getConversationId());
							res.addProperty("message",
									"Issued chat command: " + cmd + ":\n[system] Missing argument: player");
							res.addProperty("source", event.getAccount().getAccountID());
							res.addProperty("sentAt", fmt.format(new Date()));
							res.addProperty("eventId", "chat.postMessage");
							res.addProperty("success", true);
							event.getClient().sendPacket(res);
							return;
						}

						// Find player
						String id = AccountManager.getInstance().getUserByDisplayName(cmdArgs[0]);
						if (id == null) {
							SimpleDateFormat fmt = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ss");
							fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
							JsonObject res = new JsonObject();
							res.addProperty("conversationType", "private");
							res.addProperty("conversationId", event.getConversationId());
							res.addProperty("message", "Issued chat command: " + cmd
									+ ":\n[system] Invalid argument: player: player not found");
							res.addProperty("source", event.getAccount().getAccountID());
							res.addProperty("sentAt", fmt.format(new Date()));
							res.addProperty("eventId", "chat.postMessage");
							res.addProperty("success", true);
							event.getClient().sendPacket(res);
							return;
						}
						CenturiaAccount acc = AccountManager.getInstance().getAccount(id);
						if (acc == null
								|| !Stream.of(DMManager.getInstance().getDMParticipants(event.getConversationId()))
										.anyMatch(t -> t.equals(id))) {
							SimpleDateFormat fmt = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ss");
							fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
							JsonObject res = new JsonObject();
							res.addProperty("conversationType", "private");
							res.addProperty("conversationId", event.getConversationId());
							res.addProperty("message", "Issued chat command: " + cmd
									+ ":\n[system] Invalid argument: player: player not found");
							res.addProperty("source", event.getAccount().getAccountID());
							res.addProperty("sentAt", fmt.format(new Date()));
							res.addProperty("eventId", "chat.postMessage");
							res.addProperty("success", true);
							event.getClient().sendPacket(res);
							return;
						}

						// Send response
						SimpleDateFormat fmt = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ss");
						fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
						JsonObject res = new JsonObject();
						res.addProperty("conversationType", "private");
						res.addProperty("conversationId", event.getConversationId());
						res.addProperty("message",
								"Issued chat command: " + cmd + ":\n[system] Kicked " + acc.getDisplayName());
						res.addProperty("source", event.getAccount().getAccountID());
						res.addProperty("sentAt", fmt.format(new Date()));
						res.addProperty("eventId", "chat.postMessage");
						res.addProperty("success", true);
						event.getClient().sendPacket(res);

						// Kick player
						leaveGC(event.getConversationId(), acc);

						break;
					}

					case "gcadd":
					case "gca": {
						// Add player
						event.cancel();

						// Check arguments
						String[] cmdArgs = args.toArray(t -> new String[t]);
						if (cmdArgs.length < 1) {
							SimpleDateFormat fmt = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ss");
							fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
							JsonObject res = new JsonObject();
							res.addProperty("conversationType", "private");
							res.addProperty("conversationId", event.getConversationId());
							res.addProperty("message",
									"Issued chat command: " + cmd + ":\n[system] Missing argument: player");
							res.addProperty("source", event.getAccount().getAccountID());
							res.addProperty("sentAt", fmt.format(new Date()));
							res.addProperty("eventId", "chat.postMessage");
							res.addProperty("success", true);
							event.getClient().sendPacket(res);
							return;
						}

						// Find player
						String id = AccountManager.getInstance().getUserByDisplayName(cmdArgs[0]);
						if (id == null) {
							SimpleDateFormat fmt = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ss");
							fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
							JsonObject res = new JsonObject();
							res.addProperty("conversationType", "private");
							res.addProperty("conversationId", event.getConversationId());
							res.addProperty("message", "Issued chat command: " + cmd
									+ ":\n[system] Invalid argument: player: player not found");
							res.addProperty("source", event.getAccount().getAccountID());
							res.addProperty("sentAt", fmt.format(new Date()));
							res.addProperty("eventId", "chat.postMessage");
							res.addProperty("success", true);
							event.getClient().sendPacket(res);
							return;
						}
						CenturiaAccount acc = AccountManager.getInstance().getAccount(id);
						if (acc == null) {
							SimpleDateFormat fmt = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ss");
							fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
							JsonObject res = new JsonObject();
							res.addProperty("conversationType", "private");
							res.addProperty("conversationId", event.getConversationId());
							res.addProperty("message", "Issued chat command: " + cmd
									+ ":\n[system] Invalid argument: player: player not found");
							res.addProperty("source", event.getAccount().getAccountID());
							res.addProperty("sentAt", fmt.format(new Date()));
							res.addProperty("eventId", "chat.postMessage");
							res.addProperty("success", true);
							event.getClient().sendPacket(res);
							return;
						}
						if (isGCConvo(event.getConversationId(), acc)) {
							SimpleDateFormat fmt = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ss");
							fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
							JsonObject res = new JsonObject();
							res.addProperty("conversationType", "private");
							res.addProperty("conversationId", event.getConversationId());
							res.addProperty("message", "Issued chat command: " + cmd
									+ ":\n[system] Invalid argument: player: player is already in this GC");
							res.addProperty("source", event.getAccount().getAccountID());
							res.addProperty("sentAt", fmt.format(new Date()));
							res.addProperty("eventId", "chat.postMessage");
							res.addProperty("success", true);
							event.getClient().sendPacket(res);
							return;
						}

						// Check mutuals
						SocialManager manager = SocialManager.getInstance();
						if (!manager.getPlayerIsFollowing(id, event.getAccount().getAccountID())
								|| !manager.getPlayerIsFollowing(event.getAccount().getAccountID(), id)
								|| manager.getPlayerIsBlocked(id, event.getAccount().getAccountID())) {
							// Error
							SimpleDateFormat fmt = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ss");
							fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
							JsonObject res = new JsonObject();
							res.addProperty("conversationType", "private");
							res.addProperty("conversationId", event.getConversationId());
							res.addProperty("message",
									"Issued chat command: " + cmd + ":\n[system] Cannot add " + acc.getDisplayName()
											+ " to the GC: you need to be mutual followers to add players to a GC.");
							res.addProperty("source", event.getAccount().getAccountID());
							res.addProperty("sentAt", fmt.format(new Date()));
							res.addProperty("eventId", "chat.postMessage");
							res.addProperty("success", true);
							event.getClient().sendPacket(res);
							return;
						}

						// Send response
						SimpleDateFormat fmt = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ss");
						fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
						JsonObject res = new JsonObject();
						res.addProperty("conversationType", "private");
						res.addProperty("conversationId", event.getConversationId());
						res.addProperty("message",
								"Issued chat command: " + cmd + ":\n[system] Added " + acc.getDisplayName());
						res.addProperty("source", event.getAccount().getAccountID());
						res.addProperty("sentAt", fmt.format(new Date()));
						res.addProperty("eventId", "chat.postMessage");
						res.addProperty("success", true);
						event.getClient().sendPacket(res);

						// Add player
						joinGC(event.getConversationId(), acc);
						systemMessage(event.getConversationId(), event.getAccount().getDisplayName() + " added "
								+ acc.getDisplayName() + " to the group chat.");

						break;
					}

					case "gcleave":
					case "gcl": {
						// Leave GC
						leaveGC(event.getConversationId(), event.getAccount());
						event.cancel();
						break;
					}

					}
				}
			}
		}
	}

	/**
	 * Adds clients to a GC
	 * 
	 * @param id  GC ID
	 * @param acc Player to add to the gc
	 */
	public void joinGC(String id, CenturiaAccount acc) {
		// Join GC
		ChatClient cl = Centuria.chatServer.getClient(acc.getAccountID());
		if (cl != null)
			cl.joinRoom(id, true);
		DMManager.getInstance().addParticipant(id, acc.getAccountID());

		// Send join packets
		if (cl != null) {
			JsonObject res = new JsonObject();
			res.addProperty("eventId", "conversations.openPrivate");
			res.addProperty("conversationId", id);
			res.addProperty("success", true);
			cl.sendPacket(res);
			res = new JsonObject();
			res.addProperty("conversationId", id);
			res.addProperty("eventId", "conversations.create");
			res.addProperty("success", true);
			cl.sendPacket(res);
			res = new JsonObject();
			res.addProperty("conversationId", id);
			res.addProperty("participant", acc.getAccountID());
			res.addProperty("eventId", "conversations.addParticipant");
			res.addProperty("success", true);
			cl.sendPacket(res);
		}

		// Save to memory
		if (!isGCConvo(id, acc)) {
			boolean found = false;
			ArrayList<JsonElement> toRemove = new ArrayList<JsonElement>();
			if (!acc.getSaveSharedInventory().containsItem("gcs"))
				acc.getSaveSharedInventory().setItem("gcs", new JsonArray());
			JsonArray arr = acc.getSaveSharedInventory().getItem("gcs").getAsJsonArray();
			for (JsonElement ele : arr) {
				String cid = ele.getAsString();

				// Check existence
				if (DMManager.getInstance().dmExists(cid)) {
					// Check
					if (id.equals(cid))
						found = true; // Found it
				} else {
					// Remove it
					toRemove.add(ele);
				}
			}

			// Add if needed
			if (!found)
				arr.add(id);

			// Remove nonexistent
			for (JsonElement ele : toRemove)
				arr.remove(ele);

			// Save if needed
			if (toRemove.size() != 0 || !found)
				acc.getSaveSharedInventory().setItem("gcs", arr);
		}
	}

	/**
	 * Removes clients from a GC
	 * 
	 * @param id  GC ID
	 * @param acc Player to remove from the gc
	 */
	public void leaveGC(String id, CenturiaAccount acc) {
		if (isGCConvo(id, acc)) {
			// Send system message
			systemMessage(id, "User left: " + acc.getDisplayName());

			// Leave GC
			ChatClient client = Centuria.chatServer.getClient(acc.getAccountID());
			if (client != null)
				client.leaveRoom(id);
			DMManager.getInstance().removeParticipant(id, acc.getAccountID());

			// Remove GC from save data
			ArrayList<JsonElement> toRemove = new ArrayList<JsonElement>();
			JsonArray arr = acc.getSaveSharedInventory().getItem("gcs").getAsJsonArray();
			for (JsonElement ele : arr) {
				String cid = ele.getAsString();

				// Check existence
				if (DMManager.getInstance().dmExists(cid)) {
					// Check
					if (cid.equals(id))
						toRemove.add(ele); // Found it
				} else {
					// Remove it
					toRemove.add(ele);
				}
			}

			// Remove nonexistent
			for (JsonElement ele : toRemove)
				arr.remove(ele);

			// Save if needed
			if (toRemove.size() != 0)
				acc.getSaveSharedInventory().setItem("gcs", arr);

			// Delete GC if empty
			if (DMManager.getInstance().getDMParticipants(id).length == 1)
				DMManager.getInstance().deleteDM(id); // Empty GC
		}
	}

	// Sends messages as the server
	private void systemMessage(String id, String message) {
		if (DMManager.getInstance().dmExists(id)) {
			// Time format
			SimpleDateFormat fmt = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ss");
			fmt.setTimeZone(TimeZone.getTimeZone("UTC"));

			// Save message
			PrivateChatMessage msg = new PrivateChatMessage();
			msg.content = message;
			msg.sentAt = fmt.format(new Date());
			msg.source = new UUID(0, 0).toString();
			DMManager.getInstance().saveDMMessge(id, msg);

			// Build message
			JsonObject res = new JsonObject();
			res.addProperty("conversationType", "private");
			res.addProperty("conversationId", id);
			res.addProperty("message", message);
			res.addProperty("source", msg.source);
			res.addProperty("sentAt", msg.sentAt);
			res.addProperty("eventId", "chat.postMessage");
			res.addProperty("success", true);

			// Broadcast
			for (ChatClient cl : Centuria.chatServer.getClients())
				if (cl.isInRoom(id))
					cl.sendPacket(res);
		}
	}

	// Command parser
	private ArrayList<String> parseCommand(String args) {
		ArrayList<String> args3 = new ArrayList<String>();
		char[] argarray = args.toCharArray();
		boolean ignorespaces = false;
		String last = "";
		int i = 0;
		for (char c : args.toCharArray()) {
			if (c == '"' && (i == 0 || argarray[i - 1] != '\\')) {
				if (ignorespaces)
					ignorespaces = false;
				else
					ignorespaces = true;
			} else if (c == ' ' && !ignorespaces && (i == 0 || argarray[i - 1] != '\\')) {
				args3.add(last);
				last = "";
			} else if (c != '\\' || (i + 1 < argarray.length && argarray[i + 1] != '"'
					&& (argarray[i + 1] != ' ' || ignorespaces))) {
				last += c;
			}

			i++;
		}

		if (last == "" == false)
			args3.add(last);

		return args3;
	}

}
