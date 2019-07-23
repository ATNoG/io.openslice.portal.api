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

package portal.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import portal.api.model.PortalUser;
import portal.api.model.UserSession;

//RUN a single Integration Test only, but runs all unit tests
//mvn clean -Pjetty.integration -Dit.test=PortalRepositoryIT verify

public class PortalRepositoryIT {

	private static String endpointUrl;
	private static final transient Log logger = LogFactory.getLog(PortalRepositoryIT.class.getName());
	private NewCookie cookieJSESSIONID;

	@BeforeClass
	public static void beforeClass() {
		endpointUrl = System.getProperty("service.url");
		logger.info("EbeforeClass endpointUrl = " + endpointUrl);

	}

	
	@Before
	public void APIlogin(){
		Response r = execPOSTonURLForAPILogin(endpointUrl + "/services/api/repo/sessions", "admin", "changeme");
		assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());

		Map<String, NewCookie> cookies = r.getCookies();
		
		Iterator it = cookies.entrySet().iterator();
	    while (it.hasNext()) {
	        Map.Entry pairs = (Map.Entry)it.next();
	        logger.info("=======> RESPONSE COOKIES  =>"+pairs.getKey() + " = " + pairs.getValue());
	    }
		 cookieJSESSIONID = cookies.get("JSESSIONID");
	}

	@Test
	public void testManagementOfRepo() throws Exception {
		List<PortalUser> busers = getUsers();
		int initialPortalUserList = busers.size();
		PortalUser bu = new PortalUser();
		bu.setName("ATESTUSER");
		bu.setOrganization("ANORGANIZATION");
		bu.setPasswordUnencrypted("APASS");
		bu.setUsername("AUSERNAME");
		bu.setEmail("ANEMAIL");

		// add a user...
		PortalUser retBU = addUser(bu);
		assertNotNull(bu.getId());
		assertEquals(bu.getName(), retBU.getName());
		assertEquals(bu.getOrganization(), retBU.getOrganization());
		//assertEquals( EncryptionUtil.hash( bu.getPassword() ), retBU.getPassword());
		assertEquals(bu.getUsername(), retBU.getUsername());

		// should be one more user in the DB
		assertEquals(initialPortalUserList + 1, getUsers().size());

		// GET a user by Id
		PortalUser retBUbyGET = getUserById(retBU.getId());
		assertEquals(retBU.getName(), retBUbyGET.getName());
		assertEquals(retBU.getOrganization(), retBUbyGET.getOrganization());
		//assertEquals(EncryptionUtil.hash( bu.getPassword() ), retBUbyGET.getPassword());
		assertEquals(retBU.getUsername(), retBUbyGET.getUsername());

		// update user
		bu = new PortalUser();
		bu.setName("ATESTUSERNEW");
		bu.setOrganization("ANORGANIZATIONNEW");
		bu.setPasswordUnencrypted("APASSNEW");
		bu.setUsername("AUSERNAMENEW");
		bu.setId(retBU.getId());
		PortalUser retBUUpdated = updateUser(retBU.getId(), bu);
		assertEquals(retBU.getId(), retBUUpdated.getId());
		assertEquals(bu.getName(), retBUUpdated.getName());
		assertEquals(bu.getOrganization(), retBUUpdated.getOrganization());
//		assertEquals(EncryptionUtil.hash( bu.getPassword() ), retBUUpdated.getPassword());
		assertEquals(bu.getUsername(), retBUUpdated.getUsername());

		// should be again the same user count in the DB
		assertEquals(initialPortalUserList + 1, getUsers().size());

		// GET the updated user by Id
		retBUbyGET = getUserById(retBU.getId());
		assertEquals(bu.getId(), retBUbyGET.getId());
		assertEquals(bu.getName(), retBUbyGET.getName());
		assertEquals(bu.getOrganization(), retBUbyGET.getOrganization());
//		assertEquals(EncryptionUtil.hash( bu.getPassword() ), retBUbyGET.getPassword());
		assertEquals(bu.getUsername(), retBUbyGET.getUsername());
		
		
		//delete our added user
		deleteUserById(retBU.getId());

		assertEquals(initialPortalUserList , getUsers().size());
		

	}

	private void deleteUserById(int id) {
		List<Object> providers = new ArrayList<Object>();
		providers.add(new com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider());

		WebClient client = WebClient.create(endpointUrl + "/services/api/repo/admin/users/" + id, providers);
		client.cookie(cookieJSESSIONID);
		Response r = client.accept("application/json").type("application/json").delete();
		assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
		
	}

	private PortalUser updateUser(int id, PortalUser bu) throws JsonParseException, IOException {
		List<Object> providers = new ArrayList<Object>();
		providers.add(new com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider());

		WebClient client = WebClient.create(endpointUrl + "/services/api/repo/admin/users/" + id, providers);
		client.cookie(cookieJSESSIONID);
		Response r = client.accept("application/json").type("application/json").put(bu);
		assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());

		MappingJsonFactory factory = new MappingJsonFactory();
		JsonParser parser = factory.createJsonParser((InputStream) r.getEntity());
		PortalUser output = parser.readValueAs(PortalUser.class);
		return output;
	}

	private PortalUser getUserById(int id) throws JsonParseException, IOException {
		List<Object> providers = new ArrayList<Object>();
		providers.add(new com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider());

		WebClient client = WebClient.create(endpointUrl + "/services/api/repo/admin/users/" + id, providers);
		client.cookie(cookieJSESSIONID);
		Response r = client.accept("application/json").type("application/json").get();
		assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());

		MappingJsonFactory factory = new MappingJsonFactory();
		JsonParser parser = factory.createJsonParser((InputStream) r.getEntity());
		PortalUser output = parser.readValueAs(PortalUser.class);
		return output;
	}

	private PortalUser addUser(PortalUser bu) throws JsonParseException, IOException {

		List<Object> providers = new ArrayList<Object>();
		providers.add(new com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider());
		
		//without session cookie first! SHould return 401 (UNAUTHORIZED)
		WebClient client = WebClient.create(endpointUrl + "/services/api/repo/admin/users", providers);
		Response r = client.accept("application/json").type("application/json").post(bu);
		assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), r.getStatus());
		
		//again with session cookie

		client = WebClient.create(endpointUrl + "/services/api/repo/admin/users", providers);
		client.cookie(cookieJSESSIONID);
		r = client.accept("application/json").type("application/json").post(bu);
		assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
		
		MappingJsonFactory factory = new MappingJsonFactory();
		JsonParser parser = factory.createJsonParser((InputStream) r.getEntity());
		PortalUser output = parser.readValueAs(PortalUser.class);
		return output;
	}

	public List<PortalUser> getUsers() throws Exception {

		logger.info("Executing TEST = testGetUsers");

		
		Response r = execGETonURL(endpointUrl + "/services/api/repo/admin/users", cookieJSESSIONID);
		assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());

		String portalAPIVersionListHeaders = (String) r.getHeaders().getFirst("X-Portal-API-Version");
		assertEquals("1.0.0", portalAPIVersionListHeaders);

		MappingJsonFactory factory = new MappingJsonFactory();
		JsonParser parser = factory.createJsonParser((InputStream) r.getEntity());

		JsonNode node = parser.readValueAsTree();
		ObjectMapper mapper = new ObjectMapper();
		TypeReference<List<PortalUser>> typeRef = new TypeReference<List<PortalUser>>() {
		};
		List<PortalUser> portalUsersList = mapper.readValue(node.traverse(), typeRef);
		for (PortalUser f : portalUsersList) {
			logger.info("user = " + f.getName() + ", ID = " + f.getId());
		}

		return portalUsersList;
	}

	private Response execPOSTonURLForAPILogin(String url, String username, String passw) {
		List<Object> providers = new ArrayList<Object>();
		providers.add(new com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider());

		WebClient client = WebClient.create(url, providers, username, passw, null);

		Cookie cookie = new Cookie("X-Portal-Key", "123456") ;
		client.cookie(cookie );
		
		UserSession uses = new UserSession();
		uses.setUsername(username);
		uses.setPassword(passw);
		Response r = client.accept("application/json").type("application/json").post(uses);
		return r;
	}

	private Response execGETonURL(String url, Cookie sessioncookie) {
		List<Object> providers = new ArrayList<Object>();
		providers.add(new com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider());

		WebClient client = WebClient.create(url, providers);

		Cookie cookie = new Cookie("X-Portal-Key", "123456") ;
		client.cookie(cookie );
		client.cookie(sessioncookie);
		
		Response r = client.accept("application/json").type("application/json").get();
		return r;
	}

//	private Response execGETonURL(String url) {
//		List<Object> providers = new ArrayList<Object>();
//		providers.add(new com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider());
//
//		WebClient client = WebClient.create(url, providers);
//		Cookie cookie = new Cookie("X-Portal-Key", "123456") ;
//		client.cookie(cookie );
//		
//		Response r = client.accept("application/json").type("application/json").get();
//		return r;
//	}

}
