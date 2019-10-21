package ca.concordia.jaranalyzer;



import ca.concordia.jaranalyzer.Models.ClassInfo;
import ca.concordia.jaranalyzer.Models.JarInfo;
import ca.concordia.jaranalyzer.Models.PackageInfo;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.jar.JarFile;
import java.util.stream.IntStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import ca.concordia.jaranalyzer.util.Utility;


import static java.util.stream.Collectors.*;


public class JarAnalyzer {



	public TinkerGraph graph;



	public JarAnalyzer(String jarPath) {
		File file = new File(jarPath);
		graph = TinkerGraph.open();
		graph.createIndex("Kind",Vertex.class);
	}

	public void toGraph(JarInfo j) {
		Vertex jar = graph.addVertex("Kind", "Jar", "ArtifactId", j.getArtifactId(), "Version", j.getVersion(), "GroupId", j.getGroupId());
		for (PackageInfo p : j.getPackages()) {
			Vertex pkg = graph.addVertex("Kind","Package","Name",p.getName());
			jar.addEdge("ContainsPkg",pkg);
			for(ClassInfo c : p.getClasses()){
				Vertex cls = graph.addVertex("Kind","Class","isAbstract",c.isAbstract(),"isInterface",
						c.isInterface(), "Name", c.getName(),"Type",c.getType().toString(), "QName", c.getQualifiedName());
				pkg.addEdge("Contains",cls);

				if(!c.getSuperClassName().isEmpty())
					cls.addEdge("extends",graph.addVertex("Kind", "SuperClass", "Name", c.getSuperClassName()));

				c.getSuperInterfaceNames().stream()
						.forEach(e -> cls.addEdge("implements", graph.addVertex("Kind","SuperInterface","Name",e)));

				c.getMethods().stream().filter(x->!x.isPrivate())
						.forEach(m -> {
							Vertex x = graph.addVertex("Kind","Method","Name",m.getName(),"isAbstract",m.isAbstract()
									, "isConstructor",m.isConstructor(),"isStatic",m.isStatic(),"ReturnType",m.getReturnType(),"ParamType",m.getParameterTypes());
							cls.addEdge("Declares",x);
						});

				c.getFields().stream().filter(x->!x.isPrivate())
						.forEach(f -> cls.addEdge("Declares",graph.addVertex("Kind","Field", "Name",f.getName(),"ReturnType",f.getType().toString())));

			}
		}
	}

	private JarInfo getJarInfo(String groupId, String artifactId, String version) {
		JarInfo jarInfo;
		String url = "http://central.maven.org/maven2/" + groupId + "/" + artifactId + "/" + version + "/" + artifactId
				+ "-" + version + ".jar";
		jarInfo = AnalyzeJar(url, groupId, artifactId, version);

		if (jarInfo == null) {
			url = "http://central.maven.org/maven2/org/" + groupId + "/" + artifactId + "/" + version + "/" + artifactId
					+ "-" + version + ".jar";
			jarInfo = AnalyzeJar(url, groupId, artifactId, version);
		}

		if (jarInfo == null) {
			url = "http://central.maven.org/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version
					+ "/" + artifactId + "-" + version + ".jar";
			jarInfo = AnalyzeJar(url, groupId, artifactId, version);
		}
		return jarInfo;
	}



	public JarInfo AnalyzeJar(String url, String groupId, String artifactId, String version) {
		JarFile jarFile = DownloadJar(url);
		return AnalyzeJar(jarFile, groupId, artifactId, version);
	}

	public JarFile DownloadJar(String jarUrl) {
		String jarName = Utility.getJarName(jarUrl);
		String jarLocation = "";// jarsPath.toString() + '/' + jarName;
		JarFile jarFile = null;
		File file = new File(jarLocation);
		if (file.exists()) {
			try {
				return new JarFile(new File(jarLocation));
			} catch (IOException e) {
				// System.out.println("Cannot open jar: " + jarLocation);
			}
		}
		try {
			Utility.downloadUsingStream(jarUrl, jarLocation);
		} catch (IOException e) {
			// System.out.println("Could not download jar: " + jarUrl);
		}

		try {
			jarFile = new JarFile(new File(jarLocation));
		} catch (IOException e) {
			// System.out.println("Cannot open jar: " + jarLocation);
		}
		return jarFile;
	}

	public JarInfo AnalyzeJar(JarFile jarFile, String groupId, String artifactId, String version) {
		if (jarFile == null)
			return null;

		JarInfo jarInfo = new JarInfo(jarFile, groupId, artifactId, version);
		return jarInfo;
	}


	public void jarToGraph(JarFile jarFile, String groupId, String artifactId, String version) {
		JarInfo ji = new JarInfo(jarFile, groupId, artifactId, version);
		toGraph(ji);
	}
}
