/*
 * Helper for the DownLoadControlBT portable test package.
 * Not part of upstream BiglyBT. Uses published BiglyBT APIs only.
 */
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.downloadcontrolbt.DownloadControlBTBanService;
import com.biglybt.core.downloadcontrolbt.DownloadControlBTDefaults;
import com.biglybt.core.downloadcontrolbt.DownloadControlBTRateFormat;
import com.biglybt.core.util.SystemProperties;

/**
 * Writes isolated BiglyBT settings after the caller has already set
 * azureus.config.path (and related) system properties on the JVM command
 * line, before any class that caches those properties is loaded.
 *
 * Refuses to run if the resolved user path is the regular ~/.biglybt
 * directory. Profile-gated defaults are applied only when a key is not
 * already explicitly set, and nonempty speed schedules are left alone.
 */
public final class InitIsolatedConfig
{
	public static void main(String[] args)
		throws Exception
	{
		String configProp = System.getProperty(SystemProperties.SYSPROP_CONFIG_PATH);
		if (configProp == null || configProp.trim().isEmpty()) {
			fail("azureus.config.path was not set before JVM start");
		}

		File configDir = new File(configProp).getCanonicalFile();
		File regular = new File(System.getProperty("user.home"), ".biglybt").getCanonicalFile();
		if (configDir.equals(regular) || isInside(configDir, regular)) {
			fail("config path resolves to the regular BiglyBT directory: " + configDir);
		}

		COConfigurationManager.preInitialise();

		File userPath = new File(SystemProperties.getUserPath()).getCanonicalFile();
		if (!isSameOrInside(userPath, configDir)) {
			fail("SystemProperties.getUserPath() escaped isolation: " + userPath
				+ " (expected under " + configDir + ")");
		}
		if (userPath.equals(regular) || isInside(userPath, regular)) {
			fail("resolved user path is regular ~/.biglybt: " + userPath);
		}

		if (args.length < 2) {
			fail("usage: InitIsolatedConfig <downloads-dir> <torrents-dir>");
		}

		File downloads = new File(args[0]).getCanonicalFile();
		File torrents = new File(args[1]).getCanonicalFile();
		mkdirReal(downloads);
		mkdirReal(torrents);

		setIfUnset("Default save path", downloads.getPath());
		setIfUnset("Save Torrent Files", true);
		setIfUnset("General_sDefaultTorrent_Directory", torrents.getPath());
		COConfigurationManager.setParameter("Wizard Completed", true);
		COConfigurationManager.setParameter("update.start", false);
		COConfigurationManager.setParameter("update.periodic", false);
		COConfigurationManager.setParameter("update.opendialog", false);
		COConfigurationManager.setParameter("update.autodownload", false);
		COConfigurationManager.setParameter("Send Version Info", false);
		COConfigurationManager.setParameter("Logger.Enabled", true);
		COConfigurationManager.setParameter("Logging Enable", true);
		if (!COConfigurationManager.hasParameter(DownloadControlBTDefaults.CFG_LISTEN_PORTS_V1, true)) {
			COConfigurationManager.setParameter("TCP.Listen.Port", DownloadControlBTDefaults.DEFAULT_LISTEN_PORT);
			COConfigurationManager.setParameter("UDP.Listen.Port", DownloadControlBTDefaults.DEFAULT_LISTEN_PORT);
			COConfigurationManager.setParameter("UDP.NonData.Listen.Port", DownloadControlBTDefaults.DEFAULT_LISTEN_PORT);
			COConfigurationManager.setParameter(DownloadControlBTDefaults.CFG_LISTEN_PORTS_V1, true);
		}

		setIfUnset(DownloadControlBTDefaults.CFG_PROFILE, true);
		setIfUnset(DownloadControlBTDefaults.CFG_AUTO_QUEUE_DEFAULT, true);
		setIfUnset(DownloadControlBTDefaults.CFG_DEFAULT_FORCE_START, true);
		setIfUnset(DownloadControlBTDefaults.CFG_ALWAYS_SHOW_FORCE_START, true);
		setIfUnset(ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS,
			ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS_ALWAYS);
		setIfUnset("Network Selection Default.I2P", true);
		setIfUnset("Network Selection Default.Tor", true);
		setIfUnset("Rename Incomplete Files", true);
		setIfUnset("Rename Incomplete Files Extension", ".incomplete");
		setIfUnset("DefaultDir.AutoSave.AutoRename", false);
		setIfUnset("def.deletetorrent", false);
		setIfUnset("Library.viewmode", 1);
		setIfUnset("LibraryDL.viewmode", 1);
		setIfUnset("LibraryCD.viewmode", 1);
		setIfUnset("LibraryUnopened.viewmode", 1);
		setIfUnset("Library.ShowTabsInTorrentView", 1);
		// DLCBT Nov 18: Tools > Options > Mode > User Proficiency = Advanced.
		// BiglyBT's first-run ConfigurationChecker always writes "User Mode"=0
		// (Beginner), so "unset" means the key is missing or still holds that
		// auto-written 0 and this one-shot flag has not run yet. A choice of
		// Intermediate/Advanced, or any later change, is kept.
		setIfUnset(DownloadControlBTDefaults.CFG_RAREST_ONLY, true);
		setIfUnset(DownloadControlBTDefaults.CFG_AUTOFIT_SECONDS, DownloadControlBTDefaults.DEFAULT_AUTOFIT_SECONDS);

		// Transfer slots / connections, encryption, recheck, padding, bias, UPnP.
		// setIfUnset so later user changes stick. DHT/PEX/trackers already default on.
		setIfUnset("Max Uploads", 80);
		setIfUnset("Max Uploads Seeding", 80);
		setIfUnset("enable.seedingonly.maxuploads", true);
		setIfUnset("Max.Peer.Connections.Per.Torrent", 200);
		setIfUnset("Max.Peer.Connections.Per.Torrent.When.Seeding", 200);
		setIfUnset("Max.Peer.Connections.Per.Torrent.When.Seeding.Enable", true);
		setIfUnset("Max.Peer.Connections.Total", 500);
		setIfUnset("Bias Upload Enable", false);
		setIfUnset("Seeding Piece Check Recheck Enable", false);
		setIfUnset("Hide Padding Files", false);
		setIfUnset("network.transport.encrypted.require", true);
		setIfUnset("Peer Source Selection Default.Tracker", true);
		setIfUnset("Peer Source Selection Default.DHT", true);
		setIfUnset("Peer Source Selection Default.PeerExchange", true);
		// Built-in UPnP plugin stores keys as Plugin.UPnP.*
		setIfUnset("Plugin.UPnP.upnp.enable", false);
		setIfUnset("Plugin.UPnP.natpmp.enable", false);

		if (!COConfigurationManager.hasParameter("dlcbt.usermode.advanced.v1", true)) {
			if (!COConfigurationManager.hasParameter("User Mode", true)
					|| COConfigurationManager.getIntParameter("User Mode") == 0) {
				COConfigurationManager.setParameter("User Mode", 2);
			}
			COConfigurationManager.setParameter("dlcbt.usermode.advanced.v1", true);
		}

		// One-shot: expand all Open Torrent Options sections on a fresh
		// isolated profile (and once for testers who inherited the previous
		// collapsed-by-default saved false). Later user collapses persist.
		if (!COConfigurationManager.hasParameter("dlcbt.opentorrent.sections.expanded.v1", true)) {
			String[] expandKeys = {
				"ui.skin.toptions2.torrentinfo.expanded",
				"ui.skin.toptions2.filearea.expanded",
				"ui.skin.toptions2.saveto.expanded",
				"ui.skin.toptions2.start.expanded",
				"ui.skin.toptions2.peer.expanded",
				"ui.skin.toptions.torrentinfo.expanded",
				"ui.skin.toptions.filearea.expanded",
				"ui.skin.toptions.saveto.expanded",
				"ui.skin.toptions.start.expanded",
				"ui.skin.toptions.peer.expanded"
			};
			for (int i = 0; i < expandKeys.length; i++) {
				COConfigurationManager.setParameter(expandKeys[i], true);
			}
			COConfigurationManager.setParameter("dlcbt.opentorrent.sections.expanded.v1", true);
		}

		// DLCBT temporary bans (in-memory only, exact /32 or /128, 54-64 min).
		// Only seeded when missing so user edits survive.
		setIfUnset(DownloadControlBTBanService.CFG_ENABLED, true);
		setIfUnset(DownloadControlBTBanService.CFG_MIN_MINUTES, DownloadControlBTBanService.DEFAULT_MIN_MINUTES);
		setIfUnset(DownloadControlBTBanService.CFG_MAX_MINUTES, DownloadControlBTBanService.DEFAULT_MAX_MINUTES);
		setIfUnset(DownloadControlBTBanService.CFG_MIN_RATE, DownloadControlBTRateFormat.DEFAULT_MIN);
		// blank = follow the schedule's per-peer peer_down limit
		setIfUnset(DownloadControlBTBanService.CFG_MAX_RATE, "");
		setIfUnset(DownloadControlBTBanService.CFG_RAMP_SECONDS, DownloadControlBTBanService.DEFAULT_RAMP_SECONDS);
		setIfUnset(DownloadControlBTBanService.CFG_WINDOW_SECONDS, DownloadControlBTBanService.DEFAULT_WINDOW_SECONDS);
		setIfUnset(DownloadControlBTBanService.CFG_DUP_WINDOW_SECONDS, DownloadControlBTBanService.DEFAULT_DUP_WINDOW_SECONDS);
		setIfUnset(DownloadControlBTBanService.CFG_DUP_THRESHOLD, DownloadControlBTBanService.DEFAULT_DUP_THRESHOLD);
		setIfUnset(DownloadControlBTBanService.CFG_SPEED_ENABLED, true);
		setIfUnset(DownloadControlBTBanService.CFG_HANDSHAKE_ENABLED, true);
		setIfUnset(DownloadControlBTBanService.CFG_HANDSHAKE_SECONDS, DownloadControlBTBanService.DEFAULT_HANDSHAKE_SECONDS);
		setIfUnset(DownloadControlBTBanService.CFG_DUP_ENABLED, true);
		setIfUnset(DownloadControlBTBanService.CFG_HASH_FAIL_ENABLED, true);
		setIfUnset(DownloadControlBTBanService.CFG_HASH_FAIL_MIN_SECONDS, DownloadControlBTBanService.DEFAULT_HASH_FAIL_MIN_SECONDS);
		setIfUnset(DownloadControlBTBanService.CFG_HASH_FAIL_MAX_SECONDS, DownloadControlBTBanService.DEFAULT_HASH_FAIL_MAX_SECONDS);
		setIfUnset(com.biglybt.core.downloadcontrolbt.DownloadControlBTIdleBan.CFG_ENABLED, true);
		setIfUnset(com.biglybt.core.downloadcontrolbt.DownloadControlBTIdleBan.CFG_IDLE_SECONDS, com.biglybt.core.downloadcontrolbt.DownloadControlBTIdleBan.DEFAULT_IDLE_SECONDS);
		setIfUnset(com.biglybt.core.downloadcontrolbt.DownloadControlBTConnectTimeout.CFG_ENABLED, true);
		setIfUnset(com.biglybt.core.downloadcontrolbt.DownloadControlBTConnectTimeout.CFG_KICK_SECONDS, com.biglybt.core.downloadcontrolbt.DownloadControlBTConnectTimeout.DEFAULT_KICK_SECONDS);
		setIfUnset(com.biglybt.core.downloadcontrolbt.DownloadControlBTRarestUpload.CFG_DISCONNECT_ENABLED, true);
		setIfUnset(com.biglybt.core.downloadcontrolbt.DownloadControlBTRarestUpload.CFG_DISCONNECT_REJECTS, com.biglybt.core.downloadcontrolbt.DownloadControlBTRarestUpload.DISCONNECT_AFTER_REJECTS);

		// One-shot: overall/session upload unlimited (per-peer schedule
		// peer_up stays). Later user changes persist.
		if (!COConfigurationManager.hasParameter("dlcbt.upload.unlimited.v1", true)) {
			COConfigurationManager.setParameter("Max Upload Speed KBs", 0);
			COConfigurationManager.setParameter("Max Upload Speed Seeding KBs", 0);
			COConfigurationManager.setParameter("enable.seedingonly.upload.rate", false);
			COConfigurationManager.setParameter("Auto Upload Speed Enabled", false);
			COConfigurationManager.setParameter("Auto Upload Speed Seeding Enabled", false);
			COConfigurationManager.setParameter("dlcbt.upload.unlimited.v1", true);
		}

		@SuppressWarnings("rawtypes")
		List existingSchedule = COConfigurationManager.getListParameter(
			DownloadControlBTDefaults.CFG_SCHEDULE_LINES, new ArrayList());
		boolean seededSchedule = DownloadControlBTDefaults.shouldSeedSchedule(existingSchedule);
		if (seededSchedule) {
			List<String> lines = new ArrayList<String>(DownloadControlBTDefaults.defaultScheduleLines());
			COConfigurationManager.setParameter(DownloadControlBTDefaults.CFG_SCHEDULE_LINES, lines);
		}

		COConfigurationManager.save();

		System.out.println("ISOLATION_OK");
		System.out.println("config.path=" + configDir.getPath());
		System.out.println("user.path=" + userPath.getPath());
		System.out.println("regular.home.path=" + regular.getPath());
		System.out.println("install.path=" + SystemProperties.getApplicationPath());
		System.out.println("instance.port=" + com.biglybt.core.util.Constants.INSTANCE_PORT);
		System.out.println("default.save.path=" + COConfigurationManager.getStringParameter("Default save path"));
		System.out.println("torrent.dir=" + COConfigurationManager.getStringParameter("General_sDefaultTorrent_Directory"));
		System.out.println("config.file=" + new File(userPath, "biglybt.config").getPath());
		System.out.println("dlcbt.profile.enabled=" + COConfigurationManager.getBooleanParameter(DownloadControlBTDefaults.CFG_PROFILE, false));
		System.out.println("dlcbt.auto.queue.default=" + COConfigurationManager.getBooleanParameter(DownloadControlBTDefaults.CFG_AUTO_QUEUE_DEFAULT, false));
		System.out.println("dlcbt.default.force.start=" + COConfigurationManager.getBooleanParameter(DownloadControlBTDefaults.CFG_DEFAULT_FORCE_START, false));
		System.out.println("ui.addtorrent.openoptions=" + COConfigurationManager.getStringParameter(ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS));
		System.out.println("network.i2p=" + COConfigurationManager.getBooleanParameter("Network Selection Default.I2P", false));
		System.out.println("network.tor=" + COConfigurationManager.getBooleanParameter("Network Selection Default.Tor", false));
		System.out.println("library.viewmode=" + COConfigurationManager.getIntParameter("Library.viewmode"));
		System.out.println("schedule.seeded=" + seededSchedule);
		System.out.println("dlcbt.ban.enabled=" + COConfigurationManager.getBooleanParameter(DownloadControlBTBanService.CFG_ENABLED, false));
		System.out.println("upload.max.kbs=" + COConfigurationManager.getIntParameter("Max Upload Speed KBs"));
		System.out.println("schedule.up=" + DownloadControlBTDefaults.SCHEDULE_UP);
		System.out.println("schedule.down=" + DownloadControlBTDefaults.SCHEDULE_DOWN);
		System.out.println("user.mode=" + COConfigurationManager.getIntParameter("User Mode"));
		System.out.println("tcp.listen=" + COConfigurationManager.getIntParameter("TCP.Listen.Port"));
		System.out.println("udp.listen=" + COConfigurationManager.getIntParameter("UDP.Listen.Port"));
	}

	private static void setIfUnset(String key, boolean value)
	{
		if (!COConfigurationManager.hasParameter(key, true)) {
			COConfigurationManager.setParameter(key, value);
		}
	}

	private static void setIfUnset(String key, int value)
	{
		if (!COConfigurationManager.hasParameter(key, true)) {
			COConfigurationManager.setParameter(key, value);
		}
	}

	private static void setIfUnset(String key, String value)
	{
		if (!COConfigurationManager.hasParameter(key, true)) {
			COConfigurationManager.setParameter(key, value);
		}
	}

	private static void mkdirReal(File dir)
		throws Exception
	{
		if (!dir.exists() && !dir.mkdirs()) {
			fail("could not create " + dir);
		}
		File canon = dir.getCanonicalFile();
		if (!canon.isDirectory()) {
			fail("not a directory: " + canon);
		}
		if (java.nio.file.Files.isSymbolicLink(dir.toPath())
			|| java.nio.file.Files.isSymbolicLink(canon.toPath())) {
			fail("refusing symlink path: " + dir);
		}
	}

	private static boolean isInside(File child, File parent)
	{
		String c = child.getPath();
		String p = parent.getPath();
		return c.startsWith(p.endsWith(File.separator) ? p : p + File.separator);
	}

	private static boolean isSameOrInside(File child, File parent)
	{
		return child.equals(parent) || isInside(child, parent);
	}

	private static void fail(String message)
	{
		System.err.println("REFUSE: " + message);
		System.exit(2);
	}
}
