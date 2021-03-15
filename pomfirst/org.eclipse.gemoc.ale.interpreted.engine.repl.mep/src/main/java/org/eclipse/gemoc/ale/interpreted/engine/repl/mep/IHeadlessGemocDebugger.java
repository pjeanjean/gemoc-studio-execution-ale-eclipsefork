package org.eclipse.gemoc.ale.interpreted.engine.repl.mep;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.gemoc.dsl.debug.ide.event.IDSLDebugEvent;
import org.eclipse.gemoc.executionframework.debugger.IGemocDebugger;

public interface IHeadlessGemocDebugger extends IGemocDebugger {

	String getThreadName();
	
	EObject getCurrentInstruction();

	void stepReturn(String threadName);

	Object handleEvent(IDSLDebugEvent event);

	void stepOver(String threadName);

	void stepInto(String threadName);

	boolean isTerminated();

	void terminate();

	void resume();
	
}
