package ca.concordia.jaranalyzer.Models;

import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;
import java.util.stream.Collectors;

public class MethodInfo {
	private String name;
	private ClassInfo classInfo;
	private Type[] argumentTypes;
	private Type returnType;
	private List<String> thrownInternalClassNames;
	private boolean isPublic;
	private boolean isPrivate;
	private boolean isProtected;
	private boolean isAbstract;
	private boolean isStatic;
	private boolean isSynchronized;
	private boolean isConstructor;
	private boolean isVarargs;
	private String qualifiedName;
	private String internalClassConstructorSuffix;

	public MethodInfo(Vertex vertex, ClassInfo classInfo) {
		this.classInfo = classInfo;

		this.name = vertex.<String>property("Name").value();
		this.isAbstract = vertex.<Boolean>property("isAbstract").value();
		this.isConstructor = vertex.<Boolean>property("isConstructor").value();
		this.isStatic = vertex.<Boolean>property("isStatic").value();
		this.isPrivate = vertex.<Boolean>property("isPrivate").value();
		this.isPublic = vertex.<Boolean>property("isPublic").value();
		this.isProtected = vertex.<Boolean>property("isProtected").value();
		this.isSynchronized = vertex.<Boolean>property("isSynchronized").value();
		this.isVarargs = vertex.<Boolean>property("isVarargs").value();

		VertexProperty<String> internalClassConstructorSuffixProp = vertex.property("internalClassConstructorSuffix");

		if (internalClassConstructorSuffixProp.isPresent()) {
			this.internalClassConstructorSuffix = internalClassConstructorSuffixProp.value();
		}

		this.returnType = Type.getType(vertex.<String>property("returnTypeDescriptor").value());

		Iterator<VertexProperty<String>> argumentTypeDescriptorListIterator
				= vertex.properties("argumentTypeDescriptorList");

		List<Type> argumentTypeList = new ArrayList<>();

		while (argumentTypeDescriptorListIterator.hasNext()) {
			argumentTypeList.add(Type.getType(argumentTypeDescriptorListIterator.next().value()));
		}

		this.argumentTypes = argumentTypeList.toArray(new Type[0]);

		Iterator<VertexProperty<String>> thrownInternalClassNamesIterator =
				vertex.properties("thrownInternalClassNames");

		this.thrownInternalClassNames = new ArrayList<>();

		while (thrownInternalClassNamesIterator.hasNext()) {
			this.thrownInternalClassNames.add(thrownInternalClassNamesIterator.next().value());
		}
	}

	@SuppressWarnings("unchecked")
	public MethodInfo(MethodNode methodNode, ClassInfo classInfo) {
		this.name = methodNode.name;
		if (name.equals("<init>")) {
			isConstructor = true;

			if (classInfo.getName().contains("$")) {
				internalClassConstructorSuffix = classInfo.getName().substring(0, classInfo.getName().lastIndexOf("$") + 1);
				name = classInfo.getName().replace(internalClassConstructorSuffix, "");
			} else {
				name = classInfo.getName();
			}
		}

		this.classInfo = classInfo;
		this.returnType = Type.getReturnType(methodNode.desc);
		if (isConstructor && Objects.nonNull(internalClassConstructorSuffix)) {
			List<Type> types = new ArrayList<Type>();
			for (Type type : Type.getArgumentTypes(methodNode.desc)) {
				if (!classInfo.getQualifiedName().startsWith(type.getClassName() + ".")) {
					types.add(type);
				}
			}
			this.argumentTypes = new Type[types.size()];
			this.argumentTypes = types.toArray(this.argumentTypes);
		}
		else {
			this.argumentTypes = Type.getArgumentTypes(methodNode.desc);
		}
		
		this.thrownInternalClassNames = methodNode.exceptions;

		if ((methodNode.access & Opcodes.ACC_PUBLIC) != 0) {
			isPublic = true;
		} else if ((methodNode.access & Opcodes.ACC_PROTECTED) != 0) {
			isProtected = true;
		} else if ((methodNode.access & Opcodes.ACC_PRIVATE) != 0) {
			isPrivate = true;
		}
		this.qualifiedName = classInfo.getQualifiedName();
		if ((methodNode.access & Opcodes.ACC_STATIC) != 0) {
			isStatic = true;
			this.qualifiedName = qualifiedName + name;
		}

		if ((methodNode.access & Opcodes.ACC_ABSTRACT) != 0) {
			isAbstract = true;
		}

		if ((methodNode.access & Opcodes.ACC_SYNCHRONIZED) != 0) {
			isSynchronized = true;
		}
		
		if ((methodNode.access & Opcodes.ACC_VARARGS) != 0) {
			isVarargs = true;
		}
	}

	public String toString() {
		StringBuilder methodDescription = new StringBuilder();
		if (isPublic) {
			methodDescription.append("public ");
		} else if (isProtected) {
			methodDescription.append("protected ");
		} else if (isPrivate) {
			methodDescription.append("private ");
		}

		if (isStatic) {
			methodDescription.append("static ");
		}

		if (isAbstract) {
			methodDescription.append("abstract ");
		}

		if (isSynchronized) {
			methodDescription.append("synchronized ");
		}

		methodDescription.append(returnType.getClassName());
		methodDescription.append(" ");

		if (Objects.nonNull(internalClassConstructorSuffix)) {
			methodDescription.append(internalClassConstructorSuffix);
		}

		methodDescription.append(this.name);

		methodDescription.append("(");
		for (int i = 0; i < argumentTypes.length; i++) {
			Type argumentType = argumentTypes[i];
			if (i > 0) {
				methodDescription.append(", ");
			}
			methodDescription.append(argumentType.getClassName());
		}
		methodDescription.append(")");

		if (!thrownInternalClassNames.isEmpty()) {
			methodDescription.append(" throws ");
			int i = 0;
			for (String thrownInternalClassName : thrownInternalClassNames) {
				if (i > 0) {
					methodDescription.append(", ");
				}
				methodDescription.append(Type.getObjectType(
						thrownInternalClassName).getClassName());
				i++;
			}
		}

		return methodDescription.toString();
	}

	public String getName() {
		return name;
	}

	public ClassInfo getClassInfo() {
		return classInfo;
	}

	public String getQualifiedClassName() {
		return classInfo.getQualifiedName();
	}

	public String getClassName() {
		return classInfo.getName();
	}

	public String getPackageName() {
		return classInfo.getPackageName();
	}

	public Type[] getArgumentTypes() {
		return argumentTypes;
	}

	public boolean isConstructor() {
		return isConstructor;
	}

	public String getParameterTypes() {
		return Arrays.stream(argumentTypes).map(x->x.getClassName()).collect(Collectors.joining("&"));
	}

	public String getReturnType() {
		return returnType.getClassName();// .substring(returnType.getClassName().lastIndexOf('.')
											// + 1);
	}

	public Type getReturnTypeAsType() {
		return returnType;// .substring(returnType.getClassName().lastIndexOf('.')
		// + 1);
	}

	public List<String> getThrownInternalClassNames() {
		return thrownInternalClassNames;
	}

	public boolean isPublic() {
		return isPublic;
	}

	public boolean isPrivate() {
		return isPrivate;
	}

	public boolean isProtected() {
		return isProtected;
	}

	public boolean isAbstract() {
		return isAbstract;
	}

	public boolean isStatic() {
		return isStatic;
	}

	public boolean isSynchronized() {
		return isSynchronized;
	}

	public boolean isVarargs() {
		return isVarargs;
	}

	public String getInternalClassConstructorSuffix() {
		return internalClassConstructorSuffix;
	}

	public boolean matches(String methodName, int numberOfParameters) {
		if (argumentTypes.length != numberOfParameters)
			return false;

		if (name.replace('$', '.').equals(methodName)) {
			return true;
		} else if ((getClassName() + "." + name).replace('$', '.').equals(methodName)) {
			return true;
		} else if ((getQualifiedClassName() + "." + name).replace('$', '.').equals(
				methodName)) {
			return true;
		} else if (isConstructor) {
			if (getQualifiedClassName().replace('$', '.').equals(methodName)) {
				return true;
			}
			if(name.contains("$") && name.endsWith("$" + methodName)) {
				return true;
			}
		}
		return false;
	}


//	public Identification getID(Identification owner){
//		TypeSignature typeSign = TypeSignature.newBuilder()
//				.setMthdSign(MethodSign.newBuilder()
//						.setReturnType(getTypeInfo(returnType))
//						.addAllParam(Arrays.stream(argumentTypes).map(this::getTypeInfo).collect(toList())))
//				.build();
//
//		return Identification.newBuilder()
//				.setName(getName())
//				.setType(typeSign)
//				.setKind(isConstructor ? "CONSTRUCTOR" : "METHOD")
//				.setOwner(owner).build();
//	}
//
//
//
//	public TypeFactGraph<Identification> getTFG(Identification owner) {
//		Identification mid = getID(owner);
//		Identification am = Identification.newBuilder()
//				.setName(isPrivate ? "PRIVATE" : (isPublic ? "PUBLIC" : (isProtected ? "PROTECTED" : "DEFAULT")))
//				.setKind("MODIFIER").setOwner(mid).build();
//		return TypeFactGraph.<Identification>emptyTFG()
//				.map(addNode(mid))
//				.map(u_v(mid, am, "MODIFIER"));
//	}
}
