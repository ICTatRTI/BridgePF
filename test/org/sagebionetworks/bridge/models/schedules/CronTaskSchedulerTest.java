package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.asDT;
import static org.sagebionetworks.bridge.models.schedules.ScheduleTestUtils.assertDates;
import static org.sagebionetworks.bridge.models.schedules.ScheduleType.ONCE;
import static org.sagebionetworks.bridge.models.schedules.ScheduleType.RECURRING;
import static org.sagebionetworks.bridge.TestConstants.TEST_3_ACTIVITY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;

import com.google.common.collect.Maps;

/**
 * Tests for the cron scheduler. Most details of the schedule object have been tested in 
 * the IntervalTaskSchedulerTask class, here we're testing some of the specifics of 
 * the cron schedules.  
 */
public class CronTaskSchedulerTest {
    
    private Map<String, DateTime> events;
    private List<Task> tasks;
    private SchedulePlan plan = new DynamoSchedulePlan();
    
    private DateTime ENROLLMENT = DateTime.parse("2015-03-23T10:00:00Z");
    
    @Before
    public void before() {
        plan.setGuid("BBB");
        
        events = Maps.newHashMap();
        // Enrolled on March 23, 2015 @ 10am GST
        events.put("enrollment", ENROLLMENT);
    }
    
    @Test
    public void onceCronScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);

        tasks = schedule.getScheduler().getTasks(plan, getContext(ENROLLMENT.plusWeeks(1)));
        assertDates(tasks, "2015-03-25 09:15");
    }
    @Test
    public void onceStartsOnCronScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setStartsOn(asDT("2015-03-31 00:00"));
        
        tasks = schedule.getScheduler().getTasks(plan, getContext(ENROLLMENT.plusWeeks(2)));
        assertEquals(0, tasks.size());
    }
    @Test
    public void onceEndsOnCronScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setEndsOn(asDT("2015-03-31 00:00"));
        
        tasks = schedule.getScheduler().getTasks(plan, getContext(ENROLLMENT.plusWeeks(2)));
        assertDates(tasks, "2015-03-25 09:15");
    }
    @Test
    public void onceStartEndsOnCronScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setStartsOn(asDT("2015-03-23 00:00"));
        schedule.setEndsOn(asDT("2015-03-31 00:00"));
        
        tasks = schedule.getScheduler().getTasks(plan, getContext(ENROLLMENT.plusWeeks(2)));
        assertDates(tasks, "2015-03-25 09:15");
    }
    @Test
    public void onceDelayCronScheduleWorks() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setDelay("P2D");
        
        tasks = schedule.getScheduler().getTasks(plan, getContext(ENROLLMENT.plusWeeks(2)));
        assertDates(tasks, "2015-03-28 09:15");
    }
    @Test
    public void recurringCronScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        
        tasks = schedule.getScheduler().getTasks(plan, getContext(ENROLLMENT.plusWeeks(3)));
        assertDates(tasks, "2015-03-25 09:15", "2015-03-28 09:15", 
            "2015-04-01 09:15", "2015-04-04 09:15", "2015-04-08 09:15", "2015-04-11 09:15");
    }
    @Test
    public void recurringEndsOnCronScheduleWorks() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setEndsOn(asDT("2015-03-31 00:00"));
        
        tasks = schedule.getScheduler().getTasks(plan, getContext(ENROLLMENT.plusWeeks(2)));
        assertDates(tasks, "2015-03-25 09:15", "2015-03-28 9:15");
    }
    @Test
    public void onceCronScheduleFiresMultipleTimesPerDay() {
        Schedule schedule = createScheduleWith(ONCE);
        schedule.setCronTrigger("0 0 10,13,20 ? * MON-FRI *");
        
        tasks = schedule.getScheduler().getTasks(plan, getContext(ENROLLMENT.plusWeeks(1)));
        assertDates(tasks, "2015-03-23 13:00");
        
    }
    @Test
    public void rercurringCronScheduleFiresMultipleTimesPerDay() {
        Schedule schedule = createScheduleWith(RECURRING);
        schedule.setCronTrigger("0 0 10,13,20 ? * MON-FRI *");
        
        tasks = schedule.getScheduler().getTasks(plan, getContext(ENROLLMENT.plusDays(2)));
        assertDates(tasks, "2015-03-23 13:00", "2015-03-23 20:00", "2015-03-24 10:00", "2015-03-24 13:00", "2015-03-24 20:00");
    }
    
    private ScheduleContext getContext(DateTime endsOn) {
        return new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withTimeZone(DateTimeZone.UTC)
            .withEndsOn(endsOn)
            .withEvents(events).build();
    }
    
    private Schedule createScheduleWith(ScheduleType type) {
        Schedule schedule = new Schedule();
        // Wed. and Sat. at 9:15am
        schedule.setCronTrigger("0 15 9 ? * WED,SAT *");
        schedule.getActivities().add(TEST_3_ACTIVITY);
        schedule.setScheduleType(type);
        return schedule;
    }
    
}
