package org.eclipse.gemoc.ale.interpreted.engine.ui;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecoretools.ale.core.env.IAleEnvironment;
import org.eclipse.emf.ecoretools.ale.core.parser.ParsedFile;
import org.eclipse.emf.ecoretools.ale.implementation.Method;
import org.eclipse.emf.ecoretools.ale.implementation.ModelUnit;
import org.eclipse.gemoc.ale.interpreted.engine.Helper;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

public class SelectMainMethodDialog extends ElementListSelectionDialog {

	org.eclipse.gemoc.dsl.Dsl language;
	
	/**
	 * Create a selection dialog displaying all available methods with @main
	 * from elements in 'aspects' weaving 'modelElem'.
	 * If 'modelElem' is null, selection dialog displays all @main.
	 */
	public SelectMainMethodDialog(org.eclipse.gemoc.dsl.Dsl language, EObject modelElem, ILabelProvider renderer) {
		super(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), renderer);
		
		this.language = language;
		
		if(language != null)
			update(modelElem);
	}
	
	/**
	 * Display only methods with @main from Aspects applied on modelElem
	 */
	public void update(EObject modelElem){
		
		EClass target = null;
		if(modelElem != null){
			target = modelElem.eClass();
		}
		final EClass finalTarget = target;
		
		IAleEnvironment environment = Helper.gemocDslToAleDsl(language);
		
		List<ParsedFile<ModelUnit>> parsedSemantics = environment.getBehaviors().getParsedFiles();
		List<Method> mainOperations =
    		parsedSemantics
	    	.stream()
	    	.filter(sem -> sem.getRoot() != null)
	    	.map(sem -> sem.getRoot())
	    	.flatMap(unit -> unit.getClassExtensions().stream())
	    	.filter(xtdCls -> finalTarget == null || finalTarget == xtdCls.getBaseClass())
	    	.flatMap(xtdCls -> xtdCls.getMethods().stream())
    		.filter(op -> op.getTags().contains("main"))
    		.collect(Collectors.toList());
		
		this.setElements(mainOperations.toArray());
	}
}
