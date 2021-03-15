package org.eclipse.gemoc.ale.interpreted.engine.repl.mep;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

import org.eclipse.acceleo.query.runtime.EvaluationResult;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.gemoc.commons.utils.ModelAwarePrintStream;
import org.eclipse.gemoc.dsl.Dsl;
import org.eclipse.gemoc.dsl.debug.ide.event.model.StartRequest;
import org.eclipse.gemoc.executionframework.engine.headless.AbstractSequentialHeadlessExecutionContext;
import org.eclipse.gemoc.executionframework.engine.headless.HeadlessExecutionPlatform;
import org.eclipse.gemoc.executionframework.engine.headless.HeadlessExecutionWorkspace;
import org.eclipse.gemoc.executionframework.engine.headless.HeadlessJavaEngineSequentialRunConfiguration;
import org.eclipse.gemoc.executionframework.debugger.DefaultDynamicPartAccessor;
import org.eclipse.gemoc.executionframework.debugger.IDynamicPartAccessor;
import org.eclipse.gemoc.executionframework.debugger.MutableField;
import org.eclipse.gemoc.executionframework.engine.commons.EngineContextException;
import org.eclipse.gemoc.executionframework.engine.commons.sequential.ISequentialModelExecutionContext;
import org.eclipse.gemoc.executionframework.engine.commons.sequential.ISequentialRunConfiguration;
import org.eclipse.gemoc.executionframework.engine.headless.FakeOSGI;
import org.eclipse.gemoc.executionframework.mep.engine.IMEPEngine;
import org.eclipse.gemoc.executionframework.mep.engine.IMEPEventListener;
import org.eclipse.gemoc.executionframework.mep.events.Output;
import org.eclipse.gemoc.executionframework.mep.events.Stopped;
import org.eclipse.gemoc.executionframework.mep.events.StoppedReason;
import org.eclipse.gemoc.executionframework.mep.launch.MEPLauncherParameters;
import org.eclipse.gemoc.executionframework.mep.types.SourceBreakpoint;
import org.eclipse.gemoc.executionframework.mep.types.StackFrame;
import org.eclipse.gemoc.executionframework.mep.types.Variable;
import org.eclipse.gemoc.trace.commons.model.trace.Step;
import org.eclipse.gemoc.trace.gemoc.traceaddon.GenericTraceEngineAddon;
import org.eclipse.gemoc.xdsmlframework.api.core.ExecutionMode;
import org.eclipse.gemoc.xdsmlframework.api.core.IExecutionEngine;
import org.eclipse.gemoc.xdsmlframework.api.extensions.languages.LanguageDefinitionExtension;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.gemoc.DslStandaloneSetup;


public class AleReplEngineMEP<L extends LanguageDefinitionExtension> extends AleReplEngine implements IMEPEngine {

	ISequentialRunConfiguration runConfiguration = null;
	L languageDefinition;
	IHeadlessGemocDebugger modelDebugger = null;
	HeadlessDebugEventHandler debugEventHandler = null;
	AbstractSequentialHeadlessExecutionContext executionContext = null;
	IDynamicPartAccessor partAccessor = null;
	
	private PrintStream baseStream;
	private ModelAwarePrintStream modelPrintStream;
		
	public AleReplEngineMEP(L languageDefinition) {
		this.languageDefinition = languageDefinition;
	}
	
	@Override
	public IHeadlessGemocDebugger getDebugger() {
		return this.modelDebugger;
	}
	
	private GenericTraceEngineAddon traceAddon;
	public void setTraceAddon(GenericTraceEngineAddon traceAddon) {
		this.traceAddon = traceAddon;
	}
	@Override
	public void internalLaunchEngine(MEPLauncherParameters launchParameters) {
		Resource resourceModel = launchParameters.resourceModel;
		String languageName = launchParameters.languageName;
		String modelEntryPoint = launchParameters.modelEntryPoint;
		String methodEntryPoint = launchParameters.methodEntryPoint;
		String initializationMethod = launchParameters.initializationMethod;
		String initializationMethodArgs = launchParameters.initializationMethodArgs;
		
		runConfiguration = new HeadlessJavaEngineSequentialRunConfiguration(resourceModel.getURI(), languageName,
				modelEntryPoint, methodEntryPoint, initializationMethod, initializationMethodArgs);
		
		try {
			executionContext = newExecutionContext(resourceModel);	
			executionContext.initializeResourceModel();
			
			FakeOSGI.start();
			org.eclipse.emf.transaction.TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain(resourceModel.getResourceSet());
			
			this.initialize(executionContext);			

			debugEventHandler = new HeadlessDebugEventHandler();
			if (traceAddon != null) {
				executionContext.getExecutionPlatform().addEngineAddon(traceAddon);
				modelDebugger = new HeadlessOmniscientGenericSequentialModelDebugger(debugEventHandler, this);
			} else {
				modelDebugger = new HeadlessGenericSequentialModelDebugger(debugEventHandler, this);
			}
			
			executionContext.getExecutionPlatform().addEngineAddon(modelDebugger);
			partAccessor = new DefaultDynamicPartAccessor();
			
			// break on start
			modelDebugger.addPredicateBreak(new BiPredicate<IExecutionEngine<?>, Step<?>>() {
				@Override
				public boolean test(IExecutionEngine<?> t, Step<?> u) {
					return true;
				}
			});
			
			modelDebugger.handleEvent(new StartRequest());
		} catch (EngineContextException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	protected org.eclipse.gemoc.dsl.Dsl findGemocDsl(ISequentialModelExecutionContext<?> executionContext) {
		String xdsmlPath = executionContext.getLanguageDefinitionExtension().getXDSMLFilePath();
		URI xdsmlURI = URI.createURI(xdsmlPath);
		XtextResourceSet rs = new DslStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(XtextResourceSet.class);
		Resource res = rs.getResource(xdsmlURI, true);
		Dsl dsl = (Dsl) res.getContents().get(0);
		return dsl;
	}
	
	@Override
	protected void manageDiagnostic(EvaluationResult res) {

	}
	
	@Override
	protected void notifyEngineAboutToStart() {
		super.notifyEngineAboutToStart();
		
		/*
		final IMEPEngine engine = this;
		ByteArrayOutputStream modelOutputStream = new ByteArrayOutputStream() {
			@Override
			public synchronized void write(int b) {
				write(new byte[] {(byte)b}, 0, 1);
			}

			@Override
			public synchronized void write(byte[] b, int off, int len) {
				Output outputEvent = new Output(engine, new String(Arrays.copyOfRange(b, off, len)));
				notifyListeners(outputEvent);
			}
		};
		baseStream = System.out;
		modelPrintStream = new ModelAwarePrintStream(modelOutputStream, baseStream);
		modelPrintStream.registerModelExecutionThread(thread);
		System.setOut(modelPrintStream);
		*/
	}

	protected AbstractSequentialHeadlessExecutionContext newExecutionContext(Resource resourceModel) throws EngineContextException {
		return new AbstractSequentialHeadlessExecutionContext(
				runConfiguration, 
				ExecutionMode.Run, 
				languageDefinition, 
				new HeadlessExecutionWorkspace(), 
				new HeadlessExecutionPlatform()){				
					@Override
					public void initializeResourceModel() {
						_resourceModel = resourceModel;
					}
			};
	}

	@Override
	public void internalNext() {
		modelDebugger.stepOver(modelDebugger.getThreadName());
		manageAfterStep();
	}

	@Override
	public void internalStepIn() {
		modelDebugger.stepInto(modelDebugger.getThreadName());
		manageAfterStep();
	}

	@Override
	public void internalStepOut() {
		modelDebugger.stepReturn(modelDebugger.getThreadName());
		manageAfterStep();
	}
	
	private void manageAfterStep() {
		if (modelDebugger.isTerminated()) {
			System.setOut(baseStream);
			notifyListeners(new Stopped(this, StoppedReason.REACHED_SIMULATION_END));
		} else {
			notifyListeners(new Stopped(this, StoppedReason.REACHED_NEXT_LOGICAL_STEP));
		}
	}

	@Override
	public void internalSetBreakpoints(SourceBreakpoint[] breakpoints) {
		// TODO Clear existing breakpoints
		for (SourceBreakpoint bp : breakpoints) {
			modelDebugger.addPredicateBreakpoint(new BiPredicate<IExecutionEngine<?>, Step<?>>() {
				@Override
				public boolean test(IExecutionEngine<?> t, Step<?> u) {
					return NodeModelUtils.getNode(u.getMseoccurrence().getMse().getCaller()).getStartLine() == bp.getLine();
				}
			});
		}
	}

	@Override
	public void internalTerminate() {
		this.terminatedSemaphore.release();
		System.setOut(baseStream);
		modelDebugger.terminate();
	}

	@Override
	public void internalContinue() {
		final AleReplEngine engine = this;
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					debugEventHandler.waitBreakReached();
					if (modelDebugger.isTerminated()) {
						System.setOut(baseStream);
						notifyListeners(new Stopped(engine, StoppedReason.REACHED_SIMULATION_END));
					} else {
						notifyListeners(new Stopped(engine, StoppedReason.REACHED_NEXT_LOGICAL_STEP));
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}).start();
		debugEventHandler.clearPermits();
		modelDebugger.resume();
	}

	@Override
	public Variable[] internalVariables() {
		EObject rootElement = executionContext.getResourceModel().getEObject(
				executionContext.getRunConfiguration().getModelEntryPoint());
		List<Variable> variables = new ArrayList<>();
		for (MutableField mf : partAccessor.extractMutableField(rootElement)) {
			variables.add(new Variable(mf.getName(), mf.getValue() ==  null ? "null" : mf.getValue().toString()));
		}
		return variables.toArray(new Variable[0]);
	}

	@Override
	public StackFrame[] internalStackTrace() {
		EObject eObj = modelDebugger.getCurrentInstruction();
		List<StackFrame> mepFrames = new ArrayList<StackFrame>();
		long i = 0L;
		while (eObj != null) {
			ICompositeNode node = NodeModelUtils.getNode(eObj);
			StackFrame frame = new StackFrame(i++, eObj.toString(), (long) node.getStartLine(), 0L);
			mepFrames.add(frame);
			eObj = eObj.eContainer();
		}
		return mepFrames.toArray(new StackFrame[0]);
	}

	@Override
	public String internalSource() {
		EObject rootElement = executionContext.getResourceModel().getEObject(
				executionContext.getRunConfiguration().getModelEntryPoint());
		return NodeModelUtils.getNode(rootElement).getText();
	}

	private List<IMEPEventListener> mepEventListeners = new ArrayList<>();
	
	@Override
	public void addMEPEventListener(IMEPEventListener listener) {
		mepEventListeners.add(listener);
	}

	@Override
	public void removeMEPEventListener(IMEPEventListener listener) {
		mepEventListeners.remove(listener);
	}

	@Override
	public void removeAllMEPEventListeners() {
		mepEventListeners.clear();
	}
	
	private void notifyListeners(Stopped event) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				for (IMEPEventListener eventListener : mepEventListeners) {
					eventListener.stopReceived(event);
				}
			}
		}).start();
	}
	
	private void notifyListeners(Output event) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				for (IMEPEventListener eventListener : mepEventListeners) {
					eventListener.outputReceived(event);
				}
			}
		}).start();
	}
	
}
