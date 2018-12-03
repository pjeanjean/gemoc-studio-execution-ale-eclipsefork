package org.eclipse.gemoc.ale.interpreted.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecoretools.ale.ide.WorkbenchDsl;
import org.eclipse.gemoc.dsl.Entry;

public class Helper {

	public static org.eclipse.emf.ecoretools.ale.core.parser.Dsl gemocDslToAleDsl(org.eclipse.gemoc.dsl.Dsl language) {
		
		List<String> ecoreUris = getEcoreUris(language);
		List<String> aleUris = getAleUris(language);
			
		List<String> ecoreFileUris = ecoreUris
	         .stream()
	         .map(elem -> URI.createFileURI(WorkbenchDsl.convertToFile(elem)).toString())
	         .collect(Collectors.toList());
		
		return new WorkbenchDsl(ecoreFileUris,aleUris);
	}
	
	public static List<String> getEcoreUris(org.eclipse.gemoc.dsl.Dsl language) {
		List<String> ecoreUris = new ArrayList<>();
		
		Optional<Entry> ecoreEntry = 
				language
				.getEntries()
				.stream()
				.filter(entry -> entry.getKey().equals("ecore"))
				.findFirst();
		
		if(ecoreEntry.isPresent()) {
			String[] uris = ecoreEntry.get().getValue().split(",");
			for (String uri : uris) {
				ecoreUris.add(uri.trim());
			}
		}
		return ecoreUris;
	}
	
	public static List<String> getAleUris(org.eclipse.gemoc.dsl.Dsl language) {
		List<String> aleUris = new ArrayList<>();
		
		Optional<Entry> aleEntry = 
				language
				.getEntries()
				.stream()
				.filter(entry -> entry.getKey().equals("ale"))
				.findFirst();
		
		if(aleEntry.isPresent()) {
			String[] uris = aleEntry.get().getValue().split(",");
			for (String uri : uris) {
				aleUris.add(uri.trim());
			}
		}
		return aleUris;
	}
	
}
