/*
 *
 * Copyright (c) 2009-2012,
 *
 *  Steve Suh           <suhsteve@gmail.com>
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. The names of the contributors may not be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 *
 */

package util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.StringTokenizer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import spec.AndroidSpecs;
import spec.MethodNamePattern;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.strings.StringStuff;


public class EntryPoints {
	private static final Logger logger = LoggerFactory.getLogger(EntryPoints.class);
	
    private String pathToApkFile;
    private String pathToApkTool;
    private String pathToJava;
    private String tempFolder;
    private ArrayList<String[]> ActivityIntentList;
    private ArrayList<String[]> ReceiverIntentList;
    private ArrayList<String[]> ServiceIntentList;

    private LinkedList<Entrypoint> entries;

    public EntryPoints(String classpath, ClassHierarchy cha, AndroidAnalysisContext loader) {
        tempFolder = "temp";
        ActivityIntentList = new ArrayList<String[]>();
        ReceiverIntentList = new ArrayList<String[]>();
        ServiceIntentList = new ArrayList<String[]>();
        entries = new LinkedList<Entrypoint>();


//        StringTokenizer st = new StringTokenizer(classpath, File.pathSeparator);
//        String filename = st.nextToken();
//      if (filename.endsWith(".apk"))
//      {
//          unpackApk(classpath);
//          readXMLFile();
//          populateEntryPoints(cha);
//          outputIntentList();
//          ListenerEntryPoints(cha,loader);
//      }
//      else
//          defaultEntryPoints(cha, loader);

        if(CLI.hasOption("thread-run-main")) {
            systemEntry(cha, loader);
        } else if (CLI.hasOption("main-entrypoint")) {
        	Iterable<Entrypoint> mainEntrypoints = Util.makeMainEntrypoints(cha.getScope(), cha);
        	//add main entry point -- however usually used for test suites?  android don't have mains
        	for (Entrypoint entry: mainEntrypoints) {
        		entries.add(entry);
        	}
        }  else {
            defaultEntryPoints(cha, loader);
        }

//      activityModelEntry(cha,loader);
//      addTestEntry(cha,loader);


    }

    public void listenerEntryPoints(ClassHierarchy cha, AndroidAnalysisContext loader) {
        ArrayList<MethodReference> entryPointMRs = new ArrayList<MethodReference>();

        // onLocation
        entryPointMRs.add(StringStuff.makeMethodReference("android.location.LocationListener.onLocationChanged(Landroid/location/Location;)V"));
        for(MethodReference mr:entryPointMRs)
        for(IMethod im:cha.getPossibleTargets(mr))
        {
            logger.debug("Considering target "+im.getSignature());

            // limit to functions defined within the application
            if(im.getReference().getDeclaringClass().getClassLoader().
                                equals(ClassLoaderReference.Application)) {
                logger.debug("Adding entry point: "+im.getSignature());
                entries.add(new DefaultEntrypoint(im, cha));
            }
        }
    }

    public void defaultEntryPoints(ClassHierarchy cha, AndroidAnalysisContext loader) {
    	for (MethodNamePattern mnp:new AndroidSpecs().getEntrypointSpecs()) {
    		for (IMethod im: mnp.getPossibleTargets(cha)) {
    			logger.debug("Considering target "+im.getSignature());
    			// limit to functions defined within the application
    			if(LoaderUtils.fromLoader(im, ClassLoaderReference.Application))
    			{
    				logger.debug("Adding entry point: "+im.getSignature());
    				entries.add(new DefaultEntrypoint(im, cha));
    			}
    		}
    	}
    }
    
    public void activityModelEntry(ClassHierarchy cha, AndroidAnalysisContext loader) {
        ArrayList<MethodReference> entryPointMRs =
                new ArrayList<MethodReference>();

        String[] methodReferences = {
            "android.app.Activity.ActivityModel()V",
            // find all onActivityResult functions and add them as entry points
//            "android.app.Activity.onActivityResult(IILandroid/content/Intent;)V",
//
//            // SERVICE ENTRY POINTS
//            "android.app.Service.onCreate()V",
//            "android.app.Service.onStart(Landroid/content/Intent;I)V",
//            "android.app.Service.onBind(Landroid/content/Intent;)Landroid/os/IBinder;",
//            "android.app.Service.onTransact(ILandroid/os/Parcel;Landroid/os/Parcel;I)B"
         };

        for (int i = 0; i < methodReferences.length; i++) {
            MethodReference mr =
                    StringStuff.makeMethodReference(methodReferences[i]);
            
            for (IMethod im : cha.getPossibleTargets(mr)) {
                logger.debug("Considering target " + im.getSignature());

                // limit to functions defined within the application
                if (im.getReference().getDeclaringClass().getClassLoader()
                        .equals(ClassLoaderReference.Application)) {
                    logger.debug("Adding entry point: " + im.getSignature());
                    entries.add(new DefaultEntrypoint(im, cha));
                }
            }
        }
    }
    
    private void systemEntry(ClassHierarchy cha, AndroidAnalysisContext loader) {
        String[] systemEntyPoints = { 
//              "android.app.ActivityThread.main([Ljava/lang/String;)V"
//              , "com.android.server.ServerThread.run()V"
           	//"android.location.LocationManager$ListenerTransport._handleMessage(Landroid/os/Message;)V"
//           		"android.location.LocationManager$ListenerTransport$1.handleMessage(Landroid/os/Message;)V"
//           		"android.os.Handler.handleMessage(Landroid/os/Message;)V",
//           		"android.os.Handler$Callback.handleMessage(Landroid/os/Message;)Z",
//           		"com.android.internal.os.HandlerCaller$Callback.executeMessage(Landroid/os/Message;)V"
//           		"android.os.Handler.dispatchMessage(Landroid/os/Message;)V",
//           		"android.view.View.dispatchTouchEvent(Landroid/view/MotionEvent;)Z",
//           		"android.view.View.onTouchEvent(Landroid/view/MotionEvent;)Z",
//           		"android.view.View.setOnClickListener(Landroid/view/View$OnClickListener;)V",
           		"com.android.server.ServerThread.run()V"
           		//"android.app.ActivityThread.main([Ljava/lang/String;)V"
           };

           for (int i = 0; i < systemEntyPoints.length; i++) {
               MethodReference methodRef =
                       StringStuff.makeMethodReference(systemEntyPoints[i]);

               for (IMethod im : cha.getPossibleTargets(methodRef)) {
                   logger.debug("Adding entry point: " + im.getSignature());
                   entries.add(new DefaultEntrypoint(im, cha));
               }
           }
    }
    
    
    public void addTestEntry(ClassHierarchy cha, AndroidAnalysisContext loader) {
    	ArrayList<MethodReference> entryPointMRs =
    			new ArrayList<MethodReference>();

    	String[] methodReferences = {
//    			"Test.Apps.Outer$PrivateInnerClass.printNum()V",
    			//"Test.Apps.Outer$PublicInnerClass.printNum()V"
    			//"Test.Apps.Outer.<init>()V"
    			//"Test.Apps.Outer.getNum()I"
    			//"Test.Apps.FixpointSolver.someMethod(LTest/Apps/GenericSink;LTest/Apps/GenericSource;)V"
    			//"Test.Apps.Outer$PrivateInnerClass.testParameters(LTest/Apps/GenericSink;LTest/Apps/GenericSource;)V"
    			"android.view.View.setOnClickListener(Landroid/view/View$OnClickListener;)V",
    	};

    	for (int i = 0; i < methodReferences.length; i++) {
    		MethodReference mr =
    				StringStuff.makeMethodReference(methodReferences[i]);

    		for (IMethod im : cha.getPossibleTargets(mr)) {
    			logger.debug("Adding entry point: " + im.getSignature());
    			entries.add(new DefaultEntrypoint(im, cha));
    		}
    	}
    }
    

    public void unpackApk(String classpath){
        StringTokenizer st = new StringTokenizer(classpath, File.pathSeparator);
        pathToApkFile = st.nextToken();
        //String pathToApkTool = new String(System.getProperty("user.dir").replace(" ", "\\ ") + File.separator + "apktool" +File.separator);
        pathToApkTool = System.getProperty("user.dir") + File.separator + "apktool" +File.separator;
        //String pathToJava = new String(System.getProperty("java.home").replace(" ", "\\ ") + File.separator + "bin" + File.separator);
        pathToJava = System.getProperty("java.home") + File.separator + "bin" + File.separator;
        String s = null;

        //String command = new String(pathToJava + "java -jar " + pathToApkTool + "apktool.jar d -f " + pathToApkFile + " " + pathToApkTool + tempFolder);

        //System.out.println("command: " + command);

        ProcessBuilder pb = new ProcessBuilder(pathToJava + "java", "-jar", pathToApkTool + "apktool.jar", "d", "-f", pathToApkFile, pathToApkTool+tempFolder);


        try {
            //Process p = Runtime.getRuntime().exec(command);
            Process p = pb.start();
            BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(p.getInputStream()));

               BufferedReader stdError = new BufferedReader(new
                    InputStreamReader(p.getErrorStream()));

            // read the output from the command
            logger.debug("Here is the standard output of the command:\n");
            while ((s = stdInput.readLine()) != null) {
                logger.debug(s);
            }

            // read any errors from the attempted command
            logger.debug("Here is the standard error of the command (if any):\n");
            while ((s = stdError.readLine()) != null) {
                logger.debug(s);
            }

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        //System.out.println( System.getProperty("user.dir") );
        //System.out.println("classpath: " + st.nextToken());
    }

    public void readXMLFile() {
        try {

            File fXmlFile = new File(pathToApkTool + tempFolder + File.separator + "AndroidManifest.xml");
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(fXmlFile);
            doc.getDocumentElement().normalize();

            //System.out.println("Root element :" + doc.getDocumentElement().getNodeName());

            String basePackage = doc.getDocumentElement().getAttribute("package");
            NodeList iList = doc.getElementsByTagName("intent-filter");
            System.out.println("-----------------------");


            for (int i = 0; i < iList.getLength(); i++) {
                Node nNode = iList.item(i);

                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                      Element eElement = (Element) nNode;
//                    System.out.println(eElement.getNodeName());
                      populateIntentList(basePackage, eElement);
                }

            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getTagValue(String sTag, Element eElement) {
        NodeList nlList = eElement.getElementsByTagName(sTag).item(0).getChildNodes();

        Node nValue = (Node) nlList.item(0);

        return nValue.getNodeValue();
    }

    private void populateIntentList(String basePackage, Element eElement) {
        ArrayList<String[]> IntentList;
        NodeList actionList = eElement.getElementsByTagName("action");
        Node parent = eElement.getParentNode();
        IntentList = chooseIntentList(parent.getNodeName());

        String IntentClass = parent.getAttributes().getNamedItem("android:name").getTextContent();

        for (int i = 0; i < actionList.getLength(); i++)
        {
            Node nNode = actionList.item(i);
            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                IntentList.add(new String[2]);
                IntentList.get(IntentList.size()-1)[0] = actionList.item(i).getAttributes().getNamedItem("android:name").getTextContent();

                if (IntentClass.startsWith(basePackage))
                    IntentList.get(IntentList.size()-1)[1] = IntentClass;
                else {
                    if (IntentClass.startsWith("."))
                        IntentList.get(IntentList.size()-1)[1] = basePackage + IntentClass;
                    else {
                        IntentList.get(IntentList.size()-1)[1] = basePackage + "." + IntentClass;
                        IntentList.add(new String[2]);
                        IntentList.get(IntentList.size()-1)[0] = actionList.item(i).getAttributes().getNamedItem("android:name").getTextContent();
                        IntentList.get(IntentList.size()-1)[1] = IntentClass;
                    }

                    //IntentList.get(IntentList.size()-1)[1] = basePackage + (IntentClass.startsWith(".") ? IntentClass : "." + IntentClass);
                }

                //System.out.println(IntentList.get(IntentList.size()-1)[0] + " ~> " + IntentList.get(IntentList.size()-1)[1]);
            }
        }
    }

    private void populateEntryPoints(ClassHierarchy cha) {
        String method = null;
        IMethod im = null;
        for (String[] intent: ActivityIntentList) {
            //method = IntentToMethod(intent[0]);
            method = "onCreate(Landroid/os/Bundle;)V";
            logger.debug("activity intent method: "+intent[1]+"."+method);
            if (method != null)
                im = cha.resolveMethod(StringStuff.makeMethodReference(intent[1]+"."+method));
            if (im!=null)
                entries.add(new DefaultEntrypoint(im,cha));

        }
        for (String[] intent: ReceiverIntentList) {
            //Seems that every broadcast receiver can be an entrypoints?
//          method = IntentToMethod(intent[0]);
            method = "onReceive(Landroid/content/Context;Landroid/content/Intent;)V";
            logger.debug("receiver intent method: "+intent[1]+"."+method);
            if (method != null)
                im = cha.resolveMethod(StringStuff.makeMethodReference(intent[1]+"."+method));
            if (im!=null)
                entries.add(new DefaultEntrypoint(im,cha));
        }
        //IMethod im = cha.resolveMethod(StringStuff.makeMethodReference("android.app.Activity.onCreate(Landroid/os/Bundle;)V"));
        //entries.add(new DefaultEntrypoint(im, cha));
    }

    private String IntentToMethod(String intent) {
        if (intent.contentEquals("android.intent.action.MAIN") ||
                intent.contentEquals("android.media.action.IMAGE_CAPTURE") ||
                intent.contentEquals("android.media.action.VIDEO_CAPTURE") ||
                intent.contentEquals("android.media.action.STILL_IMAGE_CAMERA") ||
                intent.contentEquals("android.intent.action.MUSIC_PLAYER") ||
                intent.contentEquals("android.media.action.VIDEO_CAMERA"))
            return "onCreate(Landroid/os/Bundle;)V";

//      else if (intent.contentEquals("android.intent.action.BOOT_COMPLETED") ||
//              intent.contentEquals("android.appwidget.action.APPWIDGET_UPDATE") ||
//              intent.contentEquals("android.provider.Telephony.SECRET_CODE") )
//          return "onReceive(Landroid/content/Context;Landroid/content/Intent;)V";


        else return null;
    }

    private ArrayList<String[]> chooseIntentList(String name) {
        if (name.equals("activity"))
            return ActivityIntentList;
        else if (name.equals("receiver"))
            return ReceiverIntentList;
        else if (name.equals("service"))
            return ServiceIntentList;
        else {
            return ActivityIntentList;
//          throw new UnimplementedError("EntryPoints intent category not yet covered: " + name);
        }
    }

    private void outputIntentList() {
        if (ActivityIntentList != null)
        for (int i = 0; i < ActivityIntentList.size(); i++)
            logger.debug("Activity Intent: " + ActivityIntentList.get(i)[0] + " ~> " + ActivityIntentList.get(i)[1]);
        if (ReceiverIntentList != null)
        for (int i = 0; i < ReceiverIntentList.size(); i++)
            logger.debug("Receiver Intent: " + ReceiverIntentList.get(i)[0] + " ~> " + ReceiverIntentList.get(i)[1]);
        if (ServiceIntentList != null)
        for (int i = 0; i < ServiceIntentList.size(); i++)
            logger.debug("Service Intent: " + ServiceIntentList.get(i)[0] + " ~> " + ServiceIntentList.get(i)[1]);
    }

    public LinkedList<Entrypoint> getEntries() {
        return entries;
    }

}
