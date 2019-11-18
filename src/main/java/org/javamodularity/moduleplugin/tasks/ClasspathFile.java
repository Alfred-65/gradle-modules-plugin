package org.javamodularity.moduleplugin.tasks;

import groovy.util.Node;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.XmlProvider;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.plugins.ide.eclipse.GenerateEclipseClasspath;
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath;

/**
 * This class provides functionality to improve the content of {@code .classpath} file created
 * by the {@code eclipse} plugin.
 * 
 * <p>The version number of this file follow the rules from
 * <a href="https://semver.org/">Semantic Versioning 2.0.0</a>
 * 
 * <p><i><b>Summary:</b> Given a version number MAJOR.MINOR.PATCH, increment the:
 * <ol>
 *   <li>MAJOR version when you make incompatible API changes,
 *   <li>MINOR version when you add functionality in a backwards-compatible manner, and
 *   <li>PATCH version when you make backwards-compatible bug fixes.
 * </ol>
 * Additional labels for pre-release and build metadata are available as extensions to the
 * MAJOR.MINOR.PATCH format.</i>
 * 
 * <p><b>History:</b>
 * <ol>
 *   <li>2019-11-11, 1.0.0: first edition
 * </ol>
 * 
 * @version 1.0.0
 * @author <a href="mailto:alfred.65.fiedler@gmail.com">Alfred Fiedler</a>
 */
@SuppressWarnings("unchecked") // keep Eclipse code-checker happy
/* package */ final class ClasspathFile {
  /**
   * Logger.
   */
  private static final Logger LOGGER = Logging.getLogger(ModularJavaExec.class); // */
  
  /**
   * Name of interesting items when it comes to investigation.
   */
  /* package */ static final String NAME_ITEM = "classpathentry"; // */
  
  /**
   * Name of interesting child in item.
   */
  /* package */ static final String NAME_CHILD = "attributes"; // */
  
  /**
   * Name of interesting child in child of item.
   */
  /* package */ static final String NAME_GRAND = "attribute"; // */
  
  /**
   * Name of interesting attribute in child of child of item.
   */
  /* package */ static final String NAME_ATTRIBUTE = "gradle_used_by_scope"; // */
  
  /**
   * Adds functionality to improve the {@code .classpath} file created by the eclipse-plugin.
   * 
   * @param project
   *        the project
   */
  /* package */ static void addAction(
      final Project project
  ) {
    try {
      // --- get task eclipseClasspath
      final TaskContainer tasks = project.getTasks();
      LOGGER.debug(
          "improveEclipseClasspathFile: list of tasks:{}",
          tasks.stream()
          .map(i -> i.toString())
          .collect(Collectors.joining(
              System.lineSeparator() + "  ", System.lineSeparator() + "  ", ""
              ))
      );
      
      // might throw UnknownDomainObjectException, thus we better catch that
      final Task task = tasks.getByName("eclipseClasspath");
      // ... task with name "eclipseClasspath" available => configure it
      
      // --- add functionality for enhancing the .classpath content
      // Note: For more information see the manual for the eclipse-plugin
      final EclipseClasspath eclipseClasspath = ((GenerateEclipseClasspath) task).getClasspath();
      eclipseClasspath.file(new Action<XmlFileContentMerger>() {
        @Override
        public void execute(final XmlFileContentMerger xmlMerger) {
          xmlMerger.withXml(new Action<XmlProvider>() {
            @Override
            public void execute(final XmlProvider xmlProvider) {
              final Node rootNode = xmlProvider.asNode();
              LOGGER.debug("addAction: rootNode.before:{}", rootNode);
              ClasspathFile.improveEclipseClasspathFile(rootNode);
              LOGGER.debug("addAction: rootNode.after :{}", rootNode);
              // ClasspathFile.improveEclipseClasspathFile(xmlProvider.asNode());
            } // end inner method
          }); // end inner class Action<XmlProvider)
        } // end inner method
      }); // end inner class Action<XmlFileContentManager)
    } catch (UnknownDomainObjectException e) {
      // ... no task with name "eclipseClasspath" => do nothing
      // intentionally empty
    } // end catch (UnknownDomainObjectException)
  } // end method */
  
  /**
   * Improve ".classpath"-file.
   * 
   * <p>This method modifies the given content of a ".classpath"-file in the following ways:
   * <ol>
   *   <li>Each "classpathentry" with a gradle_used_by_scope of test
   *       gets an additional attribute test = "true".
   *   <li>Each "classpathentry" of kind = "con" with a path containing "JRE_CONTAINER"
   *       is moved to the module-path.
   *   <li>Each "classpathentry" of kind = "lib" with a gradle_used_by_scope of "main"
   *       is moved to the module-path.
   * </ol>
   * 
   * @param rootNode
   *        XML-content to be improved
   */
  /* package */ static void improveEclipseClasspathFile(final Node rootNode) {
    // #############################################################################################
    // Note 10: Earlier versions of the Gradle-Eclipse plugin didn't tag folders in "./src/test"
    //          with "only for test". Furthermore other libraries relevant only for tests also
    //          hadn't been tagged with "only for test. As of 2019-10-21 Gradle 5.6.2 tag these
    //          folders and libraries correctly. Thus there is no need for changing the XML-content
    //          in this regard. Obviously the eclipse-plugin in Gradle is now more sophisticated.
    //          Thus the following code is no longer necessary:
    // if ("src".equals(kind) && "bin/test".equals(it.attribute("output"))) {
    //   // ... node is "src" and output is "test" => add test=true
    //   // retrieve first child, which is "attributes"
    //   val attributes = it.children().get(0)
    //   if (attributes is Node) {
    //     val map = attributes.appendNode("attribute").attributes()
    //     map.put("name",  "test")
    //     map.put("value", "true")
    //   } // end if
    // } // end if ( test-source )
    
    // #############################################################################################
    // Note 20: The eclipse-plugin in Gradle puts everything on the classpath and nothing on the
    //          module-path. Its seems appropriate to adjust things such that non-test dependencies
    //          are tagged such that they appear on the module-path. This works at least with the
    //          following versions:
    //          Gradle 5.6.2, Eclipse 2019-09
    
    // #############################################################################################
    // Note 30: Strategy for improving is as follows:
    //          a. check that we got an XML-node.
    //          b. loop over all children in given XML-node
    //          c. if node's attribute "kind" has value "lib" then check how gradle treats it.
    //             If gradle's scope contains main then add a node to "attribute" with content:
    //             <attribute name="module" value="true"/>
    
    // Excerpt from an entry which should be improved:
    // <classpathentry sourcepath="D:/.p/..." kind="lib" path="D:/.p/...">
    //   <attributes>
    //     <attribute name="gradle_used_by_scope" value="main,test"/>
    //   </attributes>
    // </classpathentry>
    
    // System.out.printf("improve before:%n%s%n", object);
    
    final Map<String, String> flagTest = new LinkedHashMap<>();
    flagTest.put("name",  "test");
    flagTest.put("value", "true");
    
    final Map<String, String> flagModule = new LinkedHashMap<>();
    flagModule.put("name",  "module");
    flagModule.put("value", "true");
    
    // --- loop over all items in rootNode and collect those of type Node with name "classpathentry"
    final List<Node> classpathEntries = (List<Node>) rootNode.children().stream()
        .filter(i -> i instanceof Node) // better safe than sorry
        .filter(i -> NAME_ITEM.equals(((Node)i).name())) // filter nodes with name "classpathentry"
        .collect(Collectors.toList());
    
    putJreOnModulePath(classpathEntries);
    
    
    
    
    LOGGER.debug("improveEclipseClasspathFile, classpathEntries.type = {}", classpathEntries.getClass().toString());
    
    /*
        .filter(i -> isKindOfLib(i)) // filter nodes which are kind of "lib"
        .forEach(item -> {
          // ... item has type Node and name "classpathentry"
          // ... item is a kind of "lib"
          
          // --- loop over all children of item and investigate those with name "attributes"
          ((Node) item).children().stream()
              .filter(j -> j instanceof Node) // better safe than sorry
              .filter(j -> NAME_CHILD.equals(((Node)j).name())) // nodes with name "attributes"
              .filter(j -> shouldBeOnModulePath(j)) // child should be on module-path
              .forEach(j -> {
                // ... item has type Node and name "classpathentry"
                // ... item is a kind of "lib"
                // ... child of item has type Node and name "attributes"
                // ... grandChild of item indicates that it should be on module-path, but isn't yet
                ((Node) j).appendNode(NAME_GRAND, flagModule); // move to module-path
              }); // end forEach(j -> ...)
        }); // end forEach(i -> ...)
        // */
  } // end method */
  
  /**
   * Estimates whether given {@link Node} belongs to a JRE description.
   * 
   * @param node
   *        a {@link Node} investigated whether it is of a certain kind
   * 
   * @return true if the {@link Node} is kind of "con" and has an attribute "path" containing
   *         "JRE_CONTAINER",
   *         false otherwise
   */
  private static boolean isJre(final Node node) {
    final Object path = node.attribute("path"); // might return null
    
    return isKindOf(node, "con") && (null != path) && path.toString().contains("JRE_CONTAINER");
  } // end method */
  
  /**
   * Estimates whether given {@link Node} is of certain kind.
   * 
   * @param node
   *        a {@link Node} investigated whether it is of a certain kind
   * 
   * @param kind
   *        type for which the given {@link Node} is checked
   *        
   * @return true if the {@link Node} has attribute "kind" an the value of that attribute is
   *         equal to the given one in parameter {@code kind},
   *         false otherwise
   */
  private static boolean isKindOf(final Node node, final String kind) {
    final Object attr = node.attribute("kind"); // might return null
    
    return kind.equals(attr);
  } // end method */
  
  /**
   * Estimates whether given object is kind of "lib".
   * 
   * @param object
   *        a {@link Node} investigated whether it is kind of "lib"
   *        
   * @return true if the {@link Node} has attribute "kind" an the value of that attribute is "lib",
   *         false otherwise
   */
  private static boolean isKindOfLib(final Object object) {
    // ... assertion object is of type Node
    final Node item = (Node) object;
    final Object kind = item.attribute("kind"); // might return null
    
    return "lib".equals(kind);
  } // end method */
  
  private static void moveToModulePath(final Node node) {
    if (node.children().isEmpty()) {
      node.appendNode(NAME_CHILD);
    } // end if
    
    final List<Node> childs = node.children(); // never empty
  } // end method */
  
  /**
   * Estimates whether the given {@link Node} indicates that is should be on the module-path.
   *  
   * @param object
   *        a {@link Node}
   * 
   * @return true if a sub-node of given one indicates a gradle scope "main" but didn't indicate
   *         that it should be on module-path,
   *         false otherwise
   */
  private static boolean shouldBeOnModulePath(final Object object) {
    // ... assertion object is of type Node
    // ... assertion object has name "attributes"
    final Node child = (Node) object;
    
    // check whether children of child contain a gradle scope and
    // if so whether that contains main
    // if so whether they already contain a module-path entry
    // if not put that entry on module-path
    
    final boolean isGradleScopeMain = child.children().stream()
        .filter(k -> k instanceof Node) // better safe than sorry
        .filter(k -> NAME_GRAND.equals(((Node)k).name())) // nodes with name "attribute"
        .filter(k -> isGradleScopeMain(k)) // nodes with gradle scope of main
        .findFirst() // one grandChild per child is enough to trigger movement
        .isPresent();
    
    if (isGradleScopeMain) {
      // ... child has a gradle scope of "main"
      //     => check whether it is already on the module-path
      
      final boolean isAlreadyMoved = child.children().stream()
          .filter(k -> k instanceof Node) // better safe than sorry
          .filter(k -> NAME_GRAND.equals(((Node)k).name())) // nodes with name "attribute"
          .filter(k -> isAlreadyMoved(k)) // nodes indicating movement
          .findFirst() // one grandChild per child is enough to trigger non-movement
          .isPresent();
      
      return !isAlreadyMoved;
    } // end if
    
    return false;
  } // end method */
  
  /**
   * Estimates whether given object indicates a gradle scope of "main".
   * 
   * @param object
   *        a {@link Node}
   *        
   * @return true if given {@link Node} has a sub-node with name "attributes" and that sub-node
   *         has an attribute "gradle_used_by_scope" and that attribute contains "main",
   *         false otherwise
   */
  private static boolean isAlreadyMoved(final Object object) {
    // ... assertion object is of type Node
    // ... assertion has name "attribute"
    
    final Node grandChild = (Node) object;
    final Object name  = grandChild.attribute("name");  // might be null
    final Object value = grandChild.attribute("value"); // might be null
    return "module".equals(name) && "true".equals(value);
  } // end method */
  
  /**
   * Estimates whether given object indicates being an module-path.
   * 
   * @param object
   *        a {@link Node}
   *        
   * @return true if given {@link Node} has a sub-node with name "attributes" and that sub-node
   *         has an attribute "module" and that attribute contains "true",
   *         false otherwise
   */
  private static boolean isGradleScopeMain(final Object object) {
    // ... assertion object is of type Node
    // ... assertion has name "attribute"
    
    final Node grandChild = (Node) object;
    final Object name  = grandChild.attribute("name");  // might be null
    final Object scope = grandChild.attribute("value"); // might be null
    return NAME_ATTRIBUTE.equals(name)
        && (null != scope) // avoid NullPointerException
        && scope.toString().contains("main"); // scope isn't null here
  } // end method */
  
  /**
   * Puts every entry which is kind of "con" and with a path containing "JRE_CONTAINER"
   * on module-path.
   * 
   * @param entries
   *        list of {@link Node} with with name "classpathentry" 
   */
  private static void putJreOnModulePath(final List<Node> entries) {
    entries.stream()
        .filter(i -> isJre(i));
  } // end method */
} // end class