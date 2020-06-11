/*******************************************************************************
 * Copyright (c) 2018, 2020 Inria and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.gemoc.ale.interpreted.engine.ui.launcher.tabs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecoretools.ale.ALEInterpreter;
import org.eclipse.emf.ecoretools.ale.core.parser.Dsl;
import org.eclipse.emf.ecoretools.ale.core.parser.DslBuilder;
import org.eclipse.emf.ecoretools.ale.core.parser.visitor.ParseResult;
import org.eclipse.emf.ecoretools.ale.implementation.Method;
import org.eclipse.emf.ecoretools.ale.implementation.ModelUnit;
import org.eclipse.gemoc.ale.interpreted.engine.Helper;
import org.eclipse.gemoc.ale.interpreted.engine.ui.Activator;
import org.eclipse.gemoc.ale.interpreted.engine.ui.MethodLabelProvider;
import org.eclipse.gemoc.ale.interpreted.engine.ui.SelectMainMethodDialog;
import org.eclipse.gemoc.commons.eclipse.emf.URIHelper;
import org.eclipse.gemoc.commons.eclipse.ui.dialogs.SelectAnyIFileDialog;
import org.eclipse.gemoc.dsl.debug.ide.launch.AbstractDSLLaunchConfigurationDelegate;
import org.eclipse.gemoc.dsl.debug.ide.sirius.ui.launch.AbstractDSLLaunchConfigurationDelegateSiriusUI;
import org.eclipse.gemoc.executionframework.engine.commons.DslHelper;
import org.eclipse.gemoc.executionframework.engine.commons.sequential.SequentialRunConfiguration;
import org.eclipse.gemoc.executionframework.engine.ui.launcher.tabs.ILaunchLanguageSelectionListener;
import org.eclipse.gemoc.executionframework.ui.utils.ENamedElementQualifiedNameLabelProvider;
import org.eclipse.gemoc.xdsmlframework.api.extensions.languages.LanguageDefinitionExtensionPoint;
import org.eclipse.gemoc.xdsmlframework.ui.utils.dialogs.SelectAIRDIFileDialog;
import org.eclipse.gemoc.xdsmlframework.ui.utils.dialogs.SelectAnyEObjectDialog;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.xtext.naming.DefaultDeclarativeQualifiedNameProvider;
import org.eclipse.xtext.naming.QualifiedName;

import com.google.common.collect.Lists;

public class LaunchConfigurationMainTab extends AbstractLaunchConfigurationTab {

	protected Composite parent;

	protected IProject _modelProject;
	protected Text _modelLocationText;
	protected Text _modelInitializationMethodText;
	protected Text _modelInitializationArgumentsText;

	protected Combo _languageCombo;

	protected Text _siriusRepresentationLocationText;
	protected Text _delayText;
	protected Button _animationFirstBreak;

	protected Group _execArea;
	protected Text _entryPointModelElementText;
	protected Label _entryPointModelElementLabel;
	protected Text _entryPointMethodText;

	// list of other tabs that listen to the language selection (in order to refresh
	// their UI)
	protected ArrayList<ILaunchLanguageSelectionListener> _languageSelectionListeners = new ArrayList<ILaunchLanguageSelectionListener>();

	@Override
	public void createControl(Composite parent) {
		this.parent = parent;
		Composite area = new Composite(parent, SWT.NULL);
		GridLayout gl = new GridLayout(1, false);
		gl.marginHeight = 0;
		area.setLayout(gl);
		area.layout();
		setControl(area);

		Group modelArea = createGroup(area, "Model:");
		createModelLayout(modelArea, null);

		Group languageArea = createGroup(area, "Language:");
		createLanguageLayout(languageArea, null);

		Group debugArea = createGroup(area, "Animation:");
		createAnimationLayout(debugArea, null);

		_execArea = createGroup(area, "Sequential DSA execution:");
		createExecLayout(_execArea, null);
	}

	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
		configuration.setAttribute(SequentialRunConfiguration.LAUNCH_MODEL_ENTRY_POINT, "/");
		configuration.setAttribute(SequentialRunConfiguration.LAUNCH_DELAY, 1000);
		configuration.setAttribute(SequentialRunConfiguration.LAUNCH_MODEL_ENTRY_POINT, "");
		configuration.setAttribute(SequentialRunConfiguration.LAUNCH_SELECTED_LANGUAGE, "");
	}

	@Override
	public void initializeFrom(ILaunchConfiguration configuration) {
		try {
			SequentialRunConfiguration runConfiguration = new SequentialRunConfiguration(configuration);
			_modelLocationText.setText(URIHelper.removePlatformScheme(runConfiguration.getExecutedModelURI()));

			if (runConfiguration.getAnimatorURI() != null)
				_siriusRepresentationLocationText
						.setText(URIHelper.removePlatformScheme(runConfiguration.getAnimatorURI()));
			else
				_siriusRepresentationLocationText.setText("");

			_delayText.setText(Integer.toString(runConfiguration.getAnimationDelay()));
			_animationFirstBreak.setSelection(runConfiguration.getBreakStart());

			_entryPointModelElementText.setText(runConfiguration.getModelEntryPoint());
			_languageCombo.setText(runConfiguration.getLanguageName());
			_modelInitializationMethodText.setText(runConfiguration.getModelInitializationMethod());
			_modelInitializationArgumentsText.setText(runConfiguration.getModelInitializationArguments());
			_entryPointModelElementLabel.setText("");
			_entryPointMethodText.setText(runConfiguration.getExecutionEntryPoint());
			updateMainElementName();

			org.eclipse.gemoc.dsl.Dsl language = DslHelper.load(_languageCombo.getText());
			if (language != null) {
				List<String> errors = Helper.validate(language);
				for (String error : errors) {
					setErrorMessage(error);
				}
			} else {
				setErrorMessage("Can't find the language: '" + _languageCombo.getText() + "'");
			}

		} catch (CoreException e) {
			Activator.error(e.getMessage(), e);
		}
	}

	@Override
	public boolean isValid(ILaunchConfiguration launchConfig) {
		boolean isValid = super.isValid(launchConfig);
		setErrorMessage(null);
		setMessage(null);
		org.eclipse.gemoc.dsl.Dsl language = DslHelper.load(_languageCombo.getText());
		if (language != null) {
			List<String> errors = Helper.validate(language);
			for (String error : errors) {
				isValid =  false;
				setErrorMessage(error);
			}
		}
		try {
			Resource model = getModel();
			if (model == null) {
				setErrorMessage("Please select a model to execute.");
			} else if (_languageCombo.getText() == null || _languageCombo.getText().isEmpty()) {
					setErrorMessage("Please select a language.");
			} else if (_entryPointMethodText.getText() == null || _entryPointMethodText.getText().equals("")) {
				setErrorMessage("Please select a main method.");
			} else if (_entryPointModelElementText.getText() == null || _entryPointModelElementText.getText().isEmpty()) {
				setErrorMessage("Please select the main model element.");
			}
		} catch (Exception e) {
			setErrorMessage("Please select a model to execute.");
		}
		return isValid;
		
	}

	@Override
	public void performApply(ILaunchConfigurationWorkingCopy configuration) {
		configuration.setAttribute(AbstractDSLLaunchConfigurationDelegate.RESOURCE_URI,
				this._modelLocationText.getText());
		configuration.setAttribute(AbstractDSLLaunchConfigurationDelegateSiriusUI.SIRIUS_RESOURCE_URI,
				this._siriusRepresentationLocationText.getText());
		configuration.setAttribute(SequentialRunConfiguration.LAUNCH_DELAY, Integer.parseInt(_delayText.getText()));
		configuration.setAttribute(SequentialRunConfiguration.LAUNCH_SELECTED_LANGUAGE, _languageCombo.getText());
		configuration.setAttribute(SequentialRunConfiguration.LAUNCH_MODEL_ENTRY_POINT, _entryPointModelElementText.getText());
		configuration.setAttribute(SequentialRunConfiguration.LAUNCH_METHOD_ENTRY_POINT, _entryPointMethodText.getText());
		configuration.setAttribute(SequentialRunConfiguration.LAUNCH_INITIALIZATION_METHOD,
				_modelInitializationMethodText.getText());
		configuration.setAttribute(SequentialRunConfiguration.LAUNCH_INITIALIZATION_ARGUMENTS,
				_modelInitializationArgumentsText.getText());
		configuration.setAttribute(SequentialRunConfiguration.LAUNCH_BREAK_START, _animationFirstBreak.getSelection());
		// DebugModelID for sequential engine
		configuration.setAttribute(SequentialRunConfiguration.DEBUG_MODEL_ID, Activator.DEBUG_MODEL_ID);
	}

	@Override
	public String getName() {
		return "Main";
	}

	protected Group createGroup(Composite parent, String text) {
		Group group = new Group(parent, SWT.NULL);
		group.setText(text);
		GridLayout locationLayout = new GridLayout();
		locationLayout.numColumns = 3;
		locationLayout.marginHeight = 10;
		locationLayout.marginWidth = 10;
		group.setLayout(locationLayout);
		return group;
	}

	protected void createTextLabelLayout(Composite parent, String labelString) {
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		parent.setLayoutData(gd);
		Label inputLabel = new Label(parent, SWT.NONE);
		inputLabel.setText(labelString); // $NON-NLS-1$
	}

	private GridData createStandardLayout() {
		return new GridData(SWT.FILL, SWT.CENTER, true, false);
	}

	public Composite createModelLayout(Composite parent, Font font) {
		createTextLabelLayout(parent, "Model to execute");
		// Model location text
		_modelLocationText = new Text(parent, SWT.SINGLE | SWT.BORDER);
		_modelLocationText.setLayoutData(createStandardLayout());
		_modelLocationText.setFont(font);
		_modelLocationText.addModifyListener(fBasicModifyListener);
		Button modelLocationButton = createPushButton(parent, "Browse", null);
		modelLocationButton.addSelectionListener(new SelectionAdapter() {

			public void widgetSelected(SelectionEvent evt) {
				// handleModelLocationButtonSelected();
				// TODO launch the appropriate selector

				SelectAnyIFileDialog dialog = new SelectAnyIFileDialog();
				if (dialog.open() == Dialog.OK) {
					String modelPath = ((IResource) dialog.getResult()[0]).getFullPath().toPortableString();
					_modelLocationText.setText(modelPath);
					updateLaunchConfigurationDialog();
					_modelProject = ((IResource) dialog.getResult()[0]).getProject();
				}
			}
		});
		createTextLabelLayout(parent, "Model initialization method");
		_modelInitializationMethodText = new Text(parent, SWT.SINGLE | SWT.BORDER);
		_modelInitializationMethodText.setLayoutData(createStandardLayout());
		_modelInitializationMethodText.setFont(font);
		_modelInitializationMethodText.setEditable(false);
		createTextLabelLayout(parent, "");
		createTextLabelLayout(parent, "Model initialization arguments");
		_modelInitializationArgumentsText = new Text(parent, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
		_modelInitializationArgumentsText.setToolTipText("one argument per line");
		GridData gridData = new GridData(GridData.FILL_BOTH);
		gridData.heightHint = 40;
		_modelInitializationArgumentsText.setLayoutData(gridData);
		_modelInitializationArgumentsText.setLayoutData(createStandardLayout());
		_modelInitializationArgumentsText.setFont(font);
		_modelInitializationArgumentsText.setEditable(true);
		_modelInitializationArgumentsText.addModifyListener(new ModifyListener() {

			@Override
			public void modifyText(ModifyEvent e) {
				updateLaunchConfigurationDialog();
			}
		});
		createTextLabelLayout(parent, "");
		return parent;
	}

	public Composite createLanguageLayout(Composite parent, Font font) {
		// Language
		createTextLabelLayout(parent, "ALE Languages");
		_languageCombo = new Combo(parent, SWT.NONE);
		_languageCombo.setLayoutData(createStandardLayout());

		List<String> languagesNames = getAllLanguages();
		String[] empty = {};
		_languageCombo.setItems(languagesNames.toArray(empty));
		_languageCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				for(ILaunchLanguageSelectionListener listener : _languageSelectionListeners) {
					listener.languageChanged(_languageCombo.getText());
				}
				updateLaunchConfigurationDialog();
			}
		});
		createTextLabelLayout(parent, "");

		return parent;
	}

	private Composite createAnimationLayout(Composite parent, Font font) {
		createTextLabelLayout(parent, "Animator");

		_siriusRepresentationLocationText = new Text(parent, SWT.SINGLE | SWT.BORDER);
		_siriusRepresentationLocationText.setLayoutData(createStandardLayout());
		_siriusRepresentationLocationText.setFont(font);
		_siriusRepresentationLocationText.addModifyListener(fBasicModifyListener);
		Button siriusRepresentationLocationButton = createPushButton(parent, "Browse", null);
		siriusRepresentationLocationButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent evt) {
				// handleModelLocationButtonSelected();
				// TODO launch the appropriate selector

				SelectAIRDIFileDialog dialog = new SelectAIRDIFileDialog();
				if (dialog.open() == Dialog.OK) {
					String modelPath = ((IResource) dialog.getResult()[0]).getFullPath().toPortableString();
					_siriusRepresentationLocationText.setText(modelPath);
					updateLaunchConfigurationDialog();
				}
			}
		});

		createTextLabelLayout(parent, "Delay");
		_delayText = new Text(parent, SWT.SINGLE | SWT.BORDER);
		_delayText.setLayoutData(createStandardLayout());
		_delayText.addModifyListener(new ModifyListener() {

			@Override
			public void modifyText(ModifyEvent e) {
				updateLaunchConfigurationDialog();
			}
		});
		createTextLabelLayout(parent, "(in milliseconds)");

		new Label(parent, SWT.NONE).setText("");
		_animationFirstBreak = new Button(parent, SWT.CHECK);
		_animationFirstBreak.setText("Break at start");
		_animationFirstBreak.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent event) {
				updateLaunchConfigurationDialog();
			}
		}

		);

		return parent;
	}

	private Composite createExecLayout(Composite parent, Font font) {
		createTextLabelLayout(parent, "Main method");
		_entryPointMethodText = new Text(parent, SWT.SINGLE | SWT.BORDER);
		_entryPointMethodText.setLayoutData(createStandardLayout());
		_entryPointMethodText.setFont(font);
		_entryPointMethodText.setEditable(false);
		_entryPointMethodText.addModifyListener(fBasicModifyListener);
		Button mainMethodBrowseButton = createPushButton(parent, "Browse", null);
		mainMethodBrowseButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				if (_languageCombo.getText() == null) {
					setErrorMessage("Please select a language.");
				} else {
					org.eclipse.gemoc.dsl.Dsl language = DslHelper.load(_languageCombo.getText());
					MethodLabelProvider labelProvider = new MethodLabelProvider();
					SelectMainMethodDialog dialog = new SelectMainMethodDialog(language, null, labelProvider);
					int res = dialog.open();
					if (res == WizardDialog.OK) {
						Method selection = (Method) dialog.getFirstResult();
						_entryPointMethodText.setText(labelProvider.getText(selection));
					}

				}
			}
		});

		createTextLabelLayout(parent, "Main model element path");
		_entryPointModelElementText = new Text(parent, SWT.SINGLE | SWT.BORDER);
		_entryPointModelElementText.setLayoutData(createStandardLayout());
		_entryPointModelElementText.setFont(font);
		_entryPointModelElementText.setEditable(false);
		_entryPointModelElementText.addModifyListener(event -> updateMainElementName());
		_entryPointModelElementText.addModifyListener(fBasicModifyListener);
		Button mainModelElemBrowseButton = createPushButton(parent, "Browse", null);
		mainModelElemBrowseButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				Resource model = getModel();
				if (model == null) {
					setErrorMessage("Please select a model to execute.");
				} else if (_entryPointMethodText.getText() == null || _entryPointMethodText.getText().equals("")) {
					setErrorMessage("Please select a main method.");
				} else {
					SelectAnyEObjectDialog dialog = new SelectAnyEObjectDialog(
							PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), model.getResourceSet(),
							new ENamedElementQualifiedNameLabelProvider()) {
						protected boolean select(EObject obj) {
							String methodSignature = _entryPointMethodText.getText();
							List<String> segments = Lists.newArrayList(methodSignature.split("::"));
							if (segments.size() >= 2) {
								String callerTypeName = segments.get(segments.size() - 2);
								if (obj.eClass().getName().equals(callerTypeName)) {
									return true;
								}
							}

							return false;
						}
					};
					int res = dialog.open();
					if (res == WizardDialog.OK) {
						EObject selection = (EObject) dialog.getFirstResult();
						String uriFragment = selection.eResource().getURIFragment(selection);
						_entryPointModelElementText.setText(uriFragment);
					}
				}
			}
		});

		createTextLabelLayout(parent, "Main model element name");
		_entryPointModelElementLabel = new Label(parent, SWT.HORIZONTAL);
		_entryPointModelElementLabel.setText("");

		return parent;
	}

	/**
	 * Basic modify listener that can be reused if there is no more precise need
	 */
	private ModifyListener fBasicModifyListener = new ModifyListener() {
		@Override
		public void modifyText(ModifyEvent arg0) {
			updateLaunchConfigurationDialog();
		}
	};

	/**
	 * caches the current model resource in order to avoid to reload it many times
	 * use {@link getModel()} in order to access it.
	 */
	private Resource currentModelResource;

	private Resource getModel() {
		URI modelURI = URI.createPlatformResourceURI(_modelLocationText.getText(), true);
		if (currentModelResource == null || !currentModelResource.getURI().equals(modelURI)) {
			currentModelResource = loadModel(modelURI);
		}
		return currentModelResource;
	}

	/**
	 * Load the model for the given URI
	 * 
	 * @param modelURI to load
	 * @return the loaded resource
	 */
	public static Resource loadModel(URI modelURI) {
		Resource resource = null;
		ResourceSet resourceSet;
		resourceSet = new ResourceSetImpl();
		resource = resourceSet.createResource(modelURI);
		try {
			resource.load(null);
		} catch (IOException e) {
			// chut
		}
		return resource;
	}

	/**
	 * Update _entryPointModelElement with pretty name
	 */
	private void updateMainElementName() {
		try {
			Resource model = getModel();
			EObject mainElement = null;
			if (model != null) {
				mainElement = model.getEObject(_entryPointModelElementText.getText());
			}
			if (mainElement != null) {
				org.eclipse.xtext.naming.DefaultDeclarativeQualifiedNameProvider nameprovider = new DefaultDeclarativeQualifiedNameProvider();
				QualifiedName qname = nameprovider.getFullyQualifiedName(mainElement);
				String objectName = qname != null ? qname.toString() : mainElement.toString();
				String prettyName = objectName + " : " + mainElement.eClass().getName();
				_entryPointModelElementLabel.setText(prettyName);
			}
		} catch (Exception e) {
		}
	}

	@Override
	protected void updateLaunchConfigurationDialog() {
		super.updateLaunchConfigurationDialog();
		_execArea.setVisible(true);
		_modelInitializationMethodText.setText(getModelInitializationMethodName());
		_modelInitializationArgumentsText.setEnabled(!_modelInitializationMethodText.getText().isEmpty());
	}

	protected String getModelInitializationMethodName() {
		if (_languageCombo.getText() != null && _entryPointMethodText.getText() != null) {

			List<String> segments = Arrays.asList(_entryPointMethodText.getText().split("::"));
			if (segments.size() >= 2) {
				String tagetClassName = segments.get(segments.size() - 2);
				org.eclipse.gemoc.dsl.Dsl language = DslHelper.load(_languageCombo.getText());

				Dsl environment = Helper.gemocDslToAleDsl(language);
				try(ALEInterpreter interpreter = new ALEInterpreter()) {
					Optional<Method> initOperation = Optional.empty();
					try {
						List<ParseResult<ModelUnit>> parsedSemantics = (new DslBuilder(interpreter.getQueryEnvironment()))
								.parse(environment);
						initOperation = parsedSemantics.stream().filter(sem -> sem.getRoot() != null)
								.map(sem -> sem.getRoot()).flatMap(unit -> unit.getClassExtensions().stream())
								.filter(xtdCls -> xtdCls.getBaseClass().getName().equals(tagetClassName))
								.flatMap(xtdCls -> xtdCls.getMethods().stream()).filter(op -> op.getTags().contains("init"))
								.findFirst();
					} catch (Exception e) {
						Activator.error(e.getMessage(), e);
					}
	
					if (initOperation.isPresent()) {
						return (new MethodLabelProvider()).getText(initOperation.get());
					}
				}
			}
		}
		return "";
	}

	/**
	 * Collect all DSL paths declared in Language Extension Point
	 */
	public List<String> getAllLanguages() {
		List<String> languagesNames = new ArrayList<String>();

		IConfigurationElement[] languages = Platform
				.getExtensionRegistry().getConfigurationElementsFor(
						LanguageDefinitionExtensionPoint.GEMOC_LANGUAGE_EXTENSION_POINT);
		for (IConfigurationElement lang : languages) {
			String xdsmlPath = lang.getAttribute("xdsmlFilePath");
			String xdsmlName = lang.getAttribute("name");
			if (xdsmlPath.endsWith(".dsl")) {
				languagesNames.add(xdsmlName);
			}
		}
		return languagesNames;
	}

	public void registerLaunchLanguageSelectionListener(ILaunchLanguageSelectionListener listener) {
		this._languageSelectionListeners.add(listener);
	}
}
