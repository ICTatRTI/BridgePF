package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestConstants.TestUser;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Email;
import org.sagebionetworks.bridge.models.PasswordReset;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountCriteria;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.account.Accounts;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.Directory;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class AuthenticationServiceImplTest {

    @Resource
    AuthenticationServiceImpl authService;
    
    @Resource
    TestUserAdminHelper helper;

    @Resource
    Client stormpathClient;
    
    private static final int NUMBER_OF_TESTS = 12;
    
    private boolean setUpComplete = false;
    private int testCount = 0;
    
    @Before
    public void before() {
        if (!setUpComplete) {
            helper.createOneUser();
            setUpComplete = true;
        }
    }
    
    @After
    public void after() {
        testCount++;
        if (testCount == NUMBER_OF_TESTS) {
            helper.deleteOneUser();
        }
    }

    @Test(expected = BridgeServiceException.class)
    public void signInNoUsername() throws Exception {
        authService.signIn(helper.getStudy(), new SignIn(null, "bar"));
    }

    @Test(expected = BridgeServiceException.class)
    public void signInNoPassword() throws Exception {
        authService.signIn(helper.getStudy(), new SignIn("foo", null));
    }

    @Test(expected = EntityNotFoundException.class)
    public void signInInvalidCredentials() throws Exception {
        authService.signIn(helper.getStudy(), new SignIn("foo", "bar"));
    }

    @Test
    public void signInCorrectCredentials() throws Exception {
        UserSession session = authService.getSession(helper.getUserSessionToken());

        assertEquals("Username is for test2 user", helper.getUser().getUsername(), session.getUser().getUsername());
        assertTrue("Session token has been assigned", StringUtils.isNotBlank(session.getSessionToken()));
    }

    @Test
    public void signInWhenSignedIn() throws Exception {
        UserSession session = authService.signIn(helper.getStudy(), helper.getUserSignIn());
        assertEquals("Username is for test2 user", helper.getTestUser().getUsername(), session.getUser().getUsername());
    }

    @Test
    public void getSessionWithBogusSessionToken() throws Exception {
        UserSession session = authService.getSession("foo");
        assertNull("Session is null", session);

        session = authService.getSession(null);
        assertNull("Session is null", session);
    }

    @Test
    public void getSessionWhenAuthenticated() throws Exception {
        UserSession session = authService.getSession(helper.getUserSessionToken());

        assertEquals("Username is for test2 user", helper.getTestUser().getUsername(), session.getUser().getUsername());
        assertTrue("Session token has been assigned", StringUtils.isNotBlank(session.getSessionToken()));
    }

    @Test(expected = BridgeServiceException.class)
    public void requestPasswordResetFailsOnNull() throws Exception {
        authService.requestResetPassword(null);
    }

    @Test(expected = BridgeServiceException.class)
    public void requestPasswordResetFailsOnEmptyString() throws Exception {
        Email email = new Email("");
        authService.requestResetPassword(email);
    }

    @Test(expected = BridgeServiceException.class)
    public void resetPasswordWithBadTokenFails() throws Exception {
        authService.resetPassword(new PasswordReset("foo", "newpassword"));
    }

    @Test
    public void unconsentedUserMustSignTOU() throws Exception {
        try {
            // Create a user who has not consented.
            TestUser user = new TestUser("authTestUser", "authTestUser@sagebridge.org", "P4ssword");
            helper.createUser(user, null, false, false);
            authService.signIn(helper.getStudy(), user.getSignIn());
            fail("Should have thrown consent exception");
        } catch(ConsentRequiredException e) {
            helper.deleteUser(e.getUserSession().getSessionToken(), e.getUserSession().getUser());
        }
    }
    
    @Test
    public void createUserInNonDefaultAccountStore() {
        TestUser nonDefaultUser = new TestUser("secondStudyUser", "secondStudyUser@sagebridge.org", "P4ssword");
        try {
            Study defaultStudy = helper.getStudy();
            authService.signUp(nonDefaultUser.getSignUp(), TestConstants.SECOND_STUDY);

            // Should have been saved to this account store, not the default account store.
            Directory directory = stormpathClient.getResource(TestConstants.SECOND_STUDY.getStormpathDirectoryHref(),
                    Directory.class);
            assertTrue("Account is in store", isInStore(directory, nonDefaultUser.getSignUp()));
            
            directory = stormpathClient.getResource(defaultStudy.getStormpathDirectoryHref(), Directory.class);
            assertFalse("Account is not in store", isInStore(directory, nonDefaultUser.getSignUp()));
        } finally {
            deleteAccount(nonDefaultUser);
        }
    }

    private void deleteAccount(TestUser user) {
        Application app = StormpathFactory.createStormpathApplication(stormpathClient);
        AccountCriteria criteria = Accounts.where(Accounts.email().eqIgnoreCase(user.getEmail()));
        AccountList accounts = app.getAccounts(criteria);
        for (Account account : accounts) {
            account.delete();
        }
    }
    
    private boolean isInStore(Directory directory, SignUp signUp) {
        for (Account account : directory.getAccounts()) {
            if (account.getEmail().equals(signUp.getEmail())) {
                return true;
            }
        }
        return false;
    }
}
