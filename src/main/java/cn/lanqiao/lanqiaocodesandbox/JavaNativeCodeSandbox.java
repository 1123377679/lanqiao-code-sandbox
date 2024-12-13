package cn.lanqiao.lanqiaocodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.WordTree;
import cn.lanqiao.lanqiaocodesandbox.model.ExecuteCodeRequest;
import cn.lanqiao.lanqiaocodesandbox.model.ExecuteCodeResponse;
import cn.lanqiao.lanqiaocodesandbox.model.ExecuteMessage;
import cn.lanqiao.lanqiaocodesandbox.model.JudgeInfo;
import cn.lanqiao.lanqiaocodesandbox.utils.ProcessUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @ Author: 李某人
 * @ Date: 2024/12/10/21:57
 * @ Description:
 *
 * 守护线程:用一个新的线程，判断一个正在运行的线程是否超时
 */
public class JavaNativeCodeSandbox implements CodeSandbox{
    private static final String GLOBAL_CODE_DIR_NAME= "tmpCode";
    private static final String GLOBAL_JAVA_CLASS_NAME= "Main.java";
    private static final Long TIME_OUT = 5000L;
    //使用字典树也可以存放在简历中，字典树的使用场景
    //定义一个黑白名单，可以通过集合实现
    private static final List<String> blackList = Arrays.asList("Files","exec");
    //生成一个字典树的对象
    private static final WordTree WORD_TREE;
    //安全管理器对象
    private static final String SECURITY_MANAGER_CLASS_NAME = "MySecurityManager";
    //安全管理器的路径
    private static final String SECURITY_MANAGER_PATH = "D:\\IT\\LanqiaoJavaProject\\OJProject\\lanqiao-code-sandbox\\src\\main\\resources\\security";
    static {
        WORD_TREE = new WordTree();
        WORD_TREE.addWords(blackList);
    }

    public static void main(String[] args) {
        JavaNativeCodeSandbox javaNativeCodeSandbox = new JavaNativeCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2","3 4"));
        //传入用户写的代码
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        // String code = ResourceUtil.readStr("testCode/errorCode/RunFileError.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        //在这里使用安全管
        // System.setSecurityManager(new MySecurityManager());
        /**
         * 1.使用Java代码原生判题(不使用任何框架实现)
         * 2.使用docker沙箱判题
         *
         * 1) 获取到用户的代码，然后将用户的代码保存为文件
         * 2) 编译代码，得到class文件
         * 3) 执行代码，得到输出结果
         * 4) 收集整理输出结果
         * 5) 文件清理
         * 6) 错误处理，提高程序的健壮性
         */
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        List<String> inputList = executeCodeRequest.getInputList();
        //判断用户提交的代码中有没有黑名单的代码
        // FoundWord foundWord = WORD_TREE.matchWord(code);
        // if (foundWord != null){
        //     //如果不为空，说明有这个参数
        //     System.out.println("包含禁止词:"+foundWord.getFoundWord());
        //     return null;
        // }
        //1) 获取到用户的代码，然后将用户的代码保存为文件
        //获取到当前用户的工作目录
        String userDir = System.getProperty("user.dir");
        //File.separator 是为了兼容不同的系统的 \
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        //判断全局代码目录是否存在，没有则新建
        if (!FileUtil.exist(globalCodePathName)){
            FileUtil.mkdir(globalCodePathName);
        }
        //将用户提交的代码隔离
        String userCodeParentPath = globalCodePathName+File.separator+ UUID.randomUUID();
        //真正的用户路径
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        //直接写入到程序中
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);

        //2) 编译代码，得到class文件
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsoluteFile());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            System.out.println(executeMessage);
        } catch (Exception e) {
            return getErrorResponse(e);
        }
        //3) 执行代码，得到输出结果
        //首先拿到输入用例进行遍历
        List<ExecuteMessage> executeMessageList = new ArrayList<>();//用来存储输出信息
        for(String inputArgs:inputList){
            //如果需要更严格的内存限制的话，需要去系统层面设置，而不是设置JVM内存控制
            //Linux系统，cgroup这个技术 实现对某个cpu或者内存的资源分配
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s;%s -Djava.security.manager=%s Main %s", userCodeParentPath,SECURITY_MANAGER_PATH,SECURITY_MANAGER_CLASS_NAME, inputArgs);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                //开启线程
                new Thread(()->{
                    try {
                        Thread.sleep(TIME_OUT);
                        System.out.println("超时了,中断");
                        runProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
                System.out.println(executeMessage);
                executeMessageList.add(executeMessage);
            } catch (IOException e) {
                return getErrorResponse(e);
            }
        }
        //4) 收集整理输出结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        //模拟一个最大值，便于判断是否超时
        long maxTime = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)){
                executeCodeResponse.setMessage(errorMessage);
                //用户提交的代码执行存在错误
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();//程序执行时间
            if (time!=null){
                maxTime = Math.max(maxTime, time);
            }
        }
        //正常运行结束
        if (outputList.size() == executeMessageList.size()){
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage(outputList);
        //要借助第三方库来进行实现，非常麻烦就不实现了,当然你可以自己去网上搜索一下
        // judgeInfo.setMemory();
        judgeInfo.setTime(maxTime);

        //5) 文件清理
        if (userCodeFile.getParentFile()!=null){
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除"+(del?"成功":"失败"));
        }
        return executeCodeResponse;
    }
    //6) 错误处理，提高程序的健壮性
    /**
     * 获取错误响应
     */
    private ExecuteCodeResponse getErrorResponse(Throwable e){
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        //设置状态码为2 说明错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }
}
