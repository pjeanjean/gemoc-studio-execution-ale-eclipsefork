/*******************************************************************************
 * Copyright (c) 2018, 2020 Inria and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *     Inria - refactoring and improvements
 *******************************************************************************/
package org.eclipse.gemoc.ale.interpreted.engine.sirius;

import org.eclipse.sirius.common.tools.api.interpreter.IInterpreter;
import org.eclipse.sirius.common.tools.api.interpreter.IInterpreterProvider;

/**
 * Class registered by the plugin.xml in order to provide new EMF
 * ExpressionInterpreter to Sirius (see
 * org.eclipse.sirius.common.expressionInterpreter) (this makes expressions such
 * as "ale:" available in the odesign fields)
 */
public class ALESiriusInterpreterProvider implements IInterpreterProvider {

	public ALESiriusInterpreterProvider() {
	}

	/**
	 * retrieves the singleton interpreter, this singleton will be in charge of
	 * finding the appropriate AleInterpreter from known ALEEngines
	 */
	@Override
	public IInterpreter createInterpreter() {
		return ALESiriusInterpreter.getDefault();
	}

	@Override
	public boolean provides(String expression) {
		if (expression != null) {
			return expression.startsWith("ale:");
		}
		return false;
	}

}
