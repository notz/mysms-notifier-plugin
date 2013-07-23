package com.mysms.jenkins;

import hudson.Extension;
import hudson.Functions;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run.Artifact;
import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.log4j.Logger;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
/**
 * A {@link MysmsNotifier} is a {@link Notifier} that uses the Rest API of
 * mysms {@linkplain "http://www.mysms.com/} to send
 * text messages with the build status of individual builds.
 * 
 * This notifier was inspired in features and design by the Twillio Notifier
 * plugin.
 * 
 * @author Gernot Pansy (notz76@gmail.com)
 */
public class MysmsNotifier extends Notifier {

    /**
     * The message to send/read to the recipient.
     */
    private final String message;

    /**
     * The list of phone numbers, comma separated of people who should receive
     * the communications.
     */
    private final String toList;

    private final Boolean sendToCulprits;
    /**
     * Only send notification on failure or recovery.
     */
    private final Boolean onlyOnFailureOrRecovery;

    /**
     * Include a tiny url to the build that was failing.
     */
    private final Boolean includeUrl;

    private final String userList;

    private final String culpritMessage;
    private final Map<String, NameValuePair> userToPhoneMap;
    private final Map<String, String> substitutionAttributes;

    /**
     * Databound constructor matching the corresponding Jelly configuration
     * items.
     * 
     * @param message
     *            the message to send
     * @param toList
     *            the comma separated list of people to send to
     * @param onlyOnFailureOrRecovery
     *            whether to send notification only on failure or recovery
     * @param includeUrl
     *            whether to include a url to the build statu
     */
    @DataBoundConstructor
    public MysmsNotifier(final String message, final String toList, final String onlyOnFailureOrRecovery,
            final String includeUrl, final String userList, final String sendToCulprits,
            final String culpritMessage) {
    	
    	 Logger.getLogger(MysmsNotifier.class).info("MysmsNotifier initialize");
    	
        this.message = message;
        this.toList = toList;
        this.onlyOnFailureOrRecovery = convertToBoolean(onlyOnFailureOrRecovery);
        this.includeUrl = convertToBoolean(includeUrl);
        this.userList = userList;
        this.sendToCulprits = convertToBoolean(sendToCulprits);
        this.culpritMessage = culpritMessage;

        userToPhoneMap = parseUserList(userList);
        substitutionAttributes = new HashMap<String, String>();
        
        Logger.getLogger(MysmsNotifier.class).info("MysmsNotifier created");
    }

    protected static Map<String, NameValuePair> parseUserList(final String users) {
        Map<String, NameValuePair> resultMap = new HashMap<String, NameValuePair>();
        String[] splitUserPairArray = users.split(",");
        if (splitUserPairArray != null) {
            for (String userPair : splitUserPairArray) {

                String[] splitPair = userPair.split(":");
                if (splitPair != null) {
                    if (splitPair.length > 0) {
                        String userName = splitPair[0];
                        if (splitPair.length > 1) {
                            String phone = splitPair[1];
                            if (splitPair.length > 2) {
                                String displayName = splitPair[2];
                                NameValuePair pair = new NameValuePair(displayName, phone);
                                resultMap.put(userName, pair);
                            }

                        }
                    }

                }
            }
        }
        return resultMap;
    }

    protected static String substituteAttributes(String inputString, Map<String, String> substitutionMap) {
        String result = inputString;
        for (String key : substitutionMap.keySet()) {
            String replaceValue = substitutionMap.get(key);
            result = result.replaceAll(key, replaceValue);
        }
        return result;
    }

    /**
     * Getter for the toList.
     * 
     * @return the toList
     */
    public String getToList() {
        return this.toList;
    }

    /**
     * Validates the toList.
     * 
     * @param value the toList to validate
     * @return {@link FormValidation.ok} if valid, {@link FormValidation.error} if not valid
     */
    public FormValidation doCheckToList(@QueryParameter String value) {
        if (validatePhoneNoList(value))
            return FormValidation.ok();
        else
            return FormValidation
                    .error("The to list must consist of at least one phone number. Multiple numbers are comma separated.");
    }

    private boolean validatePhoneNoList(String toList) {

        String[] nrs = toList.split(",");
        if (nrs == null)
            return false;
        for (String number : nrs) {
            if (!number.matches("^([0-9\\(\\)\\/\\+ \\-]*)$"))
                return false;
        }
        return false;
    }

    /**
     * Getter for the message.
     * 
     * @return the message
     */
    public String getMessage() {
        return this.message;
    }

    /**
     * Getter for the culprit message.
     * 
     * @return the culprit message
     */
    public String getCulpritMessage() {
        return this.culpritMessage;
    }

    /**
     * Getter for sendToCulprits flag.
     * 
     * @return the sendToCulprits flag.
     */
    public Boolean getSendToCulprits() {
        return this.sendToCulprits;
    }

    /**
     * Converts a string to a Boolean.
     * 
     * @param string
     *            the string to convert to Boolean
     * 
     * @return the Boolean
     */
    private static Boolean convertToBoolean(final String string) {
        Boolean result = null;
        if ("true".equals(string) || "Yes".equals(string)) {
            result = Boolean.TRUE;
        } else if ("false".equals(string) || "No".equals(string)) {
            result = Boolean.FALSE;
        }
        return result;
    }

    /**
     * Returns the include url flag.
     * 
     * @return the includeUrl flag
     */
    public Boolean getIncludeUrl() {
        return this.includeUrl;
    }

    /**
     * Getter for onlyOnFailureOrRecovery flag.
     * 
     * @return the onlyOnFailureOrRecovery flag
     */
    public Boolean getOnlyOnFailureOrRecovery() {
        return this.onlyOnFailureOrRecovery;
    }

    /**
     * Getter for the message.
     * 
     * @return the message
     */
    public String getUserList() {
        return this.userList;
    }

    @Override
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) {

        try {
            listener.getLogger().println("Perform " + build.getDisplayName());

            if (shouldNotify(build)) {
                
                if (build != null) {
                    substitutionAttributes.put("%PROJECT%", build.getProject().getDisplayName());
                    substitutionAttributes.put("%BUILD%", build.getDisplayName());
                    substitutionAttributes.put("%STATUS%", build.getResult().toString());
                    
                    StringBuilder artifactsStringBuilder = new StringBuilder();
                    for (@SuppressWarnings("rawtypes") Artifact artifact : build.getArtifacts()) {
                    	artifactsStringBuilder.append(artifact.getFileName())
                    		.append(": ")
                    		.append(artifact.getHref())
                    		.append("\n");
                    }
                    substitutionAttributes.put("%ARTIFACTS%", artifactsStringBuilder.toString());
                }
               
                List<String> culpritList = getCulpritList(build, listener.getLogger());
                listener.getLogger().println("Culprits: " + culpritList.size());
                listener.getLogger().println("Culprits: " + culpritList);
                substitutionAttributes.put("%CULPRITS%", build.getResult().toString());

                List<NameValuePair> phoneToCulprits = new ArrayList<NameValuePair>();
                for (String culprit : culpritList) {
                    NameValuePair userPair = userToPhoneMap.get(culprit);
                    if (userPair != null) {
                        phoneToCulprits.add(userPair);
                    }
                }
                substitutionAttributes.put("%CULPRITS%", culpritStringFromList(phoneToCulprits));

                final String[] recipientArray = getToList().split(",");

                if (recipientArray != null) {
                    for (final String recipient : recipientArray) {
                        final String absoluteBuildURL = getDescriptor().getUrl() + build.getUrl();
                        String message = substituteAttributes(this.message, substitutionAttributes);

                        if (this.includeUrl.booleanValue()) {
                        	message += " " + createTinyUrl(absoluteBuildURL);
                        }
                        
                        sendMessage(getDescriptor().getApiKey(), getDescriptor().getMsisdn(), getDescriptor().getPassword(), recipient, message);
                    }
                }

                if (sendToCulprits) {
                    for (final NameValuePair phoneToCulprit : phoneToCulprits) {
                        final String absoluteBuildURL = getDescriptor().getUrl() + build.getUrl();
                        String recipient = phoneToCulprit.getValue();
                        final Map<String, String> localSubAttrs = new HashMap<String, String>(substitutionAttributes);
                        localSubAttrs.put("%CULPRIT-NAME%", phoneToCulprit.getName());
                        String message = null;
                        if (this.culpritMessage.isEmpty()) {
                        	message = substituteAttributes(this.message, localSubAttrs);
                        } else {
                        	message = substituteAttributes(this.culpritMessage, localSubAttrs);
                        }

                        if (this.includeUrl.booleanValue()) {
                        	message += " " + createTinyUrl(absoluteBuildURL);
                        }
                        
                        sendMessage(getDescriptor().getApiKey(), getDescriptor().getMsisdn(), getDescriptor().getPassword(), recipient, message);
                    }
                }

            } else {
                listener.getLogger().println("Not notifying: " + build.getDisplayName());

            }
        } catch (final Exception t) {
            listener.getLogger().println("Exception " + t);
        }

        return true;
    }

    protected static String culpritStringFromList(List<NameValuePair> phoneToCulprit) {
        String result = "";
        if (phoneToCulprit.size() == 1) {
            result = phoneToCulprit.get(0).getName();
        } else {

            int c = phoneToCulprit.size() - 1;
            for (NameValuePair pair : phoneToCulprit) {
                if (c == (phoneToCulprit.size() - 1)) {
                    result = pair.getName();
                } else {
                    if (c != 0) {
                        result = result + " " + pair.getName();
                    } else {
                        result = result + " and " + pair.getName();
                    }
                }
                c--;
            }
        }
        return result;
    }

    /**
     * Determine if this build represents a failure or recovery. A build failure
     * includes both failed and unstable builds. A recovery is defined as a
     * successful build that follows a build that was not successful. Always
     * returns false for aborted builds.
     * 
     * @param build
     *            the Build object
     * @return true if this build represents a recovery or failure
     */
    protected boolean isFailureOrRecovery(final AbstractBuild<?, ?> build) {
        if (build.getResult() == Result.FAILURE || build.getResult() == Result.UNSTABLE) {
            return true;
        } else if (build.getResult() == Result.SUCCESS) {
            final AbstractBuild<?, ?> previousBuild = build.getPreviousBuild();
            if (previousBuild != null && previousBuild.getResult() != Result.SUCCESS) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * Determine if this build results should be tweeted. Uses the local
     * settings if they are provided, otherwise the global settings.
     * 
     * @param build
     *            the Build object
     * @return true if we should tweet this build result
     */
    protected boolean shouldNotify(final AbstractBuild<?, ?> build) {
        if (this.onlyOnFailureOrRecovery == null) {
            return false;
        } else if (this.onlyOnFailureOrRecovery.booleanValue()) {
            return isFailureOrRecovery(build);
        } else {
            return true;
        }
    }

    /**
     * Sends a text message.
     * 
     * @param message
     * @param smsFactory
     * @param from
     * @param to
     * @throws IOException 
     * @throws HttpException 
     * @throws TwilioRestException
     */
    private void sendMessage(final String apiKey, final String msisdn, final String password, 
    		 final String recipient, final String message) throws HttpException, IOException {
        
        final HttpClient client = new HttpClient();
        final GetMethod getMethod = new GetMethod("https://api.mysms.com/json/message/send");
        getMethod.setQueryString(new NameValuePair[] { 
        		new NameValuePair("api_key", apiKey),
        		new NameValuePair("msisdn", msisdn),
        		new NameValuePair("password", password),
        		new NameValuePair("recipient", recipient),
        		new NameValuePair("message", message)
        });
        
        final int status = client.executeMethod(getMethod);
        if (status == HttpStatus.SC_OK) {
        	JSONObject response = (JSONObject) JSONSerializer.toJSON(getMethod.getResponseBodyAsString(1024));
        	int errorCode = response.getInt("errorCode");
            if (errorCode != 0) {
            	throw new RuntimeException("Send message request failed with error: " + errorCode);
            }
        } else {
            throw new IOException("Non-OK response code back from mysms: " + status);
        }
    }

    private List<String> getCulpritList(final AbstractBuild<?, ?> build, PrintStream logger) throws IOException {
        final Set<User> culprits = build.getCulprits();
        logger.println(" Culprits size" + culprits.size());
        final List<String> culpritList = new ArrayList<String>();
        final ChangeLogSet<? extends Entry> changeSet = build.getChangeSet();
        if (culprits.size() > 0) {
            for (final User user : culprits) {

                culpritList.add(user.getId());
            }
        } else if (changeSet != null) {
            logger.println(" Changeset " + changeSet.toString());
            for (final Entry entry : changeSet) {
                final User user = entry.getAuthor();
                culpritList.add(user.getId());
            }
        }
        return culpritList;
    }

    /**
     * Creates a tiny url out of a longer url.
     * 
     * @param url
     * @return
     * @throws IOException
     */
    private static String createTinyUrl(final String url) throws IOException {
        final HttpClient client = new HttpClient();
        final GetMethod getMethod = new GetMethod("http://tinyurl.com/api-create.php?url=" + url.replace(" ", "%20"));

        final int status = client.executeMethod(getMethod);
        if (status == HttpStatus.SC_OK) {
            return getMethod.getResponseBodyAsString(1024);
        } else {
            throw new IOException("Non-OK response code back from tinyurl: " + status);
        }
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        
    	public String apiKey;
        public String msisdn;
        public String password;
        public String hudsonUrl;

        public DescriptorImpl() {
            super(MysmsNotifier.class);
            load();
        }

        @Override
        public boolean configure(final StaplerRequest req, final JSONObject formData) throws FormException {
            // set the booleans to false as defaults

            this.apiKey = formData.getString("apiKey");
            this.msisdn = formData.getString("msisdn");
            this.password = formData.getString("password");
            save();
            return super.configure(req, formData);
        }

        @Override
        public String getDisplayName() {
            return "Notify via mysms";
        }

        public String getApiKey() {
            return this.apiKey;
        }

        public String getMsisdn() {
            return this.msisdn;
        }
        
        public String getPassword() {
            return this.password;
        }

        public String getUrl() {
            return this.hudsonUrl;
        }

        @SuppressWarnings("rawtypes")
		@Override
        public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public Publisher newInstance(final StaplerRequest req, final JSONObject formData) throws FormException {
            if (this.hudsonUrl == null) {
                // if Hudson URL is not configured yet, infer some default
                this.hudsonUrl = Functions.inferHudsonURL(req);
                save();
            }
            return super.newInstance(req, formData);
        }
    }

}
