/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package org.jboss.tools.rsp.api.schema;

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import cz.habarta.typescript.generator.Input;
import cz.habarta.typescript.generator.JsonLibrary;
import cz.habarta.typescript.generator.Output;
import cz.habarta.typescript.generator.Settings;
import cz.habarta.typescript.generator.TypeScriptGenerator;
import cz.habarta.typescript.generator.TypeScriptOutputKind;

public class TypescriptUtility {

	private static final String PROTOCOL_TYPE_FILE = "protocol.unified.d.ts";
	private static final String TS_TYPE_FILE_SUFFIX = ".d.ts";

	private String baseDir;

	public TypescriptUtility(String baseDir) {
		this.baseDir = baseDir;
	}

	public void writeTypescriptSchemas(Class<?>[] daoClasses) throws IOException {
		if( daoClasses == null || daoClasses.length == 0 ) {
			// TODO error somehow? 
			return;
		}
		
		URLClassLoader cl = (URLClassLoader) daoClasses[0].getClassLoader();
		
		File daoFolder = getDaoTypescriptFolder().toFile();
		if (!daoFolder.exists()) {
			daoFolder.mkdirs();
		}

		final Settings settings = new Settings();
		settings.outputKind = TypeScriptOutputKind.module;
		settings.jsonLibrary = JsonLibrary.jackson2;
		String[] clazNames = new String[daoClasses.length];
		for (int i = 0; i < daoClasses.length; i++) {
			writeTypescriptType(daoClasses[i], settings);
			clazNames[i] = daoClasses[i].getName();
		}		

		File output = getUnifiedSchemaFile();
		new TypeScriptGenerator(settings).generateTypeScript(
				Input.fromClassNamesAndJaxrsApplication(
						Arrays.asList(clazNames), null, null, false, null, cl, true), 
				Output.to(output));
	}
	
	public File getUnifiedSchemaFile() {
		return getDaoTypescriptFolder().resolve(PROTOCOL_TYPE_FILE).toFile();
	}

	private void writeTypescriptType(Class<?> clazz, final Settings settings) throws IOException {
		Path p = getDaoTypescriptFile(clazz.getSimpleName());
		File output = p.toFile();
		List<String> classes = Arrays.asList(clazz.getName());

		new TypeScriptGenerator(settings).generateTypeScript(
				Input.fromClassNamesAndJaxrsApplication(
						classes, null, null, false, null, (URLClassLoader) clazz.getClassLoader(), true),
				Output.to(output));
		
		// It loads the files with stupid autogenerated garbage
		String trimmed = SchemaIOUtil.trimFirstLines(SchemaIOUtil.safeReadFile(p), 3);
		Files.write(p, trimmed.getBytes());
	}

	public Path getDaoTypescriptFile(String simpleClassName) {
		return getDaoTypescriptFolder().resolve(simpleClassName + TS_TYPE_FILE_SUFFIX);
	}

	public Path getDaoTypescriptFolder() {
		return new File(baseDir).toPath()
				.resolve("src").resolve("main").resolve("resources").resolve("schema").resolve("typescript");
	}

	public void generateTypescriptClient(String dir) {
		generateProtocolTs(dir);
		generateMessageTs(dir);
		generateIncomingTs(dir);
		generateOutgoingTs(dir);
	}
	
	private void generateProtocolTs(String dir) {
		File existing = getUnifiedSchemaFile();
		File destination = new File(dir).toPath().resolve("src").resolve("protocol")
				.resolve("generated").resolve("protocol.ts").toFile();
		String contents = SchemaIOUtil.readFile(existing);
		String header = 
				"/**\n" + 
				" * Json objects sent between the server and the client\n" + 
				" */\n" + 
				"export namespace Protocol {\n";
		String footer = emptyFooter();
		
		String total = header + SchemaIOUtil.linePrefix(contents, "    ") + footer;
		try {
			Files.write(destination.toPath(), total.getBytes());
		} catch(IOException ioe) {
			
		}
	}
	
	private void generateMessageTs(String dir) {
		File destination = new File(dir).toPath().resolve("src").resolve("protocol")
				.resolve("generated").resolve("messages.ts").toFile();
		String fileContents = messageTsHeader() + messageTsServer() + messageTsClient() + emptyFooter();
		try {
			Files.write(destination.toPath(), fileContents.getBytes());
		} catch(IOException ioe) {
			
		}
	}

	private void generateIncomingTs(String dir) {
		File destination = new File(dir).toPath().resolve("src").resolve("protocol")
				.resolve("generated").resolve("incoming.ts").toFile();
		String fileContents = incomingTsHeader() + incomingTsClient() + emptyFooter();
		try {
			Files.write(destination.toPath(), fileContents.getBytes());
		} catch(IOException ioe) {
			
		}
	}

	private void generateOutgoingTs(String dir) {
		File destination = new File(dir).toPath().resolve("src").resolve("protocol")
				.resolve("generated").resolve("outgoing.ts").toFile();
		String fileContents = outgoingTsHeader() + outgoingTsServer() + outgoingTsFooter() + outgoingTsErrors();
		try {
			Files.write(destination.toPath(), fileContents.getBytes());
		} catch(IOException ioe) {
			
		}
	}
	
	private String outgoingTsErrors() {
		StringBuffer sb = new StringBuffer();
		sb.append("\n" + 
				"/**\n" + 
				" * Error messages\n" + 
				" */\n" + 
				"export namespace ErrorMessages {\n");
		List<String> mNames = null;
		try {
			mNames = getMethodNames(getServerInterfaceFile());
		} catch(IOException ioe) {
			throw new RuntimeException(ioe);
		}
		
		for( String n : mNames ) {
			sb.append("    ");
			sb.append("export const ");
			sb.append(methodNameToTimeoutErrorName(n));
			sb.append(" = 'Failed to ");
			sb.append(camelCaseToSpaces(n));
			sb.append(" in time';\n");
		}
		sb.append(emptyFooter());
		return sb.toString();
	}

	private String camelCaseToSpaces(String n) {
		StringBuffer sb = new StringBuffer();
		for( int i = 0; i < n.length(); i++ ) {
			if(Character.isUpperCase(n.charAt(i))) {
				sb.append(" ");
				sb.append(Character.toLowerCase(n.charAt(i)));
			} else {
				sb.append(n.charAt(i));
			}
		}
		return sb.toString();
	}
	
	private String outgoingTsHeader() {
		return  "import { Protocol } from './protocol';\n" + 
				"import { Messages } from './messages';\n" + 
				"import { Common } from '../../util/common';\n" + 
				"import { MessageConnection } from 'vscode-jsonrpc';\n" + 
				"\n" + 
				"/**\n" + 
				" * Server Outgoing\n" + 
				" */\n" + 
				"export class Outgoing {\n" + 
				"\n" + 
				"    private connection: MessageConnection;\n" + 
				"\n" + 
				"     /**\n" + 
				"     * Constructs a new discovery handler\n" + 
				"     * @param connection message connection to the RSP\n" + 
				"     */\n" + 
				"    constructor(connection: MessageConnection) {\n" + 
				"        this.connection = connection;\n" + 
				"    }\n";
	}
	
	private String outgoingTsFooter() {
		return "}";
	}
	
	private String incomingTsHeader() {
		return "import { Protocol } from './protocol';\n" + 
				"import { Messages } from './messages';\n" + 
				"import { MessageConnection } from 'vscode-jsonrpc';\n" + 
				"import { EventEmitter } from 'events';\n" + 
				"\n" + 
				"/**\n" + 
				" * Server incoming\n" + 
				" */\n" + 
				"export class Incoming {\n" + 
				"\n" + 
				"    private connection: MessageConnection;\n" + 
				"    private emitter: EventEmitter;\n" + 
				"\n" + 
				"    /**\n" + 
				"     * Constructs a new discovery handler\n" + 
				"     * @param connection message connection to the RSP\n" + 
				"     * @param emitter event emitter to handle notification events\n" + 
				"     */\n" + 
				"    constructor(connection: MessageConnection, emitter: EventEmitter) {\n" + 
				"        this.connection = connection;\n" + 
				"        this.emitter = emitter;\n" + 
				"        this.listen();\n" + 
				"    }\n" + 
				"";
	}


	private String incomingTsListen() {
		String header = "    /**\n" + 
				"     * Subscribes to notifications sent by the server\n" + 
				"     */\n" + 
				"    private listen() {\n";
		String footer = "    " + emptyFooter();
		
		StringBuffer sb = new StringBuffer();
		try {
			Map<String, JavadocComment> map = JavadocUtilities.methodToJavadocMap(getClientInterfaceFile());
			List<String> names = new ArrayList<>(map.keySet());
			String[] methods = names.toArray(new String[names.size()]);
			for( int i = 0; i < methods.length; i++ ) {
				JavadocComment jdc = map.get(methods[i]);
				MethodDeclaration md = getMethodDeclaration(jdc);
				if( JavadocUtilities.isNotification(md) ) {
					
					String methodName = md.getNameAsString();
					String notificationName = methodNameToNotificationName(methodName);
					
					sb.append("        this.connection.onNotification(Messages.Client." + notificationName + ".type, param => {\n");
					sb.append("            this.emitter.emit('" + methodName + "', param);\n");
					sb.append("        });\n");
				} else {
					// TOD? idk
				}
			}
		} catch(IOException ioe) {
			throw new RuntimeException(ioe);
		}

		return header + sb.toString() + footer;
	}
	
	private List<String> getMethodNames(File file) throws IOException {
		Map<String, JavadocComment> map = JavadocUtilities.methodToJavadocMap(getServerInterfaceFile());
		List<String> names = new ArrayList<>(map.keySet());
		return names;
	}
	
	private String outgoingTsServer() {
		StringBuffer sb = new StringBuffer();
		try {
			Map<String, JavadocComment> map = JavadocUtilities.methodToJavadocMap(getServerInterfaceFile());
			List<String> names = new ArrayList<>(map.keySet());
			String[] methods = names.toArray(new String[names.size()]);
			for( int i = 0; i < methods.length; i++ ) {
				JavadocComment jdc = map.get(methods[i]);
				MethodDeclaration md = getMethodDeclaration(jdc);

				String methodName = md.getNameAsString();
				int paramCount = md.getParameters().size();
				String paramType = paramCount > 0 ? convertReturnType(md.getParameter(0).getType().toString()) : null;
				Type retType = md.getType();
				String retTypeName = convertReturnType(retType.toString());
				
				boolean isNotification = JavadocUtilities.isNotification(md);

				String standardParams = paramType == null ? "" : "param: " + paramType;
				String timeoutParams = (standardParams.isEmpty() ? "" : ", ") + "timeout: number = Common.LONG_TIMEOUT";
				String functionDecLine = "    " + methodName + "(" + standardParams + timeoutParams + "): ";
				if( retTypeName.equals("void")) {
					functionDecLine += retTypeName + " {\n";
				} else {
					functionDecLine += "Promise<" + retTypeName + "> {\n";
				}
				String functionFooter = "    }\n";
				
				String functionBody = null;
				if( isNotification ) {
					functionBody = "        return Common.sendSimpleNotification(this.connection, Messages.Server.";
					functionBody += methodNameToNotificationName(methodName);
					functionBody += ".type, ";
					functionBody += (paramType == null ? "null" : "param");
					functionBody += ");\n";
				} else {
					functionBody = "        return Common.sendSimpleRequest(this.connection, Messages.Server.";
					functionBody += methodNameToRequestName(methodName); 
					functionBody += ".type,\n            ";
					functionBody += (paramType == null ? "null" : "param") + ", timeout, ErrorMessages.";
					functionBody += methodName.toUpperCase() + "_TIMEOUT);\n";
				}
				
				
				sb.append(functionDecLine);
				sb.append(functionBody);
				sb.append(functionFooter);
			}
		} catch(IOException ioe) {
			throw new RuntimeException(ioe);
		}
		
		return sb.toString();
	}
	
	private String methodNameToTimeoutErrorName(String name) {
		return name.toUpperCase() + "_TIMEOUT";
	}
	private String incomingTsRegisterListeners() {
		StringBuffer sb = new StringBuffer();
		try {
			Map<String, JavadocComment> map = JavadocUtilities.methodToJavadocMap(getClientInterfaceFile());
			List<String> names = new ArrayList<>(map.keySet());
			String[] methods = names.toArray(new String[names.size()]);
			for( int i = 0; i < methods.length; i++ ) {
				JavadocComment jdc = map.get(methods[i]);
				MethodDeclaration md = getMethodDeclaration(jdc);

				String methodName = md.getNameAsString();
				String capName = capFirstLetter(methodName);
				int paramCount = md.getParameters().size();
				String paramType = paramCount > 0 ? convertReturnType(md.getParameter(0).getType().toString()) : null;
				Type retType = md.getType();
				String retTypeName = convertReturnType(retType.toString());

				if( JavadocUtilities.isNotification(md) ) {
					sb.append("    on" + capName + "(listener: (arg: " + paramType + ") => " + retTypeName + "): void {\n");
					sb.append("        this.emitter.on('" + methodName + "', listener);\n");
					sb.append("    }\n");
				} else {
					String requestName = methodNameToRequestName(methodName);
					sb.append("    on" + capName + "(listener: (arg: " + paramType + ") => Promise<" + retTypeName + ">): void {\n");
					sb.append("        this.connection.onRequest(Messages.Client." + requestName + ".type, listener);\n");
					sb.append("    }\n");
				}
			}
		} catch(IOException ioe) {
			throw new RuntimeException(ioe);
		}
		
		return sb.toString();
	}
	
	private String incomingTsClient() {
		return incomingTsListen() + "\n" + incomingTsRegisterListeners();
	}
	
	private String messageTsHeader() {
		return "import { NotificationType, RequestType } from 'vscode-jsonrpc';\n" + 
				"import { Protocol } from './protocol';\n" + 
				"\n" + 
				"/**\n" + 
				" * Message types sent between the RSP server and the client\n" + 
				" */\n" + 
				"export namespace Messages {\n";
	}
	
	private String messageTsServer() {
		String header = "\n" + 
				"    /**\n" + 
				"     * Server methods\n" + 
				"     */\n" + 
				"    export namespace Server {\n\n";
		String footer = "    }\n";
		
		StringBuffer sb = new StringBuffer();
		try {
			Map<String, JavadocComment> map = JavadocUtilities.methodToJavadocMap(getServerInterfaceFile());
			List<String> names = new ArrayList<>(map.keySet());
			String[] methods = names.toArray(new String[names.size()]);
			
			for( int i = 0; i < methods.length; i++ ) {
				JavadocComment jdc = map.get(methods[i]);
				if( JavadocUtilities.isNotification(getMethodDeclaration(jdc)) ) {
					printOneNotification(methods[i], jdc, sb, "server");
				} else {
					printOneRequest(methods[i], jdc, sb, "server");
				}
			}
		} catch(IOException ioe) {
			throw new RuntimeException(ioe);
		}
		
		return header + sb.toString() + footer;
	}

	private String messageTsClient() {
		StringBuffer sb = new StringBuffer();
		sb.append("    /**\n" + 
				"     * Client methods\n" + 
				"     */\n" + 
				"    export namespace Client {\n");
		
		
		try {
			Map<String, JavadocComment> map = JavadocUtilities.methodToJavadocMap(getClientInterfaceFile());
			List<String> names = new ArrayList<>(map.keySet());
			String[] methods = names.toArray(new String[names.size()]);
			for( int i = 0; i < methods.length; i++ ) {
				JavadocComment jdc = map.get(methods[i]);
				if( JavadocUtilities.isNotification(getMethodDeclaration(jdc)) ) {
					printOneNotification(methods[i], jdc, sb, "client");
				} else {
					printOneRequest(methods[i], jdc, sb, "client");
				}
			}
		} catch(IOException ioe) {
			throw new RuntimeException(ioe);
		}

		
		
		sb.append("    }\n");
		return sb.toString();
	}


	
	private void printOneRequest(String methodName, JavadocComment jdc, 
			StringBuffer sb, String serverOrClient) {
		if( jdc != null ) {
			String comment = jdc.getContent().substring(1);
			sb.append("        /**\n");
			sb.append(comment.replaceAll("\t", "        "));
			sb.append("*/");
			sb.append("\n        export namespace ");
			sb.append(methodNameToRequestName(methodName));
			sb.append(" {\n");
			
			// TODO body
			MethodDeclaration md = getMethodDeclaration(jdc);
			sb.append("            export const type = new RequestType<");
			NodeList<Parameter> params = md.getParameters();
			if( params.size() == 0 ) {
				sb.append("void, ");
			} else {
				Type type = params.get(0).getType();
				String typeName = type.toString();
				if( typeName.equalsIgnoreCase("void")) 
					sb.append("void, ");
				else 
					sb.append("Protocol." + typeName + ", ");

			}
			
			Type retType = md.getType();
			String retTypeName = convertReturnType(retType.toString());
			sb.append(retTypeName);
			sb.append(", void, void>('" + serverOrClient + "/" + methodName + "');");
			sb.append("\n");
			sb.append("        }\n");
		}
	}

	private String methodNameToNotificationName(String methodName) {
		return capFirstLetter(methodName) + "Notification";	
	}
	
	private String methodNameToRequestName(String methodName) {
		return capFirstLetter(methodName) + "Request";
	}
	
	private String capFirstLetter(String s) {
		return s.substring(0, 1).toUpperCase() + s.substring(1);
	}
	
	private void printOneNotification(String methodName, JavadocComment jdc, 
			StringBuffer sb, String serverOrClient) {
		if( jdc != null ) {
			String comment = jdc.getContent().substring(1);
			sb.append("        /**\n");
			sb.append(comment.replaceAll("\t", "        "));
			sb.append("*/");
			sb.append("\n        export namespace ");
			sb.append(methodNameToNotificationName(methodName));
			sb.append(" {\n");
			
			// TODO body
			MethodDeclaration md = getMethodDeclaration(jdc);
			sb.append("            export const type = new NotificationType<");
			NodeList<Parameter> params = md.getParameters();
			if( params.size() == 0 ) {
				sb.append("void, ");
			} else {
				Type type = params.get(0).getType();
				String typeName = type.toString();
				if( typeName.equalsIgnoreCase("void")) 
					sb.append("void, ");
				else 
					sb.append("Protocol." + typeName + ", ");
			}
			
			Type retType = md.getType();
			String retTypeName = convertReturnType(retType.toString());
			sb.append(retTypeName);
			sb.append(">('" + serverOrClient + "/" + methodName + "');");
			sb.append("\n");
			sb.append("        }\n");
		}
	}

	private String convertReturnType(String type) {
		if( type.startsWith("CompletableFuture<") && type.endsWith(">")) {
			type = type.substring("CompletableFuture<".length());
			type = type.substring(0, type.length()-1);
		}
		if( type.startsWith("List<")) {
			type = "Array<Protocol." + type.substring("List<".length());
		} else {
			if( type.equals("String"))
				type = "string";
			else if( !type.equalsIgnoreCase("void")) 
				type = "Protocol." + type;
			
		}
		return type;
	}

	private MethodDeclaration getMethodDeclaration(JavadocComment comment) {
		Optional<Node> o = comment.getCommentedNode();
		if (o.get() != null) {
			if (!(o.get() instanceof CompilationUnit)) {
				Node n = o.get();
				if (n instanceof MethodDeclaration) {
					return (MethodDeclaration) n;
				}
			}
		}
		return null;
	}
	
	private String emptyFooter() {
		return "}\n";
	}
	
	private File getClientInterfaceFile() throws IOException {
		File f2 = new File(baseDir);
		File f = new File(f2, "../../bundles/org.jboss.tools.rsp.api/src/main/java/org/jboss/tools/rsp/api/RSPClient.java").getCanonicalFile();
		return f;
	}

	private File getServerInterfaceFile() throws IOException {
		File f2 = new File(baseDir);
		File f = new File(f2, "../../bundles/org.jboss.tools.rsp.api/src/main/java/org/jboss/tools/rsp/api/RSPServer.java").getCanonicalFile();
		return f;
	}

}
