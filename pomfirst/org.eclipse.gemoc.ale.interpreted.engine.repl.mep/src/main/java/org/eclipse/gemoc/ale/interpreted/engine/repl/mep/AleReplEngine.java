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
package org.eclipse.gemoc.ale.interpreted.engine.repl.mep;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import org.eclipse.acceleo.query.runtime.EvaluationResult;
import org.eclipse.acceleo.query.runtime.IService;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecoretools.ale.core.env.ClosedAleEnvironmentException;
import org.eclipse.emf.ecoretools.ale.core.env.IAleEnvironment;
import org.eclipse.emf.ecoretools.ale.core.env.impl.ImmutableBehaviors;
import org.eclipse.emf.ecoretools.ale.core.interpreter.IServiceCallListener;
import org.eclipse.emf.ecoretools.ale.core.interpreter.impl.AleInterpreter;
import org.eclipse.emf.ecoretools.ale.core.interpreter.services.EvalBodyService;
import org.eclipse.emf.ecoretools.ale.implementation.Method;
import org.eclipse.emf.ecoretools.ale.implementation.ModelUnit;
import org.eclipse.gemoc.ale.interpreted.engine.Helper;
import org.eclipse.gemoc.executionframework.engine.commons.DslHelper;
import org.eclipse.gemoc.executionframework.engine.commons.sequential.ISequentialModelExecutionContext;
import org.eclipse.gemoc.executionframework.engine.commons.sequential.ISequentialRunConfiguration;
import org.eclipse.gemoc.executionframework.engine.core.AbstractSequentialExecutionEngine;
import org.eclipse.gemoc.executionframework.extensions.sirius.services.IModelAnimator;
import org.eclipse.gemoc.trace.commons.model.trace.Step;
import org.eclipse.gemoc.trace.gemoc.api.IMultiDimensionalTraceAddon;
import org.eclipse.gemoc.trace.gemoc.api.ITraceViewListener;
import org.eclipse.gemoc.xdsmlframework.api.engine_addon.IEngineAddon;

import com.google.common.collect.Lists;

import fr.inria.diverse.ale.repl.REPLInterpreter;
import fr.inria.diverse.ale.repl.notebook.KernelServer;

public class AleReplEngine extends AbstractSequentialExecutionEngine<ISequentialModelExecutionContext<?>, ISequentialRunConfiguration> {

	/**
	 * Root of the model
	 */
	EObject caller;
	
	/**
	 * The semantic from .ale files
	 */
	ImmutableBehaviors parsedSemantics;
	
	List<Object> args;
	
	REPLInterpreter interpreter;

	private String mainOp;

	private String initOp;
	
	volatile Semaphore terminatedSemaphore;
	
	private KernelServer jupyterKernelServer;
	
	@Override
	public String engineKindName() {
		return "ALE Engine";
	}

	@Override
	protected void executeEntryPoint() {
		if(interpreter != null && parsedSemantics != null) {
			interpreter.getAleInterpreter().addServiceListener(new IServiceCallListener() {
				
				@Override
				public void preCall(IService service, Object[] arguments) {
					if(service instanceof EvalBodyService) {
						boolean isStep = ((EvalBodyService)service).getImplem().getTags().contains("step");
						if(isStep) {
							if (arguments[0] instanceof EObject) {
								EObject currentCaller = (EObject) arguments[0];
								String className = currentCaller.eClass().getName();
								String methodName = service.getName();
								beforeExecutionStep(currentCaller, className, methodName);
							}
						}
					}
				}
				
				@Override
				public void postCall(IService service, Object[] arguments, Object result) {
					if(service instanceof EvalBodyService) {
						boolean isStep = ((EvalBodyService)service).getImplem().getTags().contains("step");
						if(isStep) {
							afterExecutionStep();
						}
					}
				}
			});
			
			//Register animation updater
			IMultiDimensionalTraceAddon traceCandidate = null;
			List<IModelAnimator> animators = new ArrayList<>();
			for (IEngineAddon addon : AleReplEngine.this.getExecutionContext().getExecutionPlatform().getEngineAddons()) {
				if(addon instanceof IMultiDimensionalTraceAddon) {
					traceCandidate = (IMultiDimensionalTraceAddon) addon;
				}
				else if(addon instanceof IModelAnimator) {
					animators.add((IModelAnimator) addon);
				}
			}
			
			final IMultiDimensionalTraceAddon traceAddon = traceCandidate;
			ITraceViewListener diagramUpdater = new ITraceViewListener() {
				@Override
				public void update() {
					for (IModelAnimator addon : animators) {
						try {
							if(traceAddon != null) {
								Step<?> nextStep = (Step<?>) traceAddon.getTraceExplorer().getCurrentState().getStartedSteps().get(0);
								addon.activate(caller,nextStep);
							}
						} catch (Exception exception) {
							// Update failed
						}
					}
				}
			};
			if(traceAddon != null) {
				traceAddon.getTraceExplorer().registerCommand(diagramUpdater, () -> diagramUpdater.update());
			}
			
			try {
				this.terminatedSemaphore.acquire();;
			} catch (InterruptedException e) {
				throw new RuntimeException(e.getMessage(),e);
			}
			
			if(traceAddon != null) {
				traceAddon.getTraceExplorer().removeListener(diagramUpdater);
			}
		}

	}
	
	protected void manageDiagnostic(EvaluationResult res) {
		interpreter.getAleInterpreter().getLogger().diagnosticForHuman();
		
		if(res.getDiagnostic().getMessage() != null) {
			System.out.println(res.getDiagnostic().getMessage());
			throw new RuntimeException(res.getDiagnostic().getMessage());
		}
	}

	@Override
	protected void initializeModel() {
		this.terminatedSemaphore = new Semaphore(0);
	}
	
	public void setKernelServer(KernelServer kernelServer) {
		this.jupyterKernelServer = kernelServer;
	}

	@Override
	protected void prepareEntryPoint(ISequentialModelExecutionContext<?> executionContext) {
		
	}

	@Override
	protected void prepareInitializeModel(ISequentialModelExecutionContext<?> executionContext) {
		ISequentialRunConfiguration runConf = executionContext.getRunConfiguration();
		
		// caller
		Resource inputModel = executionContext.getResourceModel();
		String rootPath = runConf.getModelEntryPoint();
		caller = inputModel.getEObject(rootPath);
		
		// dslFile
		org.eclipse.gemoc.dsl.Dsl language = this.findGemocDsl(executionContext);

		// arguments
		args = Lists.newArrayList(runConf.getModelInitializationArguments().split("\n"));
		
		mainOp = runConf.getExecutionEntryPoint();
		initOp = runConf.getModelInitializationMethod();
		
		IAleEnvironment environment = Helper.gemocDslToAleDsl(language);
		interpreter = new REPLInterpreter(environment, language.getName().toLowerCase());
		parsedSemantics = new ImmutableBehaviors(environment.getBehaviors().getParsedFiles());
		
		if (this.jupyterKernelServer != null) {
			this.jupyterKernelServer.setInterpreter(interpreter);
			this.jupyterKernelServer.start();
		}
		
		/*
		 * Init interpreter
		 */
		Set<String> projects = new HashSet<String>();
		Set<String> plugins = new HashSet<String>();

		if(language.eResource().getURI().isPlatformPlugin()) {
			URI dslUri = language.eResource().getURI();
			String dslPlugin = dslUri.segmentsList().get(1);
			plugins.add(dslPlugin);
			
			List<String> ecoreUris = Helper.getEcoreUris(language);
			for(String ecoreURI : ecoreUris) {
				URI uri = URI.createURI(ecoreURI);
				String plugin = uri.segmentsList().get(1);
				plugins.add(plugin);
			}
			
			List<String> aleUris = Helper.getAleUris(language);
			for(String aleURI : aleUris) {
				URI uri = URI.createURI(aleURI);
				String plugin = uri.segmentsList().get(1);
				plugins.add(plugin);
			}
		}
		interpreter.getAleInterpreter().initScope(plugins, projects);
	}
	
	protected org.eclipse.gemoc.dsl.Dsl findGemocDsl(ISequentialModelExecutionContext<?> executionContext) {
		return DslHelper.load(executionContext.getRunConfiguration().getLanguageName());
	}
	
	public List<ModelUnit> getModelUnits() {
		if(parsedSemantics != null) {
			return 
				parsedSemantics.getParsedFiles()
				.stream()
				.map(p -> p.getRoot())
				.filter(elem -> elem != null)
				.collect(Collectors.toList());
		}
		return Lists.newArrayList();
	}
	
	public AleInterpreter getInterpreter() {
		return interpreter.getAleInterpreter();
	}
	
	public Optional<Method> getMainOp() {
		if(mainOp != null) {
			List<String> segments = Lists.newArrayList(mainOp.split("::"));
			if(segments.size() >= 2) {
				String opName = segments.get(segments.size() - 1);
				String typeName = segments.get(segments.size() - 2);
				
				return
					getModelUnits()
					.stream()
					.flatMap(unit -> unit.getClassExtensions().stream())
					.filter(xtdCls -> xtdCls.getBaseClass().getName().equals(typeName))
					.flatMap(cls -> cls.getMethods().stream())
					.filter(op -> op.getTags().contains("main"))
					.filter(op -> op.getOperationRef().getName().equals(opName))
					.findFirst();
			}
		}
		return Optional.empty();
	}
	
	public Optional<Method> getInitOp() {
		if(initOp != null) {
			List<String> segments = Lists.newArrayList(initOp.split("::"));
			if(segments.size() >= 2) {
				String opName = segments.get(segments.size() - 1);
				String typeName = segments.get(segments.size() - 2);
				
				return
					getModelUnits()
					.stream()
					.flatMap(unit -> unit.getClassExtensions().stream())
					.filter(xtdCls -> xtdCls.getBaseClass().getName().equals(typeName))
					.flatMap(cls -> cls.getMethods().stream())
					.filter(op -> op.getTags().contains("init"))
					.filter(op -> op.getOperationRef().getName().equals(opName))
					.findFirst();
			}
		}
		return Optional.empty();
	}

	@Override
	protected void finishDispose() {
		super.finishDispose();
		interpreter.getAleInterpreter().close();
	}
	
	
}
