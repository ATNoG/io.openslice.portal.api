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

package portal.api.repo;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.jaxrs.ext.MessageContext;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MappingJsonFactory;

import OSM4NBIClient.OSM4Client;
import OSM4Util.OSM4ArchiveExtractor.OSM4NSExtractor;
import OSM4Util.OSM4ArchiveExtractor.OSM4VNFDExtractor;
import OSM4Util.OSM4NSReq.OSM4NSRequirements;
import OSM4Util.OSM4VNFReq.OSM4VNFRequirements;
import OSM5NBIClient.OSM5Client;
import OSM5Util.OSM5ArchiveExtractor.OSM5NSExtractor;
import OSM5Util.OSM5ArchiveExtractor.OSM5VNFDExtractor;
import OSM5Util.OSM5NSReq.OSM5NSRequirements;
import OSM5Util.OSM5VNFReq.OSM5VNFRequirements;
import centralLog.api.CLevel;
import centralLog.api.CentralLogger;
import portal.api.bugzilla.model.ErrorMsg;
import portal.api.bus.BusController;
import portal.api.mano.MANOController;
import portal.api.model.Category;
import portal.api.model.ConstituentVxF;
import portal.api.model.DeploymentDescriptor;
import portal.api.model.DeploymentDescriptorStatus;
import portal.api.model.ExperimentMetadata;
import portal.api.model.ExperimentOnBoardDescriptor;
import portal.api.model.IPortalRepositoryAPI;
import portal.api.model.Infrastructure;
import portal.api.model.MANOplatform;
import portal.api.model.MANOprovider;
import portal.api.model.OnBoardingStatus;
import portal.api.model.PackagingFormat;
import portal.api.model.PortalProperty;
import portal.api.model.PortalUser;
import portal.api.model.Product;
import portal.api.model.UserRoleType;
import portal.api.model.UserSession;
import portal.api.model.VFImage;
import portal.api.model.ValidationJob;
import portal.api.model.ValidationStatus;
import portal.api.model.VxFMetadata;
import portal.api.model.VxFOnBoardedDescriptor;
import portal.api.osm.client.OSMClient;
import portal.api.util.AjaxUserFilter;
import portal.api.util.AttachmentUtil;
import portal.api.util.EmailUtil;
import portal.api.validation.ci.ValidationJobResult;
import pt.it.av.atnog.extractors.NSExtractor;
import pt.it.av.atnog.extractors.VNFExtractor;
import pt.it.av.atnog.requirements.NSRequirements;
import pt.it.av.atnog.requirements.VNFRequirements;
import urn.ietf.params.xml.ns.yang.nfvo.nsd.rev141027.nsd.catalog.Nsd;
import urn.ietf.params.xml.ns.yang.nfvo.nsd.rev141027.nsd.descriptor.ConstituentVnfd;
import urn.ietf.params.xml.ns.yang.nfvo.vnfd.rev150910.vnfd.catalog.Vnfd;
import urn.ietf.params.xml.ns.yang.nfvo.vnfd.rev150910.vnfd.descriptor.Vdu;

//CORS support
//@CrossOriginResourceSharing(
//        allowOrigins = {
//           "http://83.212.106.218"
//        },
//        allowCredentials = true
//        
//)
@Path("/repo")
public class PortalRepositoryAPIImpl implements IPortalRepositoryAPI {

	@Context
	UriInfo uri;

	@Context
	HttpHeaders headers;

	@Context
	MessageContext ws;

	@Context
	protected SecurityContext sc;
	
	@Context
	AjaxUserFilter ajf;
	
	private PortalRepository portalRepositoryRef;

	/** */
	private MANOController aMANOController;
	
	
	private static final transient Log logger = LogFactory.getLog(PortalRepositoryAPIImpl.class.getName());

	private static final String METADATADIR = System.getProperty("user.home") + File.separator + ".portal"
			+ File.separator + "metadata" + File.separator;
	


	// PortalUser related API

	/*************** Users API *************************/

	@GET
	@Path("/admin/users/")
	@Produces("application/json")
	// @RolesAllowed("admin") //see this for this annotation
	// http://pic.dhe.ibm.com/infocenter/radhelp/v9/index.jsp?topic=%2Fcom.ibm.javaee.doc%2Ftopics%2Ftsecuringejee.html
	public Response getUsers() {

		if ( !sc.isUserInRole( UserRoleType.PORTALADMIN.name() ) ){
			 return Response.status(Status.FORBIDDEN ).build();
		}
//		if (sc != null) {
//			if (sc.getUserPrincipal() != null)
//				logger.info(" securityContext.getUserPrincipal().toString() >"
//						+ sc.getUserPrincipal().getName() + "<");
//
//		}

		return Response.ok().entity(portalRepositoryRef.getUserValues()).build();
	}
	
	@GET
	@Path("/admin/users/mentors")
	@Produces("application/json")
	public Response getMentors() {

		
		return Response.ok().entity(portalRepositoryRef.getUserMentorsValues()).build();
	}

	@GET
	@Path("/admin/users/{userid}")
	@Produces("application/json")
	public Response getUserById(@PathParam("userid") int userid) {
		
		if ( !sc.isUserInRole( UserRoleType.PORTALADMIN.name() ) ){
			 return Response.status(Status.FORBIDDEN ).build();
		}

		PortalUser u = portalRepositoryRef.getUserByID(userid);
		
		
		if ( !sc.isUserInRole( UserRoleType.PORTALADMIN.name() ) ){
			 return Response.status(Status.FORBIDDEN ).build();
		}

		if (u != null) {
			return Response.ok().entity(u).build();
		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("User with id=" + userid + " not found in portal registry");
			throw new WebApplicationException(builder.build());
		}
	}

	@POST
	@Path("/admin/users/")
	@Produces("application/json")
	@Consumes("application/json")
	public Response addUser(PortalUser user) {

		logger.info("Received POST for usergetUsername: " + user.getUsername());
		// logger.info("Received POST for usergetPassword: " +
		// user.getPassword());
		// logger.info("Received POST for usergetOrganization: " +
		// user.getOrganization());

		if ((user.getUsername() == null)
				|| (user.getUsername().equals("") || (user.getEmail() == null) || (user.getEmail().equals("")))) {
			ResponseBuilder builder = Response.status(Status.BAD_REQUEST);
			builder.entity("New user with username=" + user.getUsername() + " cannot be registered");
			logger.info("New user with username=" + user.getUsername() + " cannot be registered BAD_REQUEST.");
			throw new WebApplicationException(builder.build());
		}
		
		if ( user.getActive() ) {
			ResponseBuilder builder = Response.status(Status.BAD_REQUEST);
			builder.entity("New user with username=" + user.getUsername() + " cannot be registered, seems ACTIVE already");
			logger.info("New user with username=" + user.getUsername() + " cannot be registered BAD_REQUEST, seems ACTIVE already");
			throw new WebApplicationException(builder.build());
		}

		PortalUser portaluser = portalRepositoryRef.getUserByUsername(user.getUsername());
		if (portaluser != null) {
			return Response.status(Status.BAD_REQUEST).entity("Username exists").build();
		}

		portaluser = portalRepositoryRef.getUserByEmail(user.getEmail());
		if (portaluser != null) {
			return Response.status(Status.BAD_REQUEST).entity("Email exists").build();
		}

		user.setApikey( UUID.randomUUID().toString() );
		portaluser = portalRepositoryRef.addPortalUserToUsers(user);

		if (portaluser != null) {
			BusController.getInstance().newUserAdded( portaluser.getId() );			
			return Response.ok().entity(portaluser).build();
		} else {
			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
			builder.entity("Requested user with username=" + user.getUsername() + " cannot be installed");
			return builder.build();
		}
	}

	@POST
	@Path("/register/")
	@Produces("application/json")
	@Consumes("multipart/form-data")
	public Response addNewRegisterUser(List<Attachment> ats) {

		PortalUser user = new PortalUser();
		user.setName(AttachmentUtil.getAttachmentStringValue("name", ats));
		user.setUsername(AttachmentUtil.getAttachmentStringValue("username", ats));
		user.setPassword(AttachmentUtil.getAttachmentStringValue("userpassword", ats));
		user.setOrganization(AttachmentUtil.getAttachmentStringValue("userorganization", ats) + "^^"
				+ AttachmentUtil.getAttachmentStringValue("randomregid", ats));
		user.setEmail(AttachmentUtil.getAttachmentStringValue("useremail", ats));
		user.setActive(false);// in any case the user should be not active
		user.addRole(UserRoleType.EXPERIMENTER); // otherwise in post he can choose
		user.addRole(UserRoleType.VXF_DEVELOPER); // otherwise in post he can choose
		// PORTALADMIN, and the
		// immediately register :-)

		String msg = AttachmentUtil.getAttachmentStringValue("emailmessage", ats);
		logger.info("Received register for usergetUsername: " + user.getUsername());

		Response r = addUser(user);

		if (r.getStatusInfo().getStatusCode() == Status.OK.getStatusCode()) {
			logger.info("Email message: " + msg);
			String subj = "[" + PortalRepository.getPropertyByName("portaltitle").getValue() + "] " + PortalRepository.getPropertyByName("activationEmailSubject").getValue();
			EmailUtil.SendRegistrationActivationEmail(user.getEmail(), msg, subj);
		}

		return r;
	}

	@POST
	@Path("/register/verify")
	@Produces("application/json")
	@Consumes("multipart/form-data")
	public Response addNewRegisterUserVerify(List<Attachment> ats) {

		String username = AttachmentUtil.getAttachmentStringValue("username", ats);
		String rid = AttachmentUtil.getAttachmentStringValue("rid", ats);

		PortalUser u = portalRepositoryRef.getUserByUsername(username);
		if (u.getOrganization().contains("^^")) {
			u.setOrganization(u.getOrganization().substring(0, u.getOrganization().indexOf("^^")));
			u.setActive(true);
		}
		u = portalRepositoryRef.updateUserInfo(  u);
		// AttachmentUtil.getAttachmentStringValue("username", ats)

		if (u != null) {
			return Response.ok().entity(u).build();
		} else {
			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
			builder.entity("Requested user with username=" + u.getUsername() + " cannot be updated");
			throw new WebApplicationException(builder.build());
		}
	}

	@PUT
	@Path("/admin/users/{userid}")
	@Produces("application/json")
	@Consumes("application/json")
	public Response updateUserInfo(@PathParam("userid") int userid, PortalUser user) {
		logger.info("Received PUT for user: " + user.getUsername());
		
		if ( !sc.isUserInRole( UserRoleType.PORTALADMIN.name() ) ){
			 return Response.status(Status.FORBIDDEN ).build();
		}

		PortalUser previousUser = portalRepositoryRef.getUserByID(userid);

//		List<Product> previousProducts = previousUser.getProducts();
//
//		if (user.getProducts().size() == 0) {
//			user.getProducts().addAll(previousProducts);
//		}
//
		previousUser.setActive( user.getActive() );
		previousUser.setEmail( user.getEmail() );
		previousUser.setName( user.getName());
		previousUser.setOrganization( user.getOrganization() );
		if ( (user.getPassword()!=null) && (!user.getPassword().equals(""))){//else will not change it
			previousUser.setPasswordUnencrypted( user.getPassword() ); 	//the unmarshaled object user has already called setPassword, so getPassword provides the encrypted password
		}
		
		previousUser.getRoles().clear();
		for (UserRoleType rt : user.getRoles()) {
			previousUser.getRoles().add(rt);
		}
		
		if ( (user.getApikey()!=null) && ( !user.getApikey().equals("")) ){
			previousUser.setApikey( user.getApikey() );			
		} else {
			previousUser.setApikey( UUID.randomUUID().toString() );			
		}

		
		PortalUser u = portalRepositoryRef.updateUserInfo( previousUser );
		

		if (u != null) {
	
			return Response.ok().entity(u).build();
		} else {
			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
			builder.entity("Requested user with username=" + user.getUsername() + " cannot be updated");
			throw new WebApplicationException(builder.build());
		}
	}

	@DELETE
	@Path("/admin/users/{userid}")
	@Produces("application/json")
	public Response deleteUser(@PathParam("userid") int userid) {
		logger.info("Received DELETE for userid: " + userid);
		
		if ( !sc.isUserInRole( UserRoleType.PORTALADMIN.name() ) ){
			 return Response.status(Status.FORBIDDEN ).build();
		}


		return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		
		/**
		 * do not allow for now to delete users!
		 */
				
//		portalRepositoryRef.deleteUser(userid);
//		return Response.ok().build();
		
	}
	
	/**
	 * @param userID
	 * @return true if user logged is equal to the requested id of owner, or is PORTALADMIN
	 */
	private boolean checkUserIDorIsAdmin(int userID){
		
		PortalUser u = portalRepositoryRef.getUserBySessionID(ws.getHttpServletRequest().getSession().getId());
		if ( (u !=null )  && (u.getId() == userID) ){
			return true;
		} 
		if ( (u !=null )  && u.getRoles().contains(UserRoleType.PORTALADMIN) ){//sc.isUserInRole( UserRoleType.PORTALADMIN.name() ) ){
			 return true;
		}
		
		if ( (u ==null) && (ws.getHttpHeaders().getHeaderString( "X-APIKEY")!=null) ){
			//retry again in case where there is no user found but still there is APIKEY
			if ( AjaxUserFilter.xapiKeyAuth( ws.getHttpServletRequest(), portalRepositoryRef) ){
				PortalUser u2 = portalRepositoryRef.getUserBySessionID(ws.getHttpServletRequest().getSession().getId());
				if ( u2!=null){
					return checkUserIDorIsAdmin( u2.getId() );
				}				
			}			
		}
		return false;
	}

	@GET
	@Path("/admin/users/{userid}/vxfs")
	@Produces("application/json")
	public Response getAllVxFsofUser(@PathParam("userid") int userid) {
		logger.info("getAllVxFsofUser for userid: " + userid);
		
		if ( !checkUserIDorIsAdmin( userid ) ){
			 return Response.status(Status.FORBIDDEN ).build();
		}
		PortalUser u = portalRepositoryRef.getUserByID(userid);

		if (u != null) {
			List<Product> prods = u.getProducts();
			List<VxFMetadata> vxfs = new ArrayList<VxFMetadata>();
			for (Product p : prods) {
				if (p instanceof VxFMetadata)
					vxfs.add((VxFMetadata) p);
			}

			return Response.ok().entity(vxfs).build();
		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("User with id=" + userid + " not found in portal registry");
			throw new WebApplicationException(builder.build());
		}
	}

	@GET
	@Path("/admin/users/{userid}/experiments")
	@Produces("application/json")
	public Response getAllAppsofUser(@PathParam("userid") int userid) {
		logger.info("getAllAppsofUser for userid: " + userid);
		
		if ( !checkUserIDorIsAdmin( userid ) ){
			 return Response.status(Status.FORBIDDEN ).build();
		}

		
		PortalUser u = portalRepositoryRef.getUserByID(userid);

		if (u != null) {
			List<Product> prods = u.getProducts();
			List<ExperimentMetadata> apps = new ArrayList<ExperimentMetadata>();
			for (Product p : prods) {
				if (p instanceof ExperimentMetadata)
					apps.add((ExperimentMetadata) p);
			}

			return Response.ok().entity(apps).build();
		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("User with id=" + userid + " not found in portal registry");
			throw new WebApplicationException(builder.build());
		}
	}

	@GET
	@Path("/admin/users/{userid}/vxfs/{vxfid}")
	@Produces("application/json")
	public Response getVxFofUser(@PathParam("userid") int userid, @PathParam("vxfid") int vxfid) {
		logger.info("getVxFofUser for userid: " + userid + ", vxfid=" + vxfid);

		if ( !checkUserIDorIsAdmin( userid ) ){
			 return Response.status(Status.FORBIDDEN ).build();
		}

		
		PortalUser u = portalRepositoryRef.getUserByID(userid);

		if (u != null) {
			VxFMetadata vxf = (VxFMetadata) u.getProductById(vxfid);
			return Response.ok().entity(vxf).build();
		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("User with id=" + userid + " not found in portal registry");
			throw new WebApplicationException(builder.build());
		}
	}

	@GET
	@Path("/admin/users/{userid}/experiments/{appid}")
	@Produces("application/json")
	public Response getAppofUser(@PathParam("userid") int userid, @PathParam("appid") int appid) {
		logger.info("getAppofUser for userid: " + userid + ", appid=" + appid);
		if ( !checkUserIDorIsAdmin( userid ) ){
			 return Response.status(Status.FORBIDDEN ).build();
		}
		
		PortalUser u = portalRepositoryRef.getUserByID(userid);

		if (u != null) {
			ExperimentMetadata appmeta = (ExperimentMetadata) u.getProductById(appid);
			return Response.ok().entity(appmeta).build();
		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("User with id=" + userid + " not found in portal registry");
			throw new WebApplicationException(builder.build());
		}
	}

	// VxFS API

	private Product addNewProductData(Product prod, Attachment image, Attachment submittedFile,
			List<Attachment> screenshots) throws IOException {

		String uuid = UUID.randomUUID().toString();

		logger.info("prodname = " + prod.getName());
		logger.info("version = " + prod.getVersion());
		logger.info("shortDescription = " + prod.getShortDescription());
		logger.info("longDescription = " + prod.getLongDescription());

		prod.setUuid(uuid);
		prod.setDateCreated(new Date());
		prod.setDateUpdated(new Date());

		// String[] catIDs = categories.split(",");
		// for (String catid : catIDs) {
		// Category category = portalRepositoryRef.getCategoryByID(
		// Integer.valueOf(catid) );
		// prod.addCategory(category);
		// }

		// for (ProductExtensionItem e : extensions) {
		//
		// }
		//
		// String[] exts = extensions.split(",");
		// for (String extparmval : exts) {
		// String[] i = extparmval.split("=");
		// prod.addExtensionItem(i[0], i[1]);
		// }

		URI endpointUrl = uri.getBaseUri();

		String tempDir = METADATADIR + uuid + File.separator;
		
		Files.createDirectories(Paths.get(tempDir));			
				
		// If an icon is submitted
		if (image != null) {
			// Get the icon filename
			String imageFileNamePosted = AttachmentUtil.getFileName(image.getHeaders());
			logger.info("image = " + imageFileNamePosted);
			// If there is an icon name
			if (!imageFileNamePosted.equals("")) {
				// Save the icon File
				String imgfile = AttachmentUtil.saveFile(image, tempDir + imageFileNamePosted);
				logger.info("imgfile saved to = " + imgfile);
				// Save the icon file destination
				prod.setIconsrc(endpointUrl.toString().replace("http:", "") + "repo/images/" + uuid + "/"+ imageFileNamePosted);
			}
		}

		if (submittedFile != null) {
			// Get the filename
			String aFileNamePosted = AttachmentUtil.getFileName(submittedFile.getHeaders());
			logger.info("vxfFile = " + aFileNamePosted);
			// Is the filename is not an empty string
			if (!aFileNamePosted.equals("")) {
				String descriptorFilePath = AttachmentUtil.saveFile(submittedFile, tempDir + aFileNamePosted);
				// Set the package location in Product instance
				logger.info("vxffilepath saved to = " + descriptorFilePath);
				prod.setPackageLocation(endpointUrl.toString().replace("http:", "") + "repo/packages/" + uuid + "/"
						+ aFileNamePosted);
				// Read the descriptor file 
				File descriptorFile = new File(descriptorFilePath);
				// If it is a VxF Object
				logger.info("Before "+prod.getPackageLocation());
				if (prod instanceof VxFMetadata) {
					// If from the user input the VxFMetadata are declared as OSMvTWO
					try
					{
						if(((VxFMetadata) prod).getPackagingFormat().name().equals("OSMvTWO"))
						{
							logger.info("VxF OSMvTWO route");
							this.loadVxfMetadataFromVxFDescriptorFile(prod, descriptorFile);	
						}					
						else if(((VxFMetadata) prod).getPackagingFormat().name().equals("OSMvFOUR"))
						{
							logger.info("VxF OSMvFOUR route");	
							this.loadVxfMetadataFromOSMvFOURVxFDescriptorFile(prod, descriptorFile);
						}
						else if(((VxFMetadata) prod).getPackagingFormat().name().equals("OSMvFIVE"))
						{
							logger.info("VxF OSMvFIVE route");	
							this.loadVxfMetadataFromOSMvFIVEVxFDescriptorFile(prod, descriptorFile);
						}
					}
					catch (NullPointerException e)
					{
						e.printStackTrace();
						return null;
					}
					logger.info("After "+prod.getPackageLocation());
					// If prod is an NS Descriptor
				} else if (prod instanceof ExperimentMetadata) {
					try
					{
						if(((ExperimentMetadata) prod).getPackagingFormat().name().equals("OSMvTWO"))
						{
							logger.info("NSD OSMvTWO route");
							this.loadNSMetadataFromNSDescriptorFile(prod, descriptorFile);								
						}
						else if(((ExperimentMetadata) prod).getPackagingFormat().name().equals("OSMvFOUR"))
						{
							logger.info("NSD OSMvFOUR route");
							this.loadNSMetadataFromOSMvFOURNSDescriptorFile(prod, descriptorFile);															
						}
						else if(((ExperimentMetadata) prod).getPackagingFormat().name().equals("OSMvFIVE"))
						{
							logger.info("VxF OSMvFIVE route");	
							this.loadNSMetadataFromOSMvFIVENSDescriptorFile(prod, descriptorFile);															
						}
					}
					catch (NullPointerException e)
					{
						e.printStackTrace();
						return null;
					}					
				}
			}
		}
		// screenshots are provided during the call of the function
		List<Attachment> ss = screenshots;
		String screenshotsFilenames = "";
		int i = 1;
		for (Attachment shot : ss) {
			String shotFileNamePosted = AttachmentUtil.getFileName(shot.getHeaders());
			logger.info("Found screenshot image shotFileNamePosted = " + shotFileNamePosted);
			logger.info("shotFileNamePosted = " + shotFileNamePosted);
			if (!shotFileNamePosted.equals("")) {
				shotFileNamePosted = "shot" + i + "_" + shotFileNamePosted;
				String shotfilepath = AttachmentUtil.saveFile(shot, tempDir + shotFileNamePosted);
				logger.info("shotfilepath saved to = " + shotfilepath);
				shotfilepath = endpointUrl.toString().replace("http:", "") + "repo/images/" + uuid + "/"
						+ shotFileNamePosted;
				screenshotsFilenames += shotfilepath + ",";
				i++;
			}
		}
		if (screenshotsFilenames.length() > 0)
			screenshotsFilenames = screenshotsFilenames.substring(0, screenshotsFilenames.length() - 1);

		prod.setScreenshots(screenshotsFilenames);

		// we must replace given product categories with the ones from our DB
		for (Category c : prod.getCategories()) {
			Category catToUpdate = portalRepositoryRef.getCategoryByID(c.getId());
			// logger.info("BEFORE PROD SAVE, category "+catToUpdate.getName()+"
			// contains Products: "+ catToUpdate.getProducts().size() );
			prod.getCategories().set(prod.getCategories().indexOf(c), catToUpdate);

		}

		//if it's a VxF we need also to update the images that this VxF will use
		if (prod instanceof VxFMetadata) {
			VxFMetadata vxfm = (VxFMetadata) prod;
			for (VFImage vfimg : vxfm.getVfimagesVDU()) {
				vfimg.getUsedByVxFs().add(vxfm);
			}
		}

		
		// Save now vxf for User
		PortalUser vxfOwner = portalRepositoryRef.getUserByID(prod.getOwner().getId());
		vxfOwner.addProduct(prod);
		prod.setOwner(vxfOwner); // replace given owner with the one from our DB

		PortalUser owner = portalRepositoryRef.updateUserInfo(  vxfOwner);
		Product registeredProd = portalRepositoryRef.getProductByUUID(uuid);

		// now fix category references
		for (Category c : registeredProd.getCategories()) {
			Category catToUpdate = portalRepositoryRef.getCategoryByID(c.getId());
			catToUpdate.addProduct(registeredProd);
			portalRepositoryRef.updateCategoryInfo(catToUpdate);
		}		
		return registeredProd;
	}


	private void updateVxfMetadataFromVxFDescriptorFile(Product prevProduct,File aVxFdescriptorFile) throws IOException,NullPointerException
	{
		VNFExtractor vnfExtract = new VNFExtractor(aVxFdescriptorFile);
		Vnfd vnfd = vnfExtract.extractVnfdDescriptor();
		if (vnfd != null) {
			//on update we need to check if name and version are the same. Only then we will accept it
			if ( !prevProduct.getName().equals( vnfd.getId()) ||  !prevProduct.getVersion().equals( vnfd.getVersion() )  ){
				throw new IOException( "Name and version are not equal to existing descriptor. No updates were performed." );
			}							
			if ( ( (VxFMetadata) prevProduct).isCertified()  ) {
				throw new IOException( "Descriptor is already Validated and cannot change! No updates were performed." );								
			}
			
			//we must change this only if a descriptor was uploaded
			prevProduct.setName(vnfd.getId());
			prevProduct.setVersion(vnfd.getVersion());
			prevProduct.setVendor(vnfd.getVendor());
			prevProduct.setShortDescription(vnfd.getName());
			prevProduct.setLongDescription(vnfd.getDescription());
			((VxFMetadata) prevProduct).setValidationStatus( ValidationStatus.UNDER_REVIEW );
			//((VxFMetadata) prevProduct).setCertified( false ); //we need to Certify/Validate again this VxF since the descriptor is changed!..Normally we will never get here due to previous Exception
			
			
			
			for (VFImage img : ((VxFMetadata) prevProduct).getVfimagesVDU()) {
				logger.info("img.getUsedByVxFs().remove(prevProduct) = " + img.getUsedByVxFs().remove(prevProduct));
				portalRepositoryRef.updateVFImageInfo(img);
			}
			((VxFMetadata) prevProduct).getVfimagesVDU().clear();//clear previous referenced images							
			for (Vdu vdu : vnfd.getVdu()) {
				String imageName = vdu.getImage();
				if ( ( imageName != null) && (!imageName.equals("")) ){
					VFImage sm = portalRepositoryRef.getVFImageByName( imageName );
					if ( sm == null ){
						sm = new VFImage();
						sm.setName( imageName );
						PortalUser vfImagewner = portalRepositoryRef.getUserByID( prevProduct.getOwner().getId());
						sm.setOwner( vfImagewner );
						sm.setShortDescription( "Automatically created during vxf " + prevProduct.getName() + " submission. Owner must update." );
						String uuidVFImage = UUID.randomUUID().toString();
						sm.setUuid( uuidVFImage );
						sm.setDateCreated(new Date());
						sm = portalRepositoryRef.saveVFImage( sm );
					}
					
					if ( !((VxFMetadata) prevProduct).getVfimagesVDU().contains(sm) ){
						((VxFMetadata) prevProduct).getVfimagesVDU().add( sm );
						sm.getUsedByVxFs().add( ((VxFMetadata) prevProduct) );
						portalRepositoryRef.updateVFImageInfo( sm );
						
					}
				}
			}						
			
			VNFRequirements vr = new VNFRequirements(vnfd);
			prevProduct.setDescriptorHTML(vr.toHTML());
			prevProduct.setDescriptor(vnfExtract.getDescriptorYAMLfile());

			if (vnfExtract.getIconfilePath() != null) {

				String imageFileNamePosted = vnfd.getLogo();
				logger.info("image = " + imageFileNamePosted);
				if (!imageFileNamePosted.equals("")) {
					String imgfile = AttachmentUtil.saveFile(vnfExtract.getIconfilePath(),
							METADATADIR + prevProduct.getUuid() + File.separator + imageFileNamePosted);
					logger.info("imgfile saved to = " + imgfile);
					prevProduct.setIconsrc(uri.getBaseUri().toString().replace("http:", "") + "repo/images/"
							+ prevProduct.getUuid() + "/" + imageFileNamePosted);
				}
			}						
		}	
		else
		{
			throw new NullPointerException();
		}		
	}
	
	private void loadVxfMetadataFromVxFDescriptorFile(Product prod,File aVxFdescriptorFile) throws IOException,NullPointerException
	{
		// Create a vnfExtractor Object for the OSMvTWO file 
		VNFExtractor vnfExtract = new VNFExtractor(aVxFdescriptorFile);
		// Get the vnfd object out of the file info
		Vnfd vnfd = vnfExtract.extractVnfdDescriptor();
		if (vnfd != null) {							
			//*************LOAD THE Product Object from the VNFD Descriptor START************************************
			// Check if a vnfd with this id already exists in the DB
			Product existingvmf = portalRepositoryRef.getProductByName( vnfd.getId() );														
			if ( ( existingvmf != null  ) && ( existingvmf instanceof  VxFMetadata )) {
				throw new IOException( "Descriptor with same name already exists. No updates were performed. Please change the name of the descriptor" );				
			}
			// Get the name for the db							
			prod.setName(vnfd.getId());
			prod.setVersion(vnfd.getVersion());
			prod.setVendor(vnfd.getVendor());
			prod.setShortDescription(vnfd.getName());
			prod.setLongDescription(vnfd.getDescription());
			
			((VxFMetadata) prod).setValidationStatus( ValidationStatus.UNDER_REVIEW  );
			((VxFMetadata) prod).getVfimagesVDU().clear();//clear previous referenced images
			for (Vdu vdu : vnfd.getVdu()) {
				String imageName = vdu.getImage();
				if ( ( imageName != null) && (!imageName.equals("")) ){
					VFImage sm = portalRepositoryRef.getVFImageByName( imageName );
					if ( sm == null ){
						sm = new VFImage();
						sm.setName( imageName );
						PortalUser vfImagewner = portalRepositoryRef.getUserByID(prod.getOwner().getId());
						sm.setOwner( vfImagewner );
						sm.setShortDescription( "Automatically created during vxf " + prod.getName() + " submission. Owner must update." );
						String uuidVFImage = UUID.randomUUID().toString();
						sm.setUuid( uuidVFImage );
						sm.setDateCreated(new Date());
						sm = portalRepositoryRef.saveVFImage( sm );
					}
					((VxFMetadata) prod).getVfimagesVDU().add( sm );
					
				}
			}			
			
			// Get VNF Requirements from the vnfd
			VNFRequirements vr = new VNFRequirements(vnfd);
			// Store the requirements in HTML
			prod.setDescriptorHTML(vr.toHTML());
			// Store the YAML file
			prod.setDescriptor(vnfExtract.getDescriptorYAMLfile());
			// If we got an IconfilePath file from/through the vnfExtractor
			if (vnfExtract.getIconfilePath() != null) {
				String imageFileNamePosted = vnfd.getLogo();
				logger.info("image = " + imageFileNamePosted);
				// If the name is not empty
				if (!imageFileNamePosted.equals("")) {
					String imgfile = AttachmentUtil.saveFile(vnfExtract.getIconfilePath(),
							METADATADIR + prod.getUuid() + File.separator + imageFileNamePosted);
					logger.info("imgfile saved to = " + imgfile);
					prod.setIconsrc(uri.getBaseUri().toString().replace("http:", "") + "repo/images/" + prod.getUuid()
							+ "/" + imageFileNamePosted);
				}
			}
			//*************LOAD THE Product Object from the VNFD Descriptor END************************************
		}
		else
		{
			throw new NullPointerException();
		}
	}

	private void loadNSMetadataFromNSDescriptorFile(Product prod,File aNSDdescriptorFile) throws IOException,NullPointerException
	{
		// Create a nsExtractor Object for the OSMvTWO file 		
		NSExtractor nsExtract = new NSExtractor(aNSDdescriptorFile);
		// Get the nsd object out of the file info		
		Nsd ns = nsExtract.extractNsDescriptor();
		if (ns != null) {
			//*************LOAD THE Product Object from the NSD Descriptor START************************************
			// Check if a vnfd with this id already exists in the DB
			Product existingmff = portalRepositoryRef.getProductByName( ns.getId() );														
			if ( ( existingmff != null  ) && ( existingmff instanceof  ExperimentMetadata )) {
				throw new IOException( "Descriptor with same name already exists. No updates were performed." );			
			}
			prod.setName(ns.getId());
			prod.setVersion(ns.getVersion());
			prod.setVendor(ns.getVendor());
			prod.setShortDescription(ns.getName());
			prod.setLongDescription(ns.getDescription());
			// Get VNF Requirements from the vnfd			
			NSRequirements vr = new NSRequirements(ns);
			// Store the requirements in HTML			
			prod.setDescriptorHTML(vr.toHTML());
			// Store the YAML file			
			prod.setDescriptor(nsExtract.getDescriptorYAMLfile());
			
			for (ConstituentVnfd v : ns.getConstituentVnfd()) {
				ConstituentVxF cvxf = new ConstituentVxF();
				cvxf.setMembervnfIndex(v.getMemberVnfIndex().intValue()); // ok we will survive with
																			// this
				cvxf.setVnfdidRef(v.getVnfdIdRef());

				VxFMetadata vxf = (VxFMetadata) portalRepositoryRef.getProductByName(v.getVnfdIdRef());

				cvxf.setVxfref(vxf);

				((ExperimentMetadata) prod).getConstituentVxF().add(cvxf);
			}
			// If we got an IconfilePath file from/through the vnfExtractor
			if (nsExtract.getIconfilePath() != null) {

				String imageFileNamePosted = ns.getLogo();
				logger.info("image = " + imageFileNamePosted);
				// If the name is not empty				
				if (!imageFileNamePosted.equals("")) {
					String imgfile = AttachmentUtil.saveFile(nsExtract.getIconfilePath(),
							METADATADIR + prod.getUuid() + File.separator + imageFileNamePosted);
					logger.info("imgfile saved to = " + imgfile);
					prod.setIconsrc(uri.getBaseUri().toString().replace("http:", "") + "repo/images/" + prod.getUuid()
							+ "/" + imageFileNamePosted);
				}
			}
			//*************LOAD THE Product Object from the NSD Descriptor END************************************			
		} else {
			throw new NullPointerException();
		}
		
	}	

	private void loadNSMetadataFromOSMvFOURNSDescriptorFile(Product prod,File aNSDdescriptorFile) throws IOException,NullPointerException
	{
		// Create a nsExtractor Object for the OSMvTWO file 		
		OSM4NSExtractor nsExtract = new OSM4NSExtractor(aNSDdescriptorFile);
		// Get the nsd object out of the file info		
		ns.yang.nfvo.nsd.rev170228.nsd.catalog.Nsd ns = nsExtract.extractNsDescriptor();
		if (ns != null) {
			//*************LOAD THE Product Object from the NSD Descriptor START************************************
			// Check if a vnfd with this id already exists in the DB
			Product existingmff = portalRepositoryRef.getProductByName( ns.getAddedId() );														
			if ( ( existingmff != null  ) && ( existingmff instanceof  ExperimentMetadata )) {
				throw new IOException( "Descriptor with same name already exists. No updates were performed." );	
			}
			prod.setName(ns.getAddedId());
			prod.setVersion(ns.getVersion());
			prod.setVendor(ns.getVendor());
			prod.setShortDescription(ns.getName());
			prod.setLongDescription(ns.getDescription());
			// Get VNF Requirements from the vnfd			
			OSM4NSRequirements vr = new OSM4NSRequirements(ns);
			// Store the requirements in HTML			
			prod.setDescriptorHTML(vr.toHTML());
			// Store the YAML file			
			prod.setDescriptor(nsExtract.getDescriptorYAMLfile());
			
			for (ns.yang.nfvo.nsd.rev170228.nsd.constituent.vnfd.ConstituentVnfd v : ns.getConstituentVnfd()) {
				ConstituentVxF cvxf = new ConstituentVxF();
				cvxf.setMembervnfIndex(Integer.parseInt(v.getMemberVnfIndex())); 
				cvxf.setVnfdidRef(v.getVnfdIdRef());

				VxFMetadata vxf = (VxFMetadata) portalRepositoryRef.getProductByName(v.getVnfdIdRef());

				cvxf.setVxfref(vxf);

				((ExperimentMetadata) prod).getConstituentVxF().add(cvxf);
			}
			// If we got an IconfilePath file from/through the vnfExtractor
			if (nsExtract.getIconfilePath() != null) {

				String imageFileNamePosted = ns.getLogo();
				logger.info("image = " + imageFileNamePosted);
				// If the name is not empty				
				if (!imageFileNamePosted.equals("")) {
					String imgfile = AttachmentUtil.saveFile(nsExtract.getIconfilePath(),
							METADATADIR + prod.getUuid() + File.separator + imageFileNamePosted);
					logger.info("imgfile saved to = " + imgfile);
					prod.setIconsrc(uri.getBaseUri().toString().replace("http:", "") + "repo/images/" + prod.getUuid()
							+ "/" + imageFileNamePosted);
				}
			}
			//*************LOAD THE Product Object from the NSD Descriptor END************************************			
		} else {
			throw new NullPointerException();
		}
		
	}	

	private void loadNSMetadataFromOSMvFIVENSDescriptorFile(Product prod,File aNSDdescriptorFile) throws IOException,NullPointerException
	{
		// Create a nsExtractor Object for the OSMvTWO file 		
		OSM5NSExtractor nsExtract = new OSM5NSExtractor(aNSDdescriptorFile);
		// Get the nsd object out of the file info		
		osm5.ns.yang.nfvo.nsd.rev170228.nsd.catalog.Nsd ns = nsExtract.extractNsDescriptor();
		if (ns != null) {
			//*************LOAD THE Product Object from the NSD Descriptor START************************************
			// Check if a vnfd with this id already exists in the DB
			Product existingmff = portalRepositoryRef.getProductByName( ns.getAddedId() );														
			if ( ( existingmff != null  ) && ( existingmff instanceof  ExperimentMetadata )) {
				throw new IOException( "Descriptor with same name already exists. No updates were performed." );	
			}
			prod.setName(ns.getAddedId());
			prod.setVersion(ns.getVersion());
			prod.setVendor(ns.getVendor());
			prod.setShortDescription(ns.getName());
			prod.setLongDescription(ns.getDescription());
			// Get VNF Requirements from the vnfd			
			OSM5NSRequirements vr = new OSM5NSRequirements(ns);
			// Store the requirements in HTML			
			prod.setDescriptorHTML(vr.toHTML());
			// Store the YAML file			
			prod.setDescriptor(nsExtract.getDescriptorYAMLfile());
			
			for (osm5.ns.yang.nfvo.nsd.rev170228.nsd.constituent.vnfd.ConstituentVnfd v : ns.getConstituentVnfd()) {
				ConstituentVxF cvxf = new ConstituentVxF();
				cvxf.setMembervnfIndex(Integer.parseInt(v.getMemberVnfIndex())); 
				cvxf.setVnfdidRef(v.getVnfdIdRef());

				VxFMetadata vxf = (VxFMetadata) portalRepositoryRef.getProductByName(v.getVnfdIdRef());

				cvxf.setVxfref(vxf);

				((ExperimentMetadata) prod).getConstituentVxF().add(cvxf);
			}
			// If we got an IconfilePath file from/through the vnfExtractor
			if (nsExtract.getIconfilePath() != null) {

				String imageFileNamePosted = ns.getLogo();
				logger.info("image = " + imageFileNamePosted);
				// If the name is not empty				
				if (!imageFileNamePosted.equals("")) {
					String imgfile = AttachmentUtil.saveFile(nsExtract.getIconfilePath(),
							METADATADIR + prod.getUuid() + File.separator + imageFileNamePosted);
					logger.info("imgfile saved to = " + imgfile);
					prod.setIconsrc(uri.getBaseUri().toString().replace("http:", "") + "repo/images/" + prod.getUuid()
							+ "/" + imageFileNamePosted);
				}
			}
			//*************LOAD THE Product Object from the NSD Descriptor END************************************			
		} else {
			throw new NullPointerException();
		}
		
	}	
	
	private void loadVxfMetadataFromOSMvFOURVxFDescriptorFile(Product prod,File aVxFdescriptorFile) throws IOException, NullPointerException
	{
		// Create a vnfExtractor Object for the OSMvFOUR file 
		OSM4VNFDExtractor vnfExtract = new OSM4VNFDExtractor(aVxFdescriptorFile);
		// Get the vnfd object out of the file info
		ns.yang.nfvo.vnfd.rev170228.vnfd.catalog.Vnfd vnfd = vnfExtract.extractVnfdDescriptor();
		if (vnfd != null) {							
			//*************LOAD THE Product Object from the VNFD Descriptor START************************************
			// Check if a vnfd with this id already exists in the DB
			Product existingvmf = portalRepositoryRef.getProductByName( vnfd.getAddedId());														
			if ( ( existingvmf != null  ) && ( existingvmf instanceof  VxFMetadata )) {
				throw new IOException( "Descriptor with same name already exists. No updates were performed. Please change the name of the descriptor" );				
			}
			// Get the name for the db							
			prod.setName(vnfd.getAddedId());
			prod.setVersion(vnfd.getVersion());
			prod.setVendor(vnfd.getVendor());
			prod.setShortDescription(vnfd.getName());
			prod.setLongDescription(vnfd.getDescription());
			
			((VxFMetadata) prod).setValidationStatus( ValidationStatus.UNDER_REVIEW  );
			((VxFMetadata) prod).getVfimagesVDU().clear();//clear previous referenced images
			for (yang.vnfd.vnfd.descriptor.Vdu vdu : vnfd.getVdu()) {
				String imageName = vdu.getImage();
				if ( ( imageName != null) && (!imageName.equals("")) ){
					VFImage sm = portalRepositoryRef.getVFImageByName( imageName );
					if ( sm == null ){
						sm = new VFImage();
						sm.setName( imageName );
						PortalUser vfImagewner = portalRepositoryRef.getUserByID(prod.getOwner().getId());
						sm.setOwner( vfImagewner );
						sm.setShortDescription( "Automatically created during vxf " + prod.getName() + " submission. Owner must update." );
						String uuidVFImage = UUID.randomUUID().toString();
						sm.setUuid( uuidVFImage );
						sm.setDateCreated(new Date());
						sm = portalRepositoryRef.saveVFImage( sm );
					}
					((VxFMetadata) prod).getVfimagesVDU().add( sm );
					
				}
			}			
			
			// Get VNF Requirements from the vnfd
			OSM4VNFRequirements vr = new OSM4VNFRequirements(vnfd);
			// Store the requirements in HTML
			prod.setDescriptorHTML(vr.toHTML());
			// Store the YAML file
			prod.setDescriptor(vnfExtract.getDescriptorYAMLfile());
			// If we got an IconfilePath file from/through the vnfExtractor
			if (vnfExtract.getIconfilePath() != null) {
				String imageFileNamePosted = vnfd.getLogo();
				logger.info("image = " + imageFileNamePosted);
				// If the name is not empty
				if (!imageFileNamePosted.equals("")) {
					String imgfile = AttachmentUtil.saveFile(vnfExtract.getIconfilePath(),
							METADATADIR + prod.getUuid() + File.separator + imageFileNamePosted);
					logger.info("imgfile saved to = " + imgfile);
					prod.setIconsrc(uri.getBaseUri().toString().replace("http:", "") + "repo/images/" + prod.getUuid()
							+ "/" + imageFileNamePosted);
				}
			}
			//*************LOAD THE Product Object from the VNFD Descriptor END************************************
		}
		else
		{
			throw new NullPointerException();
		}
	}

	private void loadVxfMetadataFromOSMvFIVEVxFDescriptorFile(Product prod,File aVxFdescriptorFile) throws IOException, NullPointerException
	{
		// Create a vnfExtractor Object for the OSMvFIVE file 
		OSM5VNFDExtractor vnfExtract = new OSM5VNFDExtractor(aVxFdescriptorFile);
		// Get the vnfd object out of the file info
		osm5.ns.yang.nfvo.vnfd.rev170228.vnfd.catalog.Vnfd vnfd = vnfExtract.extractVnfdDescriptor();
		if (vnfd != null) {							
			//*************LOAD THE Product Object from the VNFD Descriptor START************************************
			// Check if a vnfd with this id already exists in the DB
			Product existingvmf = portalRepositoryRef.getProductByName( vnfd.getAddedId());														
			if ( ( existingvmf != null  ) && ( existingvmf instanceof  VxFMetadata )) {
				throw new IOException( "Descriptor with same name already exists. No updates were performed. Please change the name of the descriptor" );				
			}
			// Get the name for the db							
			prod.setName(vnfd.getAddedId());
			prod.setVersion(vnfd.getVersion());
			prod.setVendor(vnfd.getVendor());
			prod.setShortDescription(vnfd.getName());
			prod.setLongDescription(vnfd.getDescription());
			
			((VxFMetadata) prod).setValidationStatus( ValidationStatus.UNDER_REVIEW  );
			((VxFMetadata) prod).getVfimagesVDU().clear();//clear previous referenced images
			for (osm5.ns.riftware._1._0.vnfd.base.rev170228.vnfd.descriptor.Vdu vdu : vnfd.getVdu()) {
				String imageName = vdu.getImage();
				if ( ( imageName != null) && (!imageName.equals("")) ){
					VFImage sm = portalRepositoryRef.getVFImageByName( imageName );
					if ( sm == null ){
						sm = new VFImage();
						sm.setName( imageName );
						PortalUser vfImagewner = portalRepositoryRef.getUserByID(prod.getOwner().getId());
						sm.setOwner( vfImagewner );
						sm.setShortDescription( "Automatically created during vxf " + prod.getName() + " submission. Owner must update." );
						String uuidVFImage = UUID.randomUUID().toString();
						sm.setUuid( uuidVFImage );
						sm.setDateCreated(new Date());
						sm = portalRepositoryRef.saveVFImage( sm );
					}
					((VxFMetadata) prod).getVfimagesVDU().add( sm );
					
				}
			}			
			
			// Get VNF Requirements from the vnfd
			OSM5VNFRequirements vr = new OSM5VNFRequirements(vnfd);
			// Store the requirements in HTML
			prod.setDescriptorHTML(vr.toHTML());
			// Store the YAML file
			prod.setDescriptor(vnfExtract.getDescriptorYAMLfile());
			// If we got an IconfilePath file from/through the vnfExtractor
			if (vnfExtract.getIconfilePath() != null) {
				String imageFileNamePosted = vnfd.getLogo();
				logger.info("image = " + imageFileNamePosted);
				// If the name is not empty
				if (!imageFileNamePosted.equals("")) {
					String imgfile = AttachmentUtil.saveFile(vnfExtract.getIconfilePath(),
							METADATADIR + prod.getUuid() + File.separator + imageFileNamePosted);
					logger.info("imgfile saved to = " + imgfile);
					prod.setIconsrc(uri.getBaseUri().toString().replace("http:", "") + "repo/images/" + prod.getUuid()
							+ "/" + imageFileNamePosted);
				}
			}
			//*************LOAD THE Product Object from the VNFD Descriptor END************************************
		}
		else
		{
			throw new NullPointerException();
		}
	}
	
	private void updateVxfMetadataFromOSMvFOURVxFDescriptorFile(Product prevProduct,File aVxFdescriptorFile) throws IOException, NullPointerException
	{
		// Create a vnfExtractor Object for the OSMvFOUR file 
		OSM4VNFDExtractor vnfExtract = new OSM4VNFDExtractor(aVxFdescriptorFile);
		// Get the vnfd object out of the file info
		ns.yang.nfvo.vnfd.rev170228.vnfd.catalog.Vnfd vnfd = vnfExtract.extractVnfdDescriptor();
		if (vnfd != null) {							
			//on update we need to check if name and version are the same. Only then we will accept it
			if ( !prevProduct.getName().equals( vnfd.getId()) ||  !prevProduct.getVersion().equals( vnfd.getVersion() )  ){
				throw new IOException( "Name and version are not equal to existing descriptor. No updates were performed." );
			}							
			if ( ( (VxFMetadata) prevProduct).isCertified()  ) {
				throw new IOException( "Descriptor is already Validated and cannot change! No updates were performed." );								
			}
			
			// Get the name for the db							
			prevProduct.setName(vnfd.getAddedId());
			prevProduct.setVersion(vnfd.getVersion());
			prevProduct.setVendor(vnfd.getVendor());
			prevProduct.setShortDescription(vnfd.getName());
			prevProduct.setLongDescription(vnfd.getDescription());
			
			((VxFMetadata) prevProduct).setValidationStatus( ValidationStatus.UNDER_REVIEW  );
			
			
			
			for (VFImage img : ((VxFMetadata) prevProduct).getVfimagesVDU()) {
				logger.info("img.getUsedByVxFs().remove(prevProduct) = " + img.getUsedByVxFs().remove(prevProduct));
				portalRepositoryRef.updateVFImageInfo(img);
			}			
			((VxFMetadata) prevProduct).getVfimagesVDU().clear();//clear previous referenced images
			for (yang.vnfd.vnfd.descriptor.Vdu vdu : vnfd.getVdu()) {
				String imageName = vdu.getImage();
				if ( ( imageName != null) && (!imageName.equals("")) ){
					VFImage sm = portalRepositoryRef.getVFImageByName( imageName );
					if ( sm == null ){
						sm = new VFImage();
						sm.setName( imageName );
						PortalUser vfImagewner = portalRepositoryRef.getUserByID(prevProduct.getOwner().getId());
						sm.setOwner( vfImagewner );
						sm.setShortDescription( "Automatically created during vxf " + prevProduct.getName() + " submission. Owner must update." );
						String uuidVFImage = UUID.randomUUID().toString();
						sm.setUuid( uuidVFImage );
						sm.setDateCreated(new Date());
						sm = portalRepositoryRef.saveVFImage( sm );
					}
					if ( !((VxFMetadata) prevProduct).getVfimagesVDU().contains(sm) ){
						((VxFMetadata) prevProduct).getVfimagesVDU().add( sm );
						sm.getUsedByVxFs().add( ((VxFMetadata) prevProduct) );
						portalRepositoryRef.updateVFImageInfo( sm );
						
					}
					
				}
			}			
			
			// Get VNF Requirements from the vnfd
			OSM4VNFRequirements vr = new OSM4VNFRequirements(vnfd);
			// Store the requirements in HTML
			prevProduct.setDescriptorHTML(vr.toHTML());
			// Store the YAML file
			prevProduct.setDescriptor(vnfExtract.getDescriptorYAMLfile());
			// If we got an IconfilePath file from/through the vnfExtractor
			if (vnfExtract.getIconfilePath() != null) {
				String imageFileNamePosted = vnfd.getLogo();
				logger.info("image = " + imageFileNamePosted);
				// If the name is not empty
				if (!imageFileNamePosted.equals("")) {
					String imgfile = AttachmentUtil.saveFile(vnfExtract.getIconfilePath(),
							METADATADIR + prevProduct.getUuid() + File.separator + imageFileNamePosted);
					logger.info("imgfile saved to = " + imgfile);
					prevProduct.setIconsrc(uri.getBaseUri().toString().replace("http:", "") + "repo/images/" + prevProduct.getUuid()
							+ "/" + imageFileNamePosted);
				}
			}
			//*************LOAD THE Product Object from the VNFD Descriptor END************************************
		}
		else
		{
			throw new NullPointerException();
		}
	}

	private void updateVxfMetadataFromOSMvFIVEVxFDescriptorFile(Product prevProduct,File aVxFdescriptorFile) throws IOException, NullPointerException
	{
		// Create a vnfExtractor Object for the OSMvFIVE file 
		OSM5VNFDExtractor vnfExtract = new OSM5VNFDExtractor(aVxFdescriptorFile);
		// Get the vnfd object out of the file info
		osm5.ns.yang.nfvo.vnfd.rev170228.vnfd.catalog.Vnfd vnfd = vnfExtract.extractVnfdDescriptor();
		if (vnfd != null) {							
			//on update we need to check if name and version are the same. Only then we will accept it
			if ( !prevProduct.getName().equals( vnfd.getId()) ||  !prevProduct.getVersion().equals( vnfd.getVersion() )  ){
				throw new IOException( "Name and version are not equal to existing descriptor. No updates were performed." );
			}							
			if ( ( (VxFMetadata) prevProduct).isCertified()  ) {
				throw new IOException( "Descriptor is already Validated and cannot change! No updates were performed." );								
			}
			
			// Get the name for the db							
			prevProduct.setName(vnfd.getAddedId());
			prevProduct.setVersion(vnfd.getVersion());
			prevProduct.setVendor(vnfd.getVendor());
			prevProduct.setShortDescription(vnfd.getName());
			prevProduct.setLongDescription(vnfd.getDescription());
			
			((VxFMetadata) prevProduct).setValidationStatus( ValidationStatus.UNDER_REVIEW  );
			
			
			
			for (VFImage img : ((VxFMetadata) prevProduct).getVfimagesVDU()) {
				logger.info("img.getUsedByVxFs().remove(prevProduct) = " + img.getUsedByVxFs().remove(prevProduct));
				portalRepositoryRef.updateVFImageInfo(img);
			}			
			((VxFMetadata) prevProduct).getVfimagesVDU().clear();//clear previous referenced images
			for (osm5.ns.riftware._1._0.vnfd.base.rev170228.vnfd.descriptor.Vdu vdu : vnfd.getVdu()) {
				String imageName = vdu.getImage();
				if ( ( imageName != null) && (!imageName.equals("")) ){
					VFImage sm = portalRepositoryRef.getVFImageByName( imageName );
					if ( sm == null ){
						sm = new VFImage();
						sm.setName( imageName );
						PortalUser vfImagewner = portalRepositoryRef.getUserByID(prevProduct.getOwner().getId());
						sm.setOwner( vfImagewner );
						sm.setShortDescription( "Automatically created during vxf " + prevProduct.getName() + " submission. Owner must update." );
						String uuidVFImage = UUID.randomUUID().toString();
						sm.setUuid( uuidVFImage );
						sm.setDateCreated(new Date());
						sm = portalRepositoryRef.saveVFImage( sm );
					}
					if ( !((VxFMetadata) prevProduct).getVfimagesVDU().contains(sm) ){
						((VxFMetadata) prevProduct).getVfimagesVDU().add( sm );
						sm.getUsedByVxFs().add( ((VxFMetadata) prevProduct) );
						portalRepositoryRef.updateVFImageInfo( sm );
						
					}
					
				}
			}			
			
			// Get VNF Requirements from the vnfd
			OSM5VNFRequirements vr = new OSM5VNFRequirements(vnfd);
			// Store the requirements in HTML
			prevProduct.setDescriptorHTML(vr.toHTML());
			// Store the YAML file
			prevProduct.setDescriptor(vnfExtract.getDescriptorYAMLfile());
			// If we got an IconfilePath file from/through the vnfExtractor
			if (vnfExtract.getIconfilePath() != null) {
				String imageFileNamePosted = vnfd.getLogo();
				logger.info("image = " + imageFileNamePosted);
				// If the name is not empty
				if (!imageFileNamePosted.equals("")) {
					String imgfile = AttachmentUtil.saveFile(vnfExtract.getIconfilePath(),
							METADATADIR + prevProduct.getUuid() + File.separator + imageFileNamePosted);
					logger.info("imgfile saved to = " + imgfile);
					prevProduct.setIconsrc(uri.getBaseUri().toString().replace("http:", "") + "repo/images/" + prevProduct.getUuid()
							+ "/" + imageFileNamePosted);
				}
			}
			//*************LOAD THE Product Object from the VNFD Descriptor END************************************
		}
		else
		{
			throw new NullPointerException();
		}
	}
	
	/******************* VxFs API ***********************/

	@GET
	@Path("/vxfs")
	@Produces("application/json")
	public Response getAllVxFs(@QueryParam("categoryid") Long categoryid) {
		
		logger.info("getVxFs categoryid=" + categoryid);

		List<VxFMetadata> vxfs = portalRepositoryRef.getVxFs(categoryid, true);

//		/** cut fields to optimize response payload */
//		for (VxFMetadata vxFMetadata : vxfs) {
//			vxFMetadata.setDescriptorHTML( null );
//			vxFMetadata.setDescriptor( null );
//			vxFMetadata.setVxfOnBoardedDescriptors( null );
//		}
		Response res = Response.ok().entity(vxfs).build();
		return res;

	}
	
	
	

	@GET
	@Path("/admin/vxfs")
	@Produces("application/json")
	public Response getVxFs(@QueryParam("categoryid") Long categoryid) {
		logger.info("getVxFs categoryid=" + categoryid);
		
//		long time0 = System.currentTimeMillis();
//		System.out.println(  "Time 0: "+ time0 );
		

		PortalUser u = portalRepositoryRef.getUserBySessionID(ws.getHttpServletRequest().getSession().getId());

		if (u != null) {
			List<VxFMetadata> vxfs;

			if (u.getRoles().contains(UserRoleType.PORTALADMIN) ) {
				vxfs = portalRepositoryRef.getVxFs(categoryid, false);
			} else {
				vxfs = portalRepositoryRef.getVxFsByUserID((long) u.getId());
			}


//			for (VxFMetadata vxFMetadata : vxfs) {
//				vxFMetadata.setDescriptorHTML( null );
//				vxFMetadata.setDescriptor( null );
//				vxFMetadata.setVxfOnBoardedDescriptors( null );
//			}
//			
//			System.out.println(  "Time 1: "+  (System.currentTimeMillis() - time0) );
			Response res = Response.ok().entity(vxfs).build();
//			System.out.println(  "Time 2: "+  (System.currentTimeMillis() - time0) );
			
			
			return res;

		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("User not found in portal registry or not logged in");
			throw new WebApplicationException(builder.build());
		}

	}

	@POST
	@Path("/admin/vxfs/")
	@Consumes("multipart/form-data")
	public Response addVxFMetadata(List<Attachment> ats) {

		PortalUser u = portalRepositoryRef.getUserBySessionID(ws.getHttpServletRequest().getSession().getId());

		if (u == null) {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("User not found in portal registry or not logged in ");
			throw new WebApplicationException(builder.build());
		}

		VxFMetadata vxf = null;
		
		String emsg = "";

		try {
			MappingJsonFactory factory = new MappingJsonFactory();
			// Parse ats Object
			JsonParser parser = factory.createJsonParser(AttachmentUtil.getAttachmentStringValue("vxf", ats));
			// Parse ats Object to VxFMetadata Instance vxf.
			vxf = parser.readValueAs(VxFMetadata.class);
			logger.info("Received @POST for vxf : " + vxf.getName());
			logger.info("Received @POST for vxf.extensions : " + vxf.getExtensions());
			vxf = (VxFMetadata) addNewProductData(vxf,
					AttachmentUtil.getAttachmentByName("prodIcon", ats), AttachmentUtil.getAttachmentByName("prodFile", ats),
					AttachmentUtil.getListOfAttachmentsByName("screenshots", ats));				
		} catch (JsonProcessingException e) {
			vxf = null;
			e.printStackTrace();
			logger.error( e.getMessage() );
			emsg =  e.getMessage();
		} catch (IOException e) {
			vxf = null;
			e.printStackTrace();
			logger.error( e.getMessage() );
			emsg =  e.getMessage();
		}


		if (vxf != null) {		
			//======================================================
			// AUTOMATIC ONBOARDING PROCESS -START
			// Need to select MANO Provider, convert vxfMetadata to VxFOnBoardedDescriptor and pass it as an input.
			
			// Get the MANO providers which are set for automatic onboarding
			
			List<MANOprovider> MANOprovidersEnabledForOnboarding=portalRepositoryRef.getMANOprovidersEnabledForOnboarding();
			if(MANOprovidersEnabledForOnboarding.size()>0 && vxf.getPackagingFormat() == PackagingFormat.OSMvFOUR)
			{
				for(MANOprovider mp : MANOprovidersEnabledForOnboarding)
				{
					//Create VxfOnboardedDescriptor
					VxFOnBoardedDescriptor obd = new VxFOnBoardedDescriptor();
					// Get the first one for now			
					obd.setObMANOprovider(mp);
					obd.setUuid( UUID.randomUUID().toString() );
					VxFMetadata refVxF =  ( VxFMetadata )portalRepositoryRef.getProductByID( vxf.getId() );
					// Fill the VxFMetadata of VxFOnBoardedDescriptor
					obd.setVxf( refVxF );
					// Update the VxFMetadata Object with the obd Object
					refVxF.getVxfOnBoardedDescriptors().add( obd ) ;				
					
					// ???????
					obd.setVxf( refVxF );
					
					// save product
					refVxF = (VxFMetadata) portalRepositoryRef.updateProductInfo( refVxF );					
					
					// save VxFonBoardedDescriptor or not ???
					obd = portalRepositoryRef.updateVxFOnBoardedDescriptor(obd);
					
					//set proper scheme (http or https)
					MANOController.setHTTPSCHEME( ws.getHttpServletRequest().getRequestURL().toString()  );
					
					if ( obd.getVxf().getOwner() == null ) {
						logger.error( " ========> obd.getVxf().getOwner() == null " );
					}
					
					// Send the message for automatic onboarding
					BusController.getInstance().onBoardVxFAdded( obd.getId() );
				}
			}
			if(MANOprovidersEnabledForOnboarding.size()>0 && vxf.getPackagingFormat() == PackagingFormat.OSMvFIVE)
			{
				for(MANOprovider mp : MANOprovidersEnabledForOnboarding)
				{
					//Create VxfOnboardedDescriptor
					VxFOnBoardedDescriptor obd = new VxFOnBoardedDescriptor();
					// Get the first one for now			
					obd.setObMANOprovider(mp);
					obd.setUuid( UUID.randomUUID().toString() );
					VxFMetadata refVxF =  ( VxFMetadata )portalRepositoryRef.getProductByID( vxf.getId() );
					// Fill the VxFMetadata of VxFOnBoardedDescriptor
					obd.setVxf( refVxF );
					// Update the VxFMetadata Object with the obd Object
					refVxF.getVxfOnBoardedDescriptors().add( obd ) ;				
					
					// ???????
					obd.setVxf( refVxF );
					
					// save product
					refVxF = (VxFMetadata) portalRepositoryRef.updateProductInfo( refVxF );					
					
					// save VxFonBoardedDescriptor or not ???
					obd = portalRepositoryRef.updateVxFOnBoardedDescriptor(obd);
					
					//set proper scheme (http or https)
					MANOController.setHTTPSCHEME( ws.getHttpServletRequest().getRequestURL().toString()  );
					
					if ( obd.getVxf().getOwner() == null ) {
						logger.error( " ========> obd.getVxf().getOwner() == null " );
					}
					
					// Send the message for automatic onboarding
					BusController.getInstance().onBoardVxFAdded( obd.getId() );
				}
			}
			// AUTOMATIC ONBOARDING PROCESS -END
			//======================================================
			VxFMetadata vxfr = (VxFMetadata) portalRepositoryRef.getProductByID( vxf.getId()) ; //rereading this, seems to keep the DB connection
			BusController.getInstance().validateVxF(vxfr.getId());			
			return Response.ok().entity(vxfr).build();
		} else {
			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
			builder.entity( new ErrorMsg( "Requested entity cannot be installed. " + emsg )  );						
			throw new WebApplicationException(builder.build());			
		}

	}

	@PUT
	@Path("/admin/vxfs/{bid}")
	@Consumes("multipart/form-data")
	public Response updateVxFMetadata(@PathParam("bid") int bid, List<Attachment> ats) {
		
		

		VxFMetadata vxf = null;
		String emsg = "";

		try {
			MappingJsonFactory factory = new MappingJsonFactory();
			JsonParser parser = factory.createJsonParser(AttachmentUtil.getAttachmentStringValue("vxf", ats));
			vxf = parser.readValueAs(VxFMetadata.class);
			
			if ( !checkUserIDorIsAdmin( vxf.getOwner().getId() ) ){
				 return Response.status(Status.FORBIDDEN ).build();
			}
			

			logger.info("Received @PUT for vxf : " + vxf.getName());
			logger.info("Received @PUT for vxf.extensions : " + vxf.getExtensions());

			vxf = (VxFMetadata) updateProductMetadata(vxf, AttachmentUtil.getAttachmentByName("prodIcon", ats),
					AttachmentUtil.getAttachmentByName("prodFile", ats), AttachmentUtil.getListOfAttachmentsByName("screenshots", ats));
			
			
		} catch (JsonProcessingException e) {
			vxf = null;
			e.printStackTrace();
			logger.error( e.getMessage() );
			emsg =  e.getMessage();
		} catch (IOException e) {
			vxf = null;
			e.printStackTrace();
			logger.error( e.getMessage() );
			emsg =  e.getMessage();
		}
		
		if (vxf != null) { 

			
			BusController.getInstance().updatedVxF(vxf.getId());
			//notify only if validation changed

			if ( AttachmentUtil.getAttachmentByName("prodFile", ats) != null ) { //if the descriptor changed then we must re-trigger validation
				Attachment prodFile =  AttachmentUtil.getAttachmentByName("prodFile", ats);
				String vxfFileNamePosted = AttachmentUtil.getFileName(prodFile.getHeaders());
				if ( !vxfFileNamePosted.equals("unknown") ){
					
					BusController.getInstance().validateVxF(vxf.getId());
				}
			}
			 
			return Response.ok().entity(vxf).build();
		} else {
			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
			builder.entity( new ErrorMsg( "Requested entity cannot be installed. " + emsg )  );		
			throw new WebApplicationException(builder.build());
		}

	}

	// VxFs related API

	private Product updateProductMetadata(Product prod, Attachment image, Attachment prodFile,
			List<Attachment> screenshots) throws IOException {

		logger.info("userid = " + prod.getOwner().getId());
		logger.info("prodname = " + prod.getName());
		logger.info("prodid = " + prod.getId());

		logger.info("produuid = " + prod.getUuid());
		logger.info("version = " + prod.getVersion());
		logger.info("shortDescription = " + prod.getShortDescription());
		logger.info("longDescription = " + prod.getLongDescription());

		
		
		// get User
//		PortalUser vxfOwner = portalRepositoryRef.getUserByID(prod.getOwner().getId());
//		prod.setOwner(vxfOwner); // replace given owner with the one from our DB


		// first remove all references of the product from the previous
		// categories
		Product prevProduct = (Product) portalRepositoryRef.getProductByID(prod.getId());
		prevProduct.setDateUpdated(new Date());
		
		for (Category c : prevProduct.getCategories()) {
			// logger.info("Will remove product "+prodPreUpdate.getName()+ ",
			// from Previous Category "+c.getName() );
			c.removeProduct(prevProduct);
			portalRepositoryRef.updateCategoryInfo(c);
		}
		prevProduct.getCategories().clear();

		// we must replace API given product categories with the ones from our
		// DB
		for (Category c : prod.getCategories()) {
			Category catToUpdate = portalRepositoryRef.getCategoryByID(c.getId());
			// logger.info("BEFORE PROD SAVE, category "+catToUpdate.getName()+"
			// contains Products: "+ catToUpdate.getProducts().size() );
			//prod.getCategories().set(prod.getCategories().indexOf(c), catToUpdate);
			prevProduct.getCategories().add(catToUpdate);
		}

		URI endpointUrl = uri.getBaseUri();

		String tempDir = METADATADIR + prevProduct.getUuid() + File.separator;

		Files.createDirectories(Paths.get(tempDir));

		// If an icon is submitted
		if (image != null) {
			// Get the icon filename			
			String imageFileNamePosted = AttachmentUtil.getFileName(image.getHeaders());
			logger.info("image = " + imageFileNamePosted);
			// If there is an icon name			
			if (!imageFileNamePosted.equals("unknown")) {
				// Save the icon File				
				String imgfile = AttachmentUtil.saveFile(image, tempDir + imageFileNamePosted);
				logger.info("imgfile saved to = " + imgfile);
				// Save the icon file destination				
				prevProduct.setIconsrc(endpointUrl.toString().replace("http:", "") + "repo/images/" + prevProduct.getUuid() + "/"
						+ imageFileNamePosted);
			}
		}
		
		
		if ( prevProduct instanceof VxFMetadata) 
		{
			((VxFMetadata) prevProduct).setPackagingFormat( ((VxFMetadata) prod).getPackagingFormat() );
			prevProduct.setTermsOfUse( prod.getTermsOfUse() );
			prevProduct.setPublished( prod.isPublished() );
			((VxFMetadata) prevProduct).setCertifiedBy( ((VxFMetadata) prod).getCertifiedBy() );
			((VxFMetadata) prevProduct).getSupportedMANOPlatforms().clear();
			for (MANOplatform mp : ((VxFMetadata) prod).getSupportedMANOPlatforms()) {
				MANOplatform mpdb = portalRepositoryRef.getMANOplatformByID( mp.getId());
				((VxFMetadata) prevProduct).getSupportedMANOPlatforms().add( mpdb );
			}
			//if ( !((VxFMetadata) prevProduct).isCertified() ){ //allow for now to change state
				((VxFMetadata) prevProduct).setCertified( ((VxFMetadata) prod).isCertified() );
			//}
			((VxFMetadata) prevProduct).setCertifiedBy(((VxFMetadata) prod).getCertifiedBy() );
						
			
			
		} else if ( prevProduct instanceof ExperimentMetadata) {

			prevProduct.setTermsOfUse( prod.getTermsOfUse() );
			prevProduct.setPublished( prod.isPublished() );
			//if ( !((ExperimentMetadata) prevProduct).isValid() ){  //allow for now to change state
				((ExperimentMetadata) prevProduct).setValid( ((ExperimentMetadata) prod).isValid() );
			//}
			((ExperimentMetadata) prevProduct).setPackagingFormat( ((ExperimentMetadata) prod).getPackagingFormat() );
		}

		if (prodFile != null) {
			// Get the filename			
			String vxfFileNamePosted = AttachmentUtil.getFileName(prodFile.getHeaders());
			logger.info("vxfFile = " + vxfFileNamePosted);
			// Is the filename is not an empty string			
			if (!vxfFileNamePosted.equals("unknown")) {
				String vxffilepath = AttachmentUtil.saveFile(prodFile, tempDir + vxfFileNamePosted);
				// Set the package location in Product instance				
				logger.info("vxffilepath saved to = " + vxffilepath);
				prevProduct.setPackageLocation(endpointUrl.toString().replace("http:", "") + "repo/packages/"
						+ prevProduct.getUuid() + "/" + vxfFileNamePosted);
				// Read the descriptor file 
				File descriptorFile = new File(vxffilepath);
				// If it is a VxF Object
				
				if ( prevProduct instanceof VxFMetadata) {
					try
					{
						if(((VxFMetadata) prod).getPackagingFormat().name().equals("OSMvTWO"))
						{
							logger.info("VxF OSMvTWO route");
							this.updateVxfMetadataFromVxFDescriptorFile(prevProduct, descriptorFile);	
						}					
						else if(((VxFMetadata) prod).getPackagingFormat().name().equals("OSMvFOUR"))
						{
							logger.info("VxF OSMvFOUR route");	
							this.updateVxfMetadataFromOSMvFOURVxFDescriptorFile(prevProduct, descriptorFile);
						}
						else if(((VxFMetadata) prod).getPackagingFormat().name().equals("OSMvFIVE"))
						{
							logger.info("VxF OSMvFIVE route");	
							this.updateVxfMetadataFromOSMvFIVEVxFDescriptorFile(prevProduct, descriptorFile);
						}
					}
					catch (NullPointerException e)
					{
						return null;
					}
					logger.info("After "+prod.getPackageLocation());
					// If prod is an NS Descriptor														
				} else if ( prevProduct instanceof ExperimentMetadata) {
					try
					{
						if(((ExperimentMetadata) prod).getPackagingFormat().name().equals("OSMvTWO"))
						{
							logger.info("NSD OSMvTWO route");
							this.updateNSMetadataFromNSDescriptorFile(prevProduct, descriptorFile);								
						}
						else if(((ExperimentMetadata) prod).getPackagingFormat().name().equals("OSMvFOUR"))
						{
							logger.info("NSD OSMvFOUR route");
							this.updateNSMetadataFromOSMvFOURNSDescriptorFile(prevProduct, descriptorFile);															
						}
						else if(((ExperimentMetadata) prod).getPackagingFormat().name().equals("OSMvFIVE"))
						{
							logger.info("NSD OSMvFIVE route");
							this.updateNSMetadataFromOSMvFIVENSDescriptorFile(prevProduct, descriptorFile);															
						}
					}
					catch (NullPointerException e)
					{
						return null;
					}					
					
				}

			}
		}

			List<Attachment> ss = screenshots;
			String screenshotsFilenames = "";
			int i = 1;
			for (Attachment shot : ss) {
				String shotFileNamePosted = AttachmentUtil.getFileName(shot.getHeaders());
				logger.info("Found screenshot image shotFileNamePosted = " + shotFileNamePosted);
				logger.info("shotFileNamePosted = " + shotFileNamePosted);
				if (!shotFileNamePosted.equals("")) {
					shotFileNamePosted = "shot" + i + "_" + shotFileNamePosted;
					String shotfilepath = AttachmentUtil.saveFile(shot, tempDir + shotFileNamePosted);
					logger.info("shotfilepath saved to = " + shotfilepath);
					shotfilepath = endpointUrl.toString().replace("http:", "") + "repo/images/" + prevProduct.getUuid() + "/"
							+ shotFileNamePosted;
					screenshotsFilenames += shotfilepath + ",";
					i++;
				}
			}
			if (screenshotsFilenames.length() > 0)
				screenshotsFilenames = screenshotsFilenames.substring(0, screenshotsFilenames.length() - 1);

			prevProduct.setScreenshots(screenshotsFilenames);

		
			
		

		// save product
		prevProduct = portalRepositoryRef.updateProductInfo( prevProduct );

		// now fix category product references
		for (Category catToUpdate : prevProduct.getCategories()) {
			//Product p = portalRepositoryRef.getProductByID(prod.getId());
			Category c = portalRepositoryRef.getCategoryByID( catToUpdate.getId() );
			c.addProduct( prevProduct );
			portalRepositoryRef.updateCategoryInfo(c);
		}
		

//		if (vxfOwner.getProductById(prod.getId()) == null)
//			vxfOwner.addProduct(prod);
//		portalRepositoryRef.updateUserInfo( vxfOwner);
		return prevProduct;
	}

	private void updateNSMetadataFromOSMvFOURNSDescriptorFile(Product prevProduct, File aNSDdescriptorFile) throws IOException,NullPointerException {
		// Create a nsExtractor Object for the OSMvTWO file 		
		OSM4NSExtractor nsExtract = new OSM4NSExtractor(aNSDdescriptorFile);
		// Get the nsd object out of the file info		
		ns.yang.nfvo.nsd.rev170228.nsd.catalog.Nsd ns = nsExtract.extractNsDescriptor();
		if (ns != null) {
			//*************LOAD THE Product Object from the NSD Descriptor START************************************
			//on update we need to check if name and version are the same. Only then we will accept it
			if ( !prevProduct.getName().equals( ns.getId()) ||  !prevProduct.getVersion().equals( ns.getVersion() )  ){
				throw new IOException( "Name and version are not equal to existing descriptor. No updates were performed." );
			}							
			if ( ( (ExperimentMetadata) prevProduct).isValid()  ) {
				throw new IOException( "Descriptor is already Validated and cannot change! No updates were performed." );								
			}
			prevProduct.setName(ns.getAddedId());
			prevProduct.setVersion(ns.getVersion());
			prevProduct.setVendor(ns.getVendor());
			prevProduct.setShortDescription(ns.getName());
			prevProduct.setLongDescription(ns.getDescription());
			// Get VNF Requirements from the vnfd			
			OSM4NSRequirements vr = new OSM4NSRequirements(ns);
			// Store the requirements in HTML			
			prevProduct.setDescriptorHTML(vr.toHTML());
			// Store the YAML file			
			prevProduct.setDescriptor(nsExtract.getDescriptorYAMLfile());

			((ExperimentMetadata) prevProduct).getConstituentVxF().clear();
			for (ns.yang.nfvo.nsd.rev170228.nsd.constituent.vnfd.ConstituentVnfd v : ns.getConstituentVnfd()) {
				ConstituentVxF cvxf = new ConstituentVxF();
				cvxf.setMembervnfIndex(Integer.parseInt(v.getMemberVnfIndex())); 
				cvxf.setVnfdidRef(v.getVnfdIdRef());

				VxFMetadata vxf = (VxFMetadata) portalRepositoryRef.getProductByName(v.getVnfdIdRef());

				cvxf.setVxfref(vxf);

				((ExperimentMetadata) prevProduct).getConstituentVxF().add(cvxf);
			}
			// If we got an IconfilePath file from/through the vnfExtractor
			if (nsExtract.getIconfilePath() != null) {

				String imageFileNamePosted = ns.getLogo();
				logger.info("image = " + imageFileNamePosted);
				// If the name is not empty				
				if (!imageFileNamePosted.equals("")) {
					String imgfile = AttachmentUtil.saveFile(nsExtract.getIconfilePath(),
							METADATADIR + prevProduct.getUuid() + File.separator + imageFileNamePosted);
					logger.info("imgfile saved to = " + imgfile);
					prevProduct.setIconsrc(uri.getBaseUri().toString().replace("http:", "") + "repo/images/" + prevProduct.getUuid()
							+ "/" + imageFileNamePosted);
				}
			}
			//*************LOAD THE Product Object from the NSD Descriptor END************************************			
		} else {
			throw new NullPointerException();
		}

	}

	private void updateNSMetadataFromOSMvFIVENSDescriptorFile(Product prevProduct, File aNSDdescriptorFile) throws IOException,NullPointerException {
		// Create a nsExtractor Object for the OSMvTWO file 		
		OSM5NSExtractor nsExtract = new OSM5NSExtractor(aNSDdescriptorFile);
		// Get the nsd object out of the file info		
		osm5.ns.yang.nfvo.nsd.rev170228.nsd.catalog.Nsd ns = nsExtract.extractNsDescriptor();
		if (ns != null) {
			//*************LOAD THE Product Object from the NSD Descriptor START************************************
			//on update we need to check if name and version are the same. Only then we will accept it
			if ( !prevProduct.getName().equals( ns.getId()) ||  !prevProduct.getVersion().equals( ns.getVersion() )  ){
				throw new IOException( "Name and version are not equal to existing descriptor. No updates were performed." );
			}							
			if ( ( (ExperimentMetadata) prevProduct).isValid()  ) {
				throw new IOException( "Descriptor is already Validated and cannot change! No updates were performed." );								
			}
			prevProduct.setName(ns.getAddedId());
			prevProduct.setVersion(ns.getVersion());
			prevProduct.setVendor(ns.getVendor());
			prevProduct.setShortDescription(ns.getName());
			prevProduct.setLongDescription(ns.getDescription());
			// Get VNF Requirements from the vnfd			
			OSM5NSRequirements vr = new OSM5NSRequirements(ns);
			// Store the requirements in HTML			
			prevProduct.setDescriptorHTML(vr.toHTML());
			// Store the YAML file			
			prevProduct.setDescriptor(nsExtract.getDescriptorYAMLfile());

			((ExperimentMetadata) prevProduct).getConstituentVxF().clear();
			for (osm5.ns.yang.nfvo.nsd.rev170228.nsd.constituent.vnfd.ConstituentVnfd v : ns.getConstituentVnfd()) {
				ConstituentVxF cvxf = new ConstituentVxF();
				cvxf.setMembervnfIndex(Integer.parseInt(v.getMemberVnfIndex())); 
				cvxf.setVnfdidRef(v.getVnfdIdRef());

				VxFMetadata vxf = (VxFMetadata) portalRepositoryRef.getProductByName(v.getVnfdIdRef());

				cvxf.setVxfref(vxf);

				((ExperimentMetadata) prevProduct).getConstituentVxF().add(cvxf);
			}
			// If we got an IconfilePath file from/through the vnfExtractor
			if (nsExtract.getIconfilePath() != null) {

				String imageFileNamePosted = ns.getLogo();
				logger.info("image = " + imageFileNamePosted);
				// If the name is not empty				
				if (!imageFileNamePosted.equals("")) {
					String imgfile = AttachmentUtil.saveFile(nsExtract.getIconfilePath(),
							METADATADIR + prevProduct.getUuid() + File.separator + imageFileNamePosted);
					logger.info("imgfile saved to = " + imgfile);
					prevProduct.setIconsrc(uri.getBaseUri().toString().replace("http:", "") + "repo/images/" + prevProduct.getUuid()
							+ "/" + imageFileNamePosted);
				}
			}
			//*************LOAD THE Product Object from the NSD Descriptor END************************************			
		} else {
			throw new NullPointerException();
		}

	}
	
	private void updateNSMetadataFromNSDescriptorFile(Product prevProduct, File descriptorFile) throws IOException,NullPointerException {
		NSExtractor nsExtract = new NSExtractor(descriptorFile);
		Nsd ns = nsExtract.extractNsDescriptor();
		if (ns != null) {
			//on update we need to check if name and version are the same. Only then we will accept it
			if ( !prevProduct.getName().equals( ns.getId()) ||  !prevProduct.getVersion().equals( ns.getVersion() )  ){
				throw new IOException( "Name and version are not equal to existing descriptor. No updates were performed." );
			}							
			if ( ( (ExperimentMetadata) prevProduct).isValid()  ) {
				throw new IOException( "Descriptor is already Validated and cannot change! No updates were performed." );								
			}
			prevProduct.setName(ns.getId());
			prevProduct.setVersion(ns.getVersion());
			prevProduct.setVendor(ns.getVendor());
			prevProduct.setShortDescription(ns.getName());
			prevProduct.setLongDescription(ns.getDescription());
			
			NSRequirements vr = new NSRequirements(ns);
			prevProduct.setDescriptorHTML(vr.toHTML());
			prevProduct.setDescriptor(nsExtract.getDescriptorYAMLfile());
			
			((ExperimentMetadata) prevProduct).getConstituentVxF().clear();
			for (ConstituentVnfd v : ns.getConstituentVnfd()) {
				ConstituentVxF cvxf = new ConstituentVxF();
				cvxf.setMembervnfIndex(v.getMemberVnfIndex().intValue()); // ok we will survive with
																			// this
				cvxf.setVnfdidRef(v.getVnfdIdRef());

				VxFMetadata vxf = (VxFMetadata) portalRepositoryRef.getProductByName(v.getVnfdIdRef());

				cvxf.setVxfref(vxf);

				((ExperimentMetadata) prevProduct).getConstituentVxF().add(cvxf);
			}

			if (nsExtract.getIconfilePath() != null) {

				String imageFileNamePosted = ns.getLogo();
				logger.info("image = " + imageFileNamePosted);
				if (!imageFileNamePosted.equals("")) {
					String imgfile = AttachmentUtil.saveFile(nsExtract.getIconfilePath(), 
							METADATADIR + prevProduct.getUuid() + File.separator + imageFileNamePosted);
					logger.info("imgfile saved to = " + imgfile);
					prevProduct.setIconsrc(uri.getBaseUri().toString().replace("http:", "") + "repo/images/"
							+ prevProduct.getUuid() + "/" + imageFileNamePosted);
				}
			}
		} else {
			throw new NullPointerException();
		}			
	}

	@GET
	@Path("/images/{uuid}/{imgfile}")
	@Produces({ "image/jpeg,image/png" })
	public Response getEntityImage(@PathParam("uuid") String uuid, @PathParam("imgfile") String imgfile) {
		logger.info("getEntityImage of uuid: " + uuid);
		String imgAbsfile = METADATADIR + uuid + File.separator + imgfile;
		logger.info("Image RESOURCE FILE: " + imgAbsfile);
		File file = new File(imgAbsfile);

		// ResponseBuilder response = Response.ok((Object) file );
		// logger.info( "attachment; filename=" + file.getName() );
		// response.header("Content-Disposition", "attachment; filename=" +
		// file.getName());
		// return response.build();
		// String mediaType = SomeContentTypeMapHere(file)
		return Response.ok(file).build();
	}

	@GET
	@Path("/packages/{uuid}/{vxffile}")
	@Produces("application/gzip")
	public Response downloadVxFPackage(@PathParam("uuid") String uuid, @PathParam("vxffile") String vxffile) {

		logger.info("vxffile: " + vxffile);
		logger.info("uuid: " + uuid);

		String vxfAbsfile = METADATADIR + uuid + File.separator + vxffile;
		logger.info("VxF RESOURCE FILE: " + vxfAbsfile);
		File file = new File(vxfAbsfile);

		if ((uuid.equals("77777777-668b-4c75-99a9-39b24ed3d8be"))
				|| (uuid.equals("22cab8b8-668b-4c75-99a9-39b24ed3d8be"))) {
			URL res = getClass().getResource("/files/" + vxffile);
			logger.info("TEST LOCAL RESOURCE FILE: " + res);
			file = new File(res.getFile());
		}
		
		
		//we allow everyone to download it since we have OSM onboarding 
//		if ( !portalRepositoryRef.getProductByUUID(uuid).isPublished()){
//			VxFMetadata vxf = (VxFMetadata) portalRepositoryRef.getProductByUUID( uuid );
//			if ( !checkUserIDorIsAdmin( vxf.getOwner().getId() ) ){
//				return Response.status(Status.FORBIDDEN ).build();
//			}
//		}
	

		ResponseBuilder response = Response.ok((Object) file);
		response.header("Content-Disposition", "attachment; filename=" + file.getName());
		return response.build();
	}

	@DELETE
	@Path("/admin/vxfs/{vxfid}")
	public void deleteVxF(@PathParam("vxfid") int vxfid) {
				
		VxFMetadata vxf = (VxFMetadata) portalRepositoryRef.getProductByID(vxfid);
		
		if ( !checkUserIDorIsAdmin( vxf.getOwner().getId() ) ){
			throw new WebApplicationException( Response.status(Status.FORBIDDEN ).build() );
		}
		// Get the OnBoarded Descriptors to OffBoard them
		List<VxFOnBoardedDescriptor>  vxfobds = vxf.getVxfOnBoardedDescriptors();					
		ResponseBuilder builder = Response.status(Status.FORBIDDEN );
		if(vxf.isCertified())
		{
			builder.entity( new ErrorMsg( "vxf with id=" + vxfid + " is Certified and will not be deleted" )  );	
			throw new WebApplicationException(builder.build());
		}
		if(vxfobds.size()>0)
		{
			for(VxFOnBoardedDescriptor vxfobd_tmp : vxfobds)
			{
				if(vxfobd_tmp.getOnBoardingStatus()!=OnBoardingStatus.ONBOARDED)
				{
					continue;
				}
				OnBoardingStatus previous_status = vxfobd_tmp.getOnBoardingStatus();
				vxfobd_tmp.setOnBoardingStatus(OnBoardingStatus.OFFBOARDING);
				CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+vxfobd_tmp.getVxf().getName()+" to "+vxfobd_tmp.getOnBoardingStatus());																						
				
				VxFOnBoardedDescriptor u = portalRepositoryRef.updateVxFOnBoardedDescriptor(vxfobd_tmp);

				ResponseEntity<String> response = null;
				try {
					response = aMANOController.offBoardVxFFromMANOProvider( vxfobd_tmp );
				}
				catch( HttpClientErrorException e)
				{
					vxfobd_tmp.setOnBoardingStatus(previous_status);
					CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+vxfobd_tmp.getVxf().getName()+" to "+vxfobd_tmp.getOnBoardingStatus());																											
					vxfobd_tmp.setFeedbackMessage(e.getResponseBodyAsString());
					u = portalRepositoryRef.updateVxFOnBoardedDescriptor(vxfobd_tmp);
					JSONObject result = new JSONObject(e.getResponseBodyAsString()); //Convert String to JSON Object
					builder = Response.status(e.getRawStatusCode()).type(MediaType.TEXT_PLAIN).entity("OffBoarding Failed! "+e.getStatusText()+", "+result.getString("detail"));			
					throw new WebApplicationException(builder.build());
				}        
				
				if (response == null) {
					vxfobd_tmp.setOnBoardingStatus(previous_status);
					try
					{
						CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+vxfobd_tmp.getVxf().getName()+" to "+vxfobd_tmp.getOnBoardingStatus());																											
					}
					catch(Exception e)
					{
						CentralLogger.log( CLevel.INFO, "No related VxF found for "+vxfobd_tmp.getId()+" in status  "+vxfobd_tmp.getOnBoardingStatus());					
					}
					vxfobd_tmp.setFeedbackMessage("Null response on OffBoarding request.Requested VxFOnBoardedDescriptor with ID=\" + vxfobd_tmp.getId() + \" cannot be offboarded.");
					u = portalRepositoryRef.updateVxFOnBoardedDescriptor(vxfobd_tmp);
					builder = Response.status(Status.INTERNAL_SERVER_ERROR);
					builder.entity("Requested VxFOnBoardedDescriptor with ID=" + vxfobd_tmp.getId() + " cannot be offboarded");
					throw new WebApplicationException(builder.build());
				} else {
					vxfobd_tmp.setFeedbackMessage(response.getBody().toString());					
				}
				// UnCertify Upon OffBoarding
				vxfobd_tmp.getVxf().setCertified(false);
				vxfobd_tmp.setOnBoardingStatus(OnBoardingStatus.OFFBOARDED);
				try
				{
					CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+vxfobd_tmp.getVxf().getName()+" to "+vxfobd_tmp.getOnBoardingStatus());
				}
				catch(Exception e)
				{
					CentralLogger.log( CLevel.INFO, "No related VxF found for "+vxfobd_tmp.getId()+" in status  "+vxfobd_tmp.getOnBoardingStatus());					
				}
				u = portalRepositoryRef.updateVxFOnBoardedDescriptor(vxfobd_tmp);
				BusController.getInstance().offBoardVxF( u.getId() );
			}
		}
		portalRepositoryRef.deleteProduct(vxfid);
		BusController.getInstance().deletedVxF( vxf.getId() );											
	}

	@GET
	@Path("/vxfs/{vxfid}")
	@Produces("application/json")
	public Response getVxFMetadataByID(@PathParam("vxfid") int vxfid) {
		logger.info("getVxFMetadataByID  vxfid=" + vxfid);
		VxFMetadata vxf = (VxFMetadata) portalRepositoryRef.getProductByID(vxfid);

		if (vxf != null) {
			
			if ( !vxf.isPublished() ){
				return Response.status(Status.FORBIDDEN ).build() ;
			}
			
			return Response.ok().entity(vxf).build();
		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("vxf with id=" + vxfid + " not found in portal registry");
			throw new WebApplicationException(builder.build());
		}
	}

	@GET
	@Path("/admin/vxfs/{vxfid}")
	@Produces("application/json")
	public Response getAdminVxFMetadataByID(@PathParam("vxfid") int vxfid) {

		logger.info("getAdminVxFMetadataByID  vxfid=" + vxfid);
		VxFMetadata vxf = (VxFMetadata) portalRepositoryRef.getProductByID(vxfid);

		if (vxf != null) {

			PortalUser u = portalRepositoryRef.getUserBySessionID(ws.getHttpServletRequest().getSession().getId());
			if ( !checkUserIDorIsAdmin( vxf.getOwner().getId() )  &&! u.getRoles().contains(UserRoleType.TESTBED_PROVIDER) ){
				return Response.status(Status.FORBIDDEN ).build() ;
			}
			
			
			return Response.ok().entity(vxf).build();
		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("vxf with id=" + vxfid + " not found in portal registry");
			throw new WebApplicationException(builder.build());
		}
	}

	@GET
	@Path("/vxfs/uuid/{uuid}")
	@Produces("application/json")
	public Response getVxFMetadataByUUID(@PathParam("uuid") String uuid) {

		logger.info("Received GET for vxf uuid: " + uuid);
		VxFMetadata vxf = null;

		URI endpointUrl = uri.getBaseUri();
		if (uuid.equals("77777777-668b-4c75-99a9-39b24ed3d8be")) {
			vxf = new VxFMetadata();
			vxf.setUuid(uuid);
			vxf.setName("IntegrTestLocal example service");
			vxf.setShortDescription("An example local service");
			vxf.setVersion("1.0.0");
			vxf.setIconsrc("");
			vxf.setLongDescription("");

			vxf.setPackageLocation(endpointUrl.toString().replace("http:", "")
					+ "repo/packages/77777777-668b-4c75-99a9-39b24ed3d8be/examplevxf.tar.gz");
			// }else if (uuid.equals("12cab8b8-668b-4c75-99a9-39b24ed3d8be")) {
			// vxf = new VxFMetadata(uuid, "AN example service");
			// vxf.setShortDescription("An example local service");
			// vxf.setVersion("1.0.0rc1");
			// vxf.setIconsrc("");
			// vxf.setLongDescription("");
			// //URI endpointUrl = uri.getBaseUri();
			//
			// vxf.setPackageLocation( endpointUrl
			// +"repo/packages/12cab8b8-668b-4c75-99a9-39b24ed3d8be/examplevxf.tar.gz");
		} else if (uuid.equals("22cab8b8-668b-4c75-99a9-39b24ed3d8be")) {
			vxf = new VxFMetadata();
			vxf.setUuid(uuid);
			vxf.setName("IntegrTestLocal example ErrInstall service");
			vxf.setShortDescription("An example ErrInstall local service");
			vxf.setVersion("1.0.0");
			vxf.setIconsrc("");
			vxf.setLongDescription("");
			// URI endpointUrl = uri.getBaseUri();

			vxf.setPackageLocation(endpointUrl.toString().replace("http:", "")
					+ "repo/packages/22cab8b8-668b-4c75-99a9-39b24ed3d8be/examplevxfErrInstall.tar.gz");
		} else {
			vxf = (VxFMetadata) portalRepositoryRef.getProductByUUID(uuid);
		}

		if (vxf != null) {
			
			if ( ! vxf.isPublished() ){
				return Response.status(Status.FORBIDDEN ).build() ;
			}
			
			return Response.ok().entity(vxf).build();
		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("Installed vxf with uuid=" + uuid + " not found in local registry");
			throw new WebApplicationException(builder.build());
		}

	}

	public PortalRepository getPortalRepositoryRef() {
		return portalRepositoryRef;
	}

	public void setPortalRepositoryRef(PortalRepository portalRepositoryRef) {
		this.portalRepositoryRef = portalRepositoryRef;
	}
	
	

	/**
	 * @return the aMANOController
	 */
	public MANOController getaMANOController() {
		return aMANOController;
	}

	/**
	 * @param aMANOController the aMANOController to set
	 */
	public void setaMANOController(MANOController aMANOController) {
		this.aMANOController = aMANOController;
	}

	// Sessions related API

	// @OPTIONS
	// @Path("/sessions/")
	// @Produces("application/json")
	// @Consumes("application/json")
	// @LocalPreflight
	// public Response addUserSessionOption(){
	//
	//
	// logger.info("Received OPTIONS addUserSessionOption ");
	// String origin = headers.getRequestHeader("Origin").get(0);
	// if (origin != null) {
	// return Response.ok()
	// .header(CorsHeaderConstants.HEADER_AC_ALLOW_METHODS, "GET POST DELETE PUT
	// HEAD OPTIONS")
	// .header(CorsHeaderConstants.HEADER_AC_ALLOW_CREDENTIALS, "true")
	// .header(CorsHeaderConstants.HEADER_AC_ALLOW_HEADERS, "Origin,
	// X-Requested-With, Content-Type, Accept")
	// .header(CorsHeaderConstants.HEADER_AC_ALLOW_ORIGIN, origin)
	// .build();
	// } else {
	// return Response.ok().build();
	// }
	// }


	@POST
	@Path("/sessions/")
	@Produces("application/json")
	@Consumes("application/json")
	public Response addUserSession(UserSession userSession) {

		logger.info("Received POST addUserSession usergetUsername: " + userSession.getUsername());
		// logger.info("DANGER, REMOVE Received POST addUserSession password: "
		// + userSession.getPassword());

		if (sc != null) {
			if (sc.getUserPrincipal() != null)
				logger.info(" securityContext.getUserPrincipal().toString() >"
						+ sc.getUserPrincipal().toString() + "<");

		}

		Subject currentUser = SecurityUtils.getSubject();
		if (currentUser != null) {
			AuthenticationToken token = new UsernamePasswordToken(userSession.getUsername(), userSession.getPassword());
			try {
				currentUser.login(token);
				PortalUser portalUser = portalRepositoryRef.getUserByUsername(userSession.getUsername());

				if (!portalUser.getActive()) {
					logger.info("User [" + currentUser.getPrincipal() + "] is not Active");
					return Response.status(Status.UNAUTHORIZED).build();
				}

				portalUser.setCurrentSessionID(ws.getHttpServletRequest().getSession().getId());
				userSession.setPortalUser(portalUser);
				userSession.setPassword("");
				;// so not tosend in response

				logger.info(" currentUser = " + currentUser.toString());
				logger.info("User [" + currentUser.getPrincipal() + "] logged in successfully.");
				portalRepositoryRef.updateUserInfo(  portalUser);
				if ( currentUser.getPrincipal().toString().length()>2 ){
					CentralLogger.log( CLevel.INFO, "User [" + currentUser.getPrincipal().toString().substring(0, 3) + "xxx" + "] logged in");					
				}

				return Response.ok().entity(userSession).build();
			} catch (AuthenticationException ae) {

				return Response.status(Status.UNAUTHORIZED).build();
			}
		}

		return Response.status(Status.UNAUTHORIZED).build();
	}

	@GET
	@Path("/sessions/logout")
	@Produces("application/json")
	public Response logoutUser() {

		logger.info("Received logoutUser ");

		if (sc != null) {
			if (sc.getUserPrincipal() != null)
				logger.info(" securityContext.getUserPrincipal().toString() >"
						+ sc.getUserPrincipal().toString() + "<");

			SecurityUtils.getSubject().logout();
		}

		return Response.ok().build();
	}

	// THIS IS NOT USED
	@GET
	@Path("/sessions/")
	@Produces("application/json")
	public Response getUserSessions() {

		logger.info("Received GET addUserSession usergetUsername: ");
		logger.info("Received GET addUserSession password: ");

		if (sc != null) {
			if (sc.getUserPrincipal() != null)
				logger.info(" securityContext.getUserPrincipal().toString() >"
						+ sc.getUserPrincipal().toString() + "<");

		}

		Subject currentUser = SecurityUtils.getSubject();
		if ((currentUser != null) && (currentUser.getPrincipal() != null)) {

			// logger.info(" currentUser = " + currentUser.toString() );
			// logger.info( "User [" + currentUser.getPrincipal() + "] logged in
			// successfully." );
			// logger.info(" currentUser employee = " +
			// currentUser.hasRole("employee") );
			// logger.info(" currentUser boss = " + currentUser.hasRole("boss")
			// );

			return Response.ok().build();
		}

		return Response.status(Status.UNAUTHORIZED).build();
	}

	// Subscribed resources related API

//	@GET
//	@Path("/admin/subscribedresources/")
//	@Produces("application/json")
//	public Response getSubscribedResources() {
//
//		PortalUser u = portalRepositoryRef.getUserBySessionID(ws.getHttpServletRequest().getSession().getId());
//
//		if (u != null) {
//
//			if (u.getRoles().contains(UserRoleType.PORTALADMIN)) {
//				return Response.ok().entity(portalRepositoryRef.getSubscribedResourcesAsCollection()).build(); // return
//																												// all
//			} else
//				return Response.ok().entity(u.getSubscribedResources()).build();
//
//		}
//
//		ResponseBuilder builder = Response.status(Status.NOT_FOUND);
//		builder.entity("User not found in portal registry or not logged in");
//		throw new WebApplicationException(builder.build());
//
//	}
//
//	@GET
//	@Path("/admin/subscribedresources/{smId}")
//	@Produces("application/json")
//	public Response getSubscribedResourceById(@PathParam("smId") int smId) {
//
//		PortalUser u = portalRepositoryRef.getUserBySessionID(ws.getHttpServletRequest().getSession().getId());
//
//		SubscribedResource sm = portalRepositoryRef.getSubscribedResourceByID(smId);
//
//		if ((sm != null) && (u != null)) {
//
//			if ((u.getRoles().contains(UserRoleType.PORTALADMIN)) || (sm.getOwner().getId() == u.getId()))
//				return Response.ok().entity(sm).build();
//
//		}
//
//		ResponseBuilder builder = Response.status(Status.NOT_FOUND);
//		builder.entity("SubscribedResource" + smId + " not found in portal registry");
//		throw new WebApplicationException(builder.build());
//
//	}
//
//	@POST
//	@Path("/admin/subscribedresources/")
//	@Produces("application/json")
//	@Consumes("application/json")
//	public Response addSubscribedResource(SubscribedResource sm) {
//
//		PortalUser u = sm.getOwner();
//		u = portalRepositoryRef.getUserByID(sm.getOwner().getId());
//
//		if (u != null) {
//			sm.setOwner(u);
//
//			u.getSubscribedResources().add(sm);
//			u = portalRepositoryRef.updateUserInfo(  u);
//
//			return Response.ok().entity(sm).build();
//		} else {
//			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
//			builder.entity("Requested SubscribedResource with rls=" + sm.getURL()
//					+ " cannot be registered under not found user");
//			throw new WebApplicationException(builder.build());
//		}
//	}
//
//	@PUT
//	@Path("/admin/subscribedresources/{smId}")
//	@Produces("application/json")
//	@Consumes("application/json")
//	public Response updateSubscribedResource(@PathParam("smId") int smId, SubscribedResource sm) {
//		logger.info("Received SubscribedResource for user: " + sm.getURL());
//
//		PortalUser u = portalRepositoryRef.getUserBySessionID(ws.getHttpServletRequest().getSession().getId());
//
//		PortalUser reattachedUser = portalRepositoryRef.getUserByID(sm.getOwner().getId());
//		sm.setOwner(reattachedUser);
//
//		if (u != null) {
//
//			if ((u.getRoles().contains(UserRoleType.PORTALADMIN)) || (sm.getOwner().getId() == u.getId())) {
//
//				SubscribedResource sr = portalRepositoryRef.updateSubscribedResourceInfo(smId, sm);
//				return Response.ok().entity(u).build();
//			}
//
//		}
//
//		ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
//		builder.entity("Requested SubscribedResource with url=" + sm.getURL() + " cannot be updated");
//		throw new WebApplicationException(builder.build());
//
//	}
//
//	@DELETE
//	@Path("/admin/subscribedresources/{smId}")
//	@Produces("application/json")
//	public Response deleteSubscribedResource(@PathParam("smId") int smId) {
//		logger.info("Received SubscribedResource for userid: " + smId);
//
//		PortalUser u = portalRepositoryRef.getUserBySessionID(ws.getHttpServletRequest().getSession().getId());
//
//		SubscribedResource sm = portalRepositoryRef.getSubscribedResourceByID(smId);
//		if (u != null) {
//
//			if ((u.getRoles().contains(UserRoleType.PORTALADMIN)) || (sm.getOwner().getId() == u.getId())) {
//				portalRepositoryRef.deleteSubscribedResource(smId);
//				return Response.ok().build();
//
//			}
//		}
//
//		ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
//		builder.entity("Requested SubscribedResource with id=" + smId + " cannot be deleted");
//		throw new WebApplicationException(builder.build());
//	}

	// Applications related API

	@GET
	@Path("/admin/experiments")
	@Produces("application/json")
	public Response getApps(@QueryParam("categoryid") Long categoryid) {

		PortalUser u = portalRepositoryRef.getUserBySessionID(ws.getHttpServletRequest().getSession().getId());

		if (u != null) {
			List<ExperimentMetadata> apps;

			if (u.getRoles().contains(UserRoleType.PORTALADMIN)) {
				apps = portalRepositoryRef.getExperiments(categoryid, false);
			} else {
				apps = portalRepositoryRef.getAppsByUserID((long) u.getId());
			}

			return Response.ok().entity(apps).build();

		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("User not found in portal registry or not logged in");
			throw new WebApplicationException(builder.build());
		}

	}

	@GET
	@Path("/experiments")
	@Produces("application/json")
	public Response getAllApps(@QueryParam("categoryid") Long categoryid) {
		logger.info("getexperiments categoryid=" + categoryid);
		List<ExperimentMetadata> vxfs = portalRepositoryRef.getExperiments(categoryid, true);
		return Response.ok().entity(vxfs).build();
	}
	
	
	/**
	 * @return all User's Valid experiments as well as all Public and Valid experiments 
	 */
	@GET
	@Path("/admin/experiments/deployable")
	@Produces("application/json")
	public Response getAllDeployableExperiments() {
		
		//
		PortalUser u = portalRepositoryRef.getUserBySessionID(ws.getHttpServletRequest().getSession().getId());

		if (u != null) {
			List<ExperimentMetadata> userexpr;

			if (u.getRoles().contains(UserRoleType.PORTALADMIN)) {
				userexpr = portalRepositoryRef.getExperiments( (long) -1 , false);
			} else {
				userexpr = portalRepositoryRef.getAppsByUserID((long) u.getId());
			}

			
			List<ExperimentMetadata> deplExps = new ArrayList<ExperimentMetadata>( userexpr );
			List<ExperimentMetadata> pubExps = new ArrayList<ExperimentMetadata>( portalRepositoryRef.getExperiments( (long) -1 , true) );
			List<ExperimentMetadata> returnedExps = new ArrayList<ExperimentMetadata>();
			for (ExperimentMetadata e : pubExps) {
				
				boolean found = false;
				boolean onboarded = false;
				for (ExperimentMetadata depl : deplExps) {
					if (  depl.getId() == e.getId() ) {
						found = true;					
					}
				}				
				if ( !found ) {
					deplExps.add(e);//add no duplicate public experiments
				}				
			}
			for (ExperimentMetadata depl : deplExps) {
				// If it is not already included and it has been onboarded
				if( depl.getExperimentOnBoardDescriptors().size()>0 )
				{
					for(ExperimentOnBoardDescriptor eobd : depl.getExperimentOnBoardDescriptors())
					{
						if(eobd.getOnBoardingStatus() == OnBoardingStatus.ONBOARDED)
						{
							returnedExps.add(depl);
						}
					}
				}
			}
			
//			for (Iterator<ExperimentMetadata> iter = deplExps.listIterator(); iter.hasNext(); ) { //filter only valid
//				ExperimentMetadata a = iter.next();
//			    if ( !a.isValid() ) {
//			        iter.remove();
//			    }
//			}			
			
			return Response.ok().entity( returnedExps ).build();

		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("User not found in portal registry or not logged in");
			throw new WebApplicationException(builder.build());
		}
	}

	@GET
	@Path("/experiments/{appid}")
	@Produces("application/json")
	public Response getExperimentMetadataByID(@PathParam("appid") int appid) {
		logger.info("getAppMetadataByID  appid=" + appid);
		ExperimentMetadata app = (ExperimentMetadata) portalRepositoryRef.getProductByID(appid);
		

		if (app != null) {
			if ( !app.isPublished() ){
				return Response.status(Status.FORBIDDEN ).build() ;
			}
			return Response.ok().entity(app).build();
		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("App with id=" + appid + " not found in portal registry");
			throw new WebApplicationException(builder.build());
		}
	}

	@GET
	@Path("/admin/experiments/{appid}")
	@Produces("application/json")
	public Response getAdminExperimentMetadataByID(@PathParam("appid") int appid) {
		
		logger.info("getAppMetadataByID  appid=" + appid);
		ExperimentMetadata app = (ExperimentMetadata) portalRepositoryRef.getProductByID(appid);
		

		if (app != null) {

			if ( !checkUserIDorIsAdmin( app.getOwner().getId() ) ){
				return Response.status(Status.FORBIDDEN ).build() ;
			}
			
			return Response.ok().entity(app).build();
		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("App with id=" + appid + " not found in portal registry");
			throw new WebApplicationException(builder.build());
		}
	}

	@GET
	@Path("/experiments/uuid/{uuid}")
	@Produces("application/json")
	public Response getAppMetadataByUUID(@PathParam("uuid") String uuid) {
		logger.info("Received GET for app uuid: " + uuid);
		ExperimentMetadata app = null;

		app = (ExperimentMetadata) portalRepositoryRef.getProductByUUID(uuid);

		if (app != null) {
			if ( !app.isPublished() ){
				return Response.status(Status.FORBIDDEN ).build() ;
			}
			return Response.ok().entity(app).build();
		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("Installed app with uuid=" + uuid + " not found in local registry");
			throw new WebApplicationException(builder.build());
		}

	}

	@POST
	@Path("/admin/experiments/")
	@Consumes("multipart/form-data")
	public Response addExperimentMetadata(List<Attachment> ats) {

		PortalUser u = portalRepositoryRef.getUserBySessionID(ws.getHttpServletRequest().getSession().getId());

		if (u == null) {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("User not found in portal registry or not logged in ");
			throw new WebApplicationException(builder.build());
		}			

		ExperimentMetadata experiment = new ExperimentMetadata();

		String emsg = "";
		try {
			MappingJsonFactory factory = new MappingJsonFactory();
			JsonParser parser = factory.createJsonParser(AttachmentUtil.getAttachmentStringValue("exprm", ats));
			experiment = parser.readValueAs(ExperimentMetadata.class);
			
			logger.info("Received @POST for experiment : " + experiment.getName());
			// ExperimentMetadata sm = new ExperimentMetadata();
			experiment = (ExperimentMetadata) addNewProductData(experiment, AttachmentUtil.getAttachmentByName("prodIcon", ats),
					AttachmentUtil.getAttachmentByName("prodFile", ats), AttachmentUtil.getListOfAttachmentsByName("screenshots", ats));

		}catch (JsonProcessingException e) {
			experiment = null;
			e.printStackTrace();
			logger.error( e.getMessage() );
			emsg =  e.getMessage();
		} catch (IOException e) {
			experiment = null;
			e.printStackTrace();
			logger.error( e.getMessage() );
			emsg =  e.getMessage();
		}

		if (experiment != null) {

			BusController.getInstance().newNSDAdded( experiment.getId() );		
			BusController.getInstance().validateNSD( experiment.getId() );

			//======================================================
			// AUTOMATIC ONBOARDING PROCESS -START
			// Need to select MANO Provider, convert vxfMetadata to VxFOnBoardedDescriptor and pass it as an input.
			
			// Get the MANO providers which are set for automatic onboarding
			
			List<MANOprovider> MANOprovidersEnabledForOnboarding=portalRepositoryRef.getMANOprovidersEnabledForOnboarding();
			if(MANOprovidersEnabledForOnboarding.size()>0 && experiment.getPackagingFormat() == PackagingFormat.OSMvFOUR)
			{
				for(MANOprovider mp : MANOprovidersEnabledForOnboarding)
				{
					//Create NSDOnboardDescriptor
					ExperimentOnBoardDescriptor obd = new ExperimentOnBoardDescriptor( );
					// Get the first one for now			
					obd.setObMANOprovider(mp);
					obd.setUuid( UUID.randomUUID().toString() ); 
					ExperimentMetadata refNSD =  ( ExperimentMetadata )portalRepositoryRef.getProductByID( experiment.getId() );
					// Fill the NSDMetadata of NSDOnBoardedDescriptor
					obd.setExperiment( refNSD );
					// Update the NSDMetadata Object with the obd Object
					refNSD.getExperimentOnBoardDescriptors().add( obd ) ;				
					
					// ???????
					obd.setExperiment( refNSD );
					
					// save product
					refNSD = (ExperimentMetadata) portalRepositoryRef.updateProductInfo( refNSD );
					// save VxFonBoardedDescriptor or not ???
					obd = portalRepositoryRef.updateExperimentOnBoardDescriptor(obd);
					
	//				try
	//				{
	//					aMANOController.onBoardNSDToMANOProvider(obd);					
	//				}
	//				catch(Exception e)
	//				{
	//					System.out.println("OnBoarding Failed");					
	//					System.out.println(e.getMessage());
	//					e.printStackTrace();
	//				}
					// Send the message for automatic onboarding
					//BusController.getInstance().newNSDAdded( vxf );
					
					//set proper scheme (http or https)
					MANOController.setHTTPSCHEME( ws.getHttpServletRequest().getScheme().toString() );
					BusController.getInstance().onBoardNSD( obd.getId() );
				}
			}
			if(MANOprovidersEnabledForOnboarding.size()>0 && experiment.getPackagingFormat() == PackagingFormat.OSMvFIVE)
			{
				for(MANOprovider mp : MANOprovidersEnabledForOnboarding)
				{
					//Create NSDOnboardDescriptor
					ExperimentOnBoardDescriptor obd = new ExperimentOnBoardDescriptor( );
					// Get the first one for now			
					obd.setObMANOprovider(mp);
					obd.setUuid( UUID.randomUUID().toString() ); 
					ExperimentMetadata refNSD =  ( ExperimentMetadata )portalRepositoryRef.getProductByID( experiment.getId() );
					// Fill the NSDMetadata of NSDOnBoardedDescriptor
					obd.setExperiment( refNSD );
					// Update the NSDMetadata Object with the obd Object
					refNSD.getExperimentOnBoardDescriptors().add( obd ) ;				
					
					// ???????
					obd.setExperiment( refNSD );
					
					// save product
					refNSD = (ExperimentMetadata) portalRepositoryRef.updateProductInfo( refNSD );
					// save VxFonBoardedDescriptor or not ???
					obd = portalRepositoryRef.updateExperimentOnBoardDescriptor(obd);
					
	//				try
	//				{
	//					aMANOController.onBoardNSDToMANOProvider(obd);					
	//				}
	//				catch(Exception e)
	//				{
	//					System.out.println("OnBoarding Failed");					
	//					System.out.println(e.getMessage());
	//					e.printStackTrace();
	//				}
					// Send the message for automatic onboarding
					//BusController.getInstance().newNSDAdded( vxf );
					
					//set proper scheme (http or https)
					MANOController.setHTTPSCHEME( ws.getHttpServletRequest().getScheme().toString() );
					BusController.getInstance().onBoardNSD( obd.getId() );
				}
			}

			// AUTOMATIC ONBOARDING PROCESS -END
			//======================================================
			
			ExperimentMetadata experimentr = (ExperimentMetadata) portalRepositoryRef.getProductByID( experiment.getId()) ; //rereading this, seems to keep the DB connection
			return Response.ok().entity(experimentr).build();
		} else {
			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
			builder.entity( new ErrorMsg( "Requested entity cannot be installed. " + emsg )  );	
			throw new WebApplicationException(builder.build());
		}

	}

	@PUT
	@Path("/admin/experiments/{aid}")
	@Consumes("multipart/form-data")
	public Response updateExperimentMetadata(@PathParam("aid") int aid, List<Attachment> ats) {

		ExperimentMetadata expmeta = null;
		
		String emsg= "";

		try {
			MappingJsonFactory factory = new MappingJsonFactory();
			JsonParser parser = factory.createJsonParser(AttachmentUtil.getAttachmentStringValue("exprm", ats));
			expmeta = parser.readValueAs(ExperimentMetadata.class);

			if ( !checkUserIDorIsAdmin( expmeta.getOwner().getId() ) ){
				return Response.status(Status.FORBIDDEN ).build() ;
			}
			
			logger.info("Received @POST for experiment : " + expmeta.getName());
			// logger.info("Received @POST for app.containers : " +
			// appmeta.getContainers().size());

			expmeta = (ExperimentMetadata) updateProductMetadata(expmeta, AttachmentUtil.getAttachmentByName("prodIcon", ats),
					AttachmentUtil.getAttachmentByName("prodFile", ats), AttachmentUtil.getListOfAttachmentsByName("screenshots", ats));

		} catch (JsonProcessingException e) {
			expmeta = null;
			e.printStackTrace();
			logger.error( e.getMessage() );
			emsg =  e.getMessage();
		} catch (IOException e) {
			expmeta = null;
			e.printStackTrace();
			logger.error( e.getMessage() );
			emsg =  e.getMessage();
		}
		
		
		if ( expmeta != null) { 

			BusController.getInstance().updateNSD(expmeta.getId());	
			
			if ( AttachmentUtil.getAttachmentByName("prodFile", ats) != null ) { //if the descriptor changed then we must re-trigger validation
				Attachment prodFile =  AttachmentUtil.getAttachmentByName("prodFile", ats);
				String vxfFileNamePosted = AttachmentUtil.getFileName(prodFile.getHeaders());
				if ( !vxfFileNamePosted.equals("unknown") ){
					BusController.getInstance().validationUpdateNSD(expmeta.getId());
				}
			}
			
			
			
			
			
			return Response.ok().entity(expmeta).build();
		} else {
			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
			builder.entity( new ErrorMsg( "Requested entity cannot be installed. " + emsg )  );	
			throw new WebApplicationException(builder.build());
		}
		
		
	}

	@DELETE
	@Path("/admin/experiments/{appid}")
	public void deleteExperiment(@PathParam("appid") int appid) {
		
		ExperimentMetadata nsd = (ExperimentMetadata) portalRepositoryRef.getProductByID( appid );

		if ( !checkUserIDorIsAdmin( nsd.getOwner().getId() ) ){
			throw new WebApplicationException( Response.status(Status.FORBIDDEN ).build() );
		}
		// Get the OnBoarded Descriptors to OffBoard them
		List<ExperimentOnBoardDescriptor> expobds = nsd.getExperimentOnBoardDescriptors();
		ResponseBuilder builder = Response.status(Status.FORBIDDEN );
		if ( nsd.isValid()   ) 
		{
			builder.entity( new ErrorMsg( "ExperimentMetadata with id=" + appid + " is Validated and will not be deleted" )  );	
			throw new WebApplicationException(builder.build());
		}
		if(expobds.size()>0)
		{
			for(ExperimentOnBoardDescriptor expobd_tmp : expobds)
			{
				if(expobd_tmp.getOnBoardingStatus()!=OnBoardingStatus.ONBOARDED)
				{
					continue;
				}
				OnBoardingStatus previous_status = expobd_tmp.getOnBoardingStatus();
				expobd_tmp.setOnBoardingStatus(OnBoardingStatus.OFFBOARDING);
				CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+expobd_tmp.getExperiment().getName()+" to "+expobd_tmp.getOnBoardingStatus());																										
				ExperimentOnBoardDescriptor u = portalRepositoryRef.updateExperimentOnBoardDescriptor(expobd_tmp);

				ResponseEntity<String> response = null;
				try {
					response = aMANOController.offBoardNSDFromMANOProvider( expobd_tmp );
				}
				catch( HttpClientErrorException e)
				{
					expobd_tmp.setOnBoardingStatus(previous_status);
					CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+expobd_tmp.getExperiment().getName()+" to "+expobd_tmp.getOnBoardingStatus());																											
					expobd_tmp.setFeedbackMessage(e.getResponseBodyAsString());					
					u = portalRepositoryRef.updateExperimentOnBoardDescriptor(expobd_tmp);
					JSONObject result = new JSONObject(e.getResponseBodyAsString()); //Convert String to JSON Object
					builder = Response.status(e.getRawStatusCode()).type(MediaType.TEXT_PLAIN).entity("OffBoarding Failed! "+e.getStatusText()+", "+result.getString("detail"));			
					throw new WebApplicationException(builder.build());
				}        
				
				if (response == null) {
					expobd_tmp.setOnBoardingStatus(previous_status);
					CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+expobd_tmp.getExperiment().getName()+" to "+expobd_tmp.getOnBoardingStatus());																											
					expobd_tmp.setFeedbackMessage("Null response on OffBoarding request.Requested VxFOnBoardedDescriptor with ID=\" + expobd_tmp.getId() + \" cannot be offboarded.");
					u = portalRepositoryRef.updateExperimentOnBoardDescriptor(expobd_tmp);
					builder = Response.status(Status.INTERNAL_SERVER_ERROR);
					builder.entity("Requested VxFOnBoardedDescriptor with ID=" + expobd_tmp.getId() + " cannot be offboarded");
					throw new WebApplicationException(builder.build());
				}
				// UnCertify Upon OffBoarding
				expobd_tmp.getExperiment().setValid(false);
				expobd_tmp.setFeedbackMessage(response.getBody().toString());
				expobd_tmp.setOnBoardingStatus(OnBoardingStatus.OFFBOARDED);
				CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+expobd_tmp.getExperiment().getName()+" to "+expobd_tmp.getOnBoardingStatus());																															
				u = portalRepositoryRef.updateExperimentOnBoardDescriptor(expobd_tmp);
				BusController.getInstance().offBoardNSD(u.getId());
				
			}
		}
		portalRepositoryRef.deleteProduct(appid);
		BusController.getInstance().deletedExperiment(nsd.getId());											
	}

	// categories API
	@GET
	@Path("/categories/")
	@Produces("application/json")
	public Response getCategories() {
		return Response.ok().entity(portalRepositoryRef.getCategories()).build();
	}

	@GET
	@Path("/admin/categories/")
	@Produces("application/json")
	public Response getAdminCategories() {
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		return Response.ok().entity(portalRepositoryRef.getCategories()).build();
	}

	@POST
	@Path("/admin/categories/")
	@Produces("application/json")
	@Consumes("application/json")
	public Response addCategory(Category c) {
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		Category u = portalRepositoryRef.addCategory(c);

		if (u != null) {
			return Response.ok().entity(u).build();
		} else {
			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
			builder.entity( new ErrorMsg( "Requested Category with name=" + c.getName() + " cannot be installed" )  );
			throw new WebApplicationException(builder.build());
		}
	}

	@PUT
	@Path("/admin/categories/{catid}")
	@Produces("application/json")
	@Consumes("application/json")
	public Response updateCategory(@PathParam("catid") int catid, Category c) {
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		
		Category previousCategory = portalRepositoryRef.getCategoryByID(catid);

		previousCategory.setName( c.getName() );
		
		Category u = portalRepositoryRef.updateCategoryInfo( previousCategory );

		if (u != null) {
			return Response.ok().entity(u).build();
		} else {
			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
			builder.entity( new ErrorMsg( "Requested Category with name=" + c.getName() + " cannot be installed" )  );
			throw new WebApplicationException(builder.build());
		}

	}

	@DELETE
	@Path("/admin/categories/{catid}")
	public Response deleteCategory(@PathParam("catid") int catid) {
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		
		Category category = portalRepositoryRef.getCategoryByID(catid);
		if ((category.getProducts().size() > 0)) {
			ResponseBuilder builder = Response.status(Status.METHOD_NOT_ALLOWED);
			builder.entity("The category has assigned elements. You cannot delete it!");
			throw new WebApplicationException(builder.build());
		} else {
			portalRepositoryRef.deleteCategory(catid);
			return Response.ok().build();
		}
	}

	@GET
	@Path("/categories/{catid}")
	@Produces("application/json")
	public Response getCategoryById(@PathParam("catid") int catid) {
		Category sm = portalRepositoryRef.getCategoryByID(catid);

		if (sm != null) {
			return Response.ok().entity(sm).build();
		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("Category " + catid + " not found in portal registry");
			throw new WebApplicationException(builder.build());
		}
	}

	@GET
	@Path("/admin/categories/{catid}")
	@Produces("application/json")
	public Response getAdminCategoryById(@PathParam("catid") int catid) {
		
		return getCategoryById(catid);
	}

	

	

	

	@GET
	@Path("/admin/properties/")
	@Produces("application/json")
	public Response getProperties() {
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		
		List<PortalProperty> props = portalRepositoryRef.getProperties();
		for (PortalProperty portalProperty : props) {
			if (portalProperty.getName().equals("mailpassword")) {
				portalProperty.setValue("***");
			}
		}
		return Response.ok().entity(props).build();
	}

	@PUT
	@Path("/admin/properties/{propid}")
	@Produces("application/json")
	@Consumes("application/json")
	public Response updateProperty(@PathParam("catid") int propid, PortalProperty p) {
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		
		PortalProperty previousProperty = portalRepositoryRef.getPropertyByID(propid);

		PortalProperty u = portalRepositoryRef.updateProperty(p);
		if (u != null) {
			return Response.ok().entity(u).build();
		} else {
			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
			builder.entity("Requested PortalProperty with name=" + p.getName() + " cannot be updated");
			throw new WebApplicationException(builder.build());
		}

	}

	@GET
	@Path("/admin/properties/{propid}")
	@Produces("application/json")
	public Response getPropertyById(@PathParam("propid") int propid) {
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		PortalProperty sm = portalRepositoryRef.getPropertyByID(propid);

		if (sm.getName().equals("mailpassword")) {
			sm.setValue("");
		}
		if (sm != null) {
			return Response.ok().entity(sm).build();
		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("PortalProperty " + propid + " not found in portal registry");
			throw new WebApplicationException(builder.build());
		}
	}
	

	
	@GET
	@Path("/admin/deployments")
	@Produces("application/json")
	public Response getAllDeployments( @QueryParam("status") String status ) {

		PortalUser u = portalRepositoryRef.getUserBySessionID(ws.getHttpServletRequest().getSession().getId());
		if (u != null) {
			logger.info("getAllDeployments for userid: " + u.getId());
			List<DeploymentDescriptor> deployments;
			if ( (u.getRoles().contains(UserRoleType.PORTALADMIN)) ) {
				if ( (status!=null) && status.equals( "COMPLETED" )){
					deployments = portalRepositoryRef.getAllCompletedDeploymentDescriptors();
				} else if (  (status!=null) &&  status.equals( "REJECTED" )){
					deployments = portalRepositoryRef.getAllRejectedDeploymentDescriptors();
				} else if (  (status!=null) &&  status.equals( "FAILED" )){
					deployments = portalRepositoryRef.getAllFailedDeploymentDescriptors();
				} else if (  (status!=null) &&  status.equals( "FAILED_OSM_REMOVED" )){
					deployments = portalRepositoryRef.getAllRemovedDeploymentDescriptors();					
				} else {
					deployments = portalRepositoryRef.getAllDeploymentDescriptors();			
				}			
			} else if ( (u.getRoles().contains(UserRoleType.MENTOR))) {							
				if ( (status!=null) && status.equals( "COMPLETED" )){
					deployments = portalRepositoryRef.getAllDeploymentDescriptorsByMentor(  (long) u.getId(), "COMPLETED" );
				} else if (  (status!=null) &&  status.equals( "REJECTED" )){
					deployments = portalRepositoryRef.getAllDeploymentDescriptorsByMentor(  (long) u.getId(), "REJECTED" );
				} else if (  (status!=null) &&  status.equals( "FAILED" )){
					deployments = portalRepositoryRef.getAllDeploymentDescriptorsByUser( (long) u.getId(), "FAILED" );
				} else if (  (status!=null) &&  status.equals( "FAILED_OSM_REMOVED" )){
					deployments = portalRepositoryRef.getAllDeploymentDescriptorsByUser( (long) u.getId(), "FAILED_OSM_REMOVED" );
				} else {
					deployments = portalRepositoryRef.getAllDeploymentDescriptorsByMentor(  (long) u.getId(), null );
				}			
			} else {

				if ( (status!=null) && status.equals( "COMPLETED" )){
					deployments = portalRepositoryRef.getAllDeploymentDescriptorsByUser( (long) u.getId(),  "COMPLETED"  );
				} else if (  (status!=null) &&  status.equals( "REJECTED" )){
					deployments = portalRepositoryRef.getAllDeploymentDescriptorsByUser( (long) u.getId(), "REJECTED" );
				} else if (  (status!=null) &&  status.equals( "FAILED" )){
					deployments = portalRepositoryRef.getAllDeploymentDescriptorsByUser( (long) u.getId(), "FAILED" );
				} else if (  (status!=null) &&  status.equals( "FAILED_OSM_REMOVED" )){
					deployments = portalRepositoryRef.getAllDeploymentDescriptorsByUser( (long) u.getId(), "FAILED_OSM_REMOVED" );
				} else {
					deployments = portalRepositoryRef.getAllDeploymentDescriptorsByUser( (long) u.getId(), null );
				}			
			}
			return Response.ok().entity(deployments).build();
		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("User not found in portal registry or not logged in");
			throw new WebApplicationException(builder.build());
		}

	}
	
	
//	@GET
//	@Path("/admin/deployments/user")
//	@Produces("application/json")
//	public Response getAllDeploymentsofUser() {
//
//		PortalUser u = portalRepositoryRef.getUserBySessionID(ws.getHttpServletRequest().getSession().getId());
//
//		if (u != null) {
//			logger.info("getAllDeploymentsofUser for userid: " + u.getId());
//			List<DeploymentDescriptor> deployments;
//			deployments = portalRepositoryRef.getAllDeploymentDescriptorsByUser( (long) u.getId() ); 
//
//			return Response.ok().entity(deployments).build();
//		} else {
//			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
//			builder.entity("User not found in portal registry or not logged in");
//			throw new WebApplicationException(builder.build());
//		}
//
//	}
	
	@GET
	@Path("/admin/deployments/user")
	@Produces("application/json")
	public Response getAllDeploymentsofUser( @QueryParam("status") String status ) {

		PortalUser u = portalRepositoryRef.getUserBySessionID(ws.getHttpServletRequest().getSession().getId());

		if (u != null) {
			logger.info("getAllDeploymentsofUser for userid: " + u.getId());
			List<DeploymentDescriptor> deployments = new ArrayList<DeploymentDescriptor>();
			
			if ( (status!=null) && status.equals( "COMPLETED" )){
				deployments = portalRepositoryRef.getAllDeploymentDescriptorsByUser( (long) u.getId(),  "COMPLETED"  );
			} else if (  (status!=null) &&  status.equals( "REJECTED" )){
				deployments = portalRepositoryRef.getAllDeploymentDescriptorsByUser( (long) u.getId(), "REJECTED" );
			} else if (  (status!=null) &&  status.equals( "FAILED" )){
				deployments = portalRepositoryRef.getAllDeploymentDescriptorsByUser( (long) u.getId(), "FAILED" );
			} else if (  (status!=null) &&  status.equals( "FAILED_OSM_REMOVED" )){
				deployments = portalRepositoryRef.getAllDeploymentDescriptorsByUser( (long) u.getId(), "FAILED_OSM_REMOVED" );
			} else {
				deployments = portalRepositoryRef.getAllDeploymentDescriptorsByUser( (long) u.getId(), null );
			}
			
			return Response.ok().entity(deployments).build();
		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("User not found in portal registry or not logged in");
			throw new WebApplicationException(builder.build());
		}

	}
	
	
	@GET
	@Path("/admin/deployments/scheduled")
	@Produces("application/json")
	public Response getAllScheduledDeploymentsofUser() {

		PortalUser u = portalRepositoryRef.getUserBySessionID(ws.getHttpServletRequest().getSession().getId());

		if (u != null) {
			logger.info("getAllDeploymentsofUser for userid: " + u.getId());
			List<DeploymentDescriptor> deployments;

			if ( (u.getRoles().contains(UserRoleType.PORTALADMIN)) ||  (u.getRoles().contains(UserRoleType.TESTBED_PROVIDER )) ) {
				deployments = portalRepositoryRef.getAllDeploymentDescriptorsScheduled();
			} else {
				deployments = portalRepositoryRef.getAllDeploymentDescriptorsByUser( (long) u.getId(), null ); 
			}

			return Response.ok().entity(deployments).build();
		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("User not found in portal registry or not logged in");
			throw new WebApplicationException(builder.build());
			
		}

	}

	@POST
	@Path("/admin/deployments")
	@Produces("application/json")
	@Consumes("application/json")
	public Response addDeployment(DeploymentDescriptor deployment) {

		PortalUser u = portalRepositoryRef.getUserBySessionID(ws.getHttpServletRequest().getSession().getId());

		if (u != null) {
			logger.info("addDeployment for userid: " + u.getId());

			for (DeploymentDescriptor d : u.getDeployments()) {
				logger.info("deployment already for userid: " + d.getId());
			}
			String uuid = UUID.randomUUID().toString();
			deployment.setUuid(uuid);
			deployment.setDateCreated(new Date());
			deployment.setStatus(DeploymentDescriptorStatus.UNDER_REVIEW);
			CentralLogger.log( CLevel.INFO, "Status change of deployment "+deployment.getName()+" to "+deployment.getStatus());
			logger.info( "Status change of deployment "+deployment.getName()+" to "+deployment.getStatus());			

			u = portalRepositoryRef.getUserByID(u.getId());
			deployment.setOwner(u); // reattach from the DB model
			u.getDeployments().add(deployment);
			
			// Get the Experiment Metadata from the id of the experiment from the deployment request
			ExperimentMetadata baseApplication = (ExperimentMetadata) portalRepositoryRef
					.getProductByID(deployment.getExperiment().getId());
			deployment.setExperiment(baseApplication); // reattach from the DB model

			
			deployment = portalRepositoryRef.updateDeploymentDescriptor(deployment);
			logger.info("NS status change is now "+deployment.getStatus());														

//			u = portalRepositoryRef.updateUserInfo(u);
			
//			deployment = portalRepositoryRef.getDeploymentByUUID( deployment.getUuid() );//reattach from model
			
			BusController.getInstance().newDeploymentRequest( deployment.getId() );	

//			String adminemail = PortalRepository.getPropertyByName("adminEmail").getValue();
//			if ((adminemail != null) && (!adminemail.equals(""))) {
//				String subj = "[5GinFIREPortal] New Deployment Request";
//				EmailUtil.SendRegistrationActivationEmail(adminemail,
//						"5GinFIREPortal New Deployment Request by user : " + u.getUsername() + ", " + u.getEmail()+ "\n<br/> Status: " + deployment.getStatus().name()+ "\n<br/> Description: " + deployment.getDescription()   ,
//						subj);
//			}

			return Response.ok().entity(deployment).build();
		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("User not found in portal registry or not logged in. DeploymentDescriptor not added.");
			throw new WebApplicationException(builder.build());
		}
	}

	@DELETE
	@Path("/admin/deployments/{id}")
	@Consumes("application/json")
	@Produces("application/json")
	public Response deleteDeployment(@PathParam("id") int id) {
		PortalUser u = portalRepositoryRef.getUserBySessionID(ws.getHttpServletRequest().getSession().getId());

		DeploymentDescriptor dep = portalRepositoryRef.getDeploymentByID(id);
		if (u != null) {
			if (u.getRoles().contains(UserRoleType.PORTALADMIN) || u.getId() == dep.getOwner().getId()) {
				portalRepositoryRef.deleteDeployment(id);
				return Response.ok().build();
			}
		}

		ResponseBuilder builder = Response.status(Status.NOT_FOUND);
		builder.entity("User not found in portal registry or not logged in");
		throw new WebApplicationException(builder.build());
	}

	@GET
	@Path("/admin/deployments/{id}")
	@Produces("application/json")
	public Response getDeploymentById(@PathParam("id") int deploymentId) {

		PortalUser u = portalRepositoryRef.getUserBySessionID(ws.getHttpServletRequest().getSession().getId());

		if (u != null) {
			logger.info("getDeploymentById for id: " + deploymentId);
			DeploymentDescriptor deployment = portalRepositoryRef.getDeploymentByID(deploymentId);

			if ((u.getRoles().contains(UserRoleType.PORTALADMIN)) || (deployment.getMentor().getId() == u.getId()) || (deployment.getOwner().getId() == u.getId())) {
				return Response.ok().entity(deployment).build();
			}
		}

		ResponseBuilder builder = Response.status(Status.NOT_FOUND);
		builder.entity("User not found in portal registry or not logged in");
		throw new WebApplicationException(builder.build());

	}

	@PUT
	@Path("/admin/deployments/{id}")
	@Produces("application/json")
	@Consumes("application/json")
	public Response updateDeployment(@PathParam("id") int id, DeploymentDescriptor receivedDeployment) {

		PortalUser u = portalRepositoryRef.getUserBySessionID(ws.getHttpServletRequest().getSession().getId());

		if ((u != null)) {

			if ((u.getRoles().contains(UserRoleType.PORTALADMIN)) || u.getApikey().equals(receivedDeployment.getMentor().getApikey())) // only admin or Deployment Mentor can alter a deployment
			{
				DeploymentDescriptor aDeployment = portalRepositoryRef.getDeploymentByID( receivedDeployment.getId() );
												
				//PortalUser deploymentOwner = portalRepositoryRef.getUserByID(d.getOwner().getId());
				//d.setOwner(deploymentOwner); // reattach from the DB model

//				ExperimentMetadata baseApplication = (ExperimentMetadata) portalRepositoryRef
//						.getProductByID(d.getExperiment().getId());
//				d.setExperiment(baseApplication); // reattach from the DB model
//
//				DeploymentDescriptor deployment = portalRepositoryRef.updateDeploymentDescriptor(d);
//				List<DeploymentDescriptor> deployments = deploymentOwner.getDeployments();
//				for (DeploymentDescriptor deploymentDescriptor : deployments) {
//					logger.info("Deployment id = " + deploymentDescriptor.getId() );
//				}
//				if ( ! deployments.contains(deployment) ) {
//					deploymentOwner.getDeployments().add(deployment);
//					deploymentOwner = portalRepositoryRef.updateUserInfo(  u);					 
//				}
				
				aDeployment.setName( receivedDeployment.getName() );
				aDeployment.setFeedback( receivedDeployment.getFeedback() );
				aDeployment.setStartDate( receivedDeployment.getStartDate());
				aDeployment.setEndDate( receivedDeployment.getEndDate() );

				logger.info("Previous Status is :"+aDeployment.getStatus()+",New Status is:"+receivedDeployment.getStatus()+" and Instance Id is "+aDeployment.getInstanceId());
								
				//prevDeployment = portalRepositoryRef.updateDeploymentDescriptor(prevDeployment);
				if( receivedDeployment.getStatus() != aDeployment.getStatus() )
				{
					aDeployment.setStatus( receivedDeployment.getStatus() );
					CentralLogger.log( CLevel.INFO, "Status change of deployment "+aDeployment.getName()+" to "+aDeployment.getStatus());
					logger.info( "Status change of deployment "+aDeployment.getName()+" to "+aDeployment.getStatus());			
					aDeployment.getExperimentFullDetails();
					aDeployment.getInfrastructureForAll();
					
					logger.info("updateDeployment for id: " + aDeployment.getId());
					
					
	//				String adminemail = PortalRepository.getPropertyByName("adminEmail").getValue();
	//				if ((adminemail != null) && (!adminemail.equals(""))) {
	//					String subj = "[5GinFIREPortal] Deployment Request";
	//					EmailUtil.SendRegistrationActivationEmail(prevDeployment.getOwner().getEmail(),
	//							"5GinFIREPortal Deployment Request for experiment: " + prevDeployment.getName() + "\n<br/>Status: " + prevDeployment.getStatus().name()+ "\n<br/>Feedback: " + prevDeployment.getFeedback() + "\n\n<br/><br/> The 5GinFIRE team" ,
	//							subj);
	//				}
	
					DeploymentDescriptor dd = portalRepositoryRef.getDeploymentByID( receivedDeployment.getId() );  //rereading this, seems to keep the DB connection
	

					if( receivedDeployment.getStatus() == DeploymentDescriptorStatus.SCHEDULED && aDeployment.getInstanceId() == null)
					{
						for (ExperimentOnBoardDescriptor tmpExperimentOnBoardDescriptor : dd.getExperimentFullDetails().getExperimentOnBoardDescriptors())
						{
							if(tmpExperimentOnBoardDescriptor.getObMANOprovider().getSupportedMANOplatform().getName().equals("OSM FOUR"))
							{							
								aDeployment.setStatus( receivedDeployment.getStatus() );
								CentralLogger.log( CLevel.INFO, "Status change of deployment "+aDeployment.getName()+" to "+aDeployment.getStatus());
								logger.info( "Status change of deployment "+aDeployment.getName()+" to "+aDeployment.getStatus());							
								aDeployment = portalRepositoryRef.updateDeploymentDescriptor(aDeployment);
								logger.info("NS status change is now "+aDeployment.getStatus());															
								BusController.getInstance().scheduleExperiment( aDeployment.getId() );								
							}
							if(tmpExperimentOnBoardDescriptor.getObMANOprovider().getSupportedMANOplatform().getName().equals("OSM FIVE"))
							{							
								aDeployment.setStatus( receivedDeployment.getStatus() );
								CentralLogger.log( CLevel.INFO, "Status change of deployment "+aDeployment.getName()+" to "+aDeployment.getStatus());
								logger.info( "Status change of deployment "+aDeployment.getName()+" to "+aDeployment.getStatus());							
								aDeployment = portalRepositoryRef.updateDeploymentDescriptor(aDeployment);
								logger.info("NS status change is now "+aDeployment.getStatus());															
								BusController.getInstance().scheduleExperiment( aDeployment.getId() );								
							}
						}
					}
					else if( receivedDeployment.getStatus() == DeploymentDescriptorStatus.RUNNING && aDeployment.getInstanceId() == null)
					{
						for (ExperimentOnBoardDescriptor tmpExperimentOnBoardDescriptor : dd.getExperimentFullDetails().getExperimentOnBoardDescriptors())
						{
							if(tmpExperimentOnBoardDescriptor.getObMANOprovider().getSupportedMANOplatform().getName().equals("OSM FOUR"))
							{
								//Trigger Automatic Instantiation
								//Initially we try synchronously
								//aMANOController.deployNSDToMANOProvider(prevDeployment);
								//Then try asynchronously
								aDeployment.setStatus( receivedDeployment.getStatus() );
								CentralLogger.log( CLevel.INFO, "Status change of deployment "+aDeployment.getName()+" to "+aDeployment.getStatus());
								logger.info( "Status change of deployment "+aDeployment.getName()+" to "+aDeployment.getStatus());							
								aDeployment = portalRepositoryRef.updateDeploymentDescriptor(aDeployment);
								logger.info("NS status change is now "+aDeployment.getStatus());															
	
								BusController.getInstance().deployExperiment( aDeployment.getId() );	
							}
							if(tmpExperimentOnBoardDescriptor.getObMANOprovider().getSupportedMANOplatform().getName().equals("OSM FIVE"))
							{
								//Trigger Automatic Instantiation
								//Initially we try synchronously
								//aMANOController.deployNSDToMANOProvider(prevDeployment);
								//Then try asynchronously
	
								aDeployment.setStatus( receivedDeployment.getStatus() );
								CentralLogger.log( CLevel.INFO, "Status change of deployment "+aDeployment.getName()+" to "+aDeployment.getStatus());
								logger.info( "Status change of deployment "+aDeployment.getName()+" to "+aDeployment.getStatus());							
								aDeployment = portalRepositoryRef.updateDeploymentDescriptor(aDeployment);
								logger.info("NS status change is now "+aDeployment.getStatus());															
								BusController.getInstance().deployExperiment( aDeployment.getId() );	
							}
						}
					}
					else if( receivedDeployment.getStatus() == DeploymentDescriptorStatus.COMPLETED && aDeployment.getInstanceId() != null)
					{
						aDeployment.setStatus( receivedDeployment.getStatus() );
						CentralLogger.log( CLevel.INFO, "Status change of deployment "+aDeployment.getName()+" to "+aDeployment.getStatus());
						logger.info( "Status change of deployment "+aDeployment.getName()+" to "+aDeployment.getStatus());							
						aDeployment = portalRepositoryRef.updateDeploymentDescriptor(aDeployment);
						logger.info("NS status change is now "+aDeployment.getStatus());															
						BusController.getInstance().completeExperiment( aDeployment.getId() );						
					}
					else if( receivedDeployment.getStatus() == DeploymentDescriptorStatus.REJECTED && aDeployment.getInstanceId() == null)
					{
						aDeployment.setStatus( receivedDeployment.getStatus() );
						CentralLogger.log( CLevel.INFO, "Status change of deployment "+aDeployment.getName()+" to "+aDeployment.getStatus());
						logger.info( "Status change of deployment "+aDeployment.getName()+" to "+aDeployment.getStatus());							
						aDeployment = portalRepositoryRef.updateDeploymentDescriptor(aDeployment);
						logger.info("NS status change is now "+aDeployment.getStatus());															
						BusController.getInstance().rejectExperiment( aDeployment.getId() );
						logger.info("Deployment Rejected");				
					}
					else
					{
						ResponseBuilder builder = Response.status(Status.NOT_ACCEPTABLE);
						builder.entity("Inconsistent status change");
						throw new WebApplicationException(builder.build());					
					}
				} else {

					logger.info( "Previous status is the same so just update deployment info");					
					aDeployment = portalRepositoryRef.updateDeploymentDescriptor(aDeployment);
				}
				return Response.ok().entity( aDeployment ).build();
			}

		}

		ResponseBuilder builder = Response.status(Status.NOT_ACCEPTABLE);
		builder.entity("User not found in portal registry or not logged in as admin");
		throw new WebApplicationException(builder.build());

	}

//	@POST
//	@Path("/registerresource/")
//	@Produces("application/json")
//	@Consumes("application/json")
//	public Response addANewAnauthSubscribedResource(SubscribedResource sm) {
//
//		logger.info("Received SubscribedResource for client: " + sm.getUuid() + ", URLs:" + sm.getURL() + ", OwnerID:"
//				+ sm.getOwner().getId());
//
//		PortalUser u = sm.getOwner();
//		u = portalRepositoryRef.getUserByID(sm.getOwner().getId());
//
//		if ((u != null) && (sm.getUuid() != null)) {
//
//			SubscribedResource checkSM = portalRepositoryRef.getSubscribedResourceByUUID(sm.getUuid());
//
//			if (checkSM == null) {
//				sm.setOwner(u);
//				sm.setActive(false);
//				u.getSubscribedResources().add(sm);
//				u = portalRepositoryRef.updateUserInfo(  u);
//				return Response.ok().entity(sm).build();
//			} else {
//				checkSM.setURL(sm.getURL());// update URL if changed
//				// u = portalRepositoryRef.updateUserInfo( u.getId(), u);
//				checkSM = portalRepositoryRef.updateSubscribedResourceInfo(checkSM.getId(), checkSM);
//				return Response.ok().entity(checkSM).build();
//			}
//
//		} else {
//			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
//			builder.entity("Requested SubscribedResource with rls=" + sm.getURL()
//					+ " cannot be registered under not found user");
//			throw new WebApplicationException(builder.build());
//		}
//	}

	/********************************************************************************
	 * 
	 * admin MANO platforms
	 * 
	 ********************************************************************************/

	@GET
	@Path("/manoplatforms/")
	@Produces("application/json")
	public Response getMANOplatforms() {
		return Response.ok().entity(portalRepositoryRef.getMANOplatforms()).build();
	}

	@GET
	@Path("/admin/manoplatforms/")
	@Produces("application/json")
	public Response getAdminMANOplatforms() {

		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		return Response.ok().entity(portalRepositoryRef.getMANOplatforms()).build();
	}

	@POST
	@Path("/admin/manoplatforms/")
	@Produces("application/json")
	@Consumes("application/json")
	public Response addMANOplatform(MANOplatform c) {

		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		
		MANOplatform u = portalRepositoryRef.addMANOplatform(c);

		if (u != null) {
			return Response.ok().entity(u).build();
		} else {
			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
			builder.entity("Requested MANOplatform with name=" + c.getName() + " cannot be installed");
			throw new WebApplicationException(builder.build());
		}
	}

	@PUT
	@Path("/admin/manoplatforms/{mpid}")
	@Produces("application/json")
	@Consumes("application/json")
	public Response updateMANOplatform(@PathParam("mpid") int mpid, MANOplatform c) {
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		MANOplatform previousMP = portalRepositoryRef.getMANOplatformByID(mpid);
		
		previousMP.setDescription( c.getDescription() );
		previousMP.setName( c.getName() );
		previousMP.setVersion( c.getVersion() );

		MANOplatform u = portalRepositoryRef.updateMANOplatformInfo( previousMP );

		if (u != null) {
			return Response.ok().entity(u).build();
		} else {
			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
			builder.entity("Requested MANOplatform with name=" + c.getName() + " cannot be updated");
			throw new WebApplicationException(builder.build());
		}

	}

	@DELETE
	@Path("/admin/manoplatforms/{mpid}")
	public Response deleteMANOplatform(@PathParam("mpid") int mpid) {
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		MANOplatform category = portalRepositoryRef.getMANOplatformByID(mpid);

		portalRepositoryRef.deleteMANOplatform(mpid);
		return Response.ok().build();

	}

	@GET
	@Path("/manoplatforms/{mpid}")
	@Produces("application/json")
	public Response getMANOplatformById(@PathParam("mpid") int mpid) {
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		MANOplatform sm = portalRepositoryRef.getMANOplatformByID(mpid);

		if (sm != null) {
			return Response.ok().entity(sm).build();
		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("MANOplatform " + mpid + " not found in portal registry");
			throw new WebApplicationException(builder.build());
		}
	}

	@GET
	@Path("/admin/manoplatforms/{mpid}")
	@Produces("application/json")
	public Response getAdminMANOplatformById(@PathParam("mpid") int mpid) {
		return getMANOplatformById(mpid);
	}

	/********************************************************************************
	 * 
	 * admin MANO providers
	 * 
	 ********************************************************************************/

	/**
	 * @return
	 */
	@GET
	@Path("/admin/manoproviders/")
	@Produces("application/json")
	public Response getAdminMANOproviders() {
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		return Response.ok().entity(portalRepositoryRef.getMANOproviders()).build();
	}

	@POST
	@Path("/admin/manoproviders/")
	@Produces("application/json")
	@Consumes("application/json")
	public Response addMANOprovider(MANOprovider c) {
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		MANOprovider u = portalRepositoryRef.addMANOprovider(c);

		if (u != null) {
			return Response.ok().entity(u).build();
		} else {
			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
			builder.entity("Requested MANOprovider with name=" + c.getName() + " cannot be installed");
			throw new WebApplicationException(builder.build());
		}
	}

	@PUT
	@Path("/admin/manoproviders/{mpid}")
	@Produces("application/json")
	@Consumes("application/json")
	public Response updateMANOprovider(@PathParam("mpid") int mpid, MANOprovider c) {

		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		
		MANOprovider prev = portalRepositoryRef.getMANOproviderByID(c.getId());
		prev.setApiEndpoint( c.getApiEndpoint());
		prev.setAuthorizationBasicHeader( c.getAuthorizationBasicHeader());
		prev.setDescription( c.getDescription());
		prev.setName(c.getName());
		prev.setSupportedMANOplatform( c.getSupportedMANOplatform() );
		
		MANOprovider u = portalRepositoryRef.updateMANOproviderInfo(c);

		if (u != null) {
			return Response.ok().entity(u).build();
		} else {
			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
			builder.entity("Requested MANOprovider with name=" + c.getName() + " cannot be updated");
			throw new WebApplicationException(builder.build());
		}

	}

	@DELETE
	@Path("/admin/manoproviders/{mpid}")
	public Response deleteMANOprovider(@PathParam("mpid") int mpid) {

		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		portalRepositoryRef.deleteMANOprovider(mpid);
		return Response.ok().build();

	}

	@GET
	@Path("/admin/manoproviders/{mpid}")
	@Produces("application/json")
	public Response getAdminMANOproviderById(@PathParam("mpid") int mpid) {
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		MANOprovider sm = portalRepositoryRef.getMANOproviderByID(mpid);

		if (sm != null) {
			return Response.ok().entity(sm).build();
		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("MANOprovider " + mpid + " not found in portal registry");
			throw new WebApplicationException(builder.build());
		}
	}

	@GET
	@Path("/manoprovider/{mpid}/vnfds/{vxfid}")
	@Produces("application/json")
	public Response getOSMVNFMetadataByKOSMMANOID(@PathParam("mpid") int manoprovid, @PathParam("vxfid") String vxfid) {
		logger.info("getOSMVNFMetadataByID  vxfid=" + vxfid);
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}

		MANOprovider sm = portalRepositoryRef.getMANOproviderByID(manoprovid);
		if(sm.getSupportedMANOplatform().getName().equals("OSM TWO"))
		{
			Vnfd vnfd = OSMClient.getInstance(sm).getVNFDbyID(vxfid);
			if (vnfd != null) {
				return Response.ok().entity(vnfd).build();
			} else {
				ResponseBuilder builder = Response.status(Status.NOT_FOUND);
				builder.entity("vxf with id=" + vxfid + " not found in portal registry");
				throw new WebApplicationException(builder.build());
			}
		}
		if(sm.getSupportedMANOplatform().getName().equals("OSM FOUR"))
		{
			OSM4Client osm4Client = new OSM4Client(sm.getApiEndpoint(),sm.getUsername(),sm.getPassword(),"admin");
			ns.yang.nfvo.vnfd.rev170228.vnfd.catalog.Vnfd vnfd = osm4Client.getVNFDbyID(vxfid);
			if (vnfd != null) {
				return Response.ok().entity(vnfd).build();
			} else {
				ResponseBuilder builder = Response.status(Status.NOT_FOUND);
				builder.entity("vxf with id=" + vxfid + " not found in portal registry");
				throw new WebApplicationException(builder.build());
			}
		}
		if(sm.getSupportedMANOplatform().getName().equals("OSM FIVE"))
		{
			OSM5Client osm5Client = new OSM5Client(sm.getApiEndpoint(),sm.getUsername(),sm.getPassword(),"admin");
			osm5.ns.riftware._1._0.project.vnfd.rev170228.project.vnfd.catalog.Vnfd vnfd = osm5Client.getVNFDbyID(vxfid);
			if (vnfd != null) {
				return Response.ok().entity(vnfd).build();
			} else {
				ResponseBuilder builder = Response.status(Status.NOT_FOUND);
				builder.entity("vxf with id=" + vxfid + " not found in portal registry");
				throw new WebApplicationException(builder.build());
			}
		}
		ResponseBuilder builder = Response.status(Status.NOT_FOUND);
		builder.entity("MANO does not belong to supported types");
		throw new WebApplicationException(builder.build());
	}

	@GET
	@Path("/admin/manoprovider/{mpid}/vnfds/")
	@Produces("application/json")
	public Response getOSMVNFMetadata(@PathParam("mpid") int manoprovid) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		MANOprovider sm = portalRepositoryRef.getMANOproviderByID(manoprovid);


		if(sm.getSupportedMANOplatform().getName().equals("OSM TWO"))
		{
			List<Vnfd> vnfd = OSMClient.getInstance(sm).getVNFDs();
			if (vnfd != null) {
				return Response.ok().entity(vnfd).build();
			} else {
				ResponseBuilder builder = Response.status(Status.NOT_FOUND);
				builder.entity("manoprovid with id=" + manoprovid + " not found in portal registry");
				throw new WebApplicationException(builder.build());
			}
		}
		if(sm.getSupportedMANOplatform().getName().equals("OSM FOUR"))
		{
			//OSM4Client osm4Client = new OSM4Client(sm.getApiEndpoint(),sm.getUsername(),sm.getPassword(),"admin");
			OSM4Client osm4Client = null;			
			try {
				osm4Client = new OSM4Client(sm.getApiEndpoint(), sm.getUsername(), sm.getPassword(), "admin");
			}
		    catch(HttpStatusCodeException e) 
			{
				logger.error("getOSMVNFMetadata, OSM4 fails authentication. Aborting action.");
				CentralLogger.log( CLevel.ERROR, "getOSMVNFMetadata, OSM4 fails authentication. Aborting action.");
				ResponseBuilder builder = Response.status(e.getRawStatusCode());
				builder.entity("manoprovid with id=" + manoprovid + " does not belong to the supported types or failed to communication with OSM");
				throw new WebApplicationException(builder.build());		
			}						
			
			ns.yang.nfvo.vnfd.rev170228.vnfd.catalog.Vnfd[] vnfd = osm4Client.getVNFDs();
			if (vnfd != null) {
				return Response.ok().entity(vnfd).build();
			} else {
				ResponseBuilder builder = Response.status(Status.NOT_FOUND);
				builder.entity("manoprovid with id=" + manoprovid + " not found in portal registry");
				throw new WebApplicationException(builder.build());
			}
		}
		if(sm.getSupportedMANOplatform().getName().equals("OSM FIVE"))
		{
			OSM5Client osm5Client = null;			
			try {
				osm5Client = new OSM5Client(sm.getApiEndpoint(), sm.getUsername(), sm.getPassword(), "admin");
			}
		    catch(HttpStatusCodeException e) 
			{
				logger.error("getOSMVNFMetadata, OSM5 fails authentication. Aborting action.");
				CentralLogger.log( CLevel.ERROR, "getOSMVNFMetadata, OSM5 fails authentication. Aborting action.");
				ResponseBuilder builder = Response.status(e.getRawStatusCode());
				builder.entity("manoprovid with id=" + manoprovid + " does not belong to the supported types or failed to communication with OSM");
				throw new WebApplicationException(builder.build());		
			}						
			
			osm5.ns.riftware._1._0.project.vnfd.rev170228.project.vnfd.catalog.Vnfd[] vnfd = osm5Client.getVNFDs();
			if (vnfd != null) {
				return Response.ok().entity(vnfd).build();
			} else {
				ResponseBuilder builder = Response.status(Status.NOT_FOUND);
				builder.entity("manoprovid with id=" + manoprovid + " not found in portal registry");
				throw new WebApplicationException(builder.build());
			}
		}
		ResponseBuilder builder = Response.status(Status.NOT_FOUND);
		builder.entity("manoprovid with id=" + manoprovid + " not of the supported types");
		throw new WebApplicationException(builder.build());		
	}

	@GET
	@Path("/admin/manoprovider/{mpid}/nsds/{nsdid}")
	@Produces("application/json")
	public Response getOSM_NSD_MetadataByKOSMMANOID(@PathParam("mpid") int manoprovid,
			@PathParam("vxfid") String nsdid) {
		logger.info("getOSMVNFMetadataByID  nsdid=" + nsdid);

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		MANOprovider sm = portalRepositoryRef.getMANOproviderByID(manoprovid);

		if(sm.getSupportedMANOplatform().getName().equals("OSM TWO"))
		{
			Nsd nsd = OSMClient.getInstance(sm).getNSDbyID(nsdid);
	
			if (nsd != null) {
				return Response.ok().entity(nsd).build();
			} else {
				ResponseBuilder builder = Response.status(Status.NOT_FOUND);
				builder.entity("nsdid with id=" + nsdid + " not found in portal registry");
				throw new WebApplicationException(builder.build());
			}
		}
		if(sm.getSupportedMANOplatform().getName().equals("OSM FOUR"))
		{
			OSM4Client osm4Client = new OSM4Client(sm.getApiEndpoint(),sm.getUsername(),sm.getPassword(),"admin");
			ns.yang.nfvo.vnfd.rev170228.vnfd.catalog.Vnfd vnfd = osm4Client.getVNFDbyID(nsdid);
			if (vnfd != null) {
				return Response.ok().entity(vnfd).build();
			} else {
				ResponseBuilder builder = Response.status(Status.NOT_FOUND);
				builder.entity("nsdid with id=" + nsdid + " not found in portal registry");
				throw new WebApplicationException(builder.build());
			}
		}
		if(sm.getSupportedMANOplatform().getName().equals("OSM FIVE"))
		{
			OSM5Client osm5Client = new OSM5Client(sm.getApiEndpoint(),sm.getUsername(),sm.getPassword(),"admin");
			osm5.ns.riftware._1._0.project.vnfd.rev170228.project.vnfd.catalog.Vnfd vnfd = osm5Client.getVNFDbyID(nsdid);
			if (vnfd != null) {
				return Response.ok().entity(vnfd).build();
			} else {
				ResponseBuilder builder = Response.status(Status.NOT_FOUND);
				builder.entity("nsdid with id=" + nsdid + " not found in portal registry");
				throw new WebApplicationException(builder.build());
			}
		}
		ResponseBuilder builder = Response.status(Status.NOT_FOUND);
		builder.entity("MANO does not belong to one of the supported types");
		throw new WebApplicationException(builder.build());		
	}

	@GET
	@Path("/admin/manoprovider/{mpid}/nsds/")
	@Produces("application/json")
	public Response getOSM_NSD_Metadata(@PathParam("mpid") int manoprovid) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		MANOprovider sm = portalRepositoryRef.getMANOproviderByID(manoprovid);

		if(sm.getSupportedMANOplatform().getName().equals("OSM TWO"))
		{
			List<Nsd> nsd = OSMClient.getInstance(sm).getNSDs();
	
			if (nsd != null) {
				return Response.ok().entity(nsd).build();
			} else {
				ResponseBuilder builder = Response.status(Status.NOT_FOUND);
				builder.entity("manoprovid with id=" + manoprovid + " not found in portal registry");
				throw new WebApplicationException(builder.build());
			}
		}
		if(sm.getSupportedMANOplatform().getName().equals("OSM FOUR"))
		{
			OSM4Client osm4Client = null;			
			try {
				osm4Client = new OSM4Client(sm.getApiEndpoint(), sm.getUsername(), sm.getPassword(), "admin");
			}
		    catch(HttpStatusCodeException e) 
			{
				logger.error("getOSM_NSD_Metadata, OSM4 fails authentication. Aborting action.");
				CentralLogger.log( CLevel.ERROR, "getOSM_NSD_Metadata, OSM4 fails authentication. Aborting action.");
				ResponseBuilder builder = Response.status(e.getRawStatusCode());
				builder.entity("manoprovid with id=" + manoprovid + " does not belong to the supported types or OSM communication failure");
				throw new WebApplicationException(builder.build());		
			}						
			
			ns.yang.nfvo.nsd.rev170228.nsd.catalog.Nsd[] nsd = osm4Client.getNSDs();
			if (nsd != null) {
				return Response.ok().entity(nsd).build();
			} else {
				ResponseBuilder builder = Response.status(Status.NOT_FOUND);
				builder.entity("manoprovid with id=" + manoprovid + " not found in portal registry");
				throw new WebApplicationException(builder.build());
			}
		}
		if(sm.getSupportedMANOplatform().getName().equals("OSM FIVE"))
		{
			OSM5Client osm5Client = null;			
			try {
				osm5Client = new OSM5Client(sm.getApiEndpoint(), sm.getUsername(), sm.getPassword(), "admin");
			}
		    catch(HttpStatusCodeException e) 
			{
				logger.error("getOSM_NSD_Metadata, OSM5 fails authentication. Aborting action.");
				CentralLogger.log( CLevel.ERROR, "getOSM_NSD_Metadata, OSM5 fails authentication. Aborting action.");
				ResponseBuilder builder = Response.status(e.getRawStatusCode());
				builder.entity("manoprovid with id=" + manoprovid + " does not belong to the supported types or OSM communication failure");
				throw new WebApplicationException(builder.build());		
			}						
			
			osm5.ns.riftware._1._0.project.nsd.rev170228.project.nsd.catalog.Nsd[] nsd = osm5Client.getNSDs();
			if (nsd != null) {
				return Response.ok().entity(nsd).build();
			} else {
				ResponseBuilder builder = Response.status(Status.NOT_FOUND);
				builder.entity("manoprovid with id=" + manoprovid + " not found in portal registry");
				throw new WebApplicationException(builder.build());
			}
		}
		ResponseBuilder builder = Response.status(Status.NOT_FOUND);
		builder.entity("manoprovid with id=" + manoprovid + " does not belong to the supported types");
		throw new WebApplicationException(builder.build());		
	}

	/********************************************************************************
	 * 
	 * admin VxFOnBoardedDescriptors
	 * 
	 ********************************************************************************/

	@GET
	@Path("/admin/vxfobds/")
	@Produces("application/json")
	public Response getVxFOnBoardedDescriptors() {
		return Response.ok().entity(portalRepositoryRef.getVxFOnBoardedDescriptors()).build();
	}

	@POST
	@Path("/admin/vxfobds/")
	@Produces("application/json")
	@Consumes("application/json")
	public Response addVxFOnBoardedDescriptor( VxFMetadata aVxF ) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		
		if ( aVxF != null ) {
		
			VxFMetadata refVxF =  ( VxFMetadata )portalRepositoryRef.getProductByID( aVxF.getId() );
			VxFOnBoardedDescriptor obd = new VxFOnBoardedDescriptor();
			obd.setVxf( refVxF );
			obd.setUuid( UUID.randomUUID().toString() );
			//?????
			refVxF.getVxfOnBoardedDescriptors().add( obd ) ;
			
			// save product
			refVxF = (VxFMetadata) portalRepositoryRef.updateProductInfo( refVxF );
			

			if (refVxF != null) {
				return Response.ok().entity( refVxF ).build();
			} else {
				ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
				builder.entity("Requested VxFOnBoardedDescriptor with name=" + aVxF.getId() + " cannot be installed");
				throw new WebApplicationException(builder.build());
			}
			
		} else {
			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
			builder.entity("Requested VxFOnBoardedDescriptor has NULL value.");
			throw new WebApplicationException(builder.build());
		}
	}

	@PUT
	@Path("/admin/vxfobds/{mpid}")
	@Produces("application/json")
	@Consumes("application/json")
	public Response updateVxFOnBoardedDescriptor(@PathParam("mpid") int mpid, VxFOnBoardedDescriptor c) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		VxFOnBoardedDescriptor u = portalRepositoryRef.updateVxFOnBoardedDescriptor(c);

		if (u != null) {
			return Response.ok().entity(u).build();
		} else {
			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
			builder.entity("Requested VxFOnBoardedDescriptor with name=" + c.getId() + " cannot be updated");
			throw new WebApplicationException(builder.build());
		}

	}

	@DELETE
	@Path("/admin/vxfobds/{mpid}")
	public Response deleteVxFOnBoardedDescriptor(@PathParam("mpid") int mpid) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		portalRepositoryRef.deleteVxFOnBoardedDescriptor(mpid);
		return Response.ok().build();

	}

	@GET
	@Path("/admin/vxfobds/{mpid}")
	@Produces("application/json")
	public Response getVxFOnBoardedDescriptorById(@PathParam("mpid") int mpid) {
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		VxFOnBoardedDescriptor sm = portalRepositoryRef.getVxFOnBoardedDescriptorByID(mpid);

		if (sm != null) {
			return Response.ok().entity(sm).build();
		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("VxFOnBoardedDescriptor " + mpid + " not found in portal registry");
			return builder.build();
			// throw new WebApplicationException(builder.build());
		}
	}

	@GET
	@Path("/admin/vxfobds/{mpid}/status")
	@Produces("application/json")
	public Response getVxFOnBoardedDescriptorByIdCheckMANOProvider(@PathParam("mpid") int mpid) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		VxFOnBoardedDescriptor obds = portalRepositoryRef.getVxFOnBoardedDescriptorByID(mpid);

		if (obds == null) {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("VxFOnBoardedDescriptor " + mpid + " not found in portal registry");
			return builder.build();
		}
		
		
//		/**
//		 * the following polling will be performed automatically by CAMEL with a timer
//		 */
//
//		if (obds.getOnBoardingStatus().equals(OnBoardingStatus.ONBOARDING)) {
//
//			Vnfd vnfd = null;
//			List<Vnfd> vnfds = OSMClient.getInstance(obds.getObMANOprovider()).getVNFDs();
//			for (Vnfd v : vnfds) {
//				if (v.getId().equalsIgnoreCase(obds.getVxfMANOProviderID())
//						|| v.getName().equalsIgnoreCase(obds.getVxfMANOProviderID())) {
//					vnfd = v;
//					break;
//				}
//			}
//
//			if (vnfd == null) {
//				obds.setOnBoardingStatus(OnBoardingStatus.UNKNOWN);
//			} else {
//				obds.setOnBoardingStatus(OnBoardingStatus.ONBOARDED);
//			}
//
//			obds = portalRepositoryRef.updateVxFOnBoardedDescriptor(obds);
//
//		}

		return Response.ok().entity(obds).build();

	}

	@PUT
	@Path("/admin/vxfobds/{mpid}/onboard")
	@Produces("application/json")
	@Consumes("application/json")
	public Response onBoardDescriptor(@PathParam("mpid") int mpid, final VxFOnBoardedDescriptor vxfobd) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		
		try {
			aMANOController.onBoardVxFToMANOProvider( vxfobd.getId() );
		} catch (Exception e) {				
			e.printStackTrace();
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Requested VxFOnBoardedDescriptor with ID=" + vxfobd.getId() + " cannot be onboarded").build();
		}			
		
		return Response.ok().entity(vxfobd).build();

	}
	
	@PUT
	@Path("/admin/vxfobds/{mpid}/offboard")
	@Produces("application/json")
	@Consumes("application/json")
	public Response offBoardDescriptor(@PathParam("mpid") int mpid, final VxFOnBoardedDescriptor clobd) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		OnBoardingStatus previous_status = clobd.getOnBoardingStatus();
		clobd.setOnBoardingStatus(OnBoardingStatus.OFFBOARDING);
		CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+clobd.getVxf().getName()+" to "+clobd.getOnBoardingStatus());																													
		VxFOnBoardedDescriptor updatedObd = portalRepositoryRef.updateVxFOnBoardedDescriptor(clobd);

		ResponseEntity<String> response = null;
		try {
			response = aMANOController.offBoardVxFFromMANOProvider( updatedObd );			
		}
		catch( HttpClientErrorException e)
		{
			updatedObd.setOnBoardingStatus(previous_status);
			CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+updatedObd.getVxf().getName()+" to "+updatedObd.getOnBoardingStatus());																														
			updatedObd.setFeedbackMessage(e.getResponseBodyAsString());
			updatedObd = portalRepositoryRef.updateVxFOnBoardedDescriptor( updatedObd );
			JSONObject result = new JSONObject(e.getResponseBodyAsString()); //Convert String to JSON Object
			ResponseBuilder builder = Response.status(e.getRawStatusCode()).type(MediaType.TEXT_PLAIN).entity("OffBoarding Failed! "+e.getStatusText()+", "+result.getString("detail"));			
			return builder.build();
		}        
		
		if (response == null) {
			updatedObd.setOnBoardingStatus(previous_status);
			updatedObd.setFeedbackMessage("Null Response on OffBoarding request.Requested VxFOnBoardedDescriptor with ID=\" + updatedObd.getId() + \" cannot be offboarded.");
			updatedObd = portalRepositoryRef.updateVxFOnBoardedDescriptor( updatedObd );
			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
			builder.entity("Requested VxFOnBoardedDescriptor with ID=" + updatedObd.getId() + " cannot be offboarded");
			return builder.build();							
		}
		// UnCertify Upon OffBoarding
		updatedObd.getVxf().setCertified(false);
		updatedObd.setOnBoardingStatus(OnBoardingStatus.OFFBOARDED);
		CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+updatedObd.getVxf().getName()+" to "+updatedObd.getOnBoardingStatus());																																
		updatedObd.setFeedbackMessage(response.getBody().toString());
		updatedObd = portalRepositoryRef.updateVxFOnBoardedDescriptor( updatedObd );
		BusController.getInstance().offBoardVxF( updatedObd.getId() );
		return Response.ok().entity(updatedObd).build();
		
	}

	/********************************************************************************
	 * 
	 * admin ExperimentOnBoardDescriptors
	 * 
	 ********************************************************************************/

	@GET
	@Path("/admin/experimentobds/")
	@Produces("application/json")
	public Response getExperimentOnBoardDescriptors() {
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		return Response.ok().entity(portalRepositoryRef.getExperimentOnBoardDescriptors()).build();
	}

	@POST
	@Path("/admin/experimentobds/")
	@Produces("application/json")
	@Consumes("application/json")
	public Response addExperimentOnBoardDescriptor( ExperimentMetadata exp) {
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		
		
		if ( exp != null ) {
			
			ExperimentMetadata refExp =  ( ExperimentMetadata ) portalRepositoryRef.getProductByID( exp.getId() );
			ExperimentOnBoardDescriptor obd = new ExperimentOnBoardDescriptor();
			obd.setExperiment( refExp );
			obd.setUuid( UUID.randomUUID().toString() );
			refExp.getExperimentOnBoardDescriptors().add( obd ) ;
			
			// save product
			refExp = (ExperimentMetadata) portalRepositoryRef.updateProductInfo( refExp );
			

			if (refExp != null) {
				return Response.ok().entity( refExp ).build();
			} else {
				ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
				builder.entity("Requested VxFOnBoardedDescriptor with name=" + exp.getId() + " cannot be installed");
				throw new WebApplicationException(builder.build());
			}
			
		} else {
			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
			builder.entity("Requested VxFOnBoardedDescriptor with name=" + exp.getId() + " cannot be installed");
			throw new WebApplicationException(builder.build());
		}
		
		
	}

	@PUT
	@Path("/admin/experimentobds/{mpid}")
	@Produces("application/json")
	@Consumes("application/json")
	public Response updateExperimentOnBoardDescriptor(@PathParam("mpid") int mpid, ExperimentOnBoardDescriptor c) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		ExperimentOnBoardDescriptor u = portalRepositoryRef.updateExperimentOnBoardDescriptor(c);

		if (u != null) {
			return Response.ok().entity(u).build();
		} else {
			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
			builder.entity("Requested ExperimentOnBoardDescriptor with name=" + c.getId() + " cannot be updated");
			throw new WebApplicationException(builder.build());
		}

	}

	@DELETE
	@Path("/admin/experimentobds/{mpid}")
	public Response deleteExperimentOnBoardDescriptor(@PathParam("mpid") int mpid) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		portalRepositoryRef.deleteExperimentOnBoardDescriptor(mpid);
		return Response.ok().build();

	}

	@GET
	@Path("/admin/experimentobds/{mpid}")
	@Produces("application/json")
	public Response getExperimentOnBoardDescriptorById(@PathParam("mpid") int mpid) {
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		ExperimentOnBoardDescriptor sm = portalRepositoryRef.getExperimentOnBoardDescriptorByID(mpid);

		if (sm != null) {
			return Response.ok().entity(sm).build();
		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("ExperimentOnBoardDescriptor " + mpid + " not found in portal registry");
			return builder.build();
			// throw new WebApplicationException(builder.build());
		}
	}

	@GET
	@Path("/admin/experimentobds/{mpid}/status")
	@Produces("application/json")
	public Response getExperimentOnBoardDescriptorByIdCheckMANOProvider(@PathParam("mpid") int mpid) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		ExperimentOnBoardDescriptor sm = portalRepositoryRef.getExperimentOnBoardDescriptorByID(mpid);

		if (sm == null) {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("ExperimentOnBoardDescriptor " + mpid + " not found in portal registry");
			return builder.build();
		}

//		
//		if (sm.getOnBoardingStatus().equals(OnBoardingStatus.ONBOARDING)) {
//
//			Nsd nsd = null;
//			List<Nsd> nsds = OSMClient.getInstance(sm.getObMANOprovider()).getNSDs();
//			if ( nsds != null ) {
//				for (Nsd v : nsds) {
//					if (v.getId().equalsIgnoreCase(sm.getVxfMANOProviderID())
//							|| v.getName().equalsIgnoreCase(sm.getVxfMANOProviderID())) {
//						nsd = v;
//						break;
//					}
//				}
//			}
//
//			if (nsd == null) {
//				sm.setOnBoardingStatus(OnBoardingStatus.UNKNOWN);
//			} else {
//				sm.setOnBoardingStatus(OnBoardingStatus.ONBOARDED);
//			}
//
//			sm = portalRepositoryRef.updateExperimentOnBoardDescriptor(sm);
//
//		}

		return Response.ok().entity(sm).build();

	}

	@PUT
	@Path("/admin/experimentobds/{mpid}/onboard")
	@Produces("application/json")
	@Consumes("application/json")
	public Response onExperimentBoardDescriptor(@PathParam("mpid") int mpid, final ExperimentOnBoardDescriptor experimentonboarddescriptor) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
//		c.setOnBoardingStatus(OnBoardingStatus.ONBOARDING);
//		//This is the Deployment ID for the portal		
//		c.setDeployId(UUID.randomUUID().toString());
//		ExperimentMetadata em = c.getExperiment();
//		if (em == null) {
//			em = (ExperimentMetadata) portalRepositoryRef.getProductByID(c.getExperimentid());
//		}
//
//		/**
//		 * The following is not OK. When we submit to OSMClient the createOnBoardPackage
//		 * we just get a response something like response = {"output":
//		 * {"transaction-id": "b2718ef9-4391-4a9e-97ad-826593d5d332"}} which does not
//		 * provide any information. The OSM RIFTIO API says that we could get
//		 * information about onboarding (create or update) jobs see
//		 * https://open.riftio.com/documentation/riftware/4.4/a/api/orchestration/pkt-mgmt/rw-pkg-mgmt-download-jobs.htm
//		 * with /api/operational/download-jobs, but this does not return pending jobs.
//		 * So the only solution is to ask again OSM if something is installed or not, so
//		 * for now the client (the portal ) must check via the
//		 * getVxFOnBoardedDescriptorByIdCheckMANOProvider giving the VNF ID in OSM. OSM
//		 * uses the ID of the yaml description Thus we asume that the vxf name can be
//		 * equal to the VNF ID in the portal, and we use it for now as the OSM ID. Later
//		 * in future, either OSM API provide more usefull response or we extract info
//		 * from the VNFD package
//		 * 
//		 */
//		
//		c.setVxfMANOProviderID(em.getName()); // Possible Error. This probably needs to be setExperimentMANOProviderID(em.getName())
//
//		c.setLastOnboarding(new Date());
//
//		ExperimentOnBoardDescriptor uexpobd = portalRepositoryRef.updateExperimentOnBoardDescriptor(c);
//
//		logger.info("NSD Package Location: " + em.getPackageLocation());		
//		
		try {
			aMANOController.onBoardNSDToMANOProvider( experimentonboarddescriptor.getId() );
		} catch (Exception e) {				
			e.printStackTrace();
	    	logger.error("onExperimentBoardDescriptor, OSM4 fails authentication. Aborting Onboarding action.");
			CentralLogger.log( CLevel.ERROR, "onExperimentBoardDescriptor, OSM4 fails authentication. Aborting Onboarding action.");																	
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Requested Experiment Descriptor with ID=" + experimentonboarddescriptor.getId() + " cannot be onboarded").build();
		}	
		
		return Response.ok().entity(experimentonboarddescriptor).build();
	}

	@PUT
	@Path("/admin/experimentobds/{mpid}/offboard")
	@Produces("application/json")
	@Consumes("application/json")
	public Response offBoardExperimentDescriptor(@PathParam("mpid") int mpid, final ExperimentOnBoardDescriptor c) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		OnBoardingStatus previous_status = c.getOnBoardingStatus();
		c.setOnBoardingStatus(OnBoardingStatus.OFFBOARDING);
		CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+c.getExperiment().getName()+" to "+c.getOnBoardingStatus());																																
		ExperimentOnBoardDescriptor uExper = portalRepositoryRef.updateExperimentOnBoardDescriptor(c);

		ResponseEntity<String> response = null;
		try {
			response = aMANOController.offBoardNSDFromMANOProvider(uExper);
		}
		catch( HttpClientErrorException e)
		{
			uExper.setOnBoardingStatus(previous_status);
			CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+uExper.getExperiment().getName()+" to "+uExper.getOnBoardingStatus());																																	
			uExper.setFeedbackMessage(e.getResponseBodyAsString());
			uExper = portalRepositoryRef.updateExperimentOnBoardDescriptor(uExper);
			JSONObject result = new JSONObject(e.getResponseBodyAsString()); //Convert String to JSON Object
			ResponseBuilder builder = Response.status(e.getRawStatusCode()).type(MediaType.TEXT_PLAIN).entity("OffBoarding Failed! "+e.getStatusText()+", "+result.getString("detail"));			
			return builder.build();
		}        
		
		if (response == null) {
			uExper.setOnBoardingStatus(previous_status);
			CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+uExper.getExperiment().getName()+" to "+uExper.getOnBoardingStatus());																																	
			uExper.setFeedbackMessage("Null response on OffBoarding request.Requested NSOnBoardedDescriptor with ID=\" + c.getId() + \" cannot be offboarded.");			
			uExper = portalRepositoryRef.updateExperimentOnBoardDescriptor( uExper );
			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
			builder.entity("Requested NSOnBoardedDescriptor with ID=" + c.getId() + " cannot be offboarded");
			return builder.build();							
		}
		// Set Valid to false if it is OffBoarded
		uExper.getExperiment().setValid(false);
		uExper.setOnBoardingStatus(OnBoardingStatus.OFFBOARDED);
		CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+uExper.getExperiment().getName()+" to "+uExper.getOnBoardingStatus());																																			
		uExper.setFeedbackMessage(response.getBody().toString());
		uExper = portalRepositoryRef.updateExperimentOnBoardDescriptor( uExper );
		BusController.getInstance().offBoardNSD( uExper.getId() );
		return Response.ok().entity(uExper).build();
	}
	
	/**
	 * 
	 * Infrastructure object API
	 */

	@GET
	@Path("/admin/infrastructures/")
	@Produces("application/json")
	public Response getAdminInfrastructures() {		
		return Response.ok().entity(portalRepositoryRef.getInfrastructures()).build();
	}

	@POST
	@Path("/admin/infrastructures/")
	@Produces("application/json")
	@Consumes("application/json")
	public Response addInfrastructure(Infrastructure c) {
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		Infrastructure u = portalRepositoryRef.addInfrastructure(c);

		if (u != null) {
			return Response.ok().entity(u).build();
		} else {
			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
			builder.entity("Requested Infrastructure with name=" + c.getName() + " cannot be created");
			throw new WebApplicationException(builder.build());
		}
	}

	@PUT
	@Path("/admin/infrastructures/{infraid}")
	@Produces("application/json")
	@Consumes("application/json")
	public Response updateInfrastructure(@PathParam("infraid") int infraid, Infrastructure c) {
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		Infrastructure infrastructure = portalRepositoryRef.getInfrastructureByID(infraid);
		
		infrastructure.setDatacentername( c.getDatacentername());
		infrastructure.setEmail( c.getEmail());
		infrastructure.setVIMid( c.getVIMid());
		infrastructure.setName( c.getName());
		infrastructure.setOrganization(c.getOrganization());

		Infrastructure u = portalRepositoryRef.updateInfrastructureInfo( infrastructure );

		if (u != null) {
			return Response.ok().entity(u).build();
		} else {
			ResponseBuilder builder = Response.status(Status.INTERNAL_SERVER_ERROR);
			builder.entity("Requested Infrastructure with name=" + c.getName() + " cannot be updated");
			throw new WebApplicationException(builder.build());
		}

	}

	@DELETE
	@Path("/admin/infrastructures/{infraid}")
	public Response deleteInfrastructure(@PathParam("infraid") int infraid) {
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		portalRepositoryRef.deleteInfrastructure(infraid);
		return Response.ok().build();

	}

	@GET
	@Path("/admin/infrastructures/{infraid}")
	@Produces("application/json")
	public Response getInfrastructureById(@PathParam("infraid") int infraid) {
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return Response.status(Status.FORBIDDEN ).build() ;
		}
		Infrastructure sm = portalRepositoryRef.getInfrastructureByID(infraid);

		if (sm != null) {
			return Response.ok().entity(sm).build();
		} else {
			ResponseBuilder builder = Response.status(Status.NOT_FOUND);
			builder.entity("Infrastructure " + infraid + " not found in portal registry");
			throw new WebApplicationException(builder.build());
		}
	}
	
	
	@POST
	@Path("/admin/infrastructures/{infraid}/images/{vfimageid}")
	@Produces("application/json")
	@Consumes("application/json")
	public Response addImageToInfrastructure(@PathParam("infraid") int infraid, @PathParam("vfimageid") int vfimageid) {
		
		
		if ( !sc.isUserInRole( UserRoleType.PORTALADMIN.name() ) && !sc.isUserInRole(UserRoleType.TESTBED_PROVIDER.name() ) ){
			 return Response.status(Status.FORBIDDEN ).build();
		}
		
		Infrastructure infrs = portalRepositoryRef.getInfrastructureByID(infraid);
		VFImage vfimg = portalRepositoryRef.getVFImageByID(vfimageid);

		if ( (infrs != null) && (vfimg != null)) {
			
			if ( vfimg.getDeployedInfrastructureById(infrs.getId() ) ==null ){
				vfimg.getDeployedInfrastructures().add(infrs);
			}
			if ( infrs.getSupportedImageById( vfimg.getId() ) == null ){
				infrs.getSupportedImages().add(vfimg);
			}
			
			portalRepositoryRef.updateVFImageInfo(vfimg);
			portalRepositoryRef.updateInfrastructureInfo(infrs);
			return Response.ok().entity( infrs ).build();
		} else {
			ResponseBuilder builder = Response.status(Status.BAD_REQUEST );
			builder.entity("Requested Image cannot added to Infrastructure");
			throw new WebApplicationException(builder.build());
		}
	}
	
	/**
	 * Validation Result
	 */
	@PUT
	@Path("/admin/validationjobs/{vxf_id}")
	@Produces("application/json")
	@Consumes("application/json")
	public Response updateUvalidationjob(@PathParam("vxf_id") int vxfid, ValidationJobResult vresult) {
		logger.info("Received PUT ValidationJobResult for vxfid: " + vresult.getVxfid() );		
		
		if ( !sc.isUserInRole( UserRoleType.PORTALADMIN.name() ) && !sc.isUserInRole(UserRoleType.TESTBED_PROVIDER.name() ) ){
			 return Response.status(Status.FORBIDDEN ).build();
		}
		
		vxfid = vresult.getVxfid();
		
		Product prod = portalRepositoryRef.getProductByID(vxfid) ;
		
		if ( prod == null )
		{
			logger.info("updateUvalidationjob: prod == null for VXF with id=" + vxfid + ". Return Status NOT_FOUND");		
			CentralLogger.log( CLevel.INFO, "updateUvalidationjob: prod == null for VXF with id=" + vxfid + ". Return Status NOT_FOUND");																						
			return Response.status(Status.NOT_FOUND).build();
		}
		if ( !(prod instanceof VxFMetadata) )
		{
			logger.info("updateUvalidationjob: prod not instance of VxFMetadata for VXF with id=" + vxfid + ". Return Status NOT_FOUND");		
			CentralLogger.log( CLevel.INFO, "updateUvalidationjob: prod == null for VXF with id=" + vxfid + ". Return Status NOT_FOUND");																						
			return Response.status(Status.NOT_FOUND).build();
		}
		
		VxFMetadata vxf = (VxFMetadata) prod;
		
//		We select by desing not to certify upon Successful Validation. Thus we comment this.
//		if ( vresult.getValidationStatus() ) {
//			vxf.setCertified( true );
//			vxf.setCertifiedBy( "5GinFIRE " );			
//		}
		vxf.setValidationStatus( ValidationStatus.COMPLETED );
		
		ValidationJob validationJob = new ValidationJob();
		validationJob.setDateCreated( new Date() );
		validationJob.setJobid( vresult.getBuildId() + "" );
		validationJob.setOutputLog( vresult.getOutputLog() );
		validationJob.setValidationStatus(vresult.getValidationStatus() );
		validationJob.setVxfid(vxfid); 
		vxf.getValidationJobs().add( validationJob );

		// save product
		vxf = (VxFMetadata) portalRepositoryRef.updateProductInfo( vxf );		
		
		BusController.getInstance().updatedVxF( vxf.getId() );		
		BusController.getInstance().updatedValidationJob( vxf.getId() );		
		
		VxFMetadata vxfr = (VxFMetadata) portalRepositoryRef.getProductByID( vxfid) ; //rereading this, seems to keep the DB connection
		return Response.ok().entity( vxfr ).build();
	}
	
	
	
	
	
	/**
	 * 
	 * SFA related
	 */
	
	@POST
	@Path("/sfawrap")
	@Produces("text/xml")
	@Consumes("text/xml")
	public Response getSFA( String xmlreq) {
		logger.info("/sfawrap param=" + xmlreq );
		//AggregateManager.listAvailableResources
		

		
		List<VxFMetadata> vxfs = portalRepositoryRef.getVxFs( null, true);
		
		Date d = new Date();
		StringBuilder sfaresponse = new StringBuilder();
		//sfaresponse.append( "<?xml version=\"1.0\"?>" );
		sfaresponse.append( "<rspec xmlns=\"http://www.protogeni.net/resources/rspec/2\"  type=\"advertisement\" valid_until=\"" + "2020-05-20T16:03:57+03:00" +  "\" generated=\"" + "2018-06-20T16:03:57+03:00"  + "\">" );
		sfaresponse.append( "<statistics call=\"ListResources\">" );
		sfaresponse.append( "<aggregate status=\"success\" name=\"5ginfire\" elapsed=\"0.1\"/>" );
		sfaresponse.append( "</statistics>" );
		sfaresponse.append( "<network name=\"5ginfire\">" );
		
		for (VxFMetadata vxFMetadata : vxfs) {
			sfaresponse.append( " <node component_manager_id=\"urn:publicid:IDN+5ginfire+authority+cm\"  component_id=\"urn:publicid:IDN+upatras:p2e+node+" + vxFMetadata.getUuid()  + "\"  component_name=\"" + vxFMetadata.getName() + "\" site_id=\"urn:publicid:IDN+5ginfire:p2e+authority+sa\">" );
			sfaresponse.append( "<displayname>" + vxFMetadata.getName() + "</displayname>" );
			sfaresponse.append( "<package>" + vxFMetadata.getPackageLocation()  + "</package>" );
			sfaresponse.append( "" );
			sfaresponse.append( "<location country=\"unknown\" longitude=\"21.7885\" latitude=\"38.2845\"/>" );
			sfaresponse.append( " <description>" + vxFMetadata.getShortDescription() + "</description>" );
			sfaresponse.append( "" );
			sfaresponse.append( "<lease from=\"" + d.toString() + "\" until=\"" + d.toString() + "\">false</lease>" );
			sfaresponse.append( "</node>" );
		}
		
		
		
		sfaresponse.append( "</network>" );
		sfaresponse.append( "</rspec>" );
		

		
		return Response.ok().entity( sfaresponse.toString() ).build();

	}

}
