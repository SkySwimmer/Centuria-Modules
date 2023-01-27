package org.asf.centuria.discord.handlers.discord;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.Random;

import org.asf.centuria.Centuria;
import org.asf.centuria.accounts.AccountManager;
import org.asf.centuria.accounts.CenturiaAccount;
import org.asf.centuria.discord.DiscordBotModule;
import org.asf.centuria.discord.LinkUtils;
import org.asf.centuria.entities.players.Player;
import org.asf.centuria.ipbans.IpBanManager;
import org.asf.centuria.modules.eventbus.EventBus;
import org.asf.centuria.modules.events.accounts.MiscModerationEvent;
import org.asf.centuria.networking.chatserver.ChatClient;
import org.asf.centuria.networking.chatserver.networking.SendMessage;
import org.asf.centuria.networking.gameserver.GameServer;

import com.google.gson.JsonObject;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.object.component.SelectMenu.Option;
import discord4j.core.object.component.TextInput;
import discord4j.core.object.entity.Guild;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.discordjson.json.ApplicationCommandInteractionData;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.rest.util.Permission;
import reactor.core.publisher.Mono;

public class CommandHandler {

	private static Random rnd = new Random();

	/**
	 * The setup command
	 */
	public static ApplicationCommandOptionData setupCommand() {
		return ApplicationCommandOptionData.builder().name("setup").description("Server configuration command")
				.type(ApplicationCommandOption.Type.SUB_COMMAND.getValue()).build();
	}

	/**
	 * The account panel setup command
	 */
	public static ApplicationCommandOptionData createAccountPanel() {
		return ApplicationCommandOptionData.builder().name("createaccountpanel")
				.description("Account panel creation command")
				.type(ApplicationCommandOption.Type.SUB_COMMAND.getValue()).build();
	}

	/**
	 * The account info command
	 */
	public static ApplicationCommandOptionData getAccountInfo() {
		return ApplicationCommandOptionData.builder().name("getaccountinfo")
				.description("Account info retrieval command")
				.addOption(ApplicationCommandOptionData.builder().name("member")
						.type(ApplicationCommandOption.Type.USER.getValue())
						.description("User to retrieve the Centuria account details from").required(true).build())
				.type(ApplicationCommandOption.Type.SUB_COMMAND.getValue()).build();
	}

	/**
	 * The account info command (discord)
	 */
	public static ApplicationCommandOptionData getDiscordAccountInfo() {
		return ApplicationCommandOptionData.builder().name("getdiscord").description("Link info retrieval command")
				.addOption(ApplicationCommandOptionData.builder().name("centuria-displayname")
						.type(ApplicationCommandOption.Type.STRING.getValue())
						.description("Player to retrieve the Discord account details from").required(true).build())
				.type(ApplicationCommandOption.Type.SUB_COMMAND.getValue()).build();
	}

	/**
	 * The account kick command
	 */
	public static ApplicationCommandOptionData kick() {
		return ApplicationCommandOptionData.builder().name("kick").description("Kick a player")
				.addOption(ApplicationCommandOptionData.builder().name("centuria-displayname")
						.type(ApplicationCommandOption.Type.STRING.getValue()).description("Player to kick")
						.required(true).build())
				.addOption(ApplicationCommandOptionData.builder().name("reason")
						.type(ApplicationCommandOption.Type.STRING.getValue()).description("Kick reason").build())
				.type(ApplicationCommandOption.Type.SUB_COMMAND.getValue()).build();
	}

	/**
	 * The account makemoderator command
	 */
	public static ApplicationCommandOptionData makeModerator() {
		return ApplicationCommandOptionData.builder().name("makemoderator").description("Makes a player moderator")
				.addOption(ApplicationCommandOptionData.builder().name("centuria-displayname")
						.type(ApplicationCommandOption.Type.STRING.getValue()).description("Player to promote")
						.required(true).build())
				.type(ApplicationCommandOption.Type.SUB_COMMAND.getValue()).build();
	}

	/**
	 * The account makeadmin command
	 */
	public static ApplicationCommandOptionData makeAdmin() {
		return ApplicationCommandOptionData.builder().name("makeadmin").description("Makes a player admin")
				.addOption(ApplicationCommandOptionData.builder().name("centuria-displayname")
						.type(ApplicationCommandOption.Type.STRING.getValue()).description("Player to promote")
						.required(true).build())
				.type(ApplicationCommandOption.Type.SUB_COMMAND.getValue()).build();
	}

	/**
	 * The account removeperms command
	 */
	public static ApplicationCommandOptionData removePerms() {
		return ApplicationCommandOptionData.builder().name("removeperms")
				.description("Removes permissions from a player")
				.addOption(ApplicationCommandOptionData.builder().name("centuria-displayname")
						.type(ApplicationCommandOption.Type.STRING.getValue()).description("Player to demote")
						.required(true).build())
				.type(ApplicationCommandOption.Type.SUB_COMMAND.getValue()).build();
	}

	/**
	 * The account ban command
	 */
	public static ApplicationCommandOptionData ban() {
		return ApplicationCommandOptionData.builder().name("permban").description("Permanently bans a player")
				.addOption(ApplicationCommandOptionData.builder().name("centuria-displayname")
						.type(ApplicationCommandOption.Type.STRING.getValue()).description("Player to ban")
						.required(true).build())
				.addOption(ApplicationCommandOptionData.builder().name("reason")
						.type(ApplicationCommandOption.Type.STRING.getValue()).description("Ban reason").build())
				.type(ApplicationCommandOption.Type.SUB_COMMAND.getValue()).build();
	}

	/**
	 * The account ipban command
	 */
	public static ApplicationCommandOptionData ipBan() {
		return ApplicationCommandOptionData.builder().name("ipban").description("IP-bans a player")
				.addOption(ApplicationCommandOptionData.builder().name("target")
						.type(ApplicationCommandOption.Type.STRING.getValue()).description("Player name or IP to ban")
						.required(true).build())
				.addOption(ApplicationCommandOptionData.builder().name("reason")
						.type(ApplicationCommandOption.Type.STRING.getValue()).description("Ban reason").build())
				.type(ApplicationCommandOption.Type.SUB_COMMAND.getValue()).build();
	}

	/**
	 * The account pardonip command
	 */
	public static ApplicationCommandOptionData pardonIP() {
		return ApplicationCommandOptionData.builder().name("pardonip").description("Pardons a IP")
				.addOption(ApplicationCommandOptionData.builder().name("ip")
						.type(ApplicationCommandOption.Type.STRING.getValue()).description("IP address to pardon")
						.required(true).build())
				.type(ApplicationCommandOption.Type.SUB_COMMAND.getValue()).build();
	}

	/**
	 * The account ban command
	 */
	public static ApplicationCommandOptionData tempBan() {
		return ApplicationCommandOptionData.builder().name("tempban").description("Temporarily bans a player")
				.addOption(ApplicationCommandOptionData.builder().name("centuria-displayname")
						.type(ApplicationCommandOption.Type.STRING.getValue()).description("Player to ban")
						.required(true).build())
				.addOption(ApplicationCommandOptionData.builder().name("days")
						.type(ApplicationCommandOption.Type.INTEGER.getValue())
						.description("Days to ban the player for").required(true).build())
				.addOption(ApplicationCommandOptionData.builder().name("reason")
						.type(ApplicationCommandOption.Type.STRING.getValue()).description("Ban reason").build())
				.type(ApplicationCommandOption.Type.SUB_COMMAND.getValue()).build();
	}

	/**
	 * The account mute command
	 */
	public static ApplicationCommandOptionData mute() {
		return ApplicationCommandOptionData.builder().name("mute").description("Mutes a player")
				.addOption(ApplicationCommandOptionData.builder().name("centuria-displayname")
						.type(ApplicationCommandOption.Type.STRING.getValue()).description("Player to mute")
						.required(true).build())
				.addOption(ApplicationCommandOptionData.builder().name("minutes")
						.type(ApplicationCommandOption.Type.INTEGER.getValue())
						.description("Minutes to mute the player for").build())
				.addOption(ApplicationCommandOptionData.builder().name("hours")
						.type(ApplicationCommandOption.Type.INTEGER.getValue())
						.description("Hours to mute the player for").build())
				.addOption(ApplicationCommandOptionData.builder().name("days")
						.type(ApplicationCommandOption.Type.INTEGER.getValue())
						.description("Days to mute the player for").build())
				.addOption(ApplicationCommandOptionData.builder().name("reason")
						.type(ApplicationCommandOption.Type.STRING.getValue()).description("Mute reason").build())
				.type(ApplicationCommandOption.Type.SUB_COMMAND.getValue()).build();
	}

	/**
	 * The account pardon command
	 */
	public static ApplicationCommandOptionData pardon() {
		return ApplicationCommandOptionData.builder().name("pardon").description("Removes player penalties")
				.addOption(ApplicationCommandOptionData.builder().name("centuria-displayname")
						.type(ApplicationCommandOption.Type.STRING.getValue()).description("Player to ban")
						.required(true).build())
				.addOption(ApplicationCommandOptionData.builder().name("reason")
						.type(ApplicationCommandOption.Type.STRING.getValue()).description("Pardon reason").build())
				.type(ApplicationCommandOption.Type.SUB_COMMAND.getValue()).build();
	}

	/**
	 * The clearance code generation command
	 */
	public static ApplicationCommandOptionData generateClearanceCode() {
		return ApplicationCommandOptionData.builder().name("generateclearancecode")
				.description("Generates an admin clearance code")
				.type(ApplicationCommandOption.Type.SUB_COMMAND.getValue()).build();
	}

	/**
	 * Handles slash commands
	 * 
	 * @param event   Command event
	 * @param guild   Guild it was run in
	 * @param gateway Client
	 */
	public static Mono<?> handle(ApplicationCommandInteractionEvent event, Guild guild, GatewayDiscordClient gateway) {
		ApplicationCommandInteractionData data = (ApplicationCommandInteractionData) event.getInteraction().getData()
				.data().get();
		String command = data.name().get();
		if (command.equalsIgnoreCase("centuria")) {
			// Right command, find the subcommand
			String subCmd = data.options().get().get(0).name();
			switch (subCmd) {
			case "generateclearancecode": {
				// Required permissions: mod (ingame)
				CenturiaAccount modacc = LinkUtils
						.getAccountByDiscordID(event.getInteraction().getUser().getId().asString());
				if (modacc == null) {
					event.reply("**Error:** You dont have a Centuria account linked to your Discord account").block();
					return Mono.empty();
				}

				String permLevel = "member";
				if (modacc.getSaveSharedInventory().containsItem("permissions")) {
					permLevel = modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
							.get("permissionLevel").getAsString();
				}
				if (!GameServer.hasPerm(permLevel, "admin")) {
					event.reply("**Error:** no Centuria admin permissions.").block();
					return Mono.empty();
				}

				// Handle
				long codeLong = rnd.nextLong();
				String code = "";
				while (true) {
					while (codeLong < 10000)
						codeLong = rnd.nextLong();
					code = Long.toString(codeLong, 16);
					try {
						if (!SendMessage.clearanceCodes.contains(code))
							break;
					} catch (ConcurrentModificationException e) {
					}
					code = Long.toString(rnd.nextLong(), 16);
				}
				SendMessage.clearanceCodes.add(code);
				event.deferReply().block();
				EventBus.getInstance().dispatchEvent(new MiscModerationEvent("clearancecode.generated",
						"Admin Clearance Code Generated", Map.of(), modacc.getAccountID(), null));
				event.editReply("Clearance code generated: " + code + "\nIt will expire in 2 minutes.").block();
				final String cFinal = code;
				Thread th = new Thread(() -> {
					for (int i = 0; i < 12000; i++) {
						try {
							if (!SendMessage.clearanceCodes.contains(cFinal))
								return;
						} catch (ConcurrentModificationException e) {
						}
						try {
							Thread.sleep(10);
						} catch (InterruptedException e) {
						}
					}
					SendMessage.clearanceCodes.remove(cFinal);
				}, "Clearance code expiry");
				th.setDaemon(true);
				th.start();
				break;
			}
			case "getdiscord": {
				// Required permissions: mod (ingame)
				CenturiaAccount modacc = LinkUtils
						.getAccountByDiscordID(event.getInteraction().getUser().getId().asString());
				if (modacc == null) {
					event.reply("**Error:** You dont have a Centuria account linked to your Discord account").block();
					return Mono.empty();
				}

				String permLevel = "member";
				if (modacc.getSaveSharedInventory().containsItem("permissions")) {
					permLevel = modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
							.get("permissionLevel").getAsString();
				}
				if (!GameServer.hasPerm(permLevel, "moderator")) {
					event.reply("**Error:** no Centuria moderator permissions.").block();
					return Mono.empty();
				}

				// Find player UUID
				String uuid = AccountManager.getInstance()
						.getUserByDisplayName(data.options().get().get(0).options().get().get(0).value().get());
				if (uuid == null) {
					// Respond with error message
					event.reply("**Error:** player not recognized.").block();
					return Mono.empty();
				}
				CenturiaAccount acc = AccountManager.getInstance().getAccount(uuid);
				if (acc == null) {
					// Respond with error message
					event.reply("**Error:** player not recognized.").block();
					return Mono.empty();
				}

				// Check account link
				String userID = LinkUtils.getDiscordAccountFrom(acc);
				if (userID == null) {
					// Respond with error message
					event.reply("**Error:** the specified account has not been paired with any Discord account.")
							.block();
					return Mono.empty();
				}

				// Show account info
				String res = "Discord user ID: " + userID;
				try {
					res = "Discord user: `"
							+ DiscordBotModule.getClient().getUserById(Snowflake.of(userID)).block().getTag() + " ("
							+ userID + ")`";
				} catch (Exception e) {
				}
				event.reply(res).block();
				break;
			}
			case "kick": {
				// Required permissions: mod (ingame)
				CenturiaAccount modacc = LinkUtils
						.getAccountByDiscordID(event.getInteraction().getUser().getId().asString());
				if (modacc == null) {
					event.reply("**Error:** You dont have a Centuria account linked to your Discord account").block();
					return Mono.empty();
				}

				String permLevel = "member";
				if (modacc.getSaveSharedInventory().containsItem("permissions")) {
					permLevel = modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
							.get("permissionLevel").getAsString();
				}
				if (!GameServer.hasPerm(permLevel, "moderator")) {
					event.reply("**Error:** no Centuria moderator permissions.").block();
					return Mono.empty();
				}

				// Find player UUID
				var params = data.options().get().get(0).options().get();
				String uuid = AccountManager.getInstance().getUserByDisplayName(params.get(0).value().get());
				if (uuid == null) {
					// Respond with error message
					event.reply("**Error:** player not recognized.").block();
					return Mono.empty();
				}
				CenturiaAccount acc = AccountManager.getInstance().getAccount(uuid);
				if (acc == null) {
					// Respond with error message
					event.reply("**Error:** player not recognized.").block();
					return Mono.empty();
				}
				if (acc.getOnlinePlayerInstance() == null) {
					event.reply("**Error:** player not online.").block();
					return Mono.empty();
				}

				// Check rank
				if (acc.getSaveSharedInventory().containsItem("permissions")) {
					if ((GameServer
							.hasPerm(modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
									.get("permissionLevel").getAsString(), "developer")
							&& !GameServer.hasPerm(permLevel, "developer"))
							|| GameServer
									.hasPerm(modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
											.get("permissionLevel").getAsString(), "admin")
									&& !GameServer.hasPerm(permLevel, "admin")) {
						event.reply("**Error:** unable to moderate higher-ranking members.").block();
						return Mono.empty();
					}
				}

				// Kick
				if (params.size() == 1) {
					event.deferReply().block();
					acc.kick(modacc.getAccountID(), null);
					event.editReply("Kicked player " + acc.getDisplayName()).block();
				} else if (params.size() == 2) {
					event.deferReply().block();
					acc.kick(modacc.getAccountID(), params.get(1).value().get());
					event.editReply("Kicked player " + acc.getDisplayName() + ": " + params.get(1).value().get())
							.block();
				}
				break;
			}
			case "permban": {
				// Required permissions: mod (ingame)
				CenturiaAccount modacc = LinkUtils
						.getAccountByDiscordID(event.getInteraction().getUser().getId().asString());
				if (modacc == null) {
					event.reply("**Error:** You dont have a Centuria account linked to your Discord account").block();
					return Mono.empty();
				}

				String permLevel = "member";
				if (modacc.getSaveSharedInventory().containsItem("permissions")) {
					permLevel = modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
							.get("permissionLevel").getAsString();
				}
				if (!GameServer.hasPerm(permLevel, "moderator")) {
					event.reply("**Error:** no Centuria moderator permissions.").block();
					return Mono.empty();
				}

				// Find player UUID
				var params = data.options().get().get(0).options().get();
				String uuid = AccountManager.getInstance().getUserByDisplayName(params.get(0).value().get());
				if (uuid == null) {
					// Respond with error message
					event.reply("**Error:** player not recognized.").block();
					return Mono.empty();
				}
				CenturiaAccount acc = AccountManager.getInstance().getAccount(uuid);
				if (acc == null) {
					// Respond with error message
					event.reply("**Error:** player not recognized.").block();
					return Mono.empty();
				}
				if (acc.isBanned()) {
					event.reply("**Error:** player is already banned.").block();
					return Mono.empty();
				}

				// Check rank
				if (acc.getSaveSharedInventory().containsItem("permissions")) {
					if ((GameServer
							.hasPerm(modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
									.get("permissionLevel").getAsString(), "developer")
							&& !GameServer.hasPerm(permLevel, "developer"))
							|| GameServer
									.hasPerm(modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
											.get("permissionLevel").getAsString(), "admin")
									&& !GameServer.hasPerm(permLevel, "admin")) {
						event.reply("**Error:** unable to moderate higher-ranking members.").block();
						return Mono.empty();
					}
				}

				// Ban
				if (params.size() == 1) {
					event.deferReply().block();
					acc.ban(modacc.getAccountID(), null);
					event.editReply("Banned player " + acc.getDisplayName()).block();
				} else if (params.size() == 2) {
					event.deferReply().block();
					acc.ban(modacc.getAccountID(), params.get(1).value().get());
					event.editReply("Banned player " + acc.getDisplayName() + ": " + params.get(1).value().get())
							.block();
				}
				break;
			}
			case "pardonip": {
				// Required permissions: admin (ingame)
				CenturiaAccount modacc = LinkUtils
						.getAccountByDiscordID(event.getInteraction().getUser().getId().asString());
				if (modacc == null) {
					event.reply("**Error:** You dont have a Centuria account linked to your Discord account").block();
					return Mono.empty();
				}

				String permLevel = "member";
				if (modacc.getSaveSharedInventory().containsItem("permissions")) {
					permLevel = modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
							.get("permissionLevel").getAsString();
				}
				if (!GameServer.hasPerm(permLevel, "admin")) {
					event.reply("**Error:** no Centuria admin permissions.").block();
					return Mono.empty();
				}

				// Check ip ban
				var params = data.options().get().get(0).options().get();
				String target = params.get(0).value().get();
				IpBanManager manager = IpBanManager.getInstance();
				if (manager.isIPBanned(target)) {
					manager.unbanIP(target);
					return event.reply("Pardoned IP: ||" + target + "||");
				}

				return event.reply("That IP has not been banned");
			}
			case "ipban": {
				// Required permissions: admin (ingame)
				CenturiaAccount modacc = LinkUtils
						.getAccountByDiscordID(event.getInteraction().getUser().getId().asString());
				if (modacc == null) {
					event.reply("**Error:** You dont have a Centuria account linked to your Discord account").block();
					return Mono.empty();
				}

				String permLevel = "member";
				if (modacc.getSaveSharedInventory().containsItem("permissions")) {
					permLevel = modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
							.get("permissionLevel").getAsString();
				}
				if (!GameServer.hasPerm(permLevel, "admin")) {
					event.reply("**Error:** no Centuria admin permissions.").block();
					return Mono.empty();
				}

				// Find player
				var params = data.options().get().get(0).options().get();
				String target = params.get(0).value().get();
				for (Player plr : Centuria.gameServer.getPlayers()) {
					if (plr.account.getDisplayName().equals(target)) {
						// Check rank
						if (plr.account.getSaveSharedInventory().containsItem("permissions")) {
							if ((GameServer
									.hasPerm(modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
											.get("permissionLevel").getAsString(), "developer")
									&& !GameServer.hasPerm(permLevel, "developer"))
									|| GameServer.hasPerm(modacc.getSaveSharedInventory().getItem("permissions")
											.getAsJsonObject().get("permissionLevel").getAsString(), "admin")
											&& !GameServer.hasPerm(permLevel, "admin")) {
								event.reply("**Error:** unable to moderate higher-ranking members.").block();
								return Mono.empty();
							}
						}

						// Ban
						if (params.size() == 1) {
							String addr = plr.client.getAddress();
							event.deferReply().block();
							plr.account.ipban(modacc.getAccountID(), null);
							return event.editReply("IP-banned player " + plr.account.getDisplayName() + "\nIP was: ||"
									+ addr + "|| (save this as pardoning can only be done by IP)");
						} else if (params.size() == 2) {
							event.deferReply().block();
							String addr = plr.client.getAddress();
							plr.account.ipban(modacc.getAccountID(), params.get(1).value().get());
							return event.editReply("IP-banned player " + plr.account.getDisplayName() + ": "
									+ params.get(1).value().get() + "\nIP was: ||" + addr
									+ "|| (save this as pardoning can only be done by IP)");
						}
					}
				}

				// Check if the inputted address is a IP address
				try {
					InetAddress.getByName(target);

					// Ban the IP
					event.deferReply().block();
					IpBanManager.getInstance().banIP(target);

					// Disconnect all with the given IP address (or attempt to)
					for (Player plr : Centuria.gameServer.getPlayers()) {
						// Get IP of player
						if (plr.client.getAddress().equals(target)) {
							// Ban player
							if (params.size() == 1)
								plr.account.ban(modacc.getAccountID(), null);
							else
								plr.account.ban(modacc.getAccountID(), params.get(1).value().get());
						}
					}

					// Log completion
					return event
							.editReply("Banned IP: ||" + target + "|| (save this as pardoning can only be done by IP)");
				} catch (Exception e) {
				}

				return event.editReply("**Error:** Player not found");
			}
			case "makemoderator": {
				// Required permissions: admin (ingame)
				CenturiaAccount modacc = LinkUtils
						.getAccountByDiscordID(event.getInteraction().getUser().getId().asString());
				if (modacc == null) {
					event.reply("**Error:** You dont have a Centuria account linked to your Discord account").block();
					return Mono.empty();
				}

				String permLevel = "member";
				if (modacc.getSaveSharedInventory().containsItem("permissions")) {
					permLevel = modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
							.get("permissionLevel").getAsString();
				}
				if (!GameServer.hasPerm(permLevel, "admin")) {
					event.reply("**Error:** no Centuria admin permissions.").block();
					return Mono.empty();
				}

				// Find player UUID
				var params = data.options().get().get(0).options().get();
				String uuid = AccountManager.getInstance().getUserByDisplayName(params.get(0).value().get());
				if (uuid == null) {
					// Respond with error message
					event.reply("**Error:** player not recognized.").block();
					return Mono.empty();
				}
				CenturiaAccount acc = AccountManager.getInstance().getAccount(uuid);
				if (acc == null) {
					// Respond with error message
					event.reply("**Error:** player not recognized.").block();
					return Mono.empty();
				}

				// Check
				if (acc.getSaveSharedInventory().containsItem("permissions")) {
					if (GameServer
							.hasPerm(acc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
									.get("permissionLevel").getAsString(), "developer")
							&& !GameServer.hasPerm(permLevel, "developer")) {
						return event.reply("Unable to demote higher-ranking users.");
					}
				}

				// Make moderator
				if (!acc.getSaveSharedInventory().containsItem("permissions"))
					acc.getSaveSharedInventory().setItem("permissions", new JsonObject());
				if (!acc.getSaveSharedInventory().getItem("permissions").getAsJsonObject().has("permissionLevel"))
					acc.getSaveSharedInventory().getItem("permissions").getAsJsonObject().remove("permissionLevel");
				acc.getSaveSharedInventory().getItem("permissions").getAsJsonObject().addProperty("permissionLevel",
						"moderator");
				acc.getSaveSharedInventory().setItem("permissions",
						acc.getSaveSharedInventory().getItem("permissions"));

				// Find online player
				for (ChatClient plr : Centuria.chatServer.getClients()) {
					if (plr.getPlayer().getDisplayName().equals(acc.getDisplayName())) {
						// Update inventory
						plr.getPlayer().getSaveSharedInventory().setItem("permissions",
								acc.getSaveSharedInventory().getItem("permissions"));
						break;
					}
				}

				return event.reply("Made " + acc.getDisplayName() + " moderator.");
			}
			case "makeadmin": {
				// Required permissions: admin (ingame)
				CenturiaAccount modacc = LinkUtils
						.getAccountByDiscordID(event.getInteraction().getUser().getId().asString());
				if (modacc == null) {
					event.reply("**Error:** You dont have a Centuria account linked to your Discord account").block();
					return Mono.empty();
				}

				String permLevel = "member";
				if (modacc.getSaveSharedInventory().containsItem("permissions")) {
					permLevel = modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
							.get("permissionLevel").getAsString();
				}
				if (!GameServer.hasPerm(permLevel, "admin")) {
					event.reply("**Error:** no Centuria admin permissions.").block();
					return Mono.empty();
				}

				// Find player UUID
				var params = data.options().get().get(0).options().get();
				String uuid = AccountManager.getInstance().getUserByDisplayName(params.get(0).value().get());
				if (uuid == null) {
					// Respond with error message
					event.reply("**Error:** player not recognized.").block();
					return Mono.empty();
				}
				CenturiaAccount acc = AccountManager.getInstance().getAccount(uuid);
				if (acc == null) {
					// Respond with error message
					event.reply("**Error:** player not recognized.").block();
					return Mono.empty();
				}

				// Check
				if (acc.getSaveSharedInventory().containsItem("permissions")) {
					if (GameServer
							.hasPerm(acc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
									.get("permissionLevel").getAsString(), "developer")
							&& !GameServer.hasPerm(permLevel, "developer")) {
						return event.reply("Unable to demote higher-ranking users.");
					}
				}

				// Make moderator
				if (!acc.getSaveSharedInventory().containsItem("permissions"))
					acc.getSaveSharedInventory().setItem("permissions", new JsonObject());
				if (!acc.getSaveSharedInventory().getItem("permissions").getAsJsonObject().has("permissionLevel"))
					acc.getSaveSharedInventory().getItem("permissions").getAsJsonObject().remove("permissionLevel");
				acc.getSaveSharedInventory().getItem("permissions").getAsJsonObject().addProperty("permissionLevel",
						"admin");
				acc.getSaveSharedInventory().setItem("permissions",
						acc.getSaveSharedInventory().getItem("permissions"));

				// Find online player
				for (ChatClient plr : Centuria.chatServer.getClients()) {
					if (plr.getPlayer().getDisplayName().equals(acc.getDisplayName())) {
						// Update inventory
						plr.getPlayer().getSaveSharedInventory().setItem("permissions",
								acc.getSaveSharedInventory().getItem("permissions"));
						break;
					}
				}

				return event.reply("Made " + acc.getDisplayName() + " admin.");
			}
			case "removeperms": {
				// Required permissions: admin (ingame)
				CenturiaAccount modacc = LinkUtils
						.getAccountByDiscordID(event.getInteraction().getUser().getId().asString());
				if (modacc == null) {
					event.reply("**Error:** You dont have a Centuria account linked to your Discord account").block();
					return Mono.empty();
				}

				String permLevel = "member";
				if (modacc.getSaveSharedInventory().containsItem("permissions")) {
					permLevel = modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
							.get("permissionLevel").getAsString();
				}
				if (!GameServer.hasPerm(permLevel, "admin")) {
					event.reply("**Error:** no Centuria admin permissions.").block();
					return Mono.empty();
				}

				// Find player UUID
				var params = data.options().get().get(0).options().get();
				String uuid = AccountManager.getInstance().getUserByDisplayName(params.get(0).value().get());
				if (uuid == null) {
					// Respond with error message
					event.reply("**Error:** player not recognized.").block();
					return Mono.empty();
				}
				CenturiaAccount acc = AccountManager.getInstance().getAccount(uuid);
				if (acc == null) {
					// Respond with error message
					event.reply("**Error:** player not recognized.").block();
					return Mono.empty();
				}

				// Check
				if (acc.getSaveSharedInventory().containsItem("permissions")) {
					if (GameServer
							.hasPerm(acc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
									.get("permissionLevel").getAsString(), "developer")
							&& !GameServer.hasPerm(permLevel, "developer")) {
						return event.reply("Unable to demote higher-ranking users.");
					}
				}

				// Remove permissions
				acc.getSaveSharedInventory().deleteItem("permissions");

				// Find online player
				for (ChatClient plr : Centuria.chatServer.getClients()) {
					if (plr.getPlayer().getDisplayName().equals(acc.getDisplayName())) {
						// Update inventory
						plr.getPlayer().getSaveSharedInventory().deleteItem("permissions");
						break;
					}
				}

				// Find online player
				for (Player plr : Centuria.gameServer.getPlayers()) {
					if (plr.account.getDisplayName().equals(acc.getDisplayName())) {
						// Update inventory
						plr.account.getSaveSharedInventory().deleteItem("permissions");
						plr.hasModPerms = false;
						break;
					}
				}

				return event.reply("Removed permissions from " + acc.getDisplayName());
			}
			case "tempban": {
				// Required permissions: mod (ingame)
				CenturiaAccount modacc = LinkUtils
						.getAccountByDiscordID(event.getInteraction().getUser().getId().asString());
				if (modacc == null) {
					event.reply("**Error:** You dont have a Centuria account linked to your Discord account").block();
					return Mono.empty();
				}

				String permLevel = "member";
				if (modacc.getSaveSharedInventory().containsItem("permissions")) {
					permLevel = modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
							.get("permissionLevel").getAsString();
				}
				if (!GameServer.hasPerm(permLevel, "moderator")) {
					event.reply("**Error:** no Centuria moderator permissions.").block();
					return Mono.empty();
				}

				// Find player UUID
				var params = data.options().get().get(0).options().get();
				String uuid = AccountManager.getInstance().getUserByDisplayName(params.get(0).value().get());
				if (uuid == null) {
					// Respond with error message
					event.reply("**Error:** player not recognized.").block();
					return Mono.empty();
				}
				CenturiaAccount acc = AccountManager.getInstance().getAccount(uuid);
				if (acc == null) {
					// Respond with error message
					event.reply("**Error:** player not recognized.").block();
					return Mono.empty();
				}
				if (acc.isBanned()) {
					event.reply("**Error:** player is already banned.").block();
					return Mono.empty();
				}

				// Check rank
				if (acc.getSaveSharedInventory().containsItem("permissions")) {
					if ((GameServer
							.hasPerm(modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
									.get("permissionLevel").getAsString(), "developer")
							&& !GameServer.hasPerm(permLevel, "developer"))
							|| GameServer
									.hasPerm(modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
											.get("permissionLevel").getAsString(), "admin")
									&& !GameServer.hasPerm(permLevel, "admin")) {
						event.reply("**Error:** unable to moderate higher-ranking members.").block();
						return Mono.empty();
					}
				}

				// Tempban
				if (params.size() == 2) {
					event.deferReply().block();
					acc.tempban(Integer.valueOf(params.get(1).value().get()), modacc.getAccountID(), null);
					event.editReply("Temporarily banned player " + acc.getDisplayName()).block();
				} else if (params.size() == 3) {
					event.deferReply().block();
					acc.tempban(Integer.valueOf(params.get(1).value().get()), modacc.getAccountID(),
							params.get(2).value().get());
					event.editReply(
							"Temporarily banned player " + acc.getDisplayName() + ": " + params.get(2).value().get())
							.block();
				}
				break;
			}
			case "mute": {
				// Required permissions: mod (ingame)
				CenturiaAccount modacc = LinkUtils
						.getAccountByDiscordID(event.getInteraction().getUser().getId().asString());
				if (modacc == null) {
					event.reply("**Error:** You dont have a Centuria account linked to your Discord account").block();
					return Mono.empty();
				}

				String permLevel = "member";
				if (modacc.getSaveSharedInventory().containsItem("permissions")) {
					permLevel = modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
							.get("permissionLevel").getAsString();
				}
				if (!GameServer.hasPerm(permLevel, "moderator")) {
					event.reply("**Error:** no Centuria moderator permissions.").block();
					return Mono.empty();
				}

				// Find player UUID
				var params = data.options().get().get(0).options().get();
				String uuid = AccountManager.getInstance().getUserByDisplayName(params.get(0).value().get());
				if (uuid == null) {
					// Respond with error message
					event.reply("**Error:** player not recognized.").block();
					return Mono.empty();
				}
				CenturiaAccount acc = AccountManager.getInstance().getAccount(uuid);
				if (acc == null) {
					// Respond with error message
					event.reply("**Error:** player not recognized.").block();
					return Mono.empty();
				}
				if (acc.isBanned()) {
					event.reply(
							"**Error:** player is banned, this penalty is higher than a mute and would be overwritten, cancelled.")
							.block();
					return Mono.empty();
				}

				// Check rank
				if (acc.getSaveSharedInventory().containsItem("permissions")) {
					if ((GameServer
							.hasPerm(modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
									.get("permissionLevel").getAsString(), "developer")
							&& !GameServer.hasPerm(permLevel, "developer"))
							|| GameServer
									.hasPerm(modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
											.get("permissionLevel").getAsString(), "admin")
									&& !GameServer.hasPerm(permLevel, "admin")) {
						event.reply("**Error:** unable to moderate higher-ranking members.").block();
						return Mono.empty();
					}
				}

				// Mute
				if (params.size() < 2)
					return event.reply("Error: you need to specify one of the optional time arguments");

				// Load params
				int minutes = 0;
				int hours = 0;
				int days = 0;
				if (params.stream().anyMatch(t -> t.name().equals("minutes")) && !params.stream()
						.filter(t -> t.name().equals("minutes")).findFirst().get().value().isAbsent())
					minutes = Integer.parseInt(
							params.stream().filter(t -> t.name().equals("minutes")).findFirst().get().value().get());
				if (params.stream().anyMatch(t -> t.name().equals("hours"))
						&& !params.stream().filter(t -> t.name().equals("hours")).findFirst().get().value().isAbsent())
					hours = Integer.parseInt(
							params.stream().filter(t -> t.name().equals("hours")).findFirst().get().value().get());
				if (params.stream().anyMatch(t -> t.name().equals("days"))
						&& !params.stream().filter(t -> t.name().equals("days")).findFirst().get().value().isAbsent())
					days = Integer.parseInt(
							params.stream().filter(t -> t.name().equals("days")).findFirst().get().value().get());
				event.deferReply().block();
				acc.mute(minutes, hours, days, modacc.getAccountID(), null);
				event.editReply("Muted player " + acc.getDisplayName()).block();
				break;
			}
			case "pardon": {
				// Required permissions: mod (ingame)
				CenturiaAccount modacc = LinkUtils
						.getAccountByDiscordID(event.getInteraction().getUser().getId().asString());
				if (modacc == null) {
					event.reply("**Error:** You dont have a Centuria account linked to your Discord account").block();
					return Mono.empty();
				}

				String permLevel = "member";
				if (modacc.getSaveSharedInventory().containsItem("permissions")) {
					permLevel = modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
							.get("permissionLevel").getAsString();
				}
				if (!GameServer.hasPerm(permLevel, "moderator")) {
					event.reply("**Error:** no Centuria moderator permissions.").block();
					return Mono.empty();
				}

				// Find player UUID
				var params = data.options().get().get(0).options().get();
				String uuid = AccountManager.getInstance().getUserByDisplayName(params.get(0).value().get());
				if (uuid == null) {
					// Respond with error message
					event.reply("**Error:** player not recognized.").block();
					return Mono.empty();
				}
				CenturiaAccount acc = AccountManager.getInstance().getAccount(uuid);
				if (acc == null) {
					// Respond with error message
					event.reply("**Error:** player not recognized.").block();
					return Mono.empty();
				}
				if (!acc.isBanned() && !acc.isMuted()) {
					event.reply("**Error:** player has no penalties.").block();
					return Mono.empty();
				}

				// Check rank
				if (acc.getSaveSharedInventory().containsItem("permissions")) {
					if ((GameServer
							.hasPerm(modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
									.get("permissionLevel").getAsString(), "developer")
							&& !GameServer.hasPerm(permLevel, "developer"))
							|| GameServer
									.hasPerm(modacc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
											.get("permissionLevel").getAsString(), "admin")
									&& !GameServer.hasPerm(permLevel, "admin")) {
						event.reply("**Error:** unable to moderate higher-ranking members.").block();
						return Mono.empty();
					}
				}

				// Pardon
				if (params.size() == 1) {
					event.deferReply().block();
					acc.pardon(modacc.getAccountID(), null);
					event.editReply("Pardoned player " + acc.getDisplayName()).block();
				} else if (params.size() == 2) {
					event.deferReply().block();
					acc.pardon(modacc.getAccountID(), params.get(1).value().get());
					event.editReply("Pardoned player " + acc.getDisplayName()).block();
				}
				break;
			}
			case "getaccountinfo": {
				// Required permissions: mod (ingame)
				CenturiaAccount acc = LinkUtils
						.getAccountByDiscordID(event.getInteraction().getUser().getId().asString());
				if (acc == null) {
					event.reply("**Error:** You dont have a Centuria account linked to your Discord account").block();
					return Mono.empty();
				}

				String permLevel = "member";
				if (acc.getSaveSharedInventory().containsItem("permissions")) {
					permLevel = acc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
							.get("permissionLevel").getAsString();
				}
				if (!GameServer.hasPerm(permLevel, "moderator")) {
					event.reply("**Error:** no Centuria moderator permissions.").block();
					return Mono.empty();
				}

				// Find member
				String userID = data.options().get().get(0).options().get().get(0).value().get();
				CenturiaAccount account = LinkUtils.getAccountByDiscordID(userID);
				if (account != null) {
					// Build message
					String msg = "Centuria account details:\n";
					msg += "**Ingame display name**: `" + account.getDisplayName() + "`\n";
					msg += "**Last login**: " + (account.getLastLoginTime() == -1 ? "`Unknown`"
							: "<t:" + account.getLastLoginTime() + ">") + "\n";
					msg += "**Status:** " + (account.isBanned() ? "banned"
							: (account.isMuted() ? "muted"
									: (account.getOnlinePlayerInstance() != null ? "online" : "offline")));
					event.reply(msg).subscribe();
				} else {
					// Return error
					event.reply("The given member has no Centuria account linked to their Discord account.")
							.subscribe();
				}
			}
			case "setup": {
				// Required permissions: admin (ingame), admin (discord)
				CenturiaAccount acc = LinkUtils
						.getAccountByDiscordID(event.getInteraction().getUser().getId().asString());
				if (acc == null) {
					event.reply(
							"**Error:** You dont have a Centuria account linked to your Discord account, if you are the owner of the Discord and Centuria server,\nplease link your account manually if the panel is not yet made.\n"
									+ "\n" + "To manually link your account (requires game server ownership):\n"
									+ "1. edit `accountlink.json`\n" + "2. add the following line between the `{}`: `\""
									+ event.getInteraction().getUser().getId().asString()
									+ "\":\"<insert-account-uuid>\"`\n"
									+ "3. go into the inventories folder, your account UUID, and create a new file: `pairedaccount.json`\n"
									+ "4. write the following to it: `{\"userId\":\""
									+ event.getInteraction().getUser().getId().asString() + "\"}`\n"
									+ "5. restart the server")
							.block();
					return Mono.empty();
				}

				// Check permissions
				if (!event.getInteraction().getUser().asMember(guild.getId()).block().getBasePermissions().block()
						.contains(Permission.ADMINISTRATOR)) {
					event.reply("**Error:** no Discord administrative permissions.").block();
					return Mono.empty();
				}
				String permLevel = "member";
				if (acc.getSaveSharedInventory().containsItem("permissions")) {
					permLevel = acc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
							.get("permissionLevel").getAsString();
				}
				if (!GameServer.hasPerm(permLevel, "admin")) {
					event.reply("**Error:** no Centuria administrative permissions.").block();
					return Mono.empty();
				}

				// Create message
				InteractionApplicationCommandCallbackSpec.Builder msg = InteractionApplicationCommandCallbackSpec
						.builder();

				// Message content
				msg.content(
						"**Centuria server configuration.**\nPlease select below which setting you wish to change.");

				// Dropdown
				msg.addComponent(ActionRow.of(SelectMenu.of("serverconfig", Option.of("Moderator role", "modrole"),
						Option.of("Developer role", "devrole"), Option.of("Announcement ping role", "announcementrole"),
						Option.of("Announcement channel", "announcementchannel"),
						Option.of("Member report review channel", "reportchannel"),
						Option.of("Moderation log channel", "moderationlogchannel"),
						Option.of("Feedback review channel", "feedbackchannel"))));
				msg.ephemeral(true);

				// Send message
				event.reply(msg.build()).block();
			}
			case "createaccountpanel": {
				// Required permissions: admin (ingame), admin (discord)
				CenturiaAccount acc = LinkUtils
						.getAccountByDiscordID(event.getInteraction().getUser().getId().asString());
				if (acc == null) {
					event.reply(
							"**Error:** You dont have a Centuria account linked to your Discord account, if you are the owner of the Discord and Centuria server,\nplease link your account manually if the panel is not yet made.\n"
									+ "\n" + "To manually link your account (requires game server ownership):\n"
									+ "1. edit `accountlink.json`\n" + "2. add the following line between the `{}`: `\""
									+ event.getInteraction().getUser().getId().asString()
									+ "\":\"<insert-account-uuid>\"`\n"
									+ "3. go into the inventories folder, your account UUID, and create a new file: `pairedaccount.json`\n"
									+ "4. write the following to it: `{\"userId\":\""
									+ event.getInteraction().getUser().getId().asString() + "\"}`\n"
									+ "5. restart the server")
							.block();
					return Mono.empty();
				}

				// Check permissions
				if (!event.getInteraction().getUser().asMember(guild.getId()).block().getBasePermissions().block()
						.contains(Permission.ADMINISTRATOR)) {
					event.reply("**Error:** no Discord administrative permissions.").block();
					return Mono.empty();
				}
				String permLevel = "member";
				if (acc.getSaveSharedInventory().containsItem("permissions")) {
					permLevel = acc.getSaveSharedInventory().getItem("permissions").getAsJsonObject()
							.get("permissionLevel").getAsString();
				}
				if (!GameServer.hasPerm(permLevel, "admin")) {
					event.reply("**Error:** no Centuria administrative permissions.").block();
					return Mono.empty();
				}

				// Show modal
				event.presentModal("Account Panel Creation", "createaccountpanel",
						Arrays.asList(ActionRow.of(TextInput.paragraph("message", "Message description")))).block();

				break;
			}
			}
		}
		return Mono.empty();
	}

}