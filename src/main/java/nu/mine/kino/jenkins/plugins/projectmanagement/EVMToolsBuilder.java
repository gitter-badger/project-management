package nu.mine.kino.jenkins.plugins.projectmanagement;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;
import nu.mine.kino.entity.Project;
import nu.mine.kino.jenkins.plugins.projectmanagement.utils.PMUtils;
import nu.mine.kino.projects.ACCreator;
import nu.mine.kino.projects.EVCreator;
import nu.mine.kino.projects.JSONProjectCreator;
import nu.mine.kino.projects.PVCreator;
import nu.mine.kino.projects.ProjectException;
import nu.mine.kino.projects.ProjectWriter;
import nu.mine.kino.projects.utils.ProjectUtils;
import nu.mine.kino.projects.utils.ReadUtils;

import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.commons.lang.time.DateUtils;
import org.apache.commons.lang.time.StopWatch;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Sample {@link Builder}.
 * 
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked and a new
 * {@link EVMToolsBuilder} is created. The created instance is persisted to the
 * project configuration XML by using XStream, so this allows you to use
 * instance fields (like {@link #name}) to remember the configuration.
 * 
 * <p>
 * When a build is performed, the
 * {@link #perform(AbstractBuild, Launcher, BuildListener)} method will be
 * invoked.
 * 
 * @author Masatomi KINO.
 */
public class EVMToolsBuilder extends Builder {

    private final String name;

    private static final String[] PREFIX_ARRAY = new String[] {
            PMConstants.BASE, PMConstants.BASE + "1", PMConstants.BASE + "2" };

    private final String addresses;

    private final boolean sendAll;

    private final boolean higawari;

    // Fields in config.jelly must match the parameter names in the
    // "DataBoundConstructor"
    @DataBoundConstructor
    public EVMToolsBuilder(String name, String addresses, boolean sendAll,
            boolean higawari) {
        this.name = name;
        this.addresses = addresses;
        this.sendAll = sendAll;
        this.higawari = higawari;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getName() {
        return name;
    }

    public String getAddresses() {
        return addresses;
    }

    public boolean getSendAll() {
        return sendAll;
    }

    public boolean getHigawari() {
        return higawari;
    }

    /**
     * ���[�N�X�y�[�X��̊Y���t�@�C������AJSON�t�@�C�����쐬�B�܂����݂���Ȃ�Y���t�@�C��(base,base1,base2)
     * Excel����JSON�t�@�C�����쐬�B �܂�PV/AC/EV��tsv�t�@�C�����쐬�B
     * �쐬���ꂽ���X�̃t�@�C���Q�̓r���h�f�B���N�g��(�����ɂ���)�ɃR�s�[����AAction����Q�Ɖ\�ƂȂ�B
     * 
     * �������Atsv�t�@�C���Q�̓_�E�����[�h���g�p����̂ŃR�s�[�K�{�����A
     * json�t�@�C����Project���Ƃ��ăV���A���C�Y����Ă���Εs�v��������Ȃ��B �ǂ��炪�������v�m�F�ł��邪�B
     * 
     * @see hudson.tasks.BuildStepCompatibilityLayer#perform(hudson.model.AbstractBuild,
     *      hudson.Launcher, hudson.model.BuildListener)
     */
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher,
            BuildListener listener) throws InterruptedException, IOException {

        listener.getLogger().println("[EVM Tools] �W�v�Ώ�: " + name);
        FilePath root = build.getModuleRoot(); // ���[�N�X�y�[�X�̃��[�g
        listener.getLogger().println(
                "[EVM Tools] (" + new FilePath(root, name) + ")");

        // ���ԓI�ɁA���ւ��`�F�b�N�́AJSON�t�@�C��������Ă���s���悤�ɂ����B
        // ���x�������΁A�܂����̏����͂��ƂɎ����Ă����ׂ����B
        FilePath buildRoot = new FilePath(build.getRootDir()); // ���̃r���h�̃��[�g

        StopWatch watch = new StopWatch();

        // ���ւ��^�p�������ƍs���̂ł���΁B
        boolean higawariOKFlag = false;
        if (higawari) {
            listener.getLogger()
                    .println("[EVM Tools] Jenkins���ւ��Ǘ��o�[�W�����ŉғ����܂�");
            watch.start();
            higawariOKFlag = checkHigawari(build, name, listener);
            watch.stop();
            System.out.printf("���ւ��`�F�b�N����:[%d] ms\n", watch.getTime());
            watch.reset();
            if (!higawariOKFlag) {
                throw new AbortException("���ւ��`�F�b�N�ŃG���[�ƂȂ������߁A�r���h���~���܂��B");
            }
        }

        // FilePath pmJSON =
        // executeAndCopies(root, buildRoot, new AllCallable(name, !higawari,
        // listener));

        watch.start();
        listener.getLogger().println("[EVM Tools] �쐬�J�n");
        FilePath pmJSON = executeAndCopies(root, buildRoot,
                new ProjectWriterExecutor(name, !higawari))[0];
        listener.getLogger().println("[EVM Tools] �쐬�����B�t�@�C����: " + pmJSON);
        watch.stop();
        System.out.printf("%s �쐬����:[%d] ms\n", pmJSON.getName(),
                watch.getTime());
        watch.reset();

        watch.start();
        listener.getLogger().println("[EVM Tools] PV�t�@�C���쐬�J�n");
        FilePath[] pvResult = executeAndCopies(root, buildRoot,
                new PVCreatorExecutor(name));
        listener.getLogger().println("[EVM Tools] �쐬�����B�t�@�C����: " + pvResult[0]);
        listener.getLogger().println("[EVM Tools] �쐬�����B�t�@�C����: " + pvResult[1]);
        watch.stop();
        System.out.printf("PV�쐬����:[%d] ms\n", watch.getTime());
        watch.reset();

        watch.start();
        listener.getLogger().println("[EVM Tools] AC�t�@�C���쐬�J�n");
        FilePath[] acResult = executeAndCopies(root, buildRoot,
                new ACCreatorExecutor(name));
        for (FilePath result : acResult) {
            listener.getLogger().println("[EVM Tools] �쐬�����B�t�@�C����: " + result);
        }
        watch.stop();
        System.out.printf("AC�쐬����:[%d] ms\n", watch.getTime());
        watch.reset();

        watch.start();
        listener.getLogger().println("[EVM Tools] EV�t�@�C���쐬�J�n");
        FilePath[] evResult = executeAndCopies(root, buildRoot,
                new EVCreatorExecutor(name));
        for (FilePath result : evResult) {
            listener.getLogger().println("[EVM Tools] �쐬�����B�t�@�C����: " + result);
        }
        watch.stop();
        System.out.printf("EV�쐬����:[%d] ms\n", watch.getTime());
        watch.reset();

        if (higawari && higawariOKFlag) { // ���ւ��Ǘ����Ă��āA�����ւ�肵�Ă�����ƌ������ƂȂ̂�
            listener.getLogger().println(
                    "[EVM Tools] �W�v�����������̂ŁA�u�O���荞�񂾍ŐV�t�@�C���v���㏑���ۑ����܂�");
            // FilePath targetFile = new FilePath(root, name);
            // FilePath previousNewestFile = new FilePath(root,
            // targetFile.getName() + "."+PMConstants.TMP_EXT); //
            // �O���荞�񂾍ŐV�t�@�C���ւ̎Q��
            // targetFile.copyTo(previousNewestFile); // �㏑���B

            watch.start();
            String tmpFile = pmJSON.getName() + "." + PMConstants.TMP_EXT;
            FilePath previousNewestJsonFile = new FilePath(root, tmpFile); // �O���荞�񂾍ŐV�t�@�C���ւ̎Q��
            FilePath previousNewestJsonFile2 = new FilePath(buildRoot, tmpFile);
            pmJSON.copyTo(previousNewestJsonFile); // ���̂����f�B�X�R��
            pmJSON.copyTo(previousNewestJsonFile2);
            listener.getLogger().println(
                    "[EVM Tools] " + previousNewestJsonFile);
            listener.getLogger().println(
                    "[EVM Tools] " + previousNewestJsonFile2);
            listener.getLogger().println(
                    "[EVM Tools] " + "�� " + pmJSON.getName() + " �ŏ㏑������܂���");
            watch.stop();
            System.out.printf("%s �쐬����:[%d] ms\n",
                    previousNewestJsonFile.getName(), watch.getTime());
            watch.reset();
        }

        // ProjectSummaryAction action = PMUtils.getProjectSummaryAction(build);
        // action.setFileName(pmJSON.getName());
        for (int i = 0; i < PREFIX_ARRAY.length; i++) {
            File base_json = new File(build.getRootDir(),
                    ProjectUtils.findJSONFileName(PREFIX_ARRAY[i] + "_" + name));
            System.out.println(base_json.getAbsolutePath());

            // if (base_json.exists() || i == 0) {
            if (base_json.exists()) {
                watch.reset();
                watch.start();
                ProjectSummaryAction action = new ProjectSummaryAction(build);
                action.setFileName(pmJSON.getName());
                action.setBasePrefix(PREFIX_ARRAY[i]);
                build.addAction(action);
                watch.stop();
                System.out.printf("%s �ǉ�����:[%d] ms\n", action, watch.getTime());
            }
        }

        HolidayCalendarAction action = new HolidayCalendarAction(build);
        action.setFileName(pmJSON.getName());
        build.addAction(action);

        File json = new File(build.getRootDir(), pmJSON.getName());
        // listener.getLogger().println(
        // "[EVM Tools] Project :" + json.getAbsolutePath());

        watch.reset();
        watch.start();
        Project project = null;
        try {
            project = new JSONProjectCreator(json).createProject();
        } catch (ProjectException e) {
            listener.getLogger().println(e);
            throw new AbortException(e.getMessage());
        }
        watch.stop();
        System.out
                .printf("���[�����M�`�F�b�N�̂��߂�Project�쐬����:[%d] ms\n", watch.getTime());

        watch.reset();
        watch.start();
        PMUtils.checkProjectAndMail(project, addresses, build, listener,
                sendAll);
        watch.stop();
        System.out.printf("���[�����M����:[%d] ms\n", watch.getTime());

        // testMethod(build, listener);
        watch = null;
        return true;
    }

    /**
     * @param build
     * @param root
     *            ���[�N�X�y�[�X�̃��[�g
     * @param fileName
     *            �W�v�Ώ�Excel�t�@�C��
     * @param listener
     * @throws IOException
     * @throws InterruptedException
     */
    private boolean checkHigawari(AbstractBuild build, String fileName,
            BuildListener listener) throws IOException, InterruptedException {

        FilePath root = build.getModuleRoot();

        // ���X�������t�@�C���ƁAdate.dat�̓��t������ׂ�K�v����B
        PrintStream logger = listener.getLogger();

        FilePath targetFile = new FilePath(root,
                ProjectUtils.findJSONFileName(fileName)); // targetFile
                                                          // ���Y��JSON.���͍쐬�O�̌Â����c�B(���͍���Ă��邪�B)
        FilePath previousNewestFile = new FilePath(root, targetFile.getName()
                + "." + PMConstants.TMP_EXT); // �O���荞�񂾍ŐV�t�@�C��(��JSON�t�@�C��)�ւ̎Q��

        String shimeFileName = PMConstants.DATE_DAT_FILENAME;

        FilePath shimeFile = PMUtils.findBaseDateFile1(build); // ���̍s�́A������t�@�C�����Q�Ƃ���v���W�F�N�g���Ȃ��Ȃ�����A�R�����g�A�E�g�B
        // FilePath shimeFile = new FilePath(root, shimeFileName); // ����t�@�C��
        File shimeFile2 = PMUtils.findBaseDateFile(build);

        logger.println("[EVM Tools] �O���荞�񂾍ŐV�t�@�C��(JSON�t�@�C��): "
                + previousNewestFile.getName());
        logger.println("[EVM Tools] (" + previousNewestFile + ")");
        if (!previousNewestFile.exists()) {
            logger.println("[EVM Tools] �O���荞�񂾃t�@�C�������݂��Ȃ��̂ł��̂܂܏W�v���������{���܂��B");
            return true;
        }
        if (shimeFile2 == null) {
            logger.println("[EVM Tools] ���ւ�����t�@�C�������݂��Ȃ��̂ŋ��o�[�W�����̊���t�@�C���ŏ����𑱂��܂��B"); // ���̍s�́A������t�@�C�����Q�Ƃ���v���W�F�N�g���Ȃ��Ȃ�����A�R�����g�A�E�g�B
            logger.println("[EVM Tools] �����ւ�����t�@�C��: " + shimeFile);// ���̍s�́A������t�@�C�����Q�Ƃ���v���W�F�N�g���Ȃ��Ȃ�����A�R�����g�A�E�g�B
            if (!shimeFile.exists()) {// ���̍s�́A������t�@�C�����Q�Ƃ���v���W�F�N�g���Ȃ��Ȃ�����A�R�����g�A�E�g�B
                logger.println("[EVM Tools] �����ւ�����t�@�C�������݂��Ȃ��̂ł��̂܂܏W�v���������{���܂��B");// ���̍s�́A������t�@�C�����Q�Ƃ���v���W�F�N�g���Ȃ��Ȃ�����A�R�����g�A�E�g�B
                return true;// ���̍s�́A������t�@�C�����Q�Ƃ���v���W�F�N�g���Ȃ��Ȃ�����A�R�����g�A�E�g�B
            }// ���̍s�́A������t�@�C�����Q�Ƃ���v���W�F�N�g���Ȃ��Ȃ�����A�R�����g�A�E�g�B

            // ���̓�s�́A������t�@�C�����Q�Ƃ���v���W�F�N�g���Ȃ��Ȃ�����A�A���R�����g�B
            // logger.println("[EVM Tools] ���ւ�����t�@�C�������݂��Ȃ��̂ł��̂܂܏W�v���������{���܂��B");
            // return true;
        } else {
            logger.println("[EVM Tools] ���ւ�����t�@�C��: "
                    + shimeFile2.getAbsolutePath());
        }

        logger.println("[EVM Tools] �O���荞�񂾃t�@�C�������ւ�����t�@�C�������݂��܂��B");
        logger.println("[EVM Tools] �W�v������I��������O��t�@�C���͏㏑�����Ă��܂��̂ŁA���t���`�F�b�N���ď㏑�����Ă悢�����m�F���Ă���A�W�v���������{���܂��B");

        StopWatch watch = new StopWatch();
        watch.start();
        Date targetDateOld = root.act(new DateGetter(targetFile.getName(),
                "json"));// �W�v�Ώۂ̓��t�B// ���̏����͂�����߂�B
        watch.stop();
        System.out.printf("json���t�쐬����:[%d] ms\n", watch.getTime());
        watch.reset();

        watch.start();
        Date targetDate = root.act(new DateGetter(fileName, "excel"));// �W�v�Ώۂ̓��t
        watch.stop();
        System.out.printf("poi���t�쐬����:[%d] ms\n", watch.getTime());
        watch.reset();

        Date newestDate = root.act(new DateGetter(previousNewestFile.getName(),
                "json"));// ���܂Ŏ�荞�񂾊��

        Date shimeDate = null;
        if (shimeFile2 == null) {// ���̍s�́A������t�@�C�����Q�Ƃ���v���W�F�N�g���Ȃ��Ȃ�����A�R�����g�A�E�g�B
            shimeDate = root.act(new DateGetter(shimeFileName, "txt")); // ���̍s�́A������t�@�C�����Q�Ƃ���v���W�F�N�g���Ȃ��Ȃ�����A�R�����g�A�E�g�B
        } else {// ���̍s�́A������t�@�C�����Q�Ƃ���v���W�F�N�g���Ȃ��Ȃ�����A�R�����g�A�E�g�B
            shimeDate = ProjectUtils.createDateData(shimeFile2); // ���̍s�́A������t�@�C�����Q�Ƃ���v���W�F�N�g���Ȃ��Ȃ�����A�R�����g�A�E�g�B
        }// ���̍s�́A������t�@�C�����Q�Ƃ���v���W�F�N�g���Ȃ��Ȃ�����A�R�����g�A�E�g�B

        // ���̍s�́A������t�@�C�����Q�Ƃ���v���W�F�N�g���Ȃ��Ȃ�����A�A���R�����g�B
        // shimeDate = PMUtils.getBaseDateFromBaseDateFile(shimeFile2);

        logger.println("[EVM Tools] �Ώۃt�@�C���̊��:"
                + DateFormatUtils.format(targetDate, "yyyyMMdd") + " : "
                + fileName);
        logger.println("[EVM Tools] �O���荞�񂾃t�@�C�����(�O����):"
                + DateFormatUtils.format(newestDate, "yyyyMMdd") + " : "
                + previousNewestFile.getName());
        logger.println("[EVM Tools] ���ւ����:"
                + DateFormatUtils.format(shimeDate, "yyyyMMdd") + " : "
                + shimeFileName);

        if (targetDate.getTime() <= shimeDate.getTime()) { // �V������A������ߋ��͎�荞�܂Ȃ�
            logger.println("[EVM Tools] �Ώۃt�@�C���̊�����A���ւ�����Ɠ������ߋ��B���ւ��̂��ƂɁA����܂ł̊���̃f�[�^����荞�����Ƃ��Ă���B");
            return false;
        }

        if (targetDate.getTime() == newestDate.getTime()) {// ��������̃f�[�^�̎捞�Ȃ̂ŁAOK
            logger.println("[EVM Tools] �Ώۃt�@�C���̊�����A���ւ������薢�����A�O�����Ɠ�������̃f�[�^�Ȃ̂Ŗ��Ȃ�");
            return true;
        }

        if (newestDate.getTime() == shimeDate.getTime()) {// �V������ȂǁB���͂ǂ�Ȋ�������邩������Ȃ��̂ŁAOK�B
            logger.println("[EVM Tools] �Ώۃt�@�C���̊�����A���ւ������薢�����A�O�����Ɠ��ւ�����������Ȃ̂ŁA�Ώۃt�@�C���͗l�X�Ȋ�������蓾��̂Ŗ��Ȃ�");
            return true;
        } else {

            if (targetDate.getTime() == newestDate.getTime()) {
                logger.println("[EVM Tools] �O�����Ɠ��ւ�������قȂ�(�܂�O�����ōX�V��)�A�����đΏۃt�@�C���̊���͑O�����Ƃ��Ȃ��Ȃ̂Ŗ��Ȃ�");
                return true;
            } else {
                logger.println("[EVM Tools] �O�����Ɠ��ւ�������قȂ�(�܂�O�����ōX�V��)�A�����đΏۃt�@�C���̊�����O�����ƈقȂ�B���Ȃ킿�A���ւ�肪�s���Ă��Ȃ��\��������");
                return false;
            }
        }
    }

    private static class DateGetter implements FileCallable<Date> {
        private final String fileName;

        private final String format;

        public DateGetter(String name, String format) {
            this.fileName = name;
            this.format = format;
        }

        @Override
        public Date invoke(File f, VirtualChannel channel) throws IOException,
                InterruptedException {
            File target = new File(f, fileName);
            if ("excel".equals(format)) {
                return PMUtils.getBaseDateFromExcelWithPoi(target);
            } else if ("json".equals(format)) {
                return PMUtils.getBaseDateFromJSON(target);
            } else {
                return ProjectUtils.createDateData(target);
                // String string = ReadUtils.readFile(target);
                // try {
                // Date parseDate = DateUtils.parseDate(string,
                // new String[] { "yyyyMMdd" });
                // return parseDate;
                // } catch (ParseException e) {
                // e.printStackTrace();
                // }
            }
            // return null;
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            // TODO �����������ꂽ���\�b�h�E�X�^�u

        }
    }

    /**
     * root�ɑ΂���callable�ȏ��������s���A���ʃt�@�C����buildRoot�̉��ɔz�u����B
     * FilePath[]�̃f�[�^���擾�����[�J���ɃR�s�[�B �擪�̃|�C���^(FilePath)�����㑱�̏����Ŏg���̂ŁA�Ăь��ɕԋp�B
     * 
     * @param root
     * @param buildRoot
     * @param callable
     * @throws IOException
     * @throws InterruptedException
     */
    private FilePath[] executeAndCopies(FilePath root, FilePath buildRoot,
            FileCallable<FilePath[]> callable) throws IOException,
            InterruptedException {
        FilePath[] resultPaths = root.act(callable);

        for (FilePath resultPath : resultPaths) {
            if (resultPath != null) {
                FilePath targetPath = new FilePath(buildRoot,
                        resultPath.getName());
                resultPath.copyTo(targetPath); // remote�t�@�C�����A���[�J���ɃR�s�[�B�B
            }
        }
        return resultPaths;
    }

    // private void executeAndCopy(FilePath root, FilePath buildRoot,
    // FileCallable<FilePath> callable) throws IOException,
    // InterruptedException {
    // FilePath resultPath = root.act(callable);
    //
    // // FilePath returnPath = null;
    // // for (FilePath resultPath : resultPaths) {
    // FilePath targetPath = new FilePath(buildRoot, resultPath.getName());
    // resultPath.copyTo(targetPath); // remote�t�@�C�����A���[�J���ɃR�s�[�B�B
    // // if (returnPath == null) {
    // // returnPath = targetPath;
    // // }
    // // }
    // // return returnPath;
    // }

    private static class AllCallable implements FileCallable<FilePath[]> {

        /**
         * <code>serialVersionUID</code> �̃R�����g
         */
        private static final long serialVersionUID = 5800408421981026754L;

        private final String fileName;

        private final boolean createJsonFlag;

        private final BuildListener listener;

        public AllCallable(String fileName, boolean createJsonFlag,
                BuildListener listener) {
            this.fileName = fileName;
            this.createJsonFlag = createJsonFlag;
            this.listener = listener;
        }

        @Override
        public FilePath[] invoke(File f, VirtualChannel channel)
                throws IOException, InterruptedException {
            List<FilePath> returnList = new ArrayList<FilePath>();

            StopWatch watch = new StopWatch();
            watch.start();
            listener.getLogger().println("[EVM Tools] JSON�t�@�C���쐬�J�n");
            FilePath[] doProjectWriter = doProjectWriter(f, fileName,
                    createJsonFlag);
            listener.getLogger().println(
                    "[EVM Tools] �쐬�����B�t�@�C����: " + doProjectWriter[0]);
            watch.stop();
            System.out.printf("%s �쐬����:[%d] ms\n",
                    doProjectWriter[0].getName(), watch.getTime());
            watch.reset();

            watch.start();
            listener.getLogger().println("[EVM Tools] PV�t�@�C���쐬�J�n");
            FilePath[] doPVCreatorExecutor = doPVCreatorExecutor(f, fileName);
            watch.stop();
            System.out.printf("PV�쐬����:[%d] ms\n", watch.getTime());
            watch.reset();

            watch.start();
            listener.getLogger().println("[EVM Tools] AC�t�@�C���쐬�J�n");
            FilePath[] doACCreatorExecutor = doACCreatorExecutor(f, fileName);
            watch.stop();
            System.out.printf("AC�쐬����:[%d] ms\n", watch.getTime());
            watch.reset();

            watch.start();
            listener.getLogger().println("[EVM Tools] EV�t�@�C���쐬�J�n");
            FilePath[] doEVCreatorExecutor = doEVCreatorExecutor(f, fileName);
            watch.stop();
            System.out.printf("EV�쐬����:[%d] ms\n", watch.getTime());
            watch.reset();

            returnList.addAll(Arrays.asList(doProjectWriter));
            returnList.addAll(Arrays.asList(doPVCreatorExecutor));
            returnList.addAll(Arrays.asList(doACCreatorExecutor));
            returnList.addAll(Arrays.asList(doEVCreatorExecutor));

            return returnList.toArray(new FilePath[returnList.size()]);
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            // TODO �����������ꂽ���\�b�h�E�X�^�u

        }

    }

    private static class ProjectWriterExecutor implements
            FileCallable<FilePath[]> {
        /**
         * <code>serialVersionUID</code> �̃R�����g
         */
        private static final long serialVersionUID = -1909120435783640612L;

        private final String fileName;

        private final boolean createJsonFlag;

        public ProjectWriterExecutor(String fileName, boolean createJsonFlag) {
            this.fileName = fileName;
            this.createJsonFlag = createJsonFlag;
        }

        public FilePath[] invoke(File f, VirtualChannel channel)
                throws IOException, InterruptedException {
            return doProjectWriter(f, fileName, createJsonFlag);
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            // TODO �����������ꂽ���\�b�h�E�X�^�u

        }

    }

    private static FilePath[] doProjectWriter(File f, String fileName,
            boolean createJsonFlag) throws IOException, InterruptedException {
        File target = new File(f, fileName);
        try {
            List<FilePath> returnList = new ArrayList<FilePath>();
            File result = ProjectWriter.write(target);
            returnList.add(new FilePath(result));
            for (String base_prefix : PREFIX_ARRAY) {
                File base = new File(target.getParentFile(), base_prefix + "_"
                        + target.getName());
                if (base.exists() && createJsonFlag) { // ���ւ�胂�[�h�łȂ��Ƃ���(base������΂�����)����̃t�@�C����JSON�����s��
                    FilePath result_base = new FilePath(
                            ProjectWriter.write(base));
                    returnList.add(result_base);
                }
                if (!createJsonFlag) { // ���ւ�胂�[�h�̏ꍇ�́Abase_xx.json��(����ꍇ��)���̂܂܎g��
                    FilePath result_base = new FilePath(new File(
                            base.getParentFile(),
                            ProjectUtils.findJSONFileName(base.getName())));
                    if (result_base.exists()) {
                        returnList.add(result_base);
                    }
                }
            }
            return returnList.toArray(new FilePath[returnList.size()]);
        } catch (ProjectException e) {
            throw new IOException(e);
        }
    }

    private static class PVCreatorExecutor implements FileCallable<FilePath[]> {
        /**
         * <code>serialVersionUID</code> �̃R�����g
         */
        private static final long serialVersionUID = 7813882644092331480L;

        private final String fileName;

        public PVCreatorExecutor(String fileName) {
            this.fileName = fileName;
        }

        public FilePath[] invoke(File f, VirtualChannel channel)
                throws IOException, InterruptedException {
            return doPVCreatorExecutor(f, fileName);
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            // TODO �����������ꂽ���\�b�h�E�X�^�u

        }
    }

    private static FilePath[] doPVCreatorExecutor(File f, String fileName)
            throws IOException {
        File target = new File(f, fileName);
        try {
            File jsonFile = new File(target.getParentFile(),
                    ProjectUtils.findJSONFileName(target.getName()));
            if (jsonFile.exists()) {
                File result = PVCreator.createFromJSON(jsonFile);
                File resultForPivot = PVCreator
                        .createForPivotFromJSON(jsonFile);
                return new FilePath[] { new FilePath(result),
                        new FilePath(resultForPivot) };
            }
            File result = PVCreator.create(target);
            File resultForPivot = PVCreator.createForPivot(target);
            return new FilePath[] { new FilePath(result),
                    new FilePath(resultForPivot) };

        } catch (ProjectException e) {
            throw new IOException(e);
        }
    }

    private static class ACCreatorExecutor implements FileCallable<FilePath[]> {

        /**
         * <code>serialVersionUID</code> �̃R�����g
         */
        private static final long serialVersionUID = 6430118387402281875L;

        private final String fileName;

        public ACCreatorExecutor(String fileName) {
            this.fileName = fileName;
        }

        public FilePath[] invoke(File f, VirtualChannel channel)
                throws IOException, InterruptedException {
            return doACCreatorExecutor(f, fileName);
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            // TODO �����������ꂽ���\�b�h�E�X�^�u

        }
    }

    private static FilePath[] doACCreatorExecutor(File f, String fileName)
            throws IOException {
        File target = new File(f, fileName);
        try {
            FilePath[] results = executesAC(target, PREFIX_ARRAY);
            return results;
        } catch (ProjectException e) {
            throw new IOException(e);
        }
    }

    private static FilePath[] executesAC(File target, String[] prefixArray)
            throws ProjectException {
        List<FilePath> returnList = new ArrayList<FilePath>();
        for (String base_prefix : prefixArray) {
            FilePath result = executeAC(target, base_prefix + "_");
            if (result != null) {
                returnList.add(result);
            }
        }
        return returnList.toArray(new FilePath[returnList.size()]);
    }

    private static FilePath executeAC(File target, String base_prefix)
            throws ProjectException {

        File jsonFile = new File(target.getParentFile(),
                ProjectUtils.findJSONFileName(target.getName()));
        File jsonFile_base = new File(target.getParentFile(),
                ProjectUtils.findJSONFileName(base_prefix + target.getName()));
        if (jsonFile_base.exists()) {
            File result = ACCreator.createFromJSON(jsonFile, jsonFile_base,
                    base_prefix);
            return new FilePath(result);
        }

        File base = new File(target.getParentFile(), base_prefix
                + target.getName()); // file����Prefix: base_������

        if (base.exists()) {
            File result = ACCreator.create(target, base, base_prefix);
            return new FilePath(result);
        }
        return null;
    }

    private static class EVCreatorExecutor implements FileCallable<FilePath[]> {
        /**
         * <code>serialVersionUID</code> �̃R�����g
         */
        private static final long serialVersionUID = 6168445192827009758L;

        private final String fileName;

        public EVCreatorExecutor(String fileName) {
            this.fileName = fileName;
        }

        public FilePath[] invoke(File f, VirtualChannel channel)
                throws IOException, InterruptedException {
            return doEVCreatorExecutor(f, fileName);
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            // TODO �����������ꂽ���\�b�h�E�X�^�u

        }
    }

    private static FilePath[] doEVCreatorExecutor(File f, String fileName)
            throws IOException {
        File target = new File(f, fileName);
        try {
            FilePath[] results = executesEV(target, PREFIX_ARRAY);
            return results;
            // return execute(target, base_prefix);
        } catch (ProjectException e) {
            throw new IOException(e);
        }
    }

    private static FilePath[] executesEV(File target, String[] prefixArray)
            throws ProjectException {
        List<FilePath> returnList = new ArrayList<FilePath>();
        for (String base_prefix : prefixArray) {
            FilePath result = executeEV(target, base_prefix + "_");
            if (result != null) {
                returnList.add(result);
            }
        }
        return returnList.toArray(new FilePath[returnList.size()]);
    }

    private static FilePath executeEV(File target, String base_prefix)
            throws ProjectException {

        File jsonFile = new File(target.getParentFile(),
                ProjectUtils.findJSONFileName(target.getName()));
        File jsonFile_base = new File(target.getParentFile(),
                ProjectUtils.findJSONFileName(base_prefix + target.getName()));
        if (jsonFile_base.exists()) {
            File result = EVCreator.createFromJSON(jsonFile, jsonFile_base,
                    base_prefix);
            return new FilePath(result);
        }

        File base = new File(target.getParentFile(), base_prefix
                + target.getName()); // file����Prefix: base_������
        if (base.exists()) {
            File result = EVCreator.create(target, base, base_prefix);
            return new FilePath(result);
        }
        return null;
    }

    @Override
    public Action getProjectAction(AbstractProject<?, ?> project) {
        return new ProjectSummaryProjectAction(project);
    }

    @Override
    public Collection<? extends Action> getProjectActions(
            AbstractProject<?, ?> project) {
        Collection<Action> ret = new ArrayList<Action>();

        ProjectSummaryProjectAction action = null;
        action = new ProjectSummaryProjectAction(project);
        action.setFormat(FORMAT.GRAPH);
        ret.add(action);

        action = new ProjectSummaryProjectAction(project);
        action.setFormat(FORMAT.LIST);
        ret.add(action);

        return ret;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Descriptor for {@link EVMToolsBuilder}. Used as a singleton. The class is
     * marked as public so that it can be accessed from views.
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
        private String prefixs;

        private String addresses;

        // /**
        // * To persist global configuration information, simply store it in a
        // * field and call save().
        // *
        // * <p>
        // * If you don't want fields to be persisted, use <tt>transient</tt>.
        // */
        // private boolean useFrench;

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
            return "EVM�W�v�c�[��";
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

            prefixs = formData.getString("prefixs");
            if (formData.has("useMail")) {
                JSONObject useMail = formData.getJSONObject("useMail");
                addresses = useMail.getString("addresses");
            } else {
                addresses = null;
            }
            save();
            return super.configure(req, formData);
        }

        // /**
        // * This method returns true if the global configuration says we should
        // * speak French.
        // *
        // * The method name is bit awkward because global.jelly calls this
        // method
        // * to determine the initial state of the checkbox by the naming
        // * convention.
        // */
        // public boolean getUseFrench() {
        // return useFrench;
        // }

        // Getter������΁A�ۑ������ۂ��B
        public String getPrefixs() {
            return prefixs;
        }

        // Getter������΁A�ۑ������ۂ��B
        public String getAddresses() {
            return addresses;
        }
    }

    // private static class DateChecker implements FileCallable<Boolean> {
    // private final String dotTmpFileName;
    //
    // public DateChecker(String name) {
    // dotTmpFileName = name;
    // }
    //
    // @Override
    // public Boolean invoke(File f, VirtualChannel channel)
    // throws IOException, InterruptedException {
    // try {
    // Date baseDate = new ExcelProjectCreator(new File(f,
    // dotTmpFileName)).createProject().getBaseDate();
    // String format = DateFormatUtils.format(baseDate, "yyyyMMdd");
    // File dateFile = new File(f, "date.dat");
    // if (dateFile.exists()) {
    // System.out.println("�t�@�C�������݂��܂��B");
    // }
    //
    // String string = ReadUtils.readFile(dateFile);
    // System.out.println(string);
    // System.out.println(format);
    // return string.equals(format);
    //
    // } catch (ProjectException e) {
    // // TODO �����������ꂽ catch �u���b�N
    // e.printStackTrace();
    // }
    // return Boolean.FALSE;
    // }
    //
    // }

    // private void testMethod(AbstractBuild build, BuildListener listener)
    // throws IOException, InterruptedException {
    // try {
    // ExtensionList<TokenMacro> all = TokenMacro.all();
    // for (TokenMacro tokenMacro : all) {
    // listener.getLogger().println(tokenMacro.toString());
    // }
    //
    // List<TokenMacro> privateMacros = new ArrayList<TokenMacro>();
    // ClassLoader cl = Jenkins.getInstance().pluginManager.uberClassLoader;
    // for (final IndexItem<EmailToken, TokenMacro> item : Index.load(
    // EmailToken.class, TokenMacro.class, cl)) {
    // try {
    // privateMacros.add(item.instance());
    // } catch (Exception e) {
    // // ignore errors loading tokens
    // }
    // }
    // String expand = TokenMacro.expandAll(build, listener,
    // "$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS!",
    // false, null);
    // listener.getLogger().println(expand);
    // expand = TokenMacro.expandAll(build, listener,
    // "Check console output at $BUILD_URL to view the results.",
    // false, null);
    // listener.getLogger().println(expand);
    // String BUILD_URL = (new StringBuilder())
    // .append(Hudson.getInstance().getRootUrl())
    // .append(build.getUrl()).toString();
    // String PROJECT_NAME = build.getProject().getName();
    // String BUILD_NUMBER = String.valueOf(build.getNumber());
    //
    // listener.getLogger()
    // .println(
    // String.format("%s - Build # %s", PROJECT_NAME,
    // BUILD_NUMBER));
    // listener.getLogger().println(
    // String.format(
    // "Check console output at %s to view the results.",
    // BUILD_URL));
    // } catch (MacroEvaluationException e1) {
    // // TODO �����������ꂽ catch �u���b�N
    // e1.printStackTrace();
    // }
    // }

    // This is where you 'build' the project.
    // Since this is a dummy, we just say 'hello world' and call that a
    // build.

    // This also shows how you can consult the global configuration of the
    // builder
    // if (getDescriptor().getUseFrench())
    // listener.getLogger().println("Bonjour, " + name + "!");
    // else
    // listener.getLogger().println("Hello, " + name + "!");
    //
    // Collection<User> all = User.getAll();

}