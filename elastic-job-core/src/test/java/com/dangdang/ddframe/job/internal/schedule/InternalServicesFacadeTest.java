/**
 * Copyright 1999-2015 dangdang.com.
 * <p>
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
 * </p>
 */

package com.dangdang.ddframe.job.internal.schedule;

import com.dangdang.ddframe.job.api.JobConfiguration;
import com.dangdang.ddframe.job.api.JobExecutionMultipleShardingContext;
import com.dangdang.ddframe.job.api.listener.AbstractDistributeOnceElasticJobListener;
import com.dangdang.ddframe.job.api.listener.ElasticJobListener;
import com.dangdang.ddframe.job.fixture.TestJob;
import com.dangdang.ddframe.job.internal.config.ConfigurationService;
import com.dangdang.ddframe.job.internal.election.LeaderElectionService;
import com.dangdang.ddframe.job.internal.execution.ExecutionContextService;
import com.dangdang.ddframe.job.internal.execution.ExecutionService;
import com.dangdang.ddframe.job.internal.failover.FailoverService;
import com.dangdang.ddframe.job.internal.listener.ListenerManager;
import com.dangdang.ddframe.job.internal.monitor.MonitorService;
import com.dangdang.ddframe.job.internal.offset.OffsetService;
import com.dangdang.ddframe.job.internal.server.ServerService;
import com.dangdang.ddframe.job.internal.sharding.ShardingService;
import com.dangdang.ddframe.job.internal.statistics.StatisticsService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.quartz.JobDataMap;
import org.unitils.util.ReflectionUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class InternalServicesFacadeTest {
    
    @Mock
    private ConfigurationService configService;
    
    @Mock
    private LeaderElectionService leaderElectionService;
    
    @Mock
    private ServerService serverService;
    
    @Mock
    private ShardingService shardingService;
    
    @Mock
    private ExecutionContextService executionContextService;
    
    @Mock
    private ExecutionService executionService;
    
    @Mock
    private FailoverService failoverService;
    
    @Mock
    private StatisticsService statisticsService;
    
    @Mock
    private OffsetService offsetService;
    
    @Mock
    private MonitorService monitorService;
    
    @Mock
    private ListenerManager listenerManager;
    
    private JobConfiguration jobConfig = new JobConfiguration("testJob", TestJob.class, 3, "0/1 * * * * ?");
    
    private InternalServicesFacade internalServicesFacade = new InternalServicesFacade(null, jobConfig, Arrays.asList(new TestElasticJobListener(), new TestDistributeOnceElasticJobListener()));
    
    @Before
    public void setUp() throws NoSuchFieldException {
        MockitoAnnotations.initMocks(this);
        ReflectionUtils.setFieldValue(internalServicesFacade, "configService", configService);
        ReflectionUtils.setFieldValue(internalServicesFacade, "leaderElectionService", leaderElectionService);
        ReflectionUtils.setFieldValue(internalServicesFacade, "serverService", serverService);
        ReflectionUtils.setFieldValue(internalServicesFacade, "shardingService", shardingService);
        ReflectionUtils.setFieldValue(internalServicesFacade, "executionContextService", executionContextService);
        ReflectionUtils.setFieldValue(internalServicesFacade, "executionService", executionService);
        ReflectionUtils.setFieldValue(internalServicesFacade, "failoverService", failoverService);
        ReflectionUtils.setFieldValue(internalServicesFacade, "statisticsService", statisticsService);
        ReflectionUtils.setFieldValue(internalServicesFacade, "offsetService", offsetService);
        ReflectionUtils.setFieldValue(internalServicesFacade, "monitorService", monitorService);
        ReflectionUtils.setFieldValue(internalServicesFacade, "listenerManager", listenerManager);
    }
    
    @Test
    public void testNew() throws NoSuchFieldException {
        List<ElasticJobListener> actual = ReflectionUtils.getFieldValue(internalServicesFacade, ReflectionUtils.getFieldWithName(InternalServicesFacade.class, "elasticJobListeners", false));
        assertThat(actual.size(), is(2));
        assertThat(actual.get(0), instanceOf(TestElasticJobListener.class));
        assertThat(actual.get(1), instanceOf(TestDistributeOnceElasticJobListener.class));
        assertNotNull(ReflectionUtils.getFieldValue(actual.get(1), AbstractDistributeOnceElasticJobListener.class.getDeclaredField("guaranteeService")));
    }
    
    @Test
    public void testRegisterStartUpInfo() {
        internalServicesFacade.registerStartUpInfo();
        verify(listenerManager).startAllListeners();
        verify(leaderElectionService).leaderElection();
        verify(configService).persistJobConfiguration();
        verify(serverService).persistServerOnline();
        verify(serverService).clearJobStoppedStatus();
        verify(statisticsService).startProcessCountJob();
        verify(shardingService).setReshardingFlag();
        verify(monitorService).listen();
    }
    
    @Test
    public void testFillJobDetail() {
        JobDataMap actual = new JobDataMap();
        internalServicesFacade.fillJobDetail(actual);
        assertThat((ConfigurationService) actual.get("configService"), is(configService));
        assertThat((ShardingService) actual.get("shardingService"), is(shardingService));
        assertThat((ExecutionContextService) actual.get("executionContextService"), is(executionContextService));
        assertThat((ExecutionService) actual.get("executionService"), is(executionService));
        assertThat((FailoverService) actual.get("failoverService"), is(failoverService));
        assertThat((OffsetService) actual.get("offsetService"), is(offsetService));
        assertThat(((List) actual.get("elasticJobListeners")).size(), is(2));
    }
    
    @Test
    public void testReleaseJobResource() {
        internalServicesFacade.releaseJobResource();
        verify(monitorService).close();
        verify(statisticsService).stopProcessCountJob();
    }
    
    @Test
    public void testResumeCrashedJobInfo() {
        when(shardingService.getLocalHostShardingItems()).thenReturn(Collections.<Integer>emptyList());
        internalServicesFacade.resumeCrashedJobInfo();
        verify(serverService).persistServerOnline();
        verify(shardingService).getLocalHostShardingItems();
        verify(executionService).clearRunningInfo(Collections.<Integer>emptyList());
    }
    
    @Test
    public void testClearJobStoppedStatus() {
        internalServicesFacade.clearJobStoppedStatus();
        verify(serverService).clearJobStoppedStatus();
    }
    
    @Test
    public void testIsJobStoppedManually() {
        when(serverService.isJobStoppedManually()).thenReturn(true);
        assertTrue(internalServicesFacade.isJobStoppedManually());
    }
    
    @Test
    public void testGetCron() {
        when(configService.getCron()).thenReturn("0 * * * * *");
        assertThat(internalServicesFacade.getCron(), is("0 * * * * *"));
    }
    
    @Test
    public void testIsMisfire() {
        when(configService.isMisfire()).thenReturn(true);
        assertTrue(internalServicesFacade.isMisfire());
    }
    
    @Test
    public void testNewJobTriggerListener() {
        assertThat(internalServicesFacade.newJobTriggerListener(), instanceOf(JobTriggerListener.class));
    }
    
    static class TestElasticJobListener implements ElasticJobListener {
        
        @Override
        public void beforeJobExecuted(final JobExecutionMultipleShardingContext shardingContext) {
        }
        
        @Override
        public void afterJobExecuted(final JobExecutionMultipleShardingContext shardingContext) {
        }
    }

    static class TestDistributeOnceElasticJobListener extends AbstractDistributeOnceElasticJobListener {
        
        TestDistributeOnceElasticJobListener() {
            super(500000L, 500000L);
        }
        
        @Override
        public void doBeforeJobExecutedAtLastStarted(final JobExecutionMultipleShardingContext shardingContext) {
        }
        
        @Override
        public void doAfterJobExecutedAtLastCompleted(final JobExecutionMultipleShardingContext shardingContext) {
        }
    }
}
