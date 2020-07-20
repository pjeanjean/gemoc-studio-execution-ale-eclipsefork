package org.eclipse.gemoc.ale.interpreted.engine.debug;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecoretools.ale.core.interpreter.impl.AleInterpreter;
import org.eclipse.emf.ecoretools.ale.core.interpreter.notapi.DynamicFeatureRegistry;
import org.eclipse.emf.ecoretools.ale.core.interpreter.notapi.RuntimeInstanceHelper;
import org.eclipse.emf.ecoretools.ale.implementation.ModelUnit;
import org.eclipse.emf.ecoretools.ale.implementation.RuntimeClass;
import org.eclipse.gemoc.executionframework.debugger.IDynamicPartAccessor;
import org.eclipse.gemoc.executionframework.debugger.MutableField;
import org.eclipse.xtext.naming.DefaultDeclarativeQualifiedNameProvider;
import org.eclipse.xtext.naming.QualifiedName;

public class AleDynamicAccessor implements IDynamicPartAccessor{

	Map<EClass, EClass> baseToRuntime;
	
	AleInterpreter interpreter;
	
	Map<EClass, Integer> counters = new HashMap<>();
	
	List<RuntimeClass> dynamicClasses = null; //new ArrayList<>();

	public AleDynamicAccessor(AleInterpreter interpreter, List<ModelUnit> allModelUnits) {
		List<EClass> domain = 
			interpreter
			.getQueryEnvironment()
			.getEPackageProvider()
			.getEClassifiers()
			.stream()
			.filter(cls -> cls instanceof EClass)
			.map(cls -> (EClass) cls)
			.collect(Collectors.toList());
		
		dynamicClasses = allModelUnits
		.stream()
		.flatMap(unit -> unit.getClassDefinitions().stream())
		.collect(Collectors.toList());
		
		
		this.baseToRuntime = RuntimeInstanceHelper.getBaseToRuntime(allModelUnits,domain);
		this.interpreter = interpreter;
	}
	
	@Override
	public List<MutableField> extractMutableField(EObject eObject) {
		
		List<MutableField> result = new ArrayList<MutableField>();
		if(eObject == null) {
			return result;
		}
		
		EClass runtimePart = baseToRuntime.get(eObject.eClass());
		
		/*
		 * Retrieve dynamic features defined in re-opened class from ALE interpreter internal
		 */
		if(runtimePart != null) {
			for(EStructuralFeature feature : runtimePart.getEStructuralFeatures()) {
				
				Supplier<Object> getter = () -> {
					DynamicFeatureRegistry registry = interpreter.getCurrentEngine().getEvalEnvironment().getFeatureAccess();
					return registry.aqlFeatureAccess(eObject, feature.getName());
				};
				
				Consumer<Object> setter = newValue -> {
					DynamicFeatureRegistry registry = interpreter.getCurrentEngine().getEvalEnvironment().getFeatureAccess();
					registry.setDynamicFeatureValue(eObject, feature.getName(), newValue);
				};
				
				MutableField field = new MutableField(
						feature.getName()+" ("+getName(eObject)+ " :"+eObject.eClass().getName() +")",
						eObject,
						feature,
						getter,
						setter
						);
				result.add(field);
			}
		}
		
		/*
		 * Runtime features are directly accessible from eObject if its class is defined in .ale file 
		 */
		if(isDynamic(eObject)) {
			for(EStructuralFeature feature : eObject.eClass().getEStructuralFeatures()) {
				
				Supplier<Object> getter = () -> {
					return eObject.eGet(feature);
				};
				
				Consumer<Object> setter = newValue -> {
					eObject.eSet(feature, newValue);
				};
				
				MutableField field = new MutableField(
						feature.getName()+" ("+getName(eObject)+ " :"+eObject.eClass().getName() +")",
						eObject,
						feature,
						getter,
						setter
						);
				result.add(field);
			}
		}
		
		return result;
	}

	
	private String getName(EObject eObject) {
		DefaultDeclarativeQualifiedNameProvider nameprovider = new DefaultDeclarativeQualifiedNameProvider();
		
		EAttribute idProp = eObject.eClass().getEIDAttribute();
		if (idProp != null) {
			Object id = eObject.eGet(idProp);
			if (id != null) {
				NumberFormat formatter = new DecimalFormat("00");
				String idString = id.toString();
				if(id instanceof Integer){
					idString = formatter.format((Integer)id);
				}
				return eObject.eClass().getName() + "_" + idString;
			} else {
				if (!counters.containsKey(eObject.eClass())) {
					counters.put(eObject.eClass(), 0);
				}
				Integer counter = counters.get(eObject.eClass());
				counters.put(eObject.eClass(), counter + 1);
				return eObject.eClass().getName() + "_" + counter;
			}

		} else {
			QualifiedName qname = nameprovider.getFullyQualifiedName(eObject);
			if(qname == null) 
				return eObject.toString();
			else 
				return qname.toString();
		}
	}

	@Override
	public boolean isDynamic(EObject obj) {
		
		if(obj == null) {
			return false;
		}
		
		String qualifiedClsName = getQualifiedName(obj.eClass());
		
		return 
			dynamicClasses
			.stream()
			.anyMatch(runtimeCls -> getQualifiedName(runtimeCls).equals(qualifiedClsName));
	}
	
	private String getQualifiedName(EClass cls) {
		
		List<String> segments = new ArrayList<>();
		segments.add(0,cls.getName());
		
		EPackage parent = cls.getEPackage();
		while(parent != null) {
			segments.add(0,parent.getName());
			parent = parent.getESuperPackage();
		}
		
		return String.join(".", segments);
	}
	
	private String getQualifiedName(RuntimeClass cls) {
		return ((ModelUnit) cls.eContainer()).getName() + "." + cls.getName();
	}
}
