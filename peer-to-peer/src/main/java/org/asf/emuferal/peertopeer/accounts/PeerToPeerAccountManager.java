package org.asf.emuferal.peertopeer.accounts;

import java.util.function.Consumer;

import org.asf.centuria.accounts.AccountManager;
import org.asf.centuria.accounts.CenturiaAccount;
import org.asf.emuferal.peertopeer.PeerToPeerModule;
import org.asf.emuferal.peertopeer.players.P2PPlayer;

/**
 * 
 * Dummy account manager delegating to the last assigned one except for
 * peer-to-peer players, for those it provides basic read-only data.
 * 
 * @author Sky Swimmer
 *
 */
public class PeerToPeerAccountManager extends AccountManager {

	private AccountManager manager;

	public void assign() {
		manager = AccountManager.instance;
		instance = this;
	}

	@Override
	public String authenticate(String arg0, char[] arg1) {
		return manager.authenticate(arg0, arg1);
	}

	@Override
	public String getUserByLoginName(String name) {
		return manager.getUserByLoginName(name);
	}

	@Override
	public boolean hasPassword(String arg0) {
		return manager.hasPassword(arg0);
	}

	@Override
	public boolean isDisplayNameInUse(String arg0) {
		if (PeerToPeerModule.getByDisplayName(arg0) != null)
			return true;
		return manager.isDisplayNameInUse(arg0);
	}

	@Override
	public boolean isPasswordUpdateRequested(String arg0) {
		return manager.isPasswordUpdateRequested(arg0);
	}

	@Override
	public boolean lockDisplayName(String arg0, String arg1) {
		return manager.lockDisplayName(arg0, arg1);
	}

	@Override
	public void makePasswordUpdateRequested(String arg0) {
		manager.makePasswordUpdateRequested(arg0);
	}

	@Override
	public String register(String arg0) {
		return manager.register(arg0);
	}

	@Override
	public boolean releaseDisplayName(String arg0) {
		return manager.releaseDisplayName(arg0);
	}

	@Override
	public void runForAllAccounts(Consumer<CenturiaAccount> arg0) {
		manager.runForAllAccounts(arg0);
	}

	@Override
	public boolean updatePassword(String arg0, char[] arg1) {
		return manager.updatePassword(arg0, arg1);
	}

	@Override
	public CenturiaAccount getAccount(String id) {
		// Find P2P player
		P2PPlayer player = PeerToPeerModule.getPlayer(id);
		if (player != null && !player.isLocal)
			return new PeerToPeerReadonlyCenturiaAccount(player);
		return manager.getAccount(id);
	}

	@Override
	public String getUserByDisplayName(String name) {
		// Find P2P player
		P2PPlayer player = PeerToPeerModule.getByDisplayName(name);
		if (player != null && !player.isLocal)
			return player.id;
		return manager.getUserByDisplayName(name);
	}

	@Override
	public void releaseLoginName(String name) {
		manager.releaseDisplayName(name);
	}

}
