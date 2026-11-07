/*
 * Helper for the DownLoadControlBT portable test package.
 * Not part of upstream BiglyBT. Uses published BiglyBT APIs only.
 */
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.downloadcontrolbt.DownloadControlBTDefaults;
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

		COConfigurationManager.setParameter("Default save path", downloads.getPath());
		COConfigurationManager.setParameter("Save Torrent Files", true);
		COConfigurationManager.setParameter("General_sDefaultTorrent_Directory", torrents.getPath());
		COConfigurationManager.setParameter("Wizard Completed", true);
		COConfigurationManager.setParameter("update.start", false);
		COConfigurationManager.setParameter("update.periodic", false);
		COConfigurationManager.setParameter("update.opendialog", false);
		COConfigurationManager.setParameter("update.autodownload", false);
		COConfigurationManager.setParameter("Send Version Info", false);
		COConfigurationManager.setParameter("Logger.Enabled", true);
		COConfigurationManager.setParameter("Logging Enable", true);
		COConfigurationManager.setParameter("TCP.Listen.Port", 26981);
		COConfigurationManager.setParameter("UDP.Listen.Port", 26981);
		COConfigurationManager.setParameter("UDP.NonData.Listen.Port", 26981);

		setIfUnset(DownloadControlBTDefaults.CFG_PROFILE, true);
		setIfUnset(DownloadControlBTDefaults.CFG_AUTO_QUEUE_DEFAULT, true);
		setIfUnset(DownloadControlBTDefaults.CFG_DEFAULT_FORCE_START, true);
		setIfUnset(DownloadControlBTDefaults.CFG_ALWAYS_SHOW_FORCE_START, true);
		setIfUnset(ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS,
			ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS_ALWAYS);
		setIfUnset("Network Selection Default.I2P", true);
		setIfUnset("Network Selection Default.Tor", true);
		setIfUnset("Library.viewmode", 1);
		setIfUnset("LibraryDL.viewmode", 1);
		setIfUnset("LibraryCD.viewmode", 1);
		setIfUnset("LibraryUnopened.viewmode", 1);
		setIfUnset("Library.ShowTabsInTorrentView", 1);

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
		System.out.println("schedule.up=" + DownloadControlBTDefaults.SCHEDULE_UP);
		System.out.println("schedule.down=" + DownloadControlBTDefaults.SCHEDULE_DOWN);
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
