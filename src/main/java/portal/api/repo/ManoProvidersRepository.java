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
package portal.api.repo;

import java.util.Collection;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import io.openslice.model.Category;
import io.openslice.model.MANOprovider;


/**
 * @author ctranoris
 *
 */
@Repository
public interface ManoProvidersRepository extends PagingAndSortingRepository<MANOprovider, Long> {

	@Query( value="SELECT mp FROM MANOprovider mp WHERE mp.enabledForONBOARDING = TRUE")
	Collection<MANOprovider> findAllEnabled();

	@Query( value="SELECT mp FROM MANOprovider mp WHERE mp.enabledForSYNC = TRUE")
	Collection<MANOprovider> findAllEnabledForSync();
	
}
