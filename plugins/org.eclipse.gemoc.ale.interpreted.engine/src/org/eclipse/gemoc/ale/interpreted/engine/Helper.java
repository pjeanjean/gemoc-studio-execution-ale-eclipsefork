package org.eclipse.gemoc.ale.interpreted.engine;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecoretools.ale.core.env.IAleEnvironment;
import org.eclipse.emf.ecoretools.ale.core.env.impl.PathsBasedAleEnvironment;
import org.eclipse.emf.ecoretools.ale.ide.env.WithAbsoluteBehaviorPathsAleEnvironment;
import org.eclipse.emf.ecoretools.ale.core.parser.IOUtils;
import org.eclipse.gemoc.dsl.Entry;

public class Helper {

	public static IAleEnvironment gemocDslToAleDsl(org.eclipse.gemoc.dsl.Dsl language) {

		List<String> ecoreUris = getEcoreUris(language);
		List<String> aleUris = getAleUris(language);

		List<String> ecoreFileUris = ecoreUris.stream()
				.map(elem -> URI.createFileURI(IOUtils.convertToFile(elem)).toString()).collect(Collectors.toList());

		WithAbsoluteBehaviorPathsAleEnvironment res = new WithAbsoluteBehaviorPathsAleEnvironment(new PathsBasedAleEnvironment(new ArrayList<String>(), new ArrayList<String>()));
		try {
			res = new WithAbsoluteBehaviorPathsAleEnvironment(new PathsBasedAleEnvironment(ecoreFileUris, aleUris));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return res;
	}

	/**
	 * Check language's Ecore & ALE URIs
	 */
	public static List<String> validate(org.eclipse.gemoc.dsl.Dsl language) {

		List<String> errors = new ArrayList<>();
		
		List<String> ecoreUris = getEcoreUris(language);
		List<String> aleUris = getAleUris(language);
		
		for(String uri : ecoreUris) {
			if(!checkExistURI(uri)) {
				errors.add("Can't find: " + uri + " (declared in the language '" + language.getName() + "'");
			}
		}
		
		for(String uri : aleUris) {
			if(!checkExistURI(uri)) {
				errors.add("Can't find: " + uri + " (declared in the language '" + language.getName() + "'");
			}
		}
		
		return errors;
	}
	
	public static boolean checkExistURI(String uriString) {
		URI uri = URI.createURI(uriString);
		IWorkspace ws = ResourcesPlugin.getWorkspace();
		if(ws != null) {
			IResource file = ws.getRoot().findMember(uri.toPlatformString(true));
			if(file != null) {
				return true;
			}
		}
		boolean isPresent = false;
		try {
			isPresent = Files.exists(
					Paths.get(java.net.URI.create(URI.createFileURI(IOUtils.convertToFile(uriString)).toString())));
		}
		catch(Exception e) {}
		return isPresent;
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

		Optional<Entry> aleEntry = language.getEntries().stream().filter(entry -> entry.getKey().equals("ale"))
				.findFirst();

		if (aleEntry.isPresent()) {
			String[] uris = aleEntry.get().getValue().split(",");
			for (String uri : uris) {
				aleUris.add(uri.trim());
			}
		}
		return aleUris;
	}

}
