package org.eclipse.gemoc.ale.interpreted.engine.ui.launcher.tabs;

import org.eclipse.debug.ui.AbstractLaunchConfigurationTabGroup;
import org.eclipse.debug.ui.CommonTab;
import org.eclipse.debug.ui.ILaunchConfigurationDialog;
import org.eclipse.debug.ui.ILaunchConfigurationTab;

public class LauncherTabGroup extends AbstractLaunchConfigurationTabGroup {

	public LauncherTabGroup() {
	}

	@Override
	public void createTabs(ILaunchConfigurationDialog dialog, String mode) {
		LaunchConfigurationMainTab mainTab = new LaunchConfigurationMainTab();
		LaunchConfigurationBackendsTab addonTab = new LaunchConfigurationBackendsTab();
		mainTab.registerLaunchLanguageSelectionListener(addonTab);
		
		ILaunchConfigurationTab[] tabs = new ILaunchConfigurationTab[] { mainTab, addonTab, new CommonTab() };
		setTabs(tabs);

	}

}
