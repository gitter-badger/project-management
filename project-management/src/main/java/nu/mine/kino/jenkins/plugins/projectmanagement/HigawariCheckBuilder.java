package nu.mine.kino.jenkins.plugins.projectmanagement;

import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

import javax.mail.MessagingException;
import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import nu.mine.kino.jenkins.plugins.projectmanagement.utils.PMUtils;
import nu.mine.kino.projects.utils.Utils;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.StopWatch;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * {@link EVMToolsBuilder} ���Z�b�g���ꂽ�W���u��T���A���݂̓��ւ������T���Ă���B
 * <p>
 * When a build is performed, the
 * {@link #perform(AbstractBuild, Launcher, BuildListener)} method will be
 * invoked.
 * 
 * @author Masatomi KINO.
 */
public class HigawariCheckBuilder extends Builder {

    // �l�X�g�����e�L�X�g�{�b�N�X���쐬����Ƃ��̒�΁B
    private String targetProjects;

    public static class EnableTextBlock {
        private String targetProjects;

        @DataBoundConstructor
        public EnableTextBlock(String targetProjects) {
            this.targetProjects = targetProjects;
        }
    }

    private final EnableTextBlock useFilter;

    public EnableTextBlock getUseFilter() {
        return useFilter;
    }

    private final String mailSubject;

    private final String mailBody;

    public String getMailSubject() {
        return mailSubject;
    }

    public String getMailBody() {
        return mailBody;
    }

    // Fields in config.jelly must match the parameter names in the
    // "DataBoundConstructor"
    @DataBoundConstructor
    public HigawariCheckBuilder(EnableTextBlock useFilter,
            EnableUseMailTextBlock useMail, String mailSubject, String mailBody) {

        this.useFilter = useFilter;
        this.useMail = useMail;
        this.mailSubject = mailSubject;
        this.mailBody = mailBody;
        if (useFilter != null) { // targetProjects�́A�R�R��ʂ�Ȃ���Ώ����l�ɖ߂�B
            this.targetProjects = useFilter.targetProjects;
        }
        if (useMail != null) { // targetProjects�́A�R�R��ʂ�Ȃ���Ώ����l�ɖ߂�B
            this.addresses = useMail.addresses;
        }
    }

    public String getTargetProjects() {
        return targetProjects;
    }

    // �l�X�g�����e�L�X�g�{�b�N�X���쐬����Ƃ��̒�΁B

    // �l�X�g�����e�L�X�g�{�b�N�X���쐬����Ƃ��̒�΁B
    private String addresses;

    public static class EnableUseMailTextBlock {
        private String addresses;

        @DataBoundConstructor
        public EnableUseMailTextBlock(String addresses) {
            this.addresses = addresses;
        }
    }

    private final EnableUseMailTextBlock useMail;

    public String getAddresses() {
        return addresses;
    }

    // �l�X�g�����e�L�X�g�{�b�N�X���쐬����Ƃ��̒�΁B

    public String getSamples() {
        return getDescriptor().defaultSamples();
    }

    /**
     */
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher,
            BuildListener listener) throws InterruptedException, IOException {

        String subject = createSubject(build, listener);
        String message = createMessage(build, listener);

        System.out.printf("[EVM Tools] ����: %s\n", addresses);
        System.out.printf("[EVM Tools] �T�u�W�F�N�g: %s\n", subject);
        System.out.printf("[EVM Tools] �{��:\n%s\n", message);

        if (useMail != null) {
            listener.getLogger().println("[EVM Tools] ����: " + addresses);
            listener.getLogger().println("[EVM Tools] �T�u�W�F�N�g: " + subject);
            sendMail(listener, subject, message);
        }
        return true;
    }

    private String createSubject(AbstractBuild build, BuildListener listener) {
        return createDefaultSubject(build, listener);
    }

    private String createMessage(AbstractBuild build, BuildListener listener)
            throws IOException, InterruptedException, AbortException {
        return createDefaultMessage(build, listener);

    }

    private String createDefaultSubject(AbstractBuild build,
            BuildListener listener) {
        String PROJECT_NAME = build.getProject().getName();
        String BUILD_NUMBER = String.valueOf(build.getNumber());

        String subject = String.format("%s ����̃��[��(#%s)", PROJECT_NAME,
                BUILD_NUMBER);
        return subject;
    }

    private String createDefaultMessage(AbstractBuild build,
            BuildListener listener) throws IOException, InterruptedException,
            AbortException {
        String template = "${HIGAWARI_CHECK_RESULTS}";
        try {
            template = TokenMacro.expandAll(build, listener, template);
            listener.getLogger().println(template);
        } catch (MacroEvaluationException e) {
            String errorMsg = "${HIGAWARI_CHECK_RESULTS} �̕ϊ��Ɏ��s���܂����B�����𒆒f���܂��B";
            listener.getLogger().println("[EVM Tools] " + errorMsg);
            throw new AbortException(errorMsg);
        }

        String BUILD_URL = new StringBuilder()
                .append(Jenkins.getInstance().getRootUrl())
                .append(build.getUrl()).toString();
        String PROJECT_NAME = build.getProject().getName();

        String header = "�ȉ��A" + PROJECT_NAME + " ����̃��[���ł��B\n\n";
        String footer = String.format(
                "\n\nCheck console output at %s to view the results.",
                BUILD_URL);

        StringBuffer msgBuf = new StringBuffer();
        msgBuf.append(header);
        msgBuf.append(template);
        msgBuf.append(footer);
        String message = new String(msgBuf);

        return message;
    }

    private void sendMail(BuildListener listener, String subject, String message)
            throws UnsupportedEncodingException, AbortException {
        StopWatch watch = new StopWatch();
        watch.start();

        if (!StringUtils.isEmpty(addresses)) {
            String[] addressesArray = Utils.parseCommna(addresses);
            for (String to : addressesArray) {
                System.out.printf("����: [%s]\n", to);
            }
            try {
                if (addressesArray.length > 0) {
                    PMUtils.sendMail(addressesArray, subject, message);
                } else {
                    String errorMsg = "���[�����M�Ɏ��s���܂����B����̐ݒ肪����Ă��܂���";
                    listener.getLogger().println("[EVM Tools] " + errorMsg);
                    throw new AbortException(errorMsg);
                }
            } catch (MessagingException e) {
                String errorMsg = "���[�����M�Ɏ��s���܂����B�u�V�X�e���̐ݒ�v�� E-mail �ʒm �̐ݒ�∶��Ȃǂ��������Ă�������";
                listener.getLogger().println("[EVM Tools] " + errorMsg);
                throw new AbortException(errorMsg);
            }
        }
        watch.stop();
        System.out.printf("���[�����M����:[%d] ms\n", watch.getTime());
        watch.reset();
        watch = null;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Descriptor for {@link HigawariCheckBuilder}. Used as a singleton. The
     * class is marked as public so that it can be accessed from views.
     * 
     * <p>
     * See
     * <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension
    // This indicates to Jenkins that this is an implementation of an extension
    // point.
    public static final class DescriptorImpl extends
            BuildStepDescriptor<Builder> {

        /**
         * In order to load the persisted global configuration, you have to call
         * load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
         * 
         * @param value
         *            This parameter receives the value that the user has typed.
         * @return Indicates the outcome of the validation. This is sent to the
         *         browser.
         *         <p>
         *         Note that returning {@link FormValidation#error(String)} does
         *         not prevent the form from being saved. It just means that a
         *         message will be displayed to the user.
         */
        public FormValidation doCheckName(@QueryParameter
        String value) throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a Project File Name");
            if (value.length() < 4)
                return FormValidation.warning("Isn't the name too short?");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project
            // types
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "���ւ��`�F�b�N�c�[��";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData)
                throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            // useFrench = formData.getBoolean("useFrench");
            // ^Can also use req.bindJSON(this, formData);
            // (easier when there are many fields; need set* methods for this,
            // like setUseFrench)

            save();
            return super.configure(req, formData);
        }

        // https://wiki.jenkins-ci.org/display/JENKINS/Basic+guide+to+Jelly+usage+in+Jenkins
        // config.jelly����Ăяo�����A�f�t�H���g�l���Z�b�g���郁�\�b�h�B
        public String defaultSamples() {
            StringBuffer buf = new StringBuffer();
            List<AbstractProject<?, ?>> projects = PMUtils
                    .findProjectsWithEVMToolsBuilder();
            for (int i = 0; i < projects.size(); i++) {
                buf.append(projects.get(i).getName());
                if (i < projects.size() - 1) {
                    buf.append("\n");
                }
            }
            return new String(buf);
        }
    }

}
