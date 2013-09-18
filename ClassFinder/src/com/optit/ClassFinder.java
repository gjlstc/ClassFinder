package com.optit;

import java.awt.EventQueue;
import java.awt.HeadlessException;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.swing.JFrame;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import com.optit.gui.ClassFinderGui;
import com.optit.logger.CommandLineLogger;
import com.optit.logger.Logger;
import com.sun.tools.classfile.AccessFlags;
import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.Descriptor;
import com.sun.tools.classfile.Descriptor.InvalidDescriptor;
import com.sun.tools.classfile.DescriptorException;
import com.sun.tools.classfile.Exceptions_attribute;
import com.sun.tools.classfile.Method;
import com.sun.tools.classfile.Signature;
import com.sun.tools.classfile.Signature_attribute;
import com.sun.tools.classfile.Type;

public class ClassFinder implements Runnable
{
	private Properties parameters;
	private LinkedList<File> files = new LinkedList<File>();
	private Logger logger;
	private ASTParser astParser;

	public ClassFinder()
	{
		logger = new CommandLineLogger();
	}

	public ClassFinder(Logger logger)
	{
		this.logger = logger;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		// If arguments have been passed on, run directly in command line mode
		if (args.length != 0)
		{
			ClassFinder finder = new ClassFinder();
			// Parsing of arguments was successful
			if (finder.parseArguments(args))
			{
				finder.findClass();
			}
			// Parsing of arguments was not successful, print help and exit
			else
			{
				new ClassFinder().printHelp();
			}
		}
		else
		{
			// Check whether UI can be build
			try
			{
				// JFrame will throw a HeadlessException if UI can't be started.
				new JFrame();

				// No exception got thrown, continue
				EventQueue.invokeLater(new Runnable() {
					public void run()
					{
						try
						{
							new ClassFinderGui();
						} catch (Exception e)
						{
							e.printStackTrace();
						}
					}
				});
			}
			// new JFrame threw HeadlessException - print error and help
			catch (HeadlessException he)
			{
				System.out.println(he.getMessage());
				System.out.println();
				new ClassFinder().printHelp();
			}
		}
	}

	public void run()
	{
		findClass();
	}

	/**
	 * Parses the passed on arguments
	 * 
	 * @param args
	 *            The arguments to pass on
	 * @return Whether the parsing of the arguments was successful
	 */
	public boolean parseArguments(String[] args)
	{
		// No parameters were passed, print help and exit printHelp does the
		// exit
		if (null == args || args.length == 0 || args.length == 1)
		{
			return false;
		}
		else
		{
			// Parameters were passed on, properties file ignored -> read passed
			// on parameters
			parameters = new Properties();
			// Set defaults
			parameters.setProperty(Parameters.matchCase, "false");
			parameters.setProperty(Parameters.recursiveSearch, "false");
			parameters.setProperty(Parameters.verbose, "false");
			parameters.setProperty(Parameters.matchMethodName, "");

			for(int i = 0;i < args.length;i++)
			{
				if (args[i].equals(Parameters.directory))
				{
					parameters.setProperty(Parameters.directory, args[++i]);
				}
				else if (args[i].equals(Parameters.classname))
				{
					parameters.setProperty(Parameters.classname, args[++i]);
				}
				else if (args[i].equals(Parameters.matchMethodName))
				{
					parameters.setProperty(Parameters.matchMethodName,
							args[++i]);
				}
				else if (args[i].equals(Parameters.matchCase))
				{
					parameters.setProperty(Parameters.matchCase, "true");
				}
				else if (args[i].equals(Parameters.recursiveSearch))
				{
					parameters.setProperty(Parameters.recursiveSearch, "true");
				}
				else if (args[i].equals(Parameters.verbose))
				{
					parameters.setProperty(Parameters.verbose, "true");
				}
				else if (args[i].equals(Parameters.directory)
						|| args[i].equals(Parameters.directory)
						|| args[i].equals(Parameters.directory)
						|| args[i].equals(Parameters.directory)
						|| args[i].equals(Parameters.directory))
				{
					printHelp();
					System.exit(0);
				}
				else
				{
					logger.log("Unknown parameter: " + args[i]);
					logger.log();
					return false;
				}
			}
			return true;
		}
	}

	/**
	 * Print usage help into stdout and Exit
	 */
	public void printHelp()
	{
		logger.log("Usage: java -jar ClassFinder.jar|com.optit.ClassFinder -d [directory] -c [classname] -m -v -help|-h|--help|-?");
		logger.log("");
		logger.log("[-d]			The directory to search in");
		logger.log("[-c]			The classname to search for");
		logger.log("[-m]			Match case");
		logger.log("[-r]			Recursive search (search sub directories)");
		logger.log("[-v]			Enables verbose output");
		logger.log("[-o]			Match Method Name");
		logger.log("[-help|--help|-h|-?]	Display this help");
		logger.log();
		logger.log("The directory specified will be searched recursviely.");
		logger.log("The class name can either just be the class name (e.g. String) or the fully qualified name (e.g. java.lang.String)");
		logger.log();
		logger.log("Good hunting!");
	}

	/**
	 * This function sits on the top level and catches all exceptions and prints
	 * out proper error messages
	 * 
	 * @param e
	 *            The exception that comes from somewhere within the code
	 */
	public void handleExceptions(Exception e)
	{
		logger.log("Application error: " + e.getMessage());
		if (e.getCause() != null)
		{
			logger.log("Caused by: " + e.getCause().toString());
		}
		e.printStackTrace(System.err);
	}

	// Find the class
	public void findClass()
	{
		logger.setVerbose(parameters.getProperty(Parameters.verbose).equals(
				"true"));
		boolean matchCase = (parameters.getProperty(Parameters.matchCase)
				.equals("true"));

		// Class name contains some package qualifiers
		boolean containsPackageQualifier = (parameters.getProperty(
				Parameters.classname).indexOf(".") != -1);
		// Change "." in package names to slashes (e.g. "org.apache.commons" ->
		// "org/apache/commons")
		String classname = parameters.getProperty(Parameters.classname)
				.replaceAll("\\.", "/");

		// Not case sensitive
		if (!matchCase)
		{
			classname = classname.toLowerCase();
		}

		logger.logVerbose("Building directory search tree...");
		// Get file tree of directory
		buildFileList(parameters.getProperty(Parameters.directory), parameters
				.getProperty(Parameters.recursiveSearch).equals("true"));

		Iterator<File> fileIterator = files.iterator();
		// Loop over all the filtered files
		while (fileIterator.hasNext())
		{
			File file = fileIterator.next();

			// Use full qualified file name for logging, not the \ replaced one
			// logger.logVerbose("Looking at: " + file.getAbsolutePath());

			String fullFileName = file.getAbsolutePath()
					.replaceAll("\\\\", "/");
			String fileName = file.getName();

			if (!matchCase)
			{
				fullFileName = fullFileName.toLowerCase();
				fileName = fileName.toLowerCase();
			}

			// Direct class files
			if (fullFileName.endsWith(".class"))
			{
				// IF:
				// Package qualifier was defined or a part of (e.g.
				// apache.commons.Random -> apache/commons/Random)
				// AND
				// The file name ends with that qualifier -->
				// org.apache.commons.Random.class ends with
				// "apache/commons/Random.class)
				// --> CLASS FOUND!
				// OR
				// Package qualifier wasn't specified but just the class (e.g.
				// Random)
				// AND
				// The FILE NAME (note the call to file.getName() rather than
				// getAbsolutePath()) matches -->
				// org.apache.commons.Random.class's file name is Random.class
				// --> CLASS FOUND!
				if ((containsPackageQualifier && fullFileName
						.endsWith(classname + ".class"))
						|| (!containsPackageQualifier && fileName
								.equals(classname + ".class")))
				{
					// logger.log(file.getName(), file.getAbsolutePath());
					try
					{
						findMethod(file.getName(), file.getAbsolutePath(), file);
					} catch (IOException e)
					{
						logger.logVerbose("Error reading file " + fullFileName
								+ ": " + e.getMessage());
						logger.logErr(e.getMessage());
					} catch (ConstantPoolException e)
					{
						logger.logVerbose("Error reading file from class "
								+ fullFileName + ": " + e.getMessage());
						logger.logErr(e.getMessage());
					} catch (InvalidDescriptor e)
					{
						logger.logVerbose("Error reading file from class "
								+ fullFileName + ": " + e.getMessage());
						logger.logErr(e.getMessage());
					}
				}
			}
			// Direct java source file
			else if (fullFileName.endsWith(".java"))
			{
				// IF:
				// Package qualifier was defined or a part of (e.g.
				// apache.commons.Random -> apache/commons/Random)
				// AND
				// The file name ends with that qualifier -->
				// org.apache.commons.Random.java ends with
				// "apache/commons/Random.java)
				// --> CLASS FOUND!
				// OR
				// Package qualifier wasn't specified but just the class (e.g.
				// Random)
				// AND
				// The FILE NAME (note the call to file.getName() rather than
				// getAbsolutePath()) matches -->
				// org.apache.commons.Random.java's file name is Random.java
				// --> CLASS FOUND!
				if ((containsPackageQualifier && fullFileName
						.endsWith(classname + ".java"))
						|| (!containsPackageQualifier && fileName
								.equals(classname + ".java")))
				{
					try
					{
						findMethodFromJava(file.getName(),
								file.getAbsolutePath(), file);
					} catch (IOException e)
					{
						logger.logVerbose("Error reading file " + fullFileName
								+ ": " + e.getMessage());
						logger.logErr(e.getMessage());
					} catch (ConstantPoolException e)
					{
						logger.logVerbose("Error reading file from java "
								+ fullFileName + ": " + e.getMessage());
						logger.logErr(e.getMessage());
					} catch (InvalidDescriptor e)
					{
						logger.logVerbose("Error reading file from java "
								+ fullFileName + ": " + e.getMessage());
						logger.logErr(e.getMessage());
					}
				}
			}
			// The rest of the files: jar, war, ear, zip, rar
			else
			{
				JarFile jarFile;
				try
				{
					jarFile = new JarFile(file);
					Enumeration<JarEntry> entries = jarFile.entries();
					while (entries.hasMoreElements())
					{
						JarEntry entry = (JarEntry) entries.nextElement();
						String entryName = entry.getName();
						if (!matchCase)
						{
							entryName = entryName.toLowerCase();
						}

						if (containsPackageQualifier)
						{
							if (entryName.endsWith(classname + ".class")
									|| entryName.endsWith(classname + ".java"))
							{
								findMethod(file.getAbsolutePath(), jarFile,
										entry);
							}
						}
						// No package qualified, just Class Name
						else
						{
							// IF:
							// -- 95% scenario first: Class is in a sub package
							// of the jar file
							// The Class name ends with "/Classname.class" OR
							// "/Classname.java" (e.g.
							// org.apache.commons.Random.class ends with
							// "/Random.class")
							// OR
							// In the case that the class doesn't belong to any
							// package or it was just a ZIPPED file of a .class
							// The file name equals classname.class or
							// classname.java (e.g. Random.java got zipped up
							// into Random.zip. The only thing in there is
							// Random.java and as no package qualifier was
							// given, the class is found)
							if (entryName.endsWith("/" + classname + ".class")
									|| entryName.endsWith("/" + classname
											+ ".java")
									|| entryName.equals(classname + ".class")
									|| entryName.equals(classname + ".java"))
							{
								findMethod(file.getAbsolutePath(), jarFile,
										entry);
							}
						}
					}
				} catch (IOException e)
				{
					logger.logVerbose("Error reading file " + fullFileName
							+ ": " + e.getMessage());
					logger.logErr(e.getMessage());
				} catch (ConstantPoolException e)
				{
					logger.logVerbose("Error reading file from jar "
							+ fullFileName + ": " + e.getMessage());
					logger.logErr(e.getMessage());
				} catch (InvalidDescriptor e)
				{
					logger.logVerbose("Error reading file from jar "
							+ fullFileName + ": " + e.getMessage());
					logger.logErr(e.getMessage());
				}
			}
		}

		logger.logVerbose("Finished search");
	}

	/**
	 * find method in class file that class file is in jar file
	 * 
	 * @param entry
	 * @throws IOException
	 * @throws ConstantPoolException
	 * @throws InvalidDescriptor
	 */
	public void findMethod(String pathName, JarFile jarFile, JarEntry entry)
			throws IOException, ConstantPoolException, InvalidDescriptor
	{
		if (!parameters.getProperty(Parameters.matchMethodName).equals(""))
		{
			InputStream in = jarFile.getInputStream(entry);
			ClassFile classFile = (ClassFile) ClassFile.read(in);

			for(Method method:classFile.methods)
			{
				if (method.getName(classFile.constant_pool).equals(
						parameters.getProperty(Parameters.matchMethodName)))
				{
					logger.log(entry.getName(), pathName,
							writeMethod(classFile, method));
				}
			}
		}
		else
		{
			logger.log(entry.getName(), pathName);
		}
	}

	public void findMethod(String className, String pathName, File file)
			throws IOException, ConstantPoolException, InvalidDescriptor
	{
		if (!parameters.getProperty(Parameters.matchMethodName).equals(""))
		{
			ClassFile classFile = (ClassFile) ClassFile.read(file);

			for(Method method:classFile.methods)
			{
				if (method.getName(classFile.constant_pool).equals(
						parameters.getProperty(Parameters.matchMethodName)))
				{
					logger.log(file.getName(), file.getAbsolutePath(),
							writeMethod(classFile, method));
				}
			}
		}
		else
		{
			logger.log(file.getName(), file.getAbsolutePath());
		}
	}

	public void findMethodFromJava(String className, String pathName, File file)
			throws IOException, ConstantPoolException, InvalidDescriptor
	{

		if (!parameters.getProperty(Parameters.matchMethodName).equals(""))
		{
			if (astParser == null)
				astParser = ASTParser.newParser(AST.JLS4);

			BufferedInputStream bufferedInputStream;

			bufferedInputStream = new BufferedInputStream(new FileInputStream(
					file));
			byte[] input = new byte[bufferedInputStream.available()];
			bufferedInputStream.read(input);
			bufferedInputStream.close();
			astParser.setSource(new String(input).toCharArray());
			CompilationUnit result = (CompilationUnit) astParser
					.createAST(null);
			TypeDeclaration type = (TypeDeclaration) result.types().get(0);
			MethodDeclaration[] methodList = type.getMethods();
			for(MethodDeclaration methodDeclaration:methodList)
			{
				if (methodDeclaration
						.getName()
						.toString()
						.equals(parameters
								.getProperty(Parameters.matchMethodName)))
				{
					logger.log(file.getName(), file.getAbsolutePath(),
							writeMethod(methodDeclaration));
				}
			}

		}
		else
		{
			logger.log(file.getName(), file.getAbsolutePath());
		}
	}

	private String writeMethod(MethodDeclaration methodDeclaration)
	{
		StringBuffer strMethod = new StringBuffer();

		List modifires = methodDeclaration.modifiers();
		for(int i = 0;i < modifires.size();i++)
		{
			strMethod.append(modifires.get(i));
			strMethod.append(" ");
		}

		List typeParameters = methodDeclaration.typeParameters();
		if (typeParameters.size() > 0)
		{
			strMethod.append("<");
			for(int i = 0;i < typeParameters.size();i++)
			{
				if (i > 0)
					strMethod.append(",");
				strMethod.append(typeParameters.get(i));
			}
			strMethod.append(">");

			System.out.print(" ");
		}

		strMethod.append(methodDeclaration.getReturnType2() + " "
				+ methodDeclaration.getName());
		strMethod.append(" (");
		List parameter = methodDeclaration.parameters();
		for(int i = 0;i < parameter.size();i++)
		{
			if (i > 0)
				strMethod.append(",");
			strMethod.append(parameter.get(i));
		}

		strMethod.append(")");

		List thrownExceptions = methodDeclaration.thrownExceptions();
		if (thrownExceptions.size() > 0)
		{
			strMethod.append(" throws ");
			for(int i = 0;i < thrownExceptions.size();i++)
			{
				if (i > 0)
					strMethod.append(",");
				strMethod.append(thrownExceptions.get(i));
			}
		}
		return strMethod.toString();
	}

	protected String writeMethod(ClassFile classFile, Method m)
			throws ConstantPoolException, InvalidDescriptor
	{

		StringBuffer strMethod = new StringBuffer();
		ConstantPool constant_pool = classFile.constant_pool;
		AccessFlags flags = m.access_flags;

		Descriptor d;
		Type.MethodType methodType;
		List<? extends Type> methodExceptions;

		Signature_attribute sigAttr = (Signature_attribute) m.attributes
				.get(Attribute.Signature);
		if (sigAttr == null)
		{
			d = m.descriptor;
			methodType = null;
			methodExceptions = null;
		}
		else
		{
			Signature methodSig = sigAttr.getParsedSignature();
			d = methodSig;
			try
			{
				methodType = (Type.MethodType) methodSig.getType(constant_pool);
				methodExceptions = methodType.throwsTypes;
				if (methodExceptions != null && methodExceptions.isEmpty())
					methodExceptions = null;
			} catch (ConstantPoolException e)
			{
				// report error?
				// fall back on standard descriptor
				methodType = null;
				methodExceptions = null;
			}
		}

		for(Object item:flags.getMethodModifiers())
		{
			strMethod.append(item);
			strMethod.append(" ");
		}
		if (methodType != null)
		{
			strMethod.append("<");
			strMethod.append(methodType.typeParamTypes);
			strMethod.append(">");
		}
		if (m.getName(constant_pool).equals("<init>"))
		{
			strMethod.append(classFile.getName().replace("/", "."));
			strMethod.append(getJavaParameterTypes(d, flags, constant_pool));
		}
		else if (m.getName(constant_pool).equals("<clinit>"))
		{
			strMethod.append("{}");
		}
		else
		{
			strMethod.append(d.getReturnType(constant_pool).replace("/", "."));
			strMethod.append(" ");
			strMethod.append(m.getName(constant_pool));
			strMethod.append(getJavaParameterTypes(d, flags, constant_pool));
		}

		Attribute e_attr = m.attributes.get(Attribute.Exceptions);
		if (e_attr != null)
		{ // if there are generic exceptions, there must be
			// erased exceptions
			if (e_attr instanceof Exceptions_attribute)
			{
				Exceptions_attribute exceptions = (Exceptions_attribute) e_attr;
				strMethod.append(" throws ");
				if (methodExceptions != null)
				{ // use generic list if available

					String sep = "";
					for(Object item:methodExceptions)
					{
						strMethod.append(sep);
						strMethod.append(item);
						sep = ", ";
					}
				}
				else
				{
					for(int i = 0;i < exceptions.number_of_exceptions;i++)
					{
						if (i > 0)
							strMethod.append(", ");
						strMethod.append(exceptions.getException(i,
								constant_pool).replace("/", "."));
					}
				}
			}
		}

		strMethod.append(";");

		return strMethod.toString();
	}

	String getJavaParameterTypes(Descriptor d, AccessFlags flags,
			ConstantPool constant_pool)
	{
		try
		{
			return adjustVarargs(flags, d.getParameterTypes(constant_pool))
					.replace('/', '.');
		} catch (ConstantPoolException e)
		{
			logger.logVerbose("Error reading file from jar: " + e.getMessage());
			logger.logErr(e.getMessage());
			return "";
		} catch (DescriptorException e)
		{
			logger.logVerbose("Error reading file from jar: " + e.getMessage());
			logger.logErr(e.getMessage());
			return "";
		}
	}

	String adjustVarargs(AccessFlags flags, String params)
	{
		int i = params.lastIndexOf("[]");
		if (i > 0)
			return params.substring(0, i) + "..." + params.substring(i + 2);
		return params;
	}

	/**
	 * build file list from a string that split by ";"
	 * 
	 * @param directorys
	 * @param recursive
	 */
	public void buildFileList(String directorys, boolean recursive)
	{
		String[] directoryList = directorys.split(";");
		for(String directory:directoryList)
		{
			buildFileList(new File(directory), recursive);
		}
	}

	public void buildFileList(File directory, boolean recursive)
	{
		if (!directory.exists())
		{
			logger.log("Directory \"" + directory.getAbsolutePath()
					+ "\" does not exist!");
		}
		// File is directly passed on, no directory search necessary
		else if (!directory.isDirectory()
				&& new SearchableFileFilter().accept(directory))
		{
			files.add(directory);
		}
		else
		{
			for(File file:directory.listFiles(new SearchableFileFilter()))
			{
				// Build recursive tree if recursive flag is set
				if (file.isDirectory())
				{
					if (recursive)
					{
						buildFileList(file, true);
					}
				}
				else
				{
					files.add(file);
				}
			}
		}
	}
}
