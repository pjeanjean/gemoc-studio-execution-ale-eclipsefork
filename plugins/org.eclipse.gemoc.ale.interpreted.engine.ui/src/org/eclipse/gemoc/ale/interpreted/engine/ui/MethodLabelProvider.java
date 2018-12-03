package org.eclipse.gemoc.ale.interpreted.engine.ui;

import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecoretools.ale.implementation.BehavioredClass;
import org.eclipse.emf.ecoretools.ale.implementation.ExtendedClass;
import org.eclipse.emf.ecoretools.ale.implementation.Method;
import org.eclipse.gemoc.executionframework.ui.utils.ENamedElementQualifiedNameLabelProvider;

public class MethodLabelProvider extends ENamedElementQualifiedNameLabelProvider {
	
	@Override
	public String getText(Object element) {
		
		if(element instanceof Method) {
			Method mtd = (Method) element;
			ExtendedClass base = (ExtendedClass) mtd.eContainer();
			
			if(base.getBaseClass() != mtd.getOperationRef().getEContainingClass()) {
				return super.getText(base.getBaseClass()) + "::" + mtd.getOperationRef().getName();
			}
			else {
				return super.getText(mtd.getOperationRef());
			}
		}
		
		return super.getText(element);
	}
}
