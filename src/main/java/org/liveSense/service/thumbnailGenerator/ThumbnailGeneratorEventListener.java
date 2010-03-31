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

import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Robert Csakany (robson@semmi.se)
 * @created Feb 14, 2010
 */
public class ThumbnailGeneratorEventListener implements EventListener {

    /**
     * default log
     */
    private final Logger log = LoggerFactory.getLogger(ThumbnailGeneratorEventListener.class);
    ThumbnailGenerator service = null;

    public ThumbnailGeneratorEventListener(ThumbnailGenerator service) {
        this.service = service;
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
                    log.debug("Event catched: "+eventType+" "+filePath+" "+fileName);


                    if (!parentFolder.equals(service.getThumbnailFolder())) {
                        if (event.getType() == Event.NODE_REMOVED) {
                            service.deleteThumbnailsForImage(filePath, fileName);
                        }
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
                    log.debug("Event catched: "+eventType+" "+filePath+" "+fileName+" "+propertyName);

                    
                    if (!parentFolder.equals(service.getThumbnailFolder()) && "jcr:data".equals(propertyName) ) {
                        if (event.getType() == Event.PROPERTY_ADDED) {
                            service.createThumbnailsForImage(filePath, fileName);
                        } else if (event.getType() == Event.PROPERTY_CHANGED) {
                            service.deleteThumbnailsForImage(parentFolder, fileName);
                            service.createThumbnailsForImage(filePath, fileName);
                        }
                        //else if (event.getType() == Event.PROPERTY_REMOVED) {
                        //    deleteThumbnailsForImage(filePath);
                        //}
                    }
                }


/*
                if (event.getType() == Event.NODE_ADDED && !(event.getPath().contains(thumbnailFolder))) {
                    Node addedNode = getAdministrativeSession(repository).getRootNode().getNode(event.getPath().substring(1));
                    processImageNode(addedNode);
                } else if (event.getType() == Event.PROPERTY_CHANGED && !(event.getPath().contains(thumbnailFolder))) {
                    log.info("=======",event.getPath());

                }
 * */

            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }
}
