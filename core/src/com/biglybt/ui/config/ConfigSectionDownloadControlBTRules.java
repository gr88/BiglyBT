/*
 * DownLoadControlBT (unofficial fork experiment). GPL like BiglyBT.
 */
package com.biglybt.ui.config;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.downloadcontrolbt.DownloadControlBT;
import com.biglybt.core.downloadcontrolbt.DownloadControlBTBanService;
import com.biglybt.core.downloadcontrolbt.DownloadControlBTConnectTimeout;
import com.biglybt.core.downloadcontrolbt.DownloadControlBTDefaults;
import com.biglybt.core.downloadcontrolbt.DownloadControlBTIdleBan;
import com.biglybt.core.downloadcontrolbt.DownloadControlBTRarestUpload;
import com.biglybt.core.downloadcontrolbt.DownloadControlBTRateFormat;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.config.ParameterValidator.ValidationInfo;
import com.biglybt.pifimpl.local.ui.config.ActionParameterImpl;
import com.biglybt.pifimpl.local.ui.config.BooleanParameterImpl;
import com.biglybt.pifimpl.local.ui.config.InfoParameterImpl;
import com.biglybt.pifimpl.local.ui.config.IntParameterImpl;
import com.biglybt.pifimpl.local.ui.config.LabelParameterImpl;
import com.biglybt.pifimpl.local.ui.config.ParameterGroupImpl;
import com.biglybt.pifimpl.local.ui.config.StringParameterImpl;

/**
 * Options > DownLoadControlBT > Peer Blocking Rules:
 * plain-language summary of every DLCBT and upstream peer-blocking rule,
 * toggles and thresholds to adjust them, and a live readout of active bans.
 */
public class ConfigSectionDownloadControlBTRules
	extends ConfigSectionImpl
{
	public static final String SECTION_ID = "dlcbt.rules";

	public ConfigSectionDownloadControlBTRules()
	{
		super( SECTION_ID, ConfigSectionDownloadControlBT.SECTION_ID );
	}

	@Override
	public void
	build()
	{
		if ( !DownloadControlBT.isProfileEnabled()){
			return;
		}

		add( new LabelParameterImpl( "ConfigView.section.dlcbt.rules.intro" ));

		// 1. Master temporary ban policy & shared ban duration (54-64 min)
		List<Parameter> masterList = new ArrayList<>();
		BooleanParameterImpl masterEnabled = new BooleanParameterImpl(
			DownloadControlBTBanService.CFG_ENABLED,
			"ConfigView.section.dlcbt.rules.master.enabled" );
		add( masterEnabled, masterList );

		IntParameterImpl banMinMin = new IntParameterImpl(
			DownloadControlBTBanService.CFG_MIN_MINUTES,
			"ConfigView.section.dlcbt.rules.ban.minminutes",
			1, 10080 );
		add( banMinMin, masterList );

		IntParameterImpl banMaxMin = new IntParameterImpl(
			DownloadControlBTBanService.CFG_MAX_MINUTES,
			"ConfigView.section.dlcbt.rules.ban.maxminutes",
			1, 10080 );
		add( banMaxMin, masterList );

		masterEnabled.addEnabledOnSelection( banMinMin, banMaxMin );
		add( new ParameterGroupImpl( "ConfigView.section.dlcbt.rules.group.master", masterList ));

		// 2. Speed band rule (10k - 251.1k, 60s ramp, 30s average, 54-64m ban)
		List<Parameter> speedList = new ArrayList<>();
		BooleanParameterImpl speedEnabled = new BooleanParameterImpl(
			DownloadControlBTBanService.CFG_SPEED_ENABLED,
			"ConfigView.section.dlcbt.rules.speed.enabled" );
		add( speedEnabled, speedList );
		add( new LabelParameterImpl( "ConfigView.section.dlcbt.rules.speed.desc" ), speedList );

		StringParameterImpl minRate = new StringParameterImpl(
			DownloadControlBTBanService.CFG_MIN_RATE,
			"ConfigView.section.dlcbt.rules.speed.min" );
		minRate.setWidthInCharacters( 12 );
		minRate.addStringValidator( (p, v) -> {
			if ( v == null || v.trim().isEmpty()){
				return new ValidationInfo( false, "Enter a rate like 10k or 0" );
			}
			try{
				DownloadControlBTRateFormat.parseRate( v );
				return new ValidationInfo( true );
			}catch( IllegalArgumentException e ){
				return new ValidationInfo( false, e.getMessage());
			}
		});
		add( minRate, speedList );

		StringParameterImpl maxRate = new StringParameterImpl(
			DownloadControlBTBanService.CFG_MAX_RATE,
			"ConfigView.section.dlcbt.rules.speed.max" );
		maxRate.setWidthInCharacters( 12 );
		maxRate.addStringValidator( (p, v) -> {
			if ( v == null || v.trim().isEmpty()){
				return new ValidationInfo( true );
			}
			try{
				DownloadControlBTRateFormat.parseRate( v );
				return new ValidationInfo( true );
			}catch( IllegalArgumentException e ){
				return new ValidationInfo( false, e.getMessage());
			}
		});
		add( maxRate, speedList );

		IntParameterImpl rampSec = new IntParameterImpl(
			DownloadControlBTBanService.CFG_RAMP_SECONDS,
			"ConfigView.section.dlcbt.rules.speed.ramp",
			0, 3600 );
		add( rampSec, speedList );

		IntParameterImpl windowSec = new IntParameterImpl(
			DownloadControlBTBanService.CFG_WINDOW_SECONDS,
			"ConfigView.section.dlcbt.rules.speed.window",
			5, 3600 );
		add( windowSec, speedList );

		speedEnabled.addEnabledOnSelection( minRate, maxRate, rampSec, windowSec );
		masterEnabled.addEnabledOnSelection( speedEnabled );
		add( new ParameterGroupImpl( "ConfigView.section.dlcbt.rules.group.speed", speedList ));

		// 3. Handshake wait > 3s (54-64m ban)
		List<Parameter> hsList = new ArrayList<>();
		BooleanParameterImpl hsEnabled = new BooleanParameterImpl(
			DownloadControlBTBanService.CFG_HANDSHAKE_ENABLED,
			"ConfigView.section.dlcbt.rules.handshake.enabled" );
		add( hsEnabled, hsList );
		add( new LabelParameterImpl( "ConfigView.section.dlcbt.rules.handshake.desc" ), hsList );

		IntParameterImpl hsSec = new IntParameterImpl(
			DownloadControlBTBanService.CFG_HANDSHAKE_SECONDS,
			"ConfigView.section.dlcbt.rules.handshake.seconds",
			1, 600 );
		add( hsSec, hsList );

		hsEnabled.addEnabledOnSelection( hsSec );
		masterEnabled.addEnabledOnSelection( hsEnabled );
		add( new ParameterGroupImpl( "ConfigView.section.dlcbt.rules.group.handshake", hsList ));

		// 4. Duplicate incoming connections (3 in 60s, 54-64m ban)
		List<Parameter> dupList = new ArrayList<>();
		BooleanParameterImpl dupEnabled = new BooleanParameterImpl(
			DownloadControlBTBanService.CFG_DUP_ENABLED,
			"ConfigView.section.dlcbt.rules.dup.enabled" );
		add( dupEnabled, dupList );
		add( new LabelParameterImpl( "ConfigView.section.dlcbt.rules.dup.desc" ), dupList );

		IntParameterImpl dupThresh = new IntParameterImpl(
			DownloadControlBTBanService.CFG_DUP_THRESHOLD,
			"ConfigView.section.dlcbt.rules.dup.threshold",
			1, 100 );
		add( dupThresh, dupList );

		IntParameterImpl dupWin = new IntParameterImpl(
			DownloadControlBTBanService.CFG_DUP_WINDOW_SECONDS,
			"ConfigView.section.dlcbt.rules.dup.window",
			1, 3600 );
		add( dupWin, dupList );

		dupEnabled.addEnabledOnSelection( dupThresh, dupWin );
		masterEnabled.addEnabledOnSelection( dupEnabled );
		add( new ParameterGroupImpl( "ConfigView.section.dlcbt.rules.group.dup", dupList ));

		// 5. Hash-fail temporary ban (210-270s)
		List<Parameter> hfList = new ArrayList<>();
		BooleanParameterImpl hfEnabled = new BooleanParameterImpl(
			DownloadControlBTBanService.CFG_HASH_FAIL_ENABLED,
			"ConfigView.section.dlcbt.rules.hashfail.enabled" );
		add( hfEnabled, hfList );
		add( new LabelParameterImpl( "ConfigView.section.dlcbt.rules.hashfail.desc" ), hfList );

		IntParameterImpl hfMin = new IntParameterImpl(
			DownloadControlBTBanService.CFG_HASH_FAIL_MIN_SECONDS,
			"ConfigView.section.dlcbt.rules.hashfail.min",
			1, 86400 );
		add( hfMin, hfList );

		IntParameterImpl hfMax = new IntParameterImpl(
			DownloadControlBTBanService.CFG_HASH_FAIL_MAX_SECONDS,
			"ConfigView.section.dlcbt.rules.hashfail.max",
			1, 86400 );
		add( hfMax, hfList );

		hfEnabled.addEnabledOnSelection( hfMin, hfMax );
		masterEnabled.addEnabledOnSelection( hfEnabled );
		add( new ParameterGroupImpl( "ConfigView.section.dlcbt.rules.group.hashfail", hfList ));

		// 6. Idle-peer ban (5s no payload, 54-64m ban)
		List<Parameter> idleList = new ArrayList<>();
		BooleanParameterImpl idleEnabled = new BooleanParameterImpl(
			DownloadControlBTIdleBan.CFG_ENABLED,
			"ConfigView.section.dlcbt.rules.idle.enabled" );
		add( idleEnabled, idleList );
		add( new LabelParameterImpl( "ConfigView.section.dlcbt.rules.idle.desc" ), idleList );

		IntParameterImpl idleSec = new IntParameterImpl(
			DownloadControlBTIdleBan.CFG_IDLE_SECONDS,
			"ConfigView.section.dlcbt.rules.idle.seconds",
			1, 3600 );
		add( idleSec, idleList );

		idleEnabled.addEnabledOnSelection( idleSec );
		masterEnabled.addEnabledOnSelection( idleEnabled );
		add( new ParameterGroupImpl( "ConfigView.section.dlcbt.rules.group.idle", idleList ));

		// 7. Connecting timeout kick (30-35s, disconnect only)
		List<Parameter> connList = new ArrayList<>();
		BooleanParameterImpl connEnabled = new BooleanParameterImpl(
			DownloadControlBTConnectTimeout.CFG_ENABLED,
			"ConfigView.section.dlcbt.rules.connect.enabled" );
		add( connEnabled, connList );
		add( new LabelParameterImpl( "ConfigView.section.dlcbt.rules.connect.desc" ), connList );

		IntParameterImpl connSec = new IntParameterImpl(
			DownloadControlBTConnectTimeout.CFG_KICK_SECONDS,
			"ConfigView.section.dlcbt.rules.connect.seconds",
			1, 600 );
		add( connSec, connList );

		connEnabled.addEnabledOnSelection( connSec );
		add( new ParameterGroupImpl( "ConfigView.section.dlcbt.rules.group.connect", connList ));

		// 8. Rarest-only upload & disconnect (8 rejects, disconnect only)
		List<Parameter> rarestList = new ArrayList<>();
		BooleanParameterImpl rarestEnabled = new BooleanParameterImpl(
			DownloadControlBTDefaults.CFG_RAREST_ONLY,
			"ConfigView.section.dlcbt.rarestonly" );
		add( rarestEnabled, rarestList );

		BooleanParameterImpl rarestKickEnabled = new BooleanParameterImpl(
			DownloadControlBTRarestUpload.CFG_DISCONNECT_ENABLED,
			"ConfigView.section.dlcbt.rules.rarest.kick.enabled" );
		add( rarestKickEnabled, rarestList );
		add( new LabelParameterImpl( "ConfigView.section.dlcbt.rules.rarest.desc" ), rarestList );

		IntParameterImpl rarestRejects = new IntParameterImpl(
			DownloadControlBTRarestUpload.CFG_DISCONNECT_REJECTS,
			"ConfigView.section.dlcbt.rules.rarest.rejects",
			1, 100 );
		add( rarestRejects, rarestList );

		rarestEnabled.addEnabledOnSelection( rarestKickEnabled, rarestRejects );
		rarestKickEnabled.addEnabledOnSelection( rarestRejects );
		add( new ParameterGroupImpl( "ConfigView.section.dlcbt.rules.group.rarest", rarestList ));

		// 9. Upstream BiglyBT rules kept
		List<Parameter> upstreamList = new ArrayList<>();
		add( new LabelParameterImpl( "ConfigView.section.dlcbt.rules.upstream.bad_data" ), upstreamList );
		add( new LabelParameterImpl( "ConfigView.section.dlcbt.rules.upstream.discard" ), upstreamList );
		add( new LabelParameterImpl( "ConfigView.section.dlcbt.rules.upstream.timeouts" ), upstreamList );
		add( new ParameterGroupImpl( "ConfigView.section.dlcbt.rules.group.upstream", upstreamList ));

		// 10. Current active temporary bans
		List<Parameter> activeList = new ArrayList<>();
		InfoParameterImpl activeInfo = new InfoParameterImpl(
			null,
			null,
			DownloadControlBTBanService.get().formatActiveBansSummary( System.currentTimeMillis()));
		activeInfo.setTextSelectable( true );
		add( activeInfo, activeList );

		ActionParameterImpl refreshBtn = new ActionParameterImpl(
			"ConfigView.section.dlcbt.rules.active.refresh.label",
			"ConfigView.section.dlcbt.rules.active.refresh.btn" );
		refreshBtn.addListener( p -> activeInfo.setValue(
			DownloadControlBTBanService.get().formatActiveBansSummary( System.currentTimeMillis())));
		add( refreshBtn, activeList );

		ActionParameterImpl clearBtn = new ActionParameterImpl(
			"ConfigView.section.dlcbt.rules.active.clear.label",
			"ConfigView.section.dlcbt.rules.active.clear.btn" );
		clearBtn.addListener( p -> {
			DownloadControlBTBanService.get().unbanAllOwn();
			activeInfo.setValue(
				DownloadControlBTBanService.get().formatActiveBansSummary( System.currentTimeMillis()));
		});
		add( clearBtn, activeList );

		add( new ParameterGroupImpl( "ConfigView.section.dlcbt.rules.group.active", activeList ));
	}
}
