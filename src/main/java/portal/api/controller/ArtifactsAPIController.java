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

package portal.api.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import OSM5NBIClient.OSM5Client;
import OSM5Util.OSM5ArchiveExtractor.OSM5NSExtractor;
import OSM5Util.OSM5ArchiveExtractor.OSM5VNFDExtractor;
import OSM5Util.OSM5NSReq.OSM5NSRequirements;
import OSM5Util.OSM5VNFReq.OSM5VNFRequirements;
import centralLog.api.CLevel;
import centralLog.api.CentralLogger;
import io.openslice.model.Category;
import io.openslice.model.ConstituentVxF;
import io.openslice.model.DeploymentDescriptor;
import io.openslice.model.DeploymentDescriptorStatus;
import io.openslice.model.ExperimentMetadata;
import io.openslice.model.ExperimentOnBoardDescriptor;
import io.openslice.model.Infrastructure;
import io.openslice.model.MANOplatform;
import io.openslice.model.MANOprovider;
import io.openslice.model.OnBoardingStatus;
import io.openslice.model.PackagingFormat;
import io.openslice.model.PortalProperty;
import io.openslice.model.PortalUser;
import io.openslice.model.Product;
import io.openslice.model.UserRoleType;
import io.openslice.model.VFImage;
import io.openslice.model.ValidationJob;
import io.openslice.model.ValidationStatus;
import io.openslice.model.VxFMetadata;
import io.openslice.model.VxFOnBoardedDescriptor;
import osm5.ns.riftware._1._0.project.nsd.rev170228.project.nsd.catalog.Nsd;
import portal.api.bus.BusController;
import portal.api.mano.MANOController;
import portal.api.service.CategoryService;
import portal.api.service.DeploymentDescriptorService;
import portal.api.service.InfrastructureService;
import portal.api.service.ManoPlatformService;
import portal.api.service.ManoProviderService;
import portal.api.service.NSDOBDService;
import portal.api.service.NSDService;
import portal.api.service.PortalPropertiesService;
import portal.api.service.ProductService;
import portal.api.service.UsersService;
import portal.api.service.VFImageService;
import portal.api.service.VxFOBDService;
import portal.api.service.VxFService;
import portal.api.util.AttachmentUtil;
import portal.api.validation.ci.ValidationJobResult;



@RestController
//@RequestMapping("/repo")
public class ArtifactsAPIController {
//	
	
//	private MANOController aMANOController;

	private static final transient Log logger = LogFactory.getLog(ArtifactsAPIController.class.getName());

	private static final String METADATADIR = System.getProperty("user.home") + File.separator + ".portal"
			+ File.separator + "metadata" + File.separator;
	
    @Resource(name="authenticationManager")
    private AuthenticationManager authManager;
    
	@Autowired
	PortalPropertiesService propsService;

	@Autowired
    UsersService usersService;

	@Autowired
	ManoProviderService manoProviderService;

	@Autowired
	VxFService vxfService;

	@Autowired
	NSDService nsdService;

	@Autowired
	VxFOBDService vxfOBDService;
	@Autowired
	NSDOBDService nsdOBDService;

	@Autowired
	VFImageService vfImageService;

	@Autowired
	ProductService productService;

	@Autowired
	ManoPlatformService manoPlatformService;
	
	
	@Autowired
	MANOController aMANOController;

	@Autowired
	CategoryService categoryService;
	

	@Autowired
	InfrastructureService infrastructureService;

	@Autowired
	DeploymentDescriptorService deploymentDescriptorService;

	@Autowired
	ObjectMapper objectMapper;
	
	// VxFS API

	private Product addNewProductData(Product prod, MultipartFile image, MultipartFile submittedFile,  MultipartFile[] screenshots, HttpServletRequest request ) throws IOException {

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

		//URI endpointUrl = uri.getBaseUri();

		String endpointUrl = request.getRequestURI();

		String tempDir = METADATADIR + uuid + File.separator;
		
		Files.createDirectories(Paths.get(tempDir));			
				
		// If an icon is submitted
		if (image != null) {
			// Get the icon filename
			String imageFileNamePosted = image.getOriginalFilename() ;// AttachmentUtil.getFileName(image.getHeaders());
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
			String aFileNamePosted = submittedFile.getOriginalFilename();// AttachmentUtil.getFileName(submittedFile.getHeaders());
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

						if(((VxFMetadata) prod).getPackagingFormat().name().equals("OSMvFIVE"))
						{
							logger.info("VxF OSMvFIVE route");	
							this.loadVxfMetadataFromOSMvFIVEVxFDescriptorFile( (VxFMetadata) prod, descriptorFile, request);
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
							
						if(((ExperimentMetadata) prod).getPackagingFormat().name().equals("OSMvFIVE"))
						{
							logger.info("VxF OSMvFIVE route");	
							this.loadNSMetadataFromOSMvFIVENSDescriptorFile( (ExperimentMetadata) prod, descriptorFile, request);															
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
		
		String screenshotsFilenames = "";
		int i = 1;
		for (MultipartFile shot : screenshots) {
			String shotFileNamePosted = shot.getOriginalFilename();// AttachmentUtil.getFileName(shot.getHeaders());
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
			Category catToUpdate = categoryService.findById( c.getId() );
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
		PortalUser vxfOwner = usersService.findById( prod.getOwner().getId() );
		vxfOwner.addProduct(prod);
		prod.setOwner(vxfOwner); // replace given owner with the one from our DB

		PortalUser owner = usersService.updateUserInfo(  vxfOwner);
		
		Product registeredProd = null;
		if ( prod instanceof VxFMetadata ){
			registeredProd = vxfService.getVxFtByUUID(uuid);
		} else {
			throw new NotImplementedException("NOT IMPLEMENTED YET FOR NSD PRODUCT" );
		}

		// now fix category references
		for (Category c : registeredProd.getCategories()) {
			Category catToUpdate = categoryService.findById(c.getId());
			catToUpdate.addProduct(registeredProd);
			categoryService.updateCategoryInfo(catToUpdate);
		}		
		return registeredProd;
	}


	private void loadNSMetadataFromOSMvFIVENSDescriptorFile(ExperimentMetadata prod,File aNSDdescriptorFile, HttpServletRequest request) throws IOException,NullPointerException
	{
		// Create a nsExtractor Object for the OSMvTWO file 		
		OSM5NSExtractor nsExtract = new OSM5NSExtractor(aNSDdescriptorFile);
		// Get the nsd object out of the file info		
		osm5.ns.yang.nfvo.nsd.rev170228.nsd.catalog.Nsd ns = nsExtract.extractNsDescriptor();
		if (ns != null) {
			//*************LOAD THE Product Object from the NSD Descriptor START************************************
			// Check if a vnfd with this id already exists in the DB
			
			ExperimentMetadata existingmff = nsdService.getNSDByName( ns.getAddedId() );														
			if ( ( existingmff != null  ) ) {
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

				
				VxFMetadata vxf = (VxFMetadata) vxfService.getVxFByName(v.getVnfdIdRef());

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
					prod.setIconsrc( request.getRequestURI().toString().replace("http:", "") + "repo/images/" + prod.getUuid()
							+ "/" + imageFileNamePosted);
				}
			}
			//*************LOAD THE Product Object from the NSD Descriptor END************************************			
		} else {
			throw new NullPointerException();
		}
		
	}	


	private void loadVxfMetadataFromOSMvFIVEVxFDescriptorFile(VxFMetadata prod,File aVxFdescriptorFile, HttpServletRequest request) throws IOException, NullPointerException
	{
		// Create a vnfExtractor Object for the OSMvFIVE file 
		OSM5VNFDExtractor vnfExtract = new OSM5VNFDExtractor(aVxFdescriptorFile);
		// Get the vnfd object out of the file info
		osm5.ns.yang.nfvo.vnfd.rev170228.vnfd.catalog.Vnfd vnfd = vnfExtract.extractVnfdDescriptor();
		if (vnfd != null) {							
			//*************LOAD THE Product Object from the VNFD Descriptor START************************************
			// Check if a vnfd with this id already exists in the DB
			VxFMetadata existingvmf = vxfService.getVxFByName( vnfd.getAddedId());														
			if ( ( existingvmf != null  ) ) {
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
					VFImage sm = vfImageService.getVFImageByName( imageName );
					if ( sm == null ){
						sm = new VFImage();
						sm.setName( imageName );
						PortalUser vfImagewner =  usersService.findById(prod.getOwner().getId());
						sm.setOwner( vfImagewner );
						sm.setShortDescription( "Automatically created during vxf " + prod.getName() + " submission. Owner must update." );
						String uuidVFImage = UUID.randomUUID().toString();
						sm.setUuid( uuidVFImage );
						sm.setDateCreated(new Date());
						sm = vfImageService.saveVFImage( sm );
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
					prod.setIconsrc( request.getRequestURI().toString().replace("http:", "") + "repo/images/" + prod.getUuid()
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
	

	private void updateVxfMetadataFromOSMvFIVEVxFDescriptorFile(Product prevProduct,File aVxFdescriptorFile, HttpServletRequest request) throws IOException, NullPointerException
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
				vfImageService.updateVFImageInfo(img);
			}			
			((VxFMetadata) prevProduct).getVfimagesVDU().clear();//clear previous referenced images
			for (osm5.ns.riftware._1._0.vnfd.base.rev170228.vnfd.descriptor.Vdu vdu : vnfd.getVdu()) {
				String imageName = vdu.getImage();
				if ( ( imageName != null) && (!imageName.equals("")) ){
					VFImage sm = vfImageService.getVFImageByName( imageName );
					if ( sm == null ){
						sm = new VFImage();
						sm.setName( imageName );
						PortalUser vfImagewner = usersService.findById(prevProduct.getOwner().getId());
						sm.setOwner( vfImagewner );
						sm.setShortDescription( "Automatically created during vxf " + prevProduct.getName() + " submission. Owner must update." );
						String uuidVFImage = UUID.randomUUID().toString();
						sm.setUuid( uuidVFImage );
						sm.setDateCreated(new Date());
						sm = vfImageService.saveVFImage( sm );
					}
					if ( !((VxFMetadata) prevProduct).getVfimagesVDU().contains(sm) ){
						((VxFMetadata) prevProduct).getVfimagesVDU().add( sm );
						sm.getUsedByVxFs().add( ((VxFMetadata) prevProduct) );
						vfImageService.updateVFImageInfo( sm );
						
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
					prevProduct.setIconsrc( request.getRequestURI().toString().replace("http:", "") + "repo/images/" + prevProduct.getUuid()
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
	
	private void updateNSMetadataFromOSMvFIVENSDescriptorFile(Product prevProduct, File aNSDdescriptorFile, HttpServletRequest request) throws IOException,NullPointerException {
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

				VxFMetadata vxf = vxfService.getVxFByName(v.getVnfdIdRef());

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
					prevProduct.setIconsrc( request.getRequestURI().toString().replace("http:", "") + "repo/images/" + prevProduct.getUuid()
							+ "/" + imageFileNamePosted);
				}
			}
			//*************LOAD THE Product Object from the NSD Descriptor END************************************			
		} else {
			throw new NullPointerException();
		}

	}
	
	/******************* VxFs API ***********************/

	@GetMapping( value = "/vxfs", produces = "application/json" )
	public ResponseEntity<?> getAllVxFs( @RequestParam ( name = "categoryid", required = false) Long categoryid) {
		
		logger.info("getVxFs categoryid=" + categoryid);
		List<VxFMetadata> vxfs = vxfService.getPublishedVxFsByCategory(categoryid); // portalRepositoryRef.getVxFs(categoryid, true);
		return ResponseEntity.ok( vxfs );		
	}
	
	@GetMapping( value = "/admin/vxfs", produces = "application/json" )
	public  ResponseEntity<?> getVxFs(@RequestParam( name = "categoryid", required = false) Long categoryid) {
		logger.info("getVxFs categoryid=" + categoryid);
		

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		
		PortalUser u =  usersService.findByUsername( authentication.getName() );

		if (u != null) {
			List<VxFMetadata> vxfs;

			if (u.getRoles().contains(UserRoleType.ROLE_ADMIN ) ) {
				vxfs = vxfService.getVxFsByCategory(categoryid);
			} else {
				vxfs = vxfService.getVxFsByUserID((long) u.getId());
			}

			return ResponseEntity.ok( vxfs );		

		} else {
			return (ResponseEntity<?>) ResponseEntity.badRequest().body( "User not found in registry");
		}

	}


	@PostMapping( value =  "/admin/vxfs" )
	public ResponseEntity<?> addVxFMetadata(
			@ModelAttribute("vxf") String avxf,
			@RequestParam( name = "prodIcon", required = false) MultipartFile  prodIcon,
			@RequestParam("prodFile")  MultipartFile  prodFile,
			@RequestParam(name = "screenshots", required = false) MultipartFile[] screenshots,
			HttpServletRequest request
			) {
		
		VxFMetadata vxf = null;
		try {
			vxf = objectMapper.readValue( avxf, VxFMetadata.class);	
		} catch (JsonParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}  

		PortalUser u =  usersService.findByUsername( SecurityContextHolder.getContext().getAuthentication().getName() );
		

		if (u == null) {
			return (ResponseEntity<?>) ResponseEntity.badRequest().body( "User not found in registry");
		}
		
		String emsg = "";

		VxFMetadata vxfsaved = null;
		try {
			
			logger.info("Received @POST for vxf : " + vxf.getName());
			logger.info("Received @POST for vxf.extensions : " + vxf.getExtensions());
			vxfsaved = (VxFMetadata) addNewProductData(vxf, prodIcon, prodFile, screenshots, request );				
		} catch (Exception e) {
			vxfsaved = null;
			e.printStackTrace();
			logger.error( e.getMessage() );
			emsg =  e.getMessage();
		}


		if (vxfsaved != null) {		
			//======================================================
			// AUTOMATIC ONBOARDING PROCESS -START
			// Need to select MANO Provider, convert vxfMetadata to VxFOnBoardedDescriptor and pass it as an input.
			
			// Get the MANO providers which are set for automatic onboarding
			
			BusController.getInstance().newVxFAdded( vxf.getId() );
			
			List<MANOprovider> MANOprovidersEnabledForOnboarding =  manoProviderService.getMANOprovidersEnabledForOnboarding();
			
			if(MANOprovidersEnabledForOnboarding.size()>0 && vxfsaved.getPackagingFormat() == PackagingFormat.OSMvFIVE)
			{
				for(MANOprovider mp : MANOprovidersEnabledForOnboarding)
				{
					//Create VxfOnboardedDescriptor
					VxFOnBoardedDescriptor obd = new VxFOnBoardedDescriptor();
					// Get the first one for now			
					obd.setObMANOprovider(mp);
					obd.setUuid( UUID.randomUUID().toString() );
					VxFMetadata refVxF =  ( VxFMetadata ) vxfService.getProductByID( vxfsaved.getId() );
					// Fill the VxFMetadata of VxFOnBoardedDescriptor
					obd.setVxf( refVxF );
					// Update the VxFMetadata Object with the obd Object
					refVxF.getVxfOnBoardedDescriptors().add( obd ) ;				
					
					// ???????
					obd.setVxf( refVxF );

					// save product
					refVxF = (VxFMetadata) vxfService.updateProductInfo( refVxF );						

					// save VxFonBoardedDescriptor or not ???
					obd = vxfOBDService.updateVxFOnBoardedDescriptor(obd);
					
					//set proper scheme (http or https)
					MANOController.setHTTPSCHEME( request.getRequestURL().toString()  );
					
					if ( obd.getVxf().getOwner() == null ) {
						logger.error( " ========> obd.getVxf().getOwner() == null " );
					}
					
					// Send the message for automatic onboarding
					BusController.getInstance().onBoardVxFAdded( obd.getId() );
				}
			}
			// AUTOMATIC ONBOARDING PROCESS -END
			//======================================================
			VxFMetadata vxfr =  ( VxFMetadata ) vxfService.getProductByID( vxfsaved.getId() );//rereading this, seems to keep the DB connection
			BusController.getInstance().validateVxF(vxfr.getId());	
			return ResponseEntity.ok( vxfr  );	
		} else {
			return (ResponseEntity<?>) ResponseEntity.status( HttpStatus.INTERNAL_SERVER_ERROR ).body("{ \"message\" : \"Requested entity cannot be installed. " + emsg +"\"}");
			//return (ResponseEntity<?>) ResponseEntity.badRequest().body( "Requested entity cannot be installed. " + emsg );
		}
			

	}
	
	/**
	 * @param userID
	 * @return true if user logged is equal to the requested id of owner, or is ROLE_ADMIN
	 */
	private boolean checkUserIDorIsAdmin(long userID){

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		
		logger.info("principal 1=  " + authentication.getAuthorities().contains( new SimpleGrantedAuthority( UserRoleType.ROLE_ADMIN.getValue()  ) ));
		logger.info("principal 2=  " + authentication.getAuthorities().contains( new SimpleGrantedAuthority(  UserRoleType.ROLE_TESTBED_PROVIDER.getValue() ) ));
		logger.info("principal 2=  " + authentication.getAuthorities().contains( new SimpleGrantedAuthority(  UserRoleType.ROLE_MENTOR.getValue() ) ));

		if ( authentication.getAuthorities().contains( new SimpleGrantedAuthority( UserRoleType.ROLE_ADMIN.getValue() ))){
			logger.info("checkUserIDorIsAdmin, authentication role =  " + authentication.getAuthorities().contains( new SimpleGrantedAuthority( UserRoleType.ROLE_ADMIN.getValue()  ) ));
			return true;
		}

		PortalUser uToFind = usersService.findById(  userID );
		if ( (uToFind !=null )  && ( uToFind.getUsername()  == authentication.getName()) ){
			logger.info("checkUserIDorIsAdmin, user is equal with request");
			return true;
		} 
		

		return false;
	}

	@PutMapping( value =  "/admin/vxfs/{bid}", produces = "application/json", consumes = "multipart/form-data" )
	public ResponseEntity<?> updateVxFMetadata(@PathVariable("bid") int bid, 
			@ModelAttribute("vxf") String avxf,
			@RequestParam( name = "prodIcon", required = false) MultipartFile  prodIcon,
			@RequestParam("prodFile")  MultipartFile  prodFile,
			@RequestParam(name = "screenshots", required = false) MultipartFile[] screenshots,
			HttpServletRequest request) {
		
		VxFMetadata vxf = null;
		try {
			vxf = objectMapper.readValue( avxf, VxFMetadata.class);	
		} catch (JsonParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}  
		
		PortalUser u =  usersService.findByUsername( SecurityContextHolder.getContext().getAuthentication().getName() );
		

		if (u == null) {
			return (ResponseEntity<?>) ResponseEntity.badRequest().body( "User not found in registry");
		}

		String emsg = "";
		VxFMetadata vxfsaved = null;

		try {			
			
			if ( !checkUserIDorIsAdmin( vxf.getOwner().getId() ) ){
				return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN);
			}
			

			logger.info("Received @PUT for vxf : " + vxf.getName());
			logger.info("Received @PUT for vxf.extensions : " + vxf.getExtensions());

			vxfsaved = (VxFMetadata) updateProductMetadata( vxf, prodIcon, prodFile, screenshots, request );
		}
		catch (IOException e) {
			vxfsaved = null;
			e.printStackTrace();
			logger.error( e.getMessage() );
			emsg =  e.getMessage();
		}
		
		if (vxfsaved != null) { 

			
			BusController.getInstance().updatedVxF( vxfsaved.getId());
			//notify only if validation changed

			if ( prodFile!= null ) { //if the descriptor changed then we must re-trigger validation
				
				String vxfFileNamePosted = prodFile.getName();
				if ( !vxfFileNamePosted.equals("unknown") ){
					
					BusController.getInstance().validateVxF(vxf.getId());
				}
			}

			return ResponseEntity.ok( vxf );	
		} else {
			return (ResponseEntity<?>) ResponseEntity.badRequest().body( "Requested entity cannot be installed. " + emsg );		
		}

	}

	// VxFs related API

	private Product updateProductMetadata(Product prod, MultipartFile image, MultipartFile prodFile,  
			MultipartFile[] screenshots, HttpServletRequest request  ) throws IOException {

		logger.info("userid = " + prod.getOwner().getId());
		logger.info("prodname = " + prod.getName());
		logger.info("prodid = " + prod.getId());

		logger.info("produuid = " + prod.getUuid());
		logger.info("version = " + prod.getVersion());
		logger.info("shortDescription = " + prod.getShortDescription());
		logger.info("longDescription = " + prod.getLongDescription());

		
		

		// first remove all references of the product from the previous
		// categories
		
		
		Product prevProduct =  (Product) productService.getProductByID(prod.getId());
		
		
		prevProduct.setDateUpdated(new Date());
		
		for (Category c : prevProduct.getCategories()) {
			// logger.info("Will remove product "+prodPreUpdate.getName()+ ",
			// from Previous Category "+c.getName() );
			c.removeProduct(prevProduct);
			categoryService.updateCategoryInfo(c);
		}
		prevProduct.getCategories().clear();

		// we must replace API given product categories with the ones from our
		// DB
		for (Category c : prod.getCategories()) {
			Category catToUpdate = categoryService.findById(c.getId());
			// logger.info("BEFORE PROD SAVE, category "+catToUpdate.getName()+"
			// contains Products: "+ catToUpdate.getProducts().size() );
			//prod.getCategories().set(prod.getCategories().indexOf(c), catToUpdate);
			prevProduct.getCategories().add(catToUpdate);
		}

		String endpointUrl = request.getRequestURI();

		String tempDir = METADATADIR + prevProduct.getUuid() + File.separator;

		Files.createDirectories(Paths.get(tempDir));

		// If an icon is submitted
		if (image != null) {
			// Get the icon filename			
			String imageFileNamePosted =  image.getOriginalFilename();// AttachmentUtil.getFileName(image.getHeaders());
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
				MANOplatform mpdb = manoPlatformService.getMANOplatformByID( mp.getId());
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
			String vxfFileNamePosted = prodFile.getOriginalFilename() ;// AttachmentUtil.getFileName(prodFile.getHeaders());
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

						if(((VxFMetadata) prod).getPackagingFormat().name().equals("OSMvFIVE"))
						{
							logger.info("VxF OSMvFIVE route");	
							this.updateVxfMetadataFromOSMvFIVEVxFDescriptorFile(prevProduct, descriptorFile, request);
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

						if(((ExperimentMetadata) prod).getPackagingFormat().name().equals("OSMvFIVE"))
						{
							logger.info("NSD OSMvFIVE route");
							this.updateNSMetadataFromOSMvFIVENSDescriptorFile(prevProduct, descriptorFile, request);															
						}
					}
					catch (NullPointerException e)
					{
						return null;
					}					
					
				}

			}
		}

			String screenshotsFilenames = "";
			int i = 1;
			for ( MultipartFile shot : screenshots ) {
				String shotFileNamePosted = shot.getOriginalFilename(); //AttachmentUtil.getFileName(shot.getHeaders());
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
		prevProduct = productService.updateProductInfo( prevProduct );

		// now fix category product references
		for (Category catToUpdate : prevProduct.getCategories()) {
			//Product p = portalRepositoryRef.getProductByID(prod.getId());
			Category c = categoryService.findById(catToUpdate.getId() );
			c.addProduct( prevProduct );
			categoryService.updateCategoryInfo(c);
		}
		

//		if (vxfOwner.getProductById(prod.getId()) == null)
//			vxfOwner.addProduct(prod);
//		portalRepositoryRef.updateUserInfo( vxfOwner);
		return prevProduct;
	}



	

	@GetMapping( value = "/images/{uuid}/{imgfile}", produces = { "image/jpeg",  "image/png" } )
	public @ResponseBody byte[] getEntityImage( @PathVariable("uuid") String uuid, @PathVariable("imgfile") String imgfile) throws IOException {
		logger.info("getEntityImage of uuid: " + uuid);
		String imgAbsfile = METADATADIR + uuid + File.separator + imgfile;
		logger.info("Image RESOURCE FILE: " + imgAbsfile);
		File file = new File(imgAbsfile);

		InputStream in = new FileInputStream( file );
	
		return IOUtils.toByteArray(in);
		
		// ResponseBuilder response = Response.ok((Object) file );
		// logger.info( "attachment; filename=" + file.getName() );
		// response.header("Content-Disposition", "attachment; filename=" +
		// file.getName());
		// return response.build();
		// String mediaType = SomeContentTypeMapHere(file)
		//return Response.ok(file).build();
	}

	@GetMapping( value = "/packages/{uuid}/{vxffile}", produces = "application/gzip" )
	public @ResponseBody byte[] downloadVxFPackage( @PathVariable("uuid") String uuid, @PathVariable("vxffile") String vxffile) throws IOException {

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
		InputStream in = new FileInputStream( file );	
		return IOUtils.toByteArray(in);
		
//		ResponseBuilder response = (ResponseEntity<?>).ok((Object) file);
//		response.header("Content-Disposition", "attachment; filename=" + file.getName());
//		return response.build();
	}

	@DeleteMapping( value =  "/admin/vxfs/{vxfid}", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?> deleteVxF( @PathVariable("vxfid") int vxfid) {
				
		VxFMetadata vxf = (VxFMetadata) vxfService.getProductByID( vxfid );
		
		if ( !checkUserIDorIsAdmin( vxf.getOwner().getId() ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN);
		}
		// Get the OnBoarded Descriptors to OffBoard them
		List<VxFOnBoardedDescriptor>  vxfobds = vxf.getVxfOnBoardedDescriptors();
		if(vxf.isCertified())
		{
			return (ResponseEntity<?>) ResponseEntity.badRequest().body( "vxf with id=" + vxfid + " is Certified and will not be deleted");
						
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
				
				VxFOnBoardedDescriptor u = vxfOBDService.updateVxFOnBoardedDescriptor( vxfobd_tmp );

				ResponseEntity<String> response = null;
				try {
					response = aMANOController.offBoardVxFFromMANOProvider( vxfobd_tmp );
				}
				catch( HttpClientErrorException e)
				{
					vxfobd_tmp.setOnBoardingStatus(previous_status);
					CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+vxfobd_tmp.getVxf().getName()+" to "+vxfobd_tmp.getOnBoardingStatus());																											
					vxfobd_tmp.setFeedbackMessage(e.getResponseBodyAsString());
					u = vxfOBDService.updateVxFOnBoardedDescriptor(vxfobd_tmp);
					JSONObject result = new JSONObject(e.getResponseBodyAsString()); //Convert String to JSON Object
					ResponseEntity<?> builder = (ResponseEntity<?>) ResponseEntity.status(e.getRawStatusCode()).body("OffBoarding Failed! "+e.getStatusText()+", "+result.getString("detail"));			
					return builder;
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
					u = vxfOBDService.updateVxFOnBoardedDescriptor(vxfobd_tmp);
					ResponseEntity<?> builder = (ResponseEntity<?>) ResponseEntity.status( HttpStatus.INTERNAL_SERVER_ERROR ).body( "Requested VxFOnBoardedDescriptor with ID=" + vxfobd_tmp.getId() + " cannot be offboarded" );			
					return builder;
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
				u = vxfOBDService.updateVxFOnBoardedDescriptor(vxfobd_tmp);
				BusController.getInstance().offBoardVxF( u.getId() );
			}
		}
		BusController.getInstance().deletedVxF( vxf.getId() );			
		vxfService.deleteProduct( vxf );
		return ResponseEntity.ok().body("{}");		
	}

	@GetMapping( value = "/vxfs/{vxfid}", produces = "application/json" )
	public ResponseEntity<?> getVxFMetadataByID( @PathVariable("vxfid") int vxfid) {
		logger.info("getVxFMetadataByID  vxfid=" + vxfid);
		VxFMetadata vxf = (VxFMetadata) vxfService.getProductByID( vxfid );

		if (vxf != null) {
			
			if ( !vxf.isPublished() ){
				return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN);
			}

			return ResponseEntity.ok( vxf );
		} else {
			return (ResponseEntity<?>) ResponseEntity.badRequest().body( "vxf with id=" + vxfid + " not found in portal registry");
		}
	}

	@GetMapping( value = "/admin/vxfs/{vxfid}", produces = "application/json" )
	public  ResponseEntity<?> getAdminVxFMetadataByID(@PathVariable("vxfid") int vxfid) {

		logger.info("getAdminVxFMetadataByID  vxfid=" + vxfid);
		VxFMetadata vxf = (VxFMetadata) vxfService.getProductByID( vxfid );

		if (vxf != null) {

			PortalUser u =  usersService.findByUsername( SecurityContextHolder.getContext().getAuthentication().getName() );
			
			
			if ( !checkUserIDorIsAdmin( vxf.getOwner().getId() )  &&! u.getRoles().contains(UserRoleType.ROLE_TESTBED_PROVIDER) ){
				return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN);
			}
			

			return ResponseEntity.ok( vxf );
		} else {
			return (ResponseEntity<?>) ResponseEntity.badRequest().body( "vxf with id=" + vxfid + " not found in portal registry");
			
		}
	}

	@GetMapping( value = "/vxfs/uuid/{uuid}", produces = "application/json" )
	public ResponseEntity<?> getVxFMetadataByUUID(@PathVariable("uuid") String uuid,
			HttpServletRequest request) {

		logger.info("Received GET for vxf uuid: " + uuid);
		VxFMetadata vxf = null;

		String endpointUrl = request.getRequestURI();
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
			vxf = (VxFMetadata) vxfService.getVxFtByUUID(uuid);
		}

		if (vxf != null) {
			
			if ( ! vxf.isPublished() ){
				return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN);
			}

			return ResponseEntity.ok( vxf );	
		} else {
			return (ResponseEntity<?>) ResponseEntity.badRequest().body("Installed vxf with uuid=" + uuid + " not found in local registry" );		
			
		}

	}



	// Experiments related API

	@GetMapping( value = "/admin/experiments", produces = "application/json" )
	public ResponseEntity<?> getApps(@RequestParam(name= "categoryid", required = false) Long categoryid) {

		PortalUser u =  usersService.findByUsername( SecurityContextHolder.getContext().getAuthentication().getName() );

		if (u != null) {
			List<ExperimentMetadata> apps;

			if (u.getRoles().contains(UserRoleType.ROLE_ADMIN)) {
				apps = nsdService.getdNSDsByCategory(categoryid);
			} else {
				apps = nsdService.gedNSDsByUserID((long) u.getId());
			}

			return ResponseEntity.ok( apps  );	

		} else {
	
			return (ResponseEntity<?>) ResponseEntity.notFound();
		}

	}

	@GetMapping( value = "/experiments", produces = "application/json" )
	public ResponseEntity<?>  getAllApps(@RequestParam( name="categoryid", required = false) Long categoryid) {
		logger.info("getexperiments categoryid=" + categoryid);
		List<ExperimentMetadata> nsds = nsdService.getPublishedNSDsByCategory(categoryid); 
		return ResponseEntity.ok( nsds );		
	}
	
	
	/**
	 * @return all User's Valid experiments as well as all Public and Valid experiments 
	 */
	@GetMapping( value = "/admin/experiments/deployable", produces = "application/json" )
	public ResponseEntity<?>  getAllDeployableExperiments() {
		
		//
		PortalUser u =  usersService.findByUsername( SecurityContextHolder.getContext().getAuthentication().getName() );

		if (u != null) {
			List<ExperimentMetadata> userexpr;

			if (u.getRoles().contains(UserRoleType.ROLE_ADMIN)) {
				userexpr = nsdService.getdNSDsByCategory( (long) -1 );
			} else {
				userexpr = nsdService.gedNSDsByUserID((long) u.getId());
			}

			
			List<ExperimentMetadata> deplExps = new ArrayList<ExperimentMetadata>( userexpr );
			List<ExperimentMetadata> pubExps = new ArrayList<ExperimentMetadata>( nsdService.getPublishedNSDsByCategory( (long) -1 ) );
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
			
			return ResponseEntity.ok( returnedExps );		

		} else {

			return (ResponseEntity<?>) ResponseEntity.notFound();
		}
	}

	@GetMapping( value = "/experiments/{appid}", produces = "application/json" )
	public ResponseEntity<?>  getExperimentMetadataByID(@PathVariable("appid") int appid) {
		logger.info("getAppMetadataByID  appid=" + appid);
		ExperimentMetadata app = nsdService.getProductByID(appid);
		

		if (app != null) {
			if ( !app.isPublished() ){
				return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
			}
			
			return ResponseEntity.ok( app );		
		} else {

			return (ResponseEntity<?>) ResponseEntity.notFound();
		}
	}

	@GetMapping( value = "/admin/experiments/{appid}", produces = "application/json" )
	public ResponseEntity<?>  getAdminExperimentMetadataByID(@PathVariable("appid") int appid) {
		
		logger.info("getAppMetadataByID  appid=" + appid);
		ExperimentMetadata app = nsdService.getProductByID(appid);
		

		if (app != null) {

			if ( !checkUserIDorIsAdmin( app.getOwner().getId() ) ){
				return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
			}
			
			return ResponseEntity.ok( app );	
		} else {

			return (ResponseEntity<?>) ResponseEntity.notFound();
		}
	}


	@GetMapping( value = "/experiments/uuid/{uuid}", produces = "application/json" )
	public ResponseEntity<?>  getAppMetadataByUUID(@PathVariable("uuid") String uuid) {
		logger.info("Received GET for app uuid: " + uuid);
		
		ExperimentMetadata app = nsdService.getdNSDByUUID(uuid);

		if (app != null) {
			if ( !app.isPublished() ){
				return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
			}
			return ResponseEntity.ok( app );
		} else {

			return (ResponseEntity<?>) ResponseEntity.notFound();
		}

	}

	
	@PostMapping( value =  "/admin/experiments/", produces = "application/json", consumes = "multipart/form-data" )
	public ResponseEntity<?>  addExperimentMetadata(
			final @ModelAttribute("exprm") String exp,
			@RequestParam(name = "prodIcon", required = false) MultipartFile  prodIcon,
			@RequestParam(name = "prodFile", required = false) MultipartFile  prodFile,
			@RequestParam(name = "screenshots", required = false) MultipartFile[] screenshots,
			HttpServletRequest request) {

		
		ExperimentMetadata experiment = null;
		
		try {
			experiment = objectMapper.readValue( exp, ExperimentMetadata.class);	
		} catch (JsonParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}  
		
		
		PortalUser u =  usersService.findByUsername( SecurityContextHolder.getContext().getAuthentication().getName() );

		if (u == null) {

			return (ResponseEntity<?>) ResponseEntity.notFound();
		}			


		String emsg = "";
		

		ExperimentMetadata experimentSaved = null;
		
		try {
			
			logger.info("Received @POST for experiment : " + experiment.getName());

			experimentSaved = (ExperimentMetadata) addNewProductData(experiment, prodIcon, prodFile, screenshots, request );				

		} catch (IOException e) {
			experimentSaved = null;
			e.printStackTrace();
			logger.error( e.getMessage() );
			emsg =  e.getMessage();
		}

		if (experimentSaved != null) {

			BusController.getInstance().newNSDAdded( experimentSaved.getId() );		
			BusController.getInstance().validateNSD( experimentSaved.getId() );

			//======================================================
			// AUTOMATIC ONBOARDING PROCESS -START
			// Need to select MANO Provider, convert vxfMetadata to VxFOnBoardedDescriptor and pass it as an input.
			
			// Get the MANO providers which are set for automatic onboarding
			List<MANOprovider> MANOprovidersEnabledForOnboarding=manoProviderService.getMANOprovidersEnabledForOnboarding();
		
			if(MANOprovidersEnabledForOnboarding.size()>0 && experimentSaved.getPackagingFormat() == PackagingFormat.OSMvFIVE)
			{
				for(MANOprovider mp : MANOprovidersEnabledForOnboarding)
				{
					//Create NSDOnboardDescriptor
					ExperimentOnBoardDescriptor obd = new ExperimentOnBoardDescriptor( );
					// Get the first one for now			
					obd.setObMANOprovider(mp);
					obd.setUuid( UUID.randomUUID().toString() ); 
					ExperimentMetadata refNSD =  ( ExperimentMetadata )nsdService.getProductByID( experimentSaved.getId() );
					// Fill the NSDMetadata of NSDOnBoardedDescriptor
					obd.setExperiment( refNSD );
					// Update the NSDMetadata Object with the obd Object
					refNSD.getExperimentOnBoardDescriptors().add( obd ) ;				
					
					// ???????
					obd.setExperiment( refNSD );
					
					// save product
					refNSD = (ExperimentMetadata) nsdService.updateProductInfo( refNSD );
					// save VxFonBoardedDescriptor or not ???
					obd = nsdOBDService.updateExperimentOnBoardDescriptor(obd);
					
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
					MANOController.setHTTPSCHEME( request.getRequestURL().toString()  );
					BusController.getInstance().onBoardNSD( obd.getId() );
				}
			}

			// AUTOMATIC ONBOARDING PROCESS -END
			//======================================================
			
			ExperimentMetadata experimentr = (ExperimentMetadata) nsdService.getProductByID( experimentSaved.getId()) ; //rereading this, seems to keep the DB connection
			
			return ResponseEntity.ok( experimentr );
		} else {
	

			return (ResponseEntity<?>) ResponseEntity.badRequest();
		}

	}

	
	@PutMapping( value =  "/admin/experiments/{aid}", produces = "application/json", consumes = "multipart/form-data" )
	public ResponseEntity<?>  updateExperimentMetadata(@PathVariable("aid") int aid, 
			final @ModelAttribute("exprm") String ex,
			@RequestParam(name = "prodIcon", required = false) MultipartFile  prodIcon,
			@RequestParam(name = "prodFile", required = false) MultipartFile  prodFile,
			@RequestParam(name = "screenshots", required = false) MultipartFile[] screenshots,
			HttpServletRequest request) {

		
		ExperimentMetadata expmeta = null;
		try {
			expmeta = objectMapper.readValue( ex, ExperimentMetadata.class);	
		} catch (JsonParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}  
		
		String emsg= "";
		ExperimentMetadata expmetasaved = null;
		try {
		
			if ( !checkUserIDorIsAdmin( expmeta.getOwner().getId() ) ){
				return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
			}
			
			logger.info("Received @POST for experiment : " + expmeta.getName());
			// logger.info("Received @POST for app.containers : " +
			// appmeta.getContainers().size());

			expmetasaved = (ExperimentMetadata) updateProductMetadata(expmeta, prodIcon, prodFile, screenshots, request );

	
		} catch (IOException e) {
			expmetasaved = null;
			e.printStackTrace();
			logger.error( e.getMessage() );
			emsg =  e.getMessage();
		}
		
		
		if ( expmetasaved != null) { 

			BusController.getInstance().updateNSD(expmetasaved.getId());	
			

			if ( prodFile!= null ) { //if the descriptor changed then we must re-trigger validation
				String a = prodFile.getName();
				if ( !a.equals("unknown") ){
					BusController.getInstance().validationUpdateNSD(expmetasaved.getId());
				}
			}
			
			return ResponseEntity.ok( expmetasaved );
		} else {

			return (ResponseEntity<?>) ResponseEntity.badRequest();
		}
		
		
	}

	@DeleteMapping( value =  "/admin/experiments/{appid}", produces = "application/json", consumes = "application/json" )
	public  ResponseEntity<?> deleteExperiment(@PathVariable("appid") int appid) {
		
		ExperimentMetadata nsd = (ExperimentMetadata) nsdService.getProductByID( appid );

		if ( !checkUserIDorIsAdmin( nsd.getOwner().getId() ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN);
		}
		// Get the OnBoarded Descriptors to OffBoard them
		List<ExperimentOnBoardDescriptor> expobds = nsd.getExperimentOnBoardDescriptors();
		
		if ( nsd.isValid()   ) 
		{
				
			return (ResponseEntity<?>) ResponseEntity.badRequest().body( "ExperimentMetadata with id=" + appid + " is Validated and will not be deleted");
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
				ExperimentOnBoardDescriptor u = nsdOBDService.updateExperimentOnBoardDescriptor(expobd_tmp);

				ResponseEntity<String> response = null;
				try {
					response = aMANOController.offBoardNSDFromMANOProvider( expobd_tmp );
				}
				catch( HttpClientErrorException e)
				{
					expobd_tmp.setOnBoardingStatus(previous_status);
					CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+expobd_tmp.getExperiment().getName()+" to "+expobd_tmp.getOnBoardingStatus());																											
					expobd_tmp.setFeedbackMessage(e.getResponseBodyAsString());					
					u = nsdOBDService.updateExperimentOnBoardDescriptor(expobd_tmp);
					JSONObject result = new JSONObject(e.getResponseBodyAsString()); //Convert String to JSON Object
					ResponseEntity<?> builder = (ResponseEntity<?>) ResponseEntity.status(e.getRawStatusCode()).body("OffBoarding Failed! "+e.getStatusText()+", "+result.getString("detail"));			
					
				}        
				
				if (response == null) {
					expobd_tmp.setOnBoardingStatus(previous_status);
					CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+expobd_tmp.getExperiment().getName()+" to "+expobd_tmp.getOnBoardingStatus());																											
					expobd_tmp.setFeedbackMessage("Null response on OffBoarding request.Requested VxFOnBoardedDescriptor with ID=\" + expobd_tmp.getId() + \" cannot be offboarded.");
					u = nsdOBDService.updateExperimentOnBoardDescriptor(expobd_tmp);
					
					ResponseEntity<?> builder = (ResponseEntity<?>) ResponseEntity.status( HttpStatus.INTERNAL_SERVER_ERROR ).body( "Requested ExperimentOnBoardedDescriptor with ID=" + expobd_tmp.getId() + " cannot be offboarded" );
				}
				// UnCertify Upon OffBoarding
				expobd_tmp.getExperiment().setValid(false);
				expobd_tmp.setFeedbackMessage(response.getBody().toString());
				expobd_tmp.setOnBoardingStatus(OnBoardingStatus.OFFBOARDED);
				CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+expobd_tmp.getExperiment().getName()+" to "+expobd_tmp.getOnBoardingStatus());																															
				u = nsdOBDService.updateExperimentOnBoardDescriptor(expobd_tmp);
				BusController.getInstance().offBoardNSD(u.getId());
				
			}
		}
		BusController.getInstance().deletedExperiment(nsd.getId());		
		nsdService.deleteProduct(nsd);		
		return ResponseEntity.ok().body("{}");									
	}
	

	@GetMapping( value = "/admin/properties", produces = "application/json" )
	public ResponseEntity<?>  getProperties() {
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		
		List<PortalProperty> props = propsService.getProperties();
		for (PortalProperty portalProperty : props) {
			if (portalProperty.getName().equals("mailpassword")) {
				portalProperty.setValue("***");
			}
		}
		return ResponseEntity.ok( props  );	
	}


	@PutMapping( value =  "/admin/properties/{propid}", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?>  updateProperty(@PathVariable("propid") long propid, @Valid @RequestBody PortalProperty p) {
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		
		PortalProperty previousProperty = propsService.getPropertyByID(propid);

		PortalProperty u = propsService.updateProperty(p);
		if (u != null) {
			return ResponseEntity.ok( u  );	
		} else {

			return (ResponseEntity<?>) ResponseEntity.badRequest();
		}

	}

	@GetMapping( value = "/admin/properties/{propid}", produces = "application/json" )
	public ResponseEntity<?>  getPropertyById(@PathVariable("propid") long propid) {
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		PortalProperty sm = propsService.getPropertyByID(propid);

		if (sm.getName().equals("mailpassword")) {
			sm.setValue("");
		}
		if (sm != null) {
			return ResponseEntity.ok( sm );	
		} else {

			return (ResponseEntity<?>) ResponseEntity.notFound();
		}
	}
	

	
	@GetMapping( value = "/admin/deployments", produces = "application/json" )
	public ResponseEntity<?>  getAllDeployments( @RequestParam( name = "status", required = false) String status ) {

		PortalUser u =  usersService.findByUsername( SecurityContextHolder.getContext().getAuthentication().getName() );
		if (u != null) {
			logger.info("getAllDeployments for userid: " + u.getId());
			List<DeploymentDescriptor> deployments;
			if ( (u.getRoles().contains(UserRoleType.ROLE_ADMIN)) ) {
				if ( (status!=null) && status.equals( "COMPLETED" )){
					deployments = deploymentDescriptorService.getAllCompletedDeploymentDescriptors();
				} else if (  (status!=null) &&  status.equals( "REJECTED" )){
					deployments = deploymentDescriptorService.getAllRejectedDeploymentDescriptors();
				} else if (  (status!=null) &&  status.equals( "FAILED" )){
					deployments = deploymentDescriptorService.getAllFailedDeploymentDescriptors();
				} else if (  (status!=null) &&  status.equals( "FAILED_OSM_REMOVED" )){
					deployments = deploymentDescriptorService.getAllRemovedDeploymentDescriptors();					
				} else {
					deployments = deploymentDescriptorService.getAllDeploymentDescriptors();			
				}			
			} else if ( (u.getRoles().contains(UserRoleType.ROLE_MENTOR))) {							
				if ( (status!=null) && status.equals( "COMPLETED" )){
					deployments = deploymentDescriptorService.getAllDeploymentDescriptorsByMentor(  (long) u.getId(), "COMPLETED" );
				} else if (  (status!=null) &&  status.equals( "REJECTED" )){
					deployments = deploymentDescriptorService.getAllDeploymentDescriptorsByMentor(  (long) u.getId(), "REJECTED" );
				} else if (  (status!=null) &&  status.equals( "FAILED" )){
					deployments = deploymentDescriptorService.getAllDeploymentDescriptorsByMentor( (long) u.getId(), "FAILED" );
				} else if (  (status!=null) &&  status.equals( "FAILED_OSM_REMOVED" )){
					deployments = deploymentDescriptorService.getAllDeploymentDescriptorsByMentor( (long) u.getId(), "FAILED_OSM_REMOVED" );
				} else {
					deployments = deploymentDescriptorService.getAllDeploymentDescriptorsByMentor(  (long) u.getId(), null );
				}			
			} else {

				if ( (status!=null) && status.equals( "COMPLETED" )){
					deployments = deploymentDescriptorService.getAllDeploymentDescriptorsByUser( (long) u.getId(),  "COMPLETED"  );
				} else if (  (status!=null) &&  status.equals( "REJECTED" )){
					deployments = deploymentDescriptorService.getAllDeploymentDescriptorsByUser( (long) u.getId(), "REJECTED" );
				} else if (  (status!=null) &&  status.equals( "FAILED" )){
					deployments = deploymentDescriptorService.getAllDeploymentDescriptorsByUser( (long) u.getId(), "FAILED" );
				} else if (  (status!=null) &&  status.equals( "FAILED_OSM_REMOVED" )){
					deployments = deploymentDescriptorService.getAllDeploymentDescriptorsByUser( (long) u.getId(), "FAILED_OSM_REMOVED" );
				} else {
					deployments = deploymentDescriptorService.getAllDeploymentDescriptorsByUser( (long) u.getId(), null );
				}			
			}
			
			return ResponseEntity.ok( deployments  );	
		} else {

			return (ResponseEntity<?>) ResponseEntity.notFound();
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
	
	@GetMapping( value = "/admin/deployments/user", produces = "application/json" )
	public ResponseEntity<?>  getAllDeploymentsofUser( @RequestParam( name = "status", required = false) String status ) {

		PortalUser u =  usersService.findByUsername( SecurityContextHolder.getContext().getAuthentication().getName() );

		if (u != null) {
			logger.info("getAllDeploymentsofUser for userid: " + u.getId());
			List<DeploymentDescriptor> deployments = new ArrayList<DeploymentDescriptor>();
			
			if ( (status!=null) && status.equals( "COMPLETED" )){
				deployments = deploymentDescriptorService.getAllDeploymentDescriptorsByUser( (long) u.getId(),  "COMPLETED"  );
			} else if (  (status!=null) &&  status.equals( "REJECTED" )){
				deployments = deploymentDescriptorService.getAllDeploymentDescriptorsByUser( (long) u.getId(), "REJECTED" );
			} else if (  (status!=null) &&  status.equals( "FAILED" )){
				deployments = deploymentDescriptorService.getAllDeploymentDescriptorsByUser( (long) u.getId(), "FAILED" );
			} else if (  (status!=null) &&  status.equals( "FAILED_OSM_REMOVED" )){
				deployments = deploymentDescriptorService.getAllDeploymentDescriptorsByUser( (long) u.getId(), "FAILED_OSM_REMOVED" );
			} else {
				deployments = deploymentDescriptorService.getAllDeploymentDescriptorsByUser( (long) u.getId(), null );
			}

			return ResponseEntity.ok( deployments  );	
		} else {

			return (ResponseEntity<?>) ResponseEntity.notFound();
		}

	}
	
	
	@GetMapping( value = "/admin/deployments/scheduled", produces = "application/json" )
	public ResponseEntity<?>  getAllScheduledDeploymentsofUser() {

		PortalUser u =  usersService.findByUsername( SecurityContextHolder.getContext().getAuthentication().getName() );

		if (u != null) {
			logger.info("getAllDeploymentsofUser for userid: " + u.getId());
			List<DeploymentDescriptor> deployments;

			if ( (u.getRoles().contains(UserRoleType.ROLE_ADMIN)) ||  (u.getRoles().contains(UserRoleType.ROLE_TESTBED_PROVIDER )) ) {
				deployments = deploymentDescriptorService.getAllDeploymentDescriptorsScheduled();
			} else {
				deployments = deploymentDescriptorService.getAllDeploymentDescriptorsByUser( (long) u.getId(), null ); 
			}


			return ResponseEntity.ok( deployments  );	
		} else {
			return (ResponseEntity<?>) ResponseEntity.notFound();
			
		}

	}

	@PostMapping( value =  "/admin/deployments", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?>  addDeployment( @Valid @RequestBody DeploymentDescriptor deployment) {

		PortalUser u =  usersService.findByUsername( SecurityContextHolder.getContext().getAuthentication().getName() );

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

			u = usersService.findById(u.getId());
			deployment.setOwner(u); // reattach from the DB model
			u.getDeployments().add(deployment);
			
			// Get the Experiment Metadata from the id of the experiment from the deployment request
			ExperimentMetadata baseApplication = (ExperimentMetadata) nsdService
					.getProductByID(deployment.getExperiment().getId());
			deployment.setExperiment(baseApplication); // reattach from the DB model

			
			deployment = deploymentDescriptorService.updateDeploymentDescriptor(deployment);
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

			return ResponseEntity.ok( deployment  );	
		} else {
			return (ResponseEntity<?>) ResponseEntity.notFound();
		}
	}

	@DeleteMapping( value =  "/admin/deployments/{id}", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?>  deleteDeployment(@PathVariable("id") int id) {
		PortalUser u =  usersService.findByUsername( SecurityContextHolder.getContext().getAuthentication().getName() );

		DeploymentDescriptor dep = deploymentDescriptorService.getDeploymentByID(id);
		if (u != null) {
			if (u.getRoles().contains(UserRoleType.ROLE_ADMIN) || u.getId() == dep.getOwner().getId()) {
				deploymentDescriptorService.deleteDeployment( dep );
				return ResponseEntity.ok( "{}"  );
			}
		}

		return (ResponseEntity<?>) ResponseEntity.notFound();
	}

	@GetMapping( value = "/admin/deployments/{id}", produces = "application/json" )
	public ResponseEntity<?>  getDeploymentById(@PathVariable("id") int deploymentId) {

		PortalUser u =  usersService.findByUsername( SecurityContextHolder.getContext().getAuthentication().getName() );

		if (u != null) {
			logger.info("getDeploymentById for id: " + deploymentId);
			DeploymentDescriptor deployment = deploymentDescriptorService.getDeploymentByID(deploymentId);

			if ((u.getRoles().contains(UserRoleType.ROLE_ADMIN)) || (deployment.getMentor().getId() == u.getId()) || (deployment.getOwner().getId() == u.getId())) {
				
				return ResponseEntity.ok( deployment  );
			}
		}

		return (ResponseEntity<?>) ResponseEntity.notFound();

	}


	@PutMapping( value =  "/admin/deployments/{id}", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?>  updateDeployment(@PathVariable("id") int id, @Valid @RequestBody DeploymentDescriptor receivedDeployment) {

		PortalUser u =  usersService.findByUsername( SecurityContextHolder.getContext().getAuthentication().getName() );

		if ((u != null)) {

			if ((u.getRoles().contains(UserRoleType.ROLE_ADMIN)) || u.getApikey().equals(receivedDeployment.getMentor().getApikey())) // only admin or Deployment Mentor can alter a deployment
			{
				DeploymentDescriptor aDeployment = deploymentDescriptorService.getDeploymentByID( receivedDeployment.getId() );
												
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
	
					DeploymentDescriptor dd = deploymentDescriptorService.getDeploymentByID( receivedDeployment.getId() );  //rereading this, seems to keep the DB connection
	

					if( receivedDeployment.getStatus() == DeploymentDescriptorStatus.SCHEDULED && aDeployment.getInstanceId() == null)
					{
						for (ExperimentOnBoardDescriptor tmpExperimentOnBoardDescriptor : dd.getExperimentFullDetails().getExperimentOnBoardDescriptors())
						{
							if(tmpExperimentOnBoardDescriptor.getObMANOprovider().getSupportedMANOplatform().getName().equals("OSM FOUR"))
							{							
								aDeployment.setStatus( receivedDeployment.getStatus() );
								CentralLogger.log( CLevel.INFO, "Status change of deployment "+aDeployment.getName()+" to "+aDeployment.getStatus());
								logger.info( "Status change of deployment "+aDeployment.getName()+" to "+aDeployment.getStatus());							
								aDeployment = deploymentDescriptorService.updateDeploymentDescriptor(aDeployment);
								logger.info("NS status change is now "+aDeployment.getStatus());															
								BusController.getInstance().scheduleExperiment( aDeployment.getId() );								
							}
							if(tmpExperimentOnBoardDescriptor.getObMANOprovider().getSupportedMANOplatform().getName().equals("OSM FIVE"))
							{							
								aDeployment.setStatus( receivedDeployment.getStatus() );
								CentralLogger.log( CLevel.INFO, "Status change of deployment "+aDeployment.getName()+" to "+aDeployment.getStatus());
								logger.info( "Status change of deployment "+aDeployment.getName()+" to "+aDeployment.getStatus());							
								aDeployment = deploymentDescriptorService.updateDeploymentDescriptor(aDeployment);
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
								aDeployment = deploymentDescriptorService.updateDeploymentDescriptor(aDeployment);
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
								aDeployment = deploymentDescriptorService.updateDeploymentDescriptor(aDeployment);
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
						aDeployment = deploymentDescriptorService.updateDeploymentDescriptor(aDeployment);
						logger.info("NS status change is now "+aDeployment.getStatus());															
						BusController.getInstance().completeExperiment( aDeployment.getId() );						
					}
					else if( receivedDeployment.getStatus() == DeploymentDescriptorStatus.REJECTED && aDeployment.getInstanceId() == null)
					{
						aDeployment.setStatus( receivedDeployment.getStatus() );
						CentralLogger.log( CLevel.INFO, "Status change of deployment "+aDeployment.getName()+" to "+aDeployment.getStatus());
						logger.info( "Status change of deployment "+aDeployment.getName()+" to "+aDeployment.getStatus());							
						aDeployment = deploymentDescriptorService.updateDeploymentDescriptor(aDeployment);
						logger.info("NS status change is now "+aDeployment.getStatus());															
						BusController.getInstance().rejectExperiment( aDeployment.getId() );
						logger.info("Deployment Rejected");				
					}
					else
					{
						return (ResponseEntity<?>) ResponseEntity.badRequest().body("Inconsistent status change" );					
					}
				} else {

					logger.info( "Previous status is the same so just update deployment info");					
					aDeployment = deploymentDescriptorService.updateDeploymentDescriptor(aDeployment);
				}
				return ResponseEntity.ok( aDeployment  );
			}

		}


		return (ResponseEntity<?>) ResponseEntity.badRequest();

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

	@GetMapping( value = "/manoplatforms", produces = "application/json" )
	public ResponseEntity<?>  getMANOplatforms() {
		return ResponseEntity.ok( manoPlatformService.getMANOplatforms()  );
	}

	@GetMapping( value = "/admin/manoplatforms", produces = "application/json" )
	public ResponseEntity<?>  getAdminMANOplatforms() {

		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		return ResponseEntity.ok( manoPlatformService.getMANOplatforms()  );
	}


	@PostMapping( value =  "/admin/manoplatforms", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?>  addMANOplatform( @Valid @RequestBody MANOplatform c) {

		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		
		MANOplatform u = manoPlatformService.addMANOplatform(c);

		if (u != null) {
			return ResponseEntity.ok( u  );
		} else {

			return (ResponseEntity<?>) ResponseEntity.badRequest();
		}
	}

	@PutMapping( value =  "/admin/manoplatforms/{mpid}", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?>  updateMANOplatform(@PathVariable("mpid") int mpid, @Valid @RequestBody MANOplatform c) {
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		MANOplatform previousMP = manoPlatformService.getMANOplatformByID(mpid);
		
		previousMP.setDescription( c.getDescription() );
		previousMP.setName( c.getName() );
		previousMP.setVersion( c.getVersion() );

		MANOplatform u = manoPlatformService.updateMANOplatformInfo( previousMP );

		if (u != null) {
			return ResponseEntity.ok( u  );
		} else {

			return (ResponseEntity<?>) ResponseEntity.badRequest();
		}

	}


	@DeleteMapping( value =  "/admin/manoplatforms/{mpid}", produces = "application/json")
	public ResponseEntity<?>  deleteMANOplatform(@PathVariable("mpid") int mpid) {
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		MANOplatform m = manoPlatformService.getMANOplatformByID(mpid);

		manoPlatformService.deleteMANOplatform( m );
		return ResponseEntity.ok( "{}"  );

	}

	@GetMapping( value = "/manoplatforms/{mpid}", produces = "application/json" )
	public ResponseEntity<?>  getMANOplatformById(@PathVariable("mpid") int mpid) {
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		MANOplatform sm = manoPlatformService.getMANOplatformByID(mpid);

		if (sm != null) {
			return ResponseEntity.ok(sm);
		} else {

			return (ResponseEntity<?>) ResponseEntity.notFound();
		}
	}

	@GetMapping( value = "/admin/manoplatforms/{mpid}", produces = "application/json" )
	public ResponseEntity<?>  getAdminMANOplatformById(@PathVariable("mpid") int mpid) {
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
	@GetMapping( value = "/admin/manoproviders", produces = "application/json" )
	public ResponseEntity<?>  getAdminMANOproviders() {
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN);
		}

		return ResponseEntity.ok( manoProviderService.getMANOproviders()  );
	}

	@PostMapping( value =  "/admin/manoproviders", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?>  addMANOprovider( @Valid @RequestBody MANOprovider c) {
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		
		
		c.setSupportedMANOplatform( manoPlatformService.getMANOplatformByID( c.getSupportedMANOplatform().getId() ) ); //to properly attach from the model
		MANOprovider u = manoProviderService.addMANOprovider(c);

		if (u != null) {
			return ResponseEntity.ok( u  );
		} else {

			return (ResponseEntity<?>) ResponseEntity.badRequest();
		}
	}


	@PutMapping( value =  "/admin/manoproviders/{mpid}", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?>  updateMANOprovider(@PathVariable("mpid") int mpid, @Valid @RequestBody MANOprovider c) {

		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		
		MANOprovider prev = manoProviderService.getMANOproviderByID(c.getId());
		prev.setApiEndpoint( c.getApiEndpoint());
		prev.setAuthorizationBasicHeader( c.getAuthorizationBasicHeader());
		prev.setDescription( c.getDescription());
		prev.setName(c.getName());
		prev.setSupportedMANOplatform( c.getSupportedMANOplatform() );
		
		MANOprovider u = manoProviderService.updateMANOproviderInfo(c);

		if (u != null) {
			return ResponseEntity.ok( u  );
		} else {

			return (ResponseEntity<?>) ResponseEntity.badRequest();
		}

	}


	@DeleteMapping( value =  "/admin/manoproviders/{mpid}", produces = "application/json" )
	public ResponseEntity<?>  deleteMANOprovider(@PathVariable("mpid") int mpid) {

		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}

		MANOprovider prev = manoProviderService.getMANOproviderByID( mpid );
		
		manoProviderService.deleteMANOprovider( prev );
		return ResponseEntity.ok( "{}" );

	}

	@GetMapping( value = "/admin/manoproviders/{mpid}", produces = "application/json" )
	public ResponseEntity<?>  getAdminMANOproviderById(@PathVariable("mpid") int mpid) {
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		MANOprovider sm = manoProviderService.getMANOproviderByID(mpid);

		if (sm != null) {
			return ResponseEntity.ok( sm );
		} else {

			return (ResponseEntity<?>) ResponseEntity.notFound();
		}
	}

	@GetMapping( value = "/manoprovider/{mpid}/vnfds/{vxfid}", produces = "application/json" )
	public ResponseEntity<?>  getOSMVNFMetadataByKOSMMANOID(@PathVariable("mpid") int manoprovid, @PathVariable("vxfid") String vxfid) {
		logger.info("getOSMVNFMetadataByID  vxfid=" + vxfid);
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN);
		}

		MANOprovider sm = manoProviderService.getMANOproviderByID(manoprovid);
		
		if(sm.getSupportedMANOplatform().getName().equals("OSM FIVE"))
		{
			OSM5Client osm5Client = new OSM5Client(sm.getApiEndpoint(),sm.getUsername(),sm.getPassword(),"admin");
			osm5.ns.riftware._1._0.project.vnfd.rev170228.project.vnfd.catalog.Vnfd vnfd = osm5Client.getVNFDbyID(vxfid);
			if (vnfd != null) {
				return ResponseEntity.ok( vnfd  );
			} else {
				return (ResponseEntity<?>) ResponseEntity.notFound();
			}
		}
		return (ResponseEntity<?>) ResponseEntity.notFound();
	}

	@GetMapping( value = "/admin/manoprovider/{mpid}/vnfds", produces = "application/json" )
	public ResponseEntity<?>  getOSMVNFMetadata(@PathVariable("mpid") int manoprovid) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		MANOprovider sm = manoProviderService.getMANOproviderByID(manoprovid);


		
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
				
				return (ResponseEntity<?>) ResponseEntity.status( e.getRawStatusCode() ).body("manoprovid with id=" + manoprovid + " does not belong to the supported types or failed to communication with OSM") ;
			}						
			
			osm5.ns.riftware._1._0.project.vnfd.rev170228.project.vnfd.catalog.Vnfd[] vnfd = osm5Client.getVNFDs();
			if (vnfd != null) {
				return ResponseEntity.ok( vnfd  );
			} else {
				return (ResponseEntity<?>) ResponseEntity.notFound();
			}
		}
		return (ResponseEntity<?>) ResponseEntity.notFound();
	}

	
	@GetMapping( value = "/admin/manoprovider/{mpid}/nsds/{nsdid}", produces = "application/json" )
	public ResponseEntity<?>  getOSM_NSD_MetadataByKOSMMANOID(@PathVariable("mpid") int manoprovid,
			@PathVariable("vxfid") String nsdid) {
		logger.info("getOSMVNFMetadataByID  nsdid=" + nsdid);

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		MANOprovider sm = manoProviderService.getMANOproviderByID(manoprovid);

		
		if(sm.getSupportedMANOplatform().getName().equals("OSM FIVE"))
		{
			OSM5Client osm5Client = new OSM5Client(sm.getApiEndpoint(),sm.getUsername(),sm.getPassword(),"admin");
			Nsd nsd = osm5Client.getNSDbyID(nsdid);
			if (nsd != null) {
				return ResponseEntity.ok( nsd  );
			} else {
				return (ResponseEntity<?>) ResponseEntity.notFound();
			}
		}
		return (ResponseEntity<?>) ResponseEntity.notFound();
	}


	@GetMapping( value = "/admin/manoprovider/{mpid}/nsds", produces = "application/json" )
	public ResponseEntity<?>  getOSM_NSD_Metadata(@PathVariable("mpid") int manoprovid) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN);
		}
		MANOprovider sm = manoProviderService.getMANOproviderByID(manoprovid);

		
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
				
				return (ResponseEntity<?>) ResponseEntity.status( e.getRawStatusCode() ).body("manoprovid with id=" + manoprovid + " does not belong to the supported types or failed to communication with OSM") ;
			}						
			
			osm5.ns.riftware._1._0.project.nsd.rev170228.project.nsd.catalog.Nsd[] nsd = osm5Client.getNSDs();
			if (nsd != null) {
				return ResponseEntity.ok( nsd  );
			} else {
				return (ResponseEntity<?>) ResponseEntity.notFound();
			}
		}
		return (ResponseEntity<?>) ResponseEntity.notFound();
	}

	/********************************************************************************
	 * 
	 * admin VxFOnBoardedDescriptors
	 * 
	 ********************************************************************************/


	@GetMapping( value = "/admin/vxfobds", produces = "application/json" )
	public ResponseEntity<?>  getVxFOnBoardedDescriptors() {
		return ResponseEntity.ok( vxfOBDService.getVxFOnBoardedDescriptors()  );
	}

	@PostMapping( value =  "/admin/vxfobds/", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?>  addVxFOnBoardedDescriptor(  @Valid @RequestBody VxFMetadata aVxF ) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		
		if ( aVxF != null ) {
		
			VxFMetadata refVxF =  ( VxFMetadata )vxfService.getProductByID( aVxF.getId() );
			VxFOnBoardedDescriptor obd = new VxFOnBoardedDescriptor();
			obd.setVxf( refVxF );
			obd.setUuid( UUID.randomUUID().toString() );
			//?????
			refVxF.getVxfOnBoardedDescriptors().add( obd ) ;
			
			// save product
			refVxF = (VxFMetadata) vxfService.updateProductInfo( refVxF );
			

			if (refVxF != null) {
				return ResponseEntity.ok( refVxF  );
			} else {
				return (ResponseEntity<?>) ResponseEntity.badRequest();
			}
			
		} else {
			return (ResponseEntity<?>) ResponseEntity.badRequest();
		}
	}


	@PutMapping( value =  "/admin/vxfobds/{mpid}", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?>  updateVxFOnBoardedDescriptor(@PathVariable("mpid") int mpid, @Valid @RequestBody  VxFOnBoardedDescriptor c) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN);
		}
		VxFOnBoardedDescriptor u = vxfOBDService.updateVxFOnBoardedDescriptor(c);

		if (u != null) {
			return ResponseEntity.ok( u  );
		} else {
			return (ResponseEntity<?>) ResponseEntity.badRequest();
		}

	}


	@DeleteMapping( value =  "/admin/vxfobds/{mpid}", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?>  deleteVxFOnBoardedDescriptor(@PathVariable("mpid") int mpid) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN);
		}

		VxFOnBoardedDescriptor sm = vxfOBDService.getVxFOnBoardedDescriptorByID(mpid);
		
		vxfOBDService.deleteVxFOnBoardedDescriptor( sm );
		return ResponseEntity.ok( "{}"  );

	}

	@GetMapping( value = "/admin/vxfobds/{mpid}", produces = "application/json" )
	public ResponseEntity<?>  getVxFOnBoardedDescriptorById(@PathVariable("mpid") int mpid) {
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		VxFOnBoardedDescriptor sm = vxfOBDService.getVxFOnBoardedDescriptorByID(mpid);

		if (sm != null) {
			return ResponseEntity.ok( sm  );
		} else {
			return (ResponseEntity<?>) ResponseEntity.badRequest();
			// throw new WebApplicationException(builder.build());
		}
	}


	@GetMapping( value = "/admin/vxfobds/{mpid}/status", produces = "application/json" )
	public ResponseEntity<?>  getVxFOnBoardedDescriptorByIdCheckMANOProvider(@PathVariable("mpid") int mpid) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		VxFOnBoardedDescriptor obds = vxfOBDService.getVxFOnBoardedDescriptorByID(mpid);

		if (obds == null) {
			return (ResponseEntity<?>) ResponseEntity.notFound(); 
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

		return ResponseEntity.ok( obds  );

	}

	
	@PutMapping( value =  "/admin/vxfobds/{mpid}/onboard", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?>  onBoardDescriptor(@PathVariable("mpid") int mpid, @Valid @RequestBody final VxFOnBoardedDescriptor vxfobd) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		
		try {
			aMANOController.onBoardVxFToMANOProvider( vxfobd.getId() );
		} catch (Exception e) {				

			return (ResponseEntity<?>) ResponseEntity.badRequest();
		}			
		
		return ResponseEntity.ok( vxfobd  );

	}
	

	@PutMapping( value =  "/admin/vxfobds/{mpid}/offboard", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?>  offBoardDescriptor(@PathVariable("mpid") int mpid, @Valid @RequestBody final VxFOnBoardedDescriptor clobd) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		OnBoardingStatus previous_status = clobd.getOnBoardingStatus();
		clobd.setOnBoardingStatus(OnBoardingStatus.OFFBOARDING);
		CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+clobd.getVxf().getName()+" to "+clobd.getOnBoardingStatus());																													
		VxFOnBoardedDescriptor updatedObd = vxfOBDService.updateVxFOnBoardedDescriptor(clobd);

		ResponseEntity<String> response = null;
		try {
			response = aMANOController.offBoardVxFFromMANOProvider( updatedObd );			
		}
		catch( HttpClientErrorException e)
		{
			updatedObd.setOnBoardingStatus(previous_status);
			CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+updatedObd.getVxf().getName()+" to "+updatedObd.getOnBoardingStatus());																														
			updatedObd.setFeedbackMessage(e.getResponseBodyAsString());
			updatedObd = vxfOBDService.updateVxFOnBoardedDescriptor( updatedObd );
			JSONObject result = new JSONObject(e.getResponseBodyAsString()); //Convert String to JSON Object
		

			return (ResponseEntity<?>) ResponseEntity.status( e.getRawStatusCode() ).contentType(MediaType.TEXT_PLAIN).body("OffBoarding Failed! "+e.getStatusText()+", "+result.getString("detail"))   ;
		}        
		
		if (response == null) {
			updatedObd.setOnBoardingStatus(previous_status);
			updatedObd.setFeedbackMessage("Null Response on OffBoarding request.Requested VxFOnBoardedDescriptor with ID=\" + updatedObd.getId() + \" cannot be offboarded.");
			updatedObd = vxfOBDService.updateVxFOnBoardedDescriptor( updatedObd );
			
			
			return (ResponseEntity<?>) ResponseEntity.status( HttpStatus.INTERNAL_SERVER_ERROR ).contentType(MediaType.TEXT_PLAIN).body("Requested VxFOnBoardedDescriptor with ID=" + updatedObd.getId() + " cannot be offboarded")   ;
		}
		// UnCertify Upon OffBoarding
		updatedObd.getVxf().setCertified(false);
		updatedObd.setOnBoardingStatus(OnBoardingStatus.OFFBOARDED);
		CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+updatedObd.getVxf().getName()+" to "+updatedObd.getOnBoardingStatus());																																
		updatedObd.setFeedbackMessage(response.getBody().toString());
		updatedObd = vxfOBDService.updateVxFOnBoardedDescriptor( updatedObd );
		BusController.getInstance().offBoardVxF( updatedObd.getId() );
		
		return ResponseEntity.ok( updatedObd  );
		
	}

	/********************************************************************************
	 * 
	 * admin ExperimentOnBoardDescriptors
	 * 
	 ********************************************************************************/

	@GetMapping( value = "/admin/experimentobds", produces = "application/json" )
	public ResponseEntity<?>  getExperimentOnBoardDescriptors() {
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		
		return ResponseEntity.ok(  nsdOBDService.getExperimentOnBoardDescriptors()  );
	}


	@PostMapping( value =  "/admin/experimentobds/", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?>  addExperimentOnBoardDescriptor(  @Valid @RequestBody ExperimentMetadata exp) {
		
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		
		
		if ( exp != null ) {
			
			ExperimentMetadata refExp =  ( ExperimentMetadata ) nsdService.getProductByID( exp.getId() );
			ExperimentOnBoardDescriptor obd = new ExperimentOnBoardDescriptor();
			obd.setExperiment( refExp );
			obd.setUuid( UUID.randomUUID().toString() );
			refExp.getExperimentOnBoardDescriptors().add( obd ) ;
			
			// save product
			refExp = (ExperimentMetadata) nsdService.updateProductInfo( refExp );
			

			if (refExp != null) {
				return ResponseEntity.ok( refExp  );
			} else {
				return (ResponseEntity<?>) ResponseEntity.badRequest();
			}
			
		} else {
			return (ResponseEntity<?>) ResponseEntity.badRequest();
		}
		
		
	}

	
	@PutMapping( value =  "/admin/experimentobds/{mpid}", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?>  updateExperimentOnBoardDescriptor(@PathVariable("mpid") int mpid, @Valid @RequestBody ExperimentOnBoardDescriptor c) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		ExperimentOnBoardDescriptor u = nsdOBDService.updateExperimentOnBoardDescriptor(c);

		if (u != null) {
			return ResponseEntity.ok( u  );
		} else {
			return (ResponseEntity<?>) ResponseEntity.badRequest();
		}

	}

	
	@DeleteMapping( value =  "/admin/experimentobds/{mpid}", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?>  deleteExperimentOnBoardDescriptor(@PathVariable("mpid") int mpid) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}

		ExperimentOnBoardDescriptor u = nsdOBDService.getExperimentOnBoardDescriptorByID(mpid);
		nsdOBDService.deleteExperimentOnBoardDescriptor( u );
		return ResponseEntity.ok( "{}"  );

	}

	
	@GetMapping( value = "/admin/experimentobds/{mpid}", produces = "application/json" )
	public ResponseEntity<?>  getExperimentOnBoardDescriptorById(@PathVariable("mpid") int mpid) {
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN);
		}
		ExperimentOnBoardDescriptor sm = nsdOBDService.getExperimentOnBoardDescriptorByID(mpid);

		if (sm != null) {
			return ResponseEntity.ok( sm  );
		} else {
			return (ResponseEntity<?>) ResponseEntity.notFound();
		}
	}


	@GetMapping( value = "/admin/experimentobds/{mpid}/status", produces = "application/json" )
	public ResponseEntity<?>  getExperimentOnBoardDescriptorByIdCheckMANOProvider(@PathVariable("mpid") int mpid) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		ExperimentOnBoardDescriptor sm = nsdOBDService.getExperimentOnBoardDescriptorByID(mpid);

		if (sm == null) {
			return (ResponseEntity<?>) ResponseEntity.notFound();
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

		return ResponseEntity.ok( sm  );

	}

	@PutMapping( value =  "/admin/experimentobds/{mpid}/onboard", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?>  onExperimentBoardDescriptor(@PathVariable("mpid") int mpid, @Valid @RequestBody final ExperimentOnBoardDescriptor experimentonboarddescriptor) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		try {
			aMANOController.onBoardNSDToMANOProvider( experimentonboarddescriptor.getId() );
		} catch (Exception e) {				
			e.printStackTrace();
	    	logger.error("onExperimentBoardDescriptor, OSM4 fails authentication. Aborting Onboarding action.");
			CentralLogger.log( CLevel.ERROR, "onExperimentBoardDescriptor, OSM4 fails authentication. Aborting Onboarding action.");																	

			return (ResponseEntity<?>) ResponseEntity.status( HttpStatus.INTERNAL_SERVER_ERROR ).contentType(MediaType.TEXT_PLAIN).body("Requested Experiment Descriptor with ID=" + experimentonboarddescriptor.getId() + " cannot be onboarded")   ;
		}	
		
		return ResponseEntity.ok( experimentonboarddescriptor  );
	}



	@PutMapping( value =  "/admin/experimentobds/{mpid}/offboard", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?>  offBoardExperimentDescriptor(@PathVariable("mpid") int mpid, @Valid @RequestBody final ExperimentOnBoardDescriptor c) {

		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		OnBoardingStatus previous_status = c.getOnBoardingStatus();
		c.setOnBoardingStatus(OnBoardingStatus.OFFBOARDING);
		CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+c.getExperiment().getName()+" to "+c.getOnBoardingStatus());																																
		ExperimentOnBoardDescriptor uExper = nsdOBDService.updateExperimentOnBoardDescriptor(c);

		ResponseEntity<String> response = null;
		try {
			response = aMANOController.offBoardNSDFromMANOProvider(uExper);
		}
		catch( HttpClientErrorException e)
		{
			uExper.setOnBoardingStatus(previous_status);
			CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+uExper.getExperiment().getName()+" to "+uExper.getOnBoardingStatus());																																	
			uExper.setFeedbackMessage(e.getResponseBodyAsString());
			uExper = nsdOBDService.updateExperimentOnBoardDescriptor(uExper);
			JSONObject result = new JSONObject(e.getResponseBodyAsString()); //Convert String to JSON Object
			
			return (ResponseEntity<?>) ResponseEntity.status( e.getRawStatusCode() ).contentType(MediaType.TEXT_PLAIN).body("OffBoarding Failed! "+e.getStatusText()+", "+result.getString("detail"))   ;
		}        
		
		if (response == null) {
			uExper.setOnBoardingStatus(previous_status);
			CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+uExper.getExperiment().getName()+" to "+uExper.getOnBoardingStatus());																																	
			uExper.setFeedbackMessage("Null response on OffBoarding request.Requested NSOnBoardedDescriptor with ID=\" + c.getId() + \" cannot be offboarded.");			
			uExper = nsdOBDService.updateExperimentOnBoardDescriptor( uExper );
			
			return (ResponseEntity<?>) ResponseEntity.status( HttpStatus.INTERNAL_SERVER_ERROR ).contentType(MediaType.TEXT_PLAIN).body( "Requested NSOnBoardedDescriptor with ID=" + c.getId() + " cannot be offboarded" )   ;
		}
		// Set Valid to false if it is OffBoarded
		uExper.getExperiment().setValid(false);
		uExper.setOnBoardingStatus(OnBoardingStatus.OFFBOARDED);
		CentralLogger.log( CLevel.INFO, "Onboarding Status change of VxF "+uExper.getExperiment().getName()+" to "+uExper.getOnBoardingStatus());																																			
		uExper.setFeedbackMessage(response.getBody().toString());
		uExper = nsdOBDService.updateExperimentOnBoardDescriptor( uExper );
		BusController.getInstance().offBoardNSD( uExper.getId() );
		
		return ResponseEntity.ok( uExper  );
	}
	
	/**
	 * 
	 * Infrastructure object API
	 */

	@GetMapping( value = "/admin/infrastructures", produces = "application/json" )
	public ResponseEntity<?>  getAdminInfrastructures() {	
		return ResponseEntity.ok( infrastructureService.getInfrastructures()  );
	}


	@PostMapping( value =  "/admin/infrastructures", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?>  addInfrastructure( @Valid @RequestBody Infrastructure c) {
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		Infrastructure u = infrastructureService.addInfrastructure(c);

		if (u != null) {
			return ResponseEntity.ok( u);
		} else {
			return (ResponseEntity<?>) ResponseEntity.badRequest();
		}
	}

	
	@PutMapping( value = "/admin/infrastructures/{infraid}", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?>  updateInfrastructure(@PathVariable("infraid") int infraid, @Valid @RequestBody Infrastructure c) {
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		Infrastructure infrastructure = infrastructureService.getInfrastructureByID(infraid);
		
		infrastructure.setDatacentername( c.getDatacentername());
		infrastructure.setEmail( c.getEmail());
		infrastructure.setVIMid( c.getVIMid());
		infrastructure.setName( c.getName());
		infrastructure.setOrganization(c.getOrganization());

		Infrastructure u = infrastructureService.updateInfrastructureInfo( infrastructure );

		if (u != null) {
			return ResponseEntity.ok( u);
		} else {
			return (ResponseEntity<?>) ResponseEntity.badRequest();
		}

	}

	
	@DeleteMapping( value =  "/admin/infrastructures/{infraid}", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?>  deleteInfrastructure(@PathVariable("infraid") int infraid) {
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		Infrastructure infrastructure = infrastructureService.getInfrastructureByID(infraid);
		infrastructureService.deleteInfrastructure( infrastructure );
		return ResponseEntity.ok( "{}" );

	}


	
	@GetMapping( value = "/admin/infrastructures/{infraid}", produces = "application/json" )
	public ResponseEntity<?>  getInfrastructureById(@PathVariable("infraid") int infraid) {
		if ( !checkUserIDorIsAdmin( -1 ) ){
			return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN) ;
		}
		Infrastructure sm = infrastructureService.getInfrastructureByID(infraid);

		if (sm != null) {
			return ResponseEntity.ok( sm );
		} else {
			return (ResponseEntity<?>) ResponseEntity.notFound();
		}
	}
	
	

	@PostMapping( value =  "/admin/infrastructures/{infraid}/images/{vfimageid}", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?>  addImageToInfrastructure(@PathVariable("infraid") int infraid, @PathVariable("vfimageid") int vfimageid) {
		

		PortalUser u =  usersService.findByUsername( SecurityContextHolder.getContext().getAuthentication().getName() );

		if ( (!u.getRoles().contains(UserRoleType.ROLE_ADMIN)) &&  (!u.getRoles().contains(UserRoleType.ROLE_TESTBED_PROVIDER )) ) {
			 return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN);
			
		}
		
		
		Infrastructure infrs = infrastructureService.getInfrastructureByID(infraid);
		VFImage vfimg = vfImageService.getVFImageByID(vfimageid);

		if ( (infrs != null) && (vfimg != null)) {
			
			if ( vfimg.getDeployedInfrastructureById(infrs.getId() ) ==null ){
				vfimg.getDeployedInfrastructures().add(infrs);
			}
			if ( infrs.getSupportedImageById( vfimg.getId() ) == null ){
				infrs.getSupportedImages().add(vfimg);
			}
			
			vfImageService.updateVFImageInfo(vfimg);
			infrastructureService.updateInfrastructureInfo(infrs);
			return ResponseEntity.ok( infrs );
		} else {
			return (ResponseEntity<?>) ResponseEntity.badRequest();
		}
	}
	
	/**
	 * Validation Result
	 */
	

	@PutMapping( value = "/admin/validationjobs/{vxf_id}", produces = "application/json", consumes = "application/json" )
	public ResponseEntity<?>  updateUvalidationjob(@PathVariable("vxf_id") int vxfid, @Valid @RequestBody ValidationJobResult vresult) {
		logger.info("Received PUT ValidationJobResult for vxfid: " + vresult.getVxfid() );		
		

		PortalUser u =  usersService.findByUsername( SecurityContextHolder.getContext().getAuthentication().getName() );

		if ( (!u.getRoles().contains(UserRoleType.ROLE_ADMIN)) &&  (!u.getRoles().contains(UserRoleType.ROLE_TESTBED_PROVIDER )) ) {
			 return (ResponseEntity<?>) ResponseEntity.status(HttpStatus.FORBIDDEN);
			
		}
		
		vxfid = vresult.getVxfid();
		
		Product prod = productService.getProductByID(vxfid) ;
		
		if ( prod == null )
		{
			logger.info("updateUvalidationjob: prod == null for VXF with id=" + vxfid + ". Return Status NOT_FOUND");		
			CentralLogger.log( CLevel.INFO, "updateUvalidationjob: prod == null for VXF with id=" + vxfid + ". Return Status NOT_FOUND");																						

			return (ResponseEntity<?>) ResponseEntity.notFound();
		}
		if ( !(prod instanceof VxFMetadata) )
		{
			logger.info("updateUvalidationjob: prod not instance of VxFMetadata for VXF with id=" + vxfid + ". Return Status NOT_FOUND");		
			CentralLogger.log( CLevel.INFO, "updateUvalidationjob: prod == null for VXF with id=" + vxfid + ". Return Status NOT_FOUND");																						

			return (ResponseEntity<?>) ResponseEntity.notFound();
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
		vxf = (VxFMetadata) productService.updateProductInfo( vxf );		
		
		BusController.getInstance().updatedVxF( vxf.getId() );		
		BusController.getInstance().updatedValidationJob( vxf.getId() );		
		
		VxFMetadata vxfr = (VxFMetadata) productService.getProductByID( vxfid) ; //rereading this, seems to keep the DB connection
		
		return ResponseEntity.ok( vxfr );
	}
	
	
	
	
	
//	/**
//	 * 
//	 * SFA related
//	 */
//	
//	@POST
//	@Path("/sfawrap")
//	@Produces("text/xml")
//	@Consumes("text/xml")
//	public Response getSFA( String xmlreq) {
//		logger.info("/sfawrap param=" + xmlreq );
//		//AggregateManager.listAvailableResources
//		
//
//		
//		List<VxFMetadata> vxfs = portalRepositoryRef.getVxFs( null, true);
//		
//		Date d = new Date();
//		StringBuilder sfaresponse = new StringBuilder();
//		//sfaresponse.append( "<?xml version=\"1.0\"?>" );
//		sfaresponse.append( "<rspec xmlns=\"http://www.protogeni.net/resources/rspec/2\"  type=\"advertisement\" valid_until=\"" + "2020-05-20T16:03:57+03:00" +  "\" generated=\"" + "2018-06-20T16:03:57+03:00"  + "\">" );
//		sfaresponse.append( "<statistics call=\"ListResources\">" );
//		sfaresponse.append( "<aggregate status=\"success\" name=\"5ginfire\" elapsed=\"0.1\"/>" );
//		sfaresponse.append( "</statistics>" );
//		sfaresponse.append( "<network name=\"5ginfire\">" );
//		
//		for (VxFMetadata vxFMetadata : vxfs) {
//			sfaresponse.append( " <node component_manager_id=\"urn:publicid:IDN+5ginfire+authority+cm\"  component_id=\"urn:publicid:IDN+upatras:p2e+node+" + vxFMetadata.getUuid()  + "\"  component_name=\"" + vxFMetadata.getName() + "\" site_id=\"urn:publicid:IDN+5ginfire:p2e+authority+sa\">" );
//			sfaresponse.append( "<displayname>" + vxFMetadata.getName() + "</displayname>" );
//			sfaresponse.append( "<package>" + vxFMetadata.getPackageLocation()  + "</package>" );
//			sfaresponse.append( "" );
//			sfaresponse.append( "<location country=\"unknown\" longitude=\"21.7885\" latitude=\"38.2845\"/>" );
//			sfaresponse.append( " <description>" + vxFMetadata.getShortDescription() + "</description>" );
//			sfaresponse.append( "" );
//			sfaresponse.append( "<lease from=\"" + d.toString() + "\" until=\"" + d.toString() + "\">false</lease>" );
//			sfaresponse.append( "</node>" );
//		}
//		
//		
//		
//		sfaresponse.append( "</network>" );
//		sfaresponse.append( "</rspec>" );
//		
//
//		
//		return Response.ok().entity( sfaresponse.toString() ).build();
//
//	}

}
