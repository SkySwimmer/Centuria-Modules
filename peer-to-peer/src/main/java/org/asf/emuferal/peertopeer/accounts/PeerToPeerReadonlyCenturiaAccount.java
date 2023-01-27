package org.asf.emuferal.peertopeer.accounts;

import org.asf.centuria.Centuria;
import org.asf.centuria.accounts.CenturiaAccount;
import org.asf.centuria.accounts.LevelInfo;
import org.asf.centuria.accounts.PlayerInventory;
import org.asf.centuria.accounts.SaveManager;
import org.asf.centuria.accounts.SaveMode;
import org.asf.centuria.accounts.impl.LevelManager;
import org.asf.centuria.entities.players.Player;
import org.asf.emuferal.peertopeer.players.P2PPlayer;

import com.google.gson.JsonObject;

public class PeerToPeerReadonlyCenturiaAccount extends CenturiaAccount {

	private PlayerInventory mainInv;
	private PlayerInventory sharedInv;

	private P2PPlayer player;
	private LevelManager level;

	public PeerToPeerReadonlyCenturiaAccount(P2PPlayer player) {
		this.player = player;

		mainInv = new PeerToPeerPlayerInventory(player, false);
		sharedInv = new PeerToPeerPlayerInventory(player, true);
	}

	@Override
	public long getLastLoginTime() {
		return (System.currentTimeMillis() / 1000) - 5;
	}

	@Override
	public void deleteAccount() {
	}

	@Override
	public void finishedTutorial() {
	}

	@Override
	public void forceNameChange() {
	}

	@Override
	public int getAccountNumericID() {
		return -1;
	}

	@Override
	public LevelInfo getLevel() {
		if (level == null)
			level = new LevelManager(this);

		return level;
	}

	@Override
	public Player getOnlinePlayerInstance() {
		return Centuria.gameServer.getPlayer(getAccountID());
	}

	@Override
	public JsonObject getPrivacySettings() {
		JsonObject privacy = new JsonObject();
		privacy.addProperty("voice_chat", "following");
		savePrivacySettings(privacy);
		return privacy;
	}

	@Override
	public boolean isPlayerNew() {
		return false;
	}

	@Override
	public boolean isRenameRequired() {
		return false;
	}

	@Override
	public void login() {
	}

	@Override
	public void savePrivacySettings(JsonObject arg0) {
	}

	@Override
	public void setActiveLook(String arg0) {
	}

	@Override
	public void setActiveSanctuaryLook(String arg0) {
	}

	@Override
	public boolean updateDisplayName(String arg0) {
		return false;
	}

	@Override
	public String getAccountID() {
		return player.id;
	}

	@Override
	public String getActiveLook() {
		return player.currentLook;
	}

	@Override
	public String getActiveSanctuaryLook() {
		return player.currentSanctuaryLook;
	}

	@Override
	public String getDisplayName() {
		return player.displayName;
	}

	@Override
	public String getLoginName() {
		return "<peer-to-peer: " + getDisplayName() + ">";
	}

	@Override
	public SaveManager getSaveManager() throws IllegalArgumentException {
		throw new IllegalArgumentException("Peer-to-peer mode");
	}

	@Override
	public SaveMode getSaveMode() {
		return SaveMode.SINGLE;
	}

	@Override
	public PlayerInventory getSaveSharedInventory() {
		return sharedInv;
	}

	@Override
	public PlayerInventory getSaveSpecificInventory() {
		return mainInv;
	}

	@Override
	public void migrateSaveDataToManagedMode() throws IllegalArgumentException {
		throw new IllegalArgumentException("Peer-to-peer mode");
	}

	@Override
	public boolean updateLoginName(String arg0) {
		return false;
	}

}
