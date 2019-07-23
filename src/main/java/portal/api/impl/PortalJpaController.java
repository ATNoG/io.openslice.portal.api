/**
 * Copyright 2017 University of Patras 
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at:
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * 
 * See the License for the specific language governing permissions and limitations under the License.
 */

package portal.api.impl;

import java.util.Iterator;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.PersistenceUnit;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import centralLog.api.CLevel;
import centralLog.api.CentralLogger;
import portal.api.model.Category;
import portal.api.model.DeploymentDescriptor;
import portal.api.model.ExperimentMetadata;
import portal.api.model.ExperimentOnBoardDescriptor;
import portal.api.model.VFImage;
import portal.api.model.Infrastructure;
import portal.api.model.InstalledVxF;
import portal.api.model.MANOplatform;
import portal.api.model.MANOprovider;
import portal.api.model.PortalProperty;
import portal.api.model.PortalUser;
import portal.api.model.Product;
import portal.api.model.SubscribedResource;
import portal.api.model.UserRoleType;
import portal.api.model.VxFMetadata;
import portal.api.model.VxFOnBoardedDescriptor;

/**
 * This class maintains the entity manager and get a broker element from DB
 * 
 * @author ctranoris
 * 
 */
public class PortalJpaController {
	private static final transient Log logger = LogFactory.getLog(PortalJpaController.class.getName());

	@PersistenceUnit
	private EntityManagerFactory entityManagerFactory;

//	private static EntityManagerFactory entityManagerFactory;
//    static {
//        try {
//        	entityManagerFactory = Persistence.createEntityManagerFactory(null);
//        } catch (Throwable ex) {
//            logger.error("Initial SessionFactory creation failed", ex);
//            throw new ExceptionInInitializerError(ex);
//        }
//    }	
	public void initData() {
		PortalUser admin = readPortalUserById(1);
		logger.info("======================== admin  = " + admin);

		if (admin == null) {
			PortalUser bu = new PortalUser();
			bu.setName("Portal Administrator");
			bu.setUsername("admin");
			bu.setPassword("changeme");
			bu.setEmail("");
			bu.setOrganization("");
			bu.addRole( UserRoleType.PORTALADMIN );
			bu.setActive(true);
			saveUser(bu);

			Category c = new Category();
			c.setName("None");
			saveCategory(c);
		}		
		initProperties();
	}
	
	
	private void initProperties(){
		if (readPropertyByName("adminEmail") == null){
			PortalProperty p = new PortalProperty("adminEmail", "info@example.org");
			saveProperty(p);
		}
		if (readPropertyByName("activationEmailSubject") == null){
			PortalProperty p = new PortalProperty("activationEmailSubject", "Activation Email Subject");
			saveProperty(p);
		}
		if (readPropertyByName("mailhost") == null){
			PortalProperty p = new PortalProperty("mailhost", "example.org");
			saveProperty(p);
		}
		if (readPropertyByName("mailuser") == null){
			PortalProperty p = new PortalProperty("mailuser", "exampleusername");
			saveProperty(p);
		}
		if (readPropertyByName("mailpassword") == null){
			PortalProperty p = new PortalProperty("mailpassword", "pass");
			saveProperty(p);
		}
		if (readPropertyByName("maindomain") == null){
			PortalProperty p = new PortalProperty("maindomain", "https://portal.5ginfire.eu");
			saveProperty(p);
		}
		if (readPropertyByName("bugzillaurl") == null){
			PortalProperty p = new PortalProperty("bugzillaurl", "portal.5ginfire.eu:443/bugstaging");
			saveProperty(p);
		}
		if (readPropertyByName("bugzillakey") == null){
			PortalProperty p = new PortalProperty("bugzillakey", "");
			saveProperty(p);
		}
		if (readPropertyByName("jenkinsciurl") == null){
			PortalProperty p = new PortalProperty("jenkinsciurl", "ci.5ginfire.eu");
			saveProperty(p);
		}
		if (readPropertyByName("jenkinscikey") == null){
			PortalProperty p = new PortalProperty("jenkinscikey", "");
			saveProperty(p);
		}
		if (readPropertyByName("pipelinetoken") == null){
			PortalProperty p = new PortalProperty("pipelinetoken", "");
			saveProperty(p);
		}
		if (readPropertyByName("centrallogerurl") == null){
			PortalProperty p = new PortalProperty("centrallogerurl", "");
			saveProperty(p);
		}
		if (readPropertyByName("portaltitle") == null){
			PortalProperty p = new PortalProperty("portaltitle", "5GinFIRE");
			saveProperty(p);
		}
		
		
	}

	public long countInstalledVxFs() {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT COUNT(s) FROM InstalledVxF s");
			return (Long) q.getSingleResult();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}

	public InstalledVxF updateInstalledVxF(InstalledVxF is) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
			InstalledVxF resis = entityManager.merge(is);
			entityTransaction.commit();
	
			return resis;
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}

	public void saveInstalledVxF(InstalledVxF is) {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			logger.info("Will create InstalledVxF = " + is.getUuid());
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
	
			entityManager.persist(is);
			entityManager.flush();
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}

	public InstalledVxF readInstalledVxFByName(final String name) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM InstalledVxF m WHERE m.name='" + name + "'");
			return (q.getResultList().size() == 0) ? null : (InstalledVxF) q.getSingleResult();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
	}

	public InstalledVxF readInstalledVxFByUUID(final String uuid) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM InstalledVxF m WHERE m.uuid='" + uuid + "'");
			return (q.getResultList().size() == 0) ? null : (InstalledVxF) q.getSingleResult();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}

	@SuppressWarnings("unchecked")
	public List<InstalledVxF> readInstalledVxFs(int firstResult, int maxResults) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM InstalledVxF m");
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}

	public void deleteInstalledVxF(final InstalledVxF message) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
	
			entityManager.remove(message);
	
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}

	public void getAllInstalledVxFPrinted() {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			logger.info("================= getAll() ==================START");
			List<InstalledVxF> lb = entityManager.createQuery("select p from InstalledVxF p").getResultList();
			for (Iterator iterator = lb.iterator(); iterator.hasNext();) {
				InstalledVxF iVxF = (InstalledVxF) iterator.next();
				logger.info("=== InstalledVxF found: " + iVxF.getName() + ", Id: " + iVxF.getId() + ", Uuid: "
						+ iVxF.getUuid() + ", RepoUrl: " + iVxF.getRepoUrl() + ", InstalledVersion: "
						+ iVxF.getInstalledVersion() + ", PackageURL: " + iVxF.getPackageURL() + ", PackageLocalPath: "
						+ iVxF.getPackageLocalPath() + ", Status: " + iVxF.getStatus());	
			}
			logger.info("================= getAll() ==================END");
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}

	public PortalJpaController() {
		logger.info(">>>>>>>>>>>>>> PortalJpaController constructor  <<<<<<<<<<<<<<<<<<");

	}

	public String echo(String message) {
		return "Echo processed: " + message;

	}

	public void deleteAllInstalledVxFs() {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
	
			Query q = entityManager.createQuery("DELETE FROM InstalledVxF ");
			q.executeUpdate();
			entityManager.flush();
	
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
	}

	public void deleteAllProducts() {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
	
			Query q = entityManager.createQuery("DELETE FROM Product ");
			q.executeUpdate();
			entityManager.flush();
	
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								

	}

	public void deleteAllUsers() {
		EntityManager entityManager = entityManagerFactory.createEntityManager();

		EntityTransaction entityTransaction = entityManager.getTransaction();

		entityTransaction.begin();

		Query q = entityManager.createQuery("DELETE FROM PortalUser ");
		q.executeUpdate();
		entityManager.flush();

		entityTransaction.commit();

	}

	public void saveUser(PortalUser bu) {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			logger.info("Will save PortalUser = " + bu.getName());
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
	
			entityManager.persist(bu);
	
			entityManager.flush();
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
	}

	public PortalUser readPortalUserByUsername(String username) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM PortalUser m WHERE m.username='" + username + "'");
			return (q.getResultList().size() == 0) ? null : (PortalUser) q.getSingleResult();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
	}

	public PortalUser readPortalUserBySessionID(String id) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM PortalUser m WHERE m.currentSessionID='" + id + "'");
			return (q.getResultList().size() == 0) ? null : (PortalUser) q.getSingleResult();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}												
	}

	public PortalUser readPortalUserByEmail(String email) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM PortalUser m WHERE m.email='" + email + "'");
			return (q.getResultList().size() == 0) ? null : (PortalUser) q.getSingleResult();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}
	
	public PortalUser readPortalUserByAPIKEY(String apikey) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM PortalUser m WHERE m.apikey='" + apikey + "'");
			return (q.getResultList().size() == 0) ? null : (PortalUser) q.getSingleResult();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}												
	}

	public PortalUser readPortalUserById(int userid) {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			return entityManager.find(PortalUser.class, userid);
			// Query q = entityManager.createQuery("SELECT m FROM PortalUser m WHERE
			// m.id=" + userid );
			// return (q.getResultList().size()==0)?null:(PortalUser)
			// q.getSingleResult();	
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
	}

	public PortalUser updatePortalUser(PortalUser bu) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
			PortalUser resis = entityManager.merge(bu);
			entityTransaction.commit();
	
			return resis;
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
	}

	@SuppressWarnings("unchecked")
	public List<PortalUser> readUsers(int firstResult, int maxResults) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM PortalUser m");
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}												
	}
	
	
	public List<PortalUser> readMentorUsers(int firstResult, int maxResults) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM PortalUser m INNER JOIN m.roles r WHERE r=portal.api.model.UserRoleType.MENTOR");
			//q.setParameter("inclList", "" );
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}


	public long countUsers() {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT COUNT(s) FROM PortalUser s");
			return (Long) q.getSingleResult();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}

	public void getAllUsersPrinted() {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			logger.info("================= getAllUsers() ==================START");	
			List<PortalUser> lb = entityManager.createQuery("select p from PortalUser p").getResultList();
			for (Iterator iterator = lb.iterator(); iterator.hasNext();) {
				PortalUser bu = (PortalUser) iterator.next();
				logger.info("	======> PortalUser found: " + bu.getName() + ", Id: " + bu.getId() + ", Id: "
						+ bu.getOrganization() + ", username: " + bu.getUsername());
	
				List<Product> products = bu.getProducts();
				for (Product prod : products) {
					logger.info("	======> vxfMetadata found: " + prod.getName() + ", Id: " + prod.getId() + ", getUuid: "
							+ prod.getUuid() + ", getName: " + prod.getName());
				}
	
			}
			logger.info("================= getAll() ==================END");
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
	}

	public void saveProduct(Product prod) {

		logger.info("Will save Product = " + prod.getName());
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			EntityTransaction entityTransaction = entityManager.getTransaction();
			entityTransaction.begin();
			entityManager.persist(prod);
			entityManager.flush();
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
	}

	public Product updateProduct(Product bm) {
		logger.info("================= updateProduct ==================");
		logger.info("bmgetId=" + bm.getId());
		logger.info("bm getName= " + bm.getName());
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
			Product resis = entityManager.merge(bm);
			entityTransaction.commit();
	
			return resis;
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
	}

	// public VxFMetadata updateVxFMetadata(VxFMetadata bm) {
	// logger.info("================= updateVxFMetadata ==================");
	// logger.info("bmgetId="+bm.getId());
	// logger.info("bm getName= "+bm.getName());
	// logger.info("bm getPackageLocation= "+bm.getPackageLocation());
	// EntityManager entityManager = entityManagerFactory.createEntityManager();
	//
	// EntityTransaction entityTransaction = entityManager.getTransaction();
	//
	// entityTransaction.begin();
	// VxFMetadata resis = entityManager.merge(bm);
	// entityTransaction.commit();
	//
	// return resis;
	// }

	@SuppressWarnings("unchecked")
	public List<VxFMetadata> readVxFsMetadata(Long categoryid, int firstResult, int maxResults, boolean isPublished) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			// Query q = entityManager.createQuery("SELECT m FROM VxFMetadata m");
			Query q;
			String s = "";
			
			if ((categoryid != null) && (categoryid >= 0)) {
				if (isPublished) {
					s = "a.published=TRUE";
				}
				q = entityManager
						.createQuery("SELECT a FROM VxFMetadata a WHERE " + s + " AND a.categories.id=" + categoryid + " ORDER BY a.id");
			}
			else {
				if (isPublished) {
					s = "WHERE a.published=TRUE";
				}
				q = entityManager.createQuery("SELECT a FROM VxFMetadata a " + s + " ORDER BY a.id");
			}
	
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
	
	}
	
	
	
	
	

	@SuppressWarnings("unchecked")
	public List<VxFMetadata> readVxFsMetadataForOwnerID(Long ownerid, int firstResult, int maxResults) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			// Query q = entityManager.createQuery("SELECT m FROM VxFMetadata m");
			Query q;
	
			if ((ownerid != null) && (ownerid >= 0))
				q = entityManager.createQuery("SELECT a FROM VxFMetadata a WHERE a.owner.id=" + ownerid + " ORDER BY a.id");
			else
				q = entityManager.createQuery("SELECT a FROM VxFMetadata a ORDER BY a.id");
	
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}

	public Product readProductByUUID(String uuid) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM Product m WHERE m.uuid='" + uuid + "'");
			return (q.getResultList().size() == 0) ? null : (Product) q.getSingleResult();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}

	public Product readProductByID(long id) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Product u = entityManager.find(Product.class, id);
			return u;
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}
	

	public Product readProductByName(String name) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM Product m WHERE m.name LIKE '" + name + "'");
			return (q.getResultList().size() == 0) ? null : (Product) q.getResultList().get(0);
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}

	// public VxFMetadata readVxFMetadataByUUID(String uuid) {
	// EntityManager entityManager = entityManagerFactory.createEntityManager();
	//
	// Query q = entityManager.createQuery("SELECT m FROM VxFMetadata m WHERE
	// m.uuid='" + uuid + "'");
	// return (q.getResultList().size()==0)?null:(VxFMetadata)
	// q.getSingleResult();
	// }
	//
	// public VxFMetadata readVxFMetadataByID(int vxfid) {
	// EntityManager entityManager = entityManagerFactory.createEntityManager();
	// VxFMetadata u = entityManager.find(VxFMetadata.class, vxfid);
	// return u;
	// }

	public void deleteUser(int userid) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			PortalUser u = entityManager.find(PortalUser.class, userid);
	
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
	
			entityManager.remove(u);
	
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}

	public void deleteProduct(int id) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Product p = entityManager.find(Product.class, id);
	
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
			entityManager.remove(p);
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}

	@SuppressWarnings("unchecked")
	public List<Product> readProducts(Long categoryid, int firstResult, int maxResults) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q;
	
			if ((categoryid != null) && (categoryid >= 0))
				q = entityManager
						.createQuery("SELECT a FROM Product a WHERE a.category.id=" + categoryid + " ORDER BY a.id");
			else
				q = entityManager.createQuery("SELECT a FROM Product a ORDER BY a.id");
	
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}

	public void getAllProductsPrinted() {
		logger.info("================= getAllProductsPrinted() ==================START");

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			List<Product> lb = readProducts(null, 0, 10000);
			for (Iterator iterator = lb.iterator(); iterator.hasNext();) {
				Product prod = (Product) iterator.next();
	
				logger.info("	=================> Product found: " + prod.getName() + ", Id: " + prod.getId()
						+ ", getUuid: " + prod.getUuid() + ", getName: " + prod.getName() + ", Owner.name: "
						+ prod.getOwner().getName());
	
			}
			logger.info("================= getAllProductsPrinted() ==================END");
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}

	public void saveSubscribedResource(SubscribedResource sm) {
		logger.info("Will save SubscribedResource = " + sm.getURL());

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
	
			entityManager.persist(sm);
			entityManager.flush();
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										

	}

	public SubscribedResource updateSubscribedResource(SubscribedResource sm) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
			SubscribedResource resis = entityManager.merge(sm);
			entityTransaction.commit();
	
			return resis;
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}

	public SubscribedResource readSubscribedResourceById(int userid) {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			return entityManager.find(SubscribedResource.class, userid);
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}

	public SubscribedResource readSubscribedResourceByuuid(String uuid) {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM SubscribedResource m WHERE m.uuid='" + uuid + "'");
			return (q.getResultList().size() == 0) ? null : (SubscribedResource) q.getSingleResult();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}

	public void deleteSubscribedResource(int smId) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			SubscribedResource sm = entityManager.find(SubscribedResource.class, smId);
	
			EntityTransaction entityTransaction = entityManager.getTransaction();
			entityTransaction.begin();
			entityManager.remove(sm);
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}

	public long countSubscribedResources() {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT COUNT(s) FROM SubscribedResource s");
			return (Long) q.getSingleResult();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}

	public void getAllSubscribedResourcesPrinted() {
		logger.info("================= getSubscribedResource() ==================START");

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			List<SubscribedResource> lb = entityManager.createQuery("select p from SubscribedResource p").getResultList();
			for (Iterator iterator = lb.iterator(); iterator.hasNext();) {
				SubscribedResource sm = (SubscribedResource) iterator.next();
				logger.info("	======> SubscribedResource found: " + sm.getURL() + ", Id: " + sm.getId());
			}
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}

	public void deleteAllSubscribedResources() {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
	
			Query q = entityManager.createQuery("DELETE FROM SubscribedResource ");
			q.executeUpdate();
			entityManager.flush();
	
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										

	}

	public List<SubscribedResource> readSubscribedResources(int firstResult, int maxResults) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM SubscribedResource m");
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}

	public List<ExperimentMetadata> readExperimentsMetadata(Long categoryid, int firstResult, int maxResults, boolean isPublished) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q;
			String s = "";
	
	
			if ((categoryid != null) && (categoryid >= 0)) {
				if (isPublished) {
					s = "a.published=TRUE";
				}
				q = entityManager
						.createQuery("SELECT a FROM ExperimentMetadata a WHERE " + s + " AND a.categories.id=" + categoryid + " ORDER BY a.id");
			}
			else {
				if (isPublished) {
					s = "WHERE a.published=TRUE";
				}
				q = entityManager.createQuery("SELECT a FROM ExperimentMetadata a " + s + " ORDER BY a.id");
			}
			
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										

	}

	@SuppressWarnings("unchecked")
	public List<ExperimentMetadata> readAppsMetadataForOwnerID(Long ownerid, int firstResult, int maxResults) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			// Query q = entityManager.createQuery("SELECT m FROM VxFMetadata m");
			Query q;
	
			if ((ownerid != null) && (ownerid >= 0))
				q = entityManager
						.createQuery("SELECT a FROM ExperimentMetadata a WHERE a.owner.id=" + ownerid + " ORDER BY a.id");
			else
				q = entityManager.createQuery("SELECT a FROM ExperimentMetadata a ORDER BY a.id");
	
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}

	public List<Category> readCategories(int firstResult, int maxResults) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM Category m  ORDER BY m.id");
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										

	}

	public void saveCategory(Category c) {
		logger.info("Will category = " + c.getName());

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			EntityTransaction entityTransaction = entityManager.getTransaction();
			entityTransaction.begin();
	
			entityManager.persist(c);
			entityManager.flush();
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}

	public Category readCategoryByID(int catid) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Category u = entityManager.find(Category.class, catid);
			return u;
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										

	}

	public Category updateCategory(Category c) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
			Category resis = entityManager.merge(c);
			entityTransaction.commit();
	
			return resis;
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}

	public void deleteCategory(int catid) {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Category c = entityManager.find(Category.class, catid);
	
			EntityTransaction entityTransaction = entityManager.getTransaction();
			entityTransaction.begin();
			entityManager.remove(c);
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		

	}

	public void deleteAllCategories() {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
	
			Query q = entityManager.createQuery("DELETE FROM Category");
			q.executeUpdate();
			entityManager.flush();
	
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										

	}

	public void getAllCategoriesPrinted() {
		logger.info("================= getAllCategoriesPrinted() ==================START");

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			List<Category> lb = entityManager.createQuery("select p from Category p").getResultList();
			for (Iterator iterator = lb.iterator(); iterator.hasNext();) {
				Category sm = (Category) iterator.next();
				logger.info("	======> Category found: " + sm.getName() + ", Id: " + sm.getId());
	
			}
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}

	public void saveProperty(PortalProperty p) {
		logger.info("Will PortalProperty = " + p.getName());

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			EntityTransaction entityTransaction = entityManager.getTransaction();
			entityTransaction.begin();
	
			entityManager.persist(p);
			entityManager.flush();
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		

	}

	public void deleteProperty(int propid) {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			PortalProperty c = entityManager.find(PortalProperty.class, propid);
	
			EntityTransaction entityTransaction = entityManager.getTransaction();
			entityTransaction.begin();
			entityManager.remove(c);
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										

	}

	public PortalProperty updateProperty(PortalProperty p) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
			PortalProperty bp = entityManager.merge(p);
			entityTransaction.commit();
	
			return bp;
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}

	public List<PortalProperty> readProperties(int firstResult, int maxResults) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM PortalProperty m  ORDER BY m.id");
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}

	public PortalProperty readPropertyByName(String name) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM PortalProperty m WHERE m.name='" + name + "'");
			return (q.getResultList().size() == 0) ? null : (PortalProperty) q.getSingleResult();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}

	public PortalProperty readPropertyByID(int propid) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			PortalProperty u = entityManager.find(PortalProperty.class, propid);
			return u;
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
		
	}

	@SuppressWarnings("unchecked")
	public List<DeploymentDescriptor> readDeploymentDescriptors(int firstResult, int maxResults) {
		List<DeploymentDescriptor> result = null;
		EntityManager entityManager = null;
		try {
		    entityManager = entityManagerFactory.createEntityManager();
			Query q = entityManager.createQuery(
					"SELECT m FROM DeploymentDescriptor m "
					+ " WHERE m.status<>portal.api.model.DeploymentDescriptorStatus.COMPLETED "
					+ " AND m.status<>portal.api.model.DeploymentDescriptorStatus.TERMINATED "
					+ " AND m.status<>portal.api.model.DeploymentDescriptorStatus.REJECTED "
					+ " AND m.status<>portal.api.model.DeploymentDescriptorStatus.FAILED "
					+ " AND m.status<>portal.api.model.DeploymentDescriptorStatus.DELETION_FAILED "
					+ " AND m.status<>portal.api.model.DeploymentDescriptorStatus.TERMINATION_FAILED "				
					+ " AND m.status<>portal.api.model.DeploymentDescriptorStatus.FAILED_OSM_REMOVED "
					+ " ORDER BY m.id");
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			result = q.getResultList();
			return result;
		} finally {
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}
	}
	
	@SuppressWarnings("unchecked")
	public List<DeploymentDescriptor> readCompletedDeploymentDescriptors(int firstResult, int maxResults) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery(
					"SELECT m FROM DeploymentDescriptor m  WHERE "
					+ "( m.status=portal.api.model.DeploymentDescriptorStatus.COMPLETED "
					+ " OR m.status=portal.api.model.DeploymentDescriptorStatus.TERMINATED )"
					+ "ORDER BY m.id");
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}
	}
	
	@SuppressWarnings("unchecked")
	public List<DeploymentDescriptor> readRejectedDeploymentDescriptors(int firstResult, int maxResults) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery(
					"SELECT m FROM DeploymentDescriptor m  WHERE "
					+ "m.status=portal.api.model.DeploymentDescriptorStatus.REJECTED ORDER BY m.id");
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);			
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}		
	}
	
	@SuppressWarnings("unchecked")
	public List<DeploymentDescriptor> readFailedDeploymentDescriptors(int firstResult, int maxResults) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery(
					"SELECT m FROM DeploymentDescriptor m  WHERE "
							+ "( m.status=portal.api.model.DeploymentDescriptorStatus.FAILED ) "
							+ " ORDER BY m.id");						
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();		
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}		
	}
	
	@SuppressWarnings("unchecked")
	public List<DeploymentDescriptor> readRemovedDeploymentDescriptors(int firstResult, int maxResults) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery(
					"SELECT m FROM DeploymentDescriptor m  WHERE "
						+ "( m.status=portal.api.model.DeploymentDescriptorStatus.FAILED "			
						+ " OR m.status=portal.api.model.DeploymentDescriptorStatus.FAILED_OSM_REMOVED "
						+ " OR m.status=portal.api.model.DeploymentDescriptorStatus.DELETION_FAILED "
						+ " OR m.status=portal.api.model.DeploymentDescriptorStatus.TERMINATION_FAILED ) "
						+ " ORDER BY m.id");						
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}		
		
	}
	

	@SuppressWarnings("unchecked")
	public List<DeploymentDescriptor> readDeploymentDescriptorsByUser(Long ownerid, int firstResult, int maxResults, String status) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();		
		try
		{
			String sql = "";
			if ( (status!=null) && status.equals("COMPLETED") ){
				sql = "SELECT m FROM DeploymentDescriptor m  WHERE m.owner.id=" + ownerid 
						+ " AND (m.status=portal.api.model.DeploymentDescriptorStatus.COMPLETED "
						+ " OR m.status=portal.api.model.DeploymentDescriptorStatus.TERMINATED ) "
						+ " ORDER BY m.id";			
			}else if ( (status!=null) && status.equals("REJECTED") ){
				sql = "SELECT m FROM DeploymentDescriptor m  WHERE m.owner.id=" + ownerid 
						+ " AND m.status=portal.api.model.DeploymentDescriptorStatus.REJECTED ORDER BY m.id";			
			}else if ( (status!=null) && status.equals("FAILED") ){
				sql = "SELECT m FROM DeploymentDescriptor m  WHERE m.owner.id=" + ownerid 
						+ " AND m.status=portal.api.model.DeploymentDescriptorStatus.FAILED"			
						+ " ORDER BY m.id";
			}else if ( (status!=null) && status.equals("FAILED_OSM_REMOVED") ){
				sql = "SELECT m FROM DeploymentDescriptor m  WHERE m.owner.id=" + ownerid 
						+ " AND ( m.status=portal.api.model.DeploymentDescriptorStatus.FAILED_OSM_REMOVED " 			
						+ " OR m.status=portal.api.model.DeploymentDescriptorStatus.FAILED"			
						+ " OR m.status=portal.api.model.DeploymentDescriptorStatus.DELETION_FAILED "
						+ " OR m.status=portal.api.model.DeploymentDescriptorStatus.TERMINATION_FAILED ) "
						+ " ORDER BY m.id";
			}else {
				sql = "SELECT m FROM DeploymentDescriptor m  WHERE m.owner.id=" + ownerid 
						+ " AND m.status<>portal.api.model.DeploymentDescriptorStatus.COMPLETED "
						+ " AND m.status<>portal.api.model.DeploymentDescriptorStatus.TERMINATED "
						+ " AND m.status<>portal.api.model.DeploymentDescriptorStatus.REJECTED "
						+ " AND m.status<>portal.api.model.DeploymentDescriptorStatus.FAILED "
						+ " AND m.status<>portal.api.model.DeploymentDescriptorStatus.DELETION_FAILED "
						+ " AND m.status<>portal.api.model.DeploymentDescriptorStatus.TERMINATION_FAILED "
						+ " AND m.status<>portal.api.model.DeploymentDescriptorStatus.FAILED_OSM_REMOVED "
						+ " ORDER BY m.id";
			}
			Query q = entityManager.createQuery( sql );
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}				
	}
	
	
	
	@SuppressWarnings("unchecked")
	public List<DeploymentDescriptor> readDeploymentDescriptorsByMentor(Long ownerid, int firstResult, int maxResults, String status) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			String sql = "";
			if ( (status!=null) && status.equals("COMPLETED") ){
				sql = "SELECT m FROM DeploymentDescriptor m  WHERE m.mentor.id=" + ownerid 
						+ " AND (m.status=portal.api.model.DeploymentDescriptorStatus.COMPLETED "
						+ " OR m.status=portal.api.model.DeploymentDescriptorStatus.TERMINATED ) "
						+ " ORDER BY m.id";						
			}else if ( (status!=null) && status.equals("REJECTED") ){
				sql = "SELECT m FROM DeploymentDescriptor m  WHERE m.mentor.id=" + ownerid 
						+ " AND m.status=portal.api.model.DeploymentDescriptorStatus.REJECTED ORDER BY m.id";
				
			}else if ( (status!=null) && status.equals("FAILED") ){
				sql = "SELECT m FROM DeploymentDescriptor m  WHERE m.mentor.id=" + ownerid 
						+ " AND m.status=portal.api.model.DeploymentDescriptorStatus.FAILED"			
						+ " ORDER BY m.id";
			}else if ( (status!=null) && status.equals("FAILED_OSM_REMOVED") ){
				sql = "SELECT m FROM DeploymentDescriptor m  WHERE m.mentor.id=" + ownerid 
						+ " AND ( m.status=portal.api.model.DeploymentDescriptorStatus.FAILED_OSM_REMOVED " 
						+ " OR m.status=portal.api.model.DeploymentDescriptorStatus.DELETION_FAILED "
						+ " OR m.status=portal.api.model.DeploymentDescriptorStatus.TERMINATION_FAILED ) "
						+ " ORDER BY m.id";			
			}else {
				sql = "SELECT m FROM DeploymentDescriptor m  WHERE m.mentor.id=" + ownerid 
						+ " AND m.status<>portal.api.model.DeploymentDescriptorStatus.COMPLETED "
						+ " AND m.status<>portal.api.model.DeploymentDescriptorStatus.TERMINATED "
						+ " AND m.status<>portal.api.model.DeploymentDescriptorStatus.REJECTED "
						+ " AND m.status<>portal.api.model.DeploymentDescriptorStatus.FAILED "
						+ " AND m.status<>portal.api.model.DeploymentDescriptorStatus.DELETION_FAILED "
						+ " AND m.status<>portal.api.model.DeploymentDescriptorStatus.TERMINATION_FAILED "					
						+ " AND m.status<>portal.api.model.DeploymentDescriptorStatus.FAILED_OSM_REMOVED "
						+ "ORDER BY m.id";
			}
			
			Query q = entityManager.createQuery( sql);
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}		
		
	}
	
	
	@SuppressWarnings("unchecked")
	public List<DeploymentDescriptor> readDeploymentDescriptorsScheduled( int firstResult, int maxResults ) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM DeploymentDescriptor m  WHERE m.status=portal.api.model.DeploymentDescriptorStatus.SCHEDULED ORDER BY m.id");
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}		
		
	}

	public void deleteDeployment(int id) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			DeploymentDescriptor c = entityManager.find(DeploymentDescriptor.class, id);
	
			EntityTransaction entityTransaction = entityManager.getTransaction();
			entityTransaction.begin();
			entityManager.remove(c);
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}				
	}

	public DeploymentDescriptor readDeploymentByID(int deploymentId) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			DeploymentDescriptor u = entityManager.find(DeploymentDescriptor.class, deploymentId);
			return u;
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}				
	}

	public DeploymentDescriptor updateDeploymentDescriptor(DeploymentDescriptor d) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{	
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
			DeploymentDescriptor resis = entityManager.merge(d);
			entityManager.flush();
			entityTransaction.commit();
			return resis;
		} catch(Exception e) {
	
		    entityManager.getTransaction().rollback();
			logger.info("Update of deployment " + d.getName() + " failed with message " + e.getMessage());
			CentralLogger.log(CLevel.INFO, "Update of deployment " + d.getName() + " failed with message " + e.getMessage());
			return d;
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}				
	}

	public void saveMANOplatform(MANOplatform mp) {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			logger.info("Will save MANOplatform = " + mp.getName());
			EntityTransaction entityTransaction = entityManager.getTransaction();
			entityTransaction.begin();
			entityManager.persist(mp);
			entityManager.flush();
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}				

	}

	public long countMANOplatforms() {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT COUNT(s) FROM MANOplatform s");
			return (Long) q.getSingleResult();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}				
	}

	public MANOplatform readMANOplatformById(int i) {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			return entityManager.find(MANOplatform.class, i);
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}				
	}

	public void deleteAllMANOplatforms() {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
	
			Query q = entityManager.createQuery("DELETE FROM MANOplatform");
			q.executeUpdate();
			entityManager.flush();
	
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}				

	}

	public MANOplatform readMANOplatformByName(String name) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM MANOplatform m WHERE m.name='" + name + "'");
			return (q.getResultList().size() == 0) ? null : (MANOplatform) q.getSingleResult();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}						
	}

	public void saveMANOprovider(MANOprovider mprovider) {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			logger.info("Will save MANOprovider = " + mprovider.getName());
			EntityTransaction entityTransaction = entityManager.getTransaction();
			entityTransaction.begin();
			entityManager.persist( mprovider );
			entityManager.flush();
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
	}

	public long countMANOproviders() {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT COUNT(s) FROM MANOprovider s");
			return (Long) q.getSingleResult();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
	}
	
	public MANOprovider readMANOproviderByName(String name) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM MANOprovider m WHERE m.name='" + name + "'");
			return (q.getResultList().size() == 0) ? null : (MANOprovider) q.getSingleResult();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
	}
	
	public void deleteAllMANOproviders() {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
	
			Query q = entityManager.createQuery("DELETE FROM MANOprovider");
			q.executeUpdate();
			entityManager.flush();
	
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								

	}
		
	public void deleteAllInfrastructures() {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
	
			Query q = entityManager.createQuery("DELETE FROM Infrastructure");
			q.executeUpdate();
			entityManager.flush();
	
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}


	@SuppressWarnings("unchecked")
	public List<MANOplatform> readMANOplatforms(int firstResult, int maxResults) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM MANOplatform m  ORDER BY m.id");
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								

	}

	public MANOplatform updateMANOplatform(MANOplatform c) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
			MANOplatform resis = entityManager.merge(c);
			entityTransaction.commit();
	
			return resis;
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
	}

	public void deleteMANOplatform(int mpid) {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			MANOplatform c = entityManager.find(MANOplatform.class, mpid);
	
			EntityTransaction entityTransaction = entityManager.getTransaction();
			entityTransaction.begin();
			entityManager.remove(c);
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
		
	}
	
	
	
	public List<MANOprovider> readMANOproviders(int firstResult, int maxResults) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM MANOprovider m  ORDER BY m.id");
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}

	public MANOprovider updateMANOprovider(MANOprovider c) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
			MANOprovider resis = entityManager.merge(c);
			entityTransaction.commit();
	
			return resis;
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}											
	}

	public void deleteMANOprovider(int mpid) {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			MANOprovider c = entityManager.find(MANOprovider.class, mpid);
	
			EntityTransaction entityTransaction = entityManager.getTransaction();
			entityTransaction.begin();
			entityManager.remove(c);
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
		
	}

	public MANOprovider readMANOproviderById(int i) {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			return entityManager.find(MANOprovider.class, i);
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
	}

	public List<MANOprovider> getMANOprovidersEnabledForOnboarding() {
		
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			TypedQuery<MANOprovider> query = entityManager.createQuery("SELECT mp FROM MANOprovider mp WHERE mp.enabledForONBOARDING = TRUE",MANOprovider.class);
			return query.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}
	
	public List<VxFOnBoardedDescriptor> readVxFOnBoardedDescriptors(int firstResult, int maxResults) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM VxFOnBoardedDescriptor m  ORDER BY m.id");
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}

	public void saveVxFOnBoardedDescriptor(VxFOnBoardedDescriptor mprovider) {
		logger.info("Will save VxFOnBoardedDescriptor = " + mprovider.getDeployId() );

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{	
			EntityTransaction entityTransaction = entityManager.getTransaction();
			entityTransaction.begin();
			entityManager.persist( mprovider );
			entityManager.flush();
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
	}

	public VxFOnBoardedDescriptor updateVxFOnBoardedDescriptor(VxFOnBoardedDescriptor c) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
		
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
			VxFOnBoardedDescriptor resis = entityManager.merge(c);
			entityTransaction.commit();
	
			return resis;
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}

	public void deleteVxFOnBoardedDescriptor(int mpid) {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			VxFOnBoardedDescriptor c = entityManager.find( VxFOnBoardedDescriptor.class, mpid);
	
			EntityTransaction entityTransaction = entityManager.getTransaction();
			entityTransaction.begin();
			entityManager.remove(c);
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
	}

	public VxFOnBoardedDescriptor readVxFOnBoardedDescriptorById(int i) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			return entityManager.find(VxFOnBoardedDescriptor.class, i);
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}


	
	public List<ExperimentOnBoardDescriptor> readExperimentOnBoardDescriptors(int firstResult, int maxResults) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM ExperimentOnBoardDescriptors m  ORDER BY m.id");
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}

	public void saveExperimentOnBoardDescriptor(ExperimentOnBoardDescriptor mprovider) {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			logger.info("Will save ExperimentOnBoardDescriptors = " + mprovider.getDeployId() );
			EntityTransaction entityTransaction = entityManager.getTransaction();
			entityTransaction.begin();
			entityManager.persist( mprovider );
			entityManager.flush();
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}

	public ExperimentOnBoardDescriptor updateExperimentOnBoardDescriptor(ExperimentOnBoardDescriptor c) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
			ExperimentOnBoardDescriptor resis = entityManager.merge(c);
			entityTransaction.commit();
	
			return resis;
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
	}

	public void deleteExperimentOnBoardDescriptor(int mpid) {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			ExperimentOnBoardDescriptor c = entityManager.find( ExperimentOnBoardDescriptor.class, mpid);
	
			EntityTransaction entityTransaction = entityManager.getTransaction();
			entityTransaction.begin();
			entityManager.remove(c);
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}

	public ExperimentOnBoardDescriptor readExperimentOnBoardDescriptorById(int i) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			return entityManager.find(ExperimentOnBoardDescriptor.class, i);
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
	}

	@SuppressWarnings("unchecked")
	public List<Infrastructure> readInfrastructures(int firstResult, int maxResults) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM Infrastructure m  ORDER BY m.id");
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}

	public void saveInfrastructure(Infrastructure c) {
		logger.info("Will save ExperimentOnBoardDescriptors = " + c.getName() );

		EntityManager entityManager = entityManagerFactory.createEntityManager();

		EntityTransaction entityTransaction = entityManager.getTransaction();
		entityTransaction.begin();
		entityManager.persist( c );
		entityManager.flush();
		entityTransaction.commit();
		
	}

	public Infrastructure readInfrastructureById(int infraid) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			return entityManager.find(Infrastructure.class, infraid);
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
	}

	public Infrastructure updateInfrastructure(Infrastructure c) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
			Infrastructure resis = entityManager.merge(c);
			entityTransaction.commit();
	
			return resis;
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
	}

	public void deletInfrastructure(int infraid) {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Infrastructure c = entityManager.find( Infrastructure.class, infraid );
	
			EntityTransaction entityTransaction = entityManager.getTransaction();
			entityTransaction.begin();
			entityManager.remove(c);
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}

	public Infrastructure readInfrastructureByName(String name) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM Infrastructure m WHERE m.name='" + name + "'");
			return (q.getResultList().size() == 0) ? null : (Infrastructure) q.getSingleResult();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}

	public DeploymentDescriptor readDeploymentByUUID(String uuid) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM DeploymentDescriptor m WHERE m.uuid='" + uuid + "'");
			return (q.getResultList().size() == 0) ? null : (DeploymentDescriptor) q.getSingleResult();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}
	
	
	@SuppressWarnings("unchecked")
	public List<DeploymentDescriptor> readScheduledDeployments() {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try 
		{
			Query q = entityManager.createQuery("SELECT m FROM DeploymentDescriptor m WHERE m.status = portal.api.model.DeploymentDescriptorStatus.SCHEDULED");
			return q.getResultList();
		}
		finally
		{
			entityManager.close();
		}
	}
	
	@SuppressWarnings("unchecked")
	public List<DeploymentDescriptor> readRunningAndInstantiatingDeployments() {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM DeploymentDescriptor m WHERE m.status = portal.api.model.DeploymentDescriptorStatus.RUNNING OR m.status = portal.api.model.DeploymentDescriptorStatus.INSTANTIATING");
			return q.getResultList();		
		}
		finally
		{
			entityManager.close();
		}
	}

	@SuppressWarnings("unchecked")
	public List<DeploymentDescriptor> readDeploymentsToBeTerminated() {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM DeploymentDescriptor m WHERE m.status = portal.api.model.DeploymentDescriptorStatus.TERMINATING");
			return q.getResultList();		
		}
		finally
		{
			entityManager.close();
		}
	}

	@SuppressWarnings("unchecked")
	public List<DeploymentDescriptor> readDeploymentsToBeDeleted() {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM DeploymentDescriptor m WHERE m.status = portal.api.model.DeploymentDescriptorStatus.TERMINATED OR m.status = portal.api.model.DeploymentDescriptorStatus.FAILED OR m.status = portal.api.model.DeploymentDescriptorStatus.TERMINATION_FAILED");
			return q.getResultList();		
		}
		finally
		{
			entityManager.close();
		}
	}

	@SuppressWarnings("unchecked")
	public List<DeploymentDescriptor> readRunningInstantiatingDeployments() {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM DeploymentDescriptor m WHERE m.status = portal.api.model.DeploymentDescriptorStatus.RUNNING OR m.status = portal.api.model.DeploymentDescriptorStatus.INSTANTIATING");
			return q.getResultList();
		}
		finally
		{
			entityManager.close();
		}
	}
	
	@SuppressWarnings("unchecked")
	public List<DeploymentDescriptor> readRunningInstantiatingAndTerminatingDeployments() {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM DeploymentDescriptor m WHERE m.status = portal.api.model.DeploymentDescriptorStatus.RUNNING OR m.status = portal.api.model.DeploymentDescriptorStatus.INSTANTIATING OR m.status = portal.api.model.DeploymentDescriptorStatus.TERMINATING");
			return q.getResultList();
		}
		finally
		{
			entityManager.close();
		}
		
	}
	
	/**
	 * 
	 * VFImage Objects Handling
	 */
	

	/**
	 * @param firstResult
	 * @param maxResults
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public List<VFImage> readVFImages(int firstResult, int maxResults) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM VFImage m ORDER BY m.id");
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
	}


	/**
	 * @param infraid
	 * @return
	 */
	public VFImage readVFImageById(int infraid) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			return entityManager.find( VFImage.class, infraid);
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}

	

	/**
	 * @param infraid
	 */
	public void deleteVFImage(int infraid) {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			VFImage c = entityManager.find( VFImage.class, infraid );
	
			EntityTransaction entityTransaction = entityManager.getTransaction();
			entityTransaction.begin();
			entityManager.remove(c);
			entityTransaction.commit();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}											
	}

	/**
	 * @param name
	 * @return
	 */
	public VFImage readVFImageByName(String name) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM VFImage m WHERE m.name='" + name + "'");
			return (q.getResultList().size() == 0) ? null : ( VFImage ) q.getSingleResult();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}


	public VFImage readVFImageByUUID(String uuid) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q = entityManager.createQuery("SELECT m FROM VFImage m WHERE m.uuid='" + uuid + "'");
			return (q.getResultList().size() == 0) ? null : (VFImage) q.getSingleResult();		
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}										
	}


	/**
	 * 
	 * Images by user ID and those that are published
	 * @param ownerid
	 * @param firstResult
	 * @param maxResults
	 * @return
	 */
	public List<VFImage> readVFImagesForOwnerID( Long ownerid, int firstResult, int maxResults) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			Query q;
	
			if ((ownerid != null) && (ownerid >= 0))
				q = entityManager.createQuery("SELECT a FROM VFImage a WHERE a.owner.id=" + ownerid + " OR a.published=true ORDER BY a.id");
			else
				q = entityManager.createQuery("SELECT a FROM VFImage a ORDER BY a.id");
	
			q.setFirstResult(firstResult);
			q.setMaxResults(maxResults);
			return q.getResultList();
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}


	/**
	 * @param vfimg
	 * @return
	 */
	public VFImage updateVFImage(VFImage vfimg) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		try
		{
			EntityTransaction entityTransaction = entityManager.getTransaction();
	
			entityTransaction.begin();
			VFImage bp = entityManager.merge( vfimg );
			entityTransaction.commit();
	
			return bp;
		}
		finally
		{
		    if (entityManager != null) {
		        entityManager.close();
		    }
		}								
		
	}
}
