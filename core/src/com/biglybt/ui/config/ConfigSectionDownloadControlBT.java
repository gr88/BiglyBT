/*
 * DownLoadControlBT (unofficial fork experiment). GPL like BiglyBT.
 */
package com.biglybt.ui.config;

import com.biglybt.core.downloadcontrolbt.DownloadControlBT;
import com.biglybt.core.downloadcontrolbt.DownloadControlBTDefaults;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pifimpl.local.ui.config.BooleanParameterImpl;
import com.biglybt.pifimpl.local.ui.config.IntParameterImpl;

public class ConfigSectionDownloadControlBT
	extends ConfigSectionImpl
{
	public static final String SECTION_ID = "dlcbt";

	public ConfigSectionDownloadControlBT()
	{
		super( SECTION_ID, ConfigSection.SECTION_ROOT );
	}

	@Override
	public void
	build()
	{
		if ( !DownloadControlBT.isProfileEnabled()){
			return;
		}
		BooleanParameterImpl rarest = new BooleanParameterImpl(
			DownloadControlBTDefaults.CFG_RAREST_ONLY,
			"ConfigView.section.dlcbt.rarestonly" );
		add( rarest );

		IntParameterImpl autofit = new IntParameterImpl(
			DownloadControlBTDefaults.CFG_AUTOFIT_SECONDS,
			"ConfigView.section.dlcbt.autofit.seconds",
			0, 24 * 60 * 60 );
		add( autofit );
	}
}
