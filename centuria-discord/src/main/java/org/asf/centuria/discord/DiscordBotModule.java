package org.asf.centuria.discord;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Scanner;

import org.asf.centuria.Centuria;
import org.asf.centuria.discord.applications.ApplicationManager;
import org.asf.centuria.discord.handlers.api.AppealHandler;
import org.asf.centuria.discord.handlers.api.AppealStatusHandler;
import org.asf.centuria.discord.handlers.api.ForgotPasswordHandler;
import org.asf.centuria.discord.handlers.discord.CommandHandler;
import org.asf.centuria.discord.handlers.discord.InteractionButtonHandler;
import org.asf.centuria.discord.handlers.discord.InteractionModalHandler;
import org.asf.centuria.discord.handlers.discord.InteractionSelectMenuHandler;
import org.asf.centuria.discord.handlers.game.ModuleCommands;
import org.asf.centuria.discord.handlers.registration.DiscordRegistrationHelper;
import org.asf.centuria.discord.handlers.game.AnnouncementHandlers;
import org.asf.centuria.discord.handlers.game.GamePacketHandlers;
import org.asf.centuria.discord.handlers.game.LoginEventHandler;
import org.asf.centuria.discord.handlers.game.ModerationHandlers;
import org.asf.centuria.modules.ICenturiaModule;
import org.asf.centuria.modules.eventbus.EventBus;
import org.asf.centuria.modules.eventbus.EventListener;
import org.asf.centuria.modules.events.accounts.AccountDeletionEvent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.guild.MemberLeaveEvent;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.core.object.presence.Status;
import discord4j.discordjson.json.ApplicationCommandData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DiscordBotModule implements ICenturiaModule {

	// Discord bot
	private static GatewayDiscordClient gateway;
	private static DiscordClient client;

	// Server name
	private static String serverName;

	// Ban memory
	public static HashMap<String, JsonObject> penalties = new HashMap<String, JsonObject>();

	/**
	 * Retrieves the server name
	 * 
	 * @return Server name string
	 */
	public static String getServerName() {
		return serverName;
	}

	@Override
	public String id() {
		return "centuria-discord";
	}

	@Override
	public String version() {
		return "1.0.0.A1";
	}

	/**
	 * Retrieves the discord gateway client
	 * 
	 * @return GatewayDiscordClient instance
	 */
	public static GatewayDiscordClient getClient() {
		return gateway;
	}

	@Override
	public void preInit() {
		// Bind event handlers
		EventBus.getInstance().addEventReceiver(new ModuleCommands());
		EventBus.getInstance().addEventReceiver(new ModerationHandlers());
		EventBus.getInstance().addEventReceiver(new ForgotPasswordHandler());
		EventBus.getInstance().addEventReceiver(new LoginEventHandler());
		EventBus.getInstance().addEventReceiver(new AnnouncementHandlers());
		EventBus.getInstance().addEventReceiver(new GamePacketHandlers());
		EventBus.getInstance().addEventReceiver(new AppealHandler());
		EventBus.getInstance().addEventReceiver(new AppealStatusHandler());

		// Applications
		if (!new File("applications/codes").exists())
			new File("applications/codes").mkdirs();
		if (!new File("applications/active").exists())
			new File("applications/active").mkdirs();
		if (!new File("applications/applied").exists())
			new File("applications/applied").mkdirs();
		if (!new File("applications/panels").exists())
			new File("applications/panels").mkdirs();

		// Init registration helper
		DiscordRegistrationHelper.init();

		// Disable default registration
		Centuria.allowRegistration = false;

		// Load penalties
		File penaltyFile = new File("deletedaccountpenalties.json");
		if (penaltyFile.exists()) {
			JsonObject penaltyData;
			try {
				penaltyData = JsonParser.parseString(Files.readString(penaltyFile.toPath())).getAsJsonObject();
			} catch (JsonSyntaxException | IOException e) {
				throw new RuntimeException(e);
			}
			penaltyData.keySet().forEach(t -> {
				penalties.put(t, penaltyData.get(t).getAsJsonObject());
			});
		} else
			writePenalties();

		// Load config file path as file object
		File configFile = new File("discordbot.json");

		// Create config if non-existent
		if (!configFile.exists()) {
			// Build config
			JsonObject config = new JsonObject();

			// Ask for the token
			System.out.print("Discord bot token: ");
			@SuppressWarnings("resource")
			String tkn = new Scanner(System.in).nextLine();

			// Save config
			config.addProperty("token", tkn);
			config.addProperty("serverName", "Centuria");

			// Write file
			try {
				Files.writeString(configFile.toPath(),
						new Gson().newBuilder().setPrettyPrinting().create().toJson(config));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		try {
			System.out.println("Configuring discord bot client...");

			// Load config
			JsonObject config = JsonParser.parseString(Files.readString(configFile.toPath())).getAsJsonObject();
			serverName = config.get("serverName").getAsString();

			// Build the client
			client = DiscordClient.create(config.get("token").getAsString());

			// Create commands
			ApplicationCommandRequest cmd = ApplicationCommandRequest.builder().name("centuria")
					.description("Centuria Commands").addOption(CommandHandler.setupCommand())
					.addOption(CommandHandler.createAccountPanel()).addOption(CommandHandler.getAccountInfo())
					.addOption(CommandHandler.getDiscordAccountInfo()).addOption(CommandHandler.kick())
					.addOption(CommandHandler.ban()).addOption(CommandHandler.tempBan())
					.addOption(CommandHandler.pardon()).addOption(CommandHandler.generateClearanceCode())
					.addOption(CommandHandler.mute()).addOption(CommandHandler.ipBan())
					.addOption(CommandHandler.makeModerator()).addOption(CommandHandler.makeAdmin())
					.addOption(CommandHandler.removePerms()).addOption(CommandHandler.dmAnonymous())
					.addOption(CommandHandler.generateApplicationCode()).addOption(CommandHandler.apply())
					.addOption(CommandHandler.createApplicationPanel()).build();

			// Connect
			client.gateway().setEnabledIntents(IntentSet.of(Intent.GUILD_PRESENCES, Intent.GUILD_MESSAGES,
					Intent.DIRECT_MESSAGES, Intent.GUILD_MEMBERS, Intent.GUILDS)).withGateway(gateway -> {
						// Button handler
						Mono<Void> ev = gateway.on(ButtonInteractionEvent.class, event -> {
							return InteractionButtonHandler.handle(event, gateway);
						}).then();

						// Join guild handler
						ev = ev.then().and(gateway.on(GuildCreateEvent.class, event -> {
							Flux<ApplicationCommandData> cmds = gateway.getRestClient().getApplicationService()
									.getGuildApplicationCommands(gateway.getRestClient().getApplicationId().block(),
											event.getGuild().getId().asLong());
							boolean found = cmds.any(t -> t.name().equals("centuria")).block();
							if (found) {
								ApplicationCommandData cmd2 = cmds.filter(t -> t.name().equals("centuria"))
										.blockFirst();
								if (cmd2.options().get().size() != cmd.options().get().size()) {
									// Remove
									try {
										gateway.getRestClient().getApplicationService().deleteGuildApplicationCommand(
												gateway.getRestClient().getApplicationId().block(),
												event.getGuild().getId().asLong(), cmd2.id().asLong());
										found = false;
									} catch (Exception e) {
										// Server did not grant the ability to create commands, lets not crash
									}
								}
							}
							if (!found)
								try {
									gateway.getRestClient().getApplicationService()
											.createGuildApplicationCommand(
													gateway.getRestClient().getApplicationId().block(),
													event.getGuild().getId().asLong(), cmd)
											.block();
								} catch (Exception e) {
									// Server did not grant the ability to create commands, lets not crash
								}
							return Mono.empty();
						}));

						// Select menu handler
						ev = ev.then().and(gateway.on(SelectMenuInteractionEvent.class, event -> {
							return InteractionSelectMenuHandler.handle(event, gateway);
						}));

						// Form handler
						ev = ev.then().and(gateway.on(ModalSubmitInteractionEvent.class, event -> {
							return InteractionModalHandler.handle(event, gateway);
						}));

						// Slash command handler
						ev = ev.then().and(gateway.on(ApplicationCommandInteractionEvent.class, event -> {
							Guild guild = event.getInteraction().getGuild().block();
							return CommandHandler.handle(event, guild, gateway);
						}));

						// User leave
						ev = ev.then().and(gateway.on(MemberLeaveEvent.class, event -> {
							ApplicationManager.cancelApplication(event.getUser(), true, "user left server");
							return Mono.empty();
						}));

						// Command handler
						ev = ev.then().and(gateway.on(MessageCreateEvent.class, event -> {
							Guild g = event.getGuild().block();
							if (g != null) {
								// Only accept commands from servers
								String msg = event.getMessage().getContent();

								// Only handle commands
								if (msg.startsWith("emuferal!")) {
									// Inform commands have moved
									return event.getMessage().getChannel().block().createMessage("**IMPORTANT**\n"
											+ "As of 08/17/2022, text commands have been removed.\n\nPlease use the new slash commands instead."
											+ "\nFurhtermore, command permissions __no longer use Discord permissions only, it checks for ingame permissions too.__");
								}
								if (msg.startsWith("centuria!")) {
									// Inform commands have moved
									return event.getMessage().getChannel().block().createMessage("**IMPORTANT**\n"
											+ "As of 08/17/2022, text commands have been removed.\n\nPlease use the new slash commands instead."
											+ "\nFurhtermore, command permissions __no longer use Discord permissions only, it checks for ingame permissions too.__");
								}
							}
							return Mono.empty();
						}));

						// Startup
						return gateway.on(ReadyEvent.class, t -> {
							return Mono.fromRunnable(() -> {
								DiscordBotModule.gateway = gateway;
							});
						}).then().and(ev);
					}).subscribe();

			// Bind shutdown
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				if (gateway != null) {
					// Set status to offline
					gateway.updatePresence(ClientPresence.invisible()).block();

					// Close client connection
					gateway.logout();
				}
			}));

			// Wait for the gateway connection to be ready
			while (gateway == null)
				Thread.sleep(100);

			// Set status to starting
			gateway.updatePresence(ClientPresence.of(Status.DO_NOT_DISTURB,
					ClientActivity.listening(config.get("serverName").getAsString() + " server startup..."))).block();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		// Load link database
		LinkUtils.init();
		ApplicationManager.start();
	}

	public static synchronized void writePenalties() {
		File penaltyFile = new File("deletedaccountpenalties.json");
		JsonObject res = new JsonObject();
		penalties.keySet().forEach(t -> res.add(t, penalties.get(t)));
		try {
			Files.writeString(penaltyFile.toPath(), res.toString());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void init() {
	}

	public static JsonObject getPenaltyFromMemory(String discord) {
		if (penalties.containsKey(discord)) {
			JsonObject obj = penalties.remove(discord);
			writePenalties();
			return obj;
		}
		return null;
	}

	@Override
	public void postInit() {
		// Set as online
		gateway.updatePresence(ClientPresence.of(Status.ONLINE, ClientActivity.listening(""))).block();
	}

	@EventListener
	public void handleDeleteAccount(AccountDeletionEvent event) {
		if (LinkUtils.isPairedWithDiscord(event.getAccount())) {
			LinkUtils.unpairAccount(event.getAccount(), null, false);
		}
	}

}
