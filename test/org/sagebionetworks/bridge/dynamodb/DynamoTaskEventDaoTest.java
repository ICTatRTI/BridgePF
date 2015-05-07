package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.dao.TaskEventDao;
import org.sagebionetworks.bridge.models.tasks.TaskEvent;
import org.sagebionetworks.bridge.models.tasks.TaskEventType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoTaskEventDaoTest {

    @Resource
    TaskEventDao taskEventDao;
    
    @Before
    public void before() {
        DynamoInitializer.init(DynamoTaskEvent.class);
        DynamoTestUtil.clearTable(DynamoTaskEvent.class);
    }
    
    @Test
    public void canCrudEvent() {
        DateTime time1 = DateTime.now();
        DateTime time2 = time1.plusDays(1);
        
        TaskEvent event = new DynamoTaskEvent.Builder().withHealthCode("BBB").withType(TaskEventType.ENROLLMENT).withTimestamp(time1).build();
        taskEventDao.publishEvent(event);
        
        Map<String,DateTime> map = taskEventDao.getTaskEventMap("BBB");
        assertEquals(1, map.size());
        assertEquals(time1, map.get("enrollment"));
        
        event = new DynamoTaskEvent.Builder().withHealthCode("BBB").withType(TaskEventType.ENROLLMENT).withTimestamp(time2).build();
        taskEventDao.publishEvent(event);
        
        map = taskEventDao.getTaskEventMap("BBB");
        assertEquals(time2, map.get("enrollment"));

        taskEventDao.deleteTaskEvents("BBB");
        
        map = taskEventDao.getTaskEventMap("BBB");
        assertEquals(0, map.size());
    }
    
}
