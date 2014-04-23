/**
 * This functionality is fairly complex; pulled out and tested separately.
 */
describe("ApplicationController authentication support", function() {
	
	var ApplicationController, SessionService, $rootScope, $httpBackend;

	beforeEach(function() {
		module('bridge');
		
		$window = {
			location: { replace: jasmine.createSpy()} 
		};
		$humane = {
	        confirm: jasmine.createSpy(),
	        error: jasmine.createSpy()
		}
		$location = {
	        path: jasmine.createSpy()
		}
		module(function($provide) {
			$provide.value('$window', $window);
			$provide.value('$humane', $humane);
			$provide.value('$location', $location);
		});
	});
	
	beforeEach(inject(function($injector) {
        $httpBackend = $injector.get('$httpBackend');
        $rootScope = $injector.get('$rootScope');
        SessionService = $injector.get("SessionService");
        var $controller = $injector.get('$controller');
        createController = function() {
        	return $controller('ApplicationController', {'$scope' : $rootScope });
        };
	}));	
    afterEach(function() {
    	$httpBackend.verifyNoOutstandingExpectation();
    	$httpBackend.verifyNoOutstandingRequest();
    });	
	function expectNotLoggedIn() {
		expect(SessionService.authenticated).toEqual(false);
		expect(SessionService.username).toEqual("");
		expect(SessionService.sessionToken).toEqual("");
	}
	
	it('calls the sign in service on a sign in', function() {
		$httpBackend.expectPOST('/api/auth/signIn').respond({
			"type":"org.sagebionetworks.repo.model.auth.Session",
			"payload":{sessionToken: "someToken", username: "test2", authenticated: true}
		});
		ApplicationController = createController();
		$rootScope.credentials = { "username": "test2", "password": "password" };
		$rootScope.signIn();
		$httpBackend.flush();
		
		expect(SessionService.authenticated).toEqual(true);
		expect(SessionService.username).toEqual("test2");
		expect(SessionService.sessionToken).toEqual("someToken");
	});
	it("does not authenticate user with bad credentials", function() {
		$httpBackend.expectPOST('/api/auth/signIn').respond(404, {payload: "Wrong user name or password."});
		ApplicationController = createController();
		$rootScope.credentials = { "username": "asdf", "password": "asdf" };
		$rootScope.signIn();
		$httpBackend.flush();
		expect($humane.error).toHaveBeenCalledWith("Wrong user name or password.");
		expectNotLoggedIn();
	});
	it("redirects to the consent page when TOS hasn't been signed", function() {
		$httpBackend.expectPOST('/api/auth/signIn').respond(412, {sessionToken: "abc"});
		ApplicationController = createController();
		$rootScope.credentials = { "username": "asdf", "password": "asdf" };
		$rootScope.signIn();
		$httpBackend.flush();
		expect($location.path).toHaveBeenCalledWith("/consent/abc");
		expectNotLoggedIn();
	});
	it('calls the sign out service on a sign out', function() {
		$httpBackend.expectGET('/api/auth/signOut').respond({type: "StatusMessage", message: "Signed Out"});
		ApplicationController = createController();
		$rootScope.signOut();
		$httpBackend.flush();
		expect($window.location.replace).toHaveBeenCalledWith('/');
	});
	it("clears the password in credentials after sign-in", function() {
		$httpBackend.expectPOST('/api/auth/signIn').respond(404, {});
		ApplicationController = createController();
		$rootScope.credentials = { "username": "asdf", "password": "asdf" };
		$rootScope.signIn();
		$httpBackend.flush();
		expect($rootScope.credentials.password).toEqual("");
		expectNotLoggedIn();
	});
});