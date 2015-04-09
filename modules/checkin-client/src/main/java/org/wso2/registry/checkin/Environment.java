/*
*  Copyright (c) 2005-2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*  http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*
*/


package org.wso2.registry.checkin;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.wso2.carbon.registry.core.jdbc.DumpConstants;
import org.wso2.carbon.registry.synchronization.SynchronizationConstants;
import org.wso2.carbon.registry.synchronization.SynchronizationException;
import org.wso2.carbon.registry.synchronization.Utils;
import org.wso2.carbon.registry.synchronization.message.MessageCode;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
/*

 */

public class Environment {

    private String environment = null;
    private String environmentURL = null;
    private ClientOptions clientOptions = null;

    public Environment(ClientOptions clientOptions) throws SynchronizationException {
        this.clientOptions = clientOptions;
        // set environment
        environment = clientOptions.getEnvironment();


        // set environment URL
        setEnvironmentURL();


    }
/*


 */
    public void execute() throws SynchronizationException {
        if (clientOptions.isNewEnvironment()) {
            this.addEnvironment(clientOptions.getEnvironment(), clientOptions.getEnvironmentURL());
        } else if (clientOptions.isChangeEnvironment()) {

            this.switchTo(clientOptions.getEnvironment());
            environment = clientOptions.getEnvironment();


        } else {
            String startingDir = clientOptions.getWorkingLocation();

            OMElement metaOMElement = Utils.getMetaOMElement(startingDir);
            // set environment
            if (metaOMElement != null) {
                environment = metaOMElement.getAttributeValue(new QName("environment"));
            }
            setEnvironmentURL();
        }

    }

    public String getCurrentEnvironment() {
        return environment;
    }

    public void addEnvironment(String name, String url, XMLStreamWriter xmlWriter, OMElement metaOMElement) throws XMLStreamException, SynchronizationException {

        Iterator environments = metaOMElement.getFirstChildWithName(new QName("content")).getChildren();
        boolean isDefined = false;
        OMElement envChild = null;
        while (environments.hasNext()) {
            envChild = (OMElement) environments.next();
            if (envChild.getAttributeValue(new QName("name")).equals(environment)) {
                // the environment is already defined
                isDefined = true;
                break;
            }
        }
        if (!isDefined) {
            OMFactory factory = OMAbstractFactory.getOMFactory();
            OMElement child = factory.createOMElement(new QName(DumpConstants.ELEM_ENVIRONMENT));
            child.addAttribute("name", name, null);
            child.addAttribute("url", url, null);
            child.setText("");
            metaOMElement.getFirstChildWithName(new QName("content")).addChild(child);
        }
        metaOMElement.serialize(xmlWriter);

    }

    public void addEnvironment(String name, String url) throws SynchronizationException {
        System.out.println("changes are adding to repository...");
        String filePath = clientOptions.getWorkingLocation();
        addEnvironmentRecursively(name, url, filePath);
        System.out.println("environment " + name + " successfully added");
    }

    public void addEnvironment(String name, String url, String filePath) throws SynchronizationException {
        environment = name;
        //System.out.println(filePath);
        XMLStreamWriter xmlWriter = null;
        //filePath = clientOptions.getWorkingLocation();
        String metaDirectoryPath = filePath + File.separator + SynchronizationConstants.META_DIRECTORY;


        File metaDirFile = new File(metaDirectoryPath);
        String[] metaFiles = metaDirFile.list(new FilenameFilter() {
            public boolean accept(File file, String s) {
                if (!s.equals("~.xml")) {
                    return true;
                }
                return false;
            }
        });
        if (metaFiles != null) {
            String metafilePath = null;
            for (String childMetaFileName : metaFiles) {
                String childFileName = Utils.decodeFilename(childMetaFileName.
                        substring(1, childMetaFileName.length() - 4));
                String childFilePath = metaDirFile.getParent() + File.separator + childFileName;
                //createResourceMetaElement(xmlWriter, new File(childFilePath),
                //metaDirectoryPath + File.separator + childMetaFileName, path, callback);
                metafilePath = metaDirectoryPath + File.separator + childMetaFileName;
                // confirm the existence of the meta-file
                OMElement metaOMElement = Utils.getMetaOMElement(childFilePath);
                if (metaOMElement == null) {
                    return;
                }

                try {
                    xmlWriter = XMLOutputFactory.newInstance().createXMLStreamWriter(new FileWriter(metafilePath));
                } catch (IOException e) {
                    //System.out.println(metafilePath + " file not found in local repository");
                    throw new SynchronizationException(
                            MessageCode.ERROR_IN_CREATING_TEMP_FILE_FOR_DUMP,
                            e);
                } catch (XMLStreamException e) {
                    throw new SynchronizationException(
                            MessageCode.ERROR_IN_CREATING_XML_STREAM_WRITER, e);
                }
                try {
                    addEnvironment(name, url, xmlWriter, metaOMElement);
                } catch (XMLStreamException e) {
                    throw new SynchronizationException(
                            MessageCode.ERROR_IN_CREATING_XML_STREAM_WRITER, e);
                }
            }
        }


    }

    public void addEnvironmentRecursively(String name, String url, String filePath) throws SynchronizationException {

        File directory = new File(filePath);
        String[] childrenNames = directory.list(new FilenameFilter() {
            public boolean accept(File file, String s) {
                if (file.isDirectory()) {
                    if (s.equals(SynchronizationConstants.META_DIRECTORY)) {
                        return false;
                    }
                    return true;
                }
                return false;
            }
        });

        if (childrenNames != null) {
            for (String childFileName : childrenNames) {

                String childFilePath = filePath + File.separator + childFileName;
                addEnvironment(name, url, childFilePath);
                addEnvironmentRecursively(name, url, childFilePath);

            }
        }
    }

    private void setEnvironmentURL() throws SynchronizationException {
        String filePath = clientOptions.getWorkingLocation();
        searchEnvironmentRecursively(filePath);

    }

    private void searchEnvironmentRecursively(String filePath) throws SynchronizationException {

        String metaDirectoryPath = filePath + File.separator + SynchronizationConstants.META_DIRECTORY;
        File metaDirFile = new File(metaDirectoryPath);
        String[] metaFiles = metaDirFile.list(new FilenameFilter() {
            public boolean accept(File file, String s) {
                if (!s.equals("~.xml")) {
                    return true;
                }
                return false;
            }
        });

        if (metaFiles == null || metaFiles.length == 0) {

            File directory = new File(filePath);
            String[] childrenNames = directory.list(new FilenameFilter() {
                public boolean accept(File file, String s) {
                    if (file.isDirectory()) {
                        if (s.equals(SynchronizationConstants.META_DIRECTORY)) {
                            return false;
                        }
                        return true;
                    }
                    return false;
                }
            });

            if (childrenNames != null) {

                for (String childFileName : childrenNames) {

                    String childFilePath = filePath + File.separator + childFileName;
                    searchEnvironmentRecursively(childFilePath);

                }
            }
        } else {
            OMElement metaOMElement = null;
            String childMetaFileName = metaFiles[0];
            String childFileName = Utils.decodeFilename(childMetaFileName.
                    substring(1, childMetaFileName.length() - 4));
            String childFilePath = metaDirFile.getParent() + File.separator + childFileName; // read first meta file
            metaOMElement = Utils.getMetaOMElement(childFilePath);


            if (metaOMElement != null) {
                Iterator environments = metaOMElement.getFirstChildWithName(new QName("content")).getChildren();
                OMElement child;
                while (environments.hasNext()) {
                    child = (OMElement) environments.next();
                    if (child.getAttributeValue(new QName(DumpConstants.RESOURCE_NAME)).equals(environment)) {
                        environmentURL = child.getAttributeValue(new QName(DumpConstants.ATTR_URL));
                        clientOptions.setEnvironment(environment, environmentURL);
                        break;
                    }

                }
            }

        }

    }


    public void switchTo(String name) throws SynchronizationException {
        String[] names = getEnvironmentRecursively(clientOptions.getWorkingLocation());
        boolean isExistEnvironment = false;
        for (String env : names) {
            if (env.equals(name)) {
                isExistEnvironment = true;
                break;
            }
        }

        if (isExistEnvironment) {
            switchRecursively(name, clientOptions.getWorkingLocation());
            System.out.println("environment changed to " + clientOptions.getEnvironment());
        } else {
            System.out.println(name + " environment is not defined");
        }
    }

    private void switchRecursively(String name, String filePath) throws SynchronizationException {
        String metaDirectoryPath = filePath + File.separator + SynchronizationConstants.META_DIRECTORY;
        File metaDirFile = new File(metaDirectoryPath);
        String[] metaFiles = metaDirFile.list(new FilenameFilter() {
            public boolean accept(File file, String s) {
                return true;
            }
        });

        if (metaFiles != null) {
            for (String childMetaFileName : metaFiles) {
                try {
                    String childFileName = Utils.decodeFilename(childMetaFileName.
                            substring(1, childMetaFileName.length() - 4));
                    String childFilePath = metaDirFile.getParent() + File.separator + childFileName; /////// filpath +

                    OMElement metaOMElement = Utils.getMetaOMElement(childFilePath);
                    metaOMElement.addAttribute("environment", name, null);
                    String childMetaFilePath = metaDirectoryPath + File.separator + childMetaFileName;
                    XMLStreamWriter xmlWriter = XMLOutputFactory.newInstance().createXMLStreamWriter(new FileWriter(childMetaFilePath));
                    metaOMElement.serialize(xmlWriter);

                } catch (IOException e) {
                    throw new SynchronizationException(
                            MessageCode.ERROR_IN_CREATING_TEMP_FILE_FOR_DUMP,
                            e);
                } catch (XMLStreamException e) {
                    throw new SynchronizationException(
                            MessageCode.ERROR_IN_CREATING_XML_STREAM_WRITER, e);
                }

            }

        }
        File directory = new File(filePath);
        String[] childrenNames = directory.list(new FilenameFilter() {
            public boolean accept(File file, String s) {
                if (file.isDirectory()) {
                    if (s.equals(SynchronizationConstants.META_DIRECTORY)) {
                        return false;
                    }
                    return true;
                }
                return false;
            }
        });

        if (childrenNames != null) {
            for (String childFileName : childrenNames) {
                String childFilePath = metaDirFile.getParent() + File.separator + childFileName;
                switchRecursively(name, childFilePath);

            }
        }


    }

    private String[] getEnvironmentRecursively(String filePath) throws SynchronizationException {
        String[] environmentsNames = null;
        String metaDirectoryPath = filePath + File.separator + SynchronizationConstants.META_DIRECTORY;
        File metaDirFile = new File(metaDirectoryPath);
        String[] metaFiles = metaDirFile.list(new FilenameFilter() {
            public boolean accept(File file, String s) {
                if (!s.equals("~.xml")) {
                    return true;
                }
                return false;
            }
        });

        if (metaFiles.length != 0) { //////////////////////////////////////////// != null
            for (String childMetaFileName : metaFiles) {

                OMElement metaOMElement = null;

                String childFileName = Utils.decodeFilename(childMetaFileName.
                        substring(1, childMetaFileName.length() - 4));
                String childFilePath = metaDirFile.getParent() + File.separator + childFileName;

                metaOMElement = Utils.getMetaOMElement(childFilePath);

                if (metaOMElement != null) {
                    Iterator environments = metaOMElement.getFirstChildWithName(new QName("content")).getChildren();
                    OMElement child;
                    List<String> nameList = new LinkedList<String>();
                    while (environments.hasNext()) {
                        child = (OMElement) environments.next();
                        nameList.add(child.getAttributeValue(new QName("name")));


                    }

                    environmentsNames = new String[nameList.size()];
                    int i = 0;
                    for (String name : nameList) {
                        environmentsNames[i++] = name;
                    }

                    return environmentsNames;
                }


            }
        } else {
            File directory = new File(filePath);
            String[] childrenNames = directory.list(new FilenameFilter() {
                public boolean accept(File file, String s) {
                    if (file.isDirectory()) {
                        if (s.equals(SynchronizationConstants.META_DIRECTORY)) {
                            return false;
                        }
                        return true;
                    }
                    return false;
                }
            });

            if (childrenNames != null) {

                for (String childFileName : childrenNames) {


                    String childFilePath = metaDirFile.getParent() + File.separator + childFileName;
                    environmentsNames = getEnvironmentRecursively(childFilePath);
                    return environmentsNames;


                }
            }
        }
        return environmentsNames;
    }


}
