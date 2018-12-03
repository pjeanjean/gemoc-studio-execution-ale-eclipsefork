package org.eclipse.gemoc.ale.interpreted.engine.sirius;

import java.util.List;
import java.util.Map;

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
import org.eclipse.emf.ecoretools.ale.ALEInterpreter;
import org.eclipse.gemoc.ale.interpreted.engine.AleEngine;
import org.eclipse.sirius.common.acceleo.aql.business.internal.AQLSiriusInterpreter;
import org.eclipse.sirius.common.tools.api.interpreter.EvaluationException;

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

		ALEInterpreter aleInterpreter = engine.getInterpreter();
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

				return new IEvaluationResult() {

					@Override
					public Object getValue() {
						return evalResult.getResult();
					}

					@Override
					public Diagnostic getDiagnostic() {
						List<Diagnostic> children = diagnostic.getChildren();
						if (children.size() == 1) {
							return children.get(0);
						} else {
							return diagnostic;
						}
					}
				};
			}
		}

		return new IEvaluationResult() {
			@Override
			public Object getValue() {
				return null;
			}

			@Override
			public Diagnostic getDiagnostic() {
				return new BasicDiagnostic();
			}

		};
	}
}
