/* Licensed under the Apache License, Version 2.0 (the "License");
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
package org.camunda.bpm.engine.rest.history;

import javax.ws.rs.core.Response.Status;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.jobexecutor.historycleanup.HistoryCleanupHelper;
import org.camunda.bpm.engine.rest.AbstractRestServiceTest;
import org.camunda.bpm.engine.rest.helper.MockProvider;
import org.camunda.bpm.engine.rest.mapper.JacksonConfigurator;
import org.camunda.bpm.engine.rest.util.container.TestContainerRule;
import org.camunda.bpm.engine.runtime.Job;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import com.jayway.restassured.http.ContentType;
import static com.jayway.restassured.RestAssured.given;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class HistoryCleanupRestServiceInteractionTest extends AbstractRestServiceTest {

  @ClassRule
  public static TestContainerRule rule = new TestContainerRule();

  protected static final String HISTORY_CLEANUP_URL = TEST_RESOURCE_ROOT_PATH + "/history/cleanup";
  protected static final String FIND_HISTORY_CLEANUP_JOB_URL = HISTORY_CLEANUP_URL + "/job";
  protected static final String CONFIGURATION_URL = HISTORY_CLEANUP_URL + "/configuration";

  private HistoryService historyServiceMock;

  @Before
  public void setUpRuntimeData() {
    historyServiceMock = mock(HistoryService.class);
    Job mockJob = MockProvider.createMockJob();
    when(historyServiceMock.cleanUpHistoryAsync(anyBoolean()))
        .thenReturn(mockJob);
    when(historyServiceMock.findHistoryCleanupJob())
        .thenReturn(mockJob);

    // runtime service
    when(processEngine.getHistoryService()).thenReturn(historyServiceMock);
  }

  @Test
  public void testFindHistoryCleanupJob() {
    given().contentType(ContentType.JSON)
        .then()
        .expect().statusCode(Status.OK.getStatusCode())
        .when().get(FIND_HISTORY_CLEANUP_JOB_URL);

   verify(historyServiceMock).findHistoryCleanupJob();
  }

  @Test
  public void testFindNoHistoryCleanupJob() {
    when(historyServiceMock.findHistoryCleanupJob())
        .thenReturn(null);

    given().contentType(ContentType.JSON)
        .then()
        .expect().statusCode(Status.NOT_FOUND.getStatusCode())
        .when().get(FIND_HISTORY_CLEANUP_JOB_URL);

   verify(historyServiceMock).findHistoryCleanupJob();
  }

  @Test
  public void testHistoryCleanupImmediatelyDueDefault() {
    given().contentType(ContentType.JSON)
        .then()
        .expect().statusCode(Status.OK.getStatusCode())
        .when().post(HISTORY_CLEANUP_URL);

    verify(historyServiceMock).cleanUpHistoryAsync(true);
  }

  @Test
  public void testHistoryCleanupImmediatelyDue() {
    given().contentType(ContentType.JSON)
        .queryParam("immediatelyDue", true)
        .then().expect().statusCode(Status.OK.getStatusCode())
        .when().post(HISTORY_CLEANUP_URL);

    verify(historyServiceMock).cleanUpHistoryAsync(true);
  }

  @Test
  public void testHistoryCleanup() {
    given().contentType(ContentType.JSON).queryParam("immediatelyDue", false)
        .then().expect().statusCode(Status.OK.getStatusCode())
        .when().post(HISTORY_CLEANUP_URL);

    verify(historyServiceMock).cleanUpHistoryAsync(false);
  }

  @Test
  public void testHistoryConfiguration() throws ParseException {
    ProcessEngineConfigurationImpl processEngineConfigurationImplMock = mock(ProcessEngineConfigurationImpl.class);
    Date startDate = HistoryCleanupHelper.parseTimeConfiguration("23:59");
    Date endDate = HistoryCleanupHelper.parseTimeConfiguration("00:00");
    when(processEngine.getProcessEngineConfiguration()).thenReturn(processEngineConfigurationImplMock);
    when(processEngineConfigurationImplMock.getHistoryCleanupBatchWindowStartTimeAsDate()).thenReturn(startDate);
    when(processEngineConfigurationImplMock.getHistoryCleanupBatchWindowEndTimeAsDate()).thenReturn(endDate);

    Date now = new Date();
    Calendar today = Calendar.getInstance();
    today.setTime(now);
    Calendar tomorrow = Calendar.getInstance();
    tomorrow.setTime(HistoryCleanupHelper.addDays(now, 1));

    Date timeToday = HistoryCleanupHelper.parseTimeConfiguration("23:59");
    Date timeTomorrow = HistoryCleanupHelper.parseTimeConfiguration("00:00");

    Date dateToday = HistoryCleanupHelper.updateTime(today.getTime(), timeToday);
    Date dateTomorrow = HistoryCleanupHelper.updateTime(tomorrow.getTime(), timeTomorrow);

    SimpleDateFormat sdf = new SimpleDateFormat(JacksonConfigurator.dateFormatString);

    given()
      .contentType(ContentType.JSON)
    .then().expect()
      .statusCode(Status.OK.getStatusCode())
      .body("batchWindowStartTime", containsString(sdf.format(dateToday)))
      .body("batchWindowEndTime", containsString(sdf.format(dateTomorrow)))
    .when()
      .get(CONFIGURATION_URL);

    verify(processEngineConfigurationImplMock).getHistoryCleanupBatchWindowStartTimeAsDate();
    verify(processEngineConfigurationImplMock).getHistoryCleanupBatchWindowEndTimeAsDate();
  }

  @Test
  public void testHistoryConfigurationWhenBatchNotDefined() {
    ProcessEngineConfigurationImpl processEngineConfigurationImplMock = mock(ProcessEngineConfigurationImpl.class);
    when(processEngine.getProcessEngineConfiguration()).thenReturn(processEngineConfigurationImplMock);
    when(processEngineConfigurationImplMock.getHistoryCleanupBatchWindowStartTimeAsDate()).thenReturn(null);
    when(processEngineConfigurationImplMock.getHistoryCleanupBatchWindowEndTimeAsDate()).thenReturn(null);

    given()
      .contentType(ContentType.JSON)
    .then().expect()
      .statusCode(Status.OK.getStatusCode())
      .body("batchWindowStartTime", equalTo(null))
      .body("batchWindowEndTime", equalTo(null))
    .when()
      .get(CONFIGURATION_URL);

    verify(processEngineConfigurationImplMock).getHistoryCleanupBatchWindowStartTimeAsDate();
    verify(processEngineConfigurationImplMock).getHistoryCleanupBatchWindowEndTimeAsDate();
  }

}
