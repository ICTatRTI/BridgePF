package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;

public class ScheduleValidatorTest {

    private Schedule schedule;
    ScheduleValidator validator;
    
    @Before
    public void before() {
        schedule = new Schedule();
        validator = new ScheduleValidator();
    }
    
    @Test
    public void mustHaveAtLeastOneActivity() {
        try {
            Validate.entityThrowingException(validator, schedule);
        } catch(InvalidEntityException e) {
            assertEquals(1, e.getErrors().get("activities").size());
        }
    }
    
    @Test
    public void activityMustBeFullyInitialized() {
        Activity activity = new Activity(null, null);
        
        schedule.addActivity(activity);
        
        try {
            Validate.entityThrowingException(validator, schedule);
        } catch(InvalidEntityException e) {
            assertEquals("scheduleType cannot be null", e.getErrors().get("scheduleType").get(0));
            assertEquals("activities[0].activityType cannot be null", e.getErrors().get("activities[0].activityType").get(0));
            assertEquals("activities[0].ref cannot be missing, null, or blank", e.getErrors().get("activities[0].ref").get(0));
        }
    }
    
    @Test
    public void datesMustBeChronologicallyOrdered() {
        // make it valid except for the dates....
        schedule.addActivity(new Activity("Label", "task:AAA"));
        schedule.setScheduleType(ScheduleType.ONCE);
        
        long startsOn = DateUtils.getCurrentMillisFromEpoch();
        long endsOn = startsOn + 1; // should be at least an hour later
        
        schedule.setStartsOn(startsOn);
        schedule.setEndsOn(endsOn);
        
        try {
            Validate.entityThrowingException(validator, schedule);
        } catch(InvalidEntityException e) {
            assertEquals("endsOn should be at least an hour after the startsOn time", e.getErrors().get("endsOn").get(0));
        }
        
        endsOn = startsOn + (60 * 60 * 1000);
        schedule.setEndsOn(endsOn);
        Validate.entityThrowingException(validator, schedule);
    }
    
}
