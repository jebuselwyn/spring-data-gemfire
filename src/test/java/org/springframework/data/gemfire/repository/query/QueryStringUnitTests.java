/*
 * Copyright 2012-2018 the original author or authors.
 *
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
 */

package org.springframework.data.gemfire.repository.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.data.gemfire.repository.query.QueryString.HINT_PATTERN;
import static org.springframework.data.gemfire.repository.query.QueryString.IMPORT_PATTERN;
import static org.springframework.data.gemfire.repository.query.QueryString.LIMIT_PATTERN;
import static org.springframework.data.gemfire.repository.query.QueryString.TRACE_PATTERN;

import java.util.Arrays;
import java.util.regex.Pattern;

import org.apache.geode.cache.Region;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.gemfire.repository.sample.Person;
import org.springframework.data.gemfire.repository.sample.RootUser;

/**
 * Test suite of test cases testing the contract and functionality of the {@link QueryString} class.
 *
 * @author Oliver Gierke
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mock
 * @see org.springframework.data.gemfire.repository.query.QueryString
 */
@RunWith(MockitoJUnitRunner.class)
public class QueryStringUnitTests {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	@Mock
	@SuppressWarnings("rawtypes")
	Region region;

	protected Sort.Order newSortOrder(String property) {
		return newSortOrder(property, Sort.Direction.ASC);
	}

	protected Sort.Order newSortOrder(String property, Sort.Direction direction) {
		return new Sort.Order(direction, property);
	}

	protected Sort newSort(Sort.Order... orders) {
		return new Sort(orders);
	}

	@Test
	public void createQueryStringWithDomainType() {
		assertThat(new QueryString(Person.class).toString()).isEqualTo("SELECT * FROM /Person");
	}

	@Test
	public void createQueryStringWithDomainTypeHavingCount() {
		assertThat(new QueryString(Person.class, true).toString()).isEqualTo("SELECT count(*) FROM /Person");
	}

	@Test
	public void createQueryStringWithNullDomainType() {
		exception.expect(IllegalArgumentException.class);
		exception.expectCause(is(nullValue(Throwable.class)));
		exception.expectMessage(is(equalTo("domainType must not be null")));

		new QueryString((Class<?>) null);
	}

	@Test
	public void createQueryStringWithQuery() {
		String query = "SELECT * FROM /Example";

		assertThat(new QueryString(query).toString()).isEqualTo(query);
	}

	@Test
	public void createQueryStringWithUnspecifiedQueryThrowsIllegalArgumentException() {
		assertUnspecifiedQueryThrowsIllegalArgumentException("");
		assertUnspecifiedQueryThrowsIllegalArgumentException("  ");
		assertUnspecifiedQueryThrowsIllegalArgumentException(null);
	}

	protected void assertUnspecifiedQueryThrowsIllegalArgumentException(String query) {
		exception.expect(IllegalArgumentException.class);
		exception.expectCause(is(nullValue(Throwable.class)));
		exception.expectMessage(is(equalTo("An OQL Query must be specified")));

		new QueryString(query);
	}

	@Test
	public void hintPatternMatches() {
		assertThat(matches(HINT_PATTERN, "<HINT 'ExampleIndex'>")).isTrue();
		assertThat(matches(HINT_PATTERN, "<HINT 'IdIdx'> SELECT * FROM /Example WHERE id = $1")).isTrue();
		assertThat(matches(HINT_PATTERN, "<HINT 'LastNameIdx', 'BirthDateIdx'> SELECT * FROM /Person WHERE lastName = $1 AND birthDate = $2")).isTrue();
	}

	@Test
	public void hintPatternNoMatches() {
		assertThat(matches(HINT_PATTERN, "HINT")).isFalse();
		assertThat(matches(HINT_PATTERN, "<HINT>")).isFalse();
		assertThat(matches(HINT_PATTERN, "<HINT ''>")).isFalse();
		assertThat(matches(HINT_PATTERN, "<HINT '  '>")).isFalse();
		assertThat(matches(HINT_PATTERN, "<HINT IdIdx>")).isFalse();
		assertThat(matches(HINT_PATTERN, "<HINT LastNameIdx, FirstNameIdx>")).isFalse();
		assertThat(matches(HINT_PATTERN, "SELECT * FROM /Example")).isFalse();
		assertThat(matches(HINT_PATTERN, "SELECT * FROM /Hint")).isFalse();
		assertThat(matches(HINT_PATTERN, "SELECT x.hint FROM /Clues x WHERE x.hint > $1")).isFalse();
	}

	@Test
	public void importPatternMatches() {
		assertThat(matches(IMPORT_PATTERN, "IMPORT *;")).isTrue();
		assertThat(matches(IMPORT_PATTERN, "IMPORT org.example.*;")).isTrue();
		assertThat(matches(IMPORT_PATTERN, "IMPORT org.example.Type;")).isTrue();
		assertThat(matches(IMPORT_PATTERN, "IMPORT org.example.app.domain.DomainType; SELECT * FROM /DomainType")).isTrue();
	}

	@Test
	public void importPatternNoMatches() {
		assertThat(matches(IMPORT_PATTERN, "IMPORT")).isFalse();
		assertThat(matches(IMPORT_PATTERN, "IMPORT ;")).isFalse();
		assertThat(matches(IMPORT_PATTERN, "IMPORT *")).isFalse();
		assertThat(matches(IMPORT_PATTERN, "IMPORT *:")).isFalse();
		assertThat(matches(IMPORT_PATTERN, "SELECT * FROM /Example")).isFalse();
	}

	@Test
	public void limitPatternMatches() {
		assertThat(matches(LIMIT_PATTERN, "LIMIT 0")).isTrue();
		assertThat(matches(LIMIT_PATTERN, "LIMIT 1")).isTrue();
		assertThat(matches(LIMIT_PATTERN, "LIMIT 10")).isTrue();
		assertThat(matches(LIMIT_PATTERN, "SELECT * FROM /Example LIMIT 10")).isTrue();
		assertThat(matches(LIMIT_PATTERN, "SELECT * FROM /Example WHERE id = $1 LIMIT 10")).isTrue();
	}

	@Test
	public void limitPatternNoMatches() {
		assertThat(matches(LIMIT_PATTERN, "LIMIT")).isFalse();
		assertThat(matches(LIMIT_PATTERN, "LIMIT lO")).isFalse();
		assertThat(matches(LIMIT_PATTERN, "LIMIT AF")).isFalse();
		assertThat(matches(LIMIT_PATTERN, "LIMIT ten")).isFalse();
		assertThat(matches(LIMIT_PATTERN, "SELECT * FROM /Example LIMIT")).isFalse();
		assertThat(matches(LIMIT_PATTERN, "SELECT * FROM /Example WHERE id = $1 LIM 10")).isFalse();
	}

	@Test
	public void tracePatternMatches() {
		assertThat(matches(TRACE_PATTERN, "<TRACE>")).isTrue();
		assertThat(matches(TRACE_PATTERN, "<TRACE> SELECT * FROM /Example")).isTrue();
		assertThat(matches(TRACE_PATTERN, "<TRACE>SELECT * FROM /Example")).isTrue();
		assertThat(matches(TRACE_PATTERN, "SELECT * FROM /Example<TRACE>")).isTrue();
	}

	@Test
	public void tracePatternNoMatches() {
		assertThat(matches(TRACE_PATTERN, "TRACE")).isFalse();
		assertThat(matches(TRACE_PATTERN, "TRACE SELECT * FROM /Example")).isFalse();
		assertThat(matches(TRACE_PATTERN, "<TRACE SELECT * FROM /Example>")).isFalse();
	}

	protected boolean matches(Pattern pattern, String value) {
		return pattern.matcher(value).find();
	}

	// SGF-251
	@Test
	public void replacesDomainTypeSimpleNameWithRegionPathCorrectly() {
		QueryString query = new QueryString(Person.class);

		when(region.getFullPath()).thenReturn("/foo/bar");

		assertThat(query.forRegion(Person.class, region).toString()).isEqualTo("SELECT * FROM /foo/bar");

		verify(region, times(1)).getFullPath();
	}

	// SGF-156, SGF-251
	@Test
	public void replacesFromClauseWithRegionPathCorrectly() {
		QueryString query = new QueryString("SELECT * FROM /Persons p WHERE p.lastname = $1");

		when(region.getFullPath()).thenReturn("/People");

		assertThat(query.forRegion(Person.class, region).toString())
			.isEqualTo("SELECT * FROM /People p WHERE p.lastname = $1");

		verify(region, times(1)).getFullPath();
	}

	// SGF-252
	@Test
	public void replacesFullyQualifiedSubRegionPathWithRegionPathCorrectly() {
		QueryString query = new QueryString("SELECT * FROM //Local/Root/Users u WHERE u.username = $1");

		when(region.getFullPath()).thenReturn("/Remote/Root/Users");

		assertThat(query.forRegion(RootUser.class, region).toString())
			.isEqualTo("SELECT * FROM /Remote/Root/Users u WHERE u.username = $1");

		verify(region, times(1)).getFullPath();
	}

	@Test
	public void bindsInValuesCorrectly() {
		QueryString query = new QueryString("SELECT * FROM /Collection WHERE elements IN SET $1");

		assertThat(query.bindIn(Arrays.asList(1, 2, 3)).toString())
			.isEqualTo("SELECT * FROM /Collection WHERE elements IN SET ('1', '2', '3')");
	}

	@Test
	public void detectsInParameterIndexesCorrectly() {
		QueryString query = new QueryString("SELECT * FROM /Example WHERE values IN SET $1 OR IN SET $2");

		assertThat(query.getInParameterIndexes()).isEqualTo(Arrays.asList(1, 2));
	}

	@Test
	public void addsNoOrderByClauseCorrectly() {
		QueryString query = new QueryString("SELECT * FROM /People p").orderBy(null);

		assertThat(query.toString()).isEqualTo("SELECT * FROM /People p");
	}

	@Test
	public void addsOrderByClauseCorrectly() {
		QueryString query = new QueryString("SELECT * FROM /People p WHERE p.lastName = $1")
			.orderBy(newSort(newSortOrder("lastName", Sort.Direction.DESC), newSortOrder("firstName")));

		assertThat(query.toString())
			.isEqualTo("SELECT DISTINCT * FROM /People p WHERE p.lastName = $1 ORDER BY lastName DESC, firstName ASC");
	}

	@Test
	public void addsSingleOrderByClauseCorrectly() {
		QueryString query = new QueryString("SELECT DISTINCT p.lastName FROM /People p WHERE p.firstName = $1")
			.orderBy(newSort(newSortOrder("lastName")));

		assertThat(query.toString())
			.isEqualTo("SELECT DISTINCT p.lastName FROM /People p WHERE p.firstName = $1 ORDER BY lastName ASC");
	}

	@Test
	public void withHints() {
		assertThat(new QueryString("SELECT * FROM /Example").withHints("IdIdx").toString())
			.isEqualTo("<HINT 'IdIdx'> SELECT * FROM /Example");

		assertThat(new QueryString("SELECT * FROM /Example").withHints("IdIdx", "SpatialIdx", "TxDateIdx").toString())
			.isEqualTo("<HINT 'IdIdx', 'SpatialIdx', 'TxDateIdx'> SELECT * FROM /Example");
	}

	@Test
	public void withoutHints() {
		QueryString expectedQueryString = new QueryString("SELECT * FROM /Example");

		assertThat(expectedQueryString.withHints()).isSameAs(expectedQueryString);
		assertThat(expectedQueryString.withHints((String[]) null)).isSameAs(expectedQueryString);
	}

	@Test
	public void withImport() {
		assertThat(new QueryString("SELECT * FROM /People").withImport("org.example.app.domain.Person").toString())
			.isEqualTo("IMPORT org.example.app.domain.Person; SELECT * FROM /People");
	}

	@Test
	public void withoutImport() {
		QueryString expectedQueryString = new QueryString("SELECT * FROM /Example");

		assertThat(expectedQueryString.withImport(null)).isSameAs(expectedQueryString);
		assertThat(expectedQueryString.withImport("")).isSameAs(expectedQueryString);
		assertThat(expectedQueryString.withImport("  ")).isSameAs(expectedQueryString);
	}

	@Test
	public void withLimit() {
		assertThat(new QueryString("SELECT * FROM /Example").withLimit(10).toString())
			.isEqualTo("SELECT * FROM /Example LIMIT 10");

		assertThat(new QueryString("SELECT * FROM /Example").withLimit(0).toString())
			.isEqualTo("SELECT * FROM /Example LIMIT 0");

		assertThat(new QueryString("SELECT * FROM /Example").withLimit(-5).toString())
			.isEqualTo("SELECT * FROM /Example LIMIT -5");
	}

	@Test
	public void withoutLimit() {
		QueryString expectedQueryString = new QueryString("SELECT * FROM /Example");

		assertThat(expectedQueryString.withLimit(null)).isSameAs(expectedQueryString);
	}

	@Test
	public void withTrace() {
		assertThat(new QueryString("SELECT * FROM /Example").withTrace().toString())
			.isEqualTo("<TRACE> SELECT * FROM /Example");
	}

	@Test
	public void withHintAndTrace() {
		assertThat(new QueryString("SELECT * FROM /Example").withHints("IdIdx").withTrace().toString())
			.isEqualTo("<TRACE> <HINT 'IdIdx'> SELECT * FROM /Example");
	}

	@Test
	public void withHintImportLimitAndTrace() {
		QueryString query = new QueryString("SELECT * FROM /Example");

		assertThat(query.withImport("org.example.domain.Type").withHints("IdIdx", "NameIdx").withLimit(20).withTrace().toString())
			.isEqualTo("<TRACE> <HINT 'IdIdx', 'NameIdx'> IMPORT org.example.domain.Type; SELECT * FROM /Example LIMIT 20");
	}
}
