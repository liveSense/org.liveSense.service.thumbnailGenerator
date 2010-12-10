/*
 *  Copyright 2010 Robert Csakany <robson@semmi.se>.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */
package org.liveSense.service.thumbnailGenerator;
/**
 *
 * @author Robert Csakany (robson@semmi.se)
 * @created Feb 13, 2010
 */
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;
import javax.jcr.observation.ObservationManager;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.apache.sling.event.EventUtil;
import org.apache.sling.event.JobProcessor;
import org.apache.sling.jcr.api.SlingRepository;
import org.liveSense.core.AdministrativeService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Observe the users content for changes, and generate
 * thumbnail generation job when images are added/changed/deleted.
 */
@Component(label="%thumbnailResourceChangeListener.name",
        description="%thumbnailResourceChangeListener.description",
        immediate=true,
        metatype=true,
        policy=ConfigurationPolicy.OPTIONAL)

public class ThumbnailGeneratorResourceChangeListener extends AdministrativeService {
    private static final Logger log = LoggerFactory.getLogger(ThumbnailGeneratorResourceChangeListener.class);
			
    public static final String PARAM_CONTENT_PATHES = "contentPathes";
    public static final String CONTENT_PATH_SITES = "/sites";
    public static final String CONTENT_PATH_USERS = "/users";
    public static final String[] DEFAULT_CONTENT_PATHES = new String[] {CONTENT_PATH_SITES, CONTENT_PATH_USERS};
	
    public static final String PARAM_SUPPORTED_MIME_TYPES = "supportedMimeTypes";
    public static final String MIME_TYPE_IMAGE_JPEG = "image/jpeg";
    public static final String MIME_TYPE_IMAGE_GIF = "image/gif";
    public static final String MIME_TYPE_IMAGE_PNG = "image/png";	    
    public static final String[] DEFAULT_SUPPORTED_MIME_TYPES = {MIME_TYPE_IMAGE_JPEG, MIME_TYPE_IMAGE_GIF, MIME_TYPE_IMAGE_PNG};
	
    public static final String PARAM_SUPPORTED_NODE_TYPES = "supportedNodeTypes";
    public static final String NODE_TYPE_NT_FILE = "nt:file";	        
    public static final String[] DEFAULT_NODE_TYPES = new String[]{NODE_TYPE_NT_FILE};
    
    public static final String THUMBNAIL_GENERATE_TOPIC = "org/liveSense/thumbnail/generate";
    public static final String THUMBNAIL_REMOVE_TOPIC = "org/liveSense/thumbnail/remove";
 
    @Reference
    private SlingRepository repository;
    @Reference
    private EventAdmin eventAdmin;
    @Reference
    ResourceResolverFactory resourceResolverFactory;
    
    @Property(name=PARAM_CONTENT_PATHES, 
    		label="%contentPathes.name", 
    		description="%contentPathes.description", 
    		value={CONTENT_PATH_SITES, CONTENT_PATH_USERS})
    private String[] contentPathes = DEFAULT_CONTENT_PATHES;
    
	@Property(name = PARAM_SUPPORTED_MIME_TYPES, label = "%supported.mimeTypes", description = "%supported.mimeTypes.description", value = {
			MIME_TYPE_IMAGE_JPEG, MIME_TYPE_IMAGE_GIF, MIME_TYPE_IMAGE_PNG })
	private String[] supportedMimeTypes = DEFAULT_SUPPORTED_MIME_TYPES;

	@Property(name = PARAM_SUPPORTED_NODE_TYPES, label = "%supported.nodeTypes", description = "%supported.nodeTypes.description", value = {
			NODE_TYPE_NT_FILE })
	private String[] supportedNodeTypes = DEFAULT_NODE_TYPES;

	
    Session session;

    class PathEventListener implements EventListener {

    	private void generateJobEvent(String eventType, String filePath, String fileName) {
            if (!fileName.startsWith(".")) {
            	String mimeType = null;
            	try {
            		mimeType = session.getRootNode().getNode(filePath+"/"+fileName+"/jcr:content").getProperty("jcr:mimeType").getString();
                	boolean foundMimeType = false;
                	for (String sup : supportedMimeTypes) {
                		if (sup.equals(mimeType)) {
                			foundMimeType = true;
                		}
                	}
                	if (foundMimeType) {
                		log.info(">Generate thumbnail event "+EventUtil.PROPERTY_JOB_TOPIC+" "+THUMBNAIL_GENERATE_TOPIC+" for " +eventType+" "+filePath+" "+fileName);
        	    		final Dictionary<String, Object> props = new Hashtable<String, Object>();
        	            props.put(EventUtil.PROPERTY_JOB_TOPIC, THUMBNAIL_GENERATE_TOPIC);
        	    		props.put("resourcePath", "/"+filePath+"/"+fileName);
        	    		org.osgi.service.event.Event generateThumbnailJob = new org.osgi.service.event.Event(EventUtil.TOPIC_JOB, props);
        	    		eventAdmin.sendEvent(generateThumbnailJob);
                	}
            	} catch (Exception e) {
            		log.error("Error resolving mimeType: "+filePath+"/"+fileName);
				}
            }
    		
    	}

    	private void removeJobEvent(String eventType, String filePath, String fileName) {
            if (!fileName.startsWith(".")) {
        		log.info(">Remove thumbnail event "+EventUtil.PROPERTY_JOB_TOPIC+" "+THUMBNAIL_REMOVE_TOPIC+" for " +eventType+" "+filePath+" "+fileName);
            	final Dictionary<String, Object> props = new Hashtable<String, Object>();
	            props.put(EventUtil.PROPERTY_JOB_TOPIC, THUMBNAIL_REMOVE_TOPIC);
	    		props.put("resourcePath", "/"+filePath+"/"+fileName);
	    		org.osgi.service.event.Event generateThumbnailJob = new org.osgi.service.event.Event(EventUtil.TOPIC_JOB, props);
	    		eventAdmin.sendEvent(generateThumbnailJob);
            }   		
    	}
    	
        public void onEvent(EventIterator it) {
            while (it.hasNext()) {
                Event event = it.nextEvent();
                try {

                    if (event.getType() == Event.NODE_REMOVED || event.getType() == Event.NODE_ADDED) {
                        String[] pathElements = event.getPath().split("/");

                        StringBuffer sb = new StringBuffer();
                        for (int i = 1; i < pathElements.length-2; i++) {
                            if (i!=0) sb.append("/");
                            sb.append(pathElements[i]);
                        }
                        String filePath = sb.toString().substring(1);
                        String fileName = pathElements[pathElements.length-2];
                        String parentFolder = pathElements[pathElements.length-3];

                        String eventType = (event.getType()==Event.NODE_ADDED ? "NODE_ADDED" : (event.getType()==Event.NODE_REMOVED ? "NODE_REMOVED" : "UNHANDLED_EVENT"));
                        if (event.getType() == Event.NODE_ADDED) {
                        	generateJobEvent(eventType, filePath, fileName);
                        } else if (event.getType() == Event.NODE_REMOVED) {
                        	removeJobEvent(eventType, filePath, fileName);
                        }
                    } else {
                        String[] pathElements = event.getPath().split("/");

                        StringBuffer sb = new StringBuffer();
                        for (int i = 1; i < pathElements.length-3; i++) {
                            if (i!=0) sb.append("/");
                            sb.append(pathElements[i]);
                        }
                        String filePath = sb.toString().substring(1);
                        String fileName = pathElements[pathElements.length-3];
                        String parentFolder = pathElements[pathElements.length-4];

                        String propertyName = pathElements[pathElements.length-1];

                        String eventType = (event.getType()==Event.PROPERTY_ADDED ? "PROPERTY_ADDED" : (event.getType()==Event.PROPERTY_CHANGED ? "PROPERTY_CHANGED" : (event.getType()==Event.PROPERTY_REMOVED ? "PROPERTY_REMOVED" : "UNHANDLED_EVENT")));
                        if ("jcr:data".equals(propertyName)) generateJobEvent(eventType, filePath, fileName);
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }
    
    private ArrayList<PathEventListener> eventListeners = new ArrayList<PathEventListener>();
    
    private ObservationManager observationManager;

    /**
     * Activates this component.
     *
     * @param componentContext The OSGi <code>ComponentContext</code> of this
     *            component.
     */
    protected void activate(ComponentContext componentContext) throws RepositoryException {
        // Setting up contentPathes
        contentPathes = OsgiUtil.toStringArray(componentContext.getProperties().get(PARAM_CONTENT_PATHES), DEFAULT_CONTENT_PATHES);
        // Setting up supportedMimeTypes
        supportedMimeTypes = OsgiUtil.toStringArray(componentContext.getProperties().get(PARAM_SUPPORTED_MIME_TYPES), DEFAULT_SUPPORTED_MIME_TYPES);
        // Setting up supported node types
        supportedNodeTypes = OsgiUtil.toStringArray(componentContext.getProperties().get(PARAM_SUPPORTED_NODE_TYPES), DEFAULT_NODE_TYPES);
    
        session = getAdministrativeSession(repository);
        if (repository.getDescriptor(Repository.OPTION_OBSERVATION_SUPPORTED).equals("true")) {
            observationManager = session.getWorkspace().getObservationManager();
            //String[] types = {"nt:resource"};
            for (int i = 0; i < contentPathes.length; i++) {
                String[] propType = {"nt:resource"};
                PathEventListener listener = new PathEventListener();
                observationManager.addEventListener(listener, Event.PROPERTY_ADDED | Event.PROPERTY_CHANGED | Event.PROPERTY_REMOVED, contentPathes[i], true, null, propType, true);
                eventListeners.add(listener);

                //String[] fileType = {"nt:file"};
                listener = new PathEventListener();
                observationManager.addEventListener(listener, Event.NODE_REMOVED, contentPathes[i], true, null, supportedNodeTypes, true);
                eventListeners.add(listener);
            }
        }
    }
  
    @Override
    public void deactivate(ComponentContext componentContext) throws RepositoryException {
        super.deactivate(componentContext);
        if (observationManager != null) {
            for (PathEventListener listener : eventListeners) {
                observationManager.removeEventListener(listener);
            }
        }
    }    
}

