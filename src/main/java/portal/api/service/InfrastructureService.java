/*-
 * ========================LICENSE_START=================================
 * io.openslice.portal.api
 * %%
 * Copyright (C) 2019 openslice.io
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package portal.api.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;

import io.openslice.model.ExperimentMetadata;
import io.openslice.model.Infrastructure;
import io.openslice.model.MANOprovider;
import io.openslice.model.Product;
import io.openslice.model.VxFMetadata;
import io.openslice.model.VxFOnBoardedDescriptor;
import portal.api.repo.InfrastructureRepository;
import portal.api.repo.ManoProvidersRepository;
import portal.api.repo.ProductRepository;
import portal.api.repo.VxFsRepository;

@Service
public class InfrastructureService {

	@Autowired
	InfrastructureRepository infraRepo;


	public List<Infrastructure> getInfrastructures() {
		return (List<Infrastructure>) this.infraRepo.findAll();
	}

	public Infrastructure addInfrastructure(Infrastructure c) {
		return this.infraRepo.save(c);
	}

	public Infrastructure updateInfrastructureInfo(Infrastructure infrastructure) {
		return this.infraRepo.save(infrastructure);
	}

	public Infrastructure getInfrastructureByID( long infraid) {
		Optional<Infrastructure> o = this.infraRepo.findById(infraid);

		return o.orElse(null);
	}

	public void deleteInfrastructure( Infrastructure infrastructure ) {
		this.infraRepo.delete( infrastructure );
		
	}

	/**
	 * @param d
	 * @return as json
	 * @throws JsonProcessingException
	 */
	public String getInfrastructuresEagerDataJson() throws JsonProcessingException {

		List<Infrastructure> il = this.getInfrastructures();
		ObjectMapper mapper = new ObjectMapper();
        // Registering Hibernate5Module to support lazy objects
		// this will fetch all lazy objects of VxF before marshaling
        mapper.registerModule(new Hibernate5Module()); 
		String res = mapper.writeValueAsString( il );
		
		return res;
	}
	
	/**
	 * @param d
	 * @return as json
	 * @throws JsonProcessingException
	 */
	public String addInfrastructureEagerDataJson(Infrastructure receivedInfrastructure) throws JsonProcessingException {

		Infrastructure infrastructure = this.addInfrastructure(receivedInfrastructure);
		ObjectMapper mapper = new ObjectMapper();
		
        //Registering Hibernate4Module to support lazy objects
		// this will fetch all lazy objects before marshaling
        mapper.registerModule(new Hibernate5Module()); 
		String res = mapper.writeValueAsString( infrastructure );
		
		return res;		
	}
	
}
