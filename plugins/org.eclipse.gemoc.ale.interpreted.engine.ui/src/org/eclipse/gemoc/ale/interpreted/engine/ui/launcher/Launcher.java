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
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.gemoc.ale.interpreted.engine.AleEngine;
import org.eclipse.gemoc.ale.interpreted.engine.debug.AleDynamicAccessor;
import org.eclipse.gemoc.ale.interpreted.engine.sirius.ALEInterpreterProvider;
import org.eclipse.gemoc.ale.interpreted.engine.ui.Activator;
import org.eclipse.gemoc.commons.eclipse.messagingsystem.api.MessagingSystem;
import org.eclipse.gemoc.commons.eclipse.ui.ViewHelper;
import org.eclipse.gemoc.dsl.debug.ide.IDSLDebugger;
import org.eclipse.gemoc.dsl.debug.ide.event.DSLDebugEventDispatcher;
import org.eclipse.gemoc.executionframework.debugger.AbstractGemocDebugger;
import org.eclipse.gemoc.executionframework.debugger.GenericSequentialModelDebugger;
import org.eclipse.gemoc.executionframework.debugger.OmniscientGenericSequentialModelDebugger;
import org.eclipse.gemoc.executionframework.engine.commons.EngineContextException;
import org.eclipse.gemoc.executionframework.engine.commons.GenericModelExecutionContext;
import org.eclipse.gemoc.executionframework.engine.commons.sequential.SequentialRunConfiguration;
import org.eclipse.gemoc.executionframework.engine.ui.launcher.AbstractSequentialGemocLauncher;
import org.eclipse.gemoc.executionframework.ui.views.engine.EnginesStatusView;
import org.eclipse.gemoc.trace.commons.model.trace.Step;
import org.eclipse.gemoc.trace.gemoc.traceaddon.GenericTraceEngineAddon;
import org.eclipse.gemoc.xdsmlframework.api.core.ExecutionMode;
import org.eclipse.gemoc.xdsmlframework.api.core.IExecutionEngine;
import org.eclipse.sirius.common.tools.api.interpreter.CompoundInterpreter;
import org.eclipse.sirius.common.tools.api.interpreter.IInterpreterProvider;

public class Launcher extends AbstractSequentialGemocLauncher<GenericModelExecutionContext<SequentialRunConfiguration>, SequentialRunConfiguration> {

	public final static String TYPE_ID = Activator.PLUGIN_ID + ".launcher";
	
	@Override
	protected AleEngine createExecutionEngine(SequentialRunConfiguration runConfiguration, ExecutionMode executionMode)
			throws CoreException, EngineContextException {
		
		AleEngine engine = new AleEngine();
		
		Set<IInterpreterProvider> aleProviders = 
				CompoundInterpreter
				.INSTANCE
				.getProviders()
				.stream()
				.filter(p -> p instanceof ALEInterpreterProvider)
				.collect(Collectors.toSet());
		aleProviders.forEach(p -> CompoundInterpreter.INSTANCE.removeInterpreter(p));
		
		IInterpreterProvider provider = new ALEInterpreterProvider(engine);
		CompoundInterpreter.INSTANCE.registerProvider(provider); //Register ALE for Sirius
		
		GenericModelExecutionContext<SequentialRunConfiguration> executioncontext = new GenericModelExecutionContext<SequentialRunConfiguration>(runConfiguration, executionMode);
		executioncontext.initializeResourceModel(); // load model
		engine.initialize(executioncontext);
		
		//TODO: CompoundInterpreter.INSTANCE.removeInterpreter(provider);
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
