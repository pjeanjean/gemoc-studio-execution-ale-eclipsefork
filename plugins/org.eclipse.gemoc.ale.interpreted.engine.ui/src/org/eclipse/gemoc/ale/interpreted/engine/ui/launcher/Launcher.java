/*******************************************************************************
 * Copyright (c) 2018, 2020 Inria and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.gemoc.ale.interpreted.engine.ui.launcher;

import java.util.Arrays;
import java.util.Set;
import java.util.function.BiPredicate;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.gemoc.ale.interpreted.engine.AleEngine;
import org.eclipse.gemoc.ale.interpreted.engine.debug.AleDynamicAccessor;
import org.eclipse.gemoc.ale.interpreted.engine.sirius.ALESiriusInterpreter;
import org.eclipse.gemoc.ale.interpreted.engine.sirius.ALESiriusInterpreterProviderAddon;
import org.eclipse.gemoc.ale.interpreted.engine.ui.Activator;
import org.eclipse.gemoc.commons.eclipse.messagingsystem.api.MessagingSystem;
import org.eclipse.gemoc.commons.eclipse.ui.ViewHelper;
import org.eclipse.gemoc.dsl.debug.ide.IDSLDebugger;
import org.eclipse.gemoc.dsl.debug.ide.event.DSLDebugEventDispatcher;
import org.eclipse.gemoc.executionframework.debugger.AbstractGemocDebugger;
import org.eclipse.gemoc.executionframework.debugger.GenericSequentialModelDebugger;
import org.eclipse.gemoc.executionframework.debugger.OmniscientGenericSequentialModelDebugger;
import org.eclipse.gemoc.executionframework.engine.commons.EngineContextException;
import org.eclipse.gemoc.executionframework.engine.commons.sequential.ISequentialRunConfiguration;
import org.eclipse.gemoc.executionframework.engine.commons.sequential.SequentialModelExecutionContext;
import org.eclipse.gemoc.executionframework.engine.commons.sequential.SequentialRunConfiguration;
import org.eclipse.gemoc.executionframework.engine.ui.launcher.AbstractSequentialGemocLauncher;
import org.eclipse.gemoc.executionframework.ui.views.engine.EnginesStatusView;
import org.eclipse.gemoc.trace.commons.model.trace.Step;
import org.eclipse.gemoc.trace.gemoc.traceaddon.GenericTraceEngineAddon;
import org.eclipse.gemoc.xdsmlframework.api.core.ExecutionMode;
import org.eclipse.gemoc.xdsmlframework.api.core.IExecutionEngine;
import org.eclipse.gemoc.xdsmlframework.api.engine_addon.IEngineAddon;

public class Launcher extends AbstractSequentialGemocLauncher {

	public final static String TYPE_ID = Activator.PLUGIN_ID + ".launcher";
	
	@Override
	protected AleEngine createExecutionEngine(ISequentialRunConfiguration runConfiguration, ExecutionMode executionMode)
			throws CoreException, EngineContextException {
		
		AleEngine engine = new AleEngine();
		
		SequentialModelExecutionContext executioncontext = new SequentialModelExecutionContext(runConfiguration, executionMode);
		executioncontext.initializeResourceModel(); // load model
		engine.initialize(executioncontext);
		
		
		// declare this engine as available for ale: queries in the odesign
		ALESiriusInterpreter.getDefault().addAleEngine(engine);
		// create and add addon to unregister when then engine will be disposed
		IEngineAddon aleRTDInterpreter = new ALESiriusInterpreterProviderAddon();
		Activator.getDefault().getMessaggingSystem().debug("Enabled implicit addon: "+aleRTDInterpreter.getAddonID(), getPluginID());
		engine.getExecutionContext().getExecutionPlatform().addEngineAddon(aleRTDInterpreter);
	
		
		return engine;
	}

	@Override
	protected void prepareViews() {
		ViewHelper.retrieveView(EnginesStatusView.ID);
	}

	@Override
	protected SequentialRunConfiguration parseLaunchConfiguration(ILaunchConfiguration configuration) throws CoreException {
		return new SequentialRunConfiguration(configuration);
	}

	@Override
	protected MessagingSystem getMessagingSystem() {
		return Activator.getDefault().getMessaggingSystem();
	}

	@Override
	protected void error(String message, Exception e) {
		Activator.error(message, e);
	}

	@Override
	protected void setDefaultsLaunchConfiguration(ILaunchConfigurationWorkingCopy configuration) {
		
	}

	@Override
	protected String getLaunchConfigurationTypeID() {
		return TYPE_ID;
	}

	@Override
	protected IDSLDebugger getDebugger(ILaunchConfiguration configuration, DSLDebugEventDispatcher dispatcher,
			EObject firstInstruction, IProgressMonitor monitor) {
		
		AleEngine engine = (AleEngine) getExecutionEngine();
		
		Set<GenericTraceEngineAddon> traceAddons = engine.getAddonsTypedBy(GenericTraceEngineAddon.class);
		for(GenericTraceEngineAddon addon : traceAddons) {
			addon.setDynamicPartAccessor(new AleDynamicAccessor(engine.getInterpreter(),engine.getModelUnits()));
		}
		
		AbstractGemocDebugger debugger;
		if (traceAddons.isEmpty()) {
			debugger = new GenericSequentialModelDebugger(dispatcher, engine);
		} else {
			debugger = new OmniscientGenericSequentialModelDebugger(dispatcher, engine);
		}
		
		debugger.setMutableFieldExtractors(Arrays.asList(new AleDynamicAccessor(engine.getInterpreter(),engine.getModelUnits())));
		
		// If in the launch configuration it is asked to pause at the start,
		// we add this dummy break
		try {
			if (configuration.getAttribute(SequentialRunConfiguration.LAUNCH_BREAK_START, false)) {
				debugger.addPredicateBreak(new BiPredicate<IExecutionEngine<?>, Step<?>>() {
					@Override
					public boolean test(IExecutionEngine<?> t, Step<?> u) {
						return true;
					}
				});
			}
		} catch (CoreException e) {
			Activator.error(e.getMessage(), e);
		}

		Activator.getDefault().getMessaggingSystem().debug("Enabled implicit addon: "+debugger.getAddonID(), getPluginID());
		engine.getExecutionContext().getExecutionPlatform().addEngineAddon(debugger);
		return debugger;
	}

	@Override
	protected String getDebugJobName(ILaunchConfiguration configuration, EObject firstInstruction) {
		return "GEMOC Debug Job";
	}

	@Override
	protected String getPluginID() {
		return Activator.PLUGIN_ID;
	}

	@Override
	public String getModelIdentifier() {
		//FIXME: Should be retrieve by IExecutionContext.getRunConfiguration().getDebugModelID()
		return Activator.DEBUG_MODEL_ID;
	}
	
}
