package org.asf.centuria.discord.applications;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.asf.centuria.accounts.AccountManager;
import org.asf.centuria.accounts.CenturiaAccount;
import org.asf.centuria.accounts.PlayerInventory;
import org.asf.centuria.accounts.SaveMode;
import org.asf.centuria.discord.DiscordBotModule;
import org.asf.centuria.discord.LinkUtils;
import org.asf.centuria.entities.players.Player;
import org.asf.centuria.networking.gameserver.GameServer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.LayoutComponent;
import discord4j.core.object.component.TextInput;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.PrivateChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.InteractionCallbackSpec;
import discord4j.core.spec.InteractionPresentModalSpec;
import discord4j.core.spec.InteractionReplyEditSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.spec.MessageEditSpec;
import discord4j.discordjson.json.ComponentData;
import discord4j.discordjson.json.ImmutableMessageEditRequest.Builder;
import discord4j.discordjson.json.MessageEditRequest;
import discord4j.discordjson.json.MessageReferenceData;
import discord4j.rest.entity.RestChannel;
import discord4j.rest.entity.RestMessage;
import discord4j.rest.util.Color;
import discord4j.rest.util.Image.Format;
import reactor.core.publisher.Mono;

public class ApplicationManager {

	private static ArrayList<PanelInfo> panels = new ArrayList<PanelInfo>();

	/**
	 * Starts the application manager
	 */
	public static void start() {
		// Find panels
		for (File f : new File("applications/panels")
				.listFiles(t -> !t.isDirectory() && t.getName().endsWith(".json"))) {
			try {
				// Read JSON
				JsonObject panelJson = JsonParser.parseString(Files.readString(f.toPath())).getAsJsonObject();
				PanelInfo panel = new PanelInfo();
				panel.guildID = panelJson.get("guild").getAsLong();
				panel.channelID = panelJson.get("channel").getAsLong();
				panel.messageID = panelJson.get("message").getAsLong();
				String application = panelJson.get("application").getAsString();

				// Load application
				JsonObject data;
				try {
					data = JsonParser.parseString(Files.readString(Path.of("applications/" + application + ".json")))
							.getAsJsonObject();
				} catch (IOException e) {
					return;
				}
				ApplicationDefinition def = new ApplicationDefinition().fromJson(data);
				panel.def = def;
				panel.application = application;

				// Add panel
				panels.add(panel);

				// Refresh panel
				refreshPanel(panel);
			} catch (JsonSyntaxException | IOException e) {
				f.delete(); // Invalid
			}
		}

		// Run refresh thread
		Thread th = new Thread(() -> {
			while (true) {
				// Refresh panels
				PanelInfo[] panels;
				while (true) {
					try {
						panels = ApplicationManager.panels.toArray(t -> new PanelInfo[t]);
						break;
					} catch (ConcurrentModificationException e) {
					}
				}
				for (PanelInfo panel : panels) {
					try {
						refreshPanel(panel);
					} catch (Exception e) {
						// Prevent crash
						e.printStackTrace();
					}
				}

				try {
					// Wait 15 seconds
					Thread.sleep(9000000);
				} catch (InterruptedException e) {
					break;
				}
			}
		}, "Panel refresh");
		th.setDaemon(true);
		th.start();
	}

	// Refreshes a application panel
	private static void refreshPanel(PanelInfo panel) {
		// Find guild
		Guild guild = DiscordBotModule.getClient().getGuildById(Snowflake.of(panel.guildID)).block();

		// Find channel
		GuildChannel channel = guild.getChannelById(Snowflake.of(panel.channelID)).block();
		if (channel == null) {
			// Delete panel file this is likely a deleted panel
			new File("applications/panels/" + panel.messageID + ".json").delete();
			ApplicationManager.panels.remove(panel);
		}

		// Find message
		RestMessage message = channel.getRestChannel().message(Snowflake.of(panel.messageID));
		if (message == null) {
			// Delete panel file this is likely a deleted panel
			new File("applications/panels/" + panel.messageID + ".json").delete();
			ApplicationManager.panels.remove(panel);
		}

		// Try to update
		try {
			// Check fields
			String status;
			Color color;
			boolean disabled;
			if (System.currentTimeMillis() > panel.def.deadline) {
				status = "Status: past deadline";
				disabled = true;
				color = Color.RED;
			} else if (new File("applications/applied/" + panel.application).exists()
					&& new File("applications/applied/" + panel.application)
							.listFiles().length >= panel.def.applicantLimit) {
				status = "Status: applicant limit reached";
				disabled = true;
				color = Color.RED;
			} else {
				int current = 0;
				if (new File("applications/applied/" + panel.application).exists())
					current += new File("applications/applied/" + panel.application).listFiles().length;
				if (new File("applications/active/" + panel.application).exists())
					current += new File("applications/active/" + panel.application).listFiles().length;
				if (new File("applications/active/" + panel.application).exists()
						&& current >= panel.def.applicantLimit) {
					status = "Status: applicant limit reached (review limit reached, might become available again)";
					disabled = true;
					color = Color.ORANGE;
				} else {
					status = "Available (" + (panel.def.applicantLimit - current) + " applications left)";
					disabled = false;
					color = Color.GREEN;
				}
			}
			if (!status.equals(panel.lastStatus)) {
				// Update
				panel.lastStatus = status;

				// Create message update
				Builder msg = MessageEditRequest.builder();

				// Build embed
				EmbedCreateSpec.Builder embed = EmbedCreateSpec.builder();
				embed.color(color);
				embed.thumbnail(guild.getIconUrl(Format.PNG)
						.orElse(DiscordBotModule.getClient().getSelf().block().getAvatarUrl()));
				embed.title(panel.def.name);
				embed.description(message.getData().block().embeds().get(0).description().get());
				embed.footer(status, DiscordBotModule.getClient().getSelf().block().getAvatarUrl());
				msg.embeds(Arrays.asList(embed.build().asRequest()));

				// Buttons
				if (disabled)
					msg.components(Arrays.asList(ActionRow
							.of(Button.success("application/applyfor/" + panel.application, "Apply").disabled())
							.getData()));
				else
					msg.components(Arrays.asList(ActionRow
							.of(Button.success("application/applyfor/" + panel.application, "Apply")).getData()));

				// Update
				message.edit(msg.build()).block();
			}
		} catch (Exception e) {
			// Delete panel file this is likely a deleted panel
			new File("applications/panels/" + panel.messageID + ".json").delete();
			ApplicationManager.panels.remove(panel);
		}
	}

	/**
	 * Checks if a user is already applying to a application
	 * 
	 * @param user User to check
	 * @return True if the user is applying, false otherwise
	 */
	public static boolean isApplying(User user) {
		return new File("applications/active/" + user.getId().asString() + ".json").exists();
	}

	/**
	 * Checks if a user has applied to a specific application
	 * 
	 * @param application Application ID
	 * @param user        User to check
	 * @return True if the user has applied, false otherwise
	 */
	public static boolean hasApplied(String application, User user) {
		if (!application.matches("^[0-9a-zA-Z]+$") || !new File("applications/" + application + ".json").exists())
			return false;
		return new File("applications/applied/" + application + "/" + user.getId().asString() + ".json").exists();
	}

	/**
	 * Starts a application
	 * 
	 * @param application Application ID
	 * @param user        User starting the application
	 * @return True if successful, false otherwise
	 */
	public static boolean startApplication(String application, User user) {
		if (!application.matches("^[0-9a-zA-Z]+$") || !new File("applications/" + application + ".json").exists()
				|| hasApplied(application, user) || isApplying(user))
			return false;
		CenturiaAccount account = LinkUtils.getAccountByDiscordID(user.getId().asString());
		if (account == null || account.isBanned() || account.isMuted())
			return false;

		// Load application
		JsonObject data;
		try {
			data = JsonParser.parseString(Files.readString(Path.of("applications/" + application + ".json")))
					.getAsJsonObject();
		} catch (IOException e) {
			return false;
		}
		ApplicationDefinition def = new ApplicationDefinition().fromJson(data);

		// Send DM
		try {
			EmbedCreateSpec.Builder embed = EmbedCreateSpec.builder();

			// Embed
			embed.title("Application: " + def.name);
			embed.color(Color.BLUE);
			embed.description("Do you wish to begin the application process?");
			embed.footer(DiscordBotModule.getServerName(),
					DiscordBotModule.getClient().getSelf().block().getAvatarUrl());

			// Message object
			MessageCreateSpec.Builder msg = MessageCreateSpec.builder();
			msg.addEmbed(embed.build());

			// Buttons
			msg.addComponent(ActionRow.of(Button.success("application/movenext", "Start application"),
					Button.primary("application/cancel", "Cancel application")));

			// Send message
			user.getPrivateChannel().block().createMessage(msg.build()).subscribe();

			// Create application memory object
			JsonObject memory = new JsonObject();
			JsonObject fields = new JsonObject();
			memory.addProperty("id", application);
			memory.add("application", data);
			memory.addProperty("index", 0);
			memory.add("fields", fields);
			try {
				new File("applications/active/" + application).mkdirs();
				Files.writeString(Path.of("applications/active/" + user.getId().asString() + ".json"),
						memory.toString());
				Files.writeString(Path.of("applications/active/" + application + "/" + user.getId().asString()), "");
			} catch (IOException e) {
				return false;
			}

			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Handles application form submission event
	 * 
	 * @param id      Modal ID (without the application prefix)
	 * @param event   Modal submission event
	 * @param gateway Discord client
	 * @return Result Mono object
	 */
	public static Mono<?> handleModal(String id, ModalSubmitInteractionEvent event, GatewayDiscordClient gateway) {
		// Load application memory
		JsonObject memory;
		try {
			memory = JsonParser
					.parseString(Files.readString(Path.of(
							"applications/active/" + event.getInteraction().getUser().getId().asString() + ".json")))
					.getAsJsonObject();
		} catch (Exception e) {
			return Mono.empty();
		}
		ApplicationDefinition def = new ApplicationDefinition().fromJson(memory.get("application").getAsJsonObject());
		int index = memory.get("index").getAsInt();
		JsonObject fields = memory.get("fields").getAsJsonObject();
		User user = event.getInteraction().getUser();
		CenturiaAccount account = LinkUtils.getAccountByDiscordID(user.getId().asString());
		if (account == null)
			return event.reply("**Error:** No Centuria account linked with your Discord account.");

		// Handle button
		String params = "";
		if (id.contains("/")) {
			params = id.substring(id.indexOf("/") + 1);
			id = id.substring(0, id.indexOf("/"));
		}

		switch (id) {

		// Form
		case "form": {
			// Save form
			if (def.application.get(index - 1).parameters.has("key")) {
				JsonObject res = new JsonObject();
				event.getInteraction().getData().data().get().components().get().forEach(entry -> {
					ComponentData comp = entry.components().get().get(0);
					res.addProperty(comp.customId().get(), comp.value().get());
				});
				fields.add(def.application.get(index - 1).parameters.get("key").getAsString(), res);
				try {
					Files.writeString(Path.of("applications/active/" + user.getId().asString() + ".json"),
							memory.toString());
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}

			// Call button handler for movenext
			return handleButton("movenext", event, gateway);
		}

		// Error
		default: {
			return event.reply(InteractionApplicationCommandCallbackSpec.builder()
					.addEmbed(EmbedCreateSpec.builder().title("Error occured!")
							.addField("Error", "This form was not handled", true).addField("Button ID", id, true)
							.addField("Button parameters", params, true).color(Color.RED).build())
					.build());
		}

		}
	}

	/**
	 * Cancels applications for a user
	 * 
	 * @param user   User to cancel the application for
	 * @param force  Cancel even if the application hasn't been finished
	 * @param reason Cancel reason
	 */
	public static void cancelApplication(User user, boolean force, String reason) {
		if (isApplying(user)) {
			// Load application memory
			JsonObject memory;
			try {
				memory = JsonParser
						.parseString(
								Files.readString(Path.of("applications/active/" + user.getId().asString() + ".json")))
						.getAsJsonObject();
			} catch (Exception e) {
				return;
			}

			// Check
			if (memory.has("submissionID") || force) {
				ApplicationDefinition def = new ApplicationDefinition()
						.fromJson(memory.get("application").getAsJsonObject());

				// Cancel application
				new File("applications/active/" + user.getId().asString() + ".json").delete();
				new File("applications/active/" + memory.get("id").getAsString() + "/" + user.getId().asString())
						.delete();

				if (memory.has("submissionID")) {
					// Edit submission message
					try {
						// Create updated embed
						EmbedCreateSpec.Builder embed = EmbedCreateSpec.builder();
						embed.title("Review application");
						embed.footer("Status: cancelled, reason: " + reason, null);
						embed.color(Color.RED);
						embed.thumbnail(user.getAvatarUrl());
						embed.description("Here you can review the application, select action below to review");

						// Get channel and update
						Guild guild = DiscordBotModule.getClient().getGuildById(Snowflake.of(def.reviewServer)).block();
						RestChannel ch = guild.getChannelById(Snowflake.of(def.reviewChannel)).block().getRestChannel();
						ch.getRestMessage(Snowflake.of(memory.get("submissionID").getAsLong()))
								.edit(MessageEditSpec.builder().embeds(Arrays.asList(embed.build()))
										.components(new ArrayList<LayoutComponent>()).build().asRequest()
										.getJsonPayload())
								.block();
					} catch (Exception e) {
					}
				}

				try {
					EmbedCreateSpec.Builder embed = EmbedCreateSpec.builder();

					// Embed
					embed.title("Application Cancelled");
					embed.color(Color.RED);
					embed.description("The application has been cancelled.\nReason: " + reason);
					embed.footer(DiscordBotModule.getServerName(),
							DiscordBotModule.getClient().getSelf().block().getAvatarUrl());

					// Message object
					MessageCreateSpec.Builder msg = MessageCreateSpec.builder();
					msg.addEmbed(embed.build());

					// Send response
					user.getPrivateChannel().block().createMessage(msg.build()).subscribe();
				} catch (Exception e) {
				}
			}
		}
	}

	/**
	 * Handles interaction buttons for applications
	 * 
	 * @param id      Button ID (without the application prefix)
	 * @param event   Button event
	 * @param gateway Gateway client
	 */
	public static Mono<?> handleButton(String id, ComponentInteractionEvent event, GatewayDiscordClient gateway) {
		// Parse
		String params = "";
		if (id.contains("/")) {
			params = id.substring(id.indexOf("/") + 1);
			id = id.substring(0, id.indexOf("/"));
		}

		// Handle accept/reject/etc
		switch (id) {

		// Apply
		case "applyfor": {
			CenturiaAccount account = LinkUtils
					.getAccountByDiscordID(event.getInteraction().getUser().getId().asString());
			if (account == null) {
				event.reply("**Error:** You dont have a Centuria account linked to your Discord account")
						.withEphemeral(true).block();
				return Mono.empty();
			}

			// Load parameters
			String application = params;
			int current = 0;
			if (new File("applications/applied/" + application).exists())
				current += new File("applications/applied/" + application).listFiles().length;
			if (new File("applications/active/" + application).exists())
				current += new File("applications/active/" + application).listFiles().length;

			// Check
			if (ApplicationManager.isApplying(event.getInteraction().getUser()))
				return event.reply(InteractionApplicationCommandCallbackSpec
						.builder().addEmbed(EmbedCreateSpec.builder()
								.title("You can only apply for one application at a time.").color(Color.RED).build())
						.ephemeral(true).build());
			if (ApplicationManager.hasApplied(application, event.getInteraction().getUser()))
				return event.reply(InteractionApplicationCommandCallbackSpec
						.builder().addEmbed(EmbedCreateSpec.builder()
								.title("You have already applied for this application.").color(Color.RED).build())
						.ephemeral(true).build());

			// Load application
			JsonObject data;
			try {
				data = JsonParser.parseString(Files.readString(Path.of("applications/" + application + ".json")))
						.getAsJsonObject();
			} catch (IOException e) {
				return Mono.empty();
			}
			ApplicationDefinition def = new ApplicationDefinition().fromJson(data);
			if (current >= def.applicantLimit) {
				// Limit reached
				return event.reply(InteractionApplicationCommandCallbackSpec.builder()
						.addEmbed(EmbedCreateSpec.builder()
								.title("This application is presently full. Check again later.").color(Color.RED)
								.build())
						.ephemeral(true).build());
			}

			// Start application
			event.deferReply(InteractionCallbackSpec.builder().ephemeral(true).build()).block();
			if (!ApplicationManager.startApplication(application, event.getInteraction().getUser()))
				return event.editReply(InteractionReplyEditSpec.builder()
						.addEmbed(EmbedCreateSpec.builder().title("Error").description(
								"An unexpected error occured, are your dms open?\n\nIf they aren't open the cause of the error is likely that, however if your dms are actually open then this is a server error.")
								.color(Color.RED).build())
						.build());
			return event.editReply(InteractionReplyEditSpec.builder()
					.addEmbed(EmbedCreateSpec.builder().title("Application started in DM").color(Color.GREEN).build())
					.build());
		}

		// Handle reject
		case "accept": {
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
				event.reply("**Error:** No Centuria moderator permissions.").block();
				return Mono.empty();
			}

			// Load parameters
			String application = params.split("/")[0];
			String userID = params.split("/")[1];
			CenturiaAccount account = LinkUtils.getAccountByDiscordID(userID);
			if (account == null)
				return event.reply("**Error:** The member has no Centuria account.");

			// Get user
			User user = DiscordBotModule.getClient().getUserById(Snowflake.of(userID)).block();

			// Load application memory
			JsonObject memory;
			try {
				memory = JsonParser.parseString(Files.readString(Path.of("applications/active/" + userID + ".json")))
						.getAsJsonObject();
			} catch (Exception e) {
				return Mono.empty();
			}

			// Defer
			event.deferReply().block();

			try {
				// Run rejection chain in DM
				PrivateChannel pCh = user.getPrivateChannel().block();
				ApplicationDefinition def = new ApplicationDefinition()
						.fromJson(memory.get("application").getAsJsonObject());

				// Handle commands
				Mono<?> res = handleCmds(event, def.accept, pCh, def, memory, user, account);
				if (res != null) {
					return res;
				}
			} catch (Exception e) {
			}

			if (memory.has("submissionID")) {
				// Edit submission message
				try {
					// Create updated embed
					EmbedCreateSpec.Builder embed = EmbedCreateSpec.builder();
					embed.title("Review application");
					embed.footer("Status: accepted by " + event.getInteraction().getUser().getTag(),
							event.getInteraction().getUser().getAvatarUrl());
					embed.color(Color.GREEN);
					embed.thumbnail(user.getAvatarUrl());
					embed.description("Here you can review the application, select action below to review");

					// Get channel and update
					MessageChannel ch = event.getInteraction().getChannel().block();
					ch.getMessageById(Snowflake.of(memory.get("submissionID").getAsLong())).block().edit()
							.withEmbeds(embed.build()).withComponents().block();
				} catch (Exception e) {
				}
			}

			// Move application
			new File("applications/active/" + application + "/" + userID).delete();
			new File("applications/active/" + userID + ".json").delete();
			new File("applications/applied/" + application).mkdirs();
			try {
				Files.writeString(
						Path.of("applications/applied/" + application + "/" + user.getId().asString() + ".json"),
						memory.toString());
			} catch (IOException e1) {
				throw new RuntimeException(e1);
			}

			// Delete current message
			return event.deleteReply();
		}

		// Handle reject
		case "reject": {
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
				event.reply("**Error:** No Centuria moderator permissions.").block();
				return Mono.empty();
			}

			// Load parameters
			String application = params.split("/")[0];
			String userID = params.split("/")[1];
			CenturiaAccount account = LinkUtils.getAccountByDiscordID(userID);
			if (account == null)
				return event.reply("**Error:** The member has no Centuria account.");

			// Get user
			User user = DiscordBotModule.getClient().getUserById(Snowflake.of(userID)).block();

			// Load application memory
			JsonObject memory;
			try {
				memory = JsonParser.parseString(Files.readString(Path.of("applications/active/" + userID + ".json")))
						.getAsJsonObject();
			} catch (Exception e) {
				return Mono.empty();
			}

			// Defer
			event.deferReply().block();

			try {
				// Run rejection chain in DM
				PrivateChannel pCh = user.getPrivateChannel().block();
				ApplicationDefinition def = new ApplicationDefinition()
						.fromJson(memory.get("application").getAsJsonObject());

				// Handle commands
				Mono<?> res = handleCmds(event, def.reject, pCh, def, memory, user, account);
				if (res != null) {
					return res;
				}
			} catch (Exception e) {
			}

			// Cancel application
			new File("applications/active/" + userID + ".json").delete();
			new File("applications/active/" + application + "/" + userID).delete();

			if (memory.has("submissionID")) {
				// Edit submission message
				try {
					// Create updated embed
					EmbedCreateSpec.Builder embed = EmbedCreateSpec.builder();
					embed.title("Review application");
					embed.footer("Status: rejected by " + event.getInteraction().getUser().getTag(),
							event.getInteraction().getUser().getAvatarUrl());
					embed.color(Color.RED);
					embed.thumbnail(user.getAvatarUrl());
					embed.description("Here you can review the application, select action below to review");

					// Get channel and update
					MessageChannel ch = event.getInteraction().getChannel().block();
					ch.getMessageById(Snowflake.of(memory.get("submissionID").getAsLong())).block().edit()
							.withEmbeds(embed.build()).withComponents().block();
				} catch (Exception e) {
				}
			}

			// Delete current message
			return event.deleteReply();
		}

		}

		// Load application memory
		JsonObject memory;
		try {
			memory = JsonParser
					.parseString(Files.readString(Path.of(
							"applications/active/" + event.getInteraction().getUser().getId().asString() + ".json")))
					.getAsJsonObject();
		} catch (Exception e) {
			return Mono.empty();
		}
		ApplicationDefinition def = new ApplicationDefinition().fromJson(memory.get("application").getAsJsonObject());
		String application = memory.get("id").getAsString();
		int index = memory.get("index").getAsInt();
		JsonObject fields = memory.get("fields").getAsJsonObject();
		User user = event.getInteraction().getUser();
		CenturiaAccount account = LinkUtils.getAccountByDiscordID(user.getId().asString());
		if (account == null)
			return event.reply("**Error:** No Centuria account linked with your Discord account.");

		// Handle button
		switch (id) {

		// Form button
		case "form": {
			// Load info
			ApplicationCommandDefinition cmd = def.application.get(index - 1);
			try {

				// Create modal
				InteractionPresentModalSpec.Builder modal = InteractionPresentModalSpec.builder();
				modal.title("Application step " + index);
				modal.customId("application/form");

				// Load options
				for (int i = 0; i < cmd.parameters.get("questions").getAsInt(); i++) {
					JsonObject question = cmd.parameters.get("question" + (i + 1)).getAsJsonObject();
					if (question.get("type").getAsString().equals("singleline"))
						modal.addComponent(ActionRow.of(TextInput.small(question.get("text").getAsString(),
								question.get("text").getAsString(), 2, 1000)));
					else
						modal.addComponent(ActionRow.of(TextInput.paragraph(question.get("text").getAsString(),
								question.get("text").getAsString(), 2, 1000)));
				}

				// Show modal
				event.presentModal(modal.build()).block();
				return Mono.empty();
			} catch (Exception e) {
				String stack = "";
				for (StackTraceElement ele : e.getStackTrace())
					stack += "\n    at " + ele;
				return event.reply(InteractionApplicationCommandCallbackSpec.builder()
						.addEmbed(EmbedCreateSpec.builder().color(Color.RED).title("Error occured!")
								.addField("Error", "Unhandled exception in command", true)
								.addField("Command", cmd.command, true)
								.description("Command payload:\n```json\n"
										+ new Gson().newBuilder().setPrettyPrinting().create().toJson(cmd.parameters)
												.toString()
										+ "\n```\n\nException:\n```\n" + e.getClass().getTypeName()
										+ (e.getMessage() != null ? ": " + e.getMessage() : "") + stack + "\n```")
								.build())
						.build());
			}
		}

		// Handle download
		case "downloaddata": {
			// Save to memory
			if (def.application.get(index - 1).parameters.has("key"))
				fields.addProperty(def.application.get(index - 1).parameters.get("key").getAsString(),
						"Acknowledged by user and backup was created");
			try {
				Files.writeString(Path.of("applications/active/" + user.getId().asString() + ".json"),
						memory.toString());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			// Build message
			event.getMessage().get().edit().withComponents().subscribe();
			MessageCreateSpec.Builder msg = MessageCreateSpec.builder();
			event.deferReply().block();

			// Message
			if (account.getSaveMode() == SaveMode.MANAGED) {
				msg.addEmbed(EmbedCreateSpec.builder().title("Data backup").color(Color.ORANGE)
						.description("The following zip contains your player inventory.\n"
								+ "Please note that some items aren't included for server protection.\n\n**IMPORTANT NOTICE:**\nThis only includes your ACTIVE save data, you need to switch saves and download the data separately if you want your other saves. Managed Save Data is very complicated and cannot be switched on the run for downloading.")
						.build());
			} else {
				msg.addEmbed(
						EmbedCreateSpec.builder().title("Data backup").color(Color.ORANGE)
								.description("The following zip contains your player inventory.\n"
										+ "Please note that some items aren't included for server protection.")
								.build());
			}

			ByteArrayOutputStream strm = new ByteArrayOutputStream();
			try {
				ZipOutputStream invZip = new ZipOutputStream(strm);

				// Add all inventory objects
				addItemToZip(account.getSaveSpecificInventory(), "1", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "10", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "100", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "102", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "103", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "104", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "105", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "110", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "111", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "2", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "201", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "3", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "300", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "302", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "303", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "304", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "311", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "4", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "400", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "5", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "6", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "7", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "8", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "9", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "avatars", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "level", invZip);
				addItemToZip(account.getSaveSpecificInventory(), "savesettings", invZip);
				invZip.close();
				strm.close();
			} catch (IOException e) {
			}

			// Send message
			event.getInteraction().getChannel().block().createMessage(msg.build()).block();

			// Add file
			msg = MessageCreateSpec.builder();
			msg.addFile("inventory.zip", new ByteArrayInputStream(strm.toByteArray()));
			event.getInteraction().getChannel().block().createMessage(msg.build()).block();

			// Load command
			ApplicationCommandDefinition cmd = def.application.get(index - 1);
			String btnCancelApp = cmd.parameters.get("buttonCancel").getAsString();

			// Build message
			msg = MessageCreateSpec.builder();

			// Build embed
			msg.addEmbed(EmbedCreateSpec.builder().title("Application: " + def.name + ": step " + index)
					.color(Color.GREEN).description("Backup completed, ready to continue").build());

			// Add buttons
			msg.addComponent(ActionRow.of(Button.success("application/movenext", "Proceed"),
					Button.primary("application/cancel", btnCancelApp)));

			// Send message
			event.getInteraction().getChannel().block().createMessage(msg.build()).block();
			return event.deleteReply();
		}

		// Handle acknowledge
		case "acknowledge": {
			// Save to memory
			if (def.application.get(index - 1).parameters.has("key"))
				fields.addProperty(def.application.get(index - 1).parameters.get("key").getAsString(),
						"Acknowledged by user");

			// Fallthrough to movenext
		}

		case "movenext": {
			// Get task
			if (index >= def.application.size()) {
				// Application complete, submit for review
				event.deferReply().block();

				// Build message
				MessageCreateSpec.Builder msg = MessageCreateSpec.builder();

				// Create starting embed
				EmbedCreateSpec.Builder embed = EmbedCreateSpec.builder();
				embed.title("Application review: " + user.getTag());
				embed.addField("Applicant Centuria account", account.getDisplayName(), true);
				embed.addField("Applicant Discord user", user.getMention(), true);
				embed.addField("Submitted at", "<t:" + (System.currentTimeMillis() / 1000) + ">", true);
				embed.addField("Application fields", "---------------------------------------------------------",
						false);
				for (String key : fields.keySet()) {
					if (!fields.get(key).isJsonObject()) {
						embed.addField(key, fields.get(key).getAsString(), false);
					}
				}
				embed.color(Color.BLUE);
				embed.description("Received an application submitted by " + user.getMention() + " for application `"
						+ def.name + "`");
				embed.thumbnail(user.getAvatarUrl());
				embed.footer(DiscordBotModule.getServerName(),
						DiscordBotModule.getClient().getSelf().block().getAvatarUrl());

				// Add embed
				msg.addEmbed(embed.build());

				// Send message
				try {
					// Get channel and send
					Guild guild = DiscordBotModule.getClient().getGuildById(Snowflake.of(def.reviewServer)).block();
					RestChannel ch = guild.getChannelById(Snowflake.of(def.reviewChannel)).block().getRestChannel();
					ch.createMessage(msg.build().asRequest()).block();

					// Success
					event.getMessage().get().edit().withComponents().subscribe();

					// Send all embeds
					for (String key : fields.keySet()) {
						if (fields.get(key).isJsonObject()) {
							msg = MessageCreateSpec.builder();
							embed = EmbedCreateSpec.builder();
							embed.title(key);
							embed.color(Color.BLUE);
							embed.thumbnail(user.getAvatarUrl());
							embed.footer(DiscordBotModule.getServerName(),
									DiscordBotModule.getClient().getSelf().block().getAvatarUrl());
							JsonObject data = fields.get(key).getAsJsonObject();
							for (String k : data.keySet())
								embed.addField(k, data.get(k).getAsString(), false);
							msg.addEmbed(embed.build());
							ch.createMessage(msg.build().asRequest()).block();
						}
					}

					// Create review embed
					msg = MessageCreateSpec.builder();
					embed = EmbedCreateSpec.builder();
					embed.title("Review application");
					embed.footer("Status: awaiting review...", null);
					embed.color(Color.ORANGE);
					embed.thumbnail(user.getAvatarUrl());
					embed.description("Here you can review the application, select action below to review");
					msg.addComponent(ActionRow.of(
							Button.success("application/accept/" + application + "/" + user.getId().asString(),
									"Accept"),
							Button.danger("application/reject/" + application + "/" + user.getId().asString(),
									"Reject")));
					msg.addEmbed(embed.build());
					String sID = ch.createMessage(msg.build().asRequest()).block().id().asString();
					memory.addProperty("submissionID", sID);
					try {
						Files.writeString(Path.of("applications/active/" + user.getId().asString() + ".json"),
								memory.toString());
					} catch (IOException e) {
						throw new RuntimeException(e);
					}

					return event.deleteReply();
				} catch (Exception e) {
					String stack = "";
					for (StackTraceElement ele : e.getStackTrace())
						stack += "\n    at " + ele;
					return event.editReply(InteractionReplyEditSpec.builder()
							.addEmbed(EmbedCreateSpec.builder().color(Color.RED).title("Error occured!")
									.addField("Error", "Unhandled exception in application submission", true)
									.description("Exception:\n```\n" + e.getClass().getTypeName()
											+ (e.getMessage() != null ? ": " + e.getMessage() : "") + stack + "\n```")
									.build())
							.build());
				}
			} else {
				// Move to the next
				ApplicationCommandDefinition cmd = def.application.get(index);
				try {
					switch (cmd.command) {

					// Forms
					case "form": {
						String type = "success";
						if (cmd.parameters.has("type"))
							type = cmd.parameters.get("type").getAsString();
						boolean addSkip = !cmd.parameters.get("required").getAsBoolean();
						String message = cmd.parameters.get("message").getAsString();
						String btnCancelApp = cmd.parameters.get("buttonCancel").getAsString();
						String skip = "";
						if (addSkip)
							skip = cmd.parameters.get("buttonSkip").getAsString();
						String btnOpen = cmd.parameters.get("buttonOpen").getAsString();

						// Build message
						InteractionApplicationCommandCallbackSpec.Builder msg = InteractionApplicationCommandCallbackSpec
								.builder();

						// Build embed
						msg.addEmbed(
								EmbedCreateSpec.builder().title("Application: " + def.name + ": step " + (index + 1))
										.color(Color.GREEN).description(message).build());

						// Add buttons
						if (addSkip)
							msg.addComponent(ActionRow.of(
									(type.equals("danger") ? Button.danger("application/" + cmd.command, btnOpen)
											: Button.success("application/" + cmd.command, btnOpen)),
									Button.secondary("application/movenext", skip),
									Button.primary("application/cancel", btnCancelApp)));
						else
							msg.addComponent(ActionRow.of(
									(type.equals("danger") ? Button.danger("application/" + cmd.command, btnOpen)
											: Button.success("application/" + cmd.command, btnOpen)),
									Button.primary("application/cancel", btnCancelApp)));

						// Send message
						event.reply(msg.build()).block();
						memory.addProperty("index", index + 1);
						try {
							Files.writeString(Path.of("applications/active/" + user.getId().asString() + ".json"),
									memory.toString());
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
						return event.getMessage().get().edit().withComponents();
					}

					// Suspend
					case "suspend": {
						// Suspend the account
						JsonObject banInfo = new JsonObject();
						if (cmd.parameters.get("type").getAsString().equals("permanent")) {
							if (cmd.parameters.has("reason"))
								banInfo.addProperty("reason", cmd.parameters.get("reason").getAsString());
							banInfo.addProperty("type", "ban");
							banInfo.addProperty("unbanTimestamp", -1);
							account.getSaveSharedInventory().setItem("penalty", banInfo);
						} else if (cmd.parameters.get("type").getAsString().equals("temporary")) {
							if (cmd.parameters.has("reason"))
								banInfo.addProperty("reason", cmd.parameters.get("reason").getAsString());
							banInfo.addProperty("type", "ban");
							banInfo.addProperty("unbanTimestamp", System.currentTimeMillis()
									+ (cmd.parameters.get("banDurationSeconds").getAsInt() * 1000));
							account.getSaveSharedInventory().setItem("penalty", banInfo);
						} else
							throw new IllegalArgumentException("Invalid ban type");

						// Kick
						Player plr = account.getOnlinePlayerInstance();
						if (plr != null) {
							plr.client.disconnect();
						}

						// Move to next
						memory.addProperty("index", index + 1);
						try {
							Files.writeString(Path.of("applications/active/" + user.getId().asString() + ".json"),
									memory.toString());
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
						return handleButton("movenext", event, gateway);
					}

					// Regular message
					case "message": {
						// Load arguments
						String type = "success";
						if (cmd.parameters.has("type"))
							type = cmd.parameters.get("type").getAsString();
						String message = cmd.parameters.get("message").getAsString();

						// Build message
						MessageCreateSpec.Builder msg = MessageCreateSpec.builder();

						// Build embed
						msg.addEmbed(EmbedCreateSpec.builder().title("Application: " + def.name + ": step " + (index))
								.color(type.equals("success") ? Color.GREEN : Color.RED).description(message).build());

						// Send message
						event.getInteraction().getChannel().block().createMessage(msg.build()).block();

						// Move to next
						memory.addProperty("index", index + 1);
						try {
							Files.writeString(Path.of("applications/active/" + user.getId().asString() + ".json"),
									memory.toString());
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
						return handleButton("movenext", event, gateway);
					}

					// Mute
					case "mute": {
						// Mute the account
						JsonObject muteInfo = new JsonObject();
						if (cmd.parameters.has("reason"))
							muteInfo.addProperty("reason", cmd.parameters.get("reason").getAsString());
						muteInfo.addProperty("type", "mute");
						muteInfo.addProperty("unmuteTimestamp", System.currentTimeMillis()
								+ (cmd.parameters.get("banDurationSeconds").getAsInt() * 1000));
						account.getSaveSharedInventory().setItem("penalty", muteInfo);

						// Move to next
						memory.addProperty("index", index + 1);
						try {
							Files.writeString(Path.of("applications/active/" + user.getId().asString() + ".json"),
									memory.toString());
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
						return handleButton("movenext", event, gateway);
					}

					// Account migration
					case "migratedata": {
						if (account.getSaveMode() == SaveMode.SINGLE)
							account.migrateSaveDataToManagedMode();

						// Move to next
						memory.addProperty("index", index + 1);
						try {
							Files.writeString(Path.of("applications/active/" + user.getId().asString() + ".json"),
									memory.toString());
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
						return handleButton("movenext", event, gateway);
					}

					// Pardon
					case "pardon": {
						// Pardon the account
						account.getSaveSharedInventory().deleteItem("penalty");

						// Move to next
						memory.addProperty("index", index + 1);
						try {
							Files.writeString(Path.of("applications/active/" + user.getId().asString() + ".json"),
									memory.toString());
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
						return handleButton("movenext", event, gateway);
					}

					// Create save
					case "createsave": {
						// Load data
						String name = cmd.parameters.get("name").getAsString();
						JsonObject settings = cmd.parameters.get("settings").getAsJsonObject();
						if (!account.getSaveManager().saveExists(name)) {
							// Create and switch
							if (!account.getSaveManager().createSave(name))
								throw new IOException("Save creation failure");
							if (!account.getSaveManager().switchSave(name))
								throw new IOException("Save switch failure");
							account.kickDirect("SYSTEM", "Save data switched");
							account = AccountManager.getInstance().getAccount(account.getAccountID());

							// Change settigns
							account.getSaveSpecificInventory().getSaveSettings().load(settings);
							account.getSaveSpecificInventory().writeSaveSettings();

							// Create other defaults
							if (cmd.parameters.has("items")) {
								for (JsonElement itmE : cmd.parameters.get("items").getAsJsonArray()) {
									JsonObject itm = itmE.getAsJsonObject();
									String id2 = itm.get("id").getAsString();
									String type = itm.get("type").getAsString();
									String mode = itm.get("mode").getAsString();
									JsonElement data = itm.get("data");
									PlayerInventory inv = type.equals("shared") ? account.getSaveSharedInventory()
											: account.getSaveSpecificInventory();
									if (mode.equals("append")) {
										if (!inv.containsItem(id))
											inv.setItem(id, new JsonArray());
										JsonArray old = inv.getItem(id2).getAsJsonArray();
										old.add(data);
										data = old;
									}
									inv.setItem(id2, data);
								}
							}
						}

						// Move to next
						memory.addProperty("index", index + 1);
						try {
							Files.writeString(Path.of("applications/active/" + user.getId().asString() + ".json"),
									memory.toString());
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
						return handleButton("movenext", event, gateway);
					}

					// Create save if another exists
					case "createsave_if_exists": {
						// Load data
						String name = cmd.parameters.get("name").getAsString();
						JsonObject settings = cmd.parameters.get("settings").getAsJsonObject();
						if (!account.getSaveManager().saveExists(name)
								&& account.getSaveManager().saveExists(cmd.parameters.get("other").getAsString())) {
							// Create and switch
							if (!account.getSaveManager().createSave(name))
								throw new IOException("Save creation failure");
							if (!account.getSaveManager().switchSave(name))
								throw new IOException("Save switch failure");
							account.kickDirect("SYSTEM", "Save data switched");
							account = AccountManager.getInstance().getAccount(account.getAccountID());

							// Change settigns
							account.getSaveSpecificInventory().getSaveSettings().load(settings);
							account.getSaveSpecificInventory().writeSaveSettings();

							// Create other defaults
							if (cmd.parameters.has("items")) {
								for (JsonElement itmE : cmd.parameters.get("items").getAsJsonArray()) {
									JsonObject itm = itmE.getAsJsonObject();
									String id2 = itm.get("id").getAsString();
									String type = itm.get("type").getAsString();
									String mode = itm.get("mode").getAsString();
									JsonElement data = itm.get("data");
									PlayerInventory inv = type.equals("shared") ? account.getSaveSharedInventory()
											: account.getSaveSpecificInventory();
									if (mode.equals("append")) {
										if (!inv.containsItem(id))
											inv.setItem(id, new JsonArray());
										JsonArray old = inv.getItem(id2).getAsJsonArray();
										old.add(data);
										data = old;
									}
									inv.setItem(id2, data);
								}
							}
						}

						// Move to next
						memory.addProperty("index", index + 1);
						try {
							Files.writeString(Path.of("applications/active/" + user.getId().asString() + ".json"),
									memory.toString());
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
						return handleButton("movenext", event, gateway);
					}

					// Create save if another does NOT exists
					case "createsave_if_not_exist": {
						// Load data
						String name = cmd.parameters.get("name").getAsString();
						JsonObject settings = cmd.parameters.get("settings").getAsJsonObject();
						if (!account.getSaveManager().saveExists(name)
								&& !account.getSaveManager().saveExists(cmd.parameters.get("other").getAsString())) {
							// Create and switch
							if (!account.getSaveManager().createSave(name))
								throw new IOException("Save creation failure");
							if (!account.getSaveManager().switchSave(name))
								throw new IOException("Save switch failure");
							account.kickDirect("SYSTEM", "Save data switched");
							account = AccountManager.getInstance().getAccount(account.getAccountID());

							// Change settigns
							account.getSaveSpecificInventory().getSaveSettings().load(settings);
							account.getSaveSpecificInventory().writeSaveSettings();

							// Create other defaults
							if (cmd.parameters.has("items")) {
								for (JsonElement itmE : cmd.parameters.get("items").getAsJsonArray()) {
									JsonObject itm = itmE.getAsJsonObject();
									String id2 = itm.get("id").getAsString();
									String type = itm.get("type").getAsString();
									String mode = itm.get("mode").getAsString();
									JsonElement data = itm.get("data");
									PlayerInventory inv = type.equals("shared") ? account.getSaveSharedInventory()
											: account.getSaveSpecificInventory();
									if (mode.equals("append")) {
										if (!inv.containsItem(id))
											inv.setItem(id, new JsonArray());
										JsonArray old = inv.getItem(id2).getAsJsonArray();
										old.add(data);
										data = old;
									}
									inv.setItem(id2, data);
								}
							}
						}

						// Move to next
						memory.addProperty("index", index + 1);
						try {
							Files.writeString(Path.of("applications/active/" + user.getId().asString() + ".json"),
									memory.toString());
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
						return handleButton("movenext", event, gateway);
					}

					// Delete save
					case "deletesave": {
						// Load data
						String name = cmd.parameters.get("name").getAsString();
						if (account.getSaveManager().saveExists(name)) {
							account.getSaveManager().deleteSave(name);
						}

						// Move to next
						memory.addProperty("index", index + 1);
						try {
							Files.writeString(Path.of("applications/active/" + user.getId().asString() + ".json"),
									memory.toString());
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
						return handleButton("movenext", event, gateway);
					}

					// Add role
					case "addrole": {
						long server = cmd.parameters.get("server").getAsLong();
						long role = cmd.parameters.get("role").getAsLong();
						Member mem = DiscordBotModule.getClient().getGuildById(Snowflake.of(server)).block()
								.getMemberById(user.getId()).block();
						if (!mem.getRoleIds().stream().anyMatch(t -> t.asLong() == role))
							mem.addRole(Snowflake.of(role)).block();

						// Move to next
						memory.addProperty("index", index + 1);
						try {
							Files.writeString(Path.of("applications/active/" + user.getId().asString() + ".json"),
									memory.toString());
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
						return handleButton("movenext", event, gateway);
					}

					// Remove role
					case "removerole": {
						long server = cmd.parameters.get("server").getAsLong();
						long role = cmd.parameters.get("role").getAsLong();
						Member mem = DiscordBotModule.getClient().getGuildById(Snowflake.of(server)).block()
								.getMemberById(user.getId()).block();
						if (mem.getRoleIds().stream().anyMatch(t -> t.asLong() == role))
							mem.removeRole(Snowflake.of(role)).block();

						// Move to next
						memory.addProperty("index", index + 1);
						try {
							Files.writeString(Path.of("applications/active/" + user.getId().asString() + ".json"),
									memory.toString());
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
						return handleButton("movenext", event, gateway);
					}

					// Switch save
					case "switchsave": {
						// Load data
						String name = cmd.parameters.get("name").getAsString();
						if (account.getSaveManager().saveExists(name)) {
							if (!account.getSaveManager().switchSave(name))
								throw new IOException("Save switch failure");
							account.kickDirect("SYSTEM", "Save data switched");
							account = AccountManager.getInstance().getAccount(account.getAccountID());
						}

						// Move to next
						memory.addProperty("index", index + 1);
						try {
							Files.writeString(Path.of("applications/active/" + user.getId().asString() + ".json"),
									memory.toString());
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
						return handleButton("movenext", event, gateway);
					}

					// Set item
					case "setitem": {
						// Load info
						String id2 = cmd.parameters.get("id").getAsString();
						String type = cmd.parameters.get("type").getAsString();
						String mode = cmd.parameters.get("mode").getAsString();

						// Load inventory
						JsonElement data = cmd.parameters.get("data");
						PlayerInventory inv = type.equals("shared") ? account.getSaveSharedInventory()
								: account.getSaveSpecificInventory();

						// Append
						if (mode.equals("append")) {
							if (!inv.containsItem(id))
								inv.setItem(id, new JsonArray());
							JsonArray old = inv.getItem(id2).getAsJsonArray();
							old.add(data);
							data = old;
						}

						// Set
						inv.setItem(id2, data);

						// Move to next
						memory.addProperty("index", index + 1);
						try {
							Files.writeString(Path.of("applications/active/" + user.getId().asString() + ".json"),
									memory.toString());
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
						return handleButton("movenext", event, gateway);
					}

					// Data download and simple confirmation
					case "downloaddata":
					case "acknowledge": {
						// Check payload
						String type = "success";
						if (cmd.parameters.has("type"))
							type = cmd.parameters.get("type").getAsString();
						boolean addSkip = !cmd.parameters.get("required").getAsBoolean();
						String message = cmd.parameters.get("message").getAsString();
						String btnCancelApp = cmd.parameters.get("buttonCancel").getAsString();
						String skip = "";
						if (addSkip)
							skip = cmd.parameters.get("buttonSkip").getAsString();
						String btnAcknowledge = cmd.parameters.get("buttonAcknowledge").getAsString();

						// Build message
						InteractionApplicationCommandCallbackSpec.Builder msg = InteractionApplicationCommandCallbackSpec
								.builder();

						// Build embed
						msg.addEmbed(
								EmbedCreateSpec.builder().title("Application: " + def.name + ": step " + (index + 1))
										.color(Color.GREEN).description(message).build());

						// Add buttons
						if (addSkip)
							msg.addComponent(ActionRow.of(
									(type.equals("danger") ? Button.danger("application/" + cmd.command, btnAcknowledge)
											: Button.success("application/" + cmd.command, btnAcknowledge)),
									Button.secondary("application/movenext", skip),
									Button.primary("application/cancel", btnCancelApp)));
						else
							msg.addComponent(ActionRow.of(
									(type.equals("danger") ? Button.danger("application/" + cmd.command, btnAcknowledge)
											: Button.success("application/" + cmd.command, btnAcknowledge)),
									Button.primary("application/cancel", btnCancelApp)));

						// Send message
						event.reply(msg.build()).block();
						memory.addProperty("index", index + 1);
						try {
							Files.writeString(Path.of("applications/active/" + user.getId().asString() + ".json"),
									memory.toString());
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
						return event.getMessage().get().edit().withComponents();
					}

					// Error
					default: {
						// Error
						return event.reply(InteractionApplicationCommandCallbackSpec.builder()
								.addEmbed(EmbedCreateSpec.builder().title("Error occured!").color(Color.RED)
										.addField("Error", "Unrecognized application command", true)
										.addField("Command", cmd.command, true)
										.description("Command payload:\n```json\n" + new Gson().newBuilder()
												.setPrettyPrinting().create().toJson(cmd.parameters).toString()
												+ "\n```")
										.build())
								.build());
					}

					}
				} catch (Exception e) {
					String stack = "";
					for (StackTraceElement ele : e.getStackTrace())
						stack += "\n    at " + ele;
					return event.reply(InteractionApplicationCommandCallbackSpec.builder().addEmbed(EmbedCreateSpec
							.builder().color(Color.RED).title("Error occured!")
							.addField("Error", "Unhandled exception in command", true)
							.addField("Command", cmd.command, true)
							.description("Command payload:\n```json\n"
									+ new Gson().newBuilder().setPrettyPrinting().create().toJson(cmd.parameters)
											.toString()
									+ "\n```\n\nException:\n```\n" + e.getClass().getTypeName()
									+ (e.getMessage() != null ? ": " + e.getMessage() : "") + stack + "\n```")
							.build()).build());
				}
			}
		}

		// Handle cancel
		case "cancel": {
			// Build response (the 'Are you sure' prompt)
			InteractionApplicationCommandCallbackSpec.Builder msg = InteractionApplicationCommandCallbackSpec.builder();
			msg.content("Are you sure you wish to cancel the application?");

			// Add buttons
			msg.addComponent(ActionRow.of(Button.danger("application/cancelconfirm", "Confirm"),
					Button.primary("dismissDelete", "Cancel")));

			// Reply
			return event.reply(msg.build());
		}
		case "cancelconfirm": {
			event.deferEdit().block();

			// Cancel application
			new File("applications/active/" + user.getId().asString() + ".json").delete();
			new File("applications/active/" + application + "/" + user.getId().asString()).delete();

			// Delete messages
			MessageReferenceData ref = event.getMessage().get().getData().messageReference().get();
			Message oMsg = gateway
					.getMessageById(Snowflake.of(ref.channelId().get().asString()), Snowflake.of(ref.messageId().get()))
					.block();
			oMsg.edit().withComponents().subscribe();

			// Send cancel message
			event.getMessage().get().getChannel().block()
					.createMessage(
							EmbedCreateSpec.builder().color(Color.RED).title("Cancelled the application").build())
					.block();

			// Delete current message
			return event.getMessage().get().delete();
		}

		// Error
		default: {
			// Go back
			memory.addProperty("index", index - 1);
			try {
				Files.writeString(Path.of("applications/active/" + user.getId().asString() + ".json"),
						memory.toString());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			return event.reply(InteractionApplicationCommandCallbackSpec.builder()
					.addEmbed(EmbedCreateSpec.builder().title("Error occured!")
							.addField("Error", "This button was not handled", true).addField("Button ID", id, true)
							.addField("Button parameters", params, true).color(Color.RED).build())
					.build());
		}

		}
	}

	private static Mono<?> handleCmds(ComponentInteractionEvent event, ArrayList<ApplicationCommandDefinition> cmds,
			PrivateChannel pCh, ApplicationDefinition def, JsonObject memory, User user, CenturiaAccount account) {
		for (ApplicationCommandDefinition cmd : cmds) {
			try {
				switch (cmd.command) {

				// Suspend
				case "suspend": {
					// Suspend the account
					JsonObject banInfo = new JsonObject();
					if (cmd.parameters.get("type").getAsString().equals("permanent")) {
						if (cmd.parameters.has("reason"))
							banInfo.addProperty("reason", cmd.parameters.get("reason").getAsString());
						banInfo.addProperty("type", "ban");
						banInfo.addProperty("unbanTimestamp", -1);
						account.getSaveSharedInventory().setItem("penalty", banInfo);
					} else if (cmd.parameters.get("type").getAsString().equals("temporary")) {
						if (cmd.parameters.has("reason"))
							banInfo.addProperty("reason", cmd.parameters.get("reason").getAsString());
						banInfo.addProperty("type", "ban");
						banInfo.addProperty("unbanTimestamp", System.currentTimeMillis()
								+ (cmd.parameters.get("banDurationSeconds").getAsInt() * 1000));
						account.getSaveSharedInventory().setItem("penalty", banInfo);
					} else
						throw new IllegalArgumentException("Invalid ban type");

					// Kick
					Player plr = account.getOnlinePlayerInstance();
					if (plr != null) {
						plr.client.disconnect();
					}
					break;
				}

				// Add role
				case "addrole": {
					long server = cmd.parameters.get("server").getAsLong();
					long role = cmd.parameters.get("role").getAsLong();
					Member mem = DiscordBotModule.getClient().getGuildById(Snowflake.of(server)).block()
							.getMemberById(user.getId()).block();
					if (!mem.getRoleIds().stream().anyMatch(t -> t.asLong() == role))
						mem.addRole(Snowflake.of(role)).block();
					break;
				}

				// Remove role
				case "removerole": {
					long server = cmd.parameters.get("server").getAsLong();
					long role = cmd.parameters.get("role").getAsLong();
					Member mem = DiscordBotModule.getClient().getGuildById(Snowflake.of(server)).block()
							.getMemberById(user.getId()).block();
					if (mem.getRoleIds().stream().anyMatch(t -> t.asLong() == role))
						mem.removeRole(Snowflake.of(role)).block();
					break;
				}

				// Account migration
				case "migratedata": {
					if (account.getSaveMode() == SaveMode.SINGLE)
						account.migrateSaveDataToManagedMode();
					break;
				}

				// Create save
				case "createsave": {
					// Load data
					String name = cmd.parameters.get("name").getAsString();
					JsonObject settings = cmd.parameters.get("settings").getAsJsonObject();
					if (!account.getSaveManager().saveExists(name)) {
						// Create and switch
						if (!account.getSaveManager().createSave(name))
							throw new IOException("Save creation failure");
						if (!account.getSaveManager().switchSave(name))
							throw new IOException("Save switch failure");
						account.kickDirect("SYSTEM", "Save data switched");
						account = AccountManager.getInstance().getAccount(account.getAccountID());

						// Change settigns
						account.getSaveSpecificInventory().getSaveSettings().load(settings);
						account.getSaveSpecificInventory().writeSaveSettings();

						// Create other defaults
						if (cmd.parameters.has("items")) {
							for (JsonElement itmE : cmd.parameters.get("items").getAsJsonArray()) {
								JsonObject itm = itmE.getAsJsonObject();
								String id = itm.get("id").getAsString();
								String type = itm.get("type").getAsString();
								String mode = itm.get("mode").getAsString();
								JsonElement data = itm.get("data");
								PlayerInventory inv = type.equals("shared") ? account.getSaveSharedInventory()
										: account.getSaveSpecificInventory();
								if (mode.equals("append")) {
									if (!inv.containsItem(id))
										inv.setItem(id, new JsonArray());
									JsonArray old = inv.getItem(id).getAsJsonArray();
									old.add(data);
									data = old;
								}
								inv.setItem(id, data);
							}
						}
					}
					break;
				}

				// Create save if another exists
				case "createsave_if_exists": {
					// Load data
					String name = cmd.parameters.get("name").getAsString();
					JsonObject settings = cmd.parameters.get("settings").getAsJsonObject();
					if (!account.getSaveManager().saveExists(name)
							&& account.getSaveManager().saveExists(cmd.parameters.get("other").getAsString())) {
						// Create and switch
						if (!account.getSaveManager().createSave(name))
							throw new IOException("Save creation failure");
						if (!account.getSaveManager().switchSave(name))
							throw new IOException("Save switch failure");
						account.kickDirect("SYSTEM", "Save data switched");
						account = AccountManager.getInstance().getAccount(account.getAccountID());

						// Change settigns
						account.getSaveSpecificInventory().getSaveSettings().load(settings);
						account.getSaveSpecificInventory().writeSaveSettings();

						// Create other defaults
						if (cmd.parameters.has("items")) {
							for (JsonElement itmE : cmd.parameters.get("items").getAsJsonArray()) {
								JsonObject itm = itmE.getAsJsonObject();
								String id = itm.get("id").getAsString();
								String type = itm.get("type").getAsString();
								String mode = itm.get("mode").getAsString();
								JsonElement data = itm.get("data");
								PlayerInventory inv = type.equals("shared") ? account.getSaveSharedInventory()
										: account.getSaveSpecificInventory();
								if (mode.equals("append")) {
									if (!inv.containsItem(id))
										inv.setItem(id, new JsonArray());
									JsonArray old = inv.getItem(id).getAsJsonArray();
									old.add(data);
									data = old;
								}
								inv.setItem(id, data);
							}
						}
					}
					break;
				}

				// Create save if another does NOT exists
				case "createsave_if_not_exist": {
					// Load data
					String name = cmd.parameters.get("name").getAsString();
					JsonObject settings = cmd.parameters.get("settings").getAsJsonObject();
					if (!account.getSaveManager().saveExists(name)
							&& !account.getSaveManager().saveExists(cmd.parameters.get("other").getAsString())) {
						// Create and switch
						if (!account.getSaveManager().createSave(name))
							throw new IOException("Save creation failure");
						if (!account.getSaveManager().switchSave(name))
							throw new IOException("Save switch failure");
						account.kickDirect("SYSTEM", "Save data switched");
						account = AccountManager.getInstance().getAccount(account.getAccountID());

						// Change settigns
						account.getSaveSpecificInventory().getSaveSettings().load(settings);
						account.getSaveSpecificInventory().writeSaveSettings();

						// Create other defaults
						if (cmd.parameters.has("items")) {
							for (JsonElement itmE : cmd.parameters.get("items").getAsJsonArray()) {
								JsonObject itm = itmE.getAsJsonObject();
								String id = itm.get("id").getAsString();
								String type = itm.get("type").getAsString();
								String mode = itm.get("mode").getAsString();
								JsonElement data = itm.get("data");
								PlayerInventory inv = type.equals("shared") ? account.getSaveSharedInventory()
										: account.getSaveSpecificInventory();
								if (mode.equals("append")) {
									if (!inv.containsItem(id))
										inv.setItem(id, new JsonArray());
									JsonArray old = inv.getItem(id).getAsJsonArray();
									old.add(data);
									data = old;
								}
								inv.setItem(id, data);
							}
						}
					}
					break;
				}

				// Set item
				case "setitem": {
					// Load info
					String id = cmd.parameters.get("id").getAsString();
					String type = cmd.parameters.get("type").getAsString();
					String mode = cmd.parameters.get("mode").getAsString();

					// Load inventory
					JsonElement data = cmd.parameters.get("data");
					PlayerInventory inv = type.equals("shared") ? account.getSaveSharedInventory()
							: account.getSaveSpecificInventory();

					// Append
					if (mode.equals("append")) {
						if (!inv.containsItem(id))
							inv.setItem(id, new JsonArray());
						JsonArray old = inv.getItem(id).getAsJsonArray();
						old.add(data);
						data = old;
					}

					// Set
					inv.setItem(id, data);
					break;
				}

				// Delete save
				case "deletesave": {
					// Load data
					String name = cmd.parameters.get("name").getAsString();
					if (account.getSaveManager().saveExists(name)) {
						account.getSaveManager().deleteSave(name);
					}
					break;
				}

				// Switch save
				case "switchsave": {
					// Load data
					String name = cmd.parameters.get("name").getAsString();
					if (account.getSaveManager().saveExists(name)) {
						if (!account.getSaveManager().switchSave(name))
							throw new IOException("Save switch failure");
						account.kickDirect("SYSTEM", "Save data switched");
						account = AccountManager.getInstance().getAccount(account.getAccountID());
					}
					break;
				}

				// Regular message
				case "message": {
					// Load arguments
					String type = "success";
					if (cmd.parameters.has("type"))
						type = cmd.parameters.get("type").getAsString();
					String message = cmd.parameters.get("message").getAsString();

					// Build message
					MessageCreateSpec.Builder msg = MessageCreateSpec.builder();

					// Build embed
					msg.addEmbed(EmbedCreateSpec.builder().title("Application Status")
							.color(type.equals("success") ? Color.GREEN : Color.RED).description(message).build());

					// Send message
					pCh.createMessage(msg.build()).block();
					break;
				}

				// Mute
				case "mute": {
					// Mute the account
					JsonObject muteInfo = new JsonObject();
					if (cmd.parameters.has("reason"))
						muteInfo.addProperty("reason", cmd.parameters.get("reason").getAsString());
					muteInfo.addProperty("type", "mute");
					muteInfo.addProperty("unmuteTimestamp",
							System.currentTimeMillis() + (cmd.parameters.get("banDurationSeconds").getAsInt() * 1000));
					account.getSaveSharedInventory().setItem("penalty", muteInfo);
					break;
				}

				// Pardon
				case "pardon": {
					// Pardon the account
					account.getSaveSharedInventory().deleteItem("penalty");
					break;
				}

				// Error
				default: {
					// Error
					return event.editReply(InteractionReplyEditSpec.builder()
							.addEmbed(EmbedCreateSpec.builder().title("Error occured!").color(Color.RED)
									.addField("Error", "Unrecognized application command", true)
									.addField("Command", cmd.command, true)
									.description("Command payload:\n```json\n" + new Gson().newBuilder()
											.setPrettyPrinting().create().toJson(cmd.parameters).toString() + "\n```")
									.build())
							.build());
				}
				}
			} catch (Exception e) {
				String stack = "";
				for (StackTraceElement ele : e.getStackTrace())
					stack += "\n    at " + ele;
				return event.editReply(InteractionReplyEditSpec.builder()
						.addEmbed(EmbedCreateSpec.builder().color(Color.RED).title("Error occured!")
								.addField("Error", "Unhandled exception in command", true)
								.addField("Command", cmd.command, true)
								.description("Command payload:\n```json\n"
										+ new Gson().newBuilder().setPrettyPrinting().create().toJson(cmd.parameters)
												.toString()
										+ "\n```\n\nException:\n```\n" + e.getClass().getTypeName()
										+ (e.getMessage() != null ? ": " + e.getMessage() : "") + stack + "\n```")
								.build())
						.build());
			}

		}
		return null;
	}

	private static void addItemToZip(PlayerInventory inv, String item, ZipOutputStream zipStrm)
			throws UnsupportedEncodingException, IOException {
		if (inv.containsItem(item))
			transferDataToZip(zipStrm, item + ".json", inv.getItem(item).toString().getBytes("UTF-8"));
	}

	private static void transferDataToZip(ZipOutputStream zip, String file, byte[] data) throws IOException {
		zip.putNextEntry(new ZipEntry(file));
		zip.write(data);
		zip.closeEntry();
	}

	/**
	 * Adds and refreshes a application panel
	 * 
	 * @param messageID   Panel ID
	 * @param guild       Guild ID
	 * @param channel     Channel ID
	 * @param application Application
	 */
	public static void addPanelAndRefresh(long messageID, long guild, long channel, String application) {
		// Add panel
		PanelInfo info = new PanelInfo();

		// Load application
		JsonObject data;
		try {
			data = JsonParser.parseString(Files.readString(Path.of("applications/" + application + ".json")))
					.getAsJsonObject();
		} catch (IOException e) {
			return;
		}
		ApplicationDefinition def = new ApplicationDefinition().fromJson(data);
		info.def = def;
		info.channelID = channel;
		info.messageID = messageID;
		info.guildID = guild;
		info.application = application;
		panels.add(info);

		// Refresh
		refreshPanel(info);

	}

}
