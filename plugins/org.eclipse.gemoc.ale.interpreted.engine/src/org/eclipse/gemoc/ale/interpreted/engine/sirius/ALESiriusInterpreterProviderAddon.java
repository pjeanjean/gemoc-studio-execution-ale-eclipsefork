/*******************************************************************************
 * Copyright (c) 2020 Inria and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Inria - initial API and implementation
 *******************************************************************************/

package org.eclipse.gemoc.ale.interpreted.engine.sirius;

import org.eclipse.gemoc.ale.interpreted.engine.AleEngine;
import org.eclipse.gemoc.xdsmlframework.api.core.IExecutionEngine;
import org.eclipse.gemoc.xdsmlframework.api.engine_addon.IEngineAddon;

/**
 * ALESiriusInterpreterProviderAddon is in charge of releasing resources hold by
 * the ALESiriusInterpreter singleton when engine is disposed
 */
public class ALESiriusInterpreterProviderAddon implements IEngineAddon {

	@Override
	public void engineAboutToDispose(IExecutionEngine<?> engine) {
		IEngineAddon.super.engineAboutToDispose(engine);
		if (engine instanceof AleEngine) {
			ALESiriusInterpreter.getDefault().removeAleEngine((AleEngine) engine);
		}
	}

}
