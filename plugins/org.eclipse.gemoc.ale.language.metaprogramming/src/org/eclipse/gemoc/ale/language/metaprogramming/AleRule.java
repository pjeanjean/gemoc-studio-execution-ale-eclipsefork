package org.eclipse.gemoc.ale.language.metaprogramming;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecoretools.ale.Operation;
import org.eclipse.emf.ecoretools.ale.Tag;
import org.eclipse.emf.ecoretools.ale.Unit;
import org.eclipse.gemoc.dsl.Entry;
import org.eclipse.gemoc.xdsmlframework.api.extensions.metaprog.ILanguageComponentValidator;
import org.eclipse.gemoc.xdsmlframework.api.extensions.metaprog.Message;
import org.eclipse.gemoc.xdsmlframework.api.extensions.metaprog.Severity;

/**
 * Validation rule used by the Ale meta-programming approach
 * 
 * @author GUEGUEN Ronan
 *
 */
public class AleRule implements ILanguageComponentValidator{


	@Override
	public Message validate(Entry entry) {
		if("ale".matches(entry.getKey())) {
			URI uri = URI.createURI(entry.getValue());
			
			if(!uri.isPlatformResource()) {
				return (new Message("File for \"ale\" entry not in the workspace", Severity.ERROR));
			}
						
			ResourceSet rs = new ResourceSetImpl();
			Resource res;
			
			try {
				
				res = rs.getResource(uri, true);
			
				List<EObject> contents = res.getContents().get(0).eContents();				
				
				if(contents.isEmpty()) {
					return (new Message("No classes in ale file", Severity.ERROR));
				}	
				
				TreeIterator<EObject> tree = res.getAllContents();
				List<Tag> tags = new ArrayList<Tag>();
				while(tree.hasNext()) {
					EObject node = tree.next();
					if(node instanceof Unit) {
						// TODO: If I have the time, i should perform a check on the behaviour name
						// Unit nodeUnit = (Unit) node;
					}
					if(node instanceof Operation) {
						Operation nodeOperation = (Operation) node;
						if(!nodeOperation.getTag().isEmpty()) {
							tags.addAll(nodeOperation.getTag());
						}
					}
				}
				ArrayList<String> tagNames = new ArrayList<>();
				
				for(Tag tag : tags) {
					tagNames.add(tag.getName());
				}
				
				if(!tagNames.contains("init")) {
					return (new Message("The Ale file does not contain an \"@init\" operation", Severity.WARNING));
				}
				
				if(!tagNames.contains("main")) {
					return (new Message("The Ale file does not contain an \"@main\" operation", Severity.ERROR));
				}			
			
				
			}catch (RuntimeException e) {
				return (new Message("The file for the \"ale\" entry does not exist", Severity.ERROR));
			}
		}
		return (new Message("", Severity.DEFAULT));
	}

}
