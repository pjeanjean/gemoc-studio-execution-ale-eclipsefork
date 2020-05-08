package org.eclipse.gemoc.ale.language.metaprogramming;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.gemoc.xdsmlframework.api.extensions.metaprog.IRule;
import org.eclipse.gemoc.xdsmlframework.api.extensions.metaprog.IRuleProvider;
import org.eclipse.gemoc.xdsmlframework.api.extensions.metaprog.EcoreRule;
/**
 * RuleProvider used for the Ale meta-programming approach.
 * Uses the Ecore RuleProvider
 * 
 * @author GUEGUEN Ronan
 *
 */
public class AleRuleProvider implements IRuleProvider {
	
	private ArrayList<IRule> ruleSet = new ArrayList<>();
	
	/**
	 * Creates a RuleProvider for the Ale meta-programming approach, contains rules from the Ecore RuleProvider
	 */
	public AleRuleProvider() {
		ruleSet.add(new EcoreRule());
		ruleSet.add(new AleRule());
		
	}

	@Override
	public Collection<IRule> getValidationRules() {
		return ruleSet;
	}

}