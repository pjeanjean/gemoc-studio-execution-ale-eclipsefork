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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.acceleo.query.runtime.EvaluationResult;
import org.eclipse.acceleo.query.runtime.IQueryBuilderEngine;
import org.eclipse.acceleo.query.runtime.IQueryBuilderEngine.AstResult;
import org.eclipse.acceleo.query.runtime.IQueryEnvironment;
import org.eclipse.acceleo.query.runtime.IQueryEvaluationEngine;
import org.eclipse.acceleo.query.runtime.QueryEvaluation;
import org.eclipse.acceleo.query.runtime.QueryParsing;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecoretools.ale.core.env.IAleEnvironment;
import org.eclipse.emf.ecoretools.ale.core.interpreter.IAleInterpreter;
import org.eclipse.emf.ecoretools.ale.core.interpreter.impl.AleInterpreter;
import org.eclipse.emf.ecoretools.ale.core.interpreter.impl.OptimizedEvaluationResult;
import org.eclipse.emf.ecoretools.ale.core.interpreter.notapi.EvalEnvironment;
import org.eclipse.gemoc.ale.interpreted.engine.AleEngine;
import org.eclipse.sirius.common.acceleo.aql.business.internal.AQLSiriusInterpreter;
import org.eclipse.sirius.common.tools.api.interpreter.EvaluationException;
import org.eclipse.sirius.common.tools.api.interpreter.IEvaluationResult;
import org.eclipse.xtext.EcoreUtil2;

public class ALESiriusInterpreter extends AQLSiriusInterpreter {

	/**
	 * list of known engines that can be used to evalutate the expressions
	 */
	HashSet<AleEngine> knownEngines = new HashSet<>();

	// ALESiriusInterpreter is a singleton
	private static ALESiriusInterpreter instance = new ALESiriusInterpreter();

	private ALESiriusInterpreter() {
	}

	public static ALESiriusInterpreter getDefault() {
		return instance;
	}

	@Override
	public boolean provides(String expression) {
		return expression != null && expression.startsWith("ale:");
	}

	@Override
	public IEvaluationResult evaluateExpression(final EObject target, final String fullExpression)
			throws EvaluationException {

		// select AleInterpreter or create a new one for edition mode (makes sure to
		// dispose it when not used anymore)
		Optional<AleInterpreter> aleInterpreter = findInterpreterForModel(target);
		if (aleInterpreter.isPresent() && aleInterpreter.get().getQueryEnvironment() != null) {
			return evaluateExpressionWithInterpreter(target, fullExpression, aleInterpreter.get());
		} else {
			
			if(editionAleInterpreterCache != null && editionAleInterpreterCache.resSet != target.eResource().getResourceSet()) {
				// new model, dispose previous one
				editionAleInterpreterCache.env.close();
				editionAleInterpreterCache.aleInterpreter.close();
				editionAleInterpreterCache = null;
			}
			if(editionAleInterpreterCache ==  null) {
				List<EPackage> metamodels = getMetamodels(target);
				List<String> metmodelsPathes = metamodels.stream()
						//.map(ePack -> new Path(ePack.eResource().getURI().toPlatformString(true)).toString())
						.map(ePack -> ePack.eResource().getURI().toString())
						.collect(Collectors.toList());
				IAleEnvironment env = IAleEnvironment.fromPaths(metmodelsPathes, new ArrayList<String>());
				IAleInterpreter interpreter = env.getInterpreter();
				new EvalEnvironment(env, null, null);
				editionAleInterpreterCache =  new EditionAleInterpreter(target.eResource().getResourceSet(), env, interpreter);
			}
			IEvaluationResult res = evaluateExpressionWithInterpreter(target, fullExpression, editionAleInterpreterCache.aleInterpreter);
			return res;
		}

	}
	
	EditionAleInterpreter editionAleInterpreterCache;
	
	private class EditionAleInterpreter {
		public ResourceSet resSet;
		IAleEnvironment env;
		public IAleInterpreter aleInterpreter;
		public EditionAleInterpreter(ResourceSet resSet, IAleEnvironment env, IAleInterpreter aleInterpreter) {
			this.resSet = resSet;
			this.env = env;
			this.aleInterpreter = aleInterpreter;
			
		}
	}

	public IEvaluationResult evaluateExpressionWithInterpreter(final EObject target, final String fullExpression,
			IAleInterpreter aleInterpreter) throws EvaluationException {
		this.javaExtensions.reloadIfNeeded();
		String expression = fullExpression.replaceFirst("ale:", "");
		Map<String, Object> variables = getVariables();
		variables.put("self", target); //$NON-NLS-1$

		IQueryEnvironment queryEnv = ((AleInterpreter) aleInterpreter).getQueryEnvironment();
		final IQueryBuilderEngine builder = QueryParsing.newBuilder(queryEnv);
		AstResult build = builder.build(expression);
		IQueryEvaluationEngine evaluationEngine = QueryEvaluation.newEngine(queryEnv);
		final EvaluationResult evalResult = evaluationEngine.eval(build, variables);

		final BasicDiagnostic diagnostic = new BasicDiagnostic();
		if (Diagnostic.OK != build.getDiagnostic().getSeverity()) {
			diagnostic.merge(build.getDiagnostic());
		}
		if (Diagnostic.OK != evalResult.getDiagnostic().getSeverity()) {
			diagnostic.merge(evalResult.getDiagnostic());
		}
		return new OptimizedEvaluationResult(Optional.ofNullable(evalResult.getResult()), diagnostic);
	}



	/**
	 * Look in known engines that are running the model containing the given target
	 * EObject
	 * 
	 * @param target
	 * @return the AleInterpreter of the found AleEngine
	 */
	protected Optional<AleInterpreter> findInterpreterForModel(final EObject target) {
		for (AleEngine engine : knownEngines) {
			if (engine.getExecutionContext().getResourceModel().getResourceSet() == target.eResource()
					.getResourceSet()) {
				return Optional.ofNullable(engine.getInterpreter());
			}
		}
		return Optional.empty();
	}

	/**
	 * reflectively find the metamodels of the given EObject
	 * 
	 * Ie. look for all EPackage of all Resources in the ResourceSet
	 * 
	 * @param target
	 * @return a list of EPackage
	 */
	protected List<EPackage> getMetamodels(final EObject target) {
		ArrayList<EPackage> metamodels = new ArrayList<EPackage>();
		for (Resource res : target.eResource().getResourceSet().getResources()) {
			// TODO find a more efficient request ? (ie. avoid to navigate the whole model )
			// could we make the assumption that EPackage are only at the root or contained
			// by EPackage ?
			// (DVK: I know that UML may use EPackage in order to create profiles
			// instantiation)
			metamodels.addAll(EcoreUtil2.typeSelect(EcoreUtil2.eAllContentsAsList(res), EPackage.class));
		}
		return metamodels;
	}

	public void addAleEngine(AleEngine engine) {
		knownEngines.add(engine);
	}

	public void removeAleEngine(AleEngine engine) {
		knownEngines.remove(engine);
	}
}
