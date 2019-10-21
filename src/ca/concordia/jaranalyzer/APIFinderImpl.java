package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.util.Utility;
import org.apache.commons.lang.ArrayUtils;
import org.apache.tinkerpop.gremlin.process.traversal.TextP;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static ca.concordia.jaranalyzer.Util.getCuFor;
import static ca.concordia.jaranalyzer.Util.getFileContent;
import static java.util.stream.Collectors.toList;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.*;

public class APIFinderImpl  {


//	private GraphTraversal traverser;

	public APIFinderImpl() {
//		this.traverser = t;
	}

	public APIFinderImpl(JarAnalyzer analyzer, String javaHome, String javaVersion, Stream<String> s){
		System.out.println(javaHome);
			if (javaHome != null) {
				List<String> jarFiles = Utility.getFiles(javaHome, "jar");
				System.out.println(jarFiles.size());
				for (String jarLocation : jarFiles) {
					try {
						Path path = Paths.get(jarLocation);
						if(Files.exists(path)) {
							JarFile jarFile = new JarFile(new File(jarLocation));
							analyzer.jarToGraph(jarFile, path.getFileName().toString(), "Java", javaVersion);
						}
					} catch (Exception  e) {
						e.printStackTrace();
						System.out.println(e.toString());
						System.out.println("Could not open the JAR");
					}
				}

			}



		analyzer.graph.traversal().io("D:\\MyProjects\\apache-tinkerpop-gremlin-server-3.4.3\\data\\JavaJars.kryo").write();
	}

	public static void getCompilationUnitWorld(CompilationUnit cu, String fileName, TinkerGraph g) throws JavaModelException {
		List<ImportDeclaration> imports = cu.imports();
		List<AbstractTypeDeclaration> types = cu.types();
		cu.getPackage();

		Vertex pck = g.traversal().V().has("Kind", "Package", "Name")
				.has(cu.getPackage().getName().toString())
				.toSet().stream().findFirst()
				.orElse(g.addVertex("Kind", "Package", "Name", cu.getPackage().getName().toString()));

		Vertex cuV = g.addVertex("Kind", "CompilationUnit", "Name", fileName);
		pck.addEdge("Contains",cuV);
		for(AbstractTypeDeclaration t : types){
			if(t instanceof TypeDeclaration){
				TypeDeclaration td = (TypeDeclaration)t;
				Vertex tp = g.addVertex("Kind", td.isInterface()?"Interface":"Class", "Name", td.getName().toString());
				imports.stream().map(i->i.getName().toString()).forEach(i -> {
					Vertex iv = g.addVertex("Kind", "Import", "Name", i);
					tp.addEdge("Imports", iv);
				});

				tp.addEdge("extends",g.addVertex("Kind","SuperClass","Name",td.getSuperclassType().toString()));
				List<Type> interfaces = td.superInterfaceTypes();
				interfaces.forEach(e -> tp.addEdge("implements",g.addVertex("Kind","SuperInterface","Name",e.toString())));

				for(MethodDeclaration md: td.getMethods()) {
					List<SingleVariableDeclaration> params = md.parameters();
					String[] p = (String[]) IntStream.range(0, params.size())
							.mapToObj(x -> Stream.of("ParamType:" + x, params.get(x).getType().toString()).collect(toList()))
							.flatMap(x -> x.stream())
							.toArray();
					p = (String[]) ArrayUtils.addAll(
							Stream.of("Kind", md.isConstructor()? "Constructor":"Method", "ReturnType", md.getReturnType2().toString()
							, "Name", md.getName().toString()).toArray(),p);
					tp.addEdge("Declares",g.addVertex(p));
				}
				for(FieldDeclaration f: td.getFields()){
					List<VariableDeclarationFragment> frg = f.fragments();
					Vertex fld = g.addVertex("Kind", "Field", "ReturnType", f.getType().toString(), "Name", frg.get(0).getName().toString());
					tp.addEdge("Declares",fld);
				}
				cuV.addEdge("Contains",tp);

			}else if(t instanceof EnumDeclaration){

			}
		}

	}

	public static TinkerGraph createTypeWorld(Repository repository, RevCommit commit) {
		Set<String> repositoryDirectories = new HashSet<>();
		TinkerGraph gr = TinkerGraph.open();
		RevTree parentTree = commit.getTree();
		try (TreeWalk treeWalk = new TreeWalk(repository)) {
			treeWalk.addTree(parentTree);
			treeWalk.setRecursive(true);
			while (treeWalk.next()) {
				String pathString = treeWalk.getPathString();
				if(pathString.endsWith(".java")) {
					String fileName = pathString.replace(".java", "");
					getCompilationUnitWorld(getCuFor(getFileContent(repository,treeWalk)),fileName,gr);
					repositoryDirectories.add(fileName);
				}
			}
		}
		catch (Exception e){
			e.printStackTrace();
		}

		return gr;
	}


	public Set findTypeInJars(List<String> qualifiers, String lookupType, GraphTraversalSource traverser) {
		Set set = traverser.V()
				.has("Kind", "Class")
				.where(or(has("QName", TextP.within(qualifiers)),
						has("QName", TextP.within(qualifiers.stream().map(x -> x + "." + lookupType).collect(toList())))))
				.out("Declares").has("Kind", "Method")
				.valueMap("Name", "ReturnType", "ParamType","isConstructor")
				.toSet();
		return set;
	}

}
