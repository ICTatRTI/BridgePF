package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.List;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.Task;
import org.sagebionetworks.bridge.models.studies.Study;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Verify that the combination of running the scheduler, and persistence, work 
 * together correctly.   
 */
@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class TaskServiceTest {

    @Resource
    private TaskService service;
    
    @Resource
    private StudyService studyService;
    
    @Resource
    private TestUserAdminHelper helper;
    
    @Resource
    private SchedulePlanService schedulePlanService;
    
    private SchedulePlan schedulePlan;
    
    private Study study;
    
    private TestUser testUser;
    
    @Before
    public void before() {
        study = studyService.getStudy(TEST_STUDY.getIdentifier());
        testUser = helper.createUser(TaskServiceTest.class);
        
        Schedule schedule = new Schedule();
        schedule.setLabel("Schedule Label");
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setInterval("P1D");
        schedule.setExpires("P1D");
        schedule.addTimes("10:00");
        schedule.addActivity(new Activity.Builder().withLabel("label").withTask("taskId").build());
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy(); 
        strategy.setSchedule(schedule);
        
        schedulePlan = new DynamoSchedulePlan();
        schedulePlan.setLabel("Label");
        schedulePlan.setStudyKey(TEST_STUDY.getIdentifier());
        schedulePlan.setMinAppVersion(10);
        schedulePlan.setStrategy(strategy);
        schedulePlan = schedulePlanService.createSchedulePlan(schedulePlan);
    }

    @After
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
        schedulePlanService.deleteSchedulePlan(TEST_STUDY, schedulePlan.getGuid());
        if (testUser != null) {
            helper.deleteUser(study, testUser.getEmail());
        }
    }
    
    @Test
    public void retrievalTasksAcrossTimeAndTimeZones() throws Exception {
        // We start this test in the early morning in Russia, in the future so the new user's
        // enrollment doesn't screw up the test.
        int year = DateTime.now().getYear();
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse((year+1)+"-09-23T03:39:57.779+03:00").getMillis());

        // These time zones are far apart and for our chosen time, Dave will be teleporting to the 
        // previous day. Our scheduler must do something rational.
        DateTimeZone MSK = DateTimeZone.forOffsetHours(3);
        DateTimeZone PST = DateTimeZone.forOffsetHours(-7);

        // Anticipated schedule times in Russia (exact seconds not important)
        String msk0 = DateTime.now(MSK).minusDays(1).toLocalDate().toString(); // this is yesterdays task, not expired yet 
        String msk1 = DateTime.now(MSK).toLocalDate().toString();
        String msk2 = DateTime.now(MSK).plusDays(1).toLocalDate().toString();
        String msk3 = DateTime.now(MSK).plusDays(2).toLocalDate().toString();
        String msk4 = DateTime.now(MSK).plusDays(3).toLocalDate().toString();
        
        // Anticipated schedule times in California (exact seconds not important)
        String pst1 = DateTime.now(PST).toLocalDate().toString();
        String pst2 = DateTime.now(PST).plusDays(1).toLocalDate().toString();
        String pst3 = DateTime.now(PST).plusDays(2).toLocalDate().toString();
        String pst4 = DateTime.now(PST).plusDays(3).toLocalDate().toString();
        
        // Hi, I'm dave, I'm in Moscow, what am I supposed to do for the next two days?
        // You get the schedule from yesterday that hasn't expired just yet (22nd), plus the 
        // 23rd, 24th and 25th
        ScheduleContext context = getContextWith2DayWindow(MSK);
        List<Task> tasks = service.getTasks(testUser.getUser(), context);
        assertEquals(4, tasks.size());
        assertEquals(msk0+"T10:00:00.000+03:00", tasks.get(0).getScheduledOn().toString());
        assertEquals(msk1+"T10:00:00.000+03:00", tasks.get(1).getScheduledOn().toString());
        assertEquals(msk2+"T10:00:00.000+03:00", tasks.get(2).getScheduledOn().toString());
        assertEquals(msk3+"T10:00:00.000+03:00", tasks.get(3).getScheduledOn().toString());
        
        // Dave teleports to California, where it's still the prior day. He gets 4 tasks 
        // (yesterday, today in Russia, tomorrow and the next day). One task was created beyond
        // the window, over in Moscow... that is not returned because although it exists, we 
        // filter it out from the persisted tasks retrieved from the db.
        tasks = service.getTasks(testUser.getUser(), getContextWith2DayWindow(PST));
        assertEquals(3, tasks.size());
        assertEquals(pst1+"T10:00:00.000-07:00", tasks.get(0).getScheduledOn().toString());
        assertEquals(pst2+"T10:00:00.000-07:00", tasks.get(1).getScheduledOn().toString());
        assertEquals(pst3+"T10:00:00.000-07:00", tasks.get(2).getScheduledOn().toString());
        
        // Dave returns to the Moscow and we move time forward a day.
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse((year+1)+"-09-24T03:39:57.779+03:00").getMillis());
        
        // He hasn't finished any tasks. The 22nd expires but it's too early in the day 
        // for the 23rd to expire (earlier than 10am), so, 4 tasks, but with different dates.
        tasks = service.getTasks(testUser.getUser(), getContextWith2DayWindow(MSK));
        assertEquals(4, tasks.size());
        assertEquals(msk1+"T10:00:00.000+03:00", tasks.get(0).getScheduledOn().toString());
        assertEquals(msk2+"T10:00:00.000+03:00", tasks.get(1).getScheduledOn().toString());
        assertEquals(msk3+"T10:00:00.000+03:00", tasks.get(2).getScheduledOn().toString());
        assertEquals(msk4+"T10:00:00.000+03:00", tasks.get(3).getScheduledOn().toString());
        
        // Dave, please finish some tasks... 
        tasks.get(0).setFinishedOn(DateTime.now().getMillis());
        tasks.get(1).setFinishedOn(DateTime.now().getMillis());
        service.updateTasks(testUser.getUser().getHealthCode(), tasks);
        
        // This is easy, Dave has the later tasks and that's it, at this point.
        tasks = service.getTasks(testUser.getUser(), getContextWith2DayWindow(MSK));
        assertEquals(2, tasks.size());
        assertEquals(msk3+"T10:00:00.000+03:00", tasks.get(0).getScheduledOn().toString());
        assertEquals(msk4+"T10:00:00.000+03:00", tasks.get(1).getScheduledOn().toString());
    }
    
    @Test
    public void tasksAreFilteredBasedOnAppVersion() throws Exception {
        ScheduleContext context = new ScheduleContext.Builder()
                .withContext(getContextWith2DayWindow(DateTimeZone.UTC))
                .withClientInfo(ClientInfo.fromUserAgentCache("app/5")).build();
        
        // Ask for version 5, nothing is created
        List<Task> tasks = service.getTasks(testUser.getUser(), context);
        assertEquals(0, tasks.size());
        
        // Ask for version 11, normal tasks are created.
        context = new ScheduleContext.Builder()
                .withContext(context)
                .withClientInfo(ClientInfo.fromUserAgentCache("app/11")).build();
        tasks = service.getTasks(testUser.getUser(), context);
        assertEquals(3, tasks.size());
    }
    
    @Test
    public void persistedTasksAreFilteredByEndsOn() throws Exception {
        // This was demonstrated above, but by only one task... this is a more exaggerated test
        
        // Four days...
        DateTime endsOn = DateTime.now().plusDays(4);
        ScheduleContext context = getContext(DateTimeZone.UTC, endsOn);
        List<Task> tasks = service.getTasks(testUser.getUser(), context);
        
        // Zero days... there are fewer tasks
        endsOn = DateTime.now().plusDays(0);
        context = getContext(DateTimeZone.UTC, endsOn);
        List<Task> tasks2 = service.getTasks(testUser.getUser(), context);
        
        assertTrue(tasks2.size() < tasks.size());
    }
    
    private ScheduleContext getContextWith2DayWindow(DateTimeZone zone) {
        return getContext(zone, DateTime.now(zone).plusDays(2));
    }
    
    private ScheduleContext getContext(DateTimeZone zone, DateTime endsOn) {
        // Setting the endsOn value to the end of the day, as we do in the controller.
        return new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
            .withTimeZone(zone)
            // Setting the endsOn value to the end of the day, as we do in the controller.
            .withEndsOn(endsOn.withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59))
            .withHealthCode(testUser.getUser().getHealthCode()).build();
    }
}
