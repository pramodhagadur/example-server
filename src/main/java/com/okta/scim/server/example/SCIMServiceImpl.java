package com.okta.scim.server.example;

import com.okta.scim.server.capabilities.UserManagementCapabilities;
import com.okta.scim.server.exception.DuplicateGroupException;
import com.okta.scim.server.exception.EntityNotFoundException;
import com.okta.scim.server.exception.OnPremUserManagementException;
import com.okta.scim.server.service.SCIMOktaConstants;
import com.okta.scim.server.service.SCIMService;
import com.okta.scim.util.model.Email;
import com.okta.scim.util.model.Membership;
import com.okta.scim.util.model.Name;
import com.okta.scim.util.model.PaginationProperties;
import com.okta.scim.util.model.SCIMFilter;
import com.okta.scim.util.model.SCIMFilterType;
import com.okta.scim.util.model.SCIMGroup;
import com.okta.scim.util.model.SCIMGroupQueryResponse;
import com.okta.scim.util.model.SCIMUser;
import com.okta.scim.util.model.SCIMUserQueryResponse;
import org.codehaus.jackson.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An example to show how to integrate with Okta using the SCIM SDK.
 * <p>
 * <b>Note:</b> This class is not performant, concise, or complete.
 * It was written to show how to work with the data model of the SDK.
 * <p>
 * <code>SCIMUser</code> and <code>SCIMGroup</code> objects which are part of the data model of the SDK are used to interact with the SDK.
 * <p>
 * <b>SCIMUser</b>
 * <p>
 * <p>
 * A <code>SCIMUser</code> has a set of standard properties that map to the SCIM Core Schema properties. The standard properties of
 * <code>SCIMUser</code> are converted to and from an Okta AppUser are the userName, password, id (converted into the
 * Okta AppUser's externalId), name (name.familyName and name.givenName), emails (primary and secondary), phone numbers (primary), and status.
 * <p>
 * This is how you can set the standard properties:
 * <ol type="a">
 * <li><code>SCIMUser user = new SCIMUser(); //Create an empty user</code></li>
 * <li><code>user.setUserName("someValue"); //Start setting the values for the standard properties</code></li>
 * <li><code>user.setId("someOtherValue");</code></li>
 * <li><code>user.setActive(true);</code></li>
 * <li><code>Collection&lt;Email&gt; emails = new ArrayList&lt;Email&gt;(); //emails, phone numbers and group memberships are all collections.</code>
 * <p>
 * You need to create a collection like this
 * <pre>
 *    Email email1 = new Email("abc@eample.com", "work", true);
 *    emails.add(email1);</pre>
 * <li><code>user.setEmails(emails); //Set the array emails to the user</code></li>
 * <li><code>Name name = new Name("Mr. John Smith", "Smith", "John"); //Name is a complex property.</code>
 * <li><code> user.setName(name); //Set the name to the user </code></li>
 * </ol>
 *
 * <p>
 * Similarly, get these standard properties as follows:
 * <ol type="a">
 * <li><code>String userName = user.getUserName(); //Get a non-nested simple property like userName, Id, Password, Status, etc.</code></li>
 * <li><code>String firstName = user.getName().getFirstName(); //Get a nested simple property like firstName, lastName </code></li>
 * <li><code>Mail firstEntry = user.getEmails().get(0); //Get an array element</code>
 * <li><code>String firstEmail = firstEntry.getValue(); //Get the value of that array element</code></li>
 * </ol>
 * <p></p>
 * <p>
 * If you need to set properties in addition to the standard properties, you can set them as custom properties in a namespace different from
 * that of the standard properties. The custom namespace is based on the appName and the Universal Directory (UD) schema name you created on Okta. For example, if your appName is
 * <i>onprem_app</i> and the schema name is <i>custom</i>, the custom namespace would be <code>urn:okta:onprem_app:1.0:user:custom</code>.
 * In general, the custom namespace would of the
 * form <code>urn:okta:{appName}:1.0:user:{udSchemaName}</code>. You need to know your appName and the schema name if you want to use the custom namespaces. You can login to Okta
 * and go to your App to find this information.
 * </p>
 *
 * <p>The datatypes supported are <code>String</code>, <code>Boolean</code>, <code>Integer</code>, and <code>Double</code>.</p>
 *
 * <p>There are two ways to get and set CustomProperties. In the examples below, assume that the custom namespace is <code>urn:okta:onprem_app:1.0:user:custom</code>.</p>
 *
 * <ol>
 * <li>Access the customPropertiesMap of <code>SCIMUser</code> which is a CustomSchemaName to JsonNode map for all the custom properties.
 * <p>
 * <i>Example to Set a custom property</i><pre>
 * JsonNode node = new JsonNode(); //Create a Jackson Json Node
 * node.put("customField1", "abc"); //Set string value
 * node.put("customField2", 123);   //Set an int value
 * Map&lt;String, JsonNode&gt; customMap = new HashMap&lt;String, JsonNode&gt;(); //Create a new map to be set to the SCIMUser
 * customMap.put("urn:okta:onprem_app:1.0:user:custom", node); //Put an entry in the map with the URN
 * user.setCustomPropertiesMap(customMap); //Set the custom properties map to the SCIM user
 * </pre>
 * <p>
 * <i>Example to Get a custom property</i><pre>
 * String customField1 = user.getCustomPropertiesMap().get("urn:okta:onprem_app:1.0:user:custom").get("customField1").asText();
 * You need to traverse the Json Node if the custom field is nested.
 * </pre>
 *
 * <li>Use getters and setters &ndash; <code>getCustomStringProperty</code>, <code>getCustomIntProperty</code>, <code>setCustomStringProperty</code>, <code>setCustomIntProperty</code>, etc.
 * <p><i>Example to Set a custom property</i><pre>
 * user.setCustomStringValue("urn:okta:onprem_app:1.0:user:custom", "customField1", "abc"); //Set a root level custom property
 * //Set a nested custom property where the "subRoot" is the parent of "customField1" and "root" is the parent of "subRoot".
 * user.setCustomStringValue("urn:okta:onprem_app:1.0:user:custom", "customField1", "abc", "root", "subRoot");
 * </pre>
 * <p>
 * Similar methods including &ndash; <code>setCustomIntValue</code>, <code>setCustomDoubleValue</code>, <code>setCustomBooleanValue</code> exist for the other data types.
 * <p>
 * <i>Example to Get a custom property</i><pre>
 * user.getCustomStringValue("urn:okta:onprem_app:1.0:user:custom", "customField1"); //Get a root level custom property
 * user.getCustomStringValue("urn:okta:onprem_app:1.0:user:custom", "customField1", "root", "subRoot"); //Get a nested custom property
 * </pre>
 * </ol>
 * <p>
 * <p>
 * <b>SCIMGroup</b>
 * <p>
 * A <code>SCIMGroup</code> has three standard properties. The standard properties of <code>SCIMGroup</code> are converted to and from Okta AppGroup.
 * <ol>
 * <li><code>id</code> - converted to/from the externalId of the Okta AppGroup</li>
 * <li><code>displayName</code> - the display name for the group</li>
 * <li><code>members</code> - the users that are members of the group</li>
 * </ol>
 * This is how you can set the standard properties of <code>SCIMGroup</code>:
 * <ol type="a">
 * <li><code>SCIMGroup group = new SCIMGroup(); //Create an empty group</code></li>
 * <li><code>group.setDisplayName("someValue"); //Set the display name</code></li>
 * <li><code>group.setId("someOtherValue"); //Set the Id</code></li>
 * <li><code>Collection&lt;Membership&gt; groupMemberships = new ArrayList&lt;Membership&gt;(); //Create the collection for group members, detailed below.</code>
 * <pre>
 *    Membership member = new Membership("101", "user displayName"); //Create a member entry for a user whose ID is 101, and displayName is "user displayName"
 *    groupMemberships.add(member); //Add the member
 * </pre></li>
 * <li><code>group.setMembers(groupMemberships); //Set the members for the group</code> </li>
 * </ol>
 *
 * Get these properties from a SCIMGroup, as follows:
 * <pre>
 *    String displayName = group.getDisplayName();
 *    Membership firstMember = group.getMembers().get(0);
 * </pre>
 * <p>
 * <p>
 * A <code>SCIMGroup</code> can also contain custom properties. Currently, only one custom property (<code>description</code>) is supported for an <code>SCIMGroup</code>. Note that the custom namespace
 * for the <code>SCIMGroup</code> is a constant and is not dependent on the appName.</p>
 * <p>
 * Get/Set the description of the <code>SCIMGroup</code>
 * <pre>
 *      group.setCustomStringValue(SCIMOktaConstants.APPGROUP_OKTA_CUSTOM_SCHEMA_URN, SCIMOktaConstants.OKTA_APPGROUP_DESCRIPTION, "Group Description"); //Set the value
 *      String description = group.getCustomStringValue(SCIMOktaConstants.APPGROUP_OKTA_CUSTOM_SCHEMA_URN, SCIMOktaConstants.OKTA_APPGROUP_DESCRIPTION); //Get the value
 * </pre>
 * <p>
 * <p>
 * <b>Exceptions</b>
 * <p>
 * Throw the following three exceptions, as shown in the implementations below, as noted.<p>
 * <ul>
 * <li><code>EntityNotFoundException</code> - If an update/delete/get request is received for /Users and /Groups but the resource with the ID is not found.
 * <li><code>DuplicateGroupException</code> - If we get a create request for /Groups but a duplicate group exists
 * </ul>
 * <b>Note:</b> For both the above exceptions, the SDK associates an error code and a message with the exception automatically.
 * <ul><li><code>OnPremUserManagementException</code> - If there is any other exceptional condition (You cannot access the remote application, remote application
 * threw an exception, etc).</ul>
 * <p>For <code>OnPremUserManagementException</code> you can create the exception with any of the following four different properties.
 * <ul>
 * <li>Internal Code - Any arbitrary code to associate with the exception.</li>
 * <li>Description - Description for the exception.</li>
 * <li>Helper URL - If there is any URL for referral to help fix the issue.</li>
 * <li>Exception - Any associated exception.</li>
 * </ul>
 * <p><strong>Note:</strong> For all exceptions described above, if the code throws the exception while
 * processing a UM operation,&nbsp;the exception is serialized into a json string that is visible in Okta UI,
 * as shown below.
 * <blockquote>
 * "Failed on 11-24-2013 11:15:25PM UTC: Unable to delete Group Push mapping target App group ogroup-1: Error
 * while deleting user group ogroup-1: The Provisioning Agent call to deleteGroup failed. Error code: 404, error: Not Found.
 * Errors reported by the connector : {"errors":[{"statusCode":404,"internalCode":"E0000048","description":"Cannot delete the group - Cannot find the group with the id [1004]","help_url":null}]}"
 * </blockquote>
 * <p>If you want to store/read users/groups from files, specify the absolute paths for the files in dispatcher-servlet.xml</p>
 *
 * @author rpamidimarri
 */
public class SCIMServiceImpl implements SCIMService {
    //Absolute path for users.json set in the dispatcher-servlet.xml
    private String usersFilePath;
    //Absolute path for groups.json set in the dispatcher-servlet.xml
    private String groupsFilePath;
    //Field names for the custom properties
    private static final String CUSTOM_SCHEMA_PROPERTY_IS_ADMIN = "isAdmin";
    private static final String CUSTOM_SCHEMA_PROPERTY_IS_OKTA = "isOkta";
    private static final String CUSTOM_SCHEMA_PROPERTY_DEPARTMENT_NAME = "departmentName";
    //This should be the name of the App you created. On the Okta URL for the App, you can find this name
    private static final String APP_NAME = "onprem_app";
    //This should be the name of the Universal Directory schema you created. We are assuming this name is "custom"
    private static final String UD_SCHEMA_NAME = "custom";
    private Map<String, SCIMUser> userMap = new HashMap<String, SCIMUser>();
    private Map<String, SCIMGroup> groupMap = new HashMap<String, SCIMGroup>();
    private int nextUserId;
    private int nextGroupId;
    private String userCustomUrn;
    private boolean useFilePersistence = false;

    private static final String USER_RESOURCE = "user";
    private static final String GROUP_RESOURCE = "group";

    private static final Logger LOGGER = LoggerFactory.getLogger(SCIMServiceImpl.class);

    @PostConstruct
    public void afterCreation() throws Exception {
        userCustomUrn = SCIMOktaConstants.CUSTOM_URN_PREFIX + APP_NAME + SCIMOktaConstants.CUSTOM_URN_SUFFIX + UD_SCHEMA_NAME;
        initPersistence();
        if (useFilePersistence) {
            updateCache();
            return;
        }

        userMap.clear();
        groupMap.clear();

        //Create an empty group
        SCIMGroup group1 = new SCIMGroup();
        //Set the values for the standard properties - DisplayName and the Id
        group1.setDisplayName("firstGroup");
        group1.setId("1001");
        //Create members of the first group
        Collection<Membership> firstGroupMembers = new ArrayList<Membership>();
        //Create a group member with the id = 101
        Membership firstGroupMember1 = new Membership("101", "okta");
        firstGroupMembers.add(firstGroupMember1);
        //Set the members of the first group
        group1.setMembers(firstGroupMembers);
        //Set the description to the APPGROUP_OKTA_CUSTOM_SCHEMA_URN - Okta's Custom Group URN
        //If you set it, Okta's representation of the App Group will get the description
        group1.setCustomStringValue(SCIMOktaConstants.APPGROUP_OKTA_CUSTOM_SCHEMA_URN, SCIMOktaConstants.OKTA_APPGROUP_DESCRIPTION, "This is the first group");

        //Create another group in the same way as above
        SCIMGroup group2 = new SCIMGroup();
        group2.setDisplayName("secondGroup");
        group2.setId("1002");
        //Members of the second group
        Collection<Membership> secondGroupMembers = new ArrayList<Membership>();
        Membership secondGroupMember1 = new Membership("101", "okta");
        secondGroupMembers.add(secondGroupMember1);
        //Set the members of the group
        group2.setMembers(secondGroupMembers);
        Membership secondGroupMember2 = new Membership("102", "admin");
        group2.getMembers().add(secondGroupMember2);

        groupMap.put("1001", group1);
        groupMap.put("1002", group2);
        nextGroupId = 1003;

        //Create group memberships to be set to the user
        Membership membership1 = new Membership(group1.getId(), group1.getDisplayName());
        Membership membership2 = new Membership(group2.getId(), group2.getDisplayName());

        //Create an empty user
        SCIMUser user1 = new SCIMUser();
        //Set the standard properties of the user
        user1.setId("101");
        user1.setUserName("okta");
        //Name is a property with sub properties (Like firstName, lastName, etc.)
        user1.setName(new Name("John Smith", "Smith", "John"));
        //Emails is a Collection
        user1.setEmails(Arrays.asList(new Email("jsmith@example.com", "work", true)));
        user1.setActive(true);
        user1.setPassword("inSecure");
        //Set the custom properties, if any
        //NOTE - Currently only a Single Custom URN is supported for SCIMUser. You can build the schema name as shown below. You need to replace APP_NAME with the actual name of the app.
        //Custom boolean property
        user1.setCustomBooleanValue(userCustomUrn, CUSTOM_SCHEMA_PROPERTY_IS_ADMIN, false);
        user1.setCustomBooleanValue(userCustomUrn, CUSTOM_SCHEMA_PROPERTY_IS_OKTA, true);
        //Custom string property
        user1.setCustomStringValue(userCustomUrn, CUSTOM_SCHEMA_PROPERTY_DEPARTMENT_NAME, "Cloud Service");
        //Set the groups
        user1.setGroups(Arrays.asList(membership1, membership2));

        //Create another SCIM User in the same way as above
        SCIMUser user2 = new SCIMUser();
        user2.setId("102");
        user2.setUserName("admin");
        user2.setName(new Name("Barbara Jensen", "Jensen", "Barbara"));
        user2.setEmails(Arrays.asList(new Email("bjensen@example.com", "work", true)));
        user2.setActive(false);
        user2.setPassword("god");
        user2.setCustomBooleanValue(userCustomUrn, CUSTOM_SCHEMA_PROPERTY_IS_ADMIN, true);
        user2.setCustomBooleanValue(userCustomUrn, CUSTOM_SCHEMA_PROPERTY_IS_OKTA, false);
        user2.setCustomStringValue(userCustomUrn, CUSTOM_SCHEMA_PROPERTY_DEPARTMENT_NAME, "Administration");
        user2.setGroups(Arrays.asList(membership2));

        
        //Create another SCIM User in the same way as above
        SCIMUser user3 = new SCIMUser();
        user3.setId("102");
        user3.setUserName("test1");
        user3.setName(new Name("test1 test", "test1", "test"));
        user3.setEmails(Arrays.asList(new Email("test1@example.com", "work", true)));
        user3.setActive(false);
        user3.setPassword("god");
        user3.setCustomBooleanValue(userCustomUrn, CUSTOM_SCHEMA_PROPERTY_IS_ADMIN, true);
        user3.setCustomBooleanValue(userCustomUrn, CUSTOM_SCHEMA_PROPERTY_IS_OKTA, false);
        user3.setCustomStringValue(userCustomUrn, CUSTOM_SCHEMA_PROPERTY_DEPARTMENT_NAME, "Administration");
        user3.setGroups(Arrays.asList(membership2));

        
        
        userMap.put("101", user1);
        userMap.put("102", user2);
        userMap.put("103", user3);
        nextUserId = 104;

        save();
    }

    private void initPersistence() throws Exception {
        //Both the usersFilePath and groupsFilePath should be present to consider to use the files to read/write.
        if (!StringUtils.isEmpty(usersFilePath) && !StringUtils.isEmpty(groupsFilePath)) {
            File userFile = new File(usersFilePath);
            if (!userFile.exists()) {
                LOGGER.error("Cannot find the users file [" + usersFilePath + "]");
                return;
            }
            //Make sure the customer did not provide a relative path, in which case the WEB-INF/classes/... json file is loaded.
            if (!userFile.getAbsolutePath().equals(usersFilePath)) {
                LOGGER.error("The absolute path of the users file is not [" + usersFilePath + "]");
                return;
            }


            File groupFile = new File(groupsFilePath);
            if (!groupFile.exists()) {
                LOGGER.error("Cannot find the groups file [" + groupsFilePath + "]");
                return;
            }
            //Make sure the customer did not provide a relative path, in which case the WEB-INF/classes/... json file is loaded.
            if (!groupFile.getAbsolutePath().equals(groupsFilePath)) {
                LOGGER.error("The absolute path of the groups file is not [" + groupsFilePath + "]");
                return;
            }

            //If there are valid users.json and groups.json
            useFilePersistence = true;
        }
    }

    public String getUsersFilePath() {
        return usersFilePath;
    }

    public void setUsersFilePath(String usersFilePath) {
        this.usersFilePath = usersFilePath;
    }

    public String getGroupsFilePath() {
        return groupsFilePath;
    }

    public void setGroupsFilePath(String groupsFilePath) {
        this.groupsFilePath = groupsFilePath;
    }

    /**
     * This method creates a user. All the standard attributes of the SCIM User can be retrieved by using the
     * getters on the SCIMStandardUser member of the SCIMUser object.
     * <p>
     * If there are custom schemas in the SCIMUser input, you can retrieve them by providing the name of the
     * custom property. (Example : SCIMUser.getStringCustomProperty("schemaName", "customFieldName")), if the
     * property of string type.
     * <p>
     * This method is invoked when a POST is made to /Users with a SCIM payload representing a user
     * to be created.
     * <p>
     * NOTE: While the user's group memberships will be populated by Okta, according to the SCIM Spec
     * (http://www.simplecloud.info/specs/draft-scim-core-schema-01.html#anchor4) that information should be
     * considered read-only. Group memberships should only be updated through calls to createGroup or updateGroup.
     *
     * @param user SCIMUser representation of the SCIM String payload sent by the SCIM client.
     * @return the created SCIMUser.
     * @throws OnPremUserManagementException
     */
    @Override
    public SCIMUser createUser(SCIMUser user) throws OnPremUserManagementException {
        String id = generateNextId(USER_RESOURCE);
        user.setId(id);

        /**
         * Below is an example to show how to deal with exceptional conditions while writing the connector.
         * If you cannot complete the UserManagement operation on the on premises
         * application because of any error/exception, you should throw the OnPremUserManagementException as shown below.
         * <b>Note:</b> You can throw this exception from all the CRUD (Create/Retrieve/Update/Delete) operations defined on
         * Users/Groups in the SCIM interface.
         */
        if (userMap == null) {
            //Note that the Error Code "o01234" is arbitrary - You can use any code that you want to.
            //You can specify a url which has the documentation/information to help figure out the issue.
            //You can also specify an underlying exception (null in the example below)
            throw new OnPremUserManagementException("o01234", "Cannot create the user. The userMap is null", "http://some-help-url", null);
        }

        userMap.put(user.getId(), user);
        save();
        return user;
    }

    /**
     * This method updates a user.
     * <p>
     * This method is invoked when a PUT is made to /Users/{id} with the SCIM payload representing a user to
     * be updated.
     * <p>
     * NOTE: While the user's group memberships will be populated by Okta, according to the SCIM Spec
     * (http://www.simplecloud.info/specs/draft-scim-core-schema-01.html#anchor4) that information should be
     * considered read-only. Group memberships should only be updated through calls to createGroup or updateGroup.
     *
     * @param id   the id of the SCIM user.
     * @param user SCIMUser representation of the SCIM String payload sent by the SCIM client.
     * @return the updated SCIMUser.
     * @throws OnPremUserManagementException
     */
    @Override
    public SCIMUser updateUser(String id, SCIMUser user) throws OnPremUserManagementException, EntityNotFoundException {
        /**
         * Below is an example to show how to deal with exceptional conditions while writing the connector.
         * If you cannot complete the UserManagement operation on the on premises
         * application because of any error/exception, you should throw the OnPremUserManagementException as shown below
         * <b>Note:</b> You can throw this exception from all the CRUD (Create/Retrieve/Update/Delete) operations defined on
         * Users/Groups in the SCIM interface.
         */
        if (userMap == null) {
            //Note that the Error Code "o12345" is arbitrary - You can use any code that you want to.
            throw new OnPremUserManagementException("o12345", "Cannot update the user. The userMap is null");
        }

        SCIMUser existingUser = userMap.get(id);
        if (existingUser != null) {
            userMap.put(id, user);
            save();
            return user;
        } else {
            throw new EntityNotFoundException();
        }
    }

    /**
     * Get all the users.
     * <p>
     * This method is invoked when a GET is made to /Users
     * In order to support pagination (So that the client and the server are not overwhelmed), this method supports querying based on a start index and the
     * maximum number of results expected by the client. The implementation is responsible for maintaining indices for the SCIM Users.
     *
     * @param pageProperties denotes the pagination properties
     * @param filter         denotes the filter
     * @return the response from the server, which contains a list of  users along with the total number of results, start index and the items per page
     * @throws com.okta.scim.server.exception.OnPremUserManagementException
     *
     */
    @Override
    public SCIMUserQueryResponse getUsers(PaginationProperties pageProperties, SCIMFilter filter) throws OnPremUserManagementException {
        List<SCIMUser> users = null;
        if (filter != null) {
            //Get users based on a filter
            users = getUserByFilter(filter);
            //Example to show how to construct a SCIMUserQueryResponse and how to set stuff.
            SCIMUserQueryResponse response = new SCIMUserQueryResponse();
            //The total results in this case is set to the number of users. But it may be possible that
            //there are more results than what is being returned => totalResults > users.size();
            response.setTotalResults(users.size());
            //Actual results which need to be returned
            response.setScimUsers(users);
            //The input has some page properties => Set the start index.
            if (pageProperties != null) {
                response.setStartIndex(pageProperties.getStartIndex());
            }
            return response;
        } else {
            return getUsers(pageProperties);
        }
    }

    private SCIMUserQueryResponse getUsers(PaginationProperties pageProperties) {
        SCIMUserQueryResponse response = new SCIMUserQueryResponse();
        /**
         * Below is an example to show how to deal with exceptional conditions while writing the connector.
         * If you cannot complete the UserManagement operation on the on premises
         * application because of any error/exception, you should throw the OnPremUserManagementException as shown below.
         * <b>Note:</b> You can throw this exception from all the CRUD (Create/Retrieve/Update/Delete) operations defined on
         * Users/Groups in the SCIM interface.
         */
        if (userMap == null) {
            //Note that the Error Code "o34567" is arbitrary - You can use any code that you want to.
            throw new OnPremUserManagementException("o34567", "Cannot get the users. The userMap is null");
        }

        int totalResults = userMap.size();
        if (pageProperties != null) {
            //Set the start index to the response.
            response.setStartIndex(pageProperties.getStartIndex());
        }
        //In this example we are setting the total results to the number of results in this page. If there are more
        //results than the number the client asked for (pageProperties.getCount()), then you need to set the total results correctly
        response.setTotalResults(totalResults);
        List<SCIMUser> users = new ArrayList<SCIMUser>();
        for (String key : userMap.keySet()) {
            users.add(userMap.get(key));
        }
        //Set the actual results
        response.setScimUsers(users);
        return response;
    }

    /**
     * A simple example of how to use <code>SCIMFilter</code> to return a list of users which match the filter criteria.
     * <p/>
     * An Admin who configures the UM would specify a SCIM field name as the UniqueId field name. This field and its value would be sent by Okta in the filter.
     * While implementing the connector, the below points should be noted about the filters.
     * <p/>
     * If you choose a single valued attribute as the UserId field name while configuring the App Instance on Okta,
     * you would get an equality filter here.
     * For example, if you choose userName, the Filter object below may represent an equality filter like "userName eq "someUserName""
     * If you choose the name.familyName as the UserId field name, the filter object may represent an equality filter like
     * "name.familyName eq "someLastName""
     * If you choose a multivalued attribute (email, for example), the <code>SCIMFilter</code> object below may represent an OR filter consisting of two sub-filters like
     * "email eq "abc@def.com" OR email eq "def@abc.com""
     * Of the few multi valued attributes part of the SCIM Core Schema (Like email, address, phone number), only email would be supported as a UserIdField name on Okta.
     * So, you would have to deal with OR filters only if you choose email.
     * <p/>
     * When you get a <code>SCIMFilter</code>, you should check the filter field name (And make sure it is the same field which was configured with Okta), value, condition, etc. as shown in the examples below.
     *
     * @param filter the SCIM filter
     * @return list of users that match the filter
     */
    private List<SCIMUser> getUserByFilter(SCIMFilter filter) {
        List<SCIMUser> users = new ArrayList<SCIMUser>();

        SCIMFilterType filterType = filter.getFilterType();

        if (filterType.equals(SCIMFilterType.EQUALS)) {
            //Example to show how to deal with an Equality filter
            users = getUsersByEqualityFilter(filter);
        } else if (filterType.equals(SCIMFilterType.OR)) {
            //Example to show how to deal with an OR filter containing multiple sub-filters.
            users = getUsersByOrFilter(filter);
        } else {
            LOGGER.error("The Filter " + filter + " contains a condition that is not supported");
        }
        return users;
    }

    /**
     * This is an example for how to deal with an OR filter. An OR filter consists of multiple sub equality filters.
     *
     * @param filter the OR filter with a set of sub filters expressions
     * @return list of users that match any of the filters
     */
    private List<SCIMUser> getUsersByOrFilter(SCIMFilter filter) {
        //An OR filter would contain a list of filter expression. Each expression is a SCIMFilter by itself.
        //Ex : "email eq "abc@def.com" OR email eq "def@abc.com""
        List<SCIMFilter> subFilters = filter.getFilterExpressions();
        LOGGER.info("OR Filter : " + subFilters);
        List<SCIMUser> users = new ArrayList<SCIMUser>();
        //Loop through the sub filters to evaluate each of them.
        //Ex : "email eq "abc@def.com""
        for (SCIMFilter subFilter : subFilters) {
            //Name of the sub filter (email)
            String fieldName = subFilter.getFilterAttribute().getAttributeName();
            //Value (abc@def.com)
            String value = subFilter.getFilterValue();
            //For all the users, check if any of them have this email
            for (Map.Entry<String, SCIMUser> entry : userMap.entrySet()) {
                boolean userFound = false;
                SCIMUser user = entry.getValue();
                //In this example, since we assume that the field name configured with Okta is "email", checking if we got the field name as "email" here
                if (fieldName.equalsIgnoreCase("email")) {
                    //Get the user's emails and check if the value is the same as in the filter
                    Collection<Email> emails = user.getEmails();
                    if (emails != null) {
                        for (Email email : emails) {
                            if (email.getValue().equalsIgnoreCase(value)) {
                                userFound = true;
                                break;
                            }
                        }
                    }
                }
                if (userFound) {
                    users.add(user);
                }
            }
        }
        return users;
    }

    /**
     * This is an example of how to deal with an equality filter.<p>
     * If you choose a custom field/complex field (name.familyName) or any other singular field (userName/externalId), you should get an equality filter here.
     *
     * @param filter the EQUALS filter
     * @return list of users that match the filter
     */
    private List<SCIMUser> getUsersByEqualityFilter(SCIMFilter filter) {
        String fieldName = filter.getFilterAttribute().getAttributeName();
        String value = filter.getFilterValue();
        LOGGER.info("Equality Filter : Field Name [ " + fieldName + " ]. Value [ " + value + " ]");
        List<SCIMUser> users = new ArrayList<SCIMUser>();

        //A basic example of how to return users that match the criteria
        for (Map.Entry<String, SCIMUser> entry : userMap.entrySet()) {
            SCIMUser user = entry.getValue();
            boolean userFound = false;
            //Ex : "userName eq "someUserName""
            if (fieldName.equalsIgnoreCase("userName")) {
                String userName = user.getUserName();
                if (userName != null && userName.equals(value)) {
                    userFound = true;
                }
            } else if (fieldName.equalsIgnoreCase("id")) {
                //"id eq "someId""
                String id = user.getId();
                if (id != null && id.equals(value)) {
                    userFound = true;
                }
            } else if (fieldName.equalsIgnoreCase("name")) {
                String subFieldName = filter.getFilterAttribute().getSubAttributeName();
                Name name = user.getName();
                if (name == null || subFieldName == null) {
                    continue;
                }
                if (subFieldName.equalsIgnoreCase("familyName")) {
                    //"name.familyName eq "someFamilyName""
                    String familyName = name.getLastName();
                    if (familyName != null && familyName.equals(value)) {
                        userFound = true;
                    }
                } else if (subFieldName.equalsIgnoreCase("givenName")) {
                    //"name.givenName eq "someGivenName""
                    String givenName = name.getFirstName();
                    if (givenName != null && givenName.equals(value)) {
                        userFound = true;
                    }
                }
            } else if (filter.getFilterAttribute().getSchema().equalsIgnoreCase(userCustomUrn)) { //Check that the Schema name is the Custom Schema name to process the filter for custom fields
                /**
                 * The example below shows one of the two ways to get a custom property.<p>
                 * The other way is to use the getter directly to get the value - user.getCustomStringProperty("urn:okta:onprem_app:1.0:user:custom", fieldName, null) will get the value
                 * if the fieldName is a root element. If fieldName is a child of any other field, user.getCustomStringProperty("urn:okta:onprem_app:1.0:user:custom", fieldName, parentName)
                 * will get the value.
                 */
                //"urn:okta:onprem_app:1.0:user:custom:departmentName eq "someValue""
                Map<String, JsonNode> customPropertiesMap = user.getCustomPropertiesMap();
                //Get the custom properties map (SchemaName -> JsonNode)
                if (customPropertiesMap == null || !customPropertiesMap.containsKey(userCustomUrn)) {
                    continue;
                }
                //Get the JsonNode having all the custom properties for this schema
                JsonNode customNode = customPropertiesMap.get(userCustomUrn);
                //Check if the node has that custom field
                if (customNode.has(fieldName) && customNode.get(fieldName).asText().equalsIgnoreCase(value)) {
                    userFound = true;
                }
            }

            if (userFound) {
                users.add(user);
            }
        }
        return users;
    }

    /**
     * Get a particular user.
     * <p>
     * This method is invoked when a GET is made to /Users/{id}
     *
     * @param id the Id of the SCIM User
     * @return the user corresponding to the id
     * @throws com.okta.scim.server.exception.OnPremUserManagementException
     *
     */
    @Override
    public SCIMUser getUser(String id) throws OnPremUserManagementException, EntityNotFoundException {
        SCIMUser user = userMap.get(id);
        if (user != null) {
            return user;
        } else {
            //If you do not find a user/group by the ID, you can throw this exception.
            throw new EntityNotFoundException();
        }
    }

    /**
     * This method creates a group. All the standard attributes of the SCIM group can be retrieved by using the
     * getters on the SCIMStandardGroup member of the SCIMGroup object.
     * <p>
     * If there are custom schemas in the SCIMGroup input, you can retrieve them by providing the name of the
     * custom property. (Example : SCIMGroup.getCustomProperty("schemaName", "customFieldName"))
     * <p>
     * This method is invoked when a POST is made to /Groups with a SCIM payload representing a group
     * to be created.
     *
     * @param group SCIMGroup representation of the SCIM String payload sent by the SCIM client
     * @return the created SCIMGroup
     * @throws com.okta.scim.server.exception.OnPremUserManagementException
     *
     */
    @Override
    public SCIMGroup createGroup(SCIMGroup group) throws OnPremUserManagementException, DuplicateGroupException {
        String displayName = group.getDisplayName();

        boolean duplicate = false;

        /**
         * Below is an example to show how to deal with exceptional conditions while writing the connector.
         * If you cannot complete the UserManagement operation on the on premises
         * application because of any error/exception, you should throw the OnPremUserManagementException as shown below
         * <b>Note:</b> You can throw this exception from all the CRUD (Create/Retrieve/Update/Delete) operations defined on
         * Users/Groups in the SCIM interface.
         */
        if (groupMap == null) {
            //Note that the Error Code "o23456" is arbitrary - You can use any code that you want to.
            throw new OnPremUserManagementException("o23456", "Cannot create the group. The groupMap is null");
        }

        for (Map.Entry<String, SCIMGroup> entry : groupMap.entrySet()) {
            //In this example, let us assume that a group is duplicate if the displayName is the same
            if (entry.getValue().getDisplayName().equalsIgnoreCase(displayName)) {
                duplicate = true;
            }
        }

        if (duplicate) {
            throw new DuplicateGroupException();
        }

        String id = generateNextId(GROUP_RESOURCE);
        group.setId(id);

        groupMap.put(group.getId(), group);
        save();
        return group;
    }

    /**
     * This method updates a group.
     * <p>
     * This method is invoked when a PUT is made to /Groups/{id} with the SCIM payload representing a group to
     * be updated.
     *
     * @param id    the id of the SCIM group
     * @param group SCIMGroup representation of the SCIM String payload sent by the SCIM client
     * @return the updated SCIMGroup
     * @throws com.okta.scim.server.exception.OnPremUserManagementException
     *
     */
    @Override
    public SCIMGroup updateGroup(String id, SCIMGroup group) throws OnPremUserManagementException {
        SCIMGroup existingGroup = groupMap.get(id);
        if (existingGroup != null) {
            groupMap.put(id, group);
            save();
            return group;
        } else {
            throw new EntityNotFoundException();
        }
    }

    /**
     * Get all the groups.
     * <p>
     * This method is invoked when a GET is made to /Groups
     * In order to support pagination (So that the client and the server) are not overwhelmed, this method supports querying based on a start index and the
     * maximum number of results expected by the client. The implementation is responsible for maintaining indices for the SCIM groups.
     *
     * @param pageProperties @see com.okta.scim.util.model.PaginationProperties An object holding the properties needed for pagination - startindex and the count.
     * @return SCIMGroupQueryResponse the response from the server containing the total number of results, start index and the items per page along with a list of groups
     * @throws com.okta.scim.server.exception.OnPremUserManagementException
     *
     */
    @Override
    public SCIMGroupQueryResponse getGroups(PaginationProperties pageProperties) throws OnPremUserManagementException {
        SCIMGroupQueryResponse response = new SCIMGroupQueryResponse();
        int totalResults = groupMap.size();
        if (pageProperties != null) {
            //Set the start index
            response.setStartIndex(pageProperties.getStartIndex());
        }
        //In this example we are setting the total results to the number of results in this page. If there are more
        //results than the number the client asked for (pageProperties.getCount()), then you need to set the total results correctly
        response.setTotalResults(totalResults);
        List<SCIMGroup> groups = new ArrayList<SCIMGroup>();
        for (String key : groupMap.keySet()) {
            groups.add(groupMap.get(key));
        }
        //Set the actual results
        response.setScimGroups(groups);
        return response;
    }

    /**
     * Get a particular group.
     * <p>
     * This method is invoked when a GET is made to /Groups/{id}
     *
     * @param id the Id of the SCIM group
     * @return the group corresponding to the id
     * @throws com.okta.scim.server.exception.OnPremUserManagementException
     *
     */
    @Override
    public SCIMGroup getGroup(String id) throws OnPremUserManagementException {
        SCIMGroup group = groupMap.get(id);
        if (group != null) {
            return group;
        } else {
            //If you do not find a user/group by the ID, you can throw this exception.
            throw new EntityNotFoundException();
        }
    }

    /**
     * Delete a particular group.
     * <p>
     * This method is invoked when a DELETE is made to /Groups/{id}
     *
     * @param id the Id of the SCIM group
     * @throws OnPremUserManagementException
     */
    @Override
    public void deleteGroup(String id) throws OnPremUserManagementException, EntityNotFoundException {
        if (groupMap.containsKey(id)) {
            groupMap.remove(id);
            save();
        } else {
            throw new EntityNotFoundException();
        }
    }

    /**
     * Get all the Okta User Management capabilities that this SCIM Service has implemented.
     * <p>
     * This method is invoked when a GET is made to /ServiceProviderConfigs. It is called only when you are testing
     * or modifying your connector configuration from the Okta Application instance UM UI. If you change the return values
     * at a later time please re-test and re-save your connector settings to have your new return values respected.
     * <p>
     * These User Management capabilities help customize the UI features available to your app instance and tells Okta
     * all the possible commands that can be sent to your connector.
     *
     * @return all the implemented User Management capabilities.
     */
    @Override
    public UserManagementCapabilities[] getImplementedUserManagementCapabilities() {
        return UserManagementCapabilities.values();
    }

    /**
     * Update the cache based on the data stored in the files
     */
    private synchronized void updateCache() {
        //Nothing to update if persistence is not enabled
        if (!useFilePersistence) {
            return;
        }

        userMap.clear();
        groupMap.clear();

        try {
            SCIMConnectorUtil.readUsersFromFile(userMap, usersFilePath);
        } catch (Exception e) {
            throw new OnPremUserManagementException("Exception in building the user cache from the file [" + usersFilePath + "]", e);
        }

        try {
            SCIMConnectorUtil.readGroupsFromFile(groupMap, groupsFilePath);
        } catch (Exception e) {
            throw new OnPremUserManagementException("Exception in building the group cache from the file [" + groupsFilePath + "]", e);
        }
    }

    /**
     * Save the data to files
     */
    private synchronized void save() {
        //Nothing to save if persistence is not enabled
        if (!useFilePersistence) {
            return;
        }
        //Create the user/group files if they dont already exist
        try {
            SCIMConnectorUtil.saveUsersToFile(userMap, usersFilePath);
        } catch (Exception e) {
            throw new OnPremUserManagementException("Exception in saving the users [" + userMap + "] to the file [" + usersFilePath + "]", e);
        }

        try {
            SCIMConnectorUtil.saveGroupsToFile(groupMap, groupsFilePath);
        } catch (Exception e) {
            throw new OnPremUserManagementException("Exception in saving the groups [" + groupMap + "] to the file [" + groupsFilePath + "]", e);
        }
    }

    /**
     * Generate the next if for a resource
     *
     * @param resourceType
     * @return
     */
    private String generateNextId(String resourceType) {
        if (useFilePersistence) {
            return UUID.randomUUID().toString();
        }

        if (resourceType.equals(USER_RESOURCE)) {
            return Integer.toString(nextUserId++);
        }

        if (resourceType.equals(GROUP_RESOURCE)) {
            return Integer.toString(nextGroupId++);
        }

        return null;
    }
}

