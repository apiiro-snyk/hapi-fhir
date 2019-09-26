package ca.uhn.fhir.jpa.search.cache;

/*-
 * #%L
 * HAPI FHIR JPA Server
 * %%
 * Copyright (C) 2014 - 2019 University Health Network
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
 * #L%
 */

import ca.uhn.fhir.jpa.dao.data.ISearchResultDao;
import ca.uhn.fhir.jpa.entity.Search;
import ca.uhn.fhir.jpa.entity.SearchResult;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static ca.uhn.fhir.jpa.search.SearchCoordinatorSvcImpl.toPage;

public class DatabaseSearchResultCacheSvcImpl implements ISearchResultCacheSvc {
	private static final Logger ourLog = LoggerFactory.getLogger(DatabaseSearchResultCacheSvcImpl.class);

	@Autowired
	private ISearchResultDao mySearchResultDao;

	@Override
	@Transactional(Transactional.TxType.REQUIRED)
	public List<Long> fetchResultPids(Search theSearch, int theFrom, int theTo) {
		final Pageable page = toPage(theFrom, theTo);
		if (page == null) {
			return Collections.emptyList();
		}

		List<Long> retVal = mySearchResultDao
			.findWithSearchPid(theSearch.getId(), page)
			.getContent();

		ourLog.debug("fetchResultPids for range {}-{} returned {} pids", theFrom, theTo, retVal.size());

		// FIXME: should we remove the blocked number from this message?
		Validate.isTrue((theSearch.getNumFound() - theSearch.getNumBlocked()) < theTo || retVal.size() == (theTo - theFrom), "Failed to find results in cache, requested %d - %d and got %d with total found=%d and blocked %s", theFrom, theTo, retVal.size(), theSearch.getNumFound(), theSearch.getNumBlocked());

		return new ArrayList<>(retVal);
	}

	@Override
	@Transactional(Transactional.TxType.REQUIRED)
	public List<Long> fetchAllResultPids(Search theSearch) {
		List<Long> retVal = mySearchResultDao.findWithSearchPidOrderIndependent(theSearch.getId());
		ourLog.trace("fetchAllResultPids returned {} pids", retVal.size());
		return retVal;
	}

	@Override
	@Transactional(Transactional.TxType.REQUIRED)
	public void storeResults(Search theSearch, List<Long> thePreviouslyStoredResourcePids, List<Long> theNewResourcePids) {
		List<SearchResult> resultsToSave = Lists.newArrayList();

		ourLog.trace("Storing {} results with {} previous for search", theNewResourcePids.size(), thePreviouslyStoredResourcePids.size());

		int order = thePreviouslyStoredResourcePids.size();
		for (Long nextPid : theNewResourcePids) {
			SearchResult nextResult = new SearchResult(theSearch);
			nextResult.setResourcePid(nextPid);
			nextResult.setOrder(order);
			resultsToSave.add(nextResult);
			ourLog.trace("Saving ORDER[{}] Resource {}", order, nextResult.getResourcePid());

			order++;
		}

		mySearchResultDao.saveAll(resultsToSave);
	}

	@VisibleForTesting
	void setSearchDaoResultForUnitTest(ISearchResultDao theSearchResultDao) {
		mySearchResultDao = theSearchResultDao;
	}


}
