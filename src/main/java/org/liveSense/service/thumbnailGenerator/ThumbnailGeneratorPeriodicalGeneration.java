package org.liveSense.service.thumbnailGenerator;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.jcr.api.SlingRepository;
import org.liveSense.core.AdministrativeService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(label="%thumbnailGeneratorJobEventHandler.name",
        description="%thumbnailGeneratorJobEventHandler.description",
        immediate=true,
        metatype=true,
        policy=ConfigurationPolicy.OPTIONAL)
@Service(value=java.lang.Runnable.class)
@Properties(value = {
		@Property(
				name="scheduler.name", 
				value="ThumbnailGeneratorPeriodicalGeneration"),
		@Property(
				name="scheduler.expression", 
				value="0 0 * ? * * ")
})

public class ThumbnailGeneratorPeriodicalGeneration implements Runnable {
	private static final Logger log = LoggerFactory.getLogger(ThumbnailGeneratorPeriodicalGeneration.class);

    @Activate
    protected void activate(ComponentContext componentContext) throws RepositoryException {
    	
    }

	
	@Reference(cardinality=ReferenceCardinality.MANDATORY_UNARY, policy=ReferencePolicy.DYNAMIC)
	SlingRepository repository;
		
	public void run() {
		// Scanning folders for images
		Session session = null;
		log.info("Starting scan for images...");
		try {
			session = repository.loginAdministrative(null);
			// TODO Searching for images and generating jobs
			/*
			NodeIterator nodes = session.getWorkspace().getQueryManager().createQuery("select * from [jcr:content]", Query.JCR_SQL2).execute().getNodes();
			while (nodes.hasNext()) {
				Node node = nodes.nextNode();
				if (node.getParent().getPrimaryNodeType().getName().equals("thumbnail:thumbnailImage")) {
					
				}
			}
			*/
			if (session.hasPendingChanges()) {
                session.save();
			}
			
		} catch (RepositoryException e) {
			log.error(e.getMessage());
		} finally {
			session.logout();
		}
		
		
	}

}
