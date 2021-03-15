package org.eclipse.gemoc.ale.interpreted.engine.mep;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.gemoc.executionframework.engine.commons.sequential.ISequentialModelExecutionContext;
import org.eclipse.gemoc.executionframework.engine.commons.sequential.ISequentialRunConfiguration;
import org.eclipse.gemoc.executionframework.engine.headless.HeadlessExecutionPlatform;
import org.eclipse.gemoc.trace.commons.model.trace.MSEModel;
import org.eclipse.gemoc.xdsmlframework.api.core.ExecutionMode;
import org.eclipse.gemoc.xdsmlframework.api.core.IExecutionWorkspace;
import org.eclipse.gemoc.xdsmlframework.api.extensions.languages.LanguageDefinitionExtension;
import org.eclipse.xtext.EcoreUtil2;
import org.emfjson.jackson.databind.EMFContext;
import org.emfjson.jackson.module.EMFModule;
import org.osgi.framework.Bundle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SerializableSequentialExecutionContext implements ISequentialModelExecutionContext<HeadlessExecutionPlatform>, Serializable {

	private String resourceURI;
	private String jsonModel;
	
	private Resource resourceModel = null;
	private Set<Resource> relatedResources = null;
	
	public SerializableSequentialExecutionContext(Resource resourceModel) {
		EcoreUtil2.resolveAll(resourceModel);
		ObjectMapper mapper = EMFModule.setupDefaultMapper();
		try {
			this.jsonModel = mapper.writeValueAsString(resourceModel.getContents().get(0));
			this.resourceURI = resourceModel.getURI().toFileString();
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void initializeResourceModel() {

	}

	@Override
	public IExecutionWorkspace getWorkspace() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Resource getResourceModel() {
		if (this.resourceModel == null) {
			this.resourceModel = new ResourceSetImpl().createResource(URI.createFileURI(this.resourceURI));
			ObjectMapper mapper = EMFModule.setupDefaultMapper();
			try {
				this.resourceModel.getContents().add(mapper.reader()
							.withAttribute(EMFContext.Attributes.RESOURCE_SET, this.resourceModel.getResourceSet())
							.withAttribute(EMFContext.Attributes.RESOURCE_URI, this.resourceURI)
							.forType(EObject.class).readValue(this.jsonModel));
			} catch (JsonMappingException e) {
				e.printStackTrace();
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
		}
		return this.resourceModel;
	}

	public Set<Resource> getRelatedResources() {
		if (this.relatedResources == null) {
			this.relatedResources = new HashSet<>();
			this.relatedResources.add(this.resourceModel);
		}
		return this.relatedResources;
	}
	
	@Override
	public ExecutionMode getExecutionMode() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MSEModel getMSEModel() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Bundle getDslBundle() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public LanguageDefinitionExtension getLanguageDefinitionExtension() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public HeadlessExecutionPlatform getExecutionPlatform() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ISequentialRunConfiguration getRunConfiguration() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void dispose() {
		// TODO Auto-generated method stub
		
	}

}
