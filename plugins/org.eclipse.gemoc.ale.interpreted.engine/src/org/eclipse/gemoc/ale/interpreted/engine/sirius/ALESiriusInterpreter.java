package org.eclipse.gemoc.ale.interpreted.engine.sirius;

import java.util.Map;
import java.util.Optional;

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
import org.eclipse.emf.ecoretools.ale.core.interpreter.impl.AleInterpreter;
import org.eclipse.emf.ecoretools.ale.core.interpreter.impl.OptimizedEvaluationResult;
import org.eclipse.gemoc.ale.interpreted.engine.AleEngine;
import org.eclipse.sirius.common.acceleo.aql.business.internal.AQLSiriusInterpreter;
import org.eclipse.sirius.common.tools.api.interpreter.EvaluationException;
import org.eclipse.sirius.common.tools.api.interpreter.IEvaluationResult;

public class ALESiriusInterpreter extends AQLSiriusInterpreter {

	AleEngine engine;

	public ALESiriusInterpreter(AleEngine engine) {
		this.engine = engine;
	}

	@Override
	public boolean provides(String expression) {
		return expression != null && expression.startsWith("ale:");
	}

	@Override
	public IEvaluationResult evaluateExpression(final EObject target, final String fullExpression)
			throws EvaluationException {
		this.javaExtensions.reloadIfNeeded();
		String expression = fullExpression.replaceFirst("ale:", "");
		Map<String, Object> variables = getVariables();
		variables.put("self", target); //$NON-NLS-1$

		AleInterpreter aleInterpreter = engine.getInterpreter();
		if (aleInterpreter != null) {
			IQueryEnvironment queryEnv = aleInterpreter.getQueryEnvironment();
			if (queryEnv != null) {
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
				return new OptimizedEvaluationResult(
						Optional.ofNullable(evalResult.getResult()),
						diagnostic);
			}
		}

		return org.eclipse.sirius.common.tools.api.interpreter.EvaluationResult.ofValue(null);
	}
}
