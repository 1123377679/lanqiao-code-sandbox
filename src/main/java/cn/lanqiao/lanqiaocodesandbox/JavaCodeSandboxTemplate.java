package cn.lanqiao.lanqiaocodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.lanqiao.lanqiaocodesandbox.model.ExecuteCodeRequest;
import cn.lanqiao.lanqiaocodesandbox.model.ExecuteCodeResponse;
import cn.lanqiao.lanqiaocodesandbox.model.ExecuteMessage;
import cn.lanqiao.lanqiaocodesandbox.model.JudgeInfo;
import cn.lanqiao.lanqiaocodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Java 代码沙箱模板方法的实现
 */
@Slf4j
public abstract class JavaCodeSandboxTemplate implements CodeSandbox{
    private static final String GLOBAL_CODE_DIR_NAME= "tmpCode";
    private static final String GLOBAL_JAVA_CLASS_NAME= "Main.java";
    private static final Long TIME_OUT = 5000L;

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
        String code = executeCodeRequest.getCode();//用户提交的代码
        String language = executeCodeRequest.getLanguage();//用户提交的语言
        List<String> inputList = executeCodeRequest.getInputList();//用户题目的输入用例
        //判断用户提交的代码中有没有黑名单的代码
        // FoundWord foundWord = WORD_TREE.matchWord(code);
        // if (foundWord != null){
        //     //如果不为空，说明有这个参数
        //     System.out.println("包含禁止词:"+foundWord.getFoundWord());
        //     return null;
        // }
        //1) 获取到用户的代码，然后将用户的代码保存为文件
        File userCodeFile = saveCodeToFile(code);

        //2) 编译代码，得到class文件
        ExecuteMessage compileFileExecuteMessage = compileFile(userCodeFile);
        System.out.println(compileFileExecuteMessage);

        //3) 执行代码，得到输出结果
        List<ExecuteMessage> executeMessageList = runFile(userCodeFile, inputList);

        //4) 收集整理输出结果
        ExecuteCodeResponse outputResponse = getOutputResponse(executeMessageList);

        //5) 文件清理
        boolean b = deleteFile(userCodeFile);
        if (!b){
            log.error("deleteFile error,userCodeFilePath = {}",userCodeFile.getAbsolutePath());
        }
        return outputResponse;
    }

    /**
     * 1.将用户的代码保存为文件
     * @param code
     */
    public File saveCodeToFile(String code){
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
        return userCodeFile;
    }

    /**
     * 2.编译代码
     * @param userCodeFile
     */
    public ExecuteMessage compileFile(File userCodeFile){
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsoluteFile());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            if (executeMessage.getExitValue() != 0){
                throw new RuntimeException("编译错误");
            }
            return executeMessage;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 3.执行文件,获取执行结果列表
     * @param userCodeFile
     * @param inputList
     */
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList){
        String absolutePath = userCodeFile.getParentFile().getAbsolutePath();
        //首先拿到输入用例进行遍历
        List<ExecuteMessage> executeMessageList = new ArrayList<>();//用来存储输出信息
        for(String inputArgs:inputList){
            //如果需要更严格的内存限制的话，需要去系统层面设置，而不是设置JVM内存控制
            //Linux系统，cgroup这个技术 实现对某个cpu或者内存的资源分配
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", absolutePath, inputArgs);
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
                throw new RuntimeException("执行错误",e);
            }
        }
        return executeMessageList;
    }

    /**
     * 4.获取输出结果
     * @param executeMessageList
     */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList){
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        //模拟一个最大值，便于判断是否超时
        long maxTime = 0;
        //模拟一个最大的内存值,获取最大的就行
        long maxMemory = 0;
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
            Long memory = executeMessage.getMemory();
            if (memory!=null){
                maxMemory = Math.max(maxMemory, memory);//取最大的内存
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
        judgeInfo.setMemory(maxMemory / 1024); //将字节 b 转换成kb
        judgeInfo.setTime(maxTime);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }

    /**
     * 5.删除文件
     * @param userCodeFile
     * @return
     */
    public boolean deleteFile(File userCodeFile){
        if (userCodeFile.getParentFile()!=null){
            String absolutePath = userCodeFile.getParentFile().getAbsolutePath();
            boolean del = FileUtil.del(absolutePath);
            System.out.println("删除"+(del?"成功":"失败"));
            return del;
        }
        return true;
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